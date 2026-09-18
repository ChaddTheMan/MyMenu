/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package me.ChaddTheMan.MyMenu.Commands;

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfig;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class MyMenuDelete
implements CommandExecutor {
    private int menuIndex;
    private MyMenuConfig configFile = MyMenu.menuConfigFile;
    private FileConfiguration config = this.configFile.getConfig();

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mmdelete")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Errors.NOT_A_PLAYER);
                return true;
            }
            Player player = (Player)sender;
            if (!player.hasPermission("MyMenu.admin.menu.delete")) {
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
            MyMenuMenu.removeMenu(args[0]);
            MyMenu.updateToConfig();
            player.sendMessage(String.valueOf(Messages.PREFIX) + "Menu: " + ChatColor.AQUA + args[0] + ChatColor.WHITE + " has been deleted");
            this.configFile.reload();
            this.config.set("menus." + args[0], null);
            this.configFile.saveConfig();
        }
        return true;
    }
}

