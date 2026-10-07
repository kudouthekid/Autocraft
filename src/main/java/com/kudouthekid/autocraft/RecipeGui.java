package com.example.autocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RecipeGui {

    public static final int SIZE = 54;
    public static final int[] PATTERN_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    public static final int RESULT_SLOT = 25;

    private static final Set<Integer> ALLOWED = new HashSet<>();

    static {
        for (int slot : PATTERN_SLOTS) ALLOWED.add(slot);
        ALLOWED.add(RESULT_SLOT);
    }

    private RecipeGui() {}

    public static boolean isAllowed(int slot) { return ALLOWED.contains(slot); }
    public static Set<Integer> allowedSlots() { return Collections.unmodifiableSet(ALLOWED); }

    public static void open(AutoCraftPlugin plugin, Player player, Block block, AutocraftRecipe recipe) {
        RecipeGuiHolder holder = new RecipeGuiHolder(block.getLocation());
        Inventory gui = Bukkit.createInventory(holder, SIZE,
                Component.text("AutoCraft Recipe", NamedTextColor.DARK_AQUA));
        holder.setInventory(gui);

        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            if (!isAllowed(i)) gui.setItem(i, filler);
        }

        if (recipe != null) {
            ItemStack[] pattern = recipe.pattern();
            for (int i = 0; i < 9; i++) {
                if (pattern[i] != null && !pattern[i].getType().isAir()) {
                    ItemStack ghost = pattern[i].clone();
                    plugin.manager().markGhost(ghost);
                    gui.setItem(PATTERN_SLOTS[i], ghost);
                }
            }
            if (recipe.result() != null && !recipe.result().getType().isAir()) {
                ItemStack ghostResult = recipe.result().clone();
                plugin.manager().markGhost(ghostResult);
                gui.setItem(RESULT_SLOT, ghostResult);
            }
        }

        player.openInventory(gui);
    }

    private static ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.empty()); item.setItemMeta(meta); }
        return item;
    }
}