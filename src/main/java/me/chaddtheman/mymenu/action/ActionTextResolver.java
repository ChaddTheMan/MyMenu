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

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Function;

/**
 * Turns the admin-authored text stored in an action into what the player receives.
 *
 * <h2>The two return types are the injection defence</h2>
 *
 * A message comes back as a {@link Component}: the implementation must parse the stored string
 * and replace tokens inside the result, and it has no way to hand a substituted value to a
 * parser afterwards, because the executor never sees a string. This is the same argument as
 * {@code render.TokenReplacer}.
 *
 * <p>A command comes back as a {@link String}, because commands are never parsed, only
 * substituted. What stops a substituted nickname from smuggling in a second command is that
 * {@code ActionExecutor} runs {@link CommandSanitiser} on the returned string itself, after this
 * seam and before dispatch. An implementation cannot skip it because it does not get to call it.
 */
// TODO(stage 9): TextService supplies the implementation (wildcards, then PlaceholderAPI).
public interface ActionTextResolver {

    /** The stored text parsed for display, with any tokens replaced afterwards. */
    Component message(String raw, Player viewer);

    /** The stored command with tokens substituted. Not parsed, not yet sanitised. */
    String command(String raw, Player viewer);

    /** Parses messages with {@code parser} and substitutes nothing. */
    static ActionTextResolver parsingOnly(Function<String, Component> parser) {
        return new ActionTextResolver() {
            @Override
            public Component message(String raw, Player viewer) {
                return parser.apply(raw);
            }

            @Override
            public String command(String raw, Player viewer) {
                return raw;
            }
        };
    }
}
