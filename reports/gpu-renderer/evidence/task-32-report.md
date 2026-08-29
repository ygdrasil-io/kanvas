# W46 — `SweepGradient` à deux stops sous scale uniforme

W46 promeut un `DrawRect(STROKE)` public Kanvas avec `SweepGradient` sRGB
`CLAMP` à deux stops, angle complet `0→360`, scale uniforme entière positive
de `2` et translation entière `(2,4)`. Le centre est rebasé en device de
`(18,14)` vers `(38,32)`; les quatre bandes device ont une largeur de stroke
de `4` pixels.

L’oracle CPU indépendant couvre les quatre bandes et calcule l’angle au centre
de chaque pixel par `atan2` en coordonnées device, avec interpolation sRGB
prémultipliée. Il couvre donc explicitement la couture 0/360 et les quadrants.

La preuve `sweep-gradient-two-stop-uniform-scaled-stroke-rect`, générée depuis
`b373f83d17ceadea95a01afac5a6abbafc30119e`, est promue. Elle est
pixel-validée à 100 %, sans pixels divergents (tolérance 1 LSB). La provenance
planner est `AnalyticStrokeRectUniformScaleSweepTwoStopBand`, liée à la route
`native.stroke_rect.sweep_gradient_two_stop_uniform_scale` et à sa capability
dédiée. Les transforms non uniformes, AA, target non sRGB, tile non-CLAMP,
angles partiels, stops différents de `[0,1]`, local matrix, color filter ou
capability absente refusent terminalement.

Commandes :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=b373f83d17ceadea95a01afac5a6abbafc30119e
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=b373f83d17ceadea95a01afac5a6abbafc30119e
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-two-stop-uniform-scaled-stroke-rect -PsourceCommit=b373f83d17ceadea95a01afac5a6abbafc30119e
```
