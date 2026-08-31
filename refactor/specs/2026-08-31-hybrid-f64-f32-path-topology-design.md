# Arrangement hybride F64/F32 pour les opérations de paths

Date : 2026-08-31  
Statut : validée par l'utilisateur le 2026-08-31  
Périmètre : `:math:geometry`, remplacement de la projection finale de Task 5  
Hors périmètre : font, codec, façades Kanvas, renderer GPU, changement d'API
publique et conservation des verbes courbes dans le résultat booléen

## 1. Décision

La route robuste de `PathOpsF32` adopte un arrangement topologique hybride
F64/F32 unique.

- Les paramètres, intersections, prédicats robustes et preuves de provenance
  restent en F64.
- Chaque événement topologique porte son représentant F32 avant le calcul des
  winding et l'extraction des faces.
- Les aliases et coïncidences créés par la quantification sont résolus dans le
  graphe, à partir de witnesses source exacts.
- La frontière sélectionnée est émise directement depuis une trace de
  demi-arêtes conservant sa provenance.
- La projection finale suivie d'une compaction de sommets disparaît.

Cette spec remplace les règles de « compaction de tangence » ajoutées aux
sections de projection de
`2026-08-30-math-path-topology-engine-design.md`. Les autres décisions de ce
document restent applicables, notamment la nomenclature numérique, les
prédicats adaptatifs, les limites, les opérations publiques et le placement de
toute la géométrie dans `:math`.

## 2. Motivation

Le pipeline au HEAD `badda063680c609630cab92a34c1411a5ebbd112` construit
d'abord une frontière F64, la convertit en listes de sommets F32, puis tente de
réparer les collisions d'arrondi. À ce stade, l'identité des demi-arêtes et la
provenance des spans ont déjà été perdues.

Les cinq rounds de correction de Task 5 ont démontré que cette architecture ne
permet pas une réparation locale sûre :

- une permission par paire de contours devient trop globale ;
- une marche de contacts devient transitive ;
- requalifier un overlap projeté en point ne modifie pas la géométrie émise ;
- supprimer un run remplace implicitement `W -> A -> B -> C` par la corde
  `W -> C` ;
- agréger plusieurs witnesses peut supprimer les witnesses eux-mêmes et une
  face entière ;
- les reconstructions mutables rendent le budget de travail incomplet.

Le problème n'est donc plus un prédicat manquant. La projection doit participer
à la topologie avant le winding et l'extraction.

## 3. Référence architecturale Skia

Skia PathOps suit un modèle hybride comparable :

- les entrées et les points du graphe sont des `SkPoint` F32 ;
- les solveurs d'intersection et paramètres `t` utilisent des doubles ;
- `SkOpPtT` conserve simultanément un paramètre double et un point F32 ;
- les spans non alignés, voisins et coïncidents sont réconciliés avant le
  calcul final des winding ;
- `SkPathWriter` émet ensuite directement les points F32 du graphe.

Skia n'effectue ni second arrangement F32 après écriture, ni snapping global
vers un « meilleur » voisin F32, ni revalidation topologique finale. Les
sources de référence sont :

- <https://skia.googlesource.com/skia/+/24a8072e40466cd656fa017d1753621be84093e1/src/pathops/SkOpSpan.h#85> ;
- <https://skia.googlesource.com/skia/+/24a8072e40466cd656fa017d1753621be84093e1/src/pathops/SkAddIntersections.cpp#526> ;
- <https://skia.googlesource.com/skia/+/24a8072e40466cd656fa017d1753621be84093e1/src/pathops/SkPathOpsCommon.cpp#238> ;
- <https://skia.googlesource.com/skia/+/24a8072e40466cd656fa017d1753621be84093e1/src/pathops/SkOpSegment.cpp#172>.

Kanvas reprend cette frontière de composants sans reproduire les heuristiques
tolérantes de Skia. Les prédicats exacts, le rejet conservateur des ambiguïtés,
les budgets déterministes et la parité JVM/JS restent plus stricts.

## 4. Objectifs et non-objectifs

### 4.1 Objectifs

- Rendre la quantification F32 visible au graphe avant winding.
- Conserver la provenance exacte depuis le flattening jusqu'au writer.
- Résoudre les tangences F32 représentables sans compaction ni corde.
- Distinguer un `OverlapF64` source d'une coïncidence créée par la
  quantification F32.
- Rendre les witnesses indépendants, atomiques et non transitifs.
- Préserver les acquis des Tasks 1 à 4 et les corrections Booth de Task 5.
- Maintenir une complexité bornée et entièrement débitée.
- Produire les mêmes décisions, sorties et frontières de budget sur JVM et JS.

### 4.2 Non-objectifs

- Inventer un point F32 voisin absent des évaluations des spans incidents.
- Garantir une réussite pour toute topologie F64 non représentable en F32.
- Construire un second DCEL après l'émission du `PathF32`.
- Reproduire les tolérances historiques ou l'ordre mémoire de Skia.
- Modifier `PathF32`, `PathOpsF32`, `PathAnalysisF32`, `PathMeasureF32` ou les
  signatures publiques.
- Réintroduire font ou codec dans le périmètre.

## 5. Nomenclature et placement

Tous les objets ci-dessous sont internes à `:math:geometry`.

Les valeurs numériques suivent la nomenclature du module :

- `F32` pour les représentants et coordonnées émises en `Float` ;
- `F64` pour les coordonnées, paramètres et prédicats en `Double` ;
- `I32` ou `I64` pour les IDs, compteurs et limites entières.

Les types mixtes indiquent explicitement les deux familles, par exemple
`PathArrangementF64F32`. Aucun nouveau `Point`, `Edge`, `Span`, `Tolerance` ou
`Epsilon` générique n'est introduit.

## 6. Modèle de provenance

### 6.1 Localisation et span source

`PathSourceLocationF64` contient :

- `sourceSegmentIndexI32` ;
- `parameterF64` ;
- `originalPointF32`, nullable ;
- l'identité exacte d'événement existante lorsqu'elle existe.

`PathSourceSpanF64` décrit un intervalle sans événement exact intérieur :

- `sourceSpanIdI64` ;
- l'opérande et le contour source ;
- le segment/verbe source ;
- `startLocationF64` et `endLocationF64` ;
- les subdivisions de flattening qui lui appartiennent ;
- les contributions de winding portées par ce span.

Un span peut contenir plusieurs subdivisions adaptatives. Elles ne créent pas
de nouvelle autorité topologique et restent rattachées au même intervalle
paramétrique.

### 6.2 Sommet hybride

`PathHybridVertexF64F32` contient :

- `sourcePointF64` ;
- `representativePointF32` ;
- `originalPointF32`, nullable ;
- `vertexIdentityF64` ;
- les `PathSourceSpanF64` incidents ;
- le witness ou groupe de witnesses exacts associé.

`sourcePointF64` sert aux prédicats et à la preuve. Le point F32 sert à
l'embedding projeté, à l'identité topologique finale et à l'émission.

### 6.3 Witnesses et aliases

`PathContactWitnessF64` est un type scellé :

- `PointF64` porte l'identité exacte du point et tous ses spans incidents ;
- `OverlapF64` porte les deux intervalles paramétriques exacts et leurs spans.

`PathAliasGroupF32` relie seulement les représentants appartenant au même
witness exact. Une égalité de coordonnées F32 ou une proximité F64 ne crée
jamais un alias par elle-même.

Un contact n-way exactement concurrent forme un seul groupe logique de
witness. Les relations par paires de ce groupe peuvent partager un span. Deux
witnesses exacts distincts peuvent border le même span source ou réclamer des
sous-intervalles disjoints de ce span. Ils ne peuvent jamais posséder la même
claim ni des sous-intervalles dont les intérieurs se chevauchent. Un endpoint
commun n'est partageable que s'il porte déjà la même
`vertexIdentityF64` exacte ; sinon le conflit est rejeté.

### 6.4 Coïncidence projetée

`PathProjectedCoincidenceF32` représente deux spans dont les images F32 sont
coïncidentes alors que leur relation source n'est qu'un point exact.

Elle contient :

- `projectedCoincidenceIdI64` ;
- `witnessIdentityF64` ;
- exactement deux spans pour une relation par paire ;
- les endpoints F32 de l'intervalle projeté ;
- les paramètres F64 bornant la portion de chaque span ;
- les contributions de winding de chaque opérande ;
- les claims de provenance nécessaires au commit atomique.

Cette relation reste distincte d'un `OverlapF64`. Elle peut influencer
l'agrégation des contributions du DCEL, mais ne transforme jamais le witness
source en overlap et ne supprime aucune arête source.

### 6.5 Incidence effondrée

`PathCollapsedIncidenceF64F32` conserve un `PathSourceSpanF64` dont les deux
extrémités ont le même représentant F32. Elle porte le sommet hybride, les
directions F64 entrante et sortante, les contributions de winding et la
provenance complète. Ce n'est ni une demi-arête, ni une permission de supprimer
localement le span. Son unique rôle est de rendre la perte de dimension visible
pendant la classification et de conduire ensuite à `Drop` ou `Reject` selon
la section 10.

### 6.6 Trace de frontière

`PathBoundaryTraceF64F32` conserve l'anneau ordonné des demi-arêtes
sélectionnées, leurs spans source, leurs représentants F32 et leur orientation.
Le writer consomme cette trace sans reconstruire de bridges depuis des listes
de points parallèles.

## 7. Choix du représentant F32

Le représentant d'un événement est choisi uniquement parmi les candidats
produits par ses spans incidents au paramètre F64 de l'événement.

Ordre de décision :

1. Si un unique `originalPointF32` existe, il est autoritaire et réémis
   bit-à-bit.
2. Si plusieurs points F32 d'origine existent, ils doivent avoir les mêmes
   bits topologiques après la seule canonicalisation du zéro ; sinon le conflit
   est rejeté. S'ils ne diffèrent que par le signe d'un zéro, l'ordre total
   sémantique F32 choisit une provenance originale unique, dont les bits bruts
   sont conservés par le writer.
3. Si tous les spans générés produisent le même candidat F32, ce candidat est
   utilisé.
4. Si les candidats diffèrent mais appartiennent au même witness exact, le
   candidat canonique est choisi par l'ordre total sémantique F32 existant,
   puis validé contre toutes les incidences.
5. Si l'orientation, l'incidence ou l'embedding projeté devient ambigu, la
   projection est rejetée.

Aucun voisin ULP, epsilon ou point synthétique absent de cet ensemble de
candidats ne peut être choisi.

## 8. Pipeline hybride

### 8.1 Flattening et intersections

`PathFlattenerF64` conserve `sourceSegmentIndexI32`, `parameterF64` et
`originalPointF32` sur chaque point aplati. Cette information traverse
`inputEdgesF64` et `PathIntersectionsF64` sans être réduite à une simple paire
de coordonnées.

Les intersections restent calculées avec les prédicats et witnesses exacts de
Task 3. Chaque split produit un `PathSourceSpanF64`; aucune identité n'est
reconstruite depuis une coordonnée quantifiée.

### 8.2 Registre de sommets hybrides

Avant la construction du DCEL, un registre crée les
`PathHybridVertexF64F32` et leurs candidats F32. Il fusionne seulement les
incidences partageant une identité de witness exacte.

Deux sommets F64 distincts ayant la même image F32 restent distincts jusqu'à la
phase de conflits projetés. Ils ne sont jamais fusionnés par une clé de
coordonnée seule.

### 8.3 Détection des conflits F32

Une vue projetée des spans alimente un broad phase déterministe. Les endpoints
F32 sont levés exactement en F64 pour réutiliser les prédicats robustes et
l'index spatial existant.

Chaque contact créé ou renforcé par la quantification est classé :

- `Point F32` soutenu par un `PointF64` ou un `OverlapF64` exact ;
- `Overlap F32` soutenu par un `OverlapF64` exact ;
- coïncidence F32 locale entre les deux spans directement incidents à un même
  `PointF64` ;
- conflit sans preuve locale unique.

Le dernier cas est rejeté. Un crossing F32 nouvellement créé sans witness F64
exact n'est pas synthétisé dans le graphe.

### 8.4 Claims et commit atomique

Une coïncidence F32 locale peut couvrir plusieurs subdivisions de flattening
seulement lorsqu'elles appartiennent au même `PathSourceSpanF64`. Elle s'arrête
au prochain événement exact et ne franchit jamais :

- un autre span source ;
- un autre witness ;
- un seam de contour ;
- une extrémité originale distincte ;
- une discontinuité de provenance.

Chaque proposition réclame ses spans et intervalles paramétriques. Toutes les
claims sont validées avant construction du DCEL :

- les claims du même witness n-way sont résolues comme un groupe atomique ;
- deux witnesses distincts peuvent réclamer des portions disjointes d'un même
  span, dans l'ordre de leurs paramètres F64 ;
- des claims distinctes dont les intérieurs paramétriques se chevauchent, ou
  dont l'endpoint commun ne porte pas la même identité exacte, rendent
  l'opération ambiguë ;
- l'ordre de parcours des witnesses ne modifie jamais le résultat.

Une ambiguïté rejette l'opération entière. Aucun résultat partiel n'est écrit.

### 8.5 Arrangement et winding

`PathArrangementF64` évolue vers `PathArrangementF64F32`.

Le DCEL utilise :

- les représentants F32 comme embedding final des sommets ;
- les directions et tangentes F64 source pour l'ordre angulaire ;
- les listes de provenance pour agréger les contributions coïncidentes ;
- les expansions exactes sur les points F32 levés en F64 pour les aires et
  ordres de faces projetés.

Une coïncidence autorisée agrège les winding comme une arête partagée. Elle ne
supprime ni span, ni sommet, ni face par une réécriture de chemin. Un span dont
les deux représentants F32 sont identiques devient une incidence effondrée
attachée au sommet hybride : sa provenance et sa contribution de winding
restent disponibles pour classer les secteurs, mais elle n'est jamais émise
comme arête. Si cette incidence appartient à une frontière sélectionnée, le
contour complet peut seulement devenir `Drop` selon la section 10 ; un contour
conservé qui en dépend devient `Reject`. Il n'existe donc aucune suppression
locale silencieuse de span.

Si deux directions restent indiscernables après quantification sans relation
de coïncidence validée, l'arrangement est rejeté.

### 8.6 Extraction et writer

Le winding, les opérations binaires, `simplify` et `asWinding` sélectionnent
les faces du même arrangement hybride.

L'extraction retourne des `PathBoundaryTraceF64F32`. Le writer émet leurs
représentants F32 directement dans `PathBuilder`.

La phase finale vérifie seulement :

- finitude ;
- fermeture ;
- orientation ;
- canonicalisation cyclique ;
- fill rule ;
- limites de sortie.

Elle ne recherche pas de nouveaux contacts, ne compacte pas de run et ne crée
pas de corde.

## 9. Relation Point/Overlap

La relation source et la relation projetée restent explicitement distinctes.

- Un `Point F32` peut être soutenu par un `PointF64` ou un `OverlapF64` exact.
- Un `Overlap F32` source conserve son statut d'overlap uniquement lorsqu'un
  `OverlapF64` exact couvre ses endpoints et les deux intervalles source.
- Un `PointF64` peut produire une `PathProjectedCoincidenceF32` locale dans le
  graphe hybride. Cette coïncidence n'est pas requalifiée en `OverlapF64` et ne
  devient jamais une permission par paire de contours.
- Un overlap F32 distant, transitif ou non incident reste un collapse de
  projection.

Cette formulation remplace la règle trop forte « tout overlap F32 exige un
overlap F64 » uniquement pour la représentation interne d'une coïncidence de
quantification locale. Elle ne permet pas d'émettre un raw overlap non prouvé.

## 10. Effondrement et aire

Le seuil normalisé existant reste :

- aire `2^-46` ;
- double-aire exacte `2^-45` ;
- égalité autorisée pour le retrait d'un contour entièrement effondré ;
- valeur strictement supérieure : `path-f32-projection-collapse`.

Le seuil ne permet jamais :

- de remplacer un run par une corde ;
- de compenser une perte locale par une autre zone ;
- d'agréger les pertes de plusieurs witnesses ;
- de retirer un sommet ou un span d'un contour conservé.

Une `PathProjectedCoincidenceF32` acceptée relève de la sémantique du graphe
F32 et de ses winding, pas d'une permission fondée sur une aire faible. Elle
reste bornée par la provenance et l'incidence exactes.

Un contour entièrement effondré retourne un résultat interne `Drop` si sa
double-aire source exacte est inférieure ou égale à `2^-45`. Un contour dont
au moins une arête de frontière reste représentable retourne `Keep` uniquement
si aucune incidence effondrée n'est nécessaire à sa fermeture, son orientation
ou son winding. Toute autre situation, toute ambiguïté ou perte significative
retourne `Reject`. Aucun `checkNotNull` ne convertit `Drop` en erreur générique.

## 11. Erreurs et atomicité

Les structures intermédiaires sont immuables ou construites en deux phases
proposition/commit. Les deux `PathF32` sources restent inchangés après réussite
ou échec.

Contrats publics :

- entrée non finie ou limite invalide : `IllegalArgumentException` ;
- conflit projeté ambigu ou significatif :
  `IllegalStateException("path-f32-projection-collapse")` ;
- budget candidat : `IllegalStateException("path-candidate-limit")` ;
- limites d'intersections et demi-arêtes : messages existants inchangés.

Aucune exception Kotlin interne, dépendante du backend ou issue d'une
assertion ne doit franchir l'API.

## 12. Limites, budget et complexité

Le même `PathCandidateWorkBudgetI32` traverse intersections, conflits F32,
claims, arrangement, canonicalisation et writer.

- Chaque comparaison, visite, copie, prédicat, terme d'aire, claim et
  comparaison de tri est débité avant action.
- Chaque groupe canonique de contact F32 qui exige une nouvelle identité de
  sommet ou une nouvelle coupe paramétrique consomme un slot
  `maxIntersections`. Un groupe n-way concurrent compte une fois. Les deux
  bornes nouvelles d'une coïncidence projetée comptent chacune une fois.
- Une borne ou un endpoint déjà identifié par la même
  `PathVertexIdentityF64` exacte reste un no-op pour `maxIntersections`, mais
  consomme le travail candidat documenté. Une simple égalité F32 ne bénéficie
  jamais de ce no-op.
- `maxHalfEdges` borne la structure finale avant allocation importante.
- Booth conserve son coût canonique `3n` et sa frontière invariante par
  rotation/réversion.
- Le registre de claims et la reconstruction sont linéaires ; aucun
  `removeAt` répété ni copie quadratique n'est autorisé.

Pour `S` spans et `K` conflits candidats, la route visée est
`O(S log S + K)` hors coût des prédicats adaptatifs. La mémoire est `O(S + K)`
et reste bornée par les limites existantes.

Le broad phase doit rester conservateur autour des AABB F32 : il ne peut
éliminer un candidat réel à cause d'un arrondi ou d'un ULP.

## 13. Déterminisme Kotlin Multiplatform

- Les clés F32 utilisent les bits bruts avec canonicalisation topologique du
  seul zéro signé.
- Un `originalPointF32` retenu comme provenance canonique est réémis avec ses
  bits bruts d'origine ; le choix entre plusieurs zéros signés topologiquement
  égaux suit l'ordre total défini en section 7.
- Les clés F64 utilisent les identités exactes et ordres sémantiques existants,
  jamais une string ou une quantification `Int`.
- Les IDs I32/I64 servent à la provenance et aux lookup, jamais au départage
  géométrique final.
- Aucun ordre de `HashMap`, adresse objet ou détail JVM/JS n'influence les
  décisions.
- Rotations, réversions et permutations de contours équivalentes donnent la
  même sortie et la même frontière de budget.
- Les additions et multiplications de coûts sont préflightées en `Long` avant
  conversion vers un budget I32.

## 14. Suppressions et migrations ciblées

La migration retire, après remplacement fonctionnel :

- `compactProjectedPointWitnessRunsF64` ;
- `projectionOnlyWitnessRunEndF64` ;
- les règles de non-parallélisme utilisées comme autorité de compaction ;
- les listes parallèles `sourceFirstVertices` / `sourceLastVertices` ;
- la reconstruction de bridges source depuis deux sommets projetés ;
- toute validation qui requalifie ou réécrit un contact après extraction.

Les corrections suivantes de Task 5 sont conservées :

- broad phase déterministe et AABB conservatrices ;
- ruling endpoint/no-op ;
- relation source exacte des overlaps ;
- pertes de cycles locales non compensables ;
- clés cycliques à rotation minimale ;
- coût Booth canonique `3n` ;
- seuil exact de double-aire.

La migration ne crée pas d'adaptateur public. Un adaptateur interne temporaire
peut maintenir `PathArrangementF64.boundary()` pendant la transition, mais il
doit être supprimé avant le verdict final.

## 15. Stratégie de tests

Tous les tests sont comportementaux, géométriques ou numériques dans
`commonTest`. Aucun test ne lit les sources, packages, imports, noms privés,
collections internes ou classes du graphe.

### 15.1 Régressions obligatoires

- Tangence exacte devenue coïncidence F32 pour les cinq opérations.
- Long overlap F32 distant sans witness local : rejet stable.
- Fixture single-witness de la re-review 5 : aucune perte d'appartenance ; le
  cas ambigu est rejeté.
- Fixture multi-witness de la re-review 5 : aucune face ni witness supprimé ;
  claims concurrents rejetés.
- Witness au seam puis même géométrie sous rotation/réversion.
- Vrai `OverlapF64` avec endpoints et winding conservés.
- Contact n-way et plusieurs événements sur un segment.
- Crossing F32 nouveau sans witness F64 : rejet.
- Points F32 originaux, grandes coordonnées et signed zero préservés.
- Contour effondré sous, à et au-dessus du seuil.
- Run long avec frontière de budget identique JVM/JS et inputs immuables.

### 15.2 Oracles

- appartenance sur grilles et probes discriminants ;
- bounds, orientation et fill rules ;
- identités algébriques des cinq opérations ;
- équivalence de `simplify` et `asWinding` ;
- métamorphisme sous translation, échelle, rotation cyclique et réversion ;
- type/message exact de l'erreur publique ;
- aucune assertion sur le nombre ou la classe des nœuds internes.

### 15.3 Matrice finale

1. suites ciblées Tasks 3 à 5 sur JVM et JS ;
2. matrice complète `:math:geometry:jvmTest` et
   `:math:geometry:jsNodeTest` ;
3. Task 6 métamorphique ;
4. compilation de `:integration-tests:skia` ;
5. régénération des renders et du dashboard sur les GMs rendables ;
6. review indépendante Sol sur un commit stable.

Les GMs explicitement exclus restent hors du dénominateur tant que leur
topologie F32 n'est pas représentable ou qu'ils relèvent de font/codec.

## 16. Critères d'acceptation

- Une seule autorité topologique hybride précède le winding et l'extraction.
- La provenance source atteint chaque demi-arête et chaque point émis.
- Aucun raw overlap F32 distant n'emprunte l'autorité d'un point source.
- Aucun witness ne consomme un span appartenant à un autre witness.
- Aucun run n'est remplacé par une corde.
- Les trois repros de la re-review 5 sont fermés sur JVM et JS.
- Les 15 tangences existantes et la matrice métamorphique passent.
- Les budgets sont globaux, débités avant travail et invariants par
  représentation équivalente.
- Les erreurs publiques sont stables et les inputs restent immuables.
- La nomenclature F32/F64/I32/I64 et le placement dans `:math` sont respectés.
- JVM, JS et le consommateur Skia compilent et testent avec succès.
- Un reviewer Sol indépendant rend `Spec: PASS` et `Quality: PASS` avant la
  reprise de la clôture Task 6.

## 17. Décisions closes

- Le design utilise un arrangement hybride unique, pas deux DCEL.
- La quantification F32 participe à la topologie avant winding.
- Aucun snapping vers un voisin ULP arbitraire n'est permis.
- Les valeurs F32 d'origine sont immuables.
- Une ambiguïté non résolue est rejetée conservativement.
- Le seuil d'aire ne justifie jamais une réécriture locale.
- Font et codec restent hors périmètre.

Aucun point `TBD` ou choix fonctionnel ouvert ne subsiste dans cette spec.
