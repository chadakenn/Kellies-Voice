# Archived Home Assistant v3 QA component

**Developer-only QA. Not the public Kellie's Voice integration.**

The experimental `kellies_voice_v3qa` source was moved here from the repository's root `custom_components/` folder to satisfy the official HACS catalog requirement of **one integration manifest** in that folder. The production HACS component remains at `custom_components/kellies_voice/` and is not modified by this relocation.

The old source and its test fixtures are preserved under `qa/ha_v3qa/kellies_voice_v3qa/`. Do not install this folder directly as a user-facing integration.

For developers needing the isolated test component, run:

```sh
python scripts/package_ha_v3qa.py
```

The generated archive `dist/kellies_voice_v3qa_ha.zip` still uses the original install layout `custom_components/kellies_voice_v3qa/` for compatibility with the historical developer guide. It is separate from the signed public app and is **not** included in the normal HACS integration.

The production `kellies_voice` application remains the only publishable integration under the repository root `custom_components/`. No Home Assistant user installation, pairing identity, TV login or runtime state is changed by this source relocation.

See issue #198 and upstream catalog PR https://github.com/hacs/default/pull/13067.
