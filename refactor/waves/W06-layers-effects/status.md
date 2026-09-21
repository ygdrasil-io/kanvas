# W06 — layers et effets : checkpoint W6a

## Statut

Checkpoint de convergence W6a effectué sur la source immuable
`cdacd0b8e94534b87543fecfceb48d3a67fe6e5a` (avant la correction bornée
ci-dessous). Les assertions publiques W6a sont qualifiées par leurs XML, mais
les executors natifs qui sortent avec le code 133 sont **UNKNOWN**, jamais
GREEN. Ce document ne formule donc ni claim ISO ni claim global, et ne clôt
pas W6a avant la revue whole-branch et la Draft PR pilotées séparément.

La correction de convergence borne le changement à
`FrameSourceLayoutV4.add`: lorsqu'une frame possède `layeredInput`, toute
limite de frame agrégée devient le refus W6a
`w6a.layer.frame_budget_exceeded`, sans modifier la route W4/W5 ordinaire.
Les RED publics observés étaient
`sourceUniformBudgetRefusesPreciselyAndRecovers` et
`translatedFractionalBoundsRoundOutward`; ils sont GREEN après correction.

## Cellules W6a observées

- Picture 13/schema 7 et readers Picture 8–12 : 9/9 assertions XML GREEN.
- `initWithPrevious`, bounds/origins/mappings, restore alpha/filter/final
  blend/destination-read, nesting et terminal recovery : couverts par les
  shards publics indiqués ci-dessous.
- Les budgets couvrent root/readback, layer targets, previous copies,
  destination snapshots et owners W4/W5. Les contrôles B/B−1 et recovery sont
  présents dans `W6aLayerBudgetRecoverySurfacePixelTest` (10/10 XML GREEN).
- Les lanes W4/W5 applicables restent dans leur autorité existante : le shard
  discriminant W6a est 19/19 XML GREEN; les selectors W5b/W5f/W5h ciblés sont
  consignés ci-dessous.

## Custody des shards publics W6a

Les chemins XML sont relatifs à `kanvas/build/test-results/test/`; chaque
commande est `:kanvas:test --tests <classe>` et a été exécutée séquentiellement.
`Gradle=1` signifie `BUILD FAILED` exclusivement parce que l'executor natif a
quitté 133; `native=133/UNKNOWN` est séparé des compteurs XML.

| Shard | Méthodes XML | PASS/F/E/S | Gradle | XML custody | Native |
| --- | ---: | ---: | ---: | --- | --- |
| `W6aLayerPictureTest` | 9 | 9/0/0/0 | 1 | `TEST-org.graphiks.kanvas.picture.W6aLayerPictureTest.xml` | 133/UNKNOWN |
| `W6aLayerSurfacePixelTest` (premier) | 16 | 15/1/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aLayerBoundsSurfacePixelTest` (premier) | 10 | 9/1/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aLayerRestoreSurfacePixelTest` | 9 | 9/0/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aNestedLayerSurfacePixelTest` | 10 | 10/0/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aInitWithPreviousSurfacePixelTest` | 8 | 8/0/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aInitWithPreviousSurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aLayerBudgetRecoverySurfacePixelTest` | 10 | 10/0/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aLayerW4W5SurfacePixelTest` | 19 | 19/0/0/0 | 1 | `TEST-org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest.xml` | 133/UNKNOWN |
| `W6aLayerSurfacePixelTest` (correction) | 16 | 16/0/0/0 | 1 | même custody | 133/UNKNOWN |
| `W6aLayerBoundsSurfacePixelTest` (correction) | 10 | 10/0/0/0 | 1 | même custody | 133/UNKNOWN |

Les deux premiers failures, tous deux au diagnostic de budget, exposaient
`resource.material.gradient.stop-budget` au lieu du diagnostic W6a requis;
aucun autre failure/error/skip XML W6a n'a été observé.

## Préservation ciblée W4/W5

| Selector | Méthodes XML | PASS/F/E/S | Gradle | Native |
| --- | ---: | ---: | ---: | --- |
| `W5bBlendSurfacePixelTest` | 50 | 50/0/0/0 | 0 | 0 |
| `W5fColorFilterSurfacePixelTest` | 44 | 44/0/0/0 | 1 | 133/UNKNOWN |
| `W5fFilterOrderingSurfacePixelTest.nestedFiltersPreserveBothOpacityOrdersAndDirectPathSources` | 1 | 1/0/0/0 | 1 | 133/UNKNOWN |
| `W5hConvergenceSurfacePixelTest.lateSiblingRefusalDoesNotPublishAndSameSurfaceRecovers` | 1 | 1/0/0/0 | 1 | 133/UNKNOWN |
| `W5hConvergenceSurfacePixelTest.independentEqualRuntimeOwnersKeepTheirImageBudgets` | 1 | 1/0/0/0 | 1 | 133/UNKNOWN |
| `W5hGeometryHLaneSurfacePixelTest.materialRefusalThenSameSurfaceRecovers` | 7 | 7/0/0/0 | 1 | 133/UNKNOWN |
| `W5hImageOriginSurfacePixelTest.rgbaHalfAlphaPublicControl` | 1 | 1/0/0/0 | 1 | 133/UNKNOWN |

## Compilations séparées

Toutes exécutées séquentiellement, exit 0 :
`:math:geometry:compileKotlinJvm`, `:math:matrix:compileKotlinJvm`,
`:render-ir:compileKotlin`, `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin`, `:kanvas:compileKotlin`, et
`:kanvas:compileTestKotlin`.

## Audit d'autorité et chemins conservés

La route W6a passe par `W6aLayerGraphLowerer`, `GPUW6aLayerFramePlan` et
`GPUWgpu4kW6aLayerFramePayloadMaterializer`. Elle itère le graphe et le layout
physique scellés; les créations natives de textures/buffers sont la
matérialisation des `PlanResource` déjà gelées, non une création de resource ou
pass de plan après freeze. Aucun `.toInt()` de bornes renderer-local n'est
présent dans ces fichiers W6a.

Les références interdites restent hors route W6a et sont conservées comme
legacy/W8 : `GPULayerSaveRecord` et `GPUPreparedCompositeLowerer` dans la
voie prepared historique; `mergeCompositeCommands` et
`splitCompositeChildrenRenders` dans son task-list builder; et
`copyTargetToOffscreenTexture` dans le runtime prepared historique. Elles ne
portent aucune référence W6a. Elles restent un risque legacy/W8 explicite, pas
une autorité de fallback après sélection W6a.

## Exclusions et limites

- Backdrop et tous les image/mask/spatial filters : refus W6 précis, aucune
  approximation positive.
- F16/HDR : capability gap explicite; RGBA8 est le seul target positif W6a.
- Fonts, codecs, GMs, dashboard, renders/références/scores, suite Skia globale
  et `jpg-color-cube` : non exécutés.
- Device-loss/visibilité native : non prouvés. Les exits 133 restent UNKNOWN.
- Les voies legacy prepared et travaux W8 restent conservés et suivis; ce
  checkpoint ne réclame ni couverture ISO ni convergence globale.
