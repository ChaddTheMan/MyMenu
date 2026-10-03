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
package me.chaddtheman.mymenu.service;

import me.chaddtheman.mymenu.model.ClickKey;
import me.chaddtheman.mymenu.model.Menu;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a request to {@code MenuService}. Refusals are ordinary results rather than
 * exceptions because every one of them is something a player can cause and must be told about
 * (SPEC §12: every refused command explains why).
 */
public sealed interface MutationResult {

    /**
     * @param menu the menu as it now stands, or for a delete, as it was just before removal
     */
    record Applied(Menu menu) implements MutationResult {
    }

    record Refused(Reason reason) implements MutationResult {
    }

    /**
     * Why a change was refused. Most reasons are a bare {@link Plain} constant. The two that must
     * name something are records, so the detail exists exactly where it is needed and nothing can
     * read slot numbers off a refusal that has none.
     */
    sealed interface Reason {

        enum Plain implements Reason {
            /** Menus are still loading; an edit now would be overwritten when the load lands. */
            NOT_LOADED,
            /** A reload is between its flush and its install; an edit now would be replaced by the load. */
            RELOAD_RUNNING,
            STORAGE_DEGRADED,
            NO_SUCH_MENU,
            MENU_EXISTS
        }

        /**
         * A layout change would leave occupied slots outside the menu (DECISIONS #63; SPEC §8.4.1).
         *
         * @param slots in ascending order, counted from 0
         */
        record ItemsOutsideLayout(List<Integer> slots) implements Reason {

            public ItemsOutsideLayout {
                slots = slots.stream().sorted().toList();
                if (slots.isEmpty()) {
                    throw new IllegalArgumentException("no slots are outside the layout");
                }
            }
        }

        /**
         * A list the change writes would pause for longer in total than {@code actions.maxTotalDelaySeconds}
         * allows (SPEC §9.2). Only lists the change touches are judged.
         */
        record DelayOverCap(int slot, ClickKey key, long totalTicks, int limitSeconds) implements Reason {

            public DelayOverCap {
                Objects.requireNonNull(key, "key");
            }
        }
    }
}
