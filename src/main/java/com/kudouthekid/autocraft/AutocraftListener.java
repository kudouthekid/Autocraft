package com.example.autocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

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
        if (!manager.isAutocraftItem(hand) || event.getBlockPlaced().getType() != manager.getBlockMaterial()) return;
        manager.create(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isAutocraft(block)) return;
        if (!event.getPlayer().hasPermission("autocraft.break")) { event.setCancelled(true); return; }
        event.setDropItems(false);
        manager.dropContents(block);
        manager.remove(block, false);
        block.getWorld().dropItemNaturally(block.getLocation(), manager.createItem());
    }

    // ==================== INTERACT + LINK MODE ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();

        // 1. Kalau sedang dalam link session → selesaikan link
        LinkSessionManager.LinkSession session = LinkSessionManager.get(player.getUniqueId());
        if (session != null) {
            if (manager.isLinkableContainer(block)) {
                event.setCancelled(true);
                if (manager.linkChest(session.crafter(), block, session.isOutput())) {
                    String type = session.isOutput() ? "OUTPUT" : "INPUT";
                    player.sendMessage(Component.text(
                            "✅ " + type + " chest ter-link: " + block.getType().name()
                            + " [" + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]",
                            NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("❌ Gagal link chest.", NamedTextColor.RED));
                }
                LinkSessionManager.consume(player);
                return;
            }
            LinkSessionManager.consume(player);
            player.sendMessage(Component.text("⚠️ Link mode dibatalkan.", NamedTextColor.YELLOW));
            return;
        }

        // 2. Kalau bukan autocraft, abaikan
        if (!manager.isAutocraft(block)) return;

        // 3. Shift + pegang block → biarkan vanilla handle (tempel block)
        if (player.isSneaking() && hand != null && hand.getType().isBlock()) return;

        // 4. Cancel interaksi vanilla, lalu BUKA GUI RESEP
        event.setCancelled(true);
        manager.openRecipeGui(player, block);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        LinkSessionManager.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state.getType() == manager.getBlockMaterial()) manager.isAutocraft(state.getBlock());
        }
    }

    // ==================== GUI CLICK (ghost system) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeGuiHolder)) return;
        Inventory clicked = event.getClickedInventory();

        if (clicked == null || clicked == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (!RecipeGui.isAllowed(rawSlot)) return;
        if (rawSlot == RecipeGui.RESULT_SLOT) return;

        ClickType click = event.getClick();
        ItemStack current = clicked.getItem(rawSlot);
        ItemStack cursor = event.getCursor();
        boolean currentGhost = manager.isGhost(current);
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();

        if (currentGhost) {
            if (!cursorEmpty) {
                if (click == ClickType.LEFT) {
                    clicked.setItem(rawSlot, cursor.clone());
                    event.setCursor(new ItemStack(Material.AIR));
                } else if (click == ClickType.RIGHT) {
                    ItemStack one = cursor.clone(); one.setAmount(1);
                    clicked.setItem(rawSlot, one);
                    ItemStack newCursor = cursor.clone();
                    int newAmount = newCursor.getAmount() - 1;
                    if (newAmount <= 0) event.setCursor(new ItemStack(Material.AIR));
                    else { newCursor.setAmount(newAmount); event.setCursor(newCursor); }
                }
            } else if (click == ClickType.RIGHT && event.getWhoClicked().isSneaking()) {
                clicked.setItem(rawSlot, null);
            }
            return;
        }

        // Item asli: izinkan klik normal & shift-click
        if (!event.isShiftClick() && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
            event.setCancelled(false);
        }
        if (event.isShiftClick()) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeGuiHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                if (!RecipeGui.isAllowed(rawSlot)) { event.setCancelled(true); return; }
                ItemStack current = event.getView().getTopInventory().getItem(rawSlot);
                if (manager.isGhost(current)) { event.setCancelled(true); return; }
            }
        }
    }

    // ==================== GUI CLOSE ====================

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeGuiHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory gui = event.getInventory();
        ItemStack[] pattern = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = gui.getItem(RecipeGui.PATTERN_SLOTS[i]);
            if (item != null && !item.getType().isAir()) pattern[i] = item.clone();
        }

        Recipe vanillaRecipe = manager.findMatchingVanillaRecipe(pattern);
        Block block = holder.getLocation().getBlock();

        if (vanillaRecipe != null) {
            manager.setRecipe(block, pattern, vanillaRecipe.getResult());
            player.sendMessage(Component.text("✅ Resep Vanilla tersimpan!", NamedTextColor.GREEN));
        } else {
            boolean hasPattern = Arrays.stream(pattern).anyMatch(Objects::nonNull);
            if (hasPattern) {
                player.sendMessage(Component.text("❌ Pola tidak cocok dengan resep Vanilla!", NamedTextColor.RED));
            } else {
                manager.setRecipe(block, null, null);
                player.sendMessage(Component.text("⚠️ Resep AutoCraft dihapus.", NamedTextColor.YELLOW));
            }
        }

        // Kembalikan HANYA item non-ghost (fix duplikasi)
        for (int slot : RecipeGui.allowedSlots()) {
            ItemStack item = gui.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (manager.isGhost(item)) { gui.setItem(slot, null); continue; }
            player.getInventory().addItem(item)
                    .values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            gui.setItem(slot, null);
        }
    }

    // ==================== INVENTORY MOVE ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder() instanceof Container destContainer) {
            if (manager.isAutocraft(destContainer.getBlock())) { event.setCancelled(true); return; }
        }
        if (event.getSource().getHolder() instanceof Container sourceContainer) {
            if (manager.isAutocraft(sourceContainer.getBlock())) { event.setCancelled(true); }
        }
    }
}