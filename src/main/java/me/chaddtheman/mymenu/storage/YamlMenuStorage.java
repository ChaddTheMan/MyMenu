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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Menus in {@code plugins/MyMenu/menus.yml}.
 *
 * <h2>One thread, owned here</h2>
 *
 * All file access runs on a single thread this class creates and shuts down. One thread means
 * writes land in the order they were submitted without any locking, so two quick saves of the
 * same menu cannot overtake each other. It is a plain {@code ExecutorService} rather than
 * Bukkit's scheduler because the scheduler stops accepting work while the plugin disables, which
 * is exactly when pending writes must still go out (ARCHITECTURE §7.4).
 *
 * <p>Loading crosses threads twice: the file is read and parsed as YAML here, the menus are
 * built on the main thread (resolving a material touches the server's item registry, which is
 * not safe to share), and the result comes back here to become the file image.
 *
 * <h2>The file image</h2>
 *
 * YAML holds every menu in one file, so saving one menu means rewriting all of them. Rather than
 * read the registry from this thread, storage keeps its own image of what the file should hold,
 * touched only on this thread: a load fills it, a save or delete updates it and rewrites the
 * file from it. Menus are immutable, so the image shares them with the registry safely.
 *
 * <h2>Write order, and what a failure leaves behind</h2>
 *
 * A write is: render the text; copy the live file into {@code backups/}; write the text to
 * {@code menus.yml.tmp} and force it to disk; atomically move the temp file over the live file.
 * The live file is never opened for writing, so at every instant it holds either the complete
 * old contents or the complete new contents, and it always exists.
 *
 * <p>If any step fails (rendering, the backup, the temp write, or the move), the live file is
 * untouched and still the last good save. The temp file is removed if possible; a leftover one is
 * truncated by the next write and never read. A backup copied before the failure is a genuine
 * copy of the unchanged live file, so it is merely redundant. Storage then marks itself
 * degraded, which makes {@code MenuService} refuse further edits, and remembers that its image
 * holds changes the file does not. {@link #flush} retries that write once, so shutdown still
 * tries to save the work, and {@link #loadAll} refuses to run over it, because re-reading the
 * file would silently throw those changes away. Reload retries the write through
 * {@link #retryUnwritten} before loading, so fixing the fault and reloading keeps the work; when
 * the fault is permanent, {@link #discardUnwritten} is the admin's explicit way out.
 *
 * <p>A backup failure counts as a write failure. Replacing the live file without the backup the
 * admin was promised would quietly remove the safety net, and a disk that cannot take the backup
 * is unlikely to take the temp file either.
 */
public final class YamlMenuStorage implements MenuStorage {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final Path live;
    private final Path temp;
    private final MenuYamlFormat format;
    private final BackupWriter backups;
    private final Executor mainThread;
    private final Logger logger;
    private final ExecutorService io;

    private volatile BackupWriter.Policy backupPolicy = BackupWriter.Policy.DEFAULT;
    private volatile boolean loadDegraded;
    private volatile boolean writeDegraded;

    // Storage thread only.
    private final SortedMap<String, Menu> image = new TreeMap<>();
    private boolean imageUnwritten;

    /**
     * @param mainThread runs a task on the server thread; used for the part of loading that
     *                   builds menus
     */
    public YamlMenuStorage(Path dataDirectory, ActionCodec actions, Executor mainThread, Logger logger) {
        this.live = dataDirectory.resolve("menus.yml");
        this.temp = dataDirectory.resolve("menus.yml.tmp");
        this.format = new MenuYamlFormat(actions);
        this.backups = new BackupWriter(dataDirectory.resolve("backups"), logger);
        this.mainThread = mainThread;
        this.logger = logger;
        this.io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MyMenu-Storage");
            // Never the reason the JVM cannot exit; close() is what waits for it.
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Takes effect from the next save. Safe from any thread. */
    public void setBackupPolicy(int keep, Duration minInterval) {
        backupPolicy = new BackupWriter.Policy(keep, minInterval);
    }

    @Override
    public boolean isDegraded() {
        return loadDegraded || writeDegraded;
    }

    // ---- Loading ------------------------------------------------------------------------

    private record Raw(@Nullable Object tree, List<String> problems) {
    }

    @Override
    public CompletableFuture<Collection<Menu>> loadAll() {
        return CompletableFuture.supplyAsync(this::readFile, io)
                .thenApplyAsync(raw -> {
                    MenuYamlFormat.Result parsed = format.read(raw.tree());
                    List<String> problems = new ArrayList<>(raw.problems());
                    problems.addAll(parsed.problems());
                    return new MenuYamlFormat.Result(parsed.menus(), problems);
                }, mainThread)
                .thenApplyAsync(this::install, io);
    }

    private Raw readFile() {
        if (imageUnwritten) {
            throw new UnwrittenChangesException(
                    "menus.yml has changes that could not be written; refusing to re-read over them");
        }
        String text;
        try {
            text = Files.readString(live, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new Raw(null, List.of());
        } catch (IOException e) {
            return new Raw(null, List.of("the file could not be read (" + e + "), so no menus were loaded"));
        }
        try {
            return new Raw(MenuYamlFormat.parse(text), List.of());
        } catch (YAMLException e) {
            return new Raw(null, List.of("the file is not valid YAML, so no menus were loaded: " + e.getMessage()));
        }
    }

    private Collection<Menu> install(MenuYamlFormat.Result result) {
        image.clear();
        for (Menu menu : result.menus()) {
            image.put(menu.name(), menu);
        }
        // The file and the image now agree, so an earlier write failure no longer applies.
        writeDegraded = false;
        loadDegraded = !result.problems().isEmpty();
        for (String problem : result.problems()) {
            logger.warn("menus.yml: {}", problem);
        }
        if (loadDegraded) {
            logger.error("Loaded {} menu(s) from menus.yml with {} problem(s), listed above. Menu editing is "
                    + "disabled so that nothing skipped can be overwritten. Fix menus.yml, then reload.",
                    result.menus().size(), result.problems().size());
        } else {
            logger.info("Loaded {} menu(s) from menus.yml", result.menus().size());
        }
        return List.copyOf(result.menus());
    }

    // ---- Writing ------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> saveAll(Collection<Menu> menus) {
        List<Menu> snapshot = List.copyOf(menus);
        return CompletableFuture.runAsync(() -> {
            for (Menu menu : snapshot) {
                image.put(menu.name(), menu);
            }
            imageUnwritten = true;
            writeImage(false);
        }, io);
    }

    @Override
    public CompletableFuture<Void> delete(Menu menu) {
        return CompletableFuture.runAsync(() -> {
            try {
                String yaml = MenuYamlFormat.dump(format.write(List.of(menu)));
                Path backup = backups.writeDeleteBackup(menu.name(), yaml);
                logger.info("Backed up deleted menu '{}' to {}", menu.name(), backup.getFileName());
            } catch (IOException | RuntimeException e) {
                writeDegraded = true;
                logger.error("Could not back up deleted menu '{}', so it was left in menus.yml. Menu editing is "
                        + "disabled until this is fixed and the plugin is reloaded.", menu.name(), e);
                throw new CompletionException(e);
            }
            image.remove(menu.name());
            imageUnwritten = true;
            writeImage(false);
        }, io);
    }

    @Override
    public CompletableFuture<Void> retryUnwritten() {
        return CompletableFuture.runAsync(() -> {
            if (imageUnwritten) {
                logger.warn("Retrying the failed write of menus.yml");
                writeImage(false);
            }
        }, io);
    }

    @Override
    public CompletableFuture<Boolean> discardUnwritten() {
        return CompletableFuture.supplyAsync(() -> {
            boolean had = imageUnwritten;
            if (had) {
                // The image itself is left alone: the load that follows replaces it wholesale.
                logger.warn("Discarding menu changes that could not be written, on an admin's request. "
                        + "menus.yml keeps its last good save.");
                imageUnwritten = false;
            }
            return had;
        }, io);
    }

    private void writeImage(boolean lastAttempt) {
        try {
            String text = MenuYamlFormat.dump(format.write(image.values()));
            Files.createDirectories(live.getParent());
            backups.backupBeforeSave(live, backupPolicy);
            writeTemp(text);
            Files.move(temp, live, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            imageUnwritten = false;
        } catch (IOException | RuntimeException e) {
            writeDegraded = true;
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            logger.error("Could not write menus.yml. The file on disk is unchanged and still holds the last good "
                    + "save. " + (lastAttempt
                    ? "This was the last attempt: changes since that save are lost."
                    : "Menu editing is disabled; the unsaved changes are kept in memory and will be retried "
                    + "on shutdown."), e);
            throw new CompletionException(e);
        }
    }

    private void writeTemp(String text) throws IOException {
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            // Without this, a power cut after the move can leave a live file with no contents.
            channel.force(true);
        }
    }

    // ---- Shutdown -----------------------------------------------------------------------

    @Override
    public void flush() {
        Future<?> barrier;
        try {
            barrier = io.submit(() -> {
                if (imageUnwritten) {
                    logger.warn("Retrying the failed write of menus.yml");
                    try {
                        writeImage(true);
                    } catch (CompletionException alreadyLogged) {
                        // writeImage logged the cause; there is nothing further to try.
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            logger.error("Menu storage was already shut down; nothing more can be written");
            return;
        }
        try {
            barrier.get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.error("Menu writes did not finish within {} s. menus.yml keeps its last completed save; "
                    + "later edits may be lost.", SHUTDOWN_TIMEOUT.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Flushing menu storage failed", e.getCause());
        }
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                // A thread left running would keep writing while a reloaded copy of the plugin uses
                // the same files. Interrupting it aborts any temp-file write before the move, so the
                // live file stays intact.
                logger.error("Menu storage did not stop within {} s and was interrupted. menus.yml keeps its "
                        + "last completed save.", SHUTDOWN_TIMEOUT.toSeconds());
                io.shutdownNow();
            } else {
                logger.info("Menu storage stopped");
            }
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
