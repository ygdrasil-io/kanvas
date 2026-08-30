# Math Path Topology Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer l'arrangement approximatif de Task 5 par un moteur topologique KMP robuste dans `:math:geometry`, fermer les huit findings de review et conserver une API publique `F32` correcte sur JVM et JS.

**Architecture:** La frontière publique reste `PathF32`/`PathOpsF32`/`PathAnalysisF32`/`PathMeasureF32`. Les courbes sont normalisées et aplaties adaptativement dans un noyau interne `F64`; des prédicats adaptatifs, un découpage d'intersections et un arrangement à demi-arêtes propagent les winding des faces avant projection canonique vers `F32`.

**Tech Stack:** Kotlin 2.x Multiplatform, `commonMain`/`commonTest`, primitives `Point2F32`/`Point2F64` de `:math:geometry` et `Vector2F32`/`Vector2F64` de `:math:vector`, JUnit via `kotlin.test`, aucune dépendance de clipping externe.

**Spec:** [`refactor/specs/2026-08-30-math-path-topology-engine-design.md`](../specs/2026-08-30-math-path-topology-engine-design.md)

## Global Constraints

- Tout type numérique de `:math` porte `F32`, `F64`, `I32` ou `I64`; les enums sans valeur numérique restent non suffixés.
- L'API publique de path reste en `F32`; tous les calculs géométriques intermédiaires du nouveau noyau sont en `F64`.
- Tolérance normalisée de flattening : `2.0.pow(-23)`; profondeur maximale par défaut : 32.
- Limites par défaut : 65 536 arêtes aplaties par opérande, 262 144 intersections, 262 144 sommets, 1 048 576 demi-arêtes et 16 777 216 probes candidats (`2^24`). Ce dernier est un budget global de travail séparé des résultats et de la mémoire : il débite avant chaque pop brut et chaque inspection, comparaison, copie ou mise à jour d'incidence/index de candidat.
- Les prédicats d'orientation ambigus utilisent une expansion arithmétique exacte; aucune grille décimale globale ni clé `Int` quantifiée n'est autorisée.
- Les opérations binaires refusent les coordonnées non finies et les inverse fills; `simplify` et `asWinding` conservent le caractère inverse.
- Les points de frontière sont hors de `contains`; les contours ouverts sont fermés implicitement seulement pour le fill.
- Aucun test ne lit les sources, packages, imports ou noms de composants internes; les assertions portent sur la géométrie, les résultats numériques et les erreurs publiques observables.
- Toutes les commandes shell sont préfixées par `rtk`; toute édition manuelle utilise `apply_patch`.

---

## Carte des fichiers

### Noyau numérique et nomenclature

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsLimitsI32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathNormalizationF64.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathPredicatesF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathPredicatesF64.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/OrientationPredicateF64.kt`.
- Supprimer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsEpsilon.kt` après migration de ses consommateurs.
- Modifier `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Line2F64.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokeRectGm.kt`.
- Remplacer `PathOpsEpsilonTest.kt` par les tests `PathPredicatesF32Test.kt`, `PathPredicatesF64Test.kt`, `OrientationPredicateF64Test.kt` et `PathNormalizationF64Test.kt`.

### Courbes, analyses et mesure

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArcF64.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`.
- Modifier `PathAnalysisF32.kt` et `PathMeasureF32.kt`.
- Étendre `PathAnalysisF32Test.kt` et `PathMeasureF32Test.kt`.
- Créer `PathFlatteningF64Test.kt`.

### Intersections et arrangement

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt`.
- Créer `PathIntersectionsF64Test.kt` et `PathArrangementF64Test.kt`.

### Intégration publique et matrice de preuve

- Réécrire `PathOpsF32.kt` autour du nouveau noyau.
- Étendre `PathOpsF32Test.kt`.
- Créer `PathBehaviorTestSupportF32.kt` dans `commonTest` pour les transformations et comparaisons d'appartenance partagées.
- Créer `PathOpsMetamorphicF32Test.kt`.
- Étendre `RegionF32Test.kt` pour l'immutabilité défensive.
- Mettre à jour le rapport SDD externe de Task 5 après les commits; aucun document ne prétend conserver les verbes courbes dans les sorties booléennes.

---

## Task 1 — Poser la nomenclature numérique, les limites et les prédicats robustes

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsLimitsI32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathNormalizationF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathPredicatesF32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathPredicatesF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/OrientationPredicateF64.kt`
- Delete: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsEpsilon.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/Line2F64.kt`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokeRectGm.kt`
- Delete: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsEpsilonTest.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathPredicatesF32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathPredicatesF64Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/OrientationPredicateF64Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathNormalizationF64Test.kt`

**Interfaces:**

- Consumes: `PathF32`, `Point2F32`, `Point2F64`, `PathAnalysisF32.bounds`.
- Produces:

```kotlin
internal data class PathOpsLimitsI32(
    val maxSubdivisionDepth: Int = 32,
    val maxFlattenedEdgesPerOperand: Int = 65_536,
    val maxIntersections: Int = 262_144,
    val maxVertices: Int = 262_144,
    val maxHalfEdges: Int = 1_048_576,
    val maxCandidateProbes: Int = 16_777_216,
)

internal data class PathNormalizationF64(
    val origin: Point2F64,
    val scale: Double,
) {
    fun normalize(point: Point2F32): Point2F64
    fun normalizeVector(vector: Vector2F32): Vector2F64
    fun denormalize(point: Point2F64): Point2F32
}

internal fun pathNormalizationF64(paths: List<PathF32>): PathNormalizationF64

internal data class NormalizedPathF64(
    val path: PathF32,
    val normalization: PathNormalizationF64,
)

internal object PathPredicatesF32 {
    const val EPSILON_F32: Float
    fun almostEqualUlps(a: Float, b: Float, maxUlps: Int = 16): Boolean
}

internal object PathPredicatesF64 {
    const val EPSILON_F64: Double
    fun onSegment(point: Point2F64, start: Point2F64, end: Point2F64): Boolean
    fun almostEqualUlps(a: Double, b: Double, maxUlps: Int = 16): Boolean
}

internal object OrientationPredicateF64 {
    fun sign(a: Point2F64, b: Point2F64, c: Point2F64): Int
}
```

`ExpansionF64` est internal et contenu dans `OrientationPredicateF64.kt`; il fournit `twoSum`, `twoDiff`, `twoProduct`, `expansionSum`, `expansionDiff` et `sign`. `twoProduct` utilise le splitter IEEE-754 `134_217_729.0`; `sign` lit le dernier composant non nul de l'expansion non chevauchante et ne se contente pas d'une somme arrondie.

- [ ] **Step 1: Écrire les tests rouges de nomenclature et de limites**

```kotlin
@Test
fun `default limits are positive and internally coherent`() {
    val limits = PathOpsLimitsI32()
    assertEquals(32, limits.maxSubdivisionDepth)
    assertEquals(65_536, limits.maxFlattenedEdgesPerOperand)
    assertTrue(limits.maxHalfEdges >= limits.maxVertices * 2)
    assertFailsWith<IllegalArgumentException> {
        PathOpsLimitsI32(maxSubdivisionDepth = 0)
    }
}

@Test
fun `F32 ULP comparison distinguishes the configured boundary`() {
    val one = 1f.toRawBits()
    assertTrue(PathPredicatesF32.almostEqualUlps(1f, Float.fromBits(one + 15)))
    assertFalse(PathPredicatesF32.almostEqualUlps(1f, Float.fromBits(one + 16)))
}
```

- [ ] **Step 2: Écrire les tests rouges de normalisation**

```kotlin
@Test
fun `normalization is translation and scale stable`() {
    val path = PathBuilder().addRect(RectF32.ofLTRB(3_000f, 4_000f, 5_000f, 6_000f)).build()
    val normalization = pathNormalizationF64(listOf(path))
    assertEquals(Point2F64(-0.5, -0.5), normalization.normalize(Point2F32(3_000f, 4_000f)))
    assertEquals(Point2F32(5_000f, 6_000f), normalization.denormalize(Point2F64(0.5, 0.5)))
}

@Test
fun `empty normalization is origin with unit scale`() {
    assertEquals(PathNormalizationF64(Point2F64.Origin, 1.0), pathNormalizationF64(emptyList()))
}
```

- [ ] **Step 3: Écrire les tests rouges du prédicat adaptatif**

```kotlin
@Test
fun `orientation resolves cancellation that rounds the naive determinant`() {
    val n = 134_217_728.0
    val a = Point2F64(0.0, 0.0)
    val b = Point2F64(n + 1.0, n)
    val c = Point2F64(n, n - 1.0)
    assertEquals(-1, OrientationPredicateF64.sign(a, b, c))
    assertEquals(1, OrientationPredicateF64.sign(a, c, b))
}

@Test
fun `on segment is stable after large translation`() {
    val offset = 1.0e12
    assertTrue(
        PathPredicatesF64.onSegment(
            Point2F64(offset + 5.0, offset + 5.0),
            Point2F64(offset, offset),
            Point2F64(offset + 10.0, offset + 10.0),
        ),
    )
}
```

- [ ] **Step 4: Exécuter les tests rouges**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathPredicatesF32Test*' --tests '*PathPredicatesF64Test*' --tests '*OrientationPredicateF64Test*' --tests '*PathNormalizationF64Test*'
```

Expected: compilation failure because the new numeric contracts do not exist.

- [ ] **Step 5: Implémenter les contrats et migrer les consommateurs d'epsilon**

Valider dans `PathOpsLimitsI32.init` que chaque limite est strictement positive; calculer les bounds de normalisation en `Double`, sans addition intermédiaire en `Float`. Répartir l'ancienne API epsilon par famille numérique et migrer `Line2F64` vers `PathPredicatesF64`. Dans `StrokeRectGm`, renommer la constante locale `FLT_EPSILON` en `EPSILON_F32`; ne pas rendre `PathPredicatesF32` public pour ce consommateur de test. Ne conserver aucun alias de compatibilité au nom numérique ambigu.

Pour `OrientationPredicateF64.sign`, calculer d'abord le déterminant rapide et la borne d'erreur `ccwerrboundA = (3.0 + 16.0 * EPSILON_F64) * EPSILON_F64`; invoquer `ExpansionF64` seulement lorsque cette borne ne prouve pas le signe.

- [ ] **Step 6: Vérifier JVM, JS et le consommateur Skia**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :integration-tests:skia:compileTestKotlin
```

Expected: `BUILD SUCCESSFUL` and no remaining reference to `PathOpsEpsilon`, `FLT_EPSILON` or `DBL_EPSILON`.

- [ ] **Step 7: Committer**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/path/StrokeRectGm.kt
rtk git commit -m "refactor(math): name robust path numerics explicitly"
```

---

## Task 2 — Unifier arcs, flattening adaptatif, analyses et mesure

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArcF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathAnalysisF32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathAnalysisF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathMeasureF32Test.kt`

**Interfaces:**

- Consumes: `PathNormalizationF64`, `PathOpsLimitsI32`, `PathPredicatesF32/F64` from Task 1.
- Produces:

```kotlin
internal data class ArcEndpointF64(
    val start: Point2F64,
    val end: Point2F64,
    val radius: Vector2F64,
    val xAxisRotationDegrees: Double,
    val largeArc: Boolean,
    val sweep: Boolean,
)

internal data class ArcCenterF64(
    val center: Point2F64,
    val radiusX: Double,
    val radiusY: Double,
    val rotationRadians: Double,
    val startAngle: Double,
    val sweepAngle: Double,
) {
    fun pointAt(t: Double): Point2F64
    fun derivativeAt(t: Double): Vector2F64
    fun extrema(): List<Point2F64>
}

internal fun arcCenterF64(arc: ArcEndpointF64): ArcCenterF64?

internal data class PathFlatteningPolicyF64(
    val tolerance: Double = 2.0.pow(-23),
    val limits: PathOpsLimitsI32 = PathOpsLimitsI32(),
)

internal data class FlattenedPointF64(
    val point: Point2F64,
    val sourceSegmentIndex: Int,
    val t: Double,
    val originalPointF32: Point2F32?,
)

internal data class FlattenedContourF64(
    val points: List<FlattenedPointF64>,
    val closed: Boolean,
)

internal object PathFlattenerF64 {
    fun flatten(
        normalizedPath: NormalizedPathF64,
        policy: PathFlatteningPolicyF64 = PathFlatteningPolicyF64(),
        closeForFill: Boolean = false,
    ): List<FlattenedContourF64>
}

public data class PathTopologyI32(
    public val contourCount: Int,
    public val closedContourCount: Int,
    public val orientation: ContourOrientation,
    public val inverseFill: Boolean,
)
```

- [ ] **Step 1: Ajouter les tests rouges d'arcs et de `Close`**

Construct a rotated ellipse from two SVG arcs. Its expected half-extents are:

```kotlin
val expectedX = sqrt((radiusX * cos(rotation)).pow(2) + (radiusY * sin(rotation)).pow(2))
val expectedY = sqrt((radiusX * sin(rotation)).pow(2) + (radiusY * cos(rotation)).pow(2))
```

Assert `PathAnalysisF32.bounds` matches these values within two F32 ULPs. Add a path `moveTo → lineTo → close → quadTo` and assert the quad bounds start at the contour origin, not the pre-close endpoint.

- [ ] **Step 2: Ajouter les tests rouges de fill et des détecteurs stricts**

```kotlin
@Test
fun `contains keeps every boundary outside and closes fills implicitly`() {
    val rect = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
    listOf(Point2F32(0f, 5f), Point2F32(10f, 5f), Point2F32(5f, 0f), Point2F32(5f, 10f))
        .forEach { assertFalse(PathAnalysisF32.contains(rect, it)) }

    val openTriangle = PathBuilder().moveTo(10f, 0f).lineTo(0f, 0f).lineTo(0f, 10f).build()
    assertTrue(PathAnalysisF32.contains(openTriangle, Point2F32(8f, 1f)))
    assertEquals(0, PathAnalysisF32.topology(openTriangle).closedContourCount)
}
```

Add negative shape tests: retraced zero-area rect, four-cubic path with noncanonical controls, and line/arc sequence with wrong sweep or rotation must return `null` from `rect`, `oval`, or `rrect`.

- [ ] **Step 3: Ajouter les tests rouges de flattening et mesure**

Construire une cubic et un arc aux échelles `1e-5`, `1` et `1e9`; chaque point aplati doit être fini et l'erreur au milieu de chaque corde, en coordonnées normalisées, doit être au plus `2^-23` :

```kotlin
val policy = PathFlatteningPolicyF64()
listOf(1e-5f, 1f, 1e9f).forEach { scale ->
    val path = curvedPath(scale)
    val normalization = pathNormalizationF64(listOf(path))
    val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, normalization), policy)
    assertTrue(contours.flatMap { it.points }.all { it.point.x.isFinite() && it.point.y.isFinite() })
    assertTrue(maximumNormalizedChordError(path, contours) <= policy.tolerance)
}
```

Vérifier également que le premier et le dernier `FlattenedPointF64` portent les endpoints originaux dans `originalPointF32`, bit pour bit. Tester qu'une grande diagonale finie a une longueur finie, que les tangentes de courbe suivent la dérivée analytique et que `segment(8f, 2f)` est géométriquement égal à `segment(2f, 8f)`.

Dans `PathFlatteningF64Test.kt`, définir `curvedPathF32(scale)` avec une cubic `moveTo(0,0) → cubicTo(0,scale,scale,scale,scale,0)` suivie d'un demi-arc. Définir `maximumNormalizedChordErrorF64` en évaluant le verbe source à `(first.t + second.t) / 2` et en mesurant par hypot stable la distance à la corde normalisée correspondante.

- [ ] **Step 4: Exécuter les tests rouges**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathFlatteningF64Test*' --tests '*PathAnalysisF32Test*' --tests '*PathMeasureF32Test*'
```

Expected: failures for fixed subdivision, sampled arc bounds, boundary handling, loose detectors and reversed intervals.

- [ ] **Step 5: Implémenter `PathArcF64` et le flattening adaptatif**

Partager une conversion endpoint-vers-centre entièrement `F64` entre bounds, flattening et mesure. Construire `ArcEndpointF64` en convertissant endpoints et rayons dans le même domaine; appliquer les corrections de rayon SVG avant les extrema. Utiliser la subdivision récursive De Casteljau pour quads/cubics et l'erreur de flèche pour les arcs, avec hypot stable. Compter arêtes émises et profondeur via `PathOpsLimitsI32`; produire déterministement `IllegalStateException("path-flattening-limit")` ou `IllegalStateException("path-flattening-convergence")`.

- [ ] **Step 6: Corriger l'analyse et la mesure avec le noyau partagé**

Conserver les bounds analytiques des lignes et Bézier, ajouter les extrema analytiques des arcs, restaurer `current = contourStart` sur `Close`, fermer les contours seulement pour les requêtes de fill, rejeter la frontière avant winding/parité et valider intégralement les motifs de formes canoniques. Renommer le résultat public `PathTopology` en `PathTopologyI32` et supprimer l'ancien `FlattenedContour` sans suffixe.

Stocker les longueurs mesurées en `Double`; nommer les records privés `MeasuredSegmentF64` et `MeasuredContourF64`; conserver l'index du segment source et son paramètre depuis `FlattenedPointF64` afin que la tangente utilise la dérivée line/quad/cubic/arc originale. Clamper puis ordonner les distances de segment avant de construire le `PathF32` public.

- [ ] **Step 7: Vérifier JVM et JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

Expected: `BUILD SUCCESSFUL`, with no fixed `16`/`24` curve subdivision remaining.

- [ ] **Step 8: Committer**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArcF64.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathAnalysisF32.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry
rtk git commit -m "refactor(math): make path analysis scale robust"
```

---

## Task 3 — Découper robustement intersections, tangences et recouvrements

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt`

**Interfaces:**

- Consumes: `FlattenedContourF64`, `OrientationPredicateF64`, `PathPredicatesF64`, `PathOpsLimitsI32`.
- Produces:

```kotlin
internal enum class PathOperand { FIRST, SECOND }

internal data class PathInputEdgeF64(
    val id: Int,
    val operand: PathOperand,
    val contourIndex: Int,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
    val start: Point2F64,
    val end: Point2F64,
    val windingDelta: Int,
)

internal sealed interface PathIntersectionF64 {
    data class PointF64(
        val point: Point2F64,
        val firstT: Double,
        val secondT: Double,
    ) : PathIntersectionF64

    data class OverlapF64(
        val start: Point2F64,
        val end: Point2F64,
        val firstRange: ClosedFloatingPointRange<Double>,
        val secondRange: ClosedFloatingPointRange<Double>,
    ) : PathIntersectionF64
}

internal data class PathSplitEdgeF64(
    val sourceId: Int,
    val operand: PathOperand,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
    val start: Point2F64,
    val end: Point2F64,
    val windingDelta: Int,
)

internal data class PathVertexIdentityF64(
    val incidentEdgeIds: List<Int>,
    val parameterByEdgeId: Map<Int, Double>,
    val originalPointF32: Point2F32?,
)

internal fun intersectPathEdgesF64(first: PathInputEdgeF64, second: PathInputEdgeF64): PathIntersectionF64?
internal fun splitPathEdgesF64(edges: List<PathInputEdgeF64>, limits: PathOpsLimitsI32): List<PathSplitEdgeF64>
```

Dans `PathIntersectionsF64Test.kt`, utiliser ces helpers locaux exacts :

```kotlin
private fun inputEdgeF64(
    id: Int,
    start: Point2F64,
    end: Point2F64,
    operand: PathOperand = PathOperand.FIRST,
): PathInputEdgeF64 = PathInputEdgeF64(
    id = id,
    operand = operand,
    contourIndex = 0,
    startIdentity = PathVertexIdentityF64(listOf(id), mapOf(id to 0.0), Point2F32(start.x.toFloat(), start.y.toFloat())),
    endIdentity = PathVertexIdentityF64(listOf(id), mapOf(id to 1.0), Point2F32(end.x.toFloat(), end.y.toFloat())),
    start = start,
    end = end,
    windingDelta = 1,
)

private fun fourEdgesWithThreeDistinctCrossingsF64(): List<PathInputEdgeF64> = listOf(
    inputEdgeF64(0, Point2F64(-2.0, 0.0), Point2F64(2.0, 0.0)),
    inputEdgeF64(1, Point2F64(-1.0, -1.0), Point2F64(-1.0, 1.0)),
    inputEdgeF64(2, Point2F64(0.0, -1.0), Point2F64(0.0, 1.0)),
    inputEdgeF64(3, Point2F64(1.0, -1.0), Point2F64(1.0, 1.0)),
)
```

- [ ] **Step 1: Écrire les tests rouges de croisements et endpoints**

Tester un croisement propre, un endpoint partagé, une jonction en T et quatre arêtes se croisant au même point. Le cas représentatif est :

```kotlin
val first = inputEdgeF64(0, Point2F64(-1.0, -1.0), Point2F64(1.0, 1.0))
val second = inputEdgeF64(1, Point2F64(-1.0, 1.0), Point2F64(1.0, -1.0))
val intersection = assertIs<PathIntersectionF64.PointF64>(intersectPathEdgesF64(first, second))
assertEquals(Point2F64(0.0, 0.0), intersection.point)
assertEquals(0.5, intersection.firstT)
assertEquals(0.5, intersection.secondT)
```

Répéter chaque cas après translations `3_000` et `1e12` en domaine `F64` normalisé; vérifier le même variant d'intersection et les mêmes paramètres à 4 ULP près.

- [ ] **Step 2: Écrire les tests rouges colinéaires et tangentiels**

Tester les segments colinéaires disjoints, le contact en un point, le recouvrement partiel, le recouvrement complet inversé et un segment contenu dans l'autre :

```kotlin
val overlap = assertIs<PathIntersectionF64.OverlapF64>(
    intersectPathEdgesF64(
        inputEdgeF64(0, Point2F64(0.0, 0.0), Point2F64(10.0, 0.0)),
        inputEdgeF64(1, Point2F64(4.0, 0.0), Point2F64(12.0, 0.0)),
    ),
)
assertEquals(Point2F64(4.0, 0.0), overlap.start)
assertEquals(Point2F64(10.0, 0.0), overlap.end)
assertEquals(0.4..1.0, overlap.firstRange)
assertEquals(0.0..0.75, overlap.secondRange)
```

Vérifier que les intervalles sont ordonnés et que le découpage émet chaque sous-arête non nulle une fois par opérande contributeur.

- [ ] **Step 3: Écrire le test rouge de limites**

Créer trois droites sécantes deux à deux afin de dépasser la limite 2 :

```kotlin
val error = assertFailsWith<IllegalStateException> {
    splitPathEdgesF64(fourEdgesWithThreeDistinctCrossingsF64(), PathOpsLimitsI32(maxIntersections = 2))
}
assertEquals("path-intersection-limit", error.message)
```

- [ ] **Step 4: Exécuter les tests rouges**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathIntersectionsF64Test*'
```

Expected: compilation failure because the intersection kernel does not exist.

- [ ] **Step 5: Implémenter les intersections et le découpage canonique**

Utiliser les signes d'orientation robustes pour la classification, calculer en `Double` les paramètres des croisements propres, ordonner les intervalles projetés des recouvrements colinéaires et réutiliser l'identité d'un endpoint lorsqu'un paramètre vaut 0 ou 1. Pour un événement, former un profil entrant atomique, parcourir d'abord le witness exact multi-valeur puis fusionner les `2 × (31 × 16)` flux directs AVL; un marqueur persistant par événement déduplique l'action sans masquer les pops bruts. La clé de heap/AVL est un rang de naissance immuable et de taille constante, dérivé de rangs d'arêtes pré-calculés, paramètres, point canonique et witness : elle ne parcourt jamais les incidences d'une composante. Fermer les candidats contre l'accumulateur éphémère avant une mutation persistante unique. Avant de construire cet accumulateur, une concurrence exacte répétée ne fait un no-op qu'après avoir épuisé le domaine candidat, prouvé qu'il ne possède qu'un candidat éligible et vérifié son witness/point canonique et les deux incidences entrantes. La clé de choix est géométrique/provenance canonique, jamais un ID source; normaliser `-0.0` dans toutes les décisions topologiques et les points `F64` émis, mais conserver les bits de provenance `originalPointF32`; conserver operand, contour, winding, endpoints et sens, et traiter les égalités de clé comme un lot automorphe. Un témoin exact égal est plus fort qu'une compatibilité ULP; résoudre son conflit de paramètre par endpoint, puis paramètre/clé canoniques. Compter avant action chaque pop brut (doublons inclus), chaque inspection/comparaison/copie d'incidence — commune ou non — et chaque mise à jour de profil/index contre `maxCandidateProbes`, qui échoue par `path-candidate-limit`. La recherche de candidats est `O(log C + b_j)` grâce aux 993 flux fixes et au comparateur `O(1)` en degré; le filtrage/fusion/commit a son coût explicite débité `q_j`. L'état persistant est `O(E + I + C)`; la mémoire temporaire est le heap fixe, `k` candidats acceptés amortis par les suppressions destructives sauf le winner, et un accumulateur de `O(min(I, R_j))` incidences, avec `R_j` le budget restant à sa construction. L'identité finale contient les ids d'arêtes incidentes triés et un paramètre canonique par arête; elle réunit ainsi un croisement à quatre arêtes sans clustering spatial global. Ne jamais employer de coordonnées arrondies en string ni de clés de coordonnées `Int`.

- [ ] **Step 6: Vérifier JVM et JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

- [ ] **Step 7: Committer**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt
rtk git commit -m "feat(math): split robust path intersections"
```

---

## Task 4 — Construire l'arrangement à demi-arêtes et propager les winding

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathArrangementF64Test.kt`

**Interfaces:**

- Consumes: `PathSplitEdgeF64`, `PathOperand`, `PathOpsLimitsI32`, `FillRule`, `PathBooleanOp`.
- Produces:

```kotlin
internal data class PathVertexF64(
    val id: Int,
    val point: Point2F64,
    val originalPointF32: Point2F32?,
)

internal data class PathEdgeF64(
    val startVertexId: Int,
    val endVertexId: Int,
    val firstWindingDelta: Int,
    val secondWindingDelta: Int,
)

internal data class PathContourVertexF64(
    val point: Point2F64,
    val originalPointF32: Point2F32?,
)

internal data class PathContourF64(val vertices: List<PathContourVertexF64>)

internal data class PathHalfEdgeF64(
    val id: Int,
    val originVertexId: Int,
    val twinId: Int,
    val nextId: Int,
    val leftFaceId: Int,
    val firstWindingDelta: Int,
    val secondWindingDelta: Int,
)

internal data class PathFaceI32(
    val id: Int,
    val boundaryHalfEdgeIds: List<Int>,
    val firstWinding: Int,
    val secondWinding: Int,
)

internal class PathArrangementF64 private constructor(
    private val vertices: List<PathVertexF64>,
    private val halfEdges: List<PathHalfEdgeF64>,
    private val faces: List<PathFaceI32>,
) {
    fun boundary(
        firstFillRule: FillRule,
        secondFillRule: FillRule,
        operation: PathBooleanOp,
    ): List<PathContourF64>

    fun unaryBoundary(fillRule: FillRule): List<PathContourF64>

    companion object {
        fun build(edges: List<PathSplitEdgeF64>, limits: PathOpsLimitsI32): PathArrangementF64
    }
}
```

`PathHalfEdgeF64` et `PathFaceI32` restent internes au fichier.

Dans `PathArrangementF64Test.kt`, définir `closedContourEdgesF64(points, operand)` en attribuant à chaque coin une `PathVertexIdentityF64` partagée entre ses deux arêtes incidentes, puis `projectContoursF64ToPathF32(contours)` par `moveTo`, `lineTo` et `close` sur `PathContourF64.vertices`. `squareWithHoleEdgesF64()` concatène le carré horaire `(0,0)→(10,0)→(10,10)→(0,10)` et le trou antihoraire `(3,3)→(3,7)→(7,7)→(7,3)`.

- [ ] **Step 1: Écrire les tests rouges de cycles et winding**

Construire les arêtes d'un carré horaire, d'un carré avec trou antihoraire, de deux carrés identiques de même direction et de deux carrés identiques de directions opposées. Projeter les contours retournés vers un `PathF32` de test, puis vérifier l'appartenance et l'orientation géométrique :

```kotlin
val contours = PathArrangementF64.build(squareWithHoleEdgesF64(), PathOpsLimitsI32())
    .unaryBoundary(FillRule.WINDING)
val result = projectContoursF64ToPathF32(contours)
assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 1f)))
assertFalse(PathAnalysisF32.contains(result, Point2F32(5f, 5f)))
assertTrue(signedAreaF64(contours.first().vertices.map { it.point }) > 0.0) // externe horaire en repère écran
assertTrue(signedAreaF64(contours.last().vertices.map { it.point }) < 0.0)  // trou antihoraire
```

- [ ] **Step 2: Écrire les tests rouges des cinq tables de vérité**

Utiliser deux triangles obliques superposés. Pour chaque `PathBooleanOp`, interroger une grille contenant des points first-only, second-only, overlap et outside :

```kotlin
PathBooleanOp.entries.forEach { operation ->
    val result = projectContoursF64ToPathF32(arrangement.boundary(FillRule.WINDING, FillRule.WINDING, operation))
    probes.forEach { probe ->
        assertEquals(expectedMembership(operation, probe.inFirst, probe.inSecond), PathAnalysisF32.contains(result, probe.point))
    }
}
```

Répéter avec les orientations des deux opérandes inversées.

- [ ] **Step 3: Écrire les tests rouges de tangence et colinéarité**

Tester des carrés qui se touchent en un point, partagent une arête, se recouvrent sur une partie d'arête ou forment une jonction en T. Ne pas inspecter les nœuds ou demi-arêtes internes : projeter le résultat et vérifier uniquement l'appartenance sur les deux côtés du contact, les bounds et l'aire orientée non nulle de chaque contour retourné.

- [ ] **Step 4: Exécuter les tests rouges**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathArrangementF64Test*'
```

Expected: compilation failure because the half-edge arrangement does not exist.

- [ ] **Step 5: Implémenter le graphe à demi-arêtes**

Réutiliser les identités d'endpoints et d'intersections canoniques de Task 3. Fusionner uniquement les sommets dont ces identités prouvent l'égalité. Émettre deux demi-arêtes par arête géométrique, agréger les contributions winding des arêtes coïncidentes, trier les demi-arêtes sortantes par quadrant et orientation robustes, lier les suivantes, puis énumérer cycles et faces.

- [ ] **Step 6: Propager winding from the external face**

Identifier la face non bornée de chaque composante par l'orientation du cycle et un sommet extrémal. Raccorder les composantes disjointes dans une forêt de confinement avec un témoin issu d'un sommet extrémal et de son secteur incident certifié; les racines partent de winding nuls et chaque composante imbriquée hérite des winding de sa face conteneur. Parcourir ensuite l'adjacence des faces : traverser une demi-arête met à jour les winding du premier et du second opérande par ses contributions orientées. Évaluer `WINDING` par non-zéro et `EVEN_ODD` par parité. Sélectionner les frontières dont les faces adjacentes diffèrent selon la table de vérité de l'opération. Rejeter toute propagation incohérente par `IllegalStateException("path-arrangement-inconsistent")`.

- [ ] **Step 7: Canonicaliser les contours `F64`**

Remove adjacent duplicates and provably collinear middle vertices, reject open cycles, orient external contours clockwise and holes counter-clockwise, and order contours deterministically by descending absolute area then lexicographic first vertex.

- [ ] **Step 8: Vérifier JVM et JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

- [ ] **Step 9: Committer**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathArrangementF64Test.kt
rtk git commit -m "feat(math): build winding-aware path arrangement"
```

---

## Task 5 — Brancher `PathOpsF32`, `simplify` et `asWinding`

**Files:**

- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`

**Interfaces:**

- Consumes: Tasks 1–4 public/internal interfaces.
- Produces unchanged public entry points:

```kotlin
public object PathOpsF32 {
    public fun op(first: PathF32, second: PathF32, op: PathBooleanOp): PathF32
    public fun simplify(path: PathF32): PathF32
    public fun asWinding(path: PathF32): PathF32

    internal fun op(
        first: PathF32,
        second: PathF32,
        op: PathBooleanOp,
        limits: PathOpsLimitsI32,
    ): PathF32

    internal fun simplify(path: PathF32, limits: PathOpsLimitsI32): PathF32
    internal fun asWinding(path: PathF32, limits: PathOpsLimitsI32): PathF32
}
```

`PathBehaviorTestSupportF32.kt` produit les helpers comportementaux partagés par Tasks 5 et 6 :

```kotlin
internal data class AffineTransformF32(val scale: Float, val translateX: Float, val translateY: Float)

internal data class PathOpCaseF32(
    val name: String,
    val first: PathF32,
    val second: PathF32,
    val probes: List<Point2F32>,
)

internal fun transformPointF32(point: Point2F32, transform: AffineTransformF32): Point2F32
internal fun transformPathF32(path: PathF32, transform: AffineTransformF32): PathF32
internal fun probeGridF32(bounds: RectF32, steps: Int): List<Point2F32>
internal fun expectedMembership(operation: PathBooleanOp, inFirst: Boolean, inSecond: Boolean): Boolean
internal fun pathOpCasesF32(): List<PathOpCaseF32>
internal fun assertMembershipEquivalentF32(expected: PathF32, actual: PathF32, probes: List<Point2F32>)
internal fun assertMetamorphicMembershipF32(
    case: PathOpCaseF32,
    operation: PathBooleanOp,
    transforms: List<AffineTransformF32>,
)
```

`transformPathF32` réémet chaque verbe avec endpoints/contrôles transformés; les rayons d'arc sont multipliés par `abs(scale)`, rotation et flags sont conservés. `pathOpCasesF32()` construit exactement les cinq familles suivantes : ovals canoniques tangents `[0,0,10,10]`/`[10,0,20,10]`, rectangles colinéaires `[0,0,10,10]`/`[5,0,15,10]`, triangles obliques superposés, donut imbriqué et bow-ties auto-intersectés. Chaque cas utilise `probeGridF32` sur ses bounds combinées étendues de 2 unités.

- [ ] **Step 1: Écrire les tests rouges des cinq opérations générales**

Pour des triangles superposés, un polygone concave, un donut, des contours dupliqués et un bow-tie auto-intersecté, vérifier les cinq opérations via `PathAnalysisF32.contains` sur des grilles de points :

```kotlin
PathBooleanOp.entries.forEach { operation ->
    val result = PathOpsF32.op(first, second, operation)
    probeGridF32(bounds = RectF32.ofLTRB(-2f, -2f, 22f, 22f), steps = 25).forEach { point ->
        val expected = expectedMembership(
            operation,
            PathAnalysisF32.contains(first, point),
            PathAnalysisF32.contains(second, point),
        )
        assertEquals(expected, PathAnalysisF32.contains(result, point), "$operation at $point")
    }
}
```

Ajouter les identités `A union A = A`, `A intersect A = A`, `A xor A = empty` et `A - B = reverseDifference(B, A)` par équivalence d'appartenance.

- [ ] **Step 2: Écrire les tests rouges de translation et échelle**

Utiliser les couples représentables `(scale=1e-5, translation=0)`, `(scale=1, translation=3_000)` et `(scale=1_000, translation=-1_000_000)`. Transformer opérandes et points de requête ensemble :

```kotlin
transforms.forEach { transform ->
    PathBooleanOp.entries.forEach { operation ->
        val base = PathOpsF32.op(first, second, operation)
        val transformed = PathOpsF32.op(transformPathF32(first, transform), transformPathF32(second, transform), operation)
        probes.forEach { point ->
            assertEquals(
                PathAnalysisF32.contains(base, point),
                PathAnalysisF32.contains(transformed, transformPointF32(point, transform)),
            )
        }
    }
}
```

Ajouter un cas fini autour de `1e30f`, avec des séparations supérieures à l'ULP `F32` locale.

- [ ] **Step 3: Écrire les tests rouges de `simplify` et `asWinding`**

```kotlin
@Test
fun `asWinding preserves even odd holes and duplicate cancellation`() {
    val source = PathBuilder(FillRule.EVEN_ODD)
        .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
        .addRect(RectF32.ofLTRB(5f, 5f, 15f, 15f))
        .build()
    val winding = PathOpsF32.asWinding(source)
    assertEquals(FillRule.WINDING, winding.fillRule)
    assertTrue(PathAnalysisF32.contains(winding, Point2F32(2f, 2f)))
    assertFalse(PathAnalysisF32.contains(winding, Point2F32(10f, 10f)))
}
```

Add the same invariant for `INVERSE_EVEN_ODD`, expecting `INVERSE_WINDING`, and for two identical contours where even-odd membership is empty.

- [ ] **Step 4: Écrire les tests rouges de refus et de limites**

Vérifier que les opérations binaires refusent coordonnées non finies et inverse fills par `IllegalArgumentException`. Avec la surcharge interne acceptant `PathOpsLimitsI32`, épuiser séparément flattening, intersections, sommets et demi-arêtes :

```kotlin
val sourceBefore = first.toList()
val error = assertFailsWith<IllegalStateException> {
    PathOpsF32.op(first, second, PathBooleanOp.UNION, PathOpsLimitsI32(maxVertices = 2))
}
assertEquals("path-vertex-limit", error.message)
assertEquals(sourceBefore, first.toList())
```

Répéter avec les messages `path-flattening-limit`, `path-intersection-limit` et `path-half-edge-limit`; aucun path partiel ne doit s'échapper.

- [ ] **Step 5: Exécuter les tests rouges**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test*'
```

Expected: failures from the old string-key arrangement and rule-only `asWinding`.

- [ ] **Step 6: Réécrire l'adaptateur public**

Valider les entrées, calculer une normalisation combinée, aplatir les opérandes avec `closeForFill = true`, créer les arêtes et leurs contributions winding, les découper, construire l'arrangement, appliquer l'opération demandée puis projeter les contours canoniques vers `PathF32(FillRule.WINDING)`.

Pendant la projection, supprimer les sommets adjacents devenus identiques en `F32`, puis recalculer aire et orientation. Supprimer un cycle dégénéré seulement si son aire absolue `F64` normalisée était au plus `tolerance²`; sinon échouer par `IllegalStateException("path-f32-projection-collapse")`, car une frontière topologiquement significative a disparu.

Use the unary route for `simplify` and `asWinding`; preserve inverse state as specified. Retain the rectangle fast path only after strict canonical recognition and verify it against the general route in tests.

- [ ] **Step 7: Supprimer l'ancien arrangement**

Remove `Edge`, `pointKey`, fixed absolute epsilons, pairwise midpoint side sampling and string-key reconstruction from `PathOpsF32.kt`. No replacement may use coordinate-to-`Int` keys.

- [ ] **Step 8: Vérifier JVM et JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

- [ ] **Step 9: Committer**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt
rtk git commit -m "refactor(math): route path ops through robust topology"
```

---

## Task 6 — Fermer la matrice métamorphique et la preuve Task 5

**Files:**

- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsMetamorphicF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RegionF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathAnalysisF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathMeasureF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`

**Interfaces:**

- Consumes: completed public behavior from Tasks 1–5.
- Produces: final behavioral proof closing all eight findings; no new production API.

- [ ] **Step 1: Ajouter la matrice métamorphique des cinq opérations**

Pour chaque opération et chaque translation/échelle représentable de Task 5, comparer l'appartenance sur une grille 21×21 après transformation affine simultanée des paths et des points. Réutiliser cette table de cas :

```kotlin
val transforms = listOf(
    AffineTransformF32(1e-5f, 0f, 0f),
    AffineTransformF32(1f, 3_000f, 3_000f),
    AffineTransformF32(1_000f, -1_000_000f, -1_000_000f),
)
pathOpCasesF32().forEach { case ->
    PathBooleanOp.entries.forEach { operation ->
        assertMetamorphicMembershipF32(case, operation, transforms)
    }
}
```

Ne pas vérifier le nombre ou la classe des nœuds du graphe interne.

- [ ] **Step 2: Ajouter les invariants algébriques et winding**

Vérifier la commutativité de `UNION`, `INTERSECT`, `XOR`, l'idempotence, la dualité des différences, l'intersection disjointe vide et la contenance de l'union. Pour chaque fill rule finie, comparer `simplify` et `asWinding` par appartenance; pour les conversions unaires inverses, attendre explicitement `INVERSE_WINDING` et vérifier que les points de frontière restent dehors.

- [ ] **Step 3: Compléter analyses, mesure et immutabilité de `RegionF32`**

Add rotated arc bounds for every large/sweep flag combination, multi-contour bounds, open-fill closure, all four boundaries, negative detectors, curve derivative tangents, reversed/out-of-range segments and multiple contours. For `RegionF32`, mutate every source `RectF32` after construction and every returned rectangle after access; the region's membership and bounds must remain unchanged.

- [ ] **Step 4: Vérifier les limites sans test d'infrastructure**

Use the internal limits overload to create geometries that exceed one budget at a time; assert only the public exception type/message and that both source paths remain unchanged. Do not inspect internal collections or counters.

- [ ] **Step 5: Exécuter les tests ciblés**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsMetamorphicF32Test*' --tests '*PathOpsF32Test*' --tests '*PathAnalysisF32Test*' --tests '*PathMeasureF32Test*' --tests '*RegionF32Test*'
```

Expected: all tests pass.

- [ ] **Step 6: Exécuter la matrice KMP complète**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :integration-tests:skia:compileTestKotlin
rtk git diff --check
```

Expected: `BUILD SUCCESSFUL` for JVM, JS and the Skia consumer; no whitespace errors.

- [ ] **Step 7: Committer**

```bash
rtk git add math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry
rtk git commit -m "test(math): prove robust path topology across scales"
```

## Definition of Done

- Les huit findings de la review `task-5-review.md` sont fermés par comportement.
- Aucun type numérique nouvellement introduit n'omet `F32`, `F64`, `I32` ou `I64`.
- `PathTopology` et les records génériques de flattening/mesure de l'implémentation initiale ont disparu au profit de noms `I32`, `F32` ou `F64` explicites.
- Aucun identifiant de sommet n'est dérivé d'une coordonnée quantifiée en `Int` ou en string.
- Les bounds d'arcs sont analytiques et `Close` rétablit le point courant.
- `contains` traite symétriquement les quatre bords et ferme implicitement les contours de fill.
- `PathMeasureF32` utilise longueur `F64`, flattening adaptatif, dérivée source et intervalles inversés.
- Les cinq opérations, `simplify` et `asWinding` passent la matrice de trous, tangences, colinéarité, auto-intersections, translations et échelles.
- JVM, JS et le consommateur Skia compilent et testent avec succès.
- Une review Sol indépendante rend `Spec: PASS` et `Quality: PASS` avant reprise de Task 6 du plan W0–W2.
