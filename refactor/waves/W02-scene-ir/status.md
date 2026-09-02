# W02 — Scene IR et frontières de modules

Date de génération : 2026-09-02  
Commit d'implémentation : `88e49aaf8` (`refactor: close W02 scene IR boundaries`)

## État des gates

| Gate | État | Preuve |
| --- | --- | --- |
| W00 baseline opérationnelle | Publiée | Inventaire `gpu-gm-inventory-v3` strict de 631 GMs. |
| W00 gate stricte | **NON ATTEINTE** | `jpg-color-cube` est l’unique quarantaine nominale `quarantined-resource-limit`. Son `mustAttempt=false` la filtre avant `Surface`, setup, draw et render ; aucune ressource lourde de rendu n’est engagée. |
| W1 gate stricte | **NON ATTEINTE** | La validation fraîche de `:kanvas:test` confirme la baseline globale de 51 échecs sur 3 573 tests. |
| W2 capture Scene IR stricte | **NON ATTEINTE** | 443 GMs éligibles : 431 capturées, 11 bloquées au setup (5 readbacks et 6 stubs), 1 capture invalide pour flottants non finis. |

Les 12 dettes de capture ne sont ni masquées ni reclassifiées. Il n’existe aucune
nouvelle exclusion W02. La capture ne constitue pas une preuve de rendu pixel
par la nouvelle architecture.

## Frontières finales

- `:render-ir` porte la Scene IR backend-neutral et reste indépendant de
  `:gpu-renderer`.
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

## Périmètre GM et quarantaine

Le document W00 conserve ses comptes historiques. L’inventaire régénéré après
Task13 est la vérité fraîche : il recense 631 GMs, dont 450 `eligible`, 126
`excluded-font`, 54 `excluded-codec` et 1 `quarantined-resource-limit`. Ses
routes et ses scores peuvent donc différer des artefacts précédents. Le score
audit reste strict avec zéro score orphelin et aucune exclusion n’a été ajoutée.

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
| `rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory` | Succès en 1 min 26 s ; vérité fraîche post-Task13, score audit strict et `jpg-color-cube` filtrée avant `Surface`/setup/draw/render. |
| `rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmSceneCaptureTest*'` | Succès : 443 GMs éligibles, 431 captures, 11 blocages au setup et une capture invalide ; `jpg-color-cube` reste filtrée avant `Surface`/setup/draw/render. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | Échec connu : 51 échecs sur 3 573 tests, 0 erreur. Ce compte frais confirme la baseline globale et maintient W1 strict non atteinte. |

Audit exact de l'inventaire frais :

```sh
rtk jq -e '.scoreAudit.strict == true and ([.rows[] | select((.conformanceScope == "eligible" or .conformanceScope == "accepted-skia-gap") and .setupState == "NOT_ATTEMPTED")] | length) == 0' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
```
