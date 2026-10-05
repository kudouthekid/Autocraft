package com.example.autocraft;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RecipeGuiHolder implements InventoryHolder {

    private final Location location;
    private Inventory inventory;

    public RecipeGuiHolder(Location location) {
        this.location = location;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Location getLocation() {
        return location;
    }
}