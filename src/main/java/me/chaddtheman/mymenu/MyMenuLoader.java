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
package me.chaddtheman.mymenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds MyMenu's classpath before the plugin class itself is loaded.
 *
 * <p>Paper gives every Paper plugin its own isolated classloader, and this loader runs while
 * that classloader is being assembled — before {@code MyMenu} exists, before config is read,
 * before any Bukkit API is usable. Its one job is to add HikariCP and the MySQL driver.
 *
 * <p>Resolving them here, rather than shading them into the jar, keeps the jar small and
 * avoids relocating JDBC drivers, which find each other by class name and break easily when
 * renamed. The cost is that every server downloads the driver on first start even when it
 * uses YAML storage, because config is not readable yet; ARCHITECTURE.md §11 accepts that.
 *
 * <p>The coordinates come from {@code paper-libraries.json}, which the build generates from
 * the {@code paperLibrary} entries in {@code build.gradle.kts}, so a version bump happens in
 * one place. The JSON also names a repository, and that part is deliberately ignored: it
 * points at Maven Central itself, and Central's terms forbid using it as a download CDN for
 * every server. Paper's mirror constant is used instead.
 */
public final class MyMenuLoader implements PluginLoader {

    private static final String LIBRARIES_RESOURCE = "/paper-libraries.json";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : readDependencies()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        classpathBuilder.addLibrary(resolver);
    }

    private List<String> readDependencies() {
        try (InputStream in = MyMenuLoader.class.getResourceAsStream(LIBRARIES_RESOURCE)) {
            if (in == null) {
                // Only a broken build gets here; starting without the driver would fail later
                // and far less clearly.
                throw new IllegalStateException(LIBRARIES_RESOURCE + " is missing from the jar");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonArray array = JsonParser.parseReader(reader)
                        .getAsJsonObject()
                        .getAsJsonArray("dependencies");
                List<String> dependencies = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    dependencies.add(element.getAsString());
                }
                return dependencies;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + LIBRARIES_RESOURCE, e);
        }
    }
}
