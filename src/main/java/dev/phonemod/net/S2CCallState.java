package dev.phonemod.net;

import dev.phonemod.client.ClientCallState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: the call state changed. */
public final class S2CCallState {

    private final CallPhase phase;
    private final UUID otherId;      // may be null when IDLE
    private final String otherName;  // may be empty when IDLE

    public S2CCallState(CallPhase phase, UUID otherId, String otherName) {
        this.phase = phase;
        this.otherId = otherId;
        this.otherName = otherName == null ? "" : otherName;
    }

    public static void encode(S2CCallState msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.phase);
        buf.writeBoolean(msg.otherId != null);
        if (msg.otherId != null) {
            buf.writeUUID(msg.otherId);
        }
        buf.writeUtf(msg.otherName, 64);
    }

    public static S2CCallState decode(FriendlyByteBuf buf) {
        CallPhase phase = buf.readEnum(CallPhase.class);
        UUID otherId = buf.readBoolean() ? buf.readUUID() : null;
        String otherName = buf.readUtf(64);
        return new S2CCallState(phase, otherId, otherName);
    }

    public static void handle(S2CCallState msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCallState.set(msg.phase, msg.otherId, msg.otherName)));
        ctx.setPacketHandled(true);
    }
}
