# KFONT-M5-005 Cluster Safety Evidence

Date: 2026-06-17
Status: review with bounded fixture evidence.

## Scope

KFONT-M5-005 adds a bounded pure Kotlin cluster-safety regression slice on top
of the shipped M5 foundations. It does not add GSUB/GPOS shaping, fallback
run splitting, emoji route selection, or rendering. It proves that the current
grapheme, bidi, and script-itemization boundaries stay aligned for a reviewed
fixture matrix, and it keeps `scaledemoji` explicitly visible as a remaining
legacy gate rather than implying emoji support.

## Evidence

- `ClusterSafetySuite` reuses `GraphemeClusterer`, `DefaultBidiResolver`, and
  `ScriptExtensionsItemizer` to emit a deterministic
  `cluster-safety-report.json`.
- The report links `unicode-segments.json`, `bidi-runs.json`, and
  `script-runs.json` by content hash and records per-fixture invariant results
  for grapheme-cluster integrity, bidi-run boundary alignment, and script-run
  boundary alignment.
- Checked-in fixtures now cover:
  `cluster-emoji-family-zwj.txt`, `cluster-emoji-skin-tone.txt`,
  `cluster-vs15-vs16.txt`, `cluster-arabic-mark.txt`,
  `cluster-devanagari-conjunct.txt`, `cluster-thai-tone.txt`,
  `cluster-cjk-variation-selector.txt`, `cluster-mixed-bidi.txt`, and the
  negative `cluster-negative-split.txt`.
- The negative split fixture records a stable
  `text.shaping.cluster-invariant-failed` diagnostic without using any
  external or platform oracle.
- `ClusterSafetyTest` asserts the checked-in golden byte for byte, checks that
  the ticket fixture set is present on disk, and verifies that Unicode-version
  mismatches still surface as
  `text.shaping.unicode-data-version-mismatch` in the cluster-safety path.
- Emoji-adjacent rows carry the legacy gate `scaledemoji`, but this slice does
  not retire it and does not add `emoji-sequence-unsupported`,
  fallback-route, color-glyph, or GPU evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*ClusterSafety*' --tests '*Grapheme*' --tests '*Bidi*' --tests '*ScriptItem*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No complete UAX #29, UAX #9, or script-itemization support claim.
- No GSUB/GPOS shaping, fallback-run splitting, paragraph, emoji route,
  color-glyph, or GPU text route support claim.
- No retirement of `scaledemoji`; that gate still depends on later shaping,
  fallback, and color/route evidence.

## Remaining Gate

This bounded M5-005 slice is ready for `review`, not `done`. Remaining gates:

- add an explicit `text.shaping.emoji-sequence-unsupported` refusal row owned
  by later emoji/shaping route work before claiming emoji-cluster refusal
  coverage;
- broaden reviewed CJK IVS coverage beyond the current bounded
  Script_Extensions/variation-selector context fixture before claiming full
  CJK variation-selector cluster safety;
- link later fallback-boundary evidence before M7 cluster-safe fallback work
  can reuse this ticket as complete closeout.
