---
id: "KFONT-M13-002"
title: "Route `SkTypeface` OpenType facts through pure Kotlin core"
status: "blocked"
milestone: "M13"
priority: "P1"
owner_area: "skia-facade"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M13-001", "KFONT-M1-003", "KFONT-M2-004"]
legacy_gate: ["typeface"]
---

# KFONT-M13-002 - Route `SkTypeface` OpenType facts through pure Kotlin core

## PM Note

Ce ticket fait de `SkTypeface` une vue de compatibilité sur les faits OpenType pure Kotlin, au lieu d'un chemin font séparé.

## Problem

`SkTypeface` exposes family/style facts, collection index, table access, glyph-affecting identity, variation and palette data, and metrics used by compatibility APIs. If those facts are produced by facade-specific parsing, M13 keeps two sources of truth. The facade must instead adapt pure Kotlin `FontSourceID`, `TypefaceID`, SFNT/OpenType table facts, and scaler metrics, while preserving deterministic refusals for unsupported or malformed font data.

## Scope

- Route `SkTypeface` construction and inspection through pure Kotlin font core contracts: `FontSourceID`, `TypefaceID`, table directory facts, family/style metadata, collection index, variation coordinates, palette facts, and scaler kind.
- Map supported OpenType table queries to bounded pure Kotlin SFNT table access; unsupported or malformed tables must return stable facade/core diagnostics.
- Preserve deterministic behavior for bundled/generated fixtures and mark host-dependent system scans as non-normative unless bytes are captured.
- Emit facade parity dumps that compare public `SkTypeface` facts with core `typeface-id.json`, `sfnt-directory.json`, and `sfnt-tables.json` facts.
- Keep legacy `typeface` gate visible until the facade and core dumps prove the same scoped behavior.

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
data class SkTypefaceAdapter(
    val skTypefaceHandle: SkTypefaceHandle,
    val fontSourceId: FontSourceID,
    val typefaceId: TypefaceID,
    val tableFacts: OpenTypeTableFacts,
    val scalerKind: ScalerKind,
    val diagnostics: List<RouteDiagnostic>,
)

data class SkTypefaceOpenTypeFactsDump(
    val postScriptName: String?,
    val familyName: String?,
    val styleFacts: TypefaceStyleFacts,
    val collectionIndex: Int,
    val variationCoordinates: Map<String, Double>,
    val paletteId: String?,
    val tableTags: List<String>,
    val coreDumpRefs: List<String>,
)
```

## Acceptance Criteria

- [ ] `SkTypeface` fixture construction records the same `FontSourceID` and `TypefaceID` as the pure Kotlin core for the same captured bytes.
- [ ] Public facade facts for family/style, collection index, variation coordinates, palette, table tags, and scaler kind are derived from core table/scaler facts.
- [ ] Malformed or missing tables propagate core diagnostics instead of being hidden by facade defaults.
- [ ] Host-dependent system typefaces are marked non-normative unless the source bytes are captured as fixtures.
- [ ] The `typeface` legacy gate remains open until facade/core dumps, diagnostics, tests, and dashboard evidence are linked.

## Required Evidence

- `sktypeface-opentype-facts.json` for bundled TTF, TTC, OTF/CFF, variable font, malformed directory, and missing required table fixtures.
- Parity dump linking each facade fact to `typeface-id.json`, `sfnt-directory.json`, and `sfnt-tables.json` core dumps.
- Diagnostic snapshots for malformed directory, missing required table, unsupported table request, and host-dependent system scan.
- `typeface` legacy gate dashboard row showing current status and required retirement evidence.

## Fallback / Refusal Behavior

- Unsupported OpenType facts return facade diagnostics that preserve the core refusal reason; no native parser fills missing facts.
- Missing captured bytes for system fonts mark the route host-dependent rather than deterministic support.
- Legacy gate(s) `typeface` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `SkTypeface core facts route`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless facade/core parity evidence and legacy gate retirement evidence are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- `blocked` (2026-06-19): Readiness audit confirmed that `KFONT-M1-003` and
  `KFONT-M2-004` are `done`, but this route must stay behind
  `KFONT-M13-001`. Remaining gate: land the facade adapter inventory first so
  the `SkTypeface` route has an approved owner mapping, diagnostics surface,
  legacy `typeface` gate row, and PM/dashboard classification before parity
  dumps or facade-core route promotion begin.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M13`
- `area:skia-facade`
- `claim:tracked-gap`
- `legacy:typeface`
