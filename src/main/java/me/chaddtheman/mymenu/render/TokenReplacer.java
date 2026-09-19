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

import net.kyori.adventure.text.Component;

/**
 * Replaces wildcard and placeholder tokens in text that has <em>already been parsed</em>.
 *
 * <h2>Why this takes a {@code Component}, not a {@code String}</h2>
 *
 * Displayed text is parse, then replace (SPEC §10.1, DECISIONS #72). The admin-authored string
 * is parsed into a component with its tokens still literal; tokens are then replaced inside
 * that component with plain text nodes. A substituted value, which may be a nickname or
 * PlaceholderAPI output and so player-controlled, never reaches a parser, and so cannot inject
 * {@code &c&l} or a MiniMessage tag into text an admin wrote.
 *
 * <p>The signature is the enforcement. An implementation receives a component and returns one;
 * it has no string to hand to a parser. Doing it the dangerous way round would mean changing
 * this interface rather than slipping a call into an implementation.
 *
 * <p>Implementations should use Adventure's {@code Component#replaceText}, which replaces
 * within text nodes and inserts the replacement as a literal. Command values do not come
 * through here: they are never parsed, only substituted and sanitised.
 *
 * <p>One replacer is bound to one viewer, because wildcards such as {@code {PLAYER}} and every
 * placeholder resolve per player.
 */
// TODO(stage 9): TextService supplies the per-viewer implementation (wildcards, then
// PlaceholderAPI). Until then every viewer gets NONE and tokens render literally.
@FunctionalInterface
public interface TokenReplacer {

    TokenReplacer NONE = parsed -> parsed;

    Component replace(Component parsed);
}
