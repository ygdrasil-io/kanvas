# FOR-390 M60 F16 full-scene regression discriminator

Decision: `M60_F16_FULL_SCENE_REGRESSION_DISCRIMINATOR_RECORDED`

Classification: `narrower-metadata-defendable`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-full-scene-regression-discriminator-for390/m60-f16-full-scene-regression-discriminator-for390.json`

Sources:

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-coverage-full-scene-candidate-for389/m60-f16-source-coverage-full-scene-candidate-for389.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-metadata-audit-for388/m60-f16-composition-metadata-audit-for388.json`

FOR-390 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas WGSL, les fallbacks, les seuils, le scoring, ni la
politique de promotion.

## Resultat court

FOR-389 selectionne 16 pixels avec `source-color-and-oriented-coverage-lane` : 8 ameliores et 8
regresses. FOR-390 teste des discriminateurs plus fins a l'interieur de cette
population deja selectionnee. Les 8 regressions ont la meme famille de source bleue, la meme
relation source/couverture et des alpha source/couverture egaux a 160. Elles se
separent par position locale de bande :

- round/round regresse sur `bandLocalX` 0..4, alors que les pixels utiles
  round/round sont sur 39..45 ;
- butt/bevel regresse sur `bandLocalX` 18..20, alors que le seul pixel utile
  butt/bevel est sur 17.

Le meilleur discriminateur documente est `source-facing-local-band-lane` :

- pixels selectionnes : 8 ;
- ameliores recuperes : 8/8 ;
- regressions incluses : 0/8 ;
- precision : 1.0000 ;
- rappel : 1.0000 ;
- residuel full-scene estime : 2014 -> 1949.

La classification est `narrower-metadata-defendable` parce qu'une metadata deja
presente dans l'audit (`strokeBand` + `bandLocalX`), appliquee comme raffinement
du prefiltre FOR-389, suffit a separer les 8 pixels utiles des 8 regressions sur
le perimetre FOR-389. Cette conclusion reste une preuve d'analyse : la metadata
doit etre capturee et nommee comme signal renderer stable avant toute activation.

## Pixels selectionnes

| Coord | Type evaluation | Stroke band | Orientation | bandLocalX | distance bord | coverage alpha | relation source/couverture |
|---|---:|---|---|---:|---:|---:|---|
| (52,50) | `regressed` | `round-round` | `north-west-solid` | 4 | 4 | 160 | `coverage-equals-transparent-source-alpha` |
| (51,51) | `regressed` | `round-round` | `north-west-solid` | 3 | 3 | 160 | `coverage-equals-transparent-source-alpha` |
| (50,52) | `regressed` | `round-round` | `north-west-solid` | 2 | 2 | 160 | `coverage-equals-transparent-source-alpha` |
| (49,53) | `regressed` | `round-round` | `north-west-solid` | 1 | 1 | 160 | `coverage-equals-transparent-source-alpha` |
| (48,54) | `regressed` | `round-round` | `north-west-solid` | 0 | 0 | 160 | `coverage-equals-transparent-source-alpha` |
| (93,74) | `improved` | `round-round` | `west-terminal` | 45 | 2 | 96 | `coverage-equals-transparent-source-alpha` |
| (92,75) | `improved` | `round-round` | `north-west-solid` | 44 | 3 | 160 | `coverage-equals-transparent-source-alpha` |
| (91,76) | `improved` | `round-round` | `north-west-solid` | 43 | 4 | 160 | `coverage-equals-transparent-source-alpha` |
| (17,77) | `improved` | `butt-bevel` | `north-east-solid` | 17 | 17 | 160 | `coverage-equals-transparent-source-alpha` |
| (90,77) | `improved` | `round-round` | `north-west-solid` | 42 | 5 | 160 | `coverage-equals-transparent-source-alpha` |
| (18,78) | `regressed` | `butt-bevel` | `north-east-solid` | 18 | 18 | 160 | `coverage-equals-transparent-source-alpha` |
| (89,78) | `improved` | `round-round` | `north-west-solid` | 41 | 6 | 160 | `coverage-equals-transparent-source-alpha` |
| (19,79) | `regressed` | `butt-bevel` | `north-east-solid` | 19 | 19 | 160 | `coverage-equals-transparent-source-alpha` |
| (88,79) | `improved` | `round-round` | `north-west-solid` | 40 | 7 | 160 | `coverage-equals-transparent-source-alpha` |
| (20,80) | `regressed` | `butt-bevel` | `north-east-solid` | 20 | 20 | 160 | `coverage-equals-transparent-source-alpha` |
| (87,80) | `improved` | `round-round` | `north-west-solid` | 39 | 8 | 160 | `coverage-equals-transparent-source-alpha` |

## Groupes des 8 regressions

| Metadata | Groupes |
|---|---|
| `transparentSourceRgba` | `rgba(59,86,190,160)`=8 |
| `transparentSourceColorFamily` | `blue-dominant-source`=8 |
| `orientedCoverageSide` | `north-east-solid`=3, `north-west-solid`=5 |
| `coverageOrthogonalNeighborhood` | `north=255,south=0,west=0,east=255`=3, `north=255,south=0,west=255,east=0`=5 |
| `coverageAlphaByte` | `160`=8 |
| `transparentSourceAlphaByte` | `160`=8 |
| `strokeBand` | `butt-bevel`=3, `round-round`=5 |
| `capJoin` | `butt-bevel`=3, `round-round`=5 |
| `bandLocalXBucket` | `left-terminal-0-8`=5, `source-adjacent-9-20`=3 |
| `bandEdgeDistanceBucket` | `edge-0-4`=5, `edge-18-20`=3 |
| `fringeTopology` | `2-solid-orthogonal-neighbors`=8 |
| `sourceCoverageRelation` | `coverage-equals-transparent-source-alpha`=8 |

## Discriminateurs testes

| Candidat | Selectionnes | Ameliores | Regressions | Precision | Rappel | Residuel estime | Classe |
|---|---:|---:|---:|---:|---:|---:|---|
| `for389-source-color-and-oriented-coverage-lane` | 16 | 8 | 8 | 0.5000 | 1.0000 | 2389 | `narrower-metadata-still-regresses` |
| `alpha-96-terminal-only` | 1 | 1 | 0 | 1.0000 | 0.1250 | 2007 | `metadata-insufficient` |
| `band-local-x-ge-17` | 11 | 8 | 3 | 0.7273 | 1.0000 | 2114 | `narrower-metadata-still-regresses` |
| `round-right-terminal-only` | 7 | 7 | 0 | 1.0000 | 0.8750 | 1953 | `metadata-insufficient` |
| `exclude-round-left-terminal` | 11 | 8 | 3 | 0.7273 | 1.0000 | 2114 | `narrower-metadata-still-regresses` |
| `source-facing-local-band-lane` | 8 | 8 | 0 | 1.0000 | 1.0000 | 1949 | `narrower-metadata-defendable` |
| `source-facing-local-band-lane-with-orientation` | 8 | 8 | 0 | 1.0000 | 1.0000 | 1949 | `narrower-metadata-defendable` |

## Garde anti-oracle

Les discriminateurs ci-dessus utilisent seulement des metadata renderer
disponibles dans l'audit : `transparentSourceRgba`, famille couleur source,
orientation coverage, voisinage orthogonal, alpha coverage/source, `strokeBand`,
cap/join, `bandLocalX`, distance au bord, topologie et relation
source/couverture.

Ils n'utilisent pas la reference Skia, le probe, le residuel courant,
`deltaVsCurrent`, ni les verites FOR-388/FOR-389 comme selection primaire. Ces
donnees restent dans l'artefact uniquement pour evaluer precision, rappel et
residuel estime.

## Suite

Ne pas activer `source-color-and-oriented-coverage-lane` dans FOR-390. Le prochain ticket peut
capturer un signal renderer explicite de type `source-facing local band lane`
et le re-tester en scene complete avant toute garde runtime.
