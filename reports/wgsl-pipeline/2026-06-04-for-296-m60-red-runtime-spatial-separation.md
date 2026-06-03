# FOR-296 M60 Red Runtime Spatial Separation

Linear: `FOR-296`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER`

Exact gap: `none`

## Result

FOR-296 audits the spatial split left by FOR-295 without changing rendering.
The original 59 pixels are still inside the reconstructed red candidate domain,
but they are in a candidate-minus-runtime hole component and have no overlap
with the observed FOR-294 red runtime dispatch coordinates.

| Measure | Value |
|---|---:|
| Original target pixels | 59 |
| Red candidate domain pixels | 22424 |
| Red runtime dispatch pixels | 9088 |
| Candidate-minus-runtime hole pixels | 13336 |
| Runtime pixels outside candidate | 0 |
| Final white/layer pixels | 15304 |
| Final red-tint pixels | 6108 |
| Final other pixels | 1012 |
| Targets inside red candidate | 59 |
| Targets inside red runtime dispatch | 0 |
| Targets inside candidate-minus-runtime hole | 59 |
| Targets final white/layer | 59 |
| Targets final red-tint | 0 |
| Red runtime dispatch final red-tint | 6108 |
| Red runtime dispatch final white/layer | 1968 |
| Red runtime dispatch final other | 1012 |

## Bounds

| Domain | Bounds |
|---|---|
| Red candidate | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Red runtime dispatch | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Candidate-minus-runtime holes | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Original target cluster | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` |
| Final white/layer | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Final red-tint | `{'left': 7, 'top': 7, 'right': 585, 'bottom': 585, 'rightInclusive': 584, 'bottomInclusive': 584}` |

The candidate and runtime domains share broad bounds, so bounds alone are not
enough. The red runtime dispatch set is sparse: it has four connected
components, while the target cluster remains final white/layer inside a
candidate-minus-runtime hole.

## Target Cluster

| Measure | Value |
|---|---:|
| Cluster pixels | 59 |
| Cluster connected components | 1 |
| Inside red candidate | 59 |
| Inside red runtime dispatch | 0 |
| Inside candidate-minus-runtime hole | 59 |
| Final white/layer pixels | 59 |
| Final red-tint pixels | 0 |
| Min Euclidean distance to red runtime dispatch | 70.936591 |
| Avg Euclidean distance to red runtime dispatch | 77.870976 |
| Max Euclidean distance to red runtime dispatch | 84.386018 |
| Min Manhattan distance to red runtime dispatch | 100 |
| Max Manhattan distance to red runtime dispatch | 119 |
| Min Chebyshev distance to red runtime dispatch | 54 |
| Max Chebyshev distance to red runtime dispatch | 64 |

Nearest target: `104,84` to
red runtime dispatch `158,38`.

Target hole component: `candidate-minus-runtime-002`,
pixels `3293`, bounds
`{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}`, final white/layer
`3293`, final red-tint
`0`.

## Zone Spatial Comparison

| Subzone | Candidate | Runtime red | Candidate-runtime holes | Original targets | Targets in runtime | Final white/layer | Final red-tint | Final other | Target min dist | Target avg dist | Target max dist |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `draw_oval_outer_boundary` | 10856 | 5548 | 5308 | 59 | 0 | 6176 | 3700 | 980 | 70.936591 | 77.870976 | 84.386018 |
| `difference_oval_inner_boundary` | 6168 | 1192 | 4976 | 0 | 0 | 4976 | 1176 | 16 | n/a | n/a | n/a |
| `halo_interior` | 3568 | 1248 | 2320 | 0 | 0 | 2320 | 1232 | 16 | n/a | n/a | n/a |
| `outside_draw_oval` | 1832 | 1100 | 732 | 0 | 0 | 1832 | 0 | 0 | n/a | n/a | n/a |
| `blurred_content_envelope` | 20592 | 7988 | 12604 | 59 | 0 | 13472 | 6108 | 1012 | 70.936591 | 77.870976 | 84.386018 |

## Connected Component Groups

### Red Runtime Dispatch

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 0 | 496 | 1526 | 253 |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 0 | 496 | 1526 | 253 |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 0 | 488 | 1528 | 254 |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 0 | 488 | 1528 | 252 |

### Candidate-Minus-Runtime Holes

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
| `candidate-minus-runtime-000` | 3295 | `{'left': 330, 'top': 18, 'right': 574, 'bottom': 262, 'rightInclusive': 573, 'bottomInclusive': 261}` | 0 | 3295 | 0 | 0 |
| `candidate-minus-runtime-001` | 3295 | `{'left': 330, 'top': 330, 'right': 574, 'bottom': 574, 'rightInclusive': 573, 'bottomInclusive': 573}` | 0 | 3295 | 0 | 0 |
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 59 | 3293 | 0 | 0 |
| `candidate-minus-runtime-003` | 3293 | `{'left': 18, 'top': 330, 'right': 262, 'bottom': 574, 'rightInclusive': 261, 'bottomInclusive': 573}` | 0 | 3293 | 0 | 0 |
| `candidate-minus-runtime-004` | 9 | `{'left': 262, 'top': 17, 'right': 271, 'bottom': 18, 'rightInclusive': 270, 'bottomInclusive': 17}` | 0 | 9 | 0 | 0 |
| `candidate-minus-runtime-005` | 9 | `{'left': 321, 'top': 17, 'right': 330, 'bottom': 18, 'rightInclusive': 329, 'bottomInclusive': 17}` | 0 | 9 | 0 | 0 |
| `candidate-minus-runtime-006` | 9 | `{'left': 17, 'top': 262, 'right': 18, 'bottom': 271, 'rightInclusive': 17, 'bottomInclusive': 270}` | 0 | 9 | 0 | 0 |
| `candidate-minus-runtime-007` | 9 | `{'left': 574, 'top': 262, 'right': 575, 'bottom': 271, 'rightInclusive': 574, 'bottomInclusive': 270}` | 0 | 9 | 0 | 0 |
| `candidate-minus-runtime-008` | 9 | `{'left': 17, 'top': 321, 'right': 18, 'bottom': 330, 'rightInclusive': 17, 'bottomInclusive': 329}` | 0 | 9 | 0 | 0 |
| `candidate-minus-runtime-009` | 9 | `{'left': 574, 'top': 321, 'right': 575, 'bottom': 330, 'rightInclusive': 574, 'bottomInclusive': 329}` | 0 | 9 | 0 | 0 |

### Final White/Layer

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
| `final-white-layer-000` | 15208 | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` | 59 | 15208 | 0 | 0 |
| `final-white-layer-001` | 9 | `{'left': 262, 'top': 17, 'right': 271, 'bottom': 18, 'rightInclusive': 270, 'bottomInclusive': 17}` | 0 | 9 | 0 | 0 |
| `final-white-layer-002` | 9 | `{'left': 321, 'top': 17, 'right': 330, 'bottom': 18, 'rightInclusive': 329, 'bottomInclusive': 17}` | 0 | 9 | 0 | 0 |
| `final-white-layer-003` | 9 | `{'left': 17, 'top': 262, 'right': 18, 'bottom': 271, 'rightInclusive': 17, 'bottomInclusive': 270}` | 0 | 9 | 0 | 0 |
| `final-white-layer-004` | 9 | `{'left': 574, 'top': 262, 'right': 575, 'bottom': 271, 'rightInclusive': 574, 'bottomInclusive': 270}` | 0 | 9 | 0 | 0 |
| `final-white-layer-005` | 9 | `{'left': 17, 'top': 321, 'right': 18, 'bottom': 330, 'rightInclusive': 17, 'bottomInclusive': 329}` | 0 | 9 | 0 | 0 |
| `final-white-layer-006` | 9 | `{'left': 574, 'top': 321, 'right': 575, 'bottom': 330, 'rightInclusive': 574, 'bottomInclusive': 329}` | 0 | 9 | 0 | 0 |
| `final-white-layer-007` | 9 | `{'left': 262, 'top': 574, 'right': 271, 'bottom': 575, 'rightInclusive': 270, 'bottomInclusive': 574}` | 0 | 9 | 0 | 0 |

### Final Red-Tint

| Component | Pixels | Bounds | Original targets | Final white/layer | Final red-tint | Final other |
|---|---:|---|---:|---:|---:|---:|
| `final-red-tint-000` | 1528 | `{'left': 7, 'top': 181, 'right': 31, 'bottom': 411, 'rightInclusive': 30, 'bottomInclusive': 410}` | 0 | 0 | 1528 | 0 |
| `final-red-tint-001` | 1528 | `{'left': 561, 'top': 181, 'right': 585, 'bottom': 411, 'rightInclusive': 584, 'bottomInclusive': 410}` | 0 | 0 | 1528 | 0 |
| `final-red-tint-002` | 1526 | `{'left': 181, 'top': 7, 'right': 411, 'bottom': 31, 'rightInclusive': 410, 'bottomInclusive': 30}` | 0 | 0 | 1526 | 0 |
| `final-red-tint-003` | 1526 | `{'left': 181, 'top': 561, 'right': 411, 'bottom': 585, 'rightInclusive': 410, 'bottomInclusive': 584}` | 0 | 0 | 1526 | 0 |

## Interpretation

- Non-membership in the runtime red domain: `True`.
- Reconstructed candidate includes original targets: `True`.
- Reconstructed candidate is overbroad at the original target cluster: `True`.
- Runtime-domain final-composite mismatch still present: `True`.
- Runtime-domain final-composite mismatch dominates next fix: `False`.

The next diagnostic focus is the spatial candidate/runtime split around the
original target cluster, not the final-composite mismatch inside the red
runtime dispatch domain.

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`
- FOR-293 decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`
- FOR-294 decision: `RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS`
- FOR-295 decision: `ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace, or
setPixel behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-runtime-spatial-separation-for296.json`
