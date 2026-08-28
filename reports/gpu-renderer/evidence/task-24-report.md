# W38 — RadialGradient deux stops sur stroke rectangle

La Surface publique Kanvas admet désormais un `DrawRect(STROKE)` non-AA avec
un `RadialGradient` à deux stops, `CLAMP`, CTM et matrice locale identités,
sur `rgba8unorm-srgb`. La commande est abaissée en quatre bandes analytiques
et porte l'identité `native.stroke_rect.radial_gradient_two_stop` ainsi que la
capability dédiée `first_slice.stroke_rect.radial_gradient_two_stop.native`.

Les trois stops ou plus, les tile modes autres que `CLAMP`, une cible non-sRGB,
l'anti-aliasing, une transformation, une matrice locale ou un color filter sont
refusés avant soumission. L'oracle CPU W38 est indépendant : il évalue la
distance radiale au centre aux pixel centers, interpole en linéaire prémultiplié
puis stocke en sRGB RGBA8.

La preuve `radial-gradient-two-stop-stroke-rect`, générée depuis
`97c2e66fc3abe9bfeeea5492d4b16351b4291ee8`, est promue : rendu GPU réel,
1 soumission, 4 draws, aucun refus et comparaison pixel-validée à 100 % (delta
maximal 0, tolérance 1 LSB). Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/radial-gradient-two-stop-stroke-rect/`.

Vérification :

```text
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest --tests org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbTwoStopRadialGradientStrokeCpuOracleTest
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=radial-gradient-two-stop-stroke-rect -PsourceCommit=97c2e66fc3abe9bfeeea5492d4b16351b4291ee8
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
