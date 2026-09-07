# W4d–W4e — clôture geometry/coverage

Date : 2026-09-06  
État : design et formalisation validés  
Branche de départ : `codex/w4d-strokes-hairlines`, empilée sur `codex/w4c-path-fills`

## 1. But

Fermer W4 sans déclarer prématurément la couverture géométrique atteinte. Le
reste de la vague livre, dans cet ordre :

1. W4d.1 — strokes et hairlines, avec toute décision géométrique dans `:math` ;
2. W4d.2 — transforms généraux et coverage AA commune aux fills et strokes ;
3. W4e — clip stacks path/inverse/booléennes, hard-edge et AA.

La chaîne d'autorité reste :

```text
Scene IR sémantique
  -> préparation géométrique F64/F32 dans :math
  -> sélection, graph, ressources et budgets dans :gpu-plan
  -> authentification et matérialisation mécanique dans :gpu-renderer
  -> Surface publique et readback
```

W4 ne traite ni materials complexes, ni layers/effets, ni convergence GM. Les
tests `font`, `codec`, GM Skia, dashboard, baselines et `jpg-color-cube` restent
hors périmètre.

## 2. État de départ et problème architectural

W4a–W4c ont livré Rect ScalarAA, RRect analytique et path fills hard-edge. Les
strokes, hairlines, transforms généraux et clips complexes utilisent encore
des routes historiques possédant leurs propres types, limites et décisions de
géométrie dans `:kanvas` ou `:gpu-renderer`.

Des briques GPU existent déjà pour :

- les path hairlines et strokes simples ;
- `GPUSamplePlan.MultisampleFrame(4)` et `StencilAA` ;
- les stencil clips et coverage masks ;
- une classification perspective limitée.

Elles sont des mécanismes réutilisables, pas une autorité à promouvoir. Elles
manipulent notamment des noms de transform, des listes de vertices et des
preuves calculées tardivement. W4 doit les placer derrière un `RenderGraph`
scellé, ou les remplacer si elles ne peuvent pas authentifier les faits du
planner.

Le défaut le plus important se trouve à la capture des clips : `ClipEntry` et
`ClipStackOp` ne retiennent qu'un `transformClass: String` et un booléen de
refus perspective. Cela ne suffit pas pour reproduire une géométrie projective.
La matrice capturée doit devenir une donnée typée et sérialisée.

## 3. Approches considérées

### A — préparation géométrique commune dans `:math` — retenue

Les fills, strokes, hairlines et clips produisent des snapshots device-space
immuables issus d'une préparation F64 bornée. Le planner compose ensuite des
passes directes, stencil ou coverage mask. Les composants GPU historiques ne
sont réutilisés que lorsqu'ils acceptent ces snapshots sans recalcul.

Cette approche respecte les frontières décidées, mutualise transforms et AA,
et empêche une nouvelle multiplication de lanes par type de primitive.

### B — promouvoir les routes legacy — rejetée

Brancher directement `AdvancedStrokePlan`, `GPUPathHairlineContract` ou
`GPUClipExecutionPlan` serait plus court, mais conserverait géométrie,
classification et limites dans le renderer. Les divergences après `Ready` et
les fallbacks tardifs resteraient possibles.

### C — convertir immédiatement toute coverage en full-frame mask — rejetée

Un unique modèle de mask simplifierait la composition, mais imposerait plusieurs
textures target-size aux scènes simples et ferait payer les Rect, RRect,
hairlines et clips simples pour la généralité. Les masks restent la stratégie
générale, avec des fast paths directs ou stencil prouvés.

## 4. Invariants communs

- Les objets géométriques nouveaux vivent dans `:math:geometry`; les transforms
  et projections vivent dans `:math:matrix`.
- Tout nom numérique de géométrie porte `I32`, `I64`, `F32` ou `F64` selon sa
  représentation. Les enums purement sémantiques n'ont pas de suffixe.
- Le calcul s'effectue en F64 jusqu'au snapshot device-space final F32. Les
  conversions F32 sont finies, vérifiées et conservatrices pour les bounds.
- La flèche maximale par défaut reste `0.25 px`; toute autre erreur admise est
  exprimée par une policy F64 explicite, jamais par une constante renderer.
- Chaque boucle de subdivision ou expansion possède profondeur, nombre de
  segments, vertices, indices et bytes maximaux, débités avant émission ou
  dédoublonnage.
- Un résultat de préparation est `Ready`, `Empty`, `InvalidScene` ou
  `ResourceLimitExceeded` avec une raison stable.
- Après `Ready`, aucun mapper, tessellator, stroke expander, clip classifier ou
  fallback legacy ne peut être rappelé.
- Une divergence entre graph, payload, scratch, pipeline, ressources ou ordre
  est terminale et transactionnelle : aucun rendu partiel et aucun lease perdu.
- L'ordre de paint et les opérations atomiques sont conservés sur la frame
  entière. Aucun draw admissible n'est extrait isolément d'une frame refusée.
- Les matériaux restent `SolidColor`, le blend `SrcOver`, la cible sRGB 1× et
  la profondeur de layer racine. W5 et W6 étendront ces axes indépendamment.

## 5. W4d.1 — strokes et hairlines

### 5.1 Modèle mathématique

`:math:geometry` introduit des valeurs immuables équivalentes à :

- `PathStrokeStyleF64` : largeur, cap, join, miter et dash optionnel ;
- `PathStrokeWidthF64` : `Finite` ou `Hairline` ;
- `PathStrokeDashF64` : intervalles pairs finis non négatifs, somme strictement positive, et phase finie ;
- `PathStrokeLimitsI32` et `PathStrokePolicyF64` ;
- `PathStrokeGeometryF32` : contours/edge fans, coûts I64, bounds et scissor I32 ;
- `PathStrokePreparationResult` avec raisons stables.

Les caps sont Butt, Round et Square. Les joins sont Miter, Round et Bevel. Un
miter dépassement utilise le fallback bevel sémantique, pas un refus. Les arcs
de caps/joins sont subdivisés selon la flèche device-space et débitent les mêmes
budgets que les segments source.

Le dash est appliqué sur l'arclength source avant l'expansion. Pour une
hairline, il découpe la centerline source avant projection puis chaque fragment
conserve sa coverage nominale d'un pixel device. La phase est réduite modulo la
longueur totale du pattern avec une convention stable pour les valeurs
négatives. Chaque sous-contour fermé conserve la continuité de phase définie
par la sémantique publique. Seul `PathEffectNode.Dash` appartient à W4d ;
Corner, Discrete, Path1D, Path2D et Trim restent des effets géométriques
ultérieurs et sont diagnostiqués avant `Ready`.

Un stroke de largeur finie est expansé en source-space, puis son outline est
transformé et aplati en device-space. Le dash conserve des intervalles de
primitives paramétriques source et l'outline conserve des offsets/caps/joins
paramétriques : il est interdit d'aplatir la centerline puis de l'offseter. La
certification de flèche de l'outline inclut explicitement la largeur et le
miter, puis la magnification/projective deviation de la transformation. Ce
choix conserve correctement les largeurs anisotropes sous affine ou
perspective. Une hairline est différente : la centerline est projetée en
device-space puis reçoit une coverage nominale d'un pixel device, indépendante
de l'échelle de la CTM.

Un `PathStrokeWorkLedgerI64` transactionnel unique traverse dash, outline,
projection, union topologique et finalisation fill. Il vérifie les limites path
et frame avant chaque évaluation, subdivision, fragment, candidat topologique,
vertex/index ou allocation. `Ready`/`Empty` publient les snapshots d'usage path
et frame; aucune étape ne réinitialise ou ne réadditionne tardivement son propre
compteur. Dans une frame mixte W4d, les fills W4c sont eux aussi préparés par le
worker ledger-aware et débitent attempted units, vertices, indices et snapshot
bytes avant émission; ils ne sont pas post-comptabilisés par le planner.

`STROKE_AND_FILL` produit d'abord dans `:math` l'union géométrique certifiée de
la région fill non inverse et de l'outline du stroke. Cette union passe par le
moteur topologique borné F64/F32 et publie une seule géométrie de fill ; un
échec de certification ou de budget est explicite avant `Ready`. Il est
interdit d'additionner deux windings dans le même stencil — des contours de
trou opposés pourraient s'annuler — ou de rendre fill puis stroke avec deux
`SrcOver`, ce qui doublerait l'alpha dans la zone de recouvrement. Conformément
au contrat Skia, `STROKE_AND_FILL` avec une largeur nulle se réduit à `FILL` :
il n'ajoute pas une hairline au fill. Les variantes inverse sont prises en
charge avec les inverse path draws de W4e.

### 5.2 Graph et capability

W4d.1 ajoute une capability hard-edge exacte pour les frames path
`STROKE`/`STROKE_AND_FILL`. Le draw planifié référence uniquement
`PathStrokeGeometryF32`, son style scellé, son scissor et son ordinal.

Un contour démontré simple peut suivre un direct mesh. Le cas général utilise
une paire `StencilProducer -> StencilCover`. `STROKE_AND_FILL` transporte la
géométrie d'union unique produite par `:math`, jamais plusieurs producers
winding supposés équivalents à une union. Les ressources V/I/U/D24S8 et leurs
capacités sont décidées avant `Ready` sur les coûts publiés par `:math`.

W4d.1 commence dans l'enveloppe transform W4c déjà prouvée. W4d.2 élargit
ensuite fills et strokes avec un transform commun, sans dupliquer l'expansion.

## 6. W4d.2 — transforms généraux et path coverage AA

### 6.1 Transform F64 commun

`:math:matrix` introduit le snapshot `Matrix3x3F64` construit depuis les neuf
coefficients de `Matrix3x3F32`, ainsi qu'une classification typée : Identity,
AxisAlignedAffine, GeneralAffine et Perspective. Aucun `String` ne traverse la
frontière de planification.

Les règles sont :

- rotation, reflection, skew et affine anisotrope sont préparés directement en
  F64 device-space ;
- la perspective évalue les segments paramétriques en coordonnées homogènes,
  subdivise selon l'erreur après division et certifie que l'intervalle de `w`
  ne contient pas zéro ;
- un segment dont `w` reste de signe non nul est admissible, quel que soit ce
  signe ;
- un crossing de l'horizon `w = 0`, une projection non finie ou non bornable
  est classé avant `Ready` par une raison explicite ; il n'est jamais traité
  comme un affine ni tronqué silencieusement ;
- les bounds sont calculés sur les segments projetés, puis intersectés avec la
  target et le clip simple par arithmetic checked.

Les SVG arcs sous affine continuent d'utiliser la décomposition de covariance
W4c. Sous perspective, elles sont évaluées comme courbes paramétriques puis
subdivisées ; elles ne sont pas reconstruites comme arcs elliptiques après
projection.

La capability hard-edge W4c existante reste inchangée pour la compatibilité de
son contrat. Une capability versionnée générale accepte les mêmes snapshots
de fill/stroke préparés avec affine général ou perspective bornée.

### 6.2 Coverage AA

L'AA de paths est une capability séparée, jamais une promotion du rendu
hard-edge 1×. Elle couvre fills, strokes, hairlines et `STROKE_AND_FILL` avec :

- un `SamplePlan.Multisample4` public dans `:gpu-plan` ;
- une target couleur MSAA 4×, une D24S8 4× et un resolve explicite vers la
  target logique 1× ;
- `CoveragePlan.StencilAA4` ;
- pour chaque draw demandé hard-edge dans une frame AA4, un mask binaire
  linéaire 1× produit par la route hard 1×, puis lu sans filtrage aux coordonnées
  pixel entières par un cover 4× qui diffuse la même valeur 0/1 sur les quatre
  samples. Le mask et sa D24S8 1× éventuelle sont réutilisables uniquement entre
  groupes atomiques séquentiels ;
- des producer/cover atomiques; exactement le dernier render pass physique qui
  produit la couleur porte la target logique comme `resolveTarget`. Sample
  count, formats, load/store et lifetimes sont scellés dans le graph. Le
  resolve WebGPU n'est pas une commande séparée mais la fin de ce dernier
  render pass ;
- un calcul de pic incluant les capacités poolées, pas seulement les tailles
  logiques demandées.

Le renderer peut réutiliser ses pipelines `StencilAA` et son support MSAA 4×
uniquement après authentification de tous les faits W4d.2. L'absence de support
4×, de D24S8 multisamplée, de mask 1× sampleable ou de resolve compatible est
un gap de capability avant acquisition. Une même preuve typée
`PlanTextureResolveSupport` porte le `PlanTextureFormat` exact et certifie aussi
bien le resolve couleur que le resolve d'un mask W4e.

Aucune tolérance pixel n'est ajoutée. Les preuves byte-exact utilisent des
fixtures dont le résultat de couverture est invariant pour les positions
d'échantillons légales ; les autres fixtures prouvent les invariants publics
de monotonicité, symétrie et absence de halo sans dépendre d'une position GPU
cachée. La convergence Skia exhaustive reste W7.

## 7. W4e — clips complexes

### 7.1 Capture sémantique

`ClipStackOp` et `ClipEntry` remplacent la combinaison
`perspectiveCaptureRefusal + transformClass: String` par un
`ClipTransformSnapshot` typé associé à chaque opération. Une valeur
`Known(Matrix3x3F32)` porte la CTM capturée ; une valeur `LegacyUnavailable`
conserve les anciens faits sans inventer une matrice. La géométrie source reste
immuable ; elle n'est plus prémappée par une route F32 différente selon la
classe de transform.

Cette évolution est sérialisée :

- le format public `Picture` reste v8 et son marker IR reste stable ;
- le writer passe le `SceneArchiveCodec` au schema v2 et le decoder accepte les
  schemas v1 et v2 ;
- le schema v1 devient `LegacyUnavailable` à la lecture, avec les anciens faits
  conservés et sans inventer une matrice perdue ;
- les anciens payloads v8 antérieurs au marker IR continuent de passer par le
  reader historique existant ;
- les anciens clips marqués perspective sans matrice restent explicitement non
  promouvables, jamais assimilés à Identity.

Ce changement est motivé par un comportement public et reçoit des tests de
round-trip et de replay. Aucun test source-shape ou reflection n'est admis.

### 7.2 Préparation math et composition

`:math:matrix` projette chaque entrée de clip avec le transform F64 commun,
puis transmet une `ClipDeviceGeometryF64` sans matrice à
`:math:geometry`. Ainsi `:math:geometry` ne dépend jamais de `:math:matrix` et
le graphe de modules reste acyclique. Rect, RRect et Path deviennent des
snapshots device-space portant operation Intersect ou Difference, AA, inverse
fill, coûts, bounds et scissor conservateur. Une chaîne transactionnelle de
`ClipWorkLedgerI64` module-locaux transmet des snapshots immuables stack/frame
de `:math:matrix` vers `:math:geometry`, puis entre toutes les stacks distinctes
d'une frame. Elle débite avant émission les comptes d'entrées, attempted units,
vertices, indices et snapshot bytes, avec limites entry et frame I32/I64
explicites. Les stacks réutilisées ne sont pas préparées ni débitées une seconde
fois; aucun ledger mutable ne traverse une frontière de module. Chaque
`ClipDeviceInputF64` transformé porte toutefois le snapshot immuable du travail
déjà consommé par son entrée : le sous-ledger geometry reprend ce seul snapshot
pour cumuler projection et tessellation contre la même limite entry, sans y
mêler les entrées précédentes.

W4e étend aussi les draws `GeometryNode.Path` dont le `FillRule` est
`INVERSE_WINDING` ou `INVERSE_EVEN_ODD`. Leur domaine fini est la target
intersectée avec le clip actif ; sans clip, il s'agit de la target entière. Un
path inverse vide couvre donc ce domaine au lieu de devenir `Empty`. Le graph
porte explicitement le domaine I32 et l'inversion :

- en hard-edge, le producer inscrit l'intérieur fini du path, puis un cover du
  domaine colore les fragments où le stencil indique l'extérieur et le remet
  à zéro ;
- en AA, la couverture du path est produite dans un mask puis inversée dans le
  domaine avant multiplication par le clip final ;
- un inverse `STROKE_AND_FILL` construit dans `:math:geometry` l'intérieur fini
  à exclure `F \ O`, car `¬F ∪ O = ¬(F \ O)`, avec un seul résultat de coverage
  et une seule application couleur. Si `F \ O` est vide — y compris pour une
  source non vide de couverture nulle — un état explicite `Zero` fait couvrir
  tout le domaine au lieu de devenir `Empty`.

Le scissor d'un inverse draw ne peut jamais être dérivé des seuls bounds du
path source : il vaut le domaine target/clip planifié. Le planner refuse toute
preuve qui réutilise le scissor conservateur d'un fill non inverse.

Le planner choisit la stratégie minimale prouvée :

1. aucun clip : aucune ressource ;
2. intersection d'un rect entier non-AA : scissor ;
3. clip hard-edge simple borné : stencil si son contrat tient dans D24S8 ;
4. toute composition AA, Difference, inverse ou multi-op générale : coverage
   mask ordonnée.

Le mask général utilise un format linéaire explicitement capability-gaté. Une
passe d'initialisation typée écrit WideOpen (`1.0`) dans le premier
accumulateur sur le domaine. Un producer écrit ensuite la couverture de
l'entrée ; un fold lit l'accumulateur précédent et écrit l'autre texture de
ping-pong :

```text
Intersect: next = previous * source
Difference: next = previous * (1 - source)
```

L'accumulateur initial représente WideOpen dans la target/scissor. L'inverse
fill inverse la couverture de la géométrie dans ce domaine avant le fold. Deux
accumulateurs et un scratch resolved 1× sont budgétés lorsque la composition
générale l'exige. Un producer Path/RRect AA ajoute son propre scratch mask
RGBA8 linear 4×, son resolve 1× et une D24S8 mask 4×; ces ressources sont
distinctes de la color target MSAA et de la D24S8 4× de la scène. Un producer
path hard-edge utilisant stencil ajoute une D24S8 mask 1×. Le resolve mask est,
comme le resolve couleur, le `resolveTarget` du render pass producteur et non
une commande séparée. Les formats, sample counts, usages
RenderAttachment/Sampled, bytes, passes et lifetimes sont des faits
`:gpu-plan`; aucun rôle de ressource n'est implicite.

Le draw consommateur multiplie sa coverage géométrique par le mask final. Le
mask ne modifie pas la couleur et ne crée pas une seconde application de
`SrcOver`.

### 7.3 Atomicité et réutilisation

Une clip stack identique peut être préparée une fois dans une frame et partagée
par plusieurs draws si son identité sémantique, extent, format, sample plan et
transform capturé sont identiques. Cette réutilisation est décidée par le
planner ; le renderer ne déduplique pas.

Chaque séquence producer/fold/consumer est ordonnée par dépendances explicites
du graph. Une divergence ou une ressource manquante annule la frame avant le
premier submit.

## 8. Budgets et diagnostics

Les policies de stroke, transform et clip publient leurs limites I32/I64 dans
`:math`. Le planner ajoute :

- limites de draws et d'entrées de clip par frame ;
- maximum de passes et groupes atomiques ;
- capacités vertex/index/uniform ;
- bytes des targets MSAA, D24S8 et masks de ping-pong ;
- contraintes de sample count, formats et usages du device ;
- taille hôte représentable et dynamic offsets.

Les diagnostics distinguent au minimum : InvalidScene, UnsupportedCapability,
GeometryLimit, PassLimit, HostSizeOverflow, DeviceSizeOverflow,
PerspectiveHorizonCrossing, SampleCountUnavailable et MaskFormatUnavailable.
Ils sont stables, observables publiquement et émis avant `Ready` lorsque le fait
vient de la scène ou du device.

## 9. Preuves et tests

Les tests suivent TDD et observent comportement ou valeurs publiques. Sont
interdits : source-shape, reflection, accès privé, call-count et autres tests
d'infrastructure du code.

### Math JVM/JS

- caps, joins, miter fallback, contours ouverts/fermés et dégénérés ;
- dash pairs, phase négative, wrap, coupures de contour et limites ;
- hairline indépendante du scale ;
- affine rotation/skew/reflection/aniso ;
- perspective bornée, extrema, subdivision, horizon et overflow ;
- nomenclature et snapshots immuables par comportement public ;
- clip Intersect/Difference/inverse et bounds conservateurs.

### Planner et renderer

- sélection atomique de frame et diagnostics exacts ;
- graph producer/cover/resolve et clip producer/fold/consumer ;
- ressources, pics, alignements, lifetimes, pool, completion et rollback ;
- authentification des snapshots et refus terminal des graphes forgés ;
- aucune invocation d'une route legacy après `Ready`, prouvée par résultat et
  invariants exposés, pas par call-count.

### Surface et pixels hors GM

- oracle CPU indépendant des helpers de production ;
- pixels byte-exact pour hard-edge et fixtures AA déterministes ;
- strokes cap/join/dash/hairline, affine/perspective bornée ;
- ordre `SrcOver`, `STROKE_AND_FILL` sans double alpha ;
- clips Intersect/Difference/path/inverse, hard-edge et AA ;
- RGBA/BGRA, scissor, tailles limites et transactions refusées.

Les gates globales doivent conserver exactement les noms de failures du ledger
W4c, avec zéro error et aucun nouveau nom. Les failures connues ne sont ni
masquées, ni rebaselinées.

## 10. Livraison séquentielle

La stack prévue est :

```text
codex/w4c-path-fills
  -> codex/w4d-strokes-hairlines
  -> codex/w4d-general-transform-aa
  -> codex/w4e-complex-clips
```

Chaque branche reçoit sa spec/son plan détaillé, ses commits Terra par tâche,
ses revues Sol spec puis quality, ses preuves fraîches et sa PR stackée. Une
tranche ne commence qu'après revue propre de la précédente, mais aucune attente
utilisateur n'est requise entre les tranches.

Le controller ne redemande un arbitrage que si une découverte impose :

- une rupture d'API publique non couverte par la migration de clip décrite ici ;
- une contradiction vérifiée avec les semantics Skia ;
- une nouvelle failure globale hors ledger ;
- l'abandon d'une garantie numérique, d'atomicité ou de budget de cette spec.

Les détails locaux — noms privés, découpage de fichiers, constantes dérivées,
choix de fast path et réutilisation de pipelines — sont décidés par les agents
et validés par Sol sans checkpoint utilisateur.

## 11. Critères de fermeture W4

W4 peut être déclarée atteinte lorsque :

- fills, strokes, hairlines et `STROKE_AND_FILL` admis sont préparés par `:math` ;
- identity, affine général et perspective bornée suivent le même contrat F64 ;
- hard-edge et AA 4× sont des plans explicites, sans substitution silencieuse ;
- inverse path draws et clips rect/path/RRect Intersect/Difference/inverse sont
  composés dans un sous-graphe stencil/mask ordonné ;
- ressources, budgets et lifetimes sont complets avant `Ready` ;
- aucun fallback ou recalcul géométrique n'existe après `Ready` ;
- les preuves JVM/JS, planner, renderer et Surface sont fraîches ;
- le ledger global ne contient aucun nouveau nom ;
- le status W04 publie les exclusions résiduelles classées ;
- la revue Sol finale ne contient aucun finding Critical ou Important.

La dette SDF des RRect non nuls reste réservée à W7. Les crossings projectifs
réellement non bornés à `w = 0` restent une limitation classifiée, pas un refus
non documenté. W5, W6 et W7 peuvent ensuite combiner materials, effets et GMs
avec ce socle sans rouvrir la propriété de la géométrie.
