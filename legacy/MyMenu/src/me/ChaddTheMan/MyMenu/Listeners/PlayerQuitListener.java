/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package me.ChaddTheMan.MyMenu.Listeners;

import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener
implements Listener {
    private MyMenuPlayer player;

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.player = MyMenuPlayer.getPlayerByName(event.getPlayer().getName());
        if (this.player == null) {
            return;
        }
        MyMenuPlayer.removePlayer(event.getPlayer().getName());
    }
}

