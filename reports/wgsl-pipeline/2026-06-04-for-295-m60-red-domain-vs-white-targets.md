# FOR-295 M60 Red Runtime Domain vs White Targets

Linear: `FOR-295`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN`

Exact gap: `none`

## Result

FOR-295 compares the FOR-294 red runtime root dispatch domain with the
original 59 M60 white targets. The 59 targets remain inside the reconstructed
FOR-293 red store-candidate domain, but they are outside the observed red
runtime root dispatch domain. The comparable root event on every target is
still the white `drawRect` `kSrc` path captured by FOR-290/FOR-292.

| Measure | Value |
|---|---:|
| Original target pixels | 59 |
| Red candidate domain pixels | 22424 |
| Red runtime dispatch pixels | 9088 |
| Targets inside red candidate domain | 59 |
| Targets inside red runtime dispatch domain | 0 |
| Targets outside red runtime dispatch domain | 59 |
| Targets with final white/layer readback | 59 |
| Targets with final red-tint readback | 0 |
| Min Euclidean distance to red runtime dispatch | 70.936591 |
| Max Euclidean distance to red runtime dispatch | 84.386018 |
| Avg Euclidean distance to red runtime dispatch | 77.870976 |
| Min Manhattan distance to red runtime dispatch | 100 |
| Max Manhattan distance to red runtime dispatch | 119 |
| Min Chebyshev distance to red runtime dispatch | 54 |
| Max Chebyshev distance to red runtime dispatch | 64 |

## Bounds

| Domain | Bounds |
|---|---|
| Original target bounds | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` |
| Red candidate bounds | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Red runtime dispatch bounds | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |

The target bounds lie inside the broad red runtime dispatch bounds, but no
target coordinate belongs to the sparse red runtime dispatch coordinate set.

## Subzone Comparison

| Subzone | Original targets | Red candidates | Red runtime dispatch pixels | Target/red intersection | Target final white/layer | Target final red-tint |
|---|---:|---:|---:|---:|---:|---:|
| `draw_oval_outer_boundary` | 59 | 10856 | 5548 | 0 | 59 | 0 |
| `difference_oval_inner_boundary` | 0 | 6168 | 1192 | 0 | 0 | 0 |
| `halo_interior` | 0 | 3568 | 1248 | 0 | 0 | 0 |
| `outside_draw_oval` | 0 | 1832 | 1100 | 0 | 0 | 0 |
| `blurred_content_envelope` | 59 | 20592 | 7988 | 0 | 59 | 0 |

## Runtime And Composite

| Measure | Value |
|---|---:|
| FOR-294 root events | 53936 |
| FOR-294 red root dispatch events | 9088 |
| FOR-294 red root dispatch events on original targets | 0 |
| FOR-294 red root dispatch events outside original targets | 9088 |
| FOR-294 expanded final white/layer pixels | 15304 |
| FOR-294 expanded final red-tint pixels | 6108 |
| FOR-294 expanded final other pixels | 1012 |

The expanded red runtime domain has its own final-composite mismatch
(9088 red dispatch events versus
6108 final red-tint pixels), but that is not
the membership cause for the original 59 pixels: they receive no red runtime
root dispatch event at all.

## Per-Pixel Comparison

| Pixel | Subzones | In red candidate | In red runtime dispatch | Nearest red dispatch | Euclidean distance | Final readback | Runtime source | Runtime mode | Red root event |
|---|---|---|---|---|---:|---|---|---|---|
| `96,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.201036 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.400262 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 75.604233 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 74.8131 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `100,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 74.027022 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `101,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 73.24616 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `102,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 72.470684 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `103,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 71.700767 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `104,84` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 70.936591 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.600254 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.801028 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.006493 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.216796 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 75.432089 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `100,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 74.652528 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `101,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 73.878278 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `102,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 73.109507 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `103,85` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 72.346389 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.0 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.202273 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.409183 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.620873 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.837491 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.059187 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `100,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 75.286121 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `101,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 74.518454 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `102,86` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 73.756356 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.60397 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.81228 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.025312 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.243211 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.466122 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.694198 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `100,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 75.927597 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `101,87` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 75.166482 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 81.215762 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.430094 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.649231 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.873316 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.102497 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.336925 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `100,88` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 76.576759 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 81.8352 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 81.055537 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.280757 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.511006 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 78.746428 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `99,89` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 77.987178 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,90` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 82.462113 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,90` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 81.688432 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,90` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.919713 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `97,90` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 80.156098 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `98,90` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 79.397733 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,91` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 83.09633 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,91` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 82.32861 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `96,91` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 81.565924 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,92` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 83.737686 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `95,92` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 82.9759 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |
| `94,93` | `draw_oval_outer_boundary,blurred_content_envelope` | True | False | `158,38` | 84.386018 | `[255, 255, 255, 255]` | `SkBitmapDevice.dispatchBlend` | `kSrc` | False |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`
- FOR-293 decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`
- FOR-294 decision: `RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace, or
setPixel behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-domain-vs-white-targets-for295.json`
