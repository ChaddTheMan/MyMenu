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
package me.chaddtheman.mymenu.service;

import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.model.MenuType;
import me.chaddtheman.mymenu.service.MutationResult.Applied;
import me.chaddtheman.mymenu.service.MutationResult.Reason;
import me.chaddtheman.mymenu.service.MutationResult.Refused;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The only way to change a menu: the gate every mutation passes through.
 *
 * <h2>Why the degraded check happens here, before the model</h2>
 *
 * When storage is degraded (a menu failed to parse at load, or a write failed), saving is
 * unsafe: a save would rewrite the file without the menus that failed to load, and those are
 * gone. So mutations must stop. The question is <em>where</em>.
 *
 * <p>Refusing at the storage boundary is too late. By then the registry already holds the
 * edit. The player sees it take effect, nothing is written, and the next reload discards it
 * without anyone being told. That is exactly the silent data loss the degraded state exists to
 * prevent. So every method here checks {@link MenuPersistence#isDegraded()} <em>first</em> and
 * refuses before building the new menu. A refused edit never appears to have worked.
 *
 * <p>Reads are never gated: {@link #registry()} keeps working, so players can still use every
 * menu that did load (SPEC §12).
 *
 * <h2>Order within a mutation</h2>
 *
 * Check degraded, compute the new menu, put it in the registry (which issues the new revision),
 * then tell persistence it is dirty. Computing happens before anything is committed, so a
 * change that throws leaves the registry exactly as it was. Persistence is told last and only
 * records intent; the debounced writer decides when to write.
 *
 * <p>Main thread only. That is checked rather than assumed, because a mutation from an async
 * chat handler that forgot to hop back would otherwise work almost every time.
 */
public final class MenuService {

    private final MenuRegistry registry = new MenuRegistry();
    private final MenuPersistence persistence;

    public MenuService(MenuPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    /** Read access for everyone else. Always available, degraded or not. */
    public MenuRegistry registry() {
        return registry;
    }

    public boolean isDegraded() {
        return persistence.isDegraded();
    }

    /**
     * @param name must already be normalised and valid; the command layer rejects bad names
     *             with a proper message before they get here
     */
    public MutationResult create(String name, String title, int rows, @Nullable String author,
                                 @Nullable UUID authorUuid) {
        requireMainThread();
        if (persistence.isDegraded()) {
            return new Refused(Reason.STORAGE_DEGRADED);
        }
        if (registry.contains(name)) {
            return new Refused(Reason.MENU_EXISTS);
        }
        return commit(Menu.create(name, title, rows, author, authorUuid));
    }

    public MutationResult delete(String name) {
        requireMainThread();
        if (persistence.isDegraded()) {
            return new Refused(Reason.STORAGE_DEGRADED);
        }
        Optional<Menu> removed = registry.remove(name);
        if (removed.isEmpty()) {
            return new Refused(Reason.NO_SUCH_MENU);
        }
        persistence.markDeleted(removed.get());
        return new Applied(removed.get());
    }

    /**
     * Applies {@code change} to the current version of the named menu.
     *
     * <p>A change that returns an equal menu commits nothing: no new revision, no write, so a
     * no-op click in the editor does not invalidate other players' open views.
     *
     * @throws IllegalArgumentException if {@code change} renames the menu (the name is the
     *         registry key; renaming would need to be its own operation) or builds an invalid
     *         menu. Layout changes that may strand items go through {@link #changeLayout}.
     */
    public MutationResult update(String name, UnaryOperator<Menu> change) {
        requireMainThread();
        if (persistence.isDegraded()) {
            return new Refused(Reason.STORAGE_DEGRADED);
        }
        Optional<Menu> current = registry.find(name);
        if (current.isEmpty()) {
            return new Refused(Reason.NO_SUCH_MENU);
        }
        Menu next = Objects.requireNonNull(change.apply(current.get()), "change returned null");
        if (!next.name().equals(name)) {
            throw new IllegalArgumentException("update may not rename " + name + " to " + next.name());
        }
        if (next.equals(current.get())) {
            return new Applied(current.get());
        }
        return commit(next);
    }

    /** Changes type and rows, refusing if any occupied slot would fall outside the new layout. */
    public MutationResult changeLayout(String name, MenuType type, int rows) {
        requireMainThread();
        if (persistence.isDegraded()) {
            return new Refused(Reason.STORAGE_DEGRADED);
        }
        Optional<Menu> current = registry.find(name);
        if (current.isEmpty()) {
            return new Refused(Reason.NO_SUCH_MENU);
        }
        if (!current.get().slotsOutside(type, rows).isEmpty()) {
            return new Refused(Reason.ITEMS_OUTSIDE_LAYOUT);
        }
        return update(name, menu -> menu.withLayout(type, rows));
    }

    private MutationResult commit(Menu menu) {
        registry.put(menu);
        persistence.markDirty(menu.name());
        return new Applied(menu);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("menus may only be changed on the main thread");
        }
    }
}
