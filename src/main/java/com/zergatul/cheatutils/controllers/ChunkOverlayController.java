package com.zergatul.cheatutils.controllers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zergatul.cheatutils.chunkoverlays.AbstractChunkOverlay;
import com.zergatul.cheatutils.chunkoverlays.ExplorationMiniMapChunkOverlay;
import com.zergatul.cheatutils.chunkoverlays.NewChunksOverlay;
import com.zergatul.cheatutils.chunkoverlays.WorldDownloadChunkOverlay;
import com.zergatul.cheatutils.interfaces.LevelChunkMixinInterface;
import com.zergatul.cheatutils.render.Primitives;
import com.zergatul.cheatutils.utils.Dimension;
import com.zergatul.cheatutils.wrappers.ModApiWrapper;
import com.zergatul.cheatutils.wrappers.events.BlockUpdateEvent;
import com.zergatul.cheatutils.wrappers.events.MouseScrollEvent;
import com.zergatul.cheatutils.wrappers.events.RenderGuiEvent;
import com.zergatul.cheatutils.wrappers.events.PreRenderGuiOverlayEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ChunkOverlayController {

    public static final ChunkOverlayController instance = new ChunkOverlayController();

    // store texture of 16x16 chunks
    private static final int SegmentSize = 16;
    // 250ms
    private static final long UpdateDelay = 250L * 1000000;
    private static final int TranslateZ = 250;
    private static final float MinScale = 1 * SegmentSize;
    private static final float MaxScale = 32 * SegmentSize;
    private static final float ScaleStep = 1.3f;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<AbstractChunkOverlay> overlays = new ArrayList<>();
    private float scale = 16 * SegmentSize;

    private ChunkOverlayController() {
        register(new ExplorationMiniMapChunkOverlay(SegmentSize, UpdateDelay));
        register(new NewChunksOverlay(SegmentSize, UpdateDelay));
        register(new WorldDownloadChunkOverlay(SegmentSize, UpdateDelay));

        ModApiWrapper.ScannerChunkLoaded.add(this::onChunkLoaded);
        ModApiWrapper.ScannerBlockUpdated.add(this::onBlockChanged);
        ModApiWrapper.ClientTickEnd.add(this::onClientTickEnd);
        ModApiWrapper.PostRenderGui.add(this::render);
        ModApiWrapper.PreRenderGuiOverlay.add(this::onPreRenderGameOverlay);
        ModApiWrapper.MouseScroll.add(this::onMouseScroll);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractChunkOverlay> T ofType(Class<T> clazz) {
        return (T) overlays.stream().filter(o -> o.getClass() == clazz).findFirst().orElse(null);
    }

    private void render(RenderGuiEvent event) {
        if (!isSomeOverlayEnabled()) {
            return;
        }

        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onPreRender();
        }

        if (!mc.options.keyPlayerList.isDown()) {
            return;
        }

        if (Screen.hasAltDown()) {
            return;
        }

        if (mc.player == null || mc.level == null) {
            return;
        }

        float frameTime = event.getTickDelta();
        float xp = (float) Mth.lerp(frameTime, mc.player.xo, mc.player.getX());
        float zp = (float) Mth.lerp(frameTime, mc.player.zo, mc.player.getZ());
        float xc = (float) mc.gameRenderer.getMainCamera().getPosition().x;
        float zc = (float) mc.gameRenderer.getMainCamera().getPosition().z;
        float yRot = mc.gameRenderer.getMainCamera().getYRot();

        event.getMatrixStack().pushPose();
        event.getMatrixStack().setIdentity();
        event.getMatrixStack().translate(1d * mc.getWindow().getGuiScaledWidth() / 2, 1d * mc.getWindow().getGuiScaledHeight() / 2, TranslateZ);

        Quaternionf quaternion = new Quaternionf(0, 0, 0, 1);
        quaternion.rotationYXZ(-(float)Math.PI, -(float)Math.PI, -yRot * ((float)Math.PI / 180F));
        event.getMatrixStack().mulPose(quaternion);
        RenderSystem.applyModelViewMatrix();

        //RenderSystem.enableDepthTest();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 0.5f);

        float multiplier = 1f / (16 * SegmentSize) * scale;
        Dimension dimension = Dimension.get(mc.level);

        //RenderSystem.enableTexture();

        for (AbstractChunkOverlay overlay: overlays) {
            int z = overlay.getTranslateZ();
            for (AbstractChunkOverlay.Segment segment: overlay.getSegments(dimension)) {
                if (segment.texture == null) {
                    continue;
                }

                /**/
                //RenderSystem.bindTextureForSetup(segment.texture.getId());
                //RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                //RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                /**/

                RenderSystem.setShaderTexture(0, segment.texture.getId());

                float x = (segment.pos.x * 16 * SegmentSize - xc) * multiplier;
                float y = (segment.pos.z * 16 * SegmentSize - zc) * multiplier;

                Primitives.drawTexture(
                        event.getMatrixStack().last().pose(),
                        x, y, scale, scale, z,
                        0, 0, 16 * SegmentSize, 16 * SegmentSize,
                        16 * SegmentSize, 16 * SegmentSize);
            }
        }

        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onPostDrawSegments(dimension, event.getMatrixStack(), xp, zp, xc, zc, multiplier);
        }

        event.getMatrixStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private void onPreRenderGameOverlay(PreRenderGuiOverlayEvent event) {
        if (event.getGuiOverlayType() == PreRenderGuiOverlayEvent.GuiOverlayType.PLAYER_LIST) {
            if (!isSomeOverlayEnabled()) {
                return;
            }
            if (Screen.hasAltDown()) {
                return;
            }
            event.cancel();
        }
    }

    private void onMouseScroll(MouseScrollEvent event) {
        if (!isSomeOverlayEnabled()) {
            return;
        }

        if (!mc.options.keyPlayerList.isDown()) {
            return;
        }

        event.cancel();

        if (event.getScrollDelta() >= 1.0d) {
            if (scale < MaxScale) {
                scale *= ScaleStep;
            }
        }

        if (event.getScrollDelta() <= -1.0d) {
            if (scale > MinScale) {
                scale /= ScaleStep;
            }
        }
    }

    private void register(AbstractChunkOverlay overlay) {
        overlays.add(overlay);
    }

    private void onChunkLoaded(LevelChunk chunk) {
        Dimension dimension = ((LevelChunkMixinInterface) chunk).getDimension();
        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onChunkLoaded(dimension, chunk);
        }
    }

    private void onBlockChanged(BlockUpdateEvent event) {
        Dimension dimension = ((LevelChunkMixinInterface) event.chunk()).getDimension();
        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onBlockChanged(dimension, event.pos(), event.state());
        }
    }

    private void onClientTickEnd() {
        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onClientTickEnd();
        }
    }

    private boolean isSomeOverlayEnabled() {
        return overlays.stream().anyMatch(AbstractChunkOverlay::isEnabled);
    }
}