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
| PKT-05 CFF/CFF2 vertical | Tracked-gap; generated fixture parser/scaler/operator/table/variation-store slices implemented | CFF INDEX/dicts/Type 2 operators/CFF2 variation. | `font/scaler/src/main`, `font/scaler/src/test`. | Generated CFF/CFF2 tables now reach public scalers with deterministic table evidence, malformed-table refusals, and minimal CFF2 VariationStore region lookup; complete support still needs broader real-font corpus coverage. |
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

### KFONT-M0-001/M0-002: Pure Kotlin Font CI Foundation

Status: done; merged and freshly revalidated for closeout.

Files:

- `.github/workflows/test.yml`
- `scripts/validate_pure_kotlin_text_ci.py`
- `scripts/test_validate_pure_kotlin_text_ci.py`
- `reports/pure-kotlin-text/font-ci-lane.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m0-001-002-ci-foundation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-001-wire-pure-kotlin-font-modules-into-ci.md`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-002-add-pure-kotlin-text-specs-to-ci-trigger-paths.md`

Evidence:

- Workflow job `pure_kotlin_font_foundation` names the
  `pure-kotlin-font-foundation` lane and runs on `ubuntu-latest`.
- The lane resolves an explicit PR, push, or default-branch merge-base before
  running `git diff --check` on `.upstream/specs/pure-kotlin-text` and
  `reports/pure-kotlin-text`.
- The lane validates `font-ci-lane.json`, runs the boundary validator, and
  invokes `:font:core:test`, `:font:sfnt:test`, `:font:scaler:test`,
  `:font:text:test`, `:font:glyph:test`, and `:font:gpu-api:test`.
- `scripts/test_validate_pure_kotlin_text_ci.py` rejects removed, disabled, or
  comment-only diff hygiene, CI validator, and boundary validator steps.
- Path filters include `.upstream/specs/pure-kotlin-text/**`, `font/**`,
  `reports/pure-kotlin-text/**`, and the pure Kotlin text CI/boundary
  validator scripts.
- Trigger samples cover one target spec file, one ticket file, and one
  archived-only migration path that remains inactive.
- Missing module policy emits `font.ci.module-missing` as `tracked-gap` with
  `claimPromotionAllowed=false`.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py
rtk python3 scripts/validate_pure_kotlin_text_ci.py
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py
rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py
```

Remaining gate: this is validation infrastructure only. It does not claim
parser, scaler, shaping, paragraph, glyph artifact, fallback, rendering, native
font engine, or GPU text support.

### KFONT-M0-003: Module And Package Boundary Validation

Status: done; merged and freshly revalidated for closeout.

Files:

- `reports/pure-kotlin-text/boundary-contracts.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m0-003-boundary-diagnostics.md`
- `scripts/validate_pure_kotlin_text_boundary_contracts.py`
- `scripts/test_validate_pure_kotlin_text_boundary_contracts.py`
- `.github/workflows/test.yml`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-003-freeze-module-package-layout-for-the-pure-kotlin-font-core.md`

Evidence:

- `boundary-contracts.json` records the owner package roots for font core,
  scaler, shaping, paragraph, glyph artifacts, GPU handoff, and renderer text
  command/route consumers.
- Boundary validation rejects pure Kotlin font/text/glyph imports of renderer,
  Skia-like, platform, native, HarfBuzz, FreeType, Fontations, CoreText,
  DirectWrite, or fontconfig APIs.
- Boundary diagnostics now use stable `font.architecture.*` codes. The
  synthetic snapshot asserts `font.architecture.skia-api-leak` for a `SkFont`
  import and `font.architecture.gpu-backedge` for a pure Kotlin import of
  `org.graphiks.kanvas.gpu.renderer.text.GPUTextRouteDiagnostics`.
- GPU renderer text boundary validation rejects Skia-like, parser/scaler,
  shaping, paragraph, or native font-engine imports.
- The M0 CI lane now invokes the boundary validator before the six font module
  test tasks.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py
rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py
```

Remaining gate: this is package-boundary and architecture evidence only. It
does not add font behavior, rendering behavior, or GPU text route support.

### KFONT-M0-004: Stable Diagnostic Taxonomy

Status: done; merged and freshly revalidated for closeout.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontDiagnosticTaxonomyTest.kt`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m0-004-diagnostic-taxonomy.md`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-004-introduce-stable-diagnostic-taxonomy.md`

Evidence:

- `FontDiagnosticTaxonomy` defines accepted namespace families, stable
  diagnostic rows, required fields, severity, route, and claim impact facts.
- `font-diagnostic-taxonomy.json` includes sample diagnostics for source,
  SFNT, scaler, shaping, and GPU/text route refusal cases.
- Legacy diagnostics `font.native-engine-unavailable`,
  `font.bitmap-strike-unavailable`, and
  `font.emoji-sequence-shaping-unsupported` map to target classifications while
  keeping gates open.
- Generic `font missing` is rejected as `tracked-gap` with reason
  `generic-or-unknown-diagnostic`.
- Every row keeps `claimPromotionAllowed=false`.

Validation:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:core:test
```

Remaining gate: this is taxonomy evidence only. It does not implement parser,
scaler, shaping, paragraph, glyph artifact, renderer, GPU, fixture, CPU oracle,
or GPU evidence support. Legacy gates remain open until later evidence retires
them explicitly.

### KFONT-M0-005: Dashboard Claim Classification

Status: done; merged and freshly revalidated for closeout.

Files:

- `build.gradle.kts`
- `.github/workflows/test.yml`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/font-ci-lane.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m0-005-dashboard-claim-classification.md`
- `scripts/validate_pure_kotlin_text_claim_dashboard.py`
- `scripts/test_validate_pure_kotlin_text_claim_dashboard.py`
- `scripts/validate_pure_kotlin_text_ci.py`
- `scripts/test_validate_pure_kotlin_text_ci.py`
- `.upstream/specs/pure-kotlin-text/tickets/M0-claims-ci-diagnostics/KFONT-M0-005-harden-dashboard-claim-classification.md`

Evidence:

- `font-claim-dashboard.json` records the eight claim classifications,
  classification rules, required evidence kinds, split surface rows for
  `outline/path`, `simple-latin atlas`, `complex shaping`, `fallback`,
  `emoji/color`, `SDF`, and `LCD`, and keeps `claimPromotionAllowed=false`.
- Negative generic labels `font missing`, `text works`, and `emoji supported`
  have stable `font.claim.*` or `text.claim.*` diagnostics and remain
  `tracked-gap`.
- Legacy gates `coloremoji_blendmodes`, `scaledemoji`,
  `scaledemoji_rendering`, `dftext`, `fontations`,
  `fontations_ft_compare`, and `pdf_never_embed` remain visible, open, and
  non-promotable.
- The validator rejects missing taxonomy values, generic labels in claim rows,
  GPU claims without GPU artifacts, missing legacy gates, and missing Gradle
  wiring.
- `validatePureKotlinTextClaimDashboard` is wired into
  `pipelineSceneDashboardGate` and `pipelinePmBundle`; CI path filters and the
  pure Kotlin font foundation job invoke the new dashboard validator.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py
rtk python3 scripts/validate_pure_kotlin_text_ci.py
rtk ./gradlew --no-daemon validatePureKotlinTextClaimDashboard
rtk git diff --check
```

Remaining gate: this is claim-dashboard validation infrastructure only. It
does not add rendering, shaping, fallback, SDF, color, emoji, LCD, or GPU text
support. All KFONT-M0-005 legacy gates remain open until later target evidence
retires them explicitly.

### KFONT-M1-003: Deterministic Source/Typeface Identity Dumps

Status: done; merged and freshly revalidated for closeout.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontIdentityDumpTest.kt`
- `reports/pure-kotlin-text/font-source.json`
- `reports/pure-kotlin-text/typeface-id.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m1-003-identity-dumps.md`
- `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/KFONT-M1-003-add-deterministic-source-typeface-dumps.md`

Evidence:

- `FontIdentityDumpWriter` emits the checked-in `font-source.json` and
  `typeface-id.json` goldens through canonical JSON wrappers.
- `FontIdentityDumpBundle` compares `font-source.json`, `typeface-id.json`,
  and `identity-dump-schema.json` byte-for-byte as UTF-8 strings.
- `FontIdentityDumpDeterminismResult` records matching state, both SHA-256
  digests, and exact differing file labels.
- `FontIdentityDumpSchema` records schema version `1`, required fields, output
  file order, sorted table tags, sorted variation coordinates, sorted palette
  overrides, host-dependent markers, diagnostics, and
  `claimPromotionAllowed=false`.
- The host-dependent source row remains visible through
  `system-scanned-host-dependent`, `hostDependent`, and
  `font.source.host-dependent` without host temp paths.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*IdentityDump*'
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*IdentityDump*'
rtk ./gradlew --no-daemon :font:core:test
rtk git diff --check
```

Remaining gate: this is deterministic dump evidence only. It does not add font
fixture bytes, glyph outlines, shaping, glyph scaling/cache, fallback
completion, renderer backend behavior, GPU evidence, or support-claim
promotion.

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

### PKT-02B: Fallback Decision Trace Dumps

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `CatalogFontResolver.trace(...)` records one deterministic
  `FallbackDecisionTrace` per Unicode code point while reusing the same
  catalog, style-distance, policy ordering, and coverage decisions as
  `resolve(...)`.
- Trace dumps include UTF-16 offsets, stable `U+` code point evidence,
  requested families, actual candidate family order, selected family,
  selected `TypefaceID`, coverage state, and stable refusal diagnostics without
  object identity, `Sk*` tokens, platform font APIs, or GPU handles.
- Tests cover a covered fallback selection, candidate-family ordering,
  `.notdef` routing with `font.fallback-glyph-unavailable`, empty-catalog
  refusal with `font.fallback-family-unavailable`, and preservation of existing
  `resolve(...)` behavior.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontCoreSurfaceTest.tracesFallbackDecisionWithStableCandidateOrderAndSelectedFace
rtk ./gradlew --no-daemon :font:core:test
```

Remaining gate: this is catalog-level fallback trace evidence only. It does
not claim a complete bundled fallback catalog, implicit system font scanning,
host font normative behavior, variable-axis-aware fallback, cluster-safe
fallback segmentation, complete shaping fallback, parser-backed glyph coverage,
or GPU text-route support.
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
### PKT-02D: Deterministic System Scan Fixture Goldens

Status: implemented and independently reviewed.

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
### PKT-02E: Font Scan Skipped-File Diagnostic Fixture

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `scripts/validate_pure_kotlin_text_font_fixtures.py`
- `scripts/test_validate_pure_kotlin_text_font_fixtures.py`

Evidence:

- `FontFileScanner.scanRoots(...)` now accepts `reportSkippedFiles = true`
  for explicit fixture scans that need skipped-file diagnostics without making
  default scans noisy.
- Unsupported regular files under explicit roots emit stable
  `font.scan.file-skipped` diagnostics with normalized root/path facts and a
  quoted deterministic message.
- Tests cover a generated unsupported file fixture and assert deterministic
  diagnostic code, root/path facts, and dump text.
- The font fixture inventory marks `system-scan-skipped-file-diagnostic` as
  current refusal evidence, and the manifest removes that remaining
  font-source/SFNT fixture gate.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontCoreSurfaceTest.scanRootsCanReportSkippedUnsupportedFilesDeterministically
```

Remaining gate: this is explicit scan skipped-file evidence only. It does not
claim implicit system font scanning, bundled fallback catalog completeness,
parser-backed glyph coverage, shaping fallback completeness, or GPU text-route
support.

### KFONT-M7-001: Add bundled deterministic font catalog

Status: implemented as a bounded review slice.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCatalogTest.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontDiagnosticTaxonomyTest.kt`
- `reports/pure-kotlin-text/font-catalog.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m7-001-font-catalog.md`
- `.upstream/specs/pure-kotlin-text/tickets/M7-fallback-system-fonts/KFONT-M7-001-add-bundled-deterministic-font-catalog.md`
- `.upstream/specs/pure-kotlin-text/tickets/M7-fallback-system-fonts/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `BundledFontCatalogBuilder` emits deterministic `font-catalog.json` output
  for repo-owned bundled fixtures without consulting host font directories.
- The checked-in dump records bundled source/typeface identities, content
  hashes, family/style/generic facts, script-coverage labels, locale hints,
  outline/scaler facts, variation-axis facts, and provenance/license metadata
  for Liberation Sans, Source Serif 4, and Roboto Flex.
- `FontCatalogTest` asserts byte-identical output across repeated loads and
  shuffled input order, duplicate-face refusal, provenance-missing refusal,
  required-table refusal passthrough, outline-format refusal passthrough, and
  exclusion of host-dependent rows.
- The diagnostic taxonomy now reserves `font.catalog.duplicate-face` and
  `font.catalog.provenance-missing` under a dedicated `font.catalog`
  namespace.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontCatalog*'
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:core:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this is a bounded deterministic catalog slice only. It does
not yet provide bundled Hebrew/Arabic, Devanagari/Thai, CJK, or emoji-capable
catalog rows, does not include the ticket's requested checked-in
duplicate-family conflict golden, and does not claim fallback support,
cluster-safe fallback segmentation, shaping support, platform font parity, or
GPU text-route support.
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
### PKT-03B: Bounded SFNT Table Directory Diagnostics

Status: done; merged, independently reviewed, and freshly revalidated for KFONT-M2-002 closeout.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTParserEntryPointTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/sfnt-directory.json`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m2-002-bounded-directory-diagnostics.md`

Evidence:

- `SFNTTableDirectoryValidator.validate(...)` inspects already-read directory
  records against an explicit bounded `sourceLength` and caller-provided
  required table set without parsing payloads, repairing offsets, or invoking
  host/native font engines.
- `SFNTTableDirectoryDiagnostic.dump()` emits stable single-line evidence with
  diagnostic code, table tag, offset, length, source length, and message.
- Tests cover deterministic diagnostics for duplicate tags, overlapping
  ranges, out-of-bounds ranges, missing required tables, and zero-length
  required tables using stable reason-code families such as
  `font.sfnt.table-out-of-bounds`, `font.sfnt.table-duplicate`,
  `font.sfnt.table-overlap`, and `font.sfnt.required-table-missing`.
- `SFNTParseRequest.requiredTables` now flows through `DefaultSFNTParser`, and
  `sfnt-directory.json` includes a generated malformed directory fixture row
  with duplicate, out-of-bounds, overlap, and missing-required-table evidence.
- Malformed optional table diagnostics are classified as
  `font.sfnt.optional-table-malformed` while remaining tracked-gap, with
  `sfnt-directory.json` carrying the generated `fvar` malformed fixture,
  source SHA-256, and face diagnostic.
- `font-fixture-inventory.json` records the optional malformed source SHA-256
  and intended diagnostic without promoting complete malformed-suite support.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon --rerun-tasks :font:sfnt:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

Remaining gate: this is bounded directory diagnostic evidence only. It does not
claim full SFNT parser conformance, malformed fixture suite completion,
automatic parser refusal policy integration, complete `cmap` coverage, scaler
support, shaping support, color glyph support, search-field formula validation,
checksum verification, or GPU text-route support.
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
### PKT-03D: Malformed SFNT And CMap Format 14 Fixture Pack

Status: done; independently reviewed and freshly validated.

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
### PKT-03E: SFNT Directory Diagnostics In Face Evidence

Status: done; merged, independently reviewed, and freshly revalidated for KFONT-M2-002 closeout.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `OpenTypeFaceData.faceEvidence(requiredTables = ...)` now threads bounded
  `SFNTTableDirectoryValidator` diagnostics into canonical face evidence under
  `directoryDiagnostics`.
- `SFNTTableDirectoryDiagnostic.toCanonicalJson()` preserves stable code, tag,
  offset, length, source length, and message facts for selected face dumps.
- Tests cover missing required table, zero-length required table, duplicate
  table tag, out-of-bounds table range, stable dump text, and the canonical
  `directoryDiagnostics` JSON field.
- Existing deterministic face-evidence JSON snapshots now include an empty
  `directoryDiagnostics` list when no bounded directory issue is supplied.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.openTypeFaceEvidenceIncludesBoundedDirectoryDiagnostics
rtk ./gradlew --no-daemon :font:sfnt:test --rerun-tasks
```

Remaining gate: this is selected-face directory diagnostic evidence only. It
does not claim full malformed SFNT fixture manifest completion, automatic parse
refusal policy, complete `cmap` coverage, complete TTC/OTC conformance, scaler
support, shaping support, color glyph support, or GPU text-route support.
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
  sets that require IUP emit `truetype.gvar-iup-unavailable`, and invalid
  requested axes/non-finite coordinates emit stable variation diagnostics.
- Composite point-matching and recursion-depth refusals now carry stable
  `font.outline-format-unsupported` diagnostics in the evidence path.
- Tests cover deterministic dumps and hashes, sorted requested axes, normalized
  `gvar` facts, IUP-gap diagnostics, invalid requested axis diagnostics,
  non-finite requested coordinate diagnostics, and absence of object-identity/
  `Sk*` tokens in dumps.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test
```

Remaining gate: this is current TrueType `glyf` evidence hardening only. It
does not claim complete CFF/CFF2 support, full IUP interpolation, phantom-point
metrics, HVAR/VVAR/MVAR support, complete variable-font support, native engine
parity, or pixel-perfect hinting.
### PKT-04B: TrueType Composite Component Trace Evidence

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `ScaledTrueTypeGlyphEvidence` now includes deterministic
  `compositeComponents` facts for decoded TrueType composite glyph component
  edges: recursion depth, parent glyph, component index, referenced glyph,
  raw flags, argument kind/values, and affine transform coefficients.
- Component trace collection reuses the already-decoded pure Kotlin `glyf`
  component records and the existing composite recursion depth cap; it does
  not parse CFF/CFF2, execute TrueType instructions, perform point matching, or
  add native/font-engine oracle behavior.
- Tests cover root and nested composite component trace ordering and assert the
  canonical evidence JSON exposes `compositeComponents`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.parsedTrueTypeGlyphEvidenceIncludesCompositeComponentTrace
rtk ./gradlew --no-daemon :font:scaler:test --rerun-tasks
```

Remaining gate: this is composite trace evidence only. It does not claim full
composite transform fixture coverage, complete point-matching support, full IUP
interpolation, phantom-point or advance-delta support, vertical metric
coverage, malformed glyph isolation suite completion, CFF/CFF2 support, A8/SDF
artifact support, or GPU glyph route support.
### PKT-04C: TrueType Variation Fixture Goldens

Status: done; independently reviewed and freshly validated.

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
### PKT-04D: TrueType Avar Coordinate Mapping Fixture

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `TrueTypeGlyfScaler` now applies parsed `avar` segment maps after ordinary
  `fvar` normalization and before `gvar` deltas are requested.
- The remap is deterministic, uses the axis order from parsed variation axes,
  linearly interpolates between declared `avar` segments, and clamps remapped
  coordinates to the normalized variation interval.
- Tests cover a generated one-axis fixture where requested `wght=900.0`
  normalizes to `1.0` and remaps through `avar` to `0.75` without emitting the
  prior `truetype.avar-unapplied` diagnostic.
- The font fixture inventory marks `truetype-avar-coordinate-mapping` as
  current positive evidence; the manifest now leaves only the `gvar` composite
  delta fixture gate for the current TrueType scaler row.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.trueTypeGlyfEvidenceAppliesAvarCoordinateMappingFixture
```

Remaining gate: this is `avar` coordinate mapping evidence only. It does not
claim full IUP interpolation, phantom-point metrics, CFF/CFF2 support, hinting
VM parity, or GPU glyph route support.
### PKT-04E: TrueType Composite Gvar Delta Fixture

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- Tests add a generated composite glyph fixture whose root glyph references a
  simple component glyph with a `gvar` all-point delta record.
- Existing recursive composite outline resolution already passes normalized
  variation coordinates into component glyph resolution, so the component
  point delta is visible in the composite outline commands.
- The fixture inventory marks `truetype-gvar-composite-delta` as current
  positive evidence, and the fixture manifest removes the remaining TrueType
  scaler fixture gate.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.parsedTrueTypeGlyphScalerAppliesGvarDeltasToCompositeComponents
```

Remaining gate: this proves composite component `gvar` delta evidence only. It
does not claim composite glyph-specific variation records, full IUP
interpolation, phantom-point metrics, CFF/CFF2 support, hinting VM parity, or
GPU glyph route support.
### PKT-05A: CFF/CFF2 CharString Fixture Evidence

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `CFFType2CharStringInterpreter` provides a narrow generated-fixture
  interpreter for Type 2/CFF2 charstrings without claiming full CFF table
  parsing or public CFF scaler support.
- Tests generate deterministic charstring bytes for CFF move/line/curve/flex
  evidence, local and global subroutine calls with bounded bias resolution and
  call traces, CFF2 `vsindex`/`blend` variation input, malformed stack refusal,
  and unsupported escaped-operator refusal.
- Stable refusal diagnostics now include `font.cff-stack-malformed` and
  `font.cff-operator-unsupported` evidence with glyph IDs and operator-offset
  messages.
- The font-only fixture inventory marks all five CFF/CFF2 fixture gates as
  current evidence, and the fixture manifest changes the family blocker from
  missing fixtures to tracked parser/scaler integration work.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterBuildsLineCurveAndFlexEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterTracesLocalAndGlobalSubroutines --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2FixtureInterpreterAppliesVsindexBlendEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterReportsStackAndOperatorRefusals
```

Remaining gate at this slice closeout: generated charstring evidence had not
yet been routed through CFF INDEX/top-dict/private-dict parsing or public
`CFFScaler`/`CFF2Scaler` support. `PKT-05C` below closes that routing gap for
generated fixtures only; complete CFF/CFF2 target support remains tracked.
### PKT-05B: CFF INDEX/DICT Fixture Pack And Refusal Goldens

Status: done; independently reviewed and freshly validated.

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
### PKT-05C: CFF/CFF2 Parser And Public Scaler Fixture Routing

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `CFFScaler` now consumes generated `CFF ` table bytes through a bounded parser
  covering header, Name INDEX, Top DICT INDEX, String INDEX, Global Subr INDEX,
  CharStrings INDEX, Private DICT, and local Subr INDEX routing.
- `CFF2Scaler` now consumes generated `CFF2` table bytes through a bounded
  header/top-dict/global-subr/charstrings parser and routes `vsindex`/`blend`
  through the fixture interpreter using face variation-axis tags.
- Public scaler tests prove that generated CFF tables reach outlines, local and
  global subroutines, conservative bounds, and horizontal metrics through
  `CFFScaler`, and that generated CFF2 tables reach blended outlines and
  metrics through `CFF2Scaler`.
- Missing CFF/CFF2 raw table paths keep stable refusals without falling back to
  native engines or hiding unsupported behavior.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffScalerUsesGeneratedCffTableCharstringsSubrsAndMetrics --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScalerUsesGeneratedCff2TableAndVariationBlend
rtk ./gradlew --no-daemon :font:scaler:test --rerun-tasks
```

Remaining gate: this is generated CFF/CFF2 fixture routing only. It does not
claim complete Type 2 operator coverage, real-world CFF font coverage, width
extraction from charstrings, CFF hint-mask metadata policy completion, CFF2
variation-store lookup, variation-adjusted metrics, selected-face CFF/CFF2
provenance dumps, malformed INDEX/dict refusal suite completion, or GPU glyph
route support.
### PKT-05D: CFF Type 2 Operator Width And Hint Metadata Fixtures

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `CFFType2CharStringInterpreter` now covers the remaining generated-fixture
  curve/flex operators: `hhcurveto`, `vvcurveto`, `hvcurveto`, `vhcurveto`,
  `hflex`, `hflex1`, and `flex1`.
- CFF fixture evidence now records optional charstring width, stem hint count,
  and consumed hint-mask byte count in stable canonical dumps.
- `hstem`, `vstem`, `hstemhm`, `vstemhm`, `hintmask`, and `cntrmask` paths
  consume deterministic metadata without affecting normative outline geometry.
- Public `CFFScaler.metrics(...)` now uses the generated charstring width when
  present and falls back to horizontal metrics otherwise.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterCoversRemainingCurveAndFlexOperators --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterRecordsWidthAndHintMaskMetadata --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffScalerUsesGeneratedCffTableCharstringsSubrsAndMetrics
```

Remaining gate: this still covers generated fixtures only. It does not claim
complete real-world CFF font coverage, CFF2 variation-store lookup,
variation-adjusted metrics, selected-face CFF/CFF2 provenance dumps, malformed
INDEX/dict refusal suite completion, broader corpus evidence, or GPU glyph
route support.
### PKT-05E: CFF/CFF2 Table Evidence And Malformed Refusals

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `CFFScaler.tableEvidence()` and `CFF2Scaler.tableEvidence()` expose stable
  selected-table facts for generated fixtures: format, charstring count,
  local/global subroutine counts, private-dict presence, top-dict operator
  names, and CFF2 variation-axis tags.
- `CFFTableEvidence.toCanonicalJson()` serializes those facts in deterministic
  order without object identity, native-engine facts, or host font choices.
- Malformed CFF INDEX bytes and a top dict missing `CharStrings` refuse through
  `FontScalerRefusalException` with stable `font.cff-table-malformed`,
  `cff.table-malformed`, and `table` evidence.
- The font fixture inventory now records positive selected-table provenance
  evidence and malformed INDEX/dict refusal evidence; the fixture manifest
  removes the corresponding CFF/CFF2 remaining gate.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffScalersExposeDeterministicTableEvidenceDumps --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffTableEvidenceRefusesMalformedIndexAndDictDeterministically
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk ./gradlew --no-daemon :font:scaler:test --rerun-tasks
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test --rerun-tasks
```

Remaining gate: this still covers generated fixtures only. It does not claim
complete real-world CFF font coverage, CFF2 variation-store lookup,
variation-adjusted metrics beyond the current single-vsindex face-axis blend
fixture, broader real-font corpus evidence, or GPU glyph route support.
### PKT-05F: CFF2 VariationStore Region Fixture

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `scripts/validate_pure_kotlin_text_font_fixtures.py`
- `scripts/test_validate_pure_kotlin_text_font_fixtures.py`

Evidence:

- `CFF2Scaler` now parses a generated CFF2 top-dict `VariationStore` offset and
  a bounded minimal ItemVariationStore containing one `VariationRegionList`,
  one ItemVariationData region-index list, and F2DOT14 region coordinates.
- `CFFType2CharStringInterpreter` can receive a CFF2 scalar provider so
  `blend` uses variation-region scalars instead of treating face-axis
  coordinates as direct deltas whenever a CFF2 VariationStore is present.
- The focused test proves a generated region with start `0.0`, peak `0.5`,
  and end `1.0` maps requested coordinate `0.25` to scalar `0.5`, producing a
  different outline and `GlyphMetrics.bounds` than the default coordinate.
- Table provenance names the top-dict operator as `cff.dict.variation-store`,
  and the font fixture inventory records `cff2-variation-store-region` as
  current positive evidence.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScalerUsesVariationStoreRegionScalarsForBlendAndMetricsBounds
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk ./gradlew --no-daemon :font:scaler:test --rerun-tasks
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test --rerun-tasks
```

Remaining gate: this is generated CFF2 VariationStore fixture evidence only.
It does not claim complete CFF2 variation support, HVAR/VVAR/MVAR advance
deltas, CID-keyed CFF/CFF2 coverage, broader real-font corpus evidence,
native-scaler parity, or GPU glyph route support.
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
### PKT-06C: Pinned Unicode-Data Generation Contract

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `unicode-data-generation` fixture family as `fixture-gated`.
- The row requires a pinned Unicode data version, source file names and
  checksums for script/bidi/grapheme/default-ignorable inputs, generated dump
  schema rows, and mismatch diagnostics for version, checksum, and schema
  drift before replacing `BasicUnicodeData`.
- The row keeps the JDK Unicode version policy explicit: product behavior must
  not depend on it unless that dependency is diagnosed.
- The fixture manifest validator now treats the Unicode-data generation row as
  required, and tests assert the row keeps the `no-complete-ucd-claim`
  non-claim.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is generation-contract planning only. It does not claim a
complete Unicode Character Database, UAX #9 bidi conformance, UAX #14 line
breaking, UAX #29 segmentation, emoji property coverage, or full script matrix
support.
### PKT-06D: Unicode 16.0 Metadata

Status: done; independently reviewed and freshly validated.

Files:

- `reports/font/fixtures/expected/unicode/unicode-16-source-manifest.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `unicode-16-source-manifest.json` pins Unicode version `16.0.0`, the
  official UCD source URL, the bounded source file list needed for future
  generation, fixture-creation-only download policy, and offline ordinary
  validation policy.
- `fixture-evidence-manifest.json` attaches the expected manifest to the
  `unicode-data-generation` family as current coordination evidence.
- `dump-evidence-index.json` points the existing `unicode-data-seed` producer
  row at the new expected manifest without adding a new required dump ID.
- Focused `font/text` coverage loads the manifest from the project root and
  asserts the pinned Unicode version, offline validation policy, and
  `no-complete-ucd-claim` non-claim.
- The font fixture provenance index was not changed because its current schema
  requires font assets and an accepted font license; applying `SIL-OFL-1.1` or
  inventing a font asset row for Unicode data would be unsafe provenance.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test --tests '*TextStackSurfaceTest*'
rtk git diff --check
```

Remaining gate: this is Unicode metadata and coordination evidence only. It
does not replace `BasicUnicodeData`, add generator checksum coverage, add
runtime mismatch diagnostics, claim a complete Unicode Character Database,
claim UAX #9 bidi conformance, claim UAX #14 line breaking, claim UAX #29
segmentation, claim emoji property coverage, or claim full script matrix
support.

### KFONT-M5-001: Pinned Unicode Data Generation

Status: done with bounded seed evidence and local self-review.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/UnicodeDataGeneration.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/UnicodeDataGenerationTest.kt`
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/`
- `reports/font/fixtures/expected/unicode/unicode-data-manifest.json`
- `reports/font/fixtures/expected/unicode/unicode-data-tables.json`
- `reports/font/fixtures/expected/unicode/unicode-data-version-mismatch-diagnostic.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m5-001-unicode-data-generation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M5-unicode-segmentation-bidi/KFONT-M5-001-add-pinned-unicode-data-generation.md`

Evidence:

- `PinnedUnicodeDataGenerator` accepts only Unicode `16.0.0` checked-in
  extracts, refuses missing or unpinned inputs, and records input SHA-256
  hashes in `unicode-data-manifest.json`.
- The generated bounded table fixture covers sample
  Grapheme_Cluster_Break, Bidi_Class, Script, Script_Extensions, Line_Break,
  General_Category, Default_Ignorable_Code_Point, emoji/Extended_Pictographic,
  and Variation_Selector facts.
- Generated table SHA-256 hashes are recorded in the manifest and are asserted
  against the canonical table JSON preimages in `UnicodeDataGenerationTest`.
- `unicode-data-version-mismatch-diagnostic.json` pins the stable
  `text.shaping.unicode-data-version-mismatch` refusal for a dump expecting a
  different Unicode data version.
- Ordinary validation remains offline and does not download Unicode data.

Validation:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:text:test --tests '*UnicodeData*'
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this is bounded Unicode seed generation evidence only. It does
not claim a complete Unicode Character Database, UAX #9 bidi conformance, UAX
#14 line breaking conformance, UAX #29 grapheme segmentation conformance,
replacement of `BasicUnicodeData`, shaping support promotion, paragraph support
promotion, or any GPU text route.

### KFONT-M5-002: Replace Basic Grapheme Segmenter

Status: done with bounded fixture evidence; independently reviewed.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/GraphemeSegmentation.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/UnicodeDataGeneration.kt`
- `font/text/src/main/resources/org/graphiks/kanvas/text/unicode/16.0.0/`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/GraphemeSegmentationTest.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/UnicodeDataGenerationTest.kt`
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/`
- `reports/font/fixtures/expected/unicode/unicode-data-manifest.json`
- `reports/font/fixtures/expected/unicode/unicode-data-tables.json`
- `reports/font/fixtures/expected/unicode/unicode-segments.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m5-002-grapheme-segmentation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M5-unicode-segmentation-bidi/KFONT-M5-002-replace-basic-grapheme-segmenter.md`

Evidence:

- The pinned Unicode 16.0 source extracts were expanded only with reviewed rows
  needed by the KFONT-M5-002 fixture matrix: CR/LF/control, Hangul
  `L/V/T/LV/LVT`, `Extend`, `SpacingMark`, `Prepend`, ZWJ,
  Extended_Pictographic, regional indicators, emoji modifiers, variation
  selectors, and bounded Indic_Conjunct_Break rows.
- `GraphemeClusterer` reads segmentation properties from `UnicodeDataSet` and
  emits UTF-16 ranges, code point ranges, cluster level, source text hash,
  Unicode version, per-boundary GB rule IDs, and stable diagnostics.
- `BasicTextSegmenter()` now delegates to the pinned grapheme segmenter by
  default, using module-packaged Unicode 16.0 source extracts instead of
  test-only report paths.
- `unicode-segments.json` covers the six required fixture text files plus
  `grapheme-crlf-control.txt` and `grapheme-prepend.txt`; it
  records `text.unicode.invalid-scalar` and
  `text.unicode.cluster-boundary-invalid` for the isolated surrogate refusal.
- Grapheme tests assert `text.shaping.unicode-data-version-mismatch`,
  `text.shaping.cluster-invariant-failed`, `text.unicode.invalid-scalar`,
  `text.unicode.cluster-boundary-invalid`, and
  `text.unicode.grapheme-rule-unsupported`, with explicit CR/LF/control and
  Prepend fixture coverage.
- The evidence report states that boundaries are not derived from the JDK
  Unicode version and that ordinary validation stays offline.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*Grapheme*'
rtk ./gradlew --no-daemon :font:text:test --tests '*Grapheme*' --rerun-tasks
rtk ./gradlew --no-daemon :font:text:test --rerun-tasks
rtk ./gradlew --no-daemon --rerun-tasks :font:text:test --tests '*UnicodeData*'
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Review:

- Independent spec review verdict: `ACCEPT` after default-segmenter and
  refusal-diagnostic remediation.
- Independent code-quality review verdict: `Ready to merge: Yes` after the
  runtime resource loader and CR/LF/control plus Prepend evidence fixes.

Remaining gate: none for KFONT-M5-002 closeout. This remains bounded fixture
evidence only and does not promote complete UAX #29, bidi, script itemization,
shaping, paragraph, emoji rendering, color glyph rendering, or GPU text
support.

### KFONT-M5-003: Replace Basic Bidi Resolver

Status: done with bounded fixture evidence; independently reviewed.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/BidiSegmentation.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/BidiSegmentationTest.kt`
- `reports/font/fixtures/expected/unicode/bidi-hebrew-latin.txt`
- `reports/font/fixtures/expected/unicode/bidi-arabic-number-neutral.txt`
- `reports/font/fixtures/expected/unicode/bidi-isolate-controls.txt`
- `reports/font/fixtures/expected/unicode/bidi-embedding-override-controls.txt`
- `reports/font/fixtures/expected/unicode/bidi-unbalanced-controls.txt`
- `reports/font/fixtures/expected/unicode/bidi-single-run-needs-paragraph.txt`
- `reports/font/fixtures/expected/unicode/bidi-runs.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m5-003-bidi-resolver.md`
- `.upstream/specs/pure-kotlin-text/tickets/M5-unicode-segmentation-bidi/KFONT-M5-003-replace-basic-bidi-resolver.md`

Evidence:

- `DefaultBidiResolver` emits bounded M5 run-level bidi facts for mixed
  Latin/Hebrew, Arabic plus Arabic-number and neutral punctuation, isolate
  controls, explicit embedding/override controls, unbalanced controls, and
  single-run paragraph-required fixtures.
- `BasicBidiResolver()` now delegates to `DefaultBidiResolver` by default;
  `BasicBidiResolver(UnicodeData)` keeps the old bounded legacy resolver only
  for explicit compatibility callers.
- `BasicOpenTypeShapingEngine` propagates
  `text.shaping.paragraph-bidi-required` from detailed bidi resolution for
  mixed-direction single-run shaping requests.
- `bidi-runs.json` records Unicode version, source text hashes, grapheme
  cluster references, logical UTF-16 run ranges, cluster ranges, embedding
  levels, paragraph direction, resolved bidi classes, source controls, trace
  rule IDs, diagnostics, and non-claims including the absence of a paired
  bracket resolution claim.
- Tests assert `text.shaping.unicode-data-version-mismatch`,
  `text.shaping.paragraph-bidi-required`, and
  `text.unicode.bidi-control-unbalanced`.
- Regression tests assert malformed UTF-16 and text ranges that split
  surrogate pairs return stable `text.unicode.invalid-scalar` diagnostics
  without classifying invalid scalar values through Unicode tables.
- Regression tests assert cross-family `PDF`/`PDI` mismatches remain
  unbalanced through typed bidi-control stack validation.
- No external UAX #9 comparison is used as normative evidence.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: initial `REJECT` for malformed
  UTF-16 and mixed closer handling, remediated and re-reviewed as `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*Bidi*'
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: none for KFONT-M5-003 closeout. This remains bounded
run-level fixture evidence only and does not claim complete UAX #9
conformance, paired bracket resolution, paragraph visual line ordering,
GSUB/GPOS shaping, script itemization, or GPU text support.

### KFONT-M5-004: Add Script_Extensions Itemizer

Status: done; independently reviewed and freshly validated.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ScriptItemization.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/ScriptItemizationTest.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/UnicodeDataGeneration.kt`
- `font/text/src/main/resources/org/graphiks/kanvas/text/unicode/16.0.0/GraphemeBreakProperty.txt`
- `font/text/src/main/resources/org/graphiks/kanvas/text/unicode/16.0.0/Scripts.txt`
- `font/text/src/main/resources/org/graphiks/kanvas/text/unicode/16.0.0/UnicodeData.txt`
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/GraphemeBreakProperty.txt`
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/Scripts.txt`
- `reports/font/fixtures/expected/unicode/source-extracts/16.0.0/UnicodeData.txt`
- `reports/font/fixtures/expected/unicode/script-ambiguous-extension.txt`
- `reports/font/fixtures/expected/unicode/script-arabic-extension-ambiguous.txt`
- `reports/font/fixtures/expected/unicode/script-arabic-marks.txt`
- `reports/font/fixtures/expected/unicode/script-cjk-vs.txt`
- `reports/font/fixtures/expected/unicode/script-conflicting-context.txt`
- `reports/font/fixtures/expected/unicode/script-devanagari-matra.txt`
- `reports/font/fixtures/expected/unicode/script-emoji-zwj.txt`
- `reports/font/fixtures/expected/unicode/script-greek-polytonic.txt`
- `reports/font/fixtures/expected/unicode/script-hebrew-niqqud.txt`
- `reports/font/fixtures/expected/unicode/script-latin-combining.txt`
- `reports/font/fixtures/expected/unicode/script-runs.json`
- `reports/font/fixtures/expected/unicode/script-thai-tone.txt`
- `reports/font/fixtures/expected/unicode/script-unsupported.txt`
- `reports/font/fixtures/expected/unicode/unicode-data-manifest.json`
- `reports/font/fixtures/expected/unicode/unicode-data-tables.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m5-004-script-itemization.md`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `.upstream/specs/pure-kotlin-text/tickets/M5-unicode-segmentation-bidi/KFONT-M5-004-add-script-extensions-itemizer.md`

Evidence:

- `ScriptExtensionsItemizer` builds bounded script runs from pinned grapheme
  clusters and records cluster range, UTF-16 range, code point range, selected
  script, OpenType script tags, extension candidates, language hint, reason,
  source text hash, Unicode version, and diagnostics.
- The required matrix tags covered by this itemization slice are `latn`,
  `grek`, `hebr`, `arab`, `deva`, `dev2`, `thai`, `hani`, `Zsye`, and the
  unsupported/ambiguous diagnostic paths. Cyrillic, kana, hira, hang, and Zsym
  remain future fixture rows before broad shaping promotion.
- `script-runs.json` records Latin combining marks, Greek marks, Hebrew
  niqqud, Arabic marks, Devanagari matra, Thai tone mark, CJK variation
  selector context, emoji ZWJ context, unsupported Georgian, ambiguous
  Script_Extensions-only ditto mark, isolated TATWEEL, and a neutral Common
  cluster between conflicting Latin/Greek strong context.
- Tests regenerate `script-runs.json` from the checked-in fixture text files,
  trim only trailing CR/LF line endings for canonical source text, compare the
  result byte-for-byte with the golden, and assert JSON escaping for control
  characters.
- Tests assert `text.shaping.script-unsupported`,
  `text.shaping.script-run-ambiguous`, pinned Unicode version behavior through
  existing Unicode data tests, and cluster-aligned ranges through the
  grapheme-backed itemizer.
- `dump-evidence-index.json` registers `script-runs` as golden-gated producer
  evidence and `fixture-evidence-manifest.json` keeps the broader
  shaping-scripts family fixture-gated.
- Independent spec re-review verdict: `ACCEPT`.
- Independent code-quality re-review verdict: `Ready to merge: Yes`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests 'org.graphiks.kanvas.text.ScriptItemizationTest.scriptExtensionsItemizerUsesPinnedDataClustersExtensionsAndStableDiagnostics'
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk ./gradlew --no-daemon :font:text:test --tests '*ScriptItem*'
rtk ./gradlew --no-daemon :font:text:test --tests '*UnicodeData*' --tests '*Grapheme*' --tests '*Bidi*'
rtk git diff --check
```

Remaining gate: none for bounded KFONT-M5-004 script itemization closeout.
This remains itemization evidence only and does not claim complete UCD
coverage, GSUB/GPOS shaping, default feature policy, font fallback, glyph
mapping, paragraph layout, emoji rendering, or GPU text route support.

### KFONT-M6-001: Define `OpenTypeLayoutEngine` Contract And Dumps

Status: implemented; independent review pending.

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
- `reports/pure-kotlin-text/2026-06-16-kfont-m6-001-opentype-layout-contract.md`

Evidence:

- `OpenTypeLayoutEngineContract` adds a typed no-op OpenType layout boundary
  around `OpenTypeRunInput`, `ResolvedFeatureSet`, table availability, lookup
  trace requests, direct glyph input, shaping plans, GSUB/GPOS traces, shaped
  glyph runs, glyph positions, and cluster mappings.
- The simple Latin contract fixture produces deterministic glyph IDs while
  preserving cluster ranges, Unicode version, source text hash, typeface ID,
  script tag, requested/enabled feature state, fallback facts, trace refs, and
  byte-for-byte checked goldens.
- Direct glyph ID input explicitly bypasses GSUB and GPOS while preserving
  synthetic cluster facts, while still requiring a deterministic `TypefaceID`
  and diagnosing mismatched glyph/range cluster facts.
- Direct glyph ID input does not emit missing GSUB/GPOS/GDEF table diagnostics;
  GSUB/GPOS trace dumps carry only stage-specific table or lookup diagnostics.
- Tests assert stable refusals for
  `text.shaping.engine-contract-missing`,
  `text.shaping.script-unsupported`,
  `text.shaping.feature-unsupported`,
  `text.shaping.lookup-type-unsupported`,
  `text.shaping.lookup-malformed`,
  `text.shaping.cluster-invariant-failed`, and
  `text.shaping.fallback-missing`.
- `dump-evidence-index.json`, `fixture-evidence-manifest.json`, and
  `font-claim-dashboard.json` link the new contract evidence while keeping
  `complex-shaping` dependency-gated and `claimPromotionAllowed=false`.
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

### PKT-07A: Latin GSUB/GPOS Fixture Contract

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `latin-gsub-gpos-fixtures` fixture family as `fixture-gated`, separate from
  the broader `shaping-scripts` row.
- The row requires Latin fixture provenance for `cmap`-backed glyph IDs, GSUB
  feature lookup order, GPOS pair positioning data, requested/enabled/disabled
  feature dump fields, expected glyph ID or fixture-local glyph-name dumps,
  cluster ranges, and fallback diagnostics.
- The row explicitly keeps Greek, Cyrillic, Hebrew, and complex-script
  promotion out of this Latin slice.
- The fixture manifest validator treats the Latin row as required, and tests
  assert its non-promotion non-claim remains present.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is fixture-contract and dump-golden setup only. It does
not claim complete GSUB/GPOS support, Greek/Cyrillic/Hebrew promotion, complex
script shaping, native shaper parity, or complete shaping conformance.
### PKT-07B: Latin GSUB/GPOS Fixture Goldens

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/shaping/latin-gsub-gpos-goldens.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `latin-gsub-gpos-goldens.json` records Latin-only `liga`/`kern`
  requested-on/off golden readiness cases for `font-source-liberation-core`.
- The fixture manifest points `latin-gsub-gpos-fixtures` at the checked-in
  Latin expected dump.
- The dump evidence index records `latin-gsub-gpos-goldens` as
  `golden-gated` producer evidence with non-claiming policy.
- `TextStackSurfaceTest` loads the expected dump and asserts the fixture ID,
  cases, and non-claims remain present.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test
rtk git diff --check
```

Remaining gate: this is Latin fixture-golden readiness only. It does not claim
complete GSUB/GPOS support, Greek/Cyrillic/Hebrew promotion, complex script
shaping, native shaper oracle status, CPU oracle evidence, or GPU text
evidence.
### PKT-08A: Complex-Script Readiness Matrix

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `complex-script-fixture-matrix` fixture family as `fixture-gated`.
- The row splits complex-script readiness into Arabic, Devanagari, Thai, CJK,
  and emoji rows with positive and refusal expectations instead of hiding them
  behind a broad shaping claim.
- The required gates name script-specific phase/feature evidence, fallback or
  unsupported-boundary diagnostics, and paragraph-owned blockers where
  applicable.
- Tests assert the Arabic positive/refusal gate remains present.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is readiness-matrix evidence only. It does not claim
Arabic, Indic, Thai, CJK, emoji, complete GSUB/GPOS, complex shaping, or native
shaper parity support.
### PKT-08B: Arabic Fixture Row Seed

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/shaping/arabic-seed-readiness.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `arabic-seed-readiness.json` records the Arabic seed rows for joining forms,
  lam-alef, marks, cursive attachment, and mixed bidi.
- The expected dump records required diagnostics for unavailable cursive
  attachment, mark positioning, GDEF, and paragraph bidi requirements.
- The fixture manifest points `complex-script-fixture-matrix` at the checked-in
  Arabic seed dump.
- The dump evidence index records `arabic-seed-readiness` as `golden-gated`
  producer evidence with non-claiming policy.
- `TextStackSurfaceTest` loads the expected dump and asserts diagnostics and
  non-claims remain present without asserting Arabic shaping support.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test
rtk git diff --check
```

Remaining gate: this is Arabic fixture-row seed evidence only. It does not
claim Arabic shaping support, Indic/Thai/CJK/emoji shaping support, complete
complex shaping, native shaper oracle status, CPU oracle evidence, or GPU text
evidence.
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
### PKT-09B: Paragraph Fixture And Golden Matrix

Status: implemented with local diff review.

Files:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `fixture-evidence-manifest.json` now records the required
  `paragraph-fixture-goldens` fixture family as `fixture-gated`.
- The row distinguishes paragraph-owned behavior from shaping-owned blockers:
  bidi visual line ordering, rich style/feature/variation/decorations,
  placeholders, ellipsis, hard/soft wrap, max-lines policy, hit testing,
  selection boxes, word boundaries, and grapheme boundaries.
- The row requires refusal diagnostics that name PKT-07/08 shaping blockers
  separately from paragraph layout gates.
- Tests assert the bidi visual-line blocker wording remains present.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

Remaining gate: this is fixture/golden matrix evidence only. It does not claim
complete paragraph layout, full bidi visual ordering, rich text parity,
complete hit testing/selection, complete ellipsis insertion, or Skia Paragraph
parity.
### PKT-09C: ParagraphInput Contract And Golden Schema

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/paragraph/paragraph-input-goldens.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `paragraph-input-goldens.json` records the paragraph input golden schema for
  a multi-style placeholder case plus invalid-range, non-finite metric, and
  unsupported-baseline negative cases.
- The fixture manifest points both `paragraph` and
  `paragraph-fixture-goldens` at the checked-in paragraph expected dump.
- The dump evidence index records `paragraph-input-goldens` as `golden-gated`
  producer evidence with non-claiming policy.
- `TextStackSurfaceTest` loads the expected dump and asserts the schema cases
  and paragraph non-claims remain present.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test
rtk git diff --check
```

Remaining gate: this is paragraph input golden-schema evidence only. It does
not claim complete paragraph layout, full bidi visual ordering, rich text
parity, complete selection/hit testing, ellipsis insertion, Skia Paragraph
parity, CPU oracle evidence, or GPU text evidence.
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

### KFONT-M9-001: Complete GlyphStrikeKey

Status: done; independently reviewed and freshly validated.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphStrikeKeyContractTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-strike-key.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m9-001-glyph-strike-key.md`

Evidence:

- `GlyphStrikeKey` embeds `glyphId`, cluster facts, route, mask, transform,
  subpixel, edging, SDF, palette, variation, Unicode version, and renderer
  descriptor facts in deterministic preimages and compact hashes.
- `glyph-strike-key.json` covers A8, SDF, outline, COLR, bitmap PNG, SVG, and
  unsupported routes, including variation, palette, renderer descriptor, and a
  Unicode-sensitive cluster.
- Refusal records cover missing `TypefaceID`, nondeterministic host source,
  forbidden live-handle fields, LCD future research, and route-specific key gaps.
- `font-diagnostic-taxonomy.json`, dump index, fixture manifest, and claim
  dashboard expose the evidence without support promotion.
- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: `ACCEPT`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphStrikeKey*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk ./gradlew --no-daemon :font:core:test --tests '*FontDiagnosticTaxonomy*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py scripts/test_validate_pure_kotlin_text_fixture_manifest.py scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

Remaining gate: this is contract-only evidence. It does not claim A8
rasterization, SDF generation, atlas packing, GPU text routes, color/emoji
rendering, LCD support, or `dftext` retirement. Complete `KFONT-M9-002`,
`KFONT-M9-003`, `KFONT-M9-004`, and `KFONT-M9-005` before re-evaluating M11
A8 GPU handoff.

### PKT-10B: Glyph Artifact Plan Decision Trace Dump

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`

Evidence:

- `GlyphArtifactPlanDecision` records one selected or explicitly unsupported
  route per glyph position, including `text.glyph.*` selected route, source,
  route-specific strike-key hash, fallback policy, rejected alternatives, and
  optional diagnostic.
- `GlyphArtifactRoutePlanner` now emits decisions in glyph-run order while
  preserving the existing `representations` and `diagnostics` surfaces.
- `GlyphArtifactPlan.toCanonicalGlyphArtifactPlanJson()` emits deterministic
  `glyph-artifact-plan.json`-style evidence with decisions, rejected
  alternatives, diagnostics, and `dumpSha256`.
- Tests cover outline fallback after SDF/A8 rejection, A8 fallback after SDF
  rejection, first-choice SDF selection, explicit unsupported route refusal,
  stable `text.glyph.*` route labels, decision key hashes, diagnostic linkage,
  canonical JSON field order, and dump hash shape.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.glyphArtifactPlanRecordsDecisionTraceAndCanonicalDump
```

Remaining gate: this is route-plan decision trace evidence only. It does not
claim complete COLR/bitmap/SVG plan refs, complete A8/SDF production coverage,
atlas capacity/stale-generation policy, GPU text handoff promotion, LCD
support, native/font-engine oracle behavior, or complete `glyph-artifact-plan`
fixture generation.
### PKT-10C: Atlas Capacity Refusal Diagnostic Dump

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`

Evidence:

- `RowGlyphAtlasPacker.packWithDiagnostics(...)` returns a
  `GlyphAtlasPackingResult` instead of throwing when a complete A8 pack request
  cannot fit the configured atlas width.
- Capacity overflow emits stable `text.glyph.atlas-capacity-exceeded`
  diagnostics with glyph ID, atlas width, padded width, warning severity, and a
  no-partial-placement refusal message.
- `GlyphAtlasPackingResult.toCanonicalGlyphAtlasPackingJson()` emits
  deterministic placement/diagnostic counts, placement arrays, diagnostics,
  and `dumpSha256`.
- Tests cover complete-plan refusal with no partial placements, stable
  capacity diagnostic content, canonical JSON field order, and dump hash shape.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.rowPackerReportsCapacityDiagnosticWithoutPartialPlacements
```

Remaining gate: this is A8 row-packer capacity refusal evidence only. It does
not claim atlas eviction, stale-generation detection, split-atlas planning, SDF
atlas capacity policy, upload byte hashing, invalidation tokens, GPU text
sampling, or complete `glyph-atlas.json` fixture coverage.
### PKT-10D: A8/SDF Atlas Lifecycle Fixture Contract

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/glyph/a8-sdf-atlas-lifecycle.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `a8-sdf-atlas-lifecycle.json` records the PKT-10D dump contract for the
  `font-source-liberation-core` fixture, including A8 atlas pack, SDF
  normalization, SDF transform refusal, capacity, stale generation, key
  preimage, mask/SDF hash, generation token, invalidation token, and budget
  evidence requirements.
- The expected dump names stable glyph diagnostics for SDF transform/generation
  refusal, atlas capacity, stale generation, nondeterministic key, and artifact
  budget gates without adding GPU upload evidence.
- The provenance index attaches the expected dump to
  `font-source-liberation-core`, and the fixture manifest attaches it to the
  `a8-sdf-artifacts` current evidence paths.
- The dump evidence index records `a8-sdf-atlas-lifecycle` as `golden-gated`
  producer evidence with explicit non-claims.
- `GlyphSurfaceTest` loads the expected dump and asserts exact structured
  values for dump ID, owner ticket, fixture ID, required diagnostics, and
  non-claims.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:glyph:test
rtk git diff --check
```

Remaining gate: this is fixture-contract evidence only. It does not claim
complete A8 atlas support, complete SDF production, complete atlas lifecycle
support, GPU upload execution, renderer resource ownership, or GPU text-route
promotion.
### PKT-10E: A8 Mask Artifact Evidence Dump

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`

Evidence:

- `A8GlyphMaskArtifactEvidence.from(...)` builds deterministic evidence for
  current pure Kotlin A8 masks, including glyph bounds, row stride, addressable
  pixel count, non-zero sample count, route-specific strike-key hash, and
  coverage SHA-256 over addressable samples only.
- `A8GlyphMaskArtifactEvidence.toCanonicalJson()` emits stable
  `a8-glyph-mask.json`-style evidence with diagnostics and `dumpSha256`.
- Tests cover row-padding exclusion from the coverage hash, bounds/origin facts,
  row stride, addressable-pixel count, non-zero count, stable coverage hash,
  route-specific key hash shape, canonical JSON field order, and dump hash
  shape.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.a8GlyphMaskArtifactEvidenceRecordsBoundsAndCoverageHash
```

Remaining gate: this is current A8 mask evidence only. It does not claim
quadratic/cubic outline rasterization, complete malformed-contour diagnostics,
LCD support, SDF generation, atlas eviction/stale-generation support, GPU
upload/sampling, external rasterizer oracle parity, or complete
`a8-glyph-mask.json` fixture coverage.
### PKT-10F: Atlas Stale Generation Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `GlyphRouteDiagnostic.atlasGenerationStale(...)` emits stable
  `text.glyph.atlas-generation-stale` refusal diagnostics for stale atlas
  generation tokens.
- Diagnostics record glyph ID when known, artifact generation, current
  generation, invalidation token, warning severity, canonical JSON, and a
  stable diagnostic hash through the existing `GlyphRouteDiagnostic` evidence
  surface.
- Tests cover diagnostic route, generation facts, invalidation token, severity,
  hash shape, and canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.routeDiagnosticRecordsStaleAtlasGenerationRefusal
```

Remaining gate: this is stale-generation refusal evidence only. It does not
claim live atlas invalidation, atlas eviction policy changes, regenerated
artifact production, upload byte hashing, GPU resource lifecycle support, or
complete `glyph-atlas.json` fixture coverage.
### PKT-10G: SDF Transform Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `GlyphRouteDiagnostic.sdfTransformUnsupported(...)` emits stable
  `text.glyph.SDF-transform-unsupported` refusal diagnostics for transforms
  outside the current SDF eligibility policy.
- Diagnostics record glyph ID, transform bucket, fallback route, warning
  severity, and canonical JSON through the existing `GlyphRouteDiagnostic`
  surface.
- Tests cover diagnostic route, transform bucket, fallback route, severity, and
  canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.GlyphSurfaceTest.routeDiagnosticRecordsSDFTransformUnsupportedRefusal
```

Remaining gate: this is SDF transform refusal evidence only. It does not claim
complete SDF eligibility policy, SDF generation fixture coverage, perspective
or non-affine transform support, A8 fallback production, atlas upload/sampling,
or GPU text-route promotion.
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
### PKT-11B: Bitmap Glyph PNG Plan Evidence Dump

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BitmapGlyphPlan.fromPNG(...)` records deterministic plan-only evidence for
  a selected PNG bitmap strike: glyph ID, source table family, requested size,
  selected strike ppem, source format, origin, decoded bounds, scaling policy,
  alpha policy, source payload SHA-256, decoded pixel SHA-256, diagnostics, and
  dump hash.
- `BitmapGlyphPlan.toCanonicalJson()` emits stable `bitmap-glyph-plan.json`
  style evidence without renderer handles, platform codec facts, native font
  APIs, or GPU resources.
- Tests cover pure Kotlin PNG decode input, plan field order, source/decoded
  hash shape, scaling policy, alpha policy, dump hash shape, and forbidden
  token/object-identity absence in the dump.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.bitmapGlyphPlanDumpsPngStrikeAndPixelHashes
```

Remaining gate: this is PNG bitmap glyph plan evidence only. It does not claim
complete CBDT/CBLC or sbix fixture coverage, non-PNG payload refusal coverage,
malformed PNG diagnostic wrapping, strike-origin table parsing, GPU texture
upload/sampling, emoji sequence support, or complete `bitmap-glyph-plan.json`
fixture generation.
### PKT-11C: Bitmap Non-PNG Payload Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BitmapGlyphPlan.unsupportedPayloadDiagnostic(...)` emits stable
  `text.bitmap.payload-format-unsupported` diagnostics for non-PNG embedded
  bitmap payloads with glyph ID, table family, normalized source format, and
  source payload SHA-256.
- The diagnostic uses route `bitmap`, warning severity, and canonical JSON
  through `ColorGlyphDiagnostic.toCanonicalJson()` without decoding the payload
  or invoking platform/native codecs.
- Tests cover source format normalization, payload hash presence, stable code,
  severity, route, message, and canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.bitmapGlyphPlanBuildsNonPngPayloadRefusalDiagnostic
```

Remaining gate: this is non-PNG bitmap payload refusal diagnostics only. It does
not claim CBDT/CBLC or sbix table parsing, complete bitmap strike selection,
malformed PNG diagnostic wrapping, decoded pixel oracle coverage, GPU upload or
sampling, emoji sequence routing, or complete bitmap fixture coverage.
### PKT-11D: Color Glyph Fixture Family Split

Status: implemented; independent review pending.

Files:

- `reports/font/fixtures/expected/color/color-svg-emoji-goldens.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `color-svg-emoji-goldens.json` records the PKT-11D dump contract for the
  `color-colrv1-test-glyphs` fixture across the `color-glyphs`,
  `png-bitmap-glyphs`, `svg-glyphs`, and `emoji` fixture families.
- The expected dump names required refusal diagnostics for COLRv1 cycle/budget,
  PNG decode/strike/payload, SVG external-resource/feature/budget, and emoji
  sequence/fallback/color-glyph unavailable gates.
- The provenance index attaches the expected dump to
  `color-colrv1-test-glyphs`, and the fixture manifest attaches it to the
  color, PNG, SVG, and emoji current evidence paths.
- The dump evidence index records `color-svg-emoji-goldens` as `golden-gated`
  producer evidence with explicit non-claims.
- `ColorGlyphSurfaceTest` loads the expected dump and asserts exact structured
  values for dump ID, fixture ID, color families, required refusals, and
  non-claims.

Validation:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:glyph:test
rtk git diff --check
```

Remaining gate: this is fixture-family split and refusal-contract evidence
only. It does not claim complete COLRv1 rendering, PNG bitmap glyph routing,
SVG-in-OpenType rendering, emoji sequence shaping, GPU color glyph support,
platform fallback behavior, or CPU oracle hash coverage.
### PKT-11E: Bitmap Malformed PNG Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BitmapGlyphPlan.pngDecodeFailedDiagnostic(...)` emits stable
  `text.bitmap.PNG-decode-failed` diagnostics for malformed PNG embedded bitmap
  payloads after the pure Kotlin PNG decoder reports a failure.
- The diagnostic records glyph ID, table family, `sourceFormat=png`, source
  payload SHA-256, failure class, and failure message with route `bitmap` and
  warning severity.
- Tests cover malformed PNG refusal via the existing pure Kotlin decoder,
  stable diagnostic code, route, severity, source payload hash evidence,
  failure facts, and canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.bitmapGlyphPlanBuildsMalformedPngRefusalDiagnostic
```

Remaining gate: this is malformed PNG refusal diagnostics only. It does not
claim CBDT/CBLC or sbix table parsing, complete bitmap strike selection, PNG
fixture coverage, decoded pixel oracle coverage, GPU upload or sampling, emoji
sequence routing, native/platform codec behavior, or complete bitmap glyph
support.
### PKT-11F: COLRv1 Budget Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `COLRV1Parser.budgetExceededDiagnostic(...)` emits stable
  `text.color.COLRv1-budget-exceeded` diagnostics for COLRv1 paint graph budget
  refusals without changing `COLRV1Parser.parse(...)` behavior.
- Diagnostics record glyph ID when known, route `colr`, table family `COLR`,
  COLRv1 version, stable budget name, configured limit, observed value, and
  warning severity.
- Tests cover the budget diagnostic code, route, severity, detail facts, and
  canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1BudgetExceededRefusalDiagnostic
```

Remaining gate: this is COLRv1 budget refusal evidence only. It does not claim
complete COLRv0/COLRv1 fixture coverage, cycle detection evidence, complete
COLRv1 rendering, paint-operation-specific support, GPU color glyph support, or
native/platform fallback behavior.
### PKT-11G: COLRv1 PaintColrGlyph Cycle Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `COLRV1Table.paintColrGlyphCycleDiagnostic(...)` traverses already-parsed
  COLRv1 paint data reachable through `PaintColrGlyph` links and emits stable
  `text.color.COLRv1-cycle-detected` diagnostics for the first detected cycle.
- Diagnostics record glyph ID, route `colr`, table family `COLR`, COLRv1
  version, cycle path, cycle length, warning severity, and canonical JSON.
- Tests cover a two-glyph `PaintColrGlyph` cycle through a nested `PaintGlyph`
  child, stable diagnostic code, route, severity, cycle facts, and canonical
  JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.detectsCOLRV1PaintColrGlyphCyclesWithStableDiagnostic
```

Remaining gate: this is parsed-model cycle refusal evidence only. It does not
claim complete COLRv0/COLRv1 fixture coverage, complete PaintColrGlyph graph
expansion, bounds computation, palette resolution, COLRv1 rendering, GPU color
glyph support, or native/platform fallback behavior.
### PKT-11H: SVG External Resource Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BasicSVGGlyphParser.externalResourceRefusedDiagnostic(...)` emits stable
  `text.SVG.external-resource-refused` diagnostics for SVG glyph external
  resource references without fetching or resolving the resource.
- Diagnostics record glyph ID, route `svg`, element name, attribute name, and a
  SHA-256 hash of the refused reference instead of storing the external URL as
  normative evidence.
- Tests cover diagnostic code, route, severity, element/attribute facts,
  reference hash evidence, URL omission from detail, and canonical JSON field
  order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsSVGExternalResourceRefusalDiagnostic
```

Remaining gate: this is SVG external-resource refusal evidence only. It does
not claim complete SVG-in-OpenType fixture coverage, static path/gradient/clip
support, `use` recursion refusal, unsupported feature refusal, SVG rendering,
external resource support, GPU SVG glyph support, or native/platform SVG
fallback behavior.
### PKT-11I: SVG Unsupported Feature Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BasicSVGGlyphParser.unsupportedFeatureDiagnostic(...)` emits stable
  `text.SVG.feature-unsupported` diagnostics for SVG glyph features that the
  pure Kotlin glyph-scoped subset refuses.
- Diagnostics record glyph ID, route `svg`, element name, feature name, warning
  severity, and canonical JSON without invoking a native SVG engine or
  renderer fallback.
- Tests cover diagnostic code, route, severity, element/feature facts, message,
  and canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsSVGUnsupportedFeatureRefusalDiagnostic
```

Remaining gate: this is SVG unsupported-feature refusal evidence only. It does
not claim complete SVG-in-OpenType fixture coverage, static path/gradient/clip
support, `use` recursion refusal, SVG rendering, external resource support, GPU
SVG glyph support, or native/platform SVG fallback behavior.
### PKT-11J: SVG Use Recursion Refusal Diagnostic

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BasicSVGGlyphParser.useRecursionRefusedDiagnostic(...)` emits stable
  `text.SVG.budget-exceeded` diagnostics for bounded SVG `<use>` recursion
  refusal.
- Diagnostics record glyph ID, route `svg`, referenced symbol/element ID,
  observed recursion depth, configured maximum depth, warning severity, and
  canonical JSON without resolving or expanding SVG references.
- Tests cover diagnostic code, route, severity, reference ID, depth facts,
  message, and canonical JSON field order.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsSVGUseRecursionRefusalDiagnostic
```

Remaining gate: this is SVG `<use>` recursion refusal evidence only. It does not
claim complete SVG-in-OpenType fixture coverage, actual `use` graph expansion,
static path/gradient/clip support, SVG rendering, external resource support,
GPU SVG glyph support, or native/platform SVG fallback behavior.
### PKT-11K: SVG Gradient Transform Clip Fixture

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `BasicSVGGlyphParser` now includes bounded deterministic summaries for the
  SVG glyph fixture elements and attributes needed by gradient, transform, and
  clip evidence: `linearGradient`, `stop`, `clipPath`, `clip-path`, gradient
  coordinate facts, IDs, and stop colors.
- Tests cover a generated glyph-scoped SVG fixture with one gradient, one clip
  path, one transformed path using `fill=url(...)` and `clip-path=url(...)`,
  and stable element summary ordering.
- The font fixture inventory marks `svg-gradient-transform-clip` as current
  positive evidence, and the fixture manifest removes the remaining SVG glyph
  fixture gate.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.parsesBasicSVGGradientTransformAndClipFixture
```

Remaining gate: this is SVG fixture-summary evidence only. It does not claim a
complete SVG renderer, `use` expansion support, external resource support,
filter support, text layout inside SVG glyphs, or GPU SVG glyph support.
### PKT-11L: Emoji VS Skin-Tone ZWJ Fixture Evidence

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt`
- `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `EmojiSequenceShaper` now consumes Unicode emoji skin-tone modifiers as part
  of one emoji component and excludes those modifiers from standalone emoji
  bases.
- Tests cover one fixture string containing VS15 text-style emoji, a base emoji
  plus skin-tone modifier, and a ZWJ family sequence, with deterministic UTF-16
  text ranges and glyph-cluster ranges.
- The font fixture inventory marks `emoji-vs15-vs16`, `emoji-skin-tone`, and
  `emoji-zwj-family` as current positive evidence; existing fallback and color
  glyph unavailable diagnostics continue to cover the refusal gates.
- The fixture manifest removes the remaining emoji fixture gates while keeping
  complete emoji shaping and color fallback non-claims.

Validation:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.emojiSequenceShaperDumpsVS15SkinToneAndZwjFamilyFixtures
```

Remaining gate: this is bounded emoji sequence fixture evidence only. It does
not claim full font-specific emoji substitution, complete required-script
shaping, complete color glyph fallback support, platform emoji parity, or GPU
text-route support.
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
### PKT-13B: Font-Only Fixture Inventory

Status: implemented; independent review pending because the current tool policy
does not allow subagent dispatch without an explicit user delegation request.

Files:

- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_font_fixtures.py`
- `scripts/test_validate_pure_kotlin_text_font_fixtures.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `font-fixture-inventory.json` records the font-only fixture set requested for
  PKT font closeout while explicitly excluding GPU handoff, paragraph, and
  shaping-script rows from this slice.
- The inventory covers 8 font families and 40 target fixture gates:
  A8/SDF artifacts, CFF/CFF2 scaler, color glyphs, emoji, font source/SFNT,
  PNG bitmap glyphs, SVG glyphs, and TrueType scaler.
- Each fixture records a stable fixture ID, target gate, status, fixture kind,
  generation policy, existing evidence paths, expected artifacts, and
  non-claims. Status values separate current positive evidence, current refusal
  evidence, specified fixtures, and implementation-gated fixtures.
- `validate_pure_kotlin_text_font_fixtures.py` rejects non-font rows, missing
  font gates, unsorted or duplicate fixtures, hidden `target-supported`
  statuses, missing evidence paths, and normative external-engine terms.
- `fixture-evidence-manifest.json` now references the font-only inventory and
  its validation command from every font fixture family while leaving GPU,
  paragraph, and shaping rows untouched.

Validation:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

Remaining gate: this is font-fixture inventory and validation evidence only.
It does not itself claim complete CFF/CFF2 target support, complete
variable-font support, complete SVG rendering, complete emoji sequence shaping,
complete target font support, or any GPU text route.
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
### KFONT-M1-004: Bundled Source Fixture Manifest

Status: done; merged, independently reviewed, and freshly revalidated for closeout.

Files:

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontFixtureManifestTest.kt`
- `reports/pure-kotlin-text/font-fixtures-manifest.json`
- `reports/pure-kotlin-text/font-source.json`
- `reports/pure-kotlin-text/typeface-id.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m1-004-fixture-manifest.md`
- `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/KFONT-M1-004-add-bundled-source-fixture-manifest.md`
- `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `FontFixtureManifestWriter` emits the checked-in
  `font-fixtures-manifest.json` canonical JSON byte-for-byte.
- The manifest records normative bundled fixture rows for Liberation Sans TTF,
  Source Serif OTF/CFF candidate, and Roboto Flex variable TTF candidate, with
  license/provenance, SHA-256, byte length, face count, intended coverage tags,
  and `claimPromotionAllowed=false`.
- Planned generated TTC, malformed directory, and missing-required-table rows
  record generator IDs, source parameters, non-normative status, remaining
  gates, and `font.fixture.generated-bytes-missing`.
- Host-scanned sources remain non-normative with `font.source.host-dependent`;
  normative entries missing license/provenance/hash produce
  `font.fixture.provenance-missing`.
- `font-source.json` includes `manifestFixtureId` for the bundled source row,
  and manifest entries link to stable `font-source.json` and `typeface-id.json`
  labels through report-label arrays.
- Independent spec review verdict: `ACCEPT` after removing an out-of-scope
  `font/core/build.gradle.kts` packaging change from the worktree.
- Independent code-quality review verdict: `ACCEPT_WITH_FIXES`; remediated by
  adding generated/planned fixture invariants and negative tests, tightening the
  host-dependent normative diagnostic to uncaptured bytes, and removing unused
  helper code.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FixtureManifest*'
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*FixtureManifest*'
rtk ./gradlew --no-daemon :font:core:test --tests '*FontSourceIdentity*' --tests '*IdentityDump*'
rtk ./gradlew --no-daemon :font:core:test
rtk git diff --check
```

Remaining gate: this is fixture provenance and evidence linkage only. It does
not claim SFNT parser behavior, CFF support, variable font support, TTC
support, malformed parser support, fallback behavior, glyph scaling/cache
support, rendering support, or GPU support.

### KFONT-M2-001: SFNT/TTC Parser Entry Points

Status: done; merged and freshly revalidated for closeout.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTParserEntryPointTest.kt`
- `reports/pure-kotlin-text/sfnt-directory.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m2-001-sfnt-entry-points.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-001-normalize-sfnt-ttc-parser-entry-points.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `SFNTParseRequest` carries `FontSourceID`, source kind, display name,
  bounded byte range, requested collection index, and parser generation for
  both single-face SFNT and TTC requests.
- `DefaultSFNTParser` returns one `SFNTParseResult` surface separating
  directory facts, intentionally empty parsed face facts, table slices, and
  container diagnostics.
- `DefaultSFNTParser` is directory-only for this ticket and does not delegate to
  `DefaultOpenTypeFaceParser` or typed layout/color table payload parsers.
- Invalid TTC collection index returns `font.collection-index-invalid` with no
  selected face and no delegated parse of another face.
- Unknown wrappers return stable non-promoting SFNT diagnostics without
  platform APIs or external parsers.
- `sfnt-directory.json` records one Liberation Sans single TTF, one generated
  TTC selected face, and one invalid TTC index diagnostic with
  `dashboardClassification=tracked-gap` and `claimPromotionAllowed=false`.
- Report construction is byte-for-byte deterministic across repeated parses of
  the same fixtures.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*'
rtk git diff --check
```

Remaining gate: this is parser entry-point and directory evidence only. It
does not claim complete SFNT conformance, table payload semantics, glyph
outlines, CFF/CFF2 scaler support, GSUB/GPOS shaping behavior, fallback,
paragraph layout, rendering, or broad text support.

### KFONT-M2-003: Complete CMap Format Coverage

Status: implemented; independent review pending.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontDiagnosticTaxonomyTest.kt`
- `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/ShapingTypes.kt`
- `reports/pure-kotlin-text/cmap-map.json`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`
- `reports/pure-kotlin-text/2026-06-15-kfont-m2-003-cmap-format-coverage.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-003-complete-cmap-format-coverage.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `OpenTypeCMapTableParser` covers generated fixtures for formats 12, 4, 14,
  6, and 0 with deterministic selection priority: Windows format 12 wins over
  format 4 and legacy 6/0 fallback subtables; format 4 wins over legacy 6/0
  when no usable format 12 subtable is present.
- `CMapTable.lookupGlyphId(codePoint, variationSelector: Int? = null)`
  preserves source compatibility while returning stable glyph ID `0` for
  missing code points.
- Format 14 variation selector parsing covers deterministic default and
  non-default UVS fixture rows: default ranges preserve base mapping semantics,
  and non-default mappings return the explicit glyph ID for the requested
  `(codePoint, variationSelector)` pair.
- `CMapDiagnostic.dump()` and `cmap-map.json` record
  `font.sfnt.cmap-format-unsupported` for format 13 and
  `font.sfnt.cmap-unusable` when no usable Unicode `cmap` is selected.
- `font-diagnostic-taxonomy.json` records both `font.sfnt.cmap-*` taxonomy
  rows and the `sfnt-cmap-refusal` sample diagnostic, keeping the M0 namespace
  policy intact.
- `CMapGlyphMapper` continues to expose `.notdef` parser glyph ID `0` as
  `null` at the shaping boundary, preserving existing missing-glyph diagnostics
  and fallback behavior without adding shaping support.
- `cmap-map.json` records selected subtable facts, platform/encoding IDs,
  mapped ranges or compact facts, missing-codepoint behavior, variation
  selector facts, source face identities, and `claimPromotionAllowed=false`.
- Independent spec re-review verdict: `ACCEPT`.
- Independent code-quality re-review verdict: `Ready to merge: Yes`.

Validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParserReportsUnsupportedAndUnusableCMapDiagnostics' --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParserPrefersFormat4OverLegacyFallbackSubtables' --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapMapReportCoversKfontM2CMapEvidence'
rtk ./gradlew --no-daemon :font:text:test --tests 'org.graphiks.kanvas.text.TextStackSurfaceTest.cmapGlyphMapperUsesSfntCMapLookupForBasicShapingAndTypefaceRouting'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParser*'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapMapReportCoversKfontM2CMapEvidence'
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*CMap*'
rtk ./gradlew --no-daemon :font:text:test
rtk git diff --check
```

Remaining gate: this is parser-only `cmap` evidence. Format 13 remains
fixture-gated/refused, and this checkpoint does not claim shaping, GSUB/GPOS,
bidi, segmentation, fallback runs, paragraph layout, scaler, rendering, native
font-engine parity, or GPU text-route support.

### KFONT-M2-004: OpenType Table Fact Dumps

Status: implemented; independent review pending.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTTableFactDumpTest.kt`
- `reports/pure-kotlin-text/sfnt-tables.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m2-004-table-fact-dumps.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-004-add-opentype-table-fact-dumps.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `OpenTypeTableFactReportWriter` emits
  `sfnt-tables.json` with canonical required/high-value OpenType table facts:
  `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`, `OS/2`, `post`, `loca`,
  `glyf`, `CFF `, `CFF2`, `vhea`, `vmtx`, `GDEF`, `GSUB`, `GPOS`, `BASE`,
  `kern`, `fvar`, `avar`, `gvar`, `HVAR`, `VVAR`, `MVAR`, `COLR`, `CPAL`,
  `CBDT`, `CBLC`, `sbix`, and `SVG `.
- The Liberation Sans TTF row links to M1 source/typeface evidence labels
  `bundled-fixture` and `single-face-ttf`, carries bounded byte ranges,
  checksums, raw SHA-256 table payload hashes, roles, parser status, metadata
  classifications, and `claimPromotionAllowed=false`.
- Generated rows capture a malformed optional `fvar` table diagnostic
  `font.sfnt.optional-table-malformed` and missing required `loca`/`glyf`
  diagnostics `font.sfnt.required-table-missing`.
- `cmapMapLink` deterministically references
  `reports/pure-kotlin-text/cmap-map.json` and the KFONT-M2-003 generated
  `cmap` entry IDs as metadata-only facts.
- Focused tests prove byte-for-byte repeated generation and canonical table
  ordering independent of SFNT directory order.
- `dump-evidence-index.json` tracks `sfnt-table-facts` as producer-only dump
  evidence.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*TableFactDump*' --tests '*CMap*'
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Review: independent spec review accepted with no remediations. Independent
code review accepted with non-blocking notes on writer-level entry ordering and
validation-command consistency.

Remaining gate: none for KFONT-M2-004. This is metadata-only table evidence;
it does not claim shaping, scaler, CFF/CFF2 outline, color glyph, bitmap/SVG
rendering, native engine parity, fallback, paragraph layout, or GPU text-route
support.

### KFONT-M3-001: TrueType Composite Glyph Transform Coverage

Status: done; independently reviewed.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-composite-glyphs.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m3-001-composite-glyphs.md`
- `.upstream/specs/pure-kotlin-text/tickets/M3-truetype-glyf/KFONT-M3-001-complete-composite-glyph-transform-coverage.md`
- `.upstream/specs/pure-kotlin-text/tickets/M3-truetype-glyf/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `ParsedTrueTypeGlyphScaler` now resolves composites through an internal result
  that carries both outline commands and resolved TrueType points. Point
  matching computes component offsets from resolved points rather than
  serialized path strings.
- Existing composite translation, uniform scale, non-uniform scale, two-by-two
  transforms, scaled/unscaled offsets, nested components, and component `gvar`
  behavior are covered by the focused scaler tests.
- Invalid point indices still refuse with stable
  `font.outline-format-unsupported` / `truetype.composite-point-index`
  diagnostics.
- Cycle/recursion and invalid component glyph IDs are captured in
  `scaledGlyphEvidence` diagnostics with stable details
  `truetype.composite-recursion-depth` and
  `truetype.composite-component-glyph-id`.
- Excessive composite component lists are bounded during parsing and captured
  in `scaledGlyphEvidence` diagnostics with stable detail
  `truetype.composite-component-count`.
- `truetype-composite-glyphs.json` records outline commands, bounds, metrics,
  component trace, `USE_MY_METRICS` facts, path hash/stat artifacts, diagnostic
  snapshots, and explicit non-claims.
- `USE_MY_METRICS` is proven by behavior, not only by a flag: composite
  `metrics()` and `scaledGlyphEvidence().metrics` use the first component with
  the bit set as the metrics source, and the golden records component glyph 1's
  `advanceX` for the supported composite fixture.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CompositeGlyph*' --tests '*Glyf*'
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk git diff --check
```

Review:

- Independent spec review verdict: `SPEC_ACCEPTED`.
- Independent quality review verdict: `QUALITY_ACCEPTED`.

Remaining gate: no remaining gate for KFONT-M3-001. This slice does not claim
A8/SDF glyph artifacts, GPU text routes, CFF/CFF2 outlines, native scaler
oracle behavior, full TrueType hinting VM behavior, shaping, fallback,
paragraph layout, full IUP interpolation, phantom-point metrics, vertical
metrics, or complete variable font support.

### KFONT-M2-005: Malformed SFNT Fixture Suite

Status: implemented; independent review pending.

Files:

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTParserEntryPointTest.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/MalformedSFNTFixtureSuiteTest.kt`
- `reports/pure-kotlin-text/sfnt-directory.json`
- `reports/pure-kotlin-text/cmap-map.json`
- `reports/pure-kotlin-text/malformed-sfnt-fixtures.json`
- `reports/pure-kotlin-text/2026-06-16-kfont-m2-005-malformed-sfnt-fixtures.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-005-add-malformed-sfnt-fixture-suite.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

Evidence:

- `malformed-sfnt-fixtures.json` covers bad SFNT version, truncated header,
  invalid TTC index, out-of-bounds table record, overlapping tables, duplicate
  tag, missing required table, malformed optional table, and unsupported `cmap`
  format.
- Every row records fixture ID, generator ID, sorted generator parameters,
  byte length, content SHA-256, primary expected diagnostic, expected outcome,
  linked evidence path, linked evidence entry ID, and diagnostics.
- The suite links cases to `sfnt-directory.json`, `sfnt-tables.json`, or
  `cmap-map.json` evidence without adding raw fixture byte payloads.
- Focused tests prove deterministic generation, byte-for-byte golden stability,
  linked evidence resolution, fixture/hash consistency, primary diagnostic
  coverage, stable hashes, and absence of external/native/GPU support tokens.
- `fixture-evidence-manifest.json` now links the malformed SFNT family to the
  suite while keeping remaining format-14 fixture gates explicit.

Validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*MalformedSFNT*' --tests '*SFNTParser*' --tests '*CMap*' --tests '*TableFactDump*' --rerun-tasks
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

Review: independent spec review accepted after linked evidence IDs, hashes,
status counts, and no-`__pycache__` status were verified. Independent code
review accepted with no findings.

Remaining gate: none for KFONT-M2-005. This is fixture-suite evidence only; it
does not claim malformed recovery support, complete SFNT conformance, scaler,
shaping, color glyph rendering, native engine parity, fallback, paragraph
layout, or GPU text-route support. Remaining format-14 family fixture gates stay
explicit in `fixture-evidence-manifest.json`.

### KFONT-M11 Readiness Gate Audit

Status: blocked wave documented after independent readiness review.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-004-wire-atlas-a8-artifact-route.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-006-add-gputextsubrunplan-splitting-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-007-add-resource-upload-instance-binding-plan-contracts.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-008-add-upload-before-sample-ordering-validation.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-010-add-materialkey-leakage-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/2026-06-16-kfont-m11-readiness-gates.md`

Evidence:

- The audit reviewed `origin/master`
  `10f8ffeb86783294760ea4854ccda2a2623c72ed`, M11 tickets, pure Kotlin text
  handoff specs, GPU text glyph pipeline specs, and current `font:gpu-api`
  contracts.
- `KFONT-M11-004` is blocked by missing M9 A8 mask and atlas
  entry/page/generation/invalidation evidence from `KFONT-M9-003` and
  `KFONT-M9-005`.
- `KFONT-M11-006`, `KFONT-M11-007`, `KFONT-M11-008`, `KFONT-M11-009`, and
  `KFONT-M11-010` are blocked by the missing A8 route and downstream
  subrun/resource/upload/binding contracts.
- wgsl4k evolution fixtures remain prerequisite validation evidence only; they
  do not provide real text route WGSL modules, `GPUTextBinding` comparisons,
  CPU/GPU/reference evidence, or product activation.

Validation:

```bash
rtk git diff --check
```

Remaining gate: unblock M11 by completing `KFONT-M9-003`, `KFONT-M9-004`, and
`KFONT-M9-005`, then re-evaluate `KFONT-M11-004`. This blocked wave does not
claim GPU text support, A8 atlas route support, SDF/outline/color/bitmap/SVG
text support, or retirement of `dftext`, `scaledemoji_rendering`, or
`coloremoji_blendmodes`.
