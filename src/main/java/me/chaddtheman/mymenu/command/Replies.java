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
import me.chaddtheman.mymenu.service.MutationResult.Reason.DelayOverCap;
import me.chaddtheman.mymenu.service.MutationResult.Reason.ItemsOutsideLayout;
import me.chaddtheman.mymenu.service.MutationResult.Reason.Plain;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Why {@code MenuService} refused, in words; SPEC §12 requires every refusal to say. The one
     * place refusal wording lives: the command edge and the subcommands both come here.
     */
    static String refusal(MutationResult.Reason reason, String menuName) {
        return switch (reason) {
            case Plain.RELOAD_RUNNING -> "A reload is running; try again when it has finished.";
            case Plain.NOT_LOADED -> "Menus have not finished loading, so nothing can be changed yet.";
            case Plain.STORAGE_DEGRADED -> DEGRADED;
            case Plain.NO_SUCH_MENU -> "There is no menu named '" + menuName + "'.";
            case Plain.MENU_EXISTS -> "A menu named '" + menuName + "' already exists.";
            case ItemsOutsideLayout outside -> "That would leave items outside menu '" + menuName + "', in "
                    + slots(outside.slots()) + ". Move or remove them first.";
            case DelayOverCap over -> "The " + over.key() + " actions on slot " + over.slot() + " of menu '" + menuName
                    + "' would pause for " + seconds(over.totalTicks()) + " seconds in total; the limit is "
                    + over.limitSeconds() + " seconds.";
        };
    }

    /** "slot 4", "slots 4 and 7", "slots 4, 7 and 8": SPEC counts slots from 0, and so does this. */
    private static String slots(List<Integer> slots) {
        if (slots.size() == 1) {
            return "slot " + slots.getFirst();
        }
        String head = slots.subList(0, slots.size() - 1).stream().map(String::valueOf).collect(Collectors.joining(", "));
        return "slots " + head + " and " + slots.getLast();
    }

    // A tick is a twentieth of a second, so the division is exact in decimal: 610 ticks is 30.5.
    private static String seconds(long ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }
}
