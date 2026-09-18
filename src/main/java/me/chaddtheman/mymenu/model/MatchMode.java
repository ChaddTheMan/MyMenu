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
 * How a held item that MyMenu did <em>not</em> hand out is compared against a menu's bound
 * item. Items MyMenu dispensed carry a tag that is checked first, whatever the mode.
 */
public enum MatchMode {
    TAG_ONLY,
    TYPE,
    /** Colour-stripped display name; forgiving of damage and appended lore (DECISIONS #25). */
    TYPE_AND_NAME,
    EXACT;

    public static final MatchMode DEFAULT = TYPE_AND_NAME;
}
