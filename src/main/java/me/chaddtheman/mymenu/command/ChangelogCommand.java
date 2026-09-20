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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Prints a section of the {@code CHANGELOG.md} bundled in the jar. Only the Keep-a-Changelog
 * shapes that file uses are understood: {@code ## [version]} sections, {@code ###} groups, and
 * {@code -} bullets whose continuation lines are indented.
 */
public final class ChangelogCommand {

    public static final CommandSpec SPEC = new CommandSpec("changelog", "MyMenu.admin.update",
            "Show what changed: the upcoming release, or the version you name.", false, false);

    private static final String FILE = "CHANGELOG.md";

    private record Section(String version, String heading, List<String> lines) {
    }

    private final Plugin plugin;
    private final Executor mainThread;

    public ChangelogCommand(Plugin plugin, Executor mainThread) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    /** @param version null for the first section, which is the upcoming release */
    public void execute(CommandSender sender, @Nullable String version) {
        CompletableFuture.supplyAsync(this::read)
                .thenAcceptAsync(text -> show(sender, parse(text), version), mainThread)
                .exceptionallyAsync(failure -> {
                    plugin.getSLF4JLogger().warn("Could not read the bundled {}", FILE, failure);
                    Replies.error(sender, "The changelog could not be read; the server log says why.");
                    return null;
                }, mainThread);
    }

    private String read() {
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is missing from the jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void show(CommandSender sender, List<Section> sections, @Nullable String version) {
        if (sections.isEmpty()) {
            Replies.error(sender, "The changelog is empty.");
            return;
        }
        Section section = version == null ? sections.getFirst() : sections.stream()
                .filter(candidate -> candidate.version().equalsIgnoreCase(version))
                .findFirst().orElse(null);
        if (section == null) {
            Replies.error(sender, "The changelog has no entry for '" + version + "'. It has: "
                    + sections.stream().map(Section::version).collect(Collectors.joining(", ")) + ".");
            return;
        }
        sender.sendMessage(Component.text("MyMenu " + section.heading(), NamedTextColor.GOLD));
        for (int i = 0; i < section.lines().size(); i++) {
            String line = section.lines().get(i);
            if (line.startsWith("### ")) {
                // Keep a group heading only if a bullet follows it before the next heading.
                boolean hasBullets = i + 1 < section.lines().size() && !section.lines().get(i + 1).startsWith("### ");
                if (hasBullets) {
                    sender.sendMessage(Component.text(line.substring(4), NamedTextColor.YELLOW));
                }
            } else {
                Replies.info(sender, line);
            }
        }
    }

    private static List<Section> parse(String text) {
        List<Section> sections = new ArrayList<>();
        String version = null;
        String heading = null;
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            if (raw.startsWith("## ")) {
                if (version != null) {
                    sections.add(new Section(version, heading, List.copyOf(lines)));
                }
                heading = raw.substring(3).replace("[", "").replace("]", "").trim();
                int open = raw.indexOf('[');
                int close = raw.indexOf(']');
                version = open >= 0 && close > open ? raw.substring(open + 1, close) : heading;
                lines.clear();
            } else if (version == null || raw.isBlank() || raw.startsWith("---")) {
                continue;
            } else if (raw.startsWith("### ")) {
                lines.add(raw.trim());
            } else if (raw.startsWith("- ")) {
                lines.add("• " + raw.substring(2).trim());
            } else if (!lines.isEmpty() && raw.startsWith(" ")) {
                lines.set(lines.size() - 1, lines.getLast() + " " + raw.trim());
            } else {
                lines.add(raw.trim());
            }
        }
        if (version != null) {
            sections.add(new Section(version, heading, List.copyOf(lines)));
        }
        return sections;
    }
}
