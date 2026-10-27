# Developer guide and release checklist

This is an experimental **native Android TV** project targeting the **onn 4K Pro**. Code is Java, app module `app/`. Local builds use the committed Gradle 8.9 wrapper (`./gradlew`), which is the same Gradle GitHub Actions uses.

## Development branch and test builds

Use the [`dev` branch test lane](DEV_CHANNEL.md) for signed, owner-tested
candidate APKs. On app changes, GitHub Actions builds
`kellies-voice-dev-signed` without publishing a public Release.
The ordinary in-app updater remains **stable-only**. Grok and ChatGPT
should use distinct feature branches and target `dev` for test candidates.
Do not push untested code to `main`.

## Build requirements

- JDK 17
- Android SDK platform 35 + build tools 35.0.0
- Gradle 8.9 via `./gradlew` (Android Gradle Plugin 8.7.3)
- Working dependency downloads for `com.squareup.okhttp3:okhttp:4.12.0`

Use GitHub Actions **Build Kellie's Voice Signed APK** for APKs to install on a TV. The workflow is triggered by changes to app/build config files or manually via workflow_dispatch; **README and docs-only changes do not launch an Android build**.

Do **not** hand out `app-debug.apk` files from temporary GitHub runners. The original alpha suffered **conflicts with an existing package** because Android debug signing certificates differed between runs. All user-installable updates must be consistently signed with the same private key.

## Main source files

| File | Responsibility |
| --- | --- |
| `app/src/main/java/com/example/tvvoiceprobe/VoiceActivity.java` | Main TV D-pad TALK/STOP/SETTINGS screen, HA config, transcript, answer and Android TTS playback |
| `app/src/main/java/com/example/tvvoiceprobe/HaAssistClient.java` | HA WebSocket authentication, `assist_pipeline/run` event handling, binary PCM framing, conversation state |
| `app/src/main/java/com/example/tvvoiceprobe/EnergyVad.java` | Auto-stop energy detector. Assist `stt-vad-end` remains a separate server signal |

`HaAssistClient`, `VoiceActivity` and `TvEventsService` are still large. They share the socket, the Assist run and the on-screen views, so moving those methods into new files in one pass would be a behavior change even where the text looks the same. Split one of those seams per pull request, and run `./gradlew :app:testDebugUnitTest` on that move. Release shrinking (`minifyEnabled`) stays off until a shrunk signed APK is installed on the onn. Dependabot opens the dependency updates; do not take a `targetSdk` bump without that same TV pass.
| `app/src/main/java/com/example/tvvoiceprobe/CredentialStore.java` | Device-local encrypted HA credentials via Android Keystore |
| `app/src/main/java/com/example/tvvoiceprobe/MainActivity.java` | Legacy mic/button and Android input diagnostics, opened from settings |
| `app/src/main/java/com/example/tvvoiceprobe/VoiceOverlayService.java` | Explicit opt-in floating panel over other TV apps, microphone-typed foreground service, D-pad TALK / STOP / CLOSE, HA TTS playback |
| `app/src/main/java/com/example/tvvoiceprobe/OverlayShortcutActivity.java` | Optional second TV launcher entry for onn ⭐ app-button mapping, after overlay permission and HA setup |
| `app/build.gradle` | Package ID, Android version code/name, dependencies and keystore signing configuration |
| `.github/workflows/build-apk.yml` | Repeatable signed release build and APK signature verification |

## Signing and release

Private signing key/password must stay off GitHub source control. Repository Actions secrets:

- `TV_SIGNING_KEYSTORE_B64`: private PKCS12 keystore encoded in base64, alias `tvvoice`
- `TV_SIGNING_PASSWORD`: keystore and key password

See [signing and recovery](SIGNING.md). First signed APK was verified in [run #31](https://github.com/chadakenn/Kellies-Voice/actions/runs/39397223494). Always prefer the **latest successful** signed run for new installers.

**Release checklist:**

1. Increment `versionCode` for a new installable version. Update `versionName`.
2. Commit changes to `main`, or use Actions → Run workflow.
3. Confirm **Restore private release-signing key**, **Build signed release APK**, **Verify APK signature**, and **Upload installable signed APK** all passed.
4. Download artifact `kellies-voice-signed`; unpack `app-release.apk`. Never embed signing key or HA token in the APK.
5. Test fresh install on the onn box, verify authentication, built-in microphone STT, TTS, D-pad operation and settings access.
6. **Test in-place upgrade** from previous **signed** APK; the HA token should survive. If Android reports a signing conflict, stop and compare certificates rather than uninstalling.
7. Update [changelog](../CHANGELOG.md), [test matrix](TESTING.md) and documentation based on what actually passed on hardware.

## Safety and architecture decisions

- Avoid copying code from references without checking licenses (Voice Satellite AGPL-3.0; Ava original Apache-2.0).
- Keep microphone access foreground and user-triggered until an explicit privacy-controlled background mode is designed.
- Never upload/print long-lived tokens, captured audio or private signing keys in CI logs.
- Prefer trusted HTTPS to Home Assistant. Existing local HTTP support is explicitly **unencrypted**.
- The Google TV voice key opens Google Assistant on tested hardware. An app-launch ⭐ mapping is a later project, and it does not mean we can capture the Bluetooth remote mic.
- **TTS and answer display were verified on the real onn box** (Home Assistant answered “5:07 AM” through the TV speakers). Camera overlays, PiP, the HA media_player entity, and video app audio focus still need physical hardware tests rather than assumptions from compilation.

## Contributions and repository naming

Please keep bug reports reproducible: include Android/Google TV version, app `versionName`, GitHub Actions run, remote key steps and a **redacted** status screenshot. Never post secrets. The GitHub repository is now named [`chadakenn/Kellies-Voice`](https://github.com/chadakenn/Kellies-Voice). If you cloned it before the rename, update your local remote with `git remote set-url origin https://github.com/chadakenn/Kellies-Voice.git`. Use the new canonical address in scripts and documentation.

## Open-source license and contribution notes

The project is distributed under **GNU GPL version 3 only (SPDX: `GPL-3.0-only`)**, with the full [LICENSE](../LICENSE) text at the repository root. Anyone who redistributes a modified binary must comply with GPLv3's source availability, notices and other requirements.

Do not silently import code under incompatible terms. Original [Ava](https://github.com/brownard/Ava) is Apache-2.0, while [Voice Satellite](https://github.com/jxlarrea/voice-satellite-card-integration) is AGPL-3.0; we have researched their architecture without copying implementation code. Third-party libraries retain their own licenses. Contributions are accepted under GPL-3.0-only unless explicitly agreed otherwise.
