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
 *  org.bukkit.inventory.meta.ItemMeta
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MyMenuName
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmname")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.item.name")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(Errors.NOT_ENOUGH_ARGS);
                return true;
            }
            if (player.getItemInHand().getType() == Material.AIR) {
                player.sendMessage(Errors.NO_ITEM_IN_HAND);
                return true;
            }
            ItemStack item = player.getItemInHand();
            ItemMeta itemMeta = item.getItemMeta();
            String itemName = args[0];
            if (args.length > 1) {
                int i = 1;
                while (i < args.length) {
                    itemName = String.valueOf(itemName) + " " + args[i];
                    ++i;
                }
            }
            itemName = ChatColor.translateAlternateColorCodes((char)'&', (String)itemName);
            itemMeta.setDisplayName(itemName);
            item.setItemMeta(itemMeta);
            player.sendMessage(String.valueOf(Messages.PREFIX) + "Item name set to: " + itemName);
        }
        return true;
    }
}

