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

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.chaddtheman.mymenu.model.BoundItem;
import me.chaddtheman.mymenu.model.ItemTemplate;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.render.ItemBuilder;
import me.chaddtheman.mymenu.render.TokenReplacer;
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.service.MenuRegistry;
import me.chaddtheman.mymenu.session.SessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Opens a menu when a player clicks with its bound item in the main hand.
 *
 * <h2>Tag first, then the configured mode</h2>
 *
 * An item the plugin handed out carries a persistent-data tag naming its menu, and that tag is
 * the whole answer: it matches its menu, or nothing if that menu is gone. The match mode applies
 * only to items the plugin did not dispense, and menus are tried in name order so the first name
 * wins a tie (SPEC §7).
 *
 * <h2>The tag is not the rendered-icon tag</h2>
 *
 * A creative-mode player can clone an icon straight out of an open menu. Rendered icons carry
 * {@code mymenu:rendered_icon}; bound items carry {@code mymenu:bound_item}. Sharing one key
 * would make every cloned icon a menu opener (ARCHITECTURE §4.2).
 *
 * <h2>Why cancelled events are still read</h2>
 *
 * Paper reports a click on air as already cancelled, because there is no block to interact
 * with, and protection plugins cancel clicks on claimed blocks. Neither should stop the item
 * working, so {@code ignoreCancelled} is off and only an explicit {@code DENY} on the item use
 * is respected.
 */
public final class PlayerInteractListener implements Listener {

    private final NamespacedKey boundItemKey;
    private final MenuRegistry registry;
    private final SessionManager sessions;
    private final ItemBuilder items;
    private final Logger logger;

    // Bound items that failed to build, logged once each. A cache, not per-event state.
    private final Set<String> reportedUnreadable = new HashSet<>();

    public PlayerInteractListener(Plugin plugin, MenuRegistry registry, SessionManager sessions,
                                  ItemBuilder items, Logger logger) {
        this.boundItemKey = new NamespacedKey(plugin, "bound_item");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.items = Objects.requireNonNull(items, "items");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The key stage 7's {@code give} stamps on dispensed items. Value: the menu name. */
    public NamespacedKey boundItemKey() {
        return boundItemKey;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.isEmpty()) {
            return;
        }
        String menuName = match(held);
        if (menuName == null) {
            return;
        }
        event.setCancelled(true);
        sessions.open(event.getPlayer(), menuName, ViewMode.VIEW);
    }

    private @Nullable String match(ItemStack held) {
        String tagged = held.getPersistentDataContainer().get(boundItemKey, PersistentDataType.STRING);
        if (tagged != null) {
            return registry.contains(tagged) ? tagged : null;
        }
        for (Menu menu : registry.menus()) {
            BoundItem bound = menu.boundItem();
            if (bound != null && matches(menu, bound, held)) {
                return menu.name();
            }
        }
        return null;
    }

    private boolean matches(Menu menu, BoundItem bound, ItemStack held) {
        return switch (bound.matchMode()) {
            case TAG_ONLY -> false;
            case TYPE -> sameType(menu, bound.item(), held);
            case TYPE_AND_NAME -> sameType(menu, bound.item(), held) && sameName(menu, bound.item(), held);
            case EXACT -> {
                ItemStack expected = build(menu, bound.item());
                yield expected != null && expected.isSimilar(held);
            }
        };
    }

    // Descriptive templates answer type and name without building; opaque ones must be built.
    private boolean sameType(Menu menu, ItemTemplate template, ItemStack held) {
        if (template instanceof ItemTemplate.Descriptive descriptive) {
            return descriptive.material() == held.getType();
        }
        ItemStack expected = build(menu, template);
        return expected != null && expected.getType() == held.getType();
    }

    private boolean sameName(Menu menu, ItemTemplate template, ItemStack held) {
        String expected;
        if (template instanceof ItemTemplate.Descriptive descriptive) {
            String raw = descriptive.displayName();
            expected = raw == null ? null : plain(items.parseItemText(raw));
        } else {
            ItemStack built = build(menu, template);
            if (built == null) {
                return false;
            }
            expected = plain(built.getData(DataComponentTypes.CUSTOM_NAME));
        }
        return Objects.equals(expected, plain(held.getData(DataComponentTypes.CUSTOM_NAME)));
    }

    private @Nullable ItemStack build(Menu menu, ItemTemplate template) {
        try {
            return items.build(template, TokenReplacer.NONE);
        } catch (ItemBuilder.UnreadableItemException e) {
            if (reportedUnreadable.add(menu.name())) {
                logger.warn("Menu '{}': the bound item could not be loaded, so nothing opens it by hand. "
                        + "Its data is kept unchanged. Cause: {}", menu.name(), e.getMessage());
            }
            return null;
        }
    }

    private static @Nullable String plain(@Nullable Component name) {
        return name == null ? null : PlainTextComponentSerializer.plainText().serialize(name);
    }
}
