# FOR-407 M60 F16 AA stencil-cover contribution isolation

Date: 2026-06-05

This analytical report reuses FOR-401, FOR-404, FOR-405, and FOR-406 evidence
for the 16 M60 F16 residual coordinates. It does not change Kotlin rendering,
default diagnostics, score thresholds, support policy, or promotion state.

## Result

Global classification: `insufficient-per-subdraw-data`.
Conclusion: `per-subdraw-inputs-required-before-root-cause-classification`.

FOR-406 already proves that all 16 pixels diverge at the post-pass
`StencilCoverAaPolygonDraw` boundary. FOR-405 also provides RGBA16Float
post-pass readback for the same pixels and three draw events. That is enough
to place the divergence before final present/readback, but not enough to
separate AA coverage, source color, source-over blend math, or inside/outside
subdraw accumulation.

## Sources

| Source | Path |
|---|---|
| FOR-401 selected residual pixels | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-404 runtime-hook context | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json` |
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-406 post-pass/reference comparison | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-post-pass-reference-comparison-for406/m60-f16-post-pass-reference-comparison-for406.json` |
| FOR-405 finding memory | `global/kanvas/findings/for-405-observe-les-couleurs-post-passe-aa-stencil-cover-m60-f16` |
| FOR-406 finding memory | `global/kanvas/findings/for-406-montre-que-la-divergence-m60-f16-est-deja-visible-en-post-passe-aa-stencil-cover` |
| Ticket draft memory | `global/kanvas/tickets/drafts/brouillon-ticket-for-407-m60-f16-isoler-couverture-couleur-blend-dans-la-passe-aa-stencil-cover` |

FOR-400 remains `context-only-not-direct-write-proof`.

## Existing Data

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `16` |
| Classified insufficient per-subdraw data | `16` |
| Post-pass already diverged | `16` |
| Post-pass observed pixels | `16` |
| Post-pass equals current GPU final | `16` |
| CPU equals Skia | `16` |
| Sum delta post-pass -> Skia | `1560` |
| Sum delta final GPU -> Skia | `1560` |
| FOR-401 selected residual total | `1560` |

## Missing Data

| Missing field | Why it is required |
|---|---|
| `dstBeforeRgbaFloat` | needed to replay source-over and separate bad blend math from bad inputs |
| `sourceColorPremulRgbaFloat` | needed to decide whether the source color supplied to the pass is wrong |
| `coverageOrAaAlpha` | needed to decide whether AA coverage, not color or blend, is wrong |
| `expectedSourceOverRgbaFloat` | needed to compare the pass output with independently replayed source-over |
| `dstAfterEachInsideOutsideSubdrawRgbaFloat` | needed to identify inside/outside draw order or accumulation mistakes |
| `subdrawRole` | needed to tie a sample to inside cover, outside cover, or another sub-draw role |

## Pixel Classification

| Coordinate | Post-pass observed | Skia | Delta post-pass->Skia | Observed draw boundaries | Classification |
|---|---|---|---:|---:|---|
| (92, 75) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (91, 76) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (90, 77) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (89, 78) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (88, 79) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (87, 80) | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `105` | `3` | `insufficient-per-subdraw-data` |
| (101, 37) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (102, 37) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (99, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (100, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (101, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (102, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (103, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (104, 38) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (98, 39) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |
| (99, 39) | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `93` | `3` | `insufficient-per-subdraw-data` |

## Minimal Next Hook

Required guard: `kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled`.
Enabled by default: `false`.
Scope: `M60 F16 only, StencilCoverAaPolygonDraw only, exactly the 16 FOR-401 coordinates`.
Implementation point: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt around the StencilCoverAaPolygonDraw cover sub-draw execution`.

The hook should record only:

- `pixel coordinate`
- `drawIndex`
- `subdrawOrdinal`
- `subdrawRole inside/outside/other`
- `dstBeforeRgbaFloat from the intermediate texture`
- `sourceColorPremulRgbaFloat used by the draw`
- `coverageOrAaAlpha used by the fragment`
- `blendMode, expected kSrcOver`
- `expectedSourceOverRgbaFloat recomputed from the recorded inputs`
- `dstAfterRgbaFloat after that subdraw`

Success criterion: each FOR-401 pixel can be assigned to coverage-aa-wrong, source-color-wrong, blend-source-over-wrong, or draw-order-or-accumulation-wrong without using FOR-400 as direct write proof.

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `false` |
| promoted=false | `false` |
| correctionAppliedByDefault=false | `false` |
| defaultRenderingChanged=false | `false` |
| FOR-400 direct proof | `False` |

## Validations

- `rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py`
- `rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py`
- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
