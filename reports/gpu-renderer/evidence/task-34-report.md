# W48 — radial gradient à trois stops sous scale uniforme

W48 ajoute une route GPU native dédiée à un `DrawRect(STROKE)` Kanvas avec un
`RadialGradient` sRGB `CLAMP` à trois stops `[0, 0.5, 1]`, sous scale uniforme
positive entière de 2 et translation entière `(2,4)`.

Le lowerer rebase le centre, le rayon et la géométrie du stroke en coordonnées
device. La provenance `AnalyticStrokeRectUniformScaleRadialThreeStopBand`, la
capability runtime et le product flag sont distincts du cas deux stops. Toute
variante hors contrat (capability absente, target non sRGB, AA, autre nombre ou
positions de stops, tile mode ou transform non prouvé) reste refusée avec un
code stable.

L’oracle CPU indépendant interpole les trois stops en espace linéaire après
décodage sRGB, sur la distance au centre de chaque pixel, puis réencode en
RGBA8 sRGB. La scène
`radial-gradient-three-stop-uniform-scaled-stroke-rect` passe à 100 %, avec
0 pixel divergent, delta maximal 0 et delta moyen 0 (tolérance 1 LSB). La
route observée rapporte 1 soumission, 4 draws, 1 bind de pipeline et aucun
diagnostic.

La génération, la vérification et la promotion incrémentale de la scène ont
passé. Le catalogue promu contient maintenant 111 scènes.

Commandes principales :

```text
./gradlew --offline :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.product.ProductFlagConfigTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeCapabilitiesTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --offline :integration-tests:gpu-evidence:test --tests '*GpuEvidenceCatalogTest' --tests '*GpuEvidenceCatalogOracleTest'
./gradlew --offline :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=radial-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=e10a2cbc34a9b00aa3238e8d29e38e5eac48ad9f
./gradlew --offline :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=radial-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=e10a2cbc34a9b00aa3238e8d29e38e5eac48ad9f
./gradlew --offline :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=radial-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=e10a2cbc34a9b00aa3238e8d29e38e5eac48ad9f -PpromotionReviewer=codex
./gradlew --offline :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
