package com.example.autocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

public final class AutocraftListener implements Listener {

    private final AutoCraftPlugin plugin;
    private final AutocraftManager manager;

    public AutocraftListener(AutoCraftPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();

        if (!manager.isAutocraftItem(hand)) {
            return;
        }

        Block placed = event.getBlockPlaced();

        if (placed.getType() != manager.getBlockMaterial()) {
            return;
        }

        manager.create(placed);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!manager.isAutocraft(block)) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("autocraft.break")) {
            event.setCancelled(true);
            return;
        }

        event.setDropItems(false);

        manager.dropContents(block);
        manager.remove(block, false);

        block.getWorld().dropItemNaturally(block.getLocation(), manager.createItem());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (!manager.isAutocraft(block)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();

        // Kalau player lagi SNEAK dan pegang BLOCK (kayak Hopper, Chest, Glass, dll),
        // JANGAN di-cancel. Biarin vanilla Minecraft handle biar block-nya bisa nempel.
        if (player.isSneaking() && hand != null && hand.getType().isBlock()) {
            return; 
        }

        // Selain kondisi di atas (misal klik tangan kosong, atau klik tanpa sneak),
        // Cancel event-nya biar GUI Barrel gak kebuka.
        event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state.getType() == manager.getBlockMaterial()) {
                manager.isAutocraft(state.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        // 1. Cek apakah inventory yang terbuka di layar (Top Inventory) adalah GUI AutoCraft
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeGuiHolder)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();

        // 2. Jika player mengklik inventory mereka sendiri (bagian bawah / hotbar)
        if (clicked == null || clicked == event.getView().getBottomInventory()) {
            // Cegah shift-click dari inventory player masuk langsung ke GUI AutoCraft
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            // Biarkan player mengambil/memindahkan item di inventory mereka secara normal
            return;
        }

        // 3. Mulai dari sini, player mengklik inventory GUI AutoCraft (Top Inventory)
        event.setCancelled(true); // Default: batalkan semua klik di GUI untuk keamanan

        int rawSlot = event.getRawSlot();
        
        // Jika mengklik slot yang tidak diperbolehkan (misal: kaca pembatas / filler)
        if (!RecipeGui.isAllowed(rawSlot)) {
            return;
        }

        ClickType click = event.getClick();
        ItemStack current = clicked.getItem(rawSlot);
        ItemStack cursor = event.getCursor();

        boolean currentGhost = manager.isGhost(current);
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();

        // Logika untuk item Ghost (cetakan resep)
        if (currentGhost) {
            if (!cursorEmpty) {
                // Menimpa ghost dengan item asli dari cursor
                if (click == ClickType.LEFT) {
                    clicked.setItem(rawSlot, cursor.clone());
                    event.setCursor(new ItemStack(Material.AIR));
                } else if (click == ClickType.RIGHT) {
                    ItemStack one = cursor.clone();
                    one.setAmount(1);
                    clicked.setItem(rawSlot, one);

                    ItemStack newCursor = cursor.clone();
                    int newAmount = newCursor.getAmount() - 1;

                    if (newAmount <= 0) {
                        event.setCursor(new ItemStack(Material.AIR));
                    } else {
                        newCursor.setAmount(newAmount);
                        event.setCursor(newCursor);
                    }
                }
            } else if (click == ClickType.RIGHT && event.getWhoClicked().isSneaking()) {
                // Sneak + Right Click untuk menghapus ghost
                clicked.setItem(rawSlot, null);
            }
            return;
        }

        // Logika untuk slot kosong atau item asli (bukan ghost)
        // Izinkan klik normal (kiri/kanan) jika bukan shift-click
        if (!event.isShiftClick() && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
            event.setCancelled(false);
        }
        
        // Izinkan shift-click DARI GUI ke inventory player (untuk memindahkan item keluar dari GUI)
        if (event.isShiftClick()) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeGuiHolder)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();

        // Cek semua slot yang terkena efek drag
        for (int rawSlot : event.getRawSlots()) {
            // Jika slot berada di dalam GUI AutoCraft (Top Inventory)
            if (rawSlot < topSize) {
                // Batalkan jika mencoba men-drag ke slot kaca/filler
                if (!RecipeGui.isAllowed(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
                // Batalkan jika mencoba men-drag menimpa item Ghost
                ItemStack current = event.getView().getTopInventory().getItem(rawSlot);
                if (manager.isGhost(current)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        // Jika semua slot yang di-drag valid, biarkan vanilla menanganinya (event tidak dibatalkan)
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeGuiHolder holder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory gui = event.getInventory();

        ItemStack[] pattern = new ItemStack[9];

        for (int i = 0; i < 9; i++) {
            ItemStack item = gui.getItem(RecipeGui.PATTERN_SLOTS[i]);
            if (item != null && !item.getType().isAir()) {
                pattern[i] = item.clone();
            }
        }

        ItemStack result = gui.getItem(RecipeGui.RESULT_SLOT);
        if (result != null && !result.getType().isAir()) {
            result = result.clone();
        } else {
            result = null;
        }

        Block block = holder.getLocation().getBlock();
        manager.setRecipe(block, pattern, result);

        for (int slot : RecipeGui.allowedSlots()) {
            ItemStack item = gui.getItem(slot);

            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (!manager.isGhost(item)) {
                player.getInventory().addItem(item)
                        .values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }

            gui.setItem(slot, null);
        }

        boolean hasPattern = Arrays.stream(pattern).anyMatch(Objects::nonNull);

        if (result != null && hasPattern) {
            player.sendMessage(Component.text("AutoCraft recipe saved.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("AutoCraft recipe cleared.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        Inventory source = event.getSource();

        if (destination.getHolder() instanceof Container destContainer) {
            Block destBlock = destContainer.getBlock();

            if (manager.isAutocraft(destBlock)) {
                event.setCancelled(true);

                if (!(source.getHolder() instanceof Hopper hopper)) {
                    return;
                }

                if (!manager.isAllowedInputSide(destBlock, hopper.getBlock())) {
                    return;
                }

                manager.handleHopperInput(destBlock, source, destination, event.getItem());
                return;
            }
        }

        if (source.getHolder() instanceof Container sourceContainer) {
            Block sourceBlock = sourceContainer.getBlock();

            if (manager.isAutocraft(sourceBlock)) {
                event.setCancelled(true);
            }
        }
    }
}