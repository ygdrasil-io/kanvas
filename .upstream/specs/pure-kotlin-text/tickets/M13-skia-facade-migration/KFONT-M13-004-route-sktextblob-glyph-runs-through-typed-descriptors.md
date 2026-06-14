---
id: "KFONT-M13-004"
title: "Route `SkTextBlob` glyph runs through typed descriptors"
status: "proposed"
milestone: "M13"
priority: "P1"
owner_area: "skia-facade"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M13-001", "KFONT-M9-002", "KFONT-M11-003"]
legacy_gate: ["dftext"]
---

# KFONT-M13-004 - Route `SkTextBlob` glyph runs through typed descriptors

## PM Note

Ce ticket transforme les glyph runs `SkTextBlob` en descriptors typés que le pipeline GPU peut auditer.

## Problem

`SkTextBlob` is a compatibility API for explicit glyph IDs and positions. It should bypass GSUB shaping, but it still needs typeface identity, glyph validation, metrics, synthetic clusters when source ranges exist, artifact planning, and GPU-safe descriptors. If the blob route keeps mutable `Sk*` objects or untyped glyph arrays past the facade, the GPU renderer cannot enforce the no-`Sk*` boundary or diagnose `dftext`/artifact gates.

## Scope

- Convert `SkTextBlob` glyph runs into `GlyphRunDescriptor` or equivalent typed descriptors with `TypefaceID`, glyph IDs, positions, advances, transform facts, style/material facts, and optional source text ranges.
- Validate glyph count, glyph ID bounds, position count, non-finite coordinates, source cluster mapping, and metrics availability through pure Kotlin contracts.
- Attach synthetic clusters for direct glyph runs when source text ranges are available; otherwise record an explicit cluster-bypass marker.
- Hand descriptors to glyph artifact planning and GPU handoff without leaking `SkFont`, `SkTypeface`, `SkTextBlob`, live handles, or full CPU-rendered text textures.
- Keep `dftext` visible until SDF/A8 artifact, atlas/cache, transform, GPU route, and diagnostics evidence are linked by the owning tickets.

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
data class SkTextBlobGlyphRunAdapter(
    val blobId: SkTextBlobID,
    val typefaceId: TypefaceID,
    val glyphIds: IntArray,
    val positions: List<TextPosition>,
    val sourceTextRanges: List<IntRange>?,
    val clusterPolicy: DirectGlyphClusterPolicy,
    val descriptor: GlyphRunDescriptor?,
    val diagnostics: List<RouteDiagnostic>,
)

data class SkTextBlobDescriptorDump(
    val blobHash: String,
    val descriptorId: GlyphRunDescriptorID,
    val glyphCount: Int,
    val typefaceId: TypefaceID,
    val artifactPlanRefs: List<String>,
    val noSkLeakage: Boolean,
)
```

## Acceptance Criteria

- [ ] Direct glyph runs produce typed `GlyphRunDescriptor` facts without invoking GSUB/GPOS shaping.
- [ ] Descriptor validation rejects mismatched glyph/position counts, invalid glyph IDs, missing metrics, non-finite positions, and unsupported cluster mappings with stable diagnostics.
- [ ] Descriptors can be consumed by glyph artifact planning and `DrawTextRun` handoff without `Sk*` object leakage.
- [ ] Dumps preserve deterministic glyph order, position order, typeface identity, and artifact plan references.
- [ ] The `dftext` legacy gate remains open unless typed descriptors are paired with SDF/artifact/GPU evidence required by the migration spec.

## Required Evidence

- `sktextblob-glyph-run-descriptors.json` covering positioned glyphs, RSXform-like positions if supported, missing source text ranges, synthetic clusters, and invalid glyph/position count fixtures.
- No-`Sk*` leakage report for descriptor, artifact planner input, and `DrawTextRun` payload.
- `draw-text-run-descriptor.json` or equivalent GPU handoff dump proving descriptors cross the boundary as typed artifacts.
- Diagnostic snapshots for invalid glyph ID, mismatched position count, non-finite position, missing metrics, and forbidden CPU-rendered texture fallback.
- Dashboard row for `dftext` showing remaining SDF/artifact/GPU evidence.

## Fallback / Refusal Behavior

- Invalid direct glyph runs refuse before artifact planning; they must not be reshaped from text as a fallback.
- Missing GPU artifact support emits glyph/text GPU diagnostics rather than replacing the blob with a CPU-rendered texture.
- Legacy gate(s) `dftext` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `SkTextBlob typed descriptor route`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless descriptor, artifact, no-leakage, and legacy gate evidence are attached.

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
- `legacy:dftext`
