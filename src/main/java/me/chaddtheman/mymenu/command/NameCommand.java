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

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.chaddtheman.mymenu.render.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Renames the item in hand, for preparing icons and bound items. Parsed the way menu item text is,
 * so a name set here looks the same once the item is placed in a menu.
 */
public final class NameCommand {

    public static final CommandSpec SPEC = new CommandSpec("name", "MyMenu.admin.item.name",
            "Rename the item in your main hand; & colour codes work.", true, false);

    private final ItemBuilder items;

    public NameCommand(ItemBuilder items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    public void execute(Player player, String name) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            Replies.error(player, "Hold the item to rename in your main hand.");
            return;
        }
        ItemStack renamed = held.clone();
        renamed.setData(DataComponentTypes.CUSTOM_NAME, items.parseItemText(name));
        player.getInventory().setItemInMainHand(renamed);
        Replies.ok(player, "Renamed the item in your hand.");
    }
}
