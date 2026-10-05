package com.example.autocraft;

import org.bukkit.inventory.ItemStack;

public record AutocraftRecipe(ItemStack[] pattern, ItemStack result) {
}