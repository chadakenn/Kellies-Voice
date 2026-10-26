# Contributing to Kellie's Voice

Thanks for helping build and test an Android TV companion for Home Assistant.

## Start here

- [Install the signed TV app and matching HACS integration](docs/INSTALL.md).
- [Supported TVs and community testing](docs/wiki/Supported-TVs-and-Testing.md).
- [Testing guide](docs/TESTING.md) and [development channel](docs/DEV_CHANNEL.md).
- [Security and privacy expectations](SECURITY.md).

**Release claims require physical-device evidence.** CI can validate code and packaging, but it cannot establish that a microphone, camera overlay, screen saver, or remote button works on a specific TV.

## Report an issue

Choose **Bug report**, **Device compatibility report**, or **Feature request** from [GitHub Issues](https://github.com/chadakenn/Kellies-Voice/issues/new/choose).

For a reproducible report, include the **actual Android/Google TV/Fire TV device** (which may be a streaming box attached to a different brand of television), OS version, Kellie's Voice APK version, matching HACS integration version, Home Assistant version, expected/actual behavior, exact reproduction steps, and whether the same command works when invoked directly in Home Assistant.

For microphone bugs, record whether pressing TALK produces a transcript, Android microphone permission, and only **non-sensitive** descriptions of input types from diagnostics. For camera bugs, distinguish **still snapshot**, **muted live video**, and **screensaver** features. For alerts, check **TV alert status → Resume TV alerts** before reporting.

Never upload Home Assistant long-lived access tokens, private pairing IDs, camera URLs with embedded credentials, exposed house numbers, recordings of other people without their consent, or signing material. Redact private information in screenshots and logs.

## Propose a change

1. Search issues and PRs first so you don't duplicate existing work.
2. Target the `dev` branch for experimental app features unless the maintainer explicitly requests a stable documentation change. Do **not** push directly to `main` from community contributions.
3. Keep changes scoped. Include tests or a clear reason they are not practical, and document anything a new tester must configure.
4. Say which version/device was tested. Distinguish simulator/CI tests from verified on-TV tests.
5. Explain whether the Android TV APK, Home Assistant custom integration, or **both** need an update.
6. Do not publish a new release, alter signing keys, or enable automatic posting/notifications on users' devices without explicit maintainer review.

## Documentation and wiki

The editable Wiki source is in [`docs/wiki`](docs/wiki/Home.md), and the navigation is in `_Sidebar.md`. Edit links as relative `.md` paths. The [wiki link checker](scripts/check_wiki_docs.py) should pass before publishing.

**Repository Markdown is not automatically the public GitHub Wiki.** The maintainer uses the owner-approved [wiki publishing workflow](docs/WIKI_PUBLISHING.md) to sync reviewed main-branch docs.

## Respect privacy and local-first design

Prefer local Home Assistant routes and explicit consent for microphones, cameras and alerts. Avoid requiring public inbound internet access. Keep signing keys and private tokens out of source and test fixtures. Describe any change to permissions, network access, persistent data or audio recording clearly.

See the [security policy](SECURITY.md) to report sensitive vulnerabilities through a private channel.
