---
name: verify-mermaid
description: Locally render + lint Mermaid diagrams in changed Markdown before pushing, using a vendored mermaid.min.js + headless Chrome (no Node, no CDN, no external render service — all unavailable/sandbox-blocked here). Use when adding or editing ```mermaid fences in docs, before opening/pushing a PR that touches diagrams, or when asked to "check the Mermaid", "verify the diagrams render", or "did the diagram break". A user-invoked pre-check, NOT a PreToolUse hook.
---

# Verify Mermaid

Local, offline pre-check for Mermaid diagrams. This repo has **no local Mermaid
renderer** (no Node) and the external render services (kroki / mermaid.ink) are
sandbox-blocked as exfil destinations, so the only authority used to be the
rendered GitHub PR view — and grammar-reasoning review agents gave a false
"all render-safe" verdict **twice** on #223 before GitHub caught `participant
OFF` colliding with the reserved `off` keyword (which fails the *entire*
diagram). This skill gives a real local eyeball plus deterministic greps for the
known traps.

It is **user-invoked** (run it before pushing), not a `PreToolUse` hook — a
synchronous render on every `.md` edit (~2–5 s × N docs) would be too costly.

## Run it

```bash
python3 .claude/skills/verify-mermaid/render_mermaid.py [FILE.md ...]
```

- No args → checks the changed Markdown (`git diff` staged + unstaged + vs
  `origin/develop`).
- `--out DIR` → where to write the PNGs (default: a fresh temp dir, path printed).
- `--mermaid-js PATH` → override the vendored bundle (defaults to the
  `mermaid.min.js` next to the script: pinned **mermaid 10.9.6** UMD,
  gitleaks-clean, committed so rendering needs no network).

Exit codes: **0** = ran fully (every fence rendered) and no ERROR traps; **1** =
a deterministic ERROR trap was found; **2** = ran but verification is
**INCOMPLETE** — a render was skipped/failed (e.g. no Chrome) or the changed-set
couldn't be computed, so you must **not** report render-safe; **3** = setup error
(the vendored `mermaid.min.js`, or an explicitly-passed file, is missing).
WARN-level findings stay advisory — eyeball the PNG regardless. The Summary line
prints `<rendered>/<total> rendered`, so "checked nothing visually" can never be
mistaken for "checked everything and it's clean".

## Two layers (neither is the final authority)

1. **Deterministic pre-flight greps** — encode the repo-bitten traps so the
   two-time false-"render-safe"-verdict failure mode can't silently recur:
   - **ERROR** — a `participant`/`actor` id that is a reserved Mermaid keyword
     (`off`, `on`, `end`, `as`, `note`, `loop`, … case-insensitive). Fix by
     aliasing: `participant OFFApi as Open Food Facts`.
   - **WARN** — raw `<` / `>` in message/label text (use the curly `{barcode}`
     form); unquoted `(` `)` `"` in a `stateDiagram-v2` transition label.
2. **Visual render** — builds a self-contained HTML (one labelled box per
   fence, `file:line (type)`), loads the vendored `mermaid.min.js`, and renders
   with `google-chrome --headless`. One **PNG per changed doc**. A parse failure
   draws Mermaid's **"Syntax error in text" bomb graphic** instead of a diagram
   — visually unmistakable from a real one.

## Interpreting the output

- **ERROR** → must fix before pushing; the diagram will not render on GitHub.
- **WARN** → review; may render here but GitHub's sanitizer can differ.
- **PNG** → `Read` it (the Read tool renders images). Confirm every fence drew a
  real diagram; a bomb graphic = that fence failed to parse.

## Authority — read this before reporting

This is a **strong pre-check, NOT the final authority.** The rendered **GitHub
PR view** is authoritative for Mermaid. Do **not** claim a diagram is
render-safe from this tool — or from agent reasoning — alone; have the diagrams
eyeballed on the rendered PR. (Per the CLAUDE.md lesson: subagent Mermaid
verdicts have been wrong twice; only the GitHub render reliably catches these.)

## Maintenance

- The vendored bundle is `mermaid.min.js` (UMD, pinned 10.9.6). To bump:
  `curl -sSL https://cdn.jsdelivr.net/npm/mermaid@<ver>/dist/mermaid.min.js -o
  .claude/skills/verify-mermaid/mermaid.min.js` then re-run against a known-good
  doc. Keep a **UMD** build (exposes `window.mermaid`); mermaid 11's `min.js` is
  an esbuild IIFE that does **not** expose `window.mermaid`, so it won't load
  via a plain `<script>` tag.
