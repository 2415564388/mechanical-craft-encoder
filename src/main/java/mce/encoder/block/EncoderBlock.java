package mce.encoder.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Wrenchable like every Create part/machine: sneak + wrench right-click instantly picks the block up
 * (IWrenchable default behaviour fires a BreakEvent, removes the block and hands the item to the player;
 * stored materials drop through {@link #onRemove}). A pickaxe mines it normally.
 */
public class EncoderBlock extends Block implements EntityBlock, IWrenchable {
    public EncoderBlock() {
        super(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EncoderBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (l, p, s, be) -> {
            if (be instanceof EncoderBlockEntity e) e.serverTick(l, p);
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        // Drop the stored materials instead of silently destroying them when the machine is broken.
        if (!state.is(newState.getBlock()) && !movedByPiston && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof EncoderBlockEntity be) {
                be.dropStoredItems(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (level.getBlockEntity(pos) instanceof EncoderBlockEntity be) {
            // Sneak + empty hand pops the attached filter back off (funnel-style).
            if (player.isShiftKeyDown()) {
                ItemStack f = be.takeFilter();
                if (!f.isEmpty()) {
                    if (!player.getInventory().add(f)) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5, f);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            if (be instanceof MenuProvider provider) {
                player.openMenu(provider);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Right click with a Create Filter (or any item) attaches it to the physical filter slot.
        if (!level.isClientSide && !stack.isEmpty()
                && level.getBlockEntity(pos) instanceof EncoderBlockEntity be) {
            if (be.trySetFilter(stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EncoderBlockEntity be) {
            be.markRunSoon();
        }
    }
}
