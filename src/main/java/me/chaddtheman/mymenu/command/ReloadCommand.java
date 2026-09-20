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
package me.chaddtheman.mymenu.command;

import me.chaddtheman.mymenu.action.ActionExecutor;
import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.session.SessionManager;
import me.chaddtheman.mymenu.storage.DebouncedMenuWriter;
import me.chaddtheman.mymenu.storage.MenuStorage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Re-reads {@code config.yml} and every menu without restarting (SPEC §3.6, §12).
 *
 * <h2>Why reload waits without blocking</h2>
 *
 * Pending edits must be written before the file is re-read, or the load sees the file as it was
 * before them and the edits vanish. Waiting for that on the main thread would be a second
 * blocking-I/O exception, which rule 2 forbids. It is not needed: storage runs every job on one
 * thread in submission order, so a load queued <em>after</em> the flushed writes cannot start
 * until they finish. The reload is a chain of futures that hops to the main thread only for the
 * parts that touch Bukkit or the registry.
 *
 * <h2>Unwritten changes: retry, refuse, or discard</h2>
 *
 * If a write has failed, storage holds changes the file does not, and a load would throw them
 * away, so it refuses. Before loading, reload therefore <em>retries</em> that write: if the admin
 * has fixed the cause (freed disk, fixed permissions), the retry succeeds and the reload goes
 * ahead with nothing lost. Without the retry, even a fixed fault would leave discarding as the
 * only way out.
 *
 * <p>If the retry fails again, the reload is refused and the message names the discard form. That
 * form does the same flush and retry, so anything that <em>can</em> still be written is written,
 * and only then tells storage to forget what it could not write, drops the writer's pending batch
 * so the timer cannot bring those changes back, and loads the file as it is on disk (DECISIONS #71).
 *
 * <h2>What a reload leaves behind</h2>
 *
 * Every open menu is closed, every edit session and prompt ends, and every pending action sequence
 * is cancelled, because each of them refers to menus by name and the menu behind that name may
 * now be different. This happens only once the load has succeeded, so a refused reload disturbs
 * no one. The four settings that other classes cache are re-applied through the same method
 * {@code onEnable} uses. The storage backend is not switched: a changed {@code storage.type} is
 * reported and needs a restart.
 *
 * <p>While the chain runs, the command tree refuses mutating commands. A change accepted in the
 * gap would be made against a menu the load is about to replace.
 */
public final class ReloadCommand {

    /** The literal after {@code reload} that selects the discard form. */
    public static final String DISCARD = "discard-unsaved";

    public static final CommandSpec SPEC = new CommandSpec("reload", "MyMenu.admin.reload",
            "Re-read config.yml and every menu. '" + DISCARD + "' first drops changes that cannot be saved.",
            false, false);

    private record Loaded(Collection<Menu> menus, boolean discarded) {
    }

    private final Path dataDirectory;
    private final Logger logger;
    private final Plugin plugin;
    private final Executor mainThread;
    private final MenuStorage storage;
    private final DebouncedMenuWriter writer;
    private final MenuService menus;
    private final SessionManager sessions;
    private final ActionExecutor executor;
    private final AtomicReference<PluginConfig> config;
    private final Consumer<PluginConfig> applyConfig;

    // Main thread only.
    private boolean running;

    public ReloadCommand(Plugin plugin, Executor mainThread, MenuStorage storage, DebouncedMenuWriter writer,
                         MenuService menus, SessionManager sessions, ActionExecutor executor,
                         AtomicReference<PluginConfig> config, Consumer<PluginConfig> applyConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataDirectory = plugin.getDataPath();
        this.logger = plugin.getSLF4JLogger();
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.config = Objects.requireNonNull(config, "config");
        this.applyConfig = Objects.requireNonNull(applyConfig, "applyConfig");
    }

    /** True from the moment a reload starts until it has finished or failed. Main thread. */
    public boolean isRunning() {
        return running;
    }

    public void execute(CommandSender sender, boolean discard) {
        if (running) {
            Replies.error(sender, "A reload is already running.");
            return;
        }
        running = true;
        CompletableFuture.supplyAsync(() -> PluginConfig.load(dataDirectory, logger))
                .thenComposeAsync(fresh -> reload(sender, fresh, discard), mainThread)
                .whenCompleteAsync((ignored, failure) -> {
                    running = false;
                    if (failure != null) {
                        refused(sender, unwrap(failure));
                    }
                }, mainThread);
    }

    // Main thread.
    private CompletableFuture<Void> reload(CommandSender sender, PluginConfig fresh, boolean discard) {
        PluginConfig current = config.get();
        PluginConfig applied = fresh.withStorageType(current.storageType());
        config.set(applied);
        applyConfig.accept(applied);
        if (fresh.storageType() != current.storageType()) {
            Replies.warn(sender, "config.yml now asks for " + fresh.storageType() + " storage, but changing "
                    + "storage needs a server restart. Still using " + current.storageType() + ".");
        }
        Map<String, Menu> before = new HashMap<>();
        for (Menu menu : menus.registry().menus()) {
            before.put(menu.name(), menu);
        }
        return writer.flushAsync()
                // A failed write is already logged and broadcast, and the retry below tries again.
                .exceptionally(failure -> 0)
                .thenCompose(ignored -> storage.retryUnwritten().exceptionally(failure -> null))
                .thenCompose(ignored -> discard
                        ? storage.discardUnwritten()
                        : CompletableFuture.completedFuture(false))
                .thenCompose(discarded -> storage.loadAll().thenApply(loaded -> new Loaded(loaded, discarded)))
                .thenAcceptAsync(loaded -> install(sender, loaded, before, discard), mainThread);
    }

    // Main thread.
    private void install(CommandSender sender, Loaded loaded, Map<String, Menu> before, boolean discard) {
        int dropped = discard ? writer.discardPending() : 0;
        if (dropped > 0) {
            logger.warn("Reload discarded {} pending menu change(s) on an admin's request", dropped);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean prompted = sessions.edit(player).map(session -> session.prompt() != null).orElse(false);
            if (SessionManager.openHolder(player).isPresent() || prompted) {
                Replies.warn(player, "Menus were reloaded, so the menu you were using was closed.");
            }
            executor.cancel(player.getUniqueId());
        }
        sessions.closeAll();
        menus.replaceAll(loaded.menus());

        Replies.ok(sender, "Reloaded config.yml and " + loaded.menus().size() + " menu(s).");
        if (loaded.discarded() || dropped > 0) {
            List<String> reverted = changed(before, loaded.menus());
            Replies.warn(sender, reverted.isEmpty()
                    ? "Unsaved changes were discarded."
                    : "Unsaved changes were discarded. Back to their last save: " + String.join(", ", reverted) + ".");
        }
        if (menus.isDegraded()) {
            Replies.error(sender, "Some of menus.yml could not be loaded (the server log lists what), so menu "
                    + "editing stays disabled. Fix the file and reload again.");
        }
    }

    private void refused(CommandSender sender, Throwable cause) {
        if (cause instanceof MenuStorage.UnwrittenChangesException) {
            Replies.error(sender, "Menus were not reloaded. Changes that could not be saved are still held in "
                    + "memory, and reloading would throw them away. Saving them was just retried and failed again; "
                    + "the server log says why. Fix the cause and run /mymenu reload again, or run /mymenu reload "
                    + DISCARD + " to drop those changes and load menus.yml as it is on disk.");
            Replies.info(sender, "config.yml was re-read and applied.");
            return;
        }
        logger.error("Reload failed", cause);
        Replies.error(sender, "Reload failed: " + cause.getMessage() + ". The server log has the details.");
    }

    /** Names whose menu differs between the two, including ones present on only one side. */
    private static List<String> changed(Map<String, Menu> before, Collection<Menu> after) {
        Map<String, Menu> now = new HashMap<>();
        for (Menu menu : after) {
            now.put(menu.name(), menu);
        }
        TreeSet<String> names = new TreeSet<>(before.keySet());
        names.addAll(now.keySet());
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!Objects.equals(before.get(name), now.get(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
