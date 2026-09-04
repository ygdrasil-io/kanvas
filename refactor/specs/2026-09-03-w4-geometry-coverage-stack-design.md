# W4 — stack geometry/coverage et tranche W4a ScalarAA Rect

Date : 2026-09-03  
Statut : validé  
Branche de départ : `codex/w4a-scalar-aa-rect`, stackée sur W3  
Périmètre : `:math`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`
et preuves publiques associées  
Hors périmètre : `font`, décodage/encodage `codec`, `jpg-color-cube`, materials
W5, layers/effets W6 et convergence GM W7

## 1. Décision

W4 sera livré comme une stack de capacités verticales indépendantes :

1. W4a — rectangles fractionnaires avec couverture analytique `ScalarAA` ;
2. W4b — rectangles arrondis avec rayons normalisés dans `:math` ;
3. W4c — fills de paths par tessellation puis stencil/cover lorsque requis ;
4. W4d — strokes et hairlines ;
5. W4e — clips path, inverse et booléens via stencil ou coverage masks.

Chaque tranche reste initialement bornée au matériau solid, au blend `SrcOver`
et à la cible sRGB 1x. Cette borne permet à l'axe geometry/coverage d'avancer
sans absorber W5. Elle ne doit toutefois pas créer une nouvelle route qui
reclasse la scène dans `:gpu-renderer` : le planner possède la décision
sémantique, le renderer ne fait que valider et matérialiser le plan.

W4a publie la capability :

```text
solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1
```

Elle complète sans modifier la capability W3 :

```text
solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1
```

W4 n'est pas déclarée terminée à la fin de W4a. Sa gate globale exige une
progression indépendante des axes `PATH` et `CLIP`.

## 2. Pourquoi commencer par les rectangles fractionnaires

Trois découpages ont été considérés :

- rectangle fractionnaire seul ;
- rectangle fractionnaire et RRect dans la même tranche ;
- W4 monolithique jusqu'aux clips complexes.

Le premier est retenu. Le renderer possède déjà une lane native analytique
pour `Rect + ScalarAA`, un shader de couverture et l'ABI uniforme de 80 bytes.
W4a peut donc prouver le contrat architectural du planner avec un risque pixel
borné et sans ajouter de WGSL. Les RRects introduisent en plus la normalisation
des rayons ; les paths introduisent tessellation, winding et allocations de
taille variable. Les inclure maintenant masquerait les défauts de frontière
derrière plusieurs algorithmes nouveaux.

W4a n'est pas un prototype jetable. Ses contrats de sélection, ressources,
budgets, lifetimes et absence de fallback sont ceux que les tranches suivantes
étendront.

## 3. Frontières de responsabilités

```text
SceneSnapshot
    │
    ▼
CapabilityCompilerChain ── classification handle-free
    │ capability sélectionnée
    ▼
GpuPlanCompiler ────────── géométrie device + coverage + ressources + budget
    │ RenderGraph Ready
    ▼
GpuPlanTaskListLowerer ─── validation structurelle + matérialisation ABI
    │ task list native
    ▼
lane analytique existante ── encode, submit, completion, readback
```

### 3.1 `:math`

`:math` reste l'unique propriétaire des valeurs géométriques. W4a réutilise
`RectF32`, `RectI32`, `Point2F32` et `Matrix3x3F32`. Aucun équivalent GPU de
rectangle, bounds ou scissor n'est créé dans `:gpu-plan`.

Les noms de nouveaux types géométriques futurs suivent la nomenclature de
précision et de représentation `I32`, `I64`, `F32`, `F64`. W4b placera aussi
la normalisation backend-neutral des RRects dans `:math`, jamais dans le
planner ou le renderer.

Un nœud de draw du plan peut copier et conserver un `RectF32` ou `RectI32` de
`:math`. Il s'agit d'une commande de planification, pas d'une nouvelle
primitive géométrique.

### 3.2 `:render-ir`

La `SceneSnapshot` reste la source sémantique unique. W4a ne modifie pas les
notions de `GeometryNode.Rect`, `CoverageRequest`, material, transform ou clip.
Une incohérence de scène est rejetée ici ou pendant la classification
handle-free ; elle ne devient pas une limitation du device.

### 3.3 `:gpu-plan`

Le planner décide et scelle :

- la capability de frame ;
- les bounds exactes en device space ;
- les bounds raster conservatrices et le scissor ;
- `AnalyticScalarAA`, `SingleSample` et `SrcOver` ;
- les ressources logiques et leurs lifetimes ;
- les capacités physiques réservées ;
- le budget de pic ;
- l'ordre des passes.

Le planner reste handle-free. Il consomme uniquement un snapshot immuable des
limites du device et de la politique d'allocation.

### 3.4 `:gpu-renderer`

Le renderer :

- adapte les limites du device et la politique du pool en snapshot handle-free ;
- sélectionne le lowering par `capabilityId` ;
- valide la forme fermée du graphe ;
- sérialise l'ABI uniforme analytique de 80 bytes ;
- réserve les capacités déjà décidées ;
- encode, soumet, attend la completion et effectue le readback.

Il lui est interdit de recalculer l'admissibilité géométrique, de changer de
coverage, de substituer une autre route ou de revenir au legacy après `Ready`.

## 4. Sélection compositionnelle des capabilities

### 4.1 Problème W3

`GpuRenderBackend` possède aujourd'hui un seul `GpuPlanCompiler` et acquiert le
device dès que `classify` renvoie `null`. Avec plusieurs capabilities, retourner
le gap du premier compiler empêcherait le suivant d'être essayé ; retourner
`null` trop tôt acquerrait le device pour une scène finalement legacy.

### 4.2 Chaîne ordonnée

W4a remplace le couple ambigu `classify(scene, target)` / `plan(scene, ...)`
par une sélection explicite et handle-free. Le contrat conceptuel est :

```text
GpuPlanCompiler.select(scene, target): PlanSelection
GpuPlanCompiler.plan(candidate, capabilities, budget): RenderPlanResult<RenderGraph>

PlanSelection =
    NotCandidate(diagnostic)
  | Candidate(GpuPlanCandidate)
  | InvalidScene(diagnostic)
```

Un `CapabilityCompilerChain` possède la liste ordonnée W3 puis W4a et implémente
la sélection globale. Le `GpuPlanCandidate` opaque contient le résultat
immuable de la reconnaissance sémantique, son `capabilityId`, le canonical ID
de la scène et l'empreinte du descripteur de cible. Il est créé et consommé par
le même compiler ; il évite une seconde classification divergente après
acquisition du device. Ni cache mutable global ni lookup par canonical ID ne
remplace le passage explicite de ce candidat.

Règles :

1. la chaîne valide d'abord les contradictions structurelles communes entre
   scène et cible ;
2. dans une famille reconnue, le compiler valide ses valeurs numériques avant
   de conclure à un gap plus spécialisé ; une famille entièrement hors scope
   n'est pas parcourue comme si elle était candidate ;
3. `NotCandidate` passe au compiler suivant sans device ;
4. le premier `Candidate` arrête la chaîne ;
5. si tous répondent `NotCandidate`, le backend retourne
   `GapNotMigrated` avec les diagnostics non-candidats dans l'ordre de la
   chaîne, puis la frame complète reste legacy ;
6. le device est acquis uniquement après sélection d'un candidat ;
7. seul le compiler sélectionné reçoit son token, les capacités et le budget ;
8. un échec après cette promotion est terminal et ne relance ni un autre
   compiler ni le legacy.

`GpuRenderBackend` conserve le `GpuPlanCandidate` localement, acquiert le
snapshot physique, puis appelle `plan` avec cet objet exact. La chaîne ne
dépend ni du texte ni du code d'un diagnostic pour distinguer les trois cas.

### 4.3 Compatibilité W3

Une frame composée uniquement de rectangles W3 pixel-aligned continue à
produire le même `capabilityId`, la même structure de deux ressources et le
même lowering. W4a n'est candidate que si au moins un rectangle transformé a
une arête fractionnaire. Une frame W4a peut contenir d'autres rectangles
intégraux admissibles ; elle les compile tous dans la même lane analytique afin
de conserver l'autorité de frame entière.

Les scènes mixtes avec `DrawColor`, RRect, path, stroke, material non solid ou
clip complexe restent legacy dans W4a. Aucun draw individuel n'est extrait
d'une frame non entièrement admise.

### 4.4 Gate Surface compositionnelle

La `GPUPlanSurfaceShallowGate` W3-only ne porte plus la limite sémantique de
512 sur `operations.size`. Elle devient une candidate gate compositionnelle :

- elle vérifie seulement les préconditions Surface peu coûteuses et l'union
  des variantes `DisplayOp` potentiellement reconnues par les compilers
  installés ;
- elle ne décide ni de la capability ni de la limite propre à une capability ;
- les limites de capture générales restent l'autorité de
  `SceneCaptureLimits` et un dépassement conserve le fallback legacy existant ;
- W3 applique dans son selector sa limite historique de 512 commandes totales ;
- W4a applique dans son selector sa limite de 512 commandes visuelles, les
  métadonnées restant bornées séparément par `SceneCaptureLimits`.

Ainsi, 512 rectangles fractionnaires accompagnés d'une annotation peuvent
atteindre W4a, tandis que 513 rectangles n'y sont pas candidats. Une frame W3
de plus de 512 commandes totales ne devient pas implicitement admissible. Le
nom de la gate et du port Surface cesse d'encoder « W3 » lorsqu'ils servent la
chaîne W3+W4a ; ce renommage est un changement de vocabulaire produit, pas une
preuve par forme de source.

## 5. Contrat d'admission W4a

Une frame est candidate W4a si et seulement si toutes les conditions suivantes
sont satisfaites :

- la scène et la cible ont le même extent non vide et le même espace sRGB ;
- elle contient entre 1 et 512 commandes visuelles, toutes des
  `SceneCommand.Draw` de provenance `DrawOrigin.RECT` ;
- chaque géométrie est un `GeometryNode.Rect` fini, ordonné et non vide ;
- chaque material est un solid fini ;
- le style est fill, le blend est `SrcOver`, sans blender, shader, filtre,
  effect, resource ou operation blend supplémentaire ;
- la couverture demandée est `CoverageRequest.ANTIALIASED` ;
- le transform est l'identité ou un scale/translate axis-aligned fini ;
- chaque rectangle transformé est fini et non vide ;
- au moins un rectangle transformé possède une arête fractionnaire ;
- le clip est vide, ou un `ClipStackNode.DeviceRect` non-AA dont les quatre
  arêtes sont des entiers représentables en `I32` ;
- après intersection avec la cible et le clip, chaque draw retenu possède des
  bounds raster non vides.

Un scale négatif est permis : les quatre coins sont transformés puis les
minima/maxima sont recalculés. Rotation, skew, perspective, RRect, oval, path,
stroke/hairline, inverse fill, clip AA ou booléen restent hors W4a.

Les annotations finies et les commandes de provenance transform/clip déjà
capturées par la `Scene IR` peuvent être traversées comme métadonnées, mais
elles ne doivent pas introduire une seconde source d'état différente de celle
scellée dans chaque `DrawNode`.

## 6. Modèle de draw et couverture

### 6.1 Évolution du plan

`PlanPass.RenderPass` évolue de `List<SolidRectDraw>` vers une liste immuable de
`PlanDraw`. Deux variantes fermées existent à la fin de W4a :

- `SolidRectDraw`, inchangé sémantiquement pour W3 ;
- `AnalyticRectDraw`, nouveau draw W4a.

Le pass référence aussi, lorsqu'elles existent, ses ressources de draw par un
binding typé `PlanDrawDataResources(vertex, index, uniform)`. W3 conserve un
binding absent ; W4a référence exactement les trois buffers de son graphe. Ces
identités font partie des ressources consommées par le pass, ce qui permet au
`RenderGraph` de valider leurs lifetimes sans inférence par rôle.

`AnalyticRectDraw` conserve par copie :

- `commandIndex` ;
- la couleur linear-premultiplied ;
- le `RectF32` exact en device space ;
- le `RectI32` raster conservateur ;
- le `RectI32` scissor final ;
- `CoveragePlan.AnalyticScalarAA` ;
- `SamplePlan.SingleSample` ;
- `BlendPlan.SrcOver`.

Le raster conservateur est `floor(left/top), ceil(right/bottom)`, puis
intersection avec la cible et le scissor. Toute conversion et toute addition
emploient des opérations checked ; overflow ou valeur non représentable est un
diagnostic typé, jamais un clamp silencieux.

### 6.2 Équation de couverture

Pour un pixel de cellule `[x,x+1] × [y,y+1]` et le rectangle device exact
`[l,r] × [t,b]` :

```text
cx = clamp(min(x + 1, r) - max(x, l), 0, 1)
cy = clamp(min(y + 1, b) - max(y, t), 0, 1)
coverage = cx * cy
```

La coverage multiplie la source prémultipliée avant `SrcOver`. La cible
`rgba8unorm-srgb` encode et quantifie après chaque draw ; le draw suivant relit
donc la valeur quantifiée, comme dans la preuve W3.

Le lowerer consomme les bounds exactes et le scissor déjà planifiés. Il ne
réévalue ni l'intégralité des arêtes, ni le transform, ni la formule de
coverage.

## 7. Ressources physiques et budget

### 7.1 Snapshot de capacités

`PlanCapabilitySnapshot` est étendu avec les faits nécessaires à W4a :

- `minUniformBufferOffsetAlignment` ;
- `maxDynamicUniformBuffersPerPipelineLayout` ;
- un set logique d'opérations supportées, sans type WebGPU, dont W4a exige
  `RenderPass`, `CopyUpload`, `UniformBuffer` et `Readback` ;
- politique immuable du pool : floors vertex/index/uniform et croissance
  power-of-two ;
- limites W3 existantes : génération, dimension texture, taille maximale de
  buffer, alignement de copy et formats.

Les alignements et floors doivent être positifs et des puissances de deux. La
capability exige au moins un dynamic uniform buffer par pipeline layout. Les
valeurs viennent de l'adapter du renderer ; `:gpu-plan` ne dépend pas des
constantes internes de `:gpu-renderer`.

### 7.2 Ressources du graphe

Un graphe W4a contient exactement cinq ressources frame-local :

| Rôle | Kind | Usages | Taille planifiée |
| --- | --- | --- | --- |
| `LogicalTarget` | texture 2D | render attachment, copy source | `4 × width × height` |
| `ReadbackStaging` | buffer | copy destination, map read | row bytes alignés × height |
| `VertexData` | buffer | vertex, copy destination | capacité réservée du pool |
| `IndexData` | buffer | index, copy destination | capacité réservée du pool |
| `UniformData` | buffer | uniform, copy destination | capacité réservée du pool |

Les rôles `VertexData`, `IndexData`, `UniformData` et usages `Vertex`, `Index`,
`Uniform` sont ajoutés à `:gpu-plan`. `PlanResource.byteSize` porte la capacité
effectivement réservée, pas seulement le payload utile.

Ces trois ressources sont les déclarations logiques des buffers du pool natif.
Elles ne déclenchent pas en plus une préparation de buffers « ordinary » : une
double allocation rendrait le budget faux. Pour une frame W4a, le slot du pool
doit avoir exactement les trois capacités déclarées par le graphe ; réutiliser
silencieusement un slot plus grand sous-déclarerait le pic physique.

Pour `N` draws :

```text
vertexUseful = 32 × N
indexUseful = 24 × N
uniformStride = alignUp(80, minUniformBufferOffsetAlignment)
uniformUseful = uniformStride × N
```

Chaque capacité est ensuite arrondie indépendamment à la prochaine puissance
de deux au-dessus de son floor : 16 KiB vertex, 4 KiB index et 4 KiB uniform
avec la politique actuelle. La spec n'en fait pas des constantes universelles :
elles sont des faits de l'allocator exposés dans le snapshot.

### 7.3 Pic et lifetime

Avec `Render` à l'index 0 et `Readback` à l'index 1, le target vit sur `[0,2)`,
le staging sur `[1,2)` et les trois buffers de draw sur `[0,2)`. La lifetime
des buffers reflète le lease natif actuel, qui n'est rendu au pool qu'après
completion/readback. Le pic à l'index 1 contient donc les cinq ressources.

Le pic frame-local W4a est donc la somme checked :

```text
target + readback + vertexCapacity + indexCapacity + uniformCapacity
```

Chaque buffer doit aussi respecter `maxBufferSizeBytes`. Les cinq ressources
restent vivantes jusqu'à la dernière passe qui les consomme ; la session native
et les buffers réutilisables restent réservés jusqu'à completion/readback. Une
capacité de pool arrondie ne peut pas être omise du budget au motif qu'elle sera
réutilisée par une frame future.

Le calcul renvoie un résultat typé pour input invalide, overflow, limite de
buffer ou dépassement du budget. Aucune arithmetic wraparound ni allocation
best-effort n'est autorisée après `Ready`.

## 8. Lowering et exécution native

Le lowerer est dispatché par `capabilityId` :

- le lowering W3 exige uniquement des `SolidRectDraw`, deux ressources et
  `FullOrScissor` ;
- le lowering W4a exige uniquement des `AnalyticRectDraw`, cinq ressources et
  `AnalyticScalarAA`.

Le lowering W4a valide notamment :

- le snapshot de capacités exact et sa génération ;
- les rôles, usages, tailles et lifetimes des cinq ressources ;
- les dépendances `Render -> Readback` ;
- la correspondance draw count / bytes utiles / strides ;
- l'ABI analytique exacte de 80 bytes ;
- les bounds exactes, raster et scissor déjà scellées ;
- les modes coverage/sample/blend/load/store fermés.

Il matérialise ensuite la lane native `Rect + ScalarAA` déjà existante. Aucun
nouveau shader WGSL n'est prévu par W4a. Les anciennes prepared builders ne
redeviennent pas une autorité de classification : elles peuvent être
réutilisées comme mécanisme d'exécution seulement si leurs validations ne
modifient pas le sens du plan.

Le chemin W4a est un assembler/preflight/materializer sibling scellé. Il ne
passe pas par la route analytique générique lorsqu'elle reclassifie la
géométrie, élargit ou clamp le scissor, ou prépare des buffers V/I/U ordinaires.
L'élargissement du quad nécessaire à l'émission de fragments de bord reste un
détail natif autorisé ; les bounds exactes et le scissor effectif demeurent
ceux du plan.

Un graphe contrefait, muté, émis par un autre backend, lié à une autre
génération ou devenu contradictoire produit un échec terminal. Le renderer ne
tente aucune autre route après `Ready`.

## 9. Résultats et précédence des erreurs

La précédence observable est :

1. contradiction scène/cible, ou donnée non finie appartenant à une famille
   reconnue : `InvalidScene`, sans acquisition du device ;
2. aucune capability candidate : `GapNotMigrated`, puis frame legacy entière ;
3. capability sélectionnée mais limite physique absente : catégorie
   `UnsupportedCapability`, représentée avant `Ready` par
   `GapOnPromotedScope` et après `Ready` par
   `RenderExecutionResult.UnsupportedCapability` ;
4. overflow ou budget dépassé : `ResourceLimitExceeded` ;
5. contradiction, mutation ou panne après `Ready` : résultat terminal
   d'exécution, sans fallback.

Le mapping conserve les types publics existants de `RenderPlanResult` et
`RenderExecutionResult`. Les diagnostics reçoivent un namespace W4a stable ;
leurs codes, et non leur texte, portent la classification.

## 10. Preuves acceptées

Les tests prouvent du comportement public ou des invariants de données. Sont
interdits : tests de forme du source, reflection, accès à des méthodes privées,
assertions de call-count d'infrastructure et tests qui ne vérifient qu'un wiring.

### 10.1 `:gpu-plan`

- W3 reste byte/shape-compatible pour une frame pixel-aligned ;
- la chaîne essaie W4a après un `NotCandidate` W3 sans acquérir le device avant
  la sélection finale ;
- une scène hors des deux capabilities reste `GapNotMigrated` ;
- une contradiction reste `InvalidScene` quelle que soit la position du
  compiler ;
- W3 conserve sa frontière de 512 commandes totales, W4a accepte 512 draws
  plus métadonnées dans les limites de capture et refuse 513 draws ;
- bounds fractionnaires, scale positif/négatif, raster conservateur et scissor
  sont exacts ;
- la limite 512, les alignements, les floors power-of-two, les tailles maximales,
  le budget exact et chaque frontière ±1 sont couverts ;
- un plan W4a expose exactement les cinq ressources et leurs lifetimes.

### 10.2 `:gpu-renderer`

- un graphe W4a valide devient une task list analytique avec ABI 80 bytes ;
- un mauvais capability ID, rôle, usage, stride, taille, lifetime, draw type ou
  snapshot est refusé terminalement ;
- une session reste réservée jusqu'à completion/readback ;
- aucun test ne réimplémente la classification W4a dans le renderer.

### 10.3 API publique et oracle CPU

Des tests `Surface` comparent les bytes exacts à un oracle CPU indépendant de
`:gpu-plan` et des payloads GPU :

- aire couverte d'un rectangle opaque fractionnaire ;
- deux rectangles translucides fractionnaires superposés avec quantification
  sRGB observable entre les draws ;
- frange AA coupée par un scissor intégral ;
- sorties RGBA et BGRA ;
- scopes natifs publics exactement `{Render, Readback}` ;
- scène non admise conservant le résultat legacy exact.

Les frontières 512/513 sont également prouvées via `Surface` par le résultat
et les pixels, sans assertion sur la gate, le nombre d'appels ou un composant
privé.

L'oracle implémente explicitement l'équation de coverage, la prémultiplication,
`SrcOver` linear-premultiplied et le store `rgba8unorm-srgb` à chaque draw.

### 10.4 Régression globale

La validation finale W4a doit conserver exactement les noms rouges de la
baseline W0–W2, pas seulement le total de 51 :

- `ImageTest :: ColorType enum values` ;
- 45 cas `GPUAllApiBlendSurfaceTest :: DrawPoint` ;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase` ;
- deux refus `GPUPreparedSurfaceFrameBuilderTest` ;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour` ;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch`.

`jpg-color-cube` reste explicitement en quarantaine et n'est jamais exécutée.
Les font et codec restent hors périmètre. Aucun seuil ni référence GM n'est
modifié dans W4a.

## 11. Enchaînement W4b–W4e

Après la validation interne de W4a, le travail enchaîne sans pause artificielle
sur la tranche suivante. Chaque tranche possède toutefois son propre design
détaillé lorsque de nouvelles décisions matérielles apparaissent.

### W4b — RRect analytique

- canonicalisation et normalisation des rayons dans `:math` ;
- `RRectF32` device exact scellé par le planner ;
- même modèle de ressources W4a ;
- réutilisation de la lane analytique native existante ;
- oval conservé hors scope tant que sa provenance IR reste path.

### W4c — fills de paths

- consommation du moteur topologique hybride F64/F32 de `:math` ;
- stratégies explicites tessellation puis stencil/cover ;
- buffers dimensionnés sur le coût géométrique réel ;
- fill rules et cas non admissibles diagnostiqués sans fallback silencieux.

### W4d — strokes et hairlines

- paramètres ou expansion de stroke possédés par `:math` ;
- caps, joins, miter, dash et hairline comme données géométriques explicites ;
- coverage indépendante du material.

### W4e — clips complexes

- clip stack ordonnée compilée en sous-graphe ;
- intersect/difference, path et inverse conservant leur ordre ;
- autorité unique des ressources stencil/mask et budget de texture explicite.

L'approbation de cette séquence autorise l'enchaînement opérationnel. Elle
n'autorise pas à inventer silencieusement les contrats encore non spécifiés de
W4b–W4e : seules les décisions réellement nouvelles remontent pour arbitrage.

## 12. Découpage d'implémentation W4a

1. étendre les capabilities, rôles/usages de ressources et calculs de budget ;
2. rendre la candidate gate Surface compositionnelle, introduire la chaîne de
   compilers et compiler les rectangles fractionnaires ;
3. étendre le modèle de draws sans altérer le contrat W3 ;
4. ajouter le lowering W4a sans reclassification ;
5. connecter la lane analytique existante au context et au readback public ;
6. ajouter oracle, preuves pixels, status W04 et validation globale ;
7. faire une review Sol finale, corriger, vérifier et publier la PR stackée.

L'implémentation est séquentielle avec des agents Terra adaptés à chaque tâche.
Les agents Sol sont réservés aux reviews. Deux agents d'implémentation ne
travaillent jamais en parallèle sur le même workspace partagé.

## 13. Critères de sortie W4a

W4a est prête à être empilée lorsque :

- la capability W4a est branchée de `Surface` au readback natif ;
- W3 conserve son contrat et ses preuves exactes ;
- les preuves pixels W4a sont exactes et publiques ;
- les ressources et le pic reflètent les capacités réellement réservées ;
- aucune décision géométrique n'a migré hors `:math`/`:gpu-plan` ;
- aucun fallback n'est possible après `Ready` ;
- la baseline globale conserve exactement ses 51 noms rouges connus ;
- le status W04 indique W4a atteinte et la gate W4 globale encore ouverte ;
- une review Sol ne relève aucun finding bloquant.
