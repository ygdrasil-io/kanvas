# W37 — LinearGradient à trois stops sur les strokes de rectangle

## Périmètre livré

La route publique `Kanvas Surface` accepte maintenant un `DrawRect(STROKE)`
non-AA, à CTM et `localMatrix` identités, avec un `LinearGradient` `CLAMP` à
trois stops, sur la cible `rgba8unorm-srgb`. Le lowerer conserve l'origine
typée `AnalyticStrokeRectBand` pour les quatre bandes analytiques et exige la
capability dédiée
`first_slice.stroke_rect.linear_gradient_three_stop.native`.

La voie `FillRect` publique de W32 reste séparée : une commande synthétique ou
une autre origine ne peut pas réutiliser cette admission. Les gradients à
quatre stops ou plus, les autres targets, l'anti-aliasing, une transformation,
une matrice locale non identité, ou une capability désactivée restent refusés
avant publication des paquets.

## Preuve native

La scène `linear-gradient-three-stop-stroke-rect` est validée pour le commit
`9c562b9aa8ec760cd0261d9b4db088696238024b`.

- route : `kanvas.surface.render` ;
- route d'analyse : `native.stroke_rect.linear_gradient_three_stop` ;
- exécution : 1 soumission, 4 draws, 1 bind pipeline ;
- refus : aucun ; `submissionDelta = 1` ;
- oracle : CPU indépendant à trois stops, interpolation segmentée sRGB dans
  l'espace linéaire prémultiplié et stockage sRGB ;
- comparaison : 100 %, 0 pixel différent, delta maximal 0, tolérance 1 LSB.

Les artefacts générés sont sous
`reports/gpu-renderer/evidence/correctness/generated/<commit>/` et restent
reproductibles par les commandes ci-dessous. La promotion est effectuée après
review de la preuve et vérification du catalogue.

## Vérification reproductible

```text
./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeCapabilitiesTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest --tests org.graphiks.kanvas.gpu.renderer.product.ProductFlagConfigTest
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-three-stop-stroke-rect -PsourceCommit=9c562b9aa8ec760cd0261d9b4db088696238024b
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-three-stop-stroke-rect -PsourceCommit=9c562b9aa8ec760cd0261d9b4db088696238024b
```
