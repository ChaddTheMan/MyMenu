/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package me.ChaddTheMan.MyMenu.Listeners;

import java.util.ArrayList;
import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfigMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class PlayerJoinListener
implements Listener {
    private Player player;
    private MyMenuPlayer mmPlayer;
    private int playerIndex;
    private ArrayList<MyMenuConfigMenu> menuList;
    private MyMenuMenu menu;
    private String menuToOpen;

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.player = event.getPlayer();
        if (this.player.hasPermission("MyMenu.admin.update") && MyMenu.update) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask((Plugin)MyMenu.getInstance(), new Runnable(){

                @Override
                public void run() {
                    PlayerJoinListener.this.player.sendMessage(Messages.MENU_HEADER);
                    PlayerJoinListener.this.player.sendMessage(ChatColor.AQUA + "Notice: " + ChatColor.WHITE + "A new update is available for MyMenu.");
                    PlayerJoinListener.this.player.sendMessage(ChatColor.AQUA + "To update automatically: " + ChatColor.WHITE + "/update mymenu");
                    PlayerJoinListener.this.player.sendMessage(ChatColor.AQUA + "New File: " + ChatColor.WHITE + MyMenu.name);
                    PlayerJoinListener.this.player.sendMessage(ChatColor.AQUA + "Link: " + ChatColor.WHITE + MyMenu.link);
                    PlayerJoinListener.this.player.sendMessage(Messages.MENU_FOOTER);
                }
            }, 100L);
        }
        this.menuToOpen = "";
        this.menuList = MyMenuConfigMenu.getConfigMenuList();
        int playerIndex = MyMenuPlayer.getPlayerIndex(this.player.getName());
        if (playerIndex != -1) {
            return;
        }
        this.giveItems();
        this.openMenus();
        if (this.menuToOpen.isEmpty()) {
            return;
        }
        if (MyMenuPlayer.getPlayerByName(this.player.getName()) != null) {
            MyMenuPlayer.removePlayer(this.player.getName());
        }
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask((Plugin)MyMenu.getInstance(), new Runnable(){

            @Override
            public void run() {
                PlayerJoinListener.this.mmPlayer = new MyMenuPlayer(PlayerJoinListener.this.player);
                PlayerJoinListener.this.player.closeInventory();
                PlayerJoinListener.this.mmPlayer.useMenu(PlayerJoinListener.this.menuToOpen);
            }
        }, 20L);
    }

    public void openMenus() {
        int i = 0;
        while (i < this.menuList.size()) {
            MyMenuConfigMenu configMenu = this.menuList.get(i);
            if (configMenu.opensOnJoin()) {
                this.menu = MyMenuMenu.getMenu(configMenu.getName());
                if (this.menu != null) {
                    this.playerIndex = MyMenuPlayer.getPlayerIndex(this.player.getName());
                    if (this.playerIndex != -1) {
                        MyMenuPlayer.removePlayer(this.player.getName());
                        this.player.closeInventory();
                    }
                    this.menuToOpen = configMenu.getName();
                }
            }
            ++i;
        }
    }

    public void giveItems() {
        int i = 0;
        while (i < this.menuList.size()) {
            MyMenuMenu menu;
            MyMenuConfigMenu configMenu = this.menuList.get(i);
            if (configMenu.givesItemOnJoin() && (menu = MyMenuMenu.getMenu(configMenu.getName())) != null && menu.getBoundItem() != null) {
                ItemStack menuItem = menu.getBoundItem();
                if (menu.isSpecific()) {
                    ItemMeta menuItemMeta = menuItem.getItemMeta();
                    menuItemMeta.setDisplayName(menu.getBoundItemName());
                    menuItem.setItemMeta(menuItemMeta);
                }
                if (!this.player.getInventory().contains(menuItem)) {
                    this.player.getInventory().addItem(new ItemStack[]{menuItem});
                }
            }
            ++i;
        }
    }
}

