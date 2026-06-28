---
id: KGPU-M34-003
title: "Variable font support"
status: blocked
milestone: M34
priority: P1
owner_area: text
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-003 - Variable font support

## PM Note

Les variable fonts sont résolues par le text stack ; le GPU renderer voit des
glyphs statiques.

## Problem

Variable fonts expose axis-tag/value pairs (weight, width, slant, optical
size, etc.) that the text stack must resolve into concrete glyph outlines.
The GPU renderer must define a contract for accepting per-run axis values and
diagnostic for out-of-range values, but must not perform outline generation,
HarfBuzz variation, or FreeType instance construction.

## Scope

- `GPUVariableFontInstancePlan` — per-run axis values (tag, value, precision).
- Route: axis values consumed by text stack, GPU receives resolved
  `GlyphArtifactPlan` (static glyphs only).
- Diagnostic for out-of-range axis values.

## Non-Goals

- No outline generation in `:gpu-renderer`.
- No HarfBuzz, FreeType, or CoreText variation support in `:gpu-renderer`.
- No variable font axis interpolation or instance construction.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Resolve mask format, normalize glyph pixels with padding, add glyphs to a DrawAtlas, and record pending atlas uploads.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUVariableFontAxis(
    val tag: String,
    val value: Float,
    val precision: Int,
)

data class GPUVariableFontInstancePlan(
    val fontKey: GPUFontKey,
    val axes: List<GPUVariableFontAxis>,
)
```

## Acceptance Criteria

- [ ] Text stack accepts axis-tag/value pairs and produces resolved glyphs.
- [ ] GPU treats resolved glyphs as static (no variation logic).
- [ ] Out-of-range axis value → `RefuseDiagnostic` with axis tag and value.

## Required Evidence

- `GPUVariableFontInstancePlan` dump with valid axis-tag/value pairs.
- Refusal fixture: out-of-range weight axis value.
- Static glyph rendering evidence: resolved glyph from variable font
  instance matches CPU reference.

## Fallback / Refusal Behavior

- Out-of-range axis value → clamp or `RefuseDiagnostic`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.text.variable-font`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*VariableFont*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack
  variable font resolution artifacts.
- `proposed → blocked` (2026-06-28): Blocked on pure-kotlin-text variable font resolution artifacts.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
