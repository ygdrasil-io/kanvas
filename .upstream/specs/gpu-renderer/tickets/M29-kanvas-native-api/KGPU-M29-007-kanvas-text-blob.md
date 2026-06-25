---
id: KGPU-M29-007
title: "KanvasTextBlob — glyphRun + positioning"
status: proposed
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-009]
legacy_gate: null
---

# KGPU-M29-007 - KanvasTextBlob — glyphRun + positioning

## PM Note

`KanvasTextBlob` integre les artefacts de texte purement Kotlin du pipeline
`:font` dans l'API native Kanvas. Il remplace `SkTextBlob` et permet au PM de
voir du texte rendu via les glyph runs GPU sans Skia.

## Problem

`KanvasCanvas.drawText()` needs a text blob representation. The font pipeline
(M12, M6) produces typed glyph runs but has no Kanvas-native API integration.
`KanvasTextBlob` bridges typed glyph runs to GPU text rendering through the
native API.

## Scope

- Define `KanvasTextBlob` as an immutable glyph-run container
- Implement `KanvasTextBlob(glyphRuns: List<KanvasGlyphRun>)` constructor
- Define `KanvasGlyphRun` with glyph IDs, positions, and font reference
- Wire glyph-run data to GPU text pipeline (atlas lookup, SDF, or A8)
- Use M12 font pipeline for glyph metrics and positioning

## Non-Goals

- No text shaping or layout (font pipeline owns shaping)
- No font loading or management (M12 owns fonts)
- No text measurement or metrics API
- No emoji or color font support (future milestone)
- No SkTextBlob interop (M30 owns bridge)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M6-text-glyph-handoff/README.md`
- `.upstream/specs/gpu-renderer/tickets/M20-text-a8-sdf-glyph-atlas/README.md`
- `.upstream/specs/font/README.md`

## Design Sketch

```kotlin
class KanvasTextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
)

data class KanvasGlyphRun(
    val font: KanvasFont,
    val glyphs: List<UShort>,
    val positions: List<Point>,
)
```

## Acceptance Criteria

- [ ] `KanvasTextBlob` compiles with a list of `KanvasGlyphRun`
- [ ] `KanvasGlyphRun` holds glyph IDs, positions, and font reference
- [ ] Glyph-run data is serializable to GPU atlas-lookup commands
- [ ] Empty text blob emits stable diagnostic

## Required Evidence

- `KanvasTextBlob.kt` committed
- Glyph-run-to-GPU-command dump transcript
- Atlas-lookup dispatch confirmation
- Diagnostic output for empty or unsupported text blobs

## Fallback / Refusal Behavior

Empty glyph runs emit `empty-text-blob` diagnostic and produce no draw commands.
Missing fonts emit `font-not-found` diagnostic. No CPU text rasterization fallback
and no Skia text rendering fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-text-blob`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-api:test
rtk ./gradlew --no-daemon :font:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas-api`
