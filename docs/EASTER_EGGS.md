# Kellie's Voice secret cat party 🐈🎉

A harmless **visual-only Easter egg** for Android TV. This is not a voice command, Home Assistant automation, permanent screensaver, TTS, or notification overlay.

## How to find it

1. Open **Kellie's Voice → SETTINGS → Updates & account**.
2. Highlight **Installed version** with the TV remote.
3. Press **OK five times**. After the third press, a tiny hint appears.
4. The TV fills with bouncing cartoon cat heads and falling confetti.

**Controls:** OK, Left, or Right swaps between the bouncing-cat party and a confetti-only storm. **Back** exits to Settings immediately.

## Safe by design

- No microphone, voice recording, TTS, Home Assistant requests, notifications, smart-home commands, or remote calls.
- No user data read or stored, and no network permission requested.
- Its own non-exported Android Activity, never a persistent overlay. It runs only after a person deliberately selects the version five times.
- Animation stops when it loses foreground focus; Back returns normally to Settings.
- The animation does not flash or strobe. As with all moving content, stop if it is uncomfortable.
- Cat faces and confetti are drawn in code. No art files, font downloads, or extra dependencies.

**Status:** Development PR only until signed Android TV QA. The public stable version doesn't contain this feature yet.
