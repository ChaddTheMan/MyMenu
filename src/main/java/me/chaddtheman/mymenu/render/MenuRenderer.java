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
package me.chaddtheman.mymenu.render;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import me.chaddtheman.mymenu.model.ItemTemplate;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.model.MenuItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Draws a menu for one viewer. The output is a fresh {@code Inventory} that nothing else shares.
 *
 * <h2>Why every render is per viewer</h2>
 *
 * 1.x kept one {@code Inventory} per menu and showed it to everyone, so nothing could differ
 * between players. Here a menu is data, and each open produces a new picture of it
 * (ARCHITECTURE §1). That is what makes view permissions and placeholders possible: two players
 * opening the same menu can see different items in the same slot. The inventory is never
 * stored on the model, and nothing reads menu state back out of it (hard rule 3).
 *
 * <p>Rendering reads the model and never writes it. It is main-thread only, because it builds
 * items and inventories.
 *
 * <h2>Modes</h2>
 *
 * <ul>
 *   <li><b>VIEW</b>: a player lacking an item's view permission sees its hidden fallback, or an
 *       empty slot if it has none. Nothing moves to fill the gap, so slot positions are the same
 *       for every viewer (SPEC §8.3).</li>
 *   <li><b>EDIT</b>: view permissions are not applied, since an admin must see an item to edit
 *       it. A permission-gated item gets extra lore lines naming its node and what other
 *       players see instead (SPEC §11.1). The lines are appended below any existing lore, after
 *       a blank separator line, and are added to the rendered stack only; the model is not
 *       touched.</li>
 * </ul>
 *
 * <h2>Unreadable stored items</h2>
 *
 * Serialized items are not checked at load (DECISIONS #67), so one can fail here, for example
 * when it refers to a datapack enchantment that is not loaded. The slot then shows a barrier
 * rather than going empty. An empty slot would hide the fault, and in VIEW mode it would make
 * a slot that still has actions look like nothing is there. The rest of the menu renders
 * normally. The warning is logged once per stored item, naming the menu and slot. In EDIT mode
 * the barrier's lore also names the slot and the cause, and says the stored data is kept
 * unchanged. Storage still holds the bytes, so the item returns intact once whatever it needs
 * is back.
 *
 * <h2>The rendered-icon tag</h2>
 *
 * Every item placed in a menu inventory is tagged with {@link #renderedIconKey()}. It must
 * never be the key bound items carry: a creative-mode player can clone an icon out of an open
 * menu, and a shared key would make every clone a working menu opener (ARCHITECTURE §4.2).
 */
// TODO(stage 10): the marker and barrier text below are hard-coded English; move them to
// messages.yml.
public final class MenuRenderer {

    /** The client rejects lore longer than this. */
    private static final int MAX_LORE_LINES = 256;

    private final ItemBuilder items;
    private final Function<Player, TokenReplacer> tokensFor;
    private final Logger logger;
    private final NamespacedKey renderedIconKey;

    // Warnings already logged, so a broken item logs once rather than on every open.
    private final Set<String> reportedUnreadable = new HashSet<>();

    /**
     * @param tokensFor the per-viewer token replacer; stage 9 supplies the real one, until then
     *                  {@code viewer -> TokenReplacer.NONE}
     */
    public MenuRenderer(Plugin plugin, ItemBuilder items, Function<Player, TokenReplacer> tokensFor,
                        Logger logger) {
        this.items = items;
        this.tokensFor = tokensFor;
        this.logger = logger;
        this.renderedIconKey = new NamespacedKey(plugin, "rendered_icon");
    }

    public NamespacedKey renderedIconKey() {
        return renderedIconKey;
    }

    /**
     * @param revision the registry revision recorded beside {@code menu}, read in the same main
     *                 thread tick; it is what the stale-view check compares against
     */
    public Inventory render(Menu menu, long revision, Player viewer, ViewMode mode) {
        TokenReplacer tokens = tokensFor.apply(viewer);
        Component title = tokens.replace(items.parse(menu.title()));
        MenuHolder holder = new MenuHolder(menu.name(), revision, mode, menu.type(), menu.size(), title);
        Inventory inventory = holder.getInventory();
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            ItemStack stack = switch (mode) {
                case VIEW -> renderForViewer(menu, entry.getKey(), entry.getValue(), viewer, tokens);
                case EDIT -> renderForEditor(menu, entry.getKey(), entry.getValue(), tokens);
            };
            if (stack != null) {
                stack.editPersistentDataContainer(pdc -> pdc.set(renderedIconKey, PersistentDataType.BOOLEAN, true));
                inventory.setItem(entry.getKey(), stack);
            }
        }
        return inventory;
    }

    private @Nullable ItemStack renderForViewer(Menu menu, int slot, MenuItem item, Player viewer,
                                                TokenReplacer tokens) {
        String permission = item.viewPermission();
        if (permission == null || viewer.hasPermission(permission)) {
            return buildOrBarrier(menu, slot, "item", item.icon(), tokens, ViewMode.VIEW);
        }
        ItemTemplate fallback = item.hiddenFallback();
        return fallback == null ? null : buildOrBarrier(menu, slot, "hidden fallback", fallback, tokens, ViewMode.VIEW);
    }

    private ItemStack renderForEditor(Menu menu, int slot, MenuItem item, TokenReplacer tokens) {
        ItemStack stack = buildOrBarrier(menu, slot, "item", item.icon(), tokens, ViewMode.EDIT);
        String permission = item.viewPermission();
        if (permission != null) {
            appendLore(stack, permissionMarker(menu, slot, permission, item.hiddenFallback()));
        }
        return stack;
    }

    private List<Component> permissionMarker(Menu menu, int slot, String permission,
                                             @Nullable ItemTemplate fallback) {
        String othersSee;
        if (fallback == null) {
            othersSee = "an empty slot";
        } else if (isReadable(menu, slot, fallback)) {
            othersSee = "the hidden fallback item";
        } else {
            othersSee = "a barrier (hidden fallback unreadable)";
        }
        // The node is appended as a literal text node, never parsed: it is data, not markup.
        return List.of(
                plain("Only shown with permission:", NamedTextColor.GOLD),
                plain("  ", NamedTextColor.YELLOW).append(Component.text(permission)),
                plain("Others see " + othersSee + ".", NamedTextColor.GRAY));
    }

    // EDIT mode never shows the fallback, but the admin should still hear that it is broken.
    private boolean isReadable(Menu menu, int slot, ItemTemplate template) {
        try {
            items.build(template, TokenReplacer.NONE);
            return true;
        } catch (ItemBuilder.UnreadableItemException e) {
            reportUnreadable(menu, slot, "hidden fallback", template, e);
            return false;
        }
    }

    private ItemStack buildOrBarrier(Menu menu, int slot, String role, ItemTemplate template,
                                     TokenReplacer tokens, ViewMode mode) {
        try {
            return items.build(template, tokens);
        } catch (ItemBuilder.UnreadableItemException e) {
            reportUnreadable(menu, slot, role, template, e);
            return barrier(menu, slot, role, e, mode);
        }
    }

    private void reportUnreadable(Menu menu, int slot, String role, ItemTemplate template,
                                  ItemBuilder.UnreadableItemException e) {
        // Keyed on the bytes too, so a replacement that also fails is reported again.
        int data = template instanceof ItemTemplate.Opaque opaque ? Arrays.hashCode(opaque.data()) : 0;
        if (reportedUnreadable.add(menu.name() + '/' + slot + '/' + role + '/' + data)) {
            logger.warn("Menu '{}' slot {}: the stored {} could not be loaded and shows as a barrier. "
                    + "Its data is kept unchanged. Cause: {}", menu.name(), slot, role, e.getMessage());
        }
    }

    private static ItemStack barrier(Menu menu, int slot, String role, Exception cause, ViewMode mode) {
        ItemStack stack = ItemStack.of(Material.BARRIER);
        stack.setData(DataComponentTypes.CUSTOM_NAME, plain("Unreadable item", NamedTextColor.RED));
        List<Component> lore = switch (mode) {
            case VIEW -> List.of(plain("This item could not be loaded.", NamedTextColor.GRAY));
            case EDIT -> List.of(
                    plain("The stored " + role + " in slot " + slot + " of '" + menu.name() + "'", NamedTextColor.GRAY),
                    plain("could not be loaded. Its data is kept unchanged.", NamedTextColor.GRAY),
                    plain("Cause: ", NamedTextColor.DARK_GRAY).append(Component.text(shorten(cause.getMessage()))));
        };
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return stack;
    }

    private static void appendLore(ItemStack stack, List<Component> extra) {
        ItemLore existing = stack.getData(DataComponentTypes.LORE);
        List<Component> lines = new ArrayList<>(existing == null ? List.of() : existing.lines());
        if (!lines.isEmpty()) {
            lines.add(Component.empty());
        }
        // An item already at the lore limit loses its last lines from the editor view, not the
        // marker: the marker is what the admin needs to see here.
        int room = MAX_LORE_LINES - extra.size();
        if (lines.size() > room) {
            lines = new ArrayList<>(lines.subList(0, room));
        }
        lines.addAll(extra);
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
    }

    // Server exceptions can carry whole NBT dumps; the log line has the full text.
    private static String shorten(String message) {
        return message.length() <= 60 ? message : message.substring(0, 57) + "...";
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
