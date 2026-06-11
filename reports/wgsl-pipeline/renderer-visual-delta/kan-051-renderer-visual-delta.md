# KAN-051 Renderer Visual Delta

KAN-051 selects `clip-rect-difference` / `Skbug9319GM` and burns down a
real WebGPU renderer divergence in the small-sigma `clipRect(kDifference)`
maskFilter halo. The CPU/reference artifact is unchanged across phases; the
GPU artifact changes through `SkWebGpuDevice.drawPathWithBlurMaskFilterIfApplicable`.

## Renderer Change

The WebGPU maskFilter blur path used point-sampled Gaussian weights for small-sigma filled rect masks, while the Skia-like A8 blur profile integrates coverage over destination pixels.

Before route: `webgpu.coverage.clip-difference.analytic-rrect-mask` with `point-sampled Gaussian half-kernel`.
After route: `webgpu.coverage.clip-difference.analytic-rrect-mask` with `per-pixel integrated Gaussian half-kernel for axis-aligned filled rect maskFilter blur`.
Fallback reason remains `none`.

Changed renderer files:

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipDifferenceCrossTest.kt`

## Metrics

Threshold and tolerance stay constant: threshold `80.0` -> `80.0`,
tolerance `8` -> `8`.

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| GPU matching pixels | 130672 | 131064 | 392 |
| GPU mismatching pixels | 400 | 8 | -392 |
| GPU max channel delta | 21 | 10 | -11 |
| clipRect top halo RGB abs error | 17 | 3 | -14 |
| GPU similarity | 99.6948 | 99.9939 | 0.2991 |

## Artifacts

Before artifacts live under `reports/wgsl-pipeline/renderer-visual-delta/assets/before/`.
After artifacts live under `reports/wgsl-pipeline/renderer-visual-delta/assets/after/`.
Each phase contains `skia.png`, `cpu.png`, `gpu.png`, `cpu-diff.png`,
`gpu-diff.png`, `stats.json`, `route-cpu.json`, and `route-gpu.json`.

## Existing Refusals

- `KAN-041`: `reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json` keeps `image-filter.dag-or-picture-prepass-required, image-filter.crop-input-nonnull-prepass-required` visible.
- `KAN-042`: `reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json` keeps `image-filter.blur-large-sigma-unsupported, image-filter.runtime-descriptor-scope-dependency-gated` visible.
- `KAN-043`: `reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json` keeps `font.shaping-feature-unsupported, font.shaping-fallback-missing` visible.
- `KAN-044`: `reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json` keeps `coverage.alpha-mask-unsupported` visible.
- `KAN-045`: `reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json` keeps `color.color-space-wide-gamut-unsupported, color.f16-policy-candidate-worsens-reference` visible.
- `KAN-046`: `reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json` keeps `image-sampling.mipmap-unsupported` visible.

## Validation

- `rtk python3 scripts/test_validate_kan051_renderer_visual_delta.py`
- `rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon validateKan051RendererVisualDelta`
- `rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest`
- `rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon pipelineConformance`
- `rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon pipelinePmBundle`
