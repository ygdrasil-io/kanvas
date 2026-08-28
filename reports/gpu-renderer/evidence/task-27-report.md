# W41 — stroke rectangle SweepGradient à trois stops

W41 prouve le rendu GPU natif d’un `DrawRect` public Kanvas en `STROKE` :
quatre bandes analytiques, `SweepGradient` sRGB `CLAMP` à trois stops,
anti-aliasing désactivé, CTM et `localMatrix` identités, cible Surface
`rgba8unorm-srgb`.

L’oracle CPU est indépendant du chemin GPU. Il calcule l’union exacte des
quatre bandes device, échantillonne l’angle au centre de chaque pixel,
interpole les trois stops en linéaire prémultiplié après décodage sRGB, puis
stocke le résultat en RGBA8 sRGB.

La preuve `sweep-gradient-three-stop-stroke-rect`, générée depuis le commit
de contrat `1c98bacce83e3ea4e02fb1be711b627b827cecb7`, est promue : une
soumission GPU, quatre draws, un bind de pipeline, diagnostics vides et
comparaison pixel-validée à 100 % (0 pixel divergent, delta maximal 0,
tolérance 1 LSB). Les artefacts sont sous
`reports/gpu-renderer/evidence/correctness/promoted/sweep-gradient-three-stop-stroke-rect/`.

Les limites sont explicites : W41 refuse quatre stops ou plus, les positions
malformées, tout tile mode hors `CLAMP`, les angles partiels, les cibles non
sRGB, l’anti-aliasing, les transformations, une `localMatrix` non identité et
les color filters. Deux stops relèvent de la route W39 distincte.

Commandes exécutées :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-three-stop-stroke-rect -PsourceCommit=1c98bacce83e3ea4e02fb1be711b627b827cecb7
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-three-stop-stroke-rect -PsourceCommit=1c98bacce83e3ea4e02fb1be711b627b827cecb7
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-three-stop-stroke-rect -PsourceCommit=1c98bacce83e3ea4e02fb1be711b627b827cecb7 -PpromotionReviewer=codex -PpromotionReason='W41 proves bounded three-stop clamp sweep rectangle stroke rendering through the public sRGB Surface route.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
