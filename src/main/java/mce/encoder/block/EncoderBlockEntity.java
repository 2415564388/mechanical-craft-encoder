package mce.encoder.block;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import mce.encoder.menu.EncoderMenu;
import mce.encoder.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mechanical Craft Encoder as a Create {@link SmartBlockEntity} so it gets the brass-funnel style
 * physical filter: a small value-box on the block (FilteringBehaviour) whose contents you can set /
 * read / edit like on a Create funnel. The filter chooses which recipes the machine encodes.
 */
public class EncoderBlockEntity extends SmartBlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 36;
    public static final int OUTPUT_SLOTS = 1;

    public final ItemStackHandler input = new ItemStackHandler(INPUT_SLOTS);
    public final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS);

    private boolean runPending = false;
    private int tick = 0;

    public EncoderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public EncoderBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.ENCODER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        FilteringBehaviour filtering = new FilteringBehaviour(this, new FilterBoxTransform());
        filtering.setLabel(Component.translatable("block.mce_encoder.mechanical_craft_encoder.filter"));
        behaviours.add(filtering);
    }

    /** The Create filter behaviour attached to the machine (physical, funnel-style filter slot). */
    @Nullable
    public FilteringBehaviour filtering() {
        return getBehaviour(FilteringBehaviour.TYPE);
    }

    public void markRunSoon() {
        this.runPending = true;
    }

    /** Try to attach a filter stack to the physical (funnel-style) filter slot. */
    public boolean trySetFilter(ItemStack stack) {
        FilteringBehaviour f = filtering();
        if (f == null || !f.getFilter().isEmpty() || stack.isEmpty()) return false;
        f.setFilter(stack.copyWithCount(1));
        setChanged();
        return true;
    }

    /** Take the attached filter out (empty stack if none). */
    public ItemStack takeFilter() {
        FilteringBehaviour f = filtering();
        if (f == null) return ItemStack.EMPTY;
        ItemStack s = f.getFilter();
        if (s.isEmpty()) return ItemStack.EMPTY;
        f.setFilter(ItemStack.EMPTY);
        setChanged();
        return s;
    }

    @Nullable
    public IItemHandler getHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) return output;
        return input; // top & all horizontal sides insert materials
    }

    // ------------------------------------------------------------------
    // Item drops on block break / wrench pickup
    // ------------------------------------------------------------------
    /** Spawn every stored item (materials + the attached filter) into the world. */
    public void dropStoredItems(Level level, BlockPos pos) {
        dropHandler(level, pos, input);
        dropHandler(level, pos, output);
        FilteringBehaviour f = filtering();
        if (f != null) {
            ItemStack filter = f.getFilter();
            if (!filter.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), filter);
                f.setFilter(ItemStack.EMPTY);
            }
        }
    }

    private void dropHandler(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------
    // Server ticking
    // ------------------------------------------------------------------
    /** Discard any legacy/broken package whose stored contents exceed Create's 9-slot box capacity
     *  (an old build wrote up to 81 slots, which crashes Create/CyberGoggles on read-back). */
    private void sanitizeInventory() {
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack s = output.getStackInSlot(i);
            if (!s.isEmpty() && isOversizedPackage(s)) {
                output.setStackInSlot(i, ItemStack.EMPTY);
                setChanged();
            }
        }
    }

    private boolean isOversizedPackage(ItemStack stack) {
        net.minecraft.world.item.component.ItemContainerContents contents =
                stack.get(AllDataComponents.PACKAGE_CONTENTS);
        return contents != null && contents.getSlots() > PackageItem.SLOTS;
    }

    public void serverTick(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        sanitizeInventory();
        tick++;
        boolean signal = level.hasNeighborSignal(pos);
        // Encoding only runs while a redstone signal is present. runPending merely lets a fresh
        // signal start encoding immediately instead of waiting for the next 10-tick poll boundary;
        // it must NOT allow encodes after the signal is removed (e.g. from stray neighbor updates).
        if (signal && (runPending || tick % 10 == 0)) {
            runPending = false;
            tryEncode(level);
        }
        pushOutput(level, pos);
    }

    private void pushOutput(Level level, BlockPos pos) {
        if (output.getStackInSlot(0).isEmpty()) return;
        BlockPos below = pos.below();
        if (level.getBlockEntity(below) == null) return;
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, below, Direction.UP);
        if (target == null) return;
        ItemStack left = ItemHandlerHelper.insertItemStacked(target, output.getStackInSlot(0).copy(), false);
        output.setStackInSlot(0, left);
    }

    // ------------------------------------------------------------------
    // Core: encode a Create mechanical-crafting recipe into a package
    // ------------------------------------------------------------------
    private void tryEncode(Level level) {
        if (output.getStackInSlot(0).getCount() > 0) return; // output occupied

        MechanicalCraftingRecipe recipe = chooseRecipe(level);
        if (recipe == null) return;

        int recipeWidth = recipe.getWidth();
        int height = recipe.getHeight();
        List<Ingredient> ingredients = recipe.getIngredients();

        // ---------------------------------------------------------------
        // Plan the feed first WITHOUT consuming anything.
        // One item per non-empty grid cell (row-major), mirrors the dry run
        // done by canAfford() so it is guaranteed to succeed.
        // ---------------------------------------------------------------
        ItemStack[] sim = new ItemStack[input.getSlots()];
        for (int i = 0; i < sim.length; i++) sim[i] = input.getStackInSlot(i).copy();

        List<ItemStack> samples = new ArrayList<>();  // one 1-count sample per fed cell
        List<Integer> fromSlot = new ArrayList<>();   // the input slot that feeds that cell
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < recipeWidth; col++) {
                int idx = row * recipeWidth + col;
                Ingredient ingr = idx < ingredients.size() ? ingredients.get(idx) : null;
                if (ingr == null || ingr.isEmpty()) continue;
                boolean fed = false;
                for (int s = 0; s < sim.length && !fed; s++) {
                    if (!sim[s].isEmpty() && ingr.test(sim[s])) {
                        ItemStack sample = sim[s].copy();
                        sample.setCount(1);
                        samples.add(sample);
                        fromSlot.add(s);
                        sim[s].shrink(1);
                        fed = true;
                    }
                }
                if (!fed) return; // cannot feed every cell -> do not touch the input
            }
        }

        // Order grid: one BigItemStack per pattern cell (EMPTY for blank cells).
        List<BigItemStack> cells = new ArrayList<>();
        int k = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < recipeWidth; col++) {
                int idx = row * recipeWidth + col;
                Ingredient ingr = idx < ingredients.size() ? ingredients.get(idx) : null;
                cells.add(ingr != null && !ingr.isEmpty()
                        ? new BigItemStack(samples.get(k++))
                        : new BigItemStack(ItemStack.EMPTY));
            }
        }

        // Package contents = the REAL ingredients as item stacks, aggregated by
        // item identity into at most 9 contiguous slots (Create box capacity).
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack s : samples) {
            boolean merged = false;
            for (int i = 0; i < contents.size() && !merged; i++) {
                ItemStack a = contents.get(i);
                if (ItemStack.isSameItemSameComponents(a, s) && a.getCount() < a.getMaxStackSize()) {
                    a.grow(1);
                    merged = true;
                }
            }
            if (!merged) contents.add(s.copy());
        }
        if (contents.size() > 9) return; // too many distinct materials for one package

        ItemStackHandler payload = new ItemStackHandler(9);
        for (int i = 0; i < contents.size(); i++) payload.setStackInSlot(i, contents.get(i));

        // Everything fits: commit the real consumption.
        for (int i = 0; i < fromSlot.size(); i++) input.extractItem(fromSlot.get(i), 1, false);

        PackageOrderWithCrafts order = PackageOrderWithCrafts.singleRecipe(cells);
        ItemStack pack = PackageItem.containing(payload);
        PackageItem.addOrderContext(pack, order);
        // Remember the recipe grid dimensions so our Create unpacker mixin can lay this package onto a
        // wall that is bigger than the recipe (small patterns are placed at the wall's top-left corner).
        CompoundTag meta = new CompoundTag();
        meta.putInt("mce_w", recipeWidth);
        meta.putInt("mce_h", height);
        pack.set(DataComponents.CUSTOM_DATA, CustomData.of(meta));
        output.setStackInSlot(0, pack);
        setChanged();
    }

    private List<MechanicalCraftingRecipe> allMechanicalCrafting(Level level) {
        List<MechanicalCraftingRecipe> out = new ArrayList<>();
        level.getRecipeManager().getRecipes().forEach(holder -> {
            if (holder.value() instanceof MechanicalCraftingRecipe r) out.add(r);
        });
        return out;
    }

    /** True if the input storage can feed every slot of this recipe (dry-run on copies). */
    private boolean canAfford(MechanicalCraftingRecipe recipe) {
        ItemStack[] sim = new ItemStack[input.getSlots()];
        for (int i = 0; i < sim.length; i++) {
            sim[i] = input.getStackInSlot(i).copy();
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingr : ingredients) {
            if (ingr.isEmpty()) continue;
            int need = ingr.getItems().length == 0 ? 1 : ingr.getItems()[0].getCount();
            boolean consumed = false;
            for (int j = 0; j < sim.length && !consumed; j++) {
                if (sim[j].isEmpty() || !ingr.test(sim[j])) continue;
                int take = Math.min(need, sim[j].getCount());
                if (take > 0) {
                    sim[j].shrink(take);
                    consumed = true;
                }
            }
            if (!consumed) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Recipe pick (closest width) + Create funnel-style output filtering
    // ------------------------------------------------------------------
    /** First affordable recipe whose result passes the machine's output filter. */
    @Nullable
    private MechanicalCraftingRecipe chooseRecipe(Level level) {
        for (MechanicalCraftingRecipe recipe : allMechanicalCrafting(level)) {
            if (!resultMatchesFilter(level, recipe.getResultItem(level.registryAccess()))) continue;
            if (canAfford(recipe)) return recipe;
        }
        return null;
    }

    /**
     * Result filtering comes straight from the physical (brass-funnel style) filter behaviour:
     * an empty filter lets every recipe through, a Create FilterItem (list/attribute/blacklist)
     * or a plain item is honoured by Create's own matcher.
     */
    private boolean resultMatchesFilter(Level level, ItemStack result) {
        FilteringBehaviour f = filtering();
        if (f == null || f.getFilter().isEmpty()) return true;
        return f.test(result);
    }

    // ------------------------------------------------------------------
    // Value box that marks the filter slot. Sided like Create's BasinValueBox:
    // a small window near the top of each of the four horizontal faces.
    // ------------------------------------------------------------------
    private static class FilterBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(0.5, 0.72, 1.003);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            return side.getAxis().isHorizontal();
        }
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mce_encoder.mechanical_craft_encoder");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new EncoderMenu(id, inv, this);
    }

    // ------------------------------------------------------------------
    // Persistence (Create SmartBlockEntity: write/read, behaviours saved by super)
    // ------------------------------------------------------------------
    private static void saveHandler(CompoundTag parent, String key, ItemStackHandler handler,
                                    HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < handler.getSlots(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) entry.put("Item", stack.save(registries));
            list.add(entry);
        }
        parent.put(key, list);
    }

    private static void loadHandler(CompoundTag parent, String key, ItemStackHandler handler,
                                    HolderLookup.Provider registries) {
        if (!parent.contains(key, net.minecraft.nbt.Tag.TAG_LIST)) return;
        net.minecraft.nbt.ListTag list = parent.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= handler.getSlots()) continue;
            handler.setStackInSlot(slot,
                    entry.contains("Item")
                            ? ItemStack.parseOptional(registries, entry.getCompound("Item"))
                            : ItemStack.EMPTY);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        saveHandler(tag, "Input", input, registries);
        saveHandler(tag, "Output", output, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        loadHandler(tag, "Input", input, registries);
        loadHandler(tag, "Output", output, registries);
        sanitizeInventory();
    }
}
