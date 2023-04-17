package com.zergatul.cheatutils.utils;

import net.minecraft.nbt.*;

public class NbtUtils {

    public static boolean hasShort(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof ShortTag;
    }

    public static boolean hasInt(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof IntTag;
    }

    public static boolean hasBytes(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof ByteArrayTag;
    }

    public static boolean hasLongs(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof LongArrayTag;
    }

    public static boolean hasCompound(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof CompoundTag;
    }

    public static boolean hasString(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof StringTag;
    }

    public static boolean hasList(CompoundTag compound, String key) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        return value instanceof ListTag;
    }
}