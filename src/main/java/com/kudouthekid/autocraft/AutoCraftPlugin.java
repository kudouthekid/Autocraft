package com.example.autocraft;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoCraftPlugin extends JavaPlugin {

    private AutocraftManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.manager = new AutocraftManager(this);
        this.manager.load();
        this.manager.scanLoadedChunks();

        getServer().getPluginManager().registerEvents(new AutocraftListener(this), this);

        PluginCommand command = getCommand("autocraft");
        if (command != null) {
            AutocraftCommand executor = new AutocraftCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        long interval = getConfig().getLong("craft-interval-ticks", 10L);
        if (interval < 1L) interval = 1L;

        Bukkit.getScheduler().runTaskTimer(this, () -> manager.tickAll(), interval, interval);

        getLogger().info("AutoCraft enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
        }
        getLogger().info("AutoCraft disabled.");
    }

    public AutocraftManager manager() {
        return manager;
    }
}