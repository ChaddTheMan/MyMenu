/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuCreate
implements CommandExecutor {
    private Player player;
    private MyMenuMenu menu;
    private int playerIndex;
    private MyMenuPlayer mmPlayer;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmcreate")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            this.player = (Player)sender;
            if (!this.player.hasPermission("MyMenu.admin.menu.create")) {
                this.player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0) {
                this.player.sendMessage(Errors.NOT_ENOUGH_ARGS);
                return true;
            }
            this.playerIndex = MyMenuPlayer.getPlayerIndex(this.player.getName());
            if (this.playerIndex != -1) {
                this.player.sendMessage(Errors.ALREADY_USING_MENU);
                return true;
            }
            if (args.length == 1) {
                if (args[0].length() > 32) {
                    this.player.sendMessage(Errors.MENU_LENGTH_ERROR);
                    return true;
                }
                if (MyMenuMenu.getMenu(args[0]) != null) {
                    this.player.sendMessage(Errors.MENU_ALREADY_EXISTS);
                    return true;
                }
                this.menu = new MyMenuMenu(args[0]);
                this.menu.setInventoryName(this.menu.getName());
                this.menu.setRows(6);
            } else if (args.length > 1) {
                if (args[0].length() > 32) {
                    this.player.sendMessage(Errors.MENU_LENGTH_ERROR);
                    return true;
                }
                if (MyMenuMenu.getMenu(args[0]) != null) {
                    this.player.sendMessage(Errors.MENU_ALREADY_EXISTS);
                    return true;
                }
                boolean isNumber = true;
                int rows = 6;
                try {
                    rows = Integer.valueOf(args[1]);
                }
                catch (NumberFormatException e) {
                    isNumber = false;
                }
                if (rows > 6) {
                    this.player.sendMessage(Errors.TOO_MANY_ROWS);
                    return true;
                }
                if (!isNumber) {
                    String inventoryName = args[1];
                    if (args.length > 2) {
                        int i = 2;
                        while (i < args.length) {
                            inventoryName = String.valueOf(inventoryName) + " " + args[i];
                            ++i;
                        }
                    }
                    if (inventoryName.length() > 32) {
                        this.player.sendMessage(Errors.MENU_LENGTH_ERROR);
                        return true;
                    }
                    this.menu = new MyMenuMenu(args[0]);
                    this.menu.setInventoryName(ChatColor.translateAlternateColorCodes((char)'&', (String)inventoryName));
                    this.menu.setRows(6);
                } else {
                    if (args.length > 2) {
                        String inventoryName = args[2];
                        if (args.length > 3) {
                            int i = 3;
                            while (i < args.length) {
                                inventoryName = String.valueOf(inventoryName) + " " + args[i];
                                ++i;
                            }
                        }
                        if (inventoryName.length() > 32) {
                            this.player.sendMessage(Errors.MENU_LENGTH_ERROR);
                            return true;
                        }
                        this.menu = new MyMenuMenu(args[0]);
                        this.menu.setInventoryName(ChatColor.translateAlternateColorCodes((char)'&', (String)inventoryName));
                    } else {
                        this.menu = new MyMenuMenu(args[0]);
                        this.menu.setInventoryName(this.menu.getName());
                    }
                    this.menu.setRows(rows);
                }
            }
            this.menu.setAuthor(this.player.getName());
            this.menu.setAuthorUUID(this.player.getUniqueId());
            this.menu.setInventory(Bukkit.createInventory(null, (int)this.menu.getSize(), (String)this.menu.getInventoryName()));
            MyMenu.updateToConfig();
            this.playerIndex = MyMenuPlayer.getPlayerIndex(this.player.getName());
            if (this.playerIndex != -1) {
                this.player.sendMessage(Errors.ALREADY_USING_MENU);
                return true;
            }
            this.mmPlayer = new MyMenuPlayer(this.player);
            this.player.closeInventory();
            this.mmPlayer.editMenu(args[0]);
        }
        return true;
    }
}

