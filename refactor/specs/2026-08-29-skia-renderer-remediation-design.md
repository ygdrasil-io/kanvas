# Remédiation architecturale du renderer Skia

Date : 2026-08-29  
Statut : design validé en conversation, en attente de revue du document  
Périmètre : `kanvas`, `math`, `color-management`, `gpu-renderer` et tests de
conformance associés  
Hors périmètre : `font`, décodage/encodage `codec`, windowing natif et
optimisation de performance avant la gate de fidélité

## 1. Résumé de la décision

Le renderer sera remédié par remplacement progressif de l'architecture
actuelle, et non par ajout continu de routes spécialisées.

La cible introduit :

1. des objets géométriques immuables possédés par les modules `:math` ;
2. une `Scene IR` (représentation intermédiaire de scène) immuable et
   backend-agnostic dans un nouveau module `:render-ir` ;
3. un planner compositionnel handle-free dans un nouveau module `:gpu-plan` ;
4. un `RenderGraph` (graphe de rendu) général pour les passes, ressources,
   layers, filtres et destination reads ;
5. un `gpu-renderer` réduit à la matérialisation et à l'exécution WebGPU ;
6. une migration de type `strangler` (remplacement progressif) où une frame
   complète utilise soit le nouveau moteur, soit temporairement l'ancien ;
7. une gate globale de conformance fondée sur les GMs éligibles.

L'API publique `Canvas`/`Surface` est conservée autant que possible. Les
contrats internes GPU peuvent être cassés. La sérialisation `Picture` évolue
avec un nouveau writer et un reader rétrocompatible de la version actuelle.

## 2. Contexte et problème

Le renderer actuel prouve efficacement de petites routes verticales bornées,
mais représente les fonctionnalités par familles spécialisées. Une combinaison
de géométrie, matériau, couverture, transform, clip, blend, layer et filtre doit
être admise successivement par plusieurs mappers, payloads, preflights, task
builders et materializers.

Cette architecture transforme la couverture Skia en produit cartésien. Ajouter
une capacité locale ne la rend pas automatiquement disponible avec les autres
axes. Certains composants WebGPU existent déjà, mais ne peuvent pas obtenir
l'autorité ou les ressources nécessaires dans une frame publique préparée.

L'inventaire de vérité actuellement enregistré contient 615 GMs : 439 sont
tentées, 327 rencontrent un refus terminal et 112 produisent un rendu. Même en
retirant par prudence les familles entières `TEXT` et `IMAGE`, il reste 340
tentatives, 279 refus terminaux et 61 rendus. Ces nombres sont un constat de
départ, pas le dénominateur final : W0 séparera précisément ce qui dépend d'un
codec des opérations d'image déjà décodée qui restent dans le périmètre.

## 3. Objectifs et critères de succès

### 3.1 Priorité

La fidélité Skia est prioritaire sur le temps réel, le windowing et la
concurrence. Les choix structurants ne doivent cependant pas empêcher ces
évolutions ultérieures.

### 3.2 Gate finale hors `font` et `codec`

La remédiation est terminée lorsque :

- 100 % des GMs éligibles sont exécutées ;
- au moins 95 % passent leur politique pixel sans fallback ;
- aucun refus terminal ne reste non classifié ;
- aucun fallback CPU silencieux n'est possible ;
- les écarts restants sont une liste fermée `accepted-skia-gap`, avec
  justification et owner ;
- aucune famille essentielle — path, clip, gradient, blend, layer ou filtre —
  n'est entièrement concentrée dans les 5 % restants ;
- les seuils globaux et les références ne sont pas affaiblis pour atteindre la
  gate.

### 3.3 Politique CPU

Un oracle CPU indépendant est utilisé pour valider les règles sémantiques
critiques. Un fallback CPU peut exister uniquement sous un mode explicite. Une
GM n'est déclarée compatible GPU que si toute sa frame est rendue par WebGPU.

La préparation CPU de géométrie — normalisation, expansion d'un stroke ou
tessellation — est autorisée : elle produit des ressources destinées au GPU et
ne constitue pas un fallback de rendu.

## 4. Architecture cible et dépendances

```text
:math:geometry ─────┐
                    ├──> :render-ir ──> :gpu-plan ──> :gpu-renderer
:math:matrix ───────┘          ▲                              ▲
                               │                              │ implementation
                               └──────────── :kanvas ─────────┘
```

La relation exacte de `:kanvas` vers `:gpu-renderer` est une dépendance
d'implémentation. Aucun type GPU ne fait partie de l'API publique de `:kanvas`.

### 4.1 `:math:geometry`

Ce module est l'unique propriétaire des valeurs géométriques
backend-agnostic :

- points, tailles, lignes, rectangles et rectangles arrondis ;
- paths immuables, contours et verbes ;
- fill rules, orientations et topologies ;
- bounds, canonicalisation et opérations géométriques robustes ;
- données neutres nécessaires à l'expansion des strokes.

Il reste Kotlin Multiplatform et ne dépend d'aucun module de rendu.

Le path est scindé en :

- `PathBuilder`, mutable et destiné à la construction ;
- `PathF32`, profondément immuable et destiné à l'enregistrement, la
  sérialisation, les caches et le renderer.

Le `Path` public actuel reste temporairement une façade de compatibilité. Un
appel à `drawPath` fige son contenu en `PathF32`.

### 4.2 `:math:matrix`

Ce module possède les extensions qui appliquent une matrice à une géométrie.
Cette règle respecte la dépendance existante de `:math:matrix` vers
`:math:geometry` et interdit le cycle inverse.

### 4.3 `:render-ir`

Ce nouveau module possède la sémantique de rendu backend-agnostic :

- `SceneSnapshot` ;
- commandes normalisées ;
- material graph ;
- clip stacks ordonnées ;
- blends, layers et effects ;
- identités et hashes canoniques ;
- sémantique colorimétrique et alpha ;
- résultats et diagnostics backend-agnostic ;
- port `RenderBackend` ;
- sérialisation versionnée de `Picture`.

Il référence les types géométriques de `:math` et les contrats de
`:color-management`. Il ne définit aucune primitive géométrique et n'importe
aucun type WebGPU, WGSL ou GPU.

### 4.4 `:gpu-plan`

Ce nouveau module compile une `SceneSnapshot` en `RenderGraph` sans créer de
handle natif. Il possède :

- le choix geometry/coverage ;
- la compilation du material graph ;
- le développement des layers et effets ;
- la planification des destination reads ;
- les ressources logiques et leurs lifetimes ;
- l'ordre des passes ;
- les budgets ;
- la validation des capacités du device ;
- les diagnostics de planning.

### 4.5 `:gpu-renderer`

Le module existant est progressivement réduit à :

- device, queue et surfaces WebGPU ;
- matérialisation des ressources logiques ;
- compilation et validation WGSL ;
- caches GPU ;
- encodage des passes ;
- soumission, completion et readback.

Il exécute un plan déjà validé et ne décide plus de la sémantique d'un draw.

### 4.6 `:kanvas`

Ce module reste la façade publique. Il enregistre la scène immuable, fournit
`Canvas`, `Surface` et `Picture`, puis délègue au port backend. Il n'exporte
plus `gpu-renderer` avec une dépendance `api`.

## 5. Scene IR immuable

### 5.1 Structure

Un draw est la composition d'axes indépendants :

```text
DrawNode
├── Geometry     RectF32 | RRectF32 | PathF32 | MeshGeometry
├── Material     MaterialNode
├── Coverage     demande sémantique, pas stratégie GPU
├── ClipStack    opérations ordonnées
├── Blend        mode et sémantique de destination
└── Effects      color, mask, image filters et layer
```

Les objets publics mutables sont copiés lors de l'enregistrement. Une
`SceneSnapshot` figée est la seule entrée d'un renderer.

### 5.2 Canonicalisation et sérialisation

- chaque nœud possède une identité canonique stable ;
- les collections sont profondément immuables ;
- les floats conservent une politique canonique explicite pour les hashes ;
- les discriminants sérialisés sont des identifiants stables, jamais les
  ordinals d'enums ;
- le nouveau writer `Picture` écrit explicitement le format 8 ;
- le reader accepte le nouveau format et la version actuelle ;
- la compatibilité d'écriture vers l'ancien format n'est pas requise.

## 6. Compilation geometry/coverage

La géométrie et la couverture sont deux responsabilités distinctes.

### 6.1 Normalisation

`GeometryNormalizer` consomme les types de `:math` et calcule :

- topologie et fill rule ;
- bounds locaux et device ;
- classe de transformation ;
- winding et inverse fill ;
- stroke développé ou paramètres nécessaires à son développement ;
- estimation du coût en vertices, indices, triangles et mémoire.

### 6.2 Stratégies

`CoveragePlanner` choisit selon la géométrie et les capacités du device :

- `AnalyticCoverage` pour les primitives adaptées ;
- `TessellatedCoverage` pour les fills et strokes généraux ;
- `StencilCover` pour les topologies complexes et inverse fills ;
- `CoverageMask` pour les clips AA, compositions booléennes et effets qui
  exigent une texture de couverture.

La stratégie est indépendante du matériau. Le même `CoveragePlan` peut être
consommé par un solid, un gradient, une image ou un runtime effect.

### 6.3 Clips

Une clip stack est compilée comme un sous-graphe ordonné :

- scissor pour un rectangle trivial ;
- couverture analytique pour une forme simple ;
- stencil ou mask pour un path ;
- composition `Intersect`/`Difference` dans l'ordre d'enregistrement ;
- autorité unique des ressources depth/stencil et mask pour chaque pass.

### 6.4 Budgets

Les budgets portent sur des ressources calculées : taille des buffers, nombre
de vertices/indices, mémoire temporaire, dimensions des masks et nombre de
passes. Ils ne sont pas des allowlists fonctionnelles par famille.

## 7. Material graph

### 7.1 Nœuds sémantiques

```text
MaterialNode
├── SolidColor
├── LinearGradient
├── RadialGradient
├── SweepGradient
├── ImageSample
├── RuntimeEffect
├── LocalMatrix(child)
├── ColorFilter(child)
├── Opacity(child)
└── Blend(source, destination)
```

Le graphe stocke les couleurs et stops immuables, tile modes, sampling,
espaces colorimétriques, alpha, matrices locales et relations parent/enfant.
Les conversions colorimétriques sont réalisées par `:color-management`.

### 7.2 Compilation

Le planner produit séparément :

- `MaterialProgramPlan` : programme, ordre d'évaluation, ABI et clé de
  pipeline ;
- `MaterialBindingPlan` : uniforms, buffers, textures et samplers.

La clé de pipeline dépend de la structure du programme. Les valeurs dynamiques
restent dans les ressources de données afin de limiter la cardinalité du
cache.

### 7.3 Blend et runtime effects

Un blend utilise le fixed-function lorsque possible, sinon il déclare un
destination read au `RenderGraph`.

L'IR représente un runtime effect par une signature, une ABI versionnée, des
uniforms, des child materials et une identité canonique. La première
implémentation accepte les effets enregistrés. Un frontend SkSL ultérieur peut
implémenter le port de compilation sans modifier l'IR.

## 8. Render graph, layers et effets

### 8.1 Ressources et passes

Le `RenderGraph` contient des ressources logiques sans handles et cinq types
de passes :

- `RenderPass` ;
- `TextureCopy` ;
- `FilterPass` ;
- `ResolvePass` ;
- `ReadbackPass`.

Chaque ressource déclare format, dimensions, usages et intervalle de vie. Le
backend peut réutiliser une allocation native entre des ressources dont les
lifetimes ne se chevauchent pas ; cette optimisation n'appartient pas au
contrat sémantique.

### 8.2 Layers

Un layer déclare :

- bounds et mode de calcul ;
- format et espace colorimétrique ;
- état initial transparent, préservé ou backdrop ;
- contenu ;
- filtre de sortie ;
- paint de restore.

Le planner développe le layer en cibles intermédiaires, passes, snapshots et
composition vers le parent.

### 8.3 Filtres initiaux

Le premier catalogue général couvre :

- blur séparable ;
- color matrix ;
- offset/transform ;
- crop ;
- composition de deux filtres.

Les filtres suivants ajoutent des passes ou programmes, sans ajouter une lane
spécifique par géométrie.

## 9. Runtime et modèle d'erreur

### 9.1 `RenderContext`

Un contexte possède device, queue, capacités, caches, pools, scheduler,
gestion du device loss et budget mémoire partagé. Une `Surface` possède sa
scène et son descripteur de cible, pas les ressources natives globales.

Le backend est asynchrone en interne :

```text
RenderGraph -> SubmissionHandle -> Completion
```

`Surface.render()` reste bloquant pour préserver son contrat, mais attend à la
frontière publique et jamais sous un lock global.

### 9.2 Résultats typés

Le planning distingue :

- `Ready` ;
- `GapNotMigrated` ;
- `GapOnPromotedScope` ;
- `InvalidScene` ;
- `ResourceLimitExceeded`.

L'exécution distingue :

- `Completed` ;
- `UnsupportedCapability` ;
- `InvalidPlan` ;
- `ResourceLimitExceeded` ;
- `DeviceFailure`.

Les diagnostics publics conservent des codes stables. Les erreurs internes ne
sont pas transformées en placeholders silencieux.

## 10. Migration progressive

### 10.1 Règle de frame atomique

Une frame est entièrement traitée par le nouveau moteur ou entièrement laissée
à l'ancien moteur. Aucun mélange intraframe n'est autorisé.

### 10.2 Ownership des capabilities

- `Ready` route la frame vers le nouveau moteur ;
- `GapNotMigrated` autorise temporairement l'ancien moteur ;
- `GapOnPromotedScope` est une régression et ne peut pas être masqué ;
- une capability promue appartient définitivement au nouveau moteur.

### 10.3 Étapes

1. shadow planning sans changement du rendu produit ;
2. double preuve ciblée hors production normale ;
3. promotion par capability compositionnelle ;
4. suppression des routes legacy remplacées ;
5. retrait total du moteur legacy après la gate de fidélité.

## 11. Conformance et politique de tests

### 11.1 Périmètre GM

Une GM est exclue uniquement si son résultat dépend directement d'une font ou
d'un décodage/encodage codec. Les images RGBA déjà disponibles, leur sampling,
leurs transforms, shaders, filtres et compositions restent éligibles.

Chaque GM reçoit exactement une classification :

- `eligible` ;
- `excluded-font` ;
- `excluded-codec` ;
- `accepted-skia-gap`.

### 11.2 Politique pixel

- exact lorsque la quantification est déterministe ;
- un LSB au maximum lorsque le format GPU le justifie ;
- seuil visuel spécifique uniquement lorsque l'exactitude pixel n'est pas
  raisonnable ;
- aucune baisse automatique de seuil ;
- aucune mise à jour automatique de référence ;
- modification de référence revue séparément du renderer.

### 11.3 Tests comportementaux uniquement

Les tests autorisés portent sur :

- opérations mathématiques et géométriques ;
- sérialisation publique et round-trip ;
- pixels CPU/GPU ;
- diagnostics publics ;
- budgets configurables ;
- absence observable de soumission lors d'un refus ;
- performance finale lorsqu'elle devient une gate.

Sont interdits :

- lecture ou parsing des fichiers source ;
- assertions sur packages, imports, noms de classes ou méthodes internes ;
- assertions qu'un planner ou materializer précis a été appelé ;
- snapshots textuels complets du WGSL ;
- assertions sur la forme interne exacte du `RenderGraph` ;
- compteurs techniques sans valeur fonctionnelle ;
- tests empêchant une refactorisation sémantiquement équivalente.

Les frontières d'architecture sont imposées par les modules Gradle, la
direction des dépendances, les interfaces minimales et la visibilité du
compilateur, pas par des tests d'inspection du code.

## 12. Vagues de remédiation

### W0 — Vérité de référence

- classifier précisément le périmètre GM ;
- réconcilier les scores orphelins ;
- figer références et politiques pixel ;
- établir les tableaux de progression par famille et diagnostic.

Gate : dénominateur reproductible et aucune exclusion générique `blocking`.

### W1 — Géométrie immuable dans `:math`

- ajouter `PathF32` et `PathBuilder` ;
- déplacer fill rules, topologie et opérations géométriques neutres ;
- placer les transformations dans `:math:matrix` ;
- maintenir la façade de compatibilité ;
- figer les géométries enregistrées ;
- introduire le nouveau format `Picture` et le reader rétrocompatible.

Gate : une mutation postérieure de la source ne modifie jamais une scène ou
une Picture enregistrée.

### W2 — Scene IR et frontières

- créer `:render-ir` ;
- normaliser les axes de rendu ;
- adapter les `DisplayOp` ;
- définir le port backend et les résultats typés ;
- corriger les dépendances de modules.

Gate : toutes les GMs éligibles peuvent être enregistrées dans l'IR.

### W3 — Premier chemin compositionnel

- créer `:gpu-plan` ;
- définir ressources, passes et budgets ;
- créer `RenderContext` ;
- brancher le routeur de migration ;
- rendre la première tranche solid/simple clip/SrcOver.

Gate : preuve CPU/GPU exacte sans régression du chemin legacy.

### W4 — Geometry/coverage

Ordre : primitives analytiques AA, path fills, strokes/hairlines, transforms,
clips simples, clips path/inverse/booléens, combinaison mask/stencil.

Gate : PATH et CLIP progressent indépendamment du matériau.

### W5 — Material graph

Ordre : solid/opacity, gradients, matrices/tile modes, images RGBA, color,
blends fixed-function, destination-read blends, runtime effects enregistrés.

Gate : couverture comportementale croisée material/geometry/blend et progrès
GM dans toutes les familles concernées.

### W6 — Layers et effets

- `saveLayer` général et initialisation depuis la destination ;
- snapshots de destination ;
- blur, color matrix, crop, transform et composition ;
- mask filters, backdrops et Pictures imbriquées.

Gate : les effets utilisent le graphe commun sans lane par primitive.

### W7 — Convergence GM

- regrouper les échecs par cause sémantique ;
- corriger les axes réutilisables ;
- traiter meshes/vertices et combinaisons rares ;
- fermer les écarts de colorimétrie et quantification ;
- documenter la liste finale des écarts acceptés.

Gate : 100 % exécutées, au moins 95 % conformes, zéro refus non classifié.

### W8 — Retrait legacy et runtime

- supprimer l'ancien renderer et ses adapters ;
- retirer les payloads et materializers obsolètes ;
- supprimer le lock global ;
- activer plusieurs surfaces par contexte ;
- mesurer mémoire, caches et latence.

Gate : résultats W7 inchangés après retrait complet du legacy.

## 13. Découpage de l'exécution

Cette spec décrit le programme architectural complet. Elle ne doit pas devenir
un plan d'implémentation monolithique.

Le premier plan couvre uniquement W0–W2 : vérité de référence, géométrie
immuable et Scene IR. W3 à W8 obtiennent chacun leur propre cycle spec, plan,
implémentation et validation après stabilisation du socle.

Les documents humains sont placés sous :

```text
refactor/
├── README.md
├── specs/
├── plans/
└── waves/
```

Les artefacts générés restent dans leurs répertoires techniques existants.

## 14. Risques et réponses

| Risque | Réponse de design |
| --- | --- |
| Divergence entre les deux moteurs | Routage atomique par frame et ownership irréversible après promotion |
| IR trop large avant usage | Ajouter uniquement les nœuds nécessaires aux vagues W0–W6 |
| Régression de l'API Path | Façade de compatibilité et snapshot au draw |
| Reconstruction involontaire d'un renderer CPU | Oracles petits et indépendants, aucun fallback silencieux |
| Nouveau couplage des tests à l'implémentation | Tests exclusivement comportementaux |
| Explosion du cache shader | Séparer structure du programme et valeurs dynamiques |
| Explosion mémoire des effets | Lifetimes logiques, budget de frame et refus avant allocation |
| Migration infinie | Gates par vague et suppression immédiate des routes remplacées |

## 15. Décisions actées

- fidélité avant performance ;
- API publique stable autant que possible ;
- contrats internes GPU refondables ;
- géométrie possédée par `:math` ;
- pas de fallback CPU silencieux ;
- support GPU revendiqué seulement après une frame WebGPU complète ;
- migration progressive, sans mélange intraframe ;
- objectif final de 95 % des GMs éligibles ;
- aucun test d'infrastructure du code ;
- documents de suivi humains sous `refactor/` ;
- premier plan d'implémentation limité à W0–W2.
