# KFONT-M0-001/M0-002 CI Foundation Evidence

Date: 2026-06-15
Classification: `tracked-gap`
Claim promotion allowed: `false`

Tickets:

- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-001-wire-pure-kotlin-font-modules-into-ci.md`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-002-add-pure-kotlin-text-specs-to-ci-trigger-paths.md`

Files:

- `.github/workflows/test.yml`
- `reports/pure-kotlin-text/font-ci-lane.json`
- `scripts/validate_pure_kotlin_text_ci.py`
- `scripts/test_validate_pure_kotlin_text_ci.py`

Evidence:

- CI job `pure_kotlin_font_foundation` names lane
  `pure-kotlin-font-foundation` and runs headless on `ubuntu-latest`.
- The lane resolves an explicit base commit for PR, push, or
  `workflow_dispatch` runs, then executes `git diff --check` against
  `.upstream/specs/pure-kotlin-text` and `reports/pure-kotlin-text` before
  running validators.
- The lane validates `reports/pure-kotlin-text/font-ci-lane.json` before
  invoking `scripts/validate_pure_kotlin_text_boundary_contracts.py` and:
  `:font:core:test`, `:font:sfnt:test`, `:font:scaler:test`,
  `:font:text:test`, `:font:glyph:test`, and `:font:gpu-api:test`.
- The CI validator rejects removed, disabled, or comment-only diff hygiene,
  CI validator, and boundary validator steps.
- Pull request and push path filters include
  `.upstream/specs/pure-kotlin-text/**`, `font/**`, and
  `reports/pure-kotlin-text/**`.
- Trigger samples cover one spec path, one M0 ticket path, and one archived-only
  migration path that must not activate the lane.
- Missing module policy uses diagnostic `font.ci.module-missing`,
  classification `tracked-gap`, and `claimPromotionAllowed=false`.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py
rtk python3 scripts/validate_pure_kotlin_text_ci.py
rtk git diff --check
```

Remaining gate:

This is validation infrastructure only. It does not add rendering, shaping,
scaler, parser, fallback, glyph atlas, SDF, emoji, paragraph, native engine, or
GPU text behavior.
