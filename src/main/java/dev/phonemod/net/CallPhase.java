package dev.phonemod.net;

/** What the local player's phone is doing right now. Mirrored from the server to the client. */
public enum CallPhase {
    IDLE,
    /** You are calling someone and they have not answered yet. */
    OUTGOING,
    /** Someone is calling you. */
    INCOMING,
    /** Connected: voice is flowing both ways. */
    ACTIVE
}
