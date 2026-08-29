# W40 — RadialGradient trois stops sur stroke rectangle

La Surface publique Kanvas rend un `DrawRect(STROKE)` non-AA avec un
`RadialGradient` à trois stops, `CLAMP`, CTM et matrice locale identités, sur
`rgba8unorm-srgb`. La commande est abaissée en quatre bandes analytiques et
utilise la capability dédiée
`first_slice.stroke_rect.radial_gradient_three_stop.native`.

L'oracle CPU W40 est indépendant : il construit l'union de quatre bandes
device, mesure la distance radiale aux pixel centers, sélectionne le segment
parmi les trois stops, décode les couleurs sRGB, interpole en linéaire
prémultiplié puis stocke le résultat en sRGB RGBA8. Il ne délègue ni au
chemin GPU ni à l'oracle radial deux stops.

La preuve `radial-gradient-three-stop-stroke-rect`, générée pour le commit de
contrat `83a990e1ad80734ea6fac531f79e9fe13707aebd`, est promue : rendu GPU
réel, une soumission, quatre draws, diagnostics vides et comparaison
pixel-validée à 100 % (0 pixel divergent, delta maximal 0, tolérance 1 LSB).
Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/radial-gradient-three-stop-stroke-rect/`.

Les limites restent explicites : quatre stops ou plus sont refusés par W40 ;
deux stops restent supportés par la route W38 dédiée. Un tile mode autre que
`CLAMP`, une cible non-sRGB, l'anti-aliasing, une
transformation, une matrice locale ou un color filter refusent avant la
production des packets.

Vérification :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=radial-gradient-three-stop-stroke-rect -PsourceCommit=83a990e1ad80734ea6fac531f79e9fe13707aebd
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=radial-gradient-three-stop-stroke-rect -PsourceCommit=83a990e1ad80734ea6fac531f79e9fe13707aebd
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=radial-gradient-three-stop-stroke-rect -PsourceCommit=83a990e1ad80734ea6fac531f79e9fe13707aebd -PpromotionReviewer=codex -PpromotionReason='W40 proves bounded three-stop clamp radial rectangle stroke rendering through the public sRGB Surface route.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
