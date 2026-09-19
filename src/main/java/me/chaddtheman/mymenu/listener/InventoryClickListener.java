/*
 * MyMenu - interactive chest-inventory menus for Paper
 * Copyright (C) 2026 ChaddTheMan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.chaddtheman.mymenu.listener;

import me.chaddtheman.mymenu.action.Action;
import me.chaddtheman.mymenu.model.ClickKey;
import me.chaddtheman.mymenu.model.MenuItem;
import me.chaddtheman.mymenu.model.VersionedMenu;
import me.chaddtheman.mymenu.render.MenuHolder;
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.service.MenuRegistry;
import me.chaddtheman.mymenu.session.SessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a click in a menu inventory into a click on a menu item.
 *
 * <h2>Cancel first, interpret second</h2>
 *
 * Every click while a menu is open is cancelled, whichever half of the screen it lands in. The
 * top half is the menu, and nothing may leave it. The bottom half is the player's own inventory,
 * but shift-clicks, number keys and double-click collection all reach across into the top half,
 * so cancelling by clicked inventory alone leaks items. The price is that a player cannot
 * rearrange their inventory while a menu is open, which no one does anyway.
 *
 * <h2>The click is answered from the model, not from the inventory</h2>
 *
 * The clicked {@code ItemStack} is never consulted. The slot number is looked up in the menu,
 * because the inventory is a picture (ARCHITECTURE §1): a barrier standing in for an unreadable
 * icon still has its actions in the model, and a slot hidden behind a view permission still has
 * an item in the model that must <em>not</em> run. Both come out right only by asking the model.
 *
 * <h2>Stale pictures are refused, then redrawn a tick later</h2>
 *
 * The holder records the menu name and the registry revision at render time. If the name is gone
 * the menu was deleted; if the revision moved the menu changed. Either way the click is refused
 * and the view is redrawn on the next tick, because opening an inventory inside a click handler
 * is forbidden (rule 9). {@code EDIT} views skip the check: every edit bumps the revision, so an
 * editor would refuse its own next click.
 */
public final class InventoryClickListener implements Listener {

    /**
     * The seam for stage 6. The listener has decided which item was clicked and which list
     * applies; running the list is someone else's job.
     */
    @FunctionalInterface
    public interface Dispatcher {

        Dispatcher NONE = (player, menu, slot, item, key, actions) -> {
        };

        void dispatch(Player player, VersionedMenu menu, int slot, MenuItem item, ClickKey key, List<Action> actions);
    }

    // TODO(stage 10): messages.yml.
    private static final Component MENU_DELETED = Component.text("That menu no longer exists.", NamedTextColor.RED);
    private static final Component MENU_CHANGED = Component.text("That menu has changed; showing the new version.", NamedTextColor.YELLOW);

    private final Plugin plugin;
    private final MenuRegistry registry;
    private final SessionManager sessions;
    private final Dispatcher dispatcher;

    public InventoryClickListener(Plugin plugin, MenuRegistry registry, SessionManager sessions, Dispatcher dispatcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    // LOWEST so the cancel is in place before other plugins look, whatever they do afterwards.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder.mode() == ViewMode.EDIT) {
            // Stage 8 interprets edit clicks. Until then they are cancelled and nothing else.
            return;
        }
        Optional<VersionedMenu> current = registry.lookup(holder.menuName());
        if (current.isEmpty()) {
            player.sendMessage(MENU_DELETED);
            refreshNextTick(player);
            return;
        }
        if (current.get().revision() != holder.revision()) {
            player.sendMessage(MENU_CHANGED);
            refreshNextTick(player);
            return;
        }
        int slot = event.getSlot();
        MenuItem item = current.get().menu().item(slot);
        if (item == null) {
            return;
        }
        String permission = item.viewPermission();
        if (permission != null && !player.hasPermission(permission)) {
            // The player sees the fallback or an empty slot; the item behind it is not theirs.
            return;
        }
        ClickKey key = toKey(event.getClick());
        List<Action> actions = select(item.actions(), key);
        if (actions == null) {
            return;
        }
        dispatcher.dispatch(player, current.get(), slot, item, key, actions);
    }

    private void refreshNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                sessions.refresh(player);
            }
        });
    }

    /**
     * SPEC §8.4: at most one list runs. The click's own key wins; otherwise {@code OTHER};
     * otherwise nothing. {@code DOUBLE_CLICK} never falls through, because the client sends a
     * {@code LEFT} first and both lists would run.
     */
    static @Nullable List<Action> select(Map<ClickKey, List<Action>> actions, ClickKey key) {
        List<Action> own = actions.get(key);
        if (own != null || key == ClickKey.DOUBLE_CLICK) {
            return own;
        }
        return actions.get(ClickKey.OTHER);
    }

    /** ARCHITECTURE §3's mapping from Bukkit's click type onto the menu's keys. */
    static ClickKey toKey(ClickType click) {
        return switch (click) {
            case LEFT -> ClickKey.LEFT;
            case RIGHT -> ClickKey.RIGHT;
            case SHIFT_LEFT -> ClickKey.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKey.SHIFT_RIGHT;
            case MIDDLE -> ClickKey.MIDDLE;
            case DROP, CONTROL_DROP -> ClickKey.DROP;
            case NUMBER_KEY -> ClickKey.NUMBER_KEY;
            case DOUBLE_CLICK -> ClickKey.DOUBLE_CLICK;
            case WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, SWAP_OFFHAND, CREATIVE, UNKNOWN -> ClickKey.OTHER;
        };
    }
}
