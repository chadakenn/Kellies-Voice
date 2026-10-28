# Kellie's Voice Wiki: maintenance and publishing

## Why the old links failed

The files in [`docs/wiki`](wiki/Home.md) are ordinary GitHub repository
Markdown files, not automatically published GitHub Wiki pages.
GitHub Wiki links like `[Start](Getting-Started)` fail when these files are
opened under the repository's `blob/` view. The actual Wiki tab uses its own
separate `.wiki.git` repository.

The canonical editable files are now in `docs/wiki/*.md` with standard
working `[Start](Getting-Started.md)` links. The [offline link checker](../scripts/check_wiki_docs.py)
verifies every local page and named section. When published, the same script
automatically converts links to GitHub Wiki's `Getting-Started` form.

## Preview without publishing

Open [Documentation Home](wiki/Home.md) in this repository. Navigate all
pages and make sure you can reach Getting Started, the Cookbook, and
Troubleshooting without a 404. CI runs `python3 scripts/check_wiki_docs.py`
on documentation PRs and dev/main pushes.

## Publish the actual Wiki tab (owner approval)

**Current publication policy (v0.16.14):** Wiki publishing is **manual-only**. Main-branch pushes and commit messages do **not** trigger a Wiki update. The owner intentionally runs **Actions → Publish reviewed Kellie's Voice Wiki → Run workflow**, branch `main`, and enters **PUBLISH**. This safeguards public docs against accidental publication while PRs are reviewed. GitHub's Wiki tab is separate from repository `docs/wiki` files.

1. Review the documentation PR in **dev** and merge the reviewed docs into
   **main** separately from experimental app code.
2. Open **Settings → General → Features** in the GitHub repository. Ensure
   **Wikis** is enabled.
3. Open the repository's **Wiki** tab. If GitHub shows "Create the first page",
   create a simple **Home** page and save once. This initializes the
   repository's separate Wiki Git repository.
4. Go to **Actions → Publish reviewed Kellie's Voice Wiki → Run workflow**.
   Select **main** and choose **PUBLISH**. Do not run on `dev`.
5. Confirm the job passed. Open
   [the public Wiki](https://github.com/chadakenn/Kellies-Voice/wiki)
   and check the homepage, persistent sidebar, footer, Cookbook and
   [Resume TV alerts](https://github.com/chadakenn/Kellies-Voice/wiki/Troubleshooting#resume-tv-alerts-exact-steps).

The workflow uses the scoped repository `GITHUB_TOKEN` with write access
to the repository's *own* Wiki, not a public PAT embedded in source.
If the repository settings block that token from pushing, the workflow will
fail safely, and an admin must review GitHub permissions before retrying.
No tokens, personal access keys, private HA URLs, or other secrets belong
in documentation. Do not blindly disable repository protections.

**Important:** Creating or merging `docs/wiki/*.md` alone does **not**
publish the actual Wiki. Wiki content changes only when the maintainer
explicitly runs the manual publisher. It copies the maintained 13 pages
and preserves other Wiki pages rather than deleting contributors' work.

## Editorial guide

- Keep the cover page clean: first-install path, YAML Cookbook,
  troubleshooting and compatibility are the primary entry points.
- Maintain the grouped `_Sidebar.md` and linked `_Footer.md` so users
  never reach an isolated page.
- Every new internal documentation link uses a **real `.md` target** and
  an existing heading anchor; add new pages to `PAGE_NAMES` in
  `scripts/check_wiki_docs.py` before publishing.
- Use a real-world outcome first, then copyable YAML if needed.
- State the latest **public stable release**, and label draft/dev features
  clearly. Do not claim CI results are physical device tests.
- Don't publish the maintainer's device IDs, Home Assistant IPs or tokens.
- Grok and ChatGPT can contribute from separate docs branches and open PRs
  against `dev`; the owner reviews and promotes stable documentation.

If the Wiki tab hasn't been initialized, use the **source documentation
links in README** as the immediately working fallback.
