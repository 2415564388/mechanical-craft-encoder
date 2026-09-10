package mce.encoder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import mce.encoder.block.EncoderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the attached output filter as a small item inside the machine's side windows, so it is
 * visible at all times (Create only draws it inside the hovered value box otherwise).
 */
public class EncoderRenderer implements BlockEntityRenderer<EncoderBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    public EncoderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EncoderBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (be.filtering() == null || be.getLevel() == null) return;
        ItemStack filter = be.filtering().getFilter();
        if (filter.isEmpty()) return;

        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        Vec3 center = pos.getCenter();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        for (Direction side : Direction.values()) {
            if (!side.getAxis().isHorizontal()) continue;
            Vec3 outward = Vec3.atLowerCornerOf(side.getNormal());
            if (outward.dot(cam.subtract(center)) <= 0) continue; // face faces away from the camera

            pose.pushPose();
            ValueBoxTransform transform = new FilterWindow().fromSide(side);
            transform.transform(be.getLevel(), pos, state, pose);
            ValueBoxRenderer.renderItemIntoValueBox(filter, pose, buffers, FULL_BRIGHT, overlay);
            pose.popPose();
        }
    }

    /** Same geometry as the filter slot marked on the model (a small window near the top of a face). */
    private static class FilterWindow extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(0.5, 0.72, 1.003);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            return side.getAxis().isHorizontal();
        }
    }
}
