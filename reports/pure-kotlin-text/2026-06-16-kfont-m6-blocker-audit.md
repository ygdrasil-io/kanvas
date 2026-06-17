# KFONT M6 Remaining Blocker Audit

## Scope

This wave audits the remaining KFONT M6 shaping backlog after the bounded draft
PRs currently open for prerequisite slices:

- `#1705` for `KFONT-M6-004`
- `#1706` for `KFONT-M6-002`
- `#1707` for `KFONT-M6-006`

The goal is to make the remaining gates explicit without inventing substitute
fixtures or broadening any support claim.

## Remaining Blockers

- `KFONT-M6-003` is gated by open draft PR `#1706` and the absent fixture set
  `gsub-context-format1.otf`, `gsub-context-format2-class.otf`,
  `gsub-context-format3-coverage.otf`, `gsub-context-nested-cycle.otf`, and
  `gsub-context-malformed-classdef.otf`.
- `KFONT-M6-005` is gated by open draft PR `#1705` and the absent fixture set
  `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`,
  `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`,
  `gpos-missing-gdef.otf`, and `gpos-anchor-malformed.otf`.
- `KFONT-M6-007` is gated by `KFONT-M6-003`, `KFONT-M6-005`, open draft PR
  `#1707`, and the absent Arabic fixture set named in the ticket.
- `KFONT-M6-008` is gated by `KFONT-M6-003`, `KFONT-M6-005`, open draft PR
  `#1707`, and the absent Devanagari fixture set named in the ticket.
- `KFONT-M6-009` is gated by open draft PRs `#1705` and `#1707`,
  `KFONT-M6-005`, and the absent Thai/CJK fixture set named in the ticket.
- `KFONT-M6-010` is gated by `KFONT-M6-003`, open draft PR `#1705`,
  `KFONT-M6-005`, `KFONT-M4-005` still `proposed`, and the absent advanced
  GSUB/GPOS/variation fixture set named in the ticket.

## Validation

```bash
rtk git diff --check
```

## Non-Claims

This audit does not claim that the remaining tickets are implemented, ready for
support promotion, or safely replaceable by synthetic-only fixture substitutes.
