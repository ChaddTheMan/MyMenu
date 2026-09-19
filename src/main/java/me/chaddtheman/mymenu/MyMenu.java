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
import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.listener.InventoryClickListener;
import me.chaddtheman.mymenu.listener.InventoryCloseListener;
import me.chaddtheman.mymenu.listener.InventoryDragListener;
import me.chaddtheman.mymenu.listener.PlayerInteractListener;
import me.chaddtheman.mymenu.listener.PlayerQuitListener;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.render.ItemBuilder;
import me.chaddtheman.mymenu.render.MenuRenderer;
import me.chaddtheman.mymenu.render.TokenReplacer;
import me.chaddtheman.mymenu.service.CooldownStore;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.session.SessionManager;
import me.chaddtheman.mymenu.storage.DebouncedMenuWriter;
import me.chaddtheman.mymenu.storage.YamlMenuStorage;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    private record Loaded(PluginConfig config, Collection<Menu> menus) {
    }

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

        // TODO(stage 7): actions.maxTotalDelaySeconds and navigation.maxDepth come from config.yml
        // once PluginConfig reads them; until then these are SPEC's defaults (§9.2, §9.3).
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

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(executor, this);
        plugins.registerEvents(new InventoryClickListener(this, menuService.registry(), sessions,
                executor::dispatch), this);
        plugins.registerEvents(new InventoryDragListener(), this);
        plugins.registerEvents(new InventoryCloseListener(sessions), this);
        plugins.registerEvents(new PlayerInteractListener(this, menuService.registry(), sessions, items, logger), this);
        plugins.registerEvents(new PlayerQuitListener(sessions), this);

        CompletableFuture.supplyAsync(() -> PluginConfig.load(getDataPath(), logger))
                .thenCompose(config -> {
                    storage.setBackupPolicy(config.backupsKeep(), config.backupsMinInterval());
                    return storage.loadAll().thenApply(menus -> new Loaded(config, menus));
                })
                .thenAcceptAsync(loaded -> {
                    writer.setDebounce(loaded.config().writeDebounce());
                    menuService.replaceAll(loaded.menus());
                }, mainThread)
                .exceptionally(failure -> {
                    logger.error("Menus could not be loaded; menu editing stays disabled", failure);
                    return null;
                });
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
