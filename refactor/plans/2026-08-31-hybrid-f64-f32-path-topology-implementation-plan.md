# Hybrid F64/F32 Path Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to implement this plan task by task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer la projection/compaction tardive de `PathOpsF32` par un
arrangement topologique hybride F64/F32 unique qui conserve la provenance,
accepte les coïncidences F32 locales prouvées et rejette toute ambiguïté sans
modifier silencieusement le fill.

**Architecture:** Le flattening et les intersections conservent des spans et
witnesses F64 exacts, puis choisissent leurs représentants F32 avant le winding.
Un seul DCEL hybride utilise les directions F64 et l'embedding F32, extrait des
traces de demi-arêtes avec provenance et les émet directement dans `PathBuilder`.
Il n'existe plus de compaction, de corde reconstruite ni de validation
topologique postérieure au writer.

**Tech Stack:** Kotlin 2.x Multiplatform, `commonMain`/`commonTest`, primitives
`Point2F32`/`Point2F64` de `:math:geometry`, `Vector2F64` de `:math:vector`,
prédicats adaptatifs existants, Gradle JVM/JS et intégration Skia.

**Spec:**
[`refactor/specs/2026-08-31-hybrid-f64-f32-path-topology-design.md`](../specs/2026-08-31-hybrid-f64-f32-path-topology-design.md)

## Global Constraints

- Toute la nouvelle géométrie appartient à `:math:geometry`; aucune façade
  Kanvas, font ou codec n'est modifiée.
- Toute valeur numérique nouvelle porte `F32`, `F64`, `I32` ou `I64` dans son
  type et son nom; aucun `Point`, `Edge`, `Span`, `Tolerance` ou `Epsilon`
  générique n'est introduit.
- `PathF32`, `PathOpsF32`, `PathAnalysisF32`, `PathMeasureF32` et leurs
  signatures publiques restent inchangés.
- Les intersections, paramètres, directions, tangentes et preuves restent en
  F64. Les représentants F32 participent à la topologie avant winding.
- Un représentant est choisi uniquement parmi les évaluations F32 des spans
  incidents. Aucun snapping vers un voisin ULP, epsilon ou point synthétique
  n'est autorisé.
- Un alias relie uniquement des incidences du même witness exact. Une égalité
  F32, une paire de contours ou une chaîne transitive n'est jamais une preuve.
- Un `OverlapF64` reste exact. Un `PointF64` peut produire seulement une
  coïncidence F32 locale sur les deux spans directement incidents; elle ne peut
  franchir ni witness, ni seam, ni intervalle de provenance.
- Les claims d'un witness n-way sont atomiques. Deux witnesses peuvent
  réclamer des sous-intervalles disjoints d'un span, jamais des intérieurs qui
  se chevauchent.
- Un crossing ou overlap F32 sans preuve locale unique échoue par
  `IllegalStateException("path-f32-projection-collapse")` avant toute émission.
- Un contour entièrement effondré peut devenir `Drop` si sa double-aire exacte
  est `<= 2^-45`; une valeur strictement supérieure ou un contour conservé qui
  dépend d'une incidence effondrée devient `Reject`.
- Les erreurs publiques exactes restent `path-f32-projection-collapse`,
  `path-candidate-limit`, `path-intersection-limit`, `path-vertex-limit` et
  `path-half-edge-limit`. Aucune assertion, `checkNotNull` ou erreur Kotlin
  générique ne franchit l'API.
- Une seule instance de `PathCandidateWorkBudgetI32` traverse intersections,
  conflits F32, claims, arrangement, canonicalisation et writer. Chaque visite,
  comparaison, copie, prédicat, terme d'aire, claim et comparaison de tri est
  débité avant action.
- Chaque nouveau groupe canonique de contact/coupe F32 consomme
  `maxIntersections`; un groupe n-way compte une fois et un endpoint exact déjà
  identifié reste un no-op pour cette limite.
- `maxHalfEdges` est vérifié avant allocation importante. Booth conserve sa
  borne canonique `3n`. Le registre de claims et la reconstruction restent
  linéaires.
- Pour `S` spans et `K` conflits candidats, la route reste
  `O(S log S + K)` hors coût des prédicats adaptatifs, avec une mémoire
  `O(S + K)` bornée par les limites existantes.
- JVM et JS utilisent les mêmes ordres sémantiques. Le zéro signé est
  canonicalisé uniquement pour la topologie; la provenance F32 originale
  retenue est réémise bit-à-bit.
- Tous les nouveaux tests sont comportementaux, géométriques ou numériques
  dans `commonTest`. Aucun test ne lit sources, packages, imports, noms privés,
  nombre de nœuds, collections ou forme interne du graphe.
- Les documents de suivi et de review sont écrits sous
  `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/`, jamais sous
  `.superpowers/`.
- Les GMs déjà classées non rendables restent exclues du dénominateur. Cette
  boucle n'ajoute aucune exclusion et n'active pas les GMs `BLOCKING`.
- Toutes les commandes shell sont préfixées par `rtk`; toute édition manuelle
  utilise `apply_patch`.

---

## Agent and review protocol

Pour chaque task :

1. dispatcher un nouvel agent Terra pour l'implémentation TDD ;
2. faire produire un commit stable et un rapport sous
   `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/` ;
3. dispatcher un nouvel agent Sol uniquement pour la conformité à la spec ;
4. après `Spec: PASS`, dispatcher un autre agent Sol pour la qualité ;
5. confier toute correction à un nouvel agent Terra, puis recommencer les deux
   gates Sol sur le nouveau commit.

Un implementer ne review pas son propre travail. Un reviewer ne modifie aucun
fichier. La task suivante ne commence qu'après `Spec: PASS` et `Quality: PASS`.

---

## File map

### Source topology and projected contacts

- Create:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
  — localisations, spans, witnesses exacts et résultat du split.
- Create:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
  — représentants, aliases, conflits, claims, coïncidences et incidences
  effondrées.
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
  — provenance paramétrique et seam explicite.
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
  — produire `PathSourceTopologyF64` au lieu de perdre witnesses et intervalles.

### Hybrid arrangement and output

- Create:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
  — DCEL hybride, winding, faces et traces.
- Create:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBoundaryWriterF32.kt`
  — `Drop`/`Keep`/`Reject`, canonicalisation et émission directe.
- Delete after migration:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt`.
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
  — orchestration unique et suppression de toute la projection tardive.

### Behavioral proof

- Create:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`.
- Create:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsBudgetF32Test.kt`.
- Create:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathHybridBehaviorTestSupportF64.kt`
  — injecteur géométrique de contours normalisés utilisé uniquement par les
  trois reproductions de review; ses oracles restent des `PathF32`.
- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`.
- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`.
- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt`.
- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt`.
- Delete after equivalent behavioral coverage:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathArrangementF64Test.kt`.

### Tracking

- Create:
  `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`.
- Create one `task-N-report.md`, `task-N-spec-review.md` and
  `task-N-quality-review.md` in that directory per gate.

---

## Task 1 — Preserve source spans and close the unsafe-compaction regressions

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`

**Interfaces:**

- Consumes: `FlattenedContourF64`, `PathInputEdgeF64`,
  `PathVertexIdentityF64`, `PathIntersectionF64`, `PathCandidateWorkBudgetI32`.
- Produces:

```kotlin
internal data class PathSourceLocationF64(
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
    val originalPointF32: Point2F32?,
    val vertexIdentityF64: PathVertexIdentityF64?,
)

internal data class PathFlattenedSectionF64(
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

internal data class PathSourceSpanF64(
    val sourceSpanIdI64: Long,
    val operand: PathOperand,
    val contourIndexI32: Int,
    val startLocationF64: PathSourceLocationF64,
    val endLocationF64: PathSourceLocationF64,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val flattenedSectionsF64: List<PathFlattenedSectionF64>,
    val windingDeltaI32: Int,
)

internal sealed interface PathContactWitnessF64 {
    data class PointF64(
        val vertexIdentityF64: PathVertexIdentityF64,
        val pointF64: Point2F64,
        val incidentSourceSpanIdsI64: List<Long>,
    ) : PathContactWitnessF64

    data class OverlapF64(
        val startVertexIdentityF64: PathVertexIdentityF64,
        val endVertexIdentityF64: PathVertexIdentityF64,
        val firstSourceSpanIdsI64: List<Long>,
        val secondSourceSpanIdsI64: List<Long>,
        val firstStartParameterF64: Double,
        val firstEndParameterF64: Double,
        val secondStartParameterF64: Double,
        val secondEndParameterF64: Double,
    ) : PathContactWitnessF64
}

internal data class PathSourceTopologyF64(
    val sourceSpansF64: List<PathSourceSpanF64>,
    val contactWitnessesF64: List<PathContactWitnessF64>,
)

internal data class PathInputEdgeF64(
    val idI32: Int,
    val operand: PathOperand,
    val contourIndexI32: Int,
    val sourceSegmentIndexI32: Int,
    val sourceStartParameterF64: Double,
    val sourceEndParameterF64: Double,
    val startIdentityF64: PathVertexIdentityF64,
    val endIdentityF64: PathVertexIdentityF64,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val windingDeltaI32: Int,
)

internal fun splitPathSourceTopologyF64(
    edgesF64: List<PathInputEdgeF64>,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceTopologyF64
```

- During this task only, an adapter may map `sourceSpansF64` back to
  `PathSplitEdgeF64` for the old arrangement. It must be marked for deletion in
  Task 3 and must never reconstruct provenance from coordinates.

- [ ] **Step 1: Add the three red behavioral regressions from re-review 5**

Add tests whose assertions are only emitted `PathF32` membership and exact
public errors. Reuse the exact normalized coordinates below in the existing
projection fixture during this transition; do not inspect lists of graph
objects or node counts.

```kotlin
private val identityNormalizationF64 =
    PathNormalizationF64(origin = Point2F64(0.0, 0.0), scale = 1.0)

private fun normalizedContourF64(vararg coordinatesF64: Pair<Double, Double>): PathContourF64 =
    PathContourF64(
        coordinatesF64.map { (xF64, yF64) ->
            PathContourVertexF64(Point2F64(xF64, yF64), originalPointF32 = null)
        },
    )

private fun projectOneF64(contourF64: PathContourF64): PathF32 =
    projectContoursF64ToPathF32(listOf(contourF64), identityNormalizationF64, FillRule.WINDING)

private fun projectTogetherF64(vararg contoursF64: PathContourF64): PathF32 =
    projectContoursF64ToPathF32(contoursF64.toList(), identityNormalizationF64, FillRule.WINDING)

private fun projectUnderThresholdWitnessFixtureF32(): PathF32 {
    val scaleF64 = 1.0e-8
    val tinyF64 = 1.0e-46
    val runF64 = normalizedContourF64(
        0.0 to 0.0,
        scaleF64 to tinyF64,
        2.0 * scaleF64 to -tinyF64,
        2.0 * scaleF64 to scaleF64,
    )
    val touchF64 = normalizedContourF64(
        0.0 to 0.0,
        -scaleF64 to scaleF64,
        -scaleF64 to -scaleF64,
    )
    return projectTogetherF64(runF64, touchF64)
}

@Test
fun `single source witness cannot erase either significant region`() {
    val e = 2.0.pow(-25)
    val lower = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 - e, 2.0 to 1.0 - e / 2.0,
        2.0 to -1.0, 0.0 to -1.0,
    )
    val upper = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0 + e / 2.0,
        2.0 to 3.0, 0.0 to 3.0,
    )
    assertTrue(PathAnalysisF32.contains(projectOneF64(lower), Point2F32(1f, .5f)))
    assertTrue(PathAnalysisF32.contains(projectOneF64(upper), Point2F32(1f, 1.5f)))
    val error = assertFailsWith<IllegalStateException> { projectTogetherF64(lower, upper) }
    assertEquals("path-f32-projection-collapse", error.message)
}

@Test
fun `distinct witnesses cannot consume one another`() {
    val e = 2.0.pow(-25)
    val main = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0, 3.0 to 1.0 - e,
        3.0 to -1.0, 1.5 to -2.0, 0.0 to -1.0,
    )
    val firstTouch = normalizedContourF64(0.0 to 1.0, -0.4 to 2.0, 0.4 to 2.0)
    val secondTouch = normalizedContourF64(2.0 to 1.0, 1.6 to 2.0, 2.4 to 2.0)
    assertTrue(PathAnalysisF32.contains(projectOneF64(main), Point2F32(1.5f, 0f)))
    val error = assertFailsWith<IllegalStateException> {
        projectTogetherF64(main, firstTouch, secondTouch)
    }
    assertEquals("path-f32-projection-collapse", error.message)
}

@Test
fun `under threshold collapse never leaks a generic Kotlin error`() {
    val result = projectUnderThresholdWitnessFixtureF32()
    assertTrue(PathAnalysisF32.contains(result, Point2F32(-0.5e-8f, 0f)))
}
```

`projectOneF64`, `projectTogetherF64` and
`projectUnderThresholdWitnessFixtureF32` remain private test helpers. They
exercise the production pipeline and return only `PathF32`; they do not expose
or assert an internal collection. Task 4 migrates them to the trace writer and
removes the old projection entry point.

- [ ] **Step 2: Run the new tests on both backends and record the red evidence**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

Expected now: the first two tests fail because membership is erased; the third
fails with `Required value was null.`. Copy the exact evidence into
`progress.md`.

- [ ] **Step 3: Propagate source segment and parameter provenance**

Rename `FlattenedPointF64.sourceSegmentIndex` to `sourceSegmentIndexI32` and
`t` to `parameterF64`. Give implicit close-for-fill edges a seam location whose
segment index is `-1`, with the original contour endpoint retained. Extend
`PathInputEdgeF64` with the source segment index and the two source parameters.

When a cut parameter `edgeParameterF64` splits a flattened edge, compute the
source parameter without reevaluating a coordinate:

```kotlin
private fun sourceParameterAtEdgeCutF64(
    edgeF64: PathInputEdgeF64,
    edgeParameterF64: Double,
): Double = edgeF64.sourceStartParameterF64 +
    (edgeF64.sourceEndParameterF64 - edgeF64.sourceStartParameterF64) * edgeParameterF64
```

Build every `PathFlattenedSectionF64` directly from canonical cuts, then merge
contiguous sections of the same source segment between two exact events into
one `PathSourceSpanF64`. A flattening subdivision alone never creates a witness
or a new span authority. Build
`PathContactWitnessF64` from the exact intersection component and its incident
spans, never by comparing projected coordinates. Assign deterministic I64 IDs
after semantic sorting; IDs must not break geometric ties.

- [ ] **Step 4: Remove unsafe compaction authority while the adapter remains**

Disable `compactProjectedPointWitnessRunsF64` as an authority. Until the hybrid
DCEL lands, any projected overlap supported only by a `PointF64`, any overlap of
claims from different witnesses and any run that would remove a witness must
fail with `path-f32-projection-collapse`. Replace the nullable `checkNotNull`
path by an explicit `Drop` propagation so a permitted collapsed contour is
omitted without losing sibling contours.

- [ ] **Step 5: Verify task behavior and existing numeric kernels**

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' \
  --tests '*PathFlatteningF64Test*' \
  --tests '*PathIntersectionsF64Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
rtk git diff --check
```

Expected: all tests pass; the two adversarial fixtures reject stably; the
under-threshold fixture returns a valid path; inputs remain unchanged.

- [ ] **Step 6: Commit Task 1**

```bash
rtk git add -- \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt \
  refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git commit -m "refactor(math): preserve path source topology"
```

---

## Task 2 — Build projected contacts and the hybrid arrangement

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`

**Interfaces:**

- Consumes: `PathSourceTopologyF64`, `PathNormalizationF64`, existing robust
  predicates and deterministic AABB broad phase.
- Produces:

```kotlin
internal data class PathHybridVertexF64F32(
    val sourcePointF64: Point2F64,
    val representativePointF32: Point2F32,
    val originalPointF32: Point2F32?,
    val vertexIdentityF64: PathVertexIdentityF64,
    val incidentSourceSpanIdsI64: List<Long>,
    val contactWitnessF64: PathContactWitnessF64?,
)

internal data class PathAliasGroupF32(
    val representativePointF32: Point2F32,
    val vertexIdentitiesF64: List<PathVertexIdentityF64>,
    val contactWitnessF64: PathContactWitnessF64,
)

internal data class PathProjectedSpanClaimF64(
    val sourceSpanIdI64: Long,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

internal data class PathProjectedCoincidenceF32(
    val projectedCoincidenceIdI64: Long,
    val pointWitnessF64: PathContactWitnessF64.PointF64,
    val firstSourceSpanIdI64: Long,
    val secondSourceSpanIdI64: Long,
    val startPointF32: Point2F32,
    val endPointF32: Point2F32,
    val firstClaimF64: PathProjectedSpanClaimF64,
    val secondClaimF64: PathProjectedSpanClaimF64,
)

internal data class PathCollapsedIncidenceF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val hybridVertexF64F32: PathHybridVertexF64F32,
    val incomingDirectionF64: Vector2F64,
    val outgoingDirectionF64: Vector2F64,
)

internal data class PathHybridTopologyF64F32(
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val sourceSpansF64: List<PathSourceSpanF64>,
    val aliasGroupsF32: List<PathAliasGroupF32>,
    val projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
)

internal fun buildPathHybridTopologyF64F32(
    sourceTopologyF64: PathSourceTopologyF64,
    normalizationF64: PathNormalizationF64,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathHybridTopologyF64F32
```

```kotlin
internal data class PathHybridHalfEdgeF64F32(
    val idI32: Int,
    val originVertexIndexI32: Int,
    val destinationVertexIndexI32: Int,
    val twinIndexI32: Int,
    val nextIndexI32: Int,
    val leftFaceIndexI32: Int,
    val sourceSpanIdsI64: List<Long>,
    val firstWindingDeltaI32: Int,
    val secondWindingDeltaI32: Int,
)

internal data class PathHybridFaceI32(
    val idI32: Int,
    val boundaryHalfEdgeIndicesI32: List<Int>,
    val firstWindingI32: Int,
    val secondWindingI32: Int,
)

internal data class PathBoundaryHalfEdgeTraceF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val originVertexF64F32: PathHybridVertexF64F32,
    val destinationVertexF64F32: PathHybridVertexF64F32,
    val forward: Boolean,
)

internal data class PathBoundaryTraceF64F32(
    val halfEdgesF64F32: List<PathBoundaryHalfEdgeTraceF64F32>,
)

internal class PathArrangementF64F32 private constructor(
    private val verticesF64F32: List<PathHybridVertexF64F32>,
    private val halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    private val facesI32: List<PathHybridFaceI32>,
) {
    fun boundary(
        firstFillRule: FillRule,
        secondFillRule: FillRule,
        operation: PathBooleanOp,
    ): List<PathBoundaryTraceF64F32>

    fun unaryBoundary(fillRule: FillRule): List<PathBoundaryTraceF64F32>

    companion object {
        fun build(
            topologyF64F32: PathHybridTopologyF64F32,
            limitsI32: PathOpsLimitsI32,
            candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
        ): PathArrangementF64F32
    }
}
```

- [ ] **Step 1: Add behavioral tests for local tangency and unproved contacts**

Keep the existing five operations × three transforms for tangent ovals, then
add one locally proved coincidence and the same projected geometry without its
exact source witness:

```kotlin
@Test
fun `point witness permits only its two local projected branches`() {
    val e = 2.0.pow(-25)
    val lower = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 - e, 2.0 to -1.0, 0.0 to -1.0,
    )
    val upper = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 3.0, 0.0 to 3.0,
    )
    val result = projectTogetherF64(lower, upper)
    assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 0f)))
    assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 2f)))
}

@Test
fun `same projected branches without source witness reject`() {
    val e = 2.0.pow(-25)
    val lower = normalizedContourF64(
        0.0 to 1.0 - e, 1.0 to 1.0 - e, 2.0 to -1.0, 0.0 to -1.0,
    )
    val upper = normalizedContourF64(
        0.0 to 1.0 + e, 1.0 to 1.0 + e, 2.0 to 3.0, 0.0 to 3.0,
    )
    val error = assertFailsWith<IllegalStateException> {
        projectTogetherF64(lower, upper)
    }
    assertEquals("path-f32-projection-collapse", error.message)
}

@Test
fun `tangent ovals preserve all five truth tables`() {
    val case = pathOpCasesF32().single { it.name == "tangent ovals" }
    PathBooleanOp.entries.forEach { operation ->
        val result = PathOpsF32.op(case.first, case.second, operation)
        case.probes.forEach { probeF32 ->
            assertEquals(
                expectedMembership(
                    operation,
                    PathAnalysisF32.contains(case.first, probeF32),
                    PathAnalysisF32.contains(case.second, probeF32),
                ),
                PathAnalysisF32.contains(result, probeF32),
                "$operation at $probeF32",
            )
        }
    }
}
```

- [ ] **Step 2: Run the Task 2 tests and capture the red reason**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

Expected after Task 1: the locally proved coincidence hits the conservative
adapter and rejects, while the unsupported variant still rejects.

- [ ] **Step 3: Implement representative selection and projected broad phase**

For each exact vertex, collect only the F32 values obtained by evaluating its
incident spans at the exact source parameter. Apply this order:

```kotlin
private fun chooseRepresentativePointF32(
    originalPointsF32: List<Point2F32>,
    incidentCandidatesF32: List<Point2F32>,
): Point2F32
```

1. one original point wins bit-for-bit;
2. several originals must be topologically equal after signed-zero
   canonicalisation, then the existing semantic F32 order chooses raw bits;
3. otherwise all incident candidates must agree, or a candidate from the same
   exact witness must validate against every incidence;
4. any remaining ambiguity throws `path-f32-projection-collapse`.

Lift projected endpoints exactly to F64 and reuse the deterministic AABB index.
Classify every candidate as exact point, exact overlap, local projected
coincidence or unsupported conflict. Charge before every lookup, comparison and
predicate. Expand/compare F32 AABBs conservatively so an ULP cannot remove a
real candidate. Unsupported crossings/overlaps reject before DCEL construction.

- [ ] **Step 4: Implement one hybrid DCEL authority**

Port the face enumeration and containment forest from `PathArrangementF64`,
with these replacements:

- vertex equality and face embedding use the chosen F32 point lifted to F64;
- outgoing ray order uses source F64 direction/tangent and existing exact
  orientation fallback;
- an exact or projected coincidence aggregates operand winding contributions
  on one shared edge without deleting source spans;
- each half-edge retains its `PathSourceSpanF64` and orientation;
- face areas use expansions over lifted F32 representatives;
- unresolved equal rays reject instead of using IDs.

Check `maxVertices` and `maxHalfEdges` from canonical counts before allocating
the immutable vertex/half-edge arrays.

Do not copy `canonicalContourF64`'s point-only output. Return ordered
`PathBoundaryTraceF64F32` values defined in Task 3.

- [ ] **Step 5: Verify Task 2 on JVM and JS**

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' \
  --tests '*PathOpsF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
rtk git diff --check
```

Expected: all 15 tangent-oval variants and all five operations pass; the new
unsupported contact rejects identically on JVM/JS.

- [ ] **Step 6: Commit Task 2**

```bash
rtk git add -- \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt \
  refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git commit -m "feat(math): build hybrid F64 F32 path arrangement"
```

---

## Task 3 — Make claims atomic and handle n-way, overlaps and collapsed incidences

**Files:**

- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`

**Interfaces:**

- Consumes: Task 2 hybrid topology and arrangement.
- Produces:

```kotlin
internal enum class PathBoundaryDisposition { KEEP, DROP, REJECT }
```

- [ ] **Step 1: Add red tests for claim ownership**

Add fixed common tests for:

```kotlin
@Test
fun `n way contact is one atomic event`() {
    val first = PathBuilder()
        .moveTo(0f, 0f).lineTo(-2f, 1f).lineTo(-2f, -1f).close()
        .build()
    val second = PathBuilder()
        .moveTo(0f, 0f).lineTo(2f, 2f).lineTo(2f, 1f).close()
        .moveTo(0f, 0f).lineTo(2f, -1f).lineTo(2f, -2f).close()
        .build()
    val result = PathOpsF32.op(first, second, PathBooleanOp.UNION)
    listOf(Point2F32(-1.5f, 0f), Point2F32(1.5f, 1.4f), Point2F32(1.5f, -1.4f))
        .forEach { probeF32 -> assertTrue(PathAnalysisF32.contains(result, probeF32)) }
}

@Test
fun `two disjoint events on one source segment remain independent`() {
    val main = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 4f, 2f)).build()
    val touches = PathBuilder()
        .moveTo(1f, 0f).lineTo(.5f, -1f).lineTo(1.5f, -1f).close()
        .moveTo(3f, 0f).lineTo(2.5f, -1f).lineTo(3.5f, -1f).close()
        .build()
    val result = PathOpsF32.op(main, touches, PathBooleanOp.UNION)
    listOf(Point2F32(2f, 1f), Point2F32(1f, -.5f), Point2F32(3f, -.5f))
        .forEach { probeF32 -> assertTrue(PathAnalysisF32.contains(result, probeF32)) }
}

@Test
fun `overlapping claims reject without partial output`() {
    val e = 2.0.pow(-25)
    val main = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0, 3.0 to 1.0 - e,
        3.0 to -1.0, 1.5 to -2.0, 0.0 to -1.0,
    )
    val firstTouch = normalizedContourF64(0.0 to 1.0, -0.4 to 2.0, 0.4 to 2.0)
    val secondTouch = normalizedContourF64(2.0 to 1.0, 1.6 to 2.0, 2.4 to 2.0)
    val error = assertFailsWith<IllegalStateException> {
        projectTogetherF64(main, firstTouch, secondTouch)
    }
    assertEquals("path-f32-projection-collapse", error.message)
}
```

Also retain exact-overlap rectangles and the seam fixture under every cyclic
rotation and reversal. Each helper is a fixed `PathBuilder` fixture and every
assertion is membership, immutability or exact error text.

- [ ] **Step 2: Run the claim tests red on JVM and JS**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

Expected: at least n-way aggregation, shared-span ordering or overlapping claim
atomicity fails before implementation.

- [ ] **Step 3: Implement proposal/commit claims**

Sort proposals by witness semantic key, source span semantic key and F64
parameter interval. Validate all proposals before mutating topology:

```kotlin
private fun claimsConflictF64(
    firstF64: PathProjectedSpanClaimF64,
    secondF64: PathProjectedSpanClaimF64,
): Boolean = firstF64.sourceSpanIdI64 == secondF64.sourceSpanIdI64 &&
    maxOf(firstF64.startParameterF64, secondF64.startParameterF64) <
        minOf(firstF64.endParameterF64, secondF64.endParameterF64)
```

Adjacent claims may share an endpoint only when its
`PathVertexIdentityF64` is exactly the same. Resolve all pair relations of one
n-way witness as one transaction. On any conflict, throw before publishing
aliases, coincidence records, half-edges or output.

- [ ] **Step 4: Implement collapsed-incidence disposition**

Retain equal-representative spans as `PathCollapsedIncidenceF64F32` during
winding. After face selection:

- no selected boundary dependency: ignore only at emission;
- whole selected contour collapsed and exact double-area `<= 2^-45`: `DROP`;
- exact double-area `> 2^-45`: `REJECT`;
- partially representable selected contour needing the incidence for closure,
  orientation or winding: `REJECT`.

The result is explicit; never use nullable control flow followed by
`checkNotNull`.

- [ ] **Step 5: Verify thresholds, exact overlaps and claim ordering**

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' \
  --tests '*PathOpsF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
rtk git diff --check
```

Expected: under and exactly at `2^-45` drop only the collapsed contour; above
the threshold rejects; exact overlap succeeds; n-way and disjoint claims are
order independent; overlapping claims reject without mutation.

- [ ] **Step 6: Commit Task 3**

```bash
rtk git add -- \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt \
  refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git commit -m "fix(math): make projected path claims atomic"
```

---

## Task 4 — Emit boundary traces directly and delete the legacy projection

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBoundaryWriterF32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- Delete: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathHybridBehaviorTestSupportF64.kt`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/task-4-report.md`

**Interfaces:**

- Consumes: `List<PathBoundaryTraceF64F32>` from Task 3.
- Produces:

```kotlin
internal fun writeBoundaryTracesF64F32(
    tracesF64F32: List<PathBoundaryTraceF64F32>,
    fillRule: FillRule,
    limitsI32: PathOpsLimitsI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathF32
```

- [ ] **Step 1: Add writer behavior tests**

```kotlin
@Test
fun `hybrid writer preserves original signed zero provenance`() {
    val outer = PathBuilder()
        .moveTo(-0f, -0f).lineTo(20f, -0f).lineTo(20f, 20f).lineTo(-0f, 20f).close()
        .build()
    val hole = PathBuilder().addRect(RectF32.ofLTRB(5f, 5f, 15f, 15f)).build()
    val result = PathOpsF32.op(outer, hole, PathBooleanOp.DIFFERENCE)
    val firstMoveF32 = result.first() as PathSegmentF32.MoveTo
    assertEquals((-0f).toRawBits(), firstMoveF32.point.x.toRawBits())
    assertEquals((-0f).toRawBits(), firstMoveF32.point.y.toRawBits())
}

@Test
fun `hybrid writer never replaces a boundary run by a chord`() {
    val e = 2.0.pow(-25)
    val lowerF64 = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 - e, 2.0 to 1.0 - e / 2.0,
        2.0 to -1.0, 0.0 to -1.0,
    )
    val upperF64 = normalizedContourF64(
        0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0 + e / 2.0,
        2.0 to 3.0, 0.0 to 3.0,
    )
    assertTrue(PathAnalysisF32.contains(projectOneF64(lowerF64), Point2F32(1f, .5f)))
    assertTrue(PathAnalysisF32.contains(projectOneF64(upperF64), Point2F32(1f, 1.5f)))
    val error = assertFailsWith<IllegalStateException> {
        projectTogetherF64(lowerF64, upperF64)
    }
    assertEquals("path-f32-projection-collapse", error.message)
}
```

Retain orientation, fill rule, bounds and algebraic identity assertions for
`op`, `simplify` and `asWinding`.

- [ ] **Step 2: Run writer tests before rewiring**

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' \
  --tests '*PathOpsF32Test*' --rerun-tasks
```

Expected: tests identify any remaining path through the old contour projection
or parallel source lists.

- [ ] **Step 3: Implement the direct writer**

For every kept trace, emit the origin representative of each ordered half-edge,
then close. Preserve raw bits from the canonical `originalPointF32`; generated
points use the chosen representative. The writer validates finitude,
closure, orientation, cyclic canonicalisation, fill rule and output limits. It
must not discover contacts, remove a run, remove a local span, create a bridge
or recompute source provenance.

Move the existing semantic F32 order and Booth implementation into
`PathBoundaryWriterF32.kt`, retaining the exact `3n` debit. Canonicalisation may
rotate/reverse a complete trace; it must rotate/reverse its half-edge provenance
with the points.

- [ ] **Step 4: Rewire all public operations and delete legacy code**

The final orchestration is exactly:

```kotlin
val sourceTopologyF64 = splitPathSourceTopologyF64(
    inputEdgesF64(inputs, normalizationF64, limitsI32),
    limitsI32,
    candidateWorkBudgetI32,
)
val hybridTopologyF64F32 = buildPathHybridTopologyF64F32(
    sourceTopologyF64,
    normalizationF64,
    limitsI32,
    candidateWorkBudgetI32,
)
val arrangementF64F32 = PathArrangementF64F32.build(
    hybridTopologyF64F32,
    limitsI32,
    candidateWorkBudgetI32,
)
return writeBoundaryTracesF64F32(
    arrangementF64F32.boundary(first.fillRule, second.fillRule, operation),
    FillRule.WINDING,
    limitsI32,
    candidateWorkBudgetI32,
)
```

Use `unaryBoundary` for `simplify` and `asWinding`. Delete
`ProjectedPathContourF32`, `sourceFirstVertices`, `sourceLastVertices`, all
projected contact/bridge reconstruction helpers,
`compactProjectedPointWitnessRunsF64`, `projectionOnlyWitnessRunEndF64`,
post-extraction topology validation and the temporary `PathSplitEdgeF64`
adapter.

Replace the transitional `PathContourF64` test helpers with this numerical
pipeline helper in `PathHybridBehaviorTestSupportF64.kt`; tests assert only its
emitted `PathF32` or exact error:

```kotlin
internal fun runHybridContoursF64(
    contoursF64: List<List<Point2F64>>,
    fillRule: FillRule = FillRule.WINDING,
    limitsI32: PathOpsLimitsI32 = PathOpsLimitsI32(),
): PathF32 {
    var nextEdgeIdI32 = 0
    val edgesF64 = buildList {
        contoursF64.forEachIndexed { contourIndexI32, pointsF64 ->
            require(pointsF64.size >= 3)
            val edgeIdsI32 = pointsF64.indices.map { nextEdgeIdI32++ }
            val identitiesF64 = pointsF64.indices.map { vertexIndexI32 ->
                val incomingEdgeIdI32 = edgeIdsI32[
                    if (vertexIndexI32 == 0) edgeIdsI32.lastIndex else vertexIndexI32 - 1
                ]
                val outgoingEdgeIdI32 = edgeIdsI32[vertexIndexI32]
                PathVertexIdentityF64(
                    incidentEdgeIds = listOf(incomingEdgeIdI32, outgoingEdgeIdI32).sorted(),
                    parameterByEdgeId = mapOf(incomingEdgeIdI32 to 1.0, outgoingEdgeIdI32 to 0.0),
                    originalPointF32 = null,
                )
            }
            pointsF64.indices.forEach { vertexIndexI32 ->
                val nextVertexIndexI32 = (vertexIndexI32 + 1) % pointsF64.size
                add(
                    PathInputEdgeF64(
                        idI32 = edgeIdsI32[vertexIndexI32],
                        operand = PathOperand.FIRST,
                        contourIndexI32 = contourIndexI32,
                        sourceSegmentIndexI32 = vertexIndexI32,
                        sourceStartParameterF64 = 0.0,
                        sourceEndParameterF64 = 1.0,
                        startIdentityF64 = identitiesF64[vertexIndexI32],
                        endIdentityF64 = identitiesF64[nextVertexIndexI32],
                        startPointF64 = pointsF64[vertexIndexI32],
                        endPointF64 = pointsF64[nextVertexIndexI32],
                        windingDeltaI32 = 1,
                    ),
                )
            }
        }
    }
    val budgetI32 = PathCandidateWorkBudgetI32(limitsI32.maxCandidateProbes)
    val sourceF64 = splitPathSourceTopologyF64(edgesF64, limitsI32, budgetI32)
    val normalizationF64 = PathNormalizationF64(Point2F64(0.0, 0.0), 1.0)
    val hybridF64F32 = buildPathHybridTopologyF64F32(
        sourceF64, normalizationF64, limitsI32, budgetI32,
    )
    val arrangementF64F32 = PathArrangementF64F32.build(hybridF64F32, limitsI32, budgetI32)
    return writeBoundaryTracesF64F32(
        arrangementF64F32.unaryBoundary(fillRule), fillRule, limitsI32, budgetI32,
    )
}
```

Change `normalizedContourF64` in the test to return `List<Point2F64>`, and make
`projectOneF64`/`projectTogetherF64` delegate to `runHybridContoursF64`.

- [ ] **Step 5: Verify the direct route on both backends**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
rtk ./gradlew :integration-tests:skia:compileTestKotlin --rerun-tasks
rtk git diff --check
```

Expected: complete math suite green, Skia consumer compiles, no legacy symbol
remains and no test observes a generic exception.

- [ ] **Step 6: Commit Task 4**

```bash
rtk git add -- \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBoundaryWriterF32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathHybridBehaviorTestSupportF64.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt \
  refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git commit -m "refactor(math): emit hybrid path boundaries directly"
```

---

## Task 5 — Close budgets, determinism and behavioral-only test coverage

**Files:**

- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBoundaryWriterF32.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsBudgetF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`
- Modify: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt`
- Delete: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathArrangementF64Test.kt`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/task-5-report.md`

**Interfaces:**

- Consumes: the single `PathCandidateWorkBudgetI32` created by `PathOpsF32`.
- Produces no new public interface. All work-producing helpers receive
  `candidateWorkBudgetI32` explicitly.

- [ ] **Step 1: Add red observable budget boundaries**

```kotlin
private fun closedPathF32(pointsF32: List<Point2F32>): PathF32 {
    val firstF32 = pointsF32.first()
    val builder = PathBuilder().moveTo(firstF32.x, firstF32.y)
    pointsF32.drop(1).forEach { pointF32 -> builder.lineTo(pointF32.x, pointF32.y) }
    return builder.close().build()
}

private fun rotatedPointsF32(
    pointsF32: List<Point2F32>,
    rotationI32: Int,
    reversed: Boolean,
): List<Point2F32> {
    val orientedF32 = if (reversed) pointsF32.reversed() else pointsF32
    val offsetI32 = rotationI32 % orientedF32.size
    return orientedF32.drop(offsetI32) + orientedF32.take(offsetI32)
}

private fun longWitnessRunVariantsF32(): List<Pair<PathF32, PathF32>> {
    val lowerPointsF32 = buildList {
        add(Point2F32(0f, 0f))
        add(Point2F32(32f, 0f))
        add(Point2F32(32f, 1f))
        (31 downTo 0).forEach { xI32 -> add(Point2F32(xI32.toFloat(), 1f)) }
    }
    val upperPointsF32 = buildList {
        add(Point2F32(0f, 1f))
        (1..32).forEach { xI32 -> add(Point2F32(xI32.toFloat(), 1f)) }
        add(Point2F32(32f, 2f))
        add(Point2F32(0f, 2f))
    }
    return listOf(0, 1, 17).flatMap { rotationI32 ->
        listOf(false, true).map { reversed ->
            closedPathF32(rotatedPointsF32(lowerPointsF32, rotationI32, reversed)) to
                closedPathF32(rotatedPointsF32(upperPointsF32, rotationI32, reversed))
        }
    }
}

private fun operationSucceedsAtBudgetF32(
    firstF32: PathF32,
    secondF32: PathF32,
    maxCandidateProbesI32: Int,
): Boolean = try {
    PathOpsF32.op(
        firstF32,
        secondF32,
        PathBooleanOp.UNION,
        PathOpsLimitsI32(maxCandidateProbes = maxCandidateProbesI32),
    )
    true
} catch (error: IllegalStateException) {
    if (error.message != "path-candidate-limit") throw error
    false
}

private fun firstSuccessfulBudgetI32(firstF32: PathF32, secondF32: PathF32): Int {
    var upperI32 = 1
    while (!operationSucceedsAtBudgetF32(firstF32, secondF32, upperI32)) {
        check(upperI32 <= 1_048_576) { "canonical fixture exceeded bounded budget search" }
        upperI32 *= 2
    }
    var lowerI32 = upperI32 / 2
    while (lowerI32 + 1 < upperI32) {
        val middleI32 = lowerI32 + (upperI32 - lowerI32) / 2
        if (operationSucceedsAtBudgetF32(firstF32, secondF32, middleI32)) {
            upperI32 = middleI32
        } else {
            lowerI32 = middleI32
        }
    }
    return upperI32
}

@Test
fun `long projected contact has one JVM JS budget frontier`() {
    val variantsF32 = longWitnessRunVariantsF32()
    val frontierI32 = firstSuccessfulBudgetI32(
        variantsF32.first().first,
        variantsF32.first().second,
    )
    variantsF32.forEach { (firstF32, secondF32) ->
        val firstBefore = firstF32.toList()
        val secondBefore = secondF32.toList()
        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                firstF32,
                secondF32,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = frontierI32 - 1),
            )
        }
        assertEquals("path-candidate-limit", error.message)
        assertEquals(firstBefore, firstF32.toList())
        assertEquals(secondBefore, secondF32.toList())
        PathOpsF32.op(
            firstF32,
            secondF32,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = frontierI32),
        )
    }
}
```

The bounded binary search observes only success or the exact public budget
error. Every cyclic rotation and reversal must then share its adjacent
`frontierI32 - 1` / `frontierI32` boundary. The same common test executes on
JVM and JS.

Add these max-intersection tests. The first fixture has one canonical n-way
source event. The second has one source point plus one new projected boundary:

```kotlin
@Test
fun `one n way event fits one intersection slot`() {
    val witnessF64 = Point2F64(0.0, 0.0)
    val result = runHybridContoursF64(
        contoursF64 = listOf(
            listOf(witnessF64, Point2F64(-2.0, 1.0), Point2F64(-2.0, -1.0)),
            listOf(witnessF64, Point2F64(2.0, 2.0), Point2F64(2.0, 1.0)),
            listOf(witnessF64, Point2F64(2.0, -1.0), Point2F64(2.0, -2.0)),
        ),
        limitsI32 = PathOpsLimitsI32(maxIntersections = 1),
    )
    assertTrue(PathAnalysisF32.contains(result, Point2F32(-1.5f, 0f)))
}

@Test
fun `new projected boundary consumes the second intersection slot`() {
    val e = 2.0.pow(-25)
    val contoursF64 = listOf(
        listOf(
            Point2F64(0.0, 1.0), Point2F64(1.0, 1.0 - e),
            Point2F64(2.0, -1.0), Point2F64(0.0, -1.0),
        ),
        listOf(
            Point2F64(0.0, 1.0), Point2F64(1.0, 1.0 + e),
            Point2F64(2.0, 3.0), Point2F64(0.0, 3.0),
        ),
    )
    val error = assertFailsWith<IllegalStateException> {
        runHybridContoursF64(
            contoursF64,
            limitsI32 = PathOpsLimitsI32(maxIntersections = 1),
        )
    }
    assertEquals("path-intersection-limit", error.message)
    val result = runHybridContoursF64(
        contoursF64,
        limitsI32 = PathOpsLimitsI32(maxIntersections = 2),
    )
    assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 0f)))
}
```

- [ ] **Step 2: Run budget tests red on JVM and JS**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsBudgetF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsBudgetF32Test*' --rerun-tasks
```

Expected: at least one phase performs work without the global debit or yields a
different adjacent boundary under an equivalent representation.

- [ ] **Step 3: Instrument every hybrid phase before work**

Charge before broad-phase visits, F32 comparisons, candidate evaluation,
witness lookup, claim creation/copy/validation, sorting comparisons, DCEL
allocation/copy, face-area terms, trace traversal and writer emission. Use
`requireRemainingAtLeast(Long)` only as a preflight; every real unit still calls
`consume()` immediately before execution.

Count a canonical projected event exactly once by semantic identity. Count two
new endpoints for a projected coincidence. Reuse the existing exact endpoint
no-op ruling. Preflight all products/additions in `Long`, including `3L * n`.

- [ ] **Step 4: Remove infrastructure-shape assertions**

Delete `PathArrangementF64Test.kt` after moving its fill, containment, winding,
touching-edge and limit cases to `PathOpsHybridTopologyF32Test.kt` with
membership/error oracles. In `PathIntersectionsF64Test.kt`, keep numerical
intersection/predicate assertions but remove tests whose sole oracle is a
private collection size, internal class name or storage layout. Do not replace
them with source scanning or reflection.

- [ ] **Step 5: Run the full deterministic matrix**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
rtk ./gradlew :integration-tests:skia:compileTestKotlin --rerun-tasks
rtk git diff --check
```

Expected: all tasks pass; rotations, reversals and contour permutations share
the same output and adjacent budget frontier; `op`, `simplify` and `asWinding`
leave inputs immutable on success and failure.

- [ ] **Step 6: Commit Task 5**

```bash
rtk git add -- \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBoundaryWriterF32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsBudgetF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathArrangementF64Test.kt \
  refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git commit -m "test(math): prove hybrid path topology budgets"
```

---

## Task 6 — Run the final math/Skia proof and publish tracking evidence

**Files:**

- Modify: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md`
- Create: `refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/final-verification.md`
- Modify only when generated values actually change:
  `integration-tests/skia/test-similarity-scores.properties`
- Modify only intended generated files under:
  `integration-tests/skia/src/test/resources/generated-renders/`

**Interfaces:**

- Consumes: stable Task 5 commit with both Sol gates green.
- Produces: reproducible JVM/JS/Skia evidence, refreshed dashboard for rendable
  GMs and one final independent Sol verdict.

- [ ] **Step 1: Run the complete math proof from a clean state**

```bash
rtk ./gradlew clean :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
rtk ./gradlew :integration-tests:skia:compileTestKotlin --rerun-tasks
rtk git diff --check
rtk git status --short
```

Record command, commit, duration and result in `final-verification.md`. A dirty
status is acceptable only for intended render/score/report artifacts listed in
this task.

- [ ] **Step 2: Regenerate renders, dashboard and current scores**

Before this step, read the repository skills `regenerate-renders` and
`regenerate-skia-dashboard`. Run the default paths, which omit `BLOCKING` GMs:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
rtk ./gradlew :integration-tests:skia:test
```

Do not pass `-Dkanvas.gm.includeBlocking=true`. Do not alter the existing
font/codec or non-rendable exclusions. Inspect the dashboard at
`integration-tests/skia/build/reports/skia-gm-dashboard/index.html`; record the
rendable denominator, successes, failures and changed similarity scores.

- [ ] **Step 3: Re-run affected tests after generated artifacts**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  :integration-tests:skia:compileTestKotlin --rerun-tasks
rtk git diff --check
```

- [ ] **Step 4: Request final independent Sol review**

The reviewer receives the validated spec, this plan, every task report/review,
the stable commit and final verification. Required verdict:

```text
Spec: PASS
Quality: PASS
```

The reviewer must rerun the three re-review-5 probes, inspect the absence of
compaction/chord/post-write validation, verify budget debit and confirm that no
font, codec or new GM exclusion entered the diff.

- [ ] **Step 5: Commit intended generated evidence and tracking docs**

```bash
rtk git add refactor/progress/2026-08-31-hybrid-f64-f32-path-topology
rtk git add integration-tests/skia/test-similarity-scores.properties
rtk git add integration-tests/skia/src/test/resources/generated-renders
rtk git commit -m "docs(refactor): record hybrid path topology verification"
```

If renders/scores are byte-identical, stage only
`refactor/progress/2026-08-31-hybrid-f64-f32-path-topology/`.

---

## Definition of Done

- The production flow is exactly `PathF32 -> source topology F64 -> hybrid
  topology F64/F32 -> hybrid DCEL -> boundary traces -> PathF32`.
- `compactProjectedPointWitnessRunsF64`,
  `projectionOnlyWitnessRunEndF64`, `sourceFirstVertices`,
  `sourceLastVertices`, synthetic bridges and post-write topology validation no
  longer exist.
- Single-witness, multi-witness and under-threshold re-review-5 probes are
  closed on JVM and JS without membership loss or generic error.
- Local tangencies, exact overlaps, n-way contacts, multiple events per segment,
  seams, signed zero and large F32 coordinates satisfy behavioral oracles.
- Unknown projected crossings/overlaps reject with the exact stable error.
- Threshold behavior is exact at double-area `2^-45`.
- Candidate, intersection, vertex and half-edge limits are deterministic and
  debit before work.
- All new geometry types live in `:math:geometry` and respect
  F32/F64/I32/I64 nomenclature.
- No infrastructure/source/import/package/internal-shape test was added; obsolete
  graph-shape tests were replaced by behavior.
- Full JVM/JS tests, Skia compilation, rendable-GM regeneration and dashboard
  succeed.
- Final fresh Sol review reports `Spec: PASS` and `Quality: PASS`.
