# W73 — Skia GM dashboard audit (2026-08-28)

## Résultat

Audit exécuté sur `40119b2a1c638ce40a2286a709a2ca5d4d976231` dans le worktree
partagé. Aucune modification de code ni de `gpu-renderer-scenes` n’a été
nécessaire. Le dashboard a été régénéré; le test Skia complet reste non validant
à cause des refus/échecs du runtime et s’est terminé avec le code JVM 133.

## Commandes et résultats

```text
./gradlew :integration-tests:skia:generateSkiaDashboard
BUILD SUCCESSFUL (4m56s)
Done: 143 rendered, 426 failed
Dashboard: integration-tests/skia/build/reports/skia-gm-dashboard
Dashboard summary: Total 608, Pass 533, Fail 7, No score 29, Avg sim 54.9%

./gradlew :integration-tests:skia:test
BUILD FAILED (2m32s)
722 tests completed, 454 failed, 40 skipped
Process 'Gradle Test Executor 35' finished with non-zero exit value 133
```

Le premier total (608) est le catalogue de génération; `data/gms.json` contient
569 entrées scorables/présentes dans le dashboard. Ses comptes exacts sont
533 pass, 7 fail et 29 no-score. Les 29 no-score se répartissent en 15
références absentes, 11 size mismatches et 3 références untrustable. Les 7
échecs sous seuil sont : `emboss`, `inverseclip`, `picture_mesh`,
`simpleshapes_bw`, `widebuttcaps`, `gradtext`, `text_scale_skew`.

La génération a produit 143 renders et 426 refus terminaux, principalement avec
les diagnostics stables `geometry.path.fan_budget_exceeded`,
`unsupported.stroke.width_invalid`, `unsupported.material.source_unimplemented`,
`unsupported.image.native_binding`, `unsupported.composite.paint` et
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted`. Aucun
seuil n’a été modifié et aucun rebaseline implicite n’a été effectué.

## Artefacts et changements

Artefacts générés/vérifiés :

- `integration-tests/skia/build/reports/skia-gm-dashboard/index.html`
- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`
- PNGs dans `integration-tests/skia/src/test/resources/generated-renders/`
- `integration-tests/skia/test-similarity-scores.properties`

Le worktree contient 30 PNG modifiés et 1 fichier de scores modifié (16 lignes
remplacées, sans seuil touché), plus ce rapport. SHA-256 des artefacts :

```text
6f9f71a9dc166f56dcab410a998acd353eeda5e97ec23e6bdf165b42f8f12393  .../data/gms.json
ad6cd7a3cb79206b400753ca7c0dcddace3a4bd5c910e04eeba4baafc12248e5  .../index.html
760528bc64768aee5e2c83abe1f7f572155802b471391b2eea36a1a20e835caf  .../test-similarity-scores.properties
```

## Limites

Le test global ne fournit pas une preuve de convergence native : les échecs
incluent `GPUPreparedSurfaceTerminalException`, des assertions de route et des
erreurs de taille/référence. Les 7 échecs de similarité et les refus de
lowering sont conservés comme diagnostics; ils ne sont ni masqués ni promus.

## Diagnostic du code 133

Le code 133 n'est pas reproduit par un GM isolé :

```text
./gradlew --offline --no-daemon :integration-tests:skia:test \
  --tests 'org.graphiks.kanvas.skia.GradientColorFilterGpuSmokeTest' \
  --max-workers=1
1 test completed, 1 failed
GPUPreparedSurfaceTerminalException: unsupported.material.mapping.linear_gradient_stop_count
```

Une exécution série de `SkiaGmRunner` avec
`--tests org.graphiks.kanvas.skia.SkiaGmRunner --max-workers=1 --stacktrace` a parcouru
les 608 paramètres (442 refus/échecs, 40 skips), puis Gradle a échoué pendant
la conversion du journal de sortie en XML : `TestOutputStore` a rencontré
`java.io.EOFException` / `com.esotericsoftware.kryo.KryoException: Buffer
underflow`. La suite complète reproduit aussi le code 133 avec
`--max-workers=1`, lors de l'arrêt du worker (`RestartEveryNTestClassProcessor.stop`),
sans produire de `hs_err_pid*.log` ni de signal natif explicite. La cause native
ou lifecycle exacte n'est donc pas prouvée. Ce finding du harness est distinct
des refus de lowering et ne justifie pas une modification du renderer dans cette
vague ; il reste à traiter séparément.
