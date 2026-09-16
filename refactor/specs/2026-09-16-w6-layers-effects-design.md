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

Les entrées d'un filtre sont exactement :

- source implicite du layer ;
- transparent black ;
- résultat d'un nœud identifié ;
- Picture capturée ;
- backdrop identifié.

Une entrée nulle compatible avec l'API Skia est normalisée en source implicite
avant la construction du plan. Un cycle, un nœud absent, une profondeur ou un
fan-out hors limites sont refusés pendant la capture, avant copie volumineuse.

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

Le plan contient les IDs finaux avant publication. Il n'alloue aucune texture
et ne contient aucun handle backend.

### 5.4 `:gpu-renderer`

`:gpu-renderer` abaisse exactement le plan publié. Il peut sélectionner une
implémentation spécialisée — blur séparable, RRect blur analytique, pass de
morphologie — seulement si elle authentifie le même nœud, les mêmes bounds, le
même budget et le même résultat sémantique.

Il ne peut ni recalculer les bounds, ni ajouter une pass, ni changer un format,
ni réémettre des IDs après publication. Une optimisation refusée revient à
l'implémentation W6 générique avant publication, jamais à une route legacy
après admission.

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

Le calcul inverse détermine les pixels à rendre dans le layer. Le calcul direct
détermine les pixels que le composite peut toucher. Blur, convolution,
displacement et shadows peuvent agrandir ces bounds. Crop, clip et target les
bornent selon leurs sémantiques propres.

Les bounds fournies à `saveLayer` sont un hint/filter region. Elles participent
au calcul de l'étendue intermédiaire mais ne modifient pas implicitement la
stack de clip. Un hard clip exige une opération de clip ou un nœud Crop
explicite.

Chaque filtre déclare aussi :

- s'il utilise la source implicite ;
- s'il transforme transparent black en une valeur non transparente ;
- sa capacité de mapping : translation/scale, affine ou complexe ;
- son comportement hors source et son tile mode.

Ces faits contrôlent les elisions et empêchent de réduire un layer dont le
filtre produit du contenu hors de la source.

## 7. Modèle des layers

Chaque scope capturé contient :

- une identité d'occurrence et son parent ;
- ses enfants ordonnés ;
- les bounds demandées et effectives ;
- le mapping vers le parent ;
- le paint de restore : alpha, color filter et blend ;
- le DAG de filtres source ;
- une éventuelle entrée backdrop ;
- format, espace colorimétrique et sample count requis ;
- contraintes de lifetime et budget.

Deux scopes ou filtres structurellement égaux restent distincts lorsqu'ils ont
des owners capturés distincts. Une mutualisation n'est admise que pour un nœud
réellement partagé dans le DAG, avec identity et lifetime communs.

Les layers imbriqués s'exécutent de l'intérieur vers l'extérieur, tout en
conservant l'ordre de paint. Un enfant composite dans la cible de son parent ;
seul le scope racine composite dans la cible de scène.

## 8. Ordre save/restore

L'ordre W6 est :

1. capture profonde et validation ;
2. résolution des mappings et bounds ;
3. construction topologique du DAG de passes ;
4. calcul global des ressources et budgets ;
5. réservation et allocation des targets/scratch ;
6. initialisation transparent black ou backdrop/previous ;
7. rendu ordonné des enfants ;
8. exécution du DAG de filtres ;
9. application de l'alpha et du color filter de restore ;
10. blend final unique dans le parent.

Le style, path effect, image filter et mask filter du paint ne sont pas
réinterprétés comme des attributs géométriques pendant le restore. L'image
filter est détenu séparément par le scope ; alpha, color filter et blender sont
appliqués après son résultat.

Le backdrop est évalué parent vers layer au moment logique du save, avant les
draws enfants. Il utilise un snapshot distinct ou une copie backend prouvée
équivalente ; la render attachment active n'est jamais échantillonnée
directement. Le restore évalue layer vers parent.

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
filtre spatial.

### 10.2 W6b — blur, masks et shadows

W6b ferme :

- blur image X/Y et tile modes ;
- mask blur normal, solid, outer et inner ;
- drop shadow et shadow-only ;
- auto-layer public pour les draws portant un mask ou image filter ;
- chemins analytiques quand ils sont strictement équivalents.

### 10.3 W6c — DAG spatial principal

W6c ferme `Crop`, `Offset`, `Tile`, `ColorFilter`, `Compose`, `Merge`, `Blend`,
`Dilate` et `Erode`, y compris partage explicite des inputs et lifetimes.

### 10.4 W6d — effets avancés et backdrop

W6d ferme `MatrixConvolution`, `DisplacementMap`, `Magnifier`, les six familles
lighting, `Picture`, `RuntimeEffect` enregistré, backdrop et
`initWithPrevious` filtré. Le runtime filter n'exécute que les programmes
catalogués par W5h ; aucun frontend WGSL/SkSL arbitraire n'est introduit.

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

La séquence obligatoire est :

```text
reserve -> allocate -> bind -> validate -> seal -> publish -> submit
```

Avant `publish`, tout échec libère l'intégralité des réservations. Après
publication, une erreur native est terminale pour la frame : ressources et
leases sont retirées ou mises en quarantine selon leur état, sans composite
partiel, ancienne route ou résultat transparent substitué.

Une frame W6 ne peut pas publier un sibling sain et abandonner silencieusement
un layer refusé. Admission, budgets et publication sont atomiques pour la
frame entière.

Device loss et erreurs driver ne sont revendiqués comme prouvés que s'ils sont
observables via une API publique. Aucun fake device ou hook d'injection privé
n'est ajouté pour fermer artificiellement cette cellule.

## 13. Cache

Un résultat de filtre peut être réutilisé uniquement avec une clé contenant :

- identité du DAG et version sémantique ;
- mapping layer/device ;
- bounds de sortie demandées ;
- identité, génération et subset de chaque source ;
- formats, espace colorimétrique et sample count ;
- capability/backend generation.

Le cache est explicite, budgété et soumis aux leases. Un hit ne contourne ni
le preflight, ni le budget, ni l'authentification de génération. Eviction,
retirement et device loss ne réaniment jamais un handle de génération
antérieure.

## 14. Capture et Picture

Tous les paramètres, arrays, couleurs, matrices, images en mémoire et graphes
sont snapshotés profondément. Une mutation après capture ne modifie ni le
rendu `Surface`, ni le replay `Picture` mémoire/wire.

Le schéma Picture évolue seulement pour les données W6 absentes du wire
actuel. Les anciens schémas restent décodables avec leur sémantique historique.
Les graphes malformés, IDs inconnus, cycles, tailles incohérentes et budgets de
décodage sont refusés avant publication.

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
   l'ordre défini en section 8 ;
2. les 22 familles sont exécutables dans le domaine W6 défini ou portent une
   exclusion physique précise et documentée ;
3. aucune route admise ne reconstruit scope, bounds, ressources ou filtre ;
4. les bounds input/output et mappings restent identiques jusqu'au native ;
5. les budgets couvrent toutes les ressources avant allocation ;
6. aucun fallback sémantique n'est possible après admission ;
7. capture et Picture sont immuables et versionnés ;
8. les gates publiques ciblées passent sans nouveau nom de failure/error ;
9. le covering W6e est qualifié, shardé et attribue chaque dette conservée ;
10. font, codecs, GM et tests d'infrastructure ne sont pas utilisés pour
    gonfler la preuve ;
11. la review Sol finale de la stack ne contient aucun finding Critical ou
    Important ;
12. chaque sous-vague possède une Draft PR stackée, sans merge automatique.

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
2026-09-16 :

- [`SkCanvas.h`](https://skia.googlesource.com/skia/+/refs/heads/main/include/core/SkCanvas.h), pour `SaveLayerRec`, bounds, paint, backdrop, color space, tile mode et flags ;
- [`SkCanvas.cpp`](https://skia.googlesource.com/skia/+/refs/heads/main/src/core/SkCanvas.cpp), pour mapping/bounds, création du device, initialisation backdrop, restore et ordre des effets ;
- [`SkImageFilter_Base.h`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkImageFilter_Base.h), pour DAG, input/output bounds, source implicite, CTM capability et transparent black ;
- [`SkImageFilter.cpp`](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkImageFilter.cpp), pour évaluation récursive, contexte et cache génération/subset.

Ces références fixent la sémantique attendue. Elles n'introduisent aucune
dépendance au code Skia ni aucun test GM dans W6.
