# FOR-286 CPU Active AA Difference Store Trace

Linear: `FOR-286`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_SRCOVER_OR_PAYLOAD`

Residual classification: `CPU_ACTIVE_AA_DIFFERENCE_LAYER_STORE_ORDER_RESIDUAL`

Next action: `TARGET_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER`

## Resultat Court

FOR-286 ajoute le stade manquant a FOR-283/FOR-285: la valeur que le chemin
CPU `kSrcOver` ecrit via `setPixel` sous `activeAaClip` difference. Aucun
renderer n'est modifie; le validateur reconstruit le store a partir des
branches source exactes et des PNG M60 versionnes.

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | 148 |
| Pixels cibles CPU/reference >32 | 89 |
| Source dispatch rouge | 89 |
| Coverage `activeAaClip` non nulle dans `blend` | 89 |
| Destination blanche avant blend | 89 |
| Resultat avant ecriture red-tint | 89 |
| Valeur ecrite red-tint | 89 |
| Valeur ecrite rouge dominante | 82 |
| Readback rouge dominant apres draw | 9 |
| Readback blanc/layer apres draw | 78 |
| Ecrit vs readback >32 | 81 |
| Ecrit vs readback delta max | 255 |

## Fenetres FOR-278

| Zone | Cibles | Src rouge | Cov non nulle | Dst blanc | Blend red-tint | Write red-tint | Readback blanc | Write/readback >32 | Src ancre | Src+cov ancre | Blend ancre | Write ancre | Readback ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| `draw_oval_outer_boundary` | 59 | 59 | 59 | 59 | 59 | 59 | 59 | 52 | `[255, 0, 0, 250]` | `[255, 0, 0, 250]` | `[255, 5, 5, 255]` | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` |
| `difference_oval_inner_boundary` | 18 | 18 | 18 | 18 | 18 | 18 | 14 | 17 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |
| `halo_interior` | 12 | 12 | 12 | 12 | 12 | 12 | 5 | 12 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | Src dispatch | cov | Dst avant | Blend avant write | Valeur ecrite | Readback apres draw | Write/readback delta |
|---|---|---:|---|---:|---|---|---|---|---:|
| `difference_oval_inner_boundary` | 262,17 | 237 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | 255 |
| `halo_interior` | 226,21 | 235 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | 255 |
| `halo_interior` | 222,22 | 235 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | 255 |
| `halo_interior` | 221,22 | 234 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | 255 |
| `halo_interior` | 225,21 | 231 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 252, 252, 255]` | 252 |
| `difference_oval_inner_boundary` | 261,17 | 229 | `[255, 0, 0, 255]` | 255 | `[255, 255, 255, 255]` | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[254, 249, 247, 255]` | 249 |
| `difference_oval_inner_boundary` | 263,17 | 226 | `[255, 0, 0, 255]` | 240 | `[255, 255, 255, 255]` | `[255, 15, 15, 255]` | `[255, 15, 15, 255]` | `[255, 255, 255, 255]` | 240 |
| `draw_oval_outer_boundary` | 99,89 | 224 | `[255, 0, 0, 250]` | 255 | `[255, 255, 255, 255]` | `[255, 5, 5, 255]` | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` | 250 |

## Separation Des Causes

| Cause | Retenue | Preuve |
|---|---|---|
| `srcOverMath` | `False` | The reconstructed kSrcOver branch writes the exact pre-write blend result for 89/89 FOR-278 target pixels; 89/89 written values carry red tint. |
| `activeAaClipApplication` | `False` | activeAaClip coverage is applied in blend() and remains non-zero for 89/89 target pixels; coverage modulation still leaves 89/89 red-tinted write values. |
| `layerOrStoreOrder` | `True` | The reconstructed store writes red-tinted values for 89/89 target pixels, but the committed CPU output reads back only 9 red-dominant pixels and 78 white/layer pixels. |
| `proofInsufficient` | `False` | FOR-286 has enough bounded reconstruction evidence to choose the next correction target, while still avoiding any production renderer change in this ticket. |

Decision: `NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_SRCOVER_OR_PAYLOAD`. Le bug cible n'est pas la math `SrcOver`
ni l'application scalaire de `activeAaClip`: les deux produisent une valeur
ecrite rouge pour les 89 pixels. Le residu est classe
`CPU_ACTIVE_AA_DIFFERENCE_LAYER_STORE_ORDER_RESIDUAL` parce que la sortie CPU finale relit
majoritairement le blanc/layer.

## Pleine Scene Et Preservation

| Mesure | Valeur |
|---|---:|
| CPU/reference similarity | 97.31% |
| CPU matching pixels | 908439 |
| CPU max channel delta | 237 |
| CPU/reference >32 | 15726 |
| GPU/reference similarity | 98.48% |
| GPU/reference >32 | 2869 |

M60 reste `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
Le diagnostic crop reste `image-filter.crop-input-nonnull-prepass-required`. Aucun seuil,
chemin GPU/WebGPU, `blend`, `setPixel`, support global, Ganesh/Graphite ou
SkSL n'est modifie.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-active-aa-difference-store-trace-for286.json`
