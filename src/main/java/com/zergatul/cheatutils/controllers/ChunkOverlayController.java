package com.zergatul.cheatutils.controllers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.zergatul.cheatutils.chunkoverlays.AbstractChunkOverlay;
import com.zergatul.cheatutils.chunkoverlays.ExplorationMiniMapChunkOverlay;
import com.zergatul.cheatutils.chunkoverlays.NewChunksOverlay;
import com.zergatul.cheatutils.utils.Dimension;
import com.zergatul.cheatutils.wrappers.ModApiWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        ChunkController.instance.addOnChunkLoadedHandler(this::onChunkLoaded);
        ChunkController.instance.addOnBlockChangedHandler(this::onBlockChanged);
        ModApiWrapper.addOnClientTickEnd(this::onClientTickEnd);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractChunkOverlay> T ofType(Class<T> clazz) {
        return (T) overlays.stream().filter(o -> o.getClass() == clazz).findFirst().orElse(null);
    }

    @SubscribeEvent
    public void render(RenderGuiEvent.Post event) {
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

        float frameTime = event.getPartialTick();
        float xp = (float) Mth.lerp(frameTime, mc.player.xo, mc.player.getX());
        float zp = (float) Mth.lerp(frameTime, mc.player.zo, mc.player.getZ());
        float xc = (float) mc.gameRenderer.getMainCamera().getPosition().x;
        float zc = (float) mc.gameRenderer.getMainCamera().getPosition().z;
        float yRot = mc.gameRenderer.getMainCamera().getYRot();

        event.getPoseStack().pushPose();
        event.getPoseStack().setIdentity();
        event.getPoseStack().translate(1d * mc.getWindow().getGuiScaledWidth() / 2, 1d * mc.getWindow().getGuiScaledHeight() / 2, TranslateZ);
        event.getPoseStack().mulPose(Vector3f.ZN.rotationDegrees(yRot));
        event.getPoseStack().mulPose(Vector3f.XN.rotationDegrees(180));
        event.getPoseStack().mulPose(Vector3f.YN.rotationDegrees(180));
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

                drawTexture(
                        event.getPoseStack().last().pose(),
                        x, y, scale, scale, z,
                        0, 0, 16 * SegmentSize, 16 * SegmentSize,
                        16 * SegmentSize, 16 * SegmentSize);
            }
        }

        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onPostDrawSegments(dimension, event.getPoseStack(), xp, zp, xc, zc, multiplier);
        }

        event.getPoseStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    @SubscribeEvent
    public void onPreRenderGameOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == GuiOverlayManager.findOverlay(VanillaGuiOverlay.PLAYER_LIST.id())) {
            if (!isSomeOverlayEnabled()) {
                return;
            }
            if (Screen.hasAltDown()) {
                return;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isSomeOverlayEnabled()) {
            return;
        }

        if (!mc.options.keyPlayerList.isDown()) {
            return;
        }

        event.setCanceled(true);

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

    private void onChunkLoaded(Dimension dimension, LevelChunk chunk) {
        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onChunkLoaded(dimension, chunk);
        }
    }

    private void onBlockChanged(Dimension dimension, BlockPos pos, BlockState state) {
        for (AbstractChunkOverlay overlay: overlays) {
            overlay.onBlockChanged(dimension, pos, state);
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

    private void drawTexture(Matrix4f matrix, float x, float y, float width, float height, float z, int texX, int texY, int texWidth, int texHeight, int texSizeX, int texSizeY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, x, y, z).uv(1F * texX / texSizeX, 1F * texY / texSizeY).endVertex();
        bufferBuilder.vertex(matrix, x, y + height, z).uv(1F * texX / texSizeX, 1F * (texY + texHeight) / texSizeY).endVertex();
        bufferBuilder.vertex(matrix, x + width, y + height, z).uv(1F * (texX + texWidth) / texSizeX, 1F * (texY + texHeight) / texSizeY).endVertex();
        bufferBuilder.vertex(matrix, x + width, y, z).uv(1F * (texX + texWidth) / texSizeX, 1F * texY / texSizeY).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }
}