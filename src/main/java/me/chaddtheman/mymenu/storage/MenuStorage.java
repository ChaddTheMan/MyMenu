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

import me.chaddtheman.mymenu.model.Menu;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * A backend that holds menus somewhere durable.
 *
 * <p>Every I/O method returns at once and completes its future on the backend's own thread, so
 * no caller ever blocks on disk or network. Completion happens off the main thread; anything
 * that touches the Bukkit API afterwards has to hop back first.
 *
 * <p>Writes are applied in the order they are submitted. Callers rely on that: a delete
 * followed by a save of a recreated menu with the same name must land in that order.
 *
 * <p>Storage <em>reports</em> the degraded state but never enforces it. Refusing a write here
 * would be too late, because the registry already holds the edit; {@code MenuService} refuses
 * mutations before they reach the model (ARCHITECTURE §7.1).
 */
public interface MenuStorage {

    /**
     * Reads every menu. Malformed entries are skipped, logged, and put storage into the
     * degraded state; the future still completes normally with whatever did load. It fails
     * only if the backend holds changes it has not managed to write, since a fresh load would
     * discard them; the failure is then an {@link UnwrittenChangesException}.
     */
    CompletableFuture<Collection<Menu>> loadAll();

    /**
     * Writes again the changes an earlier write failed to persist, if there are any. Completes
     * normally when there were none or the retry succeeded, exceptionally when it failed again.
     * Reload calls this first, so fixing the fault and reloading keeps the work.
     */
    CompletableFuture<Void> retryUnwritten();

    /**
     * Forgets that the backend holds changes it could not write, so that the next
     * {@link #loadAll} re-reads the durable copy instead of refusing. Completes with whether
     * anything was dropped. The explicit discard form of reload is the only caller: it is the way
     * out when the fault is permanent and the changes would be lost at shutdown anyway (SPEC §12).
     */
    CompletableFuture<Boolean> discardUnwritten();

    /** Writes these menus, adding or replacing each by name. Other menus are untouched. */
    CompletableFuture<Void> saveAll(Collection<Menu> menus);

    /**
     * Writes a standalone backup of {@code menu}, then removes it. Takes the menu rather than
     * its name because the registry has already dropped it and the backup needs its contents.
     * If the backup cannot be written, the menu is not removed.
     */
    CompletableFuture<Void> delete(Menu menu);

    /** True after a load that skipped entries or a write that failed. */
    boolean isDegraded();

    /**
     * Blocks until every submitted write has finished, retrying once any write that previously
     * failed. Bounded by a timeout. For {@code onDisable} only: it is the one sanctioned piece
     * of blocking I/O on the main thread (CLAUDE.md, hard rule 2).
     */
    void flush();

    /** Stops the backend's thread, waiting a bounded time for queued work. Call after {@link #flush}. */
    void close();

    /** {@link #loadAll} refused because re-reading would throw away changes not yet written. */
    final class UnwrittenChangesException extends IllegalStateException {

        public UnwrittenChangesException(String message) {
            super(message);
        }
    }
}
