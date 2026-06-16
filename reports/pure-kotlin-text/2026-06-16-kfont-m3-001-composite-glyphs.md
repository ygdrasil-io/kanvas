# KFONT-M3-001 Composite Glyphs

Status: done; independently reviewed.

## Scope

KFONT-M3-001 implements and proves the TrueType `glyf` composite glyph slice in
`font/scaler` only:

- component point matching in `ParsedTrueTypeGlyphScaler`;
- existing translation, uniform scale, non-uniform scale, two-by-two,
  scaled/unscaled offset, nested composite, and `gvar` component behavior;
- deterministic diagnostics for recursion/cycle depth, invalid component glyph
  IDs, invalid point indices, and excessive composite component counts;
- deterministic composite dump evidence for outline commands, bounds, metrics,
  component trace, `USE_MY_METRICS` facts, path hash/stat artifacts, and
  diagnostic snapshots.
- `USE_MY_METRICS` evidence is behavioral: `ParsedTrueTypeGlyphScaler.metrics()`
  and `scaledGlyphEvidence().metrics` use the first component carrying the bit
  as the metrics source.

## Evidence

- `reports/font/fixtures/expected/scaler/truetype-composite-glyphs.json`
  records the KFONT-M3-001 golden, including `advanceX` from component glyph 1
  when component 0 carries `USE_MY_METRICS` and the
  `truetype.composite-component-count` diagnostic snapshot.
- `ScaledTrueTypeGlyphEvidence` remains the canonical scaler evidence object
  for outline commands, bounds, metrics, component trace, and diagnostics.
- `dump-evidence-index.json`, `fixture-evidence-manifest.json`, and
  `font-fixture-inventory.json` link the golden as current/golden evidence
  without promoting a complete target support claim.
- The owning ticket is moved to `done` after independent spec and quality
  review accepted the implementation and evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CompositeGlyph*' --tests '*Glyf*'
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk git diff --check
```

All five commands passed locally on 2026-06-16.

## Non-Claims

- No A8 or SDF artifact claim.
- No GPU text route claim.
- No CFF or CFF2 outline support claim.
- No native scaler oracle or pixel-perfect hinting claim.
- No full TrueType hinting VM claim.
- No full IUP interpolation claim.
- No phantom-point metrics claim.
- No vertical metrics claim.
- No complete variable font support claim.
- No shaping, fallback, or paragraph layout claim.

## Review

- Independent spec review verdict: `SPEC_ACCEPTED`.
- Independent quality review verdict: `QUALITY_ACCEPTED`.

## Remaining Gates

No remaining gate for KFONT-M3-001. Downstream M3 variation, phantom-point,
vertical-metric, and malformed-glyph tickets remain separate tracked gaps.
