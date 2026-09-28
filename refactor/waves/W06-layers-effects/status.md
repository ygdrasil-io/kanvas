# W06 — layers et effets : checkpoints W6a/W6b/W6c/W6d

## Statut

### Task 2A0 Step 1 — inventaire de sites natifs avant tout nouveau B

Cette table est le relevé de départ pour 2A0, pas un inventaire déjà publié :
un `owner` est un `PlanPassId` et l'ordinal est celui de la séquence finale
du planner (ordinal de draw, de packet ou de bundle, selon le site). Les
faits cités sont les faits logiques typés à encoder dans `NativeSiteRecipeV1` ;
ils ne sont ni un hash générique de classe de pass, ni du WGSL, ni des bytes
du driver. Sauf la ligne W6d, chaque site **à programme** `OPEN` doit recevoir
en 2A1 une lease distincte de `max(4096L, descriptorBytes + recipeBytes)` en
checked-I64, même sur cache chaud. Cela reste une réserve logique, non une
mesure byte-exacte du driver.

| Site natif actuel | Owner / ordinal stable | Faits de recette logique typée à fermer | Charge et statut sur cette branche |
| --- | --- | --- | --- |
| W6a `RenderPass` — `SolidRectDraw` | `RenderPass.id` / `drawOrdinalI32` (`W6GeometrySiteKeyV1`) | `W6SolidRectHostRecipeV1`: fullscreen triangle, `UniformColor16` ou `FrozenColor` F32 canonique, `BlendPlan`, origine matériau, RGBA8-sRGB/1×, `FragmentPosition`, ABI group 0. | **FROZEN 2P0** dans le layout ; pas encore une `NativeSiteRecipeV1`, donc aucune nouvelle lease/budget 2A0. |
| W6a `RenderPass` — `AnalyticRectDraw` | `RenderPass.id` / `drawOrdinalI32` | `W6AnalyticRectHostRecipeV1` et selector `AnalyticRect`: bounds/raster/scissor, origine, AA analytique, triangle-list indexé, uniform/group 0 de 80 octets, blend, RGBA8-sRGB/1×. | **FROZEN 2P1** ; charge programme encore **OPEN**. |
| W6a `RenderPass` — `AnalyticRRectDraw` | `RenderPass.id` / `drawOrdinalI32` | `W6AnalyticRRectHostRecipeV1`: `RRectF32` canonique, origine `RECT`/`RRECT`, raster/scissor et le même selector analytic 80. | **FROZEN 2P2** ; charge programme encore **OPEN**. |
| W6a `RenderPass` — `W5bPointDraw` non clip-only | `RenderPass.id` / `drawOrdinalI32` | `W6PointHostRecipeV1`: `PointMode`, séquences V/I immuables, bounds/scissor, origine, selector `Point`, triangle-list, uniform/group 0 de 32 octets, blend, RGBA8-sRGB/1×. | **FROZEN 2P3** ; charge programme encore **OPEN**. Les paths W4e et la coverage W6b ne sont pas inclus. |
| W6a `RenderPass` — `W5bVerticesDraw` | `RenderPass.id` / `drawOrdinalI32` | `W6PreparedVerticesHostRecipeV1`: layout/attributs/stride, topology, largeur d'index, alpha primitive, payload upload canonique, source `MaterialPlanTable` et bindings canoniques, identité de programme hôte sans handle, blend, origine, DrawUniform64/group 0, RGBA8-sRGB/1×. | **FROZEN 2P4** ; charge programme encore **OPEN**. Text/font et W4e restent hors de cette recette. |
| W6a `LayerComposite` restore simple | `LayerComposite.id` / site `0` (`W6LayerCompositeSiteKeyV1`) | `W6PlainLayerCompositeRecipeV1`: source/destination `PlanResourceId`, bounds et origine copiés, alpha F32 canonique, blend non-destination-read, fullscreen restore, texture group 0, RGBA8-sRGB/1×. | **FROZEN 2P5/2A0b.IIIa1/IIIa2** : restore simple, filtré W5f, destination-read texture+snapshot et W5f+snapshot ont des recettes distinctes revues. Charge programme, B/B−1 et authentification 2B encore **OPEN**. |
| W6a `RenderPass`, `StencilGeometryProducerV3`, `StencilCover` hors recettes 2P | `PlanPass.id` / ordinal draw, puis **un ordinal par bundle créé** | Draw/packet final, target/depth-stencil/resolve, geometry et buffers déjà scellés, phase, sample count, load/store, stencil state, blend et bindings source/coverage. Pour un stencil, `stencil-producer` et `stencil-cover` sont deux recettes et deux ordinals, jamais un seul site. | **OPEN 2A0** pour ces sites hors source masque W6b ; aucune charge actuellement. La sélection renderer est encore dynamique. |
| W6a `FilterCoverageSourcePass` | `FilterCoverageSourcePass.id` / ordinal de bundle coverage | Variante explicite : alpha source + sampling, absence publiée (`emptyRender` transparent), `SolidRect` coverage, ou raster W4 avec target, depth, sample, geometry et bindings. Si `depthStencil != null`, `coverageRasterRender` exige deux packets et crée deux pipelines : producer (ordinal 0) puis coverage/cover (ordinal 1), sous le même owner. | **FROZEN** : raster direct/producer/cover en 2P7b, absence `Empty` en 2A0b.Ia, alpha en Ib1 et SolidRect en Ib2 ; **aucune lease/budget encore**. Les deux bundles stencil restent deux futures leases distinctes. L'absence crée réellement programme, groupe vide et draw : ce n'est pas un skip. |
| W6a helpers `emptyRender` | Pass propriétaire / ordinal de chaque appel : `PictureAggregateBeginPass`, `FilterSourceClear`, `PictureAggregateSealPass`, coverage sans producteur et no-op composite | Target, clear/load/store, couleur/raison de no-op et phase publiée. Le comportement courant crée un layout vide, shader module, pipeline layout/pipeline et bind group vide, puis un fullscreen draw. | **FROZEN 2A0b.Ia**, témoin public `PictureComposite` scissor nul confirmé en Ic2 ; **un bundle programme par site reste dû au budget 2A1**. Aucune variante « zéro programme » n'est autorisée sans changement de comportement séparé. |
| W6a sources, filtres et composites | ID de chaque `PictureSourcePass`, `FilterCoverageRetainPass`, `FilterPass`, `PictureComposite` ou `FilterComposite` / ordinal de l'unique render ou de chaque draw | Inputs/outputs et offsets target-local, operation spécialisée (Crop/Offset/Tile/ColorFilter/Merge/Blend/Morphology/blur/mask style ou mask shader), ordre des inputs, uniform/storage/image/runtime bindings, scissor, blend, format/sample, ABI. Les `FilterComposite.Draw` no-op et les branches filtrées/destination-read sont des variantes séparées. | **FROZEN 2A0b.I/II/III** : `FilterCoverageRetainPass`, les deux `PictureSourcePass` (layer/graph), tous les `FilterPass` non-W6d `Crop`/`Offset`/`Tile`/`Morphology`/`ColorFilter`/`Merge`/`Blend`/`SeparableBlur`/`MaskBlurStyle`/`MaskShader`/`MaskTable`/`MaterializedSource`/`DropShadowColorize`/`DropShadowComposite` sont gelés et revus ; les no-op composites relèvent d'`Empty`. Les composites actifs/filtrés/destination-read sont review-clean après IIIa2 ; ce gel ne réserve encore aucune charge. `TextureCopy` et `ReadbackPass` restent sans bundle ; leur destination-read sampler est réservé à Task 3. Aucune nouvelle lease/budget encore. |
| W6d `FilterPass.frozenSamplingProgram` | `FilterPass.id` / owner déjà unique (ordinal du futur inventaire à adapter, pas une copie de lease) | `W6dFrozenProgramBindingV1`, inputs/output, descriptor RGBA8/1×, usages shader module + render pipeline, génération et lifetime frame completion. | **DÉJÀ LEASÉ W6d** via `W6dProgramLeaseV1` : `max(4096L, descriptorBytes + recipePayloadBytes)`, range `[0, passes.size)`. 2A0/2A1 l'intègre par référence, sans double charge. |
| W4e `ClipMaskInitialize` dans `encodeW4eNativePasses` | `ClipMaskInitialize.id` / packet ordinal final (un seul bundle) | `W4eClipMaskInitializeRecipeV1`: output, domaine `RectI32`, coverage F32 canonique, fullscreen triangle, clear, RGBA8-unorm/1×, aucun bind group. | **FROZEN 2P6** et préflight de cohérence présent ; pas encore lease/budget 2A0. |
| W4e `ClipMaskProducer`, `ClipMaskFold` de W6 | `PlanPass.id` / `PlanPass.ordinal` du packet, puis ordinal de bundle créé | Producer : target/resolve/depth, géométrie Rect/RRect/Path/Empty, inverse, AA, sample et slices V/I/U ; Fold : previous/source/output, opération et domaine. | **FROZEN 2A0c.I + IIa + IIb1** : Rect/RRect analytiques, Fold, `Path` triangle direct et bundle stencil-edge ordinal 0 sont review-clean. **OPEN IIb2** cover ordinal 1, puis III/IV. Aucun lease/budget 2A1 ou gate 2B. `PathMaskClear` W4d direct n'est pas publié par W6. |
| W4e path packet admis par W6 dans `encodeW4eNativePasses` | `PlanPass.id` / `PlanPass.ordinal` du packet, puis ordinal de bundle dans ce packet | Phases W6 `SingleSampleDirectColor`, `SingleSampleStencilProducer`, `SingleSampleStencilColorCover` seulement ; géométrie/bounds/scissor, fill strategy/rule, target/depth, load/store/stencil/blend, consumer mask/inverse/domain et bindings U/V/I. Les phases `Multisample*`/`HardEdge*` du même encodeur restent sur la route W4d directe, hors inventaire W6. | **OPEN 2A0c.III/IV**. Un stencil-cover à intérieur inverse peut créer `interiorZero` puis `cover`. Un inverse-domain à intérieur Geometry crée **trois pipelines** : `domainStencil` 0, `interiorZero` 1, color `cover` 2. `commonSource` peut omettre le draw domainStencil, mais pas sa création logique ni sa réservation. |
| Dispatcher `materializeGeometry` | Inventaire de frame entier ; il ne fabrique pas d'owner | Route W6a/W4e/route directe, génération, `FramePlan`/source witness/encoder/resource seals et ensemble fermé des recipes attendues. | **OPEN 2B** : aucun `W6NativeArtifactConsumptionV1.preflight` commun avant allocation ; ne pas annoncer l'authentification native fermée. |
| W5a post-spécialisation dans `materializeW5aSourcePartitionV2` | `RenderPass.id` ou `StencilCover.id` / ordinal de draw source après `sourceDrawsV2` | Référence à la recette géométrique W6/W4e (jamais handle WebGPU), `MaterialPlanTable` structural id, stage/binding manifest canonique, template hôte, ABI composition, destination snapshot/bounds, flag source masque W6b et les groupes matériau/coverage/destination réellement requis. Pour un packet W4e inverse-domain, `sourceDrawsV2` accepte 2–3 draws : les préfixes no-bindings restent les bundles W4e de leurs propres ordinals ; seule la dernière draw à bind group porte la source W5a. | **OPEN 2A0/2B** ; le renderer compose encore module/layout/pipeline/group après spécialisation. Une source masque W6b et une source ordinaire sont des variantes distinctes. |
| W5a groupes coverage et destination, y compris `Kanvas.w5b.layered.nearest` | Même owner/ordinal W5a que la source qui les consomme ; destination par `TextureCopy.destination` avant freeze | Coverage : texture group 3 et ABI scalar lorsque requis. Destination : snapshot texture + sampler nearest, ABI composition et bounds. | Groupes programme **OPEN 2A0** ; le sampler natif W6 destination-read est **OPEN Task 3** (future lease `4096L` par slot). Le sampler runtime W5h reste reporté : aucun effet public positif ne le demande et `PlanCacheResourceRequest.Sampler.byteSizeI64` demeure `0L`. |

Les recettes 2P0–2P6 ci-dessus sont donc des prérequis de sélection, pas une
preuve de B/B−1 et pas une fermeture de l'authentification. Avant 2A1, aucun
nouvel inventaire de charges n'est gelé ; avant 2B, le dispatcher, W6, W4e et
W5a ne consomment pas encore une autorité commune exhaustive. Les bytes WGSL,
les handles et la taille réelle module/pipeline/sampler du driver restent dans
le renderer et hors de la comptabilité logique du planner.

## Checkpoint W6e Task 6 — budgets, cache, refus terminal et recovery publics

La convergence W6e est empilée sur le prérequis revu
`codex/w6e-filter-bounds-recipe` à `6bd5962e5` (Draft PR #2407), lui-même
empilé sur W6d ; elle ne cible donc pas W6d directement. Les neuf commits de
témoins publics déjà présents sont `bfb2ef150`, `311d2a6be`, `15d801f3a`,
`f8249079c`, `8d4c72a67`, `b65e7f674`, `99de83a62`, `8683e4c51` et
`b2fc6450d`. Ce checkpoint est amendé depuis `282709744` (`docs(refactor):
correct w6e cross-lane custody`) et s'appuie sur la source locale
`85e6f6c80` (`test(kanvas): close w6e effects convergence`) ; cette
attribution ne signale ni push ni PR. L'amendement approuvé de Task 6 ajoute, dans
`W6eCrossLaneEffectsSurfacePixelTest`, 22 cas JUnit publics et indépendamment
nommés de replay mémoire `Picture` ; chacun capture son fixture Task 5 puis le
rejoue deux fois via `Surface`. Il conserve
`W6eEffectsBudgetCacheRecoverySurfacePixelTest`, sans modifier de production,
de format Picture, de planner, de cache, de route ou de renderer.

Son fixture Picture Crop 2×1 calcule son budget avant toute construction :
root RGBA8 `2×1×4 = 8`, coverage/shaded/Crop `3×1×1×4 = 12`, deux rows
frame/material `2×16 = 32`, readback RGBA8 aligné `256`, soit
`B = 8 + 12 + 32 + 256 = 308` octets checked-I64. `B` rend exactement le bleu
puis la transparence ; le même Picture est rejoué publiquement pour chauffer
le cache, et `B−1` reste terminal avec
`w6b.filter.frame_budget_exceeded:`, sentinel intact. Un sibling bleu antérieur
suivi d'un runtime `IMAGE_FILTER` à ABI incompatible refuse avec
`w6d.runtime_effect.abi_unsupported:`, ne publie pas le sibling dans le buffer
sentinel et, après `discardRecordedOperations()`, rend le vert attendu sur la
même `Surface`. Les attentes sont définies avant `PictureRecorder`/`Surface`;
les témoins n'inspectent aucune clé de cache, lease, plan ou backend.

Les quatre preuves B/B−1 sont séparées et arithmétiques avant leur propre
`PictureRecorder`/`Surface` : W6b Shadow Picture
`8 + 8 + 12 + 52 + 256 = 336`, W6c Crop layer
`5×4 + 2×16 + 256 = 308`, W6d MatrixConvolution layer
`5×4 + 2×16 + 4096 + 256 = 4404`, et W6e Crop Picture
`8 + 12 + 32 + 256 = 308` octets checked-I64. Elles ne sont ni une formule
commune ni une observation de hit du cache natif. La matrice Task 5 reste 22
fixtures aux dimensions, inputs et ressources distincts : les replays mémoire
prouvent leurs pixels, mais ne déterminent pas les slots/lifetimes ni les
charges target/program/lease d'un pic commun. La matrice de budgets exacts
par famille et son agrégat restent donc un gap ouvert ; leases et générations
relèvent de la revue structurelle, sans claim public correspondant.

Les sept compilations prescrites sortent 0 : `:math:geometry:compileKotlinJvm`,
`:math:matrix:compileKotlinJvm`, `:render-ir:compileKotlin`,
`:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin`,
`:kanvas:compileKotlin` et `:kanvas:compileTestKotlin`. Les quatorze
sélecteurs publics sont exécutés séquentiellement avec leurs XML de classe :
Surface Core `1/0/0/0`, Composition `1/0/0/0`, Lighting/Picture `7/0/0/0`,
Advanced `1/0/0/0`, Cross-lane `47/0/0/0` (22 anciens + 22 replays mémoire +
3 témoins), Budget/Cache/Recovery `2/0/0/0` ; Picture Core `1/0/0/0`,
Composition `1/0/0/0`, Lighting `1/0/0/0`, Runtime `1/0/0/0`, Cross-lane
`1/0/0/0` ; puis W6b Budget `2/0/0/0`, W6c Spatial Cache `6/0/0/0` et W6d
Advanced Recovery `5/0/0/0`, soit `77/0/0/0` XML au total. Chaque invocation
de test a Gradle exit 1 uniquement après l'exit natif 133 : la custody native
est **UNKNOWN**, jamais PASS. Cette classification ne cache aucun RED
sémantique JUnit et la même règle classe 134 `UNKNOWN`.

L'audit manuel post-freeze du diff W6e ne trouve aucun chemin de production
modifié. La recherche des références `GPUPreparedCompositeLowerer`,
`GPUPreparedSurfaceProductEntry`, `GPUImageFilterPlan`, `PlanPass` et
`PlanResource` identifie des définitions historiques et des références W8,
pas une création post-freeze de pass/resource/ID/bounds/cache-key ou un fallback
sur la route W6e ; aucun test statique n'est ajouté. Le writer courant reste
Picture 15/schema 9 avec lecteurs historiques ; les anciennes mentions
Picture 14/schema 8 sont de la documentation obsolète, pas une instruction de
wire change. Fonts, codecs, GMs/dashboard/renders/scores, Skia global,
`jpg-color-cube`, F16/HDR positif et toute claim ISO/globale restent exclus.
La review Sol whole-branch, le push et la Draft PR sur le prérequis restent
des gates contrôleur ; aucun merge n'a été effectué.

### Gate final W6d — reviews Sol closes, Draft PR stackée #2406

La review Sol globale depuis W6c a relevé quatre points `Important` et un
`Minor`. La première correction bornée (`536d0e58e`, `79c1fa86d`) a fermé
Picture sur `saveLayer`, les offsets de sampling gelés, les branches lighting
et la tolérance du témoin onze-familles. Sa relecture a isolé deux variants
`Magnifier` encore réels : `zoom < 1` sous snapshot clippé et draw direct
transformé dont la reverse demand ignorait le transform. L'utilisateur a
autorisé exceptionnellement une seconde correction ciblée et sa relecture.

Les commits `715eb7896` et `519129b8b` réservent désormais l'enveloppe des
texels effectivement échantillonnés, en F64 puis I32 vérifié, avec le même
mapping gelé pour la demande inverse et la source du draw. L'inset réduit à
une ligne reste inclusif comme le shader ; une marge d'un demi-texel conserve
également les égalités de `round(sampled - 0.5)` à la borne exclusive. Quatre
témoins pixels publics `Surface` + `Render` + `Readback` ont donné deux RED
causaux, puis les XML `4/0/0/0`. La relecture Sol ciblée marque les deux
variants **ADDRESSED**, sans nouveau Critical/Important.

Un ancien test W6b exigeait encore le refus de Blur + `initWithPrevious`,
comportement intentionnellement devenu positif dans W6d. Le commit
`b20a833fa` retire uniquement cette méthode obsolète ; les témoins W6d
conservent l'ordre save/child, le halo Blur sous clip et la récupération sur
la même `Surface`. La review Sol de ce retrait est Approved.

Vérification indépendante sur `b20a833fa` : `:gpu-plan:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0 ; les XML ciblés `Magnifier` 4/0/0/0,
W6b AdmissionRecovery 26/0/0/0, W6d BackdropPrevious 9/0/0/0 et W6d
AdvancedRecovery 5/0/0/0. Chaque task Gradle de test sort 1 après un executor
natif 133 : statut process/native **UNKNOWN**, jamais un succès natif. Le
diff check contre la base W6c est net. La [Draft PR W6d #2406](https://github.com/ygdrasil-io/kanvas/pull/2406)
est ouverte sur `codex/w6c-spatial-dag`, sans merge. W6e n'est pas démarrée.

Les checkpoints ci-dessous conservent leur état historique ; ce gate final
prévaut sur leurs anciennes mentions « PR non ouverte ».

### Correction whole-branch W6d — contextes sampling, snapshots et branches lighting

La correction bornée sur la base revue
`a9ad64fd6de89d7471f94d7d29f9a7d70c5dc281` conserve le graphe W6 unique et
ses ressources/pass/IDs gelés. Un `ImageFilter.Picture` de `saveLayer` sans
`sourceDraw` emploie désormais le mapping déjà gelé du layer ; un carrier de
draw continue à composer son transform exactement une fois. Les demandes
inverses de `MatrixConvolution`, `DisplacementMap` et `Magnifier` sont
propagées avant l'intersection parent : le halo Matrix et les snapshots
`backdrop`/`initWithPrevious` ne perdent donc plus leurs texels d'entrée.

Les recettes W6d figent aussi l'offset target-local de **chaque** input de
`DisplacementMap` et de `Magnifier`. Le renderer lit ces valeurs scellées sans
replan ni reconstruction de bounds : map et source de displacement peuvent
avoir des origins distincts, et la source Magnifier est exprimée dans le même
repère target-local. Les six lumières gardent leur consumer domain à travers
`Compose`, `Merge` et `Blend`, de sorte que les pixels créés depuis transparent
black ne sont plus coupés par la géométrie source.

Les witnesses publics `Surface` + `Render` + `Readback` frais couvrent Picture
sur layer, map décalée, Magnifier transformé à origin non nul, halo Matrix pour
backdrop/previous et les branches lighting `Merge`/`Blend`. La recovery
onze-familles est causale dans **un seul graphe gelé** : une même `Surface`
contient les onze lanes publiques 3×3, toutes peuplées et disjointes, et chaque
baseline ou mutation construit une seule telle `Surface`. L'oracle complet est
calculé avant `Surface`/`PictureRecorder`; Matrix est rempli sur sa lane au lieu
de s'appuyer sur le cas sparse non probant. Aucun composite `SRC` ne masque les
branches. Picture et runtime restent byte-exact ; les seules tolérances famille
sont explicites (Matrix, Displacement, Magnifier ≤1, lighting ≤2).

Le contrat historique W6a `unsupportedBackdropRefusesTerminallyAndRecovers`
est remplacé par l'acceptation pixel d'un Blur backdrop depuis son parent gelé,
comportement W6d intentionnel. Les sélecteurs ciblés ont donné, dans leurs XML
au moment de l'exécution, `W6dAdvancedSampling` 7/0/0/0,
`W6dAdvancedRecovery` 5/0/0/0, `W6dBackdropPrevious` 9/0/0/0,
`W6dLighting` 31/0/0/0, `W6dPictureFilter` 19/0/0/0, puis les préservations
W6a Layer 16/0/0/0 et Previous 8/0/0/0, W6b ImageBlur 10/0/0/0, W6c Compose
4/0/0/0 et MultiInput 5/0/0/0. Les workers terminent encore après JUnit avec
133 : native **UNKNOWN**, sans claim native, ISO ou globale. La compilation
finale `:gpu-plan:compileKotlin :gpu-renderer:compileKotlin
:kanvas:compileKotlin :kanvas:compileTestKotlin` sort 0. Fonts/codecs, GMs,
dashboard/renders/scores et suite globale restent exclus ; W6e n'est pas
démarrée.

### Checkpoint W6d Task 7 — ownership atomique de frame

La tranche Terra Task 7 est implémentée sur la source
`87b3b580a0c7f615e2034b6bcb58144edfe408f0` ; ses Steps 1–6 sont clos. Les
Steps 7–8 (une review Sol whole-branch, puis la Draft PR empilée) appartiennent
explicitement au contrôleur : aucune review, push, PR ou merge n'a été lancé
ici. W6e reste la prochaine vague après ces gates contrôleur.

`W6dAdvancedRecoverySurfacePixelTest` est une preuve exclusivement publique
`Surface`/`Canvas`/`Picture` : elle calcule ses octets attendus avant la
création de la `Surface` ou du `PictureRecorder`, puis observe pixels et scopes
`Render` + `Readback`. Le B exact de son fixture 1×1 est
`4 + 4 + 4 + 4 + 4 + 16 + 16 + 4096 + 256 = 4404` octets : root RGBA8, layer,
source draw capturée, source layer, `FilterTarget`, deux records uniforms, un
lease programme W6d logique de 4096 octets, puis une ligne de readback RGBA8
alignée. B accepte ; B−1 refuse avec
`w6d.layer.frame_budget_exceeded`, laisse le sentinel intact et récupère sur la
même `Surface`. Le même test couvre replay chaud encore pessimiste, sibling
avancé tardif atomique et un seul graphe gelé qui contient les onze familles.
Ce dernier témoin contient onze bandes publiques 3×3 disjointes ; un oracle CPU
complet est calculé avant `Surface` et `PictureRecorder`, puis une mutation
publique de chaque famille doit modifier sa propre bande. Il ne termine plus
par une couleur `SRC` opaque qui pourrait masquer les branches.

`GPUColorFormat.RGBA16_FLOAT` est le plus petit token public de requête F16,
strictement refusal-only : il ne construit ni target, ni conversion, ni backend.
Une frame W6d-owned le terminalise avant capture, plan, publication ou soumission
native avec `w6d.layer.unsupported_target_format`; RGBA8 s'exécute positivement.
L'inventaire reste le graphe W6 existant : `PlanResource` impose descriptor,
usage, slot, lifetime et taille checked-I64 avant freeze ; le peak sémantique
est `peakFrameLocalBytesI64` et le peak physique additionne chaque slot par
`Math.addExact`. Chaque programme W6d ajoute avant publication un lease typé
gelé : owner `PlanPassId`, génération device, descriptor (programme, inputs,
output, RGBA8/1×), usages `{ShaderModule, RenderPipeline}`, slot physique et
lifetime `[0, passes.size)`. Sa charge est `max(4096, descriptor encodé + payload canonique checked-I64)`
et elle demeure due pour chaque owner même cache warm. C'est une réserve logique
exacte du budget publié, non une prétention de taille byte-exacte des objets
opaques shader/pipeline du driver ; cette taille physique reste non observable
par l'API et constitue la limite/risque documenté de cette tranche.

L'audit manuel ne trouve aucune conversion de bounds renderer-local ni création
post-freeze de pass/resource/ID sur la route W6d. `GPUPlanSurfaceRouter` rend
toute frame W6b/W6d-owned terminale plutôt que de la laisser rejoindre
`legacy()`. `GPUPreparedCompositeLowerer` et
`GPUPreparedSurfaceProductEntry` restent des références prepared historiques
W8 : aucun appel W6d n'y entre. Aucun test statique, mock, reflection, fake
device, GM, dashboard, render/reference/score ou suite Skia globale n'a été
ajouté.

| Selector | XML classe PASS/F/E/S | Gradle | Native / observation |
| --- | ---: | ---: | --- |
| `W6dAdvancedSamplingSurfacePixelTest` | 5/0/0/0 | 1 | 133/UNKNOWN |
| `W6dLightingSurfacePixelTest` | 29/0/0/0 | 1 | 133/UNKNOWN |
| `W6dPictureFilterSurfacePixelTest` | 18/0/0/0 | 1 | 133/UNKNOWN |
| `W6dRuntimeImageOpacitySurfacePixelTest` | 3/0/0/0 | 1 | 133/UNKNOWN |
| `W6dBackdropPreviousSurfacePixelTest` | 7/0/0/0 | 1 | 133/UNKNOWN |
| `W6dAdvancedRecoverySurfacePixelTest` | 5/0/0/0 | 1 | 133/UNKNOWN |
| `W6dPictureRuntimeEffectPictureTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6cSpatialDagPictureTest` | 3/0/0/0 | 1 | 133/UNKNOWN |
| `W6cComposeSurfaceTest` | 4/0/0/0 | 1 | 133/UNKNOWN |
| `W6cSpatialCacheRecoverySurfaceTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6bImageBlurSurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W6aInitWithPreviousSurfacePixelTest` | 8/0/0/0 | 1 | 133/UNKNOWN |
| `W6aLayerBudgetRecoverySurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W5hRuntimeEffectSurfacePixelTest` | 18/0/0/0 | 1 | 133/UNKNOWN |

Les 132 assertions XML de classe sont 132/0/0/0. Le XML de wrapper Gradle
rapporte l'exit natif 133 comme failure de processus ; il ne contredit pas ces
assertions mais rend chaque commande `Gradle=1`. Cette santé native est
**UNKNOWN**, jamais GREEN natif (la même règle couvre 134). Les sept compiles
séquentiels `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`,
`:gpu-renderer`, `:kanvas:compileKotlin` et `:kanvas:compileTestKotlin` sortent
0. Aucune claim ISO ou convergence Skia globale n'est formulée.

La relecture Sol ciblée du dernier correctif W6c (`ae26bdbc49301f9ac6b962c766a39508231dd506`)
ne conserve aucun Critical/Important. La Draft PR W6c [#2405](https://github.com/ygdrasil-io/kanvas/pull/2405)
est empilée sur W6b [#2404](https://github.com/ygdrasil-io/kanvas/pull/2404), sans merge.
W6d Tasks 1–6 sont revues ; Task 7 attend seulement les deux gates contrôleur
ci-dessus. Les sorties natives 133 restent **UNKNOWN** malgré les assertions
XML et les compilations ci-dessous.

### Checkpoint W6d Task 4 — source Picture de filtre immuable

La branche `codex/w6d-advanced-effects` à `665242f1a` termine Task 4a–4c du
[plan détaillé](../../plans/2026-09-24-w6d-picture-filter-source-implementation-plan.md).
Un agrégat Picture à owner de filtre émet Begin → enfants W4/W5/W6 ordonnés →
Seal → `FilterPass.Picture` dans le graphe W6 gelé, sans faux draw, second
graphe, replay renderer ni texture de taille zéro. Les contextes distincts et
partagés, crop `src`, Picture vide sous Compose dans les deux ordres, clips
exacts/refus explicites, layers imbriqués et halo ont des témoins pixels publics.
La capture et le wire préservent les mutations ultérieures de `src`/cull, les
identités de filtres et un vrai fixture Picture 14/schema 8 non-lighting issu
du writer historique `e5ce423ab9d18987610b5b7021dbb02745f5e729`.
Un refus tardif garde le buffer de lecture intact et récupère sur la même
`Surface`. Les reviews Sol des trois tranches et la relecture finale corrigée
ne gardent aucun Critical/Important.

Les compilations ciblées `:render-ir`, `:gpu-plan`, `:gpu-renderer` et
`:kanvas:compileTestKotlin` sortent 0. Derniers XML rapportés :
`W6dPictureFilterSurfacePixelTest` 18/0/0/0,
`W6bImageBlurSurfacePixelTest` 10/0/0/0,
`W6dPictureRuntimeEffectPictureTest` 4/0/0/0,
`W6dLightingPictureTest` 3/0/0/0 et trois sélecteurs de préservation W6a
1/0/0/0 chacun. Le contrôleur a relancé indépendamment les témoins du clip
total, du filtre partagé, du refus atomique, du fixture v14, du mapping inline
H+T et du Compose extérieur vide : XML 1/0/0/0 à chaque fois. Chaque worker
sort ensuite en 133 ; le statut process/native est **UNKNOWN**, sans claim ISO
ou convergence globale. La classe W6a Picture complète possède déjà une
assertion de version obsolète (14 attendu, writer courant 15), distincte de
Task 4. La borne numérique checked-I64 B/B−1 reste à Task 7. Aucune PR W6d
n'est encore ouverte ; Task 5 est le prochain lot.

### Quatrième correction W6c — conservation du domaine imbriqué chevauchant

La relecture Sol de `b09ed9d145c079f6351b6823dd31ca84708477e7` a retenu un
dernier variant Important : le bornage d'un `Tile` imbriqué encore chevauchant
pouvait tronquer la demande nécessaire à son parent `Compose`. La correction
`164b8ad2c518431ba74bfa512f558a21bc1f6cb2` réserve donc strictement le
bornage par consumer (et le texel no-op) au root terminal de l'occurrence. Les
nœuds non-root `Crop`/`Tile`/`Offset` conservent leur domaine public complet ;
le root garde le bornage et le no-op scellé déjà qualifiés. Cette règle ferme
également le cas `Compose(Offset(-500), Tile([0,1), [0,501)))`, qui doit
produire le bleu et non une transparence tronquée.

Le RED public isolé était ce witness chevauchant, transparent avant la
correction, ainsi que sa variante `[0,1e9)` qui ne refusait plus au budget. Le
GREEN XML frais `W6cSpatialBoundsSurfaceTest` est 15/0/0/0 : le petit witness
lit le bleu, le grand refuse avant mutation avec
`w6b.filter.frame_budget_exceeded`, préserve le sentinel et recouvre sur la
même `Surface`. Les compilations `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin` et `:kanvas:compileTestKotlin` sortent 0. Les
préservations console Compose 4/4 et Picture 3/3 passent, mais leurs workers
natives quittent ensuite en 133 : elles restent **UNKNOWN**, sans claim
native, ISO ou globale. La conclusion antérieure de conservation complète des
nœuds imbriqués est donc remplacée par cette fermeture explicite du variant
chevauchant.

### Troisième correction W6c — fermeture nested bounds et clip terminal complexe

La relecture Sol de `59feb52bb2750d94fd7394ce933699876a994e71` a identifié deux
variants Important, fermés par `b09ed9d145c079f6351b6823dd31ca84708477e7`.
Le texel no-op Crop/Tile est désormais réservé strictement à l'ID root de
l'occurrence : un nœud interne conserve son domaine sémantique et ne peut plus
devenir transparent silencieusement sous `Compose`/`Merge`/`Blend`. Le cas
imbriqué immense refuse avant publication avec le budget W6b et conserve le
sentinel. Propager une demande contextuelle précise par nœud jusqu'aux
allocations intermédiaires reste un gap explicitement reporté à W6e.

Une route directe filtrée à `ClipStackNode.Operations` refuse désormais avant
l'allocation de source avec `w6b.filter.direct_terminal_clip`; aucune AABB ou
approximation de clip n'est admise. Les routes W4e complexes non filtrées ne
sont pas modifiées. Le shard public frais `W6cSpatialBoundsSurfaceTest` donne
13/0/0/0 XML, y compris Compose positif, refus budget/sentinel/recovery et
refus de clipPath/sentinel/recovery. Native133 reste **UNKNOWN**, sans claim
native, ISO ou globale.

### Seconde correction W6c — variants hors domaine de la relecture Sol

La relecture suivante du correctif `2ffdae4afaef0780e2f47bb7a091c1b09f9fd49e`
n'a retenu que deux variants hors domaine, fermés par
`59feb52bb2750d94fd7394ce933699876a994e71`. Un draw direct filtré reçoit
maintenant l'autorité du clip terminal Canvas (et non le scissor de sa source),
puis publie le même no-op scellé `Draw`/scissor nul que les Layers. Les
Crop/Tile entièrement disjoints de leur consumer publient un texel terminal
borné plutôt que le rectangle public immense ; son terminal est le no-op
scellé. Construction, validation et materializer consomment tous ce fait sans
fallback renderer.

Le shard public frais `W6cSpatialBoundsSurfaceTest` donne 10/0/0/0 XML, dont
le draw direct Offset hors clip et Crop/Tile disjoints jusqu'à 1e9, chacun avec
sentinel et recovery même `Surface`. Les compilations
`:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0. Gradle quitte ensuite sur native133 :
**UNKNOWN**, sans claim native, ISO ou globale.

### Correction whole-branch W6c après review Sol

La correction bornée `2ffdae4afaef0780e2f47bb7a091c1b09f9fd49e`, sur la source
`cb93adb28f076636e6d26a254ed91a85a2b6387b`, ferme les cinq constats de la
review whole-branch : reverse demand externe pour `ColorFilter`/`Compose`/
`Merge`/`Blend`, terminal `Layer` scellé no-op lorsque le clip est vide,
allocation `Crop`/`Tile` bornée par le consumer, limites W5f pré-publication
des uniforms `ColorFilter`, et retrait de `CopySrc` du cache spatial sans
consumer. La décision no-op est portée par le `FilterCompositeOperationV1.Layer`
gelé (scissor nul), et partagée par construction, validation et materializer.

La vérification publique ciblée sur cette source donne 15/0/0/0 XML :
`W6cSpatialBoundsSurfaceTest` 8/0/0/0, `W6cComposeSurfaceTest` 4/0/0/0 et
`W6cSpatialDagPictureTest` 3/0/0/0. Les compilations
`:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0. Le worker natif quitte encore en 133
après les assertions JUnit ; cette observation reste **UNKNOWN**, sans claim
native, ISO ou globale.

Les préservations relancées séparément donnent aussi des XML frais
`W6aLayerSurfacePixelTest` 16/0/0/0, `W6bFilterPictureTest` 12/0/0/0 et
`W6bFilterAdmissionRecoverySurfaceTest` 27/0/0/0. Le sélecteur W5f a atteint
des assertions console PASS puis a quitté en 133 avant d'écrire son XML : il
reste **UNKNOWN** et n'entre dans aucun total de cette correction.

## Checkpoint W6c — DAG spatial principal, qualification Task 7

La branche d'implémentation est `codex/w6c-spatial-dag`. Sa base W6b revue est
`b6412bc161ccb99f1361886ec80a9d96c0f3637f`; la source qualifiée avant le
commit documentaire est `23b7a82b029a8f1668a4dea58848bc4514e0e58e`.
Les bornes de commits sont : Task 1
`b6412bc161ccb99f1361886ec80a9d96c0f3637f..f3478f13ce8172373c7221f614b394dab2b56a6e`,
Task 2 `f3478f13ce8172373c7221f614b394dab2b56a6e..a0cb854e8b1ec564e7adf7b78bf85efa16dcc99c`,
Task 3 `a0cb854e8b1ec564e7adf7b78bf85efa16dcc99c..b51da58b0936fcece89c6e8a7ee81092992b8a8c`,
Task 4 `b51da58b0936fcece89c6e8a7ee81092992b8a8c..fb648006853bfacd18e61a1675269346cb74910a`,
Task 5 `fb648006853bfacd18e61a1675269346cb74910a..a61db01b06f7f795f3c48c77c40ce33e7ab0eb43`,
et Task 6 `a61db01b06f7f795f3c48c77c40ce33e7ab0eb43..23b7a82b029a8f1668a4dea58848bc4514e0e58e`.

Les neuf familles admises sont `Crop`, `Offset`, `Tile`, `ColorFilter`,
`Compose`, `Merge`, `Blend`, `Dilate` et `Erode`. Elles passent toutes par la
table capturée W6b, la clé d'occurrence contextuelle, les quatre régions F64
projetées une fois en I32, puis les mêmes `FilterPass`/`FilterTarget` gelés.
`Compose` lie inner puis outer; `Merge`/`Blend` gardent l'ordre et les doublons;
`ColorFilter` et `Blend` réemploient respectivement les autorités W5f et W5.
Le cache spatial conserve les faits sémantiques/générations, reste budgeté à
froid comme à chaud et lease ses ressources jusqu'à completion ou quarantine.

### Custody de convergence

Les commandes ont été exécutées séquentiellement depuis cette worktree. Les
XML sont dans `kanvas/build/test-results/test/`. Sauf exception explicitement
notée, `Gradle=1` est exclusivement l'exit du worker natif `133` après la
clôture JUnit : il est **UNKNOWN**, jamais GREEN natif.

| Selector | XML PASS/F/E/S | Gradle | Native / observation |
| --- | ---: | ---: | --- |
| `W6cSpatialDagAdmissionSurfaceTest` | 2/0/0/0 | 1 | 133/UNKNOWN |
| `W6cSpatialBoundsSurfaceTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6cComposeSurfaceTest` | 3/0/0/0 | 1 | 133/UNKNOWN |
| `W6cMultiInputSurfaceTest` | 5/0/0/0 | 1 | 133/UNKNOWN |
| `W6cMorphologySurfaceTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6cSpatialCacheRecoverySurfaceTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6cSpatialDagPictureTest` | 2/0/0/0 | 1 | 133/UNKNOWN |
| `W6aLayerSurfacePixelTest` | 16/0/0/0 | 1 | 133/UNKNOWN |
| `W6aLayerBoundsSurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W6aLayerBudgetRecoverySurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W5fColorFilterSurfacePixelTest` | 44/0/0/0 | 1 | 133/UNKNOWN |
| `W5bBlendSurfacePixelTest` | 50/0/0/0 | 0 | `FROM-CACHE`; aucun nouveau worker natif observé |
| `W6bFilterPictureTest` | 12/0/0/0 | 1 | 133/UNKNOWN |
| `W6bFilterAdmissionRecoverySurfaceTest` | 27/0/0/0 | 1 | 133/UNKNOWN |
| `W6bImageBlurSurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W6bMaskBlurAutoLayerSurfacePixelTest` | 10/0/0/0 | 1 | 133/UNKNOWN |
| `W6bMaskShaderTableSurfacePixelTest` | 13/0/0/0 | 1 | 133/UNKNOWN |
| `W6bDropShadowSurfacePixelTest` | 6/0/0/0 | 1 | 133/UNKNOWN |
| `W6bBudgetRecoverySurfacePixelTest` | 2/0/0/0 | 1 | 133/UNKNOWN |

Les sept shards W6c font 30/0/0/0; les contrôles W6a/W5/W6b listés font
210/0/0/0. Les compilations séquentielles
`:math:geometry:compileKotlinJvm`, `:math:matrix:compileKotlinJvm`,
`:render-ir:compileKotlin`, `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin`, `:kanvas:compileKotlin` et
`:kanvas:compileTestKotlin` sortent toutes 0. Aucun nouveau failure/error JUnit
ni échec de compilation n'a été observé. L'exit `134` fait partie de la règle
de custody **UNKNOWN**, mais n'a pas été observé dans cette passe finale.

### Audit d'autorité W6c et limites connues

L'audit de production, sans test statique ajouté, trouve les créations
`PlanPass`/`PlanResource` dans `W6bFilterGraphConstruction` et
`W6aLayerGraphConstruction`, donc avant gel/publication. Le materializer W6
lit le graphe, les resources, les origins et les samplings gelés; il ne crée ni
pass/resource de plan ni conversion renderer-local device→target ou F64→I32.
Ses `outputExtent` et scissor sont les valeurs de resources/payloads déjà
scellées. `GPUPlanSurfaceRouter` terminalise toute frame W6b-owned : elle ne
peut rejoindre `legacy()` après admission.

`GPUImageFilterPlan`, `GPUMorphology`, `GPUFilterTile`,
`copyTargetToOffscreenTexture` et `GPUPreparedCompositeLowerer` existent encore
dans les chemins prepared/filters historiques. Ils ne sont pas référencés par
la route W6c `FilterPass` du materializer et restent un risque explicite de
retrait legacy W8, non une autorité ou fallback W6c.

La preuve publique couvre mutations, replay mémoire/wire, bounds/origins/clips,
B/B−1, refus terminal/sentinel et recovery sur la même `Surface`. Elle ne peut
pas observer directement le nombre de `FilterPass` supprimés sur un cache hit,
ni créer sans infrastructure une soumission GPU concurrente : cette limite
d'observation de cache est documentée et l'owner/leases gelés restent objet de
review de code. La rotation est également une lacune de couverture antérieure
à W6c : les routes publiques `saveLayer`/`drawPicture` sont refusées en amont
par `w6a.layer.unsupported_child`; W6c ne promeut pas W4/W5 pour fabriquer ce
témoin.

Restent exclus : backdrop, `initWithPrevious` filtré, Picture/runtime image
filters, displacement, convolution, magnifier, lighting, F16/HDR, fonts,
codecs, GMs, dashboard/renders/références/baselines/scores/rebaseline, Skia
global et `jpg-color-cube`. Ce checkpoint ne formule aucune claim ISO ou
globale; la santé native demeure **UNKNOWN**.

À la date de ce checkpoint Task 7 initial, les gates encore ouverts étaient la
review Sol whole-branch contre la base W6b exacte, puis la Draft PR empilée
vers `codex/w6b-blur-masks-shadows`. Les corrections, relectures et la PR
ultérieures sont consignées en tête du présent document.

## Checkpoint W6b / correction whole-branch round 4 — autorité producteur scissor Picture

La première vague de correction, basée sur `2bf3d52`, a fermé I2–I4, I6,
M1 et M2. Sa re-review a laissé I1, I5 et I7 ouverts. La seconde vague,
basée sur `a1aabe6`, a fermé R21, I5 et le shard Picture I7, mais sa re-review
a laissé l'authentification indépendante du scissor terminal Picture et deux
imports Render IR dans le shard admission. La troisième vague, basée sur
`6453b06`, a ajouté les mutants de validation R22, mais sa re-review a
conservé un finding : `PictureTerminalScissorAuthorityV1` copiait encore le
terminal/les operands déjà construits. La quatrième vague, basée sur
`b37bf29`, ferme seulement R23 sans étendre les exclusions W6b : l'autorité
est créée directement depuis le clip différé, le domaine composite admis et la
décision vide/non-vide, avant tout terminal, operands ou pass. Ceux-ci
reçoivent ensuite une copie du même fait immutable ; aucun ne peut plus le
produire.

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
  `:gpu-plan:test` (102 contrats), `:gpu-renderer:compileKotlin` et
  `:kanvas:compileTestKotlin` sortent 0. Aucun shard public W6b n'importe
  `GraphLimits` ni `SceneCaptureLimits` : les archives oversize et leur
  recovery passent par `Picture` public et bytes.

Chaque shard GPU a ensuite reçu exit natif 133 après ses assertions. Cet état
reste **UNKNOWN**, sans attribution au changement W6b, au test ou à
l'environnement; il n'est pas compté comme GREEN natif.

## Audit Task 6

La projection W6b passe seulement par `GPUW6aLayerFramePlan`,
`GPUW6aEncoderScopesV1` et `GPUWgpu4kW6aLayerFramePayloadMaterializer`.
Le plan scelle, avec math checked, chaque scissor, sample rectangle et offset
W6b en I32 target-local. Chaque `FilterComposite` publie aussi son offset
d'échantillonnage et son scissor terminaux. L'aggregate Picture porte un
snapshot d'admission distinct (scissor optionnel, admis et vide/non-vide),
produit par `admitPictureTerminalScissor` directement depuis les inputs
d'admission, avant les operands et les pass. La validation compare ce fait au
terminal et au `FilterComposite`, qui en sont tous deux des consommateurs ;
elle refuse un sous-rectangle contenu mais faux et un `null` forgé. Le contrat
exerce aussi un vrai compilateur W6a sur une Picture filtrée, et une mutation
temporaire du helper producteur échoue. Draw/Layer/Picture/GraphTexture
consomment les operands verbatim, sans recalcul device→target.
Les mutants de publication offset/scissor et le cas
public Picture à origin F32 très grande mais I32 target-local rendable couvrent
cette frontière, y compris la recovery de la même surface. Aucun operand/pass W6b ne consulte
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

## Checkpoint prérequis W6e — recette de bounds contextuelle, Task 3

Sur `820710118`, les nouveaux témoins publics `W6FilterBoundsRecipe*` restent
sans modification d'owner. Le Crop sous clip 2×1 dérive en I64 checked
`B=312 = root 8 + quatre targets 1×1 (16) + deux rows W6 (32) + readback
aligné 256`; B produit exactement le pixel bleu, tandis que B−1 refuse avec
`w6b.filter.frame_budget_exceeded`, ne modifie pas le sentinel et le même
`Surface` redevient enregistrable. Le ColorFilter identité a son propre seuil,
calculé avant `Surface` : `B=392 = root 8 + quatre targets 1×1 (16) + deux
rows W6 (32) + matrice 20×F32 (80) + readback aligné 256`; B/B−1 vérifient les
mêmes pixels exacts, diagnostic, sentinel et recovery. Le replay Picture
inclut son target direct supplémentaire : `B=316 = root 8 + cinq targets 1×1
(20) + 32 + 256`; les replays froid/chaud du même Picture passent à B et
refusent à B−1, puis chacun discard/re-record sur sa propre `Surface` et
vérifie pixels exacts avec Render+Readback. Le témoin de snapshot emploie une
matrice dépendante du parent qui mappe son rouge vers vert et un enfant noir
semi-transparent : le même pixel enfant conserve une contribution backdrop
visible; backdrop au save est `[177,134,76,255, 177,180,76,255]`, alors que le
contre-factuel snapshot tardif contaminé par l'enfant a vert 100 au premier
pixel. `initWithPrevious` reste vérifié après l'enfant avec
`[212,45,92,255, 241,53,106,255]`, puis les deux surfaces recover après discard.

Les XML frais des deux nouvelles classes sont `10/0/0/0` (Surface) et
`3/0/0/0` (Picture). Chaque invocation Gradle quitte néanmoins avec le worker
natif 133 après JUnit : statut natif **UNKNOWN**, jamais PASS global. La
compilation ciblée `:gpu-plan:compileKotlin :kanvas:compileTestKotlin` sort 0.

La réexécution systématique a établi trois causes distinctes, toutes corrigées
ou contractualisées explicitement. `W6bBudgetRecoverySurfacePixelTest` est
revenu à 2/0 : un Picture aggregate réévaluait `DropShadow` avec son domaine
source inverse comme `desiredOutput`, ce qui annulait sa production translatée;
le recipe conserve désormais cette demande aval. `W6bFilterAdmissionRecoverySurfaceTest`
est à 26/0 : un `DeviceRect` vide est reconnu avant toute projection du cull
à travers une matrice singulière. `W6cSpatialDagAdmissionSurfaceTest` est à
2/0 : son ancien refus `unsupported_family` était obsolète puisque Magnifier
est une famille W6d admise; le témoin public vérifie désormais le pixel bleu,
Render+Readback et la recovery après discard/re-record.

Les selectors prescrits sont tous verts en XML : nouveaux Surface 10 et
Picture 3; W6a budget 10; W6b budget 2 et filter admission 26; W6c DAG 2 et
cache 6; W6d advanced 5, backdrop/previous 9, Magnifier 4 et Picture runtime
6. Chaque invocation de test termine ensuite avec le worker natif 133 : ces
résultats natifs restent **UNKNOWN**, jamais PASS global. Les compilations
`:gpu-plan:compileKotlin :kanvas:compileTestKotlin` terminent 0.

Correction de la review Sol Task 3 : le premier run du nouveau témoin Surface
était `8/2/0/0` (identity à 312 au lieu de son B 392, et oracle backdrop avec
quantification intermédiaire erronée), puis la correction d'arithmétique et de
l'oracle est `10/0/0/0`; Picture reste `3/0/0/0`. Les selectors de préservation
rejoués sont `W6bBudgetRecoverySurfacePixelTest` `2/0/0/0` et
`W6dBackdropPreviousSurfacePixelTest` `9/0/0/0`. Chacune de ces invocations
termine ensuite par worker 133, donc native **UNKNOWN**; aucun owner de
production n'a changé pour cette correction.

Correction round 2 de la review Sol : l'ancien enfant opaque masquait backdrop
au pixel commun; le nouveau témoin le rend observablement causal avec l'enfant
noir à alpha .5 et un contre-factuel algébrique disjoint calculé avant Surface.
Le selector `W6FilterBoundsRecipeSurfacePixelTest` est `10/0/0/0` et
`W6dBackdropPreviousSurfacePixelTest` `9/0/0/0`; chaque Gradle se termine après
JUnit par worker 133, native **UNKNOWN**. Aucun owner production n'a été modifié.

Exclusions Task 3 : aucun GM, font, codec/format externe, dashboard/render,
rebaseline, suite Skia globale, `jpg-color-cube` ou test d'infrastructure. Le
worker 133 reste la limite native explicite, classée **UNKNOWN**.

## Gate final du prérequis W6e — recette de bounds contextuelle

La review Sol whole-branch a demandé trois corrections : sortie d'un Picture
top-level avant réservation du parent, source physique finie d'un draw filtré
dans Picture, et exclusion d'un restore `DST` non écrivant. La re-review Sol
les a validées, puis a identifié un cas adjacent : un draw filtré `DST` dans
une Picture inline pouvait être disjoint du parent désormais resserré et
échouer en `w6b.filter.invalid_bounds`. Le témoin public
`inlineFilteredDstOutsideWritingSiblingIsNoOpAndRecovers` a reproduit ce RED
avant le correctif `202047bd9`. L'entrée Picture reste désormais dans le
stream comme entrée élidée, sans composite physique ; la seconde re-review Sol
ciblée accepte ce correctif sans nouveau finding Critical ou Important.

Sur `202047bd9`, une vérification fraîche et sérialisée donne 8/8 JUnit
pour `W6FilterBoundsRecipePictureTest`, 12/12 pour
`W6FilterBoundsRecipeSurfacePixelTest`, 3/3 pour
`W6cSpatialDagPictureTest` et 20/20 pour
`W6dPictureFilterSurfacePixelTest`. Les deux compilations
`:gpu-plan:compileKotlin` et `:kanvas:compileTestKotlin` sortent 0 ;
`git diff --check` depuis W6d est propre. Chacun des quatre sélecteurs de
test termine cependant avec un worker natif 133 après les assertions :
**43/43 JUnit ciblés verts, statut global natif UNKNOWN**. La matrice plus
large avant ce dernier correctif comptait 191/193 JUnit verts ; ses deux
échecs connus sont le masque RRect et une assertion de test Picture14/schema8
devenue obsolète face au writer Picture15/schema9 déjà présent sur W6d. Ce
checkpoint ne prétend pas que cette matrice entière a été rejouée sur le
dernier commit.

La branche prérequis est `codex/w6e-filter-bounds-recipe`, stackée sur
`codex/w6d-advanced-effects`. Les exclusions précédentes restent inchangées :
aucun test d'infrastructure, GM, dashboard/render/rebaseline,
`jpg-color-cube`, font ou codec externe n'a été exécuté. Aucun merge ni claim
ISO n'est déduit de ces résultats.

### Audit 2A0b.IIIb1 — branche directe PictureComposite inatteignable

La tentative de recette pour un `PictureComposite` actif sans
`graphTextureOperand` a été annulée par le revert `50e271db6` du commit
`6f95c9c57`. La review Sol a tracé l'unique construction
`PlanPass.PictureComposite(...)` : elle crée d'abord une
`GraphTextureSourceRequestV1`, publie le `PictureSourcePass` sur le même
`source.resourceId`, puis attache l'operand à la publication. Les témoins
Picture publics parcourent donc la variante graph IIIb2, pas IIIb1 ; leur
XML vert `21/0/0/0` ne prouvait pas IIIb1. Aucun changement IIIb1 ne demeure
dans la production ; IIIb2 doit geler la vraie variante active et retirer ou
refuser le fallback direct tardif. Cette preuve de reachability ne ferme ni
IIIb2, ni III, ni le budget/lease/2B.

### 2A0b.IIIb2a — PictureComposite graph-texture simple

`W6PictureCompositeGraphRecipeV1` scelle uniquement le terminal actif dont
`PictureSourcePass.graphTextureOperand` est présent, sans color filter ni
destination-read. Elle encode owner/ordinal, source composite et source graph
scellée avec génération, alpha, blend, formats/extents physiques, bounds,
offset, scissor, load/store, ABI texture-only et draw fullscreen. Le scissor
nul demeure la recette `Empty` existante.

La recette entre dans le catalogue et `PlanPhysicalLayoutV1`. Avant toute
allocation, le préflight reconstruit la recette et compare ressources physiques
source/destination ainsi que le `GPUFrameResourceUse` enregistré. Le renderer
relit offset/alpha/blend depuis la recette et authentifie le bridge graph. Les
futures variantes color filter ou destination snapshot restent explicitement
hors de cette tranche (IIIb2b).

Vérification : `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0. `W6dPictureFilterSurfacePixelTest`
rapporte 21 tests JUnit passés (pixels et scopes `Render` + `Readback`), puis
le worker natif termine 133 : native **UNKNOWN**, non assimilée à un succès
Gradle. Aucun budget, lease ou authentification 2B n'est revendiqué.

### 2A0b.IIIb2b1 — PictureComposite graph-texture filtré sans snapshot

Le `DrawPicture` muni d'un `colorFilter` est admis sur la route W6 lorsqu'un
sibling `saveLayer` non vide établit l'ownership de la frame ; le seul test
top-level suivait la continuation legacy et son refus `unsupported.composite.paint`
n'était pas une preuve d'inaccessibilité. Un témoin public de cette route fixe
l'attendu avant `PictureRecorder`/`Surface`, puis vérifie pixels et scopes
`Render` + `Readback`.

`W6PictureCompositeGraphFilteredRecipeV1` scelle le graph operand, l'identité
W5f, la fenêtre uniforme, les extents et le draw. La préparation enregistre
source et uniforme ; le préflight les authentifie avant allocation ; le
renderer traduit la recette via un helper dédié. La review Sol du commit
`58cd9e4` a relevé un fallback encore admissible sans recette filtrée ; le
correctif `88261b4` le réserve au seul destination-read futur, et sa relecture
est **Approved**. Les trois compilations ciblées sortent 0 ; le XML public
`W6dPictureFilterSurfacePixelTest` est `22/0/0/0`. Le worker natif sort 133 :
**UNKNOWN**. IIIb2b2, IIIc, budget/leases et 2B restent ouverts.

### 2A0b.IIIb2b2 — PictureComposite graph-texture avec snapshot destination

Les terminaux `PictureComposite` actifs à graph-texture dont le blend est
`BlendPlan.DestinationReadV1` sont maintenant catalogués avant toute
allocation. La forme sans filtre est une recette dédiée
`W6PictureCompositeGraphDestinationRecipeV1` : source graph scellée et sa
génération, snapshot/destination versionnés, alpha, formule de blend,
descriptions source/cible/snapshot, offset, scissor, `Load`/`Store`, ABI
texture+snapshot et fullscreen draw entrent dans son encodage canonique.

La forme filtrée conserve la recette graph filtrée, mais sélectionne l'ABI
distincte `SourceTextureThenColorFilterUniformThenDestinationSnapshot`; son
uniform W5f, son offset et le snapshot sont enregistrés et préflightés dans
l'ordre source, uniform, snapshot. Le renderer est catalog-first pour ces
deux formes : une composite active sans recette est refusée, et aucun fallback
destination-read n'est conservé.

Les témoins publics `layerOwnedDrawPictureDifferenceReadsDestinationSnapshot`
et `layerOwnedDrawPictureFilteredDifferenceReadsDestinationSnapshot` fixent
respectivement le parent bleu/source rouge `DIFFERENCE` et la variante W5f
verte, avant `PictureRecorder`/`Surface`; ils vérifient pixels et scopes
`Render` + `Readback`. Les deux assertions JUnit passent; le worker natif
termine 133, donc le statut natif reste **UNKNOWN**. IIIc, leases/budget et
2B restent ouverts.

Le commit `ab7f063` a reçu une relecture Sol **Approved** sans défaut
Critical/Important. IIIb2 est review-clean ; IIIa2 attend une autorisation
explicite et IIIc ainsi que les gates ultérieurs restent ouverts.

### 2A0b.IIIc1 — FilterComposite.Draw actif

Le commit `e417bd7` fige le composite `Draw` actif dans
`W6FilterCompositeDrawRecipeV1` : descriptions physiques source/cible,
bounds, origine, offset, scissor, blend, `Load`/`Store`, shader, ABI texture et
draw fullscreen sont encodés canoniquement. Le catalogue choisit la recette
active ou la recette `Empty` déjà scellée pour un no-op ; la route active sans
recette est refusée. Le préflight confronte recette, ressources physiques et
usage enregistré avant toute allocation, puis le renderer traduit la recette
dans un helper dédié. `Layer` et `Picture` ne sont pas revendiqués ici.

Le témoin public `directImageColorFilterSrcCompositeReplacesOpaqueParent`
fixe le pixel luma attendu avant `Surface`, puis vérifie pixels et scopes
`Render` + `Readback` pour un blend `SRC` sur parent vert. Les trois
compilations ciblées réussissent ; `W6cComposeSurfaceTest` donne `5/0/0/0`
dans le XML, mais le worker natif sort 133 (**UNKNOWN**). Relecture Sol
**Approved**, aucun défaut Critical/Important. IIIc2/IIIc3, IIIa2,
W4e/W5a, leases/budget et 2B restent ouverts.

### 2A0b.IIIc2a1 — FilterComposite.Layer sans filtre de restore

Le commit `4737e09` fige le restore `FilterComposite.Layer` actif sans
`colorFilter` de restore ni lecture de destination dans une recette propre à
ce pass, distincte de `LayerComposite`. Elle porte source/cible et leurs
descriptions physiques, layer remplacée, versions parent, alpha/blend,
coordonnées, `Load`/`Store`, ABI texture seule et draw. Le catalogue et le
seal la réauthentifient ; le préflight confronte ressources et usage enregistré
avant allocation. Le renderer choisit depuis le catalogue `Empty` ou cette
recette, puis la traduit via un helper dédié. Le bridge sans recette reste
strictement réservé aux variantes W5f/destination-read des sous-lots suivants.

Deux témoins publics `saveLayer(imageFilter=...)` sont ajoutés avec attendu
fixé avant `Surface`, pixels et `Render` + `Readback` : seul celui sans filtre
de restore prouve IIIc2a1 ; l'autre garde la future variante W5f. Trois
compilations ciblées réussissent ; les XML de
`W6aLayerRestoreSurfacePixelTest` et `W6cComposeSurfaceTest` sont
respectivement `11/0/0/0` et `5/0/0/0`. L'exécuteur natif sort 133
(**UNKNOWN**). Relecture Sol **Approved**, aucun Critical/Important.
IIIc2a2, IIIc2b, IIIc3, IIIa2, W4e/W5a, budget/leases et 2B restent ouverts.

### 2A0b.IIIc2a2 — FilterComposite.Layer avec W5f, sans snapshot

Le commit `f2330b6` ajoute une recette distincte de IIIc2a1 pour le
`colorFilter` de restore. L'identité W5f, la ressource `UniformData`, sa
fenêtre offset/capacité, la description physique, le shader, l'ABI
texture+uniform et le draw sont gelés et authentifiés. La préparation inclut
explicitement l'uniform ; le pass enregistre dans l'ordre source puis uniform,
et le préflight compare cet ordre et la fenêtre avant toute allocation.
Le renderer sélectionne la variante depuis le catalogue et utilise un helper
W5f dédié ; le bridge sans recette ne reste autorisé que pour
`destination-read` de IIIc2b.

Le témoin public avec `saveLayer(imageFilter=..., colorFilter=...)` fixe
l'attendu avant `Surface`, puis vérifie pixels et `Render` + `Readback`.
Trois compilations ciblées réussissent ; les deux classes publiques ciblées
produisent `11/0/0/0` et `5/0/0/0` dans leurs XML. L'exécuteur natif sort
133 (**UNKNOWN**). La relecture Sol est **Approved**, sans défaut
Critical/Important. IIIc2a est review-clean ; IIIc2b, IIIc3, IIIa2 et les
gates W4e/W5a, budget/leases et 2B restent ouverts.

### 2A0b.IIIc2b1 — FilterComposite.Layer destination-read sans W5f

Le commit `b8c93fa` ajoute une recette distincte pour le restore filtré qui
lit un snapshot de destination, sans `colorFilter` de restore. Elle sépare
source filtrée et layer remplacée, lie snapshot et version du parent, et
scelle descriptions physiques, alpha/blend, coordonnées, ABI texture source
puis snapshot et draw. La préparation enregistre ces deux usages dans cet
ordre. Avant allocation, le préflight vérifie la copie du parent vers le
snapshot antérieure au restore, la version requise et les usages enregistrés.
Le renderer traduit la recette cataloguée dans un helper dédié ; seul le cas
combiné W5f+snapshot conserve temporairement un bridge sans recette.

Le témoin public `saveLayer(imageFilter=...)` avec parent bleu et
`DIFFERENCE` fixe magenta avant `Surface` et vérifie pixels, `Render` et
`Readback`. Le témoin cyan garde la combinaison W5f future, sans servir de
preuve de b1. Trois compilations ciblées réussissent ; les deux classes
publiques donnent `13/0/0/0` et `5/0/0/0` en XML. L'exécuteur natif sort 133
(**UNKNOWN**). Relecture Sol **Approved**, aucun Critical/Important.
IIIc2b2, IIIc3, IIIa2 et les gates W4e/W5a, budget/leases et 2B restent
ouverts.

### 2A0b.IIIc2b2 — FilterComposite.Layer W5f avec snapshot

Le commit `cfe7eda` fige la dernière variante active de
`FilterComposite.Layer` dans une recette propre : identité et fenêtre W5f,
snapshot/version du parent, ressources physiques, alpha/blend, coordonnées,
ABI source→uniform→snapshot et draw. L'uniform est préparé ; le pass enregistre
les trois usages dans cet ordre. Le préflight contrôle copie causale, fenêtre
uniforme et pass enregistré avant allocation. Le renderer sélectionne les
cinq formes `Layer` depuis le catalogue (`Empty` compris) sans bridge actif,
puis applique alpha→W5f→blend via un helper dédié.

Le témoin cyan public porte `imageFilter`, `colorFilter` de restore et
`DIFFERENCE`; l'attendu est fixé avant `Surface`, puis pixels, `Render` et
`Readback` sont contrôlés. Trois compilations ciblées réussissent ; les trois
classes publiques ciblées donnent `13/0/0/0`, `5/0/0/0` et `24/0/0/0`
dans leurs XML. L'exécuteur natif sort 133 (**UNKNOWN**). Relecture Sol
**Approved**, aucun Critical/Important. IIIc2 est review-clean ; IIIc3,
IIIa2 et les gates W4e/W5a, budget/leases et 2B restent ouverts.

### 2A0b.IIIc3a — FilterComposite.Picture direct

La recette typée texture-only, son catalogue/seal, les usages enregistrés,
le préflight avant allocation et la traduction native catalog-first couvrent
le terminal Picture direct. La provenance distingue explicitement l'absence de
`PictureSourcePass` d'un pass présent sans graph operand. Le témoin public
`filteredInnerPictureRestoresWithoutGraphOperand` fixe les pixels attendus
avant `PictureRecorder`/`Surface` et vérifie `Render` + `Readback`. Une revue Sol
a confirmé sa causalité, puis une autre a demandé le scellage complet des
facts du terminal ; la relecture ciblée de ce correctif ne relève plus de
défaut Critical/Important.

`:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` réussissent. Les premiers runs W6d échouaient
avant le plan avec `GPU runtime is unavailable` ; une instrumentation
temporaire (retirée) a identifié `NoSuchMethodError` sur `TextureDescriptor` :
le toolkit `wgpu4k` de juillet chargeait des `webgpu-ktypes*` de septembre.
Le commit `4dcbd9f` verrouille les quatre modules transitifs sur le build
compatible de juillet ; sa revue Sol est **Approved**. Sans init script,
le témoin IIIc3a donne `1/0/0/0`, puis W6d `25/0/0/0`, W6a restore
`13/0/0/0` et W6c compose `5/0/0/0` dans leurs XML ciblés. Chaque exécuteur
natif sort ensuite 133 (**UNKNOWN**). IIIc3a est commitée à `3e9e7a1` et la
relecture finale Sol est **Approved**, sans finding Critical/Important/Minor.
IIIc3b/c/d, IIIa2, les budgets et 2B ne sont pas clos.

IIIc3b est ensuite commitée dans `5c2d54f` puis corrigée par le témoin causal
de destination dans `9be726b`; la relecture Sol est **Approved**, sans finding.

### 2A0b.IIIc3b — FilterComposite.Picture direct destination-read

Le témoin public direct construit d'abord un parent vert
dans le `Picture`, puis un draw rouge avec `ImageFilter.ColorFilter(Blend bleu SRC)` et
`DIFFERENCE`; l'attendu cyan opaque, distinct du parent si le composite était
omis, est fixé avant `PictureRecorder`/`Surface`
et contrôle pixels, `Render` et `Readback`. La nouvelle recette distincte
scelle source/snapshot, provenance directe, versions, copie causale,
descriptions physiques, load/store, ABI b0+b2 et draw. Le renderer la lit
catalog-first, préflight les uses source/snapshot et la copie avant toute
allocation, puis utilise une traduction dédiée sans fallback direct.
Une première version du témoin était non causale : `Luma(red)` puis
`DIFFERENCE` laissait le même vert que l'absence de composite. L'oracle cyan
corrigé distingue maintenant l'exécution de son omission ; la sélection
gelée reste vérifiée structurellement, sans prétendre à un RED de l'ancien
fallback. Les trois compilations ciblées réussissent ; W6d redonne
`26/0/0/0` dans son XML après correction, puis
l'exécuteur natif sort 133 (**UNKNOWN**). IIIc3c/d, IIIa2, budgets et 2B
restent ouverts.

### 2A0b.IIIc3c1 — FilterComposite.Picture graph operand b0

Provenance confirmée : `Canvas.drawPicture` publie `DisplayOp.DrawPicture`,
capturé par `PictureStreamAggregateV1.build` comme filtre du root Picture ; la
construction publie ensuite `GraphTextureSourceRequestV1`/`appendStreamSource`
avant `FilterCompositeOperationV1.Picture`. Le témoin rouge-vs-bleu est donc
causal pour l'ABI graph b0, pas pour `PictureComposite`.

La recette planner distincte, le catalogue, le seal, les usages enregistrés et
le préflight avant la première allocation couvrent le seul binding b0
`pass.source`; `graphSealedSource` reste une provenance physique validée, déjà
consommée par `PictureSourcePass`. Le dispatch catalog-first et son helper
traduisent la recette sans choisir shader/layout depuis l'operand ; le bridge
temporaire conserve uniquement les variantes graph W5f/snapshot c2/d. Les trois
compilations ciblées réussissent ; le témoin causal donne `1/0/0/0` XML et la
classe W6d `27/0/0/0`, puis chaque worker natif sort 133 (**UNKNOWN**).
IIIc3c1 est revue Sol **Approved** à `8f3d62b`; IIIc3c2/d, IIIa2, budgets et 2B restent ouverts.

### 2A0b.IIIc3c2 — FilterComposite.Picture graph operand b0 + W5f b1

La recette distincte `GraphTextureThenColorFilterUniform`
porte l'identité d'exécution W5f, les bytes dynamiques, la ressource
`SourceUniformData` et sa fenêtre/offset; catalogue, seal/publication, usages
enregistrés, préflight physique et traduction catalog-first restent sur
`FilterComposite.Picture`, sans snapshot destination. Le témoin public fixe
avant `PictureRecorder`/`Surface` le jaune opaque de la source graph rouge avec
`Blend(vert, SCREEN)` et conserve le contrôle IIIc3c1 rouge sans W5f; les deux
vérifient `Render`+`Readback`. Un contre-factuel carrier bleu + SCREEN serait
cyan, mais le `drawPicture` direct est refusé par
`unsupported.surface.prepared.mixed-composite-topology`; il n'est ni présenté
comme preuve ni remplacé par `PictureComposite`.
Les trois compilations ciblées réussissent ; le témoin exact donne
`1/0/0/0` et la classe W6d complète `28/0/0/0` dans les XML. Gradle termine
sur l'exécuteur natif 133 (**UNKNOWN**), et non sur un échec JUnit.
IIIc3c2 est revue Sol **Approved** à `dab4281` ; IIIc3d, IIIa2, budgets et
2B restent ouverts.

### 2A0b.IIIc3d1 — FilterComposite.Picture graph operand b0 + destination snapshot b2

La variante externe sans W5f est maintenant une recette distincte
`W6FilterCompositePictureGraphDestinationRecipeV1`. Elle scelle le source
graph et sa génération, le snapshot de destination et sa version, les quatre
descriptions physiques source/cible/graph/snapshot, offset/scissor,
load/store, blend et l’ABI `GraphTextureThenDestinationSnapshot` (b0+b2).
Le catalogue, `PlanPhysicalLayoutV1`, les usages enregistrés, le préflight de
la `TextureCopy` causale avant le render et la traduction catalog-first la
transportent de bout en bout; aucun uniform W5f n’est accepté dans cette
variante. Le bridge W5f+snapshot reste exclusivement réservé à IIIc3d2.

Le témoin public
`externalPictureFilterDifferenceReadsDestinationSnapshotFromGraphOperand`
fixe le jaune avant `PictureRecorder`/`Surface`: source graph rouge
`DIFFERENCE` parent vert, le carrier bleu n’étant qu’un contre-factuel cyan.
Il passe avec les scopes `Render` et `Readback`. `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin` et `:kanvas:compileTestKotlin` sortent 0. Le
sélecteur public donne XML `1/0/0/0` et la classe W6d entière `29/0/0/0`,
puis l’exécuteur natif sort 133 :
**UNKNOWN**, non assimilé à un succès Gradle. La revue Sol de IIIc3d1 est
**Approved** à `47c8104` ; IIIc3d2, IIIa2, budgets et 2B restent ouverts.

### 2A0b.IIIc3d2 — FilterComposite.Picture graph operand b0 + W5f b1 + destination snapshot b2

La recette planner-owned `W6FilterCompositePictureGraphFilteredDestinationRecipeV1`
publie l’ABI ordonnée b0+b1+b2, le filtre W5f et sa fenêtre uniforme, le
graph source et le snapshot causal. Le renderer prépare et préflighte les
trois ressources avant allocation, puis traduit catalog-first l’ordre
alpha → W5f → blend destination. Le fallback actif `FilterComposite.Picture`
est retiré ; seul `Empty` reste admis pour un scissor nul. Le témoin public
fixe le blanc avant `PictureRecorder`/`Surface` : sans W5f, magenta ; sans
snapshot, jaune ; avec la carrier bleue au lieu du graph source, vert. Les
trois compilations ciblées réussissent. Le témoin donne XML `1/0/0/0`, W6d
entière `30/0/0/0`, puis l'exécuteur natif sort 133 (**UNKNOWN**). La revue
Sol de d2 est **Approved** à `e51302e`, sans finding Critical/Important/Minor.
IIIc3 est review-clean pour les six ABI Picture actives et `Empty` ; à ce
checkpoint IIIa2, W4e/W5a, budget/leases et 2B ne sont pas clos.

### 2A0b.IIIa2 — LayerComposite destination-read et gate composites

Le `LayerComposite` actif sans `imageFilter` possède deux recettes distinctes :
source texture + snapshot parent, puis source texture + uniforme W5f + snapshot
parent. Chacune scelle owner/ordinal, version destination,
bounds/origine/scissor, alpha/blend, formats/extents/samples, load/store, ABI
et draw. Le catalogue, le seal et les usages enregistrés suivent les deux
formes ; le préflight avant allocation vérifie le `TextureCopy` causal, son
ordre **et sa région exacte**, ainsi que les ressources physiques. Le renderer
traduit la recette sélectionnée et refuse l'ancien fallback destination-read
actif.

Le témoin public sans `imageFilter` fixe un parent rouge puis bleu et un child
rouge : le restore W5f vert suivi de `DIFFERENCE` doit donner cyan. Magenta
signale W5f omis, jaune un snapshot rouge périmé et vert l'ordre filtre/blend
inversé. Le témoin plain existant vérifie aussi `Render` et `Readback`. Les
commits `44d6718` et `21a3c02` ont reçu une revue Sol initiale puis une
relecture ciblée **Approved** ; l'unique finding Important sur la région
enregistrée est `ADDRESSED`.

Compilations séparées `:gpu-plan`, `:gpu-renderer` et
`:kanvas:compileTestKotlin` : exit 0 d'après le rapport Terra. Le contrôle
indépendant après correctif du sélecteur W6a donne XML `14/0/0/0`
(timestamp 2026-09-27T13:38:20Z), Gradle exit 1 après worker natif 133, donc
native **UNKNOWN**. W6c `6/0/0/0` et W6d `9/0/0/0` ont été rapportés par
Terra avant le correctif ciblé ; aucune suite globale n'est revendiquée.
IIIa1/IIIa2, IIIb2 et IIIc1–IIIc3 sont review-clean : **2A0b.III et 2A0b
sont clos pour le seul gel/sélection des recettes W6a**. 2A0c W4e, 2A0d
W5a, leases/B/B−1, 2B et le gate W6 global restent ouverts.

### Préservation W6 — uniformes filtrés dans les lanes W4e

Avant 2A0c.I1, deux témoins publics RRect échouaient pendant la publication
W6 avec `Collection contains more than one matching element`. La cause n'était
pas le GPU : quatre factories de recettes `LayerComposite` et
`FilterComposite.Layer` filtrées cherchaient une ressource par le seul rôle
`UniformData`, alors que les lanes W4e en portent aussi. Le commit `9f6f365`
sélectionne l'ID exact de l'uniforme de frame `UniformData:0` et authentifie
son type et ses usages. Les deux témoins RRect donnent XML `2/0/0/0`, la
classe publique restore `14/0/0/0`; leurs workers natifs sortent ensuite
133 (**UNKNOWN**). Revue indépendante Sol : **Approved**.

### 2A0c.I1 — ClipMaskProducer Rect/RRect sur la route W6

Les seuls producteurs analytiques finaux `Rect` et `RRect` ont une recette
versionnée avec géométrie F32 issue de `:math`, owner/packet/bundle, cible,
resolve, depth, U-slice et choix shader/ABI/load/store. Le catalogue, le
seal physique, la projection sur les packets, le préflight des ressources et
usages enregistrés avant allocation, puis la traduction native catalog-first
la transportent sans nouveau site pour `Path` ou `Empty`. `Empty` est éliminé
avant émission par le planner; la route W4d directe conserve son encodeur.
Une revue Sol a relevé l'absence initiale des paramètres depth/stencil pour
RRect AA; `3c58bb5` les fige dans la recette canonique et l'encodeur les
consomme. La relecture ciblée a marqué ce finding **ADDRESSED**, sans nouveau
Critical/Important. Implémentation initiale : `0f99d77`.

Les compilations ciblées `:gpu-plan`, `:gpu-renderer` et
`:kanvas:compileTestKotlin` sortent 0. La classe publique W6aLayerW4W5
donne XML `20/0/0/0`, mais Gradle sort 1 après worker natif 133 :
**UNKNOWN** global. Deux sélecteurs publics W4e directs, Rect/RRect/Path
ordonné et distinction des clips Rect hard/analytic, passent avec Gradle
exit 0. Une invocation plus large de `GPUPlanSurfacePixelTest` a produit
`F4/E0/S2` sur d'autres routes; son état de base n'a pas été établi et
aucune réussite de classe entière n'est revendiquée. I1 est review-clean ;
`ClipMaskFold`, producteurs/path phases, W5a, leases/B/B−1 et 2B restent
ouverts.

### 2A0c.I2 — ClipMaskFold sur la route W6

Chaque `ClipMaskFold` du binding W4e final dans W6 porte désormais une
recette versionnée : ordre `previous` puis `source`, cible `output`, opération
de combinaison, domaine I32 de `:math`, descriptions physiques, ABI à deux
textures, Clear/Store, couleur de clear et draw fullscreen. Le catalogue,
le seal, la projection packet et le préflight précèdent l'allocation native ;
la traduction choisit depuis cette recette pour W6, tandis que la route W4d
directe conserve son comportement. Le premier commit `8f717da` vérifiait
encore certains choix natifs en les émettant en dur; `ff75afd` traduit aussi
`load/store/clearColor` depuis la recette. Revue indépendante Sol :
**Approved**, aucun finding Critical/Important.

Les compilations `:gpu-plan`, `:gpu-renderer` et
`:kanvas:compileTestKotlin` sortent 0. La classe publique W6aLayerW4W5
donne XML `20/0/0/0`, et le sélecteur d'ordre Rect/RRect/Path `1/0/0/0`,
mais leurs exécuteurs natifs sortent 133 (**UNKNOWN**). Les deux sélecteurs
W4e directs pertinents passent avec Gradle exit 0. I1 et I2 sont
review-clean; Path producer, phases path, inverse-domain, W5a,
leases/B/B−1 et 2B restent ouverts.

### 2A0c.IIa — ClipMaskProducer.Path triangle direct sur la route W6

Le clip `Path` strictement triangulaire possède désormais une recette
versionnée pour son unique bundle W6 : owner/pass/packet/bundle, géométrie et
scissor de `:math`, cible/resolve/depth, ressources et slices V/I, fill rule,
inverse/AA 1× ou 4×, shader/topologie/ABI, stencil/blend et load/store/clear
depth-stencil. Le catalogue et le seal du layout authentifient cette recette ;
le packet W6 la conserve, puis le préflight compare avant `device.create*`
les rows physiques et usages enregistrés exacts, y compris le resolve 4×.
L'encodeur traduit les choix gelés pour W6 ; la route W4d directe garde son
fallback distinct. Aucune géométrie n'a été déplacée hors de `:math`.

Le témoin public d'un triangle translaté fixe ses pixels avant `Surface`,
distingue la forme de sa boîte englobante et vérifie `Render` + `Readback`.
Commits `2f7cd14`, `53f1aa8`, puis correctif `9ffe6d4`. Les trois
compilations ciblées sortent 0. Vérification indépendante sur `9ffe6d4` :
classe publique W6aLayerW4W5 XML `21/0/0/0`, worker natif 133 donc
**UNKNOWN** ; sélecteur W4e direct Rect/RRect/Path : Gradle exit 0 (la
dernière répétition contrôleur était `FROM-CACHE`). La review Sol initiale a
relevé deux findings Important — traduction native incomplète et préflight
AA/ressources inexact — et la relecture ciblée les marque **ADDRESSED**, sans
nouveau Critical/Important. IIa est review-clean ; IIb stencil-edge fan,
III/IV, 2A0d W5a, leases/B/B−1, 2B et gate W6 global restent ouverts.

### 2A0c.IIb1 — ClipMaskProducer.Path stencil-edge bundle 0

Le `Path` non triangulaire dont `:math` émet un stencil-edge fan gèle désormais
le premier des deux sites natifs de son packet : owner/pass/packet/bundle
ordinal 0, fill rule Winding/EvenOdd, fan/scissor, cible/resolve/depth et V/I,
AA/sample/inverse, stencil producer sans écriture couleur et paramètres de
pipeline/attachment. Le catalogue, le seal et la projection W6 précèdent un
préflight exact avant allocation : géométrie du packet réellement préparé,
rows physiques, V/I slice, usages enregistrés ordonnés et état D24S8. Le
pipeline edge est choisi depuis les axes typés ; le pipeline cover ordinal 1
reste provisoirement sur son chemin existant et n'a **pas** de recette IIb1.

Le témoin public concave en L, dans une layer translatée, fixe les pixels avant
`Surface`, distingue le fan de sa boîte englobante et vérifie `Render` et
`Readback`. Commits `e68d2cb`, `ddd534c`, `caf3735`, `6643968`. Compilations
`:gpu-plan`, `:gpu-renderer`, `:kanvas:compileTestKotlin` : exit 0 rapporté ;
contrôle indépendant sur `6643968` : classe W6aLayerW4W5 XML `22/0/0/0`,
Gradle exit 1 après worker natif 133 (**UNKNOWN**) ; sélecteur W4e direct
Rect/RRect/Path exécuté sans cache, Gradle exit 0. La première review Sol a
relevé un préflight incomplet et des axes natifs non consommés ; les relectures
successives marquent ces points **ADDRESSED** et ne relèvent aucun nouveau
Critical/Important. IIb1 est review-clean, mais IIb2 cover, III/IV, W5a,
leases/B/B−1, 2B et le gate W6 global restent ouverts.

### 2A0c.IIb2 — ClipMaskProducer.Path stencil-cover bundle 1

Le même `ClipMaskProducer.Path` à fan stencil gèle maintenant ses deux sites
natifs ordonnés sous le même pass et packet : edge bundle 0 puis cover bundle
1. La recette cover versionnée lie sa cible, son resolve AA éventuel et son
D24S8 aux ressources physiques finales, au fill rule/scissor de `:math`, au
sample count et aux axes shader/topologie/ABI/stencil/blend. Le cover et
l'edge sont deux pipelines du même render pass : l'état d'attachment et le
fan V/I sont portés par l'edge, dont l'identité et les usages sont vérifiés
avec le cover avant toute création native. Le catalogue, le seal, le packet
W6 et le préflight conservent les deux bundles même si un draw est omis. La
route W4d directe garde son fallback distinct.

Le témoin public EVEN_ODD à deux contours fixe ses pixels avant `Surface` :
son trou distingue la couverture du simple contour extérieur et demande
`Render` + `Readback`. Commit `164a5d796`. Les trois compilations ciblées
sortent 0 ; le sélecteur W4e direct passe avec Gradle exit 0. Le nouveau
sélecteur et la classe W6aLayerW4W5 donnent XML `23/0/0/0` pour la classe,
mais leurs workers natifs sortent 133 après JUnit : **UNKNOWN** pour le gate
global. Review indépendante Sol : **Approved**, aucun finding
Critical/Important ; la review est statique et ne remplace pas les tests.
IIb est review-clean ; 2A0c.III/IV, W5a, leases/B/B−1, 2B et le gate W6
global restent ouverts.

### 2A0c.IIIa1 — PathRenderPass DirectColor Fill triangle non clippé

Le premier site `SingleSampleDirectColor` ordinaire W6 gèle uniquement le
`drawPath` Fill triangulaire non clippé : owner final pass/packet/bundle 0,
géométrie et scissor de `:math`, cible et lignes physiques U/V/I, slices,
blend et axes du pipeline. Le catalogue, le seal, la projection précoce sur
le packet et le préflight des usages enregistrés ordonnés précèdent la
création native. L'encodeur W6 choisit ce pipeline depuis la recette ; les
autres formes DirectColor et la route W4d directe gardent leur chemin
existant. Ce sous-lot ne clôt donc **pas** IIIa.

Le témoin public `drawPath` triangulaire translaté dans une layer fixe ses
pixels avant `Surface` et vérifie `Render` + `Readback`. Commit `c878919`.
Les compilations ciblées passent ; après correction des invariants, la classe
W6aLayerW4W5 affiche XML `24/0/0/0`, puis son worker sort 133
(**UNKNOWN** pour le build). Le sélecteur W4e direct Rect/RRect/Path passe
avec Gradle exit 0 sans build cache. Revue indépendante Sol : **Approved**
sans finding Critical/Important, statique et limitée à IIIa1. Les DirectColor
clippés, Stroke ou non triangulaires doivent être audités avant une clôture
IIIa ; IIIb/c, IV, W5a, leases/B/B−1 et 2B restent ouverts.

### 2A0c.IIIa2 — PathRenderPass DirectColor Stroke triangulaire

Un Stroke public à largeur 1 et cap Butt, du point `(0,0)` à `(2^24,2^24)`,
fournit un cas rare mais réel : les deux coins distants du contour se
confondent après conversion F32 ; la géométrie finale de `:math` est un
triangle direct. L'audit initial l'avait jugé inatteignable ; une review Sol
a trouvé ce contre-exemple avant toute modification de production. La
recette `SingleSampleDirectColor` porte maintenant un tag canonique Stroke
distinct, sans changer l'encodage Fill de IIIa1. Le catalogue, le packet,
le préflight pré-allocation et la sélection native couvrent ce site ; les
autres formes restent sur leur route existante.

Le témoin `Surface` public fixe la diagonale bleue littérale avant la
construction et exige `Render` + `Readback`. Commit `ac3c20a`. Les trois
compilations ciblées passent. La classe W6aLayerW4W5 affiche XML
`25/0/0/0`, puis le worker natif sort 133 (**UNKNOWN** pour le build) ; le
sélecteur W4e direct passe avec Gradle exit 0 sans build cache. Revue
indépendante Sol : **Approved**, aucun finding Critical/Important, revue
statique. IIIa1 et IIIa2 sont review-clean ; les DirectColor sous scissor
ou masque et les phases IIIb/c restent ouverts.

### 2A0c.IIIa3 — audit du Scissor DirectColor public

La première tentative `a97f466` ajoutait une variante typée Scissor et un
témoin `clipRect` public. La review Sol a montré que ce témoin n'exerçait pas
la variante : le premier `clipRect(INTERSECT)` devient `DeviceRect` dans le
Canvas, admis par W4d sans opération W4e ; deux Rect deviennent une pile
complexe à deux entrées et sélectionnent Mask, tandis qu'une rotation
transforme le Rect en Path/Mask. Le sous-lot a été retiré par le revert
récupérable `a83e114` ; `git diff 70e4877..a83e114` est vide. Aucun test
vert de cette tentative n'est revendiqué comme preuve du site Scissor. Il
n'y a pas de recette/lease Scissor publique à compter à ce stade ; une
éventuelle entrée IR non exposée par Canvas demanderait son propre audit.
Le prochain cas public à examiner est Mask/InverseMask, sans fermer IIIa.

### 2A0c.IIIa4a — DirectColor Fill sous Mask

Un témoin `Surface` public combine `saveLayer`, un `clipPath` dur non
rectangulaire et un `drawPath` Fill triangulaire décalé. Son oracle littéral
6×6 distingue l'intersection visible du triangle sans masque et des seules
bounds du clip ; il exige `Render` et `Readback`. La route finale est un
`ClippedGeneralPathDraw(Mask)` / `SingleSampleDirectColor`, distinct du
préfixe `ClipMaskInitialize` / `ClipMaskProducer` / `ClipMaskFold`.

La recette IIIa gèle désormais le mask sampled et son ABI group-zero
texture+uniform avec fenêtre U de 32 octets ; le Fill/Stroke non masqué
conserve son ABI uniforme de 16 octets et ses clés canoniques à l'identique.
Le `RenderPass` parent du graphe et son `PathRenderPass` natif W4e ont des
IDs distincts : catalogue, seal, projection et préflight pré-allocation
authentifient leur binding final commun, les rows physiques mask/U/V/I,
la géométrie, le scissor et les usages enregistrés ordonnés. Le pipeline
masqué est sélectionné depuis la recette ; `InverseMask` et `Stroke` masqué
restent sur le fallback W4e, sans lease IIIa4a fictive.

Commits locaux `c6b6ca7`, `2d9fa83`, puis `b55d37d`. La review Sol a
identifié l'admission involontaire du `Stroke` masqué ; la garde Fill l'a
exclu et la re-review l'a approuvée sans autre finding Critical/Important.
Trois compilations ciblées passent. Après correction, le témoin exact est
XML `1/0/0/0`, la classe W6a XML `26/0/0/0` ; les deux ont Gradle exit 1
uniquement après l'exit natif 133 (**UNKNOWN**). Le sélecteur W4e direct
hard-ordered est XML `1/0/0/0`, Gradle/native exit 0. IIIa4a est
review-clean ; IIIa4b `InverseMask`, IIIb/c, IV et les gates suivants
restent ouverts.

### Diagnostic du worker natif 133 sur macOS

Le GPU Metal Apple M2 Max est disponible et le décalage ABI toolkit/ktypes
déjà corrigé n'explique pas cet exit post-JUnit. Des rapports de crash `java`
du 27 septembre montrent `EXC_BREAKPOINT`/`SIGTRAP`, avec l'assertion AppKit
« Must only be used from the main thread » sur `Java: Thread-5` ; la pile
passe par `-[NSWindow _doOrderWindow:]` puis `libglfw.dylib`. La factory
enregistre `Thread { created.close() }` comme `shutdownHook` ; la fermeture
de la session atteint `GLFWContext.close()` via le teardown gate. Cette
chaîne explique avec forte confiance le crash post-JUnit, sans preuve
absolue du call-site Java puisque la pile IPS n'est pas symbolisée à ce
niveau. `dispose()` explicite et la dernière lease peuvent également
fermer GLFW sur le thread appelant : supprimer seulement le hook ne
garantirait donc pas la sûreté générale AppKit. Aucun correctif de ce
lifecycle natif n'est revendiqué dans IIIa4a. Les XML JUnit à zéro échec
ne transforment donc pas l'exit 133 en gate natif réussi. Aucun GM ni test
d'infrastructure n'a été lancé pour ce diagnostic.

### 2A0c — InverseMask.Geometry DirectTriangle : scan spans, occurrences et recette W6

Le producer Geometry hard-edge W6 porte maintenant une recette physique distincte
`W6InverseMaskPathRecipeV1`. Elle sépare explicitement `GeometryProducer.NonEmpty`
(domaine device/local, origine, scissors I32 ordonnés, nombre de draws, D24S8,
fullscreen sans bindings et zéro slice V/I) de `GeometryProducer.Empty` (clear-only,
zéro draw), ainsi que du `GeometryCover` au owner/scissor distinct. Le freeze final,
le catalogue native-site, le seal du layout, le packet W4e/W6, le préflight avant
`device.create*` et la validation native comparent ces faits et les ressources
physiques. Le renderer ne re-rasterise rien.
La route dépend du fullscreen primitive W4e déjà publié : elle n'introduit ni
triangle V/I producer ni pipeline lié à un buffer de géométrie.

Le plafond est maintenant frame-wide : les occurrences finales de
`StencilGeometryProducerV3` sont additionnées avant `rawPasses`, graphe,
ressources et allocation. Le 4 097e draw est refusé par le diagnostic typé
`w4e.clip.scan-span-draw-limit`; deux occurrences du même `Path` (4 098 draws)
ne sont donc jamais dédupliquées par `commonSource`. Les admissions W4e lane
restent inchangées. Le B_frame exact est `15_715_808` bytes, y compris le second
`Uniform16` `SourceUniformData`; B_lane reste un gate enfant susceptible d'un
refus agrégé W6 aval.

Les témoins Surface publics Task 4 (miroir, winding, frontière `4.98f/5.02f`,
4 096 admis et 4 098 refusé/sentinel) sont XML `1/0/0/0`. Les sélecteurs
préservés Task 2/3 (rebase, Empty clear-only, fullscreen 700, B/B−1, B_lane,
4 097), direct triangle, fan EVEN_ODD et 6×6 restent chacun XML `1/0/0/0`.
Les compilations `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0. Les succès qui matérialisent le GPU ont
Gradle exit 1 uniquement après le worker GLFW macOS 133; ce gap demeure
**UNKNOWN** et séparé des XML. Le refus W4e 4 097 pré-allocation sort Gradle 0.

Commit d'implémentation Task 4 : `687d1a7` (`feat(w6): seal inverse scan-span
occurrences`). Les corrections `d98de2b4f`, `3521f98cd` et `a387979e7`
scellent la séquence native exacte et la provenance du pipeline fullscreen :
le témoin est créé avec ce pipeline dédié, non à partir d'un handle arbitraire,
et le préflight vérifie identité, génération et format de cible. La relecture
Sol ciblée du dernier correctif ne relève aucun Critical/Important. À
`a387979e7`, la compilation `:gpu-renderer:compileKotlin` sort 0 et la classe
Surface W6 donne XML `13/0/0/0` ; Gradle sort 1 uniquement après l'exit natif
GLFW 133 post-JUnit. La revue transversale a ensuite trouvé que l'admission
comptait aussi les triangles inverses AA, pourtant publiés sous
`MultisampleDirectColor`. Le correctif `cdbbd7f80` borne l'admission et la
substitution V/I aux commandes hard-edge éligibles ; son témoin Surface public
de 4 097 lignes a d'abord reproduit le faux diagnostic puis ne le reçoit plus.
La revue Sol ciblée ne relève aucun Critical/Important. À ce commit, la classe
Surface W6 est XML `14/0/0/0`, avec le même exit natif 133 post-JUnit ; le
témoin AA n'affirme ni pixels ni succès global de sa route multisample, seulement
l'absence du plafond propre au producer scan-span. Ce sous-lot inverse-mask est review-clean, mais cette
extension ne ferme pas 2A0c, 2A1 ni 2B ; ces gates restent ouverts pour les
sous-lots ultérieurs.

### 2A0c.IIIb/c — InverseMask W6 : fan, origine, reset et Zero

Les quatre témoins `Surface` publics Task 2 emploient des oracles littéraux :
le fan concave `INVERSE_EVEN_ODD` avec deux contours de même winding, le
rebase d'une couche W6 translatée, deux fills inverses successifs qui exigent
le reset du stencil, et `InverseMask.Zero` sous un clip L hard. Chaque
sélecteur donne XML `1/0/0/0`. Les quatre runs GPU finissent avec Gradle exit
1 seulement après l'exit GLFW natif macOS 133 : ce dernier reste **UNKNOWN**
et séparé du verdict JUnit.

Le source W4e scellé peut maintenant conserver sa géométrie
`InverseDomainSource`/`Empty` lorsque les phases W5/W6 rebindent seulement le
material ou le blend ; les constructeurs publics continuent de la refuser.
`InverseMask.Zero` publie son unique consumer color fullscreen avec le mask
sampled normal (`inverse=false`) : le planner exclut Zero de
`inverseMaskDirectGeometryCommands`, donc aucun producer, D24S8 ou bytes V/I
ne sont créés pour ce cas. La vérification W4e public empty/non-empty a aussi
mis en évidence une sélection W5a trop étroite : le paquet `InverseDomain`
scellé sans lane W5b est désormais associé à son unique source material.

Les compilations `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` sortent 0. La préservation W6 hard-mask donne XML
`1/0/0/0`, avec le même exit 133 **UNKNOWN** ; la préservation W4e public
empty/non-empty donne XML `1/0/0/0`, Gradle exit 0. Une relance indépendante
de la classe W6a entière à `3c2660739` donne XML `31/0/0/0`, puis Gradle exit 1
sur GLFW natif 133. La revue Sol du commit ne relève aucun Critical/Important,
y compris sur l'association W5a du packet scellé. Ce sous-lot ne lance ni GM,
ni dashboard Skia, ni suite globale ; les recettes natives fan/Zero de Task 3,
2A0c.IV et les gates 2A1/2B demeurent ouverts.

### 2A0c.IIIb/c — recettes natives par site Geometry/Zero

Le gel Task 3 remplace le wrapper producer/cover par une recette scellée par
`PathRenderPass` W4e natif, avec `NativeSiteOwnerV1(nativePass.id,
nativePass.ordinal, 0)`. Les variantes sont `GeometryProducer.ScanSpans`
(`NonEmpty`/`Empty` sans V/I), `GeometryProducer.Fan` (fill rule, V/I,
D24S8 et clear stencil), `GeometryCover` (mask/U 32, `TestZeroKeep`,
`LoadStoreTestReset`) et `ZeroCover` (mask/U 32, sans depth/V/I). Les IDs W6
restent exclusivement des témoins de binding ; la recette Zero est l'unique
site `SingleSampleDirectColor` sous son `RenderPass` W6.

Le catalogue, le seal et le packet utilisent les IDs natifs et parcourent les
bindings W4e finals ; la branche catalogue proxy `StencilGeometryProducerV3`
a été retirée. `GPUW6aLayerFramePlan` projette chaque recette vers sa phase W6,
le préflight compare les ressources et packets avant `device.create*`, et
l'appel W6 de `encodeW4eNativePasses` sélectionne son ABI/pipeline par cette
map catalog-first. L'appel W4e autonome conserve son chemin déjà scellé quand
aucune projection W6 ne fournit cette map. Aucun lease 2A1 ni claim 2B n'a été
créé.

Vérification finale : `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin`
et `:kanvas:compileTestKotlin` sortent 0. Les XML publics sont scan-span
`14/0/0/0`, W6a `31/0/0/0`, D24S8 distinct `1/0/0/0` et inverse
empty/non-empty `1/0/0/0`. Les deux classes GPU ont Gradle exit 1 seulement
après l'exit GLFW natif macOS 133, donc **UNKNOWN** séparé des XML ; les deux
sélecteurs W4e sortent Gradle 0. Task 3 est prêt pour revue Sol ; 2A0c.IV,
2A0d, 2A1 et 2B restent ouverts.

### 2A0c.IIIb/c — correctif revue Sol, round 1

Le scan-span gèle son `load/store` natif réel. Les usages W4e sont tirés de la
phase scellée : scan-span sans V/I/U, fan avec V/I sans U, covers avec U sans
V/I. Le préflight vérifie catalogue/owner, operands, load/store, blend et ordre
d’usages avant allocation. L’encodeur W6 sélectionne le stencil fan et le
pipeline cover depuis la recette, puis confronte les données préparées. Aucun
lease 2A1 ni claim 2B n’est créé.

### 2A0c.IIIb/c — correctif revue Sol, round 2

Les phases W4d multisample et hard-edge restent sur leurs usages V/I/U
historiques ; seuls les quatre sites inverse W6 réduisent leurs usages à ceux
consommés. Le préflight inverse compare désormais chaque `GPUFrameResourceUse`
entier (ref, rôle, usage, lifetime et écriture), ainsi que les slices scellées.
Les sélecteurs publics AA `AA inverse direct over scan span limit keeps its
multisample route` et hard-mask `hard path mask clips an offset direct fill
triangle in a W6 layer` sont XML `1/0/0/0`; leurs Gradle exits 1 proviennent du
GLFW 133 post-JUnit et restent **UNKNOWN**. Cette note ne ferme pas la revue
Sol ni 2A1/2B.

### 2A0c.IIIb/c — correctif revue Sol, round 3

`GeometryProducer.Fan` conserve désormais un snapshot immuable de
`PathStencilEdgeFanF32` : vertices F32 (bits bruts), indices et débuts de
contours, ainsi que son domaine. Ces faits entrent dans l'encodage canonique
du site. Avant tout `device.create*`, le préflight confronte le fan du packet
préparé et le contenu exact de sa slice V/I NDC au snapshot ; un fan de même
taille mais de contenu différent ne peut donc plus partager une identité de
catalogue. Les covers Geometry/Zero vérifient aussi les quatre bytes F32 zéro
de `inverse=false` à l'offset U+16 de leur Uniform32 scellé.

Le packet producer ne porte volontairement pas de consumer direct : le
préflight exige ce `null` puis confronte son consumer inverse retenu par
l'autorité scellée avec le cover natif suivant. Les covers, eux, authentifient
leur consumer packet, domaine, mask et état depth/stencil. `w4ePreparedPath.scissor`
reste le scissor du draw source et ne prétend pas être celui de la phase ; le
gate compare donc les scissors locaux ordonnés du scan-span et le domaine du
consumer effectivement consommés. Les trois compiles ciblées sortent 0 ; XML
scan-span `14/0/0/0`, W6a `31/0/0/0`, D24S8 distinct `1/0/0/0` et
empty/non-empty `1/0/0/0`. Les deux classes GPU terminent encore après JUnit
par GLFW 133 (**UNKNOWN**), tandis que les deux sélecteurs W4e sortent 0.
Cette note ne ferme pas la revue Sol, 2A0c.IV, 2A0d, 2A1 ou 2B.

### 2A0c.IIIc — correctif revue Sol, round 4/5

Avant la première allocation, les deux covers confrontent désormais l'identité
du consumer porté par le packet à `authority.consumerFor(passId)` ; l'égalité
des champs ne suffit donc plus. Le gate contrôle aussi le `scissor` de la
source (`bound.draw`), distinct du domaine de couverture, et le ZeroCover
exige `PathDrawGeometry.Empty`. Ces trois contrôles reproduisent les
assertions auparavant tardives de l'encodeur sans créer de géométrie.
Les trois compiles ciblées sortent 0. Le lancement groupé des sélecteurs
publics produit les XML W6a `31/0/0/0`, scan-span `14/0/0/0` et W4e
`2/0/0/0`; Gradle s'arrête ensuite avec GLFW 133, après JUnit (**UNKNOWN**
pour la couche native, sans échec JUnit).

La relecture Sol ciblée de `6082d3a68` ne relève aucun finding
Critical/Important. Le sous-lot Task 3 `2A0c.IIIb/c` est review-clean : le
catalogue par site natif, le contenu Fan et le packet inverse sont authentifiés
avant allocation, puis consommés par le dispatch fondé sur la recette. Les
gates `2A0c.IV`, `2A0d`, `2A1` et `2B` demeurent ouverts ; les XML ci-dessus
n'établissent pas la réussite de la terminaison native GLFW.

### 2A0c.IV Task 1 — inventaire InverseDomain sans mask et témoins publics

L'inventaire W6 porte sur les `finalW4eBindings`, rebinding final des sources
avant catalogue : les sites sont donc identifiés par le `PathRenderPass` natif
scellé, jamais par l'index proxy W6 ni par un ID de fixture. Pour
`InverseDomain.Zero`, quel que soit le support conservé `PathDrawGeometry.Empty`
ou `PathDrawGeometry.InverseDomainSource`, l'unique site est
`NativeSiteOwnerV1(path.id, path.ordinal, 0)`. Il est
`SingleSampleDirectColor`, crée un cover couleur fullscreen (draw 3) et
consomme uniquement `INVERSE_DOMAIN_ZERO_UNIFORM` U16 : ni V/I, ni D24S8.
Le compilateur sélectionne `Empty` seulement si `segmentCount == 0`; un Path
public non vide sans intérieur fini conserve `InverseDomainSource`.

Pour `InverseDomain.Geometry`, `DirectTriangle` et `StencilEdgeFan` partagent
le même `path.id` et `path.ordinal`, avec les slots ordonnés `0=domainStencil`,
`1=interiorZero`, `2=colorCover`. Les trois sites restent obligatoires : le
quad de domaine et l'intérieur ont leurs slices V/I exactes, le cover consomme
`INVERSE_DOMAIN_UNIFORM` U16, et le D24S8 scene-local n'existe que pour cette
variante Geometry. `commonSource=true` omet la commande de draw du slot 0,
mais le pipeline `domainStencil` est toujours créé et authentifié; aucune
omission de commande ne réduit l'identité, les bundles ou un futur compte 2A1.
Le domaine et le scissor source demeurent les facts I32/F32 scellés de
`:math:geometry`; le renderer ne les reconstruit pas.

Les témoins `Surface` ajoutés dans `W6aLayerW4W5SurfacePixelTest` fixent leur
oracle littéral avant `Surface`, n'emploient aucun `clipPath`, et exigent
chacun les scopes `Render` et `Readback`: Empty/Zero, non-empty
`InverseDomainSource`/Zero, Geometry DirectTriangle et Geometry fan
`INVERSE_EVEN_ODD` à deux contours. Les selectors Zero Empty, Zero non-empty
et fan donnent chacun XML `1/0/0/0`, puis Gradle exit `1` après GLFW `133`;
le statut natif est donc **UNKNOWN**. Le selector DirectTriangle donne XML
`1/1/0/0`, avant pixels et avant submit, avec le RED causal
`w6a.layer.invalid_native_path: W6 path operands differ from the frozen graph's
stencil IDs, ordering or load/store`; son Gradle exit est aussi `1` et GLFW
`133` reste séparé. Aucune production n'a été modifiée dans Task 1, aucun
test InverseMask/scan-span existant n'a été touché, et 2A0c.IV reste ouvert
pour la recette Geometry DirectTriangle puis le gate fan.

La revue Sol de Task 1 a relevé que le premier triangle plaçait son hypoténuse
sur quatre centres de pixels, sans règle de frontière indépendante. Le sommet
`(1,5)` est déplacé à `(1,4)` : l'expected littéral 3/2/1 ne change pas et
aucun centre ne tombe sur le nouvel edge. Le sélecteur exact reste XML
`1/1/0/0` avec le **même** diagnostic `w6a.layer.invalid_native_path`
avant pixels ; il constitue maintenant un RED causal du validateur W6.
L'analyse du code situe le refus dans la classification du premier D24S8
scene-local `InverseDomain.Geometry` comme cover, alors que l'encodeur le
clear/initialise dans son pass `SingleSampleDirectColor`. La correction
phase-aware de cette validation précède les recettes IV ; elle ne généralise
pas l'acceptation des covers sans producer.
