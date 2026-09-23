# Rapport de correction whole-branch W6b — round 2

Base de correction : `a1aabe6`.

> Historique, remplacé comme statut final par `whole-branch-fix3-report.md`.
> La re-review R22 a confirmé R21/I5, mais a trouvé que l'I1 terminal Picture
> restait auto-référentiel et que le shard admission importait encore deux
> types Render IR. Les claims I1/I7/M3 ci-dessous décrivent donc uniquement
> l'état observé à la fin du round 2, pas une fermeture finale.

Cette vague est bornée à R21 : I1 et I7 Important, la copie privée bornée I5
Minor et les claims M3. I2–I4, I6 et M1–M2 ne sont pas rouverts. Aucun chemin
W6c–W6e, GM, dashboard, render, baseline, score, `jpg-color-cube` ni suite
Skia globale n'a été modifié ni exécuté.

## RED → GREEN

| Finding | RED constaté | GREEN livré | Preuve ciblée |
| --- | --- | --- | --- |
| I1 / R21 | `FilterComposite` ne publiait pas son offset/scissor final; Draw et `filteredCompositeRender` redérivaient `source - destination` dans le renderer. Le mutant de contrat ne pouvait d'abord pas fournir ces operands. | R21 ferme la dérivation renderer-local et fige les snapshots I32. R22 a ensuite exigé l'autorité aggregate indépendante du terminal/pass ; voir le rapport round 3. | `RenderGraphContractTest` : 97/97 à ce round; la preuve R22 est dans le rapport round 3. |
| I7 | Trois assertions du shard Picture construisaient/inspectaient table, snapshot ou codec Render IR directement. | Le shard Picture a été rendu public, mais R22 a trouvé deux imports Render IR dans le shard admission ; leur retrait est documenté au round 3. | `W6bFilterPictureTest`, 12/12 XML PASS à ce round. |
| I5 | Decoder et builder avaient une seconde copie, certes bornée, de listes fraîches qu'ils possédaient déjà. | `fromOwnedNodes` valide puis prend possession de la liste privée fraîche; `SceneArchiveCodec` et le builder l'emploient. `of(Collection)` conserve sa copie défensive pour les callers publics. | Les refus/recovery Picture table et fanout oversize restent dans le shard public Picture. |
| M3 | Les rapports du round 1 déclaraient I1/I5/I7 fermés prématurément. | Status, README, ledger et rapport round 1 distinguent désormais le résultat historique de la fermeture R21; les comptes sont 97 contrat et 81 W6b. | XML et commandes ci-dessous. |

## Fichiers modifiés

- `gpu-plan`: `PlanPasses.kt`, `W6aLayerGraphConstruction.kt`,
  `W6aLayerGraphValidation.kt`, `RenderGraphContractTest.kt`.
- `gpu-renderer`: `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`.
- `render-ir`: `CapturedFilterTableV1.kt`, `SceneArchiveCodec.kt`.
- Tests publics : `W6bFilterPictureTest.kt`,
  `W6bFilterAdmissionRecoverySurfaceTest.kt`.
- Suivi : `refactor/README.md`, `refactor/waves/W06-layers-effects/status.md`,
  ledger et les deux rapports whole-branch.

## Gates frais

1. `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest' :gpu-renderer:compileKotlin :kanvas:compileTestKotlin`
   — **exit 0**, XML **97/97**, 0 failure/error/skip.
2. `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6b*' --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'`
   — XML **81/81**, 0 failure/error/skip : Picture 12, admission/recovery 28,
   image blur 10, mask blur 10, mask shader/table 13, DropShadow 6,
   budget/recovery 2. Après les assertions, `Gradle Test Executor 3` quitte
   133 : statut native **UNKNOWN**, jamais GREEN.

## Autorité et concerns restants

Aucun operand/pass W6b ne consulte `targetOriginsDeviceI32`; la recherche de
la map est vide dans les routes W6b. Les bridges `MaskShader` et `GraphTexture`
ne gardent que les mappings W5/antérieurs déjà gelés : aucun ne redérive
device→target, scissor ou offset W6b. Les exclusions W6b, le risque
device-loss/visibilité native et l'exit 133 restent inchangés et **UNKNOWN**;
ce rapport ne formule aucune claim ISO ou globale.
