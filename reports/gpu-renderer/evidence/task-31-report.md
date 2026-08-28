# W45 — `LinearGradient` à trois stops sous scale uniforme

W45 prouve un rendu GPU natif pour un `DrawRect` public Kanvas en `STROKE`,
avec un `LinearGradient` sRGB `CLAMP` à trois stops `[0, 0.5, 1]`, une échelle
uniforme positive entière de `2` et une translation entière de `(2, 4)`.
La CTM device est `scale=(2,2), translation=(2,4)`. Le rectangle est abaissé
en quatre bandes analytiques device et l’axe du gradient est rebasé de
`(8,16) → (28,16)` vers `(18,36) → (58,36)`.

L’oracle CPU indépendant décrit ces quatre bandes device et calcule, au centre
de chaque pixel, l’interpolation des trois stops prémultipliés après décodage
sRGB, puis le stockage RGBA8 sRGB. Il ne réutilise ni le materializer GPU ni
le shader WGSL. Ses tests directs couvrent les deux extrémités, le stop central,
l’intérieur, l’extérieur, l’alpha et l’axe dégénéré.

La preuve `linear-gradient-three-stop-uniform-scaled-stroke-rect`, générée
depuis le commit source `a4fdc12919d518b4f64fc479620c9d89b455fb34`, est
promue. Elle contient une soumission GPU, quatre draws et un bind de pipeline,
sans diagnostic ni ressource intermédiaire. La comparaison est pixel-validée à
100 % : 0 pixel divergent, delta maximal 0 et delta moyen 0, avec une tolérance
de 1 LSB. Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/linear-gradient-three-stop-uniform-scaled-stroke-rect/`.

Le test de planification lie explicitement la provenance
`AnalyticStrokeRectUniformScaleThreeStopBand` à la route
`native.stroke_rect.linear_gradient_three_stop_uniform_scale` et à sa
capability dédiée. Les sources `Generic` ou `PublicFillRect` restent sur leurs
routes génériques. Le contrat refuse avant émission des bandes : capability
absente, target non sRGB, AA, `localMatrix` non identité, transform non admise,
filtre de couleur, nombre de stops autre que trois, positions non finies,
hors `[0,1]`, non strictement croissantes ou sans extrémités `[0,1]`, et tile
mode autre que `CLAMP`. Il n’y a pas de fallback implicite.

Lors de la première génération, l’oracle a révélé que les bandes trois stops
étaient encore étiquetées comme une translation et conservaient une largeur de
stroke non mise à l’échelle. Le correctif source cité ci-dessus rebases les
quatre bandes en device et porte la largeur de `2` à `4`; la preuve promue est
donc produite après correction, et non une acceptation du rendu initial.

Commandes exécutées :

```text
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedStrokeRectLowererTest
./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=a4fdc12919d518b4f64fc479620c9d89b455fb34
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=a4fdc12919d518b4f64fc479620c9d89b455fb34
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=linear-gradient-three-stop-uniform-scaled-stroke-rect -PsourceCommit=a4fdc12919d518b4f64fc479620c9d89b455fb34 -PpromotionReviewer=codex -PpromotionReason='W45 proves bounded three-stop CLAMP LinearGradient rectangle stroke rendering under positive integral uniform scale and translation.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
