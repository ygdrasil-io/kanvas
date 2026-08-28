# W44 — `LinearGradient` à deux stops sous scale uniforme

W44 prouve un rendu GPU natif pour un `DrawRect` public Kanvas en `STROKE`,
avec un `LinearGradient` sRGB `CLAMP` à deux stops, une échelle uniforme
positive entière de `2` et une translation entière de `(2, 4)`. La scène
compose la translation puis l’échelle afin que la CTM device reste
`scale=(2,2), translation=(2,4)`. Le rectangle est abaissé en quatre bandes
analytiques device et l’axe du gradient est rebasé de
`(8,16) → (28,16)` vers `(18,36) → (58,36)`.

L’oracle CPU indépendant décrit les quatre bandes device et calcule, au centre
de chaque pixel, l’interpolation linéaire prémultipliée après décodage sRGB,
puis le stockage RGBA8 sRGB. Il ne réutilise ni les paquets GPU ni le shader
WGSL. Ses préconditions (quatre bandes valides, axe non dégénéré, coordonnées
finies, scale positive et couleurs RGBA8) sont vérifiées explicitement.

La preuve `linear-gradient-two-stop-uniform-scaled-stroke-rect`, générée depuis
le commit source `a40f4c5face0d2784aee3111ed148ed16775164a`, est promue. Elle
contient une soumission GPU, quatre draws, un bind de pipeline, aucun diagnostic
et une comparaison pixel-validée à 100 % : 0 pixel divergent, delta maximal 0,
delta moyen 0, avec une tolérance de 1 LSB. Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/linear-gradient-two-stop-uniform-scaled-stroke-rect/`.

Le contrat reste borné : target Surface sRGB, non-AA, `localMatrix` identité,
stops exactement `[0,1]`, échelle uniforme entière positive et translation
entière. Les transforms non linéaires, stops non prouvés, targets non sRGB ou
capability absente refusent terminalement avant l’émission des bandes, sans
fallback implicite.

Commandes exécutées :

```text
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLowererTest --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.product.ProductFlagConfigTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeCapabilitiesTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=a40f4c5face0d2784aee3111ed148ed16775164a
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=a40f4c5face0d2784aee3111ed148ed16775164a
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=linear-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=a40f4c5face0d2784aee3111ed148ed16775164a -PpromotionReviewer=codex -PpromotionReason='W44 proves bounded two-stop CLAMP LinearGradient rectangle stroke rendering under positive integral uniform scale and translation.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
