# FOR-384 M60 F16 pre-correction geometry/coverage metadata audit

Decision: `M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED`

Classification: `metadata-candidate-defendable-runtime-proof-still-blocked`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-correction-geometry-coverage-metadata-audit-for384/m60-f16-pre-correction-geometry-coverage-metadata-audit-for384.json`

FOR-384 reste diagnostic-only. Il n'applique aucune correction renderer,
n'active pas le probe FOR-380 par defaut, ne change pas les seuils, les
fallbacks, le score, WGSL, ni le runtime GPU.

## Resultat court

L'audit inspecte les 9 pixels du meilleur predicate FOR-383
`partial-alpha-current-error-shape`. Dans ce sous-ensemble, une metadata
geometrie/couverture suffit a exclure le pixel regresse restant :
`coverage-alpha-at-least-96` selectionne les 8 pixels source-locale utiles et
0 pixel regresse.

La correction locale reste bloquee pour le moteur. La separation est propre
dans le perimetre FOR-383, mais ce perimetre herite encore d'un signal de
residuel courant et de forme d'erreur face a la reference Skia. La prochaine
etape doit produire le predicate complet avec des metadata renderer, sans ce
filtre d'oracle.

## Perimetre inspecte

| Source | Valeur |
|---|---:|
| Predicate source FOR-383 | `partial-alpha-current-error-shape` |
| Pixels inspectes | 9 |
| Pixels source-locale | 8 |
| Pixels regresses inclus | 1 |

Le pixel regresse restant est le point `(21,81)` dans la bande `butt-bevel`,
avec alpha couverture/source `64`. Les 8 pixels source-locale ont alpha
couverture/source `96` ou `160`.

## Candidats metadata

| Candidat | Selectionnes | Source locale retrouves | Regresses inclus | Precision | Rappel | Residuel si applique |
|---|---:|---:|---:|---:|---:|---:|
| `coverage-alpha-at-least-96` | 8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669 |
| `coverage-and-source-alpha-at-least-96` | 8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669 |
| `round-cap-or-high-coverage` | 8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669 |
| `band-local-fringe-window` | 8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669 |
| `round-cap-only` | 7 | 7 | 0 | 1.0000 | 0.8750 | 682 -> 621 |

Le candidat principal retenu est `coverage-alpha-at-least-96`, car il est le
plus simple : il n'utilise ni reference Skia, ni residuel du probe, ni
`deltaVsCurrent`, ni appartenance FOR-379 comme predicate principal.

## Gardes anti-oracle

Les candidats de metadata ne selectionnent pas avec :

- la reference Skia ;
- le resultat du probe ;
- le residuel du probe ;
- `deltaVsCurrent` ;
- l'appartenance FOR-379.

Ces informations restent uniquement dans l'artefact comme verite d'evaluation
pour mesurer precision, rappel et regression incluse.

## Decision de suite

La correction locale bornee devient plus plausible, mais elle ne doit pas etre
activee encore. Il faut d'abord transformer cette separation couverture/geometry
en predicate moteur complet qui ne depend pas du sous-ensemble FOR-383.
