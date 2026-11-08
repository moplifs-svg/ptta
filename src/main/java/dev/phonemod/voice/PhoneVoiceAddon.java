package dev.phonemod.voice;

import dev.phonemod.call.CallManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Plasmo Voice side of the phone.
 *
 * <p>Design: players keep talking on their normal proximity activation, so people next to
 * the speaker still hear them in person. While a call is ACTIVE, every audio frame the speaker
 * sends is also forwarded, untouched, into a direct source that only the other participant
 * hears, on a dedicated "Phone" source line (so each player gets their own phone volume slider).
 */
@Addon(
        id = "pv-addon-phone",
        name = "Phone",
        version = "1.0.0",
        authors = {"PhoneMod"}
)
public final class PhoneVoiceAddon implements AddonInitializer {

    private static final Logger LOGGER = LogManager.getLogger("phonemod/voice");

    private static volatile PhoneVoiceAddon instance;

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private volatile ServerSourceLine line;

    /** Key = the player who LISTENS to the source. */
    private final Map<UUID, ServerDirectSource> sourceByListener = new ConcurrentHashMap<>();

    /** The running addon, or null before Plasmo Voice has initialised it. */
    public static PhoneVoiceAddon get() {
        return instance;
    }

    @Override
    public void onAddonInitialize() {
        instance = this;

        line = voiceServer.getSourceLineManager().createBuilder(
                this,
                "phone",                 // name: lowercase letters, digits, - and _
                "phone.voice.line",      // translation key, provided by this mod's lang file
                loadIcon(),
                20                       // weight: lower = higher in the volume list
        ).build();

        LOGGER.info("Phone voice line registered");
    }

    @Override
    public void onAddonShutdown() {
        sourceByListener.values().forEach(ServerDirectSource::remove);
        sourceByListener.clear();
        instance = null;
    }

    public boolean isReady() {
        return voiceServer != null && line != null;
    }

    public boolean hasVoiceChat(UUID playerId) {
        return voiceServer.getPlayerManager()
                .getPlayerById(playerId)
                .map(p -> p.hasVoiceChat())
                .orElse(false);
    }

    /** Opens the two direct sources for a call. Returns false if either player isn't reachable. */
    public boolean startCall(UUID a, UUID b) {
        ServerSourceLine phoneLine = line;
        if (phoneLine == null) return false;

        Optional<VoiceServerPlayer> playerA = voiceServer.getPlayerManager().getPlayerById(a);
        Optional<VoiceServerPlayer> playerB = voiceServer.getPlayerManager().getPlayerById(b);
        if (playerA.isEmpty() || playerB.isEmpty()) return false;

        stopCall(a, b); // drop anything stale

        // Direct sources are heard only by the player they are attached to.
        sourceByListener.put(a, phoneLine.createDirectSource(playerA.get(), false));
        sourceByListener.put(b, phoneLine.createDirectSource(playerB.get(), false));
        return true;
    }

    public void stopCall(UUID a, UUID b) {
        removeSource(a);
        removeSource(b);
    }

    private void removeSource(UUID listener) {
        ServerDirectSource source = sourceByListener.remove(listener);
        if (source != null) {
            source.remove();
        }
    }

    // ------------------------------------------------------------------ voice relay
    //
    // PlayerSpeakEvent is fired for every audio packet a player sends. It is cancelled once an
    // activation handles it, and @EventSubscribe skips cancelled events by default, so we run at
    // LOWEST priority (before Plasmo Voice's own handler). We never cancel: proximity voice must
    // keep working for everybody standing near the speaker.
    //
    // These handlers run on Plasmo Voice's network threads, so they only touch thread-safe state.

    @EventSubscribe(priority = EventPriority.LOWEST)
    public void onSpeak(PlayerSpeakEvent event) {
        UUID speaker = event.getPlayer().getInstance().getUuid();

        Optional<UUID> peer = CallManager.INSTANCE.activePeer(speaker);
        if (peer.isEmpty()) return;

        ServerDirectSource source = sourceByListener.get(peer.get());
        if (source == null) return;

        // Frames are already Opus-encoded and encrypted by the speaker's client, so they can be
        // forwarded as they are.
        source.sendAudioFrame(event.getPacket().getData(), event.getPacket().getSequenceNumber());
    }

    @EventSubscribe(priority = EventPriority.LOWEST)
    public void onSpeakEnd(PlayerSpeakEndEvent event) {
        UUID speaker = event.getPlayer().getInstance().getUuid();

        Optional<UUID> peer = CallManager.INSTANCE.activePeer(speaker);
        if (peer.isEmpty()) return;

        ServerDirectSource source = sourceByListener.get(peer.get());
        if (source == null) return;

        source.sendAudioEnd(event.getPacket().getSequenceNumber());
    }

    // ------------------------------------------------------------------ icon

    /** Plasmo Voice accepts "base64;<png bytes>" as an icon. Falls back to a stock icon path. */
    private static String loadIcon() {
        try (InputStream in = PhoneVoiceAddon.class
                .getResourceAsStream("/assets/phonemod/textures/icons/phone_line.png")) {
            if (in != null) {
                return "base64;" + Base64.getEncoder().encodeToString(in.readAllBytes());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load phone line icon", e);
        }
        return "plasmovoice:textures/icons/speaker_priority.png";
    }
}
