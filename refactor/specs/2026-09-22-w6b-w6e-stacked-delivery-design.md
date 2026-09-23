# W6b–W6e — livraison stackée des effets spatiaux

Date : 2026-09-22

Autorité sémantique :
[`2026-09-16-w6-layers-effects-design.md`](2026-09-16-w6-layers-effects-design.md)

Base livrée : `codex/w6a-layer-authority` / Draft PR #2403

## 1. Intention

Terminer toutes les parties restantes de W6 sous forme de quatre Draft PRs
stackées, chacune suffisamment cohérente pour être testée et reviewée sans
ouvrir une autorité concurrente :

1. `codex/w6b-blur-masks-shadows`, sur W6a ;
2. `codex/w6c-spatial-dag`, sur W6b ;
3. `codex/w6d-advanced-effects`, sur W6c ;
4. `codex/w6e-effects-convergence`, sur W6d.

Aucune PR n'est mergée automatiquement. W6e ferme la preuve de la stack mais
ne remplace ni W7 pour les GMs, ni W8 pour le retrait des routes legacy.

## 2. Décision de découpage

Une PR par sous-wave est retenue. Un découpage plus fin en PRs de capture,
planning et renderer multiplierait les bases intermédiaires non exécutables ;
une PR unique W6b–W6d rendrait les invariants de bounds, lifetimes et budgets
trop difficiles à review. Les commits internes de chaque PR restent découpés
par vertical slice et portent leur propre RED/GREEN public.

La stack est strictement linéaire. Chaque branche part du HEAD reviewé de la
branche précédente et sa Draft PR cible cette branche précédente, jamais W5h
ou `main` directement.

## 3. Autorité commune

La chaîne W6a reste unique :

```text
Surface/Picture
  -> render-ir immuable
  -> géométrie et mappings :math
  -> scopes + DAG spatial + plan physique :gpu-plan
  -> matérialisation exacte :gpu-renderer
  -> submit unique
  -> visibilité publique atomique
```

### 3.1 Capture et wire

W6b introduit la fondation partagée du DAG de filtres : une table immuable de
nœuds identifiés et des références d'entrée typées. Les formes communes sont :

- `ImplicitSource` ;
- `TransparentBlack` ;
- `Node(CapturedFilterNodeId)` ;
- `Picture(CapturedPictureId)`, réservé jusqu'à W6d ;
- `Backdrop(CapturedBackdropId)`, réservé jusqu'à W6d.

Un input public nul devient `ImplicitSource`. Deux sous-arbres égaux par valeur
restent distincts. Un partage n'existe que si la capture publique partage la
même identité. Le décodage des anciens arbres récursifs alloue un ID distinct
par occurrence et ne déduplique rien implicitement.

Picture 14/schema 8 porte la table, ses roots et `DropShadowMode`. Les archives
antérieures restent lisibles : absence de mode vaut `COMPOSITE`, absence de
table utilise la conversion récursive sans alias ajouté.

### 3.2 Géométrie et bounds

`:math` reste le seul propriétaire des nouveaux rectangles, points, tailles,
mappings et matrices, avec nomenclature `I32`, `I64`, `F32` ou `F64`.

Chaque occurrence conserve séparément `knownContent`, `desiredOutput`,
`requiredInput` et `producedOutput`. Les calculs sont F64 ; la projection en
texels I32 arrondit vers l'extérieur et refuse NaN, infini, horizon impossible
et overflow. Le renderer ne reconstruit aucune bounds.

### 3.3 Occurrences, passes et ressources

Le planner identifie une évaluation par :

```text
(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)
```

Une mutualisation exige l'égalité de ces quatre faits et des générations de
sources. Le plan physique gèle avant toute allocation les `FilterPass`,
`FilterTarget`, snapshots, ping-pong, uniforms, samplers, programmes, IDs,
slots, usages, formats, sample counts, lifetimes et budgets I64 checked.

W6b introduit un seul contrat de pass spatial extensible. W6c et W6d ajoutent
des payloads typés à ce contrat ; ils ne créent ni nouveau graph de frame, ni
second allocator, ni second submit.

### 3.4 Matérialisation et erreurs

Le renderer peut conserver des encoders ou kernels spécialisés par famille,
mais il ne choisit après freeze ni spécialisation, ni bounds, ni ressource, ni
pass, ni budget. Une spécialisation indisponible est refusée pendant le plan ou
remplacée alors par une implémentation W6 générique déjà budgétée.

Après sélection W6, aucun fallback legacy n'est possible. Toute erreur est
terminale pour la frame, ne publie aucun readback partiel, conserve le
diagnostic de son owner et permet un discard/re-record public sur la même
`Surface`. Les exits natifs 133/134 restent `UNKNOWN`.

## 4. W6b — blur, masks et shadows

W6b livre :

- blur image X/Y et les quatre tile modes ;
- `MaskFilter.Blur` NORMAL/SOLID/OUTER/INNER ;
- `MaskFilter.Shader` lié à l'autorité matériau W5 ;
- `MaskFilter.Table` avec 256 entrées pour les nouvelles constructions et
  diagnostic `invalid.mask_filter.table_length` pour les anciennes valeurs
  incompatibles ;
- `DropShadowMode.COMPOSITE` par défaut et `SHADOW_ONLY` ;
- l'auto-layer public des draws portant image ou mask filter ;
- les chemins analytiques seulement lorsqu'ils sont strictement équivalents.

Les ping-pong blur, masques, LUTs, sources, shadows et composites appartiennent
au plan physique W6. Backdrop, `initWithPrevious` filtré, F16/HDR et familles
W6c/W6d restent des refus précis.

## 5. W6c — DAG spatial principal

W6c active neuf familles sur le contrat commun : `Crop`, `Offset`, `Tile`,
`ColorFilter`, `Compose`, `Merge`, `Blend`, `Dilate` et `Erode`.

`Compose` lie `inner` à la source courante puis `outer` au résultat de
`inner`. `Merge` et `Blend` conservent l'ordre et les sources de chaque input.
`ColorFilter` réutilise l'autorité numérique W5f sans seconde table.

W6c ferme aussi le cache spatial. Sa clé contient l'occurrence, la source liée,
le mapping, la sortie demandée, les bounds, formats, générations et
capabilities. Le budget reste pessimiste sur hit et la lease reste active
jusqu'à completion ou quarantine sûre.

## 6. W6d — effets avancés et backdrop

W6d active onze familles : `MatrixConvolution`, `DisplacementMap`,
`Magnifier`, les six lighting, `Picture` et `RuntimeEffect`.

Il livre également :

- backdrop snapshoté puis filtré depuis le parent au moment logique du save,
  jamais depuis l'attachment actif et toujours avant les draws enfants ;
- `initWithPrevious` copie le parent sans filtre au save ; le DAG du layer
  s'évalue seulement après les draws enfants, avant l'alpha, le color filter et
  le blend de restore. Un témoin public combine contenu parent et enfant
  discriminants afin d'interdire une évaluation anticipée au save ;
- Picture filter fondé sur une `SceneSnapshot` immuable et budgetée ;
- catalogue runtime multi-ABI ;
- built-in versionné `kanvas.runtime.image-opacity`, ABI `IMAGE_FILTER`, child
  image `input`, uniforme F32 `alpha`, child absent lié à `ImplicitSource`,
  bounds inchangées et aucun sampling spatial.

Les tags canoniques IMAGE_FILTER sont additifs et ne modifient aucun hash W5h
existant. Un runtime non catalogué ou une ABI incorrecte est refusé avant
publication. RGBA8 est le seul format positif ; F16/HDR reste un refus
capability exact tant que la capture et le backend ne le prouvent pas de bout
en bout.

## 7. W6e — convergence

W6e n'ajoute ni API, ni planner, ni algorithme, ni format. Elle exécute six
shards publics séquentiels :

1. spatial core : Crop, Blur, DropShadow, Offset, Tile ;
2. composition : ColorFilter, Compose, Blend, Dilate, Erode, Merge ;
3. lighting/Picture : six lighting et Picture ;
4. advanced sampling : DisplacementMap, Magnifier, MatrixConvolution et
   runtime IMAGE_FILTER ;
5. cross-lane : 22 familles avec W4/W5, nesting, backdrop, previous, crop et
   destination-read ;
6. budget/cache/recovery : B/B−1, cache pessimiste, refus tardif, sentinelle
   et récupération observables publiquement ; la review structurelle vérifie
   les leases, générations et l'absence de replanning sans test de source ni
   hook privé.

Les quatre premiers shards couvrent exactement 5 + 6 + 7 + 4 = 22 familles.
Les deux derniers prouvent leurs interactions sans masquer l'attribution des
échecs.

## 8. Preuves et tolérances

Les tests passent uniquement par `Surface`, `Picture` et les APIs publiques.
Chaque expected est calculé indépendamment avant création de la `Surface`.

- identity, copy, crop, offset et composites exacts sont byte-exact ;
- blur, convolution, lighting, magnifier et displacement utilisent un oracle
  CPU indépendant et une tolérance propre à leur famille ;
- aucune tolérance globale n'est élargie ;
- chaque PR prouve mutation post-capture, replay mémoire/wire, bounds,
  origines, clips, B/B−1, refus terminal, sentinelle et recovery ;
- les témoins positifs passent explicitement par les scopes publics `Render`
  et `Readback`, afin qu'un succès par fallback ne satisfasse pas la preuve ;
- chaque PR exécute ses shards causaux, une préservation ciblée des waves
  précédentes et les compilations séparées des modules touchés.

Sont interdits : tests privés, reflection, fake device, mocks backend,
compteurs internes et tests statiques d'infrastructure.

## 9. Agents, reviews et PRs

L'implémentation utilise des Subagents Terra par défaut. Astra est réservé à
un blocage architectural ou numérique démontré ; Sol est réservé aux reviews.

Chaque tâche d'un plan reçoit une review Sol. Chaque PR reçoit ensuite une
review whole-branch Sol, au plus une vague de correction bornée par un agent
non-Sol, puis une re-review Sol scoped. Les tests Gradle sont sérialisés.

Une Draft PR n'est poussée qu'après fermeture de ces gates. Sa description
contient scope, architecture, commits, résultats, exclusions, statut natif et
base exacte de la stack. Aucun merge n'est effectué.

## 10. Exclusions inchangées

- fonts et génération de glyphes ;
- codecs et formats externes ;
- GMs, dashboard, renders, références, scores et rebaseline ;
- suite Skia globale et `jpg-color-cube` ;
- frontend SkSL/WGSL arbitraire ;
- retrait des routes legacy, réservé à W8 ;
- preuve device-loss tant qu'elle n'est pas observable publiquement ;
- claim ISO ou convergence Skia globale.

## 11. Definition of Done

La livraison W6b–W6e est terminée lorsque :

- les quatre branches et Draft PRs sont ouvertes avec les bases exactes ;
- Picture 14/schema 8 et les anciens readers sont qualifiés ;
- blur, masks, shadows, neuf familles principales et onze familles avancées
  sont exécutables dans leur domaine admis ou refusées par capability exacte ;
- backdrop, previous filtré, Picture filter et runtime image-opacity suivent
  le même graph W6 ;
- aucune route admise ne replannifie après freeze ou ne tombe en legacy ;
- budgets, caches, leases et visibility restent transactionnels ;
- le covering W6e attribue les 22 familles et leurs interactions ;
- les reviews Sol ne laissent aucun finding Critical ou Important ;
- les documents durables W06 et README décrivent résultats et exclusions sans
  claim ISO/globale.
