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
package me.chaddtheman.mymenu.model;

/**
 * The container shape a menu renders as. Only {@link #CHEST} is sized by rows; the others have
 * a fixed slot count and ignore the menu's row setting.
 */
public enum MenuType {
    CHEST(-1),
    HOPPER(5),
    DISPENSER(9),
    DROPPER(9);

    private final int fixedSlots;

    MenuType(int fixedSlots) {
        this.fixedSlots = fixedSlots;
    }

    public boolean usesRows() {
        return this == CHEST;
    }

    public int slots(int rows) {
        return usesRows() ? rows * 9 : fixedSlots;
    }
}
