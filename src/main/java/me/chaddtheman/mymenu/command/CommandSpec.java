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

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * What the tree and the help pages need to know about one subcommand. The tree gates the node
 * with {@link #permissions}, refuses {@link #mutating} commands while storage is degraded, and
 * help prints {@link #description} beside a usage line generated from the built node, so none of
 * the three can drift from the others.
 *
 * @param permissions any one of these lets a sender see and use the subcommand; the first is
 *                    the one help names
 * @param playerOnly every form needs a player to act on; the tree refuses others politely
 * @param mutating refused while storage is degraded or a reload is running (SPEC §12)
 */
public record CommandSpec(String name, List<String> permissions, String description, boolean playerOnly,
                          boolean mutating) {

    public CommandSpec {
        Objects.requireNonNull(name, "name");
        permissions = List.copyOf(permissions);
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("a subcommand needs a permission");
        }
        Objects.requireNonNull(description, "description");
    }

    public CommandSpec(String name, String permission, String description, boolean playerOnly, boolean mutating) {
        this(name, List.of(permission), description, playerOnly, mutating);
    }

    public String permission() {
        return permissions.getFirst();
    }

    public boolean canUse(CommandSender sender) {
        for (String node : permissions) {
            if (sender.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
