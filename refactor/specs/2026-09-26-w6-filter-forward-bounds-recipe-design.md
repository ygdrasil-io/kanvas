# W6 — recette contextuelle de bounds avant réservation des layers

Date : 2026-09-26

Statut : proposition à valider avant plan d'implémentation

Base stackée : `codex/w6d-advanced-effects`

Suite : `codex/w6e-effects-convergence`, à rebaser sur ce prérequis

Autorités inchangées :
[`2026-09-16-w6-layers-effects-design.md`](2026-09-16-w6-layers-effects-design.md)
et
[`2026-09-22-w6b-w6e-stacked-delivery-design.md`](2026-09-22-w6b-w6e-stacked-delivery-design.md).
Ce document amende uniquement l'ordre de calcul interne de `:gpu-plan` : W6e
reste une vague de convergence sans nouveau planner.

## 1. Problème observé

Le RED public W6e Task 5 est conservé sur sa branche : 13 échecs JUnit sur
24 tests de Surface, dont `Blur`, `Offset`, `Tile`, `Compose`, `Dilate`, les six
lighting et un Offset sous trois parents non bornés. Les sorties filtrées
hors du contenu brut de l'enfant disparaissent lors du restore. Les sorties
natives 133 restent `UNKNOWN`, indépendamment de ces assertions JUnit.

`W6aLayerGraphConstruction` fixe aujourd'hui `producedOutputByScope` à
`knownContent ∩ desiredOutput` (sauf restore qui affecte transparent black),
puis réserve les targets parents. La sortie réellement produite par le filtre
n'est calculée que plus tard dans
`W6bFilterGraphConstruction.freezeImageOccurrence`, pendant
`materializeNode`, en même temps que les passes, ressources, IDs et clés. Le
parent peut donc être trop petit. La correction provisoire « sortie = clip
complet » a été retirée : elle masque la perte de pixels mais sur-alloue un
petit `Crop` ou un `ColorFilter` identité.

La correction est un prérequis architectural distinct de W6e, livré par une
Draft PR stackée entre W6d et W6e. Elle ne change ni les APIs publiques, ni le
wire, ni les formats, ni les kernels, ni les exclusions W6.

## 2. Options et décision

1. **Élargir tout layer filtré au `desiredOutput` parent.** Simple, mais
   confond la sortie demandée et celle produite, et fait échouer le budget
   B/B−1 des filtres qui ne grossissent pas le contenu. Rejeté.
2. **Précalculer les bounds par un second parcours du DAG dans W6a.** Peut
   réserver le parent à temps, mais duplique les décisions de W6b : Compose,
   Picture, cache, sources implicites et choix d'opérations peuvent diverger.
   Rejeté.
3. **Extraire une recette contextuelle immuable du parcours W6b existant.**
   Une occurrence capturée est liée et analysée une fois, avant la réservation
   W6a. W6a consomme les seules régions produites ; W6b abaisse ensuite la
   même recette en passes et ressources. Retenu.

Une passe de demande inverse et une passe de propagation avant à travers la
*même recette* sont permises : elles ne sont pas deux interprétations
indépendantes du DAG capturé. Les méthodes de bounds W6b/W6c/W6d existantes
sont réutilisées ou déplacées vers cette autorité unique, non recopiées dans
W6a.

## 3. Contrat de la recette

`gpu-plan` construit une recette privée, immuable et propre à une occurrence
d'évaluation. Elle conserve :

- l'identité du nœud capturé et la provenance de sa source liée, sans aliaser
  deux sous-arbres égaux par valeur ;
- le mapping F64 capturé et la sortie demandée dans l'espace device ;
- les entrées contextuelles explicites : source implicite, transparent black,
  résultat de nœud, Picture et backdrop ;
- l'ordre sélectionné des opérations et leurs paramètres suffisants pour
  abaisser les `FilterPass` existants sans relire le DAG capturé ;
- séparément, `knownContent`, `desiredOutput`, `requiredInput` et
  `producedOutput` pour chaque opération et pour le terminal ;
- le domaine physique de la source à échantillonner et son ancrage, distincts
  de ces quatre régions et gelés avant l'évaluation forward du filtre.

Chaque région est une valeur copiée/gelée. `knownContent` décrit l'entrée
initialisée, pas la taille du target ; `desiredOutput` est la demande du
consommateur, pas une promesse de pixels ; `requiredInput` sert à réserver et
échantillonner la source ; `producedOutput` est la sortie forward effectivement
susceptible d'être écrite. Chaque opération intersecte sa production avec
**sa propre demande** propagée depuis ses consommateurs, non avec le clip du
terminal. Seule la sortie terminale se borne à la demande terminale. Ainsi
`Compose(Offset(-20), Offset(+20))` peut garder un résultat intermédiaire hors
du clip final qui y revient ensuite. Un résultat vide est distinct d'une
entrée invalide. Un effet qui crée des pixels à partir de transparent black
(notamment lighting) peut produire jusqu'à sa demande ; un `Crop` DECAL ou un
`ColorFilter` identité ne le fait pas.

La recette est **sémantique**, non physique : elle ne contient ni texture
native, ni `PlanResourceId` final, ni numéro de pass, ni allocation. Son calcul
utilise des faits de source et de mapping indépendants de l'étendue du target
**parent** encore à réserver, mais pas de celle du target **source**. W6a
scelle d'abord le domaine source et l'ancrage de sampling à partir du contenu
direct, des sorties enfants, du hint admissible et des pixels de snapshot
effectivement disponibles. La demande inverse guide le raster et les snapshots,
mais n'élargit pas automatiquement le domaine source fini : un échantillon hors
de ce domaine suit le mode de bord du filtre (`DECAL`/`CLAMP`). `blurBounds`,
`Offset`, `MatrixConvolution` et les modes de
bord du lighting réutilisent exactement ce domaine, non une approximation par
`knownContent`. La source ne se déduit jamais de la sortie du même filtre. Si
une famille a besoin d'un input Picture, la recette conserve sa provenance ;
le binding physique et l'abaissement utilisent ensuite l'origine gelée, sans
changer les quatre régions. Aucune nouvelle classe géométrique hors `:math` ;
les rectangles/points/tailles/mappings éventuels suivent
`I32`/`I64`/`F32`/`F64` et les projections F64→I32 restent checked avec
arrondi extérieur.

## 4. Ordre de construction

1. W6a découvre les occurrences, fige les restore facts et propage les clips
   de sortie parent vers enfants sans les rabattre sur une allocation future.
   W6b lie **une seule fois** chaque topologie capturée et ses sources
   contextuelles dans une recette, sans émettre de passes ni d'IDs physiques.
2. Depuis le clip de sortie, la phase inverse de **cette même recette**
   propage les demandes par dépendance avant toute intersection forward :
   filtre parent → entrées → enfants de scope/Picture. `desiredOutput` reste
   distinct de `requiredInput`. Un `deferredCompositeClip` Picture borne le
   composite final, non l'entrée du filtre parent ; un `recordedInnerClip`
   reste un hard clip de son enfant. Les hints `saveLayer` ne deviennent pas
   des hard clips. La phase inverse fournit également les besoins des
   snapshots backdrop/previous avant leur dimensionnement.
3. En post-ordre des scopes et des auto-layers de draws/Picture entries, W6a
   réunit contenu direct, snapshots `initWithPrevious`/backdrop et **sorties
   terminales après clip/blend** des enfants. Il scelle le domaine source
   échantillonné (contenu + sorties enfants + hint admissible + pixels de
   snapshot disponibles) et son ancrage. La demande inverse sélectionne les
   pixels à produire/lire dans ce domaine sans déplacer son bord physique.
   Cette étape ne dépend pas de la sortie
   filtrée du même scope ni de l'allocation de son parent. Pour un draw filtré,
   le domaine de son auto-layer vient du raster capturé et de sa propre
   demande inverse, pas du target parent encore absent. La phase forward de
   la recette évalue alors chaque opération sur cette source scellée.
   Les dépendances Picture et backdrop conservent leur identité et leur
   instant de capture ; aucune lecture anticipée du parent mutable.
4. W6a calcule le domaine réellement composable du restore depuis la sortie
   de la recette, son clip, l'alpha, le color filter et le blender. Pour un
   draw directement filtré, sa sortie terminale contribue au `knownContent`
   du scope courant avant que celui-ci réserve son target. W6a réserve le
   parent pour cette sortie et pour ses propres besoins de snapshot ou de
   destination-read. Il ne confond plus l'étendue physique du target enfant
   (halo/hint/input) avec les pixels que cet enfant écrit dans le parent.
5. Une fois toutes les géométries scellées, W6b abaisse la recette vers les
   `FilterPass`, sources, clés, IDs et budgets actuels, en ordre capturé. Cet
   abaissement peut fixer des coordonnées target-local dérivées de l'origine
   scellée, mais il ne recalcule ni la topologie du DAG, ni les quatre régions,
   ni l'implementation kind. Les choix qui affectent passes, ressources ou
   budget sont gelés dans `:gpu-plan` avant toute allocation native.

Le planner refuse une incohérence entre les besoins de la recette et la
géométrie réservée ; il n'élargit pas silencieusement le target et ne tente
pas de fallback legacy. Les diagnostics restent attribués à leur owner
W6a/W6b et aucune frame partielle n'est publiée.

## 5. Cas limites contraignants

- **Expansion** : Blur, DropShadow, Offset, Tile, Dilate, Compose et les
  branches pertinentes de Merge/Blend propagent leurs sorties au-delà du
  contenu brut lorsque leurs propres règles le permettent. Lighting couvre
  les pixels synthétisés sur transparent black. La demande inverse garde le
  halo nécessaire sans forcer la sortie à la taille du halo.
- **Non-expansion et budget** : petit Crop DECAL et ColorFilter identité sous
  un grand clip gardent une sortie de taille contenu/crop ; B doit être admis
  et B−1 refusé sur leurs coûts physiques, sans « tout le clip » implicite.
- **Entrées multiples** : Compose lie `inner` à la source courante puis
  `outer` au résultat de `inner`; Merge/Blend conservent chaque position et
  sa source. Les clés de cache gardent occurrence, source liée, mapping,
  demande, génération et provenance. Un cache hit ne réduit pas le budget
  pessimiste ni la durée des leases.
- **Snapshot temporel** : backdrop prélève le parent au save ; un layer
  `initWithPrevious` est filtré après ses draws enfants. Les besoins inverses
  peuvent agrandir le snapshot, sans déplacer la séquence physique
  Begin → enfants → Seal → lecteur.
- **Picture/replay** : partage capturé et nœud equal-distinct restent
  discriminés en mémoire et après wire replay. La recette n'ajoute aucun
  champ de sérialisation ni hash canonique nouveau.
- **Refus/atomicité** : overflow, mapping non représentable, graph limits,
  capability, budget et échec tardif restent terminaux pour la frame, sans
  readback partiel ; la même `Surface` permet discard/re-record.

## 6. Preuve et livraison

Le prérequis commence en TDD public (`Surface`, `Picture`, scopes `Render` et
`Readback`) avec les témoins RED minimaux : Offset sous trois parents, Blur
imbriqué, `ImageFilter.Offset`/Blur sur un draw direct sous parent non filtré,
expansion lighting, petit Crop/ColorFilter sous grand clip avec B/B−1,
`Compose(Offset(-20), Offset(+20))` (intermédiaire hors clip), multi-input,
et replay mémoire/wire pour le partage et l'equal-distinct. Les oracles sont
indépendants et construits avant `Surface`.
Les tests W6e Task 5 existants restent RED sur la branche W6e jusqu'au
restack ; ils deviennent le gate d'intégration après cette PR. Aucun test
d'infrastructure, réflexion, fake device, GM, dashboard, render ou rebaseline.

Le prérequis reçoit une review de tâche Sol et une review whole-branch Sol ;
Astra n'intervient qu'en cas de blocage architectural résiduel. Les sélecteurs
Gradle sont sérialisés. Un XML JUnit vert avec worker natif 133/134 est noté
`UNKNOWN`, jamais PASS global. La Draft PR vise
`codex/w6d-advanced-effects`. Après sa validation, W6e est rebasée dessus,
ses Tasks 5 et 6 sont terminées, puis sa propre Draft PR vise cette branche
prérequis. Aucun merge automatique.
