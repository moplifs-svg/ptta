package dev.phonemod.call;

import java.util.UUID;

/** One phone call between two players. */
public final class Call {

    public enum State {
        RINGING,
        ACTIVE
    }

    public final UUID caller;
    public final UUID callee;
    public final long startedTick;

    /** Written on the server thread, read from Plasmo Voice's network threads. */
    public volatile State state = State.RINGING;

    public Call(UUID caller, UUID callee, long startedTick) {
        this.caller = caller;
        this.callee = callee;
        this.startedTick = startedTick;
    }

    public UUID other(UUID me) {
        return me.equals(caller) ? callee : caller;
    }

    public boolean involves(UUID id) {
        return caller.equals(id) || callee.equals(id);
    }
}
