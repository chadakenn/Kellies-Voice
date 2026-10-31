# Contributing a Community Theme

1. Fork this repository.
2. Copy `themes/community/template.json` to `themes/community/your-theme-id.json`.
3. Customize colors, shapes, name, author, and ID.
4. Test locally on Android TV via **Settings → Overlay & appearance → Community Themes → Import Theme**.
5. Open a pull request adding your JSON file to `themes/community/`.
6. Include a short description and a screenshot if possible.

## Rules

- Themes must be pure JSON data.
- No scripts, external URLs, APKs, or arbitrary assets.
- Maintain readable contrast and clear focus indicators.
- Do not modify pairing, credentials, or other settings.
- The maintainers will review for safety and compatibility.

Once merged, anyone can download the raw JSON from GitHub and import it on their TV.
