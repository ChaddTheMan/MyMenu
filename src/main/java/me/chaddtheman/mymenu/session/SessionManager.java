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
package me.chaddtheman.mymenu.session;

import me.chaddtheman.mymenu.render.MenuHolder;
import me.chaddtheman.mymenu.render.MenuRenderer;
import me.chaddtheman.mymenu.render.VersionedMenu;
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.service.MenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens menus for players and remembers as little as it can get away with about them.
 *
 * <h2>Validity is derived, never trusted</h2>
 *
 * 1.x's worst bug was "You are currently already using a menu!" shown to a player who was not.
 * The plugin kept a map of who was in a menu, some path forgot to remove the entry, and from
 * then on the map entry, not the player's screen, decided the question. A map cannot be kept
 * perfectly in step with the client: closes arrive late, other plugins open their own inventories
 * over ours, opens get cancelled. So the map here is never the answer. {@link #view} and
 * {@link #edit} look at what the player <em>actually</em> has open, through the inventory's
 * {@link MenuHolder}, and an entry that reality no longer supports is dropped on the spot. A
 * leaked entry can therefore cost a little memory until the player quits, but it can never lock
 * anyone out.
 *
 * <h2>Two bounded exceptions</h2>
 *
 * Reality has two gaps where a session is real but no menu inventory is open. Both are bounded
 * so they cannot turn back into tracked flags:
 * <ul>
 *   <li><b>A swap in flight.</b> Replacing one inventory with another closes the old one first.
 *       The session raises a marker and schedules its clearing for the next tick; by then
 *       either the new inventory is open or the session is stale.</li>
 *   <li><b>A pending chat prompt</b> (stage 8). The editor closes its inventory so the admin can
 *       type. The prompt carries a timeout task that ends the session if the admin never
 *       answers.</li>
 * </ul>
 *
 * <h2>Closes are read, quits are not</h2>
 *
 * Opening a menu from inside a menu closes the first one with reason {@code OPEN_NEW}. Treating
 * that as a real close would wipe the navigation history on every hop, so {@link #closed}
 * branches on the reason. A quit is different: nothing survives it, and the cleanup runs in
 * {@code finally} so an exception cannot leave an entry behind.
 *
 * <h2>Sessions are created after the open succeeds</h2>
 *
 * {@code Player#openInventory} returns {@code null} when another plugin cancels the open. 1.x
 * created its session first, so a cancelled open leaked one with no close event to clean it up.
 * Here the entry is written only once the view exists.
 */
public final class SessionManager {

    public enum OpenResult {
        OPENED,
        /** The registry has no menu by that name. */
        NO_SUCH_MENU,
        /** Another plugin cancelled the open; the player is left with no menu open. */
        CANCELLED,
        /** {@code back} with nothing to go back to; the menu was closed instead. */
        NO_HISTORY
    }

    private final Plugin plugin;
    private final MenuRegistry registry;
    private final MenuRenderer renderer;
    private final Map<UUID, ViewSession> views = new HashMap<>();
    private final Map<UUID, EditSession> edits = new HashMap<>();

    public SessionManager(Plugin plugin, MenuRegistry registry, MenuRenderer renderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /** The holder of the menu inventory the player has open right now, if it is one of ours. */
    public static Optional<MenuHolder> openHolder(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                ? Optional.of(holder)
                : Optional.empty();
    }

    public Optional<ViewSession> view(Player player) {
        ViewSession session = views.get(player.getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        boolean open = openHolder(player).map(holder -> holder.mode() == ViewMode.VIEW).orElse(false);
        if (open || session.swapInFlight()) {
            return Optional.of(session);
        }
        views.remove(player.getUniqueId());
        return Optional.empty();
    }

    public Optional<EditSession> edit(Player player) {
        EditSession session = edits.get(player.getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        boolean open = openHolder(player)
                .map(holder -> holder.mode() == ViewMode.EDIT && holder.menuName().equals(session.menuName()))
                .orElse(false);
        if (open || session.prompt() != null || session.swapInFlight()) {
            return Optional.of(session);
        }
        edits.remove(player.getUniqueId());
        return Optional.empty();
    }

    /** Opens a menu afresh, with no history. Any earlier session of the same kind is replaced. */
    public OpenResult open(Player player, String menuName, ViewMode mode) {
        Optional<VersionedMenu> found = registry.lookup(menuName);
        if (found.isEmpty()) {
            return OpenResult.NO_SUCH_MENU;
        }
        return switch (mode) {
            case VIEW -> openView(player, found.get(), new ViewSession());
            case EDIT -> openEdit(player, found.get(), new EditSession(menuName));
        };
    }

    /** Opens a menu from inside another, keeping the way back. Outside a menu this is {@link #open}. */
    public OpenResult navigate(Player player, String menuName) {
        Optional<VersionedMenu> found = registry.lookup(menuName);
        if (found.isEmpty()) {
            return OpenResult.NO_SUCH_MENU;
        }
        Optional<ViewSession> current = view(player);
        Optional<MenuHolder> holder = openHolder(player);
        if (current.isEmpty() || holder.isEmpty()) {
            return openView(player, found.get(), new ViewSession());
        }
        current.get().history().push(holder.get().menuName());
        return openView(player, found.get(), current.get());
    }

    /** Returns to the previous menu. A menu deleted since it was visited is skipped over. */
    public OpenResult back(Player player) {
        Optional<ViewSession> current = view(player);
        if (current.isEmpty()) {
            return OpenResult.NO_HISTORY;
        }
        NavigationStack history = current.get().history();
        for (Optional<String> previous = history.pop(); previous.isPresent(); previous = history.pop()) {
            Optional<VersionedMenu> found = registry.lookup(previous.get());
            if (found.isPresent()) {
                return openView(player, found.get(), current.get());
            }
        }
        player.closeInventory();
        return OpenResult.NO_HISTORY;
    }

    /**
     * Redraws whatever menu the player has open, after a stale click. Called on the tick after
     * the click, never from inside it. A menu that no longer exists is closed instead.
     */
    public OpenResult refresh(Player player) {
        Optional<MenuHolder> holder = openHolder(player);
        if (holder.isEmpty()) {
            return OpenResult.NO_SUCH_MENU;
        }
        Optional<VersionedMenu> found = registry.lookup(holder.get().menuName());
        if (found.isEmpty()) {
            player.closeInventory();
            return OpenResult.NO_SUCH_MENU;
        }
        return switch (holder.get().mode()) {
            case VIEW -> openView(player, found.get(), view(player).orElseGet(ViewSession::new));
            case EDIT -> openEdit(player, found.get(),
                    edit(player).orElseGet(() -> new EditSession(found.get().menu().name())));
        };
    }

    private OpenResult openView(Player player, VersionedMenu menu, ViewSession session) {
        Inventory inventory = renderer.render(menu, player, ViewMode.VIEW);
        session.markSwap(plugin);
        if (player.openInventory(inventory) == null) {
            return OpenResult.CANCELLED;
        }
        views.put(player.getUniqueId(), session);
        return OpenResult.OPENED;
    }

    private OpenResult openEdit(Player player, VersionedMenu menu, EditSession session) {
        Inventory inventory = renderer.render(menu, player, ViewMode.EDIT);
        session.markSwap(plugin);
        if (player.openInventory(inventory) == null) {
            return OpenResult.CANCELLED;
        }
        edits.put(player.getUniqueId(), session);
        return OpenResult.OPENED;
    }

    /**
     * A menu inventory of ours was closed. {@code OPEN_NEW} is not a close: the session stands,
     * and a tick later {@link #view} and {@link #edit} settle whether what replaced it was ours.
     * Every other reason, including ones added to the API later, ends the session, except an
     * edit session that is waiting on a chat prompt.
     */
    public void closed(Player player, MenuHolder holder, InventoryCloseEvent.Reason reason) {
        UUID id = player.getUniqueId();
        if (reason == InventoryCloseEvent.Reason.OPEN_NEW) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    view(player);
                    edit(player);
                }
            });
            return;
        }
        switch (holder.mode()) {
            case VIEW -> views.remove(id);
            case EDIT -> {
                EditSession session = edits.get(id);
                if (session != null && session.prompt() == null) {
                    edits.remove(id);
                }
            }
        }
    }

    /** Unconditional. Nothing about a player survives their leaving. */
    public void endAll(UUID id) {
        try {
            views.remove(id);
        } finally {
            EditSession session = edits.remove(id);
            if (session != null) {
                session.clearPrompt();
            }
        }
    }

    /**
     * Closes every open menu, for {@code onDisable}. Once the listeners are gone a menu left open
     * is an ordinary chest the player can take items out of.
     */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (openHolder(player).isPresent()) {
                player.closeInventory();
            }
        }
        views.clear();
        for (EditSession session : edits.values()) {
            session.clearPrompt();
        }
        edits.clear();
    }

    /** For probes and reports only: how many entries the maps hold, valid or not. */
    public int trackedEntries() {
        return views.size() + edits.size();
    }
}
