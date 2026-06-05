# FOR-388 M60 F16 composition metadata audit

Decision: `M60_F16_COMPOSITION_METADATA_AUDIT_RECORDED`

Classification: `usable-correction-candidate`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-metadata-audit-for388/m60-f16-composition-metadata-audit-for388.json`

Source: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-residual-fringe-discriminator-audit-for387/m60-f16-residual-fringe-discriminator-audit-for387.json`

FOR-388 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas le scoring, les seuils, WGSL, les fallbacks, ni la
politique de promotion.

## Resultat court

L'audit inspecte les 33 pixels du candidat FOR-387
`local-window-edge-distance-le-17`. Ce perimetre contient 8 pixels
source-locale utiles et 25 regressions restantes.

Le meilleur signal teste est `source-color-and-oriented-coverage-lane` :

- 8 pixels selectionnes ;
- 8/8 pixels source-locale retrouves ;
- 0 regression incluse ;
- precision 1.0000 ;
- rappel 1.0000 ;
- residuel estime si applique seulement a ces pixels : 734 -> 669.

Ce resultat classe le signal comme `usable-correction-candidate`. Il reste
seulement documente comme candidat ; la correction reste desactivee.

## Signaux audites

- Relation source alpha vs coverage alpha effective : tous les pixels du
  perimetre gardent `source-alpha-equals-effective-coverage`, donc ce signal
  seul reste trop large.
- Orientation locale de frange et cote interieur/exterieur approxime par le
  voisinage orthogonal de coverage : le signal reduit les regressions mais ne
  separe pas seul les pixels utiles.
- Contribution source locale independante de la couverture transparente :
  `transparentSourceRgba` permet de distinguer les contributions bleues et
  vertes, mais la contribution bleue seule inclut encore 11 regressions.
- Combinaison source bleue + orientation de coverage : separe les 8 pixels
  utiles avec 0 regression dans ce perimetre.

## Candidats

| Candidat | Signal | Selectionnes | Source locale retrouves | Regressions incluses | Precision | Rappel | Residuel estime | Classe |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `alpha-relation-equals-effective-coverage` | `source-alpha-vs-effective-coverage-alpha` | 33 | 8 | 25 | 0.2424 | 1.0000 | 768 -> 2044 | `still-too-broad` |
| `high-alpha-source-coverage-relation` | `source-alpha-vs-effective-coverage-alpha` | 30 | 7 | 23 | 0.2333 | 0.8750 | 710 -> 1931 | `insufficient-metadata` |
| `oriented-coverage-source-side` | `oriented-fringe-side` | 17 | 8 | 9 | 0.4706 | 1.0000 | 754 -> 1143 | `still-too-broad` |
| `blue-source-contribution` | `source-local-contribution-independent-of-transparent-coverage` | 19 | 8 | 11 | 0.4211 | 1.0000 | 740 -> 1232 | `still-too-broad` |
| `source-color-and-oriented-coverage-lane` | `source-color-plus-oriented-fringe-side` | 8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669 | `usable-correction-candidate` |

## Gardes

Les candidats n'utilisent pas la reference Skia, le resultat du probe, le
residuel du probe, `deltaVsCurrent`, le residuel courant, les categories
FOR-382, ni les predicates FOR-383 a FOR-386 comme selection primaire. Ces
verites restent uniquement des donnees d'evaluation pour mesurer precision,
rappel et regressions incluses.

## Decision de suite

Ne pas activer la correction dans FOR-388. Le candidat
`source-color-and-oriented-coverage-lane` doit etre traite comme une hypothese
mesuree pour un ticket ulterieur avec garde explicite, preuve full-scene, et
verification de stabilite des fallbacks.
