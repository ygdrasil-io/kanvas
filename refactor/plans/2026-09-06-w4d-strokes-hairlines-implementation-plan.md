# W4d.1 Strokes and Hairlines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer les path strokes, hairlines et `STROKE_AND_FILL` hard-edge dans une frame path mixte, avec dash/expansion/union possédés par `:math` et une route native fermée jusqu'à `Surface`.

**Architecture:** `:math:geometry` valide le style, découpe les dashes, développe les outlines et publie des snapshots F64/F32 bornés ; `:math:matrix` orchestre la préparation device-space axis-aligned. `:gpu-plan` scelle une frame path mixte fill/stroke, ses coûts et son graph, puis `:gpu-renderer` authentifie et matérialise sans rappeler les stroke/tessellation routes legacy.

**Tech Stack:** Kotlin multiplatform JVM/JS, `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGPU4K, Gradle, `rtk`, GitHub CLI.

**Spec:** `refactor/specs/2026-09-06-w4-remaining-geometry-coverage-design.md`

## Global Constraints

- Branche : `codex/w4d-strokes-hairlines`, empilée sur `codex/w4c-path-fills`; la PR cible W4c.
- Capability exacte : `solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1`.
- Une frame candidate contient 1 à 512 `GeometryNode.Path`, peut mélanger `FILL`, `STROKE` et `STROKE_AND_FILL`, et contient au moins un stroke.
- W4d.1 accepte seulement fill rules non inverse, `HARD_EDGE`, `SolidColor`, `SrcOver`, sRGB 1×, layer racine, transform identity/scale/translate et clip vide ou scissor I32 non-AA.
- Toute géométrie nouvelle vit dans `:math`; les noms numériques portent `I32`, `I64`, `F32` ou `F64`.
- Tolérance device-space : `0.25 px`; profondeur 32, 65 536 unités géométriques tentées par path et 262 144 par frame, plus limites I32/I64 explicites de vertices/indices/bytes, débit avant émission/dédoublonnage.
- Un finite stroke conserve dash et outline paramétriques en source, puis projette et aplatit l'outline certifié. Une hairline est dashée en source, projetée, puis reçoit une couverture d'un pixel device.
- `STROKE_AND_FILL` non inverse est une union topologique certifiée dans `:math`; largeur zéro devient `FILL`.
- Après `Ready`, aucune invocation de `AdvancedStrokePlan`, `GPUStroke`, `GPUPathHairlineContract`, `PathTessellator`, mapper ou fallback legacy.
- Tests comportementaux/publics uniquement : aucun source-shape, reflection, accès privé, call-count ou test d'infrastructure.
- Aucun test/artefact `font`, `codec`, GM Skia, dashboard, baseline ou `jpg-color-cube`; aucun seuil/tolérance de comparaison ajouté.
- Chaque tâche : fresh Terra, RED → GREEN → self-review → commit, puis Sol spec review et quality review read-only; tout finding Critical/Important est corrigé avant la tâche suivante.

---

## Carte de fichiers

| Responsabilité | Créations principales | Modifications principales |
|---|---|---|
| Valeurs stroke | `math/geometry/.../PathStrokeStyleF64.kt`, `PathStrokeLimitsI32.kt`, `PathStrokeLimitsI64.kt`, `PathStrokeWorkLedgerI64.kt`, `PathStrokeGeometryF32.kt` | aucune géométrie renderer |
| Dash/outline | `PathStrokeParametricF64.kt`, `PathStrokeDashPreparationF64.kt`, `PathStrokeOutlinePreparationF64.kt`, `PathStrokeAndFillPreparationF64.kt` | débit ledger-aware du moteur topologique math |
| Transform/préparation | `math/matrix/.../PathStrokeTransformsF64.kt` | aucune map F32 legacy |
| Graph/planner | `gpu-plan/.../W4dPathStrokePlanCompiler.kt`, `PathStrokePlanBudget.kt`, `W4dPlanDiagnostics.kt` | capabilities, passes, resources, compiler chain |
| Lowering/native | `gpu-renderer/.../W4dPathStrokeGraphLowerer.kt`, `GPUPlanW4dPreparedAuthority.kt`, `GPUCorePrimitiveW4dPreparedFrameTaskListAssembler.kt` | preflight, payload/run materializers, frame pool |
| Surface/preuves | `kanvas/src/test/.../W4dPathStrokeCpuOracle.kt` | router/pixel tests; gate production inchangée |
| Suivi | ce plan | `refactor/README.md`, status W04 |

## Interfaces partagées

```kotlin
public enum class PathStrokeCap { Butt, Round, Square }
public enum class PathStrokeJoin { Miter, Round, Bevel }

public sealed interface PathStrokeWidthF64 {
    public data object Hairline : PathStrokeWidthF64
    public data class Finite(public val valueF64: Double) : PathStrokeWidthF64
}

public class PathStrokeDashF64 private constructor(
    intervalsF64: DoubleArray,
    public val phaseF64: Double,
) {
    public val intervalCountI32: Int
    public fun copyIntervalsF64(): DoubleArray
    public companion object {
        public fun of(intervalsF64: DoubleArray, phaseF64: Double): PathStrokeDashF64
    }
}

public data class PathStrokeStyleF64(
    public val widthF64: PathStrokeWidthF64,
    public val cap: PathStrokeCap,
    public val join: PathStrokeJoin,
    public val miterLimitF64: Double,
    public val dashF64: PathStrokeDashF64? = null,
)

public data class PathStrokeLimitsI32(
    public val maxSubdivisionDepthI32: Int = 32,
    public val maxAttemptedGeometryUnitsPerPathI32: Int = 65_536,
    public val maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
    public val maxEmittedVertexCountPerPathI32: Int = 262_144,
    public val maxEmittedVertexCountPerFrameI32: Int = 1_048_576,
    public val maxEmittedIndexCountPerPathI32: Int = 786_432,
    public val maxEmittedIndexCountPerFrameI32: Int = 3_145_728,
)

public data class PathStrokeLimitsI64(
    public val maxSnapshotByteCountPerPathI64: Long = 16L * 1024L * 1024L,
    public val maxSnapshotByteCountPerFrameI64: Long = 64L * 1024L * 1024L,
)

public data class PathStrokeWorkUsageI64(
    public val attemptedGeometryUnitCountI64: Long = 0L,
    public val emittedVertexCountI64: Long = 0L,
    public val emittedIndexCountI64: Long = 0L,
    public val snapshotByteCountI64: Long = 0L,
)

public data class PathStrokePolicyF64(
    public val maximumSagittaErrorF64: Double = 0.25,
    public val maximumDashArcLengthErrorF64: Double = 0.0625,
    public val limitsI32: PathStrokeLimitsI32 = PathStrokeLimitsI32(),
    public val limitsI64: PathStrokeLimitsI64 = PathStrokeLimitsI64(),
)

public enum class PathStrokeDrawMode { Stroke, StrokeAndFill }

public sealed interface PathStrokePrimitiveF64 {
    public fun pointAtF64(parameterF64: Double): Point2F64
    public fun derivativeAtF64(parameterF64: Double): Vector2F64
}

public data class PathStrokePrimitiveSpanF64(
    public val primitiveF64: PathStrokePrimitiveF64,
    public val startParameterF64: Double,
    public val endParameterF64: Double,
)

public sealed interface PathStrokeOutlinePrimitiveF64 {
    public fun pointAtF64(parameterF64: Double): Point2F64
    public fun derivativeAtF64(parameterF64: Double): Vector2F64
}

public data class PathStrokeBoundsF64(
    public val leftF64: Double,
    public val topF64: Double,
    public val rightF64: Double,
    public val bottomF64: Double,
)

public data class PathStrokeOutlineIntervalF64(
    public val primitiveF64: PathStrokeOutlinePrimitiveF64,
    public val startParameterF64: Double,
    public val endParameterF64: Double,
    public val boundsF64: PathStrokeBoundsF64,
    public val sourceSagittaUpperBoundF64: Double,
)

public sealed interface PathStrokeProjectionPointResultF64 {
    public data class Ready(public val pointF64: Point2F64) : PathStrokeProjectionPointResultF64
    public data object NonFinite : PathStrokeProjectionPointResultF64
}

public sealed interface PathStrokeProjectionIntervalResultF64 {
    public data class Bounded(
        public val maximumDeviceSagittaUpperBoundF64: Double,
    ) : PathStrokeProjectionIntervalResultF64
    public data object HorizonCrossing : PathStrokeProjectionIntervalResultF64
    public data object NonFinite : PathStrokeProjectionIntervalResultF64
    public data object Unbounded : PathStrokeProjectionIntervalResultF64
}

public interface PathStrokeProjectionF64 {
    public fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64
    public fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64
}

public class PathStrokeGeometryF32 private constructor(
    fillGeometryF32: PathFillGeometryF32,
    conservativeBoundsF32: RectF32,
    public val workUsageI64: PathStrokeWorkUsageI64,
) {
    public val vertexCostI64: Long
    public val indexCostI64: Long
    public val snapshotByteCostI64: Long
    public fun copyFillGeometryF32(): PathFillGeometryF32
    public fun copyConservativeBoundsF32(): RectF32
    public fun copyConservativeScissorI32(): RectI32
    internal companion object {
        internal fun of(
            fillGeometryF32: PathFillGeometryF32,
            conservativeBoundsF32: RectF32,
            workUsageI64: PathStrokeWorkUsageI64,
        ): PathStrokeGeometryF32
    }
}

public sealed interface PathFillWithStrokeWorkPreparationResult {
    public data class Ready(
        public val geometryF32: PathFillGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathFillWithStrokeWorkPreparationResult
    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathFillWithStrokeWorkPreparationResult
    public data class InvalidScene(
        public val reason: PathFillInvalidSceneReason,
    ) : PathFillWithStrokeWorkPreparationResult
    public data class ResourceLimitExceeded(
        public val reason: PathStrokeResourceLimitReason,
    ) : PathFillWithStrokeWorkPreparationResult
}

public fun preparePathFillGeometryWithStrokeWorkF32(
    inputF64: PathFillInputF64,
    fillPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    strokePolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathFillWithStrokeWorkPreparationResult

public sealed interface PathStrokePreparationResult {
    public data class Ready(
        public val geometryF32: PathStrokeGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokePreparationResult
    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokePreparationResult
    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : PathStrokePreparationResult
    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : PathStrokePreparationResult
}

public fun prepareProjectedPathStrokeGeometryF32(
    inputF64: PathFillInputF64,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokePreparationResult

public fun Matrix3x3F32.preparePathStrokeGeometryF32(
    path: PathF32,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokePreparationResult
```

`PathStrokeGeometryF32` est un snapshot défensif : sa copie de
`PathFillGeometryF32` reste la seule autorité direct/stencil et aucun tableau
mutable n'est rendu directement.

## Exécution W4d.1 — 2026-09-07

Les tâches 1 à 10 sont intégrées sur `codex/w4d-strokes-hairlines`. La tâche
11 a publié les preuves fraîches dans `refactor/README.md` et le status W04 :
la capability livrée est
`solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1`.
Les garanties sont la préparation F64/F32 dans `:math`, le ledger unique
débitant avant émission, le `RenderGraph` et les ressources scellés dans
`:gpu-plan`, l'authentification mécanique dans `:gpu-renderer`, l'atomicité
direct/stencil, et les leases V/I/Uniform32/D24S8 retenus jusqu'à
completion/readback avec rollback transactionnel.

Checklist de clôture : Tasks 1–10 réalisées ; Task 11 Steps 1–3 réalisés.
Le Step 4 est la revue Sol finale, réservée au contrôleur ; le Step 5 (push et
PR) est également réservé au contrôleur et ne doit pas être coché ici. Les
gates autorisées ont exclu `font`/`codec`, GM/dashboard/baseline et
`jpg-color-cube`, hormis la compilation transitive de `font` sans exécution de
ses tests. Les ouvertures restent `TopologyLimit` F64→F32 des
auto-intersections, W4d.2, W4e et le ledger historique DrawPoint.

---

### Task 1: Valeurs stroke F64, validation et limites

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeStyleF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeLimitsI32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeLimitsI64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeWorkLedgerI64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeGeometryF32.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeStyleF64Test.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeGeometryF32Test.kt`

**Interfaces:**
- Consumes: `PathFillInputF64`, `PathFillGeometryF32`, `RectI32`.
- Produces: styles/limits/usages de la section Interfaces partagées et le ledger interne partagé par toutes les étapes.

- [ ] **Step 1: Écrire les tests RED de construction et snapshots**

```kotlin
@Test fun dash_requires_even_non_negative_finite_intervals_with_positive_total_and_finite_phase() {
    assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(1.0), 0.0) }
    assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(1.0, -1.0), 0.0) }
    assertFailsWith<IllegalArgumentException> { PathStrokeDashF64.of(doubleArrayOf(0.0, 0.0), 0.0) }
    assertEquals(2, PathStrokeDashF64.of(doubleArrayOf(1.0, 0.0), 0.0).intervalCountI32)
    assertEquals(2, PathStrokeDashF64.of(doubleArrayOf(2.0, 3.0), -1.0).intervalCountI32)
}

@Test fun style_rejects_negative_width_and_non_finite_miter() {
    assertFailsWith<IllegalArgumentException> {
        PathStrokeStyleF64(PathStrokeWidthF64.Finite(-1.0), PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0)
    }
}

```

- [ ] **Step 2: Exécuter le RED JVM**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathStrokeStyleF64Test' --tests '*PathStrokeGeometryF32Test'
```

Expected: FAIL car les types W4d n'existent pas.

- [ ] **Step 3: Implémenter les valeurs minimales et raisons stables**

Ajouter `PathStrokeInvalidSceneReason` (`NonFiniteInput`, `InvalidStyle`) et
`PathStrokeResourceLimitReason` (`FlatteningDidNotConverge`,
`PathWorkLimit`, `FrameWorkLimit`, `VertexLimit`, `IndexLimit`,
`SnapshotByteLimit`, `TopologyLimit`, `RasterBoundsOverflow`,
`HostSizeOverflow`). Une miter limit finie `< 1.0`
reste valide et sélectionne Bevel pendant l'expansion. Les intervalles dash
sont finis et non négatifs, leur nombre est pair et leur somme est strictement
positive; les intervalles nuls Skia valides, dont un off interval nul, sont
préservés.

```kotlin
public enum class PathStrokeInvalidSceneReason { NonFiniteInput, InvalidStyle }
public enum class PathStrokeResourceLimitReason {
    FlatteningDidNotConverge, PathWorkLimit, FrameWorkLimit,
    VertexLimit, IndexLimit, SnapshotByteLimit,
    TopologyLimit, RasterBoundsOverflow, HostSizeOverflow
}

internal class PathStrokeWorkLedgerI64(
    private val pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    private val frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    private val limitsI32: PathStrokeLimitsI32,
    private val limitsI64: PathStrokeLimitsI64,
) {
    public fun debitBeforeEmissionI64(deltaI64: PathStrokeWorkUsageI64)
    public fun debitTopologyBeforeEmissionI64(unitCountI64: Long)
    public fun snapshotPathUsageI64(): PathStrokeWorkUsageI64
    public fun snapshotFrameUsageAfterI64(): PathStrokeWorkUsageI64
}
```

Chaque débit vérifie par arithmetic non-negative checked
`pathWorkUsageBeforeI64 + deltaI64` et
`frameWorkUsageBeforeI64 + deltaI64` avant subdivision, fragment dash,
construction d'outline, candidat topologique, vertex/index ou allocation. Le
ledger mutable n'est jamais publié; `Ready`/`Empty` exposent ses deux snapshots
immuables après commit.

- [ ] **Step 4: Exécuter JVM puis JS non filtré**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src/commonMain math/geometry/src/commonTest
rtk git commit -m "feat(math): define bounded F64 stroke values"
```

---

### Task 2: Découpage dash F64 borné

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeParametricF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64Test.kt`

**Interfaces:**
- Consumes: `PathFillInputF64`, `PathStrokeDashF64`, policy et ledger Task 1.
- Produces: spans paramétriques non aplatis et `preparePathStrokeCenterlinesF64(...)`.

```kotlin
public class PathStrokeCenterlineF64 private constructor(
    contoursF64: List<List<PathStrokePrimitiveSpanF64>>,
    closedContours: BooleanArray,
) {
    public val contourCountI32: Int
    public fun copyContourSpansF64(indexI32: Int): List<PathStrokePrimitiveSpanF64>
    public fun isContourClosed(indexI32: Int): Boolean
    internal companion object {
        internal fun of(
            contoursF64: List<List<PathStrokePrimitiveSpanF64>>,
            closedContours: BooleanArray,
        ): PathStrokeCenterlineF64
    }
}

public sealed interface PathStrokeCenterlinePreparationResult {
    public data class Ready(
        public val centerlineF64: PathStrokeCenterlineF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeCenterlinePreparationResult
    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeCenterlinePreparationResult
    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : PathStrokeCenterlinePreparationResult
    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : PathStrokeCenterlinePreparationResult
}

public fun preparePathStrokeCenterlinesF64(
    inputF64: PathFillInputF64,
    dashF64: PathStrokeDashF64?,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeCenterlinePreparationResult
```

`PathStrokeCenterlinePreparationResult.Ready` publie la centerline, le
`pathWorkUsageI64` et le `frameWorkUsageAfterI64`; `Empty` publie les mêmes
usages. L'overload interne reçoit directement le `PathStrokeWorkLedgerI64` de
l'orchestration afin que la tâche suivante continue exactement le même débit.

- [ ] **Step 1: Écrire les tests RED dash**

Tester ligne ouverte, contour fermé, phase positive/négative, wrap de phase,
segments de longueur zéro, séparation entre contours et limite débitée avant
émission. Prouver le cumul via les seuls résultats publics : un premier appel
retourne `frameWorkUsageAfterI64`, un second le reçoit et refuse avant émission
si la limite frame serait dépassée. Exemple central :

```kotlin
@Test fun negative_phase_wraps_and_splits_open_line_in_source_arclength() {
    val negative = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
        preparePathStrokeCenterlinesF64(line0To10(), dash2On2Off(-1.0)),
    )
    val wrapped = assertIs<PathStrokeCenterlinePreparationResult.Ready>(
        preparePathStrokeCenterlinesF64(line0To10(), dash2On2Off(3.0)),
    )
    assertEquals(copySegments(wrapped.centerlineF64), copySegments(negative.centerlineF64))
}
```

- [ ] **Step 2: Vérifier le RED JVM**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathStrokeDashPreparationF64Test'
```

- [ ] **Step 3: Implémenter l'arclength paramétrique et le dash**

Construire une primitive paramétrique par segment source. Résoudre les longueurs
d'arc par subdivision bornée à `maximumDashArcLengthErrorF64`, puis représenter
chaque dash par un intervalle `[t0,t1]` de la primitive originale : aucune
polyline n'est encore émise. Débiter le ledger avant chaque évaluation,
subdivision et fragment. Ne jamais fusionner deux fragments séparés par un
intervalle off.

```kotlin
private fun normalizedDashPhaseF64(dashF64: PathStrokeDashF64): Double {
    val periodF64 = dashF64.copyIntervalsF64().sum()
    return ((dashF64.phaseF64 % periodF64) + periodF64) % periodF64
}

internal fun preparePathStrokeCenterlinesF64(
    inputF64: PathFillInputF64,
    dashF64: PathStrokeDashF64?,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeCenterlineF64?
```

- [ ] **Step 4: Exécuter JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeParametricF64.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeDashPreparationF64Test.kt
rtk git commit -m "feat(math): prepare bounded F64 stroke dashes"
```

---

### Task 3: Expansion des outlines, caps, joins et hairlines

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeOutlinePreparationF64.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeOutlinePreparationF64Test.kt`

**Interfaces:**
- Consumes: `PathStrokeCenterlineF64`, `PathStrokeStyleF64`, `PathStrokePolicyF64`.
- Produces: outline source paramétrique pour finite stroke et projection device-space explicite pour hairline.

```kotlin
public class PathStrokeOutlineF64 private constructor(
    closedContoursF64: List<List<PathStrokeOutlineIntervalF64>>,
) {
    public val contourCountI32: Int
    public fun copyContourIntervalsF64(indexI32: Int): List<PathStrokeOutlineIntervalF64>
    internal companion object {
        internal fun of(
            closedContoursF64: List<List<PathStrokeOutlineIntervalF64>>,
        ): PathStrokeOutlineF64
    }
}

public fun prepareFinitePathStrokeOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeOutlinePreparationResult

public fun prepareProjectedHairlineOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeOutlinePreparationResult

public sealed interface PathStrokeOutlinePreparationResult {
    public data class Ready(
        public val outlineF64: PathStrokeOutlineF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeOutlinePreparationResult
    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeOutlinePreparationResult
    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : PathStrokeOutlinePreparationResult
    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : PathStrokeOutlinePreparationResult
}
```

Le résultat de stage porte outline + usages path/frame comme Task 2. Les
overloads internes reçoivent le même ledger mutable; aucun compteur n'est
réinitialisé entre dash, outline, projection et finalisation.

- [ ] **Step 1: Écrire les tests RED d'expansion**

Tester Butt/Round/Square, Miter/Bevel/Round, miter `<1`, miter dépassée,
open/closed, reversal, cusp, doublons, largeur finie et hairline device 1 px.

```kotlin
@Test fun miter_over_limit_becomes_bevel_without_refusal() {
    val outline = prepareFinitePathStrokeOutlineF64(rightAngleCenterline(), finiteStyle(width = 4.0, miter = 1.0), policy())
    assertIs<PathStrokeOutlinePreparationResult.Ready>(outline)
    assertTrue(outline.outlineF64.copyContourIntervalsF64(0).all { intervalF64 ->
        intervalF64.primitiveF64.pointAtF64(intervalF64.startParameterF64).isFinite()
    })
}
```

- [ ] **Step 2: Exécuter le RED JVM**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathStrokeOutlinePreparationF64Test'
```

- [ ] **Step 3: Implémenter l'expansion F64 minimale**

Construire les offsets source comme fonctions paramétriques de la primitive et
de sa normale, sans aplatir la centerline. Les cusps sont séparées avant
normalisation; caps/joins Round conservent leur intervalle angulaire. Pour
chaque intervalle d'outline, certifier :

```text
outlineSagittaUpperBoundF64
  = centerlineSagittaUpperBoundF64
  + effectiveRadiusF64 * normalChordDeviationUpperBoundF64

effectiveRadiusF64
  = halfWidthF64 * max(1.0, miterLimitF64)
```

Pour hairline seulement, projeter la centerline puis construire l'outline avec
`halfWidthF64 = 0.5` en device-space. Chaque subdivision/intervalle/cap/join
est débité avant émission.

```kotlin
internal fun prepareFinitePathStrokeOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeOutlineF64?
```

- [ ] **Step 4: Exécuter JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeOutlinePreparationF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeOutlinePreparationF64Test.kt
rtk git commit -m "feat(math): expand F64 path stroke outlines"
```

---

### Task 4: Orchestration axis-aligned et snapshot F32

**Files:**
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64Test.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillPreparationF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillGeometryF32.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeGeometryF32.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillPreparationF64Test.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeGeometryF32Test.kt`

**Interfaces:**
- Consumes: Tasks 1–3, `Matrix3x3F32`, source `PathFillInputF64` et finalizer fill ledger-aware.
- Produces: projection axis-aligned certifiée et les deux points d'entrée partagés de la section globale.

- [ ] **Step 1: Écrire les tests RED de pipeline**

```kotlin
@Test fun finite_stroke_expands_before_anisotropic_scale_but_hairline_stays_one_device_pixel() {
    val matrix = Matrix3x3F32(sx = 4f, sy = 2f)
    val finite = assertReady(matrix.preparePathStrokeGeometryF32(linePath(), finiteStyle(2.0), PathStrokeDrawMode.Stroke))
    val hairline = assertReady(matrix.preparePathStrokeGeometryF32(linePath(), hairlineStyle(), PathStrokeDrawMode.Stroke))
    assertEquals(4f, finite.geometryF32.copyConservativeBoundsF32().height)
    assertEquals(1f, hairline.geometryF32.copyConservativeBoundsF32().height)
}
```

Ajouter identity, translate, negative X/Y scale, empty, overflow, frame budget,
defensive copies et parity JVM/JS. Prouver aussi que
`preparePathFillGeometryWithStrokeWorkF32` débite avant émission les attempted
units, vertices, indices et snapshot bytes, puis chaîne exactement le snapshot
frame retourné vers la préparation suivante.

- [ ] **Step 2: Vérifier le RED JVM**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*PathStrokeTransformsF64Test' :math:geometry:jvmTest --tests '*PathStrokeGeometryF32Test'
```

- [ ] **Step 3: Implémenter l'orchestration**

Pour finite : préparer dash et outline paramétriques en source, puis subdiviser
chaque intervalle après certification par `PathStrokeProjectionF64`. La borne
device est `maximumMagnificationF64 * outlineSagittaUpperBoundF64`; largeur et
miter sont donc déjà dans la preuve avant flattening. Pour hairline : projeter
les spans de centerline, les subdiviser à `0.25 px`, puis développer 0.5 px de
chaque côté en device. Le même ledger traverse dash, outline, projection,
finalizer fill et publication du snapshot. Le worker fill ledger-aware partagé
avec `preparePathFillGeometryWithStrokeWorkF32` débite les quatre axes avant de
créer les tableaux finaux. Le chemin public W4c existant reste compatible, mais
la lane mixte W4d appelle exclusivement ce nouveau point d'entrée;
`PathFillGeometryF32.snapshotByteCostI64` expose son coût exact checked pour les
validations planner. Canonicaliser `-0.0` et valider les coefficients avant
classification.

```kotlin
val projectionF64: PathStrokeProjectionF64 = AxisAlignedPathStrokeProjectionF64.of(this)
return prepareProjectedPathStrokeGeometryF32(
    inputF64 = PathFillInputF64.fromPathF32(path),
    styleF64 = styleF64,
    mode = mode,
    projectionF64 = projectionF64,
    policyF64 = policyF64,
    frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
)
```

- [ ] **Step 4: Exécuter les modules math JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src math/matrix/src
rtk git commit -m "feat(math): prepare device path strokes"
```

---

### Task 5: Union topologique `STROKE_AND_FILL`

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathStrokeAndFillPreparationF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathTopologyWorkDebitI64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- Modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathStrokeAndFillPreparationF64Test.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64Test.kt`

**Interfaces:**
- Consumes: device fill input, device stroke outline et ledger Task 1.
- Produces: une seule `PathStrokeGeometryF32` d'union ou un résultat explicite.

```kotlin
internal fun interface PathTopologyWorkDebitI64 {
    public fun debitBeforeTopologyWorkI64(unitCountI64: Long)
}

internal sealed interface PathTopologyUnionResult {
    public data class Ready(public val inputF64: PathFillInputF64) : PathTopologyUnionResult
    public data object Limit : PathTopologyUnionResult
}

internal fun preparePathStrokeAndFillUnionF64(
    deviceFillInputF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokePreparationResult
```

- [ ] **Step 1: Écrire les tests RED d'union**

Cas obligatoires : contour simple, trou d'orientation opposée, auto-intersection,
alpha semi-transparent observé une fois, largeur zéro égale au fill, limite
topologique.

```kotlin
@Test fun opposite_hole_winding_cannot_cancel_stroke_and_fill_union() {
    val ready = assertReady(identity.preparePathStrokeGeometryF32(
        donutPath(), finiteStyle(4.0), PathStrokeDrawMode.StrokeAndFill,
    ))
    val copiedContours = copyClosedContours(ready.geometryF32)
    assertTrue(independentWindingContains(copiedContours, Point2F32(1f, 5f)))
    assertFalse(independentWindingContains(copiedContours, Point2F32(5f, 5f)))
}
```

`copyClosedContours` utilise uniquement les méthodes publiques de copie du
snapshot; `independentWindingContains` est un oracle local au test, distinct du
moteur topologique de production.

- [ ] **Step 2: Vérifier le RED**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathStrokeAndFillPreparationF64Test' :math:matrix:jvmTest --tests '*PathStrokeTransformsF64Test'
```

- [ ] **Step 3: Implémenter l'union certifiée**

Composer le fill device non inverse et l'outline device via le moteur
topologique math. Son broad phase, ses intersections et ses émissions reçoivent
`PathTopologyWorkDebitI64`, implémenté par le ledger stroke, et débitent avant
chaque action. Publier une unique géométrie Winding. Un refus devient
`ResourceLimitExceeded(TopologyLimit)`; ne jamais substituer plusieurs
producers stencil.

```kotlin
if (styleF64.widthF64 == PathStrokeWidthF64.Finite(0.0)) {
    return finalizeDeviceFillWithStrokeLedgerF32(deviceFillInputF64, policyF64, ledgerI64)
}
return when (val union = unionClosedContoursF64(
    deviceFillInputF64, deviceStrokeOutlineF64, PathTopologyWorkDebitI64(ledgerI64::debitTopologyBeforeEmissionI64),
)) {
    is PathTopologyUnionResult.Ready -> finalizeUnionGeometryF32(union.inputF64, policyF64, ledgerI64)
    is PathTopologyUnionResult.Limit -> PathStrokePreparationResult.ResourceLimitExceeded(
        PathStrokeResourceLimitReason.TopologyLimit,
    )
}
```

- [ ] **Step 4: Exécuter math JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src math/matrix/src
rtk git commit -m "feat(math): union stroke and fill geometry"
```

---

### Task 6: RenderGraph W4d et ressources typées

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`

**Interfaces:**
- Consumes: `PathStrokeGeometryF32`.
- Produces: `PathStrokeDraw`, `PathDrawGeometry.Fill/Stroke`, graph validation des groupes atomiques.

```kotlin
public sealed interface PathDrawGeometry {
    public data class Fill(public val valueF32: PathFillGeometryF32) : PathDrawGeometry
    public data class Stroke(public val valueF32: PathStrokeGeometryF32) : PathDrawGeometry
}

public class PathStrokeDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometryF32: PathStrokeGeometryF32,
    scissorI32: RectI32,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public fun copyGeometryF32(): PathStrokeGeometryF32
    public fun copyScissorI32(): RectI32

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathStrokeGeometryF32,
            scissorI32: RectI32,
        ): PathStrokeDraw
    }
}
```

- [ ] **Step 1: Écrire les tests RED de graph**

Tester snapshots défensifs, ordinals, mixture fill/stroke, unique producer/cover
pour `STROKE_AND_FILL`, adjacency atomic group, D24S8 unique, dépendances et
rejet d'une géométrie/ressource incohérente.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*W4d*'
```

- [ ] **Step 3: Implémenter les contrats minimaux**

Généraliser les passes path existantes pour accepter une géométrie scellée fill
ou stroke sans déplacer les types math. Conserver les identités W4c existantes
inchangées.

La factory `PathStrokeDraw.of(...)` valide `commandIndex >= 0` et un scissor
non vide, puis capture des snapshots défensifs de la géométrie et du scissor.

- [ ] **Step 4: Exécuter `:gpu-plan:test`**

```bash
rtk ./gradlew :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src/main gpu-plan/src/test
rtk git commit -m "feat(gpu-plan): model W4d stroke draws"
```

---

### Task 7: Compiler, budgets et diagnostics W4d

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PathStrokePlanBudget.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dPathStrokePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GpuPlanCompiler.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/PathStrokePlanBudgetTest.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4dPathStrokePlanCompilerTest.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt`

**Interfaces:**
- Consumes: Tasks 1–6, `SceneSnapshot`, `PlanCapabilitySnapshot`, `PlanBudget`.
- Produces: `W4dPathStrokePlanCompiler : GpuPlanCompiler` et capability exacte.

- [ ] **Step 1: Écrire les tests RED de sélection**

Prouver 1/512/513 draws, frame mixte avec au moins un stroke, styles/caps/joins,
dash, hairline, `STROKE_AND_FILL`, empty, invalid, budgets, unsupported effects,
inverse/AA/general transforms/complex clips encore hors scope, et ordre de la
compiler chain W4d après W4c. Les scopes sont disjoints car W4d exige au moins
un stroke; une frame fill-only continue donc d'être possédée par W4c.

- [ ] **Step 2: Exécuter le RED ciblé**

```bash
rtk ./gradlew :gpu-plan:test --tests '*W4dPathStrokePlanCompilerTest' --tests '*PathStrokePlanBudgetTest' --tests '*CapabilityCompilerChainTest'
```

- [ ] **Step 3: Implémenter selection/plan checked**

Le compiler orchestre uniquement les APIs `:math`. Toutes les multiplications
bytes/counts utilisent arithmetic checked. Après sélection W4d, invalidité,
limite ou capacité absente produit son résultat explicite et ne retombe pas sur
W4c/legacy. Un unique `frameWorkUsageI64` est chaîné dans l'ordre des draws.
Un fill est préparé par `preparePathFillGeometryWithStrokeWorkF32`, qui débite
avant émission attempted units, vertices, indices et snapshot bytes puis
retourne `frameWorkUsageAfterI64`; un stroke reprend et retourne le même type
de snapshot. Ainsi une frame mixte ne réinitialise ni ne post-comptabilise
aucun des quatre axes.

```kotlin
override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection =
    recognizeW4dStrokeFrame(scene, target)

override fun plan(
    candidate: GpuPlanCandidate,
    capabilities: PlanCapabilitySnapshot,
    budget: PlanBudget,
): RenderPlanResult<RenderGraph> {
    val sealed = candidate as? W4dPathStrokeCandidate
        ?: return invalidPlanResult("candidate-type")
    return planCheckedW4dStrokeFrame(sealed, capabilities, budget)
}

private fun checkedFrameBytesI64(vertexI64: Long, indexI64: Long, uniformI64: Long): Long =
    Math.addExact(Math.addExact(vertexI64, indexI64), uniformI64)
```

- [ ] **Step 4: Exécuter planner + math**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src/main gpu-plan/src/test
rtk git commit -m "feat(gpu-plan): compile bounded W4d strokes"
```

---

### Task 8: Lowering et autorité préparée W4d

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dPathStrokeGraphLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dPreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4dPreparedFrameTaskListAssembler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4dTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUCorePrimitivePayloadContractsTest.kt`

**Interfaces:**
- Consumes: graph W4d scellé.
- Produces: payloads V/I/Uniform32 et scratch exact authentifiés par canonical identity.

- [ ] **Step 1: Écrire les tests RED lowering**

Tester direct/stencil, mixture fill/stroke, dash/hairline/union, ordre, forged
graph, mauvaise capability, scratch insuffisant, et absence de reclassification
observable dans le résultat.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4dTest'
```

- [ ] **Step 3: Implémenter le lowering mécanique**

Copier les snapshots scellés vers l'ABI path native existante ou une extension
strictement nécessaire. Ajouter `GPUPathSourceAuthority.W4dPlannedPathStrokeV1`;
le style est scellé dans l'autorité/scratch W4d et ne devient pas un
`GPUCorePrimitiveStrokeStyle` legacy. Ne pas appeler les contracts legacy de
stroke/hairline.

```kotlin
val authority = GPUPlanW4dPreparedAuthority.of(
    planIdentity = plan.identity,
    sourceAuthority = GPUPathSourceAuthority.W4dPlannedPathStrokeV1,
    vertexBytesI64 = plan.vertexBytesI64,
    indexBytesI64 = plan.indexBytesI64,
    uniformBytesI64 = plan.uniformBytesI64,
)
return GPUCorePrimitiveW4dPreparedFrameTaskListAssembler.assemble(plan, authority)
```

- [ ] **Step 4: Exécuter les tests renderer ciblés**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*'
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(gpu-renderer): lower sealed W4d strokes"
```

---

### Task 9: Preflight, matérialisation, pool et rollback

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveW4dFramePayloadMaterializerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`

**Interfaces:**
- Consumes: prepared W4d authority et scratch exact.
- Produces: native frame exécutable avec leases jusqu'à completion/readback.

- [ ] **Step 1: Écrire les tests RED natifs**

Prouver bytes exacts, offsets, pipeline keys, D24S8 seulement si requis,
capacités poolées, ordre de passes, lease jusqu'au readback et rollback à chaque
point d'échec avant submit.

- [ ] **Step 2: Exécuter le RED ciblé**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*W4d*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
```

- [ ] **Step 3: Implémenter la matérialisation minimale**

Étendre les matchers/pipelines path uniquement pour l'autorité W4d. Toute
discordance retourne un refus terminal avant submit et libère toutes les
acquisitions de la tentative.

```kotlin
val transaction = framePool.beginW4dTransaction(prepared.requirements)
return try {
    transaction.acquireAll()
    val frame = materializeW4dFrame(prepared, transaction)
    transaction.commit(frame)
} catch (failure: Throwable) {
    transaction.rollback()
    throw failure
}
```

- [ ] **Step 4: Exécuter la gate renderer W4d**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(gpu-renderer): materialize W4d stroke frames"
```

---

### Task 10: Surface générique, oracle CPU et preuve pixel

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4dPathStrokeCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapterTest.kt`

**Interfaces:**
- Consumes: capability W4d end-to-end.
- Produces: routage public et pixels byte-exact hors GM.

- [ ] **Step 1: Écrire les tests RED Surface**

Fixtures : trois caps, trois joins, miter/bevel, dash phase négative, hairline
sous scale 4×, `STROKE_AND_FILL` donut alpha 50 %, mixed fill/stroke order,
scissor, RGBA/BGRA, frontière 512/513 et refus transactionnel.

L'oracle part de `PathF32`/Paint publics, applique son propre dash/outline
simple pour les fixtures choisies et ne peut importer `gpu.plan`,
`gpu.renderer` ou les helpers math de production.

- [ ] **Step 2: Exécuter le RED ciblé**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*W4d*' --tests '*GPUPlanSurfaceRouterTest*W4d*' --tests '*DisplayOpSceneAdapterTest*'
```

- [ ] **Step 3: Prouver le branchement Surface générique**

Ne modifier ni `GPUPlanSurfaceCandidateGate` ni `GPUPlanSurfaceRouter` : ils
admettent déjà `DrawPath` et transmettent la Scene IR au compiler chain. La
preuve montre que l'ajout du compiler dans `GpuRenderContext` suffit et que
seule la sélection `Ready` scellée atteint le renderer.

```kotlin
@Test fun w4d_public_surface_uses_the_general_scene_route() {
    val result = renderPublicStrokeScene(dashedHairlineScene())
    assertEquals(W4D_EXPECTED_RGBA8, result.copyPixelsRgba8())
    assertEquals(W4D_STROKE_CAPABILITY, result.planCapabilityId)
}
```

- [ ] **Step 4: Exécuter les gates Surface**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
```

Expected: aucune failure W4d; seules les failures exactes du ledger W4c sont
acceptables.

- [ ] **Step 5: Commit Terra**

```bash
rtk git add kanvas/src/test
rtk git commit -m "feat(kanvas): route W4d strokes through Surface"
```

---

### Task 11: Vérification finale, suivi et PR stackée

**Files:**
- Modify: `refactor/README.md`
- Modify: `refactor/waves/W04-geometry-coverage/status.md`
- Modify: ce plan

- [x] **Step 1: Exécuter les gates fraîches**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
```

La gate Kanvas globale est hors portée de cette clôture documentaire. La gate
Surface filtrée reproduit 2 073 tests, 45 failures historiques DrawPoint et 0
error ; elle ne contient aucun échec W4d.1.

- [x] **Step 2: Vérifier XML, scope et diff**

```bash
rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
rtk git diff --check codex/w4c-path-fills...HEAD
rtk git diff --name-only codex/w4c-path-fills...HEAD
```

Confirmer zéro nouveau nom de failure/error et aucun fichier exclu.

- [x] **Step 3: Publier le status W4d.1**

Documenter capability, architecture, limites, ressources, preuves fraîches,
ledger exact et ouverture W4d.2/W4e. Commit :

```bash
rtk git add refactor
rtk git commit -m "docs(refactor): publish W4d stroke evidence"
```

- [ ] **Step 4: Sol final review (contrôleur)**

Sol relit `codex/w4c-path-fills...HEAD`. Corriger tous les findings
Critical/Important avec un fresh Terra et refaire les gates proportionnées
jusqu'à `Approved`.

- [ ] **Step 5: Pousser et créer la PR stackée (contrôleur)**

Créer par `apply_patch` un body complet avec `## Summary`, `## Verification`,
`## Scope and follow-ups`, puis :

```bash
rtk git push -u origin codex/w4d-strokes-hairlines
rtk gh pr create --base codex/w4c-path-fills --head codex/w4d-strokes-hairlines --title "feat: add W4d path strokes and hairlines" --body-file /tmp/w4d-pr-body.md
```

Ne pas merger, rebaser, lancer GM/Skia ou supprimer le worktree.
