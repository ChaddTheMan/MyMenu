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
package me.chaddtheman.mymenu.action;

/**
 * The last thing a command string passes through before dispatch (SPEC §10.1).
 *
 * <p>Newlines, carriage returns and semicolons are removed anywhere in the string; leading
 * slashes are stripped. This is applied by {@code ActionExecutor} to whatever the text seam
 * returns, unconditionally, so a stage-9 implementation that substitutes a nickname into
 * {@code $give {PLAYER} diamond} cannot let {@code ;op me} through by forgetting to call it.
 */
public final class CommandSanitiser {

    private CommandSanitiser() {
    }

    public static String sanitise(String command) {
        String cleaned = command.replace("\n", "").replace("\r", "").replace(";", "");
        // Both dispatch paths take the command without its slash; a slash left in place would
        // make "/give" a command named "/give", which does not exist.
        while (true) {
            cleaned = cleaned.stripLeading();
            if (!cleaned.startsWith("/")) {
                return cleaned;
            }
            cleaned = cleaned.substring(1);
        }
    }
}
