package com.example.autocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Hopper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutocraftCommand implements TabExecutor {

    private final AutoCraftPlugin plugin;
    private final AutocraftManager manager;

    public AutocraftCommand(AutoCraftPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "set" -> handleSet(sender);
            case "remove" -> handleRemove(sender);
            case "recipe" -> handleRecipe(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "link" -> handleLink(sender, args);
            case "unlink" -> handleUnlink(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("autocraft.admin")) { noPermission(sender); return; }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return; }
        } else if (sender instanceof Player player) { target = player; }
        else { sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED)); return; }
        target.getInventory().addItem(manager.createItem())
                .values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage(Component.text("AutoCraft item given.", NamedTextColor.GREEN));
    }

    private void handleSet(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.admin")) { noPermission(player); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != manager.getBlockMaterial()) {
            player.sendMessage(Component.text("Target a " + manager.getBlockMaterial() + " block.", NamedTextColor.RED)); return;
        }
        if (manager.isAutocraft(target)) { player.sendMessage(Component.text("Already an AutoCraft block.", NamedTextColor.RED)); return; }
        if (manager.create(target)) player.sendMessage(Component.text("AutoCraft block created.", NamedTextColor.GREEN));
        else player.sendMessage(Component.text("Failed to create.", NamedTextColor.RED));
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.admin")) { noPermission(player); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) { player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED)); return; }
        manager.dropContents(target);
        manager.remove(target, true);
        player.sendMessage(Component.text("AutoCraft block removed.", NamedTextColor.GREEN));
    }

    private void handleRecipe(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.configure")) { noPermission(player); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) { player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED)); return; }
        manager.openRecipeGui(player, target);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("autocraft.admin")) { noPermission(sender); return; }
        plugin.reloadConfig();
        manager.load();
        manager.scanLoadedChunks();
        sender.sendMessage(Component.text("AutoCraft configuration reloaded.", NamedTextColor.GREEN));
    }

    // ==================== LINK / UNLINK ====================

    private void handleLink(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.configure")) { noPermission(player); return; }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage:", NamedTextColor.GOLD));
            player.sendMessage(Component.text("  /autocraft link input  - link chest sebagai sumber bahan", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  /autocraft link output - link chest sebagai tujuan hasil", NamedTextColor.YELLOW));
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        if (!type.equals("input") && !type.equals("output")) {
            player.sendMessage(Component.text("Tipe harus 'input' atau 'output'.", NamedTextColor.RED));
            return;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) {
            player.sendMessage(Component.text("Target AutoCraft block terlebih dahulu, lalu jalankan command ini.", NamedTextColor.RED));
            return;
        }

        boolean isOutput = type.equals("output");
        // Akses listener untuk memulai link mode
        plugin.getServer().getPluginManager().getPlugin("AutoCraft");
        // Kita perlu akses ke listener. Cara paling mudah: simpan di manager atau buat method di plugin.
        // Untuk simplicity, kita pakai pendekatan langsung:
        LinkSessionManager.start(player, target, isOutput);
    }

    private void startLinkMode(Player player, Block crafter, boolean isOutput) {
        // Simpan session di static map (atau bisa lewat manager)
        LinkSessionManager.start(player, crafter, isOutput);
    }

    private void handleUnlink(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.configure")) { noPermission(player); return; }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage:", NamedTextColor.GOLD));
            player.sendMessage(Component.text("  /autocraft unlink input  - hapus link chest input", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  /autocraft unlink output - hapus link chest output", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  /autocraft unlink all    - hapus semua link", NamedTextColor.YELLOW));
            return;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) {
            player.sendMessage(Component.text("Target AutoCraft block.", NamedTextColor.RED));
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        switch (type) {
            case "input" -> {
                if (manager.unlinkChest(target, false))
                    player.sendMessage(Component.text("✅ Input chest unlinked.", NamedTextColor.GREEN));
                else player.sendMessage(Component.text("Tidak ada input chest link.", NamedTextColor.YELLOW));
            }
            case "output" -> {
                if (manager.unlinkChest(target, true))
                    player.sendMessage(Component.text("✅ Output chest unlinked.", NamedTextColor.GREEN));
                else player.sendMessage(Component.text("Tidak ada output chest link.", NamedTextColor.YELLOW));
            }
            case "all" -> {
                boolean any = false;
                if (manager.unlinkChest(target, false)) any = true;
                if (manager.unlinkChest(target, true)) any = true;
                if (any) player.sendMessage(Component.text("✅ Semua chest link dihapus.", NamedTextColor.GREEN));
                else player.sendMessage(Component.text("Tidak ada chest link.", NamedTextColor.YELLOW));
            }
            default -> player.sendMessage(Component.text("Tipe harus 'input', 'output', atau 'all'.", NamedTextColor.RED));
        }
    }

    // ==================== STATUS ====================

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Only players.", NamedTextColor.RED)); return; }
        if (!player.hasPermission("autocraft.use")) { noPermission(player); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) { player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED)); return; }

        player.sendMessage(Component.text("=== AutoCraft Status ===", NamedTextColor.GOLD));

        boolean powered = manager.isPowered(target);
        player.sendMessage(Component.text("Powered: " + powered + " (min: " + manager.getMinPower() + ")",
                powered ? NamedTextColor.GREEN : NamedTextColor.RED));

        AutocraftRecipe recipe = manager.getRecipe(target);
        if (recipe == null) {
            player.sendMessage(Component.text("Recipe: NONE", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Result: " + recipe.result().getType().name() + " x" + recipe.result().getAmount(), NamedTextColor.YELLOW));
        }

        // Input chest
        Block inputChest = manager.getLinkedChest(target, false);
        if (inputChest != null) {
            player.sendMessage(Component.text("📥 Input Chest: " + inputChest.getType().name()
                    + " [" + inputChest.getX() + ", " + inputChest.getY() + ", " + inputChest.getZ() + "]",
                    NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("📥 Input Chest: NONE", NamedTextColor.GRAY));
        }

        // Output chest
        Block outputChest = manager.getLinkedChest(target, true);
        if (outputChest != null) {
            player.sendMessage(Component.text("📤 Output Chest: " + outputChest.getType().name()
                    + " [" + outputChest.getX() + ", " + outputChest.getY() + ", " + outputChest.getZ() + "]",
                    NamedTextColor.LIGHT_PURPLE));
        } else {
            player.sendMessage(Component.text("📤 Output Chest: NONE", NamedTextColor.GRAY));
        }

        List<Hopper> hoppers = manager.getOutputHoppers(target);
        player.sendMessage(Component.text("Output hoppers: " + hoppers.size(),
                hoppers.isEmpty() ? NamedTextColor.RED : NamedTextColor.GREEN));

        for (String line : manager.describeAdjacentHoppers(target)) {
            player.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== AutoCraft Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/autocraft give [player]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft set", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft remove", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft recipe", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft link <input|output>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft unlink <input|output|all>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft status", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft reload", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "set", "remove", "recipe", "reload", "status", "link", "unlink"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("link") || args[0].equalsIgnoreCase("unlink")) {
                return filter(List.of("input", "output", "all"), args[1]);
            }
            if (args[0].equalsIgnoreCase("give")) return null;
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) result.add(option);
        }
        return result;
    }
}