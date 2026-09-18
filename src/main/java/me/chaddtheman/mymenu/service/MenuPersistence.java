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

/**
 * What {@code MenuService} needs from storage, and nothing more.
 *
 * <p>This is deliberately not ARCHITECTURE §7's {@code MenuStorage}. That interface does I/O and
 * returns futures; this one only <em>records intent</em>. The debouncing that turns ten quick
 * edits into one write sits between the two (DECISIONS #64), so {@code MenuService} never
 * waits on, or even sees, a future.
 *
 * <p>TODO(stage 3): implemented by the debounced writer over {@code MenuStorage}. Every method
 * is called on the main thread and must return promptly without doing I/O or throwing: by the
 * time {@link #markDirty} runs, the registry has already changed.
 */
public interface MenuPersistence {

    /**
     * True after a load-time parse failure or a runtime write failure, until a reload clears
     * it. {@code MenuService} refuses every mutation while this holds.
     */
    boolean isDegraded();

    /** The named menu changed; write it after the debounce interval. */
    void markDirty(String menuName);

    /**
     * The menu was removed from the registry. Receives the last version so a delete backup
     * can be written from it, since the registry no longer has it.
     */
    void markDeleted(Menu menu);
}
