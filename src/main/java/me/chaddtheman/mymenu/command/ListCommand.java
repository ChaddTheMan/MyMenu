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
import me.chaddtheman.mymenu.service.MenuRegistry;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ListCommand {

    public static final CommandSpec SPEC = new CommandSpec("list", "MyMenu.admin.menu.list",
            "List every menu.", false, false);

    private final MenuRegistry registry;

    public ListCommand(MenuRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void execute(CommandSender sender) {
        Collection<Menu> menus = registry.menus();
        if (menus.isEmpty()) {
            Replies.info(sender, "There are no menus yet. Create one with /mymenu create <name>.");
            return;
        }
        Replies.info(sender, "Menus (" + menus.size() + "): " + menus.stream()
                .map(menu -> menu.name() + " (" + menu.size() + " slots)")
                .collect(Collectors.joining(", ")));
    }
}
