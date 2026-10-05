# Rapport de correction whole-branch W6b — round 4

Base de correction : `b37bf29`.

Vague strictement bornée à R23. Aucun renderer, GM, dashboard, render,
baseline, score, `jpg-color-cube` ou suite Skia globale n'a été touché ni
exécuté ; aucun push ni PR n'a été créé.

## Cause et correction

La précédente autorité `PictureTerminalScissorAuthorityV1` était obtenue à
partir du terminal ou de `PictureCompositeOperandsV1` qu'elle devait
authentifier. Un défaut du producteur pouvait donc être recopié dans
l'autorité, le terminal et `FilterComposite`, puis accepté par la validation.

`admitPictureTerminalScissor` calcule maintenant le fait immutable depuis les
inputs d'admission eux-mêmes : clip différé, domaine composite target-local
admis et décision terminale vide/non-vide. Il est appelé au début de
`freezePictureTerminal`, avant le snapshot destination, les operands et tout
pass terminal. `PictureTerminalAdmissionV1` transmet ce fait aux operands ;
`FilterComposite` reçoit son scissor depuis la même autorité, en préservant un
`null` lorsqu'une autorité présente représente un terminal vide. Les chemins
`terminal?.toTerminalScissorAuthority()` et
`operands.toTerminalScissorAuthority()` ont été supprimés.

## RED → GREEN causal

| Contrat | Résultat |
| --- | --- |
| Baseline producteur historique | Les 100 contrats précédents pouvaient passer alors que l'autorité était auto-référentielle. |
| Baseline non-vide | Admis. |
| `contained-wrong` | Refusé. |
| `forged-null` | Refusé. |
| `true-empty-null` | Admis. |
| Producteur réel | Une Picture filtrée est compilée par le vrai `W6aLayerPlanCompiler`; son aggregate, terminal et `FilterComposite` portent le même fait 0,0,4,4. |
| Mutation du helper | Forcer temporairement le producteur à `null,true,true` fait échouer ce contrat de production ; la source correcte a ensuite été restaurée. |

## Gates frais

1. `rtk ./gradlew :gpu-plan:test --tests 'org.graphiks.kanvas.gpu.plan.RenderGraphContractTest' --rerun-tasks`
   — exit 0, XML **102/102**, sans failure/error/skip.
2. `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6b*' --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest' --rerun-tasks`
   — XML **80/80**, sans failure/error/skip : Picture 12, admission/recovery
   27, image blur 10, mask blur 10, mask shader/table 13, DropShadow 6,
   budget/recovery 2. Après les assertions, `Gradle Test Executor 21` sort
   133 : statut natif **UNKNOWN**, jamais GREEN.

Les warnings Kotlin/Gradle observés pendant les compilations sont existants
hors de ce scope ; ils ne sont pas des failures de ces XML.

## Fichiers modifiés

- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PictureStreamAggregateV1.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- `refactor/README.md`, `refactor/waves/W06-layers-effects/status.md` et le ledger.

Les exclusions W6b et la sémantique renderer restent inchangées.
