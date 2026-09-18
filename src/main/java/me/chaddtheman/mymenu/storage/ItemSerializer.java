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
package me.chaddtheman.mymenu.storage;

import org.bukkit.inventory.ItemStack;

/**
 * Converts between an {@code ItemStack} and the bytes held by
 * {@link me.chaddtheman.mymenu.model.ItemTemplate.Opaque}.
 *
 * <p>TODO(stage 3): implement over {@code ItemStack#serializeAsBytes} and
 * {@code ItemStack.deserializeBytes}, both confirmed present on Paper 26.2. That format runs
 * through the game's data converters, so items survive version upgrades; hand-rolled NBT and
 * {@code ConfigurationSerializable} round-trips do not (ARCHITECTURE §7.5). Deciding whether an
 * item fits the descriptive shape also lands here.
 */
public interface ItemSerializer {

    byte[] serialize(ItemStack item);

    ItemStack deserialize(byte[] data);
}
