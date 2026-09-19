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
package me.chaddtheman.mymenu.session;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * What the plugin remembers about an admin who is editing a menu.
 *
 * <p>The editor itself is stage 8. What stage 5 fixes is the lifecycle: the session is valid
 * while the admin has this menu's {@code EDIT} view open, while a swap is in flight for one tick,
 * or while a chat prompt is pending. A prompt is the one state that outlives its inventory, since
 * the editor closes the inventory so the admin can type, and it is bounded by the timeout task
 * it carries. A prompt without a timeout would be a tracked flag again, and a leaked one would
 * bring back 1.x's "already editing" lockout.
 */
public final class EditSession {

    /**
     * A chat prompt awaiting the admin's next message. Stage 8 adds what the prompt is for; the
     * timeout is here from the start because it is what bounds the session.
     */
    public record PendingPrompt(BukkitTask timeout) {

        public PendingPrompt {
            Objects.requireNonNull(timeout, "timeout");
        }
    }

    private final String menuName;
    private boolean swapInFlight;
    private @Nullable PendingPrompt prompt;

    EditSession(String menuName) {
        this.menuName = Objects.requireNonNull(menuName, "menuName");
    }

    public String menuName() {
        return menuName;
    }

    public @Nullable PendingPrompt prompt() {
        return prompt;
    }

    /** Replaces any earlier prompt, cancelling its timeout so two cannot race to end the session. */
    public void setPrompt(@Nullable PendingPrompt prompt) {
        clearPrompt();
        this.prompt = prompt;
    }

    public void clearPrompt() {
        PendingPrompt current = prompt;
        prompt = null;
        if (current != null) {
            current.timeout().cancel();
        }
    }

    /** See {@link ViewSession#markSwap}. */
    void markSwap(Plugin plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> swapInFlight = false);
        swapInFlight = true;
    }

    boolean swapInFlight() {
        return swapInFlight;
    }
}
