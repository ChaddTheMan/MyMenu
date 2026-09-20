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

import me.chaddtheman.mymenu.model.BoundItem;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.render.ItemBuilder;
import me.chaddtheman.mymenu.render.TokenReplacer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Hands out a tagged copy of a menu's bound item. The tag ({@code mymenu:bound_item}, the menu
 * name as a string) is what the interact listener checks first, whatever the match mode, and is
 * the only way a {@code TAG_ONLY} menu can be opened by hand.
 */
public final class GiveCommand {

    public static final CommandSpec SPEC = new CommandSpec("give", "MyMenu.admin.menu.give",
            "Give a player a copy of a menu's bound item that always opens it.", false, false);

    private final ItemBuilder items;
    private final NamespacedKey boundItemKey;

    public GiveCommand(ItemBuilder items, NamespacedKey boundItemKey) {
        this.items = Objects.requireNonNull(items, "items");
        this.boundItemKey = Objects.requireNonNull(boundItemKey, "boundItemKey");
    }

    public void execute(CommandSender sender, Menu menu, Player target) {
        BoundItem bound = menu.boundItem();
        if (bound == null) {
            Replies.error(sender, "Menu '" + menu.name() + "' has no bound item. Bind one with /mymenu set "
                    + menu.name() + ".");
            return;
        }
        ItemStack copy;
        try {
            copy = items.build(bound.item(), TokenReplacer.NONE);
        } catch (ItemBuilder.UnreadableItemException e) {
            Replies.error(sender, "The bound item of '" + menu.name() + "' cannot be loaded on this server, so it "
                    + "cannot be handed out.");
            return;
        }
        // One opener, however large the stack was when it was bound.
        copy.setAmount(1);
        copy.editPersistentDataContainer(data -> data.set(boundItemKey, PersistentDataType.STRING, menu.name()));
        target.give(List.of(copy), true);
        Replies.ok(sender, "Gave " + target.getName() + " the item that opens '" + menu.name() + "'.");
        if (sender != target) {
            Replies.info(target, "You received the item that opens menu '" + menu.name() + "'.");
        }
    }
}
