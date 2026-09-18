/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.command.CommandSender
 *  org.bukkit.conversations.Conversable
 *  org.bukkit.conversations.Conversation
 *  org.bukkit.conversations.ConversationAbandonedListener
 *  org.bukkit.conversations.ConversationFactory
 *  org.bukkit.conversations.ConversationPrefix
 *  org.bukkit.conversations.Prompt
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryType$SlotType
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package me.ChaddTheMan.MyMenu.Listeners;

import java.util.HashMap;
import java.util.Map;
import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItem;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItemConversation;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Errors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class InventoryClickListener
implements Listener {
    private Player eventPlayer;
    private MyMenuPlayer player;
    private MyMenuMenu menu;
    private Inventory inventory;
    private MyMenuItem item;
    private ItemStack itemAtCursor;
    private InventoryType.SlotType slotType;
    private ClickType clickType;
    private int slot;
    private int menuIndex;
    private boolean useConsole;
    private boolean sendMessage;
    private ConversationFactory conversationFactory;
    private Conversation conversation;
    private MyMenuItemConversation menuItemConversation;
    private Map<Object, Object> conversationData;

    @EventHandler(priority=EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        this.inventory = event.getInventory();
        this.menuIndex = MyMenuMenu.getMenuIndexByInventory(this.inventory.getName());
        if (this.menuIndex == -1) {
            return;
        }
        event.setCancelled(true);
        this.slotType = event.getSlotType();
        if (this.slotType == null) {
            return;
        }
        this.eventPlayer = (Player)event.getWhoClicked();
        this.player = MyMenuPlayer.getPlayerByName(this.eventPlayer.getName());
        this.menu = MyMenuMenu.getMenuList().get(this.menuIndex);
        this.slot = event.getRawSlot();
        this.item = this.menu.getItem(this.slot);
        this.itemAtCursor = event.getCursor();
        this.clickType = event.getClick();
        if (this.slot >= this.menu.getSize()) {
            return;
        }
        if (this.player == null) {
            Player player = (Player)event.getWhoClicked();
            player.closeInventory();
            return;
        }
        if (this.player.isUsing()) {
            this.onUseMenuClick();
            return;
        }
        if (this.player.isEditing()) {
            this.onEditMenuClick();
            return;
        }
    }

    public void onUseMenuClick() {
        if (this.slotType != InventoryType.SlotType.CONTAINER) {
            return;
        }
        if (this.item == null) {
            return;
        }
        if (this.item.getCommandList() == null) {
            return;
        }
        this.player.closeMenu();
        for (String command : this.item.getCommandList()) {
            if (command == null) continue;
            if (command.contains("{WORLD}")) {
                command = command.replace("{WORLD}", this.player.getPlayer().getWorld().getName());
            }
            if (command.contains("{SERVER}")) {
                command = command.replace("{SERVER}", this.player.getPlayer().getServer().getName());
            }
            if (command.contains("{LOCATION-X}")) {
                command = command.replace("{LOCATION-X}", Double.toString(this.player.getPlayer().getLocation().getX()));
            }
            if (command.contains("{LOCATION-Y}")) {
                command = command.replace("{LOCATION-Y}", Double.toString(this.player.getPlayer().getLocation().getY()));
            }
            if (command.contains("{LOCATION-Z}")) {
                command = command.replace("{LOCATION-Z}", Double.toString(this.player.getPlayer().getLocation().getZ()));
            }
            if (command.contains("{PLAYER}")) {
                command = command.replace("{PLAYER}", this.player.getPlayer().getName());
            }
            this.useConsole = false;
            this.sendMessage = false;
            if (command.startsWith("\\")) {
                command = command.substring(1);
                this.sendMessage = true;
            }
            if (command.startsWith("$")) {
                command = command.substring(1);
                this.useConsole = true;
            }
            if (this.useConsole) {
                Bukkit.dispatchCommand((CommandSender)Bukkit.getServer().getConsoleSender(), (String)command);
                continue;
            }
            if (this.sendMessage) {
                this.player.getPlayer().sendMessage(command);
                continue;
            }
            this.player.getPlayer().performCommand(command);
        }
    }

    public void onEditMenuClick() {
        if (this.slotType != InventoryType.SlotType.CONTAINER) {
            return;
        }
        if (this.clickType == ClickType.RIGHT) {
            if (this.item == null) {
                return;
            }
            if (this.itemAtCursor.getType() != Material.AIR && this.itemAtCursor != null) {
                return;
            }
            this.inventory.setItem(this.slot, new ItemStack(Material.AIR));
            this.menu.removeMenuItem(this.slot);
            MyMenuMenu.setMenu(this.menuIndex, this.menu);
            MyMenu.updateToConfig();
        } else if (this.clickType == ClickType.MIDDLE) {
            if (this.item == null) {
                if (this.itemAtCursor == null || this.itemAtCursor.getType() == Material.AIR) {
                    return;
                }
                if (this.itemAtCursor != null) {
                    this.player.getPlayer().setItemOnCursor(new ItemStack(Material.AIR));
                    MyMenuItem menuItem = this.player.getMenuItemAtCursor();
                    this.player.setMenuItemAtCursor(null);
                    this.inventory.setItem(this.slot, menuItem.getItem());
                    this.menu.addMenuItem(this.slot, menuItem);
                    MyMenuMenu.setMenu(this.menuIndex, this.menu);
                }
            } else {
                if (this.itemAtCursor.getType() != Material.AIR && this.itemAtCursor != null) {
                    return;
                }
                MyMenuItem item = this.menu.getMenuItems().get(this.slot);
                this.player.setMenuItemAtCursor(item);
                ItemStack itemStack = item.getItem();
                itemStack.setAmount(item.getAmount());
                this.player.getPlayer().setItemOnCursor(itemStack);
                this.inventory.setItem(this.slot, new ItemStack(Material.AIR));
                this.menu.removeMenuItem(this.slot);
                MyMenuMenu.setMenu(this.menuIndex, this.menu);
            }
        } else if (this.clickType == ClickType.LEFT) {
            if (this.item != null) {
                return;
            }
            if (this.itemAtCursor.getType() != Material.AIR && this.itemAtCursor != null) {
                return;
            }
            this.player.setInConversation(true);
            this.player.closeMenu();
            if (this.conversationData != null) {
                this.conversationData = null;
            }
            this.conversationData = new HashMap<Object, Object>();
            this.conversationData.put("Menu", this.menu);
            this.conversationFactory = new ConversationFactory((Plugin)MyMenu.getInstance()).withModality(true).withPrefix((ConversationPrefix)new MyMenuItemConversation.CreatingItemPrefix()).withInitialSessionData(this.conversationData).withEscapeSequence("mmcancel").addConversationAbandonedListener((ConversationAbandonedListener)new MyMenuItemConversation.EndOfConversation()).withFirstPrompt((Prompt)new MyMenuItemConversation.GetMaterialType()).withLocalEcho(false).thatExcludesNonPlayersWithMessage(Errors.NOT_A_PLAYER);
            this.conversation = this.conversationFactory.buildConversation((Conversable)this.player.getPlayer());
            this.menuItemConversation = new MyMenuItemConversation();
            this.menuItemConversation.beginItemConversation(this.conversation, this.slot, this.player, this.menu, this.menuIndex);
        } else {
            return;
        }
    }
}

