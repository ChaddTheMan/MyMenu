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
package me.chaddtheman.mymenu.action;

import me.chaddtheman.mymenu.model.ClickKey;
import me.chaddtheman.mymenu.model.MenuItem;
import me.chaddtheman.mymenu.model.VersionedMenu;
import me.chaddtheman.mymenu.service.CooldownStore;
import me.chaddtheman.mymenu.session.NavigationStack;
import me.chaddtheman.mymenu.session.SessionManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs an item's action list for the player who clicked it.
 *
 * <h2>Why this is not a loop over the list</h2>
 *
 * Three of the eight action types cannot run where the click arrives. {@code DELAY} has to
 * stop the list and pick it up again on a later tick, so the "current position" of a list must
 * survive outside any stack frame: {@link #run} walks until it meets a delay, hands the rest of
 * the list and the index to a scheduled task, and returns. {@code MENU}, {@code BACK} and
 * {@code CLOSE} open or close an inventory, which is not allowed inside
 * {@code InventoryClickEvent} (rule 9), so each is queued for the next tick while the walk
 * carries on; the actions after them do not wait for the inventory to change.
 *
 * <h2>Why pending sequences are not session state</h2>
 *
 * SPEC §9.2: a sequence is cancelled by logout and by nothing else. Death closes the inventory,
 * which ends the view session, but the {@code $give} three seconds after the click must still
 * arrive. So the map of who is mid-sequence lives here, keyed by UUID, and is cleared by a
 * quit handler on this class rather than by anything the session manager does. Hanging the
 * task off {@code ViewSession} would have made every session-ending path cancel it silently.
 *
 * <p>A player with an entry in the map is mid-sequence and every click of theirs is ignored
 * until it finishes. The entry is written only once the task is scheduled and removed by the
 * task itself when it fires, so unlike 1.x's session map it cannot be left set with nothing
 * behind it: the worst a bug here could do is cancel a task that had already run.
 *
 * <h2>The elevation window is the synchronous dispatch and nothing more</h2>
 *
 * {@code PLAYER_ELEVATED} adds a {@link PermissionAttachment}, dispatches, and removes it in
 * {@code finally}. A command that answers later, such as a confirmation prompt or a plugin that
 * queues the work and re-checks permissions when it runs, sees the player <em>without</em> the
 * node by then and behaves as for any unprivileged player. That is the intended edge of the
 * feature, not a gap: widening the window is how 1.x's temporary op turned into a permanent
 * one. Commands that need to act later should be {@code CONSOLE} actions.
 */
public final class ActionExecutor implements Listener {

    /** SPEC §9.3's default for {@code navigation.maxDepth}. */
    public static final int DEFAULT_MAX_DEPTH = 10;

    /** Outside {@code MyMenu.*} on purpose so ops still feel cooldowns while testing them. */
    public static final String BYPASS_COOLDOWN = "MyMenu.bypass.cooldown";

    // TODO(stage 10): messages.yml.
    private static final String COOLDOWN_MESSAGE = "You can use that again in %d seconds.";
    private static final Component MENU_MISSING = Component.text("That menu does not exist.", NamedTextColor.RED);

    private final Plugin plugin;
    private final SessionManager sessions;
    private final CooldownStore cooldowns;
    private final ActionTextResolver text;
    private final Logger logger;
    private volatile int maxDepth;

    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public ActionExecutor(Plugin plugin, SessionManager sessions, CooldownStore cooldowns,
                          ActionTextResolver text, Logger logger, int maxDepth) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.text = Objects.requireNonNull(text, "text");
        this.logger = Objects.requireNonNull(logger, "logger");
        setMaxDepth(maxDepth);
    }

    /**
     * Clamped to 1..{@link NavigationStack#MAX_DEPTH}. The stack's ceiling is the invariant;
     * a config value above it would otherwise start dropping history silently.
     */
    // TODO(stage 7): config plumbing calls this once navigation.maxDepth is read.
    public void setMaxDepth(int depth) {
        int clamped = Math.clamp(depth, 1, NavigationStack.MAX_DEPTH);
        if (clamped != depth) {
            logger.warn("navigation.maxDepth {} is outside 1-{}; using {}", depth, NavigationStack.MAX_DEPTH, clamped);
        }
        this.maxDepth = clamped;
    }

    /** {@code InventoryClickListener.Dispatcher}: the click has been validated and its list chosen. */
    public void dispatch(Player player, VersionedMenu menu, int slot, MenuItem item, ClickKey key,
                         List<Action> actions) {
        // An empty list means "this click does nothing" (SPEC §8.4); it earns no feedback
        // and no cooldown, the same as a slot with no keys at all.
        if (actions.isEmpty()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) {
            return;
        }
        String menuName = menu.menu().name();
        if (item.cooldownSeconds() > 0 && !player.hasPermission(BYPASS_COOLDOWN)) {
            Optional<Duration> wait = cooldowns.remaining(id, menuName, slot);
            if (wait.isPresent()) {
                // Round up so "1 second" is never shown for a wait that is really 1.9.
                long seconds = (wait.get().toMillis() + 999) / 1000;
                player.sendMessage(Component.text(String.format(COOLDOWN_MESSAGE, seconds), NamedTextColor.RED));
                return;
            }
            cooldowns.mark(id, menuName, slot, item.cooldownSeconds());
        }
        if (item.clickSound() != null) {
            player.playSound(Sound.sound(item.clickSound(), Sound.Source.MASTER, 1f, 1f));
        }
        run(player, actions, 0);
    }

    /** True while the player is mid-sequence. For probes and reports. */
    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /**
     * Executes from {@code from} until the list ends or a delay suspends it. A step that throws
     * ends the sequence: the steps after it were written on the assumption that it ran. A delay
     * with nothing executable after it ends the sequence too (SPEC §9.2), so a trailing
     * {@code DELAY} does not hold the player pending for nothing.
     */
    private void run(Player player, List<Action> actions, int from) {
        UUID id = player.getUniqueId();
        for (int i = from; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action instanceof Action.Delay delay) {
                int next = i + 1;
                if (!hasExecutableFrom(actions, next)) {
                    return;
                }
                BukkitTask task = scheduler().runTaskLater(plugin, () -> resume(player, actions, next), delay.ticks());
                pending.put(id, task);
                return;
            }
            try {
                execute(player, action);
            } catch (RuntimeException e) {
                logger.warn("Action {} for {} failed; the rest of the list was not run", action, player.getName(), e);
                return;
            }
        }
    }

    private static boolean hasExecutableFrom(List<Action> actions, int from) {
        for (int i = from; i < actions.size(); i++) {
            if (!(actions.get(i) instanceof Action.Delay)) {
                return true;
            }
        }
        return false;
    }

    private void resume(Player player, List<Action> actions, int from) {
        pending.remove(player.getUniqueId());
        // The quit handler cancels the task, so this only guards a quit that raced the tick.
        if (!player.isOnline()) {
            return;
        }
        run(player, actions, from);
    }

    private void execute(Player player, Action action) {
        switch (action) {
            case Action.PlayerCommand command -> player.performCommand(command(command.command(), player));
            case Action.ConsoleCommand command ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(command.command(), player));
            case Action.ElevatedCommand command -> elevated(player, command);
            case Action.Message message -> player.sendMessage(text.message(message.text(), player));
            case Action.OpenMenu menu -> nextTick(player, () -> open(player, menu.menuName()));
            case Action.Back ignored -> nextTick(player, () -> sessions.back(player));
            case Action.Close ignored -> nextTick(player, player::closeInventory);
            case Action.Delay ignored -> throw new IllegalStateException("delays are handled by run()");
        }
    }

    /** The one path from stored command text to a dispatcher; the sanitiser cannot be skipped. */
    private String command(String raw, Player player) {
        return CommandSanitiser.sanitise(text.command(raw, player));
    }

    private void elevated(Player player, Action.ElevatedCommand command) {
        String line = command(command.command(), player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        try {
            for (String node : command.permissions()) {
                attachment.setPermission(node, true);
            }
            player.performCommand(line);
        } finally {
            // remove() rather than player.removeAttachment(): the latter throws if another
            // plugin already tore the attachment down, and that would mask the command's own
            // exception. Either way the node is gone when this line has run.
            attachment.remove();
        }
    }

    private void open(Player player, String menuName) {
        int depth = sessions.view(player).map(session -> session.history().size()).orElse(0);
        if (depth >= maxDepth) {
            logger.warn("{} is {} menus deep; MENU '{}' refused by navigation.maxDepth ({})",
                    player.getName(), depth, menuName, maxDepth);
            return;
        }
        if (sessions.navigate(player, menuName) == SessionManager.OpenResult.NO_SUCH_MENU) {
            logger.warn("MENU action names '{}', which does not exist", menuName);
            player.sendMessage(MENU_MISSING);
        }
    }

    private void nextTick(Player player, Runnable work) {
        scheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                work.run();
            }
        });
    }

    private BukkitScheduler scheduler() {
        return plugin.getServer().getScheduler();
    }

    /** SPEC §9.2: logout is the one thing that cancels a sequence. Quits and kicks both arrive here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    /** Drops the player's pending sequence, if any. Safe to call when there is none. */
    public void cancel(UUID player) {
        BukkitTask task = pending.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
}
