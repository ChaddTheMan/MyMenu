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

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player, per-slot click cooldowns (SPEC §8.3). In memory only, so a restart clears them.
 *
 * <p>Entries are <em>not</em> removed when a player quits: relogging would otherwise be the
 * way round every cooldown. They are pruned lazily, whenever the same player's entries are next
 * touched, so a player who never clicks again costs at most a handful of expired timestamps
 * until the next restart.
 *
 * <p>Keyed by menu name and slot rather than by {@code MenuItem}, because an edit replaces the
 * item object and a cooldown should survive the admin fixing a typo in the lore.
 */
public final class CooldownStore {

    private record Slot(String menu, int slot) {
    }

    private final InstantSource clock;
    private final Map<UUID, Map<Slot, Instant>> expiries = new HashMap<>();

    public CooldownStore() {
        this(InstantSource.system());
    }

    /** For probes: a clock that can be moved by hand. */
    public CooldownStore(InstantSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** How long the player must still wait before this slot, or empty if they may click now. */
    public Optional<Duration> remaining(UUID player, String menu, int slot) {
        Map<Slot, Instant> own = expiries.get(player);
        if (own == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        prune(own, now);
        if (own.isEmpty()) {
            expiries.remove(player);
            return Optional.empty();
        }
        Instant until = own.get(new Slot(menu, slot));
        return until == null ? Optional.empty() : Optional.of(Duration.between(now, until));
    }

    /** Starts a cooldown of {@code seconds} on this slot; zero or less starts none. */
    public void mark(UUID player, String menu, int slot, int seconds) {
        if (seconds <= 0) {
            return;
        }
        expiries.computeIfAbsent(player, ignored -> new HashMap<>())
                .put(new Slot(menu, slot), clock.instant().plusSeconds(seconds));
    }

    private static void prune(Map<Slot, Instant> own, Instant now) {
        for (Iterator<Instant> it = own.values().iterator(); it.hasNext(); ) {
            if (!it.next().isAfter(now)) {
                it.remove();
            }
        }
    }
}
