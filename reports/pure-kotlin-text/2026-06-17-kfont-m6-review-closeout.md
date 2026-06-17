# 2026-06-17 KFONT M6 Review Closeout Audit

Status: documentation-only audit wave.

## Scope

- Re-audit the three remaining M6 tickets still marked `review` after their
  bounded implementation PRs merged:
  `KFONT-M6-002`, `KFONT-M6-004`, and `KFONT-M6-006`.
- Distinguish merged prerequisite slices from tickets that are still blocked on
  absent reviewed fixture families, promoted dumps, or runtime adoption work.
- Avoid synthetic-only substitutions for the missing shaping fixtures.

## Findings

- PR `#1706` (`KFONT-M6-002`) is merged, but the ticket is not closable yet: the
  runtime slice still lacks reviewed fixture provenance and expected dumps for
  `gsub-single-substitution.otf`, `gsub-multiple-substitution.otf`,
  `gsub-ligature-fi.otf`, `gsub-coverage-malformed.otf`, and
  `gsub-ligature-bad-component.otf`, plus promoted `gsub-trace.json` /
  `shaped-glyph-run.json` beyond the M6-001 contract goldens.
- PR `#1705` (`KFONT-M6-004`) is merged, but the ticket is not closable yet: the
  bounded positioning slice still lacks reviewed fixture provenance and
  expected dumps for `gpos-single-adjustment.otf`,
  `gpos-pair-format1-kerning.otf`, `gpos-pair-format2-class.otf`,
  `gpos-valueformat-malformed.otf`, and `gpos-pair-out-of-range.otf`, plus
  promoted `gpos-trace.json` / `shaped-glyph-run.json`.
- PR `#1707` (`KFONT-M6-006`) is merged, but the ticket is not closable yet: the
  policy slice remains contract-level only until the per-script shaping
  fixture families from `KFONT-M6-007`, `KFONT-M6-008`, and `KFONT-M6-009`
  land, runtime GSUB/GPOS consumes `ResolvedFeatureSet`, and the `drawString`
  compatibility path records explicit complex-feature non-enablement.

## Outcome

- `KFONT-M6-002` stays in `review`.
- `KFONT-M6-004` stays in `review`.
- `KFONT-M6-006` stays in `review`.

The merged slices remain valid prerequisite evidence. The review state here
records that the remaining work is fixture/dump/runtime-gated, but the bounded
implementation slices themselves remain current and accepted evidence.

## Validation

```bash
rtk git diff --check
```

## Non-Claims

- No complete GSUB support claim.
- No complete GPOS support claim.
- No complete script-policy support claim.
- No complex-script shaping promotion.
- No native shaper oracle claim.
