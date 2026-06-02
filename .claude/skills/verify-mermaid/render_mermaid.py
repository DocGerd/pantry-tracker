#!/usr/bin/env python3
"""verify-mermaid: local, offline pre-check for Mermaid diagrams in Markdown.

Two layers of checking, neither of which is the final authority (the rendered
GitHub PR view is — see the note printed at the end and in SKILL.md):

  1. DETERMINISTIC pre-flight greps that encode the known, repo-bitten traps
     (reserved-keyword participant ids like `off`/`on`; raw <>/`"` in labels;
     unquoted ()/" in stateDiagram-v2 transition labels). These catch the exact
     failure mode where grammar-reasoning review agents gave a false
     "all render-safe" verdict twice on #223.

  2. A VISUAL render via headless Chrome + the vendored mermaid.min.js (no CDN,
     no external render service — both are sandbox-blocked / non-reproducible).
     A parse error renders as a visible Mermaid error graphic instead of a
     diagram, so a human (or the Read tool on the PNG) can eyeball each fence.

Usage:
    python3 render_mermaid.py [--mermaid-js PATH] [--out DIR] [FILE.md ...]

With no FILE args, defaults to the changed Markdown in `git diff --name-only`
(staged + unstaged + vs origin/develop). Exit code is non-zero only if a
deterministic ERROR-level trap is found; WARN-level findings and render results
are advisory (eyeball the PNG).
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_MERMAID_JS = os.path.join(SCRIPT_DIR, "mermaid.min.js")

# Reserved Mermaid keywords that BREAK a sequenceDiagram when used as a
# participant/actor id (case-insensitive). `off`/`on` are the ones that bit
# #223 (project shorthand "OFF" for Open Food Facts). Alias instead:
#   participant OFFApi as Open Food Facts
RESERVED_PARTICIPANT_IDS = {
    "off", "on", "end", "as", "participant", "actor", "note", "loop", "alt",
    "opt", "par", "and", "rect", "activate", "deactivate", "over", "left",
    "right", "of", "box", "critical", "break", "autonumber", "link", "links",
}

FENCE_RE = re.compile(r"^([ \t]*)```+\s*mermaid\s*$", re.IGNORECASE)
FENCE_CLOSE_RE = re.compile(r"^([ \t]*)```+\s*$")

# Arrow tokens to strip before hunting for *raw* < / > in label text, so we
# don't flag the arrows themselves. Order matters (longest first).
SEQ_ARROWS = ["-->>", "->>", "-->", "->", "--x", "-x", "--)", "-)", "<<-->>",
              "<<->>"]


class Finding:
    def __init__(self, level, doc, line, msg, fix=""):
        self.level = level  # "ERROR" | "WARN"
        self.doc = doc
        self.line = line
        self.msg = msg
        self.fix = fix

    def __str__(self):
        loc = f"{self.doc}:{self.line}"
        out = f"  [{self.level}] {loc}  {self.msg}"
        if self.fix:
            out += f"\n          fix: {self.fix}"
        return out


def extract_fences(path):
    """Yield (start_line, code) for each ```mermaid fence in the file."""
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    i = 0
    while i < len(lines):
        m = FENCE_RE.match(lines[i].rstrip("\n"))
        if m:
            indent = m.group(1)
            start = i + 1  # 1-based line of the ```mermaid opener
            body = []
            j = i + 1
            while j < len(lines):
                cm = FENCE_CLOSE_RE.match(lines[j].rstrip("\n"))
                if cm and len(cm.group(1)) == len(indent):
                    break
                body.append(lines[j].rstrip("\n"))
                j += 1
            yield start, body
            i = j + 1
        else:
            i += 1


def diagram_type(body):
    for ln in body:
        s = ln.strip()
        if s and not s.startswith("%%"):
            return s.split()[0].rstrip(":") if s.split() else ""
    return ""


def preflight(doc, start, body):
    """Run the deterministic trap greps on one fence. Returns [Finding]."""
    findings = []
    dtype = diagram_type(body)
    dtype_l = dtype.lower()

    for off, raw in enumerate(body):
        lineno = start + 1 + off  # line in the source doc
        ln = raw.strip()
        if not ln or ln.startswith("%%"):
            continue

        # --- Trap 1: reserved-keyword participant/actor id (sequenceDiagram) ---
        m = re.match(r"(?:participant|actor)\s+([A-Za-z0-9_]+)", ln)
        if m and m.group(1).lower() in RESERVED_PARTICIPANT_IDS:
            bad = m.group(1)
            findings.append(Finding(
                "ERROR", doc, lineno,
                f"participant/actor id '{bad}' is a reserved Mermaid keyword "
                f"(case-insensitive) — this fails the ENTIRE diagram on GitHub.",
                fix=f"alias it: `participant {bad}Api as <Display Name>` "
                    f"(e.g. `participant OFFApi as Open Food Facts`).",
            ))

        # --- Trap 2: raw < or > in message/label text ---
        # Look at the part after the first ':' (sequence message text) and
        # inside [..]/{..}/(..) node labels. Strip known arrows first.
        label_text = None
        if ":" in ln and ("->" in ln or "-->" in ln or "--x" in ln or
                          "-)" in ln or dtype_l.startswith("sequence")):
            label_text = ln.split(":", 1)[1]
        for br in re.findall(r"\[([^\]]*)\]|\{([^}]*)\}|\(([^)]*)\)", ln):
            piece = next((p for p in br if p), "")
            if piece:
                label_text = (label_text or "") + " " + piece
        if label_text:
            stripped = label_text
            # Strip Mermaid-supported inline HTML tags (<br>, <b>, …) BEFORE
            # hunting for raw angle brackets — they are valid label formatting,
            # not the <placeholder> trap. Without this we false-WARN on every
            # <br/> in the repo's own node labels (03-/05- arc42 diagrams).
            stripped = re.sub(r"</?(?:br|b|i|u|em|strong|sup|sub)\s*/?>", " ",
                              stripped, flags=re.IGNORECASE)
            for a in sorted(SEQ_ARROWS, key=len, reverse=True):
                stripped = stripped.replace(a, " ")
            if "<" in stripped or ">" in stripped:
                findings.append(Finding(
                    "WARN", doc, lineno,
                    "raw '<' or '>' in label/message text can break rendering "
                    "on GitHub's Mermaid.",
                    fix="use the curly placeholder form, e.g. `{barcode}` "
                        "instead of `<barcode>`.",
                ))

        # --- Trap 3: unquoted () or " in stateDiagram-v2 transition labels ---
        if dtype_l == "statediagram-v2" and ":" in ln and "-->" in ln:
            lbl = ln.split(":", 1)[1]
            if ("(" in lbl or ")" in lbl or '"' in lbl):
                findings.append(Finding(
                    "WARN", doc, lineno,
                    "unquoted '(' / ')' / '\"' in a stateDiagram-v2 transition "
                    "label can trip the stricter state grammar.",
                    fix="remove or rephrase the parentheses/quotes in the "
                        "transition label.",
                ))
    return findings


HTML_HEAD = """<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
  body {{ font-family: sans-serif; margin: 0; padding: 16px; background:#fff; }}
  .fence {{ border:1px solid #d0d7de; border-radius:6px; margin:0 0 20px;
            padding:8px 12px; }}
  .label {{ font:600 13px monospace; color:#57606a; margin-bottom:6px; }}
  .mermaid {{ background:#fff; }}
</style>
<script src="mermaid.min.js"></script>
</head><body>
"""
HTML_TAIL = """
<script>
  mermaid.initialize({ startOnLoad: true, securityLevel: 'loose',
                       suppressErrorRendering: false });
</script>
</body></html>
"""


def build_html(fences):
    """fences: list of (label, code). Returns HTML string."""
    parts = [HTML_HEAD]
    for label, code in fences:
        # Mermaid reads the textContent of div.mermaid verbatim; escape only the
        # HTML-significant chars so the diagram source survives intact.
        esc = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        parts.append(f'<div class="fence"><div class="label">{label}</div>'
                     f'<pre class="mermaid">{esc}</pre></div>\n')
    parts.append(HTML_TAIL)
    return "".join(parts)


def find_chrome():
    for c in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        p = shutil.which(c)
        if p:
            return p
    if os.path.exists("/usr/bin/google-chrome"):
        return "/usr/bin/google-chrome"
    return None


def render(html, mermaid_js, out_png, n_fences):
    """Render to out_png. Returns one of "ok" | "no-chrome" | "no-png" and
    NEVER raises, so a render problem stays advisory and the per-doc loop keeps
    going. The caller surfaces this status on stdout AND in the Summary, so a
    skipped/failed render can never read as a pass."""
    chrome = find_chrome()
    if not chrome:
        return "no-chrome"
    work = tempfile.mkdtemp(prefix="verify-mermaid-")
    try:
        shutil.copy(mermaid_js, os.path.join(work, "mermaid.min.js"))
        html_path = os.path.join(work, "index.html")
        with open(html_path, "w", encoding="utf-8") as fh:
            fh.write(html)
        height = min(250 + 560 * max(n_fences, 1), 16000)
        cmd = [chrome, "--headless=new", "--no-sandbox", "--disable-gpu",
               "--hide-scrollbars", "--force-device-scale-factor=1",
               "--virtual-time-budget=8000",
               f"--screenshot={out_png}", f"--window-size=1400,{height}",
               f"file://{html_path}"]
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        except (subprocess.TimeoutExpired, OSError) as e:
            print(f"  ! Chrome render errored ({type(e).__name__}): {e}",
                  file=sys.stderr)
            return "no-png"
        if not os.path.exists(out_png):
            print(f"  ! Chrome render produced no PNG. stderr tail:\n"
                  f"    {res.stderr.strip()[-400:]}", file=sys.stderr)
            return "no-png"
        return "ok"
    finally:
        shutil.rmtree(work, ignore_errors=True)


def changed_markdown():
    """Returns (any_ok, files). `any_ok` is False only if EVERY git probe
    failed (origin/develop unfetched, not a repo, …) — letting the caller tell
    "git couldn't compute the change set" apart from "genuinely no changed .md",
    instead of silently reporting nothing-to-check as all-clear."""
    files = set()
    any_ok = False
    for cmd in (["git", "diff", "--name-only"],
                ["git", "diff", "--name-only", "--cached"],
                ["git", "diff", "--name-only", "origin/develop...HEAD"]):
        try:
            r = subprocess.run(cmd, capture_output=True, text=True)
        except Exception as e:
            print(f"  ! git probe {' '.join(cmd)} raised: {e}", file=sys.stderr)
            continue
        if r.returncode != 0:
            first = (r.stderr.strip().splitlines() or [""])[0]
            print(f"  ! git probe {' '.join(cmd)} failed (rc={r.returncode}): "
                  f"{first}", file=sys.stderr)
            continue
        any_ok = True
        files.update(f for f in r.stdout.splitlines() if f.endswith(".md"))
    return any_ok, sorted(f for f in files if os.path.exists(f))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("files", nargs="*", help="Markdown files (default: changed)")
    ap.add_argument("--mermaid-js", default=DEFAULT_MERMAID_JS)
    ap.add_argument("--out", default=None, help="output dir for the PNG")
    args = ap.parse_args()

    # Resolve the file list. Explicit paths are validated up front — a typo must
    # not crash with a traceback nor be mistaken for a trap. With no paths, fall
    # back to the changed Markdown, distinguishing "git couldn't tell me" from
    # "genuinely nothing changed".
    if args.files:
        missing = [f for f in args.files if not os.path.exists(f)]
        if missing:
            print("ERROR: file(s) not found: " + ", ".join(missing),
                  file=sys.stderr)
            return 3
        files = args.files
    else:
        git_ok, files = changed_markdown()
        if not files:
            if not git_ok:
                print("Could not compute the changed Markdown set — every git "
                      "probe failed (see stderr). Pass file paths explicitly; "
                      "NOT verified.", file=sys.stderr)
                return 2
            print("No changed Markdown files to check (pass paths to force).")
            return 0

    if not os.path.exists(args.mermaid_js):
        print(f"ERROR: vendored mermaid.min.js not found at {args.mermaid_js}",
              file=sys.stderr)
        return 3

    out_dir = args.out or tempfile.mkdtemp(prefix="verify-mermaid-out-")
    os.makedirs(out_dir, exist_ok=True)

    grand_fences = 0
    grand_errors = 0
    grand_rendered = 0
    # One PNG PER changed doc (issue #248 acceptance), so a many-fence doc never
    # clips and each diagram is traceable to its source file.
    for path in files:
        fences = list(extract_fences(path))
        print(f"### {path} — {len(fences)} fence(s)")
        if not fences:
            print("  (no ```mermaid fences)\n")
            continue
        grand_fences += len(fences)

        findings = []
        html_fences = []
        for start, body in fences:
            dtype = diagram_type(body) or "?"
            html_fences.append((f"{path}:{start}  ({dtype})", "\n".join(body)))
            findings.extend(preflight(path, start, body))

        print("  -- pre-flight (known traps) --")
        if findings:
            for f in findings:
                print(f)
        else:
            print("  No reserved-keyword / raw-<> / state-label traps detected.")
        grand_errors += sum(1 for f in findings if f.level == "ERROR")

        safe = path.replace(os.sep, "__").removesuffix(".md") + ".png"
        out_png = os.path.join(out_dir, safe)
        status = render(build_html(html_fences), args.mermaid_js, out_png,
                        len(fences))
        if status == "ok":
            grand_rendered += len(fences)
            print(f"  -- render --\n  Rendered → {out_png}")
            print("  EYEBALL IT: Read the PNG. A Mermaid error graphic (the bomb "
                  "'Syntax error in text') vs a real diagram = that fence failed "
                  "to parse.")
        elif status == "no-chrome":
            print("  -- render: SKIPPED — no Chrome/Chromium found. THE VISUAL "
                  "LAYER DID NOT RUN; the greps above do NOT catch every parse "
                  "error. NOT render-safe.")
        else:  # "no-png"
            print("  -- render: FAILED — Chrome produced no PNG (see stderr). "
                  "THE VISUAL LAYER DID NOT RUN. NOT render-safe.")
        print()

    incomplete = grand_fences and grand_rendered < grand_fences
    print("== Summary ==")
    print(f"  {grand_fences} fence(s) across {len(files)} file(s); "
          f"{grand_rendered}/{grand_fences} rendered to PNG; "
          f"{grand_errors} deterministic ERROR(s).")
    if incomplete:
        print("  WARNING: not every fence was rendered — the visual eyeball is "
              "INCOMPLETE. Do NOT report render-safe.")
    print("== Authority ==")
    print("  This is a STRONG PRE-CHECK, not the final authority. The rendered "
          "GitHub PR view is authoritative for Mermaid — do NOT claim "
          "render-safe from this tool (or from agent reasoning) alone.")

    # Exit: 1 = deterministic ERROR trap; 2 = ran but verification INCOMPLETE
    # (a render was skipped/failed); 0 = clean + fully rendered.
    if grand_errors:
        return 1
    if incomplete:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
