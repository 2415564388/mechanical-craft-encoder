package mce.encoder.client;

import mce.encoder.menu.EncoderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Container GUI for the Mechanical Craft Encoder.
 *
 * A dedicated flat panel is drawn and a vanilla "slot" sprite is stamped at the
 * coordinates of every menu slot, so the slot recesses always line up with the
 * actual clickable slots. The filter is a physical side slot (like a Create funnel).
 */
public class EncoderScreen extends AbstractContainerScreen<EncoderMenu> {
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 232;

    private static final int COL_PANEL = 0xFFC6C6C6;
    private static final int COL_BORDER = 0xFF3C3C3C;
    private static final int COL_TEXT = 0xFF404040;

    public EncoderScreen(EncoderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelY = this.imageHeight - 94; // unused (labels drawn manually)
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COL_TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        gui.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, COL_BORDER);
        gui.fill(x, y, x + PANEL_W, y + PANEL_H, COL_PANEL);

        for (Slot slot : this.menu.slots) {
            gui.blitSprite(SLOT_SPRITE, x + slot.x, y + slot.y, 18, 18);
        }
    }
}
