# Fix the “icon not available” HACS update thumbnail

**Applies to Home Assistant 2026.3+ with HACS 2.0.5, including the Kellie's Voice HACS update entity.**

If **Settings → Updates → Kellie's Voice Update** shows a gray `icon not available` thumbnail, this is a known **HACS update-entity icon URL issue**, not a Kellie's Voice installer failure.

## Diagnosis

HACS 2.0.5 sets the `entity_picture` of `update.kellie_s_voice_update` to the *legacy public brands CDN*:

```text
https://brands.home-assistant.io/_/kellies_voice/icon.png
```

Since Home Assistant 2026.3, custom integrations can ship branding under `custom_components/<domain>/brand/`. Kellie's Voice **already ships** `brand/icon.png` and `brand/icon@2x.png` in the v0.17.1 and v0.17.4 releases. The new HA Brands API serves those assets locally, but this HACS 2.0.5 update entity points to the wrong URL. **Changing the Kellie's Voice manifest or bumping its version does not repair an image URL constructed by HACS.**

See upstream HACS discussion:
- https://github.com/hacs/integration/issues/5223
- https://github.com/hacs/integration/pull/5228
- https://github.com/hacs/integration/pull/5339

## Safe workaround: override this update entity's picture

This changes only its visual thumbnail and does not replace HACS, modify update installation behavior, or affect TV pairing.

Open **Home Assistant's existing `configuration.yaml`**, and **within its existing `homeassistant:` section**, add the following child setting (do **not** add a second `homeassistant:` key):

```yaml
  customize:
    update.kellie_s_voice_update:
      entity_picture: https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/custom_components/kellies_voice/brand/icon.png
```

For an installation with **no** existing `homeassistant:` key, use:

```yaml
homeassistant:
  customize:
    update.kellie_s_voice_update:
      entity_picture: https://raw.githubusercontent.com/chadakenn/Kellies-Voice/main/custom_components/kellies_voice/brand/icon.png
```

This is the public image from the official repository, so no HA authentication token is embedded in the URL. The image needs internet access and can be cached by browsers.

1. In Home Assistant, run **Developer Tools → YAML → Check configuration** (or the Home Assistant configuration check tool).
2. If it passes, restart Home Assistant once when convenient. Do not restart with invalid YAML.
3. Refresh the browser and check the update popup and **Developer Tools → States → `update.kellie_s_voice_update` → `entity_picture`**.
4. When a future HACS update fixes its image URL, remove this override to restore automatic local-brand behavior.

If the icon still does not display, verify that the public image is reachable in the browser. The HACS update entity may itself change its image attribute on refresh; inspect the actual `entity_picture` shown in States before assuming the override applied.

**Do not:** change the custom integration domain, copy brand icons into the old brands CDN, patch private HACS internals, uninstall/re-pair TVs, or install an unsigned development APK just for an update-dialog thumbnail.

Home Assistant's supported entity customization format is documented at https://www.home-assistant.io/integrations/homeassistant/#manual-customization . The upstream, long-term fix belongs in HACS, which should use the authenticated HA Brands API rather than the legacy CDN URL.
