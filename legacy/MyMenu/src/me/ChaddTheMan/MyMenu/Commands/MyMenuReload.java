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

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuReload
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmreload")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.reload")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            MyMenu.updateFromConfig();
            player.sendMessage(String.valueOf(Messages.PREFIX) + "Menus reloaded from config!");
        }
        return true;
    }
}

