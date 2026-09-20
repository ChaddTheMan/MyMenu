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
import me.chaddtheman.mymenu.model.ItemTemplate;
import me.chaddtheman.mymenu.model.MatchMode;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.service.MenuService;
import me.chaddtheman.mymenu.service.MutationResult;
import me.chaddtheman.mymenu.storage.ItemSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Binds the item in the player's main hand, captured whole (SPEC §3.3). */
public final class SetCommand {

    public static final CommandSpec SPEC = new CommandSpec("set", "MyMenu.admin.menu.set",
            "Bind the item in your main hand to a menu, so using it opens the menu. Match modes: TAG_ONLY, "
                    + "TYPE, TYPE_AND_NAME (default), EXACT.", true, true);

    private final MenuService menus;
    private final ItemSerializer serializer;
    private final NamespacedKey boundItemKey;

    public SetCommand(MenuService menus, ItemSerializer serializer, NamespacedKey boundItemKey) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.boundItemKey = Objects.requireNonNull(boundItemKey, "boundItemKey");
    }

    public void execute(Player player, Menu menu, MatchMode mode) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            Replies.error(player, "Hold the item to bind in your main hand.");
            return;
        }
        // A copy handed out by give carries the dispensed-item tag. Kept, it would force the
        // capture into the encoded form and make the prototype claim to be a dispensed copy.
        ItemStack prototype = held.clone();
        prototype.editPersistentDataContainer(data -> data.remove(boundItemKey));
        ItemTemplate template = serializer.capture(prototype);
        MutationResult result = menus.update(menu.name(), current -> current.withBoundItem(new BoundItem(template, mode)));
        switch (result) {
            case MutationResult.Applied ignored -> {
                Replies.ok(player, "Bound the item in your hand to '" + menu.name() + "', matched by " + mode + ".");
                if (mode == MatchMode.TAG_ONLY) {
                    Replies.info(player, "Only copies handed out with /mymenu give " + menu.name() + " will open it.");
                }
            }
            case MutationResult.Refused refused -> Replies.error(player, Replies.refusal(refused.reason(), menu.name()));
        }
    }
}
