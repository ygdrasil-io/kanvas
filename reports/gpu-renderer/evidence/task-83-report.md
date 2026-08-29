# W107 — rotation à angle droit d’un stroke sous clip path

## Objectif

Prouver que la route native existante `native.path_stroke.stencil_cover` accepte
un segment diagonal `butt/miter`, sans anti-aliasing, sous un clip path Winding,
lorsque la transformation est une rotation exacte de 90°. La vague vérifie aussi
que la nouvelle capacité reste bornée : une rotation de 45° doit rester refusée.

## Cause identifiée et correction

Avant correction, le cas à 90° était classé comme une transformation affine et
refusé par `GPUStrokeDescriptor` avec
`refused.unsupported.geometry.perspective_path`. Les contrats du clip stencil et
les listes de transformations autorisées du builder n’incluaient pas non plus
`right-angle-rotation`.

La correction est limitée aux rotations exactes de 90° et 180° déjà reconnues par
le mapping de transformation. Le descriptor de stroke conserve désormais la
classe canonique du path, et les contrats du stroke, du clip stencil et de la
préparation autorisent `right-angle-rotation`. Les rotations générales restent
hors contrat.

## Preuve native

Fixture 32×32 : segment local `(8.25,8.25) → (20.25,14.25)`, largeur 4,
`butt/miter`, AA désactivé, rotation 90° autour de `(16,16)`, sous triangle
Winding `INTERSECT`. Le test vérifie :

- route `native.path_stroke.stencil_cover` ;
- clip `StencilCoverage`, classe `right-angle-rotation` ;
- opérations stencil Winding `IncrementWrap/DecrementWrap` et consumer
  `NotEqual` ;
- préparation native réussie ;
- soumission et readback uniques ;
- résultat RGBA complet identique à un oracle CPU indépendant en coordonnées
  device (point-centre dans le triangle et distance au segment).

Le test négatif 45° confirme la frontière stable :
`refused.unsupported.geometry.perspective_path`.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.non right angle rotated diagonal stroke remains explicitly refused' --rerun-tasks
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La preuve positive 90° a également été validée séparément par le test ciblé
W107. La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a ensuite
passé avec succès (tous les tests PASS).

## Limites

Cette vague ne prend pas en charge les rotations arbitraires, les transformations
affines générales, les caps ronds, ni les contours multi-segments. Aucun seuil,
PNG de référence ou `gpu-renderer-scenes` n’a été modifié.
