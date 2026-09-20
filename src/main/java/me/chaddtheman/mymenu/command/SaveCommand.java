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

import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.storage.DebouncedMenuWriter;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Writes the pending batch now instead of when the debounce timer fires (SPEC §3.6). */
public final class SaveCommand {

    public static final CommandSpec SPEC = new CommandSpec("save", "MyMenu.admin.save",
            "Write pending menu changes now instead of a moment later.", false, false);

    private final MenuService menus;
    private final DebouncedMenuWriter writer;
    private final Executor mainThread;

    public SaveCommand(MenuService menus, DebouncedMenuWriter writer, Executor mainThread) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    public void execute(CommandSender sender) {
        // SPEC §12: while degraded, save reports the condition instead of writing.
        if (menus.isDegraded()) {
            Replies.error(sender, "Nothing was saved. " + Replies.DEGRADED);
            return;
        }
        writer.flushAsync().whenCompleteAsync((count, failure) -> {
            if (failure != null) {
                Replies.error(sender, "Saving failed; the server log says why. Menu editing is disabled until it "
                        + "is fixed and the plugin is reloaded.");
            } else if (count == 0) {
                Replies.info(sender, "Nothing to save: every change was already written.");
            } else {
                Replies.ok(sender, "Saved " + count + " menu change(s).");
            }
        }, mainThread);
    }
}
