# W3 — `gpu-plan` et première tranche compositionnelle

Date : 2026-09-02  
Statut : validée par l’utilisateur et review Sol approuvée  
Branche : `codex/w3-gpu-plan-first-slice`

## 1. Contexte

W0 à W2 ont établi la vérité de référence, les objets géométriques dans
`:math`, la Scene IR immutable dans `:render-ir` et les résultats typés de
planning et d'exécution.

Le rendu produit ne consomme toutefois pas encore cette architecture :
`Surface.render()` extrait les `DisplayOp` et entre directement dans le gros
builder prepared historique. Celui-ci mélange encore admission sémantique,
lowering, ressources, tâches, capabilities, matérialisation et exécution.

W3 doit rendre une première tranche avec le nouveau chemin sans prétendre
migrer les autres familles. La règle reste atomique : une frame utilise
entièrement le nouveau chemin ou entièrement le chemin legacy.

## 2. Décision

Créer un module JVM pur `:gpu-plan` entre `:render-ir` et `:gpu-renderer`.
Il transforme une `SceneSnapshot` en `RenderGraph` immutable et handle-free.
Un adapter étroit dans `:gpu-renderer` abaisse ensuite ce plan vers les tâches
prepared et le runtime natif existants.

```text
Surface
  -> W3 shallow gate
       `-- obviously out of scope ------> legacy whole-frame route
  -> DisplayOpSceneAdapter.capture
  -> SceneSnapshot
  -> GpuPlanCompiler.plan
       |-- Ready(RenderGraph) --------> GpuRenderBackend.submit
       |                                  -> materialize
       |                                  -> submit
       |                                  -> readback
       `-- GapNotMigrated ------------> legacy whole-frame route
```

Les décisions de géométrie, clip, blend, ressources, ordre des passes,
lifetimes et budget appartiennent au planner. L'adapter renderer peut vérifier
le plan et traduire ses valeurs, mais ne peut pas choisir une autre stratégie
de rendu.

## 3. Alternatives écartées

### 3.1 Envelopper le builder prepared actuel

Cette option classifierait la Scene IR avant de reconvertir la frame vers la
pipeline actuelle. Elle serait rapide mais laisserait le `RenderGraph`, ses
ressources et son budget sans autorité réelle. Elle est rejetée.

### 3.2 Extraire immédiatement tout le planner historique

Cette option déplacerait en une fois toutes les routes géométriques, materials,
layers, images, texte et effets. Elle agrandirait fortement la surface de
régression et empêcherait une preuve verticale courte. Elle est reportée aux
vagues W4 à W7.

## 4. Objectifs

- rendre une frame composée de rectangles solides, clips simples et `SrcOver`
  par `SceneSnapshot -> RenderGraph -> GPU` ;
- faire du `RenderGraph` l'autorité réellement consommée pour cette tranche ;
- définir les contrats généraux de ressources, passes, lifetimes, dépendances,
  capabilities et budgets sans handle natif ;
- introduire un `RenderContext` propriétaire du runtime et de ses sessions ;
- brancher le routeur de migration whole-frame dans `Surface.render()` ;
- prouver les pixels avec un oracle CPU indépendant ;
- conserver inchangé le résultat observable du chemin legacy pour toute frame
  non migrée.

## 5. Hors périmètre

- fonts et texte ;
- codecs et décodage d'images ;
- images RGBA, gradients et runtime effects ;
- paths, rrects, strokes et géométries non rectangulaires ;
- clips complexes, différence de clips, stencil et coverage masks ;
- layers, filters, destination reads et blends autres que `SrcOver` ;
- `SceneCommand.Readback` explicite ;
- `SceneCommand.Clear` explicite dans la première capability ;
- concurrence générale entre plusieurs frames et suppression complète du
  runtime legacy ;
- tests d'infrastructure, réflexion, parsing de source ou assertions sur la
  forme privée exacte du `RenderGraph`.

`jpg-color-cube` reste en quarantaine et ne doit pas être lancé par W3.

## 6. Graphe des modules

```text
:math:* -> :render-ir -> :gpu-plan -> :gpu-renderer -> :kanvas
```

- `:gpu-plan` dépend en `api` de `:render-ir` et n'importe aucun type
  `io.ygdrasil.webgpu`, `:gpu-renderer`, `:kanvas`, font ou codec.
- `:gpu-renderer` dépend de `:gpu-plan` et garde temporairement sa dépendance
  directe à `:render-ir`.
- `:kanvas` reste la façade produit et choisit le chemin whole-frame.
- aucune géométrie de remplacement n'est créée dans `:gpu-plan`.

## 7. Contrats de planning

### 7.1 Entrée

Le planner pur expose conceptuellement :

```kotlin
interface GpuPlanCompiler {
    fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}
```

`PlanCapabilitySnapshot` est une valeur immutable, typée et sans type WebGPU.
Pour W3 elle contient seulement les limites nécessaires : génération du
device, dimension maximale 2D, taille maximale de buffer, alignement des lignes
de readback et formats logiques disponibles. La génération participe au plan
afin qu'un plan devenu stale après un device loss soit refusé avant submit.

`PlanBudget` contient au minimum la limite de mémoire frame-local. Sa source
produit est un nouveau champ `RenderConfig.frameLocalBudgetBytes`, fixé par
défaut à 1 GiB et configurable avec
`kanvas.render.frameLocalBudgetBytes`. Une limite nulle ou négative est
invalide. Toute arithmétique de taille est effectuée en `Long` avec détection
d'overflow.

### 7.2 Identités

`PlanId`, `PlanResourceId` et `PlanPassId` sont stables et dérivés du contenu
sémantique, pas d'un compteur global ou d'une adresse native. L'identité du
plan inclut la scène, l'extent et le color space de la cible logique, le
snapshot de capabilities et le budget ayant participé à la décision. Le
`label` de `RenderTargetDescriptor` reste une metadata de diagnostic et de
telemetry ; il est délibérément exclu de `PlanId` et ne peut modifier ni les
décisions ni les pixels.

### 7.3 Immutabilité et géométrie

Les dimensions utilisent `SizeI32` de `:math`. Les rectangles résolus utilisent
`RectI32` de `:math` derrière une copie défensive, car ce type est mutable.
L'API de plan ne retourne jamais sa référence interne ; elle expose une copie.

Il est interdit d'ajouter `GPUPlanRect`, `PlanBounds` ou une seconde algèbre
géométrique. Les noms géométriques suivent la nomenclature `I32`, `I64`, `F32`
et `F64` du module `:math`.

## 8. `RenderGraph`

### 8.1 Ressources logiques

Une ressource déclare :

- une identité stable ;
- un kind logique (`Texture2D` ou `Buffer` pour W3) ;
- une taille ou des dimensions ;
- ses usages (`RenderAttachment`, `CopySource`, `CopyDestination`, `MapRead`) ;
- sa lifetime (`FrameLocal` pour W3) ;
- l'intervalle `[firstPassIndex, lastPassIndexExclusive)` ;
- son coût mémoire planifié en bytes.

La première tranche produit un logical target binding et un buffer de staging
pour le readback. Le binding est matérialisé exactement une fois par la session.
Sa lifetime logique est frame-local et son coût participe au pic mémoire, même
si le `RenderContext` réutilise ultérieurement une allocation physique poolée.

Le format logique décrit une couleur 8-bit sRGB avec calcul et blend en linear
premultiplied RGBA. Il ne dépend pas de l'ordre de canaux demandé par la façade
Surface.

### 8.2 Passes

Le contrat ferme les cinq familles prévues par le plan global :

- `RenderPass` ;
- `TextureCopy` ;
- `FilterPass` ;
- `ResolvePass` ;
- `ReadbackPass`.

W3 n'émet qu'un `RenderPass`, initialisé transparent, suivi d'un
`ReadbackPass`. Les autres familles existent comme contrats mais leur émission
reste hors scope.

Le `RenderPass` porte une liste ordonnée de `SolidRectDraw`. Chaque draw conserve
la couleur linear-premultiplied, les bounds visibles résolues, le scissor
résolu, la décision de coverage `FullOrScissor`, le sample plan
`SingleSample` et le blend `SrcOver`. Le pass ferme également son load/store :
clear transparent puis store.

Ces décisions sont des valeurs du plan, pas des choix implicites du lowerer.
Un adapter recevant une autre combinaison refuse le plan ; il ne la remappe
pas vers MSAA, stencil ou coverage mask. Les rectangles sont des copies de
`RectI32` provenant de `:math`, pas un nouveau type géométrique.

Les dépendances entre passes nomment uniquement des identités logiques. Elles
ne sont ni des fences, ni des handles de synchronisation.

### 8.3 Budget

Le planner calcule le coût de chaque ressource, son intervalle de vie et le pic
des ressources simultanément vivantes. Pour W3, le pic couvre la texture cible
et le staging de readback, dont `bytesPerRow` respecte l'alignement annoncé par
les capabilities.

Un dépassement produit `RenderPlanResult.ResourceLimitExceeded`; il ne peut pas
être masqué par un fallback legacy.

## 9. Capability promue W3

La capability initiale est
`solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1`.

Une frame lui appartient seulement si elle contient au moins un draw visible
et si toutes ses commandes satisfont les règles suivantes.

### 9.1 Commandes visuelles admises

Un `SceneCommand.Draw` est admis lorsque :

- `origin == DrawOrigin.RECT` ;
- la géométrie est `GeometryNode.Rect` ;
- le material est `MaterialNode.Solid` ;
- le `PaintNode`, lorsqu'il existe, est `FILL`, sans shader, custom blender,
  color filter, mask filter, path effect ou image filter ;
- `resource` et `operationBlendMode` sont absents ;
- `effects` est vide ;
- le blend est sémantiquement `SrcOver` : `BlendNode.SrcOver`,
  `BlendNode.Mode(SRC_OVER)` ou `BlendNode.Paint(SRC_OVER, null)` ;
- le transform est identity ou scale/translate, fini, et transforme les quatre
  bounds en coordonnées entières exactes ;
- la coverage est hard-edge ou antialiased ; l'admission antialiased n'est
  possible que parce que les limites résolues sont pixel-aligned ;
- le clip est vide ou un `ClipStackNode.DeviceRect` pixel-aligned ;
- l'intersection target/geometry/clip produit un rectangle visible non vide.

Un `SceneCommand.DrawColor` est admis uniquement en mode `SRC_OVER`, avec un
transform identity et un clip vide ou `DeviceRect` pixel-aligned. Il devient un
draw couvrant la cible visible.

Les couleurs opaques et translucides sont admises afin que la preuve exerce
réellement la composition `SrcOver`.

### 9.2 Contrat couleur fermé

W3 utilise une cible interne physique `rgba8unorm-srgb`, y compris lorsque la
façade demande `PixelFormat.BGRA8`. Une demande Surface RGBA retourne les bytes
du readback dans l'ordre RGBA ; une demande BGRA applique uniquement un swizzle
CPU déterministe R/B au boundary, après completion.

`ColorARGB` est interprété comme une couleur straight-alpha encodée sRGB. Le
planner calcule le payload source ainsi :

```text
a  = A / 255
sr = sRGB.decode(R / 255) * a
sg = sRGB.decode(G / 255) * a
sb = sRGB.decode(B / 255) * a
```

Le blend est effectué en linear premultiplied :

```text
out.rgb = src.rgb + dst.rgb * (1 - src.a)
out.a   = src.a   + dst.a   * (1 - src.a)
```

Le store de l'attachment sRGB encode chaque canal RGB linéaire clampé dans
`[0, 1]`; l'alpha reste linéaire. La quantification 8-bit utilise l'arrondi au
plus proche du format natif. Les fixtures exactes choisissent des valeurs dont
la quantification est non ambiguë ; un écart d'un LSB ne devient pas la règle
générale de W3.

Les valeurs physiques `RenderConfig.gpuColorFormat` incompatibles avec ce
contrat sRGB restent hors de cette capability et passent par le legacy avant
promotion. Une absence de support device pour `rgba8unorm-srgb` après
reconnaissance sémantique produit `GapOnPromotedScope`.

### 9.3 Commandes sans effet pixel

`SetTransform`, `SetClip` et `Annotation` peuvent être conservés comme
provenance sans créer de pass. Leurs valeurs sont validées avec les mêmes
contraintes finies et simples. L'état effectif d'un draw reste celui capturé
dans son `DrawNode`; le planner ne reconstruit pas un Canvas mutable.

### 9.4 Commandes non admises

Une frame vide, entièrement clipped-out, contenant un `Clear`, un readback
explicite, un layer ou toute autre forme produit `GapNotMigrated` tant que cette
forme n'est pas promue.

Le clear transparent initial du `RenderPass` est une initialisation de la cible,
pas l'implémentation de `SceneCommand.Clear`. Cette distinction évite une fausse
équivalence lorsque `Clear` apparaît après un draw.

## 10. Résultats et routage de migration

Le routeur applique cet ordre :

1. valider les dimensions de Surface avant de construire `SceneExtent` ;
2. appliquer une shallow gate aux `DisplayOp` ;
3. capturer les candidats en `SceneSnapshot` ;
4. demander un plan au backend lié à la cible physique ;
5. sur `Ready`, soumettre exclusivement le nouveau plan ;
6. sur `GapNotMigrated`, appeler exclusivement la route legacy avec les
   `DisplayOp` originaux ;
7. sur `GapOnPromotedScope`, `InvalidScene` ou
   `ResourceLimitExceeded`, terminer sans fallback ;
8. après `Ready`, tout échec de capability, validation, allocation, device,
   submit ou readback est terminal et ne relance jamais la frame en legacy.

La shallow gate ne fait aucun lowering. Elle autorise seulement les variantes
potentiellement W3 (`DrawRect`, `DrawColor`, `SetTransform`, `SetClip`,
`Annotation`) et impose `MAX_W3_COMMANDS = 512`. Toute autre variante, y
compris `Clear`, ou tout dépassement retourne immédiatement au legacy sans
capturer la frame. Les diagnostics de capture signalant une limite de nœuds ou
de ressources produisent aussi `GapNotMigrated`; les valeurs non finies,
cardinalités invalides et autres corruptions restent terminales en
`InvalidScene`.

La distinction RGBA/BGRA, la création de la cible physique et la conversion du
readback appartiennent au bridge `:kanvas`/`:gpu-renderer`, jamais à
`:gpu-plan`.

Le planner sépare explicitement classification et construction. Seul un axe
sémantique non promu — famille, géométrie, material, transform, clip, blend,
espace colorimétrique ou configuration physique déclarée hors scope — produit
`GapNotMigrated`. Dès que toutes les commandes appartiennent à la capability
promue, une incompatibilité de target, device, format, identité ou lowering
produit `GapOnPromotedScope`. Un dépassement de budget conserve sa catégorie
`ResourceLimitExceeded`.

`scene.extent` doit être égal à `target.extent` et leurs color spaces doivent
être identiques. Une contradiction est `InvalidScene`. W3 ne promeut que sRGB.

## 11. Complétion du port backend W2

Le port W2 ne peut actuellement retourner qu'un statut `Completed`, alors que
`Surface.render()` doit recevoir les bytes du readback. W3 rend le résultat
générique sans exposer de type GPU dans `:render-ir` :

```kotlin
interface RenderOutput

interface RenderBackend<P : Any, O : RenderOutput> {
    fun plan(scene: SceneSnapshot, target: RenderTargetDescriptor): RenderPlanResult<P>
    fun submit(plan: P): RenderSubmission<O>
}

interface RenderSubmission<out O : RenderOutput> {
    val id: SubmissionId
    suspend fun await(): RenderExecutionResult<O>
}

sealed interface RenderExecutionResult<out O : RenderOutput> {
    data class Completed<O : RenderOutput>(val output: O) : RenderExecutionResult<O>
    // Les issues d'échec restent covariantes sur Nothing.
}
```

`RenderOutput` est un marker contractuel handle-free : ses implémentations ne
peuvent pas exposer de handle natif ni implémenter un ownership natif.
`GpuRenderBackend` dans `:gpu-renderer` implémente ce port avec `RenderGraph` et
un `GpuFrameOutput` immutable. Celui-ci est l'unique résultat de completion et
contient :

- dimensions, row stride, ordre de canaux et bytes copiés défensivement ;
- métriques typées nécessaires à `RenderStats` ;
- diagnostics d'exécution déjà convertis en `RenderDiagnostic` ;
- structural steps immutables ;
- native evidence counters et scope kinds immutables lorsqu'ils existent.

Il n'existe aucun side channel lu après `await()`. Le backend est lié à une
cible physique par le bridge Surface ; le `RenderTargetDescriptor` reste
logique et ne gagne pas de `PixelFormat` GPU.

`await()` est idempotent : plusieurs appels observent la même issue et le même
output immutable. L'abandon ou la cancellation d'un waiter n'annule pas un
submit déjà envoyé au GPU. Les ressources temporaires sont libérées par la
completion enregistrée, qu'elle termine en succès, erreur ou cancellation du
waiter.

## 12. `RenderContext` et exécution

`RenderContext` appartient à `:gpu-renderer`. W3 utilise un context
process-scoped, créé paresseusement par un owner produit. Il possède le device,
la queue, les capabilities observées, caches, pools, scheduler et sessions. Il
fournit au planner uniquement un `PlanCapabilitySnapshot` immutable.

Chaque session est identifiée par génération du device, dimensions et format
interne. Une file ou mutex appartenant à la session garantit au plus une frame
in-flight par session ; des sessions distinctes ne dépendent pas du lock
legacy. `close()` ferme les sessions puis le backend. Un device loss invalide
la génération, ferme/évince les sessions concernées et force la création d'un
nouveau snapshot avant un futur planning.

Le lowerer renderer :

- vérifie l'intégrité des identités, références, lifetimes et budgets ;
- transforme les ressources logiques en requêtes de préparation ;
- transforme les `SolidRectDraw` en semantic payloads et tâches prepared ;
- réutilise l'assembleur core-primitive et la session native existants ;
- ne relance ni admission de famille, ni sélection de clip, ni choix de blend ;
- retourne un output de readback immutable au `RenderSubmission`.

La route produit ne passe pas par `GPUFramePathApiInventory`, qui reste un
harness d'observation sans preuve de submit natif.

`Surface.render()` reste bloquant à sa frontière publique en attendant la
completion. Le chemin nouveau n'est pas exécuté sous le lock global legacy ;
ce lock ne protège que le fallback historique tant qu'il existe. La
sérialisation du nouveau chemin appartient aux sessions du `RenderContext`,
pas à `GPUPreparedSurfaceRuntimeOwner`.

Le bridge construit `RenderResult` avec le color space sRGB, les pixels dans
l'ordre public demandé, `opsDispatched` égal au nombre de commandes visuelles
planifiées, `opsRefused = 0`, et les nombres de pipelines/draw calls issus de
l'exécution réelle. Les diagnostics et structural steps existants sont
propagés lorsqu'ils ont une signification produit ; aucun compteur artificiel
de sélection de route n'est ajouté.

Deux ressources ou passes sémantiquement identiques dans le même plan restent
distinctes par leur rôle et leur ordinal. Ces valeurs participent à leurs IDs,
ce qui rend les identités uniques dans un plan et stables entre deux plans
équivalents.

## 13. Diagnostics

Les diagnostics de planning utilisent les catégories publiques de
`:render-ir` et des codes stables. Les premiers codes couvrent au minimum :

- commande ou axe non migré ;
- géométrie/clip/transform non pixel-aligned ;
- scène invalide ;
- overflow de taille ;
- dimension ou buffer hors capability ;
- budget frame-local dépassé ;
- plan altéré ou incompatible avec le contexte d'exécution ;
- échec device, submit ou readback.

Les messages peuvent évoluer ; les tests portent sur les codes et les issues
publiques. Aucun Throwable ou handle natif ne traverse le contrat public.

## 14. Stratégie de tests

Tous les changements de comportement suivent TDD : test public rouge, preuve
du rouge attendu, implémentation minimale, puis vert.

Tests autorisés :

- valeurs et invariants publics du plan ;
- résultat `Ready` ou catégorie de refus pour des scènes construites via la
  Scene IR publique ;
- calcul de budget, overflow et alignement de readback ;
- mapping de `RenderConfig.frameLocalBudgetBytes` vers `PlanBudget` ;
- pixels exacts d'une frame avec rectangles opaques/translucides,
  `DrawColor(SRC_OVER)` et `DeviceRect`, comparés à un oracle CPU indépendant ;
- ordre de canaux RGBA et BGRA à la frontière Surface ;
- diagnostic terminal et absence observable de soumission après un refus ;
- idempotence de `await()` et libération après succès, échec ou cancellation du
  waiter ;
- fallback legacy d'une frame manifestement hors W3 ou au-dessus de la limite
  de commandes, sans régression introduite par la capture Scene IR ;
- non-régression d'au moins une frame non migrée, rendue par le legacy.

Tests interdits :

- parsing ou recherche dans les sources ;
- réflexion sur packages, classes ou méthodes privées ;
- assertion qu'un planner, lowerer ou materializer précis a été appelé ;
- snapshot textuel complet du graphe ou du WGSL ;
- assertion sur la forme privée exacte du `RenderGraph` ;
- compteur technique ajouté uniquement pour prouver le routage.

Les frontières d'architecture sont imposées par Gradle, les dépendances de
modules, les interfaces publiques minimales et la visibilité du compilateur.

## 15. Gate W3

W3 est terminée lorsque :

- `:gpu-plan` existe et ne dépend que des modules autorisés ;
- la capability W3 produit un `RenderGraph` réellement consommé ;
- coverage, sample plan, blend et load/store de la capability sont fermés par
  le plan et ne sont pas replanifiés par le renderer ;
- `Surface.render()` applique le routage atomique défini ci-dessus ;
- le nouveau chemin produit des pixels exacts contre l'oracle CPU sur RGBA et
  BGRA ;
- le `RenderContext` garantit une seule frame in-flight par session et libère
  ses ressources sur toutes les issues ;
- les frames hors scope conservent leur comportement legacy observable ;
- les refus après promotion sont terminaux et typés ;
- les tests ciblés de `:math`, `:render-ir`, `:gpu-plan`, `:gpu-renderer` et
  `:kanvas` passent hors baseline rouge connue ;
- aucun travail font, codec ou `jpg-color-cube` n'a été introduit.

La convergence GM globale reste hors de cette gate. W3 prouve la nouvelle
architecture sur une tranche étroite ; W4 et suivantes élargissent ses axes
compositionnels.
