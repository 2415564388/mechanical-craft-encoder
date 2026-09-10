package mce.encoder.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Machine block item with a compact tooltip and a SHIFT-held extended description.
 */
public class EncoderBlockItem extends BlockItem {
    public EncoderBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.mce_encoder.mechanical_craft_encoder.brief"));
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            String details = Component.translatable(
                    "tooltip.mce_encoder.mechanical_craft_encoder.details").getString();
            for (String line : details.split("\\R")) {
                if (!line.isEmpty()) tooltip.add(Component.literal(line));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.mce_encoder.mechanical_craft_encoder.shift_hint"));
        }
    }
}
