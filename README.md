# Phone Mod (Forge 1.20.1 + Plasmo Voice)

Craftable phones with a dialing screen. Calls are carried over Plasmo Voice.

## How it plays

- Craft a **Phone** (iron / lapis / glass pane / redstone, see `data/phonemod/recipes/phone.json`) or take it from the creative tab.
- **Right-click the phone** (or press **P**, rebindable) to open the screen. Pick a player to call.
- The other player gets a ring sound, a chat message with **[Answer] [Decline]** buttons, and the same options on their phone screen.
- Once answered, **just talk with your normal Plasmo Voice mic key**. The other player hears you at any distance, on a separate "Phone" volume slider (Plasmo Voice menu, Volume tab). People standing near you still hear you in person, because proximity voice is never cancelled.
- Commands (same actions as the GUI): `/phone call <player>`, `/phone answer`, `/phone decline`, `/phone hangup`.

Config: `config/phonemod-common.toml` (ring timeout, whether a phone item is required to call/receive, hang up if the phone is lost).

## Build

You need JDK 17 and the Gradle wrapper from the official Forge 1.20.1 MDK
(copy `gradlew`, `gradlew.bat` and `gradle/` next to `build.gradle`; ForgeGradle 6 wants Gradle 8.1 to 8.8).

```
./gradlew build
```

The jar lands in `build/libs/`. Install it on the **server and every client**, alongside **Plasmo Voice 2.1.17 (Forge)**.

## Plasmo Voice version

`gradle.properties` pins `plasmovoice_version=2.1.17`. If you run a different
Plasmo Voice release, change `plasmovoice_version` and `plasmovoice_version_range` to match.

## How the voice part works (`voice/PhoneVoiceAddon.java`)

- `@Addon` class loaded from the mod constructor via `PlasmoVoiceServer.getAddonsLoader().load(...)`.
- Registers a `phone` source line (own icon, own volume slider).
- When a call is answered, one direct source per participant is created (a direct source is heard only by the player it is attached to).
- `PlayerSpeakEvent` / `PlayerSpeakEndEvent` are observed at LOWEST priority, and each frame from a participant is forwarded unchanged to the other participant's direct source.

## Known limits

- If both callers are physically standing next to each other, the listener hears both the in-person voice and the phone. Hang up or step away.
- Calls need both players to be online and connected to Plasmo Voice.
