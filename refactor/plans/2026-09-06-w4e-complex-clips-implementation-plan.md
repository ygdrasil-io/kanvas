# W4e Complex Clips and Inverse Paths Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fermer W4 en composant les clip stacks rect/RRect/path Intersect/Difference, hard-edge/AA et les inverse path draws dans des sous-graphes stencil/mask planifiés, avec transforms capturés et sérialisés sans perte.

**Architecture:** La Scene IR conserve pour chaque clip sa géométrie source et un transform snapshot typé, sérialisé dans `Picture` v8 / SceneArchive schema v2. `:math` prépare les clips et inverse domains, `:gpu-plan` choisit scissor/stencil/coverage-mask et scelle les dépendances/ressources, puis `:gpu-renderer` matérialise sans utiliser l'ancien `GPUClipMapper` comme autorité.

**Tech Stack:** Kotlin/JVM/JS, `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGPU4K, `SceneArchiveCodec`, Picture v8, Gradle, `rtk`.

**Spec:** `refactor/specs/2026-09-06-w4-remaining-geometry-coverage-design.md`

## Global Constraints

- Branche : `codex/w4e-complex-clips`, empilée sur `codex/w4d-general-transform-aa`; la PR cible cette branche.
- Capabilities exactes :
  - `solid-geometry-clip-stack-hard-1x-src-over-srgb-v1` pour une frame entièrement hard-edge ;
  - `solid-geometry-clip-stack-mixed-aa4-src-over-srgb-v1` dès qu'un draw ou clip demande AA.
- Une candidate contient 1–512 draws W4 de géométrie Rect, RRect ou Path fill/stroke/`STROKE_AND_FILL`, avec au moins une clip stack complexe ou un inverse path draw.
- Material `SolidColor`, blend `SrcOver`, sRGB 1× et layer racine restent les bornes; W5/W6 sont hors scope.
- Clip operations : Intersect/Difference ordonnées; géométries Rect/RRect/Path; fill rules winding/even-odd/inverses; hard-edge/AA.
- La géométrie source et la CTM capturée sont immuables. Aucun `transformClass: String` ne peut autoriser un nouveau plan; les archives v1 sans matrice sont `LegacyUnavailable` et non promouvables.
- `Picture` reste version 8. `SceneArchiveCodec` écrit schema v2, lit v1/v2 et délègue les anciens payloads v8 sans marker au reader historique.
- Tous les objets géométriques vivent dans `:math`; nomenclature numérique I/F32/64.
- Coverage mask linéaire 8-bit explicitement formaté; Intersect=`previous*source`, Difference=`previous*(1-source)`, avec quantification après chaque fold.
- L'inverse draw est borné par `target ∩ clip`; un inverse path vide couvre tout ce domaine.
- Ressources, sample count, usages, bytes, passes, identities, lifetimes et reuse sont scellés avant `Ready`.
- Aucun fallback/recalcul après `Ready`; tout échec avant submit libère la transaction complète.
- Tests comportementaux/publics uniquement; aucun test d'infrastructure, reflection, accès privé, call-count ou source-shape.
- Aucun `font`, `codec`, GM Skia, dashboard, baseline ou `jpg-color-cube`; aucun seuil/tolérance.
- Fresh Terra par tâche; Sol spec+quality read-only après chaque commit.

---

## Carte de fichiers

| Responsabilité | Créations principales | Modifications principales |
|---|---|---|
| Capture/compatibilité | `ClipTransformSnapshot` dans `:render-ir` | SceneArchive schema v2, adapters, Canvas/ClipStack, Picture v8 |
| Géométrie math | `ClipGeometryF32.kt`, `ClipStackPreparationF64.kt`, `InversePathGeometryF32.kt`, `InversePathPreparationF64.kt` | préparation commune transformée |
| Graph/planner | `W4eClipPlanCompiler.kt`, `ClipPlanBudget.kt`, `W4ePlanDiagnostics.kt` | capabilities, resources, passes, graph, compiler chain |
| Lowering/native | `W4eClipGraphLowerer.kt`, `GPUPlanW4ePreparedAuthority.kt`, `GPUCorePrimitiveW4ePreparedFrameTaskListAssembler.kt` | preflight, materializers, executor, frame/scratch pools |
| Surface/preuves | `W4eClipCpuOracle.kt` | router tests, pixel tests, Picture tests |
| Suivi | ce plan | `refactor/README.md`, status W04 |

## Interfaces partagées

Scene IR :

```kotlin
public sealed interface ClipTransformSnapshot : CanonicalValue {
    public class Known private constructor(matrixF32: Matrix3x3F32) : ClipTransformSnapshot {
        public fun copyMatrixF32(): Matrix3x3F32
        public companion object { public fun of(matrixF32: Matrix3x3F32): Known }
    }
    public data class LegacyUnavailable(
        public val transformClass: String,
        public val perspectiveCaptureRefusal: Boolean,
    ) : ClipTransformSnapshot
}

public data class ClipEntry(
    public val geometry: GeometryNode,
    public val operation: ClipOperation,
    public val antiAlias: Boolean = true,
    public val transform: ClipTransformSnapshot = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
)
```

Math :

```kotlin
public enum class ClipCombineOperation { Intersect, Difference }

public class ClipGeometryF32 private constructor(
    public val antiAlias: Boolean,
    public val inverseFill: Boolean,
    public val attemptedEdgeCountI32: Int,
    public val vertexCostI64: Long,
    public val indexCostI64: Long,
    public val snapshotByteCostI64: Long,
    public val entryWorkUsageI64: ClipWorkUsageI64,
    conservativeScissorI32: RectI32,
    rectF32: RectF32?,
    pathFillGeometryF32: PathFillGeometryF32?,
    rrectF32: RRectF32?,
) {
    public fun copyConservativeScissorI32(): RectI32
    public fun copyRectF32OrNull(): RectF32?
    public fun copyPathFillGeometryF32OrNull(): PathFillGeometryF32?
    public fun copyRRectF32OrNull(): RRectF32?
    internal companion object {
        internal fun of(
            antiAlias: Boolean,
            inverseFill: Boolean,
            attemptedEdgeCountI32: Int,
            vertexCostI64: Long,
            indexCostI64: Long,
            snapshotByteCostI64: Long,
            entryWorkUsageI64: ClipWorkUsageI64,
            conservativeScissorI32: RectI32,
            rectF32: RectF32?,
            pathFillGeometryF32: PathFillGeometryF32?,
            rrectF32: RRectF32?,
        ): ClipGeometryF32
    }
}

// `of(...)` exige exactement une représentation géométrique non nulle parmi
// `rectF32`, `pathFillGeometryF32` et `rrectF32`; il vérifie les compteurs non
// négatifs et capture des snapshots défensifs de la géométrie et du scissor.

public class ClipStackGeometryF32 private constructor(
    entriesF32: List<ClipGeometryF32>,
    operations: List<ClipCombineOperation>,
) {
    public val entryCountI32: Int
    public fun copyEntryGeometryF32(indexI32: Int): ClipGeometryF32
    public fun operationAt(indexI32: Int): ClipCombineOperation
    internal companion object {
        internal fun of(
            entriesF32: List<ClipGeometryF32>,
            operations: List<ClipCombineOperation>,
        ): ClipStackGeometryF32
    }
}

public data class ClipPreparationPolicyF64(
    public val maximumSagittaErrorF64: Double = 0.25,
    public val limitsI32: ClipPreparationLimitsI32 = ClipPreparationLimitsI32(),
    public val limitsI64: ClipPreparationLimitsI64 = ClipPreparationLimitsI64(),
)

public data class ClipPreparationLimitsI32(
    public val maxClipEntryCountPerStackI32: Int = 64,
    public val maxClipEntryCountPerFrameI32: Int = 1_024,
    public val maxSubdivisionDepthI32: Int = 32,
    public val maxAttemptedGeometryUnitsPerEntryI32: Int = 65_536,
    public val maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
    public val maxEmittedVertexCountPerEntryI32: Int = 262_144,
    public val maxEmittedVertexCountPerFrameI32: Int = 1_048_576,
    public val maxEmittedIndexCountPerEntryI32: Int = 786_432,
    public val maxEmittedIndexCountPerFrameI32: Int = 3_145_728,
)

public data class ClipPreparationLimitsI64(
    public val maxSnapshotByteCountPerEntryI64: Long = 16L * 1024L * 1024L,
    public val maxSnapshotByteCountPerFrameI64: Long = 64L * 1024L * 1024L,
)

public data class ClipWorkUsageI64(
    public val clipEntryCountI64: Long = 0L,
    public val attemptedGeometryUnitCountI64: Long = 0L,
    public val emittedVertexCountI64: Long = 0L,
    public val emittedIndexCountI64: Long = 0L,
    public val snapshotByteCountI64: Long = 0L,
)

internal class ClipWorkLedgerI64(
    private val stackWorkUsageBeforeI64: ClipWorkUsageI64,
    private val frameWorkUsageBeforeI64: ClipWorkUsageI64,
    policyF64: ClipPreparationPolicyF64,
) {
    internal fun beginEntryI64(
        entryWorkUsageBeforeI64: ClipWorkUsageI64,
    ): ClipEntryWorkLedgerI64
    internal fun snapshotStackUsageI64(): ClipWorkUsageI64
    internal fun snapshotFrameUsageAfterI64(): ClipWorkUsageI64
}

internal interface ClipEntryWorkLedgerI64 {
    public fun debitBeforeEmissionI64(deltaI64: ClipWorkUsageI64)
    public fun commitEntryUsageI64(): ClipWorkUsageI64
}

public data class RectF64(
    public val leftF64: Double,
    public val topF64: Double,
    public val rightF64: Double,
    public val bottomF64: Double,
)

public data class CornerRadiiF64(public val xF64: Double, public val yF64: Double)

public data class RRectF64(
    public val rectF64: RectF64,
    public val topLeftF64: CornerRadiiF64,
    public val topRightF64: CornerRadiiF64,
    public val bottomRightF64: CornerRadiiF64,
    public val bottomLeftF64: CornerRadiiF64,
)

public enum class ClipInvalidSceneReason {
    NonFiniteInput, NonFiniteProjection, PerspectiveHorizonCrossing, LegacyTransformUnavailable
}

public enum class ClipResourceLimitReason {
    FlatteningDidNotConverge, EntryCountLimit, EntryWorkLimit, FrameWorkLimit,
    VertexLimit, IndexLimit, SnapshotByteLimit,
    RasterBoundsOverflow, HostSizeOverflow
}

public sealed interface ClipSourceGeometryF64 {
    public data class Rect(public val valueF64: RectF64) : ClipSourceGeometryF64
    public data class RRect(public val valueF64: RRectF64) : ClipSourceGeometryF64
    public data class Path(public val valueF64: PathFillInputF64) : ClipSourceGeometryF64
}

public sealed interface ClipDeviceGeometryF64 {
    public data class Rect(public val valueF64: RectF64) : ClipDeviceGeometryF64
    public data class RRect(public val valueF64: RRectF64) : ClipDeviceGeometryF64
    public data class Path(public val valueF64: PathFillInputF64) : ClipDeviceGeometryF64
}

public data class ClipDeviceInputF64(
    public val geometryF64: ClipDeviceGeometryF64,
    public val operation: ClipCombineOperation,
    public val antiAlias: Boolean,
    public val entryWorkUsageBeforeGeometryI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
)

public data class ClipTransformInputF64(
    public val sourceF64: ClipSourceGeometryF64,
    public val transformF64: Matrix3x3F64,
    public val operation: ClipCombineOperation,
    public val antiAlias: Boolean,
)

public sealed interface ClipTransformPreparationResult {
    public data class Ready(
        public val inputF64: ClipDeviceInputF64,
        public val entryWorkUsageAfterProjectionI64: ClipWorkUsageI64,
        public val stackWorkUsageAfterI64: ClipWorkUsageI64,
        public val frameWorkUsageAfterI64: ClipWorkUsageI64,
    ) : ClipTransformPreparationResult
    public data class Empty(
        public val entryWorkUsageAfterProjectionI64: ClipWorkUsageI64,
        public val stackWorkUsageAfterI64: ClipWorkUsageI64,
        public val frameWorkUsageAfterI64: ClipWorkUsageI64,
    ) : ClipTransformPreparationResult
    public data class InvalidScene(public val reason: ClipInvalidSceneReason) : ClipTransformPreparationResult
    public data class ResourceLimitExceeded(public val reason: ClipResourceLimitReason) : ClipTransformPreparationResult
}

public fun prepareClipStackGeometryF32(
    entriesF64: List<ClipDeviceInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult

public fun prepareTransformedClipStackGeometryF32(
    entriesF64: List<ClipTransformInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult

public sealed interface ClipStackPreparationResult {
    public data class Ready(
        public val geometryF32: ClipStackGeometryF32,
        public val stackWorkUsageI64: ClipWorkUsageI64,
        public val frameWorkUsageAfterI64: ClipWorkUsageI64,
    ) : ClipStackPreparationResult
    public data class Empty(
        public val stackWorkUsageI64: ClipWorkUsageI64,
        public val frameWorkUsageAfterI64: ClipWorkUsageI64,
    ) : ClipStackPreparationResult
    public data class InvalidScene(public val reason: ClipInvalidSceneReason) : ClipStackPreparationResult
    public data class ResourceLimitExceeded(public val reason: ClipResourceLimitReason) : ClipStackPreparationResult
}
```

`RectF64`, `CornerRadiiF64`, `RRectF64`, les source/device geometries,
`ClipDeviceInputF64`, `prepareClipStackGeometryF32` et les snapshots restent
dans `:math:geometry`. `ClipTransformInputF64`,
`ClipTransformPreparationResult` et
`prepareTransformedClipStackGeometryF32` vivent dans `:math:matrix`, seul
module qui voit `Matrix3x3F64`; `:math:geometry` ne dépend jamais de
`:math:matrix`.

Planner :

```kotlin
public enum class ClipPlanStrategy { None, Scissor, Stencil, CoverageMask }
public enum class PlanCoverageMaskFormat { RGBA8_UNORM_LINEAR }

public sealed interface PlanClipBinding {
    public data object None : PlanClipBinding
    public class Scissor private constructor(scissorI32: RectI32) : PlanClipBinding {
        public fun copyScissorI32(): RectI32
        public companion object { public fun of(scissorI32: RectI32): Scissor }
    }
    public class Stencil private constructor(
        public val depthStencil: PlanResourceId,
        public val referenceI32: Int,
        domainI32: RectI32,
    ) : PlanClipBinding {
        public fun copyDomainI32(): RectI32
        public companion object {
            public fun of(depthStencil: PlanResourceId, referenceI32: Int, domainI32: RectI32): Stencil
        }
    }
    public class CoverageMask private constructor(
        public val mask: PlanResourceId,
        domainI32: RectI32,
    ) : PlanClipBinding {
        public fun copyDomainI32(): RectI32
        public companion object {
            public fun of(mask: PlanResourceId, domainI32: RectI32): CoverageMask
        }
    }
}

public class ClippedPlanDraw private constructor(
    public val source: PlanDraw,
    public val clip: PlanClipBinding,
) : PlanDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend
    public companion object {
        public fun of(source: PlanDraw, clip: PlanClipBinding): ClippedPlanDraw
    }
}

public class InversePathDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometryF32: InversePathGeometryF32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
) : PlanDraw {
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public fun copyGeometryF32(): InversePathGeometryF32
    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: InversePathGeometryF32,
            coverage: CoveragePlan,
            sample: SamplePlan,
        ): InversePathDraw
    }
}
```

La factory `InversePathDraw.of` accepte `FullOrScissor` uniquement en
single-sample et `StencilAA4` seulement en multisample 4×; elle rejette toute
paire coverage/sample incohérente avant la construction du graph. Un inverse
hard-edge placé dans une frame AA4 produit d'abord sa couverture binaire 1× et
utilise ensuite le `BinaryMaskedPathDraw` W4d.2 : il n'est jamais rasterisé
directement avec `FullOrScissor` dans la target MSAA.

W4e conserve `PathRenderPass.draws: List<PlanDraw>` introduit par W4d.2 et
étend sa whitelist validée à `ClippedPlanDraw` et `InversePathDraw`, y compris
quand `ClippedPlanDraw.source` est un `BinaryMaskedPathDraw`. Le sample count du
consumer doit égaler celui du pass couleur; aucun wrapper ne peut masquer une
paire coverage/sample invalide.

`PlanTextureFormat.CoverageMask` et `PlanCoverageMaskFormat` sont réutilisés
depuis W4d.2. `PlanResourceRole.CoverageMaskAccumulator`,
`CoverageMaskScratch`, `CoverageMaskMultisampleScratch`,
`CoverageMaskDepthStencil` sont ajoutés; `PlanResourceUsage.Sampled` est
réutilisé. Le mask
final et ses accumulateurs utilisent RGBA8 linear 1×; le canal rouge porte la
coverage et les autres canaux sont scellés à la même valeur pour un
readback/debug déterministe. Un producer Path/RRect AA utilise en plus un
scratch RGBA8 linear 4×, un scratch resolve 1× et une D24S8 4× ; son
color-producing render pass porte le scratch 1× comme `resolveTarget`. La
coverage UNORM8 résolue est ensuite foldée puis multipliée à chaque sample du
draw consumer. Format/sample/usage/resolve exacts restent capability-gatés.

---

### Task 1: Capture de transform clip typée et SceneArchive schema v2

**Files:**
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/MaterialNode.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`
- Test: `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/MaterialNodeTest.kt`
- Test: `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodecTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/ClipStack.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneDisplayOpAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureWireV8.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/CanvasTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/ClipStackTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapterTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneRoundTripTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`

**Interfaces:**
- Consumes: `Matrix3x3F32`, existing clip geometry/operations.
- Produces: `ClipTransformSnapshot`, schema v2 writer, v1/v2 decoder et adapters lossless.

- [ ] **Step 1: Écrire les tests RED de capture/round-trip**

```kotlin
@Test fun scene_archive_v2_round_trips_each_clip_matrix_and_v1_never_invents_identity() {
    val decodedV2 = decode(encode(sceneWithPerspectiveClip()))
    assertEquals(perspectiveMatrix, decodedV2.singleClip().transform.requireKnown().copyMatrixF32())
    assertIs<ClipTransformSnapshot.LegacyUnavailable>(decode(schemaV1ClipBytes).singleClip().transform)
}
```

Ajouter Picture historique v8, schema v1 IR, schema v2, CTM figée malgré une
CTM ultérieure, affine/perspective, restore/replay, defensive copy et canonical
identity distincte.

- [ ] **Step 2: Exécuter le RED ciblé**

```bash
rtk ./gradlew :render-ir:test --tests '*SceneArchiveCodecTest*' --tests '*MaterialNodeTest*' :kanvas:test --tests '*ClipStackTest*' --tests '*DisplayOpSceneAdapterTest*' --tests '*PictureTest*'
```

- [ ] **Step 3: Implémenter capture et codecs**

Conserver le header `KPIC` v8 et le marker IR. Écrire `schemaVersion=2`; brancher
le decoder sur 1/2. Schema 1 remplit `LegacyUnavailable` avec les deux anciens
faits. Les nouveaux Canvas clips conservent géométrie source + copie CTM. Le
fast path `DeviceRect` n'est conservé que lorsqu'un rect device-space exact est
prouvé sans perte; rotation/skew/perspective restent source geometry + CTM.

```kotlin
private const val SCENE_ARCHIVE_SCHEMA_I32: Int = 2

private fun ArchiveReader.clipEntryV1(): ClipEntry = ClipEntry(
    geometry = geometry(),
    operation = enum(),
    antiAlias = bool(),
    transform = ClipTransformSnapshot.LegacyUnavailable(
        perspectiveCaptureRefusal = bool(),
        transformClass = text(),
    ),
)

private fun ArchiveWriter.clipTransformV2(value: ClipTransformSnapshot) {
    when (value) {
        is ClipTransformSnapshot.Known -> {
            i32(1)
            matrix(value.copyMatrixF32())
        }
        is ClipTransformSnapshot.LegacyUnavailable -> {
            i32(2)
            bool(value.perspectiveCaptureRefusal)
            text(value.transformClass)
        }
    }
}

private fun ArchiveReader.clipTransformV2(): ClipTransformSnapshot = when (i32()) {
    1 -> ClipTransformSnapshot.Known.of(matrix())
    2 -> ClipTransformSnapshot.LegacyUnavailable(
        perspectiveCaptureRefusal = bool(),
        transformClass = text(),
    )
    else -> throw ArchiveFailure("unknown-clip-transform", "Unknown clip transform tag")
}
```

Le dispatch schema appelle `clipEntryV1()` pour le layout historique exact
`geometry, operation, antiAlias, perspectiveCaptureRefusal, transformClass` et
le couple reader/writer v2 pour le nouveau tag. Le test utilise une fixture v1
fixe via l'API publique du codec, sans accès privé ni reflection.

- [ ] **Step 4: Exécuter les suites IR/Picture**

```bash
rtk ./gradlew :render-ir:test :kanvas:test --tests '*PictureTest*' --tests '*CanvasTest*' --tests '*ClipStackTest*' --tests '*DisplayOpSceneAdapterTest*' --tests '*SceneRoundTripTest*'
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add render-ir/src kanvas/src/main/kotlin/org/graphiks/kanvas/canvas kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir kanvas/src/main/kotlin/org/graphiks/kanvas/picture kanvas/src/test
rtk git commit -m "feat(render-ir): preserve typed clip transforms"
```

---

### Task 2: Snapshots et préparation math des clips

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RRectF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/ClipGeometryF32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/ClipPreparationLimitsI32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/ClipPreparationLimitsI64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/ClipWorkLedgerI64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/ClipStackPreparationF64.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/ClipStackPreparationF64Test.kt`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/ClipTransformsF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/ClipTransformsF64Test.kt`

**Interfaces:**
- Consumes: Rect/RRect/Path source, `Matrix3x3F64` et préparation projective W4d.2.
- Produces: valeurs device-space dans `:math:geometry`, orchestration transformée séparée dans `:math:matrix`.

```kotlin
public fun prepareClipStackGeometryF32(
    entriesF64: List<ClipDeviceInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult

public fun prepareTransformedClipStackGeometryF32(
    entriesF64: List<ClipTransformInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult
```

- [ ] **Step 1: Écrire les tests RED math**

Rect/RRect/Path, Intersect/Difference, inverse winding/even-odd, AA flag,
ordered entries, affine/perspective, empty operations, conservative scissor,
attempted-edge/frame limits, vertex/index/snapshot-byte limits, snapshots
défensifs et deux stacks successives où la seconde reçoit
`frameWorkUsageAfterI64` de la première et refuse avant émission. Ajouter un
cas où projection matrix et tessellation geometry restent séparément sous la
limite entry, mais où leur somme la dépasse : geometry doit refuser avant son
premier tableau final.

- [ ] **Step 2: Exécuter le RED JVM**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
```

- [ ] **Step 3: Implémenter la préparation**

Adapter Rect/RRect vers paths math seulement quand la stratégie l'exige.
Préserver operation/AA/inverse séparément de la géométrie. Débiter tous les
coûts avant émission; aucune classe renderer. Chaque module garde un ledger
mutable strictement interne. La façade matrix prépare la projection avec les
snapshots stack/frame reçus, puis transmet ses deux snapshots after à la
préparation geometry; aucun objet mutable ne traverse la frontière de module et
aucune limite n'est remise à zéro. Chaque `ClipDeviceInputF64` transformé porte
en plus `entryWorkUsageBeforeGeometryI64`, snapshot du coût de projection de
cette entrée uniquement; le sous-ledger geometry reprend ce snapshot pour
vérifier la limite entry cumulée sans inclure les entrées précédentes.

```kotlin
public fun prepareClipStackGeometryF32(
    inputsF64: List<ClipDeviceInputF64>,
    targetDomainI32: RectI32,
    policyF64: ClipPreparationPolicyF64 = ClipPreparationPolicyF64(),
    stackWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
    frameWorkUsageBeforeI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
): ClipStackPreparationResult {
    val ledgerI64 = ClipWorkLedgerI64(stackWorkUsageBeforeI64, frameWorkUsageBeforeI64, policyF64)
    val preparedF32 = inputsF64.map {
        val entryLedgerI64 = ledgerI64.beginEntryI64(it.entryWorkUsageBeforeGeometryI64)
        prepareSingleClipGeometryF32(it, targetDomainI32, policyF64, entryLedgerI64)
    }
    return sealClipStackGeometryF32(preparedF32, ledgerI64)
}
```

`ClipTransformsF64.kt` mappe d'abord chaque `ClipTransformInputF64` vers un
`ClipDeviceInputF64` : rect/RRect axis-aligned restent typés, toute autre
transformation devient un `PathFillInputF64` device-space. Avant chaque
évaluation/allocation, son ledger matrix débite le snapshot stack/frame. Il
appelle ensuite la fonction geometry ci-dessus avec les deux snapshots after
de projection comme valeurs before et avec le snapshot entry de chaque input.
Aucun fichier de `:math:geometry` n'importe `Matrix3x3F64`.

- [ ] **Step 4: Exécuter math JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src math/matrix/src
rtk git commit -m "feat(math): prepare transformed clip stacks"
```

---

### Task 3: Inverse path draws et domaine fini

**Files:**
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/InversePathGeometryF32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/InversePathPreparationF64.kt`
- Test: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/InversePathPreparationF64Test.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathGeometryPreparationF64.kt`
- Test: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathGeometryPreparationF64Test.kt`

**Interfaces:**
- Consumes: fill fini device-space, outline stroke device-space optionnel, mode/style et domaine I32.
- Produces: `InversePathGeometryF32` avec état intérieur explicite + domaine; aucune matrice dans `:math:geometry`.

```kotlin
public sealed interface InverseInteriorCoverageF32 {
    public data object Zero : InverseInteriorCoverageF32
    public class Geometry private constructor(geometryF32: PathFillGeometryF32) : InverseInteriorCoverageF32 {
        public fun copyGeometryF32(): PathFillGeometryF32
        public companion object { public fun of(geometryF32: PathFillGeometryF32): Geometry }
    }
}

public enum class InversePathDrawMode { Fill, StrokeAndFill }

public class InversePathGeometryF32 private constructor(
    public val interiorCoverageF32: InverseInteriorCoverageF32,
    domainI32: RectI32,
) {
    public fun copyDomainI32(): RectI32
    public companion object {
        public fun of(
            interiorCoverageF32: InverseInteriorCoverageF32,
            domainI32: RectI32,
        ): InversePathGeometryF32
    }
}

public sealed interface InversePathPreparationResult {
    public data class Ready(
        public val geometryF32: InversePathGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : InversePathPreparationResult
    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : InversePathPreparationResult
    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) : InversePathPreparationResult
}

public fun prepareInversePathGeometryF32(
    finiteFillF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64?,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): InversePathPreparationResult

public fun Matrix3x3F64.prepareTransformedInversePathGeometryF32(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): InversePathPreparationResult
```

- [ ] **Step 1: Écrire les tests RED inverse**

Inverse winding/even-odd, path vide couvrant domaine, scissor différent des
bounds source, clip vide/complexe, `STROKE_AND_FILL` inverse union, affine et
perspective bornée.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*InversePathPreparationF64Test' :math:matrix:jvmTest --tests '*PathGeometryPreparationF64Test'
```

- [ ] **Step 3: Implémenter le domaine inverse**

Normaliser la base en winding/even-odd non inverse et conserver l'inversion
comme opération sur le domaine. Pour `StrokeAndFill`, construire l'intérieur à
exclure `F \ O`, puisque `¬F ∪ O = ¬(F \ O)`. `Finite(0.0)` conserve exactement
le fill inverse. Une source non vide mais de couverture intérieure nulle et un
path vide produisent `InverseInteriorCoverageF32.Zero`, jamais `Empty`; le cover
peint alors tout le domaine. L'orchestration matrix prépare d'abord
`finiteFillF64` et `deviceStrokeOutlineF64`, puis appelle cette API device-space.

```kotlin
public fun prepareInversePathGeometryF32(
    finiteFillF64: PathFillInputF64,
    deviceStrokeOutlineF64: PathFillInputF64?,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): InversePathPreparationResult
```

`PathGeometryPreparationF64.kt` implémente la façade matrix
`prepareTransformedInversePathGeometryF32`: elle retire uniquement le bit
inverse de la fill rule, projette `F`, prépare/projette `O` selon la sémantique
finite/hairline W4d.2, puis appelle la fonction geometry ci-dessus avec le même
ledger.

- [ ] **Step 4: Exécuter math JVM/JS**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add math/geometry/src math/matrix/src
rtk git commit -m "feat(math): prepare bounded inverse path fills"
```

---

### Task 4: RenderGraph clip/mask et formats linéaires

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`

**Interfaces:**
- Consumes: clip/inverse snapshots math.
- Produces: `ClipPlanStrategy`, coverage-mask formats/resources et passes.

```kotlin
public sealed interface PlanPass {
    public class ClipMaskInitialize(
        override public val ordinal: Int,
        public val output: PlanResourceId,
        domainI32: RectI32,
        public val clearCoverageF32: Float = 1f,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskInitialize
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        public fun copyDomainI32(): RectI32
    }

    public class ClipMaskProducer(
        override public val ordinal: Int,
        public val target: PlanResourceId,
        public val resolveTarget: PlanResourceId?,
        public val depthStencil: PlanResourceId?,
        public val sampleCountI32: Int,
        geometryF32: ClipGeometryF32,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskProducer
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        public fun copyGeometryF32(): ClipGeometryF32
    }

    public class ClipMaskFold(
        override public val ordinal: Int,
        public val previous: PlanResourceId,
        public val source: PlanResourceId,
        public val output: PlanResourceId,
        public val operation: ClipCombineOperation,
        domainI32: RectI32,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskFold
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        public fun copyDomainI32(): RectI32
    }
}
```

- [ ] **Step 1: Écrire les tests RED graph**

Tester scissor sans ressource, stencil simple, mask initialize à 1 + producer +
ping-pong folds, format linéaire, usages Sampled, producer hard 1× et AA4 avec
scratch MSAA/resolve/D24S8, quantification par fold, inverse domain, wrapper
`ClippedPlanDraw`, `InverseInteriorCoverageF32.Zero` sans producer mais avec
cover du domaine, consumer dependencies, identities et rejet des alias
read/write/sample incompatibles.
Dans une frame AA4, vérifier qu'un consumer/inverse demandé hard-edge passe par
le mask binaire 1× + `BinaryMaskedPathDraw` et conserve strictement une
coverage 0/1 sur tous les samples.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*ClipMask*' --tests '*RenderGraphContractTest*Inverse*'
```

- [ ] **Step 3: Implémenter ressources/passes**

Réutiliser `PlanTextureFormat.CoverageMask(RGBA8_UNORM_LINEAR)` et ajouter les
rôles accumulator, scratch 1×, scratch MSAA et D24S8 mask (A/B sont distingués
par ordinal), usages render/sample et lifetime de l'initialisation au dernier
consumer. Interdire qu'une passe lise et écrive la même texture. Un producer
AA4 exige `{target=CoverageMaskMultisampleScratch 4×, resolveTarget=
CoverageMaskScratch 1×, depthStencil=CoverageMaskDepthStencil 4×}` et la preuve
`PlanTextureResolveSupport(PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4, 1)`; un producer hard path peut
demander une D24S8 mask 1× distincte.

```kotlin
private fun validateClipMaskFold(pass: PlanPass.ClipMaskFold) {
    require(pass.previous != pass.output) { "Clip mask fold cannot sample its output attachment" }
    require(pass.source != pass.output) { "Clip mask producer cannot alias fold output" }
}

public enum class PlanResourceRole {
    LogicalTarget, MultisampleColorTarget, ReadbackStaging,
    VertexData, IndexData, UniformData, DepthStencil,
    PathHardEdgeMask, PathHardEdgeDepthStencil,
    CoverageMaskAccumulator, CoverageMaskScratch,
    CoverageMaskMultisampleScratch, CoverageMaskDepthStencil,
}

public enum class PlanResourceUsage {
    RenderAttachment, CopySource, CopyDestination, MapRead,
    Vertex, Index, Uniform, DepthStencilAttachment, Sampled,
}

public enum class PlanPassRole {
    MainRender, StencilProducer, StencilCover, TextureCopy, Filter,
    Resolve, Readback, ClipMaskInitialize, ClipMaskProducer, ClipMaskFold,
}
```

- [ ] **Step 4: Exécuter `:gpu-plan:test`**

```bash
rtk ./gradlew :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src
rtk git commit -m "feat(gpu-plan): model ordered clip mask graphs"
```

---

### Task 5: Compiler W4e, stratégie, reuse et budgets

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4eClipPlanCompiler.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ClipPlanBudget.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4ePlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4eClipPlanCompilerTest.kt`
- Test: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/ClipPlanBudgetTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4, W4 geometry compilers et device capabilities.
- Produces: capabilities W4e exactes et graph de frame complet.

- [ ] **Step 1: Écrire les tests RED compiler**

Prouver Rect/RRect/Path consumers, Intersect/Difference order, inverse clip et
inverse draw, hard 1×/AA 4×, simple scissor/stencil/mask selection, reuse exact,
non-reuse si transform/extent/sample diffère, empty operations, 512/513,
formats/sample/budgets absents et `LegacyUnavailable`.
Prouver aussi plusieurs stacks distincts dans une frame : chaque préparation
reçoit le `frameWorkUsageAfterI64` précédent et les limites entry/attempted/
vertex/index/snapshot bytes refusent avant toute émission ou allocation.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-plan:test --tests '*W4eClipPlanCompilerTest' --tests '*ClipPlanBudgetTest'
```

- [ ] **Step 3: Implémenter selection/plan**

Dédupliquer uniquement par canonical identity complète : stack, matrices,
extent, format et sample plan. Budgéter les capacités poolées, pas seulement les
tailles logiques : deux accumulateurs 1× et un scratch resolved 1×; ajouter un
scratch RGBA8 linear 4× et une D24S8 mask 4× pour tout producer Path/RRect AA.
Une frame AA conserve aussi les ressources scène W4d.2 distinctes
`MultisampleColorTarget` 4× + `DepthStencil` 4× + resolve vers
`LogicalTarget`. Un producer path hard mask reçoit sa propre D24S8 mask 1×.
Tout draw ou inverse demandé hard-edge dans cette frame réutilise la route
`PathHardEdgeMask` 1× → `BinaryMaskedPathDraw` 4× de W4d.2 avant application
du clip final. Aucun accès au mapper legacy.

Le compiler maintient un unique `ClipWorkUsageI64` dans l'ordre des premières
occurrences de stacks distincts. Il le passe à
`prepareTransformedClipStackGeometryF32` et reprend exactement le snapshot
frame retourné; un stack réutilisé consomme le snapshot préparé et ne redébite
pas sa géométrie. Toute limite est terminale pour la lane W4e.

```kotlin
private fun checkedMaskTextureBytesI64(widthI32: Int, heightI32: Int, sampleCountI32: Int): Long =
    Math.multiplyExact(
        4L,
        Math.multiplyExact(
            Math.multiplyExact(widthI32.toLong(), heightI32.toLong()),
            sampleCountI32.toLong(),
        ),
    )

private fun checkedAaPathClipBytesI64(widthI32: Int, heightI32: Int): Long {
    val oneSampleI64 = checkedMaskTextureBytesI64(widthI32, heightI32, 1)
    val fourSamplesI64 = checkedMaskTextureBytesI64(widthI32, heightI32, 4)
    return Math.addExact(Math.multiplyExact(oneSampleI64, 3L), Math.multiplyExact(fourSamplesI64, 2L))
}

private fun ClipStackReuseKey.matches(other: ClipStackReuseKey): Boolean =
    canonicalStackId == other.canonicalStackId &&
        extentI32 == other.extentI32 && format == other.format &&
        samplePlan == other.samplePlan && transformIds == other.transformIds
```

- [ ] **Step 4: Exécuter IR/math/planner**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-plan/src
rtk git commit -m "feat(gpu-plan): compile bounded W4e clip stacks"
```

---

### Task 6: Lowering et autorités clip/inverse

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4eClipGraphLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4ePreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4ePreparedFrameTaskListAssembler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4eTest.kt`

**Interfaces:**
- Consumes: graph W4e complet.
- Produces: prepared clip producer/fold/consumer et inverse cover tasks.

- [ ] **Step 1: Écrire les tests RED lowering**

Tester chaque stratégie, order, ping-pong, shared mask, inverse empty, forged
canonical identity, wrong format/usage/sample/lifetime, scratch insuffisant et
transaction refusée.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4eTest'
```

- [ ] **Step 3: Implémenter le lowering mécanique**

Traduire les passes scellées vers une autorité W4e dédiée. Pour un producer
AA4, porter target mask 4×, `resolveTarget` mask 1× et D24S8 mask 4× jusqu'au
`GPUPreparedNativeRenderPassConfig`; pour un producer hard path, porter la
D24S8 mask 1×. Les ABI stencil/mask existantes ne sont réutilisées que si ces
contrats correspondent exactement. `GPUClipMapper` et `GPUClipExecutionPlan`
ne peuvent produire/reclasser aucun fait après `Ready`.

```kotlin
return when (pass) {
    is PlanPass.ClipMaskInitialize -> lowerMaskInitialize(pass, authority)
    is PlanPass.ClipMaskProducer -> lowerMaskProducer(
        pass = pass,
        target = authority.requireTexture(pass.target, pass.sampleCountI32),
        resolveTarget = pass.resolveTarget?.let { authority.requireTexture(it, 1) },
        depthStencil = pass.depthStencil?.let {
            authority.requireTexture(it, pass.sampleCountI32)
        },
    )
    is PlanPass.ClipMaskFold -> lowerMaskFold(pass, authority)
    else -> lowerAuthenticatedGeometryPass(pass, authority)
}
```

- [ ] **Step 4: Exécuter renderer ciblé**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4e*' --tests '*ClipStencil*' --tests '*CoverageMask*'
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(gpu-renderer): lower sealed W4e clip graphs"
```

---

### Task 7: Matérialisation mask/stencil, pool et preflight

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUScratchTexturePool.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveW4eFrameTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`

- [ ] **Step 1: Écrire les tests RED natifs**

Format RGBA8 linear, producer clear, fold quantifié, ping-pong, sample 1/4,
stencil fast path, inverse cover, shared leases, completion/readback et rollback
sur chaque allocation/pipeline/bind/encoder failure.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*W4e*' --tests '*ClipStencil*' --tests '*CoverageMask*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
```

- [ ] **Step 3: Matérialiser les passes scellées**

Créer/recycler la liste exacte déclarée par le graph via une requête W4e : deux
accumulateurs 1×, scratch resolved 1×, et, si le producer est AA, scratch mask
4× + D24S8 mask 4×; un producer path hard reçoit D24S8 mask 1×. Ces ressources
sont distinctes de la color target/D24S8 de scène W4d.2. Lever les exclusions
legacy mask+D24S8/multiple masks uniquement sous l'autorité de cette requête
scellée. Le render pass producer AA porte le scratch resolved comme
`resolveTarget`. Quantifier chaque sortie de fold en UNORM8. Le mask final est
bindé read-only aux draws; aucune passe ne sample son attachment actif.

```kotlin
private fun foldCoverageUnorm8(previousI32: Int, sourceI32: Int, operation: ClipCombineOperation): Int {
    val numeratorI32 = when (operation) {
        ClipCombineOperation.Intersect -> previousI32 * sourceI32
        ClipCombineOperation.Difference -> previousI32 * (255 - sourceI32)
    }
    return (numeratorI32 + 127) / 255
}

public data class GPUW4eAttachmentRequest(
    public val accumulatorCountI32: Int,
    public val producerSampleCountI32: Int,
    public val requiresProducerDepthStencil: Boolean,
    public val requiredPhysicalByteCountI64: Long,
)
```

- [ ] **Step 4: Exécuter la gate renderer W4e**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4e*' --tests '*ClipStencil*' --tests '*CoverageMask*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(gpu-renderer): materialize W4e clip masks"
```

---

### Task 8: Surface et oracle clip/inverse

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4eClipCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`

- [ ] **Step 1: Écrire les tests RED pixels publics**

Fixtures : rect/RRect/path Intersect, Difference, two-op order non commutatif,
inverse clip, inverse draw empty/non-empty, hard/AA, shared clip multiple draws,
affine/perspective, RGBA/BGRA, `SrcOver`, scissor et rollback.

L'oracle calcule une coverage 8-bit indépendante, quantifie après chaque fold,
multiplie la coverage draw puis encode/quantifie la couleur après chaque draw.
Il n'importe aucun planner, renderer ou helper production.

- [ ] **Step 2: Exécuter le RED**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*W4e*' --tests '*GPUPlanSurfaceRouterTest*W4e*' --tests '*PictureTest*'
```

- [ ] **Step 3: Prouver le branchement Surface générique**

Ne modifier ni la candidate gate ni le router : Rect/RRect/DrawPath sont déjà
admis vers la Scene IR. L'ajout de W4e dans la compiler chain du
`GpuRenderContext` doit suffire. Le planner reste l'unique choix de stratégie et
de reuse; Surface ne construit aucun mask/stencil fact.

```kotlin
@Test fun ordered_difference_and_inverse_are_visible_through_public_surface() {
    val rendered = renderPublicScene(orderedClipAndInverseScene())
    assertEquals(W4E_ORDERED_CLIP_EXPECTED_RGBA8, rendered.copyPixelsRgba8())
    assertEquals(W4E_MIXED_AA_CAPABILITY, rendered.planCapabilityId)
}
```

- [ ] **Step 4: Exécuter les gates Surface**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --tests '*PictureTest*' --rerun-tasks
```

- [ ] **Step 5: Commit Terra**

```bash
rtk git add kanvas/src/test
rtk git commit -m "feat(kanvas): route W4e clips and inverse paths"
```

---

### Task 9: Gates finales, fermeture W4 et PR stackée

**Files:**
- Modify: `refactor/README.md`
- Modify: `refactor/waves/W04-geometry-coverage/status.md`
- Modify: ce plan

- [ ] **Step 1: Exécuter toutes les gates fraîches**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*W4e*' --tests '*ClipStencil*' --tests '*CoverageMask*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --tests '*PictureTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --rerun-tasks
```

- [ ] **Step 2: Vérifier ledger, archive et scope**

```bash
rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
rtk git diff --check codex/w4d-general-transform-aa...HEAD
rtk git diff --name-only codex/w4d-general-transform-aa...HEAD
```

Confirmer lecture Picture v8 historique + SceneArchive schemas 1/2, zéro nouveau
nom de failure/error et aucun fichier exclu.

- [ ] **Step 3: Publier la fermeture W4**

Le status décrit capabilities, transforms, strokes, AA, clips, inverse,
ressources, budgets, preuves fraîches, limitations classifiées et passage à W5.

```bash
rtk git add refactor
rtk git commit -m "docs(refactor): close W4 geometry coverage"
```

- [ ] **Step 4: Sol final review**

Sol revoit toute la stack W4d.1→W4e et chaque preuve. Tous les findings
Critical/Important repartent vers un fresh Terra, avec gates proportionnées,
jusqu'à `Approved`.

- [ ] **Step 5: Pousser et créer la PR stackée**

```bash
rtk git push -u origin codex/w4e-complex-clips
rtk gh pr create --base codex/w4d-general-transform-aa --head codex/w4e-complex-clips --title "feat: add W4e complex clips and inverse paths" --body-file /tmp/w4e-pr-body.md
```

Le body créé par `apply_patch` contient `## Summary`, `## Verification` et
`## Scope and follow-ups`. Ne pas merger/rebaser, lancer GM/Skia ou supprimer
le worktree.

### Addendum `final-fix` — 2026-09-09

- [x] La revue finale a étendu la candidate gate W4e aux clips complexes et
  aux draws inverse, puis aux consumers Rect/RRect/Path `FILL`/`STROKE`/
  `STROKE_AND_FILL` par la seam W4 partagée; seul l'inverse `STROKE_AND_FILL`
  reçoit la translation `PathStrokeStyleF64` définie par le contrat.
- [x] La publication inverse débite le snapshot défensif réel avant sa copie;
  les entrées stack/frame, leurs reasons et leur propagation Matrix sont de
  nouveau bornées et préflightées avant toute matérialisation.
- [x] La normalisation Skia des rayons RRect est une autorité F64 unique pour
  les quatre classes de transform, avec preuves d'équivalence typed/path.
- [x] Le triplet native V/I/U est désormais une ressource W4e scellée du graph
  (ID, usage, capacité, lifetime, peak et limite device); le renderer ne crée
  plus de buffer Path/RRect/inverse ad hoc après `Ready`.
- [x] RED puis GREEN ciblés pour chaque finding; gates fraîches : G1/G2 vertes,
  G3 à 2 144/45/0/2 et G4 à 3 687/51/0/2, soit les baselines exactes. AA4 reste
  explicitement indisponible sans capability inventée.
