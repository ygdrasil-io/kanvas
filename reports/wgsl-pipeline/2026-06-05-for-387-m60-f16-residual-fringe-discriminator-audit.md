# FOR-387 M60 F16 residual fringe discriminator audit

Decision: `M60_F16_RESIDUAL_FRINGE_DISCRIMINATOR_AUDIT_RECORDED`

Classification: `fringe-discriminator-too-broad`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-residual-fringe-discriminator-audit-for387/m60-f16-residual-fringe-discriminator-audit-for387.json`

FOR-387 reste diagnostic-only. Il n'applique aucune correction renderer,
n'active pas le probe FOR-380 par defaut, ne change pas les seuils, les
fallbacks, le score, WGSL, ni le runtime GPU.

## Resultat court

L'audit inspecte les 35 pixels selectionnes par le meilleur discriminateur
FOR-386 `source-fringe-band-local-window`. Ces 35 pixels contiennent les 8
pixels source-locale utiles, mais encore 27 pixels regresses.

Le meilleur candidat plus fin teste est `local-window-edge-distance-le-17` :

- 33 pixels selectionnes ;
- 8 pixels source-locale retrouves ;
- 25 regressions encore incluses ;
- precision 0.2424 ;
- rappel 1.0000 ;
- residuel si applique : 768 -> 2044.

Le signal conserve le rappel complet, mais il reste trop large. La correction
moteur reste bloquee.

## Repartition des 27 regressions restantes

Par bande/cap/join :

| Groupe | Pixels |
|---|---:|
| `round-round|cap=round|join=round` | 20 |
| `butt-bevel|cap=butt|join=bevel` | 7 |

Par position locale de bande :

| Groupe | Pixels |
|---|---:|
| `round-round|40-47` | 17 |
| `butt-bevel|16-23` | 5 |
| `round-round|32-39` | 3 |
| `butt-bevel|8-15` | 2 |

Par distance au bord :

| Distance | Pixels |
|---|---:|
| `5-8` | 12 |
| `2-4` | 8 |
| `9-16` | 4 |
| `17-32` | 3 |

Par alpha couverture/source :

| Lane alpha | Pixels |
|---|---:|
| `160-191` | 25 |
| `96-127` | 2 |

La topologie de frange disponible ne separe pas le residu : les 27 regressions
sont toutes `two-sided-solid-fringe`, avec `coverage-equals-transparent-source-alpha`.
Les pixels utiles partagent aussi cette famille de signaux, ce qui explique le
blocage.

## Candidats discriminants

| Candidat | Selectionnes | Source locale retrouves | Regresses inclus | Precision | Rappel | Residuel si applique |
|---|---:|---:|---:|---:|---:|---:|
| `local-window-edge-distance-le-17` | 33 | 8 | 25 | 0.2424 | 1.0000 | 768 -> 2044 |
| `local-window-edge-distance-le-8` | 27 | 7 | 20 | 0.2593 | 0.8750 | 710 -> 1763 |
| `round-local-window-alpha-160-or-butt-edge-alpha-96` | 26 | 6 | 20 | 0.2308 | 0.7500 | 658 -> 1718 |
| `round-source-coverage-equal-alpha-160` | 26 | 6 | 20 | 0.2308 | 0.7500 | 658 -> 1718 |
| `round-transition-or-butt-terminal-edge` | 0 | 0 | 0 | 0.0000 | 0.0000 | 0 -> 0 |
| `butt-low-edge-source-coverage-equal` | 0 | 0 | 0 | 0.0000 | 0.0000 | 0 -> 0 |
| `alpha-160-fringe-transition` | 0 | 0 | 0 | 0.0000 | 0.0000 | 0 -> 0 |

## Gardes anti-oracle

Les candidats ne selectionnent pas avec :

- la reference Skia ;
- le resultat du probe ;
- le residuel du probe ;
- `deltaVsCurrent` ;
- l'appartenance FOR-379 ;
- le predicate FOR-383 ;
- le predicate FOR-384 ;
- le predicate FOR-385 ;
- le predicate FOR-386 comme predicate primaire ;
- le residuel courant ;
- la forme d'erreur courante.

Le predicate FOR-386 definit seulement le perimetre d'audit demande par le
ticket. Les categories FOR-382 et le resultat du probe restent uniquement des
verites d'evaluation pour compter rappel, precision et regressions incluses.

## Decision de suite

Ne pas activer la correction. Les metadonnees actuelles de bande, cap, join,
position locale, distance au bord, alpha couverture/source, voisinage
orthogonal et topologie inferable ne suffisent pas a isoler les 8 pixels utiles
sans garder au moins 25 regressions. La prochaine preuve doit exporter une
metadata renderer plus fine, probablement une relation explicite de composition
source/couverture ou une topologie de frange orientee plus riche.
