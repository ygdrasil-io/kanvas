# Rapport de correction whole-branch W6b — round 3

Base de correction : `6453b06`.

Vague strictement bornée à R22/I1, à l'élimination des imports Render IR du
shard public admission (I7), et à l'exactitude des claims. Les fermetures
I2–I6, M1–M2 et R21 sont préservées; aucun renderer, GM, dashboard, render,
baseline, score, `jpg-color-cube` ou suite Skia globale n'a été touché ni
exécuté.

## RED → GREEN

| Finding | RED observé | GREEN livré | Preuve |
| --- | --- | --- | --- |
| R22 / I1 | Une fixture publication complète `FilterCompositeOperationV1.Picture` était valide avec un scissor terminal `null` réellement vide; les mutants séparés `contained-wrong` et `forged-null` étaient acceptés, donc leurs `assertFails` échouaient. | `PictureStreamAggregateV1` porte `PictureTerminalScissorAuthorityV1`, snapshot d'admission indépendant créé avant la publication du pass. La validation compare le terminal et le scissor du `FilterComposite` à cette autorité, y compris l'état vide et l'admission. | Les deux mutants refusent et `true-empty-null` est admis dans `RenderGraphContractTest`, 100/100. |
| I7 | `W6bFilterAdmissionRecoverySurfaceTest` importait et construisait `GraphLimits` et `SceneCaptureLimits` dans un cas de limite paramétrée. | Les imports et le cas redondant sont retirés. Les deux refus de taille restent des cas publics `Picture.fromByteArray` avec bytes mutés, replay et recovery dans `W6bFilterPictureTest`; aucun test infrastructure n'est déplacé ou ajouté. | Shards publics : Picture 12, admission/recovery 27, autres shards inchangés, XML 80/80. |
| M3 | Les rapports round 2 annonçaient R21 comme fermeture définitive malgré la re-review R22. | README, status, ledger et rapport round 2 distinguent son état historique; ce rapport est le statut présent. | Bases, comptes et statut native ci-dessous. |

## Fichiers modifiés

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PictureStreamAggregateV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`
- `refactor/README.md`, `refactor/waves/W06-layers-effects/status.md`, ledger et rapports.

## Gates frais

1. `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest'`
   — **exit 0**, XML **100/100**, 0 failure/error/skip.
2. `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6b*' --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'`
   — XML **80/80**, 0 failure/error/skip : Picture 12, admission/recovery 27,
   image blur 10, mask blur 10, mask shader/table 13, DropShadow 6,
   budget/recovery 2. `gpu-renderer:compileKotlin` et `kanvas:compileTestKotlin`
   ont compilé dans ce gate. Après les assertions, un `Gradle Test Executor`
   termine par 133 : statut native **UNKNOWN**, jamais GREEN.

## Concerns restants

Les exclusions W6b restent inchangées. Device-loss/visibilité native et l'exit
133 demeurent **UNKNOWN**. Ce rapport ne formule aucune claim ISO, globale ou
de readiness hors de ce périmètre.
