# W49 — `SweepGradient` à trois stops sous scale uniforme

W49 ajoute le rendu GPU natif d’un `DrawRect(STROKE)` public Kanvas avec un
`SweepGradient` sRGB `CLAMP` à trois stops `[0, 0.5, 1]`, sur un angle complet
`0→360`, sous une scale uniforme positive entière de `2` et une translation
entière `(2,4)`.

Le lowerer rebase le centre du sweep et les quatre bandes du contour en
coordonnées device, et met à l’échelle la largeur du stroke. La provenance
`AnalyticStrokeRectUniformScaleSweepThreeStopBand`, la capability runtime, le
product flag et la route
`native.stroke_rect.sweep_gradient_three_stop_uniform_scale` sont dédiés à ce
contrat. Les variantes hors contrat (capability absente, target non sRGB, AA,
`localMatrix`, stops différents, tile mode autre que `CLAMP`, angle partiel ou
transform non uniforme) refusent explicitement avant émission, sans fallback.

L’oracle CPU indépendant calcule l’angle au centre de chaque pixel en
coordonnées device, interpole les trois stops après décodage sRGB, puis encode
la valeur en RGBA8 sRGB. Il ne réutilise ni le materializer GPU ni le shader
WGSL.

La preuve `sweep-gradient-three-stop-uniform-scaled-stroke-rect`, générée
depuis le commit source
`7c104d2341e7d971dbd1aba9994603259f9ac3d8`, est promue. Elle rapporte une
soumission, quatre draws et un bind de pipeline, sans diagnostic. La
comparaison est pixel-validée à 100 % : 0 pixel divergent, delta maximal 0 et
delta moyen 0, avec une tolérance de 1 LSB. Les artefacts promus sont sous
`reports/gpu-renderer/evidence/correctness/promoted/sweep-gradient-three-stop-uniform-scaled-stroke-rect/`.

Vérifications exécutées :

```text
./gradlew --offline :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.product.ProductFlagConfigTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeCapabilitiesTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --offline :integration-tests:gpu-evidence:test --tests '*GpuEvidenceCatalogTest' --tests '*GpuEvidenceCatalogOracleTest'
./gradlew --offline :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=7c104d2341e7d971dbd1aba9994603259f9ac3d8
./gradlew --offline :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=7c104d2341e7d971dbd1aba9994603259f9ac3d8
./gradlew --offline :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=7c104d2341e7d971dbd1aba9994603259f9ac3d8 -PpromotionReviewer=codex
./gradlew --offline :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
