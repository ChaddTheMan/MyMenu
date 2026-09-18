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

import java.util.Objects;

/**
 * The item that opens a menu when used. {@code item} is a prototype captured from the admin's
 * hand; the admin's own copy is never tagged (DECISIONS #55).
 */
public record BoundItem(ItemTemplate item, MatchMode matchMode) {

    public BoundItem {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(matchMode, "matchMode");
    }
}
