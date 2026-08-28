# W42 — stroke rectangle `LinearGradient` à deux stops avec translation entière

W42 prouve le rendu GPU natif d’un `DrawRect` public Kanvas en `STROKE`,
avec un `LinearGradient` sRGB `CLAMP` à deux stops et une translation entière
de `(2, 3)`. Le `Surface` conserve le mode non-AA et le stroke est abaissé en
quatre bandes analytiques device ; l’axe du gradient est rebasé de
`(8.5, 32.5) → (55.5, 32.5)` vers `(10.5, 35.5) → (57.5, 35.5)`.

L’oracle CPU est indépendant du chemin GPU. Il construit les quatre bandes
device translatées, translate séparément l’axe de gradient, évalue chaque
centre de pixel, interpole les deux stops après décodage sRGB en linéaire
prémultiplié, puis stocke le résultat en RGBA8 sRGB.

La preuve `linear-gradient-two-stop-translated-stroke-rect`, générée depuis
le commit de scène et de contrat
`fc23602159703f6f400c6949d8a83375075c0609`, est promue. Elle contient une
soumission GPU, quatre draws, un bind de pipeline, aucun diagnostic et une
comparaison pixel-validée à 100 % : 0 pixel divergent, delta maximal 0,
delta moyen 0, avec une tolérance de 1 LSB. Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/linear-gradient-two-stop-translated-stroke-rect/`.

La portée reste bornée : uniquement deux stops `CLAMP`, non-AA, translation
entière non nulle, cible Surface sRGB et absence de `localMatrix`. Les
translations fractionnaires et la capability dédiée absente sont refusées
avant l’émission des bandes ; les routes historiques identité et trois stops
restent inchangées.

Commandes exécutées :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-two-stop-translated-stroke-rect -PsourceCommit=fc23602159703f6f400c6949d8a83375075c0609
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-two-stop-translated-stroke-rect -PsourceCommit=fc23602159703f6f400c6949d8a83375075c0609
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=linear-gradient-two-stop-translated-stroke-rect -PsourceCommit=fc23602159703f6f400c6949d8a83375075c0609 -PpromotionReviewer=codex -PpromotionReason='W42 proves bounded two-stop clamp LinearGradient rectangle stroke rendering under an integral public Surface translation.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
