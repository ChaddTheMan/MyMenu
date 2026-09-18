/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryOpenEvent
 *  org.bukkit.inventory.Inventory
 */
package me.ChaddTheMan.MyMenu.Listeners;

import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

public class InventoryOpenListener
implements Listener {
    private Inventory inventory;
    private MyMenuPlayer player;
    private int menuIndex;

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        this.inventory = event.getInventory();
        this.menuIndex = MyMenuMenu.getMenuIndexByInventory(this.inventory.getName());
        this.player = MyMenuPlayer.getPlayerByName(event.getPlayer().getName());
        if (this.menuIndex == -1) {
            return;
        }
        if (this.player == null) {
            return;
        }
        if (this.player.isUsing()) {
            this.onUseMenuOpen();
            return;
        }
        if (this.player.isEditing()) {
            this.onEditMenuEdit();
            return;
        }
    }

    public void onUseMenuOpen() {
        if (this.menuIndex == -1) {
            return;
        }
        this.player.setMenuAccessing(this.menuIndex);
    }

    public void onEditMenuEdit() {
        if (this.menuIndex == -1) {
            return;
        }
        this.player.setMenuAccessing(this.menuIndex);
    }
}

