# KAN-050 PM Breadth Support Refusal Pack

KAN-050 aggregates existing PM-visible support, refusal, dependency, performance and cache evidence into one release-readiness pack. It does not add renderer code, weaken thresholds, or move readiness.

## Summary

- Families: 6
- Support rows checked: 16
- Readiness delta: 0.0
- Native Kadre CI required: false

## Families

| Family | Theme | Source artifacts | Summary |
|---|---|---|---|
| `runtime-effects-v2` | Runtime effects WGSL | `reports/wgsl-pipeline/runtime-effects-v2/evidence.json` | Registered descriptors with Kotlin/CPU behavior and parser-validated WGSL stay visible; arbitrary Skia/SkSL input remains refused. |
| `coverage-strokes-clips` | Coverage, strokes and clips | `reports/wgsl-pipeline/coverage-closeout-matrix/kan-040-coverage-closeout-matrix.json` | Only bounded AA clip support is claimed; hairlines, caps/joins, dashes and nested clips keep stable refusal categories. |
| `filters-layers` | Image filters and layers | `reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json`<br>`reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json` | Bounded DAG rows remain separated from arbitrary DAG, picture-prepass and large-sigma gaps. |
| `text-glyphs` | Text and glyphs | `reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json`<br>`reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json` | Simple Latin and bounded shaping evidence remain separate from broad shaping, font fallback, SDF/LCD and alpha-mask support. |
| `color-bitmap-codec` | Color, bitmap and codec provenance | `reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json`<br>`reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json`<br>`reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json` | Bounded color/bitmap rows and one real PNG source stay visible without broad codec, mipmap or color-managed decode claims. |
| `performance-cache` | Performance and cache | `reports/wgsl-pipeline/performance-family-budgets/kan-048-performance-family-budgets.json`<br>`reports/wgsl-pipeline/cache-telemetry-release-gate/kan-049-cache-telemetry-release-gate.json` | Measured bitmap/color budgets and cache telemetry classifications remain reporting-only unless accepted release-gate policy exists. |

## Categories

| Category | Count | PM meaning |
|---|---:|---|
| `supported` | 16 | Selected rows with complete reference, CPU/GPU, diff/stat, route diagnostics and fallbackReason=none. |
| `expected-unsupported` | 5 | Known limitations with stable fallback reasons; not hidden failures. |
| `dependency-gated` | 3 | Real dependency required; no short-lived font/codec substitute is added. |
| `reporting-only` | 35 | Useful PM evidence that is not a release-blocking gate. |
| `implementation-gap` | 6 | Feature-family gap with explicit next-step evidence requirements. |
| `root-cause-blocked` | 2 | Post-breadth visual-delta root causes are visible as blockers, not support claims. |

## Support Rows

| Row | Family | Source | Proof |
|---|---|---|---|
| `kan034.runtime.color_filter_luma_to_alpha` | `runtime-effects-v2` | `reports/wgsl-pipeline/runtime-effects-v2/evidence.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan034.runtime.linear_gradient_rt` | `runtime-effects-v2` | `reports/wgsl-pipeline/runtime-effects-v2/evidence.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan034.runtime.simple_rt` | `runtime-effects-v2` | `reports/wgsl-pipeline/runtime-effects-v2/evidence.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan034.runtime.spiral_rt` | `runtime-effects-v2` | `reports/wgsl-pipeline/runtime-effects-v2/evidence.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan040.m57-aaclip-bounded-grid` | `coverage-strokes-clips` | `reports/wgsl-pipeline/coverage-closeout-matrix/kan-040-coverage-closeout-matrix.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan041.crop-image-filter-nonnull-prepass` | `filters-layers` | `reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan041.m61-compose-cf-matrix-transform-dag-v2` | `filters-layers` | `reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan043.text.simple-latin.line.v1` | `text-glyphs` | `reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan043.font-kerning-style-fixture` | `text-glyphs` | `reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan045.paint.src-over-alpha.rect-stack.v1` | `color-bitmap-codec` | `reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan045.paint.color-filter.blend-kplus.rect.v1` | `color-bitmap-codec` | `reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan046.bitmap-shader-repeat-tile` | `color-bitmap-codec` | `reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan046.bitmap-subset-local-matrix-repeat` | `color-bitmap-codec` | `reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan047.paint.bitmap-rect.nearest.fixture.v1` | `color-bitmap-codec` | `reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan047.bitmap-shader-repeat-tile` | `color-bitmap-codec` | `reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |
| `kan047.bitmap-subset-local-matrix-repeat` | `color-bitmap-codec` | `reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json` | reference, cpuGpu, diffStat, routeDiagnostics, fallbackStable |

## Root-Cause Blockers

| Ticket | Source | Root cause | Reason |
|---|---|---|---|
| KAN-052 | `reports/wgsl-pipeline/image-filter-visual-delta/kan-052-image-filter-visual-delta.json` | `rgba16float-intermediate-store-to-present-byte-quantization-policy` | `not-bounded-to-image-filter-crop-prepass` |
| KAN-053 | `reports/wgsl-pipeline/text-glyph-visual-delta/kan-053-text-glyph-visual-delta.json` | `text-atlas-alpha-mask-draw-route-not-materialized` | `requires-production-glyph-atlas-sampling-route` |

## Non-Claims

- `no-broad-skia-parity`: No broad Skia parity or arbitrary GM support is claimed from selected rows.
- `no-broad-codecs-fonts`: No broad codec, font fallback, complex shaping, emoji, LCD, SDF, animated image, AVIF, JPEG XL, RAW, or video support is claimed.
- `no-estimated-performance-measured`: Estimated, unavailable, derived, or observed-partial performance/cache data is not counted as measured release evidence.
- `no-native-kadre-ci-requirement`: Headless validation and pipelinePmBundle do not require native Kadre window execution or an initialized external/poc-koreos submodule.
- `no-dynamic-sksl-compilation`: SkSL remains a Skia compatibility/refusal surface; WGSL remains the Kanvas shader implementation target.
- `no-renderer-threshold-readiness-change`: KAN-050 does not modify renderer code, shader code, thresholds, performance gates, cache gates, or readiness denominators.

## PM Bundle Manifest

- Entry key: `kan050PmBreadthSupportRefusalPack`
- Evidence JSON: `release/m99-breadth-pm-pack/evidence.json`
- Evidence Markdown: `release/m99-breadth-pm-pack/evidence.md`

## Validation

- `validateKan050PmBreadthSupportRefusalPack`
- `pipelinePmBundle`
- `pipelineConformance`
- `git diff --check`
