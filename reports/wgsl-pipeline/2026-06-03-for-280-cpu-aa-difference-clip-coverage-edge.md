# FOR-280 CPU AA Difference Clip Coverage Edge

Linear: `FOR-280`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `REFUSE_CORRECTION_PENDING_EXPLICIT_SKAA_CLIP_AND_MASK_FILTER_TRACE`

Support scene: `KEEP_EXPECTED_UNSUPPORTED`

FOR-280 audite le chemin causal restant apres FOR-279:
`SkAAClip` difference -> `SkBitmapDevice.drawPathWithMaskFilter` ->
`clipCoverage`. Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | 148 |
| CPU/reference >32 dans la fixture | 89 |
| GPU/reference >32 dans la fixture | 11 |
| Clip difference estime pleine couverture sur CPU/ref >32 | 79 |
| Clip difference estime couverture partielle sur CPU/ref >32 | 10 |
| Clip difference estime couverture zero sur CPU/ref >32 | 0 |
| Draw oval direct estime pleine couverture sur CPU/ref >32 | 64 |
| Draw oval direct estime couverture partielle sur CPU/ref >32 | 8 |
| Draw oval direct estime couverture zero sur CPU/ref >32 | 17 |
| CPU blanc/fond sur >32 | 78 |
| Reference rouge teintee sur >32 | 56 |
| CPU alpha >32 | 0 |

Le clip final estime ne coupe pas les pixels cibles: les 89 pixels
CPU/reference >32 ont tous une couverture `difference` non nulle, et 79 sont
estimes en pleine couverture. Les trois ancres FOR-278 sont en pleine couverture. Cela
refuse une correction limitee a `clipCoverage` ou au composite fond/layer.

## Fenetres FOR-278 Et Couverture Estimee

| Zone | Pixels | CPU/ref >32 | GPU/ref >32 | Repartition clip difference | Repartition draw oval | Clip ancre | Draw ancre |
|---|---:|---:|---:|---|---|---:|---:|
| `draw_oval_outer_boundary` | 59 | 59 | 11 | `{'zero': 0, 'partial': 0, 'full': 59}` | `{'zero': 17, 'partial': 8, 'full': 34}` | 255 | 255 |
| `difference_oval_inner_boundary` | 67 | 18 | 0 | `{'zero': 0, 'partial': 10, 'full': 8}` | `{'zero': 0, 'partial': 0, 'full': 18}` | 255 | 255 |
| `halo_interior` | 22 | 12 | 0 | `{'zero': 0, 'partial': 0, 'full': 12}` | `{'zero': 0, 'partial': 0, 'full': 12}` | 255 | 255 |

## Echantillons Ancres

| Zone | Pixel | Reference RGBA | CPU RGBA | GPU RGBA | CPU-reference signe | Clip estime | Draw estime |
|---|---|---|---|---|---|---:|---:|
| `draw_oval_outer_boundary` | 99,89 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | `[52, 190, 224, 0]` | 255 | 255 |
| `difference_oval_inner_boundary` | 262,17 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 196, 237, 0]` | 255 | 255 |
| `halo_interior` | 226,21 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` | `[53, 195, 235, 0]` | 255 | 255 |

## Relation Alpha/RGB

Le residu porte sur la charge RGB visible avec alpha opaque: les pixels CPU cibles sont majoritairement blancs/fond-layer alors que la reference reste rouge teintee; l'alpha ne differe jamais de plus de 32. Comme la couverture finale `difference` estimee est non nulle pour chaque pixel cible, une correction limitee a `clipCoverage` ou au composite fond/layer serait non causale.

Le signal reste RGB/payload sur alpha opaque: `alpha >32 == 0`, CPU garde
majoritairement blanc/fond, et la reference garde une charge rouge teintee.
Le GPU reste plus proche de la reference sur ces fenetres.

## Chemin CPU Observe

1. `BlurredClippedCircleGM.onDraw`: `save()`, `clipRRect(kDifference)`, puis `drawRRect`.
2. `SkCanvas.clipPathDifference`: construit le `SkAAClip` difference.
3. `SkAAClip.op`: applique `kDifference` via `parent * (255 - path) / 255`.
4. `SkBitmapDevice.drawPathWithMaskFilter`: calcule le masque floute puis appelle `dispatchBlend`.
5. `dispatchBlend`: applique `clipCoverage(x, y)` au `src.alpha`.

## Refus

`REFUSE_CORRECTION_PENDING_EXPLICIT_SKAA_CLIP_AND_MASK_FILTER_TRACE`.

La preuve est insuffisante pour un patch de production: `maskA` est local au
chemin `drawPathWithMaskFilter`, `clipCoverage` est prive, et les pixels finaux
ne separent pas directement source-mask, flou, clip final et blend. Le prochain
patch admissible doit donc ajouter une fixture/trace ciblee de ces deux valeurs
avant de modifier la regle generale de couverture.

## Pleine Scene

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | 97.31% |
| CPU matching pixels | 908439 |
| CPU max channel delta | 237 |
| CPU/reference >32 | 15726 |
| GPU/reference similarity | 98.48% |
| GPU/reference >32 | 2869 |

M60 reste `expected-unsupported` avec fallback `coverage.nested-clip-visual-parity-below-threshold`.
`image-filter.crop-input-nonnull-prepass-required` est preserve. Aucun seuil n'est affaibli,
aucun support large clip-stack/readback n'est ajoute, et aucun chemin
Ganesh/Graphite/SkSL n'est introduit.

## Prochaine Action

`INSTRUMENT_CPU_MASK_FILTER_AND_SKAA_CLIP_COVERAGE_EDGE_TRACE`.

La suite doit tracer, pour ces memes fenetres, `maskA` apres blur,
`clipCoverage`, `src.alpha` avant et apres modulation, puis comparer CPU,
reference et GPU avant toute correction.

## Validation

```text
rtk python3 scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk python3 scripts/validate_for278_m60_boundary_layer_composite_fixture.py
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-aa-difference-clip-coverage-edge-for280.json`
