/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.plugin.Plugin
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import me.ChaddTheMan.MyMenu.Updater.Updater;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class MyMenuUpdate
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("update")) {
            if (!sender.hasPermission("MyMenu.admin.update")) {
                sender.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0) {
                return true;
            }
            if (args.length > 1) {
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("mymenu")) {
                if (MyMenu.update) {
                    sender.sendMessage(String.valueOf(Messages.PREFIX) + "Updating MyMenu...");
                    MyMenu.updater = new Updater((Plugin)MyMenu.getInstance(), 86249, MyMenu.file, Updater.UpdateType.NO_VERSION_CHECK, true);
                    sender.sendMessage(String.valueOf(Messages.PREFIX) + "Notice: " + ChatColor.WHITE + "You must reload the server for the update to take effect");
                    sender.sendMessage(String.valueOf(Messages.PREFIX) + "After the reload. Type " + ChatColor.AQUA + "/mmchangelog [New-Version] " + ChatColor.WHITE + "to view the changes!");
                } else {
                    sender.sendMessage(String.valueOf(Messages.PREFIX) + "No update available at this time!");
                }
            }
        }
        return true;
    }
}

