# FOR-281 CPU Mask Filter Clip Coverage Trace

Linear: `FOR-281`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `REFUSE_CORRECTION_PENDING_MASK_SOURCE_COLOR_FILTER_BLEND_PARITY`

Support scene: `KEEP_EXPECTED_UNSUPPORTED`

FOR-281 trace les valeurs demandees pour les fenetres FOR-278:
`maskA` apres blur, `clipCoverage`, `src.alpha` avant modulation, puis
`src.alpha` apres modulation par le clip final. Aucun renderer n'est modifie.

## Resultat Court

| Mesure | Valeur |
|---|---:|
| Pixels fixture FOR-278 | 148 |
| CPU/reference >32 dans la fixture | 89 |
| GPU/reference >32 dans la fixture | 11 |
| `maskA` zero sur CPU/ref >32 | 0 |
| `src.alpha` apres modulation zero sur CPU/ref >32 | 0 |
| `maskA` partiel / plein | 61 / 28 |
| `clipCoverage` partiel / plein | 10 / 79 |
| `src.alpha` avant min / max / moyenne | 6 / 255 / 191.134831 |
| `src.alpha` apres min / max / moyenne | 6 / 255 / 177.94382 |

Les 89 pixels critiques ont tous `maskA > 0` et `src.alpha > 0` apres
modulation par le clip. Une correction qui force seulement `maskA`,
`clipCoverage`, ou le composite fond/layer serait donc non causale.

## Fenetres FOR-278

| Zone | Pixels | CPU/ref >32 | `maskA` | `clipCoverage` | `src.alpha` apres | `maskA` ancre | clip ancre | alpha apres ancre |
|---|---:|---:|---|---|---|---:|---:|---:|
| `draw_oval_outer_boundary` | 59 | 59 | `{'zero': 0, 'partial': 59, 'full': 0}` | `{'zero': 0, 'partial': 0, 'full': 59}` | `{'zero': 0, 'partial': 59, 'full': 0}` | 250 | 255 | 250 |
| `difference_oval_inner_boundary` | 67 | 18 | `{'zero': 0, 'partial': 0, 'full': 18}` | `{'zero': 0, 'partial': 10, 'full': 8}` | `{'zero': 0, 'partial': 10, 'full': 8}` | 255 | 255 | 255 |
| `halo_interior` | 22 | 12 | `{'zero': 0, 'partial': 2, 'full': 10}` | `{'zero': 0, 'partial': 0, 'full': 12}` | `{'zero': 0, 'partial': 2, 'full': 10}` | 255 | 255 | 255 |

## Echantillons Haute Difference

| Zone | Pixel | Delta max | `maskA` | clip | alpha avant | alpha apres | Reference RGBA | CPU RGBA | GPU RGBA |
|---|---|---:|---:|---:|---:|---:|---|---|---|
| `difference_oval_inner_boundary` | 262,17 | 237 | 255 | 255 | 255 | 255 | `[202, 59, 18, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 226,21 | 235 | 255 | 255 | 255 | 255 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 222,22 | 235 | 255 | 255 | 255 | 255 | `[202, 60, 20, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 221,22 | 234 | 255 | 255 | 255 | 255 | `[202, 60, 21, 255]` | `[255, 255, 255, 255]` | `[202, 59, 19, 255]` |
| `halo_interior` | 225,21 | 231 | 255 | 255 | 255 | 255 | `[202, 60, 21, 255]` | `[255, 252, 252, 255]` | `[202, 59, 19, 255]` |
| `difference_oval_inner_boundary` | 261,17 | 229 | 255 | 255 | 255 | 255 | `[202, 59, 18, 255]` | `[254, 249, 247, 255]` | `[202, 59, 19, 255]` |
| `difference_oval_inner_boundary` | 263,17 | 226 | 255 | 240 | 255 | 240 | `[203, 64, 29, 255]` | `[255, 255, 255, 255]` | `[203, 65, 31, 255]` |
| `draw_oval_outer_boundary` | 99,89 | 224 | 250 | 255 | 250 | 250 | `[203, 65, 31, 255]` | `[255, 255, 255, 255]` | `[202, 60, 21, 255]` |

## Interpretation

FOR-281 refuse une correction de production immediate:
`REFUSE_CORRECTION_PENDING_MASK_SOURCE_COLOR_FILTER_BLEND_PARITY`.

Le residu visible reste RGB/payload sur alpha opaque. Les valeurs tracees
montrent que le masque floute et la modulation de clip ne tombent pas a zero
sur les pixels cibles. La suite admissible doit isoler la parite
`SkColorFilters.Blend(RED, kSrcIn)` + alpha de masque + `SrcOver` sur une
fixture bornee, puis seulement ensuite changer le renderer.

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

`TARGET_CPU_MASK_FILTER_COLOR_FILTER_SRCIN_BLEND_PARITY`.

## Validation

```text
rtk python3 scripts/validate_for281_cpu_mask_filter_clip_coverage_trace.py
rtk python3 scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-mask-filter-clip-coverage-trace-for281.json`
