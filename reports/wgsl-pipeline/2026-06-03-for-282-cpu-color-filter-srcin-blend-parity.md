# FOR-282 CPU Color Filter SrcIn Blend Parity

Linear: `FOR-282`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `REFUSE_CORRECTION_PENDING_RUNTIME_DISPATCH_BLEND_STORE_TRACE`

Support scene: `KEEP_EXPECTED_UNSUPPORTED`

FOR-282 reconstruit la charge couleur attendue pour
`SkColorFilters.Blend(RED, kSrcIn)`, puis applique `maskA`,
`clipCoverage`, et le `SrcOver` CPU sur le layer blanc deja dessine.
Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | 148 |
| Pixels cibles CPU/reference >32 | 89 |
| Charge rouge attendue avant `SrcOver` | 89 |
| Charge rouge observee CPU | 0 |
| Pixels CPU blanc/layer | 78 |
| Pixels reference rouge teintee | 56 |
| Pixels GPU proches reference | 78 |
| Reconstruction vs CPU >32 | 81 |
| Reconstruction vs reference >32 | 85 |
| Reconstruction vs GPU >32 | 69 |

Les 89 pixels critiques ont une charge rouge non nulle apres
`Blend(RED,kSrcIn)` + masque + clip. Le CPU final n'en garde aucune et
reste majoritairement blanc/layer. La perte est donc dans le chemin RGB
apres les preuves d'alpha FOR-280/FOR-281, mais le rejeu deterministe ne
prouve pas encore si la valeur runtime envoyee a `dispatchBlend` est deja
fausse ou si la perte arrive dans `SrcOver`/store.

## Fenetres FOR-278

| Zone | Pixels | Cibles | Rouge attendu | Rouge CPU | Blanc CPU | Rouge ref | Reco vs CPU >32 | Source ancre | Reco ancre | CPU ancre |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| `draw_oval_outer_boundary` | 59 | 59 | 59 | 0 | 59 | 32 | 52 | `[255, 0, 0, 250]` | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` |
| `difference_oval_inner_boundary` | 67 | 18 | 18 | 0 | 14 | 12 | 17 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |
| `halo_interior` | 22 | 12 | 12 | 0 | 5 | 12 | 12 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` |

## Echantillons Haute Difference

| Zone | Pixel | Delta CPU/ref | `maskA` | clip | Source apres clip | Reco SrcOver blanc | Reference RGBA | CPU RGBA | GPU RGBA |
|---|---|---:|---:|---:|---|---|---|---|---|
| `difference_oval_inner_boundary` | 262,17 | 237 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 226,21 | 235 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 222,22 | 235 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 221,22 | 234 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 60, 21, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 225,21 | 231 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 60, 21, 255]` | `[255, 252, 252, 255]` | `[202, 59, 19, 255]` |
| `difference_oval_inner_boundary` | 261,17 | 229 | 255 | 255 | `[255, 0, 0, 255]` | `[255, 0, 0, 255]` | `[202, 59, 18, 255]` | `[254, 249, 247, 255]` | `[202, 59, 19, 255]` |
| `difference_oval_inner_boundary` | 263,17 | 226 | 255 | 240 | `[255, 0, 0, 240]` | `[255, 15, 15, 255]` | `[203, 64, 29, 255]` | `[255, 255, 255, 255]` | `[203, 65, 31, 255]` |
| `draw_oval_outer_boundary` | 99,89 | 224 | 250 | 255 | `[255, 0, 0, 250]` | `[255, 5, 5, 255]` | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` |

## Interpretation

FOR-282 refuse une correction de production immediate:
`REFUSE_CORRECTION_PENDING_RUNTIME_DISPATCH_BLEND_STORE_TRACE`.

Ce refus est plus precis que FOR-281: la charge rouge attendue existe pour
tous les pixels cibles, et le CPU final ne la transporte pas. Le prochain
ticket doit instrumenter de maniere bornee les arguments runtime
`srcIn`, `cov`, `dst` et la sortie de `blend`/`setPixel` autour de ces memes
coordonnees. Sans cette trace, changer le renderer risquerait de corriger un
modele reconstruit plutot que le chemin causal effectif.

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

`TARGET_CPU_MASK_FILTER_DISPATCH_SRC_AND_DST_TRACE`.

## Validation

```text
rtk python3 scripts/validate_for282_cpu_color_filter_srcin_blend_parity.py
rtk python3 scripts/validate_for281_cpu_mask_filter_clip_coverage_trace.py
rtk python3 scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-color-filter-srcin-blend-parity-for282.json`
