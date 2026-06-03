# FOR-283 CPU Dispatch Blend Store Trace

Linear: `FOR-283`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `TARGETED_CORRECTION_POSSIBLE_AT_CPU_MASK_FILTER_DISPATCH_CALLSITE`

Support scene: `KEEP_EXPECTED_UNSUPPORTED`

FOR-283 trace de maniere bornee les coordonnees FOR-278 deja isolees:
source equivalente entrant dans `dispatchBlend`, `cov`, destination avant
blend, resultat `SrcOver` avant store, puis valeur relue dans la sortie CPU.
Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | 148 |
| Pixels cibles CPU/reference >32 | 89 |
| Source dispatch rouge | 89 |
| `cov` non nul | 89 |
| Destination blanche avant blend | 89 |
| Source apres coverage rouge | 89 |
| Resultat blend teinte rouge avant store | 89 |
| Resultat blend rouge dominant avant store | 82 |
| Rouge dominant relu dans sortie CPU | 9 |
| Rouge reference-like relu FOR-282 | 0 |
| Blanc/layer relu dans sortie CPU | 78 |
| Blend vs readback >32 | 81 |
| Blend vs readback delta max | 255 |

Les 89 pixels critiques ont une source rouge locale, une couverture non
nulle, un `dst` blanc, et une teinte rouge avant store. La sortie CPU relue
reste dominee par 78 pixels blanc/layer et 0 pixel rouge reference-like dans
la classification FOR-282. La correction large du blend ou de
`SkBitmap.setPixel` n'est donc pas justifiee par cette trace.

## Fenetres FOR-278

| Zone | Pixels | Cibles | Src rouge | Cov non nul | Blend teinte | Readback rouge | Readback blanc | Src ancre | Src+cov ancre | Blend ancre | Readback ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
| `draw_oval_outer_boundary` | 59 | 59 | 59 | 59 | 59 | 0 | 59 | `[255, 0, 0, 250]` | `[255, 0, 0, 250]` | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` |
| `difference_oval_inner_boundary` | 67 | 18 | 18 | 18 | 18 | 3 | 14 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |
| `halo_interior` | 22 | 12 | 12 | 12 | 12 | 6 | 5 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | Src dispatch | cov | Dst avant | Blend avant store | Readback CPU | Reference | Blend/readback delta |
|---|---|---:|---|---:|---|---|---|---|---:|
| `difference_oval_inner_boundary` | 262,17 | 237 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | `[202, 59, 18, 255]` | 255 |
| `halo_interior` | 226,21 | 235 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | `[202, 60, 20, 255]` | 255 |
| `halo_interior` | 222,22 | 235 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | `[202, 60, 20, 255]` | 255 |
| `halo_interior` | 221,22 | 234 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` | 255 |
| `halo_interior` | 225,21 | 231 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 252, 252, 255]` | `[202, 60, 21, 255]` | 252 |
| `difference_oval_inner_boundary` | 261,17 | 229 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[254, 249, 247, 255]` | `[202, 59, 18, 255]` | 249 |
| `difference_oval_inner_boundary` | 263,17 | 226 | `[255, 0, 0, 255]` | 240 | `[255, 255, 255, 255]` | `[255, 15, 15, 255]` | `[255, 255, 255, 255]` | `[203, 64, 29, 255]` | 240 |
| `draw_oval_outer_boundary` | 99,89 | 224 | `[255, 0, 0, 250]` | 255 | `[255, 255, 255, 255]` | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` | `[203, 65, 31, 255]` | 250 |

## Interpretation

FOR-283 produit la decision `TARGETED_CORRECTION_POSSIBLE_AT_CPU_MASK_FILTER_DISPATCH_CALLSITE`.

Le chemin causal restant est le callsite CPU `drawPathWithMaskFilter` pour le
cas A8 solid + `SkColorFilters.Blend(RED,kSrcIn)`: soit le payload qui atteint
effectivement `dispatchBlend` diverge de ce contrat local, soit l'appel/store
est evite pour ces pixels malgre `maskA > 0` et `cov > 0`. La prochaine
action nommee est `PATCH_CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_DISPATCH_PAYLOAD_AND_REGENERATE_M60`.

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

## Validation

```text
rtk python3 scripts/validate_for283_cpu_dispatch_blend_store_trace.py
rtk python3 scripts/validate_for282_cpu_color_filter_srcin_blend_parity.py
rtk ./gradlew pipelineSceneDashboardGate
rtk git diff --check origin/master...HEAD
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-dispatch-blend-store-trace-for283.json`
