# W02 — Scene IR et frontières de modules

Date de génération : 2026-09-02  
Commit de vérification : `b575b5b74`

## État des gates

| Gate | État | Preuve |
| --- | --- | --- |
| W00 baseline opérationnelle | Publiée | Inventaire `gpu-gm-inventory-v3` strict de 631 GMs. |
| W00 gate stricte | **NON ATTEINTE** | `jpg-color-cube` est l’unique quarantaine nominale `quarantined-resource-limit`. Son `mustAttempt=false` la filtre avant `Surface`, setup, draw et render ; aucune ressource lourde de rendu n’est engagée. |
| W1 gate stricte | **NON ATTEINTE** | La validation fraîche de `:kanvas:test --rerun-tasks` confirme la baseline globale de 51 échecs sur 3 585 tests, 0 erreur ; aucun nouveau test W1/W2 n'échoue. |
| W2 capture Scene IR stricte | **NON ATTEINTE** | 443 GMs éligibles : 431 capturées, 11 bloquées au setup (5 readbacks et 6 stubs), 1 capture invalide pour flottants non finis. |

Les 12 dettes de capture ne sont ni masquées ni reclassifiées. Il n’existe aucune
nouvelle exclusion W02. La capture ne constitue pas une preuve de rendu pixel
par la nouvelle architecture.

## Frontières finales

- `:render-ir` porte la Scene IR backend-neutral et reste indépendant de
  `:gpu-renderer`.
- `:render-ir` publie `RenderPathFanLimits`, l’unique autorité backend-neutral
  pour la capacité des edge fans. `RenderConfig` et
  `GPUPathEdgeFanPayloadContract` y aliasent leurs limites ; `:gpu-renderer`
  la consomme par `implementation`.
- `:kanvas` expose `:render-ir` par `api` et consomme `:gpu-renderer` par
  `implementation`.
- Dans le périmètre W02, `RenderConfig` expose les limites backend-neutral
  `MAX_PATH_FAN_TRIANGLES` et `MAX_PATH_GEOMETRY_BYTES`, ainsi que les routes
  publiques `PreparedImageRoute.GENERIC_NATIVE` et
  `PreparedImageRoute.BOUNDED_NEAREST_1_TO_1`. L’adaptation vers
  `GPUPreparedImageRouteCapability` vit uniquement dans `surface.gpu`.
  Le type public historique `GPUColorFormat` reste inchangé pour compatibilité.
- Les adapters de préparation GPU sont `internal`; les consommateurs qui
  importent volontairement `GPUBackendRuntimeFactory` déclarent directement
  `:gpu-renderer` (`integration-tests:skia`, `integration-tests:test-utils`,
  `kanvas:svg`, `integration-tests:svg` au scope test, et
  `integration-tests:gpu-evidence`).
- Les valeurs géométriques restent dans `:math` avec la nomenclature
  `I32`/`I64`/`F32`/`F64`; W02 ne les déplace pas vers `:kanvas`.

## Identité et graphes

- Les identifiants canoniques de la Scene IR sont des SHA-256 hexadécimaux de
  64 caractères ; le domaine et le framing sont calculés exactement sur les
  unités UTF-16 contractuelles.
- La construction d'un DAG (directed acyclic graph, graphe orienté acyclique)
  partagé de profondeur 1 024 conserve une identité de taille fixe, sans
  croissance mémoire exponentielle. Son admission reste soumise aux limites de
  graphe : les dépassements sont refusés par un diagnostic typé piloté par
  `SceneCaptureLimits`, sans plafond local dans la phase d'enregistrement.

## Périmètre GM et quarantaine

Le document W00 conserve ses comptes historiques. L’inventaire régénéré le
2026-09-02 est la vérité fraîche : il recense 631 GMs, dont 450 `eligible`, 126
`excluded-font`, 54 `excluded-codec` et 1 `quarantined-resource-limit`. Ses
routes et ses scores peuvent donc différer des artefacts précédents. Le score
audit reste strict avec zéro score orphelin et aucune exclusion n’a été ajoutée.
La génération a abouti en 2 min 10 s. `jpg-color-cube` a
`attempted=false` et `setupState=NOT_ATTEMPTED` ; elle est filtrée avant
`Surface`/setup/draw/render par la quarantaine de limite de ressource.

Les 450 GMs `eligible` de l’inventaire W0 sont un compte post-observation de
la route d’inventaire. La capture Scene IR exécute son propre setup/draw dans
le scope `recordingOnly` et observe ensuite une sortie de font pour sept GMs
encore W0-éligibles : `convex_poly_clip`, `drawbitmaprect`,
`drawbitmaprect-subset`, `drawbitmaprect-imagerect`,
`drawbitmaprect-imagerect-subset`, `scaled_tilemode_bitmap` et `tilemodes`.
Ils sont donc reclassés `excluded-font` après draw : 450 → 443. Cette
reclassification est une observation de dépendance, pas une modification de
scope, d’allowlist, ni du périmètre `font`/`codec`.

Avant toute création de `Surface`, `captureInventoryEvidence` évalue la
décision de conformance ; une décision dont `mustAttempt=false` retourne
immédiatement son evidence d’exclusion. Cette propriété permet donc de lancer
`generateSkiaGmInventory` sans exécuter `jpg-color-cube`. Le test
comportemental existant confirme que la quarantaine est observée avant la
création de surface.

## Surface et rendu

`Surface.snapshotScene()` enregistre une Scene IR sans soumission backend. Le
scope `recordingOnly` est employé autour du setup/draw des GMs pour empêcher
que leurs snapshots ou readbacks internes n’appellent `Surface.render()` ; il
n’est pas une précondition de `snapshotScene()`. Le rendu public n’est pas migré :
`Surface.render()` appelle encore exclusivement `renderViaGpu(...)`, le
renderer legacy. Aucun rendu, score ou dashboard ne revendique des pixels
produits par la Scene IR.

## Vérifications

| Commande | Résultat |
| --- | --- |
| `rtk ./gradlew :kanvas:test --tests '*GPUPreparedDrawImageLowererTest*' :integration-tests:gpu-evidence:compileKotlin :integration-tests:skia:compileTestKotlin` | Succès : la route publique bornée conserve le refus du filtrage linéaire et les consommateurs directs compilent. |
| `rtk ./gradlew :kanvas:test --tests '*GPUFramePathApiInventoryTest*'` | Succès : les limites publiques backend-neutral déterminent les defaults et les refus de budget. |
| `rtk ./gradlew :kanvas:svg:compileKotlin :integration-tests:svg:compileTestKotlin` | Succès : les consommateurs SVG déclarent leur dépendance GPU explicite. |
| `rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory` | Succès en 2 min 10 s ; 631 GMs enregistrées, 450 éligibles, 54 `excluded-codec`, 126 `excluded-font`, 1 quarantaine ; `scoreAudit.strict=true`, `orphanCount=0`, et `jpg-color-cube` filtrée avant `Surface`/setup/draw/render. |
| `rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmInventoryTest*' --tests '*SkiaGmConformanceTest*' --tests '*SkiaGmSceneCaptureTest*'` | Succès : les sept GMs reclassés après draw font passer les 450 éligibles W0 à 443 ; 431 captures, 11 blocages au setup et une capture invalide. `jpg-color-cube` reste filtrée avant `Surface`/setup/draw/render. |
| Tests ciblés Picture/RuntimeEffect/RecordedGeometry/DisplayOpSceneAdapter/SceneRoundTrip/SceneRecordingScope/SurfaceSceneSnapshot/GPUFramePathApiInventory | Succès. Les snapshots profonds, graphes cycliques, IDs canoniques et frontières de modules sont couverts. |
| Compilation des consumers (`:kanvas`, `:kanvas:svg`, `integration-tests:test-utils`, `integration-tests:skia`, `integration-tests:svg`, `integration-tests:gpu-evidence`) | Succès. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | Échec connu : 51 échecs sur 3 585 tests, 0 erreur. Ces échecs sont exactement les six classes de baseline GPU/Image connues ; aucun nouveau test W1/W2 ne tombe. Ce compte frais maintient W1 strict non atteinte. |

Audit exact de l'inventaire frais :

```sh
rtk jq -e '.scoreAudit.strict == true and ([.rows[] | select((.conformanceScope == "eligible" or .conformanceScope == "accepted-skia-gap") and .setupState == "NOT_ATTEMPTED")] | length) == 0' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
```
