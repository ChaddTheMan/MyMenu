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
import java.time.Duration;
import java.util.Map;

/**
 * The settings in {@code config.yml} that the plugin reads so far.
 *
 * <p>Read off the main thread, like every other file (CLAUDE.md, hard rule 2), which is why this
 * does not use {@code JavaPlugin#getConfig()}: that reads synchronously wherever it is called.
 * A malformed file or value falls back to the default with a warning and is never rewritten;
 * config is not menu data, so a bad value is no reason to stop anything.
 */
public record PluginConfig(Duration writeDebounce, int backupsKeep, Duration backupsMinInterval) {

    public static final PluginConfig DEFAULTS =
            new PluginConfig(Duration.ofMillis(2000), 10, Duration.ofSeconds(300));

    private static final String FILE = "config.yml";

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
                        DEFAULTS.backupsMinInterval().toSeconds(), logger)));
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

    private static @Nullable Long asLong(Object value) {
        return value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : null;
    }
}
