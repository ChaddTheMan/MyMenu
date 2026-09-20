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
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * TODO(stage 10): run the update checker and report its result (SPEC §3.7). Until the checker
 * exists this says so plainly rather than pretending to have checked.
 */
public final class UpdateCommand {

    public static final CommandSpec SPEC = new CommandSpec("update", "MyMenu.admin.update",
            "Check whether a newer MyMenu is available. Never downloads anything.", false, false);

    private final Plugin plugin;

    public UpdateCommand(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void execute(CommandSender sender) {
        Replies.info(sender, "This build of MyMenu (" + plugin.getPluginMeta().getVersion()
                + ") cannot check for updates yet.");
    }
}
