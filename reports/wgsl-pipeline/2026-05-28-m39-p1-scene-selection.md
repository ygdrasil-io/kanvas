# M39 P1 Route Scene Selection

Date: 2026-05-28
Linear: GRA-175
Milestone: M39 Pipeline Route Convergence

## Scope

GRA-175 selects the P1 dashboard scenes for M39 route convergence work. The
selection intentionally avoids duplicating existing P0/M37/M38 dashboard rows
unless the route diagnostic is distinct from the existing scene.

Existing covered rows remain:

- `solid-rect`, `bitmap-rect-nearest`, and `analytic-aa-convex` as P0 baseline
  dashboard evidence.
- `path-aa-edge-budget-boundary` as the M37 stable expected-unsupported coverage
  breadth row.
- `crop-image-filter-nonnull-prepass` as the M38 promoted image-filter pre-pass
  route row.

## Selected P1 Scenes

| Scene | Implementation ticket | Source test | CPU route expectation | GPU route expectation | Diff / similarity policy | Fallback policy |
|---|---|---|---|---|---|---|
| `linear-gradient-rect` | GRA-185 | `LinearGradientRectTest#horizontal red-to-blue gradient interpolates across the rect()` plus `GeneratedLinearGradientWgslTest` | `cpu.shader.linear-gradient.rect` with scalar reference samples | `webgpu.generated.linear-gradient.rect` with generated WGSL/parser evidence | Reuse gradient sample tolerance from the existing generated-gradient tests; dashboard stats must record pixel counts and max channel deltas. | No fallback for the selected rect route; any unsupported route must be a distinct non-selected scene/reason. |
| `src-over-stack` | GRA-185 | `BlendModeTest#kSrcOver matches CPU for partial alpha over non opaque destination()` plus `TranslucentSrcOverTest` | `cpu.blend.src-over-stack` byte oracle with tolerance `<=1` | `webgpu.blend.src-over.fixed-function` / layer-composite route | Byte-level CPU/GPU comparison with tolerance `<=1`; dashboard stats must include matching pixels and max channel deltas. | No fallback for the selected SrcOver stack; unsupported blend variants remain outside this row. |
| `runtime-effect-simple` | GRA-186 | `RuntimeEffectDescriptorWebGpuTest#SimpleRT runtime shader renders through descriptor-backed WGSL path()` | `cpu.runtime-effect.descriptor.simple_rt` | `webgpu.runtime-effect.descriptor.simple_rt` with registered WGSL descriptor and parser validation | Reuse the descriptor-backed runtime-effect oracle; dashboard stats must include parser/descriptor evidence and pixel similarity. | No arbitrary SkSL compilation; only registered descriptors qualify for this row. |
| `clip-rect-difference` | GRA-186 | `ClipDifferenceCrossTest#Skbug9319GM survives clipRect_kDifference and clipRRect_kDifference on GPU()` | `cpu.coverage.clip-rect-difference` through the coverage fallback oracle | `webgpu.coverage.clip-rect-difference`, or stable unsupported reason for non-selected AA clip variants | Dashboard must show route diagnostics even if the chosen GPU path is a stable refusal; CPU/reference artifacts must still be present. | Non-selected AA clip variants may stay expected-unsupported with a stable reason; do not hide them as similarity failures. |
| `bitmap-shader-local-matrix` | GRA-186 | `BitmapShaderRotatedTest#rotated localMatrix on axis-aligned rect rotates the bitmap pattern()` | `cpu.shader.bitmap.local-matrix` with opaque sampling payload handoff | `webgpu.shader.bitmap.local-matrix` with route JSON showing local-matrix inverse/remap state | Pixel similarity must be reported against the existing rotated bitmap reference; route JSON must expose the local-matrix state that distinguishes it from P0 bitmap nearest. | Unsupported bitmap shader variants must be reasoned separately; this row is only the rotated local-matrix rect route. |

## Implementation Mapping

- GRA-185 owns the paint/blend rows: `linear-gradient-rect` and
  `src-over-stack`.
- GRA-186 owns the runtime-effect, clip, and local-matrix rows:
  `runtime-effect-simple`, `clip-rect-difference`, and
  `bitmap-shader-local-matrix`.
- GRA-187 closes the route convergence dashboard evidence across the P0,
  M37/M38, and new M39 P1 rows.

## Validation

Required validation for this selection ticket:

```bash
rtk git diff --check
```

No raster or dashboard artifact generation is required in GRA-175 because this
ticket only selects implementation-ready P1 scene contracts. Artifact generation
belongs to GRA-185, GRA-186, and GRA-187.
