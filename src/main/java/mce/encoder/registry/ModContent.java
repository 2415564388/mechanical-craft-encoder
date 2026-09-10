package mce.encoder.registry;

import mce.encoder.MCEEncoder;
import mce.encoder.block.EncoderBlock;
import mce.encoder.block.EncoderBlockEntity;
import mce.encoder.item.EncoderBlockItem;
import mce.encoder.menu.EncoderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModContent {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MCEEncoder.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MCEEncoder.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MCEEncoder.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MCEEncoder.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MCEEncoder.MODID);

    public static final DeferredHolder<Block, EncoderBlock> ENCODER =
            BLOCKS.register("mechanical_craft_encoder", EncoderBlock::new);
    public static final DeferredHolder<Item, EncoderBlockItem> ENCODER_ITEM =
            ITEMS.register("mechanical_craft_encoder",
                    () -> new EncoderBlockItem(ENCODER.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EncoderBlockEntity>> ENCODER_BE =
            BE.register("mechanical_craft_encoder",
                    () -> BlockEntityType.Builder.of(EncoderBlockEntity::new, ENCODER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<EncoderMenu>> ENCODER_MENU =
            MENUS.register("mechanical_craft_encoder",
                    () -> new MenuType<>(EncoderMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENCODER_TAB =
            TABS.register("encoder", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mce_encoder"))
                    .icon(() -> new ItemStack(ModContent.ENCODER_ITEM.get()))
                    .displayItems((params, out) -> out.accept(new ItemStack(ModContent.ENCODER_ITEM.get())))
                    .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BE.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
