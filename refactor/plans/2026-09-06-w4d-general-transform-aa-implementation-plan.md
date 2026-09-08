# W4d.2 General Transforms and Path AA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Étendre les path fills/strokes W4c–W4d aux transforms affine généraux et perspectives bornées, puis livrer une coverage AA 4× explicite et résolue pour des frames path mixtes.

**Architecture:** `:math:matrix` possède un snapshot `Matrix3x3F64`, la classification et la projection homogène certifiée ; `:math:geometry` conserve flattening, stroke et snapshots device-space. `:gpu-plan` choisit un graph hard-edge 1× ou MSAA 4× selon la frame, scelle targets/D24S8/resolve et budgets, puis le renderer matérialise les pipelines existants uniquement sous l'autorité du graph.

**Tech Stack:** Kotlin multiplatform JVM/JS, interval arithmetic F64, `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGPU4K, Gradle, `rtk`.

**Spec:** `refactor/specs/2026-09-06-w4-remaining-geometry-coverage-design.md`

## Global Constraints

- Branche : `codex/w4d-general-transform-aa`, empilée sur `codex/w4d-strokes-hairlines`; la PR cible cette branche W4d.1.
- Capabilities exactes :
  - `solid-path-geometry-hard-1x-general-transform-simple-scissor-src-over-srgb-v1` pour une frame entièrement hard-edge ;
  - `solid-path-geometry-mixed-aa4-general-transform-simple-scissor-src-over-srgb-v1` dès qu'au moins un draw est AA; chaque draw demandé hard-edge passe alors par un mask binaire 1× échantillonné sans filtrage par un cover 4×, afin de ne pas acquérir une couverture MSAA fractionnaire.
- La lane accepte 1–512 path draws fill/stroke/`STROKE_AND_FILL`, fill rules non inverse, material solid, `SrcOver`, sRGB 1×, layer racine et clip vide/scissor I32 non-AA.
- Identity, scale/translate, affine rotation/skew/reflection et perspective bornée passent par la même préparation F64; aucune matrice n'est classée par `String`.
- Un intervalle projectif dont `w` contient réellement zéro est `PerspectiveHorizonCrossing` avant `Ready`; aucune approximation affine.
- Flèche device-space `0.25 px`, profondeur 32, limites d'arêtes W4c/W4d inchangées et débit avant émission.
- Une frame contenant au moins un draw AA utilise color target + D24S8 MSAA 4× et un resolve explicite. Si elle contient aussi un draw hard-edge, elle possède un mask binaire linéaire 1× réutilisable et, lorsqu'un tel path requiert stencil, une D24S8 1×; aucun rendu AA implicite d'un draw hard-edge.
- Toute géométrie dans `:math`, nomenclature I/F32/64, snapshots défensifs, arithmetic checked.
- Aucun fallback/recalcul après `Ready`; rollback complet avant submit.
- Tests comportementaux/publics uniquement; aucun test d'infrastructure, source-shape, reflection, accès privé ou call-count.
- `font`, `codec`, GM Skia, dashboard, baselines et `jpg-color-cube` restent hors scope; aucun seuil/tolérance ajouté.
- Fresh Terra par tâche, Sol spec+quality read-only après chaque commit.

## État d'exécution — 2026-09-08

| Task | État vérifié | Révision de clôture / preuve |
| --- | --- | --- |
| 1 — matrice F64 | Terminé | `a37d90a8f`, revue Sol CLEAN |
| 2 — affine général | Terminé | `11a31a66b`, revue Sol CLEAN |
| 3 — perspective bornée | Terminé | `33d19fa12`, corrections de tangence et revue Sol CLEAN |
| 4 — préparation commune | Terminé avec gap PathOps suivi | `deb3efe00`, revue Sol CLEAN ; `StrokeAndFill` projectif non vide peut retourner `TopologyLimit` |
| 5 — contrats MSAA/resolve | Terminé | `067958ca0`, revue Sol CLEAN |
| 6 — compiler/budgets | Terminé | `3c801385f`, revue Sol CLEAN |
| 7 — lowering/autorité | Terminé | `92c219a0b`, revue Sol CLEAN |
| 8 — matérialisation/pool | Implémenté ; preuves de gates présentes | `bafbd4019` puis correctifs d'autorité, préflight et générations de depth ; revue Sol globale CLEAN après corrections documentaires |
| 9 — Surface/oracle | Terminé | `42efea430`, revue Sol CLEAN ; AA4 réel reste terminal sur le runtime courant |
| 10 — vérification/documentation | Terminé | gates, ledger XML et suivi publiés dans `9849f12` ; revue Sol finale APPROVED/CLEAN ; PR stackée [#2392](https://github.com/ygdrasil-io/kanvas/pull/2392) ouverte et mergeable vers `codex/w4d-strokes-hairlines` |

Les cases de conception ci-dessous décrivent le déroulé originel ; ce tableau
est le ledger d'exécution autoritaire après les corrections et revues.

---

## Carte de fichiers

| Responsabilité | Créations principales | Modifications principales |
|---|---|---|
| Transform F64 | `Matrix3x3F64.kt`, `PathAffineTransformsF64.kt`, `PathProjectivePreparationF64.kt`, `PathProjectiveIntervalsF64.kt` | `PathFillTransformsF64.kt`, `PathStrokeTransformsF64.kt` |
| Préparation commune | `PathGeometryPreparationF64.kt` | snapshots W4c/W4d déjà possédés par `:math` |
| Graph/planner AA | `W4dGeneralPathPlanCompiler.kt`, `PathAaPlanBudget.kt`, `W4dGeneralPlanDiagnostics.kt` | capabilities, resources, passes, graph, compiler chain |
| Lowering/native | `W4dGeneralPathGraphLowerer.kt`, `GPUPlanW4dGeneralPreparedAuthority.kt` | lowering, preflight, materializers, executor, frame pool |
| Surface/preuves | `W4dGeneralPathCpuOracle.kt` | refus legacy, router tests, pixel tests |
| Suivi | ce plan | `refactor/README.md`, status W04 |

## Interfaces partagées

```kotlin
public data class Matrix3x3F64(
    public val sxF64: Double = 1.0,
    public val kxF64: Double = 0.0,
    public val txF64: Double = 0.0,
    public val kyF64: Double = 0.0,
    public val syF64: Double = 1.0,
    public val tyF64: Double = 0.0,
    public val persp0F64: Double = 0.0,
    public val persp1F64: Double = 0.0,
    public val persp2F64: Double = 1.0,
)

public enum class PathTransformClass {
    Identity, AxisAlignedAffine, GeneralAffine, Perspective
}

public enum class PathProjectiveInvalidSceneReason {
    NonFiniteMatrix, NonFiniteProjection, PerspectiveHorizonCrossing
}

public enum class PathProjectiveResourceLimitReason {
    FlatteningDidNotConverge, PathWorkLimit, FrameWorkLimit,
    SnapshotByteLimit, RasterBoundsOverflow
}

public sealed interface PathProjectivePreparationResult {
    public data class Ready(
        public val inputF64: PathFillInputF64,
        public val transformClass: PathTransformClass,
        public val pathWorkUsageAfterI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathProjectivePreparationResult
    public data class Empty(
        public val pathWorkUsageAfterI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathProjectivePreparationResult
    public data class InvalidScene(public val reason: PathProjectiveInvalidSceneReason) : PathProjectivePreparationResult
    public data class ResourceLimitExceeded(public val reason: PathProjectiveResourceLimitReason) : PathProjectivePreparationResult
}

public fun Matrix3x3F32.toMatrix3x3F64(): Matrix3x3F64

public fun Matrix3x3F64.prepareProjectedPathFillInputF64(
    path: PathF32,
    policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    workPolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathProjectivePreparationResult

public fun Matrix3x3F64.toPathStrokeProjectionF64(): PathStrokeProjectionF64
```

Planner additions:

```kotlin
public enum class CoveragePlan { FullOrScissor, AnalyticScalarAA, StencilAA4, BinaryMaskCover4 }
public enum class SamplePlan { SingleSample, Multisample4 }
public enum class PlanCoverageMaskFormat { RGBA8_UNORM_LINEAR }
public sealed interface PlanTextureFormat {
    public data class Color(public val value: PlanLogicalColorFormat) : PlanTextureFormat
    public data class DepthStencil(public val value: PlanDepthStencilFormat) : PlanTextureFormat
    public data class CoverageMask(public val value: PlanCoverageMaskFormat) : PlanTextureFormat
}
public enum class PlanResourceRole {
    LogicalTarget, MultisampleColorTarget, ReadbackStaging,
    VertexData, IndexData, UniformData, DepthStencil,
    PathHardEdgeMask, PathHardEdgeDepthStencil
}

public data class PlanTextureSampleSupport(
    public val format: PlanTextureFormat,
    public val sampleCountI32: Int,
    public val usages: Set<PlanResourceUsage>,
)

public data class PlanTextureResolveSupport(
    public val format: PlanTextureFormat,
    public val sourceSampleCountI32: Int,
    public val destinationSampleCountI32: Int = 1,
)
```

`PlanResourceUsage.Sampled` est ajouté pour les masks lus par un cover.

Chaque texture `PlanResource` porte `sampleCountI32`. Un nouveau
`GeneralPathDraw` porte la paire coverage/sample explicite sans assouplir les
factories W4c. `PlanPass.PathRenderPass` porte le sample count, l'atomic group
et un `resolveTarget` optionnel : sous AA4, le producer stencil n'a pas de
resolve et exactement le dernier color-producing direct/cover de la frame
résout vers `LogicalTarget` à la fin de son render pass. Le `ResolvePass`
générique existant reste inchangé pour ses
consommateurs historiques et n'est pas utilisé par W4d.2.

Dans une frame AA4, un `GeneralPathDraw` hard-edge reste `SingleSample` et
produit un `PathHardEdgeMask` 1×, directement ou via
`PathHardEdgeDepthStencil` 1×. Un `BinaryMaskedPathDraw` consomme ce mask par
`textureLoad` aux coordonnées pixel entières et couvre un quad aligné sur le
scissor dans la target 4× : la valeur 0/1 est donc diffusée identiquement aux
quatre samples. Le producer et le cover forment un groupe atomique par draw;
le mask peut être réutilisé entre groupes et l'ordre `SrcOver` reste celui de
la scène.

```kotlin
public enum class PathRenderStage { DirectOrCover, StencilProducer }

public class GeneralPathDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometry: PathDrawGeometry,
    scissorI32: RectI32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
) : PlanDraw {
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public fun copyGeometry(): PathDrawGeometry
    public fun copyScissorI32(): RectI32
    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometry: PathDrawGeometry,
            scissorI32: RectI32,
            coverage: CoveragePlan,
            sample: SamplePlan,
        ): GeneralPathDraw
    }
}

public class BinaryMaskedPathDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    public val mask: PlanResourceId,
    scissorI32: RectI32,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.BinaryMaskCover4
    override public val sample: SamplePlan = SamplePlan.Multisample4
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public fun copyScissorI32(): RectI32
    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            mask: PlanResourceId,
            scissorI32: RectI32,
        ): BinaryMaskedPathDraw
    }
}

public class PathRenderPass(
    override val ordinal: Int,
    public val target: PlanResourceId,
    public val resolveTarget: PlanResourceId?,
    public val depthStencil: PlanResourceId?,
    draws: List<PlanDraw>,
    public val sampleCountI32: Int,
    public val atomicGroup: PlanAtomicGroupId,
    public val stage: PathRenderStage,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
) : PlanPass {
    override val role: PlanPassRole = when (stage) {
        PathRenderStage.DirectOrCover -> PlanPassRole.MainRender
        PathRenderStage.StencilProducer -> PlanPassRole.StencilProducer
    }
    override val id: PlanPassId = checkedPassId(role, ordinal)
    public fun draws(): List<PlanDraw>
}
```

`GeneralPathDraw.of` accepte `FullOrScissor` uniquement en 1× et accepte
`StencilAA4` seulement avec `Multisample4`; `BinaryMaskCover4` appartient
uniquement à `BinaryMaskedPathDraw`. Le validator W4d.2 limite les listes
`PlanDraw` aux deux types ci-dessus; W4e étendra explicitement cette whitelist
aux wrappers clip et inverse sans changer le type du pass. `PathRenderPass`
exige en AA4 une
target `MultisampleColorTarget`; `StencilProducer` interdit `resolveTarget`,
tandis que seul le dernier pass physique `DirectOrCover` AA4 exige le
`LogicalTarget` 1× comme resolve target; tous les color passes antérieurs le
laissent absent et chargent/conservent la target MSAA. En 1×, `resolveTarget`
est absent. Le validateur W4c continue de
refuser `PathRenderPass` et tout resolve, sans modification de son contrat.

---

### Task 1: `Matrix3x3F64` et classification exacte

**Files:**
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F64Test.kt`

**Interfaces:**
- Consumes: `Matrix3x3F32`.
- Produces: `Matrix3x3F64`, `PathTransformClass`, `toMatrix3x3F64`, `classifyPathTransform()`.

- [ ] **Step 1: Écrire les tests RED F64**

```kotlin
@Test fun classification_distinguishes_general_affine_and_perspective_without_tolerance() {
    assertEquals(PathTransformClass.GeneralAffine, Matrix3x3F64(kxF64 = 1e-12).classifyPathTransform())
    assertEquals(PathTransformClass.Perspective, Matrix3x3F64(persp0F64 = 1e-15).classifyPathTransform())
}

@Test fun conversion_preserves_all_f32_coefficients_exactly_as_f64_and_canonicalizes_negative_zero() {
    val mapped = Matrix3x3F32(sx = -0.0f, kx = 0.25f, persp1 = 0.5f).toMatrix3x3F64()
    assertEquals(0.0.toBits(), mapped.sxF64.toBits())
    assertEquals(0.25, mapped.kxF64)
    assertEquals(0.5, mapped.persp1F64)
}
```

- [ ] **Step 2: Exécuter le RED JVM**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test'
```

- [ ] **Step 3: Implémenter snapshot/classification**

Valider les neuf coefficients finis au point d'usage. La classification utilise
des égalités IEEE exactes sur les zéros canoniques; aucune epsilon ne peut
transformer un affine/perspective en classe plus simple.

```kotlin
public fun Matrix3x3F64.classifyPathTransform(): PathTransformClass = when {
    persp0F64 != 0.0 || persp1F64 != 0.0 || persp2F64 != 1.0 -> PathTransformClass.Perspective
    kxF64 != 0.0 || kyF64 != 0.0 -> PathTransformClass.GeneralAffine
    sxF64 != 1.0 || syF64 != 1.0 || txF64 != 0.0 || tyF64 != 0.0 -> PathTransformClass.AxisAlignedAffine
    else -> PathTransformClass.Identity
}
```

- [ ] **Step 4: Exécuter JVM/JS**

```bash
rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/matrix/src
rtk git commit -m "feat(math): add typed F64 path transforms"
```

---

### Task 2: Mapping affine général des fills et strokes

**Files:**
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathAffineTransformsF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathAffineTransformsF64Test.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathFillTransformsF64.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64.kt`

**Interfaces:**
- Consumes: Task 1, W4c fill input et W4d stroke outlines.
- Produces: mapping interne ledger-aware des fills et projection pure des outlines/centerlines F64.

```kotlin
internal fun interface PathTransformWorkDebitI64 {
    public fun debitBeforeTransformWorkI64(deltaI64: PathStrokeWorkUsageI64)
}

internal fun Matrix3x3F64.mapAffinePathFillInputF64(
    inputF64: PathFillInputF64,
    debitI64: PathTransformWorkDebitI64,
): PathFillInputF64
```

- [ ] **Step 1: Écrire les tests RED affine**

Tester rotation 90°/arbitraire, skew infime, reflection, anisotropie, SVG arc
rotée et stroke outline. Vérifier les extrema avec un oracle matriciel F64
indépendant.

```kotlin
@Test fun nonzero_skew_never_uses_axis_aligned_arc_shortcut() {
    val result = mapAffineForTestF64(Matrix3x3F64(kxF64 = 1e-12), rotatedArcPath())
    assertArcSupportMatchesCovariance(result, maximumErrorF64 = 0.25)
}
```

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*PathAffineTransformsF64Test'
```

- [ ] **Step 3: Implémenter le mapping affine F64**

Généraliser la covariance W4c aux deux vecteurs axes transformés. Les lignes et
controls sont mappés directement. Les outlines restent des contours math ; ne
pas les convertir en payload renderer. Le helper fill est interne et exige un
`PathTransformWorkDebitI64`; il débite attempted units et snapshot bytes avant
chaque évaluation ou allocation. La façade publique de production reste
`prepareProjectedPathFillInputF64`, qui publie les snapshots path/frame.

```kotlin
private fun Matrix3x3F64.mapAffinePointF64(pointF64: Point2F64): Point2F64 = Point2F64(
    x = sxF64 * pointF64.x + kxF64 * pointF64.y + txF64,
    y = kyF64 * pointF64.x + syF64 * pointF64.y + tyF64,
)
```

- [ ] **Step 4: Exécuter math matrix JVM/JS**

```bash
rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/matrix/src
rtk git commit -m "feat(math): map general affine path geometry"
```

---

### Task 3: Projection perspective bornée

**Files:**
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathProjectivePreparationF64.kt`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathProjectiveIntervalsF64.kt`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathProjectiveStrokeProjectionF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathProjectivePreparationF64Test.kt`

**Interfaces:**
- Consumes: Tasks 1–2, `PathF32`, `PathFillFlatteningPolicyF64`, `PathTransformWorkDebitI64`, `PathStrokeOutlineIntervalF64` et interval arithmetic math.
- Produces: `prepareProjectedPathFillInputF64`, `toPathStrokeProjectionF64` et raisons projectives stables.

```kotlin
internal data class PathProjectiveIntervalF64(
    public val minimumF64: Double,
    public val maximumF64: Double,
)
```

- [ ] **Step 1: Écrire les tests RED perspective**

Cas : ligne/quad/cubic/arc fill et intervalles d'outline/centerline stroke avec
`w>0`, `w<0`, extrema après divide, `w` proche de zéro mais séparé, crossing
réel, non-fini, profondeur et budgets. Prouver que la projection débite son
`PathStrokeWorkUsageI64` avant chaque évaluation/subdivision et que le snapshot
path/frame retourné peut être transmis à la finalisation fill sans remise à
zéro des limites path ou frame.

```kotlin
@Test fun certified_negative_w_path_is_projected_but_horizon_crossing_is_classified() {
    assertIs<PathProjectivePreparationResult.Ready>(negativeWMatrix.prepareProjectedPathFillInputF64(curvePath()))
    val crossing = assertIs<PathProjectivePreparationResult.InvalidScene>(horizonMatrix.prepareProjectedPathFillInputF64(lineAcrossHorizon()))
    assertEquals(PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing, crossing.reason)
}
```

- [ ] **Step 2: Exécuter le RED JVM**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*PathProjectivePreparationF64Test'
```

- [ ] **Step 3: Implémenter évaluation homogène + intervalles**

Évaluer `(X(t), Y(t), W(t))`, utiliser des intervalles à arrondi sortant pour
certifier `0 ∉ W([t0,t1])`, projeter les endpoints/midpoints, puis subdiviser
jusqu'à `0.25 px`. L'adaptateur `PathStrokeProjectionF64` ne subdivise jamais
lui-même : il projette un point ou renvoie la borne device/horizon/non-fini
d'un `PathStrokeOutlineIntervalF64`; `:math:geometry` décide et débite chaque
split. Une arc perspective est évaluée paramétriquement; ne pas fabriquer une
ellipse post-projection. La préparation fill projective crée un ledger local à
partir des snapshots path/frame reçus, vérifie les limites W4d avant chaque
travail, puis publie les snapshots after dans `Ready`/`Empty`; le finalizer fill
reprend ces deux snapshots via `preparePathFillGeometryWithStrokeWorkF32`.

```kotlin
private fun projectCertifiedPointF64(xF64: Double, yF64: Double, wF64: Double): Point2F64 {
    require(wF64.isFinite() && wF64 != 0.0)
    return Point2F64(xF64 / wF64, yF64 / wF64)
}

private fun intervalCrossesHorizon(intervalF64: PathProjectiveIntervalF64): Boolean =
    intervalF64.minimumF64 <= 0.0 && intervalF64.maximumF64 >= 0.0

public fun Matrix3x3F64.toPathStrokeProjectionF64(): PathStrokeProjectionF64 =
    MatrixPathStrokeProjectionF64(this)
```

- [ ] **Step 4: Exécuter JVM/JS**

```bash
rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/matrix/src
rtk git commit -m "feat(math): project bounded perspective paths"
```

---

### Task 4: Préparation commune fill/stroke sous transform général

**Files:**
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathFillTransformsF64.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathStrokeTransformsF64.kt`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathGeometryPreparationF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathGeometryPreparationF64Test.kt`

**Interfaces:**
- Consumes: Tasks 1–3 et W4c/W4d math preparation.
- Produces: `preparePathFillGeometryF32`/`preparePathStrokeGeometryF32` capables de toutes les classes typées.

- [ ] **Step 1: Écrire les tests RED end-to-end math**

Prouver fill, finite stroke, dash, hairline et `STROKE_AND_FILL` sous
rotation/skew/perspective. La hairline reste un pixel après projection; le
finite stroke est développé source avant projection.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*PathGeometryPreparationF64Test'
```

- [ ] **Step 3: Dispatcher par classe sans duplication géométrique**

AxisAligned réutilise W4c/W4d. GeneralAffine utilise Task 2. Perspective
utilise `toPathStrokeProjectionF64()` de Task 3 : finite stroke fournit son
outline source paramétrique à l'adaptateur, hairline fournit sa centerline puis
est développée en device. Consolider le `PathStrokeWorkUsageI64` de toutes les
étapes. Les fills passent par `preparePathFillGeometryWithStrokeWorkF32` pour
débiter attempted units, vertices, indices et snapshot bytes dans le même total
de frame avant émission; aucun coût n'est ajouté après la construction du
snapshot.

```kotlin
// Façade fill : projection puis finalisation conservent les deux snapshots.
val projectedFill = prepareProjectedPathFillInputF64(
    path = path,
    policyF64 = fillPolicyF64,
    workPolicyF64 = strokePolicyF64,
    pathWorkUsageBeforeI64 = PathStrokeWorkUsageI64(),
    frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
)
if (projectedFill is PathProjectivePreparationResult.Ready) {
    return preparePathFillGeometryWithStrokeWorkF32(
        inputF64 = projectedFill.inputF64,
        fillPolicyF64 = fillPolicyF64,
        strokePolicyF64 = strokePolicyF64,
        pathWorkUsageBeforeI64 = projectedFill.pathWorkUsageAfterI64,
        frameWorkUsageBeforeI64 = projectedFill.frameWorkUsageAfterI64,
    )
}

// Façade stroke : geometry pilote la subdivision via l'adaptateur matrix.
return when (classifyPathTransform()) {
    PathTransformClass.Identity,
    PathTransformClass.AxisAlignedAffine -> prepareAxisAlignedPathGeometryF32(path, style, policyF64)
    PathTransformClass.GeneralAffine,
    PathTransformClass.Perspective -> prepareProjectedPathStrokeGeometryF32(
        inputF64 = PathFillInputF64.fromPathF32(path),
        styleF64 = style,
        mode = mode,
        projectionF64 = toPathStrokeProjectionF64(),
        policyF64 = policyF64,
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
    )
}
```

- [ ] **Step 4: Exécuter tous les tests math JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/matrix/src math/geometry/src
rtk git commit -m "feat(math): prepare transformed path geometry"
```

---

### Task 5: Sample plans, textures MSAA et resolve dans le graph

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`

**Interfaces:**
- Consumes: existing W4c/W4d graph.
- Produces: sample/resolve support facts, `MultisampleColorTarget`, mask binaire 1×, `Multisample4`, `StencilAA4`, `GeneralPathDraw`, `BinaryMaskedPathDraw`, `PlanPass.PathRenderPass`.

- [ ] **Step 1: Écrire les tests RED graph MSAA**

Tester `sampleCountI32` 1/4, format/sample et `PlanTextureResolveSupport`, target MSAA + D24S8 4×,
`resolveTarget` porté uniquement par le dernier color-producing pass et dans le
même atomic group que son éventuel producer, lifetimes jusqu'au resolve. Pour
chaque hard draw dans une frame 4×, prouver la séquence ordonnée mask 1×
clear/producer[/stencil-cover] puis `BinaryMaskedPathDraw` 4×, le `textureLoad`
non filtré et la même valeur binaire sur les quatre samples. Rejeter toute incohérence
sample/format/pass. Les validators W4c existants continuent de refuser tout
resolve et ne sont pas assouplis.

- [ ] **Step 2: Exécuter le RED planner**

```bash
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*AA4*'
```

- [ ] **Step 3: Implémenter les contrats typés**

Ajouter une preuve `PlanTextureSampleSupport(format, sampleCountI32, usages)`
et une preuve `PlanTextureResolveSupport(format, 4, 1)` dans
`PlanCapabilitySnapshot`. Ajouter `PlanTextureFormat.CoverageMask`,
`PathHardEdgeMask` 1× avec usages RenderAttachment/Sampled et
`PathHardEdgeDepthStencil` D24S8 1×. Leur durée de vie est bornée au groupe
atomique du draw et les mêmes allocations peuvent être réutilisées par les
groupes séquentiels. La taille logique d'une texture multisamplée est
`bytesPerPixel * width * height * sampleCountI32`, checked.

```kotlin
public fun checkedTextureBytesI64(
    bytesPerPixelI32: Int,
    widthI32: Int,
    heightI32: Int,
    sampleCountI32: Int,
): Long = Math.multiplyExact(
    Math.multiplyExact(bytesPerPixelI32.toLong(), widthI32.toLong()),
    Math.multiplyExact(heightI32.toLong(), sampleCountI32.toLong()),
)
```

- [ ] **Step 4: Exécuter `:gpu-plan:test`**

```bash
rtk ./gradlew :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src/main gpu-plan/src/test
rtk git commit -m "feat(gpu-plan): model explicit path MSAA resolve"
```

---

### Task 6: Compiler transforms/AA et budgets

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PathAaPlanBudget.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompilerTest.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/PathAaPlanBudgetTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5 et Scene IR.
- Produces: deux capabilities exactes hard/AA.

- [ ] **Step 1: Écrire les tests RED de selection/plan**

Prouver classes transform, frame mixte hard+AA, all-hard 1×, any-AA 4×,
support device absent, bytes MSAA exacts, perspective horizon, 512/513,
terminalité et préservation des routes W4c/W4d étroites. Dans la frame mixte,
prouver pixel par pixel que les bords hard-edge restent identiques à la lane
1× et n'acquièrent aucune coverage intermédiaire aux positions MSAA.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-plan:test --tests '*W4dGeneralPathPlanCompilerTest' --tests '*PathAaPlanBudgetTest'
```

- [ ] **Step 3: Implémenter le compiler**

Sélectionner la lane générale seulement si au moins un transform dépasse
AxisAlignedAffine ou si un draw demande AA. Un graph all-hard n'alloue ni
target MSAA ni resolve; any-AA alloue une seule target couleur 4× et une D24S8
4×. Il alloue aussi un mask binaire linéaire 1× réutilisable et une D24S8 1×
si au moins un hard path utilise la route stencil. Chaque hard draw devient un
groupe atomique mask-producer → binary color-cover; les draws AA restent sur la
route stencil MSAA. Les groupes suivent strictement l'ordre des commandes et
seul le dernier pass couleur porte le resolve.

Le budget de pic ajoute au coût scène 4× `4 * width * height` bytes pour le
mask RGBA8 linear 1× et, si une route hard stencil existe,
`4 * width * height` bytes pour la D24S8 1×. Ces capacités poolées sont comptées
une fois même si elles sont louées successivement par plusieurs groupes; toutes
les multiplications/additions sont checked.

```kotlin
val samplePlan = if (sealedDraws.any { it.requestsAntiAlias }) {
    SamplePlan.Multisample4
} else {
    SamplePlan.SingleSample
}
return if (samplePlan == SamplePlan.Multisample4) {
    buildAa4PathGraph(sealedDraws, capabilities, budget)
} else {
    buildHardPathGraph(sealedDraws, capabilities, budget)
}
```

- [ ] **Step 4: Exécuter math/planner**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src
rtk git commit -m "feat(gpu-plan): compile transformed AA path frames"
```

---

### Task 7: Lowering et autorité MSAA

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dGeneralPreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUW4dPathSampleContinuationAuthority.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4dGeneralTest.kt`

**Interfaces:**
- Consumes: graph hard/AA général.
- Produces: prepared tasks avec color/D24S8 sample count exact et resolve scellé.

- [ ] **Step 1: Écrire les tests RED lowering**

Cas hard 1×, AA 4×, mixed coverage, fill/stroke/hairline, affine/perspective,
resolve target absent sur tous les passes antérieurs et présent uniquement sur
le dernier cover/direct couleur, sample forgé, mauvaise ressource et scratch.
Prouver aussi `Store + Skip` pour tous les segments AA antérieurs et
`Store + ResolveCanonical` exactement sur le dernier color pass, ainsi que le
lowering du mask hard-edge 1× vers un `textureLoad` non filtré dans le cover 4×.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4dGeneralTest'
```

- [ ] **Step 3: Implémenter le lowering mécanique**

Valider sample count, attachments, `resolveTarget` et atomic group avant toute
conversion. Mapper `SamplePlan.Multisample4` vers
`GPUSamplePlan.MultisampleFrame(4)` et `CoveragePlan.StencilAA4` vers l'autorité
native existante uniquement après validation graph complète.
`CoveragePlan.BinaryMaskCover4` exige le mask 1× authentifié par le graph et un
shader cover qui charge la texel integer correspondante puis écrit la même
couleur/alpha sur tous les samples; aucune interpolation ni filtering.

```kotlin
val gpuSamplePlan = when (planDraw.sample) {
    SamplePlan.SingleSample -> GPUSamplePlan.SingleSampleFrame
    SamplePlan.Multisample4 -> GPUSamplePlan.MultisampleFrame(sampleCount = 4)
}
require(planDraw.coverage != CoveragePlan.StencilAA4 || gpuSamplePlan.sampleCount == 4)

val continuation = GPUW4dPathSampleContinuationAuthority.from(pathRenderPasses)
require(continuation.transitions.dropLast(1).all {
    it.storeAction == GPUSampleStoreAction.Store && it.resolveAction == GPUSampleResolveAction.Skip
})
require(continuation.transitions.last().resolveAction == GPUSampleResolveAction.ResolveCanonical)
```

Ne pas assouplir `GPUSampleContinuationPlanner`, qui continue d'exiger un
resolve par transition pour les lanes historiques. L'autorité W4d.2 est
versionnée, dérivée uniquement du graph validé et vérifiée de nouveau en
preflight.

- [ ] **Step 4: Exécuter renderer ciblé**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4dGeneral*' --tests '*CoverageSampleAuthority*'
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(gpu-renderer): lower transformed AA path plans"
```

---

### Task 8: Matérialisation MSAA, resolve et frame pool

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveW4dGeneralFrameTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt`

**Interfaces:**
- Consumes: Task 7.
- Produces: native target 4×, D24S8 4×, mask hard-edge 1×, D24S8 hard-edge 1× optionnelle, resolve 1× et leases complets.

- [ ] **Step 1: Écrire les tests RED natifs**

Vérifier sample counts, formats, load/store, resolve target, aucun readback avant
resolve, pool key incluant sample count, completion lifetime et rollback à
chaque échec d'acquisition/pipeline/encoder. Les preflights historiques gardent
leur refus de `Skip`; seule l'autorité W4d.2 authentifiée admet la séquence
`Skip...ResolveCanonical`. Vérifier aussi la remise à zéro et la réutilisation
séquentielle du mask hard-edge/D24S8 1×, ainsi que leur présence dans le budget
physique de pic et dans le rollback.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*W4dGeneral*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
```

- [ ] **Step 3: Matérialiser les ressources/passes scellées**

Réutiliser `StencilAA`/MSAA existants sans leur laisser recalculer coverage ou
sample plan. Les passes AA4 antérieures matérialisent `resolveTarget=null` et
conservent la target MSAA; exactement le dernier `PathRenderPass`
color-producing matérialise le `LogicalTarget` comme `resolveTarget` de
`GPUPreparedNativeRenderPassConfig`. Ne jamais émettre une pseudo-commande de
resolve séparée. Les ressources hard-edge 1× sont des leases distincts des
attachments scène 4×; le cover 4× reçoit uniquement leur texture view mask
read-only et ne réinterprète pas les triangles source.

```kotlin
for (pass in prepared.graph.passes()) {
    when (pass) {
        is PlanPass.PathRenderPass -> materializePathRenderPass(
            pass = pass,
            colorTarget = resources.requireTextureView(pass.target, pass.sampleCountI32),
            resolveTarget = pass.resolveTarget?.let { resources.requireTextureView(it, 1) },
            depthStencilTarget = pass.depthStencil?.let {
                resources.requireTextureView(it, pass.sampleCountI32)
            },
        )
        else -> materializeNonResolvePass(pass, resources, encoder)
    }
}
```

- [ ] **Step 4: Exécuter la gate renderer AA**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*StencilAa*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(gpu-renderer): materialize path MSAA resolve"
```

---

### Task 9: Surface et preuves transforms/AA

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURefusalGuards.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4dGeneralPathCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPURefusalGuardsTest.kt`

- [ ] **Step 1: Écrire les tests RED publics**

Fixtures fill/stroke/hairline : rotation, skew, reflection, perspective bornée,
horizon refusé, mixed hard/AA, sample-position-independent coverages 0/0.5/1,
RGBA/BGRA, `SrcOver` ordonné et rollback.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*W4dGeneral*' --tests '*GPUPlanSurfaceRouterTest*W4dGeneral*'
```

- [ ] **Step 3: Faire précéder les refus legacy par les capabilities générales**

Le router transmet le graph et ne classe ni matrice ni coverage. Le refus
legacy non-identity de `GPURefusalGuards` reste inchangé pour la route legacy,
mais il n'est consulté qu'après que la compiler chain W4d.2 a décliné la scène.
L'oracle est indépendant des helpers production et n'utilise aucune tolérance.

```kotlin
@Test fun general_path_capability_precedes_legacy_transform_refusal() {
    val rendered = renderPublicScene(perspectiveAaPathScene())
    assertEquals(W4D_GENERAL_AA_EXPECTED_RGBA8, rendered.copyPixelsRgba8())
    assertEquals(W4D_GENERAL_AA_CAPABILITY, rendered.planCapabilityId)
}
```

- [ ] **Step 4: Exécuter les gates Surface**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add kanvas/src
rtk git commit -m "feat(kanvas): route transformed AA paths"
```

---

### Task 10: Vérification, documentation et PR stackée

**Files:**
- Modify: `refactor/README.md`
- Modify: `refactor/waves/W04-geometry-coverage/status.md`
- Modify: ce plan

- [x] **Step 1: Exécuter les gates fraîches**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*StencilAa*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --rerun-tasks
```

- [x] **Step 2: Contrôler XML/scope/diff**

```bash
rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
rtk git diff --check codex/w4d-strokes-hairlines...HEAD
rtk git diff --name-only codex/w4d-strokes-hairlines...HEAD
```

- [x] **Step 3: Mettre à jour le suivi et committer**

Publier capabilities, transform classes, MSAA resources, résultats frais,
ledger et W4e encore ouverte.

```bash
rtk git add refactor
rtk git commit -m "docs(refactor): publish W4d transform AA evidence"
```

- [x] **Step 4: Sol final review et corrections**

Relire le diff complet depuis W4d.1, corriger tout Critical/Important via fresh
Terra, refaire les gates affectées jusqu'à `Approved`. Revue Sol finale
`APPROVED/CLEAN` après les corrections documentaires.

- [x] **Step 5: Pousser et créer la PR stackée**

```bash
rtk git push -u origin codex/w4d-general-transform-aa
rtk gh pr create --base codex/w4d-strokes-hairlines --head codex/w4d-general-transform-aa --title "feat: add general path transforms and AA" --body-file /tmp/w4d-aa-pr-body.md
```

PR stackée [#2392](https://github.com/ygdrasil-io/kanvas/pull/2392) ouverte et
mergeable, de `codex/w4d-general-transform-aa` vers
`codex/w4d-strokes-hairlines`; elle n'est pas encore mergée.

Le body créé par `apply_patch` contient `## Summary`, `## Verification` et
`## Scope and follow-ups`. Ne pas merger/rebaser ni lancer les tests exclus.
