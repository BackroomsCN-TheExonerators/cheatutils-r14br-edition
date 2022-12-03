package com.zergatul.cheatutils.utils;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public class ItemUtils {

    public static boolean isShulkerBox(ItemStack itemStack) {
        if (itemStack.getItem() instanceof BlockItem) {
            Block block = (((BlockItem)itemStack.getItem())).getBlock();
            if (block instanceof ShulkerBoxBlock) {
                return true;
            }
        }

        return false;
    }

    public static NonNullList<ItemStack> getShulkerContent(ItemStack itemStack) {
        CompoundTag compound = itemStack.getTagElement("BlockEntityTag");
        if (compound != null) {
            if (compound.contains("Items", 9)) {
                NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(compound, list);
                return list;
            }
        }
        return NonNullList.withSize(0, ItemStack.EMPTY);
    }

    public static int getStackSize(Item item) {
        ItemStack itemStack = new ItemStack(item, 1);
        return itemStack.getMaxStackSize();
    }
}