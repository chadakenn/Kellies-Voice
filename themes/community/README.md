# Community Theme Packs

Anyone can create, share, and import custom themes for Kellie's Voice on Android TV without rebuilding the app.

Themes are **data only** (JSON). They never contain code, scripts, URLs, or assets.

## Quick start

1. Copy `template.json` or `example-copper-night.json`.
2. Edit the colors, shapes, name, author, and unique ID (`community.your-id`).
3. Save as a `.json` file.
4. On the TV: **Settings → Overlay & appearance → Community Themes → Import Theme**.
5. Use the system file picker to select your JSON.
6. Preview, then Apply.

## Theme format (v1)

```json
{
  "format": 1,
  "id": "community.my-theme",
  "name": "My Theme",
  "author": "Your Name",
  "base": "glass",
  "colors": { ... },
  "shapes": { ... },
  "animationScale": 1.0
}
```

- `id` must match `community.[a-z0-9][a-z0-9-]{2,31}`
- Colors are `#RRGGBB` only
- Text contrast against screen/panel/card must be ≥ 4.5:1
- Focus contrast against card must be ≥ 3:1
- Max file size: 16 KB
- Max installed themes: 20

Built-in themes (`glass`, `classic`, `signal`, `aurora`, `obsidian`, `terminal`) remain available and are never replaced.

## Gallery

| Theme | Author | Base | Notes |
|-------|--------|------|-------|
| Copper Night | Community | glass | Example matching the default copper look |

See [CONTRIBUTE.md](CONTRIBUTE.md) for how to share your theme.
