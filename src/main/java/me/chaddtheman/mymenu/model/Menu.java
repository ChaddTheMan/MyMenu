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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A menu as authored: data only, never an {@code Inventory}.
 *
 * <h2>Why the model is not the view</h2>
 *
 * A {@code Menu} is what an admin built. An {@code Inventory} is what one player sees of it,
 * at one moment. They are different things, and 1.x's decision to make them one shared
 * {@code Inventory} is what made most of 2.0's features impossible there:
 * <ul>
 *   <li><b>Per-viewer content.</b> View permissions hide items from some players, and wildcards
 *       and placeholders resolve to each player's own values. One shared inventory can only
 *       show one thing.</li>
 *   <li><b>Modes.</b> Edit mode shows items that view mode hides. That is a second picture of
 *       the same data, which again needs the data to live somewhere other than a picture.</li>
 *   <li><b>Storage.</b> Serialising a live UI object ties the file format to whatever the
 *       inventory happened to display, and a database backend has no use for one.</li>
 *   <li><b>Existence without viewers.</b> A menu nobody has open still exists, still saves,
 *       and still answers {@code /mymenu info}.</li>
 * </ul>
 * So the renderer takes {@code (Menu, Player, mode)} and produces a fresh inventory every time,
 * and nothing ever reads menu state back out of an inventory.
 *
 * <h2>Why it is immutable</h2>
 *
 * Every {@code with...} method returns a new {@code Menu}; nothing changes one in place. That
 * is what makes "only {@code MenuService} mutates menus" structurally true rather than a
 * convention: {@code Menu} lives in {@code model/}, {@code MenuService} in {@code service/},
 * and Java's package-private access cannot cross that boundary, so a public setter here
 * would be callable by anyone. With no setters, a change only takes effect when
 * {@code MenuService} puts the new menu into the registry, and it checks the degraded state
 * first. It also means a menu can be handed to the storage writer's thread without copying,
 * because no one can change it underneath the writer (DECISIONS #61).
 *
 * <p>Invariant, checked on every construction: every item's slot is inside the layout.
 *
 * @param title raw text with {@code &} codes unresolved; unrestricted, unlike the name
 * @param rows 1 to 6; stored for every type, but only {@link MenuType#CHEST} uses it
 * @param boundItem null when no item opens this menu
 * @param items by zero-based slot, iterated in slot order
 */
public record Menu(String name,
                   String title,
                   @Nullable String author,
                   @Nullable UUID authorUuid,
                   MenuType type,
                   int rows,
                   @Nullable BoundItem boundItem,
                   boolean giveItemOnJoin,
                   Map<Integer, MenuItem> items) {

    public static final int MIN_ROWS = 1;
    public static final int MAX_ROWS = 6;
    public static final int DEFAULT_ROWS = 3;
    public static final int MAX_NAME_LENGTH = 32;

    // Names become YAML path segments and table keys, so '.' and case variants are excluded.
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1," + MAX_NAME_LENGTH + "}");

    public Menu {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid menu name: " + name);
        }
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows " + rows + " outside " + MIN_ROWS + ".." + MAX_ROWS);
        }
        TreeMap<Integer, MenuItem> copy = new TreeMap<>();
        items.forEach((slot, item) -> copy.put(Objects.requireNonNull(slot), Objects.requireNonNull(item)));
        List<Integer> outside = slotsOutside(copy, type, rows);
        if (!outside.isEmpty()) {
            throw new IllegalArgumentException("slots " + outside + " outside " + type + " x" + rows);
        }
        items = Collections.unmodifiableSortedMap(copy);
    }

    /** A new, empty chest menu. */
    public static Menu create(String name, String title, int rows, @Nullable String author,
                              @Nullable UUID authorUuid) {
        return new Menu(name, title, author, authorUuid, MenuType.CHEST, rows, null, false, Map.of());
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    /** Lowercases user input; the result still has to pass {@link #isValidName}. */
    public static String normalizeName(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    public int size() {
        return type.slots(rows);
    }

    /** Occupied slots that a change to {@code type} and {@code rows} would leave outside the menu. */
    public List<Integer> slotsOutside(MenuType type, int rows) {
        return slotsOutside(items, type, rows);
    }

    private static List<Integer> slotsOutside(Map<Integer, MenuItem> items, MenuType type, int rows) {
        int size = type.slots(rows);
        List<Integer> outside = new ArrayList<>();
        for (int slot : items.keySet()) {
            if (slot < 0 || slot >= size) {
                outside.add(slot);
            }
        }
        return outside;
    }

    public @Nullable MenuItem item(int slot) {
        return items.get(slot);
    }

    public Menu withTitle(String title) {
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, items);
    }

    /** @throws IllegalArgumentException if items would fall outside; check {@link #slotsOutside} first */
    public Menu withLayout(MenuType type, int rows) {
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, items);
    }

    public Menu withBoundItem(@Nullable BoundItem boundItem) {
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, items);
    }

    public Menu withGiveItemOnJoin(boolean giveItemOnJoin) {
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, items);
    }

    /** Places {@code item} at {@code slot}, replacing whatever was there. */
    public Menu withItem(int slot, MenuItem item) {
        TreeMap<Integer, MenuItem> next = new TreeMap<>(items);
        next.put(slot, item);
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, next);
    }

    public Menu withoutItem(int slot) {
        TreeMap<Integer, MenuItem> next = new TreeMap<>(items);
        next.remove(slot);
        return new Menu(name, title, author, authorUuid, type, rows, boundItem, giveItemOnJoin, next);
    }
}
