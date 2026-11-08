package dev.phonemod;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PhoneConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue RING_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PHONE_TO_CALL;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PHONE_TO_RECEIVE;
    public static final ForgeConfigSpec.BooleanValue END_CALL_WHEN_PHONE_LOST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("calls");
        RING_TIMEOUT_SECONDS = b
                .comment("Seconds a call rings before it is dropped as unanswered.")
                .defineInRange("ringTimeoutSeconds", 30, 5, 300);
        REQUIRE_PHONE_TO_CALL = b
                .comment("The caller must carry a phone item (inventory or offhand).")
                .define("requirePhoneToCall", true);
        REQUIRE_PHONE_TO_RECEIVE = b
                .comment("The person being called must carry a phone item too.")
                .define("requirePhoneToReceive", true);
        END_CALL_WHEN_PHONE_LOST = b
                .comment("Hang up automatically if a participant no longer carries a phone.")
                .define("endCallWhenPhoneLost", true);
        b.pop();

        SPEC = b.build();
    }

    private PhoneConfig() {
    }
}
