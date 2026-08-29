# W43 — `LinearGradient` à trois stops sur rectangle stroké translaté

W43 prouve un rendu GPU natif pour un `DrawRect` public Kanvas en `STROKE`,
avec un `LinearGradient` sRGB `CLAMP` à trois stops et une translation entière
non nulle de `(2, 3)`. Le `Surface` conserve le mode non-AA ; le stroke est
abaissé en quatre bandes analytiques device. L’axe du gradient est rebasé de
`(8.5, 32.5) → (55.5, 32.5)` vers `(10.5, 35.5) → (57.5, 35.5)` sans modifier
les couleurs ni les positions de stops `0`, `0.5`, `1`.

L’oracle CPU indépendant décrit les quatre bandes device translatées et
échantillonne, au centre de chaque pixel, un gradient à trois stops après
décodage sRGB, interpolation linéaire prémultipliée puis stockage RGBA8 sRGB.
Il ne réutilise ni les paquets GPU ni le shader WGSL.

La preuve `linear-gradient-three-stop-translated-stroke-rect`, générée depuis
le commit de scène et de catalogue
`8ce7b58a08bcebe93275a77203d52161beab248c`, est promue. Elle contient une
soumission GPU, quatre draws, un bind de pipeline, aucun diagnostic et une
comparaison pixel-validée à 100 % : 0 pixel divergent, delta maximal 0,
delta moyen 0, avec une tolérance de 1 LSB. Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/linear-gradient-three-stop-translated-stroke-rect/`.

La portée est volontairement bornée : trois stops `CLAMP`, target Surface sRGB,
non-AA, `localMatrix` identité et translation CTM entière non nulle. Les
translations fractionnaires, scale/rotation, cibles non sRGB et capability
dédiée absente refusent avant l’émission des bandes. La provenance typée et la
capability W43 restent distinctes de la route deux-stops translatée : aucune
dégradation silencieuse vers une autre route n’est admise.

Commandes exécutées :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.product.ProductFlagConfigTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeCapabilitiesTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-three-stop-translated-stroke-rect -PsourceCommit=8ce7b58a08bcebe93275a77203d52161beab248c
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-three-stop-translated-stroke-rect -PsourceCommit=8ce7b58a08bcebe93275a77203d52161beab248c
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=linear-gradient-three-stop-translated-stroke-rect -PsourceCommit=8ce7b58a08bcebe93275a77203d52161beab248c -PpromotionReviewer=codex -PpromotionReason='W43 proves bounded three-stop clamp LinearGradient rectangle stroke rendering under an integral public Surface translation.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
