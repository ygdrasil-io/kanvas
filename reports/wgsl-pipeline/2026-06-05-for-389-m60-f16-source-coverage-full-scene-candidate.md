# FOR-389 M60 F16 source/coverage full-scene candidate

Decision: `M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED`

Classification: `full-scene-regresses`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-coverage-full-scene-candidate-for389/m60-f16-source-coverage-full-scene-candidate-for389.json`

Source: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-metadata-audit-for388/m60-f16-composition-metadata-audit-for388.json`

FOR-389 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas WGSL, les fallbacks, les seuils, le scoring, ni la
politique de promotion.

## Resultat court

FOR-388 avait prouve `source-color-and-oriented-coverage-lane` sur 33 pixels locaux : 8 utiles, 0
regression. L'evaluation full-scene selectionne maintenant 16
pixels :

- 8 pixels ameliores ;
- 0 pixels inchanges ;
- 8 pixels regresses ;
- 8/8 pixels source-locale retrouves ;
- precision 0.5000 ;
- rappel 1.0000.

Le residuel selectionne passe de 734 a
1109. Simule sur la scene complete, appliquer le probe
uniquement aux pixels selectionnes ferait passer le residuel de
2014 a 2389.

La classification est donc `full-scene-regresses`.

## Garde explicite

Le candidat est documente derriere `kanvas.webgpu.m60F16SourceCoverageFullSceneCandidate.enabled`, desactive par defaut, en
mode `diagnostic-simulation-only`. Aucun hook runtime n'est installe.

## Preuve de selection

La selection utilise uniquement :

- `transparentSourceRgba` pour la famille source bleue ;
- `coverageOrthogonalNeighborhood` pour le cote de coverage oriente ;
- `strokeBand`/cap/join et `coverageAlphaByte`.

Elle n'utilise pas la reference Skia, le resultat du probe, le residuel courant,
le residuel probe, `deltaVsCurrent`, ni les verites FOR-387/FOR-388 comme oracle.
Ces donnees servent seulement a mesurer precision, rappel et regressions.

## Risque observe

Les 8 regressions selectionnees sont des pixels deja reference-equivalents avant
probe, mais la recomposition source-couleur les degrade. Le candidat parfait sur
le perimetre FOR-388 n'est donc pas defendable en full-scene sans metadata plus
etroite.
