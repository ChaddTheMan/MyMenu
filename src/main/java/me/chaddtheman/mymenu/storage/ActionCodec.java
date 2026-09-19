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
package me.chaddtheman.mymenu.storage;

import me.chaddtheman.mymenu.action.Action;

import java.util.Map;

/**
 * Converts one stored action entry, such as {@code {type: CONSOLE, value: 'time set day'}}, to
 * and from an {@link Action}.
 *
 * <p>This is the seam between storage and the action system. The YAML format handles the
 * {@code actions:} structure itself (click keys, lists, one mapping per entry) and hands each
 * entry here without knowing what an action is.
 *
 * <p>TODO(stage 6): {@code ActionParser} implements this. Until then {@link #NONE} is used, and
 * any stored action is a load error for its slot rather than something silently dropped.
 */
public interface ActionCodec {

    /** @throws IllegalArgumentException if the entry is not a valid action */
    Action read(Map<String, Object> entry);

    /** Must return a new, mutable map on every call; see {@code MenuYamlFormat#dump}. */
    Map<String, Object> write(Action action);

    /** Knows no action types. Empty action lists still load and save. */
    ActionCodec NONE = new ActionCodec() {
        @Override
        public Action read(Map<String, Object> entry) {
            throw new IllegalArgumentException("actions are not supported by this build");
        }

        @Override
        public Map<String, Object> write(Action action) {
            throw new IllegalStateException("no action types exist to write");
        }
    };
}
