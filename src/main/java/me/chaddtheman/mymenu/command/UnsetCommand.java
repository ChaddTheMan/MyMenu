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

import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.service.MutationResult;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class UnsetCommand {

    public static final CommandSpec SPEC = new CommandSpec("unset", "MyMenu.admin.menu.unset",
            "Remove a menu's bound item.", false, true);

    private final MenuService menus;

    public UnsetCommand(MenuService menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    public void execute(CommandSender sender, Menu menu) {
        if (menu.boundItem() == null) {
            Replies.info(sender, "Menu '" + menu.name() + "' has no bound item.");
            return;
        }
        switch (menus.update(menu.name(), current -> current.withBoundItem(null))) {
            // Dispensed copies are matched by their tag alone (PlayerInteractListener), so say so.
            case MutationResult.Applied ignored -> Replies.ok(sender, "Menu '" + menu.name() + "' no longer has a "
                    + "bound item. Copies handed out with /mymenu give still open it while the menu exists.");
            case MutationResult.Refused refused -> Replies.error(sender, Replies.refusal(refused.reason(), menu.name()));
        }
    }
}
