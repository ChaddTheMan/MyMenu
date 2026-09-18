/*
 * Decompiled with CFR 0.152.
 */
package me.ChaddTheMan.MyMenu.Tools;

import me.ChaddTheMan.MyMenu.Tools.Messages;

public class Errors {
    public static final String NO_PERMISSION = String.valueOf(Messages.PREFIX) + "You do not have permission to run this command!";
    public static final String INVALID_SUBCOMMAND = String.valueOf(Messages.PREFIX) + "You entered an invalid subcommand!";
    public static final String NOT_ENOUGH_ARGS = String.valueOf(Messages.PREFIX) + "You must enter required fields!";
    public static final String NOT_A_PLAYER = String.valueOf(Messages.PREFIX) + "You must be a player to execute this command!";
    public static final String INVALID_HELP = String.valueOf(Messages.PREFIX) + "The specified command does not exist.";
    public static final String NO_HELP = String.valueOf(Messages.PREFIX) + "You must specify a command.";
    public static final String INVALID_MATERIAL = String.valueOf(Messages.PREFIX) + "Invalid material!";
    public static final String INVALID_MENU = String.valueOf(Messages.PREFIX) + "Invalid menu!";
    public static final String COMMAND_NOT_SET = String.valueOf(Messages.PREFIX) + "Command has not been set for this item.";
    public static final String ALREADY_USING_MENU = String.valueOf(Messages.PREFIX) + "You are already using/editing a menu!";
    public static final String INVALID_ARGS = String.valueOf(Messages.PREFIX) + "You entered invalid arguments.";
    public static final String NO_ITEM_IN_HAND = String.valueOf(Messages.PREFIX) + "You do not have an item in your hand!";
    public static final String MENU_ALREADY_EXISTS = String.valueOf(Messages.PREFIX) + "Menu already exists! Try /mmedit instead!";
    public static final String CONVERSATION_ABANDONED = String.valueOf(Messages.PREFIX) + "An unexpected error occurred! Please re add the item to the menu using /mmedit [menu]";
    public static final String INVENTORY_ERROR_OCCURRED = String.valueOf(Messages.PREFIX) + "An error occurred. Inventory closed to prevent further errors. (Maybe a server reload?)";
    public static final String MENU_LENGTH_ERROR = String.valueOf(Messages.PREFIX) + "The length of the menu name was too long! Name cannot be longer than 32 characters.";
    public static final String TOO_MANY_ROWS = String.valueOf(Messages.PREFIX) + "You entered too many rows! Rows cannot be greater than 6.";
    public static final String INVALID_VERSION = String.valueOf(Messages.PREFIX) + "The version specified is not found in the changelog!";
    public static final String PLAYER_NOT_FOUND = String.valueOf(Messages.PREFIX) + "The specified player is not online!";
}

