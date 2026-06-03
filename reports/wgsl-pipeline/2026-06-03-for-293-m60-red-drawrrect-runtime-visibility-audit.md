# FOR-293 M60 Red drawRRect Runtime Visibility Audit

Linear: `FOR-293`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`

Exact gap: `FOR-290's runtime trace is target-filtered to exactly the 59 outer-boundary pixels; 22365 reconstructed red drawRRect store-candidate pixels outside those captured coordinates have no root runtime visibility yet.`

## Result

FOR-293 audits the visibility question left by FOR-292: the 59
`draw_oval_outer_boundary` pixels are reconstructed from the red blurred
`drawRRect` path, but the comparable runtime root dispatch is still the
white `drawRect` path. The red reconstructed domain is not zero-masked or
fully clipped at the target pixels: all 59 targets have non-zero blur mask,
full clip coverage, and non-zero alpha after clip.

The current runtime evidence cannot prove whether the red `drawRRect`
emits root dispatches elsewhere, because the FOR-290 trace harness is
hard-filtered to exactly the 59 target pixels. The reconstructed red
store-candidate domain contains 22424
pixels after mask and clip, leaving
22365 candidate
pixels outside the captured runtime coordinate set.

| Measure | Value |
|---|---:|
| Target pixels | 59 |
| Red store candidates after mask/clip | 22424 |
| Red candidates outside captured runtime coordinates | 22365 |
| Targets inside red candidate domain | 59 |
| Targets with non-zero mask | 59 |
| Targets with full clip coverage | 59 |
| Targets with non-zero alpha after clip | 59 |
| Targets mapped to red drawRRect A8 SrcIn kSrcOver | 59 |
| Runtime root events captured | 118 |
| Runtime root dispatch events captured | 59 |
| Runtime red/kSrcOver events in captured trace | 0 |
| Runtime root events outside targets in captured trace | 0 |

## Bounds And Coverage

| Field | Bounds |
|---|---|
| Draw oval device bounds | `[8, 8, 584, 584]` |
| Difference oval device bounds | `[16, 16, 576, 576]` |
| Mask-filter bounds | `{'left': 2, 'top': 2, 'right': 590, 'bottom': 590, 'margin': 5}` |
| Red mask non-zero bounds | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Red store-candidate bounds after clip | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Target bounds | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` |
| Captured runtime root bounds | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` |

## Red Candidate Subzones

| Subzone | Pixels |
|---|---:|
| `difference_oval_inner_boundary` | 6168 |
| `draw_oval_outer_boundary` | 10856 |
| `halo_interior` | 3568 |
| `removed_difference_oval_interior` | 0 |
| `outside_draw_oval` | 1832 |
| `blurred_content_envelope` | 20592 |

## Captured Runtime Root Counts

| Source | Events |
|---|---:|
| `SkBitmap.eraseColor` | 59 |
| `SkBitmapDevice.dispatchBlend` | 59 |

The captured runtime coordinates equal the 59 target coordinates:
`True`. This proves the captured trace
does not include an outside-target search area.

## Anchor Pixel

| Field | Red reconstructed path | Runtime root path |
|---|---|---|
| Pixel | `99,89` | `99,89` |
| Draw | `BlurredClippedCircleGM.c.drawRRect(rr, paint)` | `BlurredClippedCircleGM.c.drawRect(clipRect1, whitePaint)` |
| Source | `[255, 0, 0, 250]` | `[255, 255, 255, 255]` |
| Mode | `SkBlendMode.kSrcOver` | `kSrc` |
| Mask alpha | 250 | n/a |
| Clip coverage | 255 | 255 |
| Alpha after clip | 250 | n/a |
| Branch | `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | `SkBitmapDevice.blend.kSrc.setPixel(out)` |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-drawrrect-runtime-visibility-audit-for293.json`
