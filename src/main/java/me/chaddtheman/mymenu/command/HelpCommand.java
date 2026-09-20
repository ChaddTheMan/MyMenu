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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Help pages, printed from the subcommand registry rather than written by hand. 1.x shipped 14 KB
 * of hand-maintained help that documented a command which never existed; here every line is a
 * {@link CommandSpec} description beside a usage string generated from the registered node, so a
 * command cannot appear in help without existing, or exist without appearing.
 */
public final class HelpCommand {

    public static final String ADMIN_PERMISSION = "MyMenu.help.admin";

    public static final CommandSpec SPEC = new CommandSpec("help", "MyMenu.help.player",
            "Show the commands you can use. 'admin' lists every command with its permission; "
                    + "'command <name>' explains one.", false, false);

    /** One subcommand as help shows it. {@code usage} excludes the leading {@code /mymenu}. */
    public record Entry(CommandSpec spec, String usage) {
    }

    /** @param all the admin page: every command with its permission, whether the sender has it or not */
    public void execute(CommandSender sender, List<Entry> entries, boolean all) {
        sender.sendMessage(Component.text(all ? "MyMenu commands (all):" : "MyMenu commands:", NamedTextColor.GOLD));
        for (Entry entry : entries) {
            Component line = Component.text("/mymenu " + entry.usage(), NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.suggestCommand("/mymenu " + entry.spec().name() + " "))
                    .append(Component.text(" - " + entry.spec().description(), NamedTextColor.GRAY));
            if (all) {
                line = line.append(Component.text(" [" + entry.spec().permission() + "]", NamedTextColor.DARK_GRAY));
            }
            sender.sendMessage(line);
        }
        if (!all) {
            Replies.info(sender, "A menu with a bound item opens when you use that item; no command is needed.");
        }
    }

    public void executeOne(CommandSender sender, Entry entry) {
        CommandSpec spec = entry.spec();
        sender.sendMessage(Component.text("/mymenu " + entry.usage(), NamedTextColor.GOLD));
        Replies.info(sender, spec.description());
        Replies.info(sender, "Permission: " + String.join(" or ", spec.permissions()));
        if (spec.playerOnly()) {
            Replies.info(sender, "Only a player can use it.");
        }
        if (spec.mutating()) {
            Replies.info(sender, "Refused while menu storage is degraded.");
        }
    }
}
