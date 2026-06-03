# FOR-278 M60 Boundary Layer Composite Fixture

Linear: `FOR-278`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

Dominant finding: `MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED`.

FOR-278 extrait une fixture d'audit minimale depuis les PNG reference/CPU/GPU
M60 existants. Elle ne modifie aucun renderer: trois fenetres 11x11 sont
centrees sur les pixels CPU/reference les plus divergents des zones FOR-277
`draw_oval_outer_boundary`, `difference_oval_inner_boundary` et
`halo_interior`, puis intersectees avec leurs masques de zone.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels de fixture combines | 148 |
| CPU/reference >32 pixels | 89 |
| CPU/reference max channel delta | 237 |
| CPU blanc/layer sur >32 | 78 |
| CPU blanc/layer share | 87.640449% |
| Reference rouge teintee sur >32 | 56 |
| Reference rouge teintee share | 62.921348% |
| CPU alpha >32 | 0 |
| GPU/reference >32 pixels | 11 |
| GPU/reference max channel delta | 41 |

La fixture reproduit le signal FOR-277: le CPU conserve du blanc/fond ou layer
la ou la reference garde une charge rouge teintee, sans delta alpha >32. Le GPU
reste beaucoup plus proche de la reference sur ces fenetres.

## Fenetres De Bord

| Zone | Bounds | Pixels | CPU/ref >32 | GPU/ref >32 | CPU blanc/layer | Ref rouge | CPU-ref sample signe |
|---|---|---:|---:|---:|---:|---:|---|
| `draw_oval_outer_boundary` | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 95}` | 59 | 59 | 11 | 100.0% | 54.237288% | `[52, 190, 224, 0]` |
| `difference_oval_inner_boundary` | `{'left': 257, 'top': 12, 'right': 268, 'bottom': 23}` | 67 | 18 | 0 | 77.777778% | 66.666667% | `[53, 196, 237, 0]` |
| `halo_interior` | `{'left': 221, 'top': 16, 'right': 232, 'bottom': 27}` | 22 | 12 | 0 | 41.666667% | 100.0% | `[53, 195, 235, 0]` |

## Echantillons Signes

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | GPU-reference signe |
|---|---|---|---|---|---|---|
| `draw_oval_outer_boundary` | 99,89 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | `[-1, -5, -10, 0]` |
| `difference_oval_inner_boundary` | 262,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | `[0, 0, 1, 0]` |
| `halo_interior` | 226,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | `[0, -1, -1, 0]` |

## Classification

`MINIMIZED_BOUNDARY_LAYER_COMPOSITE_RESIDUAL_ISOLATED`.

A three-window crop around the strongest FOR-277 boundary pixels keeps the same polarity: CPU stores white/layer-background RGB while the Skia reference keeps red-tinted blur payload, with no alpha >32 delta. GPU is much closer to reference in these windows.

## Decision

Conserver `expected-unsupported` avec fallback
`coverage.nested-clip-visual-parity-below-threshold`. Preserver
`image-filter.crop-input-nonnull-prepass-required`. Aucun seuil n'est affaibli et aucun
support large clip-stack, readback/fallback, Ganesh, Graphite ou compilateur
SkSL n'est ajoute.

## Prochaine Action

`TARGET_CPU_LAYER_BACKGROUND_COMPOSITE_AROUND_DIFFERENCE_CLIP_BOUNDARY`.

La prochaine correction doit cibler le composite CPU fond/layer autour du bord
`clipRRect(kDifference)` + blur SrcIn, puis verifier que ces fenetres et la
scene complete M60 bougent avant toute promotion.

## Validation

```text
rtk python3 scripts/validate_for278_m60_boundary_layer_composite_fixture.py
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-boundary-layer-composite-fixture-for278.json`
