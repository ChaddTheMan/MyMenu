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
package me.chaddtheman.mymenu.storage;

import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuPersistence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Turns {@code MenuService}'s "this changed" into storage writes, a burst at a time.
 *
 * <h2>Why writes are batched</h2>
 *
 * Ten clicks on the amount control are ten mutations. Writing each one would rewrite
 * {@code menus.yml} ten times in a few seconds (SPEC §11.6). Instead, the first change after a
 * quiet period starts a timer of {@code storage.writeDebounceMillis}; every change until it
 * fires joins the same batch, and the batch is written once.
 *
 * <p>The timer is <em>not</em> restarted by later changes. A classic debounce, which waits for
 * a quiet gap, never writes at all while someone keeps editing faster than the interval; this
 * one writes at most one interval after the first unsaved change, however long the session.
 *
 * <p>The writer holds the menus themselves, not names. A later change to the same menu replaces
 * the earlier one in the batch, so only the newest version is written, and nothing has to read
 * the registry back when the timer fires.
 *
 * <h2>Threads</h2>
 *
 * Everything here runs on the main thread: {@code MenuService} calls in on it, and the timer is
 * a Bukkit task. Using Bukkit's scheduler for the timer is safe even though it stops during
 * disable, because {@link #flush} does not wait for the timer: it cancels it and submits the
 * batch directly. The I/O itself happens on storage's own thread.
 *
 * <p>Deletes in a batch are submitted before saves. Deleting {@code shop} and creating a new
 * {@code shop} inside one interval must remove the old one before writing the new one, and
 * storage applies writes in submission order.
 */
public final class DebouncedMenuWriter implements MenuPersistence {

    /** Who is told when a write fails; reloading is the way out of the degraded state. */
    private static final String FAILURE_NOTICE_PERMISSION = "MyMenu.admin.reload";

    private final Plugin plugin;
    private final MenuStorage storage;
    private final Executor mainThread;
    private final Logger logger;

    private final Map<String, Menu> pendingSaves = new LinkedHashMap<>();
    private final List<Menu> pendingDeletes = new ArrayList<>();
    private long delayTicks = toTicks(Duration.ofMillis(2000));
    private @Nullable BukkitTask timer;

    public DebouncedMenuWriter(Plugin plugin, MenuStorage storage, Executor mainThread, Logger logger) {
        this.plugin = plugin;
        this.storage = storage;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /** Takes effect from the next batch. */
    public void setDebounce(Duration debounce) {
        delayTicks = toTicks(debounce);
    }

    @Override
    public boolean isDegraded() {
        return storage.isDegraded();
    }

    @Override
    public void markDirty(Menu menu) {
        pendingSaves.put(menu.name(), menu);
        startTimer();
    }

    @Override
    public void markDeleted(Menu menu) {
        pendingSaves.remove(menu.name());
        pendingDeletes.add(menu);
        startTimer();
    }

    /**
     * Writes the pending batch now and blocks, for a bounded time, until storage has finished
     * everything submitted. {@code onDisable} only.
     */
    public void flush() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        submit();
        storage.flush();
    }

    private void startTimer() {
        if (timer == null) {
            timer = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                timer = null;
                submit();
            }, delayTicks);
        }
    }

    private void submit() {
        for (Menu menu : pendingDeletes) {
            storage.delete(menu).whenComplete((ignored, failure) -> reportFailure(failure));
        }
        pendingDeletes.clear();
        if (!pendingSaves.isEmpty()) {
            storage.saveAll(List.copyOf(pendingSaves.values()))
                    .whenComplete((ignored, failure) -> reportFailure(failure));
            pendingSaves.clear();
        }
    }

    // Runs on storage's thread; storage has already logged the cause in full.
    private void reportFailure(@Nullable Throwable failure) {
        if (failure == null) {
            return;
        }
        mainThread.execute(() -> {
            // TODO(stage 10): move to messages.yml.
            Component notice = Component.text("[MyMenu] Saving menus failed, so menu editing is disabled. "
                    + "Existing menus still work. See the server log.", NamedTextColor.RED);
            int told = plugin.getServer().broadcast(notice, FAILURE_NOTICE_PERMISSION);
            logger.debug("Told {} admin(s) about the failed write", told);
        });
    }

    private static long toTicks(Duration duration) {
        // Rounded up so a short interval never becomes zero ticks, which Bukkit runs at once.
        return Math.max(1, (duration.toMillis() + 49) / 50);
    }
}
