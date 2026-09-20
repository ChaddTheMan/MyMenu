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

import me.chaddtheman.mymenu.model.Menu;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Sets {@code joinMenu} in {@code config.yml}, the only runtime write to that file (SPEC §3.4).
 * It exists because the plugin promises that no file ever has to be edited by hand.
 */
public final class JoinMenuCommand {

    public static final CommandSpec SPEC = new CommandSpec("joinmenu", "MyMenu.admin.joinmenu",
            "Choose the menu opened when a player joins, or 'none'.", false, true);

    /** Writes the value to {@code config.yml} off the main thread; empty means none. */
    @FunctionalInterface
    public interface Writer {
        CompletableFuture<Void> write(String menuName);
    }

    private final Writer writer;
    private final Executor mainThread;

    public JoinMenuCommand(Writer writer, Executor mainThread) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    /** @param menu null for {@code none} */
    public void execute(CommandSender sender, @Nullable Menu menu) {
        String name = menu == null ? "" : menu.name();
        writer.write(name).whenCompleteAsync((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                Replies.error(sender, "Could not write config.yml, so nothing changed: " + cause.getMessage());
            } else if (menu == null) {
                Replies.ok(sender, "No join menu is set now.");
            } else {
                Replies.ok(sender, "Menu '" + name + "' is now the join menu.");
            }
        }, mainThread);
    }
}
