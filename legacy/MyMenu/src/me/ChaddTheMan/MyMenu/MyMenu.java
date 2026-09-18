/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.event.Listener
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.PluginManager
 *  org.bukkit.plugin.java.JavaPlugin
 */
package me.ChaddTheMan.MyMenu;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import me.ChaddTheMan.MyMenu.Commands.MyMenuChangelog;
import me.ChaddTheMan.MyMenu.Commands.MyMenuCreate;
import me.ChaddTheMan.MyMenu.Commands.MyMenuDelete;
import me.ChaddTheMan.MyMenu.Commands.MyMenuEdit;
import me.ChaddTheMan.MyMenu.Commands.MyMenuHelp;
import me.ChaddTheMan.MyMenu.Commands.MyMenuInfo;
import me.ChaddTheMan.MyMenu.Commands.MyMenuList;
import me.ChaddTheMan.MyMenu.Commands.MyMenuMyMenu;
import me.ChaddTheMan.MyMenu.Commands.MyMenuName;
import me.ChaddTheMan.MyMenu.Commands.MyMenuOpen;
import me.ChaddTheMan.MyMenu.Commands.MyMenuReload;
import me.ChaddTheMan.MyMenu.Commands.MyMenuSave;
import me.ChaddTheMan.MyMenu.Commands.MyMenuSet;
import me.ChaddTheMan.MyMenu.Commands.MyMenuUpdate;
import me.ChaddTheMan.MyMenu.Listeners.InventoryClickListener;
import me.ChaddTheMan.MyMenu.Listeners.InventoryCloseListener;
import me.ChaddTheMan.MyMenu.Listeners.InventoryOpenListener;
import me.ChaddTheMan.MyMenu.Listeners.PlayerInteractListener;
import me.ChaddTheMan.MyMenu.Listeners.PlayerJoinListener;
import me.ChaddTheMan.MyMenu.Listeners.PlayerQuitListener;
import me.ChaddTheMan.MyMenu.MCStats.Metrics;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfig;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfigMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItemConversation;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuPlayer;
import me.ChaddTheMan.MyMenu.Updater.Updater;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MyMenu
extends JavaPlugin {
    private static MyMenu plugin;
    public static MyMenuConfig configFile;
    public static FileConfiguration config;
    public static MyMenuConfig menuConfigFile;
    public static MyMenuConfig menuConfigConfigFile;
    public boolean metricsEnabled;
    private boolean checkUpdate = true;
    public static Updater updater;
    public static boolean update;
    public static String name;
    public static Updater.ReleaseType type;
    public static String link;
    public static File file;
    public Metrics metrics;
    public Metrics.Graph pluginVersionGraph;
    public Metrics.Graph boundItemGraph;
    public Metrics.Graph serverMenuSizeGraph;
    public Metrics.Graph numberOfMenusGraph;

    static {
        update = false;
        name = "";
        type = null;
        link = "";
        file = null;
    }

    public void onEnable() {
        plugin = this;
        configFile = new MyMenuConfig(this, "config.yml", this.getDataFolder().toString());
        configFile.reload();
        config = configFile.getConfig();
        configFile.saveDefaultConfig();
        menuConfigFile = new MyMenuConfig(this, "menus.yml", this.getDataFolder().toString());
        menuConfigFile.reload();
        menuConfigFile.saveDefaultConfig();
        menuConfigConfigFile = new MyMenuConfig(this, "config.yml", this.getDataFolder().toString());
        if (!config.contains("Plugin.CheckForUpdates")) {
            config.set("Plugin.CheckForUpdates", (Object)true);
            configFile.saveConfig();
        }
        plugin.getCommand("mymenu").setExecutor((CommandExecutor)new MyMenuMyMenu());
        plugin.getCommand("mmhelp").setExecutor((CommandExecutor)new MyMenuHelp());
        plugin.getCommand("mmlist").setExecutor((CommandExecutor)new MyMenuList());
        plugin.getCommand("mmopen").setExecutor((CommandExecutor)new MyMenuOpen());
        plugin.getCommand("mmedit").setExecutor((CommandExecutor)new MyMenuEdit());
        plugin.getCommand("mmcreate").setExecutor((CommandExecutor)new MyMenuCreate());
        plugin.getCommand("mmdelete").setExecutor((CommandExecutor)new MyMenuDelete());
        plugin.getCommand("mmset").setExecutor((CommandExecutor)new MyMenuSet());
        plugin.getCommand("mminfo").setExecutor((CommandExecutor)new MyMenuInfo());
        plugin.getCommand("mmreload").setExecutor((CommandExecutor)new MyMenuReload());
        plugin.getCommand("mmsave").setExecutor((CommandExecutor)new MyMenuSave());
        plugin.getCommand("mmname").setExecutor((CommandExecutor)new MyMenuName());
        plugin.getCommand("mmchangelog").setExecutor((CommandExecutor)new MyMenuChangelog());
        plugin.getCommand("update").setExecutor((CommandExecutor)new MyMenuUpdate());
        PluginManager pm = Bukkit.getServer().getPluginManager();
        pm.registerEvents((Listener)new InventoryClickListener(), (Plugin)this);
        pm.registerEvents((Listener)new InventoryOpenListener(), (Plugin)this);
        pm.registerEvents((Listener)new InventoryCloseListener(), (Plugin)this);
        pm.registerEvents((Listener)new PlayerJoinListener(), (Plugin)this);
        pm.registerEvents((Listener)new PlayerQuitListener(), (Plugin)this);
        pm.registerEvents((Listener)new PlayerInteractListener(), (Plugin)this);
        MyMenuMenu.setupMenuList();
        MyMenuConfigMenu.setupMenuConfigList();
        this.metricsEnabled = this.startMetrics();
        Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)this, new Runnable(){

            @Override
            public void run() {
                MyMenu.this.checkUpdate();
            }
        });
    }

    public void onDisable() {
        updater = null;
        update = false;
        name = null;
        type = null;
        link = null;
        try {
            MyMenuItemConversation.clearConversationQueue();
            MyMenuPlayer.clearPlayerList();
            MyMenuMenu.clearMenuList();
            MyMenuMenu.setMenuList(new ArrayList<MyMenuMenu>());
            MyMenuConfigMenu.clearConfigMenuList();
            MyMenuConfigMenu.setConfigMenuList(new ArrayList<MyMenuConfigMenu>());
        }
        catch (NoClassDefFoundError noClassDefFoundError) {
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void checkUpdate() {
        this.checkUpdate = config.getBoolean("Plugin.CheckForUpdates");
        if (this.checkUpdate) {
            file = this.getFile();
            updater = new Updater((Plugin)this, 86249, file, Updater.UpdateType.NO_DOWNLOAD, true);
            update = updater.getResult() == Updater.UpdateResult.UPDATE_AVAILABLE;
            name = updater.getLatestName();
            type = updater.getLatestType();
            link = updater.getLatestFileLink();
            Bukkit.getLogger().info("[MyMenu] Checking for updates:");
            if (!update) {
                Bukkit.getLogger().info("[MyMenu] No update available!");
            } else if (update) {
                Bukkit.getLogger().info("[MyMenu] Update available! Run 'update mymenu' to automatically update to the latest version.");
            }
        } else {
            Bukkit.getLogger().info("[MyMenu] Skipping Update Check");
        }
    }

    public boolean startMetrics() {
        try {
            this.metrics = new Metrics((Plugin)plugin);
            this.pluginVersionGraph = this.metrics.createGraph("pluginVersion");
            this.boundItemGraph = this.metrics.createGraph("boundItems");
            this.serverMenuSizeGraph = this.metrics.createGraph("menusPerServer");
            this.numberOfMenusGraph = this.metrics.createGraph("totalMenus");
            Metrics.Plotter versionPlotter = new Metrics.Plotter("Version: " + plugin.getDescription().getVersion()){

                @Override
                public String getColumnName() {
                    return "Version: " + plugin.getDescription().getVersion();
                }

                @Override
                public int getValue() {
                    return 1;
                }
            };
            this.pluginVersionGraph.addPlotter(versionPlotter);
            for (MyMenuMenu menu : MyMenuMenu.getMenuList()) {
                ItemStack boundItem;
                if (!menu.isBound() || (boundItem = menu.getBoundItem()) == null) continue;
                Material material = boundItem.getType();
                Metrics.Plotter itemPlotter = null;
                itemPlotter = new Metrics.Plotter(material.toString().toLowerCase()){

                    @Override
                    public int getValue() {
                        return 1;
                    }
                };
                this.boundItemGraph.addPlotter(itemPlotter);
            }
            final int size = MyMenuMenu.getMenuList().size();
            Metrics.Plotter sizePlotter = new Metrics.Plotter(String.valueOf(size) + " menus"){

                @Override
                public int getValue() {
                    return 1;
                }
            };
            this.serverMenuSizeGraph.addPlotter(sizePlotter);
            Metrics.Plotter createdMenusPlotter = new Metrics.Plotter("Total Menus"){

                @Override
                public String getColumnName() {
                    return "Total Menus";
                }

                @Override
                public int getValue() {
                    return size;
                }
            };
            this.numberOfMenusGraph.addPlotter(createdMenusPlotter);
            return this.metrics.start();
        }
        catch (IOException e) {
            this.getLogger().info("Plugin Metrics failed to start");
            return false;
        }
    }

    public static void updateToConfig() {
        MyMenuMenu.saveMenuList();
        MyMenuConfigMenu.saveMenuConfigList();
    }

    public static void updateFromConfig() {
        plugin = MyMenu.getInstance();
        try {
            MyMenuItemConversation.clearConversationQueue();
            MyMenuPlayer.clearPlayerList();
            MyMenuMenu.clearMenuList();
            MyMenuMenu.setMenuList(new ArrayList<MyMenuMenu>());
            MyMenuConfigMenu.clearConfigMenuList();
            MyMenuConfigMenu.setConfigMenuList(new ArrayList<MyMenuConfigMenu>());
        }
        catch (NoClassDefFoundError noClassDefFoundError) {
        }
        catch (Exception exception) {
            // empty catch block
        }
        configFile = new MyMenuConfig(MyMenu.getInstance(), "config.yml", MyMenu.getInstance().getDataFolder().toString());
        configFile.reload();
        config = configFile.getConfig();
        configFile.saveDefaultConfig();
        menuConfigFile = new MyMenuConfig(MyMenu.getInstance(), "menus.yml", MyMenu.getInstance().getDataFolder().toString());
        menuConfigFile.reload();
        menuConfigFile.saveDefaultConfig();
        menuConfigConfigFile = new MyMenuConfig(MyMenu.getInstance(), "config.yml", MyMenu.getInstance().getDataFolder().toString());
        MyMenuMenu.setupMenuList();
        MyMenuConfigMenu.setupMenuConfigList();
    }

    public static MyMenu getInstance() {
        return plugin;
    }
}

