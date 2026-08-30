# Moteur topologique robuste pour les paths de `:math`

Date : 2026-08-30  
Statut : validé par l'utilisateur le 2026-08-30  
Périmètre : `:math:geometry`, Task 5 du plan W0–W2  
Hors périmètre : façades Kanvas, renderer GPU, font, codec et conservation des
verbes courbes dans le résultat d'une opération booléenne

## 1. Décision

`PathOpsF32`, `PathAnalysisF32` et `PathMeasureF32` s'appuient sur un noyau
topologique Kotlin Multiplatform interne à `:math:geometry`.

Le noyau :

- normalise les coordonnées `F32` dans un domaine local `F64` ;
- aplatit les courbes avec une erreur bornée relativement à l'étendue du path ;
- utilise des prédicats d'orientation adaptatifs et exacts en cas d'ambiguïté ;
- construit un arrangement planaire à demi-arêtes ;
- propage les winding (nombres d'enroulement) entre faces ;
- extrait des contours canoniques pour les cinq opérations booléennes ;
- sert également à `simplify` et `asWinding`.

Aucune dépendance de clipping externe n'est ajoutée. La responsabilité reste
entièrement dans `:math`.

## 2. Raisons

L'implémentation initiale de Task 5 quantifie les coordonnées dans des `Int`,
emploie des tolérances absolues et aplatit toujours une courbe avec le même
nombre de segments. Cette combinaison n'est pas invariante par translation ou
changement d'échelle et échoue déjà au-delà d'environ 2147 unités.

Une correction locale des epsilons ne suffit pas. Les cas requis —
intersections colinéaires, tangences, trous, contours superposés et
auto-intersections — exigent une autorité topologique commune. Réduire
`PathOpsF32` aux rectangles ou aux polygones convexes compromettrait l'objectif
de compatibilité Skia presque ISO.

## 3. Nomenclature numérique de `:math`

Tout type qui porte des valeurs numériques indique leur famille et leur
largeur :

- `F32` pour les coordonnées publiques en `Float` ;
- `F64` pour les valeurs de calcul internes en `Double` ;
- `I32` ou `I64` pour les limites ou clés entières lorsqu'elles existent.

La frontière publique conserve :

- `PathF32` ;
- `PathOpsF32` ;
- `PathAnalysisF32` ;
- `PathMeasureF32` ;
- `PathLocationF32` ;
- `PathTopologyI32` ;
- `RegionF32`.

Le noyau introduit des composants internes nommés explicitement :

- `PathNormalizationF64` et `NormalizedPathF64` ;
- `PathFlatteningPolicyF64` et `PathFlattenerF64` ;
- `OrientationPredicateF64` et `ExpansionF64` ;
- `PathIntersectionF64` ;
- `PathVertexF64`, `PathEdgeF64` et `PathHalfEdgeF64` ;
- `PathArrangementF64` ;
- `PathOpsLimitsI32`.

Les enums sans valeur de coordonnée, comme `PathBooleanOp`,
`RegionBooleanOp` et `ContourOrientation`, ne reçoivent pas de suffixe.
Les noms numériques ambigus existants introduits par Task 5 sont renommés ou
internalisés : `PathTopology` devient `PathTopologyI32` et les records privés
de mesure portent `F64`. Aucun nouveau `Point`, `Edge`, `Segment`, `Tolerance`
ou `Epsilon` générique n'est ajouté dans `:math`.

## 4. Frontière publique `F32`

### 4.1 Opérations booléennes

`PathOpsF32.op(first, second, operation)` conserve ses cinq opérations :

- `DIFFERENCE` ;
- `INTERSECT` ;
- `UNION` ;
- `XOR` ;
- `REVERSE_DIFFERENCE`.

Le résultat utilise `FillRule.WINDING`. Les contours externes sont horaires
dans le repère écran du module et les trous sont antihoraires. Les résultats
booléens sont linéarisés : conserver les verbes courbes n'est pas requis.

Le fast path rectangle est conservé uniquement lorsque
`PathAnalysisF32.rect` reconnaît un contour canonique strict. Son résultat
doit être géométriquement équivalent à celui du moteur général.

### 4.2 Simplification et conversion winding

`simplify` utilise l'arrangement unaire pour retirer les recouvrements,
résoudre les auto-intersections et produire des contours canoniques.
Il conserve le caractère inverse ou non inverse de la fill rule source.

`asWinding` utilise le même arrangement. Il ne remplace jamais seulement la
fill rule : il reconstruit des contours non chevauchants dont l'appartenance
`WINDING` est identique à celle du path source, y compris pour `EVEN_ODD`, les
trous et les contours dupliqués. `EVEN_ODD` devient `WINDING` et
`INVERSE_EVEN_ODD` devient `INVERSE_WINDING` ; le caractère inverse n'est
jamais perdu.

### 4.3 Entrées refusées

Les opérations booléennes finies refusent par `IllegalArgumentException` :

- une coordonnée ou un rayon non fini ;
- `INVERSE_WINDING` ou `INVERSE_EVEN_ODD`, dont le domaine rempli est non
  borné ;
- une configuration de limites invalide.

Une convergence impossible ou le dépassement d'une limite de complexité
produit une `IllegalStateException` déterministe. Aucun résultat partiel n'est
retourné.

## 5. Normalisation et politique d'erreur

### 5.1 Domaine normalisé

Les bounds combinées des opérandes déterminent :

- une origine égale au centre calculé en `F64` ;
- une échelle égale à `max(width, height)` calculée en `F64` ;
- une échelle de `1.0` lorsque l'étendue est dégénérée.

Un ensemble d'opérandes vides utilise l'origine `(0.0, 0.0)` et l'échelle
`1.0`.

Chaque coordonnée est convertie par `(value - origin) / scale`. La translation
et l'échelle de la scène ne changent donc pas la décision topologique, sous
réserve de la précision déjà perdue dans les entrées `F32`.

### 5.2 Flattening adaptatif

La tolérance normalisée par défaut vaut `2^-23`, soit l'ordre d'une ULP `F32`
relativement à l'étendue du path.

- une quad ou cubic est subdivisée jusqu'à ce que sa flatness soit inférieure
  à cette tolérance ;
- un arc est subdivisé selon l'erreur de flèche dans le domaine normalisé ;
- la profondeur maximale est 32 ;
- les évaluations, longueurs et flatness utilisent `F64` et une forme stable de
  `hypot` ;
- les endpoints originaux sont conservés exactement lors du retour en `F32`.

Atteindre la profondeur maximale sans respecter la tolérance est une erreur de
convergence, pas une autorisation de retourner une approximation non bornée.

### 5.3 Limites

`PathOpsLimitsI32` centralise des limites testables : profondeur, nombre
d'arêtes aplaties, intersections, sommets, demi-arêtes et travail de recherche
de candidats. Les valeurs par défaut sont :

- profondeur de subdivision : 32 ;
- arêtes aplaties par opérande : 65 536 ;
- intersections : 262 144 ;
- sommets : 262 144 ;
- demi-arêtes : 1 048 576 ;
- probes de candidats : 16 777 216 (`2^24`).

`maxCandidateProbes` est un budget global de CPU (travail), distinct des
limites de résultat et de mémoire. `2^24` vaut 64 fois la limite par défaut
d'intersections et 16 fois celle des demi-arêtes : il laisse de la marge aux
événements numériques denses sans autoriser une recherche non bornée. Chaque
pop brut d'un index de candidats, doublons inclus, ainsi que chaque inspection,
comparaison, copie ou mise à jour d'incidence de profil/index consomme une
unité avant l'action, y compris pour une incidence non commune. La
réconciliation exacte des paramètres, les copies d'accumulateur et les
retraits/réinsertions d'index sont soumis au même compteur. À épuisement, le
moteur échoue de façon déterministe par
`IllegalStateException("path-candidate-limit")`.

Le broad phase des arêtes est un arbre AABB équilibré sur les intervalles de
l'ordre sémantique canonique. Une requête visite ses sous-arbres de gauche à
droite et émet ainsi les seules paires `i, j` conservées avec `j > i` dans le
même ordre que le scan imbriqué de référence, sans liste de paires ni tri par
`i`. Une AABB ne peut être rejetée que si son écart d'axe reste séparé même
selon la politique ULP qui snap les paramètres d'endpoint du noyau; les
contacts inclusifs, un overflow intermédiaire de span et toute incertitude
restent donc candidats. La construction persistante est linéaire; chaque
visite de nœud, comparaison de bounds, émission de candidat et classification
de paire est débitée avant action du même budget global. La pré-classification
d'une paire est elle aussi débitée avant de construire ses relations endpoint,
de les filtrer ou d'appeler un prédicat d'orientation; le noyau normal et les
incidences d'un no-op conservent leurs débits distincts.

Une surcharge interne permet aux tests d'exercer chaque limite sans exposer
l'organisation du moteur. Une modification ultérieure de ces valeurs exige
une preuve comportementale et mémoire ; elle ne change pas le résultat d'une
opération qui reste sous les deux ensembles de limites.

## 6. Prédicats et intersections

`OrientationPredicateF64` calcule d'abord le déterminant en `F64` avec une
borne d'erreur issue des magnitudes des opérandes. Lorsque le signe est
incertain, `ExpansionF64` évalue le prédicat avec une expansion arithmétique
exacte.

Les intersections couvrent :

- croisements propres ;
- endpoints communs ;
- tangences ;
- segments colinéaires disjoints, contigus ou superposés ;
- intersections multiples au même sommet.

Les endpoints existants conservent une identité stable. Une intersection
calculée est canonisée par les identités de ses arêtes incidentes et leurs
paramètres ordonnés. Le clustering reste local à une même intersection
topologiquement démontrée ; aucune grille décimale globale ou conversion en
`Int` n'est utilisée.

Pour chaque événement de paire, le noyau construit d'abord un profil entrant
éphémère de ses deux incidences et de son témoin homogène exact éventuel. Il
énumère tous les membres des signatures ULP directes compatibles des deux
arêtes, ainsi que tous les membres du témoin exact : une signature directe a
un voisinage fixe de `31 × 16`, mais son AVL interne est multi-valeur. Les
993 flux au plus (`2 × 496 + 1`) sont parcourus dans l'ordre d'une clé
sémantique d'arête/événement indépendante des IDs source; les zéros signés
sont normalisés pour toute décision géométrique et tout point `F64` émis. Le
flux exact, lorsqu'il existe, est d'abord parcouru dans son ordre sémantique,
puis les 992 flux directs sont fusionnés par un heap de taille fixe. Un
marqueur par composante et par événement déduplique l'action, mais jamais le
coût : chaque pop brut, même dupliqué, reste compté.

Le comparateur de heap/AVL emploie un rang d'événement de naissance immuable,
de taille constante : rang d'arête pré-calculé, paramètres ordonnés, point
canonique et witness éventuel. Il ne parcourt jamais le profil mutable d'une
composante. Les arêtes à clé égale reçoivent le même rang et forment un lot
automorphe; tous ses membres restent évalués avant le commit atomique. Les IDs
restent donc de la provenance de sortie, jamais un tie-break topologique; ils
ne départagent que le stockage de membres sémantiquement égaux.

Chaque candidat est d'abord comparé au profil entrant entier. Les témoins
exacts égaux ont priorité et se ferment transitivement. Avant de copier un
accumulateur, une concurrence exacte répétée parcourt le domaine candidat
complet et ne devient un no-op que si son unique candidat éligible possède déjà
le même witness/point canoniques et les deux incidences entrantes. Cette garde
ne masque donc ni une incidence nouvelle, ni un élargissement, ni un autre
candidat direct; ses pops et contrôles sont budgétés. Sinon l'accumulateur
éphémère commence au profil entrant; un candidat direct n'est accepté que si
toutes ses incidences communes conservent un diamètre strictement inférieur à
16 ULP dans cet accumulateur. Le lot accepté est ensuite écrit en une seule
mutation persistante. Cette fermeture atomique choisit volontairement une
identité unique pour un pont ULP compatible sur ses deux incidences, sans
autoriser la chaîne transitive `0/15/30`, car son intervalle accumulé aurait
alors un diamètre de 30 ULP.

L'index est une accélération de candidats, non une preuve de fusion : chaque
union est justifiée soit par un témoin exact égal, soit par les incidences
communes compatibles. Les witnesses exacts ont eux aussi un AVL multi-valeur;
en cas de conflit de paramètres sous un même witness, un endpoint exact a
priorité, sinon le paramètre et la clé canonique minimaux retiennent une seule
coupe. Le lookup d'un événement coûte `O(log C + b_j)`, où `b_j` compte les
membres bruts visités : le facteur de 993 flux et les comparaisons de heap sont
constants relativement à `C` et au degré. Le filtrage, la fermeture et le
commit ont leur coût explicite `q_j` d'incidences inspectées/copiées/mises à
jour; chaque unité est débitée avant lecture ou écriture. L'état persistant
reste `O(E + I + C)`. La mémoire temporaire est le heap fixe, le profil entrant
et l'accumulateur de fermeture, plus `k` composants acceptés; l'accumulateur
contient au plus `O(min(I, R_j))` incidences, où `R_j` est le budget restant au
début de sa construction, car chaque copie ou insertion consomme une unité.
Les `k` composants acceptés sont eux-mêmes amortis par les suppressions
destructives sauf le winner.

L'égalité générée de `Point2F64` reste bit-à-bit. Le noyau ne la modifie pas :
il canonicalise seulement les coordonnées `-0.0` en `+0.0` dans la géométrie
topologique et les points émis. Les identités endpoint et leur
`originalPointF32` conservent au contraire leur provenance exacte, y compris
le zéro signé d'un endpoint non modifié.

Deux segments finis non dégénérés qui partagent exactement une seule identité
endpoint concrète et dont les porteurs sont prouvés non colinéaires n'ajoutent
pas une nouvelle composante d'intersection : l'identité existe déjà et ne
consomme donc pas `maxIntersections`. Avant même d'examiner leurs relations ou
leurs orientations, la pré-classification de la paire est débitée; ce no-op
consomme ensuite les deux unités minimales de travail correspondant à ses
incidences entrantes. Ces débits restent distincts de l'émission broad phase
et du noyau normal, afin qu'un budget candidat trop petit conserve l'erreur
`path-candidate-limit`. Les cas colinéaires, incertains, réversés ou ne
partageant que des coordonnées suivent le noyau normal.

## 7. Arrangement planaire et classification

Après découpe aux intersections, chaque segment produit deux demi-arêtes.
Les sorties d'un sommet sont ordonnées angulairement avec les prédicats
robustes. Les cycles obtenus définissent les faces bornées et la face externe.

Les composantes connexes disjointes sont ensuite raccordées dans une forêt de
confinement. Une composante externe hérite des winding de la face qui la
contient ; les composantes racines héritent de winding nuls. Le témoin de
confinement est dérivé d'un sommet extrémal et d'un secteur incident certifié,
sans déplacement epsilon arbitraire. Cette étape rend la propagation correcte
pour les trous et îlots dont les frontières ne partagent aucun sommet.

La classification n'échantillonne pas arbitrairement un point proche d'une
arête. Les winding des deux opérandes sont propagés depuis la face externe :
traverser une demi-arête applique sa contribution orientée à chacun des deux
paths. La fill rule transforme ensuite winding/parité en appartenance.

La table de vérité de `PathBooleanOp` sélectionne les faces du résultat. Une
arête appartient à la frontière de sortie si ses deux faces ont des états de
sélection différents. Les cycles sont simplifiés uniquement par suppression
des doublons et points colinéaires dont l'équivalence est démontrée.

La projection finale `F64` vers `F32` arrondit explicitement sur la même
grille IEEE-754 sur JVM et JS, supprime les sommets adjacents devenus
identiques, puis recalcule aire et orientation des cycles. Un cycle dégénéré
est retiré seulement si son aire normalisée absolue est au plus `tolerance² =
2^-46`; l'implémentation compare donc exactement la double-aire à `2^-45`.

La validation porte sur l'ensemble des frontières projetées, jamais sur un
cycle isolé. Elle associe chaque arête projetée au pont d'arête `F64` réel qui
lui correspond, puis utilise le même broad phase déterministe et budgété pour
chercher tout contact non-adjacent. Le pont source est classifié par le noyau
robuste avant son image `F32`. L'ordre partiel des relations est strict :
`Point F32 <- Point F64 | Overlap F64`, tandis que
`Overlap F32 <- Overlap F64` seulement. La classe ne suffit pas : pour un
witness `Overlap`, chaque endpoint du contact `F32` et les deux ponts source
localement mappés doivent appartenir à l'intervalle source exact et à son image
sur la lattice `F32`.

Un witness source `Point` ne conserve donc jamais un `Overlap F32`. Une
tangence aplatie qui arrondit temporairement des fragments colinéaires est
normalisée *comme relation de validation* en son unique `Point F32`, mais
seulement avec un certificat direct : les deux ponts source du candidat et les
deux ponts incidents au witness poursuivent des branches opposées et robustement
non parallèles autour du point exact, et le contact arrondi reste sur sa
coordonnée normale `F32`. Des rails source parallèles, même de part et d'autre
du witness, sont un long overlap nouveau et échouent. L'intervalle brut ne
survit pas comme relation `Overlap`. Il n'existe aucun
cache par paire de contours, aucune BFS et aucune fermeture transitive : un
contact source à gauche ne peut jamais autoriser un endpoint ou overlap projeté
distant à droite. Toute préimage ou relation locale ambiguë échoue
conservativement par `path-f32-projection-collapse`; chaque comparaison ou
prédicat de ce certificat débite d'abord le budget candidat.
Une jonction, un croisement ou un overlap partiel nouveau entre cycles source-
disjoints échoue si les cycles ne sont pas exactement le même cycle projeté;
un contact nouveau non-adjacent au sein d'un cycle dérive d'abord ses préimages
source exactes. Pour un Point, les deux arcs source fermés par la corde de
contact sont évalués; pour un Overlap, les deux endpoints et les bords de la
bande locale le sont. Chaque double-aire absolue est additionnée sans signe;
une préimage ambiguë échoue conservativement. Une variation d'aire distante
de signe opposé ne peut donc pas autoriser la perte du pont local.

Les cycles projetés sont groupés avec une clé structurelle `F32`, indépendante
de la rotation et de l'orientation, après normalisation des zéros signés et
suppression exacte des sommets colinéaires. Toute rotation est départagée par
la séquence cyclique complète (rotation minimale linéaire de Booth, dans les
deux sens), y compris lorsqu'un minimum est répété; la multiplicité et
l'orientation de chaque membre restent séparées de cette clé. Booth débite le
budget immédiatement avant chaque comparaison réelle de points (jamais une
réservation fixe `2n`), ce qui conserve son coût `O(n)` et rend la frontière de
budget identique sur JVM et JS; ses index cycliques et incréments sont calculés
en arithmétique élargie avant le retour à `Int`. Pour chaque
groupe, des expansions calculent
à la fois la modification signée agrégée des doubles-aires source/projetées et
l'étendue cumulée des frontières source; l'une ou l'autre supérieure à `2^-45`
fait échouer par `IllegalStateException("path-f32-projection-collapse")`. Ce
calcul global détecte notamment trois cycles `+,-,+` dont chaque paire serait
sous le seuil, ainsi qu'un outer/hole devenu un même cycle. Un groupe entier
dont la modification exacte reste au plus au seuil peut être retiré sans
retourner un path incohérent.

Ce même flux unaire fournit `simplify` et `asWinding`.

## 8. Analyses et mesure

### 8.1 Bounds

- lignes, quads et cubics utilisent leurs extrema analytiques ;
- les arcs partagent une conversion endpoint-vers-centre `F64` ;
- les angles annulant `dx/dθ` ou `dy/dθ` sont inclus seulement s'ils
  appartiennent au sweep ;
- `Close` remet toujours le point courant au début du contour.

Ainsi, les bounds ne dépendent jamais du flattening.

### 8.2 Contains et topologie

Pour les requêtes de fill, un contour ouvert est fermé implicitement. Cette
fermeture ne change ni `closedContourCount`, ni la sémantique de mesure.

Un test de point-sur-frontière précède le ray casting robuste. Conformément au
contrat existant, un point sur la frontière est hors du path, y compris pour
les fill rules inverses.

### 8.3 Détecteurs

- `rect` exige quatre coins distincts, quatre côtés axis-aligned, un tour
  cohérent et une aire non nulle ;
- `oval` exige le motif complet endpoints/contrôles produit par `addOval` ;
- `rrect` exige endpoints, côtés, rayons, rotation, flags d'arc, orientation et
  fermeture du motif produit par `addRRect` ;
- les comparaisons emploient une politique ULP/relative compatible avec les
  valeurs `F32`, pas une tolérance absolue indépendante de l'échelle.

### 8.4 Mesure

`PathMeasureF32` réutilise le flattening adaptatif et accumule les longueurs en
`F64`. La position est interpolée sur le segment aplati correspondant ; la
tangente provient de la dérivée du verbe source au paramètre associé, puis est
normalisée en `F64` avant conversion en `F32`.

Les distances sont clampées. Lorsque `start > stop`, `segment` les échange
pour conserver le comportement historique.

## 9. Tests comportementaux

Les tests communs JVM et JS couvrent :

- les cinq opérations booléennes ;
- idempotence, commutativité lorsqu'elle s'applique, dualité des différences
  et invariance de l'appartenance après `asWinding` ;
- contours externes, trous, contours dupliqués, fill rules, tangences,
  colinéarité et auto-intersections ;
- tests métamorphiques par translation et changements d'échelle, de la
  micro-géométrie aux coordonnées 4K et proches des limites finies `F32` ;
- bounds d'arcs tournés, large/small arc, les deux sweeps, correction de rayons
  et commandes après `Close` ;
- points de frontière, fermeture implicite et inverse fill pour `contains` ;
- détecteurs positifs canoniques et contre-exemples proches ;
- longueurs, positions, tangentes, contours multiples, contours fermés et
  intervalles inversés de `PathMeasureF32` ;
- immutabilité défensive de `RegionF32` ;
- dépassement contrôlé de chaque limite.

Les résultats sont comparés par appartenance, bounds, mesures et invariants
algébriques. Aucun test ne lit les sources, packages, imports, noms des types
internes ou la forme exacte de l'arrangement.

## 10. Découpage d'implémentation

L'amendement reste dans Task 5 mais doit être livré en sous-étapes TDD :

1. corriger bounds, `Close`, contains, détecteurs et mesure ;
2. introduire normalisation, politique de flattening et limites ;
3. introduire les prédicats adaptatifs et les intersections ;
4. construire l'arrangement et les winding de faces ;
5. brancher les cinq opérations, `simplify` et `asWinding` ;
6. exécuter la matrice JVM/JS complète.

Les façades Kanvas restent réservées à Task 7. Aucun type du noyau `F64` ne
devient une seconde API publique de path.

## 11. Critères de sortie

Task 5 est validée lorsque :

- les huit findings de la review initiale sont fermés ;
- les opérations générales sont invariantes dans la marge `F32` documentée
  sous translation et changement d'échelle ;
- `asWinding` conserve l'appartenance des paths `EVEN_ODD` complexes ;
- aucune quantification globale `Int` ni tolérance absolue ad hoc ne subsiste ;
- les tests JVM et JS sont verts ;
- une review indépendante confirme spec et qualité.
