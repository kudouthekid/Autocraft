package com.example.autocraft;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkSessionManager {

    public record LinkSession(Block crafter, boolean isOutput) {}

    private static final Map<UUID, LinkSession> sessions = new ConcurrentHashMap<>();

    private LinkSessionManager() {}

    public static void start(Player player, Block crafter, boolean isOutput) {
        sessions.put(player.getUniqueId(), new LinkSession(crafter, isOutput));
        String type = isOutput ? "OUTPUT" : "INPUT";
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "🔗 Link " + type + " mode aktif! Klik chest/container target.",
                net.kyori.adventure.text.format.NamedTextColor.AQUA));
    }

    public static LinkSession consume(Player player) {
        return sessions.remove(player.getUniqueId());
    }

    public static LinkSession get(UUID uuid) {
        return sessions.get(uuid);
    }

    public static void remove(UUID uuid) {
        sessions.remove(uuid);
    }
}