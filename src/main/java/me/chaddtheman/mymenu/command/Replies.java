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
package me.chaddtheman.mymenu.command;

import me.chaddtheman.mymenu.service.MutationResult;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Command feedback. TODO(stage 10): every string here and in the subcommands moves to
 * {@code messages.yml}.
 */
final class Replies {

    static final String DEGRADED = "Menu editing is disabled because menu storage is degraded: a menu failed to "
            + "load or a save failed (the server log has the details). Existing menus still work. Fix the cause, "
            + "then run /mymenu reload.";

    private Replies() {
    }

    static void ok(Audience to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    static void info(Audience to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }

    static void warn(Audience to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }

    static void error(Audience to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    /** Why {@code MenuService} refused, in words; SPEC §12 requires every refusal to say. */
    static String refusal(MutationResult.Reason reason, String menuName) {
        return switch (reason) {
            case NOT_LOADED -> "Menus have not finished loading, so nothing can be changed yet.";
            case STORAGE_DEGRADED -> DEGRADED;
            case NO_SUCH_MENU -> "There is no menu named '" + menuName + "'.";
            case MENU_EXISTS -> "A menu named '" + menuName + "' already exists.";
            case ITEMS_OUTSIDE_LAYOUT -> "That would leave items outside menu '" + menuName + "'.";
        };
    }
}
