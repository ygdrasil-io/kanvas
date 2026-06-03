# FOR-290 M60 Expanded Runtime Write Trace

Linear: `FOR-290`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`

Next action: `RECONSTRUCTED_RED_STORE_NOT_RUNTIME_ROOT_WRITE`

## Result

FOR-290 extended the disabled-by-default CPU runtime trace beyond
`SkBitmapDevice.dispatchBlend` to include root `SkBitmap` direct
`eraseColor` / `setPixel` writes. The trace remains bounded to the same 59
`draw_oval_outer_boundary` target pixels and to the GM root dimensions
1164x802, excluding temporary mask/layer devices by dimension.

| Measure | Value |
|---|---:|
| Target pixels | 59 |
| Root write events | 118 |
| Direct bitmap root write events | 59 |
| Dispatch root write events | 59 |
| Runtime red root store events | 0 |
| Targets with runtime red root store | 0 |
| Final readback white/layer targets | 59 |

| Event | Index | Pixel | Device | Source | Branch | Before | Written |
|---|---:|---|---|---|---|---|---|
| First runtime red root store | n/a | n/a | n/a | n/a | n/a | n/a | n/a |

## Interpretation

Trace gap: none

No runtime red root store was observed in the expanded trace unless named
above. The observed root writes on the 59 target pixels are the direct bitmap
background fill plus the white/layer `dispatchBlend` writes. The FOR-288
classification remains `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE` and
the FOR-289 decision remains `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`.

M60 remains `expected-unsupported` for
`coverage.nested-clip-visual-parity-below-threshold`, and the crop fallback
remains `image-filter.crop-input-nonnull-prepass-required`.

## Source Counts

| Source | Events |
|---|---:|
| `SkBitmap.eraseColor` | 59 |
| `SkBitmapDevice.dispatchBlend` | 59 |

## Branch Counts

| Branch | Events |
|---|---:|
| `SkBitmap.eraseColor.kRGBA_8888.fill` | 59 |
| `SkBitmapDevice.blend.kSrc.setPixel(out)` | 59 |

Machine artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-expanded-runtime-write-trace-for290.json`
