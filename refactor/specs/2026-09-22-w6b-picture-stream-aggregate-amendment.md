# W6b — amendement `PictureStreamAggregateV1`

Date : 2026-09-22

Autorités parentes :

- [`2026-09-16-w6-layers-effects-design.md`](2026-09-16-w6-layers-effects-design.md)
- [`2026-09-22-w6b-w6e-stacked-delivery-design.md`](2026-09-22-w6b-w6e-stacked-delivery-design.md)

Branche : `codex/w6b-blur-masks-shadows`

## 1. Problème découvert

Le planning W6b sait représenter un draw filtré et une `Picture` contenant des
draws filtrés. Il ne peut pas encore représenter fidèlement une `Picture`
filtrée qui contient elle-même une `Picture` filtrée.

Traiter le parent comme une feuille laisse les occurrences descendantes sans
consommateur. Aplatir seulement les descendants filtrés supprime les siblings
non filtrés et perd l'ordre. Rejouer tout le `SceneSnapshot` pour chaque
descendant duplique les siblings. Refuser ce cas serait sûr, mais laisserait
W6b incomplet et reporterait une frontière structurante à Task 3.

La décision retenue est donc d'ajouter un agrégat de command stream typé,
backend-neutral et gelé par `:gpu-plan` avant toute allocation native.

## 2. Décision

`PictureStreamAggregateV1` représente une occurrence de draw `Picture` comme
un scope d'exécution ordonné et gelé. Son `executionMode` est arrêté par
`:gpu-plan`, et non choisi par le renderer :

- `INLINE_CURRENT_TARGET` est obligatoire lorsque le `drawPicture` capturé a
  `paint == null`. Les enfants déjà planifiés écrivent alors dans la cible
  courante du parent, dans l'ordre enregistré. Il n'y a ni cible transparente,
  ni composite synthétique du Picture. Un `DST_OUT`, un blend dépendant de la
  destination, ou un `saveLayer(initWithPrevious = true)` enfant observe donc
  bien les pixels produits par ses siblings précédents.
- `ISOLATED_SOURCE` est obligatoire lorsque le `drawPicture` a un `Paint`, y
  compris un paint dont les attributs W6b sont finalement neutres. Il possède
  une cible d'agrégat transparente, la scelle en source immuable, puis applique
  le paint du Picture et un unique composite terminal dans la cible courante.
  Les mask/image filters du paint restent dans ce mode ; ils ne sont jamais
  répartis sur les enfants.

La hiérarchie et l'ordre source restent typés dans les deux modes. L'interdit
d'aplatissement vise la perte de scope, de paint ou d'ordre ; il n'interdit pas
le playback inline requis par la sémantique du `drawPicture` sans paint.

L'agrégat :

- possède une identité d'occurrence distincte de l'identité canonique de la
  `Picture` capturée ;
- référence le `SceneSnapshot` capturé sans le recopier ni le modifier ;
- conserve le paint, le transform, le clip, le cull et la chaîne de Pictures
  extérieures de l'occurrence ;
- contient toutes les commandes enfants, filtrées ou non, exactement une fois
  et dans l'ordre source ;
- produit, seulement en mode `ISOLATED_SOURCE`, une ressource
  `PictureAggregateSource` scellée ;
- applique alors le mask filter, l'opération matériau/couleur W5, l'image
  filter, le clip différé et le blend du parent exactement une fois ;
- peut être imbriqué dans un autre agrégat sans aplatir ni inverser l'ordre.

Ce contrat concerne le draw public d'une `Picture`. Il est distinct de la
famille `ImageFilter.Picture`, qui reste réservée à W6d.

## 3. Contrat capturé et contrat planifié

Aucune nouvelle donnée wire n'est nécessaire. Picture 14/schema 8 continue de
capturer le `SceneSnapshot`, ses commandes, ses paints et sa table de filtres.
`PictureStreamAggregateV1` est une vue planifiée d'une occurrence capturée ; il
n'est ni sérialisé dans Picture, ni exposé par l'API publique.

Le plan introduit des IDs entiers typés, sans géométrie privée :

```kotlin
@JvmInline
public value class PictureStreamAggregateIdI32(public val valueI32: Int)

@JvmInline
public value class PictureStreamEntryIdI32(public val valueI32: Int)
```

Un agrégat possède au minimum :

```text
PictureStreamAggregateV1
  idI32
  executionMode: INLINE_CURRENT_TARGET | ISOLATED_SOURCE
  sourceSceneCanonicalId
  sourcePictureOccurrenceIdI32
  outerPicturePathI32[]
  outerEvaluationMappingF64
  recordedInnerClip / deferredCompositeClip / cullContentBound / demandRegion
  parentTargetId
  aggregateTargetId? / sealedSourceId? / sealedSourceGenerationI64?
  entries[]
  bounds: knownContent / desiredOutput / requiredInput / producedOutput
```

Les `entries` forment une séquence immuable et monotone :

```text
DrawEntry       -> coverage brute, lane matériau W5, filtre éventuel, composite
PictureEntry    -> référence vers un PictureStreamAggregateV1 enfant
LayerEntry      -> scope W6a enfant et son composite terminal
```

Une entry ne contient jamais un objet public `Picture`, `Paint`, `ImageFilter`
ou `MaskFilter`. Elle référence uniquement les snapshots `:render-ir`, les
mappings `:math`, les source lanes W5 et les ressources/pass IDs `:gpu-plan`.

Chaque élément visuel porte un locator immuable
`PictureSourceLocatorV1(pictureOccurrenceIdI32, sourceCommandIndexI32)` et un
`FramePlannedCommandIdI32` unique dans la frame. Le premier identifie le
command stream d'une occurrence de Picture ; le second est la clé des bindings
W4/W5, des sources, des générations et des composites. Deux occurrences du
même `sourceSceneCanonicalId`, avec des transforms ou paints différents, ne
partagent donc jamais par erreur une lane indexée par le seul
`sourceCommandIndexI32`. `sourceSceneCanonicalId` et `outerPicturePathI32[]`
restent des provenances : aucun renderer ne les utilise comme clé d'exécution
ou de cache.

La construction couvre exhaustivement le vocabulaire actuel de
`SceneCommand` ; une commande n'est jamais silencieusement perdue :

| `SceneCommand` capturée | Représentation W6b | Règle d'admission |
| --- | --- | --- |
| `Draw` hors `GeometryNode.Picture` | `DrawEntry` visuelle | Un terminal W4/W5/filter déjà gelé écrit une fois dans la cible du scope. |
| `Draw(GeometryNode.Picture)` | `PictureEntry` | Construit récursivement un enfant `INLINE_CURRENT_TARGET` ou `ISOLATED_SOURCE` selon le paint capturé. |
| `Clear` | `ClearEntry` visuelle | Écrit dans la cible courante du scope ; elle n'est pas remplacée par une annotation. |
| `DrawColor` | `DrawColorEntry` visuelle | Conserve color, blend, transform et clip gelés. |
| `SetTransform` | état consommé | Met à jour le mapping immuable des entrées suivantes ; aucun draw n'est créé. |
| `SetClip` | état consommé | Met à jour le `recordedInnerClip` des entrées suivantes ; aucun clip externe n'est anticipé. |
| `BeginLayer` … `EndLayer` | un `LayerEntry` à intervalle fermé | Possède exactement l'intervalle équilibré, son target et son composite W6a terminal. |
| `Annotation` | no-op explicite | Métadonnée sans effet raster, conservée dans la couverture d'index. |
| `State` | refus stable existant | `w6a.layer.unsupported_child`, avec locator de l'occurrence et index source. |
| `Readback` | refus stable existant | `w6a.layer.unsupported_child`, avec locator de l'occurrence et index source. |

La validation exige une partition exacte des indices sources : chaque index est
soit une entry visuelle, soit un état consommé/no-op explicite, soit le membre
d'un unique intervalle `LayerEntry` fermé, soit l'objet d'un refus stable. Elle
vérifie les piles Begin/End, les intervalles disjoints, l'ordre monotonique et
l'absence d'alias entre occurrences répétées.

## 4. Ordre d'exécution gelé

Le `RenderGraph` encode l'une des deux séquences, sans décision tardive :

1. `INLINE_CURRENT_TARGET` exécute les entries visuelles dans la cible courante
   du parent. Une `PictureEntry` inline ouvre seulement son sous-stream typé et
   referme ce sous-stream après son dernier terminal enfant ; elle ne crée pas
   de surface transparente et ne composite pas une seconde fois.
2. `ISOLATED_SOURCE` initialise `PictureAggregateTarget` à transparent black,
   exécute les entries dans l'ordre source, scelle sa génération, transforme la
   source agrégée avec le paint parent, puis composite une fois dans la cible
   courante.

Dans les deux modes, une entry filtrée suit coverage → mask → matériau W5 →
image filter → terminal ; une entry non filtrée suit sa source W5 puis son
terminal. Un enfant isolé doit être complètement scellé et son unique terminal
écrit dans la cible de son parent avant le sibling suivant. Un enfant inline
conserve la même chronologie mais ses opérations écrivent directement dans la
cible qui était courante à son entrée. Aucun résultat descendant ne peut écrire
dans la cible du grand-parent en contournant son scope.

Les frontières nommées sont typées, mais n'impliquent pas une seconde passe
native :

- `PictureAggregateBegin` crée la cible logique du mode isolé ;
- `PictureAggregateSeal` est une frontière graph/witness qui publie une
  génération source ; une copie native n'est permise que si le plan gelé la
  contient déjà ;
- `PictureAggregateChildComposite` désigne le même `PlanPassId` terminal que
  l'écriture effective d'une entry enfant isolée. Il ne crée jamais un composite
  additionnel autour d'un `FilterComposite` ou `LayerComposite` existant ;
- un sous-stream inline référence ses terminaux enfants effectifs et ne reçoit
  aucun composite synthétique.

Chaque entry non vide a un terminal effectif unique. Pour une entry isolée, le
terminal référencé par `PictureAggregateChildComposite` est ce même pass ; pour
une entry inline, le terminal est celui du dernier travail visuel de son
sous-stream. Une Picture vide est un no-op explicite sans pass. Ainsi aucun
pass terminal effectif n'est possédé par deux entries, et aucun entry ne double
un composite existant.

Le renderer ne reconstruit ni la séquence, ni la hiérarchie, ni les bounds, ni
le choix inline/isolé. Les Tasks 3–6 matérialisent exactement ces opérations
gelées.

## 5. Transforms, clips, coverage et matériaux

Chaque scope distingue quatre valeurs qui ne se substituent pas :

- `recordedInnerClip` est le clip actif dans l'espace device de la Picture au
  moment où sa commande enfant a été enregistrée. Il s'applique à cette
  commande ou à son scope enfant avant son propre filtre.
- `deferredCompositeClip` est le clip actif à la frontière du `drawPicture`
  extérieur. En mode isolé, il s'applique seulement au composite final après
  le paint/image filter du parent ; il ne retire pas des samples nécessaires au
  filtre du parent.
- `cullContentBound` est l'indice de contenu/cull enregistré. Il peut borner le
  travail source connu, mais ne coupe jamais par lui-même le halo produit par
  le filter du parent.
- `demandRegion` est une demande de sortie, propagée en arrière du
  `deferredCompositeClip ∩ targetDomain` à la source agrégée, puis aux enfants
  et à leurs propres filtres. C'est elle, et non le cull, qui pilote les
  `desiredOutput`/`requiredInput` du parent.

La convention de matrices est explicite. Si `Mrecorded` amène une géométrie
locale dans le device de sa Picture et `Mouter` représente la chaîne des
drawPicture extérieurs, le mapping d'évaluation est `Meval = Mouter ∘
Mrecorded`, avec la convention de composition de `LayerMappingF64`. Un clip
capturé dans le device enregistré reçoit `Mouter` exactement une fois ; il ne
reçoit pas une seconde fois `Mrecorded`. Les coordonnées locales W5 utilisent
`Meval` et la chaîne de local matrices déjà capturée par W5, sans recomposition
au renderer. Chaque origin de texture effective et chaque rectangle déjà
converti en coordonnées target-locales sont gelés via `LayerMappingF64` et
`FilterBoundsPlanV1`; aucune soustraction device→target n'est redécidée côté
native.

Pour une source isolée scellée `S = (rgbPremul, a)`, la frontière du paint
parent est définie sans re-shader les enfants :

```text
Cparent(x) = a(S(x))                              // coverage parent
Mparent(x) = MaskFilter(Cparent)(x), ou Cparent(x) sans mask
N(x)       = rgbPremul(S(x)) / a(S(x)) si a(S(x)) > 0, sinon transparent black
Pmask(x)   = (N(x) * Mparent(x), Mparent(x))       // alpha remplacé, non multiplié deux fois
Pparent    = ImageFilter(Color/alpha/W5(Pmask))
```

Les échantillons hors domaine de la source agrégée sont transparent black. Un
mask ne relit jamais les matériaux des enfants et n'utilise jamais `a(S)` comme
un second multiplicateur du `rgbPremul` déjà produit. Ainsi un mask identité
préserve les enfants semi-transparents superposés, les trous transparents et
leur alpha prémultipliée ; un filtre du parent reçoit une source couleur/alpha
dûment définie. La lane graph-texture porte le sample domain scellé et ses
coordonnées pour que le résultat soit le même pour une origine target négative
ou translatée.

`MaterialSourceConstructionV4` / `FrameSourceLayoutV4` restent l'unique
autorité W5. Ils publient un `GraphTextureSourceOperandV1` minimal, gelé avant
publication : `sealedSourceId`, `sealedSourceGenerationI64`,
`targetOriginDeviceI32`, rectangles target-locaux, `LayerMappingF64`,
`MaterialPlanRef`, binding uniforme, opération alpha/couleur et `BlendPlan`
final. Cet operand ne porte ni `SceneSnapshot`, ni `Picture`, ni recette de
replay renderer. Il transforme la source RGBA scellée du parent ; il ne réémet
aucune source lane enfant.

Le paint du `drawPicture` extérieur consomme une fois alpha, color filter,
matériau W5/coordonnées, mask filter, image filter, clip différé et `BlendPlan`.
Les attributs géométriques du paint (style, stroke, path effect et anti-alias)
ne s'appliquent qu'aux draws enfants qui les ont capturés et sont ignorés à la
frontière Picture. Backdrop/filtered previous, F16/HDR, et les familles W6c ou
W6d restent refusés avec leurs diagnostics existants. Ni alpha, ni color
filter, ni mask/image filter, ni blend du parent ne peut être propagé dans
chaque enfant.

## 6. Bounds, ressources et budget

Chaque entry conserve les quatre régions W6 : `knownContent`,
`desiredOutput`, `requiredInput` et `producedOutput`. L'agrégat calcule :

- `knownContent` par union ordonnée des outputs enfants réellement initialisés ;
- `desiredOutput` depuis la `demandRegion` issue du clip différé et du domaine
  de la cible, sans intersection automatique avec le cull ;
- `requiredInput` par propagation inverse de cette demande à travers le filtre
  parent puis les filtres enfants ;
- `producedOutput` depuis l'union enfant, le paint/filter parent et leur halo
  effectivement produit.

Les bounds physiques de `PictureAggregateSource` sont scellées en F64 puis
projetées outward en I32 par `:math`. Elles ne sont pas remplacées par l'étendue
du parent et ne tronquent pas un blur ou une shadow.

Une cible d'agrégat isolée a un état immuable et versionné :

```text
uninitialized
  → accumulating(targetId, versionI64 = 0)
  → accumulating(targetId, versionI64 + 1)    // un terminal enfant effectif
  → sealed(sealedSourceId, sourceGenerationI64 = versionI64)
```

Seul `PictureAggregateBegin` peut passer à `accumulating`. Chaque terminal
enfant ordonné incrémente une fois `versionI64`. `PictureAggregateSeal` est
autorisé une fois, seulement après tous les enfants, et publie le couple exact
`(sealedSourceId, sourceGenerationI64)`. Toute lecture filtre/matériau parent
requiert ce couple ; toute lecture avant `sealed`, écriture après `sealed`,
double seal, source d'un autre agrégat, ou composite dans une cible non parente
est invalide. Les dépendances graph relient begin → tous les terminaux → seal →
le premier consommateur parent. Les entrées inline n'ont pas de cible/seal
propre : elles restent dans l'état/version de leur cible courante.

Le budget I64 pessimiste inclut :

- la cible de chaque agrégat actif ;
- les sources coverage/W5 et FilterTargets de ses entries ;
- les agrégats enfants jusqu'à leur composite terminal et leurs générations
  scellées jusqu'au dernier lecteur parent ;
- les uniforms, matériaux, LUTs, samplers et leases associés.

Le witness lie, pour chaque composite, le pass sémantique et la ressource,
l'origin, le rect target-local, le clip, le `BlendPlan`, le snapshot de
destination, load/store, usages, version destination et binding physique déjà
typés. Il prouve la bijection des terminaux, le propriétaire parent unique, les
dépendances et la contention de lifetime. `PlanPhysicalLayoutV1` garde les
slots W6b distincts et pessimistes : une durée de vie logique peut finir au
dernier lecteur, mais un lease physique reste retenu jusqu'à la completion ou
la quarantine de la frame. Un cache hit ne réduit jamais le coût d'admission et
aucune réutilisation/aliasing précoce n'est introduite par cet amendement.

## 7. Validation et diagnostics

La publication du graph refuse avant allocation. Toute violation structurelle
du présent aggregate retourne `w6b.picture_stream.invalid` avec au minimum
`pictureOccurrenceIdI32`, `sourceCommandIndexI32` lorsque disponible,
`aggregateIdI32`, invariant violé et IDs de pass/resource impliqués. Les
diagnostics existants de capture, bounds/projection et budget restent leurs
codes propres ; ce code ne les remplace pas.

La validation vérifie notamment :

- ID d'agrégat ou d'entry absent/dupliqué ;
- cycle de Pictures, profondeur/nombre de commandes hors limites ou partition
  d'indices non exhaustive ;
- ordre non monotone, intervalle layer non équilibré, entry non représentée ou
  alias de `FramePlannedCommandIdI32` entre occurrences ;
- terminal d'enfant absent, doublement possédé, composite après le sibling
  suivant ou composite synthétique doublant un terminal existant ;
- state machine invalide : begin/seal cardinality, seal avant enfant complet,
  lecture avant seal, écriture après seal, génération source erronée ou parent
  non unique ;
- mapping/clip non fini ou projection I32 impossible ;
- source lane W5, material row ou uniforme manquant ;
- ressource/pass/binding physique, origin, rectangle, clip, blend, snapshot ou
  usage ne correspondant pas au pass sémantique ;
- budget I64 overflow ou supérieur à la limite.

Après ownership W6b, ces refus sont terminaux et ne tombent jamais en legacy.
Les exits natifs 133/134 restent `UNKNOWN` sans preuve indépendante.

## 8. Répartition des tâches

- **Task 2** est l'unique propriétaire du contrat gelé : modes inline/isolate,
  vocabulaire exhaustif, locators/IDs occurrence-locaux, transforms/clips/cull
  et demand, quatre régions, `GraphTextureSourceOperandV1`, ressources/passes
  typés, versions/seal, witness, slots/lifetimes et budget. Elle publie la
  validation de production `RenderGraph` et les refus/recovery publics, mais
  conserve `w6b.filter.native_execution_unimplemented` à la frontière native.
  Un selector de contrat `:gpu-plan` n'est permis que s'il appelle cette
  validation de production avec des valeurs graph réelles ; il ne peut employer
  ni mock, fake device, reflection, compteur, ni test d'infrastructure.
- **Task 3** matérialise les opérations image-only déjà gelées et fournit les
  pixels positifs Picture ordonnés/imbriqués, le contrôle `DST_OUT` de l'enfant
  inline, le contrôle d'enfant peint isolé, les clips/cull/origins/transforms,
  et le replay mémoire/wire. Elle ne peut ni créer/partitionner des entries, ni
  choisir un mode, ni recalculer bounds, pass, source ou budget.
- **Task 4** matérialise les opérations mask-blur gelées et prouve les pixels
  mask du parent et d'un descendant, sans modifier la hiérarchie Picture.
- **Task 5** matérialise les opérations mask shader/table gelées et prouve les
  pixels parent/descendant, material rows et mutations après capture, sans
  introduire une autorité W5 ou Picture concurrente.
- **Task 6** matérialise les opérations shadow gelées et qualifie le même
  aggregate pour shadows, B/B−1, nested-budget et late refusal atomique.
- L'acceptation positive complète de l'amendement est une gate de fin W6b, pas
  un prérequis pour accepter le refus native terminal de Task 2.
- **W6d `ImageFilter.Picture`** réutilise une source Picture immuable, mais ne
  remplace pas le contrat de draw Picture défini ici.

## 9. Preuves requises

Les preuves utilisent uniquement `Surface`, `Canvas`, `Picture`, Render et
Readback pour le comportement externe ; les selectors de validation de graph
autorisés à Task 2 appellent le contrat de production sans fake, mock,
reflection, compteur ni inspection statique :

1. un parent Picture filtré avec enfant Picture filtré, siblings filtrés/non
   filtrés alternés, ordre non commutatif et replay mémoire/wire ;
2. un enfant `paint == null` en `DST_OUT` qui efface le sibling précédent dans
   le parent filtré, et le contrôle voisin où un enfant peint reste isolé ;
3. du contenu hors clip final qui contribue à un blur visible dans le clip, et
   du output de blur/shadow hors cull mais dans la cible ;
4. origine target négative/translatée et transform extérieur non commutatif,
   avec mêmes pixels avant/après replay ;
5. recouvrements semi-transparents, trou transparent, mask + image filter du
   parent, et alpha/color-filter/blend parent appliqués une seule fois ;
6. mutations après capture, deux occurrences du même Picture sous transforms/
   paints distincts et générations source distinctes ;
7. cycle, commande imbriquée non admise, ordre, terminal unique, begin/seal,
   source/génération, cible sémantique/physique, B/B−1 et late refusal ;
8. refus terminal, sentinelle intacte et recovery sur la même `Surface` tant
   que la matérialisation positive n'est pas livrée.

## 10. Alternatives rejetées

### Exclusion transitoire

Un diagnostic terminal serait sûr mais reporterait une frontière indispensable
à Task 3 et rendrait W6b incomplet. Cette option n'est pas retenue.

### Aplatissement global

Aplatir toutes les commandes dans le parent complique les scopes, duplique les
paints extérieurs et rend les lifetimes Picture implicites. Cette option est
rejetée.

### Replay opaque dans le renderer

Transmettre seulement un `SceneSnapshot` et demander au renderer de le rejouer
recréerait un planner tardif sans bounds ni budget authentifiés. Cette option
est interdite par l'autorité W6.

## 11. Critères d'acceptation de l'amendement

L'amendement est fermé lorsque :

1. toute commande enfant est couverte exactement une fois par une entry, un
   état/no-op explicite, un intervalle layer ou un refus stable, dans l'ordre
   source et sans alias inter-occurrence ;
2. `INLINE_CURRENT_TARGET` conserve la destination courante et
   `ISOLATED_SOURCE` scelle son enfant puis le composite avant le sibling
   suivant, sans composite double ;
3. le filtre/paint parent consomme seulement le couple source/génération
   scellé, avec la frontière coverage/couleur/alpha explicitement définie ;
4. transforms, clips internes/différés, cull, demand, origins et matériaux
   conservent leurs espaces et leurs stages propres ;
5. les quatre régions, les versions, les dépendances et les lifetimes restent
   distincts jusqu'au native ;
6. graph, layout physique et budget sont gelés avant allocation, et les leases
   restent jusqu'à completion/quarantine ;
7. Tasks 3–6 matérialisent sans parcourir de `SceneSnapshot` ni recalculer une
   décision de structure, bounds, material, pass ou budget ;
8. les preuves positives et les validations/recovers de la section 9 sont
   présentes à la fermeture de W6b ;
9. `w6b.picture_stream.invalid` est stable pour les structures malformées,
   sans masquer les diagnostics bounds/budget/capture existants ;
10. aucune route legacy, autorité matériau concurrente ou système d'allocation/
    replay supplémentaire n'est introduit.
