# KAN-044 Glyph Mask Atlas Ownership

KAN-044 packages the glyph mask / atlas ownership boundary from existing text
and geometry evidence. It records the text-owned atlas upload plan, CPU mask
oracle, coverage handoff, and WebGPU standalone alpha-mask refusal without
adding renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 4 |
| Atlas upload-plan rows | 1 |
| CPU mask oracle rows | 1 |
| Coverage handoff rows | 1 |
| WebGPU refusal rows | 1 |
| Rows missing glyph keys | 0 |
| Rows missing atlas generation | 0 |
| Rows missing upload bytes | 0 |
| Coverage ownership violations | 0 |

## Ownership Rows

| Row | Status | Category | WebGPU/Coverage route | Reason |
|---|---|---|---|---|
| `text.simple-latin.glyph-atlas.upload-plan` | `pass` | `atlas-upload-plan` | `webgpu.text.glyph-atlas.simple-latin` | `none` |
| `text.simple-latin.cpu-mask-oracle` | `pass` | `cpu-mask-oracle` | `webgpu.text.glyph-atlas.simple-latin` | `none` |
| `geometry.glyph-mask.alpha-mask-handoff` | `pass` | `coverage-handoff` | `deferred-to-backend-decision` | `none` |
| `webgpu.standalone-alpha-mask-refusal` | `expected-unsupported` | `webgpu-alpha-mask-refusal` | `webgpu.refuse.standalone-alpha-mask` | `coverage.alpha-mask-unsupported` |

The owner is `text-glyph-infrastructure`; coverage consumes opaque mask refs only.

## Claim Guard

| Guard | Value |
|---|---|
| rowsMissingGlyphKeys | `[]` |
| rowsMissingAtlasGeneration | `[]` |
| rowsMissingUploadBytes | `[]` |
| rowsMissingCacheIds | `[]` |
| cpuMaskOracleMissing | `[]` |
| coverageOwnershipViolations | `[]` |
| webGpuRouteMissingDecision | `[]` |
| hiddenAtlasEvictionClaims | `[]` |
| lcdOrSdfClaims | `[]` |
| ganeshGraphiteClaims | `[]` |
| thresholdOrRendererChanges | `[]` |

## Required Validation

- `validateKan044GlyphMaskAtlasOwnership`
- `:render-pipeline:pipelineConformanceTest -- includes GeometryCoverageContractsTest`
- `:gpu-raster:pipelineConformanceTest -- includes SkWebGpuGlyphAtlasTest and SimpleLatinLineSceneEvidenceTest`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `atlas-route-visible` | `pass` | Atlas row records glyph keys, generation, R8/A8 format, upload bytes, upload hash, and cache ids. |
| `cpu-mask-oracle-visible` | `pass` | SkWebGpuGlyphAtlasTest proves atlas sampling matches CPU glyph mask alpha. |
| `coverage-consumes-opaque-ref` | `pass` | GlyphMaskLowering exposes CoveragePlan.AlphaMask while ownership remains text-glyph-infrastructure. |
| `webgpu-refusal-visible` | `pass` | Standalone alpha-mask WebGPU route remains expected-unsupported via coverage.alpha-mask-unsupported. |

## Non-Claims

- KAN-044 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-044 does not claim broad glyph atlas support, dynamic atlas eviction, LCD, SDF, bitmap glyph, or color-font support.
- KAN-044 does not move glyph discovery, rasterization, atlas lifetime, or invalidation ownership into geometry/coverage.
- KAN-044 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
