package dev.phonemod.call;

import com.mojang.brigadier.CommandDispatcher;
import dev.phonemod.PhoneConfig;
import dev.phonemod.item.PhoneItem;
import dev.phonemod.net.CallPhase;
import dev.phonemod.net.PhoneNetwork;
import dev.phonemod.net.S2CCallState;
import dev.phonemod.voice.PhoneVoiceAddon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every call on the server. State changes happen on the server thread;
 * {@link #activePeer(UUID)} is also read from Plasmo Voice's network threads.
 */
public final class CallManager {

    public static final CallManager INSTANCE = new CallManager();

    private enum EndReason {
        HANGUP,
        DECLINED,
        CANCELLED,
        TIMEOUT,
        DISCONNECTED,
        PHONE_LOST,
        SERVER_STOPPING
    }

    /** Maps BOTH participants to the same Call object. */
    private final Map<UUID, Call> calls = new ConcurrentHashMap<>();
    private long tick;

    private CallManager() {
    }

    // ------------------------------------------------------------------ voice-thread API

    /** The player on the other end of an ACTIVE call, if any. Safe to call from any thread. */
    public Optional<UUID> activePeer(UUID player) {
        Call call = calls.get(player);
        if (call == null || call.state != Call.State.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(call.other(player));
    }

    // ------------------------------------------------------------------ actions

    public void request(ServerPlayer caller, UUID targetId) {
        MinecraftServer server = caller.getServer();
        if (server == null) return;

        if (caller.getUUID().equals(targetId)) {
            caller.sendSystemMessage(error("phone.msg.call_self"));
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            caller.sendSystemMessage(error("phone.msg.not_online"));
            return;
        }

        if (calls.containsKey(caller.getUUID())) {
            caller.sendSystemMessage(error("phone.msg.you_busy"));
            return;
        }
        if (calls.containsKey(targetId)) {
            caller.sendSystemMessage(error("phone.msg.target_busy", target.getName()));
            return;
        }

        if (PhoneConfig.REQUIRE_PHONE_TO_CALL.get() && !PhoneItem.hasPhone(caller)) {
            caller.sendSystemMessage(error("phone.msg.you_need_phone"));
            return;
        }
        if (PhoneConfig.REQUIRE_PHONE_TO_RECEIVE.get() && !PhoneItem.hasPhone(target)) {
            caller.sendSystemMessage(error("phone.msg.target_no_phone", target.getName()));
            return;
        }

        PhoneVoiceAddon voice = PhoneVoiceAddon.get();
        if (voice == null || !voice.isReady()) {
            caller.sendSystemMessage(error("phone.msg.voice_unavailable"));
            return;
        }
        if (!voice.hasVoiceChat(caller.getUUID())) {
            caller.sendSystemMessage(error("phone.msg.you_no_voice"));
            return;
        }
        if (!voice.hasVoiceChat(targetId)) {
            caller.sendSystemMessage(error("phone.msg.target_no_voice", target.getName()));
            return;
        }

        Call call = new Call(caller.getUUID(), targetId, tick);
        calls.put(caller.getUUID(), call);
        calls.put(targetId, call);

        sendState(caller, CallPhase.OUTGOING, target);
        sendState(target, CallPhase.INCOMING, caller);

        caller.sendSystemMessage(Component.translatable("phone.msg.calling", target.getName())
                .withStyle(ChatFormatting.GRAY));

        MutableComponent ring = Component.translatable("phone.msg.incoming", caller.getName())
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" "))
                .append(button("phone.msg.answer", ChatFormatting.GREEN, "/phone answer"))
                .append(Component.literal(" "))
                .append(button("phone.msg.decline", ChatFormatting.RED, "/phone decline"));
        target.sendSystemMessage(ring);
        ringTone(target);
    }

    public void answer(ServerPlayer player) {
        Call call = calls.get(player.getUUID());
        if (call == null || call.state != Call.State.RINGING || !call.callee.equals(player.getUUID())) {
            player.sendSystemMessage(error("phone.msg.nothing_to_answer"));
            return;
        }

        MinecraftServer server = player.getServer();
        ServerPlayer caller = server == null ? null : server.getPlayerList().getPlayer(call.caller);
        if (caller == null) {
            endCall(call, EndReason.DISCONNECTED, null);
            return;
        }

        PhoneVoiceAddon voice = PhoneVoiceAddon.get();
        if (voice == null || !voice.startCall(call.caller, call.callee)) {
            player.sendSystemMessage(error("phone.msg.voice_unavailable"));
            endCall(call, EndReason.DISCONNECTED, null);
            return;
        }

        call.state = Call.State.ACTIVE;

        sendState(caller, CallPhase.ACTIVE, player);
        sendState(player, CallPhase.ACTIVE, caller);

        caller.sendSystemMessage(Component.translatable("phone.msg.connected", player.getName())
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.translatable("phone.msg.connected", caller.getName())
                .withStyle(ChatFormatting.GREEN));
    }

    public void decline(ServerPlayer player) {
        Call call = calls.get(player.getUUID());
        if (call == null || call.state != Call.State.RINGING || !call.callee.equals(player.getUUID())) {
            player.sendSystemMessage(error("phone.msg.nothing_to_answer"));
            return;
        }
        endCall(call, EndReason.DECLINED, player.getUUID());
    }

    /** Ends an active call, or cancels an outgoing one that has not been answered. */
    public void hangup(ServerPlayer player) {
        Call call = calls.get(player.getUUID());
        if (call == null) {
            player.sendSystemMessage(error("phone.msg.not_in_call"));
            return;
        }

        if (call.state == Call.State.RINGING) {
            if (call.callee.equals(player.getUUID())) {
                endCall(call, EndReason.DECLINED, player.getUUID());
            } else {
                endCall(call, EndReason.CANCELLED, player.getUUID());
            }
        } else {
            endCall(call, EndReason.HANGUP, player.getUUID());
        }
    }

    // ------------------------------------------------------------------ internals

    private void endCall(Call call, EndReason reason, UUID actor) {
        // Remove first so a re-entrant event cannot end the same call twice.
        boolean removedCaller = calls.remove(call.caller, call);
        boolean removedCallee = calls.remove(call.callee, call);
        if (!removedCaller && !removedCallee) return;

        PhoneVoiceAddon voice = PhoneVoiceAddon.get();
        if (voice != null) {
            voice.stopCall(call.caller, call.callee);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerPlayer caller = server.getPlayerList().getPlayer(call.caller);
        ServerPlayer callee = server.getPlayerList().getPlayer(call.callee);

        if (caller != null) {
            sendState(caller, CallPhase.IDLE, null);
            caller.sendSystemMessage(endMessage(reason, true, actor, call, callee));
        }
        if (callee != null) {
            sendState(callee, CallPhase.IDLE, null);
            callee.sendSystemMessage(endMessage(reason, false, actor, call, caller));
        }
    }

    private Component endMessage(EndReason reason, boolean toCaller, UUID actor, Call call, ServerPlayer otherPlayer) {
        Component otherName = otherPlayer != null ? otherPlayer.getName() : Component.literal("?");
        boolean iActed = actor != null && actor.equals(toCaller ? call.caller : call.callee);

        String key = switch (reason) {
            case HANGUP -> iActed ? "phone.msg.ended" : "phone.msg.hung_up";
            case DECLINED -> iActed ? "phone.msg.you_declined" : "phone.msg.declined";
            case CANCELLED -> iActed ? "phone.msg.cancelled" : "phone.msg.missed";
            case TIMEOUT -> toCaller ? "phone.msg.no_answer" : "phone.msg.missed";
            case DISCONNECTED -> "phone.msg.ended";
            case PHONE_LOST -> "phone.msg.phone_lost";
            case SERVER_STOPPING -> "phone.msg.ended";
        };

        return Component.translatable(key, otherName).withStyle(ChatFormatting.GRAY);
    }

    private void sendState(ServerPlayer to, CallPhase phase, ServerPlayer other) {
        UUID otherId = other != null ? other.getUUID() : null;
        String otherName = other != null ? other.getGameProfile().getName() : "";
        PhoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> to),
                new S2CCallState(phase, otherId, otherName));
    }

    private static void ringTone(ServerPlayer target) {
        // playNotifySound is heard only by this player, which is what a ringing phone should be.
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static Component error(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.translatable(key).withStyle(Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private void onServerTick(MinecraftServer server) {
        tick++;
        if (calls.isEmpty() || tick % 20 != 0) return;

        Set<Call> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int timeoutTicks = PhoneConfig.RING_TIMEOUT_SECONDS.get() * 20;

        for (Call call : calls.values()) {
            if (!seen.add(call)) continue;

            ServerPlayer caller = server.getPlayerList().getPlayer(call.caller);
            ServerPlayer callee = server.getPlayerList().getPlayer(call.callee);

            if (caller == null || callee == null) {
                endCall(call, EndReason.DISCONNECTED, null);
                continue;
            }

            if (PhoneConfig.END_CALL_WHEN_PHONE_LOST.get()) {
                boolean callerLost = PhoneConfig.REQUIRE_PHONE_TO_CALL.get() && !PhoneItem.hasPhone(caller);
                boolean calleeLost = PhoneConfig.REQUIRE_PHONE_TO_RECEIVE.get() && !PhoneItem.hasPhone(callee);
                if (callerLost || calleeLost) {
                    endCall(call, EndReason.PHONE_LOST, null);
                    continue;
                }
            }

            if (call.state == Call.State.RINGING) {
                if (tick - call.startedTick >= timeoutTicks) {
                    endCall(call, EndReason.TIMEOUT, null);
                } else if ((tick - call.startedTick) % 40 == 0) {
                    ringTone(callee);
                }
            }
        }
    }

    private void onLogout(ServerPlayer player) {
        Call call = calls.get(player.getUUID());
        if (call != null) {
            endCall(call, EndReason.DISCONNECTED, player.getUUID());
        }
    }

    private void onServerStopping() {
        Set<Call> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Call call : calls.values()) {
            if (seen.add(call)) endCall(call, EndReason.SERVER_STOPPING, null);
        }
        calls.clear();
    }

    // ------------------------------------------------------------------ Forge events + commands

    public static final class ForgeEvents {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) INSTANCE.onServerTick(server);
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                INSTANCE.onLogout(player);
            }
        }

        @SubscribeEvent
        public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
            INSTANCE.onServerStopping();
        }

        @SubscribeEvent
        public void onRegisterCommands(RegisterCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

            d.register(Commands.literal("phone")
                    .requires(src -> src.getEntity() instanceof ServerPlayer)
                    .then(Commands.literal("call")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer caller = ctx.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        INSTANCE.request(caller, target.getUUID());
                                        return 1;
                                    })))
                    .then(Commands.literal("answer").executes(ctx -> {
                        INSTANCE.answer(ctx.getSource().getPlayerOrException());
                        return 1;
                    }))
                    .then(Commands.literal("decline").executes(ctx -> {
                        INSTANCE.decline(ctx.getSource().getPlayerOrException());
                        return 1;
                    }))
                    .then(Commands.literal("hangup").executes(ctx -> {
                        INSTANCE.hangup(ctx.getSource().getPlayerOrException());
                        return 1;
                    })));
        }
    }
}
