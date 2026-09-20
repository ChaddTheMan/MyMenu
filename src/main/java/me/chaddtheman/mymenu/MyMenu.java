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
package me.chaddtheman.mymenu;

import me.chaddtheman.mymenu.action.ActionExecutor;
import me.chaddtheman.mymenu.action.ActionParser;
import me.chaddtheman.mymenu.action.ActionTextResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.chaddtheman.mymenu.command.ChangelogCommand;
import me.chaddtheman.mymenu.command.CommandTree;
import me.chaddtheman.mymenu.command.CreateCommand;
import me.chaddtheman.mymenu.command.DeleteCommand;
import me.chaddtheman.mymenu.command.EditCommand;
import me.chaddtheman.mymenu.command.GiveCommand;
import me.chaddtheman.mymenu.command.HelpCommand;
import me.chaddtheman.mymenu.command.InfoCommand;
import me.chaddtheman.mymenu.command.JoinMenuCommand;
import me.chaddtheman.mymenu.command.ListCommand;
import me.chaddtheman.mymenu.command.NameCommand;
import me.chaddtheman.mymenu.command.OpenCommand;
import me.chaddtheman.mymenu.command.ReloadCommand;
import me.chaddtheman.mymenu.command.SaveCommand;
import me.chaddtheman.mymenu.command.SetCommand;
import me.chaddtheman.mymenu.command.UnsetCommand;
import me.chaddtheman.mymenu.command.UpdateCommand;
import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.listener.InventoryClickListener;
import me.chaddtheman.mymenu.listener.InventoryCloseListener;
import me.chaddtheman.mymenu.listener.InventoryDragListener;
import me.chaddtheman.mymenu.listener.PlayerInteractListener;
import me.chaddtheman.mymenu.listener.PlayerQuitListener;
import me.chaddtheman.mymenu.render.ItemBuilder;
import me.chaddtheman.mymenu.render.MenuRenderer;
import me.chaddtheman.mymenu.render.TokenReplacer;
import me.chaddtheman.mymenu.service.CooldownStore;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.session.SessionManager;
import me.chaddtheman.mymenu.storage.DebouncedMenuWriter;
import me.chaddtheman.mymenu.storage.ItemSerializer;
import me.chaddtheman.mymenu.storage.YamlMenuStorage;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Plugin entry point.
 *
 * <p>This instance is the root owner of all runtime state: registries, sessions, storage and
 * services are fields here or on objects reachable from here, never {@code static}. 1.x kept
 * its registries and its in-progress editor conversation in static fields, which let two admins
 * editing at once corrupt each other's work.
 *
 * <h2>Enabling without touching the disk</h2>
 *
 * {@code onEnable} builds the services and returns; it reads nothing. Config and menus load on
 * other threads, and the result is installed on the main thread a tick or so later. Until then
 * {@code MenuService} refuses every edit, because anything changed in that window would be
 * replaced by the load when it landed. If loading fails outright, it simply stays that way:
 * nothing is editable, and nothing is overwritten.
 *
 * <h2>Disabling leaves nothing running</h2>
 *
 * {@code onDisable} writes the pending batch, then stops storage's thread and waits for it, with
 * a timeout. This is the only place the plugin blocks the main thread on I/O. It is not
 * optional: {@code /bukkit:reload} starts a fresh copy of the plugin moments later in the same
 * JVM, and a storage thread left over from this copy would go on writing the same files
 * (DECISIONS #59).
 */
public final class MyMenu extends JavaPlugin {

    private @Nullable YamlMenuStorage storage;
    private @Nullable DebouncedMenuWriter writer;
    private @Nullable MenuService menuService;
    private @Nullable SessionManager sessions;

    // Written on the main thread by load and reload, and by a joinmenu write from another thread.
    private final AtomicReference<PluginConfig> config = new AtomicReference<>(PluginConfig.DEFAULTS);
    // Chains config.yml writes so two quick joinmenu commands land in order. Main thread only.
    private CompletableFuture<Void> configWrites = CompletableFuture.completedFuture(null);

    @Override
    public void onEnable() {
        Logger logger = getSLF4JLogger();
        // Dropped once disabled: the scheduler rejects a disabled plugin's tasks, and nothing that
        // arrives that late has anywhere useful to go.
        Executor mainThread = task -> {
            if (isEnabled()) {
                getServer().getScheduler().runTask(this, task);
            }
        };

        // Built with SPEC's defaults; applyConfig replaces them before the first load parses anything.
        ActionParser actions = new ActionParser(logger, ActionParser.DEFAULT_MAX_TOTAL_DELAY_SECONDS);
        YamlMenuStorage storage = new YamlMenuStorage(getDataPath(), actions, mainThread, logger);
        DebouncedMenuWriter writer = new DebouncedMenuWriter(this, storage, mainThread, logger);
        MenuService menuService = new MenuService(writer);
        this.storage = storage;
        this.writer = writer;
        this.menuService = menuService;

        // One renderer for the plugin's lifetime: its log-once set is per instance, so a renderer
        // per open would repeat every unreadable-item warning on every open.
        ItemBuilder items = new ItemBuilder();
        MenuRenderer renderer = new MenuRenderer(this, items, viewer -> TokenReplacer.NONE, logger);
        SessionManager sessions = new SessionManager(this, menuService.registry(), renderer);
        this.sessions = sessions;

        // TODO(stage 9): TextService replaces the parsing-only resolver with one that substitutes.
        ActionExecutor executor = new ActionExecutor(this, sessions, new CooldownStore(),
                ActionTextResolver.parsingOnly(items::parse), logger, ActionExecutor.DEFAULT_MAX_DEPTH);
        Consumer<PluginConfig> applyConfig = loaded -> applyConfig(loaded, storage, writer, actions, executor);

        PlayerInteractListener interact =
                new PlayerInteractListener(this, menuService.registry(), sessions, items, logger);
        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(executor, this);
        plugins.registerEvents(new InventoryClickListener(this, menuService.registry(), sessions,
                executor::dispatch), this);
        plugins.registerEvents(new InventoryDragListener(), this);
        plugins.registerEvents(new InventoryCloseListener(sessions), this);
        plugins.registerEvents(interact, this);
        plugins.registerEvents(new PlayerQuitListener(sessions), this);

        ReloadCommand reload = new ReloadCommand(this, mainThread, storage, writer, menuService, sessions, executor,
                config, applyConfig);
        CommandTree commands = new CommandTree(logger, menuService, reload::isRunning, new CommandTree.Subcommands(
                new HelpCommand(),
                new ListCommand(menuService.registry()),
                new InfoCommand(config::get),
                new OpenCommand(this, sessions),
                new EditCommand(this, sessions),
                new CreateCommand(menuService),
                new DeleteCommand(this, menuService, sessions, config::get),
                new SetCommand(menuService, new ItemSerializer(items), interact.boundItemKey()),
                new UnsetCommand(menuService),
                new GiveCommand(items, interact.boundItemKey()),
                new JoinMenuCommand(this::writeJoinMenu, mainThread),
                new NameCommand(items),
                new SaveCommand(menuService, writer, mainThread),
                reload,
                new ChangelogCommand(this, mainThread),
                new UpdateCommand(this)));
        // Fires again on every datapack reload, so register must be safe to repeat.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        CompletableFuture.supplyAsync(() -> PluginConfig.load(getDataPath(), logger))
                .thenComposeAsync(loaded -> {
                    config.set(loaded);
                    if (loaded.storageType() != PluginConfig.StorageType.YAML) {
                        logger.warn("storage.type {} is not available in this build; using YAML", loaded.storageType());
                    }
                    applyConfig.accept(loaded);
                    return storage.loadAll();
                }, mainThread)
                .thenAcceptAsync(menuService::replaceAll, mainThread)
                .exceptionally(failure -> {
                    logger.error("Menus could not be loaded; menu editing stays disabled", failure);
                    return null;
                });
    }

    /**
     * The one place settings reach the objects that cache them, used by enable and by reload.
     * Main thread, because the writer's debounce is main-thread state. It runs before a load, so
     * the delay cap applies to the menus that load parses.
     */
    private static void applyConfig(PluginConfig config, YamlMenuStorage storage, DebouncedMenuWriter writer,
                                    ActionParser actions, ActionExecutor executor) {
        storage.setBackupPolicy(config.backupsKeep(), config.backupsMinInterval());
        writer.setDebounce(config.writeDebounce());
        actions.setMaxTotalDelaySeconds(config.maxTotalDelaySeconds());
        executor.setMaxDepth(config.maxDepth());
    }

    /** Main thread. The future fails if the file could not be written, and then nothing changed. */
    private CompletableFuture<Void> writeJoinMenu(String menuName) {
        CompletableFuture<Void> write = configWrites
                .exceptionally(earlier -> null)
                .thenRunAsync(() -> {
                    try {
                        PluginConfig.writeJoinMenu(getDataPath(), menuName, getSLF4JLogger());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .thenRun(() -> config.updateAndGet(current -> current.withJoinMenu(menuName)));
        configWrites = write;
        return write;
    }

    @Override
    public void onDisable() {
        try {
            // Listeners go with the plugin; a menu left open would become a chest to loot.
            if (sessions != null) {
                sessions.closeAll();
            }
        } finally {
            disableStorage();
        }
    }

    private void disableStorage() {
        try {
            if (writer != null) {
                writer.flush();
            }
        } finally {
            if (storage != null) {
                storage.close();
            }
        }
    }
}
