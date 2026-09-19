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

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import me.chaddtheman.mymenu.model.ItemTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts items to and from the forms menus store them in.
 *
 * <h2>Opaque bytes</h2>
 *
 * {@link #serialize} and {@link #deserialize} use Paper's byte-array format. It runs through the
 * game's own data converters, so a stored item survives a Minecraft upgrade; hand-rolled NBT and
 * {@code ConfigurationSerializable} round-trips both lose data on modern items (ARCHITECTURE
 * §7.5).
 *
 * <h2>Choosing a shape: {@link #capture}</h2>
 *
 * SPEC §6 stores an item in the readable form when those fields express it fully, and as bytes
 * otherwise. "Fully" is decided by a round trip, not by a list of things the readable form
 * cannot hold: build the readable candidate, turn it back into an {@code ItemStack}, and keep
 * the candidate only if that stack {@code equals} the original. {@code ItemStack#equals}
 * compares type, amount and every data component, so anything the candidate dropped (a head
 * texture, a real enchantment, custom model data, a component added in a future version that
 * nobody here has heard of) makes the two unequal, and the item is stored as bytes instead. A
 * list of "unsupported components" would need updating every release and would fail open; this
 * fails closed. A false "no" only costs readability; a false "yes" would lose data, and the
 * round trip cannot produce one.
 *
 * <p>The candidate's text goes through the same {@code &}-code conversion used to read it back,
 * so text that does not survive that conversion (a font, a click event, an explicit
 * {@code italic: false}, a literal {@code &c} in a name) also sends the item to bytes.
 *
 * <p>Main thread only. Deserialising and building stacks reach into server registries.
 */
public final class ItemSerializer {

    /**
     * The prefix that makes stored text MiniMessage (SPEC §10.2). A captured name starting with
     * it would be reinterpreted at render time, so such items go to bytes.
     */
    private static final String MINIMESSAGE_PREFIX = "<!mm>";

    // TODO(stage 9): must stay the conversion TextService applies to readable items, or captured
    // items would render differently from the item they were captured from.
    private final LegacyComponentSerializer legacy =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    public byte[] serialize(ItemStack item) {
        return item.serializeAsBytes();
    }

    public ItemStack deserialize(byte[] data) {
        return ItemStack.deserializeBytes(data);
    }

    /**
     * Turns a real item, typically one from an admin's hand, into a template: readable when the
     * readable fields reproduce it exactly, opaque otherwise.
     */
    public ItemTemplate capture(ItemStack item) {
        Objects.requireNonNull(item, "item");
        if (item.isEmpty()) {
            throw new IllegalArgumentException("cannot capture an empty item");
        }
        ItemTemplate.Descriptive candidate = readableCandidate(item);
        if (candidate != null && toItemStack(candidate).equals(item)) {
            return candidate;
        }
        // A forced glint moves out of the bytes into the glow flag, or turning glow off in the
        // editor would have no visible effect. A suppressed glint (false) stays in the bytes.
        ItemStack stored = item;
        boolean glow = Boolean.TRUE.equals(item.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
        if (glow) {
            stored = item.clone();
            stored.resetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        }
        return new ItemTemplate.Opaque(serialize(stored), glow);
    }

    private ItemTemplate.@Nullable Descriptive readableCandidate(ItemStack item) {
        if (item.getAmount() > ItemTemplate.MAX_AMOUNT || !item.getType().isItem()) {
            return null;
        }
        Component name = item.getData(DataComponentTypes.CUSTOM_NAME);
        String displayName = name == null ? null : legacy.serialize(name);
        if (displayName != null && displayName.startsWith(MINIMESSAGE_PREFIX)) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        ItemLore itemLore = item.getData(DataComponentTypes.LORE);
        if (itemLore != null) {
            for (Component line : itemLore.lines()) {
                String text = legacy.serialize(line);
                if (text.startsWith(MINIMESSAGE_PREFIX)) {
                    return null;
                }
                lore.add(text);
            }
        }
        boolean glow = Boolean.TRUE.equals(item.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
        return new ItemTemplate.Descriptive(item.getType(), displayName, item.getAmount(), lore, glow);
    }

    /** The stack a readable template stands for, before any per-viewer substitution. */
    private ItemStack toItemStack(ItemTemplate.Descriptive template) {
        ItemStack stack = ItemStack.of(template.material(), template.amount());
        if (template.displayName() != null) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, legacy.deserialize(template.displayName()));
        }
        if (!template.lore().isEmpty()) {
            stack.setData(DataComponentTypes.LORE,
                    ItemLore.lore(template.lore().stream().map(legacy::deserialize).toList()));
        }
        if (template.glow()) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}
