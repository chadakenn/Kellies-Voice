# Community Theme Packs

Custom themes change the look of Kellie's Voice without rebuilding the Android TV app.

**Important for public v0.17.0:** The APK bundles all six themes, but the public SETTINGS button routes to the older settings dialogs, hiding this gallery. This is a confirmed release-only navigation issue addressed by [PR #295](https://github.com/chadakenn/Kellies-Voice/pull/295). Use a newer **official signed** release with the routing fix when published; do not uninstall or install a QA/debug APK to recover the gallery.

## Install one of the included themes (works without an Android file picker)

1. On the TV, open **Settings → Overlay & appearance → Community Themes**.
2. Choose **Browse included community themes (6)**.
3. Select **Install & apply Midnight Ocean**, or any other example.
4. Verify the Settings, voice overlay and TV popups follow the selected palette.
5. Choose **Restore Default (Copper Glass)** to go back.

The six theme examples from [`themes/community`](https://github.com/chadakenn/Kellies-Voice/tree/main/themes/community) are also bundled as JSON resources in the APK. This works even when the TV lacks Android's DocumentsUI picker.

## Import a custom JSON theme

**If your TV has a file picker:** choose **Import Theme (file picker)**, select your own `.json` theme file and then select the imported theme to apply it.

**If you see "You don't have an app that can do this":** your TV lacks a supported Android document picker. No extra file manager is required for the included samples. For custom themes, use the app-owned ADB transfer directory:

1. With wireless ADB already paired and connected, run this on the PC after opening Kellie's Voice on the TV once (replace the PC filename as needed):

   ```powershell
   .\adb.exe push .\midnight-ocean.json /sdcard/Android/data/com.example.tvvoiceprobe.qa/files/theme-import/midnight-ocean.json
   ```

   For the **public stable** `com.example.tvvoiceprobe` app, omit the `.qa` suffix in the TV path. If Android reports `No such file or directory`, first choose **Scan ADB theme import folder** in Kellie's Voice to create the directory, then push again.

2. In Settings, select **Scan ADB theme import folder**.
3. Select **Import midnight-ocean.json** (or your transferred filename). The theme is validated, installed and applied.

This fallback is explicitly for users who already have ADB and does not enable arbitrary public file-system access. The normal file-picker flow remains available on Android devices with a provider. ADB is not needed to use the six included themes.

## Create and share a theme

Copy [`themes/community/template.json`](https://github.com/chadakenn/Kellies-Voice/blob/main/themes/community/template.json), edit the colors and ID, then import it on your TV. See [CONTRIBUTE.md](https://github.com/chadakenn/Kellies-Voice/blob/main/themes/community/CONTRIBUTE.md) for sharing themes via pull request.

## Safety and limits

- JSON data only. No APKs, scripts, custom code or arbitrary URLs.
- Max 16 KB per JSON; max 20 installed community themes.
- Text and focus colors must meet contrast requirements.
- Only app-owned ADB theme files are read; no broad Android storage permission is required.
- The install step validates the JSON before it replaces an existing theme.
- Pairing, passwords and Home Assistant credentials are unaffected.
- Built-in themes remain available, with **Restore Default** returning to Copper Glass.
