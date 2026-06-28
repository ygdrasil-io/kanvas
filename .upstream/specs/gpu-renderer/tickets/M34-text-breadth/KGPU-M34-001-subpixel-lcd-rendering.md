---
id: KGPU-M34-001
title: "Subpixel LCD rendering"
status: proposed
milestone: M34
priority: P0
owner_area: text
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-001 - Subpixel LCD rendering

## PM Note

Le rendu LCD subpixel (RGB/BGR) améliore la netteté du texte sur écrans à
géométrie de pixels connue.

## Problem

Standard grayscale A8 glyph atlas rendering cannot exploit the subpixel
geometry of LCD displays. Horizontal RGB/BGR stripe arrangements benefit from
per-component alpha modulation, while the GPU renderer currently has no
contract to discover pixel geometry, construct per-component coverage masks,
or emit a subpixel-aware WGSL render step.

## Scope

- `GPUSubpixelLCDPlan` — pixel geometry (RGB/BGR horizontal, VRGB/VBGR
  vertical), per-component coverage mask descriptor.
- `GPUSubpixelCoverageMask` — per-component R, G, B alpha atlas entry,
  sampled by the subpixel render step.
- `GPUSubpixelLCDRenderStep` — WGSL render step that modulates per-component
  alpha from the subpixel coverage mask.
- Route only when adapter reports pixel geometry AND target format is
  compatible (rgba8unorm accepted; others refused).

## Non-Goals

- No rotated display subpixel geometry.
- No translucent destination without destination-read.
- No subpixel positioning or hinting — pixel geometry only.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- `GFX-GLYPH-ATLAS` from `../GRAPHITE-ALGORITHM-REFERENCES.md` — study
  subpixel atlas entry allocation and component mask layout.
- `GFX-SUBPIXEL-RENDERSTEP` — study subpixel render step color modulation
  and per-component alpha blending.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
enum class GPUPixelGeometry {
    RGBHorizontal,
    BGRHorizontal,
    VRGBVertical,
    VBGRVertical,
}

data class GPUSubpixelLCDPlan(
    val pixelGeometry: GPUPixelGeometry,
    val perComponentMask: GPUSubpixelCoverageMask,
    val renderStep: GPUSubpixelLCDRenderStep,
)

data class GPUSubpixelCoverageMask(
    val atlasEntry: GPUAtlasEntryRef,
    val rComponent: Float,
    val gComponent: Float,
    val bComponent: Float,
)

data class GPUSubpixelLCDRenderStep(
    val modulation: GPUPerComponentAlphaModulation,
    val wgslModule: GPUSubpixelLCDWGSL,
)
```

## Acceptance Criteria

- [ ] At least one A8 glyph run promoted to RGB subpixel with CPU oracle
      parity.
- [ ] `rgba8unorm` target format accepted; other target formats refused.
- [ ] Adapter without pixel geometry → `RefuseDiagnostic`.
- [ ] Opaque-only destination accepted; translucent without destination-read
      refused.

## Required Evidence

- `GPUSubpixelLCDPlan` dump.
- CPU oracle comparison (per-component alpha values match within tolerance).
- Refusal fixtures:
  - Rotated display (unknown pixel geometry).
  - Unknown pixel geometry (adapter reports none).
  - Translucent destination.
  - Incompatible target format.
- WGSL validation (subpixel render step compiles and passes `wgsl4k`
  validation).

## Fallback / Refusal Behavior

- Unknown pixel geometry → `unsupported.text.subpixel_pixel_geometry`.
- Incompatible target format → `unsupported.text.subpixel_target_format`.
- Silent fallback to CPU-rendered text texture is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.text.subpixel-lcd`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SubpixelLCD*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
