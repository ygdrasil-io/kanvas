# Semantic Geometry Primitives Codegen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer les primitives géométriques ambiguës par des types `Point*` et `Vector*` immutables/mutables, générés et versionnés, puis rendre leur sémantique obligatoire dans les rectangles, lignes et matrices.

**Architecture:** Un module JVM non publié, `:math:geometry-codegen`, transforme un manifeste Kotlin typé en sources déterministes sous `math/vector/src/generated/kotlin` et `math/geometry/src/generated/kotlin`. Le build JVM/JS normal compile ces sources sans lancer le générateur. Les opérations géométriques sont une table fermée, les migrations sont cassantes, et les tests numériques utilisent des oracles scalaires écrits à la main.

**Tech Stack:** Kotlin 2.4.0, Kotlin Multiplatform JVM/JS, Gradle Kotlin DSL, KotlinPoet 2.3.0, `kotlin.test`, Kotlin compiler embeddable 2.4.0 pour les fixtures de compilation, kotlinx-benchmark 0.4.17.

**Spec:** `docs/superpowers/specs/2026-08-23-semantic-geometry-primitives-codegen-design.md`

## Global Constraints

- Aucune compatibility layer (couche de compatibilité), dépréciation, `typealias`, factory `of()`, méthode `map*`, `copy()` ou `componentN()` n'est conservée.
- Les sources générées sont committées. Seules les tâches explicites `generateMathPrimitives` et `verifyMathPrimitivesGenerated` lancent le générateur ; aucun `check`, `build`, `compileKotlin*` ou test math normal ne dépend d'elles.
- Le backend immutable courant est `FINAL_CLASS`, fallback déclaré de la cible `MULTI_FIELD_VALUE`. Le passage futur au backend MFVC change une stratégie unique du générateur, pas les signatures publiques.
- L'égalité des immutables flottants utilise `toBits()` composant par composant, puis les `hashCode()` primitifs. Les mutables ne redéfinissent ni `equals` ni `hashCode`.
- `normalized()` retourne `Zero` si la longueur est proche de zéro ou non finie. `normalizeInPlace()` retourne `false` et ne modifie pas le mutable dans ces deux cas.
- `I32` sature pour `+`, `-`, négation et multiplication scalaire. `dot` et `cross` retournent `Long` avec produits élargis et accumulation/soustraction saturante.
- Les transformations groupées prennent `source` avant `destination`; les destinations sont des tableaux de types mutables pour ne pas allouer un objet par résultat.
- Les tests du générateur couvrent le schéma, les fichiers et le déterminisme. Ils ne servent jamais d'oracle pour l'arithmétique générée.
- Les tests fonctionnels JVM/JS calculent les attendus depuis des scalaires littéraux ou des formules locales, jamais via une autre opération de la classe testée.
- Lancer toutes les commandes de ce plan avec le préfixe `rtk` requis par le dépôt.

### Surface publique finale

```kotlin
// Points
point + vector                 // Point
point - vector                 // Point
pointB - pointA                // Vector
point.distanceTo(other)        // F32/F64
point.midpointTo(other)        // F32/F64

// Vectors
vector + vector
vector - vector
-vector
vector * scalar
scalar * vector
vector / scalar               // F32/F64
vector.dot(other)
vector.cross(other)
vector.normalized()            // F32/F64

// Matrices
matrix.transform(point)
matrix.transform(vector)       // seulement si affine pour 3x3/4x4
matrix * point
matrix * vector
matrix.transformInto(source, destination)
matrix.transformPoints(source, destination, count)
matrix.transformVectors(source, destination, count)
matrix.transformDisplacementAt(anchor, displacement)
```

### Inventaire final du manifeste

| Immutable | Mutable |
|---|---|
| `Point2F32` | `MutablePoint2F32` |
| `Vector2F32` | `MutableVector2F32` |
| `Point3F32` | — |
| `Vector3F32` | `MutableVector3F32` |
| `Vector4F32` | — |
| `Point2F64` | `MutablePoint2F64` |
| `Vector2F64` | `MutableVector2F64` |
| `Point2I32` | — |
| `Vector2I32` | — |

---

## Task 1: Scaffold the typed schema and validate the vector manifest

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `math/geometry-codegen/build.gradle.kts`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/PrimitiveSchema.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/MathPrimitiveManifest.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/SchemaValidator.kt`
- Test: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/SchemaValidatorTest.kt`

**Interfaces:**

```kotlin
internal enum class Semantic { POINT, VECTOR }
internal enum class ScalarId { F32, F64, I32 }
internal enum class ArithmeticPolicy { IEEE_754, SATURATING }
internal enum class Capability { DIVIDE, NORMALIZE, FINITE_CHECK }
internal enum class ImmutableRepresentation { MULTI_FIELD_VALUE, FINAL_CLASS }

internal data class ScalarSpec(
    val id: ScalarId,
    val kotlinType: String,
    val arithmetic: ArithmeticPolicy,
    val accumulatorType: String? = null,
)

internal data class PrimitiveSpec(
    val semantic: Semantic,
    val dimension: Int,
    val scalar: ScalarId,
    val capabilities: Set<Capability>,
    val targetRepresentation: ImmutableRepresentation = ImmutableRepresentation.MULTI_FIELD_VALUE,
    val fallbackRepresentation: ImmutableRepresentation? = ImmutableRepresentation.FINAL_CLASS,
    val generateImmutable: Boolean = true,
    val generateMutable: Boolean = false,
)

internal data class PrimitiveSchema(
    val scalars: List<ScalarSpec>,
    val primitives: List<PrimitiveSpec>,
)

internal class SchemaValidationException(message: String) : IllegalArgumentException(message)
internal object SchemaValidator { fun validate(schema: PrimitiveSchema) }
```

- [ ] **Step 1: Write failing validation tests**

Écrire des cas manuels pour chaque règle, dont les assertions précises suivantes :

```kotlin
@Test
fun `point requires matching vector`() {
    val schema = schemaOf(primitive(Semantic.POINT, 2, ScalarId.F32))
    val error = assertFailsWith<SchemaValidationException> {
        SchemaValidator.validate(schema)
    }
    assertEquals(
        "Point2F32 requires Vector2F32; add the matching VECTOR primitive",
        error.message,
    )
}

@Test
fun `I32 rejects normalization`() {
    val schema = schemaOf(
        primitive(
            Semantic.VECTOR,
            2,
            ScalarId.I32,
            capabilities = setOf(Capability.NORMALIZE),
        ),
    )
    val error = assertFailsWith<SchemaValidationException> {
        SchemaValidator.validate(schema)
    }
    assertEquals(
        "Vector2I32 cannot use NORMALIZE with scalar I32; remove NORMALIZE",
        error.message,
    )
}
```

Ajouter des cas distincts pour dimension hors `2..4`, composant non défini, scalaire dupliqué, nom de type dupliqué, chemin de sortie dupliqué, mutable sans immutable et stratégie indisponible sans fallback.

- [ ] **Step 2: Run the focused test and observe RED**

Run: `rtk ./gradlew :math:geometry-codegen:test --tests '*SchemaValidatorTest'`

Expected: échec de compilation car `PrimitiveSchema`, `SchemaValidator` et le module n'existent pas encore.

- [ ] **Step 3: Add the JVM tool module and dependencies**

Ajouter `include(":math:geometry-codegen")`, puis le catalogue :

```toml
[versions]
kotlinPoet = "2.3.0"

[libraries]
kotlinPoet = { module = "com.squareup:kotlinpoet", version.ref = "kotlinPoet" }
```

Configurer le module :

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(libs.kotlinPoet)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("org.graphiks.math.codegen.MainKt")
}
```

- [ ] **Step 4: Implement the model and validator minimally**

Valider tout le schéma avant de retourner. Produire des diagnostics stables en triant les primitives par `(semantic, dimension, scalar)`. Définir les composants par dimension avec une table fermée :

```kotlin
internal fun componentNames(dimension: Int): List<String> = when (dimension) {
    2 -> listOf("x", "y")
    3 -> listOf("x", "y", "z")
    4 -> listOf("x", "y", "z", "w")
    else -> throw SchemaValidationException(
        "dimension $dimension has no component names; use a dimension in 2..4",
    )
}
```

Le manifeste de cette étape sélectionne uniquement les vecteurs finaux : `Vector2F32` mutable, `Vector3F32` mutable, `Vector4F32`, `Vector2F64` mutable et `Vector2I32`. Les points sont ajoutés dans les Tasks 3–5 afin d'éviter les doubles déclarations durant la migration.

- [ ] **Step 5: Run tests and observe GREEN**

Run: `rtk ./gradlew :math:geometry-codegen:test --tests '*SchemaValidatorTest'`

Expected: `BUILD SUCCESSFUL` et tous les diagnostics exacts passent.

- [ ] **Step 6: Commit**

```bash
rtk git add settings.gradle.kts gradle/libs.versions.toml math/geometry-codegen
rtk git commit -m "build: scaffold semantic geometry codegen"
```

---

## Task 2: Emit deterministic vector sources and replace handwritten vectors

**Files:**

- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/SemanticModel.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/KotlinEmitter.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/GeneratedTree.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/GeneratedSourceSynchronizer.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/Main.kt`
- Test: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/KotlinEmitterTest.kt`
- Test: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/GeneratedSourceSynchronizerTest.kt`
- Modify: `build.gradle.kts`
- Modify: `math/vector/build.gradle.kts`
- Create: `math/scalar/src/commonMain/kotlin/org/graphiks/math/scalar/SaturatingArithmetic.kt`
- Test: `math/scalar/src/commonTest/kotlin/org/graphiks/math/scalar/SaturatingArithmeticTest.kt`
- Generate: `math/vector/src/generated/kotlin/org/graphiks/math/vector/*.kt`
- Delete: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector2F32.kt`
- Delete: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector3F32.kt`
- Delete: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/Vector4F32.kt`
- Delete: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/MutableVector2F32.kt`
- Delete: `math/vector/src/commonMain/kotlin/org/graphiks/math/vector/MutableVector3F32.kt`
- Modify: all current `math/**.kt` call sites of `Vector[234]F32.of`, immutable `.normalize()`, or vector component-wise `operator *`
- Modify tests: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/*.kt`

**Interfaces:**

```kotlin
internal data class GeneratedFile(val relativePath: String, val utf8: ByteArray)
internal data class GeneratedTree(val files: List<GeneratedFile>)
internal object KotlinEmitter { fun emit(schema: PrimitiveSchema): GeneratedTree }
internal object GeneratedSourceSynchronizer {
    fun generate(repoRoot: Path, tree: GeneratedTree)
    fun verify(repoRoot: Path, tree: GeneratedTree): List<String>
}
```

Generated vector signatures:

```kotlin
public class Vector2F32(public val x: Float, public val y: Float) {
    public operator fun plus(other: Vector2F32): Vector2F32
    public operator fun minus(other: Vector2F32): Vector2F32
    public operator fun unaryMinus(): Vector2F32
    public operator fun times(scalar: Float): Vector2F32
    public operator fun div(scalar: Float): Vector2F32
    public fun dot(other: Vector2F32): Float
    public fun cross(other: Vector2F32): Float
    public fun lengthSquared(): Float
    public fun length(): Float
    public fun normalized(): Vector2F32
    public fun isFinite(): Boolean
    public fun isZero(): Boolean
    public fun toMutable(): MutableVector2F32
    public companion object { val Zero; val UnitX; val UnitY }
}

public class MutableVector2F32(public var x: Float, public var y: Float) {
    public fun add(other: Vector2F32)
    public fun subtract(other: Vector2F32)
    public fun scaleBy(scalar: Float)
    public fun normalizeInPlace(): Boolean
    public fun hasSameComponentsAs(other: MutableVector2F32): Boolean
    public fun hasSameComponentsAs(other: Vector2F32): Boolean
    public fun toImmutable(): Vector2F32
}
```

Les dimensions et scalaires adaptent ces signatures : `cross` retourne `Vector3F32` en 3D, n'est pas émis en 4D, et `Vector2I32.dot/cross` retournent `Long` sans division ni normalisation.

- [ ] **Step 1: Write RED tests for deterministic output mechanics**

Le golden attendu est écrit manuellement et ne valide que la forme : header, package, constructeur direct, absence de `data`, `copy`, `componentN`, `of`, timestamp et chemin absolu.

```kotlin
@Test
fun `same schema emits byte-identical sorted files`() {
    val first = KotlinEmitter.emit(MathPrimitiveManifest.schema)
    val second = KotlinEmitter.emit(MathPrimitiveManifest.schema)
    assertEquals(first.files.map { it.relativePath }.sorted(), first.files.map { it.relativePath })
    assertContentEquals(
        first.files.flatMap { it.utf8.asIterable() },
        second.files.flatMap { it.utf8.asIterable() },
    )
}

@Test
fun `fallback surface excludes data class conveniences`() {
    val source = emitted("Vector2F32.kt")
    assertContains(source, "public class Vector2F32(")
    assertFalse("data class" in source)
    assertFalse("fun copy(" in source)
    assertFalse("component1" in source)
    assertFalse("fun of(" in source)
}
```

Tester aussi : LF unique, newline finale, doublon de path refusé, tentative de sortie hors des deux racines refusée, fichier manuscrit dans une racine générée non supprimé silencieusement, fichier généré obsolète supprimé, et `verify` ne modifie aucun byte.

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:geometry-codegen:test --tests '*KotlinEmitterTest' --tests '*GeneratedSourceSynchronizerTest'`

Expected: échec de compilation sur les nouvelles interfaces.

- [ ] **Step 3: Implement scalar saturation with independent boundary tests**

API commune :

```kotlin
public fun saturatingAddI32(a: Int, b: Int): Int = clampToI32(a.toLong() + b.toLong())
public fun saturatingSubtractI32(a: Int, b: Int): Int = clampToI32(a.toLong() - b.toLong())
public fun saturatingNegateI32(value: Int): Int = if (value == Int.MIN_VALUE) Int.MAX_VALUE else -value
public fun saturatingMultiplyI32(a: Int, b: Int): Int = clampToI32(a.toLong() * b.toLong())
public fun saturatingAddI64(a: Long, b: Long): Long
public fun saturatingSubtractI64(a: Long, b: Long): Long
```

Pour `Long`, détecter l'overflow par bornes avant l'opération, jamais en comparant avec le résultat déjà rebouclé. Les tests attendent des bornes littérales, par exemple `assertEquals(2_147_483_647, saturatingAddI32(2_147_483_647, 1))` et `assertEquals(-9_223_372_036_854_775_808L, saturatingSubtractI64(Long.MIN_VALUE, 1L))`.

- [ ] **Step 4: Implement semantic expansion and KotlinPoet emission**

Produire un `GeneratedTree` entièrement en mémoire. Chaque fichier commence exactement par :

```kotlin
// Generated by :math:geometry-codegen.
// Edit MathPrimitiveManifest.kt and run generateMathPrimitives.
```

Pour le fallback flottant, émettre sans test d'identité :

```kotlin
override fun equals(other: Any?): Boolean =
    other is Vector2F32 && x.toBits() == other.x.toBits() && y.toBits() == other.y.toBits()

override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()
override fun toString(): String = "Vector2F32(x=$x, y=$y)"
```

La longueur doit éviter l'overflow intermédiaire en mettant les composants à l'échelle par leur valeur absolue maximale. Elle retourne `NaN` si un composant est `NaN`, `+Infinity` si aucun n'est `NaN` mais qu'un est infini, et zéro si tous sont nuls.

Pour `Vector2I32`, émettre :

```kotlin
public fun dot(other: Vector2I32): Long = saturatingAddI64(
    x.toLong() * other.x.toLong(),
    y.toLong() * other.y.toLong(),
)

public fun cross(other: Vector2I32): Long = saturatingSubtractI64(
    x.toLong() * other.y.toLong(),
    y.toLong() * other.x.toLong(),
)
```

- [ ] **Step 5: Implement staging, safe synchronization, and public tasks**

Le synchronizer ne possède que :

```text
math/vector/src/generated/kotlin
math/geometry/src/generated/kotlin
```

Il génère d'abord sous `math/geometry-codegen/build/codegen-staging/<uuid>`, vérifie les paths relatifs normalisés, puis synchronise. Avant de supprimer un fichier obsolète, exiger le header généré ; sinon échouer avec son path.

Le mode `verify` émet deux arbres indépendants en mémoire, compare d'abord leurs paths et bytes pour prouver le déterminisme, puis compare le premier arbre aux deux répertoires versionnés sans aucune écriture.

Ajouter dans `math/geometry-codegen/build.gradle.kts` deux `JavaExec` et dans le root deux aliases :

```kotlin
tasks.register("generateMathPrimitives") {
    dependsOn(":math:geometry-codegen:generateMathPrimitives")
}
tasks.register("verifyMathPrimitivesGenerated") {
    dependsOn(":math:geometry-codegen:verifyMathPrimitivesGenerated")
}
```

Ne les connecter à aucune lifecycle task (tâche de cycle de vie).

- [ ] **Step 6: Wire and generate the vector source set**

Dans `math/vector/build.gradle.kts` :

```kotlin
commonMain {
    kotlin.srcDir("src/generated/kotlin")
    dependencies { implementation(project(":math:scalar")) }
}
```

Run: `rtk ./gradlew generateMathPrimitives`

Expected: les huit fichiers vectoriels sélectionnés sont créés, plus aucun autre fichier.

Supprimer ensuite les cinq anciennes classes manuscrites. Remplacer mécaniquement les factories F32 par les constructeurs et `.normalize()` immutable par `.normalized()`. Si un vrai produit composante par composante est trouvé, créer une extension manuscrite `hadamardProduct`; ne pas réintroduire `operator fun times(Vector)`.

- [ ] **Step 7: Replace vector tests with independent numeric oracles**

Conserver les fichiers de test existants mais remplacer leurs attendus dérivés. Cas minimaux :

```kotlin
@Test
fun `Vector2F32 dot and cross use independent scalar expectations`() {
    val a = Vector2F32(3f, -4f)
    val b = Vector2F32(-2f, 5f)
    assertEquals(3f * -2f + -4f * 5f, a.dot(b))
    assertEquals(3f * 5f - -4f * -2f, a.cross(b))
}

@Test
fun `Vector2I32 accumulation saturates after widened products`() {
    val value = Vector2I32(Int.MIN_VALUE, Int.MIN_VALUE)
    assertEquals(9_223_372_036_854_775_807L, value.dot(value))
}

@Test
fun `mutable conversion copies components without aliasing`() {
    val immutable = Vector2F32(2f, 3f)
    val mutable = immutable.toMutable()
    mutable.x = 9f
    assertEquals(2f, immutable.x)
    assertEquals(9f, mutable.x)
}
```

Ajouter zéro proche, NaN, infini, `-0.0`, égalité/hachage, axes, 3D cross, absence de mutable 4D, et bornes des quatre opérations I32.

- [ ] **Step 8: Verify all affected modules and generation drift**

Run:

```bash
rtk ./gradlew :math:scalar:jvmTest :math:scalar:jsNodeTest \
  :math:vector:jvmTest :math:vector:jsNodeTest \
  :math:geometry:jvmTest :math:matrix:jvmTest \
  verifyMathPrimitivesGenerated
```

Expected: `BUILD SUCCESSFUL`; un second `rtk ./gradlew generateMathPrimitives` laisse `rtk git status --short` inchangé.

- [ ] **Step 9: Commit**

```bash
rtk git add build.gradle.kts math/scalar math/vector math/geometry-codegen math/geometry math/matrix
rtk git commit -m "feat(math): generate semantic vector primitives"
```

---

## Task 3: Generate F32 points and classify rectangle geometry

**Files:**

- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/MathPrimitiveManifest.kt`
- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/SemanticModel.kt`
- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/KotlinEmitter.kt`
- Modify: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/KotlinEmitterTest.kt`
- Modify: `math/geometry/build.gradle.kts`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2F32.kt`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/MutablePoint2F32.kt`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point3F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectF32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Line2F64.kt`
- Create test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Point2F32Test.kt`
- Create test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/MutablePoint2F32Test.kt`
- Create test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Point3F32Test.kt`
- Modify test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectF32Test.kt`
- Modify test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Line2F64Test.kt`

**Interfaces:**

```kotlin
public class Point2F32(public val x: Float, public val y: Float) {
    public operator fun plus(delta: Vector2F32): Point2F32
    public operator fun minus(delta: Vector2F32): Point2F32
    public operator fun minus(other: Point2F32): Vector2F32
    public fun distanceTo(other: Point2F32): Float
    public fun midpointTo(other: Point2F32): Point2F32
    public fun isFinite(): Boolean
    public fun toMutable(): MutablePoint2F32
    public fun toPoint2F64(): Point2F64 // émis seulement après Task 5
    public companion object { public val Origin: Point2F32 }
}

public class MutablePoint2F32(public var x: Float, public var y: Float) {
    public fun translateBy(delta: Vector2F32)
    public fun hasSameComponentsAs(other: Point2F32): Boolean
    public fun hasSameComponentsAs(other: MutablePoint2F32): Boolean
    public fun toImmutable(): Point2F32
}
```

`Point3F32` expose les mêmes opérateurs point/vecteur avec `Vector3F32`, `distanceTo`, `midpointTo`, `isFinite` et `Origin`, mais aucune variante mutable.

- [ ] **Step 1: Write RED point-emission and runtime tests**

Dans le test du générateur, vérifier seulement que `POINT` sélectionne les signatures `plus(Vector): Point`, `minus(Vector): Point`, `minus(Point): Vector` et n'émet pas `unaryMinus`, `times`, `dot`, `cross`, `normalized`.

Dans les tests communs, écrire des attendus primitifs :

```kotlin
@Test
fun `point and vector operators preserve semantic result types`() {
    val p = Point2F32(10f, 20f)
    val d = Vector2F32(3f, -5f)

    val moved: Point2F32 = p + d
    val delta: Vector2F32 = moved - p

    assertEquals(13f, moved.x)
    assertEquals(15f, moved.y)
    assertEquals(3f, delta.x)
    assertEquals(-5f, delta.y)
}

@Test
fun `midpoint expected value is computed from primitive components`() {
    val midpoint = Point2F32(Float.MAX_VALUE, -6f)
        .midpointTo(Point2F32(Float.MAX_VALUE, 10f))
    assertEquals(Float.MAX_VALUE, midpoint.x)
    assertEquals(2f, midpoint.y)
}
```

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:geometry-codegen:test --tests '*KotlinEmitterTest' :math:geometry:jvmTest`

Expected: les points F32 et leurs opérations n'existent pas.

- [ ] **Step 3: Extend the emitter and manifest**

Ajouter `Point2F32(generateMutable = true)` et `Point3F32(generateMutable = false)`. Pour les distances, émettre une formule de longueur stable directement depuis les différences de composants. Pour les milieux F32, calculer la somme en `Double` avant conversion :

```kotlin
public fun midpointTo(other: Point2F32): Point2F32 = Point2F32(
    ((x.toDouble() + other.x.toDouble()) * 0.5).toFloat(),
    ((y.toDouble() + other.y.toDouble()) * 0.5).toFloat(),
)
```

Ne pas encore émettre `toPoint2F64`, car le type cible n'est pas présent dans le manifeste.

Dans `math/geometry/build.gradle.kts`, ajouter `commonMain.kotlin.srcDir("src/generated/kotlin")`, puis lancer `rtk ./gradlew generateMathPrimitives`.

- [ ] **Step 4: Classify `RectF32` and the F32 line inputs**

Changer exactement :

```kotlin
public fun center(): Point2F32
public fun topLeft(): Point2F32
public fun topRight(): Point2F32
public fun bottomLeft(): Point2F32
public fun bottomRight(): Point2F32
public fun offset(delta: Vector2F32)
public fun offsetBy(delta: Vector2F32): RectF32
public fun contains(point: Point2F32): Boolean
public fun bounds(points: Array<Point2F32>): RectF32?
```

Conserver les overloads scalaires utiles (`contains(x, y)`, `offset(dx, dy)`) : ils ne confondent pas point et vecteur. Changer `Line2F64.set` pour accepter deux `Point2F32`, sans ajouter de conversion vectorielle implicite.

- [ ] **Step 5: Add non-tautological rectangle and mutability coverage**

Pour `RectF32.bounds`, vérifier un tableau de points manuels et les quatre bornes littérales. Pour `offsetBy`, vérifier que l'offset n'est accepté qu'en tant que `Vector2F32` et que le résultat vaut `(left + dx, top + dy, right + dx, bottom + dy)` avec quatre assertions scalaires. Pour les conversions mutables, modifier le mutable après copie et vérifier que l'immutable reste inchangé.

- [ ] **Step 6: Verify JVM/JS and generated drift**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  :math:matrix:jvmTest :math:matrix:jsNodeTest \
  verifyMathPrimitivesGenerated
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
rtk git add math/geometry-codegen math/geometry
rtk git commit -m "feat(math): generate F32 point primitives"
```

---

## Task 4: Split integer points from saturating vectors

**Files:**

- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/MathPrimitiveManifest.kt`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2I32.kt`
- Delete: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Vector2I32.kt`
- Delete: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Vector2I32Test.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectI32.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectI32Test.kt`
- Create test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Point2I32Test.kt`
- Create test: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/Vector2I32Test.kt`

**Interfaces:**

```kotlin
public class Point2I32(public val x: Int, public val y: Int) {
    public operator fun plus(delta: Vector2I32): Point2I32
    public operator fun minus(delta: Vector2I32): Point2I32
    public operator fun minus(other: Point2I32): Vector2I32
    public companion object { public val Origin: Point2I32 }
}

public fun RectI32.topLeft(): Point2I32
public fun RectI32.offset(delta: Vector2I32)
public fun RectI32.offsetBy(delta: Vector2I32): RectI32
public fun RectI32.contains(point: Point2I32): Boolean
```

- [ ] **Step 1: Write RED semantic and saturation tests**

```kotlin
@Test
fun `integer point translation saturates instead of wrapping`() {
    val moved = Point2I32(2_147_483_647, -2_147_483_648) + Vector2I32(1, -1)
    assertEquals(2_147_483_647, moved.x)
    assertEquals(-2_147_483_648, moved.y)
}

@Test
fun `subtracting integer points yields a saturating vector`() {
    val delta: Vector2I32 =
        Point2I32(2_147_483_647, -2_147_483_648) - Point2I32(-1, 1)
    assertEquals(2_147_483_647, delta.x)
    assertEquals(-2_147_483_648, delta.y)
}
```

Pour `RectI32`, calculer les attendus de translation avec `Long` dans le test et `coerceIn(-2_147_483_648L, 2_147_483_647L).toInt()`, sans appeler les helpers de production.

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:geometry:jvmTest :math:vector:jvmTest`

Expected: `Point2I32` généré n'existe pas et l'ancien alias ne satisfait pas les nouvelles assertions de type.

- [ ] **Step 3: Add `Point2I32`, regenerate, and delete the alias source**

Ajouter le point I32 au manifeste, lancer `rtk ./gradlew generateMathPrimitives`, puis supprimer l'ancien fichier qui déclarait simultanément `Vector2I32` et `Point2I32`. Le point généré importe les helpers de `math:scalar`; le vecteur réside exclusivement dans `org.graphiks.math.vector`.

- [ ] **Step 4: Migrate `RectI32`**

Importer `org.graphiks.math.vector.Vector2I32` et les fonctions `saturating*` de `math:scalar`. Remplacer les appels statiques historiques `Vector2I32.saturatingAdd32`, `saturatingSub32` et `pinToInt32` par ces fonctions. Les coins et les entrées `contains` deviennent des points; seuls les offsets restent des vecteurs.

- [ ] **Step 5: Verify no integer alias remains**

Run:

```bash
rtk rg -n "typealias (Point2I32|Vector2I32)|geometry\.Vector2I32" math
rtk ./gradlew :math:vector:jvmTest :math:vector:jsNodeTest \
  :math:geometry:jvmTest :math:geometry:jsNodeTest \
  verifyMathPrimitivesGenerated
```

Expected: `rg` ne retourne aucun match (exit 1), puis Gradle retourne `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
rtk git add math/geometry-codegen math/vector math/geometry
rtk git commit -m "feat(math): split integer points and vectors"
```

---

## Task 5: Split F64 path geometry into immutable and mutable point/vector types

**Files:**

- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/MathPrimitiveManifest.kt`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2F64.kt`
- Generate: `math/geometry/src/generated/kotlin/org/graphiks/math/geometry/MutablePoint2F64.kt`
- Already generated/extend: `math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector2F64.kt`
- Already generated/extend: `math/vector/src/generated/kotlin/org/graphiks/math/vector/MutableVector2F64.kt`
- Delete: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Point2F64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsPointPredicates.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Line2F64.kt`
- Replace test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Point2F64Test.kt`
- Create test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/MutablePoint2F64Test.kt`
- Create test: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/Vector2F64Test.kt`
- Create test: `math/vector/src/commonTest/kotlin/org/graphiks/math/vector/MutableVector2F64Test.kt`
- Modify test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/Line2F64Test.kt`

**Interfaces:**

```kotlin
public class Point2F64(public val x: Double, public val y: Double)
public class MutablePoint2F64(public var x: Double, public var y: Double)
public class Vector2F64(public val x: Double, public val y: Double)
public class MutableVector2F64(public var x: Double, public var y: Double)

internal fun Point2F64.pathOpsApproximatelyDEquals(other: Point2F64): Boolean
internal fun Point2F64.pathOpsApproximatelyEquals(other: Point2F64): Boolean
internal fun Point2F64.pathOpsRoughlyEquals(other: Point2F64): Boolean
internal fun Point2F64.pathOpsApproximatelyZero(): Boolean
internal fun Vector2F64.pathOpsCrossCheck(other: Vector2F64): Double
internal fun Vector2F64.pathOpsCrossNoNormalCheck(other: Vector2F64): Double

public val Line2F64.start: Point2F64
public val Line2F64.end: Point2F64
public fun Line2F64.direction(): Vector2F64
```

- [ ] **Step 1: Write RED tests for the four distinct F64 types**

```kotlin
@Test
fun `line direction is endpoint difference`() {
    val line = Line2F64(arrayOf(Point2F64(2.0, 7.0), Point2F64(11.0, -5.0)))
    val direction: Vector2F64 = line.direction()
    assertEquals(9.0, direction.x)
    assertEquals(-12.0, direction.y)
}

@Test
fun `mutable point and vector conversions do not share state`() {
    val point = Point2F64(4.0, 8.0)
    val mutable = point.toMutable()
    mutable.translateBy(Vector2F64(3.0, -2.0))
    assertEquals(4.0, point.x)
    assertEquals(8.0, point.y)
    assertEquals(7.0, mutable.x)
    assertEquals(6.0, mutable.y)
}
```

Les tests des prédicats pathops conservent leurs vectors de nombres historiques et leurs booléens attendus; ils ne sont pas générés et ne calculent pas l'attendu avec un autre prédicat.

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:geometry:jvmTest :math:vector:jvmTest`

Expected: les types sont encore aliasés/mutables et les signatures n'existent pas.

- [ ] **Step 3: Add the F64 point manifest entries and conversions**

Ajouter `Point2F64(generateMutable = true)` puis régénérer. Comme `Point2F32` et `Point2F64` sont désormais tous deux sélectionnés, émettre les conversions nommées dans les deux sens :

```kotlin
public fun Point2F32.toPoint2F64(): Point2F64 = Point2F64(x.toDouble(), y.toDouble())
public fun Point2F64.toPoint2F32(): Point2F32 = Point2F32(x.toFloat(), y.toFloat())
```

Ne pas émettre de conversion Point/Vector. Supprimer l'ancien `Point2F64.kt` et son `typealias Vector2F64` dans le même changement.

- [ ] **Step 4: Move pathops-only math to handwritten extensions**

Déplacer les corps existants de `approximatelyDEqual`, `approximatelyEqual`, `roughlyEqual`, `approximatelyZero`, `crossCheck` et `crossNoNormalCheck` vers `PathOpsPointPredicates.kt`, en changeant uniquement les receivers sémantiques : prédicats de position sur `Point2F64`, cross sur `Vector2F64`. Garder tous les appels à `PathOpsEpsilon` et les formules ULP (unit in the last place, unité au dernier rang) identiques.

Les anciennes variantes prenant un `Vector2F32` sont remplacées par des variantes prenant `Point2F32`. Les helpers statiques comparant deux anciens `Vector2F32` points deviennent des fonctions top-level sur deux `Point2F32`.

- [ ] **Step 5: Migrate `Line2F64` by role**

Stocker deux points immutables et remplacer les éléments du tableau lors d'un `set`. Ajouter :

```kotlin
public val start: Point2F64 get() = pts[0]
public val end: Point2F64 get() = pts[1]
public fun direction(): Vector2F64 = end - start
```

Dans `nearPoint` et `nearRay`, typer `len` et `ab0` en `Vector2F64`, utiliser `len.dot(ab0)`, et garder `realPt` en `Point2F64`. `ptAtT` construit directement un point immutable. Le constructeur sans argument utilise deux `Point2F64.Origin` sans partager d'état mutable.

- [ ] **Step 6: Verify aliases are gone and pathops remains correct on JVM/JS**

Run:

```bash
rtk rg -n "typealias (Point2F64|Vector2F64)|Point2F64\.of|asVector2F32" math
rtk ./gradlew :math:vector:jvmTest :math:vector:jsNodeTest \
  :math:geometry:jvmTest :math:geometry:jsNodeTest \
  verifyMathPrimitivesGenerated
```

Expected: aucun match obsolète, puis `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
rtk git add math/geometry-codegen math/vector math/geometry
rtk git commit -m "feat(math): split F64 path points and vectors"
```

---

## Task 6: Replace `Matrix3x3F32.map*` with semantic 2D transforms

**Files:**

- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F32.kt`
- Modify: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F32Test.kt`
- Modify: any math test or source still calling the removed `Matrix3x3F32.map*` API

**Interfaces:**

```kotlin
public fun transform(point: Point2F32): Point2F32
public fun transform(vector: Vector2F32): Vector2F32
public operator fun times(point: Point2F32): Point2F32
public operator fun times(vector: Vector2F32): Vector2F32
public fun transformInto(source: Point2F32, destination: MutablePoint2F32)
public fun transformInto(source: Vector2F32, destination: MutableVector2F32)
public fun transformPoints(
    source: Array<Point2F32>,
    destination: Array<MutablePoint2F32>,
    count: Int = source.size,
)
public fun transformVectors(
    source: Array<Vector2F32>,
    destination: Array<MutableVector2F32>,
    count: Int = source.size,
)
public fun transformHomogeneousPoints(
    source: Array<Point2F32>,
    destination: Array<MutableVector3F32>,
    count: Int = source.size,
)
public fun transformDisplacementAt(
    anchor: Point2F32,
    displacement: Vector2F32,
): Vector2F32
public fun transformBounds(rect: RectF32): RectF32
public fun transformBoundsScaleTranslate(rect: RectF32): RectF32
public fun transformRadius(radius: Float): Float
```

- [ ] **Step 1: Write RED tests from raw coefficients**

Ajouter au moins ces cas, avec attendus calculés sans méthode de transformation :

```kotlin
@Test
fun `translation affects points but not vectors`() {
    val matrix = Matrix3x3F32.of(
        2f, 3f, 11f,
        5f, 7f, 13f,
        0f, 0f, 1f,
    )
    val point = matrix.transform(Point2F32(17f, 19f))
    val vector = matrix.transform(Vector2F32(17f, 19f))

    assertEquals(2f * 17f + 3f * 19f + 11f, point.x)
    assertEquals(5f * 17f + 7f * 19f + 13f, point.y)
    assertEquals(2f * 17f + 3f * 19f, vector.x)
    assertEquals(5f * 17f + 7f * 19f, vector.y)
}

@Test
fun `projective matrix rejects global vector transform`() {
    val projective = Matrix3x3F32.of(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0.25f, 0f, 1f,
    )
    assertFailsWith<IllegalArgumentException> {
        projective.transform(Vector2F32(3f, 4f))
    }
}
```

Pour `transformDisplacementAt`, calculer dans le test les deux triplets `(rawX, rawY, rawW)` depuis les neuf coefficients, effectuer les deux divisions puis soustraire les scalaires. Ne pas appeler `transform(point)` pour construire l'attendu.

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F32Test'`

Expected: les méthodes `transform` et les opérateurs typés n'existent pas.

- [ ] **Step 3: Implement scalar point/vector kernels**

Point :

```kotlin
public fun transform(point: Point2F32): Point2F32 {
    val rawX = sx * point.x + kx * point.y + tx
    val rawY = ky * point.x + sy * point.y + ty
    if (!hasPerspective()) return Point2F32(rawX, rawY)
    val w = persp0 * point.x + persp1 * point.y + persp2
    val inverseW = if (w == 0f) 0f else 1f / w
    return Point2F32(rawX * inverseW, rawY * inverseW)
}
```

Vector : exiger `!hasPerspective()` avec le message exact `"transform(vector) requires an affine Matrix3x3F32"`, puis appliquer uniquement le bloc linéaire 2×2. Les deux `operator times` délèguent aux formes nommées.

Implémenter `transformDisplacementAt` comme `transform(anchor + displacement) - transform(anchor)` en production; son test reste l'oracle brut décrit au Step 1.

- [ ] **Step 4: Replace bulk APIs with mutable destinations**

Conserver les fast paths identity/translate/scale/affine/perspective, mais écrire les champs de `destination[i]` au lieu de remplacer un immutable. Valider :

```kotlin
require(count in 0..source.size && count <= destination.size) {
    "count=$count exceeds source.size=${source.size} or destination.size=${destination.size}"
}
```

`transformVectors` vérifie la précondition affine une seule fois avant la boucle. `transformHomogeneousPoints` sort `(rawX, rawY, rawW)` dans `MutableVector3F32` et n'effectue aucune division.

- [ ] **Step 5: Rename the remaining geometric operations**

- `mapRect` → `transformBounds`
- `mapRectScaleTranslate` → `transformBoundsScaleTranslate`
- `mapRadius` → `transformRadius`, avec précondition affine

Les quatre coins intermédiaires sont des `Point2F32`. `transformRadius` applique le bloc linéaire à `Vector2F32(radius, 0f)` et `Vector2F32(0f, radius)` puis conserve la moyenne géométrique existante.

- [ ] **Step 6: Remove every old `Matrix3x3F32.map*` entry point**

Migrer les tests et call sites; ne pas garder de wrapper. Puis :

```bash
rtk rg -n "\bmap(XY|Vector|Points|Vectors|HomogeneousPoints|Rect|RectScaleTranslate|Radius)\b" \
  math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F32.kt
```

Expected: aucun match (exit 1).

- [ ] **Step 7: Verify JVM and JS**

Run: `rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest`

Expected: `BUILD SUCCESSFUL`, y compris les tests de bulk destination, perspective, opérateurs et bounds.

- [ ] **Step 8: Commit**

```bash
rtk git add math/matrix
rtk git commit -m "feat(math): add semantic Matrix3x3 transforms"
```

---

## Task 7: Add semantic 3D and homogeneous matrix transforms

**Files:**

- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x4F32.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix4x4F32.kt`
- Modify: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x4F32Test.kt`
- Modify: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix4x4F32Test.kt`
- Modify: any math source/test still calling the removed `Matrix3x4F32.map` or `Matrix4x4F32.map*` API

**Interfaces:**

```kotlin
// Matrix3x4F32, toujours affine
public fun transform(point: Point3F32): Point3F32
public fun transform(vector: Vector3F32): Vector3F32
public operator fun times(point: Point3F32): Point3F32
public operator fun times(vector: Vector3F32): Vector3F32

// Matrix4x4F32
public fun isAffine(): Boolean
public fun transform(point: Point3F32): Point3F32
public fun transform(vector: Vector3F32): Vector3F32
public fun transformHomogeneous(value: Vector4F32): Vector4F32
public operator fun times(point: Point3F32): Point3F32
public operator fun times(vector: Vector3F32): Vector3F32
public operator fun times(value: Vector4F32): Vector4F32
public fun transformBounds(rect: RectF32): RectF32

public fun lookAt(
    eye: Point3F32,
    center: Point3F32,
    up: Vector3F32,
): Matrix4x4F32
```

- [ ] **Step 1: Write RED 3×4 semantic tests**

Utiliser une matrice dont les coefficients linéaires et la translation sont tous différents. Calculer chaque composant attendu à partir des douze scalaires :

```kotlin
@Test
fun `Matrix3x4 translation affects a point only`() {
    val matrix = Matrix3x4F32(
        2f, 3f, 5f, 7f,
        11f, 13f, 17f, 19f,
        23f, 29f, 31f, 37f,
    )
    val point = matrix * Point3F32(41f, 43f, 47f)
    val vector = matrix * Vector3F32(41f, 43f, 47f)
    assertEquals(2f * 41f + 3f * 43f + 5f * 47f + 7f, point.x)
    assertEquals(2f * 41f + 3f * 43f + 5f * 47f, vector.x)
    // mêmes assertions indépendantes pour y et z
}
```

- [ ] **Step 2: Write RED 4×4 and camera tests**

Cas obligatoires : point affine avec translation, vector affine sans translation, `Vector4F32` homogène avec les seize coefficients, point projectif avec division `w`, rejet d'un vecteur projectif, bounds affine et projectif, et `lookAt` avec `eye/center` points.

Pour `lookAt`, ne pas reconstruire l'attendu avec une autre matrice de vue : vérifier que `lookAt(eye, center, up).transform(eye)` donne `(0,0,0)` à une tolérance explicite et ajouter un second oracle scalaire pour un cas axial connu (`eye=(0,0,5)`, `center=Origin`, `up=UnitY`) dont la translation Z attendue est `-5`.

- [ ] **Step 3: Run and observe RED**

Run: `rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x4F32Test' --tests '*Matrix4x4F32Test'`

Expected: les signatures point/vector n'existent pas.

- [ ] **Step 4: Implement Matrix3x4 point and vector kernels**

`transform(point)` inclut `m03/m13/m23`; `transform(vector)` les exclut. Les opérateurs délèguent. Supprimer les deux anciennes overloads `map` et corriger la KDoc qui décrivait tout comme un vecteur.

- [ ] **Step 5: Implement Matrix4 homogeneous, point, and affine vector kernels**

La matrice est stockée en column-major (par colonnes), donc conserver les indices existants :

```kotlin
public fun isAffine(): Boolean =
    fMat[3] == 0f && fMat[7] == 0f && fMat[11] == 0f && fMat[15] == 1f

public fun transform(point: Point3F32): Point3F32 {
    val value = transformHomogeneous(Vector4F32(point.x, point.y, point.z, 1f))
    val inverseW = if (value.w == 0f) 0f else 1f / value.w
    return Point3F32(value.x * inverseW, value.y * inverseW, value.z * inverseW)
}

public fun transform(vector: Vector3F32): Vector3F32 {
    require(isAffine()) { "transform(vector) requires an affine Matrix4x4F32" }
    val value = transformHomogeneous(Vector4F32(vector.x, vector.y, vector.z, 0f))
    return Vector3F32(value.x, value.y, value.z)
}
```

`transformHomogeneous` contient directement les seize multiplications/additions actuelles. Le comportement documenté d'un point dont `w == 0` reste un résultat nul après la politique `inverseW = 0`, ce qui garde la fonction totale comme dans la spec.

- [ ] **Step 6: Type the camera and rectangle paths**

Changer `lookAt` en `(Point3F32, Point3F32, Vector3F32)`. `center - eye` produit naturellement un `Vector3F32`; aucun cast ou helper d'effacement sémantique n'est ajouté.

Renommer `mapRect` en `transformBounds`; ses coins 2D sont construits comme `Point3F32(x, y, 0f)` avant la projection. Les helpers privés deviennent `transformBoundsAffine` et `transformBoundsPerspective`. Les structures homogènes intermédiaires restent `Vector4F32`.

- [ ] **Step 7: Remove all public matrix `map*` names and verify**

Run:

```bash
rtk rg -n "public fun map|\.map(Point|Rect|Vector)?\(" math/matrix math/geometry math/vector
rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest \
  :math:geometry:jvmTest :math:geometry:jsNodeTest
```

Expected: aucun ancien appel public `map*`; Gradle retourne `BUILD SUCCESSFUL`. Les appels Kotlin standard `collections.map` éventuels sont examinés et ne sont pas renommés.

- [ ] **Step 8: Commit**

```bash
rtk git add math/matrix math/geometry math/vector
rtk git commit -m "feat(math): add semantic 3D matrix transforms"
```

---

## Task 8: Enforce compile-time semantics and forbid fallback identity use

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `math/geometry-codegen/build.gradle.kts`
- Create: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/FixtureCompiler.kt`
- Create: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/SemanticCompilationTest.kt`
- Create: `math/geometry-codegen/src/test/resources/semantic-fixtures/positive/AllowedOperations.kt`
- Create: `math/geometry-codegen/src/test/resources/semantic-fixtures/negative/PointPlusPoint.kt`
- Create: `math/geometry-codegen/src/test/resources/semantic-fixtures/negative/PointScale.kt`
- Create: `math/geometry-codegen/src/test/resources/semantic-fixtures/negative/PointNormalize.kt`
- Create: `math/geometry-codegen/src/test/resources/semantic-fixtures/negative/PointAssignedAsVector.kt`
- Create: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/IdentityUsageVerifier.kt`
- Create: `math/geometry-codegen/src/test/kotlin/org/graphiks/math/codegen/IdentityUsageVerifierTest.kt`
- Modify: `math/geometry-codegen/src/main/kotlin/org/graphiks/math/codegen/Main.kt`
- Modify: legitimate identity comparisons in `math/color/.../ColorMatrixF32.kt`
- Modify: remove unnecessary `this === other` shortcuts in `Matrix4x4F32.kt` and `Line2F64.kt`

**Interfaces:**

```kotlin
internal data class CompilationResult(
    val exitCode: ExitCode,
    val diagnostics: String,
)

internal fun compileFixture(source: Path, classpath: String, destination: Path): CompilationResult

internal data class IdentityViolation(val path: String, val line: Int, val expression: String)
internal object IdentityUsageVerifier {
    fun verify(repoRoot: Path): List<IdentityViolation>
}
```

- [ ] **Step 1: Add compiler dependency and write RED compilation tests**

Ajouter :

```toml
kotlinCompilerEmbeddable = { module = "org.jetbrains.kotlin:kotlin-compiler-embeddable", version.ref = "kotlin" }
```

et `testImplementation(libs.kotlinCompilerEmbeddable)`. La fixture positive contient explicitement :

```kotlin
fun allowed(
    p1: Point2F32,
    p2: Point2F32,
    v: Vector2F32,
    matrix: Matrix3x3F32,
) {
    val moved: Point2F32 = p1 + v
    val delta: Vector2F32 = p2 - p1
    val scaled: Vector2F32 = 2f * v
    val transformedPoint: Point2F32 = matrix * p1
    val transformedVector: Vector2F32 = matrix * v
}
```

Chaque fixture négative ne contient qu'une violation, respectivement :

```kotlin
val illegal = point1 + point2
val illegal = point * 2f
val illegal = point.normalized()
val illegal: Vector2F32 = matrix.transform(point)
```

Le test exige `ExitCode.OK` pour la positive et `ExitCode.COMPILATION_ERROR` pour chaque négative. Il exige aussi que les diagnostics mentionnent le fichier et le symbole fautif (`plus`, `times`, `normalized` ou `transform`) afin qu'une erreur d'import étrangère ne fasse pas passer le test.

- [ ] **Step 2: Run and observe RED**

Run: `rtk ./gradlew :math:geometry-codegen:test --tests '*SemanticCompilationTest'`

Expected: `FixtureCompiler` n'existe pas.

- [ ] **Step 3: Implement compilation through the matching Kotlin compiler**

Utiliser `K2JVMCompiler` avec le classpath du worker de test et une destination temporaire :

```kotlin
val arguments = K2JVMCompilerArguments().apply {
    freeArgs = listOf(source.toAbsolutePath().toString())
    destination = destination.toAbsolutePath().toString()
    classpath = classpath
    noStdlib = true
    noReflect = true
}
val output = ByteArrayOutputStream()
val exitCode = K2JVMCompiler().exec(
    PrintingMessageCollector(
        PrintStream(output),
        MessageRenderer.PLAIN_RELATIVE_PATHS,
        false,
    ),
    Services.EMPTY,
    arguments,
)
```

Le test de `geometry-codegen` dépend en `testImplementation` des variantes JVM de `:math:vector`, `:math:geometry` et `:math:matrix`; aucune dépendance inverse n'est ajoutée.

- [ ] **Step 4: Write RED tests for the identity scanner**

Cas à reconnaître dans des fichiers temporaires : `a === b`, `a !== b`, `System.identityHashCode(a)`, `synchronized(a)`, `kotlin.synchronized(a)` et `IdentityHashMap`. Cas à ignorer : commentaires, strings, et ligne explicitement justifiée par `// identity-ok: array aliasing`.

Le marqueur sans raison (`// identity-ok:`) reste une violation.

- [ ] **Step 5: Implement and wire the static identity guard**

Scanner les fichiers `math/**/src/**/*.{kt,kts}` avec un petit lexer qui ignore commentaires et littéraux; ne pas employer une simple regex sur le texte brut. Toute ligne de code contenant une opération interdite échoue, sauf marqueur `identity-ok` non vide sur cette même ligne.

Les comparaisons d'aliasing réellement requises sur des tableaux deviennent par exemple :

```kotlin
if (destination === source) { /* ... */ } // identity-ok: mutable array aliasing
```

Supprimer les fast paths `this === other` des `equals` de classes ordinaires : ils ne sont pas requis pour la correction. Ajouter une tâche `verifyMathPrimitiveIdentityUsage`, puis faire dépendre `verifyMathPrimitivesGenerated` de cette vérification explicite. Ne la connecter à aucune tâche de build normale.

- [ ] **Step 6: Run compile contracts and the guard**

Run:

```bash
rtk ./gradlew :math:geometry-codegen:test \
  verifyMathPrimitiveIdentityUsage verifyMathPrimitivesGenerated
```

Expected: fixture positive OK, quatre fixtures négatives en `COMPILATION_ERROR`, aucun usage d'identité non justifié et aucune dérive générée.

- [ ] **Step 7: Commit**

```bash
rtk git add gradle/libs.versions.toml math/geometry-codegen math/matrix math/color math/geometry
rtk git commit -m "test(math): enforce semantic geometry contracts"
```

---

## Task 9: Add isolated JVM/JS benchmarks without performance claims

**Files:**

- Modify: `settings.gradle.kts`
- Create: `math/geometry-benchmarks/build.gradle.kts`
- Create: `math/geometry-benchmarks/src/commonMain/kotlin/org/graphiks/math/benchmarks/SemanticGeometryBenchmark.kt`
- Create: `math/geometry-benchmarks/src/jvmMain/kotlin/org/graphiks/math/benchmarks/JvmAllocationProbe.kt`
- Create after measurement: `reports/math-geometry/2026-08-23-semantic-primitives-baseline.md`

**Interfaces:**

```kotlin
@State(Scope.Benchmark)
public class SemanticGeometryBenchmark {
    @Benchmark public fun transformSinglePoint(): Float
    @Benchmark public fun transformSingleVector(): Float
    @Benchmark public fun transformPointBatch(): Float
    @Benchmark public fun mutableVectorAccumulation(): Float
}
```

- [ ] **Step 1: Create an isolated benchmark module**

Ajouter `include(":math:geometry-benchmarks")` et :

```kotlin
plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinxBenchmark)
}

kotlin {
    jvm()
    js { nodejs() }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":math:vector"))
            implementation(project(":math:geometry"))
            implementation(project(":math:matrix"))
            implementation(libs.kotlinxBenchmarkRuntime)
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("js")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 300
            iterationTimeUnit = "ms"
        }
    }
}
```

Ne dépendre de ce module depuis aucun module de production et ne connecter aucune benchmark task à `check` ou `build`.

- [ ] **Step 2: Implement benchmarks with an observable sink**

Préparer une matrice affine non triviale, un point, un vecteur, 1 024 sources et 1 024 destinations mutables. Chaque méthode retourne la somme d'un composant final afin que le calcul soit observable. Le benchmark batch appelle `transformPoints`; il ne reconstruit pas l'algorithme à la main.

Taguer la sortie avec la représentation réellement émise (`FINAL_CLASS`) et ne pas créer de benchmark MFVC factice sous Kotlin 2.4.0.

- [ ] **Step 3: Add a JVM allocation probe outside correctness tests**

Utiliser `com.sun.management.ThreadMXBean` quand `isThreadAllocatedMemorySupported` est vrai. Après cinq warmups, mesurer un million d'opérations, conserver un `@Volatile` sink et produire :

```json
{
  "representation": "FINAL_CLASS",
  "operation": "Matrix3x3F32.transform(Point2F32)",
  "iterations": 1000000,
  "allocatedBytes": 0,
  "allocatedBytesPerOperation": 0.0
}
```

Les valeurs `0` ci-dessus décrivent seulement le schéma; le programme écrit les valeurs observées sous `math/geometry-benchmarks/build/reports/allocations.json`. Si le JVM ne supporte pas le compteur, il écrit `"status":"unsupported"` et n'invente pas zéro allocation.

Ajouter une tâche explicite `measureJvmGeometryAllocations` qui dépend de la compilation JVM du benchmark, jamais d'une lifecycle task.

- [ ] **Step 4: Run the two timing targets and allocation probe**

Run:

```bash
rtk ./gradlew :math:geometry-benchmarks:jvmBenchmark
rtk ./gradlew :math:geometry-benchmarks:jsBenchmark
rtk ./gradlew :math:geometry-benchmarks:measureJvmGeometryAllocations
```

Expected: résultats de timing JVM/Node.js et JSON d'allocations JVM, ou statut `unsupported` explicite.

- [ ] **Step 5: Record the baseline without extrapolation**

Créer le rapport Markdown avec : commit, OS/JVM/Node, Kotlin 2.4.0, représentation `FINAL_CLASS`, commandes exactes, warmups/iterations, sorties brutes ou liens vers les fichiers de build, et une section littérale :

```text
Non-claim: this baseline does not establish that the generated representation
is allocation-free or faster than a future multi-field value class backend.
```

Ne pas établir de seuil de correction et ne pas comparer à MFVC tant que ce backend ne compile pas sur JVM et JS.

- [ ] **Step 6: Commit**

```bash
rtk git add settings.gradle.kts math/geometry-benchmarks reports/math-geometry
rtk git commit -m "perf(math): baseline semantic geometry primitives"
```

---

## Task 10: Audit the breaking migration and run final cross-platform verification

**Files:**

- Create: `math/README.md`
- Modify: any residual math source/test found by the absence scans below
- Modify: generated sources only by rerunning `generateMathPrimitives`, never by hand

**Interfaces:** Aucun nouvel entry point; cette tâche ferme les critères d'acceptation.

- [ ] **Step 1: Document the semantic model and generation workflow**

Dans `math/README.md`, documenter : tableau Point/Vector, mutable/immutable, opérations permises/interdites, règles affine/projective, commandes `generateMathPrimitives` et `verifyMathPrimitivesGenerated`, répertoires générés versionnés, et stratégie future MFVC. Inclure un exemple `matrix * point` / `matrix * vector`, sans `map`.

- [ ] **Step 2: Run API absence scans**

Run:

```bash
rtk rg -n "typealias (Point|Vector)|Vector[234](F32|F64|I32)\.of|Point[23](F32|F64|I32)\.of" math
rtk rg -n "public fun map|fun map(XY|Point|Vector|Points|Vectors|Rect|Radius)" math/matrix
rtk rg -n "^[[:space:]]+public operator fun times\(scalar: (Float|Double|Int)\): Vector" \
  math/vector/src/generated/kotlin
rtk rg -n "^public operator fun (Float|Double|Int)\.times\(vector: Vector" \
  math/vector/src/generated/kotlin
rtk rg -n "^[[:space:]]+public operator fun times\([^)]*: (Float|Double|Int)\)" \
  math/geometry/src/generated/kotlin
rtk rg -n "^[[:space:]]+public operator fun times\([^)]*: Vector" \
  math/vector/src/generated/kotlin
rtk rg -n "Point[234](F32|F64|I32).*(dot|cross|normalized)|operator fun unaryMinus.*Point" \
  math/geometry/src/generated/kotlin
```

Expected:

- les deux premières commandes ne trouvent rien;
- les troisième et quatrième classent respectivement les cinq formes
  vector×scalar et les cinq formes scalar×vector autorisées;
- les cinquième et sixième ne trouvent rien : elles rejettent respectivement
  Point×scalar et Vector×Vector;
- la septième ne trouve rien.

Examiner chaque match au lieu de modifier aveuglément les `map` de collections Kotlin.

- [ ] **Step 3: Prove the normal build does not invoke code generation**

Run:

```bash
rtk ./gradlew --dry-run :math:vector:jvmTest :math:geometry:jvmTest :math:matrix:jvmTest \
  | rtk rg "generateMathPrimitives|verifyMathPrimitivesGenerated"
```

Expected: aucun match (exit 1). Si une tâche apparaît, supprimer la dépendance de lifecycle avant de continuer.

- [ ] **Step 4: Prove generation is deterministic and non-mutating when clean**

Capturer `rtk git status --porcelain`, puis lancer :

```bash
rtk ./gradlew verifyMathPrimitiveIdentityUsage verifyMathPrimitivesGenerated
rtk ./gradlew generateMathPrimitives
rtk ./gradlew verifyMathPrimitivesGenerated
rtk git status --porcelain
```

Expected: le status final est byte-for-byte identique au status initial; les deux vérifications passent.

- [ ] **Step 5: Run the complete math matrix on JVM and JS**

Run:

```bash
rtk ./gradlew \
  :math:scalar:jvmTest :math:scalar:jsNodeTest \
  :math:vector:jvmTest :math:vector:jsNodeTest \
  :math:geometry:jvmTest :math:geometry:jsNodeTest \
  :math:matrix:jvmTest :math:matrix:jsNodeTest \
  :math:color:jvmTest :math:color:jsNodeTest \
  :math:geometry-codegen:test
```

Expected: `BUILD SUCCESSFUL`, sans test skipped dans les modules listés.

- [ ] **Step 6: Review oracle independence explicitly**

Lire chaque nouveau test math et refuser :

- un attendu produit par `transform`, `normalized`, `dot`, `cross`, `distanceTo` ou un helper de saturation de production;
- un golden de runtime produit par le manifeste ou l'emitter;
- une identité algébrique comme seul oracle;
- un test négatif qui échoue pour une import manquante plutôt que pour l'opération interdite.

Les seuls tests pouvant comparer deux générations sont ceux de déterminisme du générateur.

- [ ] **Step 7: Commit final documentation or residual cleanup**

```bash
rtk git add math/README.md math
rtk git commit -m "docs(math): document semantic geometry primitives"
```

Si `rtk git diff --cached --quiet` indique qu'il n'y a rien à committer, ne pas créer de commit vide.

---

## Final Acceptance Checklist

- [ ] Le manifeste final contient exactement les neuf immutables et cinq mutables approuvés.
- [ ] Points et vecteurs sont des types distincts sans alias ni conversion implicite.
- [ ] Les opérations interdites échouent pour la bonne raison dans les fixtures de compilation.
- [ ] Les transformations distinguent translation de point et bloc linéaire de vecteur.
- [ ] Les vecteurs projectifs sont rejetés; les déplacements projectifs exigent un ancrage.
- [ ] Les conversions mutables/immutables copient les composants.
- [ ] Les limites I32/Long saturent selon des oracles élargis indépendants.
- [ ] Les sorties générées sont byte-déterministes, versionnées et vérifiées sans modifier le checkout.
- [ ] Le build normal ne lance ni génération ni benchmark.
- [ ] Les tests JVM/JS, fixtures et vérifications statiques passent.
- [ ] Les mesures de performance sont isolées et ne soutiennent aucune affirmation non mesurée.
