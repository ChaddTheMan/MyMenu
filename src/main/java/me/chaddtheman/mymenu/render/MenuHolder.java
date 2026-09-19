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

import me.chaddtheman.mymenu.model.MenuType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/**
 * Marks an inventory as a rendering of a menu, and records which menu, which revision, and
 * which mode.
 *
 * <h2>Why a name and a revision, not the {@code Menu}</h2>
 *
 * Listeners identify menus with {@code getHolder() instanceof MenuHolder} rather than by title
 * (ARCHITECTURE §4.1): titles are not unique, and players can influence them through
 * placeholders.
 *
 * <p>The holder deliberately does not keep the {@code Menu} object. A stale view is detected by
 * looking the name up in the registry again on click (§4.3): absent means the menu was deleted,
 * and a different revision means it changed. A held {@code Menu} could detect neither. Delete
 * and reload leave the old object intact, and it would go on answering as if nothing had
 * happened. Holding only the name also means an open inventory can never become a second
 * source of truth about a menu (hard rule 3).
 *
 * <p>The revision comes from the registry's single plugin-wide counter, so it cannot repeat
 * after a delete and recreate.
 *
 * <p>Only {@link MenuRenderer} creates holders, so every holder is paired with a rendered
 * inventory.
 */
public final class MenuHolder implements InventoryHolder {

    private final String menuName;
    private final long revision;
    private final ViewMode mode;
    private final Inventory inventory;

    MenuHolder(String menuName, long revision, ViewMode mode, MenuType type, int size, Component title) {
        this.menuName = Objects.requireNonNull(menuName, "menuName");
        this.revision = revision;
        this.mode = Objects.requireNonNull(mode, "mode");
        // createInventory only records the holder reference, so passing a partly built `this`
        // is safe. Doing it here keeps the inventory field final.
        this.inventory = switch (type) {
            case CHEST -> Bukkit.createInventory(this, size, title);
            case HOPPER -> Bukkit.createInventory(this, InventoryType.HOPPER, title);
            case DISPENSER -> Bukkit.createInventory(this, InventoryType.DISPENSER, title);
            case DROPPER -> Bukkit.createInventory(this, InventoryType.DROPPER, title);
        };
    }

    public String menuName() {
        return menuName;
    }

    public long revision() {
        return revision;
    }

    public ViewMode mode() {
        return mode;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
