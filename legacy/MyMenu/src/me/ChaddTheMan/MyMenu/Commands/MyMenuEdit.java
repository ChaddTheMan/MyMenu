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

import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuEdit
implements CommandExecutor {
    private Player player;
    private int menuIndex;
    private int playerIndex;
    private MyMenuPlayer mmPlayer;

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmedit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            this.player = (Player)sender;
            if (!this.player.hasPermission("MyMenu.admin.menu.edit")) {
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
            this.menuIndex = MyMenuMenu.getMenuIndex(args[0]);
            if (this.menuIndex == -1) {
                this.player.sendMessage(Errors.INVALID_MENU);
                return true;
            }
            this.mmPlayer = new MyMenuPlayer(this.player);
            this.player.closeInventory();
            this.mmPlayer.editMenu(args[0]);
        }
        return true;
    }
}

