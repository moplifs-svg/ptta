package dev.phonemod.net;

import dev.phonemod.call.CallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: the player pressed a button on the phone screen. */
public final class C2SPhoneAction {

    public enum Action {
        CALL,
        ANSWER,
        DECLINE,
        /** Also used to cancel an outgoing call that has not been answered. */
        HANGUP
    }

    private final Action action;
    private final UUID target; // only used for CALL

    public C2SPhoneAction(Action action, UUID target) {
        this.action = action;
        this.target = target;
    }

    public static C2SPhoneAction call(UUID target) {
        return new C2SPhoneAction(Action.CALL, target);
    }

    public static C2SPhoneAction of(Action action) {
        return new C2SPhoneAction(action, null);
    }

    public static void encode(C2SPhoneAction msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.target != null);
        if (msg.target != null) {
            buf.writeUUID(msg.target);
        }
    }

    public static C2SPhoneAction decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new C2SPhoneAction(action, target);
    }

    public static void handle(C2SPhoneAction msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            CallManager calls = CallManager.INSTANCE;
            switch (msg.action) {
                case CALL -> {
                    if (msg.target != null) calls.request(sender, msg.target);
                }
                case ANSWER -> calls.answer(sender);
                case DECLINE -> calls.decline(sender);
                case HANGUP -> calls.hangup(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}
