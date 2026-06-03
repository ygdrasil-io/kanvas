# FOR-274 Nested RRect CPU/Reference Layer Mask Audit

Linear: `FOR-274`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

Dominant hypothesis: `CPU_LAYER_BACKGROUND_RETENTION_DOMINANT_MASK_EXTENT_SECONDARY`.

FOR-274 audits the residual left after FOR-273. The scene stays on route
`webgpu.coverage.nested-rrect-clip.expected-unsupported` with fallback `coverage.nested-clip-visual-parity-below-threshold`. No renderer path
is changed and no support promotion is claimed: CPU/reference is
`97.31` and GPU/reference is
`98.48`, both below the strict
`99.95` threshold.

## Post-FOR-273 Summary

| Metric | Value |
|---|---:|
| CPU/reference >32 pixels | 15726 |
| GPU/reference >32 pixels | 2869 |
| GPU/CPU >32 pixels | 12200 |
| CPU primary-subzone >32 pixels | 15726 |
| CPU primary white/layer pixels | 13518 |
| CPU primary white/layer share | 85.959557% |
| GPU `halo_interior` >32 pixels | 0 |

## Required Subzones

| Subzone | CPU/ref >32 | GPU/ref >32 | CPU signed RGB mean on >32 | CPU actual white/layer | Classification |
|---|---:|---:|---|---:|---|
| `draw_oval_outer_boundary` | 8077 | 2796 | `[31.37, 98.116, 107.845]` | 6157 | `LAYER_BACKGROUND_RETENTION_DOMINANT` |
| `difference_oval_inner_boundary` | 5201 | 73 | `[48.141, 173.852, 206.74]` | 5001 | `LAYER_BACKGROUND_RETENTION_DOMINANT` |
| `halo_interior` | 2448 | 0 | `[51.677, 190.746, 227.419]` | 2360 | `LAYER_BACKGROUND_RETENTION_DOMINANT` |

## Signed RGB Samples

| Subzone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signed | GPU-reference signed |
|---|---|---|---|---|---|---|
| `draw_oval_outer_boundary` | 99,89 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | `[-1, -5, -10, 0]` |
| `draw_oval_outer_boundary` | 98,90 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | `[-1, -5, -10, 0]` |
| `difference_oval_inner_boundary` | 262,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | `[0, 0, 1, 0]` |
| `difference_oval_inner_boundary` | 329,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | `[0, 0, 1, 0]` |
| `halo_interior` | 226,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | `[0, -1, -1, 0]` |
| `halo_interior` | 365,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | `[0, -1, -1, 0]` |

## Interpretation

The CPU/reference residual is RGB-only: every primary subzone reports zero
alpha >32 pixels. CPU pixels are mostly opaque white/layer RGB where the
reference is red-tinted. WebGPU is no longer the dominant blocker after
FOR-273 because `halo_interior` is cleared and GPU/reference >32 is now much
smaller than CPU/reference.

Classification:

- Layer/fond retention: dominant.
- Mask extent or blur extent: secondary boundary question, still worth a
  minimized CPU fixture.
- CPU color-filter/blur order: not primary for this solid
  `Blend(RED, kSrcIn)` chain, because the expected payload should remain red.
- Explicit reference divergence refusal: not justified yet without a smaller
  CPU/reference fixture.

## Next Action

Add a bounded CPU mask-filter/layer fixture for `SkBlurMaskFilter(kNormal)` +
`SkColorFilters.Blend(RED, kSrcIn)` to prove whether the white layer comes from
the CPU layer/fond composite or from blur/mask extent. Do not promote M60 until
CPU/reference and GPU/reference both reach `99.95` with
route/stat evidence.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. Preserve
`image-filter.crop-input-nonnull-prepass-required`. No threshold weakening,
broad clip-stack support, fallback/readback, Ganesh, Graphite, or SkSL compiler
was added.

## Validation

```text
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-cpu-reference-layer-mask-audit-for274.json`
