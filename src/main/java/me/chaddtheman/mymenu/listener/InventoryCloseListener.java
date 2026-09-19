/*
 * MyMenu - interactive chest-inventory menus for Paper
 * Copyright (C) 2026 ChaddTheMan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.chaddtheman.mymenu.listener;

import me.chaddtheman.mymenu.render.MenuHolder;
import me.chaddtheman.mymenu.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Objects;

/**
 * Hands closes of menu inventories to the session manager, reason attached. The branching on
 * the reason lives there, next to the validity rules it interacts with.
 *
 * <p>The cursor is not handled here. Every click and drag in a menu is cancelled, so the cursor
 * can only hold what the player brought, and the server returns that to them on close.
 */
public final class InventoryCloseListener implements Listener {

    private final SessionManager sessions;

    public InventoryCloseListener(SessionManager sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder
                && event.getPlayer() instanceof Player player) {
            sessions.closed(player, holder, event.getReason());
        }
    }
}
