# W5h — Runtime effects enregistrés et fermeture des lanes H

Date : 2026-09-15  
Branche : `codex/w5h-registered-runtime-effects`  
Base stackée : `codex/w5g-composed-procedural-materials` (`da9b367bd`)  
Autorité parente : `refactor/specs/2026-09-09-w5-material-graph-design.md`

## 1. Objectif

W5h ferme W5 en introduisant une autorité backend-neutral pour les runtime
effects enregistrés, puis en faisant consommer le material graph commun par
toutes les lanes marquées `H` dans la matrice de promotion W5.

À la sortie :

- un runtime effect positif est identifié sans ambiguïté par
  `(RuntimeEffectId, semanticVersionI32, abiHash)` ;
- `:gpu-plan` décide seul si sa sémantique, ses children, ses uniforms, ses
  ressources et ses budgets sont admissibles avant `Ready` ;
- `:gpu-renderer` matérialise exclusivement le programme et les bindings déjà
  scellés ;
- Rect, RRect, Path fill, Path stroke/hairline, Point(s), Text pré-résolu,
  Vertices/Mesh et les origines image applicables consomment la même autorité
  material ;
- aucune lane W5h promue ne reconstruit localement un descripteur, un programme,
  un tint ou un blend material parallèle.

## 2. Hors périmètre

W5h n'inclut pas :

- un frontend SkSL ou l'exécution arbitraire du WGSL appelant ;
- l'enregistrement applicatif de nouveaux triplets sémantique/CPU/WGSL ;
- le décodage ou l'encodage d'images externes ;
- la génération, le shaping ou la validation de fonts et glyphs ;
- les image filters, mask filters, layers, backdrop et effets spatiaux W6 ;
- AA4 natif, targets HDR ou formats physiques nouveaux ;
- les GMs Skia, dashboard, renders, baselines, scores et `jpg-color-cube` ;
- le retrait complet du renderer legacy, réservé à W8.

Les nouvelles valeurs géométriques ou de transformation restent dans les
modules `:math`, avec la nomenclature I32/I64/F32/F64. W5h ne crée aucun Rect,
Point, Size, Matrix ou autre objet géométrique privé dans `:kanvas`,
`:gpu-plan` ou `:gpu-renderer`.

## 3. État actuel et écarts

Le runtime public actuel est indexé principalement par `id`. Sa construction
et `compile(wgsl)` alimentent un registry legacy, et le descriptor IR v2
conserve encore le module WGSL, des uniforms décrits par `binding/type/size`
et des children sans nullability.

La voie renderer possède plusieurs registries, resolvers, executors et
materializers. Elle peut préparer un runtime effect sans catalogue sémantique
dans `:gpu-plan`. Cette voie ne constitue donc pas une autorité W5h, même
lorsqu'elle possède un CPU oracle ou une reflection WGSL locale.

Le plan composé W5g admet Rect et Path fill. Les autres familles conservent des
barrières explicites ou des bridges locaux :

- RRect refuse les material plans composés récents ;
- Path stroke/hairline refuse `MaterialV5` ;
- Point(s) extrait encore un payload solid dans son bridge ;
- Text et Vertices ont une provenance material W5a-only ;
- Mesh possède en plus une voie runtime program distincte ;
- l'image directe conserve un tint et une restriction `SRC_OVER` locaux ;
- A8 n'utilise pas encore uniformément `mask * paintMaterial` ;
- `MaterialNode.RuntimeEffect` est refusé par la construction composée W5g.

## 4. Approches considérées

### 4.1 Autorité runtime puis promotions verticales — retenue

Construire d'abord le catalogue sémantique et l'exécution commune sur Rect et
Path fill, puis migrer chaque lane H vers cette même autorité. Chaque migration
géométrique devient alors un branchement de material plan, pas une nouvelle
implémentation runtime.

### 4.2 Promouvoir les lanes avant le runtime — rejetée

Cette option fermerait quelques cellules rapidement, mais imposerait de refaire
les programmes, layouts et preuves lorsque le runtime serait ajouté. Elle
multiplie les états intermédiaires et le risque de routes parallèles.

### 4.3 Adapter directement le registry renderer — rejetée

Cette option pourrait produire un premier pixel plus vite, mais laisserait le
WGSL, les bindings et la version renderer décider de la sémantique. Elle viole
les frontières W5 et ne ferme pas le critère d'autorité unique.

## 5. Autorités et frontières de modules

Les trois couches utilisent exactement la même clé :

```text
(RuntimeEffectId, semanticVersionI32, abiHash)
        ├── gpu-plan     : sémantique + graphe numérique + CPU evaluator
        ├── gpu-renderer : manifest logique + fragment WGSL enregistré
        └── kanvas       : lookup public du built-in enregistré
```

### 5.1 `:render-ir`

`:render-ir` porte les valeurs backend-neutral nécessaires au snapshot et au
hash :

- `RuntimeEffectDescriptor` v3 ;
- `RuntimeUniformSlotV2` et `RuntimeUniformBlockV1` ;
- child slots ordonnés avec type et nullability ;
- logical resource slots sans group/binding physique ;
- `CanonicalHashBytesV1` et les tags numériques exhaustifs définis par la spec
  W5 parente.

Un descriptor v3 positif contient l'identité, le kind et la forme logique de
l'ABI, mais aucun WGSL ni handle backend.

### 5.2 `:gpu-plan`

`:gpu-plan` porte `RuntimeEffectSemanticCatalogSnapshot` et
`RuntimeEffectSemanticEntryV1`. Une entrée contient :

- le triplet exact et le kind shader/color-filter/blender ;
- les contrats couleur d'entrée et de sortie ;
- l'ordre et le layout logique des uniforms ;
- les child slots et ressources logiques ;
- `numericContractId`, `NumericOperationGraphV1` et l'identité/version du CPU
  evaluator ;
- les limites de graphe, de bindings et de ressources.

Le snapshot est immuable et injecté dans la compilation de frame. Il est figé
avant toute construction de plan et ne consulte jamais un registry renderer.

### 5.3 `:gpu-renderer`

`:gpu-renderer` porte un manifest enregistré pour le même triplet. Le manifest
contient le fragment WGSL sans annotation `@group/@binding`, son ABI logique et
ses besoins physiques.

Avant toute pipeline native, le renderer :

1. recalcule l'`abiHash` du manifest ;
2. l'égale byte-exactement au descriptor et à l'entrée sémantique ;
3. assemble les bindings depuis le layout composé scellé ;
4. valide parser et reflection du module assemblé contre ce layout ;
5. matérialise uniquement les ressources déjà planifiées.

### 5.4 `:kanvas`

`:kanvas` expose le lookup public et capture les valeurs immuables. Il ne choisit
ni module WGSL, ni binding physique, ni fallback.

## 6. ABI logique et hashes

`semanticVersionI32` est strictement positif pour une entrée cataloguée. La
version zéro est réservée aux effets issus de `compile(wgsl)` et aux anciennes
archives.

`abiHash` est le SHA-256 de la préimage `CanonicalHashBytesV1` décrite par la
spec W5 parente, encodé en exactement 64 caractères hex lowercase, sans préfixe
`sha256:`. Les valeurs dynamiques, pixels, handles, group/binding physiques et
offsets de composition n'entrent pas dans ce hash.

Les uniforms respectent l'ordre du descriptor. Leur layout local est :

| Type | Alignement | Taille |
| --- | ---: | ---: |
| FLOAT / INT1 | 4 | 4 |
| FLOAT2 | 8 | 8 |
| FLOAT3 | 16 | 12 |
| FLOAT4 | 16 | 16 |
| MAT3X3 | 16 | 48 |
| MAT4X4 | 16 | 64 |

Chaque offset est `alignUp(cursor, alignment)` et la taille finale du block est
alignée à 16 octets. W5h fixe `arrayCountI32=1` et
`arrayStrideBytesI32=0`. Les arrays sont refusés.

Les ressources logiques conservent leur slot et leurs faits typés, jamais une
sentinelle. W5h admet seulement les familles exhaustivement autorisées par le
§12 parent : uniform, storage-read, sampled texture 2D float-filterable et les
samplers explicitement permis. Les textures storage, comparison samplers,
binding arrays et autres dimensions/address spaces restent refusés.

Le `ComposedBindingLayoutV1` W5g est complété, pas remplacé. Chaque block de
nœud commence sur une base alignée à 16 octets, tandis que ses champs gardent
leurs alignements locaux réels de 4, 8 ou 16 octets et leurs trous éventuels.
Les ressources physiques sont affectées après le block uniforme dans l'ordre
préfixe des owners.

## 7. Built-in initial

W5h livre au minimum `kanvas.runtime.child-opacity` version 1 :

- kind `SHADER` ;
- un child shader obligatoire `child` ;
- un uniforme F32 `alpha` fini dans `[0,1]` ;
- entrée/sortie `LINEAR_PREMUL` ;
- résultat `scaleAlpha(eval(child), alpha)` ;
- aucune ressource logique propre.

Avec `alpha=1`, l'effet est un passthrough. Une valeur non triviale prouve que
l'uniforme, le child, l'ordre d'évaluation et le graphe numérique sont réellement
consommés. L'effet possède une implémentation sémantique, un CPU evaluator et un
fragment renderer concordants.

Les descriptors historiques présents uniquement dans les registries renderer
ne deviennent pas catalogués implicitement. Chaque migration future devra
fournir les trois autorités et le triplet exact.

## 8. API publique et transition v0

L'API durable est :

```kotlin
RuntimeEffect.registered(
    id: String,
    semanticVersionI32: Int,
): RuntimeEffect?
```

Le lookup est exact et ne sélectionne jamais automatiquement la dernière
version connue.

La couche suivante est explicitement transitoire :

- `register(effect)` ;
- `registered(id)` sans version ;
- auto-enregistrement de `compile(wgsl)` ;
- WGSL embarqué dans les anciens descriptors ;
- registries, resolvers et routes runtime renderer historiques.

W5h déprécie et isole cette couche. Elle ne peut obtenir aucune admission W5.
W8 retire ses hooks et routes renderer parallèles. Le décodage d'une ancienne
archive peut rester comme compatibilité inerte tant que la politique de wire le
demande, mais son résultat demeure version zéro et non exécutable.

L'enregistrement applicatif d'un effet positif reste impossible. Le registry
legacy v0 et le catalogue built-in positif sont séparés ; une collision d'ID ne
permet donc jamais à une archive ancienne de masquer une version positive.

## 9. Picture et SceneArchive

Le writer `Picture`/`SceneArchiveCodec` reçoit une nouvelle version de wire et
encode le descriptor v3 avec :

- `semanticVersionI32` et `abiHash` ;
- les offsets, tailles, alignements, count et stride des uniforms ;
- les child slots et leur nullability ;
- les ressources logiques ;
- le module legacy uniquement pour un descriptor version zéro.

Les anciennes versions décodent vers `semanticVersionI32=0`. Leur ABI legacy
est snapshotée de façon déterministe, mais ce hash ne leur confère aucune
capability. La reconstruction et l'installation legacy restent transactionnelles
après validation complète de l'archive.

Un descriptor positif avec module WGSL, un descriptor zéro prétendant être une
entrée cataloguée, une version négative ou un hash malformé est refusé.

Cette évolution concerne exclusivement le wire de scène interne. Aucun codec
d'image externe n'est modifié.

## 10. Plan material V6 et data flow

W5h fait évoluer le plan composé vers un schéma V6 tout en conservant les
interfaces existantes `MaterialProgramPlan` et `MaterialBindingPlan` :

```text
DrawNode
  -> EffectiveMaterial + catalogue sémantique scellé
  -> MaterialProgramPlan/MaterialBindingPlan V6
  -> source stage commun
  -> lane géométrique W4
  -> blend final commun
```

V6 n'est pas un second compilateur. Il étend le même DAG, la même table de
plans, le même source stage et le même inventaire frame-wide. À la fermeture
W5h, toute nouvelle admission W5 utilise V6. Les plans V1–V5 restent seulement
pour la compatibilité des witnesses antérieurs et ne sont pas sélectionnés par
une lane promue W5h.

Pour un nœud runtime, le planner :

1. valide le graphe original et les budgets de capture ;
2. résout le triplet exact dans le snapshot sémantique ;
3. recalcule l'ABI et compare descriptor/catalogue ;
4. valide uniforms, children, nullability, ressources et contrats couleur ;
5. inline les children dans le DAG en préservant l'ordre et le partage ;
6. attribue owners, offsets logiques et ressources physiques ;
7. valide capabilities et budgets de la frame complète ;
8. publie le plan seulement après réussite de toutes les lanes et siblings.

Aucun child, upload, buffer, pipeline ou lease ne peut être préparé pendant
qu'un sibling ou une autre lane de la frame peut encore faire échouer le
preflight.

## 11. Promotion des lanes H

### 11.1 Rect et Path fill

Le runtime catalogué est d'abord admis sur les deux lanes composées V5 déjà
fonctionnelles. Cela ferme l'ABI et l'exécution end-to-end avant toute extension
géométrique.

### 11.2 RRect et Path stroke/hairline

Les compilers géométriques W4 restent l'autorité de coverage et de topologie.
Leurs refus material récents sont remplacés par une référence au plan V6 et au
source stage commun. Aucun élargissement AA4 n'est permis.

### 11.3 Point(s)

Le bridge W5b cesse d'extraire un `SolidColor` local. Il transporte un
`MaterialPlanRef` et conserve l'autorité commune de blend. Les 45 cas historiques
DrawPoint, leurs quinze modes et trois contextes restent obligatoires.

### 11.4 Text pré-résolu

Le `TextBlob` public fournit des glyph IDs et positions déjà résolus. L'atlas A8
produit uniquement la coverage ; le plan V6 produit la couleur. Aucun descriptor
text-local, resolver material parallèle ou évolution font n'est autorisé.

### 11.5 Vertices et Mesh

Le paint material devient une référence V6. La couleur vertex et
`operationBlendMode` restent une composition interne à la source, distincte du
blend final. Un `MeshProgram` runtime ne peut être exécuté que par un triplet
catalogué ; la voie resolver historique ne confère aucune capability W5.

### 11.6 Image origin

Pour RGBA, la source image est modulée uniquement par l'alpha du paint. Pour
A8, le canal image est un masque multiplié par la couleur ou le shader V6 du
paint. Le blend final utilise le `BlendPlan` commun, y compris `NoOp` et
destination-read. Le tint local et la restriction `SRC_OVER` du lowerer image
ne restent pas des autorités parallèles.

Les images de test sont construites depuis des pixels déjà décodés en mémoire.

## 12. Diagnostics, budgets et récupération

Les refus sont terminaux avant `Ready` et avant ownership natif. Ils distinguent
au minimum :

- version zéro ou triplet absent :
  `unsupported.material.runtime_effect.unregistered_semantics` ;
- version, kind ou descriptor incompatible ;
- ABI ou manifest mismatch ;
- uniform manquant, extra, non fini ou de mauvais type ;
- child manquant, extra, mal ordonné, nullable/type incompatible ;
- ressource logique ou reflection physique incompatible ;
- graph/capture/device/frame budget dépassé ;
- capability physique absente.

Le diagnostic conserve l'index du draw et l'identité material sans objet
backend. Un refus ne prépare aucune ressource. La récupération est prouvée par
un rendu public valide sur la même surface immédiatement après le refus.

Les octets uniformes sont comptés avant copie, puis à nouveau dans le plan de
frame. Les opérations utilisent l'arithmétique checked I32/I64. Le layout
composé, les textures, buffers, samplers, binding counts et évaluations runtime
participent aux limites existantes avant allocation native.

## 13. Stratégie de tests

Le développement suit RED -> GREEN -> refactor pour chaque comportement.

Les nouvelles gates observent uniquement les APIs publiques : `Surface`,
`Canvas`, `Picture` et `render()`.

Elles couvrent :

- lookup exact du built-in et absence de résolution implicite ;
- pixels du `child-opacity` avec alpha 1 et non trivial ;
- ordre et partage des children, mutation post-capture des uniforms et children ;
- round-trip Picture nouveau et lecture publique d'archives historiques v0 ;
- refus v0, identité/version/hash/ABI/child/uniform invalides et récupération ;
- chaque cellule H applicable avec alpha non trivial, mutation post-capture et
  blend final non trivial ;
- Rect, RRect, Path fill, Path stroke/hairline, Point(s), Text pré-résolu,
  Vertices/Mesh et origine image A8 ;
- images RGBA pour leur alpha et leur blend final ;
- les familles W5 antérieures : solid/opacity, gradients, matrices/tile/clamp,
  ImageSample, color filters, Blend children et Noise ;
- les 45 DrawPoint historiques inchangés.

Les comparaisons sont byte-exactes lorsque l'enveloppe numérique est singleton,
ou limitées aux deux codes adjacents explicitement produits par l'oracle
analytique. Aucun seuil de similarité n'est introduit.

Sont interdits : tests de source shape, private/internal comme preuve,
reflection de test, call counts, assertion d'identité de cache, mocks/fake
devices, injection artificielle de capability et tests d'infrastructure du
code. L'absence de route material parallèle est contrôlée par review humaine.

Les suites font, codecs externes, GM Skia, dashboard, renders/baselines,
`:integration-tests:skia`, `jpg-color-cube` et les suites globales restent hors
des gates. Un exit natif 133 reste `UNKNOWN` sans preuve contraire.

## 14. Découpage d'implémentation

W5h est livré en sept lots séquentiels :

1. contrats backend-neutral, hash canonique et catalogue ;
2. API publique, descriptor v3 et wire Picture ;
3. runtime commun sur Rect et Path fill ;
4. RRect, Path stroke/hairline et Point(s) ;
5. Text pré-résolu, Vertices et Mesh ;
6. origines image A8/RGBA et blend final commun ;
7. nettoyage, matrice globale, documentation et Draft PR stackée.

Les agents d'implémentation sont choisis selon le lot. Sol est réservé aux
reviews de conformité et de qualité. Chaque lot est revu avant le suivant ; la
stack complète reçoit une dernière review Sol sans finding Critical ou
Important.

Une seule Draft PR `codex/w5h-registered-runtime-effects` cible directement
`codex/w5g-composed-procedural-materials`. Aucun merge ni update du parent n'est
inclus.

## 15. Nettoyage et transition vers W8

Après promotion d'une lane, la review vérifie qu'elle n'appelle plus une voie
qui reconstruit son matériau :

- `GPUMaterialMapper` comme autorité material ;
- provenance bridges W5a-only ;
- resolver/runtime child authority historique ;
- tint ou blend local du lowerer image ;
- descriptor/program material propre à Text, Vertices ou Mesh.

Les éléments réellement morts sont supprimés. Les routes legacy encore utiles
à des familles hors W5 restent jusqu'à W8, mais ne sont jamais sélectionnées
pour une cellule promue. Leur présence est documentée sans test structurel.

La couche publique v0 est une compatibilité transitoire, pas une deuxième
architecture. W8 retire les hooks et routes renderer associés ; la conservation
éventuelle d'un decoder d'archives v0 reste inerte et fait l'objet d'une décision
de wire séparée.

## 16. Critères de fermeture

W5h et W5 sont fermés lorsque :

1. le built-in `child-opacity` est rendu par la route commune sur toutes les
   lanes applicables ;
2. `compile(wgsl)` et toute ancienne archive v0 refusent avant `Ready` avec le
   diagnostic exact ;
3. descriptor, catalogue, CPU evaluator et manifest renderer concordent sur le
   triplet et les deux hashes normatifs ;
4. toutes les cellules `H` applicables de la matrice W5 sont promues ;
5. aucune lane promue ne possède de route material parallèle ;
6. program structure et valeurs dynamiques restent séparées ;
7. uniforms, children et ressources respectent les budgets avant copie puis
   avant allocation native ;
8. les gates publiques ciblées sont vertes sans nouveau failure/error ;
9. les 45 DrawPoint historiques restent fermés ;
10. aucun test font, codec externe, GM, dashboard, baseline, score,
    `jpg-color-cube` ou global n'est utilisé pour gonfler le résultat ;
11. le suivi durable est limité à `refactor/README.md`, ce design, le plan et
    `refactor/waves/W05-material-graph/status.md` ;
12. la review Sol finale de la stack ne contient aucun finding Critical ou
    Important.

