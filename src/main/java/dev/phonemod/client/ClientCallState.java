package dev.phonemod.client;

import dev.phonemod.net.CallPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

/** What the server last told this client about the local player's call. Client-only. */
public final class ClientCallState {

    private static volatile CallPhase phase = CallPhase.IDLE;
    private static volatile UUID otherId;
    private static volatile String otherName = "";
    private static volatile long phaseSinceMs = System.currentTimeMillis();

    public static void set(CallPhase newPhase, UUID newOtherId, String newOtherName) {
        phase = newPhase;
        otherId = newOtherId;
        otherName = newOtherName == null ? "" : newOtherName;
        phaseSinceMs = System.currentTimeMillis();

        // If the phone screen is open it re-reads this state itself on its next tick.
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof PhoneScreen phoneScreen) {
            phoneScreen.refresh();
        }
    }

    public static void reset() {
        set(CallPhase.IDLE, null, "");
    }

    public static CallPhase phase() {
        return phase;
    }

    public static UUID otherId() {
        return otherId;
    }

    public static String otherName() {
        return otherName;
    }

    public static long secondsInPhase() {
        return Math.max(0L, (System.currentTimeMillis() - phaseSinceMs) / 1000L);
    }

    private ClientCallState() {
    }
}
