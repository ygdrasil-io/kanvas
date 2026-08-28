# W35 — contour de rectangle avec translation entière

## Ce qui est prouvé

La route publique `Surface` rend nativement un contour de rectangle solide
non-AA après une translation entière. La scène
`translated-stroke-rect-outline` dessine un rectangle `(16,16)-(48,48)`,
épaisseur `6`, après `translate(5,7)`. Les quatre bandes du contour sont donc
attendues aux coordonnées device `(18,20)-(56,26)`, `(18,52)-(56,58)`,
`(18,26)-(24,52)` et `(50,26)-(56,52)`.

Ce n'est pas une nouvelle généralisation de géométrie :
`GPUPreparedStrokeRectLowerer` savait déjà abaisser cette forme sur la route
native `kanvas.surface.render`. W35 ajoute le cas public, l'oracle et la
preuve qui attestent ce contrat précis.

## Bornes et refus

La preuve couvre un `DrawRect` stroke solide, opaque, non-AA, avec une
translation entière. Elle ne promet ni rotation, ni scale, ni translation
fractionnaire, ni stroke général.

Les gardes existantes refusent avant soumission :

- AA : `unsupported.stroke.rect_anti_alias` ;
- translation fractionnaire, scale, skew ou perspective :
  `unsupported.stroke.rect_transform` ;
- shader, gradient, filtre ou autre matériau non solide :
  `unsupported.stroke.rect_material`.

Ces refus sont couverts par `GPUPreparedStrokeRectLowererTest` et préservent
la politique de fallback au lieu d'élargir implicitement la route.

## Preuve CPU/GPU promue

L'oracle `reference-raster-stroke-rect-bands` version 2 est indépendant de la
route GPU : il remplit directement le fond puis les quatre bandes device aux
coordonnées ci-dessus. L'évidence promue est dans
`correctness/promoted/translated-stroke-rect-outline/` et est rattachée au
commit source `40794259f491f77b757de609f367cc3f42eb6952`.

- GPU : route `kanvas.surface.render`, une soumission, cinq draws et un bind
  pipeline ;
- diagnostics : aucun refus, `submissionDelta = 1` ;
- comparaison RGBA8 : 0 pixel différent, delta maximal 0, similarité 100 %
  (tolérance 0).

Commandes rejouables :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=translated-stroke-rect-outline -PsourceCommit=40794259f491f77b757de609f367cc3f42eb6952
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=translated-stroke-rect-outline -PsourceCommit=40794259f491f77b757de609f367cc3f42eb6952
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=translated-stroke-rect-outline -PsourceCommit=40794259f491f77b757de609f367cc3f42eb6952 -PpromotionReviewer=codex -PpromotionReason='W35 proves the existing public Surface integer-translated non-AA stroke rectangle route.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
