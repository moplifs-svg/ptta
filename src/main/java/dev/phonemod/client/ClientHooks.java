package dev.phonemod.client;

import dev.phonemod.item.PhoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Entry points called from common code. Kept in the client package so servers never load Screen classes. */
public final class ClientHooks {

    public static void openPhoneScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new PhoneScreen());
    }

    /** For the keybind: only opens the screen if the player is carrying a phone. */
    public static void openPhoneScreenIfCarrying() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (PhoneItem.hasPhone(player)) {
            mc.setScreen(new PhoneScreen());
        }
    }

    private ClientHooks() {
    }
}
