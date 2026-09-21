# W6 — Layers et effets spatiaux

Date : 2026-09-16
Branche de design : `codex/w6a-layer-authority`
Base stackée : `codex/w5h-registered-runtime-effects` / Draft PR #2402

## 1. Décision

W6 ferme les layers et effets spatiaux derrière une autorité plan-first unique.
Elle réutilise les contrats de layer, filtres, cibles offscreen et passes déjà
présents, mais supprime leur rôle d'autorités concurrentes : après admission,
une frame suit exactement une chaîne immuable de la capture publique à
l'exécution native.

La chaîne autoritaire est :

```text
API Surface/Picture
  -> render-ir immuable
  -> géométrie et mappings :math
  -> plan spatial et RenderGraph :gpu-plan
  -> matérialisation :gpu-renderer
  -> exécution des passes
  -> composite dans le parent
```

W6 adopte la sémantique de Skia pour `saveLayer`, les bounds, le backdrop et
l'ordre des effets, tout en conservant la propriété transactionnelle de
Kanvas : DAG complet, budgets et lifetimes sont validés avant publication.
Skia décide progressivement autour d'une stack de `SkDevice`; Kanvas ne copie
pas ce contrôle immédiat et ne renonce pas à son `RenderGraph` capturé.

## 2. Objectif et périmètre

W6 doit rendre fonctionnels, sur les géométries W4 et matériaux W5 déjà admis :

1. `saveLayer`, restore, alpha, color filter et blend final ;
2. layers imbriqués et cibles offscreen ;
3. `initWithPrevious` et backdrop ;
4. image filters et mask filters ;
5. blur, drop shadows et morphologie ;
6. DAG composés, multi-inputs et filtres spatiaux avancés ;
7. capture et replay `Picture` ;
8. budgets, caches, rollback et récupération publique.

Les 22 familles d'image filters inventoriées sont : `Crop`, `Blur`,
`DropShadow`, `ColorFilter`, `Compose`, `Blend`, `Dilate`, `Erode`,
`DistantLitDiffuse`, `PointLitDiffuse`, `SpotLitDiffuse`,
`DistantLitSpecular`, `PointLitSpecular`, `SpotLitSpecular`, `Offset`, `Tile`,
`Merge`, `DisplacementMap`, `Picture`, `Magnifier`, `MatrixConvolution` et
`RuntimeEffect`.

Restent hors périmètre :

- génération de fonts et glyphs ;
- codecs, décodage ou encodage de formats externes ;
- GMs Skia, dashboard, renders, références, scores et rebaseline ;
- `jpg-color-cube` ;
- retrait final des routes legacy, réservé à W8 ;
- tests privés, reflection, fake device, mocks de backend, compteurs internes
  et tests d'infrastructure.

## 3. Approches considérées

### 3.1 Catalogue complet avant exécution

Cette approche modéliserait toutes les familles et leurs ABI avant de produire
les premiers pixels. Elle est rejetée : elle augmenterait la surface de
contrats non exécutables et retarderait la validation des frontières layer.

### 3.2 Tranches verticales par capacités

Chaque tranche livre capture, plan, ressources, exécution et preuve publique
d'une famille cohérente. Les primitives spécialisées peuvent être conservées
comme implémentations, mais pas comme planners concurrents. C'est l'approche
retenue.

### 3.3 Interpréteur spatial générique avant les layers

Cette approche traduirait tous les effets vers une VM ou un moteur WGSL
générique. Elle est rejetée : elle mélangerait W6 avec le frontend runtime de
W5h, sans résoudre d'abord les bounds, les targets et le restore.

## 4. État de départ

Le dépôt possède déjà une part importante des pièces W6 :

- `GPULayerSaveRecord`, plans de bounds, target, ressources et composite ;
- `GPUSaveLayerIsolatedTargetPlanner` et matérialisation native ;
- `DrawLayer`, builders, route decisions et pass contracts ;
- cibles offscreen et copies de destination dans le backend ;
- descripteurs canoniques pour 22 image filters ;
- normalizers, oracles, blur, mask filter et drop-shadow planners ;
- lowerers de composite et task-list handling de `saveLayer`.

Ces éléments ne forment pas encore une autorité W6 : plusieurs analyses
refusent `sourceFilterCount > 0`, backdrop, `initWithPrevious`, F16 ou
`preserveLCDText`; des chemins reconstruisent encore localement scope, bounds,
target ou composite. W6 consolide ces pièces au lieu d'introduire une seconde
hiérarchie.

## 5. Frontières de modules

### 5.1 `:render-ir`

`:render-ir` capture un arbre ordonné de scopes et un DAG immuable d'effets. Il
porte les paramètres sémantiques, les entrées explicites et les identités de
capture. Il ne contient ni handle GPU, ni texture native, ni WGSL, ni décision
de cache.

Les entrées capturées d'un filtre sont exactement :

- source implicite du layer ;
- transparent black ;
- résultat d'un nœud identifié ;
- Picture capturée ;
- backdrop identifié.

Une entrée nulle compatible avec l'API Skia est capturée comme le sentinel
explicite `ImplicitSource`; elle n'est jamais liée globalement pendant la
capture. Le planning crée ensuite des occurrences d'évaluation identifiées par
le tuple `(capturedNodeId, boundSourceId, mapping, desiredOutput)` : à la racine,
la source implicite est la source du layer ; dans `Compose(outer, inner)`, la
source implicite de `inner` reste la source courante et celle de `outer` est le
résultat de `inner`. Un même nœud capturé évalué avec deux sources liées produit
donc deux occurrences, lifetimes et coûts distincts. Une mutualisation exige le
même nœud capturé, la même source liée, le même mapping et la même sortie
demandée ; l'égalité canonique seule ne crée jamais d'alias.

Le wire W6 représente le DAG par une table de nœuds et des références stables,
avec des IDs de racine ; il préserve ainsi un partage explicitement capturé. Les
anciens arbres récursifs décodés sont convertis en occurrences distinctes, sans
dédupliquer des sous-arbres égaux par valeur. Un cycle, un nœud absent, une
profondeur ou un fan-out hors limites sont refusés pendant la capture ou le
décodage, avant copie volumineuse.

### 5.2 `:math`

`:math` reste l'unique propriétaire des objets géométriques, mappings,
rectangles, points, tailles et matrices nécessaires à W6. Toute nouvelle valeur
suit la nomenclature `I32`, `I64`, `F32` ou `F64`.

Les transformations et calculs de bounds sont effectués en F64. La projection
vers des texels I32 arrondit vers l'extérieur et refuse NaN, infini, inversion
impossible et overflow. Aucun `Rect`, `Point`, `Size` ou `Matrix` privé n'est
créé dans `:kanvas` ou `:gpu-renderer`.

### 5.3 `:gpu-plan`

`:gpu-plan` produit deux autorités immuables :

- un plan sémantique de scopes et filtres, avec ordre, mappings, bounds
  requises/produites et dépendances ;
- un plan physique backend-neutral, avec passes, formats, usages, sample
  counts, ressources logiques, lifetimes et budgets.

Le choix de plan utilise seulement des capabilities authentifiées : toutes les
spécialisations qui changent passes, ressources, budgets ou lifetimes sont
sélectionnées ici. Le plan est ensuite gelé avec ses IDs et son implementation
kind finaux. Il n'alloue aucune texture et ne contient aucun handle backend.

### 5.4 `:gpu-renderer`

`:gpu-renderer` prépare, matérialise puis exécute exactement le plan gelé. Il ne
sélectionne aucune spécialisation modifiant passes, ressources ou budget : blur
séparable, RRect blur analytique et morphologie sont déjà encodés dans
l'implementation kind du plan.

Il ne peut ni recalculer les bounds, ni ajouter une pass, ni changer un format,
ni réémettre des IDs. Une spécialisation impossible revient à l'implémentation
W6 générique pendant la sélection du plan, jamais pendant la matérialisation et
jamais à une route legacy après admission.

### 5.5 `:kanvas`

`:kanvas` expose et capture les opérations publiques `Surface`/`Picture`. Il
maintient la stack save/restore publique et transmet les snapshots. Il ne
choisit ni le nombre de passes, ni les cibles, ni les programmes de filtres.

## 6. Espaces et bounds

W6 distingue explicitement :

- parameter space : espace des paramètres publics du filtre ;
- local space : espace du draw avant CTM ;
- layer space : espace raster intermédiaire choisi pour les filtres ;
- device space : espace de la cible parent.

Le mapping entre ces espaces est immuable et authentifié de la capture à
l'exécution. Chaque nœud de filtre fournit deux opérations distinctes :

1. `requiredInputBounds` : bounds d'entrée minimales nécessaires pour produire
   une sortie demandée ;
2. `producedOutputBounds` : bounds de sortie produites par un contenu connu.

Le plan distingue quatre régions, sans les réduire à un seul rectangle :

1. `knownContent` : union du contenu initialisé et du contenu produit par les
   enfants ; une initialisation transparente contribue vide, backdrop contribue
   son résultat filtré et `initWithPrevious` contribue le snapshot parent ;
2. `desiredOutput` : sortie demandée, initialement le clip device de la cible
   parent ;
3. `requiredInput` : entrée minimale propagée à rebours dans le DAG ;
4. `producedOutput` : sortie propagée vers l'avant depuis `knownContent`.

Le domaine de composite final est calculé après le DAG avec l'alpha, le color
filter et le blender de restore. Blur, convolution, displacement et shadows
peuvent agrandir `requiredInput` et `producedOutput`. Crop, clip et target les
bornent uniquement selon leur sémantique propre.

Les bounds fournies à `saveLayer` sont un hint/filter region, jamais un clip
implicite. Leur admission suit les mêmes prédicats que la révision Skia
épinglée :

- `filtersPriorDevice` est vrai pour backdrop, ou pour `initWithPrevious`
  combiné avec un image filter, un color filter, un blender ou un alpha de
  restore inférieur à 1 ;
- `restoreMustFillClip` est vrai sans image filter lorsqu'un color filter ou
  un blender transforme transparent black ;
- `trivialRestore` vaut
  `!filtersPriorDevice && !restoreMustFillClip`.

Le hint peut borner la région intermédiaire seulement pour `trivialRestore`.
Cela inclut `initWithPrevious` seul : les pixels parent non matérialisés hors du
hint sont un identity passthrough et restent déjà inchangés dans la cible
parent. Ils ne disparaissent pas de la sémantique de `knownContent`; le plan
prouve seulement qu'ils n'ont besoin ni d'une copie ni d'un composite. Lorsque
`trivialRestore` est faux, le hint ne peut tronquer ni `requiredInput` sur le
parent ni le domaine de composite final : `desiredOutput` reste le clip device
de la cible parent. Un restore produisant hors du contenu connu étend également
le domaine final au clip parent. Un hard clip exige une opération de clip ou un
nœud Crop explicite.

Chaque filtre déclare aussi :

- s'il utilise la source implicite ;
- s'il transforme transparent black en une valeur non transparente ;
- sa capacité de mapping : translation/scale, affine ou complexe ;
- son comportement hors source et son tile mode.

Ces faits contrôlent les elisions et empêchent de réduire un layer dont le
filtre produit du contenu hors de la source. Le plan encode séparément
`readsPriorDevice` et `restoreAffectsTransparentBlack`; alpha seul ne crée pas
de couleur depuis transparent black, mais `initWithPrevious` plus alpha reste
dépendant du prior device. Les propriétés exactes du color filter et du blender
participent donc au calcul de bounds et à l'admission.

## 7. Modèle des layers

Chaque scope capturé contient :

- une identité d'occurrence et son parent ;
- ses enfants ordonnés ;
- les bounds demandées et effectives ;
- le mapping vers le parent ;
- le paint de restore : alpha, color filter et blend ;
- le DAG de filtres source ;
- une éventuelle entrée backdrop ;
- le flag public `initWithPrevious`, distinct du backdrop ;
- format, espace colorimétrique et sample count requis ;
- contraintes de lifetime et budget.

Deux scopes ou filtres structurellement égaux restent distincts lorsqu'ils ont
des owners capturés distincts. Une mutualisation n'est admise que pour un nœud
réellement partagé dans le DAG, avec identity et lifetime communs.

Les layers imbriqués s'exécutent de l'intérieur vers l'extérieur, tout en
conservant l'ordre de paint. Un enfant composite dans la cible de son parent ;
seul le scope racine composite dans la cible de scène.

## 8. Ordre save/restore

La préparation transactionnelle W6 est :

1. capture profonde et validation ;
2. résolution des mappings et bounds sémantiques ;
3. sélection des spécialisations à partir de ces faits et des capabilities ;
4. construction du DAG physique final, des ressources et des budgets ;
5. validation globale puis gel du plan handle-free ;
6. réservation et matérialisation non publiée des targets/scratch ;
7. publication d'un ready token natif complet.

Le plan gelé encode ensuite cet ordre d'exécution, réalisé après soumission :

1. initialisation transparent black ou backdrop/previous ;
2. rendu ordonné des enfants ;
3. exécution du DAG de filtres ;
4. application de l'alpha et du color filter de restore ;
5. blend final unique dans le parent.

Le style, path effect, image filter et mask filter du paint ne sont pas
réinterprétés comme des attributs géométriques pendant le restore. L'image
filter est détenu séparément par le scope ; alpha, color filter et blender sont
appliqués après son résultat.

Le backdrop est évalué parent vers layer au moment logique du save, avant les
draws enfants. Il utilise un snapshot distinct ou une copie backend prouvée
équivalente ; la render attachment active n'est jamais échantillonnée
directement. L'initialisation est exclusive et ordonnée : backdrop filtre un
snapshot du parent s'il est présent ; sinon `initWithPrevious` copie le parent
sans filtre ; sinon le layer démarre en transparent black. Le restore évalue
layer vers parent.

L'auto-layer d'un draw possède un contrat distinct du restore explicite. Chaque
attribut du paint est consommé exactement une fois, dans cet ordre :

1. géométrie, style et path effect déterminent la couverture brute ;
2. le mask filter transforme cette couverture ;
3. shader, couleur, alpha et color filter produisent la source du draw ;
4. l'image filter consomme l'image source ainsi formée ;
5. le clip borne la sortie écrite dans la cible ;
6. le blender final composite une seule fois vers la destination.

`MaskFilter.Blur` applique son style à la couverture ; `MaskFilter.Shader`
multiplie cette couverture par l'alpha du matériau W5 évalué dans le même
mapping ; `MaskFilter.Table` applique une LUT immuable de 256 entrées à la
couverture 8-bit. Les nouvelles constructions `Table` exigent 256 entrées. Les
Pictures historiques contenant une autre taille restent décodables, mais leur
admission W6 échoue avec le diagnostic stable
`invalid.mask_filter.table_length`. Le mask shader réutilise l'autorité
matériau W5 et n'introduit aucun second compilateur.

Le planning retire du paint intermédiaire les attributs déjà consommés. Alpha,
color filter, image filter, mask filter et blend ne peuvent donc être appliqués
deux fois entre draw, layer implicite et composite. Le clip participe aux
bounds de chaque pass mais n'est pas transformé en un second effet de paint.

## 9. Elisions et optimisations

Une elision nécessite une preuve immuable. Sont notamment prouvables :

- clip vide ;
- alpha nul avec blend qui ne modifie pas la destination ;
- filtre identité ;
- layer sans backdrop ni effet dont le restore est équivalent aux draws
  directs ;
- réutilisation d'un résultat de filtre avec identité, mapping, source,
  génération et bounds exacts.

En absence de preuve, le layer normal est conservé. Une optimisation ne peut
pas changer les arrondis de bounds, la colorimétrie, l'ordre, les budgets ou le
diagnostic public.

## 10. Familles de filtres

### 10.1 W6a — layer authority

W6a ferme `saveLayer`, restore, alpha, color filter, blends de restore déjà
portés par W5, target offscreen, layers imbriqués et `initWithPrevious` sans
filtre spatial. `SaveLayerRec` gagne un champ public `initWithPrevious` avec la
valeur par défaut `false`; l'IR et le wire le snapshotent, et les anciens
records sont décodés avec `false`.

### 10.2 W6b — blur, masks et shadows

W6b ferme :

- blur image X/Y et tile modes ;
- les trois familles publiques de mask filter : blur normal/solid/outer/inner,
  shader de couverture et table de couverture ;
- drop shadow et shadow-only, via un mode public `DropShadowMode` dont la
  valeur par défaut `COMPOSITE` préserve le comportement existant et dont
  `SHADOW_ONLY` est snapshoté par l'IR et le wire ; les anciens records sont
  décodés en `COMPOSITE` ;
- auto-layer public pour les draws portant un mask ou image filter ;
- chemins analytiques quand ils sont strictement équivalents.

### 10.3 W6c — DAG spatial principal

W6c ferme `Crop`, `Offset`, `Tile`, `ColorFilter`, `Compose`, `Merge`, `Blend`,
`Dilate` et `Erode`, y compris partage explicite des inputs et lifetimes.

### 10.4 W6d — effets avancés et backdrop

W6d ferme `MatrixConvolution`, `DisplacementMap`, `Magnifier`, les six familles
lighting, `Picture`, `RuntimeEffect` enregistré, backdrop et
`initWithPrevious` filtré. W6d étend explicitement le catalogue W5h à plusieurs
ABI et livre au moins le built-in versionné `kanvas.runtime.image-opacity` : ABI
`IMAGE_FILTER`, un child image `input`, un uniforme F32 `alpha`, lecture du même
pixel, multiplication premultiplied de RGBA, bounds inchangées et aucun
échantillonnage spatial. Le child absent utilise le sentinel
`ImplicitSource` et suit le binding contextuel de la section 5.1. Ce chemin
positif prouve qu'un runtime image filter catalogué est exécutable. Aucun
frontend WGSL/SkSL arbitraire n'est introduit ; un effet non catalogué reste
refusé.

La colorimétrie du layer hérite du parent par défaut. Un espace ou format
explicite n'est admis que si la capture publique le porte et si le backend le
matérialise sans conversion implicite. RGBA8 est le domaine initial ; F16 doit
être admis ou refusé par capability exacte, jamais remplacé silencieusement.

### 10.5 W6e — convergence

W6e combine les 22 familles avec les lanes W4/W5 applicables, Picture,
nesting, origines, crops, destination-read, budgets, caches, refus tardifs et
récupération sur la même `Surface`.

## 11. Ressources, lifetimes et budgets

Le plan recense avant préparation native :

- targets de layers ;
- snapshots backdrop et destination-read ;
- textures intermédiaires ;
- ping-pong de blur, morphologie et convolution ;
- buffers, staging, uniforms, samplers et programmes ;
- ressources W5 référencées par les draws ou runtime filters.

Chaque ressource possède un owner, une génération, un descriptor, des usages
et un intervalle de vie. Deux ressources ne peuvent partager une allocation
que si leurs lifetimes ne se chevauchent pas et si leurs descriptors, usages,
formats et sample counts sont strictement compatibles.

Les additions et multiplications de budget utilisent I64 avec overflow
vérifié. Le budget couvre les bytes logiques et physiques, alignements inclus.
Un refus de budget précède toute allocation et garde un diagnostic stable.

## 12. Matérialisation et rollback

W6 distingue quatre frontières qui ne partagent pas le mot
« publication » :

1. **plan selection/freeze** dans `:gpu-plan` : choix des spécialisations,
   passes, ressources, lifetimes, budget et IDs, sans handle natif ;
2. **native preparation/materialization** dans `:gpu-renderer` : réservation,
   allocation, binding, validation et seal derrière un ready token invisible ;
3. **native publication/commit** : publication atomique du ready token complet
   vers la queue de soumission ;
4. **submission/completion/public visibility** : soumission, achèvement GPU,
   puis seulement succès public de `Surface` et readback observable.

La séquence obligatoire est donc :

```text
capture -> select/freeze plan -> reserve -> allocate -> bind -> validate
        -> seal -> publish native token -> submit -> complete -> expose
```

Un choix qui change les passes, ressources, lifetimes ou budgets est interdit
après `select/freeze plan`. Avant la publication native, tout échec libère
l'intégralité des réservations. Après publication mais avant soumission, une
erreur retire ou met en quarantine le ready token complet. Après soumission,
Kanvas ne prétend pas annuler le travail GPU : la frame est terminalement
échouée, aucune réussite/readback partielle n'est exposée publiquement, et les
ressources restent leased jusqu'à completion ou quarantine sûre.

Une frame W6 ne peut pas publier un sibling sain et abandonner silencieusement
un layer refusé. Admission, budgets et publication sont atomiques pour la
frame entière du point de vue public ; cette atomicité signifie « aucun
résultat public partiel », pas un rollback physique du GPU après soumission.

Device loss et erreurs driver ne sont revendiqués comme prouvés que s'ils sont
observables via une API publique. Aucun fake device ou hook d'injection privé
n'est ajouté pour fermer artificiellement cette cellule.

## 13. Cache

Un résultat de filtre peut être réutilisé uniquement avec une clé contenant :

- identité de l'occurrence d'évaluation, donc nœud capturé et source implicite
  liée, plus version sémantique ;
- mapping layer/device ;
- bounds de sortie demandées ;
- identité, génération et subset de chaque source ;
- formats, espace colorimétrique et sample count ;
- capability/backend generation.

Le cache est explicite, budgété et soumis aux leases. Un hit ne contourne ni
le preflight, ni le budget, ni l'authentification de génération. Eviction,
retirement et device loss ne réaniment jamais un handle de génération
antérieure. Les règles W5 restent normatives : le budget est pessimiste même
sur cache hit et chaque ressource réutilisée garde sa lease jusqu'à completion
ou quarantine sûre de la frame consommatrice.

## 14. Capture et Picture

Tous les paramètres, arrays, couleurs, matrices, images en mémoire et graphes
sont snapshotés profondément. Une mutation après capture ne modifie ni le
rendu `Surface`, ni le replay `Picture` mémoire/wire.

W6 autorise les évolutions publiques additives nécessaires :
`SaveLayerRec.initWithPrevious = false` et
`DropShadowMode.COMPOSITE`. L'IR et le wire portent explicitement ces valeurs.
Les appels et anciens schémas restent décodables avec ces defaults, donc leur
comportement historique est conservé.

Le schéma Picture évolue aussi vers la table de nœuds et les références stables
définies en section 5.1. Un partage explicitement capturé reste partagé ; deux
sous-arbres seulement égaux restent distincts. Les anciens arbres récursifs
restent décodables avec leur sémantique historique et sans déduplication
implicite. Les graphes malformés, IDs inconnus, cycles, tailles incohérentes et
budgets de décodage sont refusés avant publication.

Picture filter référence une Picture immuable et bornée. Elle ne capture ni
surface vivante, ni codec externe, ni font generation.

## 15. Preuves publiques

Les tests utilisent seulement `Surface`, `Picture` et les APIs publiques de
paint/layer/filter. Chaque tranche prouve :

- pixels et replay mémoire/wire ;
- mutation post-capture ;
- ordre children, filtre, restore et clip ;
- bounds, crop, expansion, transparent black et origines ;
- nesting et interactions W4/W5 ;
- budgets aux frontières acceptée/refusée ;
- refus tardif sans publication et récupération sur la même `Surface`.

La matrice minimale de fermeture est distincte de l'inventaire des 22 image
filters :

| Capacité | Lot | Preuve publique minimale |
|---|---|---|
| saveLayer simple, imbriqué, restore | W6a | pixels, ordre, recovery |
| initWithPrevious public/wire | W6a puis filtré W6d | défaut false, mutation, roundtrip |
| Blur/Shader/Table mask filters | W6b | couverture discriminante et auto-layer |
| blur et deux modes de shadow | W6b | bounds, COMPOSITE/SHADOW_ONLY, roundtrip |
| 9 familles DAG principales | W6c | binding implicite, partage, multi-input |
| 11 familles avancées | W6d | oracle par famille, backdrop et runtime IMAGE_FILTER |
| combinaison des 22 familles | W6e | covering shardé, budgets et recovery |

Cette matrice compte les trois mask filters séparément : ils ne sont pas inclus
dans le total des 22 image filters.

Identity, copy, crop, offset et composites exacts exigent des pixels
byte-exact. Blur, convolution, lighting, magnifier et displacement utilisent
un oracle CPU indépendant et une tolérance propre à leur famille. Aucune
tolérance globale n'est élargie pour faire passer une matrice.

Chaque PR exécute ses RED/GREEN causaux, une préservation ciblée et les
compilations séparées des modules touchés. W6e utilise un covering public
shardé et séquentiel, jamais un monolithe connu pour durer plusieurs heures.
Les dettes historiques, skips et exits natifs sont comptés séparément ;
`native133` reste `UNKNOWN` et ne vaut pas succès.

## 16. Stack de livraison et reviews

La livraison est séquentielle :

1. `codex/w6a-layer-authority`, stackée sur W5h #2402 ;
2. W6b, stackée sur W6a ;
3. W6c, stackée sur W6b ;
4. W6d, stackée sur W6c ;
5. W6e, stackée sur W6d.

Chaque lot utilise un agent d'implémentation adapté, Astra pour les tâches
architecturales ou numériques complexes. Sol est réservé aux reviews. Une
seule vague de correction bornée, suivie d'une re-review Sol, est autorisée par
lot. L'exécution Gradle est sérialisée.

Les documents durables restent dans `refactor/` : cette spec, les plans, le
status W06 et le README. Les rapports intermédiaires d'agents restent ignorés.

## 17. Critères de fermeture W6

W6 est fermée lorsque :

1. `saveLayer`, restore, nesting, backdrop et `initWithPrevious` suivent
   l'ordre défini en section 8, avec les defaults publics/wire rétrocompatibles ;
2. les trois mask filters publics et les deux modes de drop shadow sont
   exécutables par leur auto-layer sans double application du paint ;
3. les 22 familles sont exécutables dans le domaine W6 défini ou portent une
   exclusion physique précise et documentée ;
4. `Compose` et tout DAG lient chaque source implicite au bon contexte, et le
   wire préserve le partage explicite sans aliaser par valeur ;
5. le catalogue contient au moins un `RuntimeEffect` ABI `IMAGE_FILTER`
   exécutable sans frontend arbitraire ;
6. aucune route admise ne reconstruit scope, bounds, ressources ou filtre ;
7. les quatre régions de bounds et leurs mappings restent identiques jusqu'au
   native ;
8. les budgets couvrent toutes les ressources avant allocation ;
9. les frontières plan gelé, publication native, soumission et visibilité
   publique sont distinctes, sans spécialisation tardive ;
10. aucun fallback sémantique n'est possible après admission ;
11. capture et Picture sont immuables et versionnés ;
12. les gates publiques ciblées passent sans nouveau nom de failure/error ;
13. le covering W6e est qualifié, shardé et attribue chaque dette conservée ;
14. font, codecs, GM et tests d'infrastructure ne sont pas utilisés pour
    gonfler la preuve ;
15. la review Sol finale de la stack ne contient aucun finding Critical ou
    Important ;
16. chaque sous-vague possède une Draft PR stackée, sans merge automatique.

## 18. Limites reportées

- convergence, score et rebaseline GM : W7 ;
- retrait des routes legacy et compatibility facades : W8 ;
- frontend SkSL/WGSL arbitraire ;
- formats externes et codecs ;
- font et glyph generation ;
- capabilities physiques réellement absentes, notamment certains chemins
  AA4/MSAA ou formats HDR, avec diagnostic exact ;
- device-loss non observable publiquement, tant qu'aucune API publique ne
  permet une preuve honnête.

Ces reports n'autorisent ni un fallback silencieux, ni une déclaration ISO ou
Skia globale.

## 19. Références Skia

La sémantique W6 est alignée sur les sources officielles Skia consultées le
2026-09-16, toutes épinglées à la révision `93912d50850d` :

- [`SkCanvas.h`](https://skia.googlesource.com/skia/+/93912d50850d/include/core/SkCanvas.h), pour `SaveLayerRec`, bounds, paint, backdrop, color space, tile mode et flags ;
- [`SkCanvas.cpp`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkCanvas.cpp), pour mapping/bounds, création du device, initialisation backdrop, restore et appels de l'auto-layer ;
- [`SkCanvasPriv.h`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkCanvasPriv.h) et [`SkCanvasPriv.cpp`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkCanvasPriv.cpp), pour l'implémentation de l'auto-layer et la partition des effets du paint ;
- [`SkImageFilter_Base.h`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkImageFilter_Base.h), pour DAG, input/output bounds, source implicite, CTM capability et transparent black ;
- [`SkImageFilter.cpp`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkImageFilter.cpp), pour évaluation récursive, contexte et cache génération/subset ;
- [`SkComposeImageFilter.cpp`](https://skia.googlesource.com/skia/+/93912d50850d/src/effects/imagefilters/SkComposeImageFilter.cpp), pour le binding contextuel de la source implicite entre `inner` et `outer`.

Ces références fixent la sémantique attendue. Elles n'introduisent aucune
dépendance au code Skia ni aucun test GM dans W6.
