# W06 — layers et effets : checkpoints W6a/W6b

## Statut

## Checkpoint W6b / correction whole-branch — shadows et budgets

La vague de correction bornée issue de la revue whole-branch est basée sur
`2bf3d52`. Elle ferme I1–I7 et M1–M3 sans étendre les exclusions W6b.

Task 6 matérialise uniquement les opérations déjà gelées
`DROP_SHADOW_COLORIZE` et `DROP_SHADOW_COMPOSITE`. La route native consomme
le schedule, les `FilterPass`, les `FilterTarget`, les origins, les générations
de sources et les terminaux publiés par `:gpu-plan`; elle ne recrée ni pass,
source, bounds, slot ni budget. `COMPOSITE` lie la source originale exactement
une fois au composite interne puis laisse le blend parent gelé s'exécuter une
fois; `SHADOW_ONLY` ne lie aucune source originale.

Le contrat compact a été vérifié indépendamment avant toute modification de
l'oracle : `ImageFilter.DropShadow` n'expose aucun `TileMode`, tandis que Skia
construit `SkImageFilters::Blur(sigma, input)` dans
`SkDropShadowImageFilter.cpp`; la surcharge sans mode a `kDecal` par défaut.
Le `TileMode.CLAMP` antérieur dans le graphe W6b était donc une divergence de
la sémantique publique, pas une convention d'implémentation. La planification
gelée emploie désormais `DECAL`; l'oracle public modèle un domaine transparent
étendu, afin de ne pas tronquer le halo avant l'offset. La conversion de la
couleur encode également la couleur sRGB après la multiplication alpha en
linéaire, conformément au contrat W3 RGBA8 sRGB prémultiplié.

- `W6bDropShadowSurfacePixelTest` prouve `SHADOW_ONLY` sans source, halo
  étendu/translaté, `COMPOSITE`, layer explicite imbriqué et replay Picture
  mémoire/wire : 6/6 XML PASS.
- `W6bBudgetRecoverySurfacePixelTest` dérive publiquement B=336
  (`root 8 + aggregate/source 8 + X/Y/color 12 + uniforms 52 + readback 256`)
  et B imbriqué=344; B accepte, B−1 refuse avant allocation
  avec `w6b.filter.frame_budget_exceeded`, et le sibling tardif conserve le
  sentinel avant `discardRecordedOperations`/recovery : 2/2 XML PASS.
- Les sept shards W6b ciblés totalisent 80/80 assertions XML PASS : Picture
  12, admission/recovery 27, image blur 10, mask blur 10, mask shader/table
  13, DropShadow 6 et budget/recovery 2. Les compilations ciblées
  `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
  `:kanvas:compileTestKotlin` sortent 0.

Chaque shard GPU a ensuite reçu exit natif 133 après ses assertions. Cet état
reste **UNKNOWN**, sans attribution au changement W6b, au test ou à
l'environnement; il n'est pas compté comme GREEN natif.

## Audit Task 6

La projection W6b passe seulement par `GPUW6aLayerFramePlan`,
`GPUW6aEncoderScopesV1` et `GPUWgpu4kW6aLayerFramePayloadMaterializer`.
Le plan scelle, avec math checked, chaque scissor, sample rectangle et offset
W6b en I32 target-local; native valide et matérialise ces valeurs sans
recalcul device→target. Aucun operand/pass W6b ne consulte
`targetOriginsDeviceI32` : la map a été retirée. Le seul bridge de position
restant est l'origin device W5/W4e déjà figée sur le `RenderPass` pour un
matériau legacy, et `MaskShader`/`GraphTexture` conservent uniquement leurs
mappings émis par l'autorité antérieure, sans scissor ni offset W6b tardif.
Il n'y a ni création post-freeze de `PlanPass`/`PlanResource`, ni budget ou
bounds renderer-local. Les références `GPUSeparableBlurRectFrameRecorder`,
`GPUPreparedFilterDAGPlanner`, `GPUPreparedMaskFilterLowerer` et
`GPUDropShadow` restent des définitions de voies legacy/W8 sans appel depuis
la route W6b. Aucun test de forme/source, fake device ou compteur interne n'a
été ajouté.

W6c reçoit sans modification la table capturée, les evaluation keys, les
`FilterPass`/`FilterTarget`, le schedule, les lifetimes et les terminaux
scellés par Tasks 1–6.

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
pass de plan après freeze. Les operands W6b ne font aucun `.toInt()` de
bornes, projection de clip ou conversion device→target renderer-local.

Les références interdites restent hors route W6a et sont conservées comme
legacy/W8 : `GPULayerSaveRecord` et `GPUPreparedCompositeLowerer` dans la
voie prepared historique; `mergeCompositeCommands` et
`splitCompositeChildrenRenders` dans son task-list builder; et
`copyTargetToOffscreenTexture` dans le runtime prepared historique. Elles ne
portent aucune référence W6a. Elles restent un risque legacy/W8 explicite, pas
une autorité de fallback après sélection W6a.

## Exclusions et limites

- Backdrop et `initWithPrevious` filtré restent refusés; W6c/W6d et les
  familles spatiales hors blur/mask/shadow W6b ne sont pas admis.
- F16/HDR : capability gap explicite; RGBA8 est le seul target positif W6.
- Fonts, codecs, GMs, dashboard, renders/références/baselines/scores, suite
  Skia globale et `jpg-color-cube` : non exécutés.
- Device-loss/visibilité native : non prouvés. Les exits 133 restent UNKNOWN.
- Les voies legacy prepared et travaux W8 restent conservés et suivis; ce
  checkpoint ne réclame ni couverture ISO ni convergence globale.

## Clôture W6a

La revue Sol whole-branch sur `8bdc730c9..4fc780e07` n'a relevé aucun
finding Critical, Important ou Minor. La vérification finale combinée a
ensuite révélé que le diagnostic W5g `budget.w5g.composed-uniform` était
réécrit par la correction de convergence; `4509aa9c6` préserve désormais cet
owner précis tout en conservant `w6a.layer.frame_budget_exceeded` pour le
budget frame-wide W6a. La re-review Sol scoped est clean.

Sur le HEAD corrigé, les sept compilations prescrites sortent 0 et les huit
shards publics W6a exécutés ensemble totalisent 91/91 assertions JUnit PASS.
Le worker natif sort encore 133 après ces assertions : le statut natif reste
**UNKNOWN**.

Draft PR W6a directement sur `codex/w5h-registered-runtime-effects` :
[#2403](https://github.com/ygdrasil-io/kanvas/pull/2403). W6a est clôturée dans
les limites explicites ci-dessus. Task 6 ferme W6b dans son périmètre borné;
W6c est l'étape suivante pour les familles et capabilities exclues, sans
réouvrir les operands target-local scellés de W6b.
