/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.util.ArrayList;
import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfig;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import org.bukkit.configuration.file.FileConfiguration;

public class MyMenuConfigMenu {
    private static ArrayList<MyMenuConfigMenu> configMenuList;
    private static MyMenuConfig configFile;
    private static FileConfiguration config;
    private String name;
    private boolean openOnJoin;
    private boolean giveItemOnJoin;

    public MyMenuConfigMenu(MyMenuMenu menu) {
        if (menu == null) {
            throw new IllegalArgumentException();
        }
        this.name = menu.getName();
        this.openOnJoin = false;
        this.giveItemOnJoin = false;
    }

    public static void setupMenuConfigList() {
        MyMenuConfigMenu.clearConfigMenuList();
        MyMenuConfigMenu.reloadMenuConfigList();
        MyMenuConfigMenu.saveMenuConfigList();
    }

    private static void reloadMenuConfigList() {
        MyMenuConfigMenu.clearConfigMenuList();
        configFile = MyMenu.menuConfigConfigFile;
        configFile.reload();
        config = configFile.getConfig();
        String location = "Menus.";
        for (MyMenuMenu menu : MyMenuMenu.getMenuList()) {
            MyMenuConfigMenu menuConfig = new MyMenuConfigMenu(menu);
            boolean ooj = false;
            ooj = config.getString(String.valueOf(location) + menu.getName() + ".OpenOnJoin") != null ? config.getBoolean(String.valueOf(location) + menu.getName() + ".OpenOnJoin") : false;
            boolean gioj = false;
            gioj = config.getString(String.valueOf(location) + menu.getName() + ".GiveItemOnJoin") != null ? config.getBoolean(String.valueOf(location) + menu.getName() + ".GiveItemOnJoin") : false;
            menuConfig.setOpenOnJoin(ooj);
            menuConfig.setGiveItemOnJoin(gioj);
            configMenuList.add(menuConfig);
        }
    }

    public static void saveMenuConfigList() {
        configFile = MyMenu.menuConfigConfigFile;
        configFile.reload();
        config = configFile.getConfig();
        MyMenuConfigMenu.reloadMenuConfigList();
        config.set("Menus", null);
        String location = "Menus.";
        for (MyMenuConfigMenu menu : configMenuList) {
            if (Boolean.toString(menu.opensOnJoin()) != null) {
                config.set(String.valueOf(location) + menu.getName() + ".OpenOnJoin", (Object)menu.opensOnJoin());
            } else {
                config.set(String.valueOf(location) + menu.getName() + ".OpenOnJoin", (Object)false);
            }
            if (Boolean.toString(menu.givesItemOnJoin()) != null) {
                config.set(String.valueOf(location) + menu.getName() + ".GiveItemOnJoin", (Object)menu.givesItemOnJoin());
                continue;
            }
            config.set(String.valueOf(location) + menu.getName() + ".GiveItemOnJoin", (Object)false);
        }
        configFile.saveConfig();
    }

    public static void clearConfigMenuList() {
        if (configMenuList == null) {
            configMenuList = new ArrayList();
        }
        configMenuList.clear();
        configMenuList = null;
        configMenuList = new ArrayList();
    }

    public static void addConfigMenu(MyMenuConfigMenu menu) {
        if (menu == null) {
            return;
        }
        configMenuList.add(menu);
    }

    public static void removeConfigMenu(int index) {
        if (configMenuList.get(index) == null) {
            return;
        }
        configMenuList.remove(index);
    }

    public static void setConfigMenu(int index, MyMenuConfigMenu menu) {
        if (index < 0) {
            return;
        }
        if (menu == null) {
            configMenuList.remove(index);
            return;
        }
        configMenuList.set(index, menu);
    }

    public static void setConfigMenuList(ArrayList<MyMenuConfigMenu> list) {
        if (list == null) {
            configMenuList = null;
            return;
        }
        configMenuList = list;
    }

    public static int getConfigMenuIndex(String name) {
        int i = 0;
        while (i < MyMenuConfigMenu.getConfigMenuList().size()) {
            if (name.equals(MyMenuConfigMenu.getConfigMenuList().get(i).getName())) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    public static MyMenuConfigMenu getConfigMenu(String menu) {
        if (menu == null) {
            return null;
        }
        int index = MyMenuConfigMenu.getConfigMenuIndex(menu);
        if (index == -1) {
            return null;
        }
        return MyMenuConfigMenu.getConfigMenuList().get(index);
    }

    public static ArrayList<MyMenuConfigMenu> getConfigMenuList() {
        return configMenuList;
    }

    public void setGiveItemOnJoin(boolean b) {
        this.giveItemOnJoin = b;
    }

    public void setOpenOnJoin(boolean b) {
        this.openOnJoin = b;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean givesItemOnJoin() {
        return this.giveItemOnJoin;
    }

    public boolean opensOnJoin() {
        return this.openOnJoin;
    }

    public String getName() {
        return this.name;
    }
}

