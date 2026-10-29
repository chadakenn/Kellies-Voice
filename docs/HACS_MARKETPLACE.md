# Home Assistant HACS and Marketplace installation

This page covers **only the Home Assistant custom integration** for Kellie's Voice. The **Android TV APK remains a separate, signed download** from [Kellie's Voice Releases](https://github.com/chadakenn/Kellies-Voice/releases/latest). Neither HACS nor Marketplace installs the Android APK.

## Home Assistant 2026.10 and earlier: standalone HACS

1. Open **HACS → ⋮ → Custom repositories** in Home Assistant.
2. Enter `https://github.com/chadakenn/Kellies-Voice` and select **Integration**. If this repository is already in HACS, do **not** add it again.
3. Open Kellie's Voice and install a compatible public **release**, then restart Home Assistant so the custom integration loads.
4. On Android TV, install the matching signed APK (currently v0.16.14 on stable), keep your existing saved login, and add the discovered TV in **Settings → Devices & services**. See [the full setup guide](INSTALL.md).

You can also use the [HACS add-repository link](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration). Until an official catalog listing has been accepted, it is still a **custom repository**.

## Home Assistant 2026.11 and later: built-in Marketplace

Marketplace is the planned built-in successor to the standalone HACS experience in Home Assistant 2026.11. **This integration is not yet listed in the official catalog.** In releases supporting Marketplace, use its custom repository/install path (UI wording may vary), with the same source `chadakenn/Kellies-Voice`. After approval into `hacs/default`, users should be able to discover the integration from the catalog without typing the GitHub URL.

This repository needs **one catalog submission** to `hacs/default`, not separate submissions for HACS and Marketplace. **Do not install a duplicate integration** if Home Assistant has already migrated a working Kellie's Voice custom repository. Keep the same integration domain `kellies_voice`, device registry and saved pairing data.

Do not upgrade Home Assistant solely for a store listing. Existing users may stay on HACS while we test the migration.

## Release/upgrade safeguards

- The currently published stable TV app and HA component are **v0.16.14**. The HA custom component's `manifest.json` version must match its intended released integration version. Install a newer signed Android APK **over** the existing one; do not uninstall or clear its data.
- After updating the HA integration, restart HA. Existing per-TV entities and automation actions should remain intact.
- For testing the new store, verify migrated repository and updates appear once, the domain `kellies_voice` has not duplicated, device/TV IDs and existing entity configuration remain present, and alert receiver/cameras/Assist continue to work.
- Do not treat this document as confirmation that Home Assistant 2026.11 migration was physically tested. A beta/release migration rehearsal is still needed.
- Do not confuse Marketplace for integrations with Home Assistant app/add-on stores or the separate signed TV APK.

## Official catalog publishing checklist (maintainers)

- [x] Public GitHub repository, `hacs.json`, versioned `custom_components/kellies_voice/manifest.json`, integration documentation and release history.
- [x] `hacs/action@main` CI defined **without ignored checks** in the draft readiness PR.
- [x] hassfest workflow created; its reported intent dependency, config-entry-only schema and key-order failures fixed in the draft readiness PR.
- [ ] **GitHub About → Edit repository details → Topics:** add **`home-assistant`** and **`hacs`** (optionally `android-tv` / `voice-assistant`), then verify HACS validation passes **without ignores**.
- [ ] Review icon/brand requirements, install docs and stable release tags. Test HA 2026.11 migration on a non-production HA instance.
- [ ] Owner approval, then fork [`hacs/default`](https://github.com/hacs/default) and add `chadakenn/Kellies-Voice` to its `integration` list in alphabetical order; submit that **separate PR** and wait for the HACS catalog maintainers' review. The catalog submission is **not** the same as this repository's readiness PR.
- [ ] Do not publish an APK or bump production versions merely to pass catalog tests.

Official references: [HACS publishing](https://www.hacs.xyz/docs/publish/include/) and [Home Assistant config-entry schema](https://contributors.home-assistant.io/core/integrations/yaml/).
