/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.InfoMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuChangelog
implements CommandExecutor {
    /*
     * Enabled aggressive block sorting
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("mmchangelog")) return true;
        if (!(sender instanceof Player)) {
            sender.sendMessage(Errors.NOT_A_PLAYER);
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("MyMenu.admin.update")) {
            player.sendMessage(Errors.NO_PERMISSION);
            return true;
        }
        if (args.length <= 0) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG);
            return true;
        }
        if (args[0].equalsIgnoreCase("0.9.0")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_0_9_0);
            return true;
        }
        if (args[0].equalsIgnoreCase("1.0.0")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_1_0_0);
            return true;
        }
        if (args[0].equalsIgnoreCase("1.0.1")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_1_0_1);
            return true;
        }
        if (args[0].equalsIgnoreCase("1.0.2")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_1_0_2);
            return true;
        }
        if (args[0].equalsIgnoreCase("1.0.3")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_1_0_3);
            return true;
        }
        if (args[0].equalsIgnoreCase("1.0.4")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_1_0_4);
            return true;
        }
        if (args[0].equalsIgnoreCase("latest")) {
            InfoMenus.PrintMenu(player, InfoMenus.UPDATE_CHANGELOG_LATEST);
            return true;
        }
        player.sendMessage(Errors.INVALID_VERSION);
        return true;
    }
}

