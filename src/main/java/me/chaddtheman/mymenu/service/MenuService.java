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

import me.chaddtheman.mymenu.action.Action;
import me.chaddtheman.mymenu.model.ClickKey;
import me.chaddtheman.mymenu.model.Menu;
import me.chaddtheman.mymenu.model.MenuItem;
import me.chaddtheman.mymenu.model.MenuType;
import me.chaddtheman.mymenu.service.MutationResult.Applied;
import me.chaddtheman.mymenu.service.MutationResult.Reason;
import me.chaddtheman.mymenu.service.MutationResult.Reason.DelayOverCap;
import me.chaddtheman.mymenu.service.MutationResult.Reason.ItemsOutsideLayout;
import me.chaddtheman.mymenu.service.MutationResult.Reason.Plain;
import me.chaddtheman.mymenu.service.MutationResult.Refused;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The only way to change a menu: the gate every mutation passes through.
 *
 * <h2>Why the checks happen here, before the model</h2>
 *
 * When storage is degraded (a menu failed to parse at load, or a write failed), saving is
 * unsafe: a save would rewrite the file without the menus that failed to load, and those are
 * gone. So mutations must stop. The question is <em>where</em>.
 *
 * <p>Refusing at the storage boundary is too late. By then the registry already holds the
 * edit. The player sees it take effect, nothing is written, and the next reload discards it
 * without anyone being told. That is exactly the silent data loss the degraded state exists to
 * prevent. So every method here asks the gate <em>first</em> and refuses before building the new
 * menu. A refused edit never appears to have worked.
 *
 * <p>Reads are never gated: {@link #registry()} keeps working, so players can still use every
 * menu that did load (SPEC §12).
 *
 * <h2>One gate, three conditions, one owner each</h2>
 *
 * {@link #gate()} refuses while a reload is running, while menus have not finished loading, and
 * while storage is degraded. All three mean the same thing: an edit accepted now would be lost,
 * either replaced by a load that is about to land or never written. They live together so that
 * nothing outside this class has to know the list. The command tree asks the gate before it runs a
 * mutating command, which gives an early message in the same words (and covers {@code joinmenu}
 * and {@code edit}, which never reach this class); the editor will ask it before acting on a click.
 * But neither is what protects the model. Every mutator here asks again, before it computes
 * anything, so a caller that forgets to ask still cannot get an edit through.
 *
 * <p>The reload flag is owned here rather than read from the reload command, so the gate depends
 * on nothing above it. {@code /mymenu reload} raises it with {@link #beginReload()} and must lower
 * it with {@link #endReload()} however the reload ends; a flag left up would refuse every edit until
 * restart, which is 1.x's lockout in a new place. The reload's own steps never pass through the
 * gate: the flush and the retry talk to storage, and {@link #replaceAll} is the load itself.
 *
 * <h2>The delay cap, judged on what a change touches</h2>
 *
 * SPEC §9.2 caps a list's total delay. Loading clamps an over-cap list with a warning, in the
 * parser, and that does not change. A change made through this class is refused instead, but only
 * for the lists it writes: a list equal to the one already at that slot and click key is not judged.
 * So lowering the cap never blocks an unrelated edit to a menu that already holds a longer list. The
 * check is here, not in the model records, because the records would need the configured value, and
 * a lowered cap would then make existing menus fail to construct at load.
 *
 * <h2>Order within a mutation</h2>
 *
 * Ask the gate, compute the new menu, check the lists it touches, put it in the registry (which
 * issues the new revision), then tell persistence it is dirty. Everything before the registry
 * write commits nothing, so a change that throws or is refused leaves the registry exactly as it
 * was. Persistence is told last and only records intent; the debounced writer decides when to
 * write.
 *
 * <h2>Before the first load</h2>
 *
 * Menus load asynchronously, so for a moment after enable the registry is empty and storage
 * has not yet decided whether it is degraded. A mutation in that window would be overwritten
 * by the load when it lands, so mutations are refused until {@link #replaceAll} has run.
 *
 * <p>Main thread only. That is checked rather than assumed, because a mutation from an async
 * chat handler that forgot to hop back would otherwise work almost every time.
 */
public final class MenuService {

    private static final int TICKS_PER_SECOND = 20;

    private final MenuRegistry registry = new MenuRegistry();
    private final MenuPersistence persistence;

    // Main thread only, like every write here.
    private boolean loaded;
    private boolean reloading;
    private int maxTotalDelaySeconds;

    public MenuService(MenuPersistence persistence, int maxTotalDelaySeconds) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        setMaxTotalDelaySeconds(maxTotalDelaySeconds);
    }

    /** Read access for everyone else. Always available, degraded or not. */
    public MenuRegistry registry() {
        return registry;
    }

    public boolean isDegraded() {
        return persistence.isDegraded();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** {@code actions.maxTotalDelaySeconds}, applied by config loading and by every reload. */
    public void setMaxTotalDelaySeconds(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("maxTotalDelaySeconds is negative");
        }
        this.maxTotalDelaySeconds = seconds;
    }

    /**
     * Why a mutation would be refused right now, before it looks at any menu, or empty if it may
     * proceed. Every mutator here calls this first; callers outside call it to refuse early.
     */
    public Optional<Reason> gate() {
        requireMainThread();
        if (reloading) {
            return Optional.of(Plain.RELOAD_RUNNING);
        }
        if (!loaded) {
            return Optional.of(Plain.NOT_LOADED);
        }
        if (persistence.isDegraded()) {
            return Optional.of(Plain.STORAGE_DEGRADED);
        }
        return Optional.empty();
    }

    /**
     * Raises the reload flag. Returns false, changing nothing, if a reload is already running. The
     * caller must call {@link #endReload()} on every way the reload can end.
     */
    public boolean beginReload() {
        requireMainThread();
        if (reloading) {
            return false;
        }
        reloading = true;
        return true;
    }

    public void endReload() {
        requireMainThread();
        reloading = false;
    }

    /**
     * Installs what storage loaded, replacing everything registered, and opens the gate. Every
     * menu gets a fresh revision, so views rendered before a reload are all detected as stale.
     * Nothing is marked dirty: these menus came from storage. Not gated: this is the load the gate
     * waits for, and a reload calls it with its own flag still raised.
     */
    public void replaceAll(Collection<Menu> menus) {
        requireMainThread();
        registry.replaceAll(menus);
        loaded = true;
    }

    /**
     * @param name must already be normalised and valid; the command layer rejects bad names
     *             with a proper message before they get here
     */
    public MutationResult create(String name, String title, int rows, @Nullable String author,
                                 @Nullable UUID authorUuid) {
        Optional<Reason> refused = gate();
        if (refused.isPresent()) {
            return new Refused(refused.get());
        }
        if (registry.contains(name)) {
            return new Refused(Plain.MENU_EXISTS);
        }
        // A new menu has no action lists, so the delay cap has nothing to judge.
        return commit(Menu.create(name, title, rows, author, authorUuid));
    }

    public MutationResult delete(String name) {
        Optional<Reason> refused = gate();
        if (refused.isPresent()) {
            return new Refused(refused.get());
        }
        Optional<Menu> removed = registry.remove(name);
        if (removed.isEmpty()) {
            return new Refused(Plain.NO_SUCH_MENU);
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
        Optional<Reason> refused = gate();
        if (refused.isPresent()) {
            return new Refused(refused.get());
        }
        Optional<Menu> current = registry.find(name);
        if (current.isEmpty()) {
            return new Refused(Plain.NO_SUCH_MENU);
        }
        Menu next = Objects.requireNonNull(change.apply(current.get()), "change returned null");
        if (!next.name().equals(name)) {
            throw new IllegalArgumentException("update may not rename " + name + " to " + next.name());
        }
        if (next.equals(current.get())) {
            return new Applied(current.get());
        }
        Optional<DelayOverCap> overCap = overDelayCap(current.get(), next);
        if (overCap.isPresent()) {
            return new Refused(overCap.get());
        }
        return commit(next);
    }

    /**
     * Changes type and rows, refusing if any occupied slot would fall outside the new layout. The
     * refusal names those slots (SPEC §8.4.1).
     */
    public MutationResult changeLayout(String name, MenuType type, int rows) {
        Optional<Reason> refused = gate();
        if (refused.isPresent()) {
            return new Refused(refused.get());
        }
        Optional<Menu> current = registry.find(name);
        if (current.isEmpty()) {
            return new Refused(Plain.NO_SUCH_MENU);
        }
        List<Integer> outside = current.get().slotsOutside(type, rows);
        if (!outside.isEmpty()) {
            return new Refused(new ItemsOutsideLayout(outside));
        }
        return update(name, menu -> menu.withLayout(type, rows));
    }

    /**
     * The first list in {@code next}, by slot and then click key, that differs from the list at the
     * same place in {@code current} and pauses for longer than the cap. A slot new to the menu
     * counts as differing. Lists equal to the current ones are skipped, however long they are.
     */
    private Optional<DelayOverCap> overDelayCap(Menu current, Menu next) {
        long budget = (long) maxTotalDelaySeconds * TICKS_PER_SECOND;
        for (Map.Entry<Integer, MenuItem> slot : next.items().entrySet()) {
            MenuItem before = current.item(slot.getKey());
            for (Map.Entry<ClickKey, List<Action>> list : slot.getValue().actions().entrySet()) {
                List<Action> was = before == null ? null : before.actions().get(list.getKey());
                if (list.getValue().equals(was)) {
                    continue;
                }
                long total = totalDelayTicks(list.getValue());
                if (total > budget) {
                    return Optional.of(new DelayOverCap(slot.getKey(), list.getKey(), total, maxTotalDelaySeconds));
                }
            }
        }
        return Optional.empty();
    }

    private static long totalDelayTicks(List<Action> actions) {
        long total = 0;
        for (Action action : actions) {
            if (action instanceof Action.Delay delay) {
                total += delay.ticks();
            }
        }
        return total;
    }

    private MutationResult commit(Menu menu) {
        registry.put(menu);
        persistence.markDirty(menu);
        return new Applied(menu);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("menus may only be changed on the main thread");
        }
    }
}
