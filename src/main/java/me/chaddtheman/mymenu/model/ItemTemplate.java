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
package me.chaddtheman.mymenu.model;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The recipe for an item, not the item itself.
 *
 * <p>A template is resolved into an {@code ItemStack} at render time, per viewer, because the
 * same template renders differently for different players: text holds colour codes and
 * wildcards <em>unresolved</em>. Resolving at load time would bake one player's view into
 * everyone's.
 *
 * <p>It is a sealed interface with exactly two shapes, matching the two mutually exclusive
 * {@code item:} forms in SPEC §6:
 * <ul>
 *   <li>{@link Descriptive} — the four readable fields, hand-editable in YAML;</li>
 *   <li>{@link Opaque} — a serialised {@code ItemStack}, for anything those fields cannot hold
 *       (head textures, potion data, real enchantments, custom model data).</li>
 * </ul>
 * Storing both for one item would let them drift, so "sealed, pick one" is the point, and a
 * {@code switch} over a template is checked exhaustively by the compiler.
 *
 * <p>{@code glow} is on both shapes because it is a rendering flag (the enchantment glint
 * override), not item data, so it never forces an item into the opaque form (DECISIONS #54).
 */
public sealed interface ItemTemplate permits ItemTemplate.Descriptive, ItemTemplate.Opaque {

    /** The largest stack size a data component can declare. */
    int MAX_AMOUNT = 99;

    boolean glow();

    ItemTemplate withGlow(boolean glow);

    /**
     * @param displayName raw text with {@code &} codes and wildcards unresolved; null means the
     *                    material's own name
     */
    record Descriptive(Material material, @Nullable String displayName, int amount, List<String> lore,
                       boolean glow) implements ItemTemplate {

        public Descriptive {
            Objects.requireNonNull(material, "material");
            if (material.isAir() || !material.isItem()) {
                throw new IllegalArgumentException(material + " is not an item");
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                throw new IllegalArgumentException("amount " + amount + " outside 1.." + MAX_AMOUNT);
            }
            lore = List.copyOf(lore);
        }

        public static Descriptive of(Material material) {
            return new Descriptive(material, null, 1, List.of(), false);
        }

        public Descriptive withDisplayName(@Nullable String displayName) {
            return new Descriptive(material, displayName, amount, lore, glow);
        }

        public Descriptive withAmount(int amount) {
            return new Descriptive(material, displayName, amount, lore, glow);
        }

        public Descriptive withLore(List<String> lore) {
            return new Descriptive(material, displayName, amount, lore, glow);
        }

        @Override
        public Descriptive withGlow(boolean glow) {
            return new Descriptive(material, displayName, amount, lore, glow);
        }
    }

    /**
     * An item in Paper's byte-array form. The bytes are produced and consumed only by
     * {@link me.chaddtheman.mymenu.storage.ItemSerializer}; the model never interprets them.
     *
     * <p>Arrays are mutable and records compare them by reference, so this record copies on the
     * way in and out and overrides equality. Without that, a caller could alter a stored item
     * behind {@code MenuService}'s back, and an unchanged item would compare as changed.
     */
    record Opaque(byte[] data, boolean glow) implements ItemTemplate {

        public Opaque {
            Objects.requireNonNull(data, "data");
            if (data.length == 0) {
                throw new IllegalArgumentException("serialised item is empty");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public Opaque withGlow(boolean glow) {
            return new Opaque(data, glow);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Opaque other && glow == other.glow && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(data) + Boolean.hashCode(glow);
        }

        @Override
        public String toString() {
            return "Opaque[" + data.length + " bytes, glow=" + glow + "]";
        }
    }
}
