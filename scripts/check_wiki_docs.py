#!/usr/bin/env python3
"""Validate repository-viewable wiki Markdown and optionally render GitHub Wiki pages.

GitHub repository Markdown requires Page.md links, while published GitHub Wikis
normally use Page slugs. Keep docs/wiki as the reviewable source of truth.
"""

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs" / "wiki"
PAGE_NAMES = (
    "Home", "Getting-Started", "TV-App-and-Permissions",
    "Home-Assistant-HACS-Setup", "Voice-and-Timers",
    "Cameras-and-Frigate", "Announcements-and-Weather",
    "YAML-Cookbook", "Troubleshooting", "Supported-TVs-and-Testing",
    "FAQ", "_Sidebar", "_Footer",
)
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
WIKI_FILE_LINK = re.compile(r"\]\(([A-Za-z0-9_-]+)\.md(#[A-Za-z0-9_-]+)?\)")


def slug(heading):
    heading = re.sub(r"<[^>]*>", "", heading.lower())
    heading = re.sub(r"[^a-z0-9_\-\s]", "", heading)
    return re.sub(r"\s", "-", heading.strip())


def headings(page):
    result = set()
    for line in page.read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            match = re.match(r"^#{1,6}\s+(.+?)\s*#*\s*$", line)
            if match:
                result.add(slug(match.group(1)))
    return result


def verify():
    errors = []
    for name in PAGE_NAMES:
        if not (DOCS / (name + ".md")).exists():
            errors.append(f"Missing wiki source page: {name}.md")
    for name in PAGE_NAMES:
        file = DOCS / (name + ".md")
        if not file.exists():
            continue
        content = file.read_text(encoding="utf-8")
        for url in LINK.findall(content):
            target = url.split()[0].strip("<>")
            if target.startswith(("https://", "http://", "mailto:")):
                continue
            if target.startswith("#"):
                dest = file
                anchor = target[1:]
            else:
                path, sep, anchor = target.partition("#")
                if not path.endswith(".md"):
                    errors.append(f"{name}.md: wiki navigation must use Page.md: {url}")
                    continue
                dest = (file.parent / path).resolve()
                if not dest.is_relative_to(DOCS.resolve()):
                    errors.append(f"{name}.md: external relative path: {url}")
                    continue
            if not dest.is_file():
                errors.append(f"{name}.md: missing link target: {url}")
            elif anchor and anchor.lower() not in headings(dest):
                errors.append(f"{name}.md: missing anchor in {dest.name}: #{anchor}")
    return errors


def publish(dest):
    dest.mkdir(parents=True, exist_ok=True)
    for name in PAGE_NAMES:
        source = (DOCS / f"{name}.md").read_text(encoding="utf-8")
        rendered = WIKI_FILE_LINK.sub(
            lambda match: f"]({match.group(1)}{match.group(2) or ''})", source
        )
        (dest / f"{name}.md").write_text(rendered, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--render-wiki", type=Path, help="Render repository links as GitHub Wiki links")
    args = parser.parse_args()
    errors = verify()
    if errors:
        for issue in errors:
            print("ERROR:", issue, file=sys.stderr)
        return 1
    print(f"Wiki link validation passed ({len(PAGE_NAMES)} pages, local targets and anchors).")
    if args.render_wiki:
        publish(args.render_wiki)
        print(f"Rendered wiki pages into {args.render_wiki}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
