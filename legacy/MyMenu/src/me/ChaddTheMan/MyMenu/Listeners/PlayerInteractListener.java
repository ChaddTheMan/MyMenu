/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.ItemStack
 */
package me.ChaddTheMan.MyMenu.Listeners;

import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener
implements Listener {
    private Action action;
    private ItemStack item;
    private Player eventPlayer;
    private MyMenuPlayer player;
    private int playerIndex;
    private MyMenuMenu menu;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        this.action = event.getAction();
        if (this.action == null) {
            return;
        }
        if (this.action == Action.PHYSICAL) {
            return;
        }
        this.item = event.getItem();
        if (this.item == null) {
            return;
        }
        if (this.item.getType() == Material.AIR) {
            return;
        }
        this.menu = MyMenuMenu.getMenuByItem(this.item);
        if (this.menu == null) {
            return;
        }
        this.eventPlayer = event.getPlayer();
        this.playerIndex = MyMenuPlayer.getPlayerIndex(this.eventPlayer.getName());
        if (this.playerIndex != -1) {
            event.setCancelled(true);
            return;
        }
        this.player = new MyMenuPlayer(this.eventPlayer);
        event.setCancelled(true);
        this.player.useMenu(this.menu.getName());
    }
}

