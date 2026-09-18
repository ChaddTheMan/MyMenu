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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Every loaded menu, by name, and the revision each one was last written at.
 *
 * <h2>Reads are public; writes belong to {@code MenuService}</h2>
 *
 * The write methods are package-private and the constructor is too, so only code in
 * {@code service/} can change what is registered, and {@code MenuService} is the one that
 * does. Combined with immutable {@code Menu} objects, that is the whole enforcement: there is
 * no other door. Keep {@code service/} small for that reason.
 *
 * <h2>Why the revision counter is plugin-wide, not per-menu</h2>
 *
 * The model is not the view (see {@code Menu}), so a rendered inventory is a picture that can
 * go out of date. When a player clicks one, the question is "is what this player is looking
 * at still what the menu says?" The inventory's holder records the menu's <em>name</em> and
 * the revision at render time; on click, the name is looked up here.
 * <ul>
 *   <li>Name absent: the menu was deleted.</li>
 *   <li>Name present, different revision: the menu changed since the picture was taken.</li>
 * </ul>
 * A per-menu counter, the obvious alternative, fails in two ways.
 * <ol>
 *   <li><b>Delete and reload are invisible to it.</b> If the holder keeps the {@code Menu}
 *       object and asks it for its counter, deleting or reloading leaves that old object
 *       untouched. Its number still matches itself, so a click on a deleted menu succeeds.</li>
 *   <li><b>Numbers get reissued.</b> Delete {@code shop} at revision 4 and create a new
 *       {@code shop}; its counter starts again and reaches 4 after a few edits. A player still
 *       looking at the old {@code shop} now passes the check and triggers whatever sits in the
 *       new one's slot.</li>
 * </ol>
 * A single counter that only ever increases fixes both. Every write, to any menu, takes the
 * next number, so no number is ever issued twice while the plugin is running. A recreated or
 * reloaded menu always carries a number no old picture can hold. Looking up by name rather
 * than holding the object is what makes deletion visible.
 *
 * <p>The revision is kept here rather than on {@code Menu} because it is bookkeeping about
 * this running instance, not menu data: it is never saved, and two equal menus loaded at
 * different times must still have different revisions.
 *
 * <h2>Threading</h2>
 *
 * Writes happen on the main thread only (MenuService checks). Reads are safe from
 * <em>any</em> thread: each write builds a new immutable snapshot and publishes it through one
 * {@code volatile} field, so a reader sees either the old state or the new one, never half of
 * a write. Menus are immutable, so the snapshot needs no deeper copy. Menu counts are small,
 * so copying the map on each write is cheap. In exchange, the storage writer and any
 * off-thread command suggestions can read the registry without locks (ARCHITECTURE §7, §8).
 */
public final class MenuRegistry {

    private record State(SortedMap<String, Menu> menus, Map<String, Long> revisions) {

        // Copies here rather than at the call sites, so a snapshot cannot alias a working map.
        State {
            menus = Collections.unmodifiableSortedMap(new TreeMap<>(menus));
            revisions = Map.copyOf(revisions);
        }
    }

    private volatile State state = new State(Collections.emptySortedMap(), Map.of());

    // Only ever touched by the writer on the main thread, so it needs no volatile.
    private long lastRevision;

    MenuRegistry() {
    }

    public Optional<Menu> find(String name) {
        return Optional.ofNullable(state.menus().get(name));
    }

    public OptionalLong revision(String name) {
        Long revision = state.revisions().get(name);
        return revision == null ? OptionalLong.empty() : OptionalLong.of(revision);
    }

    public boolean contains(String name) {
        return state.menus().containsKey(name);
    }

    /** An unchanging snapshot in name order; name order is also the bound-item tiebreak. */
    public Collection<Menu> menus() {
        return state.menus().values();
    }

    /** An unchanging snapshot in name order. */
    public Set<String> names() {
        return state.menus().keySet();
    }

    public int size() {
        return state.menus().size();
    }

    /** Adds or replaces a menu, stamping it with a fresh revision. */
    long put(Menu menu) {
        State current = state;
        TreeMap<String, Menu> menus = new TreeMap<>(current.menus());
        menus.put(menu.name(), menu);
        Map<String, Long> revisions = new HashMap<>(current.revisions());
        long revision = ++lastRevision;
        revisions.put(menu.name(), revision);
        state = new State(menus, revisions);
        return revision;
    }

    Optional<Menu> remove(String name) {
        State current = state;
        Menu removed = current.menus().get(name);
        if (removed == null) {
            return Optional.empty();
        }
        TreeMap<String, Menu> menus = new TreeMap<>(current.menus());
        menus.remove(name);
        Map<String, Long> revisions = new HashMap<>(current.revisions());
        revisions.remove(name);
        state = new State(menus, revisions);
        return Optional.of(removed);
    }
}
