# Primitives géométriques sémantiques et génération de code

Date : 2026-08-23  
Statut : conception approuvée

## 1. Contexte

Les primitives actuelles de `math` mélangent trois axes indépendants :

- la sémantique géométrique (`Point` ou `Vector`) ;
- la mutabilité ;
- la représentation numérique (`F32`, `F64` ou `I32`).

Les incohérences observées sont les suivantes :

- `Vector2F32` est immutable mais sert aussi à représenter des points dans les
  rectangles et les matrices ;
- `Point2F64` est mutable et expose à la fois des opérations de point et de
  vecteur ;
- `Vector2F64` est un alias de `Point2F64` ;
- `Vector2I32` est mutable et `Point2I32` en est un alias ;
- les matrices distinguent déjà partiellement les points et les vecteurs par
  le nom des méthodes, mais pas par le système de types.

Le projet est en incubation. La migration peut casser l'API source et binaire.
Aucune couche de compatibilité ni période de dépréciation n'est requise.

## 2. Objectifs

La conception doit :

1. faire vérifier par le compilateur la différence entre position et
   déplacement ;
2. fournir des variantes immutables par défaut et des variantes mutables
   publiques pour les chemins sensibles aux allocations ;
3. générer les familles répétitives depuis un manifeste typé ;
4. versionner les sources générées afin que le build normal ne lance pas le
   générateur ;
5. permettre une adoption ultérieure des multi-field value classes sans
   redessiner l'API ;
6. conserver les propriétés numériques importantes, notamment la saturation
   de l'arithmétique `I32` ;
7. migrer matrices, rectangles, lignes et `pathops` vers des signatures
   sémantiques explicites ;
8. valider la sémantique avec des oracles indépendants du générateur.

## 3. Hors périmètre

Le premier refactor ne génère pas :

- `Size`, `Direction`, `Normal` ou d'autres sémantiques ;
- des buffers compacts comme `Point2F32Buffer` ;
- toutes les combinaisons dimension × scalaire possibles ;
- une hiérarchie générique `Point<T>` ou `Vector<T>` ;
- une garantie de zéro allocation ;
- une conversion implicite entre point et vecteur ;
- une API de compatibilité avec les anciens aliases, factories ou méthodes
  `map*`.

Les comparaisons approximatives propres à `pathops` restent des algorithmes
écrits à la main. Elles ne font pas partie du noyau généré.

## 4. Architecture des modules

Le découpage reste unidirectionnel :

- `:math:vector` possède les types `Vector*` ;
- `:math:geometry` possède les types `Point*` et dépend de `:math:vector` ;
- `:math:matrix` dépend de `:math:geometry` et de `:math:vector` ;
- `:math:geometry-codegen` est un outil JVM qui contient le manifeste et le
  générateur.

`geometry-codegen` n'est pas publié comme dépendance runtime. Il écrit dans :

```text
math/vector/src/generated/kotlin
math/geometry/src/generated/kotlin
```

Ces répertoires sont versionnés et ajoutés à `commonMain`. Le build JVM/JS
normal compile ces fichiers comme des sources ordinaires et ne dépend pas de la
tâche de génération.

## 5. Modèle déclaratif

Le manifeste Kotlin décrit séparément les scalaires et les primitives.

```kotlin
scalar(
    id = F32,
    kotlinType = "Float",
    supportsNormalization = true,
    supportsFiniteCheck = true,
)

scalar(
    id = I32,
    kotlinType = "Int",
    arithmetic = SATURATING,
    accumulatorType = "Long",
)

primitive(
    semantic = POINT,
    dimension = 2,
    scalar = F32,
    immutableRepresentation = MULTI_FIELD_VALUE,
    generateMutable = true,
)
```

Une primitive est définie par :

```text
semantic:   POINT | VECTOR
dimension:  2 | 3 | 4
scalar:     F32 | F64 | I32
mutable:    activé indépendamment par primitive
```

Le générateur est capable de représenter davantage de combinaisons que le
manifeste initial, mais il ne produit que les combinaisons explicitement
sélectionnées.

### 5.1 Inventaire initial

| Type | Immutable | Mutable | Usage initial |
|---|---:|---:|---|
| `Point2F32` | oui | oui | géométrie 2D générale |
| `Vector2F32` | oui | oui | déplacements et algèbre 2D |
| `Point3F32` | oui | non | matrices affines et caméra |
| `Vector3F32` | oui | oui | axes, directions et normales |
| `Vector4F32` | oui | non | matrices et coordonnées homogènes |
| `Point2F64` | oui | oui | positions précises de `pathops` |
| `Vector2F64` | oui | oui | différences et directions de `pathops` |
| `Point2I32` | oui | non | coordonnées entières |
| `Vector2I32` | oui | non | offsets entiers saturants |

`Point4F32` n'est pas généré. Dans l'API actuelle, `(x, y, z, w)` représente
une valeur homogène algébrique et reste donc un `Vector4F32`.

### 5.2 Validation du manifeste

La validation précède toute écriture et refuse au minimum :

- un `Point<D, S>` sans le `Vector<D, S>` correspondant ;
- une variante mutable sans sa variante immutable ;
- une capacité incompatible avec le scalaire, par exemple `NORMALIZE` sur
  `I32` ;
- deux types générés portant le même nom ;
- deux sorties portant le même chemin ;
- une dimension sans noms de composants définis ;
- une stratégie de représentation indisponible sans fallback déclaré.

Les diagnostics nomment la primitive, la règle violée et l'action attendue.

## 6. Sémantique des opérations

Le générateur applique une table fermée. Une opération absente de cette table
n'est pas émise.

| Expression | Résultat |
|---|---|
| `point + vector` | `Point` |
| `point - vector` | `Point` |
| `pointB - pointA` | `Vector` |
| `vector + vector` | `Vector` |
| `vector - vector` | `Vector` |
| `-vector` | `Vector` |
| `vector * scalar` | `Vector` |
| `scalar * vector` | `Vector` |
| `vector / scalar` | `Vector`, si le scalaire le permet |
| `vector.dot(vector)` | scalaire ou accumulateur élargi |
| `vector.cross(vector)` | scalaire en 2D, vecteur en 3D |
| `vector.normalized()` | `Vector`, pour `F32` et `F64` |
| `point.distanceTo(point)` | scalaire flottant, pour `F32` et `F64` |
| `point.midpointTo(point)` | `Point`, pour `F32` et `F64` |

Les opérations suivantes n'existent pas :

```text
Point + Point
-Point
Point * scalar
Point.dot(...)
Point.cross(...)
Point.normalized()
```

Le produit composante par composante de deux vecteurs n'utilise pas
`operator *`. S'il est requis par un consommateur, il est exposé sous un nom
explicite tel que `hadamardProduct` dans une extension écrite à la main ou dans
une capacité ultérieure du manifeste.

### 6.1 Constantes et construction

Les constructeurs directs sont l'API normale :

```kotlin
Point2F32(10f, 20f)
Vector2F32(4f, -2f)
```

Les points exposent `Origin`. Les vecteurs exposent `Zero` et, lorsque cela est
pertinent, les vecteurs unitaires d'axe. Les anciennes factories `of()` ne sont
pas conservées.

### 6.2 Mutabilité

Les types immutables et mutables sont des types concrets distincts sans
interface publique commune.

Les immutables ont une égalité structurelle. Les mutables sont des classes
ordinaires avec champs `var`, identité propre et égalité référentielle. Ils ne
doivent pas servir de clés stables dans les collections.

Une variante mutable expose `hasSameComponentsAs` pour les comparaisons de
contenu explicites. Elle ne redéfinit pas `equals` ou `hashCode`.

Exemples d'opérations mutables :

```kotlin
mutablePoint.translateBy(vector)
mutableVector.add(vector)
mutableVector.subtract(vector)
mutableVector.scaleBy(scalar)
mutableVector.normalizeInPlace()
```

Les conversions sont explicites et copient les composants :

```kotlin
point.toMutable()
mutablePoint.toImmutable()
point.toPoint2F64()
```

La conversion ne partage aucun tableau ni stockage mutable.

Une conversion point/vecteur n'est pas générale. Si un algorithme choisit une
origine, son API doit la nommer, par exemple `point.vectorFromOrigin()`.

### 6.3 Politiques numériques

`F32` et `F64` fournissent la longueur, la normalisation et `isFinite`.
La politique existante de `math:scalar` pour les vecteurs proches de zéro
reste l'autorité numérique. L'égalité structurelle reste exacte ; elle ne
devient pas une égalité approximative implicite.

`I32` utilise une arithmétique saturante pour l'addition, la soustraction, la
négation et la multiplication scalaire. `dot` et `cross` utilisent des
produits `Long` et une accumulation saturante en `Long`, afin que même les cas
extrêmes ne rebouclent pas silencieusement. La normalisation entière n'est pas
générée ; le consommateur convertit d'abord vers `F32` ou `F64`.

## 7. Représentation immutable

La représentation cible est une multi-field value class :

```kotlin
value class Point2F32(
    val x: Float,
    val y: Float,
)
```

Cette représentation exprime l'absence d'identité, l'immutabilité superficielle
et l'égalité structurelle. Elle ne garantit pas, à elle seule, l'absence
d'allocation.

Le projet utilise actuellement Kotlin 2.4.0. Le manifeste déclare néanmoins :

```kotlin
immutableRepresentation = MULTI_FIELD_VALUE
```

Tant que le compilateur sélectionné ne prend pas en charge cette construction,
le backend de compatibilité du générateur émet une classe finale avec champs
`val` et implémentations structurelles générées de `equals`, `hashCode` et
`toString`.

Le fallback n'est pas une `data class` : il ne doit pas exposer `copy()` ni
`componentN()`, car ces méthodes ne font pas partie de la surface cible des
multi-field value classes. Le passage au backend natif MFVC est piloté par une
seule stratégie du générateur et exige :

1. un compilateur Kotlin supportant MFVC pour les cibles du projet ;
2. l'acceptation explicite du niveau expérimental requis ;
3. le passage des tests JVM et JS ;
4. des benchmarks avant toute affirmation de gain de performance.

Les variantes mutables ne deviennent jamais des value classes.

Limitation temporaire : une classe fallback possède techniquement une identité
JVM/JS et Kotlin autorise donc `===`. Le dépôt ne doit pas observer cette
identité, appeler `identityHashCode`, synchroniser sur ces valeurs ou les placer
dans des APIs fondées sur l'identité. Une vérification statique dédiée refuse
ces usages pour les types immutables générés. Cette règle rend le passage au
backend MFVC source-compatible pour les consommateurs internes.

## 8. Transformations et opérateurs

`map` est remplacé par `transform`. Les formes nommées sont l'API documentée,
et `operator *` fournit l'écriture mathématique concise.

```kotlin
fun transform(point: Point2F32): Point2F32
fun transform(vector: Vector2F32): Vector2F32

operator fun times(point: Point2F32): Point2F32 = transform(point)
operator fun times(vector: Vector2F32): Vector2F32 = transform(vector)
```

Exemple :

```kotlin
val devicePoint = matrix * localPoint
val deviceVector = matrix * localVector
```

Les destinations mutables et opérations groupées gardent un nom explicite :

```kotlin
matrix.transformInto(sourcePoint, destinationPoint)
matrix.transformPoints(sourcePoints, destinationPoints)
matrix.transformVectors(sourceVectors, destinationVectors)
```

Les noms distincts pour les opérations groupées évitent aussi les collisions
JVM causées par l'effacement générique de `Array<Point>` et `Array<Vector>`.

### 8.1 Perspective

Une matrice projective n'induit pas un unique vecteur 2D indépendant de sa
position. Pour `Matrix3x3F32` et les matrices 4 × 4 projectives :

- `transform(point)` est toujours défini et effectue la division homogène ;
- `transform(vector)` et `matrix * vector` exigent une matrice affine et
  lèvent `IllegalArgumentException` si cette précondition est violée ;
- une différence finie sous perspective utilise :

```kotlin
fun transformDisplacementAt(
    anchor: Point2F32,
    displacement: Vector2F32,
): Vector2F32
```

avec la définition indépendante de l'origine globale :

```text
transformDisplacementAt(p, v) = transform(p + v) - transform(p)
```

La séparation éventuelle entre `AffineTransform*` et `ProjectiveTransform*`
est différée. Elle n'est pas nécessaire au premier refactor.

Une normale reste représentée par `Vector3F32` dans ce périmètre, mais sa
transformation inverse-transposée n'est pas assimilée à `transform(vector)`.
Toute API correspondante conserve un nom explicite comme `transformNormal` et
reste écrite à la main.

### 8.2 Signatures principales

`Matrix3x3F32` expose les transformations 2D typées décrites ci-dessus.

`Matrix3x4F32`, toujours affine, expose :

```kotlin
fun transform(point: Point3F32): Point3F32
fun transform(vector: Vector3F32): Vector3F32
```

`Matrix4x4F32` expose :

```kotlin
fun transform(point: Point3F32): Point3F32
fun transform(vector: Vector3F32): Vector3F32
fun transformHomogeneous(value: Vector4F32): Vector4F32
```

La caméra devient :

```kotlin
fun lookAt(
    eye: Point3F32,
    center: Point3F32,
    up: Vector3F32,
): Matrix4x4F32
```

Les rectangles et lignes deviennent notamment :

```kotlin
rect.center(): Point2F32
rect.topLeft(): Point2F32
rect.offsetBy(delta: Vector2F32): RectF32
RectF32.bounds(points: Array<Point2F32>): RectF32?

line.start: Point2F64
line.end: Point2F64
line.direction(): Vector2F64
```

## 9. Pipeline de génération

Le flux est :

```text
Manifest
   ↓
SchemaValidator
   ↓
SemanticModel
   ↓
KotlinEmitter
   ↓
staging temporaire
   ↓
src/generated/kotlin
```

Le générateur utilise KotlinPoet pour construire les déclarations, imports et
KDoc sans concaténation textuelle fragile.

Chaque fichier commence par :

```kotlin
// Generated by :math:geometry-codegen.
// Edit MathPrimitiveManifest.kt and run generateMathPrimitives.
```

La génération produit d'abord un arbre complet dans un répertoire temporaire.
Après validation, la commande explicite synchronise seulement les deux
répertoires générés dont elle est propriétaire. Elle ne modifie jamais les
sources écrites à la main.

Deux tâches publiques sont fournies :

```text
generateMathPrimitives
verifyMathPrimitivesGenerated
```

`generateMathPrimitives` synchronise les sorties, y compris la suppression des
anciens fichiers générés devenus obsolètes.

`verifyMathPrimitivesGenerated` génère dans un répertoire temporaire et vérifie
sans modifier le workspace :

- la liste exacte des fichiers ;
- leur contenu exact ;
- l'absence de sortie obsolète ;
- le déterminisme d'une seconde génération.

Aucun timestamp, chemin absolu ou ordre dépendant d'une hash map n'apparaît
dans les sorties.

## 10. Stratégie de test

### 10.1 Principe d'indépendance

Les sources peuvent être générées, mais les oracles sémantiques sont écrits à
la main. Le même modèle ou emitter ne doit jamais produire à la fois une
opération et son résultat attendu.

Le générateur teste uniquement :

- la validation du schéma ;
- le déterminisme ;
- la liste et la forme des sorties ;
- la compilation des sources produites.

### 10.2 Tests de compilation

Des fixtures manuelles vérifient les expressions autorisées :

```kotlin
val p3: Point2F32 = p1 + vector
val delta: Vector2F32 = p2 - p1
```

Des fixtures négatives manuelles doivent échouer à la compilation :

```kotlin
point1 + point2
point * 2f
point.normalized()
matrix.transform(pointAsVector)
```

### 10.3 Oracles numériques

Les résultats attendus utilisent des formules scalaires indépendantes :

- les transformations calculent directement `x'`, `y'`, `z'` et `w'` à
  partir des coefficients ;
- elles n'appellent ni `transform`, ni une autre transformation de la classe
  testée pour construire l'attendu ;
- la saturation `I32` utilise des calculs élargis et des bornes littérales ;
- les tests mutables comparent les composants primitifs et vérifient
  l'absence d'aliasing ;
- les identités algébriques complètent les exemples mais ne constituent jamais
  l'unique oracle.

Les tests communs s'exécutent sur JVM et JS. Ils couvrent les opérateurs, les
types retournés, l'égalité, les conversions, les valeurs non finies, les
vecteurs proches de zéro et les limites entières.

### 10.4 Performance

Les benchmarks sont séparés des tests fonctionnels. Ils mesurent au minimum :

- la représentation fallback ;
- les multi-field value classes lorsqu'elles sont disponibles ;
- les transformations unitaires et groupées ;
- les allocations JVM observées ;
- les résultats JVM et JS pertinents.

Ils documentent les résultats mais ne deviennent pas une condition arbitraire
de correction.

## 11. Migration

La migration est cassante mais chaque étape doit laisser le build dans un état
cohérent.

1. Ajouter `geometry-codegen`, le manifeste, les validations, les tâches et les
   répertoires source générés. Remplacer dans le même changement les classes
   `Vector*` manuscrites afin d'éviter les déclarations dupliquées.
2. Introduire `Point2F32` et classifier les positions, sommets, offsets,
   directions et tangentes de `RectF32` et `RectI32`.
3. Remplacer l'actuel `Point2F64` partagé par `Point2F64`,
   `MutablePoint2F64`, `Vector2F64` et `MutableVector2F64`, puis classifier les
   variables de `pathops` selon leur rôle réel.
4. Migrer les matrices vers `transform`, `operator *`, les destinations
   mutables et les règles de perspective.
5. Migrer tous les consommateurs du dépôt avec l'aide des erreurs de type.
6. Supprimer les aliases point/vecteur, `of()`, `mapXY`, `mapPoints` et toute
   conversion silencieuse restante.
7. Exécuter les tests JVM/JS, les fixtures négatives, la vérification des
   sources générées et les benchmarks de référence.

## 12. Critères d'acceptation

Le refactor est terminé lorsque :

- aucun alias public ne confond `Point` et `Vector` ;
- les signatures publiques utilisent le type sémantique correct ;
- les opérations interdites échouent dans les fixtures de compilation ;
- les opérations autorisées retournent les types attendus ;
- points et vecteurs réagissent différemment à la translation ;
- les déplacements sous perspective exigent un point d'ancrage ;
- les conversions mutable/immutable ne partagent aucun état ;
- l'arithmétique `I32` sature aux limites documentées ;
- les tests JVM et JS passent avec des oracles indépendants ;
- `verifyMathPrimitivesGenerated` passe sur un checkout propre ;
- le build normal ne lance pas le générateur ;
- les anciens fichiers, aliases et méthodes ambiguës ont disparu ;
- aucune affirmation de performance n'est faite sans benchmark correspondant.

## 13. Références Kotlin

- [KEEP-0454 — Multi-Field Value Classes](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0454-better-immutability-value-classes-MFVC.md)
- [Kotlin — Inline value classes](https://kotlinlang.org/docs/inline-classes.html)
