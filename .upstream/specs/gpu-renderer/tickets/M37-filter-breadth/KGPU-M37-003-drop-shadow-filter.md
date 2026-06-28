---
id: KGPU-M37-003
title: "Drop shadow filter"
status: proposed
milestone: M37
priority: P1
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001, KGPU-M37-001]
legacy_gate: null
---

# KGPU-M37-003 - Drop shadow filter

## PM Note

L'ombre portee est le premier filtre compose M37. Il reutilise le blur
separable de M37-001 et ajoute le masque alpha, le decalage et la composition
SrcOver. C'est le filtre le plus demande par les designers pour les interfaces.

## Problem

Current GPU renderer has no drop shadow filter route. Drop shadow requires three
coordinated operations: alpha mask extraction, separable blur of the mask,
offset translation, and SrcOver composition of the blurred shadow behind the
original source. Without M37-001 blur as a dependency, the shadow blur pass
cannot be implemented. Drop shadow is the highest-impact compound filter for
UI compositing.

## Scope

- `GPUDropShadowPlan` — offset (dx, dy in logical pixels), sigma (X and Y,
  independently), shadow color (RGBA), mode (shadow-only or composite with
  source), tile mode.
- `GPUDropShadowMaskPlan` — alpha channel extraction from source texture,
  producing a single-channel mask used as input to the blur pass.
- `GPUDropShadowBlurPlan` — wraps `GPUSeparableBlurPlan` from M37-001, applying
  the separable blur to the extracted alpha mask with the specified sigma.
- `GPUDropShadowCompositePlan` — multiplies shadow color by blurred alpha mask
  (producing colored shadow), then composites source over shadow using SrcOver
  blend mode with offset translation.

## Non-Goals

- Do not implement inner shadow or inset shadow variants.
- Do not implement multiple shadow layers per element.
- Do not implement shadow spread/choke (morphology-based expansion) — that is a
  future M37-002 composition.
- Do not activate product routing for drop shadow.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-DROPSHADOW-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dropshadow-backend) — Study Graphite drop shadow pipeline decomposition into blur, offset, and composite passes.
- [`GFX-BLUR-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-blur-backend) — Inherited from M37-001; re-examine intermediate texture lifetime across shadow passes.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUDropShadowPlan(
    val offsetDx: Float,
    val offsetDy: Float,
    val sigmaX: Float,
    val sigmaY: Float,
    val shadowColor: GPUColor,
    val mode: GPUDropShadowMode, // ShadowOnly, Composite
    val tileMode: GPUTileMode,
)

data class GPUDropShadowMaskPlan(
    val sourceBinding: GPUTextureBinding,
    val maskOutput: GPUBlurIntermediateArtifact, // single-channel R8
)

data class GPUDropShadowBlurPlan(
    val blur: GPUSeparableBlurPlan, // reuses M37-001 contract
    val maskInput: GPUBlurIntermediateArtifact,
    val blurredOutput: GPUBlurIntermediateArtifact,
)

data class GPUDropShadowCompositePlan(
    val shadowBinding: GPUTextureBinding, // blurred + colored shadow
    val sourceBinding: GPUTextureBinding,
    val targetBinding: GPUTextureBinding,
    val offset: GPUOffset2D,
)

enum class GPUDropShadowMode { ShadowOnly, Composite }
```

### Route

```
DropShadow(offset, sigma, color, mode) → drop shadow plan
    → alpha mask extraction (source → single-channel R8 mask)
    → blur reuse (mask → GPUSeparableBlurPlan from M37-001 with sigmaX, sigmaY)
    → shadow coloring (blurred mask × shadowColor → shadow texture)
    → if mode == Composite:
        → composite (shadow at offset behind source, SrcOver)
    → if mode == ShadowOnly:
        → output shadow texture only
    → result

Blur unavailable → refusal (unsupported.filter.drop_shadow_blur_unavailable)
```

## Acceptance Criteria

- [ ] Offset (5, 5) pixels, sigma = 2.0, black shadow color, Composite mode
      produces correct drop shadow matching CPU oracle: shadow appears behind
      source at the correct offset with correct blur spread.
- [ ] Shadow-only mode produces only the blurred colored shadow with no source
      pixels, correct offset position.
- [ ] SrcOver composition correctly places source pixels over shadow pixels;
      transparent source regions show shadow, opaque source regions occlude
      shadow.
- [ ] Refusal is emitted with stable diagnostic when M37-001 blur contracts are
      unavailable or incomplete.
- [ ] Sigma = 0 for both X and Y produces a crisp unblurred shadow (identity
      mask pass, no intermediate blur allocation).
- [ ] Offset (0, 0) produces shadow directly behind source with shadow fully
      occluded by source in Composite mode (shadow not visible, but correct
      pipeline execution).

## Required Evidence

- Contract tests for drop shadow with offsets [(0,0), (5,5), (-3,2)] and sigma
  [0, 2.0, 5.0].
- CPU oracle parity dumps: per-pixel MSE < 1e-3 for shadow color channels at
  sigma 2.0.
- Refusal fixture when `GPUSeparableBlurPlan` is not registered.
- Intermediate artifact dumps: alpha mask, blurred mask, colored shadow, final
  composite.
- WGSL validation for mask extraction, shadow coloring, and SrcOver composite
  shader modules.

## Fallback / Refusal Behavior

- Blur contracts from M37-001 unavailable:
  `unsupported.filter.drop_shadow_blur_unavailable` diagnostic.
- Intermediate texture budget exceeded:
  `unsupported.filter.drop_shadow_intermediate_budget` diagnostic.
- Source texture is not compatible with alpha extraction:
  `unsupported.filter.drop_shadow_source_format` diagnostic.
- No silent CPU-rendered complete drop-shadow-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.drop-shadow`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DropShadow*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M37`
- `area:filters`
