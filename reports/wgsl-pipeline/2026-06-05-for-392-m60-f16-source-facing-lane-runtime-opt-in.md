# FOR-392 M60 F16 sourceFacingLocalBandLane runtime opt-in

Decision: `M60_F16_SOURCE_FACING_LANE_RUNTIME_OPT_IN_EVALUATED`

Classification: `runtime-hook-refused-missing-per-fragment-lane-metadata`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-facing-lane-runtime-opt-in-for392/m60-f16-source-facing-lane-runtime-opt-in-for392.json`

FOR-392 ajoute le garde opt-in `kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled`, desactive par defaut. Le
garde est reconnu cote renderer mais il n'active pas de correction : le hook
runtime est refuse car le shader AA stencil-cover n'expose pas de metadata par
fragment `strokeBand`, `bandLocalX` ou `sourceFacingLocalBandLane`.

## Resultat

- comportement par defaut inchange : residuel M60 F16 `2014` ;
- garde FOR-392 active : correction refusee, residuel `2014`, delta `0` ;
- simulation ideale du predicate si metadata runtime par fragment disponible : `2014 -> 1949` ;
- pixels selectionnes : `8` ;
- ameliores recuperes : `8/8` ;
- regressions incluses : `0/8` ;
- promotion : `false`.

## Pourquoi le hook est refuse

Le vieux probe FOR-380 est un controle draw-wide : il force toute la passe a
conserver la source dans le domaine direct/recompose-on-white. FOR-389 a deja
montre que cette famille regresse en full-scene (`2014 -> 2389`). Le nouveau
predicate ne doit toucher que les 8 pixels `sourceFacingLocalBandLane` ; sans metadata
par fragment, brancher le garde sur ce probe serait une activation unsafe.

## Pixels du predicate

| Coord | Stroke band | bandLocalX | Evaluation | Residuel |
|---|---|---:|---|---:|
| (93,74) | `round-round` | 45 | `improved` | 52 -> 45 |
| (92,75) | `round-round` | 44 | `improved` | 105 -> 96 |
| (91,76) | `round-round` | 43 | `improved` | 105 -> 96 |
| (17,77) | `butt-bevel` | 17 | `improved` | 52 -> 48 |
| (90,77) | `round-round` | 42 | `improved` | 105 -> 96 |
| (89,78) | `round-round` | 41 | `improved` | 105 -> 96 |
| (88,79) | `round-round` | 40 | `improved` | 105 -> 96 |
| (87,80) | `round-round` | 39 | `improved` | 105 -> 96 |

## Politique

Le resultat reste candidate-only, non promu, non active par defaut et limite a
M60 F16. Aucun score, seuil, fallback, scene non liee ou WGSL runtime non lie
n'est modifie.
