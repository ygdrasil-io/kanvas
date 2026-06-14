# Pure Kotlin Text Coverage And Ticket Matrix

Date: 2026-06-14
Status: coordination evidence

This report maps `.upstream/specs/pure-kotlin-text/` to implementation slices.
It is not a support claim. The current support source of truth remains
`.upstream/specs/font/` and implementation evidence in tests/reports. Complete
target support still requires fixtures, semantic dumps, CPU oracle evidence,
GPU evidence when a GPU route is claimed, and stable refusal diagnostics.

## Hard Constraints

- Normative behavior stays pure Kotlin.
- HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite,
  fontconfig, platform shapers, and native font APIs may appear only in
  optional non-normative drift reports.
- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WGSL as the GPU shader target.
- `:gpu-renderer` must not parse fonts, shape text, resolve fallback fonts, or
  depend on direct `Sk*` API types.
- `:kanvas-skia` is a facade over the pure Kotlin core, not the text core.
- LCD subpixel text is future research, not part of this complete target.
- Archived migration checkboxes and closed milestone labels are historical
  evidence only.

## Spec Coverage Matrix

| Spec | Capability Target | Current Implementation Surface | Evidence Gate |
|---|---|---|---|
| `00-architecture-and-module-boundaries.md` | Module ownership, dependency direction, boundary contracts, serializable diagnostics. | `font/core`, `font/sfnt`, `font/scaler`, `font/text`, `font/glyph`, `font/gpu-api`, `gpu-renderer/text`. | Boundary tests, no forbidden dependencies, dumps with no object identity. |
| `01-font-source-sfnt-and-scalers.md` | Font sources, TTC/OTC, `cmap`, TrueType `glyf`, CFF/CFF2, variations, fallback catalog. | `font/core`, `font/sfnt`, `font/scaler`, current `kanvas-skia` OpenType backend. | TTF/TTC/malformed fixtures, path/metric dumps, variation fixtures, stable `font.*` diagnostics. |
| `02-opentype-layout-shaping-engine.md` | Unicode data, bidi, script itemization, GSUB/GPOS/GDEF, clusters, fallback runs, script matrix. | `font/text` basic segmentation/bidi/script, bounded kerning and GPOS pair slice. | One positive and one refusal fixture per required script row, shaping dumps, `text.shaping.*` diagnostics. |
| `03-paragraph-engine.md` | Paragraph builder, style runs, wrapping, bidi lines, ellipsis, placeholders, selection, hit testing. | `font/text` paragraph skeleton and deterministic simple line breaker. | Layout dumps, paragraph fixtures, hit-test/selection evidence. |
| `04-glyph-representation-and-artifacts.md` | Outline, A8, SDF, atlas, strike keys, cache, invalidation, CPUPreparedGPU artifacts. | `font/glyph`, `font/gpu-api`, current `SkCpuGlyphCache` prototype. | Mask/SDF hashes, atlas dumps, stale/capacity refusals, `text.glyph.*` diagnostics. |
| `05-color-fonts-bitmap-svg-emoji.md` | COLR/CPAL, COLRv1 graph, PNG bitmap glyphs, SVG-in-OpenType, emoji dispatch. | `font/glyph/color`, `font/sfnt` metadata, current OpenType color metadata. | COLR/PNG/SVG/emoji fixtures, budget/security refusals, non-PNG refusal evidence. |
| `06-gpu-renderer-handoff.md` | `DrawTextRun`, artifact refs, upload/generation facts, typed text artifacts, no `Sk*` payloads. | `font/gpu-api`, `gpu-renderer/commands`, `gpu-renderer/text`. | Handoff tests, artifact registry tests, unregistered/stale/upload refusals, no CPU-rendered text texture. |
| `07-validation-conformance-and-drift.md` | Kanvas-owned fixtures, CPU oracles, GPU evidence, drift reports only as non-normative. | Module tests and existing report directories. | Fixture manifest, checked-in dumps, old/new golden diffs, dashboard classifications. |
| `08-performance-budgets-and-telemetry.md` | Advisory metrics, cache counters, upload counters, later budget promotion. | `gpu-renderer/telemetry` scaffold and module-local cache facts. | Deterministic telemetry dumps; no hidden blocking perf gates. |
| `09-migration-from-current-font-pack.md` | Promote current prototypes without hiding remaining gates. | `OpenTypeFont.kt`, `SkShaper.kt`, `SkCpuGlyphCache.kt`, `SkWebGpuGlyphAtlas.kt`. | Gate retirement only with target spec section, tests, dumps, CPU/GPU evidence, and diagnostics. |

## Vertical Tickets

| Ticket | Classification | Scope | Probable Write Set | Ready Evidence |
|---|---|---|---|---|
| PKT-01 identity and diagnostics foundation | Implementable now | Promote `FontSourceID`, `TypefaceID`, `GlyphStrikeKey`, text route diagnostics, deterministic dumps. | `font/core`, `font/glyph`, `font/gpu-api`. | Surface tests proving stable UUID/value semantics and no object identity in dumps. |
| PKT-02 font source catalog and fallback facts | Implementable now | Source provenance, explicit root scans, duplicate/skipped diagnostics, fallback plan dumps. | `font/core/src/main`, `font/core/src/test`. | Scan fixtures, host-dependent markers, fallback ordering tests. |
| PKT-03 SFNT face and `cmap` contract | Implementable now | Required table diagnostics, TTC index, `cmap` format coverage/refusals. | `font/sfnt/src/main`, `font/sfnt/src/test`. | Malformed required/optional table fixtures and selected face dumps. |
| PKT-04 TrueType `glyf` and variation evidence | Implementable now | Simple/composite outlines, component transforms, variation metadata and metrics dumps. | `font/scaler/src/main`, `font/scaler/src/test`. | Path hashes, bounds, variation delta fixtures. |
| PKT-05 CFF/CFF2 vertical | Dependency-gated | CFF INDEX/dicts/Type 2 operators/CFF2 variation. | `font/scaler/src/main`, `font/scaler/src/test`. | Needs CFF/CFF2 generated fixtures before support claims. |
| PKT-06 Unicode data and script matrix seed | Implementable now | Pinned Unicode version surface, basic segmentation/bidi/script dumps. | `font/text/src/main/.../shaping`, `font/text/src/test`. | Script/bidi/grapheme tests and explicit unsupported-script diagnostics. |
| PKT-07 GSUB/GPOS simple script shaping | Dependency-gated | Latin/Greek/Cyrillic/Hebrew defaults, features, clusters, fallback runs. | `font/text`, `font/sfnt`. | Requires parsed layout table fixtures and feature ordering evidence. |
| PKT-08 complex shaping rows | Dependency-gated | Arabic, Devanagari, Thai, CJK, emoji shaping support/refusals. | `font/text`. | Requires PKT-07 and per-row positive/refusal fixtures. |
| PKT-09 paragraph semantic layout | Partially implementable; full claim gated | Rich styles, bidi visual lines, placeholders, ellipsis, selection, hit testing. | `font/text/src/main/.../paragraph`, `font/text/src/test`. | Layout dumps; full claim waits on shaping/fallback support. |
| PKT-10 A8/SDF glyph artifact planner | Implementable now | Route policy, key preimage, A8/SDF generation, atlas capacity/stale diagnostics. | `font/glyph`, `font/gpu-api`. | Mask/SDF hashes, atlas dump tests, stable `text.glyph.*` refusals. |
| PKT-11 color/bitmap/SVG glyph plans | Plan slices implementable; final support gated | COLR/CPAL plan, PNG glyph plan, SVG subset plan, emoji dispatch. | `font/glyph/src/main/.../color`, `font/glyph/src/test/.../color`. | COLR/PNG/SVG/emoji fixtures and precise unsupported diagnostics. |
| PKT-12 `DrawTextRun` handoff contract | Dependency-gated but contract slice implementable | Dumpable normalized text command, artifact refs, upload/generation facts, no `Sk*`. | `font/gpu-api`, `gpu-renderer/commands`, `gpu-renderer/text`. | Handoff tests and refusal diagnostics; no actual GPU draw claim. |
| PKT-13 validation fixture and evidence harness | Implementable now | Fixture manifest, deterministic dumps, CPU oracle hooks, drift labels. | `font/*/src/test`, `reports/pure-kotlin-text`. | Golden update policy and no external normative oracle. |
| PKT-14 text telemetry and cache counters | Skeleton implementable now | Cache keys, hit/miss/bytes/upload counters, advisory budget records. | `font/*`, `gpu-renderer/telemetry`. | Deterministic telemetry records; no blocking perf gate. |
| PKT-15 GPU A8/SDF route registration | Dependency-gated | Artifact registry, A8/SDF route refusals, upload-before-sample ordering. | `gpu-renderer/text`, `gpu-renderer/resources`, `gpu-renderer/routing`. | Requires stable text artifacts plus GPU ABI route evidence. |
| PKT-16 `:kanvas-skia` facade migration adapters | Dependency-gated | Delegate `SkFontMgr`, `SkTypeface`, `SkShaper`, `SkTextBlob` toward pure Kotlin dumps. | `kanvas-skia/src/main/kotlin/org/skia/foundation`. | Current gates preserved; no implicit complex `drawString`. |

## Parallel Workstreams

| Workstream | Safe Initial Scope | Conflict Risk |
|---|---|---|
| Architecture/modules | Boundary tests, module/package docs, no behavior claims. | Low if it avoids API renames. |
| Font sources/SFNT/scalers | PKT-02/03/04 focused fixtures and diagnostics. | Medium between SFNT and scaler fixtures. |
| OpenType shaping | PKT-06 seed diagnostics before PKT-07/08. | Medium with paragraph tests in `font/text`. |
| Paragraph engine | API/dump improvements and refusal diagnostics. | Medium with shaping in shared test file. |
| Glyph artifacts A8/SDF/atlas | PKT-10 key/dump/refusal hardening. | Medium with `font/gpu-api` handoff names. |
| COLR/PNG/SVG/emoji | PKT-11 plan-only and refusal evidence. | Low if isolated to `glyph/color`. |
| GPU handoff | PKT-12 command/artifact/refusal contracts only. | Low if no renderer route activation. |
| Validation/drift/perf | PKT-13/14 harness and telemetry skeletons. | Low, but avoid modifying business APIs. |
| Migration/refusal retirement | PKT-16 only after supporting evidence exists. | High; defer facade changes until contracts stabilize. |

## Current Coordination Notes

- Initial focused baseline passed:
  `rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test :gpu-renderer:test`.
- The first active implementation slice is PKT-12 contract-only handoff. It
  adds a dumpable `NormalizedDrawCommand.DrawTextRun` surface and stable
  renderer text refusal code constants without claiming any text GPU route.
- `Text/glyph run` remains `DependencyGated` in
  `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md` until pure
  Kotlin text artifacts, route diagnostics, GPU renderer registry support,
  WGSL/binding evidence, and GPU evidence are promoted.

## Checkpoint Evidence

### PKT-01A: Boundary And Identity Contract Audit

Status: implemented and independently reviewed.

Files:

- `reports/pure-kotlin-text/boundary-contracts.json`
- `scripts/validate_pure_kotlin_text_boundary_contracts.py`
- `scripts/test_validate_pure_kotlin_text_boundary_contracts.py`

Evidence:

- `boundary-contracts.json` records the spec 00 package-root ownership map for
  font core, font scaler, shaping, paragraph, glyph artifacts, GPU text handoff,
  and GPU renderer text command/route consumer surfaces.
- The manifest records current identity and boundary contract symbols:
  `FontSourceID`, `TypefaceID`, `GlyphStrikeKey`, `GPUGlyphRunDescriptor`,
  `GPUTextArtifactID`, `GPUTextArtifactGeneration`,
  `GPUTextArtifactReference`, `GPUTextRouteDiagnostics`,
  `NormalizedDrawCommand.DrawTextRun`, and `GPUTextDiagnosticCodes`.
- The validator enforces stable manifest keys, sorted unique package/contract
  IDs, required package roots, required contract symbols, in-repo path guards,
  owner-file existence, and symbol declaration checks including nested symbols.
- Import-boundary scans parse only Kotlin `package` and `import` declarations
  after masking line comments, nested block comments, normal strings, and
  triple-quoted strings. They reject pure Kotlin text/glyph imports of renderer,
  Skia-like, platform, or native/external font engines, and reject GPU renderer
  text command imports of Skia-like, parser/scaler/shaping/paragraph, or native
  font-engine packages.
- Tests cover the happy path, missing required contract symbols, forbidden
  pure-text imports, forbidden GPU renderer font/scaler imports, comment/string
  masking, nested block comments, package comment and import alias/wildcard
  parsing, exact package-segment matching, nested owner-symbol validation,
  hidden support-claim wording, and path traversal rejection.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py
rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py
```

Remaining gate: this is architecture and boundary audit infrastructure only.
It does not add rendering behavior, complete target fixtures, CPU/GPU oracle
evidence, or GPU text route support.

### PKT-02A: Font Source Provenance Evidence Dumps

Status: implemented and independently reviewed.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`

Evidence:

- `FontSourceEvidence` records stable source ID, provenance kind, display name,
  captured-byte SHA-256, host-dependent marker, face count, table tags, and
  source diagnostics.
- `FontSource.provenanceEvidence(...)` builds normalized evidence without
  scanning implicit system paths or parsing font bytes inside `font/core`.
- Public construction validates lowercase SHA-256, one-line diagnostic codes,
  sorted/deduplicated printable ASCII SFNT table tags, and non-negative face
  counts so dumps remain deterministic.
- Tests cover deterministic source dumps, host-dependent markers, direct
  constructor invariant enforcement, invalid tags, invalid hashes, and invalid
  diagnostic codes.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test
```

Remaining gate: this is source evidence and fallback-catalog hardening only. It
does not claim complete SFNT parsing, TTC/OTC support, scaler coverage, or
complete source-discovery behavior.

### PKT-02C: System-Scan Refusal And Provenance Fixture Plan

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `font-source-system-scan` fixture family as `fixture-gated`.
- The row requires deterministic scan-root fixtures from explicit in-repo
  paths, skipped-file diagnostics for unreadable/malformed/unsupported and
  duplicate source candidates, host-dependent markers, fallback order dump
  fields, and no hidden platform font registry or native font API.
- The fixture manifest validator now treats the system-scan row as required,
  so the actionable plan cannot disappear without validation failure.
- Tests assert the row remains present and preserves the
  `no-platform-font-api-claim` non-claim.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is fixture/provenance planning only. It does not claim
complete system font discovery, host fallback parity, implicit root scanning,
SFNT parsing, scaler support, shaping fallback support, or platform/native
font API behavior.

### PKT-03A: SFNT/OpenType Face Evidence Dumps

Status: implemented and independently reviewed.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`

Evidence:

- `OpenTypeFaceData.faceEvidence()` emits deterministic face evidence for
  already-parsed SFNT/OpenType data: selected face index, source/typeface IDs,
  source kind, raw scaler type, sorted table records, raw table byte sizes and
  SHA-256 hashes, preferred `cmap` facts, metric summaries, and parse
  diagnostics.
- `OpenTypeFaceData.faceIndex` is preserved at the end of the public data-class
  constructor so existing positional construction order remains source
  compatible.
- Public evidence constructors validate table tags, hashes, diagnostic tokens,
  sorted table records, and sorted diagnostics so `toCanonicalJson()` remains
  deterministic even for direct construction.
- Tests cover TTC face index evidence, deterministic table ordering, raw table
  hashes, preferred `cmap` facts, metrics, parse diagnostics, constructor-order
  compatibility, and evidence constructor invariants.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test
```

Remaining gate: this is parser evidence hardening only. It does not claim
complete SFNT conformance, TrueType scaler support, CFF/CFF2 support, or
complete font-source coverage.

### PKT-03C: Malformed Table And Format-14 Fixture Plan

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `sfnt-malformed-tables` fixture family as `fixture-gated`.
- The row makes missing required table diagnostics, malformed optional table
  diagnostics, TTC face-index positive/refusal rows, and `cmap` format 14
  positive/refusal expectations explicit before parser promotion.
- The fixture manifest validator now treats the malformed SFNT row as
  required, preserving the actionable fixture plan.
- Tests assert the row remains present and keeps the format 14
  variation-selector gate visible.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is fixture planning only. It does not claim complete SFNT
conformance, complete required-table validation, `cmap` format 14 support,
CFF/CFF2 support, scaler support, shaping support, or platform font behavior.

### PKT-02D: Deterministic System Scan Fixture Goldens

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/font-source/liberation-scan-root.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

Evidence:

- `liberation-scan-root.json` materializes an explicit in-repo scan-root golden
  for `reports/font/fixtures/fonts/liberation` with `hostDependent=false`.
- The golden records deterministic accepted-family order for Liberation Mono,
  Liberation Sans, and Liberation Serif with empty scan diagnostics.
- Provenance and manifest rows attach the expected dump without adding implicit
  filesystem scanning or host font registry behavior.
- The dump index records `font-source-liberation-scan-root` as
  `golden-gated`, and the validator now requires that row.
- Focused `font/core` coverage loads only the three explicit Liberation fixture
  paths and verifies deterministic fallback catalog ordering.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test
rtk git diff --check
```

Remaining gate: this is deterministic fixture and golden evidence only. It does
not claim complete system font discovery, host fallback parity, implicit root
scanning, SFNT parsing coverage, scaler support, shaping fallback support, or
platform/native font API behavior.

### PKT-03D: Malformed SFNT And CMap Format 14 Fixture Pack

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/sfnt/sfnt-cmap-format14-readiness.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

Evidence:

- `sfnt-cmap-format14-readiness.json` records the Liberation core SFNT
  required-table set and keeps format 14 status `fixture-gated`.
- The readiness golden records `font.cmap.format14-fixture-missing` as the
  explicit diagnostic for the current non-claim.
- Provenance and manifest rows attach the expected dump without promoting
  complete SFNT or `cmap` format 14 support.
- The dump index records `sfnt-cmap-format14-readiness` as `golden-gated`, and
  the validator now requires that row.
- Focused `font/sfnt` coverage opens `LiberationSans-Regular.ttf`, verifies the
  required SFNT directory tags through Kanvas SFNT APIs, and asserts the
  format 14 gate remains explicit.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test
rtk git diff --check
```

Remaining gate: this is required-table and format 14 readiness evidence only.
It does not claim complete SFNT conformance, complete required-table
validation, complete `cmap` format 14 support, CFF/CFF2, scaler support,
shaping support, platform font behavior, native oracle behavior, or GPU route
support.

### PKT-04A: TrueType Scaler Evidence Dumps

Status: implemented and independently reviewed.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`

Evidence:

- `ParsedTrueTypeGlyphScaler.scaledGlyphEvidence(...)` and
  `TrueTypeGlyfScaler.scaledGlyphEvidence(...)` emit deterministic current-state
  evidence for TrueType `glyf` glyphs: glyph ID, scaler family, route, bounded
  `loca` range, requested variation axes, normalized variation axes when
  available, outline command dump and SHA-256 hash, conservative bounds,
  metrics, and stable diagnostics.
- Evidence constructors validate stable diagnostic tokens, sorted variation
  coordinates, SHA-256 hashes, bounded `loca` ranges, and deterministic
  diagnostic ordering.
- Current variation gaps are visible instead of silent: partial `gvar` point
  sets that require IUP emit `truetype.gvar-iup-unavailable`, invalid
  requested axes/non-finite coordinates emit stable variation diagnostics, and
  parsed-but-unapplied `avar` maps emit `truetype.avar-unapplied`.
- Composite point-matching and recursion-depth refusals now carry stable
  `font.outline-format-unsupported` diagnostics in the evidence path.
- Tests cover deterministic dumps and hashes, sorted requested axes, normalized
  `gvar` facts, IUP-gap diagnostics, invalid requested axis diagnostics,
  non-finite requested coordinate diagnostics, `avar` unapplied diagnostics,
  and absence of object-identity/`Sk*` tokens in dumps.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test
```

Remaining gate: this is current TrueType `glyf` evidence hardening only. It
does not claim complete CFF/CFF2 support, full IUP interpolation, phantom-point
metrics, `avar` application, HVAR/VVAR/MVAR support, complete variable-font
support, native engine parity, or pixel-perfect hinting.


### PKT-04C: TrueType Variation Fixture Goldens

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/fonts/scaler/RobotoFlex-Variable.ttf`
- `reports/font/fixtures/licenses/roboto-flex-OFL-1.1.txt`
- `reports/font/fixtures/expected/scaler/truetype-variation-readiness.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

Evidence:

- Vendored official Roboto Flex variable TrueType bytes from
  `googlefonts/roboto-flex` main with SIL-OFL-1.1 license evidence and recorded
  GitHub blob provenance `0abe2ee29292f1b39f59103d069feda87cde585e`.
- `truetype-variation-readiness.json` records PKT-04C readiness requirements for
  IUP, phantom-point/advance delta, `avar`, HVAR/VVAR/MVAR metric-refusal,
  normalized-coordinate, path-hash, bounds, and variation-diagnostic evidence.
- The provenance index records fixture ID `scaler-roboto-flex-variable`,
  SHA-256, size, license path, expected dump, and non-claims.
- The fixture manifest and dump index attach the golden as coordination evidence
  without promoting full variable-font support.
- Focused scaler coverage opens the vendored font bytes, asserts the readiness
  dump and non-claims, and existing variation diagnostics/refusals remain
  explicit.
- Local `Stroking.ttf`, `Stroking.otf`, and `Variable.ttf` stand-ins were
  rejected because `skia-integration-tests/src/test/resources/fonts/Stroking_VARIABLE_PROVENANCE.md`
  does not name an accepted license.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:scaler:test
rtk git diff --check
```

Remaining gate: this is fixture readiness and refusal/golden evidence only. It
claims no full variable-font support, no complete target support, no native
scaler oracle, no hinting VM, no HVAR/VVAR/MVAR implementation support, and no
GPU text route support.

### PKT-05B: CFF INDEX/DICT Fixture Pack And Refusal Goldens

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/fonts/scaler/SourceSerif4-Regular.otf`
- `reports/font/fixtures/licenses/source-serif-OFL-1.1.txt`
- `reports/font/fixtures/expected/scaler/cff-cff2-readiness.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`

Evidence:

- Vendored official Source Serif 4.005R desktop CFF OTF bytes from the Adobe
  release zip with SIL-OFL-1.1 license evidence, extracting only
  `source-serif-4.005_Desktop/OTF/SourceSerif4-Regular.otf`.
- `cff-cff2-readiness.json` records PKT-05B readiness requirements for CFF
  INDEX/dict rows, Type 2 line/curve/flex/endchar/width expectations,
  local/global subroutines, malformed INDEX/dict/bounds/stack/operator
  refusals, and CFF2 blend/vsindex/variation-store rows.
- The provenance index records fixture ID `scaler-source-serif-cff`, SHA-256,
  size, license path, expected dump, and CFF/CFF2 non-claims.
- The fixture manifest and dump index attach the golden as coordination evidence
  without promoting CFF rendering or CFF2 variation support.
- Focused scaler coverage opens the vendored OTF bytes, asserts the readiness
  dump and non-claims, and existing CFF/CFF2 Type 2 charstring refusals remain
  explicit.
- Local `Stroking.ttf`, `Stroking.otf`, and `Variable.ttf` stand-ins were
  rejected because `skia-integration-tests/src/test/resources/fonts/Stroking_VARIABLE_PROVENANCE.md`
  does not name an accepted license.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:scaler:test
rtk git diff --check
```

Remaining gate: this is fixture readiness and refusal/golden evidence only. It
claims no CFF rendering support, no CFF2 variation support, no Type 2
interpreter support, no complete target support, no native scaler oracle, and no
GPU text route support.

### PKT-06A: Stable Shaping Diagnostic Families

Status: implemented and independently reviewed.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`

Evidence:

- Current missing fallback, unsupported feature, and cluster invariant shaping
  diagnostics map onto stable spec reason-code families:
  `text.shaping.fallback-missing`, `text.shaping.feature-unsupported`, and
  `text.shaping.cluster-invariant-failed`.
- Existing emission-path tests assert the codes produced for missing glyphs,
  unresolved fallback runs, unapplied OpenType pair positioning, and
  conflicting fallback typefaces inside a shaping cluster.
- Public KDoc documents that missing glyph and unresolved fallback diagnostics
  are semantic aliases of the same stable `text.shaping.fallback-missing`
  family, not distinct stable leaf codes.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test
```

Remaining gate: this is diagnostic-family hardening only. It does not claim
complete GSUB/GPOS coverage, full required script matrix support, or complete
pure Kotlin shaping conformance.

### PKT-09A: Paragraph Semantic Layout Dumps And Refusals

Status: implemented and independently reviewed.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/paragraph/ParagraphTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`

Evidence:

- `ParagraphLayoutResult.dump()` serializes current paragraph input and layout
  facts: text, style ranges, placeholders, max width, line ranges, metrics,
  text boxes, glyph-run counts, overflow flags, layout-refusal state, and
  diagnostics.
- `BasicParagraphLayoutEngine` now returns structured refusal diagnostics for
  invalid max-width constraints, negative `maxLines`, non-finite `lineHeight`,
  and max-lines ellipsis requests that overflow while ellipsis insertion remains
  unsupported.
- Shaping diagnostics produced for shaped lines are merged into paragraph
  diagnostics so layout dumps do not hide missing glyph/fallback or feature
  diagnostics from the shaping layer.
- Tests cover deterministic dumps, absence of object-identity/`Sk*` tokens,
  max-line ellipsis refusal, invalid numeric/style refusals, and shaping
  diagnostic propagation.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test
```

Remaining gate: this is current-state semantic dump and refusal hardening only.
It does not claim full rich text, full bidi visual ordering, complete
selection/hit testing, complete ellipsis insertion, or Skia Paragraph parity.

### PKT-10A: Glyph Strike-Key Preimage And Route Diagnostic Dumps

Status: implemented and independently reviewed.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`

Evidence:

- `GlyphStrikeKey.canonicalPreimage(glyphId)` serializes deterministic cache
  facts for typeface, glyph ID, size, scale, transform bucket, subpixel bucket,
  route, mask format, edging, variation coordinates, palette identity, SDF
  facts, Unicode data version, and renderer version.
- `GlyphStrikeKey.preimageSha256(glyphId)` produces compact SHA-256 evidence
  from the canonical preimage.
- `InMemoryGlyphCache` now separates cache entries by the new rendering facts
  so route, mask format, palette, Unicode version, SDF, and renderer-version
  differences cannot collide with otherwise identical strikes.
- `GlyphRouteDiagnostic` and diagnostic lists have canonical JSON dumps and
  SHA-256 hashes for route evidence.
- Tests cover stable field order, variation-coordinate ordering, route/palette
  cache separation, route diagnostic dumps, and preservation of distinct
  `Float` values in preimages.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test
```

Remaining gate: this is key/dump hardening only. It does not claim complete
A8/SDF artifact generation, atlas lifecycle support, color glyph support, or
GPU text-route promotion.

### PKT-11A: Color Glyph Planning Evidence Dumps

Status: implemented and independently reviewed.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`

Evidence:

- `EmojiGlyphDispatch.toCanonicalJson()` and
  `ColorGlyphPlanningResult.toCanonicalJson()` emit deterministic planning-only
  evidence for current color glyph route decisions: stable route order,
  selected routes, outline fallback facts, diagnostics, and SHA-256 hashes.
- `ColorGlyphDiagnostic` now carries stable `code`, `detail`, and `severity`
  facts for route diagnostics while preserving a JVM overload for the previous
  four-argument constructor shape.
- Dispatch diagnostics distinguish unavailable color glyph routes, bitmap
  strike unavailability, selected routes, lower-preference skipped routes, and
  full missing-route refusals with stable spec-family codes including
  `text.emoji.color-glyph-unavailable`,
  `text.bitmap.strike-unavailable`, and
  `text.emoji.fallback-unavailable`.
- Dump hashes are computed from the current canonical body so mutable input
  lists cannot leave a stale cached hash after mutation.
- Tests cover deterministic route order, selected route ordering, outline
  fallback facts, stable diagnostic code/detail/severity values, full missing
  route refusal evidence, hash/body consistency after mutable-list mutation,
  JVM constructor compatibility, and absence of object-identity/`Sk*`/native
  engine/host-path/GPU-handle tokens in dumps.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk ./gradlew --no-daemon :font:glyph:test
```

Remaining gate: this is planning and diagnostic evidence only. It does not
claim complete COLRv1 rendering, complete PNG bitmap glyph routing, complete
SVG-in-OpenType rendering, complete emoji sequence shaping, GPU color glyph
support, or native/platform fallback behavior.

### PKT-12A: GPU Renderer `DrawTextRun` Handoff Surface

Status: implemented and independently reviewed.

Files:

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextCommandHandoffTest.kt`

Evidence:

- `NormalizedDrawCommand.DrawTextRun` carries dumpable text layout/run IDs,
  glyph run descriptor refs, typed artifact refs, artifact key hashes,
  generation tokens, upload dependency facts, route diagnostics, and captured
  draw state.
- `GPUTextDiagnosticCodes.all` records the current `unsupported.text.*`
  refusal names from the GPU text target.
- Tests assert no `org.skia.*` or `Sk*` field types leak into `DrawTextRun`.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.text.GPUTextCommandHandoffTest
rtk ./gradlew --no-daemon :gpu-renderer:test
```

Remaining gate: no GPU text route is promoted. Artifact registry, route
selection, WGSL/binding evidence, and GPU evidence remain dependency-gated.

### PKT-12B: Text-Stack Typed Artifact References

Status: implemented by implementation agent and independently reviewed.

Files:

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactsSurfaceTest.kt`

Evidence:

- `GPUTextArtifactReference` records typed artifact plan name, artifact ID,
  generation, content fingerprint, and source label.
- `TextGPUArtifactBundle.artifactReferences()` emits references in stable
  category order: `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`,
  `GlyphUploadPlan`, `OutlineGlyphPlan`, `ColorGlyphPlan`,
  `BitmapGlyphPlan`, `SVGGlyphPlan`.
- Tests assert deterministic refs, absence of renderer/GPU/`Sk*` handle
  tokens, and that refs are richer than `contentFingerprint` alone.
- Independent review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test
rtk ./gradlew --no-daemon :font:gpu-api:test :gpu-renderer:test
```

Remaining gate: target specs still separate `contentFingerprint` from compact
artifact key/hash facts more explicitly than the current value object. This is
tracked as future hardening before GPU route promotion, not a current support
claim.

### PKT-13A: Validation Fixture And Evidence Manifest

Status: implemented and independently reviewed.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`

Evidence:

- `fixture-evidence-manifest.json` records the complete target fixture-family
  inventory required by spec 07: font source/SFNT, TrueType scaler, CFF/CFF2
  scaler, shaping scripts, paragraph, color glyphs, PNG bitmap glyphs, SVG
  glyphs, emoji, A8/SDF artifacts, and GPU handoff.
- The manifest includes the exact dashboard classifications from spec 07 while
  keeping every row below `target-supported`; current rows remain narrower
  evidence references with explicit remaining gates.
- Each family records target spec path/section, current evidence paths,
  required evidence gates, validation or fixture commands, and non-claims.
- `validate_pure_kotlin_text_fixture_manifest.py` enforces stable key order,
  known classifications, required families, sorted unique family IDs, existing
  in-repo evidence/spec paths, non-empty gates for gated rows, no
  `target-supported` rows, and no normative external-engine terms outside
  non-claims or drift-only rows.
- Tests cover the happy path plus hidden support-claim rejection, missing-gate
  rejection, normative external-engine oracle rejection, and relative path
  traversal outside the project root.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is validation infrastructure and fixture-inventory
coordination only. It does not add new text implementation behavior, generate
complete target fixtures, provide CPU oracle artifacts, promote external drift
comparisons to normative status, or claim GPU text support.

### PKT-14A: Text Artifact Telemetry Snapshot

Status: implemented and independently reviewed.

Files:

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextTelemetry.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextTelemetrySurfaceTest.kt`

Evidence:

- `TextGPUArtifactBundle.telemetrySnapshot(...)` emits deterministic advisory
  telemetry from typed GPU text artifact bundles without adding renderer state
  or runtime upload claims.
- `GPUTextTelemetryCounters` records artifact reference count, upload plan
  count, CPU-side upload bytes/ranges, glyph upload plans, glyph count,
  A8/SDF atlas counts, outline/color/bitmap/SVG plan counts, diagnostic count,
  and refusal-required state.
- CPU-side upload plan telemetry is separated from optional caller-supplied
  future GPU upload facts; empty GPU upload facts mean no GPU upload evidence
  was supplied.
- Caller-supplied cache telemetry preserves cache name, key preimage, hit/miss
  and eviction counts, resident bytes, and generation token without inventing
  cache observations.
- Advisory budget records reject hidden blocking performance gates, and sample
  metadata rejects release-gate promotion in this contract.
- Canonical JSON dumps have stable field order and deterministic sorting for
  caller-supplied cache, budget, and GPU upload rows.
- Tests cover deterministic snapshots and counts, empty cache/budget/GPU facts,
  full-field sorting, upload range overflow rejection, inconsistent glyph
  upload-plan attribution rejection, invalid negative/blank values, blocking
  gate rejection, and absence of renderer/native/`Sk*` handle tokens in dumps.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests org.graphiks.kanvas.glyph.gpu.GPUTextTelemetrySurfaceTest
rtk ./gradlew --no-daemon :font:gpu-api:test
```

Remaining gate: this is telemetry scaffolding only. It does not measure actual
runtime performance, promote indicative budgets into release gates, synthesize
GPU upload evidence, or claim GPU text rendering support.
