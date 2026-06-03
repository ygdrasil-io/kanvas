# FOR-277 M60 Post-FOR-276 CPU Residual Audit

Linear: `FOR-277`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

Dominant finding: `LAYER_BACKGROUND_BOUNDARY_RESIDUAL_REMAINS_DOMINANT_AFTER_FOR276`.

FOR-277 relit la scene complete apres FOR-276 avec les masques de FOR-269,
FOR-271 et FOR-274. Aucun chemin renderer n'est modifie. Le but est de savoir
si la correction locale FOR-276 explique le residu CPU/reference de M60.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | 97.31% |
| CPU matching pixels | 908439 |
| CPU max channel delta | 237 |
| CPU/reference >32 pixels | 15726 |
| GPU/reference similarity | 98.48% |
| GPU matching pixels | 919363 |
| GPU max channel delta | 57 |
| GPU/reference >32 pixels | 2869 |
| Seuil promotion | 99.95% |

M60 reste `expected-unsupported`: CPU/reference et GPU/reference restent sous
le seuil strict de `99.95%`.

## Relation avec FOR-276

| Compteur fixture FOR-276 | Avant | Apres |
|---|---:|---:|
| Pixels rouges conserves dans le clip de halo | 0 | 8 |
| Pixels rouges perdus dans le clip de halo | 10 | 2 |
| Part recuperee | 0.0% | 80.0% |

La fixture bornee est valide, mais le residu pleine scene est numeriquement
inchange contre FOR-274: CPU/reference >32 delta
`0`, CPU max-delta delta
`0`. FOR-276 ne suffit donc pas a expliquer M60.

## Zones Dominantes

| Zone | CPU/ref >32 | GPU/ref >32 | CPU blanc/layer | CPU pixels blancs | CPU alpha >32 | CPU signed RGB moyen |
|---|---:|---:|---:|---:|---:|---|
| `draw_oval_outer_boundary` | 8077 | 2796 | 76.228798% | 6157 | 0 | `[31.37, 98.116, 107.845]` |
| `difference_oval_inner_boundary` | 5201 | 73 | 96.154586% | 5001 | 0 | `[48.141, 173.852, 206.74]` |
| `halo_interior` | 2448 | 0 | 96.405229% | 2360 | 0 | `[51.677, 190.746, 227.419]` |

Synthese CPU: les zones primaires portent
`15726` pixels >32,
soit `100.0%`
du residu CPU/reference. Les pixels CPU blancs/layer representent
`85.959557%` de ces zones, avec
`0` pixel a alpha
>32. Le probleme reste donc RGB/fond, pas une perte d'alpha globale.

## Echantillons Signes

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | GPU-reference signe |
|---|---|---|---|---|---|---|
| `draw_oval_outer_boundary` | 99,89 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | `[-1, -5, -10, 0]` |
| `draw_oval_outer_boundary` | 98,90 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | `[-1, -5, -10, 0]` |
| `difference_oval_inner_boundary` | 262,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | `[0, 0, 1, 0]` |
| `difference_oval_inner_boundary` | 329,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | `[0, 0, 1, 0]` |
| `halo_interior` | 226,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | `[0, -1, -1, 0]` |
| `halo_interior` | 365,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | `[0, -1, -1, 0]` |

## Classement des hypotheses

| Hypothese | Classement | Preuve simple |
|---|---|---|
| `layer_background_or_final_boundary_composite` | `DOMINANT` | CPU garde majoritairement du blanc/fond ou layer la ou la reference est rouge teintee, sans delta alpha >32. |
| `mask_extent_or_source_clip_order` | `REDUCED_AFTER_FOR276` | FOR-276 recupere 80% dans la fixture AA, mais les compteurs pleine scene restent identiques a FOR-274. |
| `color_payload` | `NOT_PRIMARY_FOR_CPU` | La polarite CPU est blanche/fond, pas une mauvaise charge rouge; le gros probleme GPU de couleur a deja ete reduit par FOR-273. |
| `reference_divergence` | `UNPROVEN` | Aucun cas minimal ne justifie encore un refus de la reference Skia. |

## Decision

Conserver `expected-unsupported` avec fallback
`coverage.nested-clip-visual-parity-below-threshold`. Preserver
`image-filter.crop-input-nonnull-prepass-required`. Aucun seuil n'est affaibli et
aucun support large clip-stack, readback/fallback, Ganesh, Graphite ou
compilateur SkSL n'est ajoute.

## Prochaine Action

`CREATE_MINIMIZED_FULL_SCENE_BOUNDARY_LAYER_COMPOSITE_AUDIT_BEFORE_ANY_M60_PROMOTION`.

La suite doit isoler le composite/fond de bord pleine scene, par exemple avec
une fixture minimale qui reproduit l'empilement saveLayer + difference oval +
blur SrcIn aux dimensions/bords M60, avant toute promotion.

## Validation

```text
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-post-for276-cpu-residual-audit-for277.json`
