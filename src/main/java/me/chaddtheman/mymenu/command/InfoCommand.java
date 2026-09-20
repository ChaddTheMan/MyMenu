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

import me.chaddtheman.mymenu.config.PluginConfig;
import me.chaddtheman.mymenu.model.BoundItem;
import me.chaddtheman.mymenu.model.ItemTemplate;
import me.chaddtheman.mymenu.model.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.function.Supplier;

public final class InfoCommand {

    public static final CommandSpec SPEC = new CommandSpec("info", "MyMenu.admin.menu.info",
            "Show a menu's title, layout, items and bound item.", false, false);

    private final Supplier<PluginConfig> config;

    public InfoCommand(Supplier<PluginConfig> config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void execute(CommandSender sender, Menu menu) {
        sender.sendMessage(Component.text("Menu '" + menu.name() + "'", NamedTextColor.GOLD));
        Replies.info(sender, "Title: " + menu.title());
        Replies.info(sender, "Layout: " + menu.type()
                + (menu.type().usesRows() ? ", " + menu.rows() + " row(s)" : "") + ", " + menu.size() + " slots");
        Replies.info(sender, "Items: " + menu.items().size());
        Replies.info(sender, "Author: " + (menu.author() == null ? "unknown" : menu.author()));
        BoundItem bound = menu.boundItem();
        Replies.info(sender, "Bound item: " + (bound == null ? "none"
                : describe(bound.item()) + ", matched by " + bound.matchMode()));
        Replies.info(sender, "Given on join: " + (menu.giveItemOnJoin() ? "yes" : "no"));
        Replies.info(sender, "Join menu: " + (menu.name().equals(config.get().joinMenu()) ? "yes" : "no"));
    }

    private static String describe(ItemTemplate item) {
        return switch (item) {
            case ItemTemplate.Descriptive descriptive -> descriptive.material().getKey().getKey();
            case ItemTemplate.Opaque ignored -> "an encoded item";
        };
    }
}
