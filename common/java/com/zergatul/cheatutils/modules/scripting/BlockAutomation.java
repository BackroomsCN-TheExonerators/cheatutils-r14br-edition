package com.zergatul.cheatutils.modules.scripting;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zergatul.cheatutils.common.Events;
import com.zergatul.cheatutils.common.Registries;
import com.zergatul.cheatutils.common.events.RenderWorldLastEvent;
import com.zergatul.cheatutils.configs.ConfigStore;
import com.zergatul.cheatutils.configs.BlockAutomationConfig;
import com.zergatul.cheatutils.controllers.CurrentBlockController;
import com.zergatul.cheatutils.modules.automation.VillagerRoller;
import com.zergatul.cheatutils.modules.utilities.RenderUtilities;
import com.zergatul.cheatutils.render.LineRenderer;
import com.zergatul.cheatutils.utils.BlockPlacingMethod;
import com.zergatul.cheatutils.utils.BlockUtils;
import com.zergatul.cheatutils.utils.NearbyBlockEnumerator;
import com.zergatul.cheatutils.utils.SlotSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BlockAutomation {

    public static final BlockAutomation instance = new BlockAutomation();

    private final Minecraft mc = Minecraft.getInstance();
    private final SlotSelector slotSelector = new SlotSelector();
    private Runnable script;
    private String[] itemIds;
    private BlockPlacingMethod method;
    private boolean breakCurrentBlock;
    private String breakItemId;
    private String breakEnchantmentId;
    private BlockPos currentDestroyingBlock;
    private BlockUtils.PlaceBlockPlan debugPlan;
    private volatile boolean debugStep;
    private double actionTickCounter;

    private BlockAutomation() {
        Events.ClientTickEnd.add(this::onClientTickEnd);
        Events.AfterRenderWorld.add(this::onRenderWorldLast);
    }

    public void setScript(Runnable script) {
        this.script = script;
    }

    public void useItem(String itemId, BlockPlacingMethod method) {
        useItem(new String[] { itemId }, method);
    }

    public void useItem(String[] itemIds, BlockPlacingMethod method) {
        this.itemIds = itemIds;
        this.method = method;
    }

    public void breakBlock(String itemId, String enchantmentId) {
        this.breakCurrentBlock = true;
        this.breakItemId = itemId;
        this.breakEnchantmentId = enchantmentId;
    }

    public void placeOne() {
        debugStep = true;
    }

    public boolean isBreakingBlock() {
        BlockAutomationConfig config = ConfigStore.instance.getConfig().blockAutomationConfig;
        if (!config.enabled) {
            return false;
        }
        if (script == null) {
            return false;
        }

        return currentDestroyingBlock != null;
    }

    private void onClientTickEnd() {
        BlockAutomationConfig config = ConfigStore.instance.getConfig().blockAutomationConfig;
        // Block Automation code stops breaking lectern after first tick for Villager Roller
        if (!config.enabled || VillagerRoller.instance.isActive()) {
            actionTickCounter = 0;
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            actionTickCounter = 0;
            return;
        }

        if (script == null) {
            actionTickCounter = 0;
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1);

        actionTickCounter += config.actionsPerTick;
        boolean actionPerformed = false;

        actionLoop:
        while (actionTickCounter >= 1)
        {
            actionTickCounter -= 1;

            if (mc.gameMode.isDestroying()) {
                if (mc.options.keyAttack.isDown()) {
                    // player destroying block
                } else {
                    if (currentDestroyingBlock != null) {
                        // check distance to block
                        if (currentDestroyingBlock.distToCenterSqr(eyePos) > config.maxRange * config.maxRange) {
                            mc.gameMode.stopDestroyBlock();
                        } else {
                            if (mc.gameMode.continueDestroyBlock(currentDestroyingBlock, Direction.UP)) {
                                mc.player.swing(InteractionHand.MAIN_HAND);
                            }
                        }
                    } else {
                        mc.gameMode.stopDestroyBlock();
                    }
                }
                actionTickCounter = 0;
                return;
            }

            currentDestroyingBlock = null;

            for (BlockPos pos : NearbyBlockEnumerator.getPositions(eyePos, config.maxRange)) {
                BlockState state = mc.level.getBlockState(pos);

                itemIds = null;
                breakCurrentBlock = false;
                CurrentBlockController.instance.set(pos, state);
                script.run();
                CurrentBlockController.instance.clear();

                if (breakCurrentBlock && !mc.level.isEmptyBlock(pos) && selectItemForBlockBreak(config)) {
                    currentDestroyingBlock = pos;
                    mc.gameMode.startDestroyBlock(pos, Direction.UP);
                    if (mc.gameMode.continueDestroyBlock(currentDestroyingBlock, Direction.UP)) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                    actionPerformed = true;
                    continue actionLoop;
                } else if (itemIds != null) {
                    for (String itemId : itemIds) {
                        Item item = Registries.ITEMS.safeParse(itemId);
                        if (item == null || item == Items.AIR) {
                            continue;
                        }

                        int slot = slotSelector.selectItem(config, item);
                        if (slot < 0) {
                            continue;
                        }

                        BlockUtils.PlaceBlockPlan plan = BlockUtils.getPlacingPlan(pos, config.attachToAir, method);
                        if (plan != null) {
                            if (config.debugMode && !debugStep) {
                                debugPlan = plan;
                                break actionLoop;
                                // TODO: test after actions per tick change?
                            } else {
                                debugPlan = null;
                                debugStep = false;
                                mc.player.getInventory().selected = slot;
                                BlockUtils.applyPlacingPlan(plan, config.useShift);
                                actionPerformed = true;
                                continue actionLoop;
                            }
                        }
                    }
                }
            }

            if (!actionPerformed) {
                actionTickCounter = 0;
            }

            break;
        }
    }

    private boolean selectItemForBlockBreak(BlockAutomationConfig config) {
        assert mc.level != null;
        assert mc.player != null;

        if (breakItemId == null) {
            return true;
        }

        Item item = Registries.ITEMS.getValue(ResourceLocation.parse(breakItemId));
        if (item == Items.AIR) {
            return false;
        }

        Holder<Enchantment> enchantment = null;
        if (breakEnchantmentId != null) {
            HolderLookup<Enchantment> lookup = mc.level.holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
            ResourceLocation location = ResourceLocation.parse(breakEnchantmentId);
            enchantment = lookup.listElements().filter(ref -> ref.key().location().equals(location)).findFirst().get();
        }

        final Holder<Enchantment> enchantment2 = enchantment;
        int slot = slotSelector.selectItem(config, item, stack -> {
            return enchantment2 == null || stack.getEnchantments().getLevel(enchantment2) > 0;
        });

        if (slot >= 0) {
            mc.player.getInventory().selected = slot;
            return true;
        } else {
            return false;
        }
    }

    private void onRenderWorldLast(RenderWorldLastEvent event) {
        BlockAutomationConfig config = ConfigStore.instance.getConfig().blockAutomationConfig;
        if (config.enabled && config.debugMode && debugPlan != null) {
            // draw neighbour block
            LineRenderer renderer = RenderUtilities.instance.getLineRenderer();
            renderer.begin(event, false);

            double x1 = debugPlan.neighbour().getX();
            double y1 = debugPlan.neighbour().getY();
            double z1 = debugPlan.neighbour().getZ();
            double x2 = x1 + 1;
            double y2 = y1 + 1;
            double z2 = z1 + 1;
            renderer.cuboid(x1, y1, z1, x2, y2, z2, 1f, 1f, 1f, 1f);
            renderer.end();

            // draw target block
            renderer.begin(event, false);
            RenderSystem.setShaderColor(0.7f, 1f, 0.7f, 1f);

            x1 = debugPlan.destination().getX() + 0.05;
            y1 = debugPlan.destination().getY() + 0.05;
            z1 = debugPlan.destination().getZ() + 0.05;
            x2 = x1 + 0.9;
            y2 = y1 + 0.9;
            z2 = z1 + 0.9;
            renderer.cuboid(x1, y1, z1, x2, y2, z2, 1f, 1f, 1f, 1f);
            renderer.end();

            // draw target point
            renderer.begin(event, false);
            RenderSystem.setShaderColor(1f, 1f, 0.7f, 1f);

            for (Direction direction : Direction.values()) {
                Vec3 p1 = debugPlan.target().relative(direction, 0.1);
                Vec3 p2 = debugPlan.target().relative(direction, -0.1);
                renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, 1f, 1f, 1f, 1f);
            }
            renderer.end();

            // reset color
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}