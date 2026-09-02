# Task 14 report

Base : `b393cdaad`

## Résultat

`RenderConfig` ne dépend plus de types ni de constantes de `:gpu-renderer` :
les limites de path sont neutres dans `surface` et la route publique
`PreparedImageRoute` remplace la capability GPU exposée auparavant. Le nom de
propriété système historique `kanvas.render.preparedImageRouteCapability` et
ses valeurs historiques restent acceptés. La seule conversion vers
`GPUPreparedImageRouteCapability` est maintenant privée à l'adapter
`surface.gpu`.

`:kanvas` expose `:render-ir` avec `api` et consomme `:gpu-renderer` avec
`implementation`. Les consommateurs qui importent volontairement une API GPU
déclarent leur dépendance directe : `integration-tests:skia`,
`integration-tests:test-utils`, `kanvas:svg`, `integration-tests:svg` (tests)
et `integration-tests:gpu-evidence`.

Les adapters des dix-sept fichiers prévus sont internalisés lorsqu'ils ne font
pas partie de l'API Canvas/Surface/Picture, des diagnostics ou des résultats
publics. `GPUPreparedAtlasLowering`, hors de cette liste, a aussi dû devenir
`internal` parce que son résultat public aurait sinon exposé
`GPUFramePathVisualCommand`, devenu interne par fermeture de frontière. Aucun
contrat public utile n'a été retiré sans nécessité démontrée par le
compilateur.

## Inventaire et gates

Le code vérifie `mustAttempt` avant toute création de `Surface`, setup, draw ou
render. `jpg-color-cube` peut encore être instanciée par le registre, mais sa
quarantaine `quarantined-resource-limit` la filtre à cette frontière sans
engager de ressource lourde de rendu. `generateSkiaGmInventory` a donc été
relancé sans l'exécuter.

L'inventaire frais post-Task13 contient 631 GMs (450 `eligible`, 126
`excluded-font`, 54 `excluded-codec`, une quarantaine). Son audit de scores est
strict et ne contient aucun `eligible`/`accepted-skia-gap` non tenté au setup.
Les routes et scores de cet artefact frais ne sont pas supposés identiques aux
artefacts antérieurs.

- W00 stricte : **NON ATTEINTE**, en raison de la seule quarantaine.
- W1 stricte : **NON ATTEINTE**, la suite complète fraîche confirme 51 échecs
  sur 3 573 tests, la baseline globale connue.
- W2 capture stricte : **NON ATTEINTE** — 443 éligibles, 431 capturées, 11
  bloquées au setup (5 readbacks, 6 stubs), une capture invalide pour flottants
  non finis.

Il n'y a aucune nouvelle exclusion W02. `Surface.snapshotScene()` est
backend-neutral ; le scope `recordingOnly` encadre le setup/draw des GMs afin
que leurs snapshots/readbacks internes n'appellent pas le renderer.
`Surface.render()` reste exclusivement sur le chemin legacy `renderViaGpu(...)`.
Cette tâche ne revendique donc aucun pixel rendu par la nouvelle architecture.

## Vérification

```text
rtk ./gradlew :kanvas:test --tests '*GPUPreparedDrawImageLowererTest*' :integration-tests:gpu-evidence:compileKotlin :integration-tests:skia:compileTestKotlin
BUILD SUCCESSFUL

rtk ./gradlew :kanvas:svg:compileKotlin :integration-tests:svg:compileTestKotlin
BUILD SUCCESSFUL

rtk ./gradlew :kanvas:test --tests '*GPUPreparedTextRefusalMatrixTest*'
BUILD SUCCESSFUL

rtk ./gradlew :kanvas:compileKotlin :kanvas:svg:compileKotlin :integration-tests:test-utils:compileKotlin :integration-tests:skia:compileKotlin :integration-tests:skia:compileTestKotlin :integration-tests:svg:compileTestKotlin :integration-tests:gpu-evidence:compileKotlin
BUILD SUCCESSFUL

rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory
BUILD SUCCESSFUL in 1 min 26 s

rtk jq -e '<audit strict sans setup NOT_ATTEMPTED>' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
true

rtk ./gradlew :kanvas:test --rerun-tasks
3 573 tests, 51 échecs, 0 erreur
```

Le retour à la baseline a nécessité d'éliminer deux collisions de fixtures
RuntimeEffect sans modifier le comportement du registre : la fixture canonique
`runtime.simple_rt` utilise désormais son descripteur compatible
`registered-only` et le cas volontairement inconnu a l'identifiant spécifique
`not.registered.vertices`. La capture des enfants de `MeshProgram` conserve
également leurs types ABI dans le descripteur restauré. Les suites ciblées
`SceneRoundTripTest` et `GPUPreparedVerticesLowererTest` sont vertes.

La matrice de refus texte a d'abord révélé un effet de bord d'internalisation :
un factory `@MethodSource` Kotlin `internal` est manglé et JUnit ne le trouve
plus. La fixture est maintenant publique au niveau JVM mais n'expose plus de
type GPU interne ; sa suite ciblée est verte. Aucun test d'inspection de
source/import/reflection/infrastructure n'a été ajouté ; le test statique de
mapping couleur touché dans ce périmètre a été retiré.
