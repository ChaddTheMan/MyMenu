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
import me.chaddtheman.mymenu.service.MutationResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Takes a validated name rather than a {@code Menu}, because the menu does not exist yet; the
 * tree has already lowercased it and checked it against SPEC §3.2's rules. The console may
 * create menus too, and the menu then records no author rather than a made-up one.
 */
public final class CreateCommand {

    public static final CommandSpec SPEC = new CommandSpec("create", "MyMenu.admin.menu.create",
            "Create an empty chest menu. Rows default to 3 and the title to the name; & colour codes work.",
            false, true);

    private final MenuService menus;

    public CreateCommand(MenuService menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    /** @param author the player who ran it, or null from the console, which records no author */
    public void execute(CommandSender sender, @Nullable Player author, String name, int rows, String title) {
        MutationResult result = menus.create(name, title, rows,
                author == null ? null : author.getName(), author == null ? null : author.getUniqueId());
        switch (result) {
            case MutationResult.Applied ignored -> Replies.ok(sender, "Created menu '" + name + "' with " + rows
                    + " row(s)." + (author == null ? "" : " Open it for editing with /mymenu edit " + name + "."));
            case MutationResult.Refused refused -> Replies.error(sender, Replies.refusal(refused.reason(), name));
        }
    }
}
