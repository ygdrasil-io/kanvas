# FOR-297 M60 Candidate Hole Overbreadth

Linear: `FOR-297`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER`

Exact gap: `none`

## Result

FOR-297 compares `candidate-minus-runtime-002` against the FOR-294 red
runtime components without changing rendering. The candidate hole is not an
alpha-zero or clip-zero region: every pixel in the component passes the
FOR-293 reconstructed predicate
`mask_alpha > 0 && clip_coverage > 0 && alpha_after_clip > 0`.

The current evidence rejects subzone selection as the cause. The original 59
targets are in `draw_oval_outer_boundary`; FOR-294 also observed 5,548 red
runtime dispatch pixels in that same subzone. The remaining explanation is an
additional runtime write-eligibility filter between the reconstructed
mask/clip model and the observed A8 `srcInPayload` root dispatch membership.

| Measure | Value |
|---|---:|
| Red candidate domain pixels | 22424 |
| Red runtime dispatch pixels | 9088 |
| Candidate-minus-runtime pixels | 13336 |
| Runtime pixels outside candidate | 0 |
| Original target pixels | 59 |
| Targets inside red candidate | 59 |
| Targets inside red runtime dispatch | 0 |
| Targets inside `candidate-minus-runtime-002` | 59 |
| Target min distance to red runtime dispatch | 70.936591 |
| Target avg distance to red runtime dispatch | 77.870976 |
| Target max distance to red runtime dispatch | 84.386018 |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Avg mask | Avg clip | Avg alpha-after-clip | Final white/layer | Final red-tint | Red runtime pixels |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 59 | 195.074097 | 240.901002 | 180.975099 | 3293 | 0 | 0 |
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 0 | 138.114286 | 253.757802 | 136.872088 | 496 | 1526 | 2275 |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 0 | 138.114286 | 253.757802 | 136.872088 | 496 | 1526 | 2275 |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 0 | 138.52511 | 253.755066 | 137.280176 | 488 | 1528 | 2270 |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 0 | 138.432099 | 253.753968 | 137.186067 | 488 | 1528 | 2268 |

## Mask, Clip, Alpha

`candidate-minus-runtime-002`:

| Metric | Pixels | Min | Max | Average | Zero | Non-zero | Full |
|---|---:|---:|---:|---:|---:|---:|---:|
| Mask alpha | 3293 | 1 | 255 | 195.074097 | 0 | 3293 | 1517 |
| Clip coverage | 3293 | 16 | 255 | 240.901002 | 0 | 3293 | 2937 |
| Alpha after clip | 3293 | 1 | 255 | 180.975099 | 0 | 3293 | 1161 |

Original 59 target cluster:

| Metric | Pixels | Min | Max | Average | Zero | Non-zero | Full |
|---|---:|---:|---:|---:|---:|---:|---:|
| Mask alpha | 59 | 6 | 251 | 158.694915 | 0 | 59 | 0 |
| Clip coverage | 59 | 255 | 255 | 255.0 | 0 | 59 | 59 |
| Alpha after clip | 59 | 6 | 251 | 158.694915 | 0 | 59 | 0 |

## Subzones

| Subzone | Original targets | `candidate-minus-runtime-002` | Red runtime dispatch |
|---|---:|---:|---:|
| `draw_oval_outer_boundary` | 59 | 1326 | 5548 |
| `difference_oval_inner_boundary` | 0 | 1220 | 1192 |
| `halo_interior` | 0 | 580 | 1248 |
| `outside_draw_oval` | 0 | 167 | 1100 |
| `blurred_content_envelope` | 59 | 3126 | 7988 |

## Cluster Neighborhood

| Chebyshev radius | Sampled pixels | Red candidate | Red runtime dispatch | Target-hole pixels | Final white/layer | Final red-tint |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 59 | 59 | 0 | 59 | 59 | 0 |
| 4 | 291 | 246 | 0 | 246 | 246 | 0 |
| 8 | 651 | 386 | 0 | 386 | 386 | 0 |
| 16 | 1755 | 668 | 0 | 668 | 668 | 0 |
| 32 | 5499 | 1215 | 0 | 1215 | 1215 | 0 |
| 64 | 19131 | 2165 | 20 | 2145 | 2165 | 0 |
| 72 | 23819 | 2390 | 56 | 2333 | 2384 | 0 |

## Runtime Component Distances

| Runtime component | Bounds | Min distance | Avg distance | Max distance | Nearest pair |
|---|---|---:|---:|---:|---|
| `red-runtime-000` | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 70.936591 | 77.870976 | 84.386018 | `104,84` -> `158,38` |
| `red-runtime-002` | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 85.79627 | 92.617708 | 99.156442 | `94,93` -> `38,158` |
| `red-runtime-003` | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 455.057139 | 460.877967 | 464.612742 | `104,84` -> `553,158` |
| `red-runtime-001` | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 464.430834 | 469.770919 | 473.080331 | `94,93` -> `158,553` |

## Payload And Source

For the original 59 pixels, FOR-292 still maps the reconstructed source to
`BlurredClippedCircleGM.c.drawRRect(rr, paint)` through
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload`, while the comparable
runtime root source is the white `drawRect` `kSrc` dispatch. FOR-294 proves
that red A8 runtime dispatch does exist elsewhere, but not in
`candidate-minus-runtime-002`.

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`
- FOR-293 decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`
- FOR-294 decision: `RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS`
- FOR-295 decision: `ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN`
- FOR-296 decision: `RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace, or
setPixel behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-candidate-hole-overbreadth-for297.json`
