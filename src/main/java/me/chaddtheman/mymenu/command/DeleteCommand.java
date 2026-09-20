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

import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.render.MenuHolder;
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.service.MutationResult;
import me.chaddtheman.mymenu.session.NavigationStack;
import me.chaddtheman.mymenu.session.SessionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deletes a menu and cleans up everything that still points at it (SPEC §3.5).
 *
 * <h2>Order</h2>
 *
 * The registry drops the menu first, through {@code MenuService}, which refuses while storage is
 * degraded. The delete backup is written by storage before the menu leaves {@code menus.yml}; if
 * the backup fails the menu stays in the file and a reload brings it back. Everything a player can
 * see is then cleaned up on the next tick, because this command may be running inside a menu
 * click, where closing an inventory is not allowed (rule 9). In the gap, a click on the deleted
 * menu already fails the stale-view check, which closes it.
 *
 * <h2>What is not cancelled</h2>
 *
 * SPEC §3.5 also asks for pending {@code MENU} actions that target the menu to be cancelled.
 * {@code ActionExecutor} can cancel a player's whole pending sequence but cannot say what the
 * sequence still holds, so that step needs a change in {@code action/} and is not done here. Until
 * then such an action finds no menu when it fires, and the player is told the menu does not exist.
 */
public final class DeleteCommand {

    public static final CommandSpec SPEC = new CommandSpec("delete", "MyMenu.admin.menu.delete",
            "Delete a menu. A copy is kept in the backups folder.", false, true);

    private final Plugin plugin;
    private final MenuService menus;
    private final SessionManager sessions;
    private final Supplier<PluginConfig> config;

    public DeleteCommand(Plugin plugin, MenuService menus, SessionManager sessions, Supplier<PluginConfig> config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void execute(CommandSender sender, Menu menu) {
        String name = menu.name();
        if (menus.delete(name) instanceof MutationResult.Refused refused) {
            Replies.error(sender, Replies.refusal(refused.reason(), name));
            return;
        }
        Replies.ok(sender, "Deleted menu '" + name + "'. A backup copy is written to the backups folder before "
                + "it leaves menus.yml.");
        if (name.equals(config.get().joinMenu())) {
            Replies.warn(sender, "joinMenu in config.yml still names '" + name + "', which no longer exists. "
                    + "Change it with /mymenu joinmenu <menu|none>.");
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> cascade(name));
    }

    private void cascade(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Prompts first: a pending prompt keeps an edit session alive with no inventory open.
            sessions.edit(player).filter(session -> session.menuName().equals(name)).ifPresent(session -> {
                session.clearPrompt();
                Replies.error(player, "Menu '" + name + "' was deleted, so your editing session ended.");
            });
            Optional<MenuHolder> open = SessionManager.openHolder(player);
            if (open.isPresent() && open.get().menuName().equals(name)) {
                if (open.get().mode() == ViewMode.VIEW) {
                    Replies.error(player, "The menu you had open was deleted.");
                }
                player.closeInventory();
            }
            // Drops an edit session that nothing supports any more.
            sessions.edit(player);
            sessions.view(player).ifPresent(session -> purge(session.history(), name));
        }
    }

    /** Removes every visit to {@code name}, keeping the rest in order. */
    private static void purge(NavigationStack history, String name) {
        List<String> kept = new ArrayList<>();
        for (Optional<String> entry = history.pop(); entry.isPresent(); entry = history.pop()) {
            if (!entry.get().equals(name)) {
                kept.add(entry.get());
            }
        }
        // pop() yields most recent first, so push back from the oldest.
        for (int i = kept.size() - 1; i >= 0; i--) {
            history.push(kept.get(i));
        }
    }
}
