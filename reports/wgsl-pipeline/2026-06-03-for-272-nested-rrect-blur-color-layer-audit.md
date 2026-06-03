# FOR-272 Nested RRect Blur Color/Layer Audit

Linear: `FOR-272`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

Dominant hypothesis: `GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_PLUS_CPU_REFERENCE_LAYER_RESIDUAL`.

The audited chain is `SkBlurMaskFilter(kNormal, sigma=1.366025)` plus
`SkColorFilters.Blend(RED, kSrcIn)` on the `BlurredClippedCircleGM`
`drawRRect`. The scene remains on route `webgpu.coverage.nested-rrect-clip.expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold` because GPU/reference `97.5`
and CPU/reference `97.31` remain below the
strict `99.95` threshold.

## Color/Layer Findings

| Comparison | >32 pixels | Signed mean RGBA in blurred envelope >32 | Max delta |
|---|---:|---|---:|
| GPU/reference | 19361 | `[-146.615, -36.157, -11.198, 0.0]` | 203 |
| CPU/reference | 15726 | `[40.078, 137.583, 159.165, 0.0]` | 237 |
| GPU/CPU | 17660 | `[-195.322, -160.526, -150.786, 0.0]` | 255 |

| Subzone | GPU/ref >32 | CPU/ref >32 | Dominant hypothesis |
|---|---:|---:|---|
| `draw_oval_outer_boundary` | 9868 | 8077 | `MIXED_BOUNDARY_COLOR_PAYLOAD_AND_LAYER_EDGE` |
| `difference_oval_inner_boundary` | 5925 | 5201 | `MIXED_BOUNDARY_COLOR_PAYLOAD_AND_LAYER_EDGE` |
| `halo_interior` | 3568 | 2448 | `GPU_SOLID_BLUR_COLOR_FILTER_NOT_FOLDED_WITH_CPU_BACKGROUND_LAYER_RETENTION` |
| `removed_difference_oval_interior` | 0 | 0 | `NOT_PRIMARY_FOR_FOR272` |
| `outside_draw_oval` | 0 | 0 | `NOT_PRIMARY_FOR_FOR272` |

## Signed RGB Samples

Halo-interior samples show the polarity directly:

| Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signed | GPU-reference signed |
|---|---|---|---|---|---|
| 225,21 | `[202, 60, 21, 255]` | `[255, 252, 252, 255]` | `[0, 0, 0, 255]` | `[53, 192, 231, 0]` | `[-202, -60, -21, 0]` |
| 226,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 255]` | `[53, 195, 235, 0]` | `[-202, -60, -20, 0]` |
| 365,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[0, 0, 0, 255]` | `[53, 195, 235, 0]` | `[-202, -60, -20, 0]` |

## Interpretation

FOR-271 already proved `COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED`: alpha is not the
primary failure. FOR-272 narrows the color/layer polarity:

- Reference envelope pixels are red-tinted.
- CPU/reference high-delta pixels skew white, consistent with layer/background
  retention or mask extent ordering in the CPU oracle.
- WebGPU/reference high-delta pixels skew black/clear RGB. The bounded source
  candidate is the solid blur path using `paint.color` for `paintColor` while
  the GM supplies the red tint through `SkColorFilters.Blend(RED, kSrcIn)`.

This is not enough for support promotion because CPU/reference is still below
the strict threshold in the same envelope. A bounded GPU color-filter fold may
be a next implementation candidate, but it must be validated against FOR-270
and FOR-271 and cannot clear support alone.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. No threshold weakening,
broad clip-stack support, fallback/readback, Ganesh, Graphite, or SkSL compiler
was added. Preserve `image-filter.crop-input-nonnull-prepass-required`.

## Validation

```text
rtk python3 scripts/validate_for272_nested_rrect_blur_color_layer_audit.py
rtk python3 scripts/validate_for271_nested_rrect_blurred_envelope_audit.py
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-blur-color-layer-audit-for272.json`
