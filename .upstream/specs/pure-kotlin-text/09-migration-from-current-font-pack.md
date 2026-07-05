# Migration From Current Font Pack

Status: Draft
Date: 2026-06-13

## Purpose

Define how current font specs, code, tests, reports, and refusals migrate
toward the complete pure Kotlin text target.

This file is not an implementation plan. It identifies which current assets are
reusable, which contracts need replacement, and what evidence is required
before old gates can be retired.

## Current Pack Role

The older font pack is transitional current-state evidence. It is expected to
be retired once this pack carries the durable gates, blocker names, migration
rules, and validation taxonomy needed for the complete target.

This pack records the complete target. Current code can be reused as prototype
or migration evidence, but current behavior does not become complete support
until it satisfies this pack's contracts and evidence rules.

## Reusable Current Assets

Reusable prototypes and evidence:

| Asset | Migration role |
|---|---|
| `OpenTypeFont.kt` | Starting point for SFNT parsing, TrueType `glyf`, current variation and color metadata slices. |
| `OpenTypeFontMgr` / `OpenTypeTypeface` | Starting point for pure Kotlin typeface construction and table access. |
| `OpenTypeSystemFontMgr` | Starting point for pure Kotlin system scanning and fallback policy. |
| `SkShaper.kt` | Existing explicit shaping boundary and diagnostics model, not complete shaping engine. |
| `SkCpuGlyphCache.kt` | Prototype for A8 glyph inventory, mask dumps, key preimages, and CPU oracle hashes. |
| `SkWebGpuGlyphAtlas.kt` | Prototype for atlas upload-plan evidence, not final GPU renderer artifact API. |
| Font fixtures in `reports/font/fixtures/` and `integration-tests/skia/` | Candidate fixtures after provenance and target route review. |

## Durable Legacy Gates

These gates must remain visible after the older font pack disappears. They are
not support claims; they are blocker rows that must either be retired with
evidence or kept with explicit refusal policy.

| Gate | Target owner | Required retirement evidence |
|---|---|---|
| `coloremoji_blendmodes` | `05-color-fonts-bitmap-svg-emoji.md`, `06-gpu-renderer-handoff.md` | COLR/emoji color route, blend/composite policy, CPU oracle, GPU artifact when GPU is claimed. |
| `scaledemoji` | `02-opentype-layout-shaping-engine.md`, `05-color-fonts-bitmap-svg-emoji.md` | Emoji sequence shaping, emoji fallback, selected color glyph representation, route diagnostics. |
| `scaledemoji_rendering` | `05-color-fonts-bitmap-svg-emoji.md`, `06-gpu-renderer-handoff.md` | Emoji glyph artifact plan, renderer route evidence, unsupported sequence refusals. |
| `dftext` | `04-glyph-representation-and-artifacts.md`, `06-gpu-renderer-handoff.md`, `08-performance-budgets-and-telemetry.md` | SDF artifact contract, atlas/cache telemetry, transform policy, CPU/GPU evidence for the claimed slice. |
| `fontations` | `07-validation-conformance-and-drift.md` | Remains `drift-only` or `expected-unsupported`; no Fontations dependency is allowed for normative support. |
| `fontations_ft_compare` | `07-validation-conformance-and-drift.md` | Optional drift report only; FreeType/Fontations parity is not a product oracle. |
| `pdf_never_embed` | Adjacent PDF/font-subset workstream | Keep separate from the pure Kotlin runtime font core unless a future PDF subset spec adopts it. |

Rows such as `gammatext`, `dftext_blob_persp`, and `typeface` may provide useful
regression evidence, but they must not be used to imply broad text, SDF,
perspective, Type1/PFA/PFB, or FreeType parity support.

## Baselines To Preserve

The following baselines remain useful after legacy spec retirement:

| Baseline | How to use it |
|---|---|
| Bundled Liberation families | Deterministic bundled font baseline for family/style matching, fallback, and simple text fixtures. |
| Tiny generated fonts | Preferred parser/scaler/shaping/color fixtures when license-compatible and easier to audit than external binaries. |
| Simple `drawString` path rendering | Current compatibility behavior; keep simple and deterministic, not a complex shaping claim. |
| `drawTextBlob` glyph-run rendering | Compatibility route for explicit glyph IDs and positions; migrate toward typed glyph run descriptors. |
| Simple WebGPU text smoke evidence | Current proof that outline/path text can reach WebGPU; not proof of broad glyph atlas or shaping support. |
| CPU glyph mask inventory | Prototype evidence for future A8 artifacts, key preimages, and oracle hashes. |

## Contracts To Replace Or Promote

Current contracts that need promotion before complete-target support:

- current `SkShaper` feature approximations -> pure Kotlin GSUB/GPOS shaping
  engine with script matrix evidence;
- current simple glyph cache -> `GlyphArtifactPlanner` with A8/SDF/color/SVG
  representation decisions;
- current `SkWebGpuGlyphAtlas` -> typed `GlyphAtlasArtifact` and
  `SDFGlyphAtlasArtifact` registered with GPU renderer artifact policy;
- current color metadata parsing -> complete COLRv1, PNG bitmap glyph, and SVG
  glyph rendering plans;
- current system fallback policy -> variable-axis-aware, script/locale/emoji
  fallback catalog;
- current docs page -> split target specs plus current-state evidence links.

## Refusal Retirement Rule

An old refusal or gate can be retired only when the new route has:

- owning target spec section;
- implementation tests;
- generated or bundled fixture provenance;
- semantic dump;
- CPU oracle evidence;
- GPU evidence if GPU support is claimed;
- stable diagnostics for remaining unsupported subcases;
- dashboard or report update that names the new support scope.

No gate is retired by substituting:

- native font engine behavior;
- platform fallback;
- CPU-rendered texture compatibility;
- broad visual tolerances;
- untyped CPU preparation;
- metadata parsing without rendering support.

## Legacy Diagnostic Mapping

Older diagnostics should be mapped into the target taxonomy before support is
promoted.

| Legacy diagnostic | Target classification |
|---|---|
| `font.native-engine-unavailable` | `expected-unsupported` or `drift-only` for native FreeType/Fontations parity requests. |
| `font.bitmap-strike-unavailable` | `text.color.bitmap-strike-unavailable` or equivalent route-specific bitmap refusal. |
| `font.bitmap-glyph-decode-unavailable` | `text.color.bitmap-glyph-decode-unavailable` until the PNG decoder and artifact route are proved. |
| `font.bitmap-glyph-format-unsupported` | `expected-unsupported` for non-PNG payloads unless a future spec expands codec scope. |
| `font.emoji-table-dispatch-unavailable` | `text.shaping.emoji-sequence-unsupported` or `text.color.route-unavailable`, depending on blocker. |
| `font.emoji-sequence-shaping-unsupported` | `text.shaping.emoji-sequence-unsupported`. |
| `font.emoji-fallback-unavailable` | `text.shaping.fallback-unavailable` with emoji-capable face facts. |
| `STUB.FONTATIONS` | `drift-only` or `expected-unsupported`; never a product dependency gate. |
| `STUB.EMOJI_TABLES` | Split into shaping, fallback, COLR/bitmap/SVG, and GPU route blockers. |
| `STUB.DF_TEXT_FULL_GM` | Split into SDF artifact, atlas/cache, transform, and GPU route blockers. |
| `STUB.PDF_TABLE_SUBSET_FONTMGR` | Adjacent PDF subset workstream, not pure Kotlin runtime font support. |

## Migration Categories

| Category | Meaning |
|---|---|
| `reuse-as-is` | Current behavior already matches complete target contract after evidence is linked. |
| `promote-with-contract` | Current behavior is useful but needs value-object contract, diagnostics, and tests. |
| `replace` | Current prototype is too narrow and should be replaced by target subsystem. |
| `keep-current-gate` | Current refusal remains until target implementation exists. |
| `expected-unsupported` | Behavior is outside complete Kanvas target and should keep durable refusal. |

## Suggested First Contract Promotions

These promotions are useful before implementation slicing:

1. Define `FontSourceID`, `TypefaceID`, and `GlyphStrikeKey` value objects.
2. Define `TextLayoutResult`, `ShapedGlyphRun`, and `GlyphRunDescriptor`
   dumps.
3. Define `GlyphArtifactPlan`, `GlyphAtlasArtifact`, and
   `SDFGlyphAtlasArtifact`.
4. Define text route diagnostics shared by current and new paths.
5. Add current-code adapters that can emit target-shaped dumps without changing
   rendering support claims.

These are contract promotions, not claims that final shaping/color/SVG/SDF
support is implemented.

## Relationship To GPU Renderer Migration

The GPU renderer text family remains `DependencyGated` until this pack provides
artifact contracts and tests. GPU renderer integration should happen only after:

- text command payloads are free of `Sk*` objects;
- artifacts are registered typed `CPUPreparedGPU` artifacts;
- route diagnostics are deterministic;
- atlas generation and upload facts are explicit;
- CPU-rendered texture compatibility remains forbidden.

## Documentation Updates

When implementation work starts, update:

- this pack for complete-target contract changes;
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md` only when
  the text dependency gate changes;
- validation reports with support/refusal evidence.

## Acceptance Criteria

- Current and target docs do not contradict each other about what is supported
  today.
- Reused current code is identified as prototype, promoted contract, or final
  behavior.
- Old font gates remain visible until evidence retires them.
- GPU renderer text integration waits for typed artifacts and deterministic
  diagnostics.
- The durable gates listed here are sufficient to retire the older font specs
  without losing blocker traceability.
