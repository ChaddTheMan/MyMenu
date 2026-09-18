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

import me.chaddtheman.mymenu.action.Action;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One occupied slot: what it looks like, who may see it, and what clicking it does.
 *
 * <p>An empty action list under a key is kept, not normalised away. It is not the same as an
 * absent key: an absent key falls back to {@link ClickKey#OTHER}; an empty list stops there.
 *
 * @param clickSound a sound key such as {@code minecraft:ui.button.click}. A key rather than
 *                   Bukkit's {@code Sound}, which is no longer an enum on 26.2 (DECISIONS #62)
 * @param cooldownSeconds 0 for none
 */
public record MenuItem(ItemTemplate icon,
                       Map<ClickKey, List<Action>> actions,
                       @Nullable String viewPermission,
                       @Nullable ItemTemplate hiddenFallback,
                       @Nullable Key clickSound,
                       int cooldownSeconds) {

    public MenuItem {
        Objects.requireNonNull(icon, "icon");
        if (viewPermission != null && viewPermission.isBlank()) {
            throw new IllegalArgumentException("viewPermission is blank; use null for none");
        }
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds is negative");
        }
        // EnumMap keeps click keys in declaration order, so saved files come out stable.
        EnumMap<ClickKey, List<Action>> copy = new EnumMap<>(ClickKey.class);
        actions.forEach((key, list) -> copy.put(Objects.requireNonNull(key), List.copyOf(list)));
        actions = Collections.unmodifiableMap(copy);
    }

    public static MenuItem of(ItemTemplate icon) {
        return new MenuItem(icon, Map.of(), null, null, null, 0);
    }

    public MenuItem withIcon(ItemTemplate icon) {
        return new MenuItem(icon, actions, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }

    /** Replaces the list under {@code key}; a null list removes the key entirely. */
    public MenuItem withActions(ClickKey key, @Nullable List<Action> list) {
        EnumMap<ClickKey, List<Action>> next = new EnumMap<>(ClickKey.class);
        next.putAll(actions);
        if (list == null) {
            next.remove(key);
        } else {
            next.put(key, list);
        }
        return new MenuItem(icon, next, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }

    public MenuItem withViewPermission(@Nullable String viewPermission) {
        return new MenuItem(icon, actions, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }

    public MenuItem withHiddenFallback(@Nullable ItemTemplate hiddenFallback) {
        return new MenuItem(icon, actions, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }

    public MenuItem withClickSound(@Nullable Key clickSound) {
        return new MenuItem(icon, actions, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }

    public MenuItem withCooldownSeconds(int cooldownSeconds) {
        return new MenuItem(icon, actions, viewPermission, hiddenFallback, clickSound, cooldownSeconds);
    }
}
