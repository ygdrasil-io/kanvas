# FOR-383 M60 F16 pre-probe predicate audit

Decision: `M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED`

Classification: `pre-probe-predicate-too-broad`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-probe-predicate-audit-for383/m60-f16-pre-probe-predicate-audit-for383.json`

FOR-383 reste diagnostic-only. Il n'applique aucune correction renderer,
n'active pas le probe FOR-380 par defaut, ne change pas les seuils, les
fallbacks, le score, WGSL, ni le runtime GPU.

## Resultat court

L'audit teste des predicates disponibles avant correction : alpha de
couverture/source, bande cap/join, voisinage local, residuel courant et forme
d'erreur RGB courante.

Le meilleur candidat est `partial-alpha-current-error-shape`. Il retrouve les
8 pixels source/couleur locaux de FOR-382, mais selectionne aussi 1 pixel qui
regresse sous le probe. Ce n'est donc pas encore un predicate moteur activable.
La prochaine etape doit produire une preuve de geometrie/couverture ou une
metadata renderer qui separe ce pixel regresse sans utiliser la reference Skia.

## Verite d'evaluation preservee

| Jeu | Pixels | Residuel avant | Residuel apres | Delta |
|---|---:|---:|---:|---:|
| Tous les pixels audites | 24576 | 2014 | 231162 | 229148 |
| Ameliores FOR-381 | 8 | 734 | 669 | -65 |
| Regresses FOR-381 | 3171 | 1274 | 230487 | 229213 |
| Inchanges FOR-381 | 21397 | 6 | 6 | 0 |
| Critiques FOR-379 | 10 | 856 | 816 | -40 |

Categories FOR-382 conservees :

| Categorie | Pixels |
|---|---:|
| `source-locale-plausible` | 8 |
| `coverage-composition-plausible` | 3024 |
| `mixed` | 147 |
| `insufficient` | 21397 |

## Predicates candidats

| Predicate | Selectionnes | Source locale retrouves | Regresses inclus | Precision | Rappel | Residuel si applique |
|---|---:|---:|---:|---:|---:|---:|
| `partial-coverage-and-source-alpha` | 451 | 8 | 443 | 0.0177 | 1.0000 | 1146 -> 21556 |
| `partial-alpha-current-residual-high` | 10 | 8 | 2 | 0.8000 | 1.0000 | 856 -> 816 |
| `partial-alpha-current-error-shape` | 9 | 8 | 1 | 0.8889 | 1.0000 | 790 -> 734 |
| `partial-alpha-round-or-butt-fringe` | 9 | 8 | 1 | 0.8889 | 1.0000 | 790 -> 734 |
| `partial-alpha-current-low` | 441 | 0 | 441 | 0.0000 | 0.0000 | 290 -> 20740 |

Les deux meilleurs candidats restent trop larges. Ils prouvent que les signaux
pre-probe contiennent presque assez d'information, mais pas assez pour corriger
localement sans risque.

## Gardes anti-oracle

Les predicates candidats ne selectionnent pas avec :

- `deltaVsCurrent < 0` ;
- le residuel du probe ;
- l'appartenance FOR-379 comme oracle principal.

Ces informations restent uniquement dans l'artefact comme verite d'evaluation
pour mesurer precision, rappel et regression incluse.

## Decision de suite

Ne pas tenter de correction locale renderer tout de suite. La suite utile est
un audit plus proche du moteur : prouver une appartenance couverture/composition
ou exposer une metadata renderer avant correction qui exclut le pixel regresse
restant.
