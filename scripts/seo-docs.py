#!/usr/bin/env python3
"""Post-process mdBook output so search engines can find every chapter.

mdBook leaves the sidebar empty in the HTML and fills it from deferred JS, so
crawlers that only see the first page never discover the rest of the book.
This script:

  * inlines the chapter list into each page (crawlable <a> links)
  * writes unique descriptions, canonical URLs, Open Graph URL, and JSON-LD
  * emits sitemap.xml and robots.txt
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SITE = "https://pavinglayer.github.io/jdbg"

SKIP_SITEMAP_NAMES = {
    "404.html",
    "print.html",
    "toc.html",
}
SKIP_SITEMAP_PREFIXES = ("google",)

SCROLLBOX_RE = re.compile(
    r"<mdbook-sidebar-scrollbox class=\"sidebar-scrollbox\">"
    r".*?</mdbook-sidebar-scrollbox>",
    re.DOTALL,
)
TITLE_RE = re.compile(r"<title>(.*?)</title>", re.DOTALL)
DESC_RE = re.compile(r'(<meta name="description" content=")([^"]*)(">)')
OG_DESC_RE = re.compile(r'(<meta property="og:description" content=")([^"]*)(">)')
TW_DESC_RE = re.compile(r'(<meta name="twitter:description" content=")([^"]*)(">)')
OG_TYPE_RE = re.compile(r'(<meta property="og:type" content=")([^"]*)(">)')
CANONICAL_RE = re.compile(r'<link rel="canonical" href="[^"]*"\s*/?>')
OG_URL_RE = re.compile(r'<meta property="og:url" content="[^"]*"\s*/?>')
JSONLD_RE = re.compile(
    r'<script type="application/ld\+json">.*?</script>',
    re.DOTALL,
)
ROBOTS_RE = re.compile(r'<meta name="robots" content="[^"]*"\s*/?>')
MAIN_RE = re.compile(r"<main\b[^>]*>(.*?)</main>", re.DOTALL)
H1_RE = re.compile(r"<h1\b[^>]*>(.*?)</h1>", re.DOTALL)
H2_RE = re.compile(r"<h2\b")
PRE_RE = re.compile(r"<pre\b[^>]*>.*?</pre>", re.DOTALL)
P_RE = re.compile(r"<p\b[^>]*>(.*?)</p>", re.DOTALL)
TAG_RE = re.compile(r"<[^>]+>")
TOC_OL_RE = re.compile(r'(<ol class="chapter">.*</ol>)', re.DOTALL)
HREF_RE = re.compile(r'href="([^"]*)"')
HEAD_CLOSE_RE = re.compile(r"</head>", re.IGNORECASE)


def path_to_root(rel_posix: str) -> str:
    depth = rel_posix.count("/")
    return "../" * depth


def is_content_html(path: Path) -> bool:
    if path.suffix != ".html":
        return False
    name = path.name
    if name in SKIP_SITEMAP_NAMES:
        return False
    if name.startswith(SKIP_SITEMAP_PREFIXES) and name.endswith(".html"):
        return False
    return True


def strip_tags(fragment: str) -> str:
    text = TAG_RE.sub("", fragment)
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def truncate_meta(text: str, limit: int = 160) -> str:
    if len(text) <= limit:
        return text
    cut = text[: limit - 1]
    if " " in cut:
        cut = cut.rsplit(" ", 1)[0]
    return cut.rstrip(".,;: ") + "…"


def heading_text(page_html: str) -> str:
    main = MAIN_RE.search(page_html)
    body = main.group(1) if main else page_html
    match = H1_RE.search(body)
    return strip_tags(match.group(1)) if match else ""


def first_paragraph(page_html: str) -> str:
    """Use the lede under H1, not a random later paragraph."""
    main = MAIN_RE.search(page_html)
    body = main.group(1) if main else page_html
    h1 = H1_RE.search(body)
    rest = body[h1.end() :] if h1 else body
    h2 = H2_RE.search(rest)
    lead = rest[: h2.start()] if h2 else rest[:2000]
    lead = PRE_RE.sub(" ", lead)
    for match in P_RE.finditer(lead):
        text = strip_tags(match.group(1))
        if len(text) >= 25:
            return text
    return ""


def page_title(page_html: str) -> str:
    match = TITLE_RE.search(page_html)
    return strip_tags(match.group(1)) if match else ""


def attr_escape(value: str) -> str:
    return html.escape(value, quote=True)


def rewrite_toc_hrefs(ol_html: str, prefix: str) -> str:
    ol_html = re.sub(r'\s*target="_parent"', "", ol_html)

    def repl(match: re.Match[str]) -> str:
        href = match.group(1)
        if href.startswith(("http://", "https://", "/", "#", "mailto:")):
            return match.group(0)
        return f'href="{prefix}{href}"'

    return HREF_RE.sub(repl, ol_html)


def load_toc_ol(book_dir: Path) -> str:
    toc_path = book_dir / "toc.html"
    if not toc_path.is_file():
        raise SystemExit(f"missing {toc_path}")
    match = TOC_OL_RE.search(toc_path.read_text(encoding="utf-8"))
    if not match:
        raise SystemExit(f"no chapter list in {toc_path}")
    return match.group(1)


def canonical_for(rel_posix: str, site: str) -> str:
    if rel_posix in ("index.html", "intro.html"):
        return f"{site}/"
    return f"{site}/{rel_posix}"


def source_for(rel_posix: str, src_dir: Path) -> Path:
    if rel_posix in ("index.html", "intro.html"):
        index_md = src_dir / "index.md"
        intro_md = src_dir / "intro.md"
        return index_md if index_md.is_file() else intro_md
    return src_dir / Path(rel_posix).with_suffix(".md")


def git_lastmod(src: Path) -> str | None:
    if not src.is_file():
        return None
    try:
        rel = src.resolve().relative_to(REPO_ROOT)
    except ValueError:
        rel = src
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "log", "-1", "--format=%cs", "--", str(rel)],
        capture_output=True,
        text=True,
        check=False,
    )
    date = result.stdout.strip()
    if result.returncode == 0 and date:
        return date
    return None


def file_lastmod(path: Path) -> str:
    ts = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
    return ts.strftime("%Y-%m-%d")


def priority_for(rel_posix: str) -> str:
    if rel_posix in ("index.html", "intro.html"):
        return "1.0"
    if rel_posix.startswith("guide/"):
        return "0.9"
    if rel_posix.startswith("howto/"):
        return "0.8"
    if rel_posix.startswith("commands/"):
        return "0.7"
    if rel_posix.startswith("reference/"):
        return "0.7"
    if rel_posix.startswith("contributing/"):
        return "0.5"
    return "0.6"


def upsert_after_description(page_html: str, tag: str, pattern: re.Pattern[str]) -> str:
    if pattern.search(page_html):
        return pattern.sub(tag, page_html, count=1)
    desc = DESC_RE.search(page_html)
    if desc:
        insert_at = desc.end()
        return page_html[:insert_at] + "\n        " + tag + page_html[insert_at:]
    close = HEAD_CLOSE_RE.search(page_html)
    if not close:
        raise SystemExit("page is missing </head>")
    return page_html[: close.start()] + tag + "\n    " + page_html[close.start() :]


def replace_desc(page_html: str, description: str) -> str:
    escaped = attr_escape(description)

    def repl(match: re.Match[str]) -> str:
        return match.group(1) + escaped + match.group(3)

    if DESC_RE.search(page_html):
        page_html = DESC_RE.sub(repl, page_html, count=1)
    if OG_DESC_RE.search(page_html):
        page_html = OG_DESC_RE.sub(repl, page_html, count=1)
    if TW_DESC_RE.search(page_html):
        page_html = TW_DESC_RE.sub(repl, page_html, count=1)
    return page_html


def json_ld(title: str, description: str, canonical: str, site: str, is_home: bool) -> str:
    graph: list[dict] = []
    if is_home:
        graph.append(
            {
                "@type": "WebSite",
                "name": "JDBG Documentation",
                "url": f"{site}/",
                "description": description,
            }
        )
        graph.append(
            {
                "@type": "SoftwareSourceCode",
                "name": "JDBG",
                "description": description,
                "url": f"{site}/",
                "codeRepository": "https://github.com/PavingLayer/jdbg",
                "programmingLanguage": ["Rust", "Java"],
            }
        )
    graph.append(
        {
            "@type": "TechArticle",
            "headline": title,
            "name": title,
            "description": description,
            "url": canonical,
            "inLanguage": "en",
            "isPartOf": {"@type": "WebSite", "name": "JDBG Documentation", "url": f"{site}/"},
        }
    )
    payload = {"@context": "https://schema.org", "@graph": graph}
    dumped = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f'<script type="application/ld+json">{dumped}</script>'


def patch_page(
    page_html: str,
    *,
    rel_posix: str,
    site: str,
    toc_ol: str,
    fallback_description: str,
) -> str:
    is_home = rel_posix in ("index.html", "intro.html")
    prefix = path_to_root(rel_posix)
    filled_toc = rewrite_toc_hrefs(toc_ol, prefix)
    scrollbox = (
        '<mdbook-sidebar-scrollbox class="sidebar-scrollbox">'
        f"{filled_toc}"
        "</mdbook-sidebar-scrollbox>"
    )
    if not SCROLLBOX_RE.search(page_html):
        raise SystemExit(f"{rel_posix}: sidebar scrollbox not found")
    page_html = SCROLLBOX_RE.sub(scrollbox, page_html, count=1)

    title = page_title(page_html) or "JDBG Documentation"
    lede = first_paragraph(page_html)
    h1 = heading_text(page_html)
    if lede:
        description = lede
    elif h1:
        description = f"{h1}. {fallback_description}"
    else:
        description = f"{title}. {fallback_description}"
    description = truncate_meta(description)
    canonical = canonical_for(rel_posix, site)

    page_html = replace_desc(page_html, description)
    if is_home and OG_TYPE_RE.search(page_html):
        page_html = OG_TYPE_RE.sub(
            lambda m: m.group(1) + "website" + m.group(3),
            page_html,
            count=1,
        )

    page_html = upsert_after_description(
        page_html,
        f'<link rel="canonical" href="{attr_escape(canonical)}">',
        CANONICAL_RE,
    )
    page_html = upsert_after_description(
        page_html,
        f'<meta property="og:url" content="{attr_escape(canonical)}">',
        OG_URL_RE,
    )

    ld = json_ld(title, description, canonical, site, is_home)
    if JSONLD_RE.search(page_html):
        page_html = JSONLD_RE.sub(ld, page_html, count=1)
    else:
        close = HEAD_CLOSE_RE.search(page_html)
        if not close:
            raise SystemExit(f"{rel_posix}: missing </head>")
        page_html = page_html[: close.start()] + "        " + ld + "\n    " + page_html[close.start() :]
    return page_html


def ensure_noindex(page_html: str) -> str:
    if ROBOTS_RE.search(page_html):
        return ROBOTS_RE.sub('<meta name="robots" content="noindex">', page_html, count=1)
    close = HEAD_CLOSE_RE.search(page_html)
    if not close:
        return page_html
    tag = '        <meta name="robots" content="noindex">\n'
    return page_html[: close.start()] + tag + page_html[close.start() :]


def write_sitemap(urls: list[tuple[str, str, str]], dest: Path) -> None:
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ]
    for loc, lastmod, priority in urls:
        lines.extend(
            [
                "  <url>",
                f"    <loc>{xml_escape(loc)}</loc>",
                f"    <lastmod>{xml_escape(lastmod)}</lastmod>",
                f"    <priority>{xml_escape(priority)}</priority>",
                "  </url>",
            ]
        )
    lines.append("</urlset>")
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_robots(dest: Path, site: str) -> None:
    host_path = "/" + site.split("/", 3)[-1].strip("/")
    # robots.txt paths are host-relative. This file is served from the project
    # site (/jdbg/robots.txt); Google still fetches robots.txt from the host
    # root, so every page also links the sitemap via rel=sitemap.
    body = (
        "User-agent: *\n"
        "Allow: /\n"
        f"Disallow: {host_path}/print.html\n"
        f"Disallow: {host_path}/toc.html\n"
        f"\nSitemap: {site}/sitemap.xml\n"
    )
    dest.write_text(body, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "book_dir",
        nargs="?",
        default=str(REPO_ROOT / "doc" / "book"),
        help="mdBook output directory",
    )
    parser.add_argument(
        "--src",
        default=str(REPO_ROOT / "doc" / "src"),
        help="mdBook markdown source directory",
    )
    parser.add_argument(
        "--site",
        default=os.environ.get("JDBG_DOCS_URL", DEFAULT_SITE),
        help="Public site origin including path prefix, no trailing slash",
    )
    args = parser.parse_args()

    book_dir = Path(args.book_dir).resolve()
    src_dir = Path(args.src).resolve()
    site = args.site.rstrip("/")
    if not book_dir.is_dir():
        raise SystemExit(f"book directory not found: {book_dir}")

    toc_ol = load_toc_ol(book_dir)
    fallback = (
        "Documentation for JDBG, a non-interactive, scriptable Java debugger CLI."
    )

    urls: list[tuple[str, str, str]] = []
    patched = 0
    for html_path in sorted(book_dir.rglob("*.html")):
        rel = html_path.relative_to(book_dir).as_posix()
        text = html_path.read_text(encoding="utf-8")

        if rel in ("404.html", "print.html", "toc.html"):
            html_path.write_text(ensure_noindex(text), encoding="utf-8")
            continue
        if not is_content_html(html_path):
            continue

        html_path.write_text(
            patch_page(
                text,
                rel_posix=rel,
                site=site,
                toc_ol=toc_ol,
                fallback_description=fallback,
            ),
            encoding="utf-8",
        )
        patched += 1

        if rel == "intro.html":
            continue
        src = source_for(rel, src_dir)
        lastmod = git_lastmod(src) or file_lastmod(html_path)
        urls.append((canonical_for(rel, site), lastmod, priority_for(rel)))

    if not urls:
        raise SystemExit("no content pages found for sitemap")

    # Homepage first, then the rest in path order (already sorted via rglob).
    urls.sort(key=lambda item: (0 if item[0] == f"{site}/" else 1, item[0]))
    write_sitemap(urls, book_dir / "sitemap.xml")
    write_robots(book_dir / "robots.txt", site)

    # Older builds published Introduction at intro.html. Keep that URL as a
    # client redirect so existing links consolidate on the homepage.
    if not (book_dir / "intro.html").exists():
        home = f"{site}/"
        (book_dir / "intro.html").write_text(
            "<!DOCTYPE html>\n"
            '<html lang="en">\n<head>\n'
            '<meta charset="utf-8">\n'
            "<title>Introduction - JDBG - Scriptable Java Debugger</title>\n"
            f'<link rel="canonical" href="{attr_escape(home)}">\n'
            '<meta http-equiv="refresh" content="0;url=./">\n'
            '<meta name="robots" content="noindex">\n'
            f'<script>location.replace({json.dumps("./")});</script>\n'
            "</head>\n<body>\n"
            '<p>This page has moved to <a href="./">the JDBG documentation home</a>.</p>\n'
            "</body>\n</html>\n",
            encoding="utf-8",
        )

    sitemap_text = (book_dir / "sitemap.xml").read_text(encoding="utf-8")
    required = (
        f"{site}/",
        f"{site}/guide/getting-started.html",
        f"{site}/commands/index.html",
    )
    for loc in required:
        if f"<loc>{loc}</loc>" not in sitemap_text:
            raise SystemExit(f"sitemap missing {loc}")
    if "print.html" in sitemap_text or "toc.html" in sitemap_text:
        raise SystemExit("sitemap includes noindex pages")

    sample = (book_dir / "index.html").read_text(encoding="utf-8")
    if 'rel="canonical"' not in sample:
        raise SystemExit("index.html missing canonical URL")
    if "guide/getting-started.html" not in sample:
        raise SystemExit("index.html sidebar is missing chapter links")

    print(f"SEO: patched {patched} pages")
    print(f"SEO: wrote {book_dir / 'sitemap.xml'} ({len(urls)} URLs)")
    print(f"SEO: wrote {book_dir / 'robots.txt'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
