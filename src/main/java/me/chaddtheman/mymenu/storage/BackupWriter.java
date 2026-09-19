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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Writes the two kinds of backup in {@code backups/} (SPEC §11.6).
 *
 * <p><b>Save backups</b> are copies of the live file taken just before it is replaced. They are
 * rate-limited and pruned: at most one per {@code minInterval}, at most {@code keep} retained.
 * Without the rate limit, debounced saves during an ordinary editing session would still push
 * every useful backup out of retention within minutes, which looks like protection and is not.
 *
 * <p><b>Delete backups</b> hold one deleted menu, in the same layout as {@code menus.yml} so it
 * can be pasted back. They are never counted against {@code keep} and never pruned.
 *
 * <p>Timestamps come from file names, not file times: copying preserves the source's
 * modification time on some platforms, and names survive restarts, so the rate limit holds
 * across a restart too. They are UTC, so that name order is time order through daylight-saving
 * changes. Only files that match the save-backup pattern exactly are ever pruned; anything else
 * an admin puts in {@code backups/} is left alone.
 *
 * <p>Storage thread only.
 */
final class BackupWriter {

    /**
     * @param keep save backups retained; 0 turns save backups off. Delete backups are unaffected.
     */
    record Policy(int keep, Duration minInterval) {

        static final Policy DEFAULT = new Policy(10, Duration.ofSeconds(300));

        Policy {
            if (keep < 0) {
                throw new IllegalArgumentException("keep is negative");
            }
            if (minInterval.isNegative()) {
                throw new IllegalArgumentException("minInterval is negative");
            }
        }
    }

    private static final String SAVE_PREFIX = "menus-";
    private static final String DELETE_PREFIX = "deleted-";
    private static final String SUFFIX = ".yml";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final Path directory;
    private final Logger logger;

    BackupWriter(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /**
     * Copies {@code live} into the backups unless one was taken within the policy's interval,
     * then prunes the oldest save backups beyond {@code keep}. Does nothing if {@code live} does
     * not exist yet.
     *
     * @throws IOException if the copy fails; the caller must not replace the live file then
     */
    void backupBeforeSave(Path live, Policy policy) throws IOException {
        if (policy.keep() == 0 || !Files.exists(live)) {
            return;
        }
        Instant now = Instant.now();
        List<Path> existing = saveBackups();
        if (!existing.isEmpty()) {
            Instant newest = stampOf(existing.getLast(), SAVE_PREFIX);
            if (newest != null && Duration.between(newest, now).compareTo(policy.minInterval()) < 0) {
                return;
            }
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(SAVE_PREFIX + STAMP.format(now) + SUFFIX);
        Files.copy(live, target);
        existing.add(target);
        prune(existing, policy.keep());
    }

    /** Writes a standalone backup of one deleted menu, already rendered as file text. */
    Path writeDeleteBackup(String menuName, String yaml) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(DELETE_PREFIX + menuName + "-" + STAMP.format(Instant.now()) + SUFFIX);
        // CREATE_NEW: two deletes of the same name within a millisecond must not share a file.
        Files.writeString(target, yaml, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return target;
    }

    /** Save backups, oldest first. */
    private List<Path> saveBackups() throws IOException {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return found;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, SAVE_PREFIX + "*" + SUFFIX)) {
            for (Path path : stream) {
                if (stampOf(path, SAVE_PREFIX) != null) {
                    found.add(path);
                }
            }
        }
        Collections.sort(found);
        return found;
    }

    private void prune(List<Path> oldestFirst, int keep) {
        for (int i = 0; i < oldestFirst.size() - keep; i++) {
            try {
                Files.deleteIfExists(oldestFirst.get(i));
            } catch (IOException e) {
                // One backup too many is harmless, and no reason to hold up the save.
                logger.warn("Could not prune old backup {}: {}", oldestFirst.get(i).getFileName(), e.toString());
            }
        }
    }

    private static @Nullable Instant stampOf(Path path, String prefix) {
        String name = Objects.toString(path.getFileName(), "");
        if (!name.startsWith(prefix) || !name.endsWith(SUFFIX)) {
            return null;
        }
        try {
            return STAMP.parse(name.substring(prefix.length(), name.length() - SUFFIX.length()), Instant::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
