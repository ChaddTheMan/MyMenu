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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Opens a menu for a player on command.
 *
 * <h2>The permission gate lives in the tree, not in the session manager</h2>
 *
 * Using a bound item opens a menu with no permission at all; that is how ordinary players use the
 * plugin (SPEC §4). Both paths call {@link SessionManager#open}, so a check there would gate the
 * bound item too and lock every player out of every menu. The {@code MyMenu.admin.menu.open}
 * nodes are therefore checked by the command tree's {@code requires}, before this class runs.
 *
 * <h2>Why the open waits a tick</h2>
 *
 * A menu item can run {@code /mymenu open other} as a player command, and actions run inside
 * {@code InventoryClickEvent}, where opening an inventory is not allowed (CLAUDE.md, rule 9).
 * The command cannot tell how it was invoked, so it always defers.
 */
public final class OpenCommand {

    public static final String OPEN = "MyMenu.admin.menu.open";
    public static final String OPEN_OTHER = "MyMenu.admin.menu.open.other";

    public static final CommandSpec SPEC = new CommandSpec("open", List.of(OPEN, OPEN_OTHER),
            "Open a menu for yourself, or for another player.", false, false);

    private final Plugin plugin;
    private final SessionManager sessions;

    public OpenCommand(Plugin plugin, SessionManager sessions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public void execute(CommandSender sender, Player target, Menu menu) {
        open(plugin, sessions, sender, target, menu.name(), ViewMode.VIEW);
    }

    /** Shared with {@code edit}: the next-tick open and its report. */
    static void open(Plugin plugin, SessionManager sessions, CommandSender sender, Player target, String menuName,
                     ViewMode mode) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!target.isOnline()) {
                return;
            }
            switch (sessions.open(target, menuName, mode)) {
                case OPENED -> {
                    if (sender != target) {
                        Replies.ok(sender, "Opened '" + menuName + "' for " + target.getName() + ".");
                    }
                }
                case NO_SUCH_MENU -> Replies.error(sender, "Menu '" + menuName + "' was deleted before it could open.");
                case CANCELLED -> Replies.error(sender, "Another plugin stopped '" + menuName + "' from opening.");
                case NO_HISTORY -> {
                    // Only back() reports this.
                }
            }
        });
    }
}
