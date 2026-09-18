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

public class MyMenuInfo
implements CommandExecutor {
    private int menuIndex;
    private MyMenuMenu menu;
    private String menuName;
    private String menuInvName;
    private String menuAuthor;
    private String menuAuthorUUID;
    private int menuRows;
    private int menuSize;
    private int menuItemAmount;
    private String menuBoundItem;
    private String menuBoundItemName;
    private boolean menuIsBound;
    private boolean menuIsSpecific;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mminfo")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.menu.info")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(Errors.NOT_ENOUGH_ARGS);
                return true;
            }
            this.menuIndex = MyMenuMenu.getMenuIndex(args[0]);
            if (this.menuIndex == -1) {
                player.sendMessage(Errors.INVALID_MENU);
                return true;
            }
            this.menu = MyMenuMenu.getMenuList().get(this.menuIndex);
            this.menuName = this.menu.getName();
            this.menuInvName = this.menu.getInventoryName();
            this.menuAuthor = this.menu.getAuthor() != null ? this.menu.getAuthor() : "Author not available";
            this.menuAuthorUUID = this.menu.getAuthorUUID() != null ? this.menu.getAuthorUUID().toString() : "Author UUID not available";
            this.menuRows = this.menu.getRows();
            this.menuSize = this.menu.getSize();
            this.menuItemAmount = this.menu.getMenuItems() != null ? this.menu.getMenuItems().size() : 0;
            this.menuIsBound = this.menu.isBound();
            this.menuIsSpecific = this.menu.isSpecific();
            this.menuBoundItem = this.menuIsBound ? this.menu.getBoundItem().getType().toString() : "Not bound";
            this.menuBoundItemName = this.menuIsSpecific ? this.menu.getBoundItemName() : "Not name specific";
            player.sendMessage(Messages.MENU_HEADER);
            player.sendMessage(ChatColor.AQUA + "Name: " + ChatColor.WHITE + this.menuName);
            player.sendMessage(ChatColor.AQUA + "Inventory-Name: " + ChatColor.WHITE + ChatColor.translateAlternateColorCodes((char)'$', (String)this.menuInvName));
            player.sendMessage(ChatColor.AQUA + "Author: " + ChatColor.WHITE + this.menuAuthor);
            player.sendMessage(ChatColor.AQUA + "AuthorUUID: " + ChatColor.WHITE + this.menuAuthorUUID);
            player.sendMessage(ChatColor.AQUA + "Rows: " + ChatColor.WHITE + this.menuRows);
            player.sendMessage(ChatColor.AQUA + "Size: " + ChatColor.WHITE + this.menuSize);
            player.sendMessage(ChatColor.AQUA + "Items: " + ChatColor.WHITE + this.menuItemAmount);
            player.sendMessage(ChatColor.AQUA + "Is bound to item: " + ChatColor.WHITE + this.menuIsBound);
            player.sendMessage(ChatColor.AQUA + "Item: " + ChatColor.WHITE + this.menuBoundItem);
            player.sendMessage(ChatColor.AQUA + "Is bound item name specific: " + ChatColor.WHITE + this.menuIsSpecific);
            player.sendMessage(ChatColor.AQUA + "Item name: " + ChatColor.WHITE + this.menuBoundItemName);
            player.sendMessage(Messages.MENU_FOOTER);
        }
        return true;
    }
}

