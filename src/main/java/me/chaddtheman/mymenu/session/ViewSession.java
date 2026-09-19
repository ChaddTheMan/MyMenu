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
package me.chaddtheman.mymenu.session;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * What the plugin remembers about a player who is looking at a menu.
 *
 * <p>Deliberately little. The menu the player is looking at is <em>not</em> stored here: it is
 * read from the {@code MenuHolder} of the inventory they actually have open, because a copy kept
 * here could disagree with it and there would be no way to tell which was right. What remains is
 * the navigation history, which exists nowhere else, and the one-tick swap marker that
 * {@link SessionManager} uses to keep the session valid while one inventory replaces another.
 */
public final class ViewSession {

    private final NavigationStack history = new NavigationStack();
    private boolean swapInFlight;

    ViewSession() {
    }

    public NavigationStack history() {
        return history;
    }

    /**
     * Bounded by the scheduler, not by a tick number: {@code Bukkit.getCurrentTick()} was observed
     * standing still across many scheduled tasks on Paper 26.2 (DECISIONS #77), so a marker
     * compared against it never expired. The task is scheduled first so a refused schedule leaves
     * the marker down rather than up forever.
     */
    void markSwap(Plugin plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> swapInFlight = false);
        swapInFlight = true;
    }

    boolean swapInFlight() {
        return swapInFlight;
    }
}
