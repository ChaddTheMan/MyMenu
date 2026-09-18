/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 */
package me.ChaddTheMan.MyMenu.Tools;

import org.bukkit.ChatColor;

public class Messages {
    public static final String NAME = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + " MyMenu " + ChatColor.DARK_GRAY + "]" + ChatColor.WHITE;
    public static final String PREFIX = String.valueOf(NAME) + ChatColor.DARK_GRAY + ": " + ChatColor.WHITE;
    public static final String MENU_HEADER = ChatColor.DARK_GRAY + "[]====================" + NAME + ChatColor.DARK_GRAY + "====================[]" + ChatColor.WHITE;
    public static final String MENU_FOOTER = ChatColor.DARK_GRAY + "[]=================================================[]" + ChatColor.WHITE;
    public static final String NULL_JAVAPLUGIN = "JAVAPLUGIN CANNOT BE NULL";
    public static final String NULL_FILENAME = "FILENAME CANNOT BE NULL";
    public static final String NULL_FILEPATH = "FILEPATH CANNOT BE NULL";
    public static final String DEBUG_PREFIX = "[MyMenu] [Debug] ";
    public static final String DEBUG_NO_PERMISSION = "Player does not have permission to run the command.";
    public static final String DEBUG_NO_ITEM_IN_HAND = "Player does not have an item in their hand";
    public static final String DEBUG_PERMISSION = "Player has permission to run the command.";
    public static final String DEBUG_EXECUTED = " command executed.";
    public static final String DEBUG_SUBCOMMAND_EXECUTED = " subcommand executed.";
    public static final String DEBUG_RUNNING = "Running command: ";
    public static final String DEBUG_NOT_A_PLAYER = "The sender is not a player.";
    public static final String DEBUG_INVALID_SUBCOMMAND = "Invalid subcommand entered: ";
    public static final String DEBUG_GETTING_COMMAND = "Getting Command: ";
    public static final String DEBUG_INVALID_HELP_COMMAND = "The specified command does not exist.";
    public static final String DEBUG_NO_HELP_COMMAND = "A command was not specified.";
    public static final String DEBUG_NOT_ENOUGH_ARGS = "Not enough arguments were specified.";
    public static final String DEBUG_INVALID_MATERIAL = "Invalid material entered";
    public static final String DEBUG_INVALID_MENU = "Invalid menu entered";
    public static final String DEBUG_ILLEGAL_ARGS = "Invalid arguments entered";
    public static final String DEBUG_REGISTERING_EVENT = "Registering Event: ";
}

