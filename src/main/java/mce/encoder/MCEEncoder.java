package mce.encoder;

import mce.encoder.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Mechanical Craft Encoder - standalone NeoForge 1.21.1 mod.
 * Encodes Create mechanical_crafting recipes into Create cardboard packages,
 * and lays small recipes onto a larger crafter wall when a packager unwraps them.
 */
@Mod(MCEEncoder.MODID)
public class MCEEncoder {
    public static final String MODID = "mce_encoder";

    public MCEEncoder(IEventBus modBus) {
        ModContent.register(modBus);

        modBus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModContent.ENCODER_BE.get(),
                (be, side) -> be.getHandler(side));
    }
}
