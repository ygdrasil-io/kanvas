# M60 Target-Color Candidate Inventory - 2026-06-02

Linear: `FOR-234`

## Decision

`targetColorSpaceBlend` remains an opt-in diagnostic mode. No scene is
promoted by this inventory, and no global rendering path is changed.

## Summary

| Metric | Value |
|---|---:|
| Scenes inspected | `34` |
| Candidate scenes | `1` |
| Diagnostic fixtures | `1` |
| Non-candidate scenes | `32` |
| Promotable scenes by exact `99.95%` proof | `0` |

## Candidate Scenes

| Scene | Decision | Exact | Target exact | Tol. 8 | Cause | WebGPU route | Reason |
|---|---|---:|---:|---:|---|---|---|
| `m60-bounded-stroke-cap-join` | `candidate-residual-aa` | 0.00 | 95.91 | 99.96 | `coverage.stroke-cap-join-aa-residual` | `webgpu.coverage.refuse` | targetColorSpaceBlend improves the scene into tolerance-8 parity, but exact parity remains below 99.95 due to the recorded AA residual |

## Diagnostic Fixtures

| Scene | Decision | Exact | Target exact | Tol. 8 | Cause | WebGPU route | Reason |
|---|---|---:|---:|---:|---|---|---|
| `m60-target-colorspace-neutral-aa` | `diagnostic-fixture` | n/a | n/a | n/a | `none` | `missing-route-gpu.json` | isolated neutral AA fixture proves targetColorSpaceBlend can match the CPU sample for the covered solid-color AA path |

## Non-Candidate Scenes

| Scene | Decision | Exact | Target exact | Tol. 8 | Cause | WebGPU route | Reason |
|---|---|---:|---:|---:|---|---|---|
| `analytic-aa-convex` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.coverage.path-convex-fan` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `bitmap-rect-nearest` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.image-rect.strict-nearest` | bitmap shader path is outside the FOR-234 solid-color AA scope |
| `bitmap-shader-local-matrix` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.shader.bitmap.local-matrix` | bitmap shader path is outside the FOR-234 solid-color AA scope |
| `bitmap-shader-repeat-tile` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.shader.bitmap.repeat-tile` | bitmap shader path is outside the FOR-234 solid-color AA scope |
| `bitmap-subset-local-matrix-repeat` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.shader.bitmap.subset-local-matrix-repeat` | bitmap shader path is outside the FOR-234 solid-color AA scope |
| `clip-rect-difference` | `not-candidate-no-target-color-signal` | 84.44 | n/a | n/a | `none` | `webgpu.coverage.clip-difference.analytic-rrect-mask` | exact score is below 99.95, but artifacts provide no tolerance-8/color-space signal for the target blend pilot |
| `crop-image-filter-nonnull-prepass` | `not-candidate-out-of-scope` | 98.13 | n/a | n/a | `none` | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` | image-filter path is outside the FOR-234 solid-color AA scope |
| `draw-paint-clipped-rect` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.paint.draw-paint.clip-rect` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `draw-paint-full-clip` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.paint.draw-paint.full-clip` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `font-complex-shaping-refusal` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `font.complex-shaping-requires-explicit-shaper` | `webgpu.text.refuse` | font/text path is outside the FOR-234 solid-color AA scope |
| `font-emoji-color-glyph-refusal` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `font.color-glyph-emoji-unsupported` | `webgpu.text.refuse` | font/text path is outside the FOR-234 solid-color AA scope |
| `font-kerning-style-fixture` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `none` | `webgpu.text.outline.kerning-style-fixture` | font/text path is outside the FOR-234 solid-color AA scope |
| `font-latin-outline-drawstring` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `none` | `webgpu.text.outline.simple-latin` | font/text path is outside the FOR-234 solid-color AA scope |
| `font-textblob-positioned-glyph-run` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `none` | `webgpu.text.outline.positioned-glyph-run` | font/text path is outside the FOR-234 solid-color AA scope |
| `gradient-color-filter-linear-kplus` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.generated.linear-gradient.color-filter.blend-kplus` | gradient path is outside the FOR-234 solid-color AA scope |
| `image-filter-compose-cf-matrix-transform` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` | image-filter path is outside the FOR-234 solid-color AA scope |
| `image-filter-crop-nonnull-prepass-required` | `not-candidate-out-of-scope` | n/a | n/a | n/a | `image-filter.crop-input-nonnull-prepass-required` | `webgpu.image-filter.refuse` | image-filter path is outside the FOR-234 solid-color AA scope |
| `linear-gradient-rect` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.generated.linear-gradient.rect` | gradient path is outside the FOR-234 solid-color AA scope |
| `m57-aaclip-bounded-grid` | `not-candidate-target-blend-negative` | 98.83 | 94.71 | 94.73 | `none` | `webgpu.coverage.aaclip-bounded-grid` | targetColorSpaceBlend evidence exists, but the normal route is not an expected-unsupported candidate and the target-blend render does not reach the exact support threshold |
| `m60-bounded-nested-rrect-clip` | `not-candidate-stable-refusal` | 71.22 | n/a | n/a | `coverage.nested-clip-visual-parity-below-threshold` | `webgpu.coverage.nested-rrect-clip.expected-unsupported` | current route is an expected-unsupported refusal with stable cause `coverage.nested-clip-visual-parity-below-threshold` and no targetColorSpaceBlend evidence |
| `path-aa-convexpaths-edge-budget` | `not-candidate-stable-refusal` | n/a | n/a | n/a | `coverage.edge-count-exceeded` | `webgpu.coverage.refuse` | current route is an expected-unsupported refusal with stable cause `coverage.edge-count-exceeded` and no targetColorSpaceBlend evidence |
| `path-aa-dashing-edge-budget` | `not-candidate-stable-refusal` | n/a | n/a | n/a | `coverage.edge-count-exceeded` | `webgpu.coverage.refuse` | current route is an expected-unsupported refusal with stable cause `coverage.edge-count-exceeded` and no targetColorSpaceBlend evidence |
| `path-aa-edge-budget-boundary` | `not-candidate-insufficient-metrics` | n/a | n/a | n/a | `coverage.edge-count-exceeded` | `webgpu.coverage.refuse` | artifacts do not expose exact GPU similarity or targetColorSpaceBlend evidence |
| `path-aa-stroke-outline-fallback` | `not-candidate-insufficient-metrics` | n/a | n/a | n/a | `coverage.stroke-outline-edge-count-exceeded` | `webgpu.coverage.refuse` | artifacts do not expose exact GPU similarity or targetColorSpaceBlend evidence |
| `path-aa-stroke-primitive` | `not-candidate-no-target-color-signal` | 91.81 | n/a | n/a | `none` | `webgpu.coverage.path-aa-stroke-primitive` | exact score is below 99.95, but artifacts provide no tolerance-8/color-space signal for the target blend pilot |
| `runtime-effect-linear-gradient` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.runtime-effect.descriptor.linear_gradient_rt` | gradient path is outside the FOR-234 solid-color AA scope |
| `runtime-effect-simple` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.runtime-effect.descriptor.simple_rt` | runtime-effect descriptor path is outside the FOR-234 solid-color AA scope |
| `runtime-effect-spiral` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.runtime-effect.descriptor.spiral_rt` | runtime-effect descriptor path is outside the FOR-234 solid-color AA scope |
| `scaled-rects-transform-stack` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.transform.scaled-rects.convex-polygon` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `solid-rect` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.coverage.analytic-rect` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `src-over-stack` | `not-candidate-already-exact` | 100.00 | n/a | n/a | `none` | `webgpu.blend.src-over.fixed-function` | current WebGPU evidence is already exact at or above 99.95, so targetColorSpaceBlend is not needed |
| `sweep-gradient-path-clamp` | `not-candidate-out-of-scope` | 100.00 | n/a | n/a | `none` | `webgpu.generated.sweep-gradient.path-aa` | gradient path is outside the FOR-234 solid-color AA scope |

## Policy Checks

- `targetColorSpaceBlend` is not globally enabled.
- No scene is promoted without exact similarity `>= 99.95%`.
- Gradients, bitmaps, runtime effects, image filters, layers, text/font masks, and blur/drop-shadow paths remain out of scope.
- Existing expected-unsupported diagnostics are preserved as route/cause fields.

## Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json`
- `reports/wgsl-pipeline/2026-06-02-m60-target-color-candidate-inventory.md`

## Validation

```text
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/m60-target-color-candidate-inventory.json
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
