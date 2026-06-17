# 2026-06-17 KFONT-M7-004 Fallback Segmentation

Status: review with bounded fixture evidence only.

## Scope

- Add a deterministic `fallback-segmentation-report.json` for nine bounded
  fallback cluster fixtures.
- Reuse the M5 cluster inputs and cluster ranges while reusing M7 fallback
  decision helpers only as bounded synthetic evidence.
- Keep `scaledemoji` explicit and visible; do not claim emoji rendering, color
  fallback, platform fallback, or GPU text support.

## Evidence

- `defaultFallbackClusterEvidenceCases()` adds bounded fallback cases for
  Arabic mark, CJK variation-selector context, Devanagari conjunct, emoji ZWJ,
  emoji skin tone, VS15/VS16, Thai tone, Latin combining mark, and a reviewed
  negative split case.
- `FallbackSegmentationReport` links checked-in SHA-256 hashes for
  `cluster-safety-report.json`, `fallback-decision-trace.json`,
  `resolved-font-runs.json`, and `shaped-glyph-run.json`.
- Positive rows assert that fallback run boundaries align to whole grapheme
  clusters.
- `fallback-cluster-negative-split.txt` keeps the `scaledemoji` legacy gate
  open and records:
  `text.shaping.cluster-invariant-failed`,
  `font.fallback-glyph-unavailable`,
  `text.shaping.emoji-sequence-unsupported`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecision*'
rtk ./gradlew --no-daemon :font:text:test --tests '*FallbackSegmentation*' --tests '*ClusterSafety*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No complete cluster-safe fallback support claim.
- No emoji rendering or color glyph fallback claim.
- No platform or host-dependent fallback support claim.
- No `scaledemoji` retirement.

## Remaining Gates

- The negative emoji case is still a reviewed split-hazard report, not yet a
  complete whole-cluster refusal path.
- Dedicated per-fixture fallback trace and resolved-run assets still need to
  be promoted into checked-in fallback dumps before `done`.
- Host-dependent fallback participation is still unproved and remains
  non-normative.
