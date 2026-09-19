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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts one stored action entry, such as {@code {type: CONSOLE, value: 'time set day'}}, to
 * and from an {@link Action}.
 *
 * <p>This is the seam between storage and the action system. The YAML format handles the
 * {@code actions:} structure itself (click keys, lists, one mapping per entry) and hands each
 * entry here without knowing what an action is.
 *
 * <p>{@code ActionParser} implements this. {@link #NONE} exists for builds and probes that
 * carry no action types; under it any stored action is a load error for its slot rather than
 * something silently dropped.
 *
 * <p>Lists go through {@link #readList} rather than one {@link #read} per entry, because the
 * one list-level rule, the cap on a list's total delay (SPEC §9.2), has to be applied at parse
 * time and cannot be seen from a single entry.
 */
public interface ActionCodec {

    /** @throws IllegalArgumentException if the entry is not a valid action */
    Action read(Map<String, Object> entry);

    /**
     * Every entry of one click key's list, in order. The default reads each entry; an
     * implementation may also apply list-level rules. {@code where} names the menu, slot and
     * key for any warning the codec logs.
     *
     * @throws IllegalArgumentException if any entry is not a valid action
     */
    default List<Action> readList(List<Map<String, Object>> entries, String where) {
        List<Action> actions = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            actions.add(read(entry));
        }
        return actions;
    }

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
