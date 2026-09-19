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
package me.chaddtheman.mymenu.action;

import java.util.List;
import java.util.Objects;

/**
 * One step in an item's action list (SPEC §9).
 *
 * <p>Sealed, with one record per type, so that {@code ActionExecutor} and {@code ActionParser}
 * can switch over it exhaustively: adding a ninth type is a compile error at every place that
 * forgot it, rather than a step that silently does nothing. Every record is immutable because
 * menu models are shared between the main thread and the storage writer without copying.
 *
 * <p>The records are nested rather than top-level so their names do not collide with Bukkit's
 * {@code Player} or the plugin's own {@code Menu} at every import site.
 */
public sealed interface Action {

    ActionType type();

    /** Runs the command as the player, with the player's own permissions. */
    record PlayerCommand(String command) implements Action {
        public PlayerCommand {
            requireText(command, "command");
        }

        @Override
        public ActionType type() {
            return ActionType.PLAYER;
        }
    }

    /** Runs the command from the console. */
    record ConsoleCommand(String command) implements Action {
        public ConsoleCommand {
            requireText(command, "command");
        }

        @Override
        public ActionType type() {
            return ActionType.CONSOLE;
        }
    }

    /**
     * Runs the command as the player with {@code permissions} granted for the duration of the
     * dispatch and no longer (SPEC §9.1). An empty list is allowed: the {@code !} shorthand has
     * nowhere to carry nodes, and the editor attaches them afterwards.
     */
    record ElevatedCommand(String command, List<String> permissions) implements Action {
        public ElevatedCommand {
            requireText(command, "command");
            permissions = List.copyOf(permissions);
            for (String node : permissions) {
                requireText(node, "permission node");
            }
        }

        @Override
        public ActionType type() {
            return ActionType.PLAYER_ELEVATED;
        }
    }

    /** Sends the player a message. The text is admin-authored and parsed at execution time. */
    record Message(String text) implements Action {
        public Message {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public ActionType type() {
            return ActionType.MESSAGE;
        }
    }

    /** Opens another menu, pushing the current one onto the navigation history. */
    record OpenMenu(String menuName) implements Action {
        public OpenMenu {
            requireText(menuName, "menuName");
        }

        @Override
        public ActionType type() {
            return ActionType.MENU;
        }
    }

    /** Returns to the previous menu, or closes when there is none. */
    record Back() implements Action {
        @Override
        public ActionType type() {
            return ActionType.BACK;
        }
    }

    /** Closes the menu. */
    record Close() implements Action {
        @Override
        public ActionType type() {
            return ActionType.CLOSE;
        }
    }

    /** Suspends the list for {@code ticks} before the next step runs. */
    record Delay(int ticks) implements Action {
        public Delay {
            if (ticks < 0) {
                throw new IllegalArgumentException("ticks is negative");
            }
        }

        @Override
        public ActionType type() {
            return ActionType.DELAY;
        }
    }

    private static void requireText(String value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isBlank()) {
            throw new IllegalArgumentException(what + " is blank");
        }
    }
}
