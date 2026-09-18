/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuOpen
implements CommandExecutor {
    private int menuIndex;
    private int playerIndex;
    Player player;
    MyMenuPlayer mmPlayer;
    MyMenuPlayer user;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmopen")) {
            if (!sender.hasPermission("MyMenu.admin.menu.open")) {
                sender.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(Errors.NOT_ENOUGH_ARGS);
                return true;
            }
            this.menuIndex = MyMenuMenu.getMenuIndex(args[0]);
            if (this.menuIndex == -1) {
                sender.sendMessage(Errors.INVALID_MENU);
                return true;
            }
            if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Errors.NOT_A_PLAYER);
                    return true;
                }
                this.player = (Player)sender;
                this.playerIndex = MyMenuPlayer.getPlayerIndex(this.player.getName());
                if (this.playerIndex != -1) {
                    this.player.sendMessage(Errors.ALREADY_USING_MENU);
                    return true;
                }
                this.mmPlayer = new MyMenuPlayer(this.player);
                this.player.closeInventory();
                this.mmPlayer.useMenu(args[0]);
                return true;
            }
            if (args.length > 1) {
                if (!sender.hasPermission("MyMenu.admin.menu.open.other")) {
                    sender.sendMessage(Errors.NO_PERMISSION);
                    return true;
                }
                Player otherPlayer = Bukkit.getServer().getPlayer(args[1]);
                if (otherPlayer == null) {
                    sender.sendMessage(Errors.PLAYER_NOT_FOUND);
                    return true;
                }
                this.playerIndex = MyMenuPlayer.getPlayerIndex(otherPlayer.getName());
                if (this.playerIndex != -1) {
                    MyMenuPlayer.getPlayerByName(otherPlayer.getName()).closeMenu();
                }
                this.mmPlayer = new MyMenuPlayer(otherPlayer);
                otherPlayer.closeInventory();
                this.mmPlayer.useMenu(args[0]);
                return true;
            }
        }
        return true;
    }
}

