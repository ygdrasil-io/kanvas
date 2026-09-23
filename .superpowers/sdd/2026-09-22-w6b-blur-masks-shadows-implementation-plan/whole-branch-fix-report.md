# Rapport de correction whole-branch W6b

Base de correction : `2bf3d52`.

Cette vague est bornée aux findings I1–I7 et M1–M3 de
`whole-branch-review.md`. Aucun chemin W6c–W6e, GM, dashboard, render,
baseline, score, `jpg-color-cube` ou suite Skia globale n'a été modifié ni
exécuté.

## RED → GREEN

| Finding | RED constaté | GREEN livré | Preuve ciblée |
| --- | --- | --- | --- |
| I1 | Le renderer reprojetait clips/origins et reconstituait des offsets W6b. | `FilterInputSamplingV1` et `PictureCompositeOperandsV1` portent les faits I32 target-local, calculés checked dans `W6aLayerGraphConstruction`/`W6bFilterGraphConstruction`. La map `targetOriginsDeviceI32` est supprimée; le bridge W4e lit l'origin déjà scellée du `RenderPass`. | `RenderGraphContractTest.w6b publication witness rejects renderer-local source sampling`; scan production sans `targetOriginsDeviceI32` ni projection clip W6b. |
| I2 | Table appliquait `coverage * LUT[coverage]`, qui mettait une AA identity au carré. | Le fragment retourne directement `LUT[coverage]`. | `W6bMaskShaderTableSurfacePixelTest.identity 256 entry table preserves fractional anti aliased coverage`. |
| I3 | `SOLID`/`OUTER`/`INNER` utilisaient `max`/soustraction/min, et l'oracle recopiait ces formules. | WGSL et oracle indépendant utilisent Porter-Duff : `o+b*(1-o)`, `b*(1-o)`, `b*o`. | `W6bMaskBlurAutoLayerSurfacePixelTest.fractional anti aliased coverage uses Porter Duff mask blur styles`. |
| I4 | Un sigma ou offset fini non représentable levait une exception générique et perdait `w6b.filter.invalid_bounds`. | La reverse-demand transforme ce cas en `ConstructionFailure(InvalidBounds)` avant publication; le sentinel et le recovery sont conservés. | Les deux cas `finite huge ... refuses with stable bounds diagnostic and same surface recovers` dans `W6bFilterAdmissionRecoverySurfaceTest`. |
| I5 | Decode schema 8 et Merge pouvaient allouer/copier au-delà de `GraphLimits.maxNodes` avant refus. | `SceneArchiveCodec` rejette compte table/fanout avant liste; builder/réserve sont bornés et la table validée n'est plus recopiée deux fois. | Les cas publics Picture `oversized schema 8 filter table ...` et `oversized schema 8 Merge fanout ...`, puis replay/recovery. |
| I6 | Les `RectF32` des nodes restaient mutables à l'entrée et via `nodeAt`. | Crop/Tile/Picture/Magnifier snapshotent à l'entrée, stockent des copies et n'exposent que des copies; canonical/wire restent stables. | `captured filter table snapshots mutable rectangle input and node exposure`. |
| I7 | `W6bFilterPictureTest` inspectait adapter/compiler/graph, hors gate public. | Le test ne garde que Picture bytes/memory/wire/replay public; le test direct de l'adapter W6b est retiré. Le seul invariant de publication est dans `RenderGraphContractTest`. | `W6bFilterPictureTest` (12 cas publics), plus le contrat I1. |
| M1 | IDs et paramètres W6b I32 avaient des noms ambigus. | `CapturedFilterNodeIdI32`, `CapturedPictureIdI32`, `CapturedBackdropIdI32`, `valueI32` et les paramètres/offsets W6b sont renommés sans modifier canonical ou wire. | Compilation `render-ir` transitive, `gpu-plan`, `gpu-renderer`, `kanvas`. |
| M2 | Le baseline négatif DropShadow était invalide avant sa mutation annoncée. | Le helper publie un baseline complet (sampling linéaire et offsets); chaque cas ne mute qu'un fait. | `RenderGraphContractTest.w6b publication witness validates drop shadow original ownership and mode arity`. |
| M3 | Status, base et comptes étaient contradictoires. | Ledger : base W6a exacte et vague finale `2bf3d52`; status/README : B=336, nested=344, shadow 6, sept shards 80. | Relecture des documents et XML frais ci-dessous. |

## Fichiers de production principaux

- `gpu-plan`: `W6bFilterPlanV1.kt`, `W6bFilterGraphConstruction.kt`,
  `W6bFilterGraphWitnessV1.kt`, `W6aLayerGraphConstruction.kt`,
  `W6aLayerPlanCompiler.kt`, `PlanPasses.kt`, `PictureStreamAggregateV1.kt`.
- `gpu-renderer`: `GPUW6aLayerFramePlan.kt`,
  `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`,
  `W6bMaskCoverageSnippet.kt`, `W6bSeparableBlurSnippet.kt`.
- `render-ir`: `CapturedFilterTableV1.kt`, `SceneArchiveCodec.kt`.

## Gates frais

1. `rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileTestKotlin`
   — exit 0.
2. `rtk ./gradlew :gpu-plan:test --tests org.graphiks.kanvas.gpu.plan.RenderGraphContractTest`
   — exit 0, XML 95/95, 0 failure/error/skip.
3. Les sept selectors W6b publics (Picture, admission/recovery, image blur,
   mask blur, mask shader/table, DropShadow, budget/recovery) — XML 80/80,
   0 failure/error/skip. Après les assertions, `Gradle Test Executor 80` est
   sorti 133 : statut native **UNKNOWN**, jamais GREEN.

## Autorité et risques restants

Les scissors, sample rectangles et offsets W6b sont désormais les valeurs
target-local I32 scellées par le plan. Aucun operand/pass W6b ne consulte une
map d'origins. `MaskShader` peut encore recevoir l'origin device requise par
un matériau W5 déjà scellé, et `GraphTexture` son mapping déjà émis par son
autorité antérieure; aucun des deux ne recalcule device→target, scissor ou
offset W6b. Le native exit 133 reste le seul état non qualifié. Les exclusions
W6b et les domaines W6c–W6e restent inchangés.
