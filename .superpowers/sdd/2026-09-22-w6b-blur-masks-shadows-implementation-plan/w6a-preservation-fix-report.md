# W6a preservation — correctif de clôture

Base diagnostiquée : `5958fe676`.

## RED et cause racine

Le témoin public existant
`W6aLayerW4W5SurfacePixelTest` échouait de manière reproductible sur 4/19
méthodes avec `w6a.layer.invalid_plan: Missing sealed W4e material origin`.
Les quatre échecs couvrent les chemins W4c/W4d translated avec un clip/path
stencil ; les 15 autres méthodes passaient déjà.

`0e7b91dfc` a supprimé la map renderer `targetOriginsDeviceI32` et impose
désormais une origin W5 scellée. `appendRender` la publie sur les nouveaux
`RenderPass`, mais la relocalisation finale de `W6aLayerGraphConstruction`
reconstruisait les `RenderPass` W4 importés à partir de
`copyMaterialDeviceOriginI32()`, qui vaut `null` pour les passes historiques.

L'hypothèse initiale — fallback plan-side vers l'origin du target pour chaque
`RenderPass` relocalisé — était nécessaire mais insuffisante. Une
instrumentation temporaire, retirée avant ce commit, a montré que les quatre
échecs venaient du `StencilCover` W4e (`StencilCover:3`, `:6` et `:8`), et non
d'un `RenderPass`. Le binding W4e associé ne transportait plus l'origin que
l'ancien renderer dérivait depuis sa map.

## Correction

- La publication W6a scelle `pass.copyMaterialDeviceOriginI32() ?:
  targetOriginDevice(pass.target)` sur chaque `RenderPass` relocalisé.
- `PlanW4eGeometryBindingV1` snapshotte désormais l'origin device de son
  target, la conserve pendant `bindSources`, et l'expose défensivement.
- Le renderer W4e utilise ce fait publié par le plan pour tous ses paths,
  y compris `StencilCover`; il ne reconstruit aucune map ni origin de target.

Le test pixel public était déjà le contrat de régression adapté : il a été
observé rouge avant la correction puis vert après. Aucun test d'infrastructure
ou test statique de source n'a été ajouté. Les compteurs documentés restent
inchangés, donc le ledger ne nécessite pas de correction.

## Vérification fraîche

| Gate | Résultat JUnit | Processus |
| --- | --- | --- |
| `W6aLayerBudgetRecoverySurfacePixelTest` + `W6aLayerW4W5SurfacePixelTest` + `W6aNestedLayerSurfacePixelTest` | 39/39 PASS | exit 133 post-JUnit, `UNKNOWN` |
| Sept shards publics W6b | 80/80 PASS | exit 133 post-JUnit, `UNKNOWN` |
| `:gpu-plan:test --tests RenderGraphContractTest` | 102/102 PASS | exit 0 |

`git diff --check` est propre. L'audit de `gpu-renderer` ne trouve aucune
réintroduction de `targetOriginsDeviceI32` ni de `targetOriginDeviceI32(...)`;
les seuls consommateurs d'origin sont les snapshots explicitement publiés par
`RenderPass` ou `PlanW4eGeometryBindingV1`.

Les exits 133 sont le comportement post-JUnit déjà connu ; ce correctif ne les
classe pas comme succès de l'exécution native.
