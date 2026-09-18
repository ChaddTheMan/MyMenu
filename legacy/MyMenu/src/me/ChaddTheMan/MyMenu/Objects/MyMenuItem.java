/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import me.ChaddTheMan.MyMenu.Objects.MyMenuConfig;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MyMenuItem {
    private static MyMenuConfig menuConfigFile;
    private static FileConfiguration menuConfig;
    private String name;
    private ItemStack item;
    private ArrayList<String> commands = new ArrayList();

    public MyMenuItem(ItemStack item) {
        this.name = null;
        this.item = item;
        item.setAmount(1);
        this.commands = null;
    }

    public MyMenuItem(String name, ItemStack item) {
        this.name = name;
        this.item.getItemMeta().setDisplayName(name);
        this.item = item;
        item.setAmount(1);
        this.commands = null;
    }

    public MyMenuItem(String name, ItemStack item, int amount) {
        this.name = name;
        this.item.getItemMeta().setDisplayName(name);
        this.item = item;
        item.setAmount(amount);
        this.commands = null;
    }

    public MyMenuItem(String name, ItemStack item, String command) {
        this.name = name;
        this.item.getItemMeta().setDisplayName(name);
        this.item = item;
        item.setAmount(1);
        this.addCommand(command);
    }

    public MyMenuItem(String name, ItemStack item, int amount, String command) {
        this.name = name;
        this.item.getItemMeta().setDisplayName(name);
        this.item = item;
        item.setAmount(amount);
        this.addCommand(command);
    }

    public static Inventory addInventoryItems(Inventory inv, MyMenuMenu menu) {
        menuConfigFile = MyMenuMenu.menuConfigFile;
        menuConfigFile.reload();
        menuConfig = menuConfigFile.getConfig();
        menu.setMenuItems(null);
        menu.setMenuItems(new HashMap<Integer, MyMenuItem>());
        for (String location : menuConfig.getConfigurationSection("menus." + menu.getName() + ".inventory.slots").getKeys(false)) {
            int slot = Integer.valueOf(location.substring(1));
            String menuLocation = "menus." + menu.getName() + ".inventory.slots." + location;
            MyMenuItem menuItem = new MyMenuItem(new ItemStack(Material.getMaterial((String)menuConfig.getString(String.valueOf(menuLocation) + ".item.material"))));
            ItemStack item = menuItem.getItem();
            ItemMeta itemMeta = item.getItemMeta();
            item.setType(Material.getMaterial((String)menuConfig.getString(String.valueOf(menuLocation) + ".item.material")));
            if (menuConfig.getString(String.valueOf(menuLocation) + ".item.displayName") != null) {
                String displayName = menuConfig.getString(String.valueOf(menuLocation) + ".item.displayName").replace('\u00a7', '&');
                itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes((char)'&', (String)displayName));
            }
            if (menuConfig.getString(String.valueOf(menuLocation) + ".command") != null) {
                String coloredCommand = menuConfig.getString(String.valueOf(menuLocation) + ".command").replace('\u00a7', '&');
                String command = ChatColor.translateAlternateColorCodes((char)'&', (String)coloredCommand);
                ArrayList<String> commands = new ArrayList<String>();
                commands.add(command);
                menuConfig.set(String.valueOf(menuLocation) + ".command", null);
                menuConfig.set(String.valueOf(menuLocation) + ".commands", commands);
                menuConfigFile.saveConfig();
            }
            if (menuConfig.getStringList(String.valueOf(menuLocation) + ".commands") != null) {
                List commands = menuConfig.getStringList(String.valueOf(menuLocation) + ".commands");
                int ii = 0;
                while (ii < commands.size()) {
                    String coloredCommand = (String)commands.get(ii);
                    String command = ChatColor.translateAlternateColorCodes((char)'&', (String)coloredCommand);
                    menuItem.addCommand(command);
                    ++ii;
                }
            }
            if (menuConfig.getStringList(String.valueOf(menuLocation) + ".item.itemLore") != null) {
                List lore = menuConfig.getStringList(String.valueOf(menuLocation) + ".item.itemLore");
                int ii = 0;
                while (ii < lore.size()) {
                    String newString = ((String)lore.get(ii)).replace('\u00a7', '&');
                    String loreString = ChatColor.translateAlternateColorCodes((char)'&', (String)newString);
                    lore.set(ii, loreString);
                    ++ii;
                }
                itemMeta.setLore(lore);
            }
            if (menuConfig.getString(String.valueOf(menuLocation) + ".item.amount") != null) {
                item.setAmount(menuConfig.getInt(String.valueOf(menuLocation) + ".item.amount"));
            } else {
                item.setAmount(1);
            }
            if (menuConfig.getString(String.valueOf(menuLocation) + ".item.type") != null) {
                item.setDurability((short)menuConfig.getInt(String.valueOf(menuLocation) + ".item.type"));
            }
            if (itemMeta.hasEnchants()) {
                itemMeta.getEnchants().clear();
            }
            item.setItemMeta(itemMeta);
            menuItem.setItem(item);
            inv.setItem(slot, menuItem.getItem());
            menu.addMenuItem(slot, menuItem);
        }
        return inv;
    }

    public static void saveInventoryItems(MyMenuMenu menu) {
        menuConfigFile = MyMenuMenu.menuConfigFile;
        menuConfig = menuConfigFile.getConfig();
        String menuRef = "menus." + menu.getName() + ".inventory.slots.";
        menuConfig.createSection("menus." + menu.getName() + ".inventory");
        menuConfig.createSection("menus." + menu.getName() + ".inventory.slots");
        for (int slot : menu.getMenuItems().keySet()) {
            String newstr;
            if (menu.getMenuItems().get(slot) == null) continue;
            menuConfig.createSection("menus." + menu.getName() + ".inventory.slots.s" + slot);
            String menuLocation = String.valueOf(menuRef) + "s" + slot;
            MyMenuItem menuItem = menu.getMenuItems().get(slot);
            ItemStack item = menuItem.getItem();
            ItemMeta itemMeta = item.getItemMeta();
            if (menuItem.getCommandList() != null) {
                ArrayList<String> commands = new ArrayList<String>();
                for (String str : menuItem.getCommandList()) {
                    newstr = str.replace('\u00a7', '&');
                    commands.add(newstr);
                }
                menuConfig.set(String.valueOf(menuLocation) + ".commands", commands);
            } else {
                menuConfig.set(String.valueOf(menuLocation) + ".command", null);
            }
            menuConfig.createSection("menus." + menu.getName() + ".inventory.slots.s" + slot + ".item");
            if (item.getType() != null) {
                menuConfig.set(String.valueOf(menuLocation) + ".item.material", (Object)item.getType().toString());
            } else {
                menuConfig.set(String.valueOf(menuLocation) + ".item.material", null);
            }
            if (itemMeta.getDisplayName() != null) {
                String displayName = itemMeta.getDisplayName().replace('\u00a7', '&');
                menuConfig.set(String.valueOf(menuLocation) + ".item.displayName", (Object)displayName);
            } else {
                menuConfig.set(String.valueOf(menuLocation) + ".item.displayName", null);
            }
            if (String.valueOf(item.getAmount()) != null) {
                menuConfig.set(String.valueOf(menuLocation) + ".item.amount", (Object)item.getAmount());
            } else {
                menuConfig.set(String.valueOf(menuLocation) + ".item.amount", (Object)1);
            }
            if (String.valueOf(item.getDurability()) != null) {
                menuConfig.set(String.valueOf(menuLocation) + ".item.type", (Object)item.getDurability());
            }
            if (itemMeta.hasLore()) {
                ArrayList<String> lore = new ArrayList<String>();
                for (String str : itemMeta.getLore()) {
                    newstr = str.replace('\u00a7', '&');
                    lore.add(newstr);
                }
                menuConfig.set(String.valueOf(menuLocation) + ".item.itemLore", lore);
                continue;
            }
            menuConfig.set(String.valueOf(menuLocation) + ".item.itemLore", null);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCommandList(ArrayList<String> commands) {
        this.commands = commands;
    }

    public void addCommand(String command) {
        if (command == null) {
            throw new IllegalArgumentException();
        }
        if (this.commands == null) {
            this.commands = new ArrayList();
        }
        this.commands.add(command);
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public String getName() {
        return this.name;
    }

    public ArrayList<String> getCommandList() {
        return this.commands;
    }

    public int getAmount() {
        return this.item.getAmount();
    }

    public ItemStack getItem() {
        return this.item;
    }
}

