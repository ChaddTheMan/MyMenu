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

import me.chaddtheman.mymenu.storage.ActionCodec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads actions from their two input forms and writes them back to storage.
 *
 * <h2>Two ways in, one way out</h2>
 *
 * Storage always holds the explicit form, one mapping per action with a {@code type} field.
 * The prefix shorthand ({@code $} console, {@code \} message, {@code !} elevated, bare player)
 * is accepted only at the input boundary, the chat prompt of the editor, and is converted here
 * before anything else sees it. Keeping the shorthand out of the file means a file can be read
 * without knowing the editor's conventions, and the conventions can change without a migration.
 *
 * <h2>The delay cap is applied to lists, not entries</h2>
 *
 * SPEC §9.2 caps the <em>total</em> delay of a list and says stored lists over the cap are
 * clamped with a warning, never rejected and never checked at run time. A per-entry check
 * cannot see the total, so this codec reads whole lists ({@link #readList}) and clamps once,
 * reducing the delay that crosses the cap and zeroing those after it, so the actions still run
 * in order and the file is repaired on its next save. The same clamp covers shorthand input.
 *
 * <p>Thread-safe: reading happens on whichever thread storage loads on, and the cap can be
 * changed from the main thread after config loads.
 */
public final class ActionParser implements ActionCodec {

    /** SPEC §9.2's default for {@code actions.maxTotalDelaySeconds}. */
    public static final int DEFAULT_MAX_TOTAL_DELAY_SECONDS = 30;

    private static final int TICKS_PER_SECOND = 20;

    private static final String TYPE = "type";
    private static final String VALUE = "value";
    private static final String PERMISSIONS = "permissions";
    private static final String TICKS = "ticks";

    private final Logger logger;
    private volatile int maxTotalDelaySeconds;

    public ActionParser(Logger logger, int maxTotalDelaySeconds) {
        this.logger = Objects.requireNonNull(logger, "logger");
        setMaxTotalDelaySeconds(maxTotalDelaySeconds);
    }

    // TODO(stage 7): config plumbing calls this once actions.maxTotalDelaySeconds is read.
    public void setMaxTotalDelaySeconds(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("maxTotalDelaySeconds is negative");
        }
        this.maxTotalDelaySeconds = seconds;
    }

    public int maxTotalDelaySeconds() {
        return maxTotalDelaySeconds;
    }

    // ---- Storage form -------------------------------------------------------------------

    @Override
    public Action read(Map<String, Object> entry) {
        String typeName = text(entry, TYPE);
        if (typeName == null) {
            throw new IllegalArgumentException("an action has no '" + TYPE + "'");
        }
        ActionType type;
        try {
            type = ActionType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown action type '" + typeName + "' (expected one of "
                    + Arrays.toString(ActionType.values()) + ")");
        }
        rejectUnknownKeys(entry, type);
        return switch (type) {
            case PLAYER -> new Action.PlayerCommand(requireText(entry, VALUE, type));
            case CONSOLE -> new Action.ConsoleCommand(requireText(entry, VALUE, type));
            case PLAYER_ELEVATED -> new Action.ElevatedCommand(requireText(entry, VALUE, type),
                    textList(entry, PERMISSIONS, type));
            case MESSAGE -> new Action.Message(requireText(entry, VALUE, type));
            case MENU -> new Action.OpenMenu(requireText(entry, VALUE, type));
            case BACK -> new Action.Back();
            case CLOSE -> new Action.Close();
            case DELAY -> new Action.Delay(requireTicks(entry, type));
        };
    }

    @Override
    public List<Action> readList(List<Map<String, Object>> entries, String where) {
        List<Action> actions = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            actions.add(read(entry));
        }
        return clampTotalDelay(actions, where);
    }

    /** A new map every call: {@code MenuYamlFormat.dump} aliases any instance it sees twice. */
    @Override
    public Map<String, Object> write(Action action) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(TYPE, action.type().name());
        switch (action) {
            case Action.PlayerCommand command -> out.put(VALUE, command.command());
            case Action.ConsoleCommand command -> out.put(VALUE, command.command());
            case Action.ElevatedCommand command -> {
                out.put(VALUE, command.command());
                out.put(PERMISSIONS, new ArrayList<>(command.permissions()));
            }
            case Action.Message message -> out.put(VALUE, message.text());
            case Action.OpenMenu menu -> out.put(VALUE, menu.menuName());
            case Action.Back ignored -> {
            }
            case Action.Close ignored -> {
            }
            case Action.Delay delay -> out.put(TICKS, delay.ticks());
        }
        return out;
    }

    // ---- Shorthand form -----------------------------------------------------------------

    /**
     * One line as an admin types it: {@code $} console, {@code \} message, {@code !} elevated
     * with no nodes yet, anything else a player command. A leading slash on a command is
     * dropped so {@code /spawn} and {@code spawn} mean the same thing.
     *
     * @throws IllegalArgumentException if nothing usable follows the prefix
     */
    public static Action parseShorthand(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("the action is empty");
        }
        char prefix = trimmed.charAt(0);
        String rest = trimmed.substring(1).strip();
        return switch (prefix) {
            case '$' -> new Action.ConsoleCommand(command(rest, "console command"));
            case '\\' -> new Action.Message(rest);
            case '!' -> new Action.ElevatedCommand(command(rest, "elevated command"), List.of());
            default -> new Action.PlayerCommand(command(trimmed, "command"));
        };
    }

    /** Every line through {@link #parseShorthand}, then the list-level delay clamp. */
    public List<Action> parseList(List<String> lines, String where) {
        List<Action> actions = new ArrayList<>(lines.size());
        for (String line : lines) {
            actions.add(parseShorthand(line));
        }
        return clampTotalDelay(actions, where);
    }

    private static String command(String text, String what) {
        String command = CommandSanitiser.sanitise(text);
        if (command.isBlank()) {
            throw new IllegalArgumentException("the " + what + " is empty");
        }
        return command;
    }

    // ---- The cap ------------------------------------------------------------------------

    /**
     * Returns {@code actions} unchanged when the delays fit, or a copy in which the delay that
     * crosses the cap is shortened to what remains and every later delay is zero. Logs once per
     * list, naming {@code where}.
     */
    public List<Action> clampTotalDelay(List<Action> actions, String where) {
        int cap = maxTotalDelaySeconds;
        int budget = cap * TICKS_PER_SECOND;
        long total = 0;
        for (Action action : actions) {
            if (action instanceof Action.Delay delay) {
                total += delay.ticks();
            }
        }
        if (total <= budget) {
            return actions;
        }
        List<Action> clamped = new ArrayList<>(actions.size());
        int remaining = budget;
        for (Action action : actions) {
            if (action instanceof Action.Delay delay) {
                int ticks = Math.min(delay.ticks(), remaining);
                remaining -= ticks;
                clamped.add(new Action.Delay(ticks));
            } else {
                clamped.add(action);
            }
        }
        logger.warn("{}: the delays add up to {} ticks, over the {}-second cap; clamped to {} ticks",
                where, total, cap, budget);
        return clamped;
    }

    // ---- Field access -------------------------------------------------------------------

    private static Set<String> knownKeys(ActionType type) {
        return switch (type) {
            case PLAYER, CONSOLE, MESSAGE, MENU -> Set.of(TYPE, VALUE);
            case PLAYER_ELEVATED -> Set.of(TYPE, VALUE, PERMISSIONS);
            case BACK, CLOSE -> Set.of(TYPE);
            case DELAY -> Set.of(TYPE, TICKS);
        };
    }

    // Unknown keys are an error rather than a warning, unlike the menu-level keys in
    // MenuYamlFormat: a misspelt 'value' would otherwise surface only as "no 'value'".
    private static void rejectUnknownKeys(Map<String, Object> entry, ActionType type) {
        Set<String> known = knownKeys(type);
        for (String key : entry.keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException("a " + type + " action has no '" + key + "' field");
            }
        }
    }

    private static @Nullable String text(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new IllegalArgumentException("'" + key + "' must be text; put it in quotes");
    }

    private static String requireText(Map<String, Object> entry, String key, ActionType type) {
        String value = text(entry, key);
        if (value == null) {
            throw new IllegalArgumentException("a " + type + " action has no '" + key + "'");
        }
        return value;
    }

    private static List<String> textList(Map<String, Object> entry, String key, ActionType type) {
        Object value = entry.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("'" + key + "' of a " + type + " action must be a list");
        }
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof String node) || node.isBlank()) {
                throw new IllegalArgumentException("every entry of '" + key + "' must be a permission node");
            }
            out.add(node);
        }
        return out;
    }

    private static int requireTicks(Map<String, Object> entry, ActionType type) {
        Object value = entry.get(TICKS);
        if (value == null) {
            throw new IllegalArgumentException("a " + type + " action has no '" + TICKS + "'");
        }
        if (!(value instanceof Integer ticks)) {
            throw new IllegalArgumentException("'" + TICKS + "' must be a whole number, not '" + value + "'");
        }
        if (ticks < 0) {
            throw new IllegalArgumentException("'" + TICKS + "' is negative");
        }
        return ticks;
    }
}
