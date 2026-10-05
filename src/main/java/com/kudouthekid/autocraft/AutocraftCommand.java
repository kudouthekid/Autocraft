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
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "set" -> handleSet(sender);
            case "remove" -> handleRemove(sender);
            case "recipe" -> handleRecipe(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("autocraft.admin")) {
            noPermission(sender);
            return;
        }

        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);

            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
            return;
        }

        target.getInventory().addItem(manager.createItem())
                .values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        sender.sendMessage(Component.text("AutoCraft item given.", NamedTextColor.GREEN));
    }

    private void handleSet(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("autocraft.admin")) {
            noPermission(player);
            return;
        }

        Block target = player.getTargetBlockExact(6);

        if (target == null || target.getType() != manager.getBlockMaterial()) {
            player.sendMessage(
                    Component.text("Target a " + manager.getBlockMaterial() + " block.", NamedTextColor.RED)
            );
            return;
        }

        if (manager.isAutocraft(target)) {
            player.sendMessage(Component.text("That block is already an AutoCraft block.", NamedTextColor.RED));
            return;
        }

        if (manager.create(target)) {
            player.sendMessage(Component.text("AutoCraft block created.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to create AutoCraft block.", NamedTextColor.RED));
        }
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("autocraft.admin")) {
            noPermission(player);
            return;
        }

        Block target = player.getTargetBlockExact(6);

        if (target == null || !manager.isAutocraft(target)) {
            player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED));
            return;
        }

        manager.dropContents(target);
        manager.remove(target, true);

        player.sendMessage(Component.text("AutoCraft block removed.", NamedTextColor.GREEN));
    }

    private void handleRecipe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("autocraft.configure")) {
            noPermission(player);
            return;
        }

        Block target = player.getTargetBlockExact(6);

        if (target == null || !manager.isAutocraft(target)) {
            player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED));
            return;
        }

        manager.openRecipeGui(player, target);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("autocraft.admin")) {
            noPermission(sender);
            return;
        }

        plugin.reloadConfig();
        manager.load();
        manager.scanLoadedChunks();

        sender.sendMessage(Component.text("AutoCraft configuration reloaded.", NamedTextColor.GREEN));
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("AutoCraft commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/autocraft give [player] - give AutoCraft block", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft set - convert targeted barrel to AutoCraft", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft remove - remove targeted AutoCraft", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft recipe - open recipe GUI for targeted AutoCraft", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/autocraft reload - reload configuration", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "set", "remove", "recipe", "reload", "status"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null;
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String option : options) {
            if (option.startsWith(lower)) {
                result.add(option);
            }
        }

        return result;
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("autocraft.use")) {
            noPermission(player);
            return;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || !manager.isAutocraft(target)) {
            player.sendMessage(Component.text("Target an AutoCraft block.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== AutoCraft Status ===", NamedTextColor.GOLD));

        boolean powered = manager.isPowered(target);
        player.sendMessage(Component.text(
                "Powered: " + powered + " (min power: " + manager.getMinPower() + ")",
                powered ? NamedTextColor.GREEN : NamedTextColor.RED
        ));

        AutocraftRecipe recipe = manager.getRecipe(target);
        if (recipe == null) {
            player.sendMessage(Component.text("Recipe: NONE", NamedTextColor.RED));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                ItemStack item = recipe.pattern()[i];
                if (item != null && !item.getType().isAir()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(i).append(":").append(item.getType().name()).append("x").append(item.getAmount());
                }
            }
            player.sendMessage(Component.text("Recipe grid: " + sb, NamedTextColor.YELLOW));
            player.sendMessage(Component.text(
                    "Result: " + recipe.result().getType().name() + " x" + recipe.result().getAmount(),
                    NamedTextColor.YELLOW
            ));
        }

        ItemStack[] grid = manager.getGrid(target);
        StringBuilder gb = new StringBuilder();
        int filled = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack item = grid[i];
            if (item != null && !item.getType().isAir()) {
                filled++;
                if (gb.length() > 0) gb.append(", ");
                gb.append(i).append(":").append(item.getType().name()).append("x").append(item.getAmount());
            }
        }
        player.sendMessage(Component.text(
                "Buffer grid (" + filled + "/9): " + (gb.length() == 0 ? "empty" : gb.toString()),
                NamedTextColor.AQUA
        ));

                List<Hopper> hoppers = manager.getOutputHoppers(target);
        player.sendMessage(Component.text(
                "Output hoppers found: " + hoppers.size(),
                hoppers.isEmpty() ? NamedTextColor.RED : NamedTextColor.GREEN
        ));

        for (String line : manager.describeAdjacentHoppers(target)) {
            player.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    
      }
      
}