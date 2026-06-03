# FOR-273 WebGPU Solid Blur SrcIn Fold

Linear: `FOR-273`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

FOR-273 folds only `SkColorFilters.Blend(colour, kSrcIn)` into the WebGPU
solid-blur paint colour. The scene remains on route `webgpu.coverage.nested-rrect-clip.expected-unsupported` with
fallback `coverage.nested-clip-visual-parity-below-threshold` because GPU/reference `98.48`
and CPU/reference `97.31` remain below the strict
`99.95` threshold.

## Before / After

| Metric | FOR-272 before | FOR-273 after | Delta |
|---|---:|---:|---:|
| GPU/reference similarity | 97.5 | 98.48 | 0.98 |
| GPU matching pixels | 910225 | 919363 | 9138 |
| GPU max channel delta | 203 | 57 | -146 |
| CPU/reference similarity | 97.31 | 97.31 | 0.0 |

| Comparison | >32 before | >32 after | Max delta before | Max delta after |
|---|---:|---:|---:|---:|
| GPU/reference | 19361 | 2869 | 203 | 57 |
| CPU/reference | 15726 | 15726 | 237 | 237 |

## Key Subzones

| Subzone | GPU/ref >32 before | GPU/ref >32 after |
|---|---:|---:|
| `halo_interior` | 3568 | 0 |
| `draw_oval_outer_boundary` | 9868 | 2796 |
| `difference_oval_inner_boundary` | 5925 | 73 |

## Residual Risk

The WebGPU black/clear RGB halo is removed, but this does not promote the
scene. CPU/reference remains at `97.31` and the route stays
`expected-unsupported`. The remaining work is a separate CPU/reference
layer-or-mask-extent decision, not a GPU-only support claim.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk python3 scripts/validate_for271_nested_rrect_blurred_envelope_audit.py
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-solid-blur-srcin-fold-for273.json`
