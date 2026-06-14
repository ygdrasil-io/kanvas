---
id: "KFONT-M13-003"
title: "Route explicit `SkShaper` APIs through pure Kotlin shaping"
status: "proposed"
milestone: "M13"
priority: "P1"
owner_area: "skia-facade"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M13-001", "KFONT-M5-005", "KFONT-M6-010", "KFONT-M7-004"]
legacy_gate: ["scaledemoji", "scaledemoji_rendering"]
---

# KFONT-M13-003 - Route explicit `SkShaper` APIs through pure Kotlin shaping

## PM Note

Ce ticket connecte les appels `SkShaper` explicites au moteur de shaping pure Kotlin, avec refus clairs pour les scripts ou emoji non prouvés.

## Problem

`SkShaper` is the compatibility boundary for complex shaping, but it must not become an independent shaping implementation or silently broaden `SkCanvas.drawString`. The facade needs to pass text, font, script/language, feature, direction, fallback, and cluster data into the pure Kotlin shaping engine, then expose shaped glyph runs with the same deterministic diagnostics and evidence required by the script matrix.

## Scope

- Adapt explicit `SkShaper` text APIs to pure Kotlin shaping requests with text range, `TypefaceID`, script, language, direction, OpenType features, variation coordinates, fallback policy, and cluster output.
- Preserve the distinction between explicit shaping APIs and the simple `SkCanvas.drawString` route.
- Map shaped output to `ShapedGlyphRun` facts: glyph IDs, clusters, positions, direction, script/language, enabled/disabled features, fallback runs, and diagnostics.
- Cover required script matrix slices that are supported by dependencies and emit stable refusals for unsupported script phases, emoji sequences, fallback gaps, or missing paragraph-level bidi context.
- Keep `scaledemoji` and `scaledemoji_rendering` gates visible until shaping, fallback, color glyph dispatch, artifact route, and dashboard evidence all exist.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class SkShaperFacadeRequest(
    val text: String,
    val textRange: IntRange,
    val typefaceId: TypefaceID,
    val script: OpenTypeScriptTag?,
    val language: Bcp47LanguageTag?,
    val direction: TextDirection,
    val features: List<OpenTypeFeatureSetting>,
    val fallbackPolicy: FallbackPolicy,
)

data class SkShaperAdapter(
    val request: SkShaperFacadeRequest,
    val shapedRun: ShapedGlyphRun?,
    val facadeClusters: List<FacadeClusterRange>,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Explicit `SkShaper` calls delegate to pure Kotlin shaping and expose `ShapedGlyphRun` glyph IDs, clusters, positions, direction, script/language, features, fallback runs, and diagnostics.
- [ ] Unsupported scripts, unsupported OpenType features, missing fallback, emoji sequence gaps, and paragraph-only bidi requirements produce stable `text.shaping.*` diagnostics through the facade.
- [ ] `SkCanvas.drawString` behavior is unchanged by this ticket and remains the simple deterministic path.
- [ ] Facade cluster ranges match pure Kotlin cluster ranges for every supported fixture.
- [ ] Legacy gates `scaledemoji` and `scaledemoji_rendering` remain open until shaping plus glyph/color/GPU evidence required by those gates is linked.

## Required Evidence

- `skshaper-shaping-dump.json` for Latin ligature/kerning, Arabic joining, Devanagari reordering, mixed bidi, fallback split, and emoji VS/ZWJ or explicit refusal fixtures.
- Facade/core parity dump comparing `SkShaper` output with pure Kotlin `ShapedGlyphRun` dumps for glyph IDs, clusters, positions, features, fallback runs, and diagnostics.
- Diagnostic snapshots for `text.shaping.feature-unsupported`, `text.shaping.emoji-sequence-unsupported`, `text.shaping.fallback-missing`, and paragraph-context bidi refusal.
- Dashboard rows for `scaledemoji` and `scaledemoji_rendering` showing remaining evidence requirements.

## Fallback / Refusal Behavior

- Unsupported shaping requests refuse by text range and diagnostic; they do not call HarfBuzz, CoreText, DirectWrite, or platform shapers.
- Emoji sequences are refused as sequences when unsupported, not approximated as unrelated individual code points.
- Legacy gate(s) `scaledemoji`, `scaledemoji_rendering` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `SkShaper pure Kotlin route`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless supported script and legacy gate evidence are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M13`
- `area:skia-facade`
- `claim:tracked-gap`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
