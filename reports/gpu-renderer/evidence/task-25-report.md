# W39 — SweepGradient deux stops sur stroke rectangle

La Surface publique Kanvas rend un `DrawRect(STROKE)` non-AA avec un
`SweepGradient` à deux stops, `CLAMP`, angles 0–360°, CTM et matrice locale
identités, sur `rgba8unorm-srgb`. La commande est abaissée en quatre bandes
analytiques, avec la capability dédiée
`first_slice.stroke_rect.sweep_gradient_two_stop.native`.

La preuve est fondée sur un oracle CPU indépendant : il construit l'union de
quatre bandes device, échantillonne l'angle au centre de chaque pixel, décode
les couleurs sRGB, interpole en linéaire prémultiplié, puis stocke le résultat
en sRGB RGBA8. Il ne délègue ni à l'oracle de rectangle contigu ni au chemin
GPU.

La preuve `sweep-gradient-two-stop-stroke-rect`, générée depuis
`51506fe0fe82000abb8ac406baa9e1a790252570`, est promue : rendu GPU réel,
une soumission, quatre draws, aucun diagnostic de refus et comparaison
pixel-validée à 100 % (0 pixel divergent, delta maximal 0, tolérance 1 LSB).
Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/sweep-gradient-two-stop-stroke-rect/`.

Les limites restent explicites : un nombre de stops différent de deux, un tile mode autre que
`CLAMP`, des angles partiels, une cible non-sRGB, l'anti-aliasing, une
transformation, une matrice locale ou un color filter refusent avant la
production des packets.

Vérification :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-two-stop-stroke-rect -PsourceCommit=51506fe0fe82000abb8ac406baa9e1a790252570
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-two-stop-stroke-rect -PsourceCommit=51506fe0fe82000abb8ac406baa9e1a790252570
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-two-stop-stroke-rect -PsourceCommit=51506fe0fe82000abb8ac406baa9e1a790252570 -PpromotionReviewer=codex -PpromotionReason='W39 proves bounded two-stop clamp sweep rectangle stroke rendering through the public sRGB Surface route.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
