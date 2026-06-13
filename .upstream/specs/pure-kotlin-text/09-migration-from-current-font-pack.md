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

`.upstream/specs/font/` remains active as current-state evidence. It records:

- current OpenType backend scope;
- current simple text and `SkShaper` boundary;
- current glyph representation gates;
- current color font and emoji gates;
- current validation and GM classification policy.

This new pack records the complete target. When the two packs differ, the
current pack describes what is true now and this pack describes what must
become true before final support claims.

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
| Font fixtures in `kanvas-skia` and `skia-integration-tests` | Candidate fixtures after provenance and target route review. |
| `.upstream/specs/font/` | Source of current gates and stable blocker names. |

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

- `.upstream/specs/font/` for current-state changes;
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
