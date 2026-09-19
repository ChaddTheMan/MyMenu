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

import me.chaddtheman.mymenu.model.Menu;

import java.util.Objects;

/**
 * A menu together with the registry revision it was read at.
 *
 * <p>The two are only meaningful as a pair: the revision is what a rendered view later compares
 * against to learn that its menu changed or vanished, so a revision taken from one lookup and a
 * menu from another would let a stale view pass the check. Making the pair a type means the
 * registry hands out both from one snapshot and nothing downstream can separate them.
 */
public record VersionedMenu(Menu menu, long revision) {

    public VersionedMenu {
        Objects.requireNonNull(menu, "menu");
    }
}
