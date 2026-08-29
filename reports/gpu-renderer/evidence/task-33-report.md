# W47 — `RadialGradient` à deux stops sous scale uniforme

W47 ajoute le rendu GPU natif d’un `DrawRect(STROKE)` public Kanvas avec un
`RadialGradient` sRGB `CLAMP` à deux stops, sous scale uniforme positive de 2
et translation entière `(2,4)`. Le lowerer rebase le centre et le rayon du
gradient en coordonnées device et produit les quatre bandes du contour avec
une largeur de stroke mise à l’échelle.

L’oracle CPU indépendant calcule la distance radiale au centre de chaque pixel,
interpole les deux stops après décodage sRGB, puis réencode en RGBA8 sRGB. Il
ne réutilise ni le materializer GPU ni le shader WGSL. La preuve
`radial-gradient-two-stop-uniform-scaled-stroke-rect` est pixel-validée à
100 %, avec 0 pixel divergent, delta maximal 0 et delta moyen 0 (tolérance
1 LSB). La route native rapporte 1 soumission, 4 draws et 1 bind de pipeline,
sans diagnostic.

La provenance planner est
`AnalyticStrokeRectUniformScaleRadialTwoStopBand`, liée à la capability
`first_slice.stroke_rect.radial_gradient_two_stop_uniform_scale.native` et à la
route `native.stroke_rect.radial_gradient_two_stop_uniform_scale`. Les sources
génériques, les transforms non uniformes, l’AA, les targets non sRGB, les
gradients non-CLAMP et les nombres de stops différents restent refusés.

Pendant la génération complète, le cas historique `round-cap-stroke` a révélé
un bug indépendant : le planner reconnaissait le contrat rond exact, mais le
refus générique `unsupported.stroke.cap` intervenait avant la sélection de la
route native. Le refus accepte désormais ce seul contrat pixel-exact ; les
autres strokes ronds conservent le refus. Le planner et le smoke test natif le
couvrent explicitement.

La génération et la vérification des 110 scènes du catalogue complet ont
passé. La promotion est incrémentale : seule la scène W47 est ajoutée au
catalogue promu afin d’éviter un rebaseline massif des preuves historiques.

Commandes exécutées :

```text
./gradlew --offline :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest
./gradlew --offline :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest
./gradlew --offline :integration-tests:gpu-evidence:generateGpuEvidence -Pall=true -PsourceCommit=736881643d9dd318bfe9a964378a68f137693fab
./gradlew --offline :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pall=true -PsourceCommit=736881643d9dd318bfe9a964378a68f137693fab
./gradlew --offline :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=radial-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=736881643d9dd318bfe9a964378a68f137693fab
./gradlew --offline :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=radial-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=736881643d9dd318bfe9a964378a68f137693fab
./gradlew --offline :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=radial-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=736881643d9dd318bfe9a964378a68f137693fab -PpromotionReviewer=codex -PpromotionReason='W47 proves bounded two-stop CLAMP radial gradient rectangle stroke rendering under positive integral uniform scale and translation; native round-cap admission regression is covered.'
./gradlew --offline :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
