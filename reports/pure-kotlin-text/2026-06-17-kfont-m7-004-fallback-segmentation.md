# 2026-06-17 KFONT-M7-004 Fallback Segmentation

Status: done with bounded fixture evidence only.

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
  emoji skin tone, VS15/VS16, Thai tone, Latin combining mark, and a bounded
  whole-cluster refusal case for the negative emoji fixture.
- `FallbackSegmentationReport` links checked-in SHA-256 hashes for
  `cluster-safety-report.json`, `fallback-decision-trace.json`,
  `resolved-font-runs.json`, and `shaped-glyph-run.json`.
- Positive rows assert that fallback run boundaries align to whole grapheme
  clusters.
- `fallback-cluster-negative-split.txt` keeps the `scaledemoji` legacy gate
  open and records:
  `text.fallback.cluster-split-forbidden`,
  `text.fallback.emoji-fallback-unavailable`,
  `text.shaping.emoji-sequence-unsupported`, and
  `text.shaping.fallback-missing`.
- Every `fallback-cluster-*` case now carries dedicated `fallback-fixture`
  refs for `decisions`, `runs`, and the combined per-fixture asset, while a
  checked non-normative host-dependent marker row points to
  `host-dependent-system-fallback` without broadening fallback support claims.

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

No ticket-local gate remains for `KFONT-M7-004`. This stays bounded done
evidence only: no complete cluster-safe fallback support claim, no emoji
rendering claim, no platform fallback support claim, and no `scaledemoji`
retirement.
