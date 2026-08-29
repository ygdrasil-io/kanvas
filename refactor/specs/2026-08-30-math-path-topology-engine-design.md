# Moteur topologique robuste pour les paths de `:math`

Date : 2026-08-30  
Statut : design validé en conversation, en attente de revue du document  
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
internalisés ; aucun nouveau `Point`, `Edge`, `Segment`, `Tolerance` ou
`Epsilon` générique n'est ajouté dans `:math`.

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
d'arêtes aplaties, intersections, sommets et demi-arêtes. Les valeurs par
défaut sont :

- profondeur de subdivision : 32 ;
- arêtes aplaties par opérande : 65 536 ;
- intersections : 262 144 ;
- sommets : 262 144 ;
- demi-arêtes : 1 048 576.

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

## 7. Arrangement planaire et classification

Après découpe aux intersections, chaque segment produit deux demi-arêtes.
Les sorties d'un sommet sont ordonnées angulairement avec les prédicats
robustes. Les cycles obtenus définissent les faces bornées et la face externe.

La classification n'échantillonne pas arbitrairement un point proche d'une
arête. Les winding des deux opérandes sont propagés depuis la face externe :
traverser une demi-arête applique sa contribution orientée à chacun des deux
paths. La fill rule transforme ensuite winding/parité en appartenance.

La table de vérité de `PathBooleanOp` sélectionne les faces du résultat. Une
arête appartient à la frontière de sortie si ses deux faces ont des états de
sélection différents. Les cycles sont simplifiés uniquement par suppression
des doublons et points colinéaires dont l'équivalence est démontrée.

La projection finale `F64` vers `F32` supprime les sommets adjacents devenus
identiques, puis recalcule aire et orientation des cycles. Un cycle dégénéré
est retiré. Si deux frontières topologiquement distinctes deviennent
indiscernables en `F32` et que leur fusion changerait l'appartenance au-delà de
la tolérance promise, l'opération échoue explicitement au lieu de retourner un
path incohérent.

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
