# Android APK signing for this TV app

**Status (2026-10-20):** the permanent GitHub Actions signing secrets have been configured. **[Run #31](https://github.com/chadakenn/Kellies-Voice/actions/runs/39397223494) succeeded**, including private-keystore restoration, building a release APK, Android signature verification and publishing the `kellies-voice-signed` artifact.

## Why the original APK reports "conflicts with another app"

The original `app-debug.apk` from GitHub Actions was signed with a temporary Android debug key generated on each hosted build machine. Android will not install a new APK over an installed app with the same package ID but a different certificate. This is Android's security protection, NOT a Home Assistant authentication error.

The original app uses `android:allowBackup="false"` and Android Keystore encrypted credentials. If you uninstall it, the saved Home Assistant URL and token are lost.


## Signed build provenance (development candidate)

The signed stable and development build workflows now request GitHub Actions
attestation permissions and use `actions/attest@v4` after verifying the
signed APK. This stores a signed SLSA provenance attestation keyed to the
*exact APK digest*. The public repo can verify it with the GitHub CLI:

```bash
gh attestation verify app-release.apk --repo chadakenn/Kellies-Voice
```

Download the APK from its actual GitHub Release before verification. A
successful provenance check confirms the artifact was attested by the stated
GitHub repository/workflow; it **does not** substitute for Android's APK
signature verification or imply every action/dependency in that workflow
was trustworthy. The app's built-in updater does **not** check provenance
attestations yet: it still validates the release checksum, installed app
identity, signing certificates, and (in the dev candidate) the APK's
versionCode/versionName against the signed release metadata.

Future work to review separately: pin all third-party and first-party
Actions to reviewed immutable commit SHAs, periodically review that list
for security updates, and plan any signing key/passphrase rotation with an
Android signing-lineage migration and real-device APK upgrade tests. Do
**not** change the permanent signing alias/keystore or passwords casually:
the existing `tvvoice` PKCS12 setup uses the same configured password
for the store and private key, and changing it without migrating the
actual keystore will break releases. These migration items are not
implemented by this candidate.

## Permanent, private release signing

The build workflow now signs `app-release.apk` with an **identical signing key on every future run**, read from GitHub Actions encrypted repository secrets.

The user receives a backup ZIP outside this repository with:
- `ha-julia-voice-signing.p12`: private Android signing key backup (NEVER COMMIT)
- `TV_SIGNING_KEYSTORE_B64.txt`: the base64 of that signing key, to paste into GitHub Actions encrypted secret
- `TV_SIGNING_PASSWORD.txt`: randomly generated strong keystore password, to paste into GitHub Actions encrypted secret
- `README.txt`: instructions

The files must be protected like passwords; anyone who possesses both the keystore and password could create updates that Android treats as authorized.

1. In GitHub open **Settings > Secrets and variables > Actions** for this repo.
2. Add repository secret named exactly `TV_SIGNING_KEYSTORE_B64`. Paste **the entire single line** from the corresponding private `.txt` file.
3. Add repository secret named exactly `TV_SIGNING_PASSWORD`. Paste the entire password from its private `.txt` file.
4. Open **Actions > Build Kellie's Voice Signed APK > Run workflow**, then wait for a green successful build.
5. Download artifact `kellies-voice-signed`. Extract `app-release.apk` and transfer to the TV.
6. **First stable release only**: because the old debug certificate is unrelated, uninstall the old alpha app from TV settings, then install the new `app-release.apk`. This clears the saved HA login; configure once more.
7. On each subsequent update, install the newly built `app-release.apk` **over the existing signed app without uninstalling**. The Home Assistant settings remain intact.

Never upload the signing key/password to the public repo, an issue, a chat screenshot, or a third-party file-sharing service. Back up both privately. GitHub Actions secret values are not visible in the source code.

On any fork or replacement repository **without both secrets**, the workflow intentionally fails rather than publishing another incompatible debug APK. In this repository, the signing setup was verified by run #31. All future releases must reuse **exactly the same** original signing key and increment `versionCode` for app updates.

If the password or key is lost, future releases cannot update this signed app without a reinstall and associated loss of stored credentials.
