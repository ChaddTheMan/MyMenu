/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 */
package me.ChaddTheMan.MyMenu.Listeners;

import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class InventoryCloseListener
implements Listener {
    private Inventory inventory;
    private MyMenuPlayer player;
    private String playerName;
    private int menuIndex;

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        this.inventory = event.getInventory();
        this.menuIndex = MyMenuMenu.getMenuIndexByInventory(this.inventory.getName());
        if (this.menuIndex == -1) {
            return;
        }
        this.playerName = event.getPlayer().getName();
        this.player = MyMenuPlayer.getPlayerByName(this.playerName);
        if (this.player == null) {
            return;
        }
        if (this.player.isUsing()) {
            this.onUsingMenuClose();
            return;
        }
        if (this.player.isEditing()) {
            this.onEditingMenuClose();
            return;
        }
    }

    public void onUsingMenuClose() {
        MyMenuPlayer.removePlayer(this.playerName);
    }

    public void onEditingMenuClose() {
        if (!this.player.isInConversation()) {
            MyMenu.updateToConfig();
            MyMenuPlayer.removePlayer(this.playerName);
        }
    }
}

