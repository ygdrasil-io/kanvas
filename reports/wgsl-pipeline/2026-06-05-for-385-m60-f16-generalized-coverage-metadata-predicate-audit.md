# FOR-385 M60 F16 generalized coverage metadata predicate audit

Decision: `M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED`

Classification: `generalized-predicate-too-broad`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-generalized-coverage-metadata-predicate-audit-for385/m60-f16-generalized-coverage-metadata-predicate-audit-for385.json`

FOR-385 reste diagnostic-only. Il n'applique aucune correction renderer,
n'active pas le probe FOR-380 par defaut, ne change pas les seuils, les
fallbacks, le score, WGSL, ni le runtime GPU.

## Resultat court

L'audit sort du filtre FOR-383 et applique les candidats metadata/couverture
sur le perimetre `full-scene-nonzero-coverage-source-alpha` : tous les pixels
M60 F16 avec alpha couverture et alpha source transparente non nuls avant
correction.

Ce perimetre contient 3179 pixels, dont les 9 pixels FOR-383 seulement comme
comparaison. La generalisation n'est pas encore exploitable comme correction
moteur : le meilleur candidat retrouve les 8 pixels source-locale utiles, mais
inclut 428 pixels regresses.

## Perimetre inspecte

| Source | Valeur |
|---|---:|
| Pixels scene complete | 24576 |
| Pixels du perimetre generalise | 3179 |
| Pixels FOR-383 dans le perimetre, comparaison seule | 9 |
| Pixels source-locale utiles | 8 |
| Pixels regresses dans la scene | 3171 |

Le perimetre generalise n'utilise ni reference Skia, ni resultat de probe, ni
residuel du probe, ni `deltaVsCurrent`, ni appartenance FOR-379/FOR-383/FOR-384
comme filtre primaire.

## Candidats metadata

| Candidat | Selectionnes | Source locale retrouves | Regresses inclus | Precision | Rappel | Residuel si applique |
|---|---:|---:|---:|---:|---:|---:|
| `coverage-alpha-at-least-96` | 3164 | 8 | 3156 | 0.0025 | 1.0000 | 1855 -> 230735 |
| `coverage-and-source-alpha-at-least-96` | 3164 | 8 | 3156 | 0.0025 | 1.0000 | 1855 -> 230735 |
| `partial-coverage-alpha-at-least-96` | 436 | 8 | 428 | 0.0183 | 1.0000 | 993 -> 21135 |
| `coverage-alpha-at-least-160` | 3134 | 7 | 3127 | 0.0022 | 0.8750 | 1766 -> 229630 |
| `round-cap-or-high-coverage` | 3171 | 8 | 3163 | 0.0025 | 1.0000 | 1933 -> 230931 |
| `high-coverage-fringe-neighborhood` | 512 | 0 | 512 | 0.0000 | 0.0000 | 203 -> 37797 |

Le meilleur candidat au sens FOR-385 est `partial-coverage-alpha-at-least-96` :
il conserve le rappel complet et reduit le nombre de pixels regresses inclus,
mais reste trop large. `coverage-alpha-at-least-96`, candidat source de
FOR-384, se revele massif hors filtre FOR-383.

## Gardes anti-oracle

Les candidats de metadata ne selectionnent pas avec :

- la reference Skia ;
- le resultat du probe ;
- le residuel du probe ;
- `deltaVsCurrent` ;
- l'appartenance FOR-379 ;
- le predicate FOR-383 ;
- le predicate FOR-384 ;
- le residuel courant ;
- la forme d'erreur courante.

Les categories FOR-382 et le resultat du probe restent uniquement des verites
d'evaluation pour compter rappel, precision et regressions incluses.

## Decision de suite

La correction locale bornee reste bloquee. La prochaine preuve doit ajouter un
signal renderer plus discriminant sur les 428 regressions encore selectionnees,
probablement par bande/cap/join, lane d'alpha ou forme de voisinage de
couverture, sans revenir au filtre FOR-383.
