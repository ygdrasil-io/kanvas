# W6d Advanced Effects Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the eleven W6d advanced image-filter families, backdrop and filtered `initWithPrevious` through the single frozen W6 graph, with public `Surface`/`Picture` Render+Readback evidence.

**Architecture:** W6d starts from the reviewed HEAD of `codex/w6c-spatial-dag`. Capture continues to produce the immutable W6b filter-node table; `:math` alone derives F64 bounds and checked I32 texel domains, then `:gpu-plan` adds typed operation arms only to the existing `FilterPass` and `FilterTarget` contracts. `:gpu-renderer` materializes the selected operation verbatim, and never chooses a kernel, target, format, mapping, pass, resource, ID, lifetime, or fallback after the plan is frozen.

**Tech Stack:** Kotlin/JVM; `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; JUnit 5; public `Surface`, `Picture`, `Canvas`, `Paint`, `ImageFilter`, `RenderResult`, and `readPixels` APIs.

**Spec:** `refactor/specs/2026-09-16-w6-layers-effects-design.md` §§5–16, `refactor/specs/2026-09-22-w6b-w6e-stacked-delivery-design.md` §§3, 6, 8–11, and the Task 3 amendment `refactor/specs/2026-09-23-w6d-lighting-3d-design.md` (Astra-reviewed, corrected at `0edbe98`).

## Global Constraints

- Base branch: the reviewed HEAD of `codex/w6c-spatial-dag`; implementation branch: `codex/w6d-advanced-effects`. The W6d Draft PR targets `codex/w6c-spatial-dag`, never W6b, W5h, or `main`.
- Read both approved W6 specifications before every task. If the W6c contracts differ from this plan, stop the task, record the exact conflict in the review request, and do not create a parallel authority.
- Every task is a Terra implementation slice followed by a Sol review. At PR level, permit exactly one bounded non-Sol fix wave only for Critical/Important findings, then one scoped Sol re-review; never iterate a review loop.
- Every checkbox is a single 2–5 minute action. If a listed Gradle selector is still running, wait for its one result before starting the next checkbox; never parallelize Gradle or combine selectors into a broad gate.
- Use RED -> GREEN -> refactor for every public behavior. A RED is causal only when unchanged production runs and reaches the asserted semantic failure; a compilation, fixture, permission, timeout, mock, reflection, or infrastructure failure is not evidence.
- Run Gradle commands one at a time, in the listed order, from `/Users/chaos/.codex/worktrees/cbf6/kanvas`, always prefixed by `rtk` (or `rtk proxy` for the two Kotlin Multiplatform compiler commands).
- Tests use only public `Surface`, `Picture`, `Canvas`, paint/layer/filter APIs, `render()`, `readPixels`, pixels, diagnostics, and public `discardRecordedOperations()`. Do not add private/internal tests, source-shape assertions, reflection, fake devices, injected capabilities, mocks, counters, hooks, static-source tests, or backend test infrastructure.
- Each positive witness asserts `result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback"))` in addition to public pixels. Expected bytes/oracles are calculated before the `Surface` or `PictureRecorder` is created.
- Exact identity/copy/crop/offset/composite cases are byte-exact. `MatrixConvolution`, `DisplacementMap`, `Magnifier`, and all six lighting variants use a separately written CPU oracle and their family-local tolerance; no suite-wide tolerance or similarity threshold is permitted.
- `:math` is the only owner of new rectangles, points, vectors, sizes, mappings, and projections. New geometry names state their precision (`I32`, `I64`, `F32`, or `F64`); calculations are F64 and projection rounds outward to checked I32. Neither `:kanvas` nor `:gpu-renderer` may introduce private geometry, string bounds, or `.toInt()` planning bounds.
- Lighting's six public families use only `Vector3F32`/`Point3F32`; no 2D overload or implicit Z is retained. New Picture output is version 15/schema 9; older 2D-lighting records fail closed while older non-lighting scenes remain readable. All three coordinates enter canonical identity and frozen planning.
- W6d lighting admits finite invertible affine parameter-to-layer mappings only. It maps XY as point or vector, and maps Z **and** `surfaceScale` as `((A·(z,z)).x + (A·(z,z)).y)/2`. Lighting output is unbounded before demanded-output/clip/target intersection; Sobel needs a one-texel halo with Skia's per-edge clamp/decal decision.
- `kd/ks` are finite and non-negative. `surfaceScale` may be negative; `shininess` and spot exponent are any finite F32; spot cutoff is finite degrees and its frozen cosine is finite in `[-1,1]`. Legacy `GPULighting.kt` restrictions do not govern W6d. Degenerate-light and undefined-power cases follow the bounded, non-ISO Kanvas zero-contribution convention in the lighting spec.
- W6d adds physical graph vocabulary only by extending W6b's existing `FilterPassOperationV1` arms on `PlanPass.FilterPass`; it reuses `PlanResourceRole.FilterTarget`. It creates no second frame graph, allocator, submit path, cache, planner, or renderer-side plan type.
- The full graph freezes before native preparation: passes, targets, snapshots, programs, uniforms, samplers, IDs, slots, usages, formats, sample counts, lifetimes, cache facts, and checked I64 budget. The renderer only binds and encodes those facts.
- RGBA8 is W6d's only positive target format. F16/HDR is refused before native publication with the exact W6 capability diagnostic; it is never silently converted to RGBA8.
- A W6-owned refusal is frame-terminal: before native publication it discards every reservation; between publication and submit it removes/quarantines the complete ready token; after submit it exposes no partial successful readback and keeps leases until completion/quarantine. A balanced public canvas followed by `discardRecordedOperations()` must recover on the same `Surface`.
- Backdrop snapshots are copied from the immediate parent at logical save time, before child draws, and filtered from the snapshot, never sampled from the active render attachment. `initWithPrevious` copies the immediate parent unfiltered at save, then the layer DAG evaluates after all children and before restore alpha/color-filter/blend.
- Runtime catalogue additions are additive. Existing W5h descriptor ABI hashes and the `kanvas.runtime.child-opacity` descriptor remain byte-for-byte unchanged; W6d registers the new IMAGE_FILTER entry under a distinct ID and hash.
- Keep W4/W5 authorities for geometry coverage, materials, color filters, `BlendPlan`, destination versions, image ownership, runtime registrations, caches, pessimistic budgets, and leases. No admitted W6d scene may fall back to W6a/W6b/W6c legacy/prepared routes.
- Exclude fonts/glyph generation, codecs/external format decode or encode, GMs, dashboard, renders, references, scores, rebaseline, the global Skia suite, `jpg-color-cube`, arbitrary SkSL/WGSL frontends, W8 legacy removal, and unobservable device-loss proof. Do not claim ISO or global Skia convergence.
- Native exits 133 and 134 remain `UNKNOWN`, not passing evidence. Record Gradle exit, XML method custody, failure/error/skip counts, source commit, and native exit separately.
- Durable documentation is limited to `refactor/README.md`, `refactor/waves/W06-layers-effects/status.md`, this plan, and the approved specs. Agent scratch reports remain ignored.

## Review Focus

- **Save-time ordering:** `W6dBackdropPreviousSurfacePixelTest.backdropAndFilteredPreviousSeeParentAndChildAtTheirSpecifiedTimes` proves a parent/child colour witness: backdrop observes only the parent at save, whereas filtered previous observes the unfiltered parent plus the child after children render.
- **Sampling bounds and edge policy:** `W6dAdvancedSamplingSurfacePixelTest.convolutionDisplacementAndMagnifierMatchTheirIndependentOraclesAtEdges` pins outward bounds, tile mode, kernel offset, displacement channels, zoom, and inset without a global tolerance.
- **Lighting geometry and degenerates:** `W6dLightingSurfacePixelTest.sixLightingFamiliesHandleFlatAlphaAndSpotCutoffWithFamilyOracles` and its named companion witnesses cover same XY/different Z, nonuniform affine scale plus offset, signed `surfaceScale`, both Sobel edge policies, light on transparent black, cutoff, zero vectors/undefined powers, premultiplied output, and local tolerances.
- **Snapshot and wire isolation:** `W6dPictureRuntimeEffectPictureTest.pictureFilterAndImageOpacitySurviveMemoryWireReplayAndPostCaptureMutation` proves `SceneSnapshot` ownership, old Picture readers, explicit sharing, absent input binding, and unchanged W5h hashes through public replay.
- **Atomic resource pressure:** `W6dAdvancedRecoverySurfacePixelTest.exactBudgetAcceptsAndOneByteLessRefusesWithoutReadbackPublicationThenRecovers` ties B/B-1 accounting, snapshot/filter targets, programs/uniforms, late refusal, and same-surface recovery to public observable behavior.

---

## Actual Code Map and Intended Ownership

Repository-relative aliases used below:

```text
GEOM = math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/
MATRIX = math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/
IR = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
```

| Existing owner | W6d responsibility |
| --- | --- |
| `API/paint/ImageFilter.kt`, `API/canvas/SaveLayerRec.kt`, `API/render/ir/PaintSceneAdapter.kt`, `API/render/ir/DisplayOpSceneAdapter.kt` | Keep public filter values and layer backdrop capture; snapshot every array, rect, picture, uniform, and optional input into the W6c node table. |
| `IR/EffectNode.kt`, `IR/SceneCommand.kt`, `IR/SceneSnapshot.kt`, `IR/SceneArchiveCodec.kt`, `API/picture/Picture.kt` | Bind the eleven advanced nodes to typed W6c references; Task 3 advances new Picture output to 15/schema 9 for 3D lighting while preserving older non-lighting readers, then Task 4 carries Picture filter `SceneSnapshot` and backdrop identity without deduplicating equal values. |
| `GEOM/RectF64.kt`, `GEOM/RectProjectionF64.kt`, `MATRIX/LayerMappingF64.kt` | Add only precision-named pure geometry helpers for sampling halos, translated domains, lens domains, and checked outward I32 projection. |
| `PLAN/W6aLayerPlanCompiler.kt`, `PLAN/W6aLayerGraphConstruction.kt`, `PLAN/W6aPlanDiagnostics.kt` | Replace the W6a advanced/backdrop refusal with W6d admission, occurrence binding, save-time snapshots, final filtered layer DAG, terminal diagnostics, and immutable freeze. |
| `PLAN/W6bFilterPlanV1.kt`, `PLAN/PlanPasses.kt`, `PLAN/PlanResources.kt`, `PLAN/RenderGraph.kt` | Add immutable advanced arms to `FilterPassOperationV1`; reuse the existing `FilterPass` and `FilterTarget`; preserve the one graph, IDs, dependencies, lifetime validation, and no active-attachment sampling rule. |
| `PLAN/RuntimeEffectSemanticCatalog.kt`, `PLAN/RuntimeEffectCpuEvaluatorV1.kt` | Generalize the catalogue to multi-ABI entries while preserving W5h's shader entry/hash; define the registered `kanvas.runtime.image-opacity` IMAGE_FILTER semantics and CPU oracle. |
| `GPU/planning/W6aLayerGraphLowerer.kt`, `GPU/recording/GPUW6aLayerFramePlan.kt`, `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPU/execution/GPUW6aEncoderScopesV1.kt` | Lower sealed FilterPass operations and frozen resource IDs to native encoders; materialize snapshots/targets/programs without recomputation or selection. |
| `GPU/filters/GPUDisplacementMap.kt`, `GPU/filters/GPULighting.kt`, `GPU/filters/GPUPreparedFilterDescriptors.kt`, `GPU/filters/GPUFilterOracle.kt` | Reuse only as implementation kernels/oracle references after the W6d plan has selected and frozen a typed operation; do not route these helpers directly from public capture. |
| `TEST/surface/W6aInitWithPreviousSurfacePixelTest.kt`, `TEST/picture/W6aLayerPictureTest.kt` | Preserve unfiltered previous/wire behavior as focused regression selectors; W6d tests are new public files rather than rewrites of W6a evidence. |

The following remain legacy or helper-only and must not acquire W6d semantic planning decisions: `GPU/layers/LayerContracts.kt`, `GPU/layers/GPUPreparedCompositeLowerer.kt`, `API/surface/gpu/GPUPreparedCompositeCapture.kt`, `API/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`, `GPU/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`, `GPU/execution/GPUPreparedSurfaceNativePreflight.kt`, and `GPU/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`.

## Frozen W6d Interfaces

W6b supplies `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `CapturedFilterTableV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `FilterPassOperationV1`, `PlanPass.FilterPass`, and `PlanResourceRole.FilterTarget`; W6c has already extended those exact owners. W6d consumes them rather than recreating a node table, occurrence key, payload hierarchy, pass, or target role.

```kotlin
// GEOM/AdvancedFilterBoundsF64.kt — pure geometry; no IR or renderer imports.
public fun RectF64.expandSamplingHaloF64OrNull(
    leftF64: Double,
    topF64: Double,
    rightF64: Double,
    bottomF64: Double,
): RectF64?

public fun RectF64.translateF64OrNull(dxF64: Double, dyF64: Double): RectF64?

public fun RectF64.intersectF64OrNull(otherF64: RectF64): RectF64?
```

Each helper rejects non-finite values, inverted output, and overflow; `:gpu-plan` calls `roundOutToRectI32OrNull()` only after the F64 operation. Kernel extents/offsets, displacement scale, and lens geometry are converted at the W6 capture/planning boundary into F64 scalar arguments; no `Rect`, `Point`, `Size`, or `Matrix` is introduced outside `:math`.

```kotlin
// PLAN/W6bFilterPlanV1.kt — additional arms on the one W6b operation hierarchy.
public sealed interface FilterPassOperationV1 {
    public val kind: FilterImplementationKindV1
    public val bounds: FilterBoundsPlanV1

    public class MatrixConvolution(
        kernelSizeI32: SizeI32,
        kernel: ImmutableFloats,
        public val gainF32: Float,
        public val biasF32: Float,
        kernelOffsetF64: Vector2F64,
        public val tileMode: TileMode,
        public val convolveAlpha: Boolean,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1 {
        private val kernelSizeSnapshotI32 = kernelSizeI32.copy()
        private val kernelSnapshot = ImmutableFloats.copyOf(kernel.copyToFloatArray())
        private val kernelOffsetSnapshotF64 = Vector2F64(kernelOffsetF64.x, kernelOffsetF64.y)
        public fun copyKernelSizeI32(): SizeI32 = kernelSizeSnapshotI32.copy()
        public fun copyKernel(): ImmutableFloats = ImmutableFloats.copyOf(kernelSnapshot.copyToFloatArray())
        public fun copyKernelOffsetF64(): Vector2F64 = Vector2F64(kernelOffsetSnapshotF64.x, kernelOffsetSnapshotF64.y)
    }

    public class DisplacementMap(
        public val xChannel: ColorChannel,
        public val yChannel: ColorChannel,
        public val scaleF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1

    public class Magnifier(
        sourceF64: RectF64,
        public val zoomF32: Float,
        public val insetF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1 {
        private val sourceSnapshotF64 = sourceF64.copyF64()
        public fun copySourceF64(): RectF64 = sourceSnapshotF64.copyF64()
    }

    public class Lighting(
        public val family: LightingFamilyV1,
        parameters: LightingParametersV1,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1 {
        private val parametersSnapshot = parameters.copy()
        public fun copyParameters(): LightingParametersV1 = parametersSnapshot.copy()
    }

    public class Picture(
        public val scene: SceneSnapshot,
        cullRectF64: RectF64,
        sourceRectF64: RectF64?,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1 {
        private val cullRectSnapshotF64 = cullRectF64.copyF64()
        private val sourceRectSnapshotF64 = sourceRectF64?.copyF64()
        public fun copyCullRectF64(): RectF64 = cullRectSnapshotF64.copyF64()
        public fun copySourceRectF64(): RectF64? = sourceRectSnapshotF64?.copyF64()
    }

    public class RuntimeImageOpacity(
        public val effect: RuntimeEffectDescriptor,
        public val alphaF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1
}

public enum class LightingFamilyV1 {
    DISTANT_DIFFUSE, POINT_DIFFUSE, SPOT_DIFFUSE,
    DISTANT_SPECULAR, POINT_SPECULAR, SPOT_SPECULAR,
}

public sealed interface LightingParametersV1 {
    public fun copy(): LightingParametersV1
}
```

`FilterImplementationKindV1` gains exactly `MATRIX_CONVOLUTION`, `DISPLACEMENT_MAP`, `MAGNIFIER`, `DISTANT_DIFFUSE`, `POINT_DIFFUSE`, `SPOT_DIFFUSE`, `DISTANT_SPECULAR`, `POINT_SPECULAR`, `SPOT_SPECULAR`, `PICTURE`, and `RUNTIME_IMAGE_OPACITY`. The existing `FilterPass(ordinal, inputs, output, evaluationKey, operation)` validates that all input/output targets are `FilterTarget` (except the pre-existing W6 source target), the operation bounds are non-empty, all F32 values are finite, and `output` is the one physical target represented by the operation. `PlanResourceRole.FilterTarget` remains the W6b RGBA8, single-sample, frame-local role with sealed `Sampled`, `RenderAttachment`, `CopySource`, and/or `CopyDestination` usage; its `PlanResource` still owns extent, bytes, format, lifetime, and allocation slot. No W6d class may add another operation hierarchy, `PlanPass` subtype, target role, or resource graph.

```kotlin
// PLAN/LayerScopePlanV1.kt semantic extension; physical work remains TextureCopy + FilterPass.
public class BackdropInitializationPlanV1 internal constructor(
    public val parentTarget: PlanResourceId,
    public val snapshotTarget: PlanResourceId,
    public val filteredTarget: PlanResourceId,
    public val capturedParentVersion: DestinationVersionI64,
    public val filterRoot: CapturedFilterNodeIdI32,
)

public sealed interface LayerInitializationPlanV1 {
    public data object TransparentBlack : LayerInitializationPlanV1
    public class PreviousCopy internal constructor(
        public val parentTarget: PlanResourceId,
        public val layerTarget: PlanResourceId,
        public val capturedParentVersion: DestinationVersionI64,
        sourceBoundsParentI32: RectI32,
        destinationOriginLayerI32: Point2I32,
    ) : LayerInitializationPlanV1
    public class Backdrop(public val plan: BackdropInitializationPlanV1) : LayerInitializationPlanV1
}
```

The layer execution sequence is frozen as `TextureCopy(parent -> snapshot)`, backdrop `FilterPass(snapshot -> filteredTarget)`, copy/composite filtered target into the layer target, child render segments, then the normal W6c DAG FilterPass chain, then the existing restore. `PreviousCopy` stays a raw copy at BeginLayer; only the already-captured layer filter root runs after child segments. An absent runtime `input` is W6b `CapturedFilterInputV1.ImplicitSource`, never a global binding.

```kotlin
// PLAN/RuntimeEffectSemanticCatalog.kt
public class RuntimeEffectSemanticEntryV1 internal constructor(
    public val descriptor: RuntimeEffectDescriptor,
    public val semanticKind: RuntimeEffectSemanticKindV1,
    public val cpuEvaluator: RuntimeEffectCpuEvaluatorV1,
    public val graphLimits: GraphLimits = GraphLimits(),
    public val frameLimits: MaterialFrameLimits = MaterialFrameLimits(),
)

public enum class RuntimeEffectSemanticKindV1 { SHADER_CHILD_OPACITY, IMAGE_OPACITY }
```

The new built-in descriptor is `RuntimeEffectId("kanvas.runtime.image-opacity")`, semantic version `1`, ABI `IMAGE_FILTER`, one required `RuntimeChildSlotV2("input", RuntimeChildType.IMAGE_FILTER, true)`, and one F32 `alpha` slot. Its sealed evaluator reads the bound input at the same pixel and returns `(r * alpha, g * alpha, b * alpha, a * alpha)` in premultiplied RGBA; it changes neither bounds nor sample coordinates. `RuntimeEffectSemanticCatalogSnapshot.find` remains keyed by `(id, semanticVersionI32, abiHash)`, so a wrong ABI, descriptor version, hash, uniform, child declaration, unregistered ID, or F16 target yields a stable pre-publication refusal.

### Task 1: Freeze W6d Filter-Pass, Target, Geometry, and Runtime Contracts

**Agent:** Terra implementation; Sol review.

**Outcome:** The existing W6 graph owns all advanced physical work through additional typed immutable arms on W6b `FilterPassOperationV1` and the existing `FilterTarget` role; geometry remains in `:math`, and the runtime catalogue accepts additive ABI-specific entries without changing W5h hashes.

**Files:**

- Create: `GEOM/AdvancedFilterBoundsF64.kt`
- Modify: `IR/CanonicalHashBytesV1.kt`
- Modify: `PLAN/W6bFilterPlanV1.kt`
- Create: `TEST/pipeline/W6dRuntimeEffectCatalogTest.kt`
- Modify: `PLAN/PlanPasses.kt`
- Modify: `PLAN/PlanResources.kt`
- Modify: `PLAN/RenderGraph.kt`
- Modify: `PLAN/RuntimeEffectSemanticCatalog.kt`
- Modify: `PLAN/RuntimeEffectCpuEvaluatorV1.kt`
- Modify: `GPU/planning/W6aLayerGraphLowerer.kt`

**Interfaces:**

- Consumes: W6b `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `FilterPassOperationV1`, frozen `RenderGraph`, `PlanResourceId`, `RuntimeEffectDescriptor`, and W5h `RuntimeEffectSemanticEntryV1`.
- Produces: the advanced arms on the exact W6b `FilterPassOperationV1`, `LightingFamilyV1`, the unchanged `PlanPass.FilterPass(ordinal, inputs, output, evaluationKey, operation)`, `AdvancedFilterBoundsF64` functions, and multi-ABI `RuntimeEffectSemanticKindV1` contracts used by Tasks 2–6.

- [ ] **Step 1 (2–5 min): Verify the stack base and create the W6d branch.**

```sh
git switch codex/w6c-spatial-dag
git status --short
git switch -c codex/w6d-advanced-effects
```

Expected: the base is the reviewed W6c HEAD and no unrelated local change is staged into W6d.

- [ ] **Step 2 (2–5 min): Write the failing public catalogue contract.**

```kotlin
@Test fun `image opacity descriptor is additive and leaves w5h hash unchanged`() {
    val shaderBefore = assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1))
    val image = assertNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1))
    assertEquals(RuntimeEffectAbi.SHADER, shaderBefore.kind)
    assertEquals(RuntimeEffectAbi.IMAGE_FILTER, image.kind)
}
```

Task 2 exercises non-finite halos and every new `:math` helper through public filter refusal/recovery; Sol reviews ownership and defensive copies structurally. Do not add direct planner, renderer, source-shape, or infrastructure tests for these contracts.

- [ ] **Step 3 (2–5 min): Run the causal RED selectors.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.pipeline.W6dRuntimeEffectCatalogTest'`

Expected: the tests fail because W6d geometry/operation/catalogue contracts do not exist, not because a production compiler failed.

- [ ] **Step 4 (2–5 min): Add the pure F64 helpers and immutable operation arms.**

```kotlin
public fun RectF64.translateF64OrNull(dxF64: Double, dyF64: Double): RectF64? {
    if (!isFinite() || !dxF64.isFinite() || !dyF64.isFinite()) return null
    val translated = RectF64(left + dxF64, top + dyF64, right + dxF64, bottom + dyF64)
    return translated.takeIf { it.isFinite() && !it.isEmpty }
}

val operation = FilterPassOperationV1.MatrixConvolution(
    kernelSizeI32 = kernelSizeI32,
    kernel = ImmutableFloats.copyOf(kernelF32),
    gainF32 = gainF32,
    biasF32 = biasF32,
    kernelOffsetF64 = kernelOffsetF64,
    tileMode = tileMode,
    convolveAlpha = convolveAlpha,
    bounds = bounds,
    kind = FilterImplementationKindV1.MATRIX_CONVOLUTION,
)
passes += FilterPass(nextOrdinal(), inputs, output, evaluationKey, operation)
```

- [ ] **Step 5 (2–5 min): Implement the additive catalogue entry and lowerer exhaustiveness check.**

```kotlin
RuntimeEffectSemanticEntryV1(
    descriptor = imageOpacityDescriptorV1(),
    semanticKind = RuntimeEffectSemanticKindV1.IMAGE_OPACITY,
    cpuEvaluator = ImageOpacityCpuEvaluatorV1,
)

when (val operation = pass.operation) {
    is FilterPassOperationV1.MatrixConvolution -> lowerMatrixConvolution(operation)
    is FilterPassOperationV1.DisplacementMap -> lowerDisplacement(operation)
    is FilterPassOperationV1.Magnifier -> lowerMagnifier(operation)
    is FilterPassOperationV1.Lighting -> lowerLighting(operation)
    is FilterPassOperationV1.Picture -> lowerPicture(operation)
    is FilterPassOperationV1.RuntimeImageOpacity -> lowerRuntimeImageOpacity(operation)
}
```

- [ ] **Step 6 (2–5 min): Run GREEN and focused W5h preservation.**

Run: `rtk proxy ./gradlew :math:geometry:compileKotlinJvm`

Run: `rtk ./gradlew :render-ir:compileKotlin`

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :gpu-renderer:compileKotlin`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.pipeline.W6dRuntimeEffectCatalogTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest'`

Expected: PASS; W5h semantic descriptor/hash remains unchanged.

- [ ] **Step 7 (2–5 min): Refactor and commit the frozen contract slice.**

```sh
git add math/geometry render-ir gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/pipeline/W6dRuntimeEffectCatalogTest.kt
git commit -m "feat(gpu): freeze w6d advanced filter pass contracts"
```

- [ ] **Step 8 (2–5 min): Request Sol review.** Review only the Task 1 commit for public contract compatibility, defensive snapshots, F64/I32 ownership, FilterPass/FilterTarget-only graph extension, and unchanged W5h hashes.

### Task 2: Matrix Convolution, Displacement Map, and Magnifier

**Agent:** Terra implementation; Sol review.

**Outcome:** Three advanced sampling families plan their immutable input bounds and resources, execute their frozen RGBA8 operations, and match independent family CPU oracles through public Render+Readback.

**Files:**

- Create: `TEST/surface/W6dAdvancedSamplingSurfacePixelTest.kt`
- Create: `TEST/surface/W6dAdvancedSamplingCpuOracle.kt`
- Modify: `IR/EffectNode.kt`
- Modify: `API/render/ir/PaintSceneAdapter.kt`
- Modify: `PLAN/W6aLayerPlanCompiler.kt`
- Modify: `PLAN/W6aLayerGraphConstruction.kt`
- Modify: `PLAN/W6aPlanDiagnostics.kt`
- Modify: `GPU/filters/GPUDisplacementMap.kt`
- Modify: `GPU/recording/GPUW6aLayerFramePlan.kt`
- Modify: `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- Modify: `GPU/execution/GPUW6aEncoderScopesV1.kt`

**Interfaces:**

- Consumes: `FilterPassOperationV1.MatrixConvolution`, `.DisplacementMap`, `.Magnifier`; W6c bound inputs; Task 1 F64 helpers; W6c cache key and FilterTarget allocation path.
- Produces: one frozen FilterPass per advanced operation, with inputs ordered `[displacement, source]` for displacement and `[source]` otherwise; public test/oracle cases used by Task 7 convergence.

- [ ] **Step 1: Write independent, public failing witnesses before a Surface is created.**

```kotlin
@Test fun `convolution displacement and magnifier match their independent oracles at edges`() {
    val expected = W6dAdvancedSamplingCpuOracle.convolution3x3Clamp(sourcePixels, kernel)
    val surface = Surface(3, 1)
    surface.canvas { drawFilteredSource(matrixConvolution(kernel)) }
    val result = surface.render()
    assertFamilyNear(expected, result.pixels, maxChannelDelta = 1)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}
```

Add separately named public methods `displacementUsesSelectedChannelsAndImplicitSource` and `magnifierHonorsLensInsetAndOrigin`, each with a precomputed oracle and family-local tolerance.

- [ ] **Step 2: Run causal REDs.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedSamplingSurfacePixelTest'`

Expected: FAIL with the current W6c precise advanced-filter refusal; record the diagnostic per method.

- [ ] **Step 3: Capture and plan exact input/output domains.**

```kotlin
val required = desiredOutputF64.expandSamplingHaloF64OrNull(
    leftF64 = kernelOffsetF64.x,
    topF64 = kernelOffsetF64.y,
    rightF64 = kernelWidthF64 - 1.0 - kernelOffsetF64.x,
    bottomF64 = kernelHeightF64 - 1.0 - kernelOffsetF64.y,
) ?: refuse(W6dPlanDiagnostics.NonFiniteSamplingGeometry)
val requiredI32 = requireNotNull(required.roundOutToRectI32OrNull())
passes += PlanPass.FilterPass(passes.size, inputs, output, evaluationKey, operation)
```

Validate matrix kernel dimensions/count, finite gain/bias/offset/scale/zoom/inset, non-empty lens/source rect, and source multiplicity before resource allocation. Bind absent optional input through W6c `ImplicitSource`; do not call a renderer normalizer from capture.

- [ ] **Step 4: Materialize the sealed operation without renderer choices.**

```kotlin
fun encodeFilter(pass: GPUW6dFilterPass) {
    val target = requireTarget(pass.outputId)
    val inputs = pass.inputIds.map(::requireSampledTexture)
    encoder.encode(pass.programId, target, inputs, pass.uniformOffsetI64)
}
```

The lowerer sets `programId`, target IDs, uniform offsets, sampler/tile mode, and dispatch extent from the frozen operation. It must reject an impossible binding as a terminal ready-token failure, never select another kernel, resize a target, or use legacy image-filter execution.

- [ ] **Step 5: Run GREEN, then refactor duplicated oracle fixture code only.**

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :gpu-renderer:compileKotlin`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedSamplingSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'`

Expected: PASS; the W6a backdrop refusal selector is allowed to change only when Task 6 replaces it with W6d acceptance.

- [ ] **Step 6: Commit and request Sol review.**

```sh
git add render-ir gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dAdvancedSampling*
git commit -m "feat(gpu): execute w6d advanced sampling filters"
```

Review the sampled edge/oracle maths, input ordering, F64-to-I32 projection, RGBA8-only admission, target/lease accounting, and renderer non-replanning.

### Task 3: 3D Lighting Contract, Wire, and Math Mapping

**Agent:** Terra implementation; Sol review. The breaking API and historical wire fixture must compile before a meaningful public pixel RED can run. A compile failure is not causal RED evidence.

**Outcome:** The public 2D lighting signatures disappear; six 3D variants capture and round-trip through Picture 15/schema 9; `:math` provides checked affine 3D mapping. The render route belongs to Tasks 31–32 below.

**Files:**

- Modify: `API/paint/ImageFilter.kt`, `API/render/ir/PaintSceneAdapter.kt`, `API/render/ir/SceneDisplayOpAdapter.kt`, `API/picture/Picture.kt`
- Modify: `IR/EffectNode.kt`, `IR/CapturedFilterTableV1.kt`, `IR/SceneArchiveCodec.kt`
- Modify: `MATRIX/LayerMappingF64.kt` (F64 computations, checked 3D F32 math outputs)
- Create: `TEST/picture/W6dLightingPictureTest.kt`
- Migrate public-call fixtures to 3D: `TEST/paint/EffectsExpansionTest.kt`, `TEST/render/ir/DisplayOpSceneAdapterTest.kt`, `TEST/surface/gpu/GPUImageFilterPlanTest.kt`, `TEST/picture/PictureTest.kt`, `TEST/picture/W6bFilterPictureTest.kt`, `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshotTest.kt`, `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodecTest.kt`. The out-of-scope `integration-tests/skia` GM sources are not changed or used as a W6d gate; record any source-compatibility fallout for their later migration.

**Interfaces:**

- Consumes: existing `Point3F32`/`Vector3F32` and `LayerMappingF64` from `:math`, W6c captured node table, and old Picture readers.
- Produces: six public 3D constructors; 3D `ImageFilterNode` and `CapturedFilterNodeV1` identities; Picture 15/schema 9 writer and fail-closed old 2D lighting readers; checked affine 3D mapping in `:math` consumed by Tasks 31–32.

- [ ] **Step 1: Change the six public/captured signatures and canonical identities.**

Before the signature change, record one v14/schema 8 2D-lighting `Picture`
produced by the current public writer as a literal test fixture: use
`ImageFilter.PointLitDiffuse(Point2F32(1f, 0f), ColorARGB.White, 1f, 1f)`
on a 2×1 `Picture`, obtain its `toByteArray()` once, and retain those bytes
in `W6dLightingPictureTest`. This is historical data, not a new writer path.

```kotlin
data class DistantLitDiffuse(val direction: Vector3F32, val lightColor: ColorARGB,
    val surfaceScale: Float, val kd: Float, val input: ImageFilter? = null) : ImageFilter
data class PointLitDiffuse(val location: Point3F32, val lightColor: ColorARGB,
    val surfaceScale: Float, val kd: Float, val input: ImageFilter? = null) : ImageFilter
data class SpotLitDiffuse(val location: Point3F32, val target: Point3F32,
    val specularExponent: Float, val cutoffAngle: Float, val lightColor: ColorARGB,
    val surfaceScale: Float, val kd: Float, val input: ImageFilter? = null) : ImageFilter
```

Apply the same 3D type substitutions to the specular siblings, `ImageFilterNode`, and `CapturedFilterNodeV1`. Include X/Y/Z canonical bits and bump each lighting canonical version to v2. Capture, copy and display-op replay preserve Z. Remove 2D lighting constructors entirely; do not change the 2D types of unrelated filters.

- [ ] **Step 2: Add checked mapping in `:math` and migrate source callers.**

```kotlin
fun LayerMappingF64.mapLightingPointToLayerF32OrNull(point: Point3F32): Point3F32?
fun LayerMappingF64.mapLightingVectorToLayerF32OrNull(vector: Vector3F32): Vector3F32?
fun LayerMappingF64.mapLightingZToLayerF32OrNull(value: Float): Float?
```

For affine `A=[[sx,kx],[ky,sy]]`, return XY point=`A·XY+t`, XY vector=`A·XY`, and Z=`((A·(z,z)).x+(A·(z,z)).y)/2`; perform arithmetic in F64 and narrow only after finite/F32-range checks. Use the frozen parameter-to-layer mapping and refuse perspective/non-invertible/overflow before publication. Migrate the named in-scope constructor call sites with explicit Z. No inferred Z in product code.

- [ ] **Step 3: Version the Picture wire and prove capture/replay publicly.**

```kotlin
private const val pictureVersion = 15
private const val schemaVersion = 9
// Schema 9 tags 9..14: write/read X, Y, Z for each lighting point/vector.
// Schema <= 8 or historical v1..v8 tag for 2D lighting: Invalid("unsupported-2d-lighting", ...).
```

Branch on schema before reading old tag payloads in both table and recursive codecs. Keep decoding old non-lighting scenes and explicit DAG sharing. Update `Picture.kt` version dispatch, including the historical reader's 2D lighting tags. `W6dLightingPictureTest` writes a 3D light, round-trips memory and wire, compares public `Picture.ops` and wire bytes for two different Z values, checks an old non-lighting fixture still reads, and checks the Step 1 v14 2D-lighting fixture returns decode failure without a partial `Picture`. Do not synthesize that fixture from the new writer. Update `PictureTest`'s writer-version expectation from 14 to 15. Run `rtk ./gradlew :render-ir:compileKotlin`, then `rtk ./gradlew :kanvas:compileTestKotlin`, then `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dLightingPictureTest'` for the wire-only methods; run its pixel/replay method only in Task 32 Step 3. Commit this contract checkpoint as `feat(ir): capture and serialize w6d 3d lighting`.

- [ ] **Step 4: Verify the contract and math checkpoint.** Run `rtk proxy ./gradlew :math:matrix:compileKotlinJvm`, `rtk ./gradlew :render-ir:compileKotlin`, `rtk ./gradlew :kanvas:compileTestKotlin`, and `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dLightingPictureTest'` sequentially. Require 2/0/0/0 XML for the public wire selector; report each Gradle exit and any native status separately.

- [ ] **Step 5: Commit and request Sol review.** Commit the contract and math mapping as separate commits if that makes the causal boundary clearer. Sol reviews the 3D-only API, canonical Z identity, old 2D wire refusal, non-lighting old-reader preservation, mapping F64 arithmetic and checked F32 narrowing. Neither positive lighting pixels nor native admission are claimed by this task.

### Task 31: Distant Diffuse Lighting Vertical Slice

**Agent:** Fresh Terra implementation; Sol task review. Execute after Task 3 review, before Task 32 or Task 4.

**Outcome:** One 3D distant-diffuse light produces public RGBA8 pixels through W6c's single frozen `FilterPass` path. This establishes reusable normal sampling, unbounded output demand, mapping, per-edge policy, and recipe selection without claiming the other five families.

**Files:**

- Create: `TEST/surface/W6dLightingCpuOracle.kt`, `TEST/surface/W6dLightingSurfacePixelTest.kt`
- Modify: `PLAN/W6bFilterPlanV1.kt`, `PLAN/W6bFilterGraphConstruction.kt`, `PLAN/W6aLayerGraphConstruction.kt`, `PLAN/W6aPlanDiagnostics.kt`, `PLAN/W6dSamplingProgramV1.kt`
- Modify: `GPU/filters/GPULighting.kt`, `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- Inspect, modify only if the generic FilterPass contract actually requires it: `PLAN/PlanPasses.kt`, `GPU/execution/GPUW6aEncoderScopesV1.kt`

**Interfaces:**

- Consumes: Task 3 `Vector3F32` captured direction and `LayerMappingF64.mapLightingVectorToLayerF32OrNull`/`mapLightingZToLayerF32OrNull`; Task 2 `W6dFrozenProgramBindingV1` on the existing `PlanPass.FilterPass`.
- Produces: `W6dSamplingProgramIdV1.DISTANT_DIFFUSE_RGBA8_V1` and immutable lighting recipe selected before graph freeze, one source `FilterTarget` and one output `FilterTarget`, public distant-diffuse CPU oracle and pixel witness. Other five families keep precise pre-publication refusal until Task 32.

- [ ] **Step 1: Add the public distant-diffuse RED and independent oracle.** Use `ImageFilter.DistantLitDiffuse(Vector3F32(1f,0f,1f), ColorARGB.White, 1f, 1f)` over a 3×3 alpha fixture. Calculate expected alpha-derived normals and saturated `kd*lightRGB*dot(normal, normalizedDirection)` with opaque output alpha **before** creating `Surface`; assert each RGBA8 channel to family-local tolerance and `nativeEvidenceScopeKinds` contains both `Render` and `Readback`. Compare a second direction `(1,0,100)` with the same XY to prove Z changes public pixels. Add another child-output fixture with an interior edge and a distant light visible on transparent black. Run `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'`; causal RED must reach the existing lighting-family refusal, not compilation or a test fixture error.

- [ ] **Step 2: Freeze mapping, admission and bounds for distant diffuse.** Map public direction and `surfaceScale` with the Task 3 math functions; allow finite signed scale, require finite `kd≥0` and finite mapped uniforms; refuse perspective/overflow with a stable W6 diagnostic. The output is unbounded before `desiredOutput`/clip/target intersection, `requiredInput=desiredOutput.outset(1px)`, and each of four edge modes is frozen from child-output versus demanded-output bounds. Form exactly one `FilterPassOperationV1.Lighting(family=DISTANT_DIFFUSE, parameters=LightingParametersV1.Distant(mappedDirection3F32, lightColor, mappedSurfaceDepthF32, kd, null), bounds, kind=DISTANT_DIFFUSE)` and its existing target/budget/lease resources. An unsupported family is a W6 terminal refusal; do not invoke `GPULightingFilter.execute`.

- [ ] **Step 3: Freeze then materialize one program recipe.** Add the one-input `DISTANT_DIFFUSE_RGBA8_V1` ID and an immutable lighting recipe with mapped 3D direction, mapped depth, coefficient/color, per-edge Sobel bounds/modes and exact input/output IDs to `W6dSamplingProgramV1`/`W6dFrozenProgramBindingV1`. Select it during `PlanPass.FilterPass` construction; the renderer may only translate this frozen recipe to WGSL and encode its frozen bindings. Sobel uses alpha and `0.25*(1,2,1)` weights; its normal is `normalize((-depth*dx,-depth*dy,1))`. Zero distant direction gives zero contribution and opaque black, not NaN. No new graph, target role, allocator, late specialization, active-attachment sampling, or hidden color conversion.

- [ ] **Step 4: Run GREEN and preservation sequentially.** Run `rtk ./gradlew :gpu-plan:compileKotlin`, `rtk ./gradlew :gpu-renderer:compileKotlin`, `rtk ./gradlew :kanvas:compileTestKotlin`, `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'`, then `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'`. Record XML methods/F/E/S and native exit separately; 133/134 is UNKNOWN, never GREEN. Commit `feat(gpu): execute w6d distant diffuse lighting`.

- [ ] **Step 5: Request Sol review.** Review public oracle independence, same-XY/different-Z capability, signed depth mapping, halo and both edge modes, transparent-black output, all frozen program/resource facts, terminal unsupported-family refusal, budget and no renderer-side plan choice. Resolve Critical/Important findings before Task 32.

### Task 32: Point/Spot and Specular Lighting Completion

**Agent:** Fresh Terra implementation; Sol task review. Execute after Task 31 review, before Task 4.

**Outcome:** The five remaining 3D families join the same frozen lighting path; all six have independent public pixel/oracle, invalid-input/refusal/recovery and Picture replay evidence.

**Files:**

- Modify: `TEST/surface/W6dLightingCpuOracle.kt`, `TEST/surface/W6dLightingSurfacePixelTest.kt`, `TEST/picture/W6dLightingPictureTest.kt`
- Modify: `PLAN/W6bFilterPlanV1.kt`, `PLAN/W6bFilterGraphConstruction.kt`, `PLAN/W6aLayerGraphConstruction.kt`, `PLAN/W6aPlanDiagnostics.kt`, `PLAN/W6dSamplingProgramV1.kt`, `PLAN/PlanPasses.kt`
- Modify: `GPU/filters/GPULighting.kt`, `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPU/execution/GPUW6aEncoderScopesV1.kt`

**Interfaces:**

- Consumes: Task 31's selected lighting recipe, Sobel normal source/edge policy and FilterPass/FilterTarget path; Task 3's 3D point/vector math and Picture 15/schema 9.
- Produces: the five further immutable IDs `POINT_DIFFUSE_RGBA8_V1`, `SPOT_DIFFUSE_RGBA8_V1`, `DISTANT_SPECULAR_RGBA8_V1`, `POINT_SPECULAR_RGBA8_V1`, `SPOT_SPECULAR_RGBA8_V1`; mapped 3D point/spot parameter arms; full six-family public evidence.

- [ ] **Step 1: Add five-family public REDs with independent expected pixels.** Extend the oracle's per-pixel formula with point/spot `surfaceToLight=normalize(location-(x,y,alpha*mappedDepth))`, spot direction=`normalize(mappedTarget-mappedLocation)`, cutoff cosine in degrees, falloff, Skia's `0.016` cone-edge ramp, specular half-vector `normalize(surfaceToLight+(0,0,1))`, exponent `shininess`, RGB saturation and output alpha=`max(R,G,B)`; diffuse stays opaque. Before `Surface` creation calculate cases for each family, point lights at `(1,0,1)` versus `(1,0,100)`, affine `diag(2,3)+(5,7)` (mapped Z=`2.5*z`), alpha flat/height variation, spot cutoff and premultiplication. Run the focused `W6dLightingSurfacePixelTest`; causal RED must be the current exact refusal of the five unsupported families.

- [ ] **Step 2: Freeze all five family recipes and execute them.** Map location/target as checked `Point3F32`, compute mapped spot direction and cosine in the planner, freeze all scalars/geometry and selected program IDs before graph validation, then materialize only those recipes through the existing Task 31 WGSL/bindings. Require finite coordinates, cutoff and exponents, `kd/ks≥0`, but allow negative `surfaceScale` and exponents outside `[1,128]`. Never route through legacy `GPULightingFilter.execute` or add an unplanned resource. Apply the lighting spec's explicit zero-contribution convention for zero distant/spot direction, point light on the surface, zero specular half-vector, undefined `pow` and non-finite intermediates; diffuse zero contribution is opaque black, specular zero contribution transparent black.

- [ ] **Step 3: Pin admission, edge and replay variants publicly.** Add named cases for signed `surfaceScale=-1`, finite exponents `0.5`/`129`, negative `kd/ks=-0.1` refusal, non-finite refusal, unsupported perspective refusal, child touching output edge versus child strictly inside, lighting of transparent black, zero vectors/coincident point/spot/half-vector and `pow(0,0)`/undefined powers. For each refusal assert unchanged readback sentinel and same-`Surface` recovery through public `discardRecordedOperations()`. Extend `W6dLightingPictureTest` to replay a 3D lighting Picture from memory and wire and show that changing only Z changes public pixels. No mocks, static-source or private tests.

- [ ] **Step 4: Run GREEN and preservation sequentially.** Run `rtk ./gradlew :gpu-plan:compileKotlin`, `rtk ./gradlew :gpu-renderer:compileKotlin`, `rtk ./gradlew :kanvas:compileTestKotlin`, `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'`, `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dLightingPictureTest'`, then `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'`. Record XML methods/F/E/S and native exit separately. Commit `feat(gpu): complete w6d six-family lighting`.

- [ ] **Step 5: Request Sol review.** Review the five new family formulas, mapped Z and spotlight cutoff, finite-domain acceptance, degenerate convention, public oracles and replay, RGBA8 premultiplication, exact source/output bounds, frozen resources/budgets and no late planning. Resolve Critical/Important findings before Task 4.

### Task 4: Picture Filter as Immutable SceneSnapshot

**Agent:** Terra implementation; Sol review.

**Outcome:** `ImageFilter.Picture` owns a bounded immutable `SceneSnapshot`, preserves explicit W6c node identity through Picture memory/wire replay, and renders through the same W6 graph with no live Surface or codec dependency.

**Files:**

- Create: `TEST/picture/W6dPictureRuntimeEffectPictureTest.kt`
- Create: `TEST/surface/W6dPictureFilterSurfacePixelTest.kt`
- Modify: `IR/EffectNode.kt`
- Modify: `IR/SceneArchiveCodec.kt`
- Modify: `API/picture/Picture.kt`
- Modify: `API/render/ir/PaintSceneAdapter.kt`
- Modify: `PLAN/W6bFilterPlanV1.kt`
- Modify: `PLAN/W6aLayerGraphConstruction.kt`
- Modify: `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`

**Interfaces:**

- Consumes: `ImageFilterNode.Picture.scene: SceneSnapshot`, Picture 15/schema 9 W6b node table after Task 3, `FilterPassOperationV1.Picture`, and current Picture readers.
- Produces: a sealed Picture FilterPass whose scene/cull/source rect are deep snapshots, replayed with the scope mapping and W6 frame budget; it is used by Task 7 budget/recovery coverage.

- [ ] **Step 1: Write public SceneSnapshot and wire witnesses.**

```kotlin
@Test fun `picture filter and image opacity survive memory wire replay and post capture mutation`() {
    val picture = recordedPictureFilterSource()
    val bytes = picture.toByteArray()
    val wire = assertNotNull(Picture.fromByteArray(bytes))
    val expected = expectedPictureFilterPixels()
    listOf(picture, wire).forEach { replay ->
        val surface = Surface(2, 1)
        surface.canvas { replay.playback(this) }
        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }
}
```

Include public methods `pictureFilterDeepSnapshotsMutableCullAndSourceRects`, `equalPictureFiltersRemainDistinctWithoutCapturedSharing`, and `oldPicture14Schema8ReaderKeepsUnrelatedNodesByteStable`.

- [ ] **Step 2: Run causal REDs.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'`

Expected: FAIL because W6c reserves Picture inputs/filters; old Picture reader selectors remain green.

- [ ] **Step 3: Capture and archive the bounded SceneSnapshot.**

```kotlin
is ImageFilter.Picture -> ImageFilterNode.Picture.of(
    scene = capturePicture(value.picture).requireCaptured(),
    cullRect = value.picture.cullRect.copy(),
    src = value.src?.copy(),
)
```

The archive references the table entry by stable captured ID, writes the `SceneSnapshot` through existing bounded Picture primitives, and rejects unknown ID/cycle/incoherent size before publishing a `Picture`. Equal values are not aliased; a truly shared captured node retains its single stable reference.

- [ ] **Step 4: Plan and materialize the Picture operation as W6 input.**

```kotlin
val operation = FilterPassOperationV1.Picture(
    scene = pictureNode.scene,
    cullRectF64 = mapping.mapRectBoundsF64OrNull(pictureNode.copyCullRect().toRectF64())
        ?: refuse(W6dPlanDiagnostics.PictureMapping),
    sourceRectF64 = pictureNode.copySource()?.toRectF64(),
    bounds = bounds,
    kind = FilterImplementationKindV1.PICTURE,
)
```

The plan recursively binds the captured scene only through W6c's occurrence/source-context rules and charges its targets, uniforms, images, staging, and leases before freeze. The renderer receives the selected nested operation/IDs; it does not call `Picture.playback`, capture a live `Surface`, or allocate a separate frame.

- [ ] **Step 5: Run GREEN and historical compatibility selectors.**

Run: `rtk ./gradlew :render-ir:compileKotlin`

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest'`

- [ ] **Step 6: Commit and request Sol review.**

```sh
git add render-ir gpu-plan gpu-renderer kanvas/src/main/kotlin/org/graphiks/kanvas/picture kanvas/src/test/kotlin/org/graphiks/kanvas/picture kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dPictureFilterSurfacePixelTest.kt
git commit -m "feat(kanvas): execute immutable w6d picture filters"
```

Sol checks SceneSnapshot immutability, wire compatibility, identity versus equality, bounded recursive planning, and absence of live-Surface/codec coupling.

### Task 5: Registered IMAGE_FILTER Runtime Effect

**Agent:** Terra implementation; Sol review.

**Outcome:** W6d executes the registered multi-ABI `kanvas.runtime.image-opacity` built-in, including absent `input -> ImplicitSource`, while unregistered/mismatched effects refuse before publication and W5h hashes remain unchanged.

**Files:**

- Modify: `IR/ResourceSnapshot.kt`
- Modify: `IR/EffectNode.kt`
- Modify: `PLAN/RuntimeEffectSemanticCatalog.kt`
- Modify: `PLAN/RuntimeEffectCpuEvaluatorV1.kt`
- Modify: `PLAN/W6bFilterPlanV1.kt`
- Modify: `PLAN/W6aLayerGraphConstruction.kt`
- Modify: `GPU/runtimeeffects/RuntimeEffectContracts.kt`
- Modify: `GPU/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
- Modify: `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- Modify: `TEST/picture/W6dPictureRuntimeEffectPictureTest.kt`
- Create: `TEST/surface/W6dRuntimeImageOpacitySurfacePixelTest.kt`

**Interfaces:**

- Consumes: Task 1 `RuntimeEffectSemanticKindV1.IMAGE_OPACITY`, `FilterPassOperationV1.RuntimeImageOpacity`, `RuntimeEffectAbi.IMAGE_FILTER`, and W6b `CapturedFilterInputV1.ImplicitSource` binding.
- Produces: `kanvas.runtime.image-opacity` v1 execution whose input/output bounds are identical; public positive, absent-input, wrong-ABI, unknown-ID, and replay tests.

- [ ] **Step 1: Add public RED cases.**

```kotlin
@Test fun `image opacity multiplies premultiplied rgba without spatial sampling`() {
    val expected = rgba(60, 30, 15, 128)
    val surface = Surface(1, 1)
    surface.canvas { drawFilteredSource(imageOpacity(alpha = .5f, input = null)) }
    val result = surface.render()
    assertContentEquals(expected, result.pixels)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}
```

Add public `explicitInputAndAbsentInputBindTheSameContext`, `unknownOrWrongAbiRefusesWithoutReadbackMutation`, and `runtimeImageOpacityWireReplayKeepsW5hHashes` methods.

- [ ] **Step 2: Run causal REDs.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dRuntimeImageOpacitySurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'`

Expected: FAIL with W6c's exact non-executable IMAGE_FILTER refusal; W5h shader runtime tests continue to pass.

- [ ] **Step 3: Select only the registered descriptor and seal the same-pixel operation.**

```kotlin
val descriptor = catalog.find(node.descriptor.id, node.descriptor.semanticVersionI32, node.descriptor.abiHash)
    ?: return refuse(W6dPlanDiagnostics.RuntimeEffectNotRegistered)
if (descriptor.semanticKind != RuntimeEffectSemanticKindV1.IMAGE_OPACITY) {
    return refuse(W6dPlanDiagnostics.RuntimeEffectAbiUnsupported)
}
val input = node.childAtOrNull("input") ?: CapturedFilterInputV1.ImplicitSource
```

Validate required `alpha: FLOAT`, IMAGE_FILTER child type, canonical semantic version/hash, finite uniform, and RGBA8 format. Store the selected program ID/uniform offset/input target in the existing FilterPass operation/resource bindings; never parse or compile arbitrary source.

- [ ] **Step 4: Encode the frozen same-pixel operation.**

```kotlin
fun evaluateImageOpacity(input: RuntimeEffectCpuColorF32, alphaF32: Float): RuntimeEffectCpuColorF32 =
    RuntimeEffectCpuColorF32(input.rF32 * alphaF32, input.gF32 * alphaF32, input.bF32 * alphaF32, input.aF32 * alphaF32)
```

The native program samples the sealed input coordinate once, multiplies premultiplied RGBA, and writes the sealed output target. It introduces no sampling transform, bounds expansion, dynamic program choice, or W5h descriptor mutation.

- [ ] **Step 5: Run GREEN and W5h preservation.**

Run: `rtk ./gradlew :render-ir:compileKotlin`

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :gpu-renderer:compileKotlin`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dRuntimeImageOpacitySurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest'`

- [ ] **Step 6: Commit and request Sol review.**

```sh
git add render-ir gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dRuntimeImageOpacitySurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6dPictureRuntimeEffectPictureTest.kt
git commit -m "feat(gpu): execute registered w6d image opacity runtime effect"
```

Sol checks multi-ABI catalogue keys, exact ABI/hash preservation, implicit binding context, uniform layout, CPU/native premultiplication agreement, and terminal refusals.

### Task 6: Backdrop and Filtered Previous Save/Restore Ordering

**Agent:** Terra implementation; Sol review.

**Outcome:** Backdrop snapshots/filtering happen at save before children; `initWithPrevious` remains an unfiltered save-time copy, while a layer DAG filters parent-plus-children only after children and before restore effects.

**Files:**

- Create: `TEST/surface/W6dBackdropPreviousSurfacePixelTest.kt`
- Modify: `PLAN/LayerScopePlanV1.kt`
- Modify: `PLAN/W6aLayerPlanCompiler.kt`
- Modify: `PLAN/W6aLayerGraphConstruction.kt`
- Modify: `PLAN/W6aLayerPlanBudget.kt`
- Modify: `PLAN/W6aPlanDiagnostics.kt`
- Modify: `PLAN/PlanPasses.kt`
- Modify: `GPU/planning/W6aLayerGraphLowerer.kt`
- Modify: `GPU/recording/GPUW6aLayerFramePlan.kt`
- Modify: `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- Modify: `GPU/execution/GPUW6aEncoderScopesV1.kt`
- Modify: `TEST/picture/W6dPictureRuntimeEffectPictureTest.kt`

**Interfaces:**

- Consumes: `BackdropInitializationPlanV1`, existing `LayerInitializationPlanV1.PreviousCopy`, Task 1 FilterPass operations, W6a destination versions, W6c layer-root filter references, and FilterTarget slots.
- Produces: frozen `TextureCopy + FilterPass + render-children + FilterPass* + LayerComposite` sequence and the discriminating public parent/child witness required by Review Focus 1.

- [ ] **Step 1: Write save-order RED cases with explicit public Render+Readback assertions.**

```kotlin
@Test fun `backdrop and filtered previous see parent and child at their specified times`() {
    val expected = rgba(17, 61, 211) + rgba(43, 181, 93)
    val surface = Surface(2, 1)
    surface.canvas {
        drawOpaque(0f, 2f, red)
        saveLayer(SaveLayerRec(backdrop = invertFilter(), paint = Paint(imageFilter = imageOpacity(.5f))))
        drawOpaque(0f, 1f, blue)
        restore()
    }
    val result = surface.render()
    assertContentEquals(expected, result.pixels)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}
```

Add `filteredPreviousCopiesUnfilteredParentAtSaveThenFiltersAfterChild`, `nestedBackdropReadsImmediateParentNotRoot`, `backdropAndPreviousAreExclusiveBackdropWins`, `filteredPreviousIgnoresRestrictiveHintWhenRestoreChangesTransparentBlack`, and memory/wire Picture replay cases. Compute each expected sequence before `Surface` creation.

- [ ] **Step 2: Run causal REDs.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dBackdropPreviousSurfacePixelTest'`

Expected: FAIL on current W6a unsupported-backdrop/unsupported-spatial diagnostics or W6c ordering gap, with no changed sentinel.

- [ ] **Step 3: Freeze save-time snapshots and post-child DAG sequence.**

```kotlin
if (descriptor.backdrop !is EffectStack.Empty) {
    passes += PlanPass.TextureCopy(passes.size, parentTarget, backdropSnapshot, parentBounds, Point2I32.Origin)
    passes += PlanPass.FilterPass(passes.size, listOf(backdropSnapshot), filteredBackdrop, backdropPayload)
    initialization = LayerInitializationPlanV1.Backdrop(BackdropInitializationPlanV1(
        parentTarget, backdropSnapshot, filteredBackdrop, capturedParentVersion, backdropFilterRoot,
    ))
} else if (descriptor.initWithPrevious) {
    passes += PlanPass.TextureCopy(passes.size, parentTarget, layerTarget, previousBounds, previousOrigin)
    initialization = LayerInitializationPlanV1.PreviousCopy(
        parentTarget, layerTarget, capturedParentVersion, previousBounds, previousOrigin,
    )
}
```

Use the captured parent `DestinationVersionI64`, close/load parent segments around copies, and never sample an active attachment. Emit layer-root filter passes only after the final child render segment, then apply existing restore alpha/color filter/blend once. Backdrop has precedence and is exclusive with previous initialization.

- [ ] **Step 4: Seal snapshots, targets, budgets, and native lifecycle.**

```kotlin
val reservation = W6aLayerPlanBudget.reserveAll(resources, passes)
require(reservation.peakBytesI64 <= budget.frameLocalBytesI64)
val ready = materializer.prepareFrozen(framePlan, reservation)
queue.publish(ready) // only after every target/program/binding validates
```

Charge parent snapshots, filtered backdrop target, previous copy, all DAG intermediates, uniforms, samplers, programs, staging, alignment, and leases before `Ready`. On any prepare/bind error discard/quarantine the whole ready token.

- [ ] **Step 5: Run GREEN and W6a order preservation.**

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :gpu-renderer:compileKotlin`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dBackdropPreviousSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aInitWithPreviousSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'`

- [ ] **Step 6: Commit and request Sol review.**

```sh
git add gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dBackdropPreviousSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6dPictureRuntimeEffectPictureTest.kt
git commit -m "feat(gpu): execute w6d backdrop and filtered previous layers"
```

Sol checks physical command order, immediate-parent versions/origins, backdrop precedence, no active attachment sample, post-child filtering, restore ordering, and atomic ready-token lifecycle.

### Task 7: W6d Atomic Budget, Exact F16 Refusal, Recovery, and Stack Gate

**Agent:** Terra implementation; Sol review.

**Outcome:** W6d's complete advanced graph has B/B-1 public proof, exact RGBA8/F16 admission behavior, terminal recovery, targeted preservation, durable status, one whole-branch Sol review, and one stacked Draft PR.

**Files:**

- Create: `TEST/surface/W6dAdvancedRecoverySurfacePixelTest.kt`
- Modify: `PLAN/W6aLayerPlanBudget.kt`
- Modify: `PLAN/W6aPlanDiagnostics.kt`
- Modify: `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `API/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `refactor/waves/W06-layers-effects/status.md`
- Modify: `refactor/README.md`
- Modify: `refactor/plans/2026-09-22-w6d-advanced-effects-implementation-plan.md`

**Interfaces:**

- Consumes: every frozen Task 1–6 resource/pass/slot, W6c cache/lease rules, public `Surface.render()`, `Surface.readPixels`, `Surface.discardRecordedOperations()`, and W6 diagnostics.
- Produces: `w6d.layer.frame_budget_exceeded`, exact F16 capability refusal, no-publication/same-surface recovery proof, status custody, and the W6d Draft PR gate.

- [ ] **Step 1: Add B/B-1, F16, late-refusal, and recovery cases.**

```kotlin
@Test fun `exact budget accepts and one byte less refuses without readback publication then recovers`() {
    val accepted = advancedWitnessSurface(frameLocalBudgetBytes = W6D_BUDGET_BYTES)
    assertContentEquals(expectedPixels, accepted.render().pixels)

    val refused = advancedWitnessSurface(frameLocalBudgetBytes = W6D_BUDGET_BYTES - 1L)
    assertTerminalWithoutReadbackMutation(refused, "w6d.layer.frame_budget_exceeded")
    refused.discardRecordedOperations()
    refused.canvas { drawOpaque(0f, 1f, green) }
    assertContentEquals(rgba(43, 181, 93), refused.render().pixels)
}
```

Add `f16RequestsRefuseWithExactCapabilityBeforeReadback`, `warmReplayKeepsPessimisticBudget`, `lateAdvancedSiblingRefusalPublishesNoHealthySibling`, and `allElevenFamiliesUseOneFrozenGraph` using public output only. Derive B from documented fixture dimensions/resource formula, never by asking the planner in the test.

- [ ] **Step 2: Run causal REDs.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedRecoverySurfacePixelTest'`

Expected: FAIL while final accounting/router diagnostics are incomplete; no internal telemetry is asserted.

- [ ] **Step 3: Complete pre-publication accounting and terminal ownership.**

```kotlin
val physicalPeakI64 = checkedPhysicalAllocationPeakI64(frozenSlots)
val semanticPeakI64 = peakFrameLocalBytesI64(spans, passes.size)
val peakI64 = maxOf(physicalPeakI64, semanticPeakI64)
if (peakI64 > budget.frameLocalBytesI64) throw W6aResourceLimitFailure(W6dPlanDiagnostics.FrameBudgetExceeded)
```

Require every FilterTarget/snapshot/previous copy/buffer/program/sampler/staging resource to have an owner, generation, descriptor, usage, slot, lifetime, and checked I64 charge before freeze. Cache hits retain pessimistic B and leases. Candidate gate/router terminalize W6d-owned F16, runtime, resource, and late material failures rather than entering a prepared route.

- [ ] **Step 4: Run W6d shards and targeted prior-wave preservation sequentially.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedSamplingSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dRuntimeImageOpacitySurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dBackdropPreviousSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedRecoverySurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6cSpatialDagPictureTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialCacheRecoverySurfaceTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aInitWithPreviousSurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'`

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest'`

- [ ] **Step 5: Compile touched modules sequentially and audit only manually.**

Run: `rtk proxy ./gradlew :math:geometry:compileKotlinJvm`

Run: `rtk proxy ./gradlew :math:matrix:compileKotlinJvm`

Run: `rtk ./gradlew :render-ir:compileKotlin`

Run: `rtk ./gradlew :gpu-plan:compileKotlin`

Run: `rtk ./gradlew :gpu-renderer:compileKotlin`

Run: `rtk ./gradlew :kanvas:compileKotlin`

Run: `rtk ./gradlew :kanvas:compileTestKotlin`

Manually search the W6d production path for renderer-local bounds conversions, post-freeze resource/pass/ID creation, `GPUPreparedCompositeLowerer`, `GPUPreparedSurfaceProductEntry`, and legacy filter dispatch. Classify legitimate W8 references in review notes; do not create a static-source test.

- [ ] **Step 6: Update durable status and commit.** Record exact test/XML custody, Gradle exits, source commit, native status (`133`/`134` = `UNKNOWN`), B/B-1 formula, accepted RGBA8, exact F16 refusal, exclusions, no ISO/global claim, and W6e as the next wave.

```sh
git add gpu-plan kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dAdvancedRecoverySurfacePixelTest.kt refactor
git commit -m "feat(gpu): close w6d advanced effect frame ownership"
```

- [ ] **Step 7: Request whole-branch Sol review and run the one bounded correction policy.** Review `codex/w6c-spatial-dag..HEAD` for spec coverage, all eleven families, bounds/mappings, save order, immutable Picture/wire, runtime ABI/hash isolation, resources/lifetimes/budgets, atomic visibility, public evidence, and legacy fallback. If there is a Critical/Important finding, Terra makes one bounded correction commit, reruns only causally affected Task 2–7 selectors plus preservation, then requests one scoped Sol re-review.

- [ ] **Step 8: Create the stacked Draft PR only after green gates.** Push `codex/w6d-advanced-effects` and create/update one Draft PR targeting `codex/w6c-spatial-dag`. Its description names the seven task commits, exact gates/custody, accepted RGBA8, F16 refusal, exclusions, native `UNKNOWN` when applicable, one Sol review/fix-wave status, and W6e as next step. Do not merge.

## W6d Definition of Done

- [ ] MatrixConvolution, DisplacementMap, Magnifier, six lighting families, Picture, and registered RuntimeEffect execute through W6c occurrence binding and W6d arms on `FilterPassOperationV1` only.
- [ ] Backdrop snapshots the immediate parent at save and filters before child draws; filtered previous copies unfiltered parent at save and evaluates its DAG only after children.
- [ ] `ImageFilter.Picture` is a bounded immutable `SceneSnapshot`; memory/wire replay, mutation isolation, stable references, malformed-table refusal, and historical readers are publicly covered.
- [ ] `kanvas.runtime.image-opacity` v1 has IMAGE_FILTER ABI, required `input` image child, F32 `alpha`, same-pixel premultiplied RGBA multiplication, unchanged bounds, absent-input `ImplicitSource`, and no arbitrary runtime frontend; W5h hashes are unchanged.
- [ ] Only RGBA8 is positively admitted; F16/HDR gets an exact capability refusal with no silent format substitution.
- [ ] `:math` owns new geometry; `:gpu-plan` freezes only FilterPass/FilterTarget extensions; renderer has no post-freeze choice, replan, bounds reconstruction, target resize, pass insertion, ID rewrite, or legacy fallback.
- [ ] Every resource, cache lease, snapshot, target, program, uniform, sampler, staging buffer, alignment, and allocation slot is budgeted with checked I64 before allocation; B/B-1, terminal no-publication, and same-surface recovery are publicly proved.
- [ ] Each positive W6d witness asserts public Render+Readback scope evidence; exact and family-oracle tolerance policies remain separate; no prohibited private/infrastructure tests exist.
- [ ] Required W6d shards, targeted W6a/W5h preservation, and touched-module compilations pass with recorded custody; native exits 133/134 remain `UNKNOWN`.
- [ ] One whole-branch Sol review has no open Critical/Important finding after at most one bounded correction wave, and one unmerged Draft PR is stacked on W6c.

## Plan Self-Review

- **Spec coverage:** Tasks 2–5 cover the eleven advanced families; Task 6 covers backdrop and filtered previous timing; Task 4 covers immutable Picture/table/wire; Task 5 covers multi-ABI image-opacity and W5h hash stability; Task 7 covers RGBA8/F16, budgets, atomicity, recovery, custody, review, and Draft PR. Fonts, codecs, GMs, arbitrary frontend code, legacy removal, global convergence, and unobservable device loss are explicitly excluded.
- **Type consistency:** Every later task consumes W6b `FilterPassOperationV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `PlanResourceRole.FilterTarget`, Task 1 `RuntimeEffectSemanticKindV1`, and F64/I32 bounds helpers. Backdrop uses `BackdropInitializationPlanV1`; filtered previous retains existing `LayerInitializationPlanV1.PreviousCopy`; both use `PlanPass.TextureCopy` plus the same `PlanPass.FilterPass` type.
- **Review Focus:** each of the five header focus lines names its owning public test in Tasks 2–7, including the parent/child timing witness, family edge oracles, lighting degenerates, Picture/runtime wire isolation, and B/B-1 recovery.
- **Placeholder scan:** an `rg` scan for deferred-work markers found none outside this review item; every task has concrete files, interfaces, tests, commands, implementation snippets, review gate, and commit.

## Execution Handoff

The plan is ready for `superpowers:subagent-driven-development`: Terra implements each Task 1–7 in order, Sol reviews each task and the whole branch, and there is exactly one bounded Terra correction wave if the whole-branch review needs it. Do not begin W6e until Task 7 has either opened the W6d Draft PR or recorded a concrete blocker in the durable W06 status.
