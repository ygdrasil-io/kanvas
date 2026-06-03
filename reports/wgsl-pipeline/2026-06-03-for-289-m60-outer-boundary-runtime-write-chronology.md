# FOR-289 M60 Outer Boundary Runtime Write Chronology

Linear: `FOR-289`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`

Next action: `EXPAND_RUNTIME_TRACE_BEYOND_SKBITMAPDEVICE_DISPATCHBLEND`

## Result

FOR-289 traced actual CPU writes on the root `SkBitmapDevice` for the 59
`draw_oval_outer_boundary` target pixels derived from the FOR-288 outer window.
Temporary mask devices are excluded by matching the GM output dimensions
1164x802. The trace captures write order, coordinate, logical source, callsite,
branch, value before write, value written, and value read immediately after
write.

| Measure | Value |
|---|---:|
| Target pixels | 59 |
| Runtime write events | 59 |
| Targets with runtime red store | 0 |
| Targets with later white/layer write | 0 |
| Final readback white/layer targets | 59 |

| Event | Index | Pixel | Source | Branch | Before | Written |
|---|---:|---|---|---|---|---|
| First runtime red store | n/a | n/a | n/a | n/a | n/a | n/a |
| First later white/layer write | n/a | n/a | n/a | n/a | n/a | n/a |

## Interpretation

Proof gap: The root-device-bounded trace observed only the initial white kSrc output writes on the 59 target pixels. No runtime write with source SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload wrote a red-tinted value to those root pixels, so the FOR-286 reconstructed red store is not yet proven to be an actual output-device write.

The FOR-288 classification is preserved as
`OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`. M60 remains
`expected-unsupported` for `coverage.nested-clip-visual-parity-below-threshold`,
and the crop fallback remains `image-filter.crop-input-nonnull-prepass-required`.

## Source Counts

| Source | Events |
|---|---:|
| `SkBitmapDevice.dispatchBlend` | 59 |

## Branch Counts

| Branch | Events |
|---|---:|
| `SkBitmapDevice.blend.kSrc.setPixel(out)` | 59 |

Machine artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-outer-boundary-runtime-write-chronology-for289.json`
