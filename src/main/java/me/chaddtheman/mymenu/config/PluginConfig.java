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
package me.chaddtheman.mymenu.config;

import me.chaddtheman.mymenu.model.Menu;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings in {@code config.yml} that the plugin reads so far.
 *
 * <p>Read off the main thread, like every other file (CLAUDE.md, hard rule 2), which is why this
 * does not use {@code JavaPlugin#getConfig()}: that reads synchronously wherever it is called.
 * A malformed file or value falls back to the default with a warning and is never rewritten;
 * config is not menu data, so a bad value is no reason to stop anything. The one exception is
 * {@link #writeJoinMenu}, the only runtime writer of this file (SPEC §3.4).
 *
 * @param maxDepth passed on unclamped; the executor clamps it and warns, so the range lives in
 *                 one place
 * @param joinMenu a valid menu name, or empty for none
 * @param storageType what the file asks for. The backend is chosen once, at enable; reload
 *                    compares this so it can say that a change needs a restart.
 */
public record PluginConfig(Duration writeDebounce, int backupsKeep, Duration backupsMinInterval,
                           int maxTotalDelaySeconds, int maxDepth, String joinMenu, StorageType storageType) {

    public enum StorageType {
        YAML,
        MYSQL
    }

    public static final PluginConfig DEFAULTS = new PluginConfig(Duration.ofMillis(2000), 10,
            Duration.ofSeconds(300), 30, 10, "", StorageType.YAML);

    private static final String FILE = "config.yml";

    // A top-level joinMenu line, keeping any trailing comment. Written values are menu names,
    // which never contain a quote or '#', so a quoted-or-bare value pattern is enough.
    private static final Pattern JOIN_MENU_LINE = Pattern.compile(
            "(?m)^joinMenu[ \\t]*:[ \\t]*(?:'[^'\\r\\n]*'|\"[^\"\\r\\n]*\"|[^#\\r\\n]*?)([ \\t]+#[^\\r\\n]*)?[ \\t]*$");

    public PluginConfig withJoinMenu(String joinMenu) {
        return new PluginConfig(writeDebounce, backupsKeep, backupsMinInterval, maxTotalDelaySeconds, maxDepth,
                joinMenu, storageType);
    }

    public PluginConfig withStorageType(StorageType storageType) {
        return new PluginConfig(writeDebounce, backupsKeep, backupsMinInterval, maxTotalDelaySeconds, maxDepth,
                joinMenu, storageType);
    }

    /** Reads {@code config.yml}, first writing the bundled default if there is none. Blocking. */
    public static PluginConfig load(Path dataDirectory, Logger logger) {
        Path file = dataDirectory.resolve(FILE);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            text = writeDefault(file, logger);
        } catch (IOException e) {
            logger.warn("Could not read {}; using defaults: {}", FILE, e.toString());
            return DEFAULTS;
        }
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(text);
        } catch (YAMLException e) {
            logger.warn("{} is not valid YAML; using defaults: {}", FILE, e.getMessage());
            return DEFAULTS;
        }
        Map<?, ?> top = root instanceof Map<?, ?> map ? map : Map.of();
        return new PluginConfig(
                Duration.ofMillis(read(top, "storage", "writeDebounceMillis",
                        DEFAULTS.writeDebounce().toMillis(), logger)),
                (int) read(top, "backups", "keep", DEFAULTS.backupsKeep(), logger),
                Duration.ofSeconds(read(top, "backups", "minIntervalSeconds",
                        DEFAULTS.backupsMinInterval().toSeconds(), logger)),
                (int) read(top, "actions", "maxTotalDelaySeconds", DEFAULTS.maxTotalDelaySeconds(), logger),
                (int) read(top, "navigation", "maxDepth", DEFAULTS.maxDepth(), logger),
                readJoinMenu(top.get("joinMenu"), logger),
                readStorageType(top.get("storage") instanceof Map<?, ?> map ? map.get("type") : null, logger));
    }

    /**
     * Sets {@code joinMenu} in {@code config.yml}, leaving every other line, comments included,
     * as it was. Blocking; call off the main thread. Writes a temp file and moves it over the
     * live one, so a failure part-way leaves the old file whole.
     *
     * @param menuName a valid menu name, or empty to disable
     */
    public static void writeJoinMenu(Path dataDirectory, String menuName, Logger logger) throws IOException {
        if (!menuName.isEmpty() && !Menu.isValidName(menuName)) {
            throw new IllegalArgumentException("invalid menu name: " + menuName);
        }
        Path file = dataDirectory.resolve(FILE);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            text = writeDefault(file, logger);
        }
        String line = "joinMenu: '" + menuName + "'";
        Matcher matcher = JOIN_MENU_LINE.matcher(text);
        String updated;
        if (matcher.find()) {
            String comment = matcher.group(1) == null ? "" : matcher.group(1);
            updated = text.substring(0, matcher.start()) + line + comment + text.substring(matcher.end());
        } else {
            updated = text + (text.isEmpty() || text.endsWith("\n") ? "" : System.lineSeparator())
                    + line + System.lineSeparator();
        }
        Path temp = file.resolveSibling(FILE + ".tmp");
        Files.writeString(temp, updated, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String writeDefault(Path file, Logger logger) {
        try (InputStream in = PluginConfig.class.getResourceAsStream("/" + FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is missing from the jar");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return text;
        } catch (IOException e) {
            logger.warn("Could not write the default {}; using defaults: {}", FILE, e.toString());
            return "";
        }
    }

    /** A non-negative whole number at {@code section.key}, or the fallback with a warning. */
    private static long read(Map<?, ?> top, String section, String key, long fallback, Logger logger) {
        Object value = top.get(section) instanceof Map<?, ?> map ? map.get(key) : null;
        if (value == null) {
            return fallback;
        }
        Long number = asLong(value);
        if (number == null || number < 0 || number > Integer.MAX_VALUE) {
            logger.warn("{}: {}.{} must be a whole number from 0 up, not '{}'; using {}",
                    FILE, section, key, value, fallback);
            return fallback;
        }
        return number;
    }

    private static String readJoinMenu(@Nullable Object value, Logger logger) {
        if (value == null) {
            return "";
        }
        String name = Menu.normalizeName(value.toString().trim());
        if (!name.isEmpty() && !Menu.isValidName(name)) {
            logger.warn("{}: joinMenu '{}' is not a valid menu name; treating it as empty", FILE, value);
            return "";
        }
        return name;
    }

    private static StorageType readStorageType(@Nullable Object value, Logger logger) {
        if (value == null) {
            return DEFAULTS.storageType();
        }
        try {
            return StorageType.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warn("{}: storage.type must be YAML or MYSQL, not '{}'; using {}", FILE, value,
                    DEFAULTS.storageType());
            return DEFAULTS.storageType();
        }
    }

    private static @Nullable Long asLong(Object value) {
        return value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : null;
    }
}
