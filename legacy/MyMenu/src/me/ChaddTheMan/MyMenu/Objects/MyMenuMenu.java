/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import me.ChaddTheMan.MyMenu.MyMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfig;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfigMenu;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MyMenuMenu {
    protected static MyMenuConfig menuConfigFile;
    private static FileConfiguration menuConfig;
    private static ArrayList<MyMenuMenu> menuList;
    private Inventory inv;
    private String name;
    private String invName;
    private String author;
    private UUID authorUUID;
    private int rows;
    private int size;
    private ItemStack boundItem;
    private ItemMeta boundItemMeta;
    private boolean isBound;
    private boolean isSpecific;
    private HashMap<Integer, MyMenuItem> menuItems;

    public MyMenuMenu(String name) {
        if (name == null) {
            throw new IllegalArgumentException();
        }
        this.name = name;
        this.author = null;
        this.authorUUID = null;
        this.rows = 6;
        this.size = this.rows * 9;
        this.isBound = false;
        this.inv = Bukkit.createInventory(null, (int)this.size, (String)name);
        this.menuItems = new HashMap();
        menuList.add(this);
    }

    public MyMenuMenu(Player player, String name, int rows) {
        if (player == null || name == null) {
            throw new IllegalArgumentException();
        }
        this.name = name;
        this.author = player.getName();
        this.authorUUID = player.getUniqueId();
        this.rows = rows < 0 ? 1 : (rows > 6 ? 6 : rows);
        this.size = rows * 9;
        this.isBound = false;
        this.inv = Bukkit.createInventory(null, (int)this.size, (String)name);
        this.menuItems = new HashMap();
        menuList.add(this);
    }

    public MyMenuMenu(Player player, String name) {
        if (player == null || name == null) {
            throw new IllegalArgumentException();
        }
        this.name = name;
        this.author = player.getName();
        this.boundItem = null;
        this.authorUUID = player.getUniqueId();
        this.rows = 6;
        this.size = this.rows * 9;
        this.boundItem = null;
        this.isBound = false;
        this.inv = Bukkit.createInventory(null, (int)this.size, (String)name);
        this.menuItems = new HashMap();
        menuList.add(this);
    }

    public static void clearMenuList() {
        if (menuList == null) {
            menuList = new ArrayList();
        }
        int i = 0;
        while (i < menuList.size()) {
            MyMenuMenu menu = menuList.get(i);
            if (menu.menuItems == null) {
                menu.menuItems = new HashMap();
            }
            int x = 0;
            while (x < menu.menuItems.size()) {
                menu.menuItems.remove(x);
                ++x;
            }
            menu.menuItems.clear();
            menu.menuItems = null;
            menu.menuItems = new HashMap();
            MyMenuMenu.setMenu(i, menu);
            ++i;
        }
        menuList.clear();
        menuList = null;
        menuList = new ArrayList();
    }

    public static void updateMenu(MyMenuMenu menu) {
        int index = MyMenuMenu.getMenuIndex(menu.getName());
        if (index == -1) {
            return;
        }
        MyMenuMenu.setMenu(index, menu);
    }

    public static void setupMenuList() {
        MyMenuMenu.clearMenuList();
        menuConfigFile = MyMenu.menuConfigFile;
        menuConfigFile.reload();
        menuConfig = menuConfigFile.getConfig();
        for (String name : menuConfig.getConfigurationSection("menus").getKeys(false)) {
            MyMenuMenu menu = new MyMenuMenu(name);
            if (menuConfig.getString("menus." + name + ".inventoryName") != null) {
                String invName = ChatColor.stripColor((String)menuConfig.getString("menus." + name + ".inventoryName"));
                menu.setInventoryName(ChatColor.translateAlternateColorCodes((char)'&', (String)invName));
            } else {
                menu.setInventoryName(menu.getName());
            }
            if (menuConfig.getString("menus." + name + ".author") != null) {
                menu.setAuthor(menuConfig.getString("menus." + name + ".author"));
            } else {
                menu.setAuthor(null);
            }
            if (menuConfig.getString("menus." + name + ".authorUUID") != null) {
                menu.setAuthorUUID(UUID.fromString(menuConfig.getString("menus." + name + ".authorUUID")));
            } else {
                menu.setAuthorUUID(null);
            }
            if (menuConfig.getString("menus." + name + ".rows") != null) {
                menu.setRows(menuConfig.getInt("menus." + name + ".rows"));
            } else if (menuConfig.getInt("menus." + name + ".rows") == 0) {
                menu.setRows(6);
            } else if (menuConfig.getInt("menus." + name + ".rows") > 6) {
                menu.setRows(6);
            } else if (menuConfig.getString("menus." + name + ".rows") == null) {
                menu.setRows(6);
            }
            if (menuConfig.getString("menus." + name + ".boundItem") != null) {
                menu.setBoundItem(new ItemStack(Material.getMaterial((String)menuConfig.getString("menus." + name + ".boundItem"))));
            } else {
                menu.setBoundItem(null);
            }
            if (menuConfig.getString("menus." + name + ".boundItemName") != null) {
                String boundName = ChatColor.stripColor((String)menuConfig.getString("menus." + name + ".boundItemName"));
                menu.setBoundItemName(ChatColor.translateAlternateColorCodes((char)'&', (String)boundName));
            } else {
                menu.setBoundItemName(null);
            }
            menuConfigFile.saveConfig();
            menu.setInventory(MyMenuItem.addInventoryItems(Bukkit.createInventory(null, (int)menu.getSize(), (String)menu.getInventoryName()), menu));
        }
    }

    public static void saveMenuList() {
        menuConfigFile = MyMenu.menuConfigFile;
        menuConfigFile.reload();
        menuConfig = menuConfigFile.getConfig();
        menuConfig.createSection("menus");
        for (MyMenuMenu menu : menuList) {
            menuConfig.createSection("menus." + menu.getName());
            if (menu.getInventoryName() != null) {
                String invName = menu.getInventoryName().replace('\u00a7', '&');
                menuConfig.set("menus." + menu.getName() + ".inventoryName", (Object)invName);
            } else {
                menuConfig.set("menus." + menu.getName() + ".inventoryName", (Object)menu.getName());
            }
            if (menu.getAuthor() != null) {
                menuConfig.set("menus." + menu.getName() + ".author", (Object)menu.getAuthor());
            } else {
                menuConfig.set("menus." + menu.getName() + ".author", null);
            }
            if (menu.getAuthorUUID() != null) {
                menuConfig.set("menus." + menu.getName() + ".authorUUID", (Object)menu.getAuthorUUID().toString());
            } else {
                menuConfig.set("menus." + menu.getName() + ".authorUUID", null);
            }
            if (Integer.toString(menu.getRows()) != null) {
                menuConfig.set("menus." + menu.getName() + ".rows", (Object)menu.getRows());
            } else {
                menuConfig.set("menus." + menu.getName() + ".rows", (Object)6);
            }
            if (!menu.isBound()) {
                menu.setBoundItem(null);
                menuConfig.set("menus." + menu.getName() + ".boundItem", null);
            } else if (menu.getBoundItem() != null) {
                menuConfig.set("menus." + menu.getName() + ".boundItem", (Object)menu.getBoundItem().getType().toString());
            } else {
                menuConfig.set("menus." + menu.getName() + ".boundItem", null);
            }
            if (!menu.isSpecific()) {
                menuConfig.set("menus." + menu.getName() + ".boundItemName", null);
            } else if (menu.getBoundItemName() != null) {
                String boundName = menu.getBoundItemName().replace('\u00a7', '&');
                menuConfig.set("menus." + menu.getName() + ".boundItemName", (Object)boundName);
            } else {
                menuConfig.set("menus." + menu.getName() + ".boundItemName", null);
            }
            menuConfigFile.saveConfig();
            MyMenuItem.saveInventoryItems(menu);
        }
        menuConfigFile.saveConfig();
    }

    public static void addMenu(MyMenuMenu menu) {
        if (menu == null) {
            return;
        }
        menuList.add(menu);
    }

    public static void removeMenu(String menu) {
        int index = MyMenuMenu.getMenuIndex(menu);
        if (index == -1) {
            return;
        }
        MyMenuMenu.setMenu(index, null);
        int configIndex = MyMenuConfigMenu.getConfigMenuIndex(menu);
        if (index == -1) {
            return;
        }
        MyMenuConfigMenu.setConfigMenu(configIndex, null);
        MyMenuConfigMenu.saveMenuConfigList();
    }

    public static void removeMenu(int index) {
        if (menuList.get(index) == null) {
            return;
        }
        MyMenuMenu.setMenu(index, null);
    }

    public static void setMenu(int index, MyMenuMenu menu) {
        if (menu == null) {
            menuList.set(index, null);
            menuList.remove(index);
            return;
        }
        menuList.set(index, menu);
    }

    public static void setMenuList(ArrayList<MyMenuMenu> list) {
        if (list == null) {
            menuList = null;
            menuList = new ArrayList();
            return;
        }
        menuList = list;
    }

    public static MyMenuMenu getMenuByItem(ItemStack item) {
        MyMenuMenu menu = null;
        int i = 0;
        while (i < MyMenuMenu.getMenuList().size()) {
            menu = MyMenuMenu.getMenuList().get(i);
            if (menu.isBound() && menu.getBoundItem().getType().equals((Object)item.getType())) {
                if (menu.isSpecific()) {
                    if (menu.getBoundItemName() != null && item.getItemMeta().getDisplayName() != null && item.getItemMeta().getDisplayName().equals(menu.getBoundItemName())) {
                        return menu;
                    }
                } else {
                    return menu;
                }
            }
            ++i;
        }
        return null;
    }

    public static int getMenuIndex(String menu) {
        int i = 0;
        while (i < MyMenuMenu.getMenuList().size()) {
            if (menu.equals(MyMenuMenu.getMenuList().get(i).getName())) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    public static int getMenuIndexByInventory(String inventory) {
        int i = 0;
        while (i < MyMenuMenu.getMenuList().size()) {
            if (inventory.equals(MyMenuMenu.getMenuList().get(i).getInventory().getName())) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    public static MyMenuMenu getMenu(String menu) {
        if (menu == null) {
            return null;
        }
        int index = MyMenuMenu.getMenuIndex(menu);
        if (index == -1) {
            return null;
        }
        return MyMenuMenu.getMenuList().get(index);
    }

    public static ArrayList<MyMenuMenu> getMenuList() {
        return menuList;
    }

    public void removeMenuItem(int index) {
        this.getInventory().setItem(index, new ItemStack(Material.AIR));
        this.menuItems.put(index, null);
        this.menuItems.remove(index);
    }

    public void addMenuItem(int index, MyMenuItem menuItem) {
        this.setMenuItem(index, menuItem);
    }

    public void setMenuItem(int slot, MyMenuItem menuItem) {
        this.getInventory().setItem(slot, menuItem.getItem());
        this.menuItems.put(slot, menuItem);
    }

    public void setMenuItems(HashMap<Integer, MyMenuItem> menuItems) {
        this.menuItems = menuItems;
    }

    public void setSpecific(Boolean b) {
        this.isSpecific = b;
    }

    public void setBound(Boolean b) {
        this.isBound = b;
    }

    public void setBoundItem(ItemStack boundItem) {
        if (boundItem == null) {
            this.boundItem = null;
            this.boundItemMeta = null;
            this.setBound(false);
            return;
        }
        this.boundItem = boundItem;
        this.boundItemMeta = boundItem.getItemMeta();
        this.setBound(true);
    }

    public void setRows(int rows) {
        this.rows = rows < 0 ? 1 : (rows > 6 ? 6 : rows);
        this.size = rows * 9;
    }

    public void setAuthorUUID(UUID authorUUID) {
        if (authorUUID == null) {
            this.authorUUID = null;
            return;
        }
        this.authorUUID = authorUUID;
    }

    public void setBoundItemName(String boundItemName) {
        if (this.boundItem == null) {
            this.setSpecific(false);
            return;
        }
        if (boundItemName == null) {
            this.boundItemMeta = null;
            this.boundItem.setItemMeta(null);
            this.setSpecific(false);
            return;
        }
        this.boundItemMeta.setDisplayName(boundItemName);
        this.boundItem.setItemMeta(this.boundItemMeta);
        this.setSpecific(true);
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setInventoryName(String name) {
        this.invName = name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }

    public boolean isSpecific() {
        return this.isSpecific;
    }

    public boolean isBound() {
        return this.isBound;
    }

    public MyMenuItem getItem(int slot) {
        if (this.menuItems.containsKey(slot)) {
            return this.menuItems.get(slot);
        }
        return null;
    }

    public HashMap<Integer, MyMenuItem> getMenuItems() {
        if (this.menuItems == null) {
            return null;
        }
        return this.menuItems;
    }

    public ItemStack getBoundItem() {
        return this.boundItem;
    }

    public int getSize() {
        return this.size;
    }

    public int getRows() {
        return this.rows;
    }

    public UUID getAuthorUUID() {
        return this.authorUUID;
    }

    public String getBoundItemName() {
        return ChatColor.translateAlternateColorCodes((char)'&', (String)this.boundItem.getItemMeta().getDisplayName());
    }

    public String getAuthor() {
        return this.author;
    }

    public String getInventoryName() {
        return this.invName;
    }

    public String getName() {
        return this.name;
    }

    public Inventory getInventory() {
        if (this.inv == null) {
            return null;
        }
        return this.inv;
    }
}

