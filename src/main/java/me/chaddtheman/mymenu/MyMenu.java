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

import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.storage.ActionCodec;
import me.chaddtheman.mymenu.storage.DebouncedMenuWriter;
import me.chaddtheman.mymenu.storage.YamlMenuStorage;
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

        YamlMenuStorage storage = new YamlMenuStorage(getDataPath(), ActionCodec.NONE, mainThread, logger);
        DebouncedMenuWriter writer = new DebouncedMenuWriter(this, storage, mainThread, logger);
        MenuService menuService = new MenuService(writer);
        this.storage = storage;
        this.writer = writer;
        this.menuService = menuService;

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
