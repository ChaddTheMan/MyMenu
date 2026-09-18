/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.conversations.Conversation
 *  org.bukkit.conversations.ConversationAbandonedEvent
 *  org.bukkit.conversations.ConversationAbandonedListener
 *  org.bukkit.conversations.ConversationContext
 *  org.bukkit.conversations.ConversationPrefix
 *  org.bukkit.conversations.MessagePrompt
 *  org.bukkit.conversations.NumericPrompt
 *  org.bukkit.conversations.Prompt
 *  org.bukkit.conversations.ValidatingPrompt
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItem;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.MessagePrompt;
import org.bukkit.conversations.NumericPrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class MyMenuItemConversation {
    private static LinkedList<Conversation> conversationQueue = new LinkedList();
    private static String prefix = Messages.PREFIX;
    private static MyMenuPlayer player;
    private static Player eventPlayer;
    private static MyMenuMenu menu;
    private static int menuIndex;
    private static int slot;
    private static List<String> itemItemLore;
    private static int atLoreLength;
    private static ArrayList<String> commands;
    private static int atCommandLength;
    private static HashMap<String, ItemStack> materialMap;

    static {
        itemItemLore = null;
        atLoreLength = 1;
        commands = null;
        atCommandLength = 1;
        materialMap = new HashMap();
    }

    public static synchronized Conversation addConversation(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        conversationQueue.addLast(conversation);
        return conversationQueue.getLast();
    }

    public static synchronized void removeLastConversation() {
        conversationQueue.removeFirst();
    }

    public static synchronized void clearConversationQueue() {
        LinkedList<Conversation> queue = conversationQueue;
        conversationQueue = null;
        conversationQueue = new LinkedList();
        for (Conversation conversation : queue) {
            conversation.getForWhom().sendRawMessage(String.valueOf(Messages.PREFIX) + "Conversation was abandoned. Please try again.");
            conversation.abandon();
        }
    }

    public void beginItemConversation(Conversation conversation, int slot, MyMenuPlayer player, MyMenuMenu menu, int menuIndex) {
        if (player == null || menu == null || conversation == null) {
            throw new IllegalArgumentException();
        }
        MyMenuItemConversation.slot = slot;
        MyMenuItemConversation.player = player;
        MyMenuItemConversation.menu = menu;
        MyMenuItemConversation.menuIndex = menuIndex;
        eventPlayer = player.getPlayer();
        player.setInConversation(true);
        MyMenuItemConversation.addConversation(conversation).begin();
    }

    private static void setupMaterialMap() {
        materialMap.put("oak plank", new ItemStack(Material.WOOD));
        materialMap.put("spruce plank", new ItemStack(Material.WOOD, 1, 1));
        materialMap.put("birch plank", new ItemStack(Material.WOOD, 1, 2));
        materialMap.put("jungle plank", new ItemStack(Material.WOOD, 1, 3));
        materialMap.put("acacia plank", new ItemStack(Material.WOOD, 1, 4));
        materialMap.put("dark oak plank", new ItemStack(Material.WOOD, 1, 5));
        materialMap.put("oak sapling", new ItemStack(Material.SAPLING, 1));
        materialMap.put("spruce sapling", new ItemStack(Material.SAPLING, 1, 1));
        materialMap.put("birch sapling", new ItemStack(Material.SAPLING, 1, 2));
        materialMap.put("jungle sapling", new ItemStack(Material.SAPLING, 1, 3));
        materialMap.put("acacia sapling", new ItemStack(Material.SAPLING, 1, 4));
        materialMap.put("dark oak sapling", new ItemStack(Material.SAPLING, 1, 5));
        materialMap.put("oak wood", new ItemStack(Material.LOG, 1));
        materialMap.put("spruce wood", new ItemStack(Material.LOG, 1, 1));
        materialMap.put("birch wood", new ItemStack(Material.LOG, 1, 2));
        materialMap.put("jungle wood", new ItemStack(Material.LOG, 1, 3));
        materialMap.put("oak leaves", new ItemStack(Material.LEAVES, 1));
        materialMap.put("spruce leaves", new ItemStack(Material.LEAVES, 1, 1));
        materialMap.put("birch leaves", new ItemStack(Material.LEAVES, 1, 2));
        materialMap.put("jungle leaves", new ItemStack(Material.LEAVES, 1, 3));
        materialMap.put("chiseled sandstone", new ItemStack(Material.SANDSTONE, 1, 1));
        materialMap.put("smooth sandstone", new ItemStack(Material.SANDSTONE, 1, 2));
        materialMap.put("tallgrass", new ItemStack(Material.LONG_GRASS, 1, 1));
        materialMap.put("fern", new ItemStack(Material.LONG_GRASS, 1, 2));
        materialMap.put("white wool", new ItemStack(Material.WOOL, 1));
        materialMap.put("orange wool", new ItemStack(Material.WOOL, 1, 1));
        materialMap.put("magenta wool", new ItemStack(Material.WOOL, 1, 2));
        materialMap.put("light blue wool", new ItemStack(Material.WOOL, 1, 3));
        materialMap.put("yellow wool", new ItemStack(Material.WOOL, 1, 4));
        materialMap.put("lime wool", new ItemStack(Material.WOOL, 1, 5));
        materialMap.put("pink wool", new ItemStack(Material.WOOL, 1, 6));
        materialMap.put("gray wool", new ItemStack(Material.WOOL, 1, 7));
        materialMap.put("light gray wool", new ItemStack(Material.WOOL, 1, 8));
        materialMap.put("cyan wool", new ItemStack(Material.WOOL, 1, 9));
        materialMap.put("purple wool", new ItemStack(Material.WOOL, 1, 10));
        materialMap.put("blue wool", new ItemStack(Material.WOOL, 1, 11));
        materialMap.put("brown wool", new ItemStack(Material.WOOL, 1, 12));
        materialMap.put("green wool", new ItemStack(Material.WOOL, 1, 13));
        materialMap.put("red wool", new ItemStack(Material.WOOL, 1, 14));
        materialMap.put("black wool", new ItemStack(Material.WOOL, 1, 15));
        materialMap.put("rose", new ItemStack(Material.RED_ROSE, 1));
        materialMap.put("blue orchid", new ItemStack(Material.RED_ROSE, 1, 1));
        materialMap.put("allium", new ItemStack(Material.WOOL, 1, 2));
        materialMap.put("azure bluet", new ItemStack(Material.WOOL, 1, 3));
        materialMap.put("red tulip", new ItemStack(Material.WOOL, 1, 4));
        materialMap.put("orange tulip", new ItemStack(Material.WOOL, 1, 5));
        materialMap.put("white tulip", new ItemStack(Material.WOOL, 1, 6));
        materialMap.put("pink tulip", new ItemStack(Material.WOOL, 1, 7));
        materialMap.put("oxeye daisy", new ItemStack(Material.WOOL, 1, 8));
        materialMap.put("double slab", new ItemStack(Material.DOUBLE_STEP, 1));
        materialMap.put("double sandstone slab", new ItemStack(Material.DOUBLE_STEP, 1, 1));
        materialMap.put("double wooden slab", new ItemStack(Material.DOUBLE_STEP, 1, 2));
        materialMap.put("double cobblestone slab", new ItemStack(Material.DOUBLE_STEP, 1, 3));
        materialMap.put("double brick slab", new ItemStack(Material.DOUBLE_STEP, 1, 4));
        materialMap.put("double stone brick slab", new ItemStack(Material.DOUBLE_STEP, 1, 5));
        materialMap.put("double nether brick slab", new ItemStack(Material.DOUBLE_STEP, 1, 6));
        materialMap.put("double quartz slab", new ItemStack(Material.DOUBLE_STEP, 1, 7));
        materialMap.put("slab", new ItemStack(Material.STEP, 1));
        materialMap.put("sandstone slab", new ItemStack(Material.STEP, 1, 1));
        materialMap.put("wooden slab", new ItemStack(Material.STEP, 1, 2));
        materialMap.put("cobblestone slab", new ItemStack(Material.STEP, 1, 3));
        materialMap.put("brick slab", new ItemStack(Material.STEP, 1, 4));
        materialMap.put("stone brick slab", new ItemStack(Material.STEP, 1, 5));
        materialMap.put("nether brick slab", new ItemStack(Material.STEP, 1, 6));
        materialMap.put("quartz slab", new ItemStack(Material.STEP, 1, 7));
        materialMap.put("white stained glass", new ItemStack(Material.STAINED_GLASS, 1));
        materialMap.put("orange stained glass", new ItemStack(Material.STAINED_GLASS, 1, 1));
        materialMap.put("magenta stained glass", new ItemStack(Material.STAINED_GLASS, 1, 2));
        materialMap.put("light blue stained glass", new ItemStack(Material.STAINED_GLASS, 1, 3));
        materialMap.put("yellow stained glass", new ItemStack(Material.STAINED_GLASS, 1, 4));
        materialMap.put("lime stained glass", new ItemStack(Material.STAINED_GLASS, 1, 5));
        materialMap.put("pink stained glass", new ItemStack(Material.STAINED_GLASS, 1, 6));
        materialMap.put("gray stained glass", new ItemStack(Material.STAINED_GLASS, 1, 7));
        materialMap.put("light gray stained glass", new ItemStack(Material.STAINED_GLASS, 1, 8));
        materialMap.put("cyan stained glass", new ItemStack(Material.STAINED_GLASS, 1, 9));
        materialMap.put("purple stained glass", new ItemStack(Material.STAINED_GLASS, 1, 10));
        materialMap.put("blue stained glass", new ItemStack(Material.STAINED_GLASS, 1, 11));
        materialMap.put("brown stained glass", new ItemStack(Material.STAINED_GLASS, 1, 12));
        materialMap.put("green stained glass", new ItemStack(Material.STAINED_GLASS, 1, 13));
        materialMap.put("red stained glass", new ItemStack(Material.STAINED_GLASS, 1, 14));
        materialMap.put("black stained glass", new ItemStack(Material.STAINED_GLASS, 1, 15));
        materialMap.put("stone monster egg", new ItemStack(Material.MONSTER_EGGS, 1));
        materialMap.put("stone brick monster egg", new ItemStack(Material.MONSTER_EGGS, 1, 1));
        materialMap.put("mossy stone brick monster egg", new ItemStack(Material.MONSTER_EGGS, 1, 3));
        materialMap.put("cracked stone brick monster egg", new ItemStack(Material.MONSTER_EGGS, 1, 4));
        materialMap.put("chiseled stone brick monster egg", new ItemStack(Material.MONSTER_EGGS, 1, 5));
        materialMap.put("stone bricks", new ItemStack(Material.SMOOTH_BRICK, 1));
        materialMap.put("mossy stone bricks", new ItemStack(Material.SMOOTH_BRICK, 1, 1));
        materialMap.put("cracked stone bricks", new ItemStack(Material.SMOOTH_BRICK, 1, 2));
        materialMap.put("chiseled stone bricks", new ItemStack(Material.SMOOTH_BRICK, 1, 3));
        materialMap.put("oak wood slab", new ItemStack(Material.WOOD_STEP, 1));
        materialMap.put("spruce wood slab", new ItemStack(Material.WOOD_STEP, 1, 1));
        materialMap.put("birch wood slab", new ItemStack(Material.WOOD_STEP, 1, 2));
        materialMap.put("jungle wood slab", new ItemStack(Material.WOOD_STEP, 1, 3));
        materialMap.put("acacia wood slab", new ItemStack(Material.WOOD_STEP, 1, 4));
        materialMap.put("dark oak wood slab", new ItemStack(Material.WOOD_STEP, 1, 5));
        materialMap.put("cobblestone wall", new ItemStack(Material.COBBLE_WALL, 1));
        materialMap.put("mossy cobblestone wall", new ItemStack(Material.WOOD_STEP, 1, 1));
        materialMap.put("quartz block", new ItemStack(Material.QUARTZ_BLOCK, 1));
        materialMap.put("chiseled quartz block", new ItemStack(Material.QUARTZ_BLOCK, 1, 1));
        materialMap.put("pillar quartz block", new ItemStack(Material.QUARTZ_BLOCK, 1, 2));
        materialMap.put("white stained clay", new ItemStack(Material.STAINED_CLAY, 1));
        materialMap.put("orange stained clay", new ItemStack(Material.STAINED_CLAY, 1, 1));
        materialMap.put("magenta stained clay", new ItemStack(Material.STAINED_CLAY, 1, 2));
        materialMap.put("light blue stained clay", new ItemStack(Material.STAINED_CLAY, 1, 3));
        materialMap.put("yellow stained clay", new ItemStack(Material.STAINED_CLAY, 1, 4));
        materialMap.put("lime stained clay", new ItemStack(Material.STAINED_CLAY, 1, 5));
        materialMap.put("pink stained clay", new ItemStack(Material.STAINED_CLAY, 1, 6));
        materialMap.put("gray stained clay", new ItemStack(Material.STAINED_CLAY, 1, 7));
        materialMap.put("light gray stained clay", new ItemStack(Material.STAINED_CLAY, 1, 8));
        materialMap.put("cyan stained clay", new ItemStack(Material.STAINED_CLAY, 1, 9));
        materialMap.put("purple stained clay", new ItemStack(Material.STAINED_CLAY, 1, 10));
        materialMap.put("blue stained clay", new ItemStack(Material.STAINED_CLAY, 1, 11));
        materialMap.put("brown stained clay", new ItemStack(Material.STAINED_CLAY, 1, 12));
        materialMap.put("green stained clay", new ItemStack(Material.STAINED_CLAY, 1, 13));
        materialMap.put("red stained clay", new ItemStack(Material.STAINED_CLAY, 1, 14));
        materialMap.put("black stained clay", new ItemStack(Material.STAINED_CLAY, 1, 15));
        materialMap.put("white stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1));
        materialMap.put("orange stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 1));
        materialMap.put("magenta stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 2));
        materialMap.put("light blue stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 3));
        materialMap.put("yellow stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 4));
        materialMap.put("lime stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 5));
        materialMap.put("pink stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 6));
        materialMap.put("gray stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 7));
        materialMap.put("light gray stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 8));
        materialMap.put("cyan stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 9));
        materialMap.put("purple stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 10));
        materialMap.put("blue stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 11));
        materialMap.put("brown stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 12));
        materialMap.put("green stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 13));
        materialMap.put("red stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 14));
        materialMap.put("black stained glass pane", new ItemStack(Material.STAINED_GLASS_PANE, 1, 15));
        materialMap.put("acacia leaves", new ItemStack(Material.LEAVES_2, 1));
        materialMap.put("dark oak leaves", new ItemStack(Material.LEAVES_2, 1, 1));
        materialMap.put("acacia wood", new ItemStack(Material.LOG_2, 1));
        materialMap.put("dark oak wood", new ItemStack(Material.LOG_2, 1, 1));
        materialMap.put("white carpet", new ItemStack(Material.CARPET, 1));
        materialMap.put("orange carpet", new ItemStack(Material.CARPET, 1, 1));
        materialMap.put("magenta carpet", new ItemStack(Material.CARPET, 1, 2));
        materialMap.put("light blue carpet", new ItemStack(Material.CARPET, 1, 3));
        materialMap.put("yellow carpet", new ItemStack(Material.CARPET, 1, 4));
        materialMap.put("lime carpet", new ItemStack(Material.CARPET, 1, 5));
        materialMap.put("pink carpet", new ItemStack(Material.CARPET, 1, 6));
        materialMap.put("gray carpet", new ItemStack(Material.CARPET, 1, 7));
        materialMap.put("light gray carpet", new ItemStack(Material.CARPET, 1, 8));
        materialMap.put("cyan carpet", new ItemStack(Material.CARPET, 1, 9));
        materialMap.put("purple carpet", new ItemStack(Material.CARPET, 1, 10));
        materialMap.put("blue carpet", new ItemStack(Material.CARPET, 1, 11));
        materialMap.put("brown carpet", new ItemStack(Material.CARPET, 1, 12));
        materialMap.put("green carpet", new ItemStack(Material.CARPET, 1, 13));
        materialMap.put("red carpet", new ItemStack(Material.CARPET, 1, 14));
        materialMap.put("black carpet", new ItemStack(Material.CARPET, 1, 15));
        materialMap.put("sunflower", new ItemStack(Material.DOUBLE_PLANT, 1));
        materialMap.put("lilac", new ItemStack(Material.DOUBLE_PLANT, 1, 1));
        materialMap.put("double tallgrass", new ItemStack(Material.DOUBLE_PLANT, 1, 2));
        materialMap.put("large fern", new ItemStack(Material.DOUBLE_PLANT, 1, 3));
        materialMap.put("rose bush", new ItemStack(Material.DOUBLE_PLANT, 1, 4));
        materialMap.put("peony", new ItemStack(Material.DOUBLE_PLANT, 1, 5));
        materialMap.put("charcoal", new ItemStack(Material.COAL, 1, 1));
        materialMap.put("enchanted golden apple", new ItemStack(Material.GOLDEN_APPLE, 1, 1));
        materialMap.put("rose red", new ItemStack(Material.INK_SACK, 1, 1));
        materialMap.put("cactus green", new ItemStack(Material.INK_SACK, 1, 2));
        materialMap.put("coco beans", new ItemStack(Material.INK_SACK, 1, 3));
        materialMap.put("lapis lazuli", new ItemStack(Material.INK_SACK, 1, 4));
        materialMap.put("purple dye", new ItemStack(Material.INK_SACK, 1, 5));
        materialMap.put("cyan dye", new ItemStack(Material.INK_SACK, 1, 6));
        materialMap.put("light gray dye", new ItemStack(Material.INK_SACK, 1, 7));
        materialMap.put("gray dye", new ItemStack(Material.INK_SACK, 1, 8));
        materialMap.put("pink dye", new ItemStack(Material.INK_SACK, 1, 9));
        materialMap.put("lime dye", new ItemStack(Material.INK_SACK, 1, 10));
        materialMap.put("dandelion yellow", new ItemStack(Material.INK_SACK, 1, 11));
        materialMap.put("light blue dye", new ItemStack(Material.INK_SACK, 1, 12));
        materialMap.put("magenta dye", new ItemStack(Material.INK_SACK, 1, 13));
        materialMap.put("orange dye", new ItemStack(Material.INK_SACK, 1, 14));
        materialMap.put("bone meal", new ItemStack(Material.INK_SACK, 1, 15));
        materialMap.put("creeper egg", new ItemStack(Material.MONSTER_EGG, 1, 50));
        materialMap.put("skeleton egg", new ItemStack(Material.MONSTER_EGG, 1, 51));
        materialMap.put("spider egg", new ItemStack(Material.MONSTER_EGG, 1, 52));
        materialMap.put("zombie egg", new ItemStack(Material.MONSTER_EGG, 1, 54));
        materialMap.put("slime egg", new ItemStack(Material.MONSTER_EGG, 1, 55));
        materialMap.put("ghast egg", new ItemStack(Material.MONSTER_EGG, 1, 56));
        materialMap.put("pigman egg", new ItemStack(Material.MONSTER_EGG, 1, 57));
        materialMap.put("enderman egg", new ItemStack(Material.MONSTER_EGG, 1, 58));
        materialMap.put("cave spider egg", new ItemStack(Material.MONSTER_EGG, 1, 59));
        materialMap.put("silverfish egg", new ItemStack(Material.MONSTER_EGG, 1, 60));
        materialMap.put("blaze egg", new ItemStack(Material.MONSTER_EGG, 1, 61));
        materialMap.put("magma cube egg", new ItemStack(Material.MONSTER_EGG, 1, 62));
        materialMap.put("bat egg", new ItemStack(Material.MONSTER_EGG, 1, 65));
        materialMap.put("witch egg", new ItemStack(Material.MONSTER_EGG, 1, 66));
        materialMap.put("endermite egg", new ItemStack(Material.MONSTER_EGG, 1, 67));
        materialMap.put("guardian egg", new ItemStack(Material.MONSTER_EGG, 1, 68));
        materialMap.put("pig egg", new ItemStack(Material.MONSTER_EGG, 1, 90));
        materialMap.put("sheep egg", new ItemStack(Material.MONSTER_EGG, 1, 91));
        materialMap.put("cow egg", new ItemStack(Material.MONSTER_EGG, 1, 92));
        materialMap.put("chicken egg", new ItemStack(Material.MONSTER_EGG, 1, 93));
        materialMap.put("squid egg", new ItemStack(Material.MONSTER_EGG, 1, 94));
        materialMap.put("wolf egg", new ItemStack(Material.MONSTER_EGG, 1, 95));
        materialMap.put("mooshroom egg", new ItemStack(Material.MONSTER_EGG, 1, 96));
        materialMap.put("ocelot egg", new ItemStack(Material.MONSTER_EGG, 1, 98));
        materialMap.put("horse egg", new ItemStack(Material.MONSTER_EGG, 1, 100));
        materialMap.put("villager egg", new ItemStack(Material.MONSTER_EGG, 1, 120));
        materialMap.put("skeleton head", new ItemStack(Material.SKULL, 1));
        materialMap.put("wither head", new ItemStack(Material.SKULL, 1, 1));
        materialMap.put("zombie head", new ItemStack(Material.SKULL, 1, 2));
        materialMap.put("human head", new ItemStack(Material.SKULL, 1, 3));
        materialMap.put("mob head", new ItemStack(Material.SKULL, 1, 4));
    }

    public static class CreatingItemPrefix
    implements ConversationPrefix {
        public String getPrefix(ConversationContext context) {
            ItemStack itemMaterial = null;
            String itemMaterialType = null;
            if ((ItemStack)context.getSessionData((Object)"itemMaterial") != null) {
                itemMaterial = (ItemStack)context.getSessionData((Object)"itemMaterial");
                itemMaterialType = itemMaterial.getType().toString();
            }
            int itemAmount = 0;
            try {
                if (context.getSessionData((Object)"itemAmount") != null) {
                    itemAmount = (Integer)context.getSessionData((Object)"itemAmount");
                }
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            ArrayList commands = null;
            if ((ArrayList)context.getSessionData((Object)"commands") != null) {
                commands = (ArrayList)context.getSessionData((Object)"commands");
            }
            String itemName = null;
            if ((String)context.getSessionData((Object)"itemName") != null) {
                itemName = (String)context.getSessionData((Object)"itemName");
            }
            List itemItemLore = null;
            if ((List)context.getSessionData((Object)"itemItemLore") != null) {
                itemItemLore = (List)context.getSessionData((Object)"itemItemLore");
            }
            if (itemMaterialType != null && itemAmount == 0 && commands == null && itemName == null && itemItemLore == null) {
                return String.valueOf(Messages.MENU_HEADER) + "\n" + prefix + "Create New Item: " + ChatColor.AQUA + "\nMaterial: " + ChatColor.WHITE + itemMaterialType + "\n" + Messages.MENU_FOOTER;
            }
            if (itemMaterial != null && itemAmount != 0 && commands == null && itemName == null && itemItemLore == null) {
                return String.valueOf(Messages.MENU_HEADER) + "\n" + prefix + "Set Item: " + ChatColor.AQUA + "\nMaterial: " + ChatColor.WHITE + itemMaterial + ChatColor.AQUA + "\nAmount: " + ChatColor.WHITE + itemAmount + "\n" + Messages.MENU_FOOTER;
            }
            if (itemMaterial != null && itemAmount != 0 && commands != null && itemName == null && itemItemLore == null) {
                return String.valueOf(Messages.MENU_HEADER) + "\n" + prefix + "Set Item: " + ChatColor.AQUA + "\nMaterial: " + ChatColor.WHITE + itemMaterial + ChatColor.AQUA + "\nAmount: " + ChatColor.WHITE + itemAmount + ChatColor.AQUA + "\nCommands: " + ChatColor.WHITE + this.getCommandString(commands) + "\n" + Messages.MENU_FOOTER;
            }
            if (itemMaterial != null && itemAmount != 0 && commands != null && itemName != null && itemItemLore == null) {
                return String.valueOf(Messages.MENU_HEADER) + "\n" + prefix + "Set Item: " + ChatColor.AQUA + "\nMaterial: " + ChatColor.WHITE + itemMaterial + ChatColor.AQUA + "\nAmount: " + ChatColor.WHITE + itemAmount + ChatColor.AQUA + "\nCommands: " + ChatColor.WHITE + this.getCommandString(commands) + ChatColor.AQUA + "\nName: " + ChatColor.translateAlternateColorCodes((char)'&', (String)itemName) + "\n" + Messages.MENU_FOOTER;
            }
            if (itemMaterial != null && itemAmount != 0 && commands != null && itemName != null && itemItemLore != null) {
                return String.valueOf(Messages.MENU_HEADER) + "\n" + prefix + ChatColor.AQUA + "\nMaterial: " + ChatColor.WHITE + itemMaterial + ChatColor.AQUA + "\nAmount: " + ChatColor.WHITE + itemAmount + ChatColor.AQUA + "\nCommand: " + ChatColor.WHITE + this.getCommandString(commands) + ChatColor.AQUA + "\nName: " + ChatColor.translateAlternateColorCodes((char)'&', (String)itemName) + ChatColor.AQUA + "\nItem Lore: " + this.getLoreString(itemItemLore) + "\n" + Messages.MENU_FOOTER;
            }
            return String.valueOf(prefix) + "Create New Item: ";
        }

        private String getCommandString(ArrayList<String> commands) {
            if (commands != null) {
                String commandString = "";
                int i = 0;
                while (i < commands.size()) {
                    commandString = String.valueOf(commandString) + ChatColor.RESET + "\n - " + commands.get(i);
                    ++i;
                }
                if ((commandString = ChatColor.translateAlternateColorCodes((char)'&', (String)commandString)).startsWith("\\")) {
                    commandString = commandString.substring(1);
                }
                if (commandString.startsWith("$")) {
                    commandString = commandString.replaceFirst("$", ChatColor.AQUA + "Console: ");
                }
                return commandString;
            }
            return "No commands for this item.";
        }

        private String getLoreString(List<String> lore) {
            if (lore != null) {
                String loreString = "";
                int i = 0;
                while (i < lore.size()) {
                    loreString = String.valueOf(loreString) + ChatColor.RESET + "\n - " + lore.get(i);
                    ++i;
                }
                loreString = ChatColor.translateAlternateColorCodes((char)'&', (String)loreString);
                return loreString;
            }
            return "No item lore for this item.";
        }
    }

    public static class EndOfConversation
    implements ConversationAbandonedListener {
        public void conversationAbandoned(ConversationAbandonedEvent event) {
            try {
                MyMenuItemConversation.removeLastConversation();
            }
            catch (NoClassDefFoundError noClassDefFoundError) {
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player != null) {
                player = null;
                player = MyMenuPlayer.getPlayerByName(eventPlayer.getName());
                player.setInConversation(false);
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)MyMenu.getInstance(), new Runnable(){

                @Override
                public void run() {
                    player.editMenu(menu.getName());
                }
            }, 5L);
        }
    }

    public static class FinishSettingItem
    extends MessagePrompt {
        public String getPromptText(ConversationContext context) {
            MyMenuMenu menu = (MyMenuMenu)context.getSessionData((Object)"Menu");
            MyMenuItem menuItem = new MyMenuItem(new ItemStack((ItemStack)context.getSessionData((Object)"itemMaterial")));
            ItemStack item = menuItem.getItem();
            ItemMeta itemMeta = item.getItemMeta();
            item.setAmount(((Integer)context.getSessionData((Object)"itemAmount")).intValue());
            int commandLength = 0;
            try {
                commandLength = (Integer)context.getSessionData((Object)"commandLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (commandLength != 0) {
                menuItem.setCommandList((ArrayList)context.getSessionData((Object)"commands"));
            }
            if (!context.getSessionData((Object)"itemName").equals("None")) {
                itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes((char)'&', (String)((String)context.getSessionData((Object)"itemName"))));
            }
            int itemItemLoreLength = 0;
            try {
                itemItemLoreLength = (Integer)context.getSessionData((Object)"itemItemLoreLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (itemItemLoreLength != 0) {
                itemMeta.setLore((List)context.getSessionData((Object)"itemItemLore"));
            }
            if (itemMeta.hasEnchants()) {
                itemMeta.getEnchants().clear();
            }
            item.setItemMeta(itemMeta);
            menu.addMenuItem(slot, menuItem);
            MyMenuMenu.setMenu(menuIndex, menu);
            MyMenu.updateToConfig();
            itemItemLore = null;
            return "\n" + prefix + "Item has been added to menu!";
        }

        protected Prompt getNextPrompt(ConversationContext context) {
            return Prompt.END_OF_CONVERSATION;
        }
    }

    public static class GetItemAmount
    extends NumericPrompt {
        public String getPromptText(ConversationContext context) {
            return "\n\n" + prefix + "Enter item amount: (Between 1 and 64)";
        }

        protected boolean isNumberValid(ConversationContext context, Number number) {
            return number.intValue() > 0 && number.intValue() <= 64;
        }

        protected String getFailedValidationText(ConversationContext context, Number number) {
            return "\n\n" + prefix + ChatColor.DARK_RED + "Number must be between 1 and 64!";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, Number number) {
            context.setSessionData((Object)"itemAmount", (Object)number);
            return new getCommandLength();
        }
    }

    public static class GetLore
    extends ValidatingPrompt {
        public String getPromptText(ConversationContext context) {
            int itemItemLoreLength = 0;
            try {
                itemItemLoreLength = (Integer)context.getSessionData((Object)"itemItemLoreLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (itemItemLore == null) {
                itemItemLore = new ArrayList();
            }
            if (itemItemLoreLength == 0) {
                return "\n\n" + prefix + "Skipping item lore";
            }
            return "\n\n" + prefix + "Enter lore for line " + atLoreLength + ": (Use &[Color/Format] for color.";
        }

        protected boolean isInputValid(ConversationContext context, String string) {
            return string.length() <= 45;
        }

        protected String getFailedValidationText(ConversationContext context, String string) {
            return "\n" + prefix + ChatColor.DARK_RED + "Lore line length is too long! (Must be fewer than 45 characters)";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, String string) {
            int itemItemLoreLength = 0;
            try {
                itemItemLoreLength = (Integer)context.getSessionData((Object)"itemItemLoreLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (itemItemLoreLength == 0) {
                atLoreLength = 1;
                context.setSessionData((Object)"itemItemLore", null);
                return new FinishSettingItem();
            }
            if (atLoreLength == itemItemLoreLength) {
                itemItemLore.add(ChatColor.RESET + ChatColor.translateAlternateColorCodes((char)'&', (String)string));
                context.setSessionData((Object)"itemItemLore", (Object)itemItemLore);
                itemItemLore = null;
                itemItemLore = new ArrayList();
                atLoreLength = 1;
                return new FinishSettingItem();
            }
            itemItemLore.add(ChatColor.RESET + ChatColor.translateAlternateColorCodes((char)'&', (String)string));
            atLoreLength = atLoreLength + 1;
            return new GetLore();
        }
    }

    public static class GetLoreLength
    extends NumericPrompt {
        public String getPromptText(ConversationContext context) {
            return "\n\n" + prefix + "Enter how many lines of Item Lore: (Between 0 and 10)";
        }

        protected boolean isNumberValid(ConversationContext context, Number number) {
            return number.intValue() > -1 && number.intValue() <= 10;
        }

        protected String getFailedValidationText(ConversationContext context, Number number) {
            return "\n" + prefix + ChatColor.DARK_RED + "Number must be between 0 and 10!";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, Number number) {
            context.setSessionData((Object)"itemItemLoreLength", (Object)number);
            if (number.intValue() == 0) {
                return new FinishSettingItem();
            }
            return new GetLore();
        }
    }

    public static class GetMaterialType
    extends ValidatingPrompt {
        ItemStack materialType;

        public String getPromptText(ConversationContext context) {
            MyMenuItemConversation.setupMaterialMap();
            return "\n\n" + prefix + "Enter material type: ";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, String string) {
            this.materialType = materialMap.get(string.toLowerCase()) != null ? (ItemStack)materialMap.get(string.toLowerCase()) : new ItemStack(Material.matchMaterial((String)string.toUpperCase()));
            context.setSessionData((Object)"itemMaterial", (Object)this.materialType);
            return new GetItemAmount();
        }

        protected boolean isInputValid(ConversationContext context, String string) {
            string = string.toLowerCase();
            if (materialMap.containsKey(string)) {
                return true;
            }
            if (Material.matchMaterial((String)string) == null) {
                return false;
            }
            if (Material.matchMaterial((String)string) == Material.AIR) {
                return false;
            }
            return Material.matchMaterial((String)string) != null;
        }

        protected String getFailedValidationText(ConversationContext context, String string) {
            return String.valueOf(prefix) + ChatColor.DARK_RED + "You must enter a valid material";
        }
    }

    public static class getCommandLength
    extends NumericPrompt {
        public String getPromptText(ConversationContext context) {
            return "\n\n" + prefix + "Enter how many commands you would like to run: (Between 0 and 10)";
        }

        protected boolean isNumberValid(ConversationContext context, Number number) {
            return number.intValue() > -1 && number.intValue() <= 10;
        }

        protected String getFailedValidationText(ConversationContext context, Number number) {
            return "\n" + prefix + ChatColor.DARK_RED + "Number must be between 0 and 10";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, Number number) {
            context.setSessionData((Object)"commandLength", (Object)number);
            if (number.intValue() == 0) {
                return new getItemName();
            }
            return new getCommands();
        }
    }

    public static class getCommands
    extends ValidatingPrompt {
        public String getPromptText(ConversationContext context) {
            int commandLength = 0;
            try {
                commandLength = (Integer)context.getSessionData((Object)"commandLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (commands == null) {
                commands = new ArrayList();
            }
            if (commandLength == 0) {
                return "\n\n" + prefix + "Skipping commands";
            }
            if (atCommandLength == 1) {
                return "\n\n" + prefix + "Enter command " + atCommandLength + ":\n" + ChatColor.AQUA + "Tips:" + ChatColor.WHITE + "\n - Use &[color/format] for color.\n - Use $[command] to run using console.\n - Do not use and '/' in front of the command. \n - Use \\[Command] to send a message instead of run a command.";
            }
            return "\n\n" + prefix + "Enter command " + atCommandLength + ":";
        }

        protected boolean isInputValid(ConversationContext context, String string) {
            return string.length() <= 45;
        }

        protected String getFailedValidationText(ConversationContext context, String string) {
            return "\n" + prefix + ChatColor.DARK_RED + "Command length is too long! (Must be fewer than 45 characters.";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, String string) {
            int commandLength = 0;
            try {
                commandLength = (Integer)context.getSessionData((Object)"commandLength");
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            if (commandLength == 0) {
                atCommandLength = 1;
                context.setSessionData((Object)"commands", null);
                return new getItemName();
            }
            if (atCommandLength == commandLength) {
                commands.add(ChatColor.translateAlternateColorCodes((char)'&', (String)string));
                context.setSessionData((Object)"commands", (Object)commands);
                commands = null;
                commands = new ArrayList();
                atCommandLength = 1;
                return new getItemName();
            }
            commands.add(ChatColor.translateAlternateColorCodes((char)'&', (String)string));
            atCommandLength = atCommandLength + 1;
            return new getCommands();
        }
    }

    public static class getItemName
    extends ValidatingPrompt {
        public String getPromptText(ConversationContext context) {
            return "\n\n" + prefix + "Enter item name: " + "\n" + ChatColor.AQUA + "Tips:" + ChatColor.WHITE + "\n - Use &[Color/Format] for color.\n - Type 'none' for no item name.";
        }

        protected Prompt acceptValidatedInput(ConversationContext context, String string) {
            if (string == null || string.equalsIgnoreCase("none")) {
                context.setSessionData((Object)"itemName", (Object)"None");
            } else {
                context.setSessionData((Object)"itemName", (Object)(ChatColor.RESET + string));
            }
            return new GetLoreLength();
        }

        protected boolean isInputValid(ConversationContext context, String string) {
            return string.length() <= 30;
        }

        protected String getFailedValidationText(ConversationContext context, String string) {
            return "\n" + prefix + ChatColor.DARK_RED + "Item name is too long! (Must be fewer than 30 characters)";
        }
    }
}

