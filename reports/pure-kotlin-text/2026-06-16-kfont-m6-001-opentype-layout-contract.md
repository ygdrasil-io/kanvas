# KFONT-M6-001 OpenType Layout Contract Evidence

Status: done; independently reviewed and freshly validated.

Ticket:

- `.upstream/specs/pure-kotlin-text/tickets/M6-opentype-layout-shaping/KFONT-M6-001-define-opentypelayoutengine-contract-and-dumps.md`

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/OpenTypeLayoutContract.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/OpenTypeLayoutEngineContractTest.kt`
- `reports/font/fixtures/expected/shaping/shaping-plan.json`
- `reports/font/fixtures/expected/shaping/gsub-trace.json`
- `reports/font/fixtures/expected/shaping/gpos-trace.json`
- `reports/font/fixtures/expected/shaping/shaped-glyph-run.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`

Evidence:

- `OpenTypeLayoutEngineContract` defines typed `OpenTypeRunInput`,
  `ResolvedFeatureSet`, table availability, lookup trace requests, direct
  glyph input, shaping plan, GSUB/GPOS trace, shaped glyph run, glyph
  positions, cluster mappings, and canonical evidence bundle outputs.
- `shaping-plan.json` is byte-for-byte asserted by
  `OpenTypeLayoutEngineContractTest` as an aggregated dump with four required
  contract cases: `simple-latin`, `direct-glyph-run`, `unsupported-script`,
  and `missing-table`.
- `gsub-trace.json`, `gpos-trace.json`, and `shaped-glyph-run.json` remain
  byte-for-byte asserted for the simple Latin no-op contract case.
- `gsub-trace.json` and `gpos-trace.json` include the run typeface,
  script/language-system facts, requested/enabled/disabled features, stable
  no-op event decisions, diagnostics, Unicode version, and source text hash.
- The no-op Latin fixture maps deterministic code points to glyph IDs while
  preserving cluster ranges, Unicode version, source text hash, typeface ID,
  script tag, feature state, fallback facts, and stable trace references.
- Direct glyph ID input bypasses GSUB and GPOS explicitly while still recording
  `directGlyphInput=true` and synthetic cluster facts in the aggregated
  shaping plan fixture.
- Direct glyph ID input still requires a deterministic `TypefaceID`, diagnoses
  mismatched glyph/range cluster facts, and does not fabricate empty source
  ranges.
- Direct glyph ID input does not emit missing GSUB/GPOS/GDEF table diagnostics,
  and GSUB/GPOS traces carry only stage-specific table or lookup diagnostics.
- The aggregated shaping plan fixture records stable refusal diagnostics for
  `text.shaping.script-unsupported` and
  `text.shaping.engine-contract-missing`, while tests also assert unsupported
  features, unsupported lookup types, malformed lookups, missing fallback, and
  cluster invariant failure.
- `dump-evidence-index.json`, `fixture-evidence-manifest.json`, and
  `font-claim-dashboard.json` register the contract as tracked/golden-gated
  evidence without changing `complex-shaping` support classification.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: initial `REJECT` for direct glyph
  validation and trace diagnostic precision, remediated and re-reviewed as
  `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:text:test --tests '*OpenTypeLayoutEngineContractTest*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py scripts/test_validate_pure_kotlin_text_fixture_manifest.py scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk ./gradlew --no-daemon :font:text:test --tests '*OpenTypeLayoutEngine*' --tests '*TextStackSurface*' --tests '*ScriptItem*'
rtk git diff --check
```

Remaining gate: this is contract and dump evidence only. It does not implement
GSUB or GPOS lookup behavior, required script support, font fallback policy,
paragraph layout, glyph artifacts, CPU oracle support promotion, or any GPU
text route.
