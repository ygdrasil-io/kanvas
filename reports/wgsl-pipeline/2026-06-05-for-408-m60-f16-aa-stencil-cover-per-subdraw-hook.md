# FOR-408 M60 F16 AA stencil-cover per-subdraw hook

Date: 2026-06-05

## Result

Classification: `per-subdraw-framebuffer-state-unavailable`.

FOR-408 adds the opt-in guard
`kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled`, disabled by
default. The hook is bounded to M60 F16, `StencilCoverAaPolygonDraw`, and the
16 FOR-401 coordinates.

The runtime snapshot records per-subdraw shader-side source color and
coverage/AA alpha where the inside/outside cover fragments are observed. The
remaining blocker is narrower than FOR-407: WebGPU fixed-function blending
does not expose per-subdraw framebuffer `dstBeforeRgbaFloat` or
`dstAfterRgbaFloat` to this fragment shader, so independent `kSrcOver` replay
cannot be completed from the captured fields.

FOR-400 remains context-only, not direct write proof.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json`

Source artifacts:

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-406 post-pass/reference comparison | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-post-pass-reference-comparison-for406/m60-f16-post-pass-reference-comparison-for406.json` |
| FOR-407 prior blocker | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-contribution-isolation-for407/m60-f16-aa-stencil-cover-contribution-isolation-for407.json` |
| FOR-407 memory finding | `global/kanvas/findings/for-407-formalise-le-manque-de-donnees-per-subdraw-pour-isoler-la-cause-m60-f16` |

## Captured Fields

Captured when a selected coordinate is observed by the bounded shader hook:

| Field | Status |
|---|---|
| `coordinate` | available |
| `drawIndex` | available |
| `subdrawOrdinal` | available |
| `subdrawRole` | available as `inside` or `outside` |
| `sourceColorPremulRgbaFloat` | available shader-side |
| `coverageOrAaAlpha` | available shader-side |
| `blendMode` | expected `kSrcOver` |
| `dstBeforeRgbaFloat` | unavailable at fixed-function blend fragment boundary |
| `expectedSourceOverRgbaFloat` | unavailable without `dstBeforeRgbaFloat` |
| `dstAfterRgbaFloat` | unavailable per subdraw; post-pass destination remains available through FOR-405-style readback |

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `false` |
| promoted=false | `false` |
| correctionAppliedByDefault=false | `false` |
| defaultRenderingChanged=false | `false` |
| FOR-400 direct proof | `false` |

## Validations

- `rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py`
- `rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py`
- `rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py`
- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- evidence test with `-Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
