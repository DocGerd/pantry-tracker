# NNNN. Short, imperative title

<!--
  Copy this file to `NNNN-kebab-case-slug.md` where NNNN is the next available
  zero-padded sequence number (look at the highest-numbered existing ADR and
  add 1). The filename slug and the H1 title should match.

  Keep the body terse — the value of an ADR is making the decision searchable
  and citable, not re-writing the design doc. ~80-180 lines is a good target
  for most ADRs.
-->

## Status

<!--
  One of:
    Proposed         — under discussion, not yet a commitment
    Accepted         — decision is in effect; include the acceptance date
    Deprecated       — the decision still describes today's code but is no
                       longer recommended for new work
    Superseded by NNNN-…  — a later ADR replaces this one; link it

  Once the status is `Accepted`, the body of the ADR is immutable except for
  typo fixes and clarifying edits that do not change the decision. To reverse
  or revise a decision, write a NEW ADR and add a `Superseded by` line on the
  old one.
-->

Accepted — YYYY-MM-DD

## Context

<!--
  1-3 short paragraphs explaining the forces at play: what problem are we
  solving, what constraints apply, what alternatives were considered, what
  prior decisions or specs this builds on. Avoid pre-justifying the decision
  here — just describe the situation a reasonable reader would need in order
  to understand why a decision was needed at all.

  Link to the upstream prose source (design spec, arc42 section, related PR)
  rather than re-stating it.
-->

## Decision

<!--
  A terse, declarative statement of what is being decided. Imperative voice
  ("We use Room for local persistence"), not future ("We will use…") or
  passive ("Room is used…"). One paragraph is usually enough. If the
  decision has multiple parts, use a short numbered list.

  This is the section a reader skimming the directory will see first. Make
  it possible to understand the decision without reading Context.
-->

## Consequences

<!--
  A bullet list. Cover BOTH positive and negative consequences — do not
  whitewash the trade-offs. If the decision has a known limit ("breaks at
  scale N", "won't work on Android < X"), name it here.

  Suggested shape:

  **Positive.**
  - …
  - …

  **Negative.**
  - …
  - …

  If a follow-up action is implied by the decision (e.g. "we will need a
  static-inspection script before the next release"), note it here and link
  the tracking issue.
-->

## References

<!--
  Relative links to the upstream prose this ADR draws from:
    - the arc42 section or specs/ document that originally documented the
      decision
    - the source files that implement it (so a reader can jump straight from
      the decision to the code)
    - the PR / issue numbers where the decision landed

  Format: relative paths for in-repo files, full URLs for GitHub PRs/issues.
-->
