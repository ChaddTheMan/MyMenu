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

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/**
 * The menus a viewer came through, most recent first, so a {@code BACK} action can return.
 *
 * <p>Depth is capped. Two menus that link to each other would otherwise grow the stack by one
 * entry per hop for as long as the player keeps clicking, and nobody ever goes back that far.
 * When the cap is hit the oldest entry is dropped, not the newest.
 */
public final class NavigationStack {

    static final int MAX_DEPTH = 32;

    private final ArrayDeque<String> names = new ArrayDeque<>();

    public void push(String menuName) {
        Objects.requireNonNull(menuName, "menuName");
        if (names.size() >= MAX_DEPTH) {
            names.removeLast();
        }
        names.addFirst(menuName);
    }

    public Optional<String> pop() {
        return Optional.ofNullable(names.pollFirst());
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public int size() {
        return names.size();
    }

    public void clear() {
        names.clear();
    }
}
