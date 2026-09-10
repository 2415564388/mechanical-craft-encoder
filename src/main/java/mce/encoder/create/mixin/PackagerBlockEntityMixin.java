package mce.encoder.create.mixin;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import mce.encoder.create.CrafterWall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * When a package with our {@code mce_w}/{@code mce_h} metadata is unwrapped by a Create packager onto
 * a crafter wall that is LARGER than the recipe pattern, Create's stock loader (which zips pattern
 * cells to the whole wall's sorted inventory list) would scatter the items and never craft.
 *
 * <p>This mixin intercepts {@link PackagerBlockEntity#unwrapBox} and, for that case only, lays the
 * recipe onto the wall's top-left corner by real world position (Create folds away the empty
 * surroundings), then arms a deferred craft trigger. All other packages keep Create's original path.</p>
 */
@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {

    @Shadow
    public ItemStack previouslyUnwrapped;

    @Shadow
    public int animationTicks;

    @Shadow
    public boolean animationInward;

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void mceSpatialUnwrap(ItemStack stack, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        Level level = getMceLevel();
        if (level == null || level.isClientSide) return;

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data == null || data.isEmpty()) return;
        CompoundTag meta = data.copyTag();
        if (!meta.contains("mce_w", Tag.TAG_INT) || !meta.contains("mce_h", Tag.TAG_INT)) return;
        int w = meta.getInt("mce_w");
        int h = meta.getInt("mce_h");
        if (w <= 0 || h <= 0) return;

        PackageOrderWithCrafts order = PackageItem.getOrderContext(stack);
        if (order == null || !PackageOrderWithCrafts.hasCraftingInformation(order)) return;
        List<BigItemStack> pattern = order.getCraftingInformation();
        if (pattern == null || pattern.size() != w * h) return;

        Direction dir = packagerFacing();
        BlockPos target = this.getMcePos().relative(dir.getOpposite());
        if (!(level.getBlockEntity(target) instanceof MechanicalCrafterBlockEntity anchor)) return;

        CrafterWall wall = CrafterWall.discover(level, target);
        if (wall == null) return;
        if (pattern.size() >= wall.rows() * wall.cols()) return;   // exact/full walls: keep stock loader
        if (w > wall.cols() || h > wall.rows()) return;            // cannot fit here: stock path decides

        ItemStackHandler contents = PackageItem.getContents(stack);
        if (contents == null) return;

        boolean ok = CrafterWall.load(level, anchor, wall, w, h, pattern, contents, simulate);
        if (!ok) {
            cir.setReturnValue(false);
            return;
        }
        if (!simulate) {
            previouslyUnwrapped = stack;
            animationInward = true;
            animationTicks = 20;
            BlockState bs = this.getMceState();
            level.sendBlockUpdated(this.getMcePos(), bs, bs, 3);
        }
        cir.setReturnValue(true);
    }

    private Level getMceLevel() {
        try {
            return ((PackagerBlockEntity) (Object) this).getLevel();
        } catch (Throwable t) {
            return null;
        }
    }

    private BlockPos getMcePos() {
        return ((PackagerBlockEntity) (Object) this).getBlockPos();
    }

    private BlockState getMceState() {
        return ((PackagerBlockEntity) (Object) this).getBlockState();
    }

    private Direction packagerFacing() {
        Direction dir = Direction.UP;
        BlockState bs = getMceState();
        for (Property<?> p : bs.getProperties()) {
            if (p instanceof DirectionProperty dp && p.getName().equals("facing")) {
                if (bs.getValue(dp) instanceof Direction d) {
                    dir = d;
                    break;
                }
            }
        }
        return dir;
    }
}
