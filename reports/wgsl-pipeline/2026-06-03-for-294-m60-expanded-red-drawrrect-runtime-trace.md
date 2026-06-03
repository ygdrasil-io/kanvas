# FOR-294 M60 Expanded Red drawRRect Runtime Trace

Linear: `FOR-294`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS`

Exact limit/gap: `none`

## Result

FOR-294 expands the runtime root trace from the 59
`draw_oval_outer_boundary` pixels used by FOR-290 to the complete FOR-293
red `drawRRect` reconstructed store-candidate domain. This uses a test-only
audit harness because the FOR-290 test intentionally asserts exactly 59
coordinates.

Strategy: complete capture of 22424 red-domain
coordinates via `build/reports/for294/m60-expanded-red-drawrrect-runtime-trace-for294.targets.txt`. The trace observed
53936 root write events and covered
22424 unique root coordinates.

| Measure | Value |
|---|---:|
| Requested expanded coordinates | 22424 |
| Captured target coordinates | 22424 |
| Coordinates outside original 59 targets | 22365 |
| Missing runtime coordinates | 0 |
| Root events | 53936 |
| Root dispatch events | 31512 |
| Direct `eraseColor` root events | 22424 |
| Direct `setPixel` root events | 0 |
| Red root dispatch events | 9088 |
| Red root dispatch pixels | 9088 |
| Red root dispatch events on original targets | 0 |
| Red root dispatch events outside original targets | 9088 |
| Red root dispatch pixels outside original targets | 9088 |
| Final white/layer pixels in expanded domain | 15304 |
| Final red-tint pixels in expanded domain | 6108 |
| Final other pixels in expanded domain | 1012 |

| Event | Index | Pixel | Source | Branch | Mode | Source RGBA | Written RGBA |
|---|---:|---|---|---|---|---|---|
| First red root dispatch | 44848 | `279,4` | `SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` | `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | `kSrcOver` | `[202, 59, 19, 1]` | `[255, 254, 254, 255]` |
| First red root dispatch outside original targets | 44848 | `279,4` | `SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` | `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | `kSrcOver` | `[202, 59, 19, 1]` | `[255, 254, 254, 255]` |

## Subzone Comparison

| Subzone | Candidate pixels | Root events | Root dispatch events | Red dispatch events | Red dispatch pixels | Final white/layer | Final red-tint |
|---|---:|---:|---:|---:|---:|---:|---:|
| `draw_oval_outer_boundary` | 10856 | 27260 | 16404 | 5548 | 5548 | 6176 | 3700 |
| `difference_oval_inner_boundary` | 6168 | 13528 | 7360 | 1192 | 1192 | 4976 | 1176 |
| `halo_interior` | 3568 | 8384 | 4816 | 1248 | 1248 | 2320 | 1232 |
| `outside_draw_oval` | 1832 | 4764 | 2932 | 1100 | 1100 | 1832 | 0 |
| `blurred_content_envelope` | 20592 | 49172 | 28580 | 7988 | 7988 | 13472 | 6108 |

## Runtime Root Source Counts

| Source | Events |
|---|---:|
| `SkBitmap.eraseColor` | 22424 |
| `SkBitmapDevice.dispatchBlend` | 22424 |
| `SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` | 9088 |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`
- FOR-293 decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-expanded-red-drawrrect-runtime-trace-for294.json`
