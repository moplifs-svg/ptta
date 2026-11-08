package dev.phonemod;

import dev.phonemod.call.CallManager;
import dev.phonemod.item.PhoneItems;
import dev.phonemod.net.PhoneNetwork;
import dev.phonemod.voice.PhoneVoiceAddon;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Mod(PhoneMod.MOD_ID)
public final class PhoneMod {

    public static final String MOD_ID = "phonemod";

    // Plasmo Voice addon class (annotated with @Addon).
    private final PhoneVoiceAddon addon = new PhoneVoiceAddon();

    public PhoneMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        PhoneItems.ITEMS.register(modBus);
        PhoneItems.TABS.register(modBus);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PhoneConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(new CallManager.ForgeEvents());

        // Per the Plasmo Voice docs, a Forge mod loads its addon in the mod constructor.
        PlasmoVoiceServer.getAddonsLoader().load(addon);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PhoneNetwork::register);
    }
}
