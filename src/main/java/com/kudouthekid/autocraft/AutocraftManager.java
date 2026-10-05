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
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    };

    private static final Set<BlockFace> SIDE_SET = EnumSet.copyOf(Arrays.asList(SIDES));

    private final AutoCraftPlugin plugin;
    private final Set<AutocraftPos> positions = ConcurrentHashMap.newKeySet();

    private final NamespacedKey flagKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey ghostKey;

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
    }

    public void load() {
        positions.clear();

        for (String entry : plugin.getConfig().getStringList("locations")) {
            try {
                positions.add(AutocraftPos.parse(entry));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid location entry: " + entry);
            }
        }

        reloadSettings();
    }

    public ItemStack[] getGrid(Block block) {
        ItemStack[] grid = new ItemStack[9];
        if (!(block.getState() instanceof Container container)) {
            return grid;
        }
        Inventory inv = container.getInventory();
        for (int i = 0; i < 9; i++) {
            grid[i] = inv.getItem(i);
        }
        return grid;
    }

    public List<Hopper> getOutputHoppers(Block block) {
        return findOutputHoppers(block);
    }

    public void save() {
        List<String> list = positions.stream()
                .map(AutocraftPos::serialize)
                .toList();

        plugin.getConfig().set("locations", list);
        plugin.saveConfig();
    }

    public void reloadSettings() {
        FileConfiguration config = plugin.getConfig();

        Material material = Material.matchMaterial(config.getString("block-material", "BARREL"));
        if (material != null && material.isBlock()) {
            this.blockMaterial = material;
        } else {
            this.blockMaterial = Material.BARREL;
        }

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
                if (SIDE_SET.contains(face)) {
                    faces.add(face);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (faces.isEmpty()) {
            return EnumSet.copyOf(SIDE_SET);
        }

        return faces;
    }

    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state.getType() == blockMaterial) {
                        isAutocraft(state.getBlock());
                    }
                }
            }
        }
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(blockMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("AutoCraft Block", NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Place, then shift + right-click to set recipe.", NamedTextColor.GRAY),
                    Component.text("Requires enough redstone power.", NamedTextColor.GRAY)
            ));

            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isAutocraftItem(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public boolean create(Block block) {
        if (block == null || block.getType() != blockMaterial) {
            return false;
        }

        if (isAutocraft(block)) {
            return false;
        }

        if (!(block.getState() instanceof TileState tile)) {
            return false;
        }

        tile.getPersistentDataContainer().set(flagKey, PersistentDataType.BYTE, (byte) 1);
        tile.update(true, false);

        positions.add(AutocraftPos.of(block.getLocation()));
        save();
        return true;
    }

    public boolean remove(Block block, boolean dropItem) {
        if (block == null || !isAutocraft(block)) {
            return false;
        }

        clearInventory(block);

        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(flagKey);
            tile.getPersistentDataContainer().remove(recipeKey);
            tile.update(true, false);
        }

        positions.remove(AutocraftPos.of(block.getLocation()));
        save();

        if (dropItem) {
            block.getWorld().dropItemNaturally(block.getLocation(), createItem());
        }

        return true;
    }

    public void dropContents(Block block) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }

        Inventory inventory = container.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                block.getWorld().dropItemNaturally(block.getLocation(), item.clone());
            }
        }

        inventory.clear();
        container.update(true, false);
    }

    public void clearInventory(Block block) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }

        container.getInventory().clear();
        container.update(true, false);
    }

    public boolean isAutocraft(Block block) {
        if (block == null || block.getType() != blockMaterial) {
            return false;
        }

        if (!hasFlag(block)) {
            positions.remove(AutocraftPos.of(block.getLocation()));
            return false;
        }

        positions.add(AutocraftPos.of(block.getLocation()));
        return true;
    }

    private boolean hasFlag(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return false;
        }

        return tile.getPersistentDataContainer().has(flagKey, PersistentDataType.BYTE);
    }

    public AutocraftRecipe getRecipe(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return null;
        }

        String data = tile.getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            ItemStack[] loaded = SerializationUtil.fromBase64(data);
            if (loaded.length != 10) {
                return null;
            }

            ItemStack[] pattern = Arrays.copyOf(loaded, 9);
            ItemStack result = loaded[9];

            if (result == null || result.getType().isAir()) {
                return null;
            }

            boolean allEmpty = Arrays.stream(pattern)
                    .allMatch(item -> item == null || item.getType().isAir());

            if (allEmpty) {
                return null;
            }

            return new AutocraftRecipe(pattern, result);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load AutoCraft recipe at " + block.getLocation());
            return null;
        }
    }

    public void setRecipe(Block block, ItemStack[] pattern, ItemStack result) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }

        ItemStack[] safePattern = new ItemStack[9];
        if (pattern != null) {
            for (int i = 0; i < Math.min(9, pattern.length); i++) {
                safePattern[i] = cleanRecipeItem(pattern[i]);
            }
        }

        ItemStack cleanResult = cleanRecipeItem(result);

        boolean empty = cleanResult == null
                || cleanResult.getType().isAir()
                || Arrays.stream(safePattern).allMatch(item -> item == null || item.getType().isAir());

        AutocraftRecipe oldRecipe = getRecipe(block);
        boolean changed = !recipeEquals(oldRecipe, safePattern, empty ? null : cleanResult);

        if (empty) {
            tile.getPersistentDataContainer().remove(recipeKey);
        } else {
            ItemStack[] serialized = new ItemStack[10];

            for (int i = 0; i < 9; i++) {
                serialized[i] = safePattern[i] == null ? null : safePattern[i].clone();
            }

            serialized[9] = cleanResult.clone();

            tile.getPersistentDataContainer().set(
                    recipeKey,
                    PersistentDataType.STRING,
                    SerializationUtil.toBase64(serialized)
            );
        }

        tile.update(true, false);

        if (changed) {
            dropContents(block);
        }
    }

    private boolean recipeEquals(AutocraftRecipe old, ItemStack[] pattern, ItemStack result) {
        boolean newEmpty = result == null
                || result.getType().isAir()
                || Arrays.stream(pattern).allMatch(item -> item == null || item.getType().isAir());

        if (old == null) {
            return newEmpty;
        }

        if (newEmpty) {
            return false;
        }

        if (!old.result().isSimilar(result) || old.result().getAmount() != result.getAmount()) {
            return false;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack a = old.pattern()[i];
            ItemStack b = pattern[i];

            boolean aEmpty = a == null || a.getType().isAir();
            boolean bEmpty = b == null || b.getType().isAir();

            if (aEmpty && bEmpty) {
                continue;
            }

            if (aEmpty || bEmpty) {
                return false;
            }

            if (!a.isSimilar(b) || a.getAmount() != b.getAmount()) {
                return false;
            }
        }

        return true;
    }

    private ItemStack cleanRecipeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().remove(ghostKey);
            clone.setItemMeta(meta);
        }

        return clone;
    }

    public void openRecipeGui(Player player, Block block) {
        RecipeGui.open(plugin, player, block, getRecipe(block));
    }

    public void markGhost(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    public boolean isGhost(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE);
    }

    public boolean isPowered(Block block) {
        int power = block.getBlockPower();

        if (block.isBlockIndirectlyPowered()) {
            power = Math.max(power, 15);
        }

        return power >= minPower;
    }

    public void tickAll() {
        Iterator<AutocraftPos> iterator = positions.iterator();

        while (iterator.hasNext()) {
            AutocraftPos pos = iterator.next();
            Location location = pos.toLocation();

            if (location == null || !location.isChunkLoaded()) {
                continue;
            }

            Block block = location.getBlock();

            if (block.getType() != blockMaterial || !hasFlag(block)) {
                iterator.remove();
                continue;
            }

            if (!isPowered(block)) {
                continue;
            }

            attemptCraft(block);
        }
    }

    public void attemptCraft(Block block) {
        AutocraftRecipe recipe = getRecipe(block);
        if (recipe == null) {
            return;
        }

        if (!(block.getState() instanceof Container container)) {
            return;
        }

        Inventory inventory = container.getInventory();

        if (!hasIngredients(inventory, recipe)) {
            return;
        }

        ItemStack result = recipe.result().clone();
        if (result.getAmount() <= 0) {
            result.setAmount(1);
        }

        List<Hopper> hoppers = findOutputHoppers(block);
        if (totalCapacity(hoppers, result) < result.getAmount()) {
            return;
        }

        consumeIngredients(inventory, recipe);
        container.update(true, false);

        deliver(block, hoppers, result);
    }

    private boolean hasIngredients(Inventory inventory, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            ItemStack current = inventory.getItem(i);

            if (required == null || required.getType().isAir()) {
                if (current != null && !current.getType().isAir()) {
                    return false;
                }
            } else {
                if (current == null || current.getType().isAir()) {
                    return false;
                }

                if (!required.isSimilar(current)) {
                    return false;
                }

                if (current.getAmount() < requiredAmount(required)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void consumeIngredients(Inventory inventory, AutocraftRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack required = recipe.pattern()[i];
            ItemStack current = inventory.getItem(i);

            if (required == null || required.getType().isAir()) {
                if (current != null && !current.getType().isAir()) {
                    inventory.setItem(i, null);
                }
            } else {
                if (current == null || current.getType().isAir()) {
                    continue;
                }

                int need = requiredAmount(required);
                int newAmount = current.getAmount() - need;

                if (newAmount <= 0) {
                    inventory.setItem(i, null);
                } else {
                    current.setAmount(newAmount);
                }
            }
        }
    }

    private int requiredAmount(ItemStack required) {
        int amount = required.getAmount();
        if (amount <= 0) {
            amount = 1;
        }

        int max = required.getMaxStackSize();
        if (max <= 0) {
            max = 64;
        }

        return Math.min(amount, max);
    }

    private List<Hopper> findOutputHoppers(Block block) {
        List<Hopper> hoppers = new ArrayList<>();

        for (BlockFace face : outputFaces) {
            Block relative = block.getRelative(face);

            if (!(relative.getState() instanceof Hopper hopper)) {
                continue;
            }

            // Hopper yang mengarah MASUK ke autocraft adalah hopper input,
            // jangan pernah dipakai sebagai target output.
            if (isFeedingInto(relative, block)) {
                continue;
            }

            hoppers.add(hopper);
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

        for (Hopper hopper : hoppers) {
            total += freeSpace(hopper.getInventory(), stack);
        }

        return total;
    }

    private int freeSpace(Inventory inventory, ItemStack stack) {
        int max = stack.getMaxStackSize();
        if (max <= 0) {
            max = 64;
        }

        int space = 0;

        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType().isAir()) {
                space += max;
            } else if (slot.isSimilar(stack)) {
                space += Math.max(0, max - slot.getAmount());
            }
        }

        return space;
    }

    private void deliver(Block origin, List<Hopper> hoppers, ItemStack result) {
        ItemStack remaining = result.clone();

        int max = remaining.getMaxStackSize();
        if (max <= 0) {
            max = 64;
        }

        while (remaining.getAmount() > 0) {
            boolean addedAny = false;

            for (Hopper hopper : hoppers) {
                if (remaining.getAmount() <= 0) {
                    break;
                }

                Inventory inventory = hopper.getInventory();
                int space = freeSpace(inventory, remaining);

                if (space <= 0) {
                    continue;
                }

                int addAmount = Math.min(max, Math.min(space, remaining.getAmount()));
                if (addAmount <= 0) {
                    continue;
                }

                ItemStack toAdd = remaining.clone();
                toAdd.setAmount(addAmount);

                Map<Integer, ItemStack> leftoverMap = inventory.addItem(toAdd);
                int leftoverAmount = leftoverMap.values().stream()
                        .mapToInt(ItemStack::getAmount)
                        .sum();

                int accepted = addAmount - leftoverAmount;

                if (accepted > 0) {
                    remaining.setAmount(remaining.getAmount() - accepted);
                    hopper.update(true, false);
                    addedAny = true;
                }
            }

            if (!addedAny) {
                break;
            }
        }

        if (remaining.getAmount() > 0) {
            origin.getWorld().dropItemNaturally(origin.getLocation(), remaining);
        }
    }

    public boolean isAllowedInputSide(Block destBlock, Block sourceBlock) {
        for (BlockFace face : SIDES) {
            if (destBlock.getRelative(face).equals(sourceBlock)) {
                return inputFaces.contains(face);
            }
        }

        return false;
    }

    public void handleHopperInput(Block destBlock, Inventory source, Inventory destination, ItemStack moved) {
        if (moved == null || moved.getType().isAir()) {
            return;
        }

        if (inputRequiresPower && !isPowered(destBlock)) {
            return;
        }

        AutocraftRecipe recipe = getRecipe(destBlock);
        if (recipe == null) {
            return;
        }

        int planned = calculateAccepted(recipe, destination, moved, moved.getAmount());
        if (planned <= 0) {
            return;
        }

        ItemStack toRemove = moved.clone();
        toRemove.setAmount(planned);

        Map<Integer, ItemStack> leftoverMap = source.removeItem(toRemove);
        int leftover = leftoverMap.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();

        int actual = planned - leftover;
        if (actual <= 0) {
            return;
        }

        distribute(destination, recipe, moved, actual);
    }

    private int calculateAccepted(AutocraftRecipe recipe, Inventory grid, ItemStack item, int amount) {
        int remaining = amount;
        int accepted = 0;

        for (int i = 0; i < 9 && remaining > 0; i++) {
            ItemStack required = recipe.pattern()[i];

            if (required == null || required.getType().isAir()) {
                continue;
            }

            if (!required.isSimilar(item)) {
                continue;
            }

            ItemStack current = grid.getItem(i);

            if (current != null && !current.getType().isAir() && !required.isSimilar(current)) {
                continue;
            }

            int currentAmount = current != null ? current.getAmount() : 0;
            int requiredAmount = requiredAmount(required);

            if (currentAmount >= requiredAmount) {
                continue;
            }

            int canAdd = Math.min(requiredAmount - currentAmount, remaining);
            accepted += canAdd;
            remaining -= canAdd;
        }

        return accepted;
    }

    private void distribute(Inventory grid, AutocraftRecipe recipe, ItemStack item, int amount) {
        int remaining = amount;

        for (int i = 0; i < 9 && remaining > 0; i++) {
            ItemStack required = recipe.pattern()[i];

            if (required == null || required.getType().isAir()) {
                continue;
            }

            if (!required.isSimilar(item)) {
                continue;
            }

            ItemStack current = grid.getItem(i);

            if (current != null && !current.getType().isAir() && !required.isSimilar(current)) {
                continue;
            }

            int currentAmount = current != null ? current.getAmount() : 0;
            int requiredAmount = requiredAmount(required);

            if (currentAmount >= requiredAmount) {
                continue;
            }

            int canAdd = Math.min(requiredAmount - currentAmount, remaining);
            if (canAdd <= 0) {
                continue;
            }

            if (current == null || current.getType().isAir()) {
                ItemStack add = item.clone();
                add.setAmount(canAdd);
                grid.setItem(i, add);
            } else {
                current.setAmount(currentAmount + canAdd);
            }

            remaining -= canAdd;
        }
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public int getMinPower() {
        return minPower;
    }

    public List<String> describeAdjacentHoppers(Block block) {
        List<String> lines = new ArrayList<>();

        for (BlockFace face : SIDES) {
            Block relative = block.getRelative(face);

            if (!(relative.getState() instanceof Hopper hopper)) {
                continue;
            }

            String facing = "?";
            if (relative.getBlockData() instanceof org.bukkit.block.data.type.Hopper data) {
                facing = data.getFacing().name();
            }

            boolean feeding = isFeedingInto(relative, block);
            boolean locked = relative.isBlockPowered() || relative.getBlockPower() > 0;

            StringBuilder items = new StringBuilder();
            for (ItemStack item : hopper.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    if (items.length() > 0) items.append(", ");
                    items.append(item.getType().name()).append("x").append(item.getAmount());
                }
            }

            lines.add(face.name() + ": facing=" + facing
                    + " input=" + feeding
                    + " locked=" + locked
                    + " contents=[" + items + "]");
        }

        return lines;
    }

}