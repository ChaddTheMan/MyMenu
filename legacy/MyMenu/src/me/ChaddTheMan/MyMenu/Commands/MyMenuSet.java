/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MyMenuSet
implements CommandExecutor {
    private String boundItemName;
    private int index;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmset")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.menu.set")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0 || args.length == 1) {
                player.sendMessage(Errors.NOT_ENOUGH_ARGS);
                return true;
            }
            if (args.length == 2) {
                this.index = MyMenuMenu.getMenuIndex(args[0]);
                if (this.index == -1) {
                    player.sendMessage(Errors.INVALID_MENU);
                    return true;
                }
                MyMenuMenu menu = MyMenuMenu.getMenuList().get(this.index);
                if (args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("null")) {
                    if (!player.hasPermission("MyMenu.admin.menu.unset")) {
                        player.sendMessage(Errors.NO_PERMISSION);
                        return true;
                    }
                    menu.setBound(false);
                    menu.setBoundItem(null);
                    menu.setSpecific(false);
                    menu.setBoundItemName(null);
                    MyMenuMenu.setMenu(this.index, menu);
                    MyMenu.updateToConfig();
                    player.sendMessage(String.valueOf(Messages.PREFIX) + ChatColor.WHITE + "Menu: " + ChatColor.AQUA + args[0] + ChatColor.WHITE + " has been unbound.");
                    return true;
                }
                if (Material.getMaterial((String)args[1].toUpperCase()) == null) {
                    player.sendMessage(Errors.INVALID_MATERIAL);
                    return true;
                }
                menu.setBound(true);
                menu.setBoundItem(new ItemStack(Material.getMaterial((String)args[1].toUpperCase())));
                menu.setSpecific(false);
                menu.setBoundItemName(null);
                MyMenuMenu.setMenu(this.index, menu);
                MyMenu.updateToConfig();
                player.sendMessage(String.valueOf(Messages.PREFIX) + ChatColor.WHITE + "Menu: " + ChatColor.AQUA + args[0] + ChatColor.WHITE + " Is now bound to item: " + ChatColor.AQUA + args[1]);
            } else {
                this.index = MyMenuMenu.getMenuIndex(args[0]);
                if (this.index == -1) {
                    player.sendMessage(Errors.INVALID_MENU);
                    return true;
                }
                MyMenuMenu menu = MyMenuMenu.getMenuList().get(this.index);
                if (args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("null")) {
                    if (!player.hasPermission("MyMenu.admin.menu.unset")) {
                        player.sendMessage(Errors.NO_PERMISSION);
                        return true;
                    }
                    menu.setBound(false);
                    menu.setBoundItem(null);
                    menu.setSpecific(false);
                    menu.setBoundItemName(null);
                    MyMenuMenu.setMenu(this.index, menu);
                    MyMenu.updateToConfig();
                    player.sendMessage(String.valueOf(Messages.PREFIX) + ChatColor.WHITE + "Menu: " + ChatColor.AQUA + args[0] + ChatColor.WHITE + " has been unbound.");
                    return true;
                }
                if (Material.getMaterial((String)args[1].toUpperCase()) == null) {
                    player.sendMessage(Errors.INVALID_MATERIAL);
                    return true;
                }
                this.boundItemName = args[2];
                if (args.length > 3) {
                    int i = 3;
                    while (i < args.length) {
                        this.boundItemName = String.valueOf(this.boundItemName) + " " + args[i];
                        ++i;
                    }
                }
                menu.setBound(true);
                menu.setBoundItem(new ItemStack(Material.getMaterial((String)args[1].toUpperCase())));
                menu.setSpecific(true);
                menu.setBoundItemName(this.boundItemName);
                MyMenuMenu.setMenu(this.index, menu);
                MyMenu.updateToConfig();
                player.sendMessage(String.valueOf(Messages.PREFIX) + ChatColor.WHITE + "Menu: " + ChatColor.AQUA + args[0] + ChatColor.WHITE + " Is now bound to item: " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " When named: " + ChatColor.translateAlternateColorCodes((char)'&', (String)this.boundItemName));
            }
        }
        return true;
    }
}

