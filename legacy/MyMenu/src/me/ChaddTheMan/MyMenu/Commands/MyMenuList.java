/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuList
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmlist")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.menu.list")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            sender.sendMessage(Messages.MENU_HEADER);
            sender.sendMessage(ChatColor.AQUA + "Available Menus: ");
            sender.sendMessage(ChatColor.WHITE + "White: Unbound " + ChatColor.BLUE + "Blue: Bound" + ChatColor.RED + " Red: Bound and name-specific");
            if (MyMenuMenu.getMenuList().isEmpty()) {
                sender.sendMessage(ChatColor.DARK_RED + "No menus saved!");
            } else {
                int i = 0;
                while (i < MyMenuMenu.getMenuList().size()) {
                    MyMenuMenu menu = MyMenuMenu.getMenuList().get(i);
                    int number = i + 1;
                    if (menu.isBound() & menu.isSpecific()) {
                        sender.sendMessage(ChatColor.WHITE + " " + number + ". " + ChatColor.RED + menu.getName());
                    } else if (menu.isBound()) {
                        sender.sendMessage(ChatColor.WHITE + " " + number + ". " + ChatColor.BLUE + menu.getName());
                    } else {
                        sender.sendMessage(ChatColor.WHITE + " " + number + ". " + menu.getName());
                    }
                    ++i;
                }
            }
            sender.sendMessage(Messages.MENU_FOOTER);
        }
        return true;
    }
}

