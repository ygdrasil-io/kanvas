# FOR-391 M60 F16 source-facing local band lane metadata

Decision: `M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED`

Classification: `source-facing-local-band-lane-stable-diagnostic-only`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-facing-lane-metadata-for391/m60-f16-source-facing-lane-metadata-for391.json`

Sources:

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-full-scene-regression-discriminator-for390/m60-f16-full-scene-regression-discriminator-for390.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-coverage-full-scene-candidate-for389/m60-f16-source-coverage-full-scene-candidate-for389.json`

FOR-391 reste diagnostic-only. Il nomme et exporte la metadata
`sourceFacingLocalBandLane`, sans correction, sans hook runtime, sans changement WGSL,
fallback, scoring, seuil ou promotion.

## Metadata

`sourceFacingLocalBandLane` est derivee avant correction depuis les metadata renderer
`strokeBand` et `bandLocalX` :

- `round-round && bandLocalX >= 39` ;
- `butt-bevel && bandLocalX <= 17`.

Les champs interdits pour la derivation sont la reference Skia, le probe, les
residuels, `deltaVsCurrent`, et les verites FOR-389/FOR-390. Ils restent
seulement disponibles dans `evaluationOnly` pour verifier les compteurs.

## Resultat

- pixels selectionnes : 8 ;
- ameliores recuperes : 8/8 ;
- regressions incluses : 0/8 ;
- precision : 1.0000 ;
- rappel : 1.0000 ;
- residuel full-scene estime : 2014 -> 1949.

Ces compteurs reproduisent FOR-390 pour `source-facing-local-band-lane` tout en rendant le
signal explicite comme metadata diagnostique stable.

## Pixels exportes

| Coord | sourceFacingLocalBandLane | Stroke band | bandLocalX | Evaluation | Residuel |
|---|---:|---|---:|---|---:|
| (52,50) | `False` | `round-round` | 4 | `regressed` | 0 -> 55 |
| (51,51) | `False` | `round-round` | 3 | `regressed` | 0 -> 55 |
| (50,52) | `False` | `round-round` | 2 | `regressed` | 0 -> 55 |
| (49,53) | `False` | `round-round` | 1 | `regressed` | 0 -> 55 |
| (48,54) | `False` | `round-round` | 0 | `regressed` | 0 -> 55 |
| (93,74) | `True` | `round-round` | 45 | `improved` | 52 -> 45 |
| (92,75) | `True` | `round-round` | 44 | `improved` | 105 -> 96 |
| (91,76) | `True` | `round-round` | 43 | `improved` | 105 -> 96 |
| (17,77) | `True` | `butt-bevel` | 17 | `improved` | 52 -> 48 |
| (90,77) | `True` | `round-round` | 42 | `improved` | 105 -> 96 |
| (18,78) | `False` | `butt-bevel` | 18 | `regressed` | 0 -> 55 |
| (89,78) | `True` | `round-round` | 41 | `improved` | 105 -> 96 |
| (19,79) | `False` | `butt-bevel` | 19 | `regressed` | 0 -> 55 |
| (88,79) | `True` | `round-round` | 40 | `improved` | 105 -> 96 |
| (20,80) | `False` | `butt-bevel` | 20 | `regressed` | 0 -> 55 |
| (87,80) | `True` | `round-round` | 39 | `improved` | 105 -> 96 |

## Non-activation

FOR-391 ne modifie pas le renderer, n'installe aucun hook runtime et n'active
aucune correction. Toute evaluation runtime reste hors portee de ce ticket.
