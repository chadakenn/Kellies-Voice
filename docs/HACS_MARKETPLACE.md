# Install Kellie's Voice in HACS or Home Assistant Marketplace

Kellie's Voice has **two separate parts**: a Home Assistant custom integration and a signed Android TV APK. The Marketplace/HACS installs **only the Home Assistant integration**, not the APK. Install the matching Android TV app from the [official GitHub Releases](https://github.com/chadakenn/Kellies-Voice/releases/latest).

**Catalog status (October 31, 2026):** This repository is available as a **custom repository**, not yet indexed in the official `hacs/default` catalog. A custom installation and a successful HACS validation are not proof of catalog approval.

## Home Assistant 2026.10 and earlier: standalone HACS

1. In HACS, open its custom repositories menu and enter `https://github.com/chadakenn/Kellies-Voice`, category **Integration**. Do not add another entry if the repo is already installed.
2. Install the public integration release. Restart Home Assistant if prompted so the Python integration loads.
3. Install or update the **signed** Android TV APK from [Releases](https://github.com/chadakenn/Kellies-Voice/releases/latest). Update in place; do **not** uninstall or erase TV data.
4. Add the discovered TV under **Settings → Devices & services → Add integration → Kellie's Voice**, or use its supported manual pairing fallback.

The existing [HACS repository link](https://my.home-assistant.io/redirect/hacs_repository/?owner=chadakenn&repository=Kellies-Voice&category=integration) also supports adding the custom repo.

## Home Assistant 2026.11 and later: built-in Marketplace

Home Assistant 2026.11 introduces **Settings → Marketplace**, built from HACS and sharing its `hacs/default` catalog. The existing `hacs_repository` links continue to work and redirect to Marketplace on supporting versions.

Until this repo is accepted into that catalog, use **Marketplace's custom-repository path** with the same GitHub repository URL. Do **not** install a second `kellies_voice` integration just because Marketplace replaces the old HACS UI. After catalog acceptance, users should be able to discover the listing directly in Marketplace.

Home Assistant's documented HACS-to-Marketplace migration is intended to carry existing installed/custom repositories and update entities forward. This project has **not** yet verified a complete 2026.11 migration with the paired TV, so take a normal HA backup before an OS upgrade and verify your environment afterward. Do not upgrade HA just to obtain a new app-store link.

## Release versions and safe updates

- **Published as of October 31, 2026:** [v0.17.1](https://github.com/chadakenn/Kellies-Voice/releases/tag/v0.17.1). Future installs should use the [latest release](https://github.com/chadakenn/Kellies-Voice/releases/latest) and its matching HACS component version.
- Keep the existing `kellies_voice` config entries, paired TV IDs, entity names, and automation targets. An in-place HACS update should not require removing/re-adding the integration.
- The signed TV APK is installed separately. Do not install QA or debug APKs over the public package.
- After upgrading, check authenticated Assist, the TV alert receiver, cameras, Quick Actions, themes and wake-word settings as relevant; preserve the existing owner's pairing.
- Do not interpret a GitHub PR merging into `main` as an official Marketplace listing. Admission is controlled by the external catalog maintainers.

## Maintainer checklist for catalog submission

- [x] Public repository, issues enabled, useful README, description and topics `home-assistant`, `hacs`, `android-tv`, `voice-assistant` (verified October 31, 2026).
- [x] `hacs.json` and versioned `custom_components/kellies_voice/manifest.json`, integration documents and GitHub Releases.
- [x] Bundled branding icons in `custom_components/kellies_voice/brand/`.
- [ ] Merge and verify **current-main** HACS CI without any `ignore` parameter.
- [ ] Merge and verify **current-main** hassfest CI.
- [ ] Review current HA Core 2026.11 compatibility and run a non-production HACS → Marketplace migration test without losing installed repositories, update entities, or paired TV configurations.
- [ ] Obtain owner approval for external submission, **then** fork [hacs/default](https://github.com/hacs/default) and add `chadakenn/Kellies-Voice` alphabetically to the `integration` catalog list.
- [ ] Fill out the external PR template using the *actual passing* HACS and hassfest run URLs and current signed release. Await upstream review (it may take months); acceptance is not automatic.

**Official guidance:** [Get into the catalog](https://contributors.home-assistant.io/marketplace/guides/catalog/), [validation checks](https://contributors.home-assistant.io/marketplace/reference/validation/), [Marketplace migration behavior](https://contributors.home-assistant.io/marketplace/how-it-works/).
