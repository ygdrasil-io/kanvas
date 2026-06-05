# FOR-386 M60 F16 coverage regression discriminator audit

Decision: `M60_F16_COVERAGE_REGRESSION_DISCRIMINATOR_AUDIT_RECORDED`

Classification: `discriminator-candidate-too-broad`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-regression-discriminator-audit-for386/m60-f16-coverage-regression-discriminator-audit-for386.json`

FOR-386 reste diagnostic-only. Il n'applique aucune correction renderer,
n'active pas le probe FOR-380 par defaut, ne change pas les seuils, les
fallbacks, le score, WGSL, ni le runtime GPU.

## Resultat court

L'audit inspecte les 436 pixels selectionnes par le meilleur candidat FOR-385
`partial-coverage-alpha-at-least-96`. Ces 436 pixels contiennent les 8 pixels
source-locale utiles, mais aussi 428 pixels regresses.

Le meilleur discriminateur teste est `source-fringe-band-local-window` :

- 35 pixels selectionnes ;
- 8 pixels source-locale retrouves ;
- 27 pixels regresses encore inclus ;
- precision 0.2286 ;
- rappel 1.0000 ;
- residuel si applique : 768 -> 2154.

Le signal est nettement plus precis que FOR-385, mais reste trop large. La
correction moteur reste donc bloquee.

## Repartition des 428 regressions

Par bande/cap/join :

| Groupe | Pixels |
|---|---:|
| `square-bevel|cap=square|join=bevel` | 232 |
| `round-round|cap=round|join=round` | 132 |
| `butt-bevel|cap=butt|join=bevel` | 64 |

Par lane d'alpha couverture/source :

| Lane alpha | Pixels |
|---|---:|
| `160-191` | 382 |
| `128-159` | 18 |
| `96-127` | 11 |
| `224-254` | 9 |
| `192-223` | 8 |

Par distance au bord de bande :

| Distance | Pixels |
|---|---:|
| `17-32` | 141 |
| `9-16` | 115 |
| `33+` | 80 |
| `5-8` | 41 |
| `2-4` | 31 |
| `0-1` | 20 |

Le groupe dominant est donc un ensemble de pixels partiels alpha 160 dans des
zones de bande/cap/join qui ne se confondent pas avec les 8 pixels utiles.

## Candidats discriminants

| Candidat | Selectionnes | Source locale retrouves | Regresses inclus | Precision | Rappel | Residuel si applique |
|---|---:|---:|---:|---:|---:|---:|
| `source-fringe-band-local-window` | 35 | 8 | 27 | 0.2286 | 1.0000 | 768 -> 2154 |
| `round-cap-high-alpha-lane` | 124 | 6 | 118 | 0.0484 | 0.7500 | 736 -> 7315 |
| `butt-or-round-low-edge-distance` | 246 | 8 | 238 | 0.0325 | 1.0000 | 894 -> 12579 |
| `alpha-160-quiet-neighborhood` | 360 | 7 | 353 | 0.0194 | 0.8750 | 876 -> 17321 |
| `round-cap-local-fringe-window` | 27 | 7 | 20 | 0.2593 | 0.8750 | 710 -> 1763 |
| `coverage-neighborhood-sparse-edge` | 382 | 8 | 374 | 0.0209 | 1.0000 | 946 -> 18133 |

## Gardes anti-oracle

Les candidats ne selectionnent pas avec :

- la reference Skia ;
- le resultat du probe ;
- le residuel du probe ;
- `deltaVsCurrent` ;
- l'appartenance FOR-379 ;
- le predicate FOR-383 ;
- le predicate FOR-384 ;
- le predicate FOR-385 comme predicate primaire ;
- le residuel courant ;
- la forme d'erreur courante.

Le predicate FOR-385 definit seulement le perimetre d'audit demande par le
ticket. Les categories FOR-382 et le resultat du probe restent uniquement des
verites d'evaluation pour compter rappel, precision et regressions incluses.

## Decision de suite

Ne pas activer la correction. Le meilleur signal actuel reduit fortement le
bruit, mais il inclut encore 27 regressions. La prochaine preuve doit exporter
ou deriver une metadata renderer plus fine que la fenetre locale de bande,
probablement autour de la composition source/couverture ou de la topologie de
frange, avant tout ticket de correction.
