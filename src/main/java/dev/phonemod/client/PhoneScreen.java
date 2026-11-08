package dev.phonemod.client;

import dev.phonemod.net.C2SPhoneAction;
import dev.phonemod.net.CallPhase;
import dev.phonemod.net.PhoneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The phone. One panel whose contents follow the call phase the server reports:
 * contact list (idle), "calling..." (outgoing), answer/decline (incoming), in-call timer (active).
 */
public final class PhoneScreen extends Screen {

    private static final int PANEL_W = 160;
    private static final int PANEL_H = 214;
    private static final int ROWS = 7;
    private static final int ROW_H = 22;

    private static final int COL_BODY = 0xFF1B1D22;
    private static final int COL_BEZEL = 0xFF0C0D10;
    private static final int COL_SCREEN = 0xFF262A33;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_DIM = 0xFFA6ADBB;
    private static final int COL_GREEN = 0xFF4CD964;
    private static final int COL_YELLOW = 0xFFFFD60A;

    private int left;
    private int top;
    private int scroll;

    private CallPhase builtFor = null;
    private int builtPlayerCount = -1;

    public PhoneScreen() {
        super(Component.translatable("phone.gui.title"));
    }

    /** Called by ClientCallState when the server pushes a new state. */
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        // Never pause: in single player that would freeze the call too.
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        CallPhase phase = ClientCallState.phase();
        builtFor = phase;

        int cx = left + PANEL_W / 2;
        int bw = PANEL_W - 28;
        int bx = left + 14;

        switch (phase) {
            case IDLE -> buildContactList();

            case OUTGOING -> addRenderableWidget(Button.builder(
                            Component.translatable("phone.gui.cancel"),
                            b -> send(C2SPhoneAction.Action.HANGUP))
                    .bounds(bx, top + PANEL_H - 56, bw, 20).build());

            case INCOMING -> {
                int half = (bw - 6) / 2;
                addRenderableWidget(Button.builder(
                                Component.translatable("phone.gui.answer"),
                                b -> send(C2SPhoneAction.Action.ANSWER))
                        .bounds(bx, top + PANEL_H - 56, half, 20).build());
                addRenderableWidget(Button.builder(
                                Component.translatable("phone.gui.decline"),
                                b -> send(C2SPhoneAction.Action.DECLINE))
                        .bounds(bx + half + 6, top + PANEL_H - 56, half, 20).build());
            }

            case ACTIVE -> addRenderableWidget(Button.builder(
                            Component.translatable("phone.gui.hangup"),
                            b -> send(C2SPhoneAction.Action.HANGUP))
                    .bounds(bx, top + PANEL_H - 56, bw, 20).build());
        }

        // Close is always available; closing the screen never ends a call.
        addRenderableWidget(Button.builder(
                        Component.translatable("phone.gui.close"),
                        b -> onClose())
                .bounds(cx - 30, top + PANEL_H - 30, 60, 18).build());
    }

    private void buildContactList() {
        List<PlayerInfo> others = onlinePlayers();
        builtPlayerCount = others.size();

        int maxScroll = Math.max(0, others.size() - ROWS);
        scroll = Math.min(Math.max(scroll, 0), maxScroll);

        int rowX = left + 12;
        int rowW = PANEL_W - 24 - (others.size() > ROWS ? 14 : 0);
        int y = top + 36;

        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            if (index >= others.size()) break;

            PlayerInfo info = others.get(index);
            UUID id = info.getProfile().getId();
            String name = info.getProfile().getName();

            addRenderableWidget(Button.builder(Component.literal(name), b -> {
                        PhoneNetwork.CHANNEL.sendToServer(C2SPhoneAction.call(id));
                    })
                    .bounds(rowX, y + i * ROW_H, rowW, 20).build());
        }

        if (others.size() > ROWS) {
            int arrowX = left + PANEL_W - 24;
            addRenderableWidget(Button.builder(Component.literal("^"), b -> scrollBy(-1))
                    .bounds(arrowX, y, 12, 20).build());
            addRenderableWidget(Button.builder(Component.literal("v"), b -> scrollBy(1))
                    .bounds(arrowX, y + (ROWS - 1) * ROW_H, 12, 20).build());
        }
    }

    private List<PlayerInfo> onlinePlayers() {
        List<PlayerInfo> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) return list;

        UUID self = mc.player.getUUID();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (!info.getProfile().getId().equals(self)) {
                list.add(info);
            }
        }
        list.sort(Comparator.comparing(i -> i.getProfile().getName().toLowerCase()));
        return list;
    }

    private void scrollBy(int delta) {
        scroll += delta;
        rebuildWidgets();
    }

    private void send(C2SPhoneAction.Action action) {
        PhoneNetwork.CHANNEL.sendToServer(C2SPhoneAction.of(action));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (ClientCallState.phase() == CallPhase.IDLE) {
            scrollBy(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();

        // Rebuild if the phase changed, or if players joined/left while browsing contacts.
        CallPhase phase = ClientCallState.phase();
        if (phase != builtFor
                || (phase == CallPhase.IDLE && onlinePlayers().size() != builtPlayerCount)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // Phone body
        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, COL_BEZEL);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, COL_BODY);
        g.fill(left + 6, top + 6, left + PANEL_W - 6, top + PANEL_H - 6, COL_SCREEN);

        // Speaker slot
        g.fill(left + PANEL_W / 2 - 14, top + 2, left + PANEL_W / 2 + 14, top + 4, COL_BEZEL);

        int cx = left + PANEL_W / 2;
        CallPhase phase = ClientCallState.phase();
        String other = ClientCallState.otherName();

        switch (phase) {
            case IDLE -> {
                g.drawCenteredString(font, Component.translatable("phone.gui.contacts"), cx, top + 14, COL_TEXT);
                if (builtPlayerCount == 0) {
                    g.drawCenteredString(font, Component.translatable("phone.gui.nobody"), cx, top + 80, COL_DIM);
                }
            }
            case OUTGOING -> {
                g.drawCenteredString(font, Component.translatable("phone.gui.calling"), cx, top + 40, COL_DIM);
                g.drawCenteredString(font, other, cx, top + 60, COL_TEXT);
                g.drawCenteredString(font, dots(), cx, top + 80, COL_YELLOW);
            }
            case INCOMING -> {
                g.drawCenteredString(font, Component.translatable("phone.gui.incoming"), cx, top + 40, COL_DIM);
                g.drawCenteredString(font, other, cx, top + 60, COL_TEXT);
                g.drawCenteredString(font, dots(), cx, top + 80, COL_YELLOW);
            }
            case ACTIVE -> {
                g.drawCenteredString(font, Component.translatable("phone.gui.in_call"), cx, top + 40, COL_GREEN);
                g.drawCenteredString(font, other, cx, top + 60, COL_TEXT);
                long s = ClientCallState.secondsInPhase();
                g.drawCenteredString(font, String.format("%02d:%02d", s / 60, s % 60), cx, top + 80, COL_TEXT);
                g.drawCenteredString(font, Component.translatable("phone.gui.mic_hint"), cx, top + 110, COL_DIM);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String dots() {
        int n = (int) ((System.currentTimeMillis() / 400L) % 4L);
        return ".".repeat(n);
    }
}
