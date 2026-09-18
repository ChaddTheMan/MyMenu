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
 * The keys an item's action lists are stored under.
 *
 * <p>This is MyMenu's own enum rather than Bukkit's {@code ClickType} because {@link #OTHER} is
 * not a click the client can send; it is the stored fallback (DECISIONS #62). Translating a
 * Bukkit click into a key, including the rule that {@link #DOUBLE_CLICK} never falls through to
 * {@code OTHER}, belongs to the click handler, not the model.
 */
public enum ClickKey {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    NUMBER_KEY,
    DOUBLE_CLICK,
    OTHER
}
