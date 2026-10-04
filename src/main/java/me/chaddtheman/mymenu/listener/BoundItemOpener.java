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

import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Opens a bound item's menu on the tick after the click. This is the one path by which a bound item
 * opens its menu: the interact listener calls {@link #request}, and stage 7.5's entity listeners will
 * call the same method.
 *
 * <h2>Why the open waits a tick</h2>
 *
 * The click arrives as an event, and an event handler does only what decides the event's outcome
 * (CLAUDE.md, rule 9). For a bound item that is the match and the cancel, which stay in the listener.
 * Opening a screen is not part of the outcome, so it is scheduled. The wait is at most 50 ms, inside
 * ordinary network delay, and it has a use of its own: every other plugin finishes reacting to the
 * same click before MyMenu acts on it.
 *
 * <h2>Why another screen stops the open, without a word</h2>
 *
 * If, by the tick, the player has any screen open other than their own inventory, someone else
 * answered the click first: another plugin's screen for the block they clicked, a vanilla container,
 * or a MyMenu screen opened by another path such as a plugin running {@code /mymenu open} on the same
 * click. Opening over it would replace a screen the player is looking at with one they may not have
 * meant to ask for, so the open is dropped. Nothing is said because nothing went wrong from the
 * player's side: they see the screen that answered. The same silence covers a menu deleted since the
 * click and an open another plugin cancels, which is how an open from a click has always behaved.
 *
 * <h2>Why at most one open is pending per player</h2>
 *
 * One physical click can fire more than one interact event. The server reads an arm swing that
 * follows a right-click as a left-click on air, or in adventure mode as a left-click on the block, and
 * a right-click on a block can be followed by a right-click on air when the server's own ray trace
 * misses that block (DECISIONS #99). Each of those events is still cancelled by the listener, so the
 * item's own use never happens, but only the first request schedules an open; later requests in the
 * same tick are dropped, even if they name a different menu.
 *
 * <h2>Why the guard needs no quit handler</h2>
 *
 * The task removes the player from the pending set as its very first step, before any check that could
 * return or throw, so an entry lives exactly as long as its task waits: one tick. It is not cleared by
 * comparing tick numbers, because {@code Bukkit.getCurrentTick()} does not advance between tasks run in
 * the same scheduler pass (DECISIONS #77). The task holds the player's UUID, not the {@code Player}, and
 * looks them up when it runs: a held {@code Player} from before a rejoin can still report itself online
 * (ARCHITECTURE §6), while a lookup finds only the player who is online now. A player who quit is simply
 * not found. On disable the scheduler cancels the tasks and the set goes with this instance.
 */
public final class BoundItemOpener {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final Set<UUID> pending = new HashSet<>();

    public BoundItemOpener(Plugin plugin, SessionManager sessions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Schedules {@code menuName} to open for {@code player} on the next tick, unless an open is already
     * pending for them. Called from inside the click's event, after the caller has cancelled it.
     */
    public void request(Player player, String menuName) {
        UUID id = player.getUniqueId();
        if (pending.contains(id)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> open(id, menuName));
        // Added once the task exists, so a schedule that throws cannot leave the player blocked.
        pending.add(id);
    }

    private void open(UUID id, String menuName) {
        pending.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (player == null || hasScreenOpen(player)) {
            return;
        }
        sessions.open(player, menuName, ViewMode.VIEW);
    }

    /**
     * True when the player has a screen open other than their own inventory.
     *
     * <p>A player with nothing open still has an open view on the server: their own inventory with its
     * 2×2 crafting grid. The client does not tell the server when a survival player opens their own
     * inventory, so "nothing open" and "own inventory open" are the same state here. Checked in the
     * Paper 26.2 server jar ({@code javap}):
     * <ul>
     *   <li>{@code getOpenInventory()} returns the view of the player's current container menu, which
     *       every close path resets to their own inventory menu;</li>
     *   <li>that menu's top inventory is a 2×2 crafting container, and {@code CraftInventory.getType()}
     *       reports a crafting container of fewer than 9 slots as {@code CRAFTING}, 9 or more (a
     *       crafting table) as {@code WORKBENCH}, and a crafter as {@code CRAFTER};</li>
     *   <li>the view's own {@code getType()} reports {@code CREATIVE} instead of {@code CRAFTING} for a
     *       creative-mode player. The top inventory's type does not depend on game mode, which is why it
     *       is the one tested;</li>
     *   <li>{@code CRAFTING} cannot be passed to {@code createInventory}, so no plugin screen, and no
     *       MyMenu menu, can report it.</li>
     * </ul>
     * The client-side creative inventory sends nothing either, so it counts as the player's own
     * inventory. Screens that are not inventory views are invisible to this test and do not hold the
     * open back: a book opened with {@code openBook}, a sign editor, a dialog.
     */
    private static boolean hasScreenOpen(Player player) {
        return player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING;
    }
}
