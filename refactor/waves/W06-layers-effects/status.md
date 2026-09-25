# W06 — layers et effets : checkpoints W6a/W6b/W6c/W6d

## Statut

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
