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

import me.ChaddTheMan.MyMenu.Tools.Errors;
import me.ChaddTheMan.MyMenu.Tools.InfoMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MyMenuHelp
implements CommandExecutor {
    /*
     * Enabled aggressive block sorting
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("mmhelp")) return true;
        if (!(sender instanceof Player)) {
            sender.sendMessage(Errors.NOT_A_PLAYER);
            return true;
        }
        Player player = (Player)sender;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("admin")) {
                if (!player.hasPermission("MyMenu.help.admin")) {
                    player.sendMessage(Errors.NO_PERMISSION);
                    return true;
                }
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_MAIN);
                return true;
            }
            if (args[0].equalsIgnoreCase("player")) {
                if (!player.hasPermission("MyMenu.help.player")) {
                    player.sendMessage(Errors.NO_PERMISSION);
                    return true;
                }
                InfoMenus.PrintMenu(player, InfoMenus.PLAYER_HELP);
                return true;
            }
            if (!args[0].equalsIgnoreCase("command")) {
                sender.sendMessage(Errors.INVALID_SUBCOMMAND);
                return true;
            }
            if (!player.hasPermission("MyMenu.help.admin")) {
                player.sendMessage(Errors.NO_PERMISSION);
                return true;
            }
            if (args.length <= 1) {
                player.sendMessage(Errors.NO_HELP);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmlist")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_LIST);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmopen")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_OPEN);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmedit")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_EDIT);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmcreate")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_CREATE);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmdelete")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_DELETE);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmset")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_SET);
                return true;
            }
            if (args[1].equalsIgnoreCase("mminfo")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_INFO);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmreload")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_RELOAD);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmsave")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_SAVE);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmname")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_ITEM_NAME);
                return true;
            }
            if (args[1].equalsIgnoreCase("mmchangelog")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_CHANGELOG);
                return true;
            }
            if (args[1].equalsIgnoreCase("update")) {
                InfoMenus.PrintMenu(player, InfoMenus.ADMIN_HELP_UPDATE);
                return true;
            }
            player.sendMessage(Errors.INVALID_HELP);
            return true;
        }
        if (!player.hasPermission("MyMenu.help")) {
            player.sendMessage(Errors.NO_PERMISSION);
            return true;
        }
        InfoMenus.PrintMenu(player, InfoMenus.GENERAL_HELP);
        return true;
    }
}

