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
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Opens a menu in edit mode. What editing does once it is open is stage 8. */
public final class EditCommand {

    public static final CommandSpec SPEC = new CommandSpec("edit", "MyMenu.admin.menu.edit",
            "Open a menu in edit mode.", true, true);

    private final Plugin plugin;
    private final SessionManager sessions;

    public EditCommand(Plugin plugin, SessionManager sessions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public void execute(Player player, Menu menu) {
        OpenCommand.open(plugin, sessions, player, player, menu.name(), ViewMode.EDIT);
    }
}
