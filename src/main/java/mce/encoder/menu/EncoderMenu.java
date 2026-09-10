package mce.encoder.menu;

import mce.encoder.block.EncoderBlockEntity;
import mce.encoder.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class EncoderMenu extends AbstractContainerMenu {
    private static final int INPUT = 0;
    private static final int OUTPUT = EncoderBlockEntity.INPUT_SLOTS;
    public static final int SLOT_COUNT = OUTPUT + EncoderBlockEntity.OUTPUT_SLOTS;

    private final ItemStackHandler input;
    private final ItemStackHandler output;
    private final EncoderBlockEntity blockEntity;

    /** Client-side reconstruction: same slot layout, placeholders; real items are synced by the server. */
    public EncoderMenu(int id, Inventory inv) {
        this(id, inv, null, new ItemStackHandler(EncoderBlockEntity.INPUT_SLOTS),
                new ItemStackHandler(EncoderBlockEntity.OUTPUT_SLOTS));
    }

    /** Server-side menu bound to the actual block entity. */
    public EncoderMenu(int id, Inventory inv, EncoderBlockEntity be) {
        this(id, inv, be, be.input, be.output);
    }

    private EncoderMenu(int id, Inventory inv, EncoderBlockEntity be, ItemStackHandler input,
                        ItemStackHandler output) {
        super(ModContent.ENCODER_MENU.get(), id);
        this.blockEntity = be;
        this.input = input;
        this.output = output;

        // Input: 4 rows x 9
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new HandlerSlot(input, r * 9 + c, 8 + c * 18, 18 + r * 18));
            }
        }
        // Output slot (accessory row). The filter lives on the physical side slot (like a brass funnel).
        addSlot(new HandlerSlot(output, 0, 152, 96));

        // Player inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 150 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 208));
        }
    }

    public EncoderBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < SLOT_COUNT) {
                if (!this.moveItemStackTo(stack, SLOT_COUNT, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, INPUT, OUTPUT, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == moved.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        // Client-side reconstruction has no block entity; only the server validates the machine.
        if (blockEntity == null) return true;
        if (blockEntity.isRemoved()) return false;
        if (blockEntity.getLevel() == null) return false;
        // The menu must die if the machine was broken out from under the player.
        if (blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) != blockEntity) return false;
        return player.distanceToSqr(blockEntity.getBlockPos().getCenter()) <= 64.0;
    }

    private static class HandlerSlot extends Slot {
        private final ItemStackHandler handler;
        private final int slotIndex;

        HandlerSlot(ItemStackHandler handler, int slotIndex, int x, int y) {
            super(new EmptyContainer(), 0, x, y);
            this.handler = handler;
            this.slotIndex = slotIndex;
        }

        @Override public ItemStack getItem() { return handler.getStackInSlot(slotIndex); }
        @Override public void set(ItemStack stack) { handler.setStackInSlot(slotIndex, stack); }
        @Override public ItemStack remove(int amount) { return handler.extractItem(slotIndex, amount, false); }
        @Override public boolean mayPlace(ItemStack stack) { return handler.isItemValid(slotIndex, stack); }
    }

    private static class EmptyContainer implements Container {
        @Override public int getContainerSize() { return 0; }
        @Override public boolean isEmpty() { return true; }
        @Override public ItemStack getItem(int i) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItem(int i, int j) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int i) { return ItemStack.EMPTY; }
        @Override public void setItem(int i, ItemStack stack) { }
        @Override public int getMaxStackSize() { return 64; }
        @Override public void setChanged() { }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void startOpen(Player player) { }
        @Override public void stopOpen(Player player) { }
        @Override public boolean canPlaceItem(int i, ItemStack stack) { return true; }
        @Override public void clearContent() { }
    }
}
