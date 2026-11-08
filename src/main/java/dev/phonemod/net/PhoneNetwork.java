package dev.phonemod.net;

import dev.phonemod.PhoneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PhoneNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PhoneMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        int id = 0;
        CHANNEL.registerMessage(id++, C2SPhoneAction.class,
                C2SPhoneAction::encode, C2SPhoneAction::decode, C2SPhoneAction::handle);
        CHANNEL.registerMessage(id++, S2CCallState.class,
                S2CCallState::encode, S2CCallState::decode, S2CCallState::handle);
    }

    private PhoneNetwork() {
    }
}
