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
package me.chaddtheman.mymenu.render;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import me.chaddtheman.mymenu.model.ItemTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Turns an {@link ItemTemplate} into an {@code ItemStack}. The one place that knows what the
 * stored text format means.
 *
 * <h2>One build path, used by render and by capture</h2>
 *
 * {@code ItemSerializer.capture} decides whether an item can be stored readably by building the
 * readable candidate back into a stack and comparing (DECISIONS #66). It calls
 * {@link #build(ItemTemplate.Descriptive, TokenReplacer)} to do that, the same method the
 * renderer uses. If capture had its own copy of the conversion, the two would drift: an item
 * that capture judged readable would render differently from the item it came from. The
 * italic rule (DECISIONS #70) is exactly such a difference, and sharing the method is what keeps
 * it applied on both sides.
 *
 * <h2>Text</h2>
 *
 * Stored text is {@code &} codes with {@code &#RRGGBB} hex, or MiniMessage when it starts with
 * {@code <!mm>} (SPEC §10.2). Item names and lore get italic explicitly disabled at the root
 * unless the text sets it. Minecraft italicises custom names by default, which admins almost
 * never want. Setting it at the root means a run that turns italic on ({@code &o}) still wins.
 *
 * <p>Tokens are replaced <em>after</em> parsing, through a {@link TokenReplacer}; see that
 * interface for why the order matters.
 *
 * <h2>Opaque items</h2>
 *
 * Only the opaque shape can fail, because its bytes are not validated at load (DECISIONS #67).
 * Only {@link #build(ItemTemplate, TokenReplacer)} can reach them, and it declares the checked
 * {@link UnreadableItemException}, so every caller has to decide what a broken slot looks like.
 * Their text is already components and is not re-parsed; tokens in it are still replaced.
 *
 * <p>Glow is the enchantment glint override and is applied last, identically for both shapes.
 *
 * <p>Main thread only: building stacks and deserialising bytes reach into server registries.
 */
public final class ItemBuilder {

    /** Marks stored text as MiniMessage rather than {@code &} codes. */
    public static final String MINIMESSAGE_PREFIX = "<!mm>";

    private final LegacyComponentSerializer legacy =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** Thrown when a stored item's bytes cannot be turned back into an item. */
    public static final class UnreadableItemException extends Exception {

        UnreadableItemException(String message, Throwable cause) {
            super(message, cause);
        }

        UnreadableItemException(String message) {
            super(message);
        }
    }

    /**
     * Parses admin-authored text. Tokens such as {@code {PLAYER}} come out as literal text, ready
     * for a {@link TokenReplacer}. Applies no italic rule, so it suits titles as well as items.
     */
    public Component parse(String raw) {
        if (raw.startsWith(MINIMESSAGE_PREFIX)) {
            return miniMessage.deserialize(raw.substring(MINIMESSAGE_PREFIX.length()));
        }
        return legacy.deserialize(raw);
    }

    /** {@link #parse}, plus the item-text italic rule. */
    public Component parseItemText(String raw) {
        return parse(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * The {@code &}-code form of a component: the inverse of {@link #parse} for text that the
     * format can express. Capture uses it and then checks the round trip, so a lossy result is
     * caught rather than trusted.
     */
    public String toReadableText(Component text) {
        return legacy.serialize(text);
    }

    /** Builds either shape. */
    public ItemStack build(ItemTemplate template, TokenReplacer tokens) throws UnreadableItemException {
        return switch (template) {
            case ItemTemplate.Descriptive descriptive -> build(descriptive, tokens);
            case ItemTemplate.Opaque opaque -> buildOpaque(opaque, tokens);
        };
    }

    /** Builds a readable template. Cannot fail: the constructor already rejected bad materials. */
    public ItemStack build(ItemTemplate.Descriptive template, TokenReplacer tokens) {
        ItemStack stack = ItemStack.of(template.material(), template.amount());
        if (template.displayName() != null) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, tokens.replace(parseItemText(template.displayName())));
        }
        if (!template.lore().isEmpty()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(
                    template.lore().stream().map(line -> tokens.replace(parseItemText(line))).toList()));
        }
        return applyGlow(stack, template.glow());
    }

    private ItemStack buildOpaque(ItemTemplate.Opaque template, TokenReplacer tokens) throws UnreadableItemException {
        ItemStack stack;
        try {
            stack = ItemStack.deserializeBytes(template.data());
        } catch (RuntimeException e) {
            // The failure types are the server's internals (NBT, data fixers, registries), not
            // API. Anything thrown here means the same thing to a caller.
            throw new UnreadableItemException(describe(e), e);
        }
        if (stack.isEmpty()) {
            throw new UnreadableItemException("the stored item is empty");
        }
        Component name = stack.getData(DataComponentTypes.CUSTOM_NAME);
        if (name != null) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, tokens.replace(name));
        }
        ItemLore lore = stack.getData(DataComponentTypes.LORE);
        if (lore != null) {
            List<Component> lines = lore.lines().stream().map(tokens::replace).toList();
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        return applyGlow(stack, template.glow());
    }

    private static ItemStack applyGlow(ItemStack stack, boolean glow) {
        // Only ever forces the glint on. A stored `false` override (glint suppressed) is part of
        // the item and is left alone.
        if (glow) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
