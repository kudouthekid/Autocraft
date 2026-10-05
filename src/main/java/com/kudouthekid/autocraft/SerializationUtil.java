package com.example.autocraft;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class SerializationUtil {

    private SerializationUtil() {
    }

    public static String toBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOut = new BukkitObjectOutputStream(byteOut)) {
                dataOut.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOut.writeObject(item);
                }
                dataOut.flush();
            }
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize ItemStack[]", ex);
        }
    }

    public static ItemStack[] fromBase64(String data) {
        try {
            ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            try (BukkitObjectInputStream dataIn = new BukkitObjectInputStream(byteIn)) {
                int size = dataIn.readInt();
                ItemStack[] items = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    items[i] = (ItemStack) dataIn.readObject();
                }
                return items;
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Failed to deserialize ItemStack[]", ex);
        }
    }
}