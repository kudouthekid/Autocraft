package com.example.autocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AutocraftManager {

    private static final BlockFace[] SIDES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    private static final Set<BlockFace> SIDE_SET = EnumSet.copyOf(Arrays.asList(SIDES));

    private final AutoCraftPlugin plugin;
    private final Set<AutocraftPos> positions = ConcurrentHashMap.newKeySet();

    private final NamespacedKey flagKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey ghostKey;
    private final NamespacedKey inputChestKey;
    private final NamespacedKey outputChestKey;

    private Material blockMaterial = Material.BARREL;
    private int minPower = 1;
    private boolean inputRequiresPower = true;
    private Set<BlockFace> inputFaces = EnumSet.copyOf(SIDE_SET);
    private Set<BlockFace> outputFaces = EnumSet.copyOf(SIDE_SET);

    public AutocraftManager(AutoCraftPlugin plugin) {
        this.plugin = plugin;
        this.flagKey = new NamespacedKey(plugin, "autocraft_flag");
        this.recipeKey = new NamespacedKey(plugin, "autocraft_recipe");
        this.itemKey = new NamespacedKey(plugin, "autocraft_item");
        this.ghostKey = new NamespacedKey(plugin, "autocraft_gui_ghost");
        this.inputChestKey = new NamespacedKey(plugin, "autocraft_input_chest");
        this.outputChestKey = new NamespacedKey(plugin, "autocraft_output_chest");
    }

    public void load() {
        positions.clear();
        for (String entry : plugin.getConfig().getStringList("locations")) {
            try { positions.add(AutocraftPos.parse(entry)); }
            catch (Exception ex) { plugin.getLogger().warning("Skipping invalid location entry: " + entry); }
        }
        reloadSettings();
    }

    public void save() {
        List<String> list = positions.stream().map(AutocraftPos::serialize).toList();
        plugin.getConfig().set("locations", list);
        plugin.saveConfig();
    }

    public void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        Material material = Material.matchMaterial(config.getString("block-material", "BARREL"));
        this.blockMaterial = (material != null && material.isBlock()) ? material : Material.BARREL;
        this.minPower = Math.max(0, Math.min(15, config.getInt("min-redstone-power", 1)));
        this.inputRequiresPower = config.getBoolean("input-requires-power", true);
        this.inputFaces = parseFaces(config.getStringList("input-faces"));
        this.outputFaces = parseFaces(config.getStringList("output-faces"));
    }

    private Set<BlockFace> parseFaces(List<String> values) {
        EnumSet<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
        for (String value : values) {
            try {
                BlockFace face = BlockFace.valueOf(value.toUpperCase(Locale.ROOT));
                if (SIDE_SET.contains(face)) faces.add(face);
            } catch (IllegalArgumentException ignored) {}
        }
        return faces.isEmpty() ? EnumSet.copyOf(SIDE_SET) : faces;
    }

    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state.getType() == blockMaterial) isAutocraft(state.getBlock());
                }
            }
        }
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(blockMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("AutoCraft Block", NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAutocraftItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public boolean create(Block block) {
        if (block == null || block.getType() != blockMaterial || isAutocraft(block)) return false;
        if (!(block.getState() instanceof TileState tile)) return false;
        tile.getPersistentDataContainer().set(flagKey, PersistentDataType.BYTE, (byte) 1);
        tile.update(true, false);
        positions.add(AutocraftPos.of(block.getLocation()));
        save();
        return true;
    }

    public boolean remove(Block block, boolean dropItem) {
        if (block == null || !isAutocraft(block)) return false;
        clearInventory(block);
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(flagKey);
            tile.getPersistentDataContainer().remove(recipeKey);
            tile.getPersistentDataContainer().remove(inputChestKey);
            tile.getPersistentDataContainer().remove(outputChestKey);
            tile.update(true, false);
        }
        positions.remove(AutocraftPos.of(block.getLocation()));
        save();
        if (dropItem) block.getWorld().dropItemNaturally(block.getLocation(), createItem());
        return true;
    }

    public void dropContents(Block block) {
        if (block.getState(false) instanceof Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir())
                    block.getWorld().dropItemNaturally(block.getLocation(), item.clone());
            }
            container.getInventory().clear();
        }
    }

    public void clearInventory(Block block) {
        if (block.getState(false) instanceof Container container) container.getInventory().clear();
    }

    public boolean isAutocraft(Block block) {
        if (block == null || block.getType() != blockMaterial) return false;
        if (!hasFlag(block)) { positions.remove(AutocraftPos.of(block.getLocation())); return false; }
        positions.add(AutocraftPos.of(block.getLocation()));
        return true;
    }

    private boolean hasFlag(Block block) {
        return block.getState() instanceof TileState tile
                && tile.getPersistentDataContainer().has(flagKey, PersistentDataType.BYTE);
    }

    // ==================== GHOST ITEM SYSTEM (fix duplikasi) ====================

    public void markGhost(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    public boolean isGhost(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE);
    }

    private ItemStack cleanRecipeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(ghostKey);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    // ==================== CHEST LINKING ====================

    public boolean isLinkableContainer(Block block) {
        if (block == null) return false;
        return block.getState() instanceof Container;
    }

    public boolean linkChest(Block crafterBlock, Block chestBlock, boolean isOutput) {
        if (!isAutocraft(crafterBlock)) return false;
        if (!(crafterBlock.getState() instanceof TileState tile)) return false;
        if (!isLinkableContainer(chestBlock)) return false;

        NamespacedKey key = isOutput ? outputChestKey : inputChestKey;
        String pos = AutocraftPos.of(chestBlock.getLocation()).serialize();
        tile.getPersistentDataContainer().set(key, PersistentDataType.STRING, pos);
        tile.update(true, false);
        return true;
    }

    public boolean unlinkChest(Block crafterBlock, boolean isOutput) {
        if (!isAutocraft(crafterBlock)) return false;
        if (!(crafterBlock.getState() instanceof TileState tile)) return false;

        NamespacedKey key = isOutput ? outputChestKey : inputChestKey;
        if (!tile.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return false;

        tile.getPersistentDataContainer().remove(key);
        tile.update(true, false);
        return true;
    }

    public Block getLinkedChest(Block crafterBlock, boolean isOutput) {
        if (!(crafterBlock.getState() instanceof TileState tile)) return null;

        NamespacedKey key = isOutput ? outputChestKey : inputChestKey;
        String posStr = tile.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (posStr == null || posStr.isEmpty()) return null;

        try {
            AutocraftPos pos = AutocraftPos.parse(posStr);
            Location loc = pos.toLocation();
            if (loc == null || !loc.isChunkLoaded()) return null;

            Block chestBlock = loc.getBlock();
            if (!(chestBlock.getState() instanceof Container)) {
                // Chest sudah hancur → auto-unlink
                tile.getPersistentDataContainer().remove(key);
                tile.update(true, false);
                return null;
            }
            return chestBlock;
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== RECIPE ====================

    public AutocraftRecipe getRecipe(Block block) {
        if (!(block.getState() instanceof TileState tile)) return null;
        String data = tile.getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return null;
        try {
            ItemStack[] loaded = SerializationUtil.fromBase64(data);
            if (loaded.length != 10) return null;
            ItemStack[] pattern = Arrays.copyOf(loaded, 9);
            ItemStack result = loaded[9];
            if (result == null || result.getType().isAir()) return null;
            if (Arrays.stream(pattern).allMatch(i -> i == null || i.getType().isAir())) return null;
            return new AutocraftRecipe(pattern, result);
        } catch (Exception ex) { return null; }
    }

    public void setRecipe(Block block, ItemStack[] pattern, ItemStack result) {
        if (!(block.getState() instanceof TileState tile)) return;
        ItemStack[] safePattern = new ItemStack[9];
        if (pattern != null) {
            for (int i = 0; i < 9; i++) safePattern[i] = cleanRecipeItem(pattern[i]);
        }
        ItemStack cleanResult = cleanRecipeItem(result);
        boolean empty = cleanResult == null || cleanResult.getType().isAir()
                || Arrays.stream(safePattern).allMatch(i -> i == null || i.getType().isAir());
        if (empty) {
            tile.getPersistentDataContainer().remove(recipeKey);
        } else {
            ItemStack[] serialized = new ItemStack[10];
            for (int i = 0; i < 9; i++) serialized[i] = safePattern[i];
            serialized[9] = cleanResult;
            tile.getPersistentDataContainer().set(
                    recipeKey, PersistentDataType.STRING,
                    SerializationUtil.toBase64(serialized));
        }
        tile.update(true, false);
    }

    public void openRecipeGui(Player player, Block block) {
        RecipeGui.open(plugin, player, block, getRecipe(block));
    }

    // ==================== POWER & TICK ====================

    public boolean isPowered(Block block) {
        int power = block.getBlockPower();
        if (block.isBlockIndirectlyPowered()) power = Math.max(power, 15);
        return power >= minPower;
    }

    public void tickAll() {
        Iterator<AutocraftPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            AutocraftPos pos = iterator.next();
            Location location = pos.toLocation();
            if (location == null || !location.isChunkLoaded()) continue;
            Block block = location.getBlock();
            if (block.getType() != blockMaterial || !hasFlag(block)) { iterator.remove(); continue; }
            if (!isPowered(block)) continue;
            try { attemptCraft(block); } catch (Exception ignored) {}
        }
    }

    public void attemptCraft(Block block) {
        AutocraftRecipe recipe = getRecipe(block);
        if (recipe == null || !(block.getState(false) instanceof Container container)) return;
        Inventory inventory = container.getInventory();

        if (!hasIngredients(inventory, recipe)) {
            pullIngredients(block, inventory, recipe);
            if (!hasIngredients(inventory, recipe)) return;
        }

        ItemStack result = recipe.result().clone();

        // Cek kapasitas output: chest output dulu, baru hopper
        Block outputChest = getLinkedChest(block, true);
        List<Hopper> hoppers = findOutputHoppers(block);

        int totalSpace = 0;
        Inventory outputChestInv = null;
        if (outputChest != null && outputChest.getState(false) instanceof Container oc) {
            outputChestInv = oc.getInventory();
            totalSpace += freeSpace(outputChestInv, result);
        }
        totalSpace += totalCapacity(hoppers, result);

        if (totalSpace < result.getAmount()) return;

        consumeIngredients(inventory, recipe);
        deliver(block, outputChestInv, hoppers, result);
    }

    private boolean hasIngredients(Inventory inventory, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            ItemStack current = inventory.getItem(i);
            if (required == null || required.getType().isAir()) {
                if (current != null && !current.getType().isAir()) return false;
            } else {
                if (current == null || current.getType().isAir() || !required.isSimilar(current)) return false;
                if (current.getAmount() < requiredAmount(required)) return false;
            }
        }
        return true;
    }

    private void pullIngredients(Block block, Inventory inventory, AutocraftRecipe recipe) {
        // PRIORITAS 1: Input chest yang ter-link
        Block inputChest = getLinkedChest(block, false);
        if (inputChest != null && inputChest.getState(false) instanceof Container ic) {
            pullFromInventory(inventory, ic.getInventory(), recipe);
        }

        // PRIORITAS 2: Buffer internal (slot 9-26)
        pullFromSelf(inventory, recipe);

        // PRIORITAS 3: Hopper input
        List<Hopper> inputHoppers = findInputHoppers(block);
        for (Hopper hopper : inputHoppers) {
            pullFromInventory(inventory, hopper.getInventory(), recipe);
        }
    }

    private void pullFromSelf(Inventory inventory, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            if (required == null || required.getType().isAir()) continue;
            ItemStack current = inventory.getItem(i);
            int needed = requiredAmount(required);
            int currentAmount = (current != null && required.isSimilar(current)) ? current.getAmount() : 0;
            if (currentAmount >= needed) continue;
            if (current != null && !required.isSimilar(current)) continue;
            int toPull = needed - currentAmount;

            for (int slot = 9; slot < inventory.getSize() && toPull > 0; slot++) {
                ItemStack selfItem = inventory.getItem(slot);
                if (selfItem != null && required.isSimilar(selfItem)) {
                    int take = Math.min(toPull, selfItem.getAmount());
                    if (current == null) {
                        ItemStack moved = selfItem.clone(); moved.setAmount(take);
                        inventory.setItem(i, moved); current = moved;
                    } else { current.setAmount(current.getAmount() + take); }
                    selfItem.setAmount(selfItem.getAmount() - take);
                    if (selfItem.getAmount() <= 0) inventory.setItem(slot, null);
                    toPull -= take;
                }
            }
        }
    }

    private void pullFromInventory(Inventory target, Inventory source, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            if (required == null || required.getType().isAir()) continue;
            ItemStack current = target.getItem(i);
            int needed = requiredAmount(required);
            int currentAmount = (current != null && required.isSimilar(current)) ? current.getAmount() : 0;
            if (currentAmount >= needed) continue;
            if (current != null && !required.isSimilar(current)) continue;
            int toPull = needed - currentAmount;

            for (int slot = 0; slot < source.getSize() && toPull > 0; slot++) {
                ItemStack sourceItem = source.getItem(slot);
                if (sourceItem != null && required.isSimilar(sourceItem)) {
                    int take = Math.min(toPull, sourceItem.getAmount());
                    ItemStack pulled = sourceItem.clone(); pulled.setAmount(take);
                    if (current == null) { target.setItem(i, pulled); current = pulled; }
                    else { current.setAmount(current.getAmount() + take); }
                    sourceItem.setAmount(sourceItem.getAmount() - take);
                    if (sourceItem.getAmount() <= 0) source.setItem(slot, null);
                    toPull -= take;
                }
            }
        }
    }

    private void consumeIngredients(Inventory inventory, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            ItemStack current = inventory.getItem(i);
            if (required == null || required.getType().isAir()) {
                if (current != null && !current.getType().isAir()) inventory.setItem(i, null);
            } else {
                if (current != null && !current.getType().isAir()) {
                    int newAmount = current.getAmount() - requiredAmount(required);
                    inventory.setItem(i, newAmount <= 0 ? null : new ItemStack(current.getType(), newAmount));
                }
            }
        }
    }

    private int requiredAmount(ItemStack required) {
        int amount = required.getAmount();
        return amount <= 0 ? 1 : Math.min(amount, required.getMaxStackSize());
    }

    // ==================== HOPPER LOGIC ====================

    public List<Hopper> findInputHoppers(Block block) {
        List<Hopper> hoppers = new ArrayList<>();
        for (BlockFace face : inputFaces) {
            Block relative = block.getRelative(face);
            if (!(relative.getState(false) instanceof Hopper hopper)) continue;
            if (isFeedingInto(relative, block)) hoppers.add(hopper);
        }
        return hoppers;
    }

    public List<Hopper> findOutputHoppers(Block block) {
        List<Hopper> hoppers = new ArrayList<>();
        if (outputFaces.contains(BlockFace.DOWN)) {
            Block relative = block.getRelative(BlockFace.DOWN);
            if (relative.getState(false) instanceof Hopper hopper) hoppers.add(hopper);
        }
        return hoppers;
    }

    private boolean isFeedingInto(Block hopperBlock, Block target) {
        if (hopperBlock.getBlockData() instanceof org.bukkit.block.data.type.Hopper data) {
            Block fed = hopperBlock.getRelative(data.getFacing());
            return fed.getWorld().equals(target.getWorld())
                    && fed.getX() == target.getX()
                    && fed.getY() == target.getY()
                    && fed.getZ() == target.getZ();
        }
        return false;
    }

    private int totalCapacity(List<Hopper> hoppers, ItemStack stack) {
        int total = 0;
        for (Hopper hopper : hoppers) total += freeSpace(hopper.getInventory(), stack);
        return total;
    }

    private int freeSpace(Inventory inventory, ItemStack stack) {
        int max = stack.getMaxStackSize() <= 0 ? 64 : stack.getMaxStackSize();
        int space = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType().isAir()) space += max;
            else if (slot.isSimilar(stack)) space += Math.max(0, max - slot.getAmount());
        }
        return space;
    }

    /**
     * Kirim hasil craft: output chest dulu → hopper → drop
     */
    private void deliver(Block origin, Inventory outputChestInv, List<Hopper> hoppers, ItemStack result) {
        ItemStack remaining = result.clone();

        // 1. Coba masukkan ke output chest
        if (outputChestInv != null) {
            remaining = tryAddToInventory(outputChestInv, remaining);
        }

        // 2. Kalau masih sisa, coba ke hopper output
        if (remaining != null && remaining.getAmount() > 0) {
            for (Hopper hopper : hoppers) {
                if (remaining == null || remaining.getAmount() <= 0) break;
                remaining = tryAddToInventory(hopper.getInventory(), remaining);
            }
        }

        // 3. Kalau masih sisa juga, drop
        if (remaining != null && remaining.getAmount() > 0) {
            origin.getWorld().dropItemNaturally(origin.getLocation(), remaining);
        }
    }

    /**
     * Coba masukkan item ke inventory, kembalikan sisa yang tidak masuk
     */
    private ItemStack tryAddToInventory(Inventory inventory, ItemStack item) {
        if (item == null || item.getAmount() <= 0) return null;
        Map<Integer, ItemStack> leftover = inventory.addItem(item);
        if (leftover.isEmpty()) return null;
        // Gabungkan semua leftover
        ItemStack combined = null;
        for (ItemStack left : leftover.values()) {
            if (combined == null) {
                combined = left.clone();
            } else {
                combined.setAmount(combined.getAmount() + left.getAmount());
            }
        }
        return combined;
    }

    public boolean isAllowedInputSide(Block destBlock, Block sourceBlock) {
        for (BlockFace face : SIDES) {
            if (destBlock.getRelative(face).equals(sourceBlock)) return inputFaces.contains(face);
        }
        return false;
    }

    public Material getBlockMaterial() { return blockMaterial; }
    public int getMinPower() { return minPower; }
    public List<Hopper> getOutputHoppers(Block target) { return findOutputHoppers(target); }

    public ItemStack[] getGrid(Block block) {
        ItemStack[] grid = new ItemStack[9];
        if (!(block.getState(false) instanceof Container container)) return grid;
        Inventory inv = container.getInventory();
        for (int i = 0; i < 9; i++) grid[i] = inv.getItem(i);
        return grid;
    }

    public List<String> describeAdjacentHoppers(Block block) {
        List<String> lines = new ArrayList<>();
        for (BlockFace face : SIDES) {
            Block relative = block.getRelative(face);
            if (!(relative.getState(false) instanceof Hopper hopper)) continue;
            String facing = "?";
            if (relative.getBlockData() instanceof org.bukkit.block.data.type.Hopper data) facing = data.getFacing().name();
            boolean isInput = isFeedingInto(relative, block);
            boolean isOutput = (face == BlockFace.DOWN);
            StringBuilder items = new StringBuilder();
            for (ItemStack item : hopper.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    if (items.length() > 0) items.append(", ");
                    items.append(item.getType().name()).append("x").append(item.getAmount());
                }
            }
            lines.add(face.name() + ": facing=" + facing + " input=" + isInput + " output=" + isOutput + " contents=[" + items + "]");
        }
        return lines;
    }

    // ==================== VANILLA RECIPE MATCHING ====================

    public Recipe findMatchingVanillaRecipe(ItemStack[] pattern) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof ShapedRecipe shaped && matchesShaped(pattern, shaped)) return recipe;
            if (recipe instanceof ShapelessRecipe shapeless && matchesShapeless(pattern, shapeless)) return recipe;
        }
        return null;
    }

    private boolean matchesShaped(ItemStack[] pattern, ShapedRecipe recipe) {
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> map = recipe.getChoiceMap();
        int rows = shape.length; if (rows == 0) return false;
        int cols = shape[0].length();
        if (rows > 3 || cols > 3) return false;
        for (int dr = 0; dr <= 3 - rows; dr++) {
            for (int dc = 0; dc <= 3 - cols; dc++) {
                if (matchesAtShift(pattern, shape, map, dr, dc)) return true;
            }
        }
        return false;
    }

    private boolean matchesAtShift(ItemStack[] pattern, String[] shape, Map<Character, RecipeChoice> map, int dr, int dc) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                ItemStack patternItem = pattern[r * 3 + c];
                int shapeR = r - dr, shapeC = c - dc;
                if (shapeR >= 0 && shapeR < shape.length && shapeC >= 0 && shapeC < shape[shapeR].length()) {
                    RecipeChoice choice = map.get(shape[shapeR].charAt(shapeC));
                    if (choice == null) { if (patternItem != null && !patternItem.getType().isAir()) return false; }
                    else { if (patternItem == null || patternItem.getType().isAir() || !choice.test(patternItem)) return false; }
                } else { if (patternItem != null && !patternItem.getType().isAir()) return false; }
            }
        }
        return true;
    }

    private boolean matchesShapeless(ItemStack[] pattern, ShapelessRecipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>(recipe.getChoiceList());
        for (ItemStack patternItem : pattern) {
            if (patternItem == null || patternItem.getType().isAir()) continue;
            boolean found = false;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).test(patternItem)) { choices.remove(i); found = true; break; }
            }
            if (!found) return false;
        }
        return choices.isEmpty();
    }
}