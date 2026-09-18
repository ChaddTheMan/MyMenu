/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.java.JavaPlugin
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MyMenuConfig {
    private final JavaPlugin plugin;
    private final String filename;
    private final String filepath;
    private FileConfiguration config = null;
    private File configFile = null;

    public MyMenuConfig(JavaPlugin plugin, String filename) {
        if (plugin == null) {
            throw new IllegalArgumentException("JAVAPLUGIN CANNOT BE NULL");
        }
        if (filename == null) {
            throw new IllegalArgumentException("FILENAME CANNOT BE NULL");
        }
        this.plugin = plugin;
        this.filename = filename;
        this.filepath = plugin.getDataFolder().toString();
    }

    public MyMenuConfig(JavaPlugin plugin, String filename, String filepath) {
        if (plugin == null) {
            throw new IllegalArgumentException("JAVAPLUGIN CANNOT BE NULL");
        }
        if (filename == null) {
            throw new IllegalArgumentException("FILENAME CANNOT BE NULL");
        }
        if (filepath == null) {
            throw new IllegalArgumentException("FILEPATH CANNOT BE NULL");
        }
        this.plugin = plugin;
        this.filename = filename;
        this.filepath = filepath;
    }

    public void reload() {
        if (this.configFile == null) {
            this.configFile = new File(this.filepath, this.filename.toString());
        }
        this.config = YamlConfiguration.loadConfiguration((File)this.configFile);
    }

    public FileConfiguration getConfig() {
        if (this.config == null) {
            this.reload();
        }
        return this.config;
    }

    public void saveConfig() {
        if (this.config == null || this.configFile == null) {
            return;
        }
        try {
            this.getConfig().save(this.configFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().info("Could not save config: " + this.configFile.toString());
            e.printStackTrace();
        }
    }

    public void saveDefaultConfig() {
        if (this.configFile == null) {
            this.configFile = new File(this.filepath, this.filename.toString());
        }
        if (!this.configFile.exists()) {
            this.plugin.saveResource(this.filename, false);
        }
    }
}

