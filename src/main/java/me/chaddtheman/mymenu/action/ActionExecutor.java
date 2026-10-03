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
import me.chaddtheman.mymenu.render.ViewMode;
import me.chaddtheman.mymenu.service.CooldownStore;
import me.chaddtheman.mymenu.session.NavigationStack;
import me.chaddtheman.mymenu.session.SessionManager;
import me.chaddtheman.mymenu.session.ViewSession;
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
import org.jspecify.annotations.Nullable;
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
 * <h2>Why the whole list waits for the next tick</h2>
 *
 * The click arrives inside {@code InventoryClickEvent}, and an event handler does only what decides
 * the event's outcome, plus the bookkeeping that belongs to that moment (CLAUDE.md, rule 9).
 * {@link #dispatch} therefore does the click's part and nothing more: it refuses a click while a list
 * is pending, applies the cooldown, plays the click sound, and schedules the list for the next tick.
 * Running the list is not part of the click's outcome. A {@code PLAYER} action can run any plugin's
 * command, and a command that opens a screen from inside a click handler is exactly what rule 9
 * forbids. Deferring only {@code MENU}, {@code BACK} and {@code CLOSE}, as stage 6 did, left the
 * commands in the handler and reordered the list: {@code [CLOSE, PLAYER warps]} queued the close
 * behind the warps screen and closed that instead.
 *
 * <p>Because every list now starts on a scheduled task, the steps that open and close screens run
 * in place, in the order they are written. That is safe only on a scheduled task, so the code is
 * shaped to keep it there: the walk is {@link Sequence#run}, and the one place a {@code Sequence} is
 * made hands it straight to the scheduler. No caller ever holds a sequence it could run inline.
 *
 * <h2>Why this is still not a loop over the list</h2>
 *
 * {@code DELAY} has to stop the list and pick it up again on a later tick, so the position in the
 * list must survive outside any stack frame. The {@code Sequence} carries it: the walk runs until it
 * meets a delay, schedules the same sequence again, and returns.
 *
 * <h2>Pending from the click, not from the first step</h2>
 *
 * The player is recorded as pending when the list is scheduled, so a second click in the same tick
 * is refused and a quit before the tick cancels the list. A list with nothing executable in it, only
 * delays, is never scheduled at all, so it holds no one pending (SPEC §9.2).
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
 * <h2>{@code CLOSE} closes only the menu its list was acting on</h2>
 *
 * A list remembers the view session it was clicked in, and {@code CLOSE} closes the player's screen
 * only while that same session object is the one open. Anything else the player is looking at by
 * then, another plugin's screen opened by a {@code PLAYER} action, a chest opened during a delay, an
 * editor, a menu they opened themselves (which always starts a new session), is left alone, without
 * a word. When the list's own {@code MENU} or {@code BACK} opens a menu, the list follows it to the
 * session that menu is now open in, which is the same session unless the player had closed the menu
 * during a delay. The open screen is also checked to be a menu in view mode, because
 * {@link SessionManager#view} keeps a session valid for the tick in which its menu was opened even if
 * something else has already replaced it.
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

    // A delay of 0 still runs on the scheduler's next pass, which is the next tick.
    private static final long NEXT_TICK = 0L;

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
    public void setMaxDepth(int depth) {
        int clamped = Math.clamp(depth, 1, NavigationStack.MAX_DEPTH);
        if (clamped != depth) {
            logger.warn("navigation.maxDepth {} is outside 1-{}; using {}", depth, NavigationStack.MAX_DEPTH, clamped);
        }
        this.maxDepth = clamped;
    }

    /**
     * {@code InventoryClickListener.Dispatcher}: the click has been validated and its list chosen.
     * Runs inside the click event, so it decides the click and schedules the list; it runs nothing.
     */
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
        if (!hasExecutableFrom(actions, 0)) {
            return;
        }
        schedule(new Sequence(player, actions, sessions.view(player).orElse(null)), NEXT_TICK);
    }

    /** True while the player is mid-sequence. For probes and reports. */
    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /** The only door to the scheduler, and the only place a player becomes pending. */
    private void schedule(Sequence sequence, long delayTicks) {
        BukkitTask task = scheduler().runTaskLater(plugin, sequence, delayTicks);
        pending.put(sequence.player.getUniqueId(), task);
    }

    private static boolean hasExecutableFrom(List<Action> actions, int from) {
        for (int i = from; i < actions.size(); i++) {
            if (!(actions.get(i) instanceof Action.Delay)) {
                return true;
            }
        }
        return false;
    }

    private void execute(Sequence sequence, Action action) {
        Player player = sequence.player;
        switch (action) {
            case Action.PlayerCommand command -> player.performCommand(command(command.command(), player));
            case Action.ConsoleCommand command ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(command.command(), player));
            case Action.ElevatedCommand command -> elevated(player, command);
            case Action.Message message -> player.sendMessage(text.message(message.text(), player));
            case Action.OpenMenu menu -> open(sequence, menu.menuName());
            case Action.Back ignored -> {
                if (sessions.back(player) == SessionManager.OpenResult.OPENED) {
                    sequence.followOpenMenu();
                }
            }
            case Action.Close ignored -> close(sequence);
            case Action.Delay ignored -> throw new IllegalStateException("delays are handled by Sequence.run()");
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

    private void open(Sequence sequence, String menuName) {
        Player player = sequence.player;
        int depth = sessions.view(player).map(session -> session.history().size()).orElse(0);
        if (depth >= maxDepth) {
            logger.warn("{} is {} menus deep; MENU '{}' refused by navigation.maxDepth ({})",
                    player.getName(), depth, menuName, maxDepth);
            return;
        }
        switch (sessions.navigate(player, menuName)) {
            case OPENED -> sequence.followOpenMenu();
            case NO_SUCH_MENU -> {
                logger.warn("MENU action names '{}', which does not exist", menuName);
                player.sendMessage(MENU_MISSING);
            }
            case CANCELLED, NO_HISTORY -> {
                // Another plugin refused the open; navigate never reports NO_HISTORY.
            }
        }
    }

    private void close(Sequence sequence) {
        Player player = sequence.player;
        boolean menuOpen = SessionManager.openHolder(player).map(holder -> holder.mode() == ViewMode.VIEW).orElse(false);
        ViewSession open = sessions.view(player).orElse(null);
        if (menuOpen && open != null && open == sequence.session) {
            player.closeInventory();
        }
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

    /**
     * One action list on its way through, from the tick after the click. Built only in
     * {@link #dispatch}, which passes it straight to {@link #schedule}; the scheduler is the only
     * caller of {@link #run}. The list is the one captured at the click, so an edit made while it
     * waits on a delay does not change what it does.
     */
    private final class Sequence implements Runnable {

        private final Player player;
        private final List<Action> actions;
        // The view session CLOSE may close. Null when the click had none, and then CLOSE does nothing.
        private @Nullable ViewSession session;
        private int next;

        private Sequence(Player player, List<Action> actions, @Nullable ViewSession session) {
            this.player = player;
            this.actions = List.copyOf(actions);
            this.session = session;
        }

        /**
         * Executes until the list ends or a delay suspends it. A step that throws ends the sequence:
         * the steps after it were written on the assumption that it ran. A delay with nothing
         * executable after it ends the sequence too (SPEC §9.2).
         */
        @Override
        public void run() {
            pending.remove(player.getUniqueId());
            while (next < actions.size()) {
                // The quit handler cancels a waiting task; this catches a quit that raced the tick,
                // or one caused by an earlier step of this list, such as a kick command.
                if (!player.isOnline()) {
                    return;
                }
                Action action = actions.get(next++);
                if (action instanceof Action.Delay delay) {
                    if (hasExecutableFrom(actions, next)) {
                        schedule(this, delay.ticks());
                    }
                    return;
                }
                try {
                    execute(this, action);
                } catch (RuntimeException e) {
                    logger.warn("Action {} for {} failed; the rest of the list was not run", action, player.getName(), e);
                    return;
                }
            }
        }

        /** A {@code MENU} or {@code BACK} of this list opened a menu; {@code CLOSE} now means that one. */
        private void followOpenMenu() {
            session = sessions.view(player).orElse(null);
        }
    }
}
