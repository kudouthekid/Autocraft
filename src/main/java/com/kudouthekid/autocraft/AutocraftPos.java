package com.example.autocraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class AutocraftPos {

    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;

    public AutocraftPos(UUID worldId, int x, int y, int z) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static AutocraftPos of(Location location) {
        Objects.requireNonNull(location.getWorld(), "location.world");
        return new AutocraftPos(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public static AutocraftPos parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid position: " + value);
        }

        return new AutocraftPos(
                UUID.fromString(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
        );
    }

    public String serialize() {
        return worldId.toString() + "," + x + "," + y + "," + z;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutocraftPos other)) return false;
        return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }

    @Override
    public String toString() {
        return serialize();
    }
}