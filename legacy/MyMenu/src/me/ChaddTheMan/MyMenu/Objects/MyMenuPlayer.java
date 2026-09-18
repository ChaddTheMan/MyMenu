/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 */
package me.ChaddTheMan.MyMenu.Objects;

import java.util.ArrayList;
import me.ChaddTheMan.MyMenu.Objects.MyMenuItem;
import me.ChaddTheMan.MyMenu.Objects.MyMenuMenu;
import me.ChaddTheMan.MyMenu.Tools.Messages;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class MyMenuPlayer {
    private static ArrayList<MyMenuPlayer> playerList = new ArrayList();
    private Player player;
    private int menuAccessing;
    private boolean isEditing;
    private boolean isUsing;
    private boolean inConversation;
    private MyMenuItem menuItemAtCursor;
    private PlayerInventory backupInventory;

    public MyMenuPlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Invalid arguments entered");
        }
        this.setPlayer(player);
        MyMenuPlayer.addPlayer(this);
    }

    public static void addPlayer(MyMenuPlayer player) {
        if (player == null) {
            return;
        }
        MyMenuPlayer newPlayer = player;
        MyMenuPlayer.getPlayerList().add(newPlayer);
    }

    public static void removePlayer(String player) {
        int index = MyMenuPlayer.getPlayerIndex(player);
        if (index == -1) {
            return;
        }
        MyMenuPlayer.getPlayerList().remove(index);
    }

    public static void updatePlayer(MyMenuPlayer player) {
        int index = MyMenuPlayer.getPlayerIndex(player.getPlayer().getName());
        if (index == -1) {
            return;
        }
        MyMenuPlayer.getPlayerList().set(index, player);
    }

    public static void clearPlayerList() {
        if (playerList == null) {
            playerList = new ArrayList();
        }
        for (MyMenuPlayer player : playerList) {
            if (!player.isUsing() && !player.isEditing()) continue;
            player.getPlayer().sendMessage(String.valueOf(Messages.PREFIX) + "Server reloaded. Menu closed");
            player.closeMenu();
        }
        playerList.clear();
        playerList = null;
        playerList = new ArrayList();
    }

    public static MyMenuPlayer getPlayerByName(String player) {
        if (player == null) {
            return null;
        }
        int index = MyMenuPlayer.getPlayerIndex(player);
        if (index == -1) {
            return null;
        }
        return MyMenuPlayer.getPlayerList().get(index);
    }

    public static int getPlayerIndex(String player) {
        int i = 0;
        while (i < MyMenuPlayer.getPlayerList().size()) {
            if (player.equals(MyMenuPlayer.getPlayerList().get(i).getPlayer().getName())) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    public static ArrayList<MyMenuPlayer> getPlayerList() {
        return playerList;
    }

    public void closeMenu() {
        this.getPlayer().closeInventory();
    }

    public void editMenu(String menuName) {
        if (menuName == null) {
            return;
        }
        MyMenuMenu menu = MyMenuMenu.getMenu(menuName);
        if (menu == null) {
            return;
        }
        this.setEditing(true);
        this.getPlayer().openInventory(menu.getInventory());
    }

    public void useMenu(String menuName) {
        if (menuName == null) {
            return;
        }
        MyMenuMenu menu = MyMenuMenu.getMenu(menuName);
        if (menu == null) {
            return;
        }
        this.setUsing(true);
        this.getPlayer().openInventory(menu.getInventory());
    }

    public void setInventory(PlayerInventory inventory) {
        this.player.getInventory().clear();
        this.player.getInventory().setContents(inventory.getContents());
        this.player.getInventory().setArmorContents(inventory.getArmorContents());
    }

    public void restoreInventory() {
        this.player.getInventory().clear();
        int i = 0;
        while (i < this.player.getPlayer().getInventory().getSize()) {
            if (this.backupInventory.getItem(i) != null && this.backupInventory.getItem(i).getType() != null) {
                this.player.getPlayer().getInventory().setItem(i, new ItemStack(this.backupInventory.getItem(i).getType(), this.backupInventory.getItem(i).getAmount()));
            }
            ++i;
        }
        if (this.backupInventory.getHelmet() != null) {
            this.player.getInventory().setHelmet(new ItemStack(this.backupInventory.getHelmet().getType()));
        }
        if (this.backupInventory.getChestplate() != null) {
            this.player.getInventory().setChestplate(new ItemStack(this.backupInventory.getChestplate().getType()));
        }
        if (this.backupInventory.getLeggings() != null) {
            this.player.getInventory().setLeggings(new ItemStack(this.backupInventory.getLeggings().getType()));
        }
        if (this.backupInventory.getBoots() != null) {
            this.player.getInventory().setBoots(new ItemStack(this.backupInventory.getBoots().getType()));
        }
    }

    public void setBackupInventory(PlayerInventory inventory) {
        this.backupInventory = inventory;
        int i = 0;
        while (i < inventory.getSize()) {
            if (inventory.getItem(i) != null && inventory.getItem(i).getType() != null) {
                this.backupInventory.setItem(i, new ItemStack(inventory.getItem(i).getType(), inventory.getItem(i).getAmount()));
            }
            ++i;
        }
        if (inventory.getHelmet() != null) {
            this.player.getInventory().setHelmet(new ItemStack(inventory.getHelmet().getType()));
        }
        if (inventory.getChestplate() != null) {
            this.player.getInventory().setChestplate(new ItemStack(inventory.getChestplate().getType()));
        }
        if (inventory.getLeggings() != null) {
            this.player.getInventory().setLeggings(new ItemStack(inventory.getLeggings().getType()));
        }
        if (inventory.getBoots() != null) {
            this.player.getInventory().setBoots(new ItemStack(inventory.getBoots().getType()));
        }
    }

    public void setUsing(boolean b) {
        this.isUsing = b;
        if (b) {
            this.setEditing(false);
        }
    }

    public void setEditing(boolean b) {
        this.isEditing = b;
        if (b) {
            this.setUsing(false);
        }
    }

    public void setInConversation(boolean b) {
        this.inConversation = b;
    }

    public void setMenuAccessing(int menu) {
        if (MyMenuMenu.getMenuList().get(menu) == null) {
            return;
        }
        this.menuAccessing = menu;
    }

    public void setPlayer(Player player) {
        if (player == null) {
            return;
        }
        this.player = player;
    }

    public void setMenuItemAtCursor(MyMenuItem item) {
        this.menuItemAtCursor = item;
    }

    public MyMenuItem getMenuItemAtCursor() {
        return this.menuItemAtCursor;
    }

    public boolean isUsing() {
        return this.isUsing;
    }

    public boolean isEditing() {
        return this.isEditing;
    }

    public boolean isInConversation() {
        return this.inConversation;
    }

    public PlayerInventory getBackupInventory() {
        return this.backupInventory;
    }

    public int getMenuAccessing() {
        return this.menuAccessing;
    }

    public Player getPlayer() {
        return this.player;
    }
}

