# Rapport de correction whole-branch W6b — round 1 (historique)

Base de correction : `2bf3d52`.

> La re-review a constaté que ce round ne fermait pas encore I1, I5 et I7.
> Les preuves historiques R21 sont dans `whole-branch-fix2-report.md`; le
> statut final R22 est dans `whole-branch-fix3-report.md`.

Cette vague est bornée aux findings I1–I7 et M1–M3 de
`whole-branch-review.md`. Aucun chemin W6c–W6e, GM, dashboard, render,
baseline, score, `jpg-color-cube` ou suite Skia globale n'a été modifié ni
exécuté.

## RED → GREEN

| Finding | RED constaté | GREEN livré | Preuve ciblée |
| --- | --- | --- | --- |
| I1 | Le renderer reprojetait clips/origins et reconstituait des offsets W6b. | Partiel : les operands d'entrée et Picture étaient scellés, mais `FilterComposite` recalculait encore l'offset côté native. Fermé seulement au round 2. | Re-review R21 puis `whole-branch-fix2-report.md`. |
| I2 | Table appliquait `coverage * LUT[coverage]`, qui mettait une AA identity au carré. | Le fragment retourne directement `LUT[coverage]`. | `W6bMaskShaderTableSurfacePixelTest.identity 256 entry table preserves fractional anti aliased coverage`. |
| I3 | `SOLID`/`OUTER`/`INNER` utilisaient `max`/soustraction/min, et l'oracle recopiait ces formules. | WGSL et oracle indépendant utilisent Porter-Duff : `o+b*(1-o)`, `b*(1-o)`, `b*o`. | `W6bMaskBlurAutoLayerSurfacePixelTest.fractional anti aliased coverage uses Porter Duff mask blur styles`. |
| I4 | Un sigma ou offset fini non représentable levait une exception générique et perdait `w6b.filter.invalid_bounds`. | La reverse-demand transforme ce cas en `ConstructionFailure(InvalidBounds)` avant publication; le sentinel et le recovery sont conservés. | Les deux cas `finite huge ... refuses with stable bounds diagnostic and same surface recovers` dans `W6bFilterAdmissionRecoverySurfaceTest`. |
| I5 | Decode schema 8 et Merge pouvaient allouer/copier au-delà de `GraphLimits.maxNodes` avant refus. | Partiel : les limites et `reserve()` étaient bornés, mais les listes privées fraîches restaient recopiées une seconde fois. Fermé seulement au round 2. | Re-review R21 puis `whole-branch-fix2-report.md`. |
| I6 | Les `RectF32` des nodes restaient mutables à l'entrée et via `nodeAt`. | Crop/Tile/Picture/Magnifier snapshotent à l'entrée, stockent des copies et n'exposent que des copies; canonical/wire restent stables. | La preuve a été ramenée au capture/bytes/replay Picture public au round 2. |
| I7 | `W6bFilterPictureTest` inspectait adapter/compiler/graph, hors gate public. | Partiel : l'adapter direct avait été retiré, mais le shard gardait table/codec/`SceneSnapshot` directs. Fermé seulement au round 2. | Re-review R21 puis `whole-branch-fix2-report.md`. |
| M1 | IDs et paramètres W6b I32 avaient des noms ambigus. | `CapturedFilterNodeIdI32`, `CapturedPictureIdI32`, `CapturedBackdropIdI32`, `valueI32` et les paramètres/offsets W6b sont renommés sans modifier canonical ou wire. | Compilation `render-ir` transitive, `gpu-plan`, `gpu-renderer`, `kanvas`. |
| M2 | Le baseline négatif DropShadow était invalide avant sa mutation annoncée. | Le helper publie un baseline complet (sampling linéaire et offsets); chaque cas ne mute qu'un fait. | `RenderGraphContractTest.w6b publication witness validates drop shadow original ownership and mode arity`. |
| M3 | Status, base et comptes étaient contradictoires. | Partiel : les comptes du round 1 étaient exacts, mais la fermeture I1/I5/I7 était sur-déclarée. Fermé seulement au round 2. | Re-review R21 puis `whole-branch-fix2-report.md`. |

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

## Autorité et risques restants (état round 1)

Les operands d'entrée et les terminaux Picture étaient target-local I32
scellés, et aucun operand/pass W6b ne consultait une map d'origins.
`MaskShader` pouvait encore recevoir l'origin device requise par un matériau
W5 déjà scellé, et `GraphTexture` son mapping déjà émis par son autorité
antérieure; aucun des deux ne recalculait device→target, scissor ou offset
W6b. Restait néanmoins la dérivation des composites et la validation I1, la
seconde copie privée I5 et les appels IR directs I7. Ces écarts, ainsi que le
native exit 133 **UNKNOWN**, sont traités/qualifiés dans le rapport round 2;
les exclusions W6b et les domaines W6c–W6e restent inchangés.
