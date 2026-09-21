# W6a Layer Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one plan-first authority for `saveLayer`, restore alpha/color filter/blend, offscreen targets, nested layers, and unfiltered `initWithPrevious`, with public Surface/Picture evidence and no semantic fallback after W6a admission.

**Architecture:** Capture the public layer record in immutable Scene IR, derive occurrence-based scopes and F64 mappings in `:gpu-plan`, freeze one frame-wide `RenderGraph` with globally unique resources/passes/budgets, then let `:gpu-renderer` materialize that exact graph. The legacy prepared-layer labels remain a W8 compatibility path and are never promoted as W6 authority.

**Tech Stack:** Kotlin/JVM; `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; JUnit 5; public `Surface`, `Canvas`, `Picture`, and paint APIs.

**Spec:** `refactor/specs/2026-09-16-w6-layers-effects-design.md`, especially sections 5–8, 10.1, 11–16.

## Global Constraints

- Base branch: `codex/w5h-registered-runtime-effects` at `8bdc730c9e8c55f159547135263bc85fa055445a`; implementation branch: `codex/w6a-layer-authority`. The W6a Draft PR targets W5h directly.
- Read the complete approved spec before every task. If repository reality conflicts with it, stop that task and report the exact conflict; do not silently add a second authority.
- Use RED → GREEN → refactor for each public behavior. RED must fail for the intended behavior on unchanged production, not for a harness, fixture, permission, or compilation problem.
- Tests use only public `Surface`, `Canvas`, `Picture`, paint/layer APIs, public bytes, public diagnostics, readback buffers, and recovery. Do not add tests for private/internal code, source shape, reflection, counters, fake devices, injected capabilities, call counts, mocks, or infrastructure.
- Pixel expectations are computed independently before creating the `Surface` or `PictureRecorder`. Exact identity/copy/composite cases are byte-exact; do not add a global similarity threshold.
- New rectangles, points, sizes, matrices, and mappings belong only in `:math`, using I32/I64/F32/F64 nomenclature. Renderer-owned `GPUBounds`, `GPUPixelBounds`, labels, or string rectangles are not W6 plan values.
- Fonts, glyph generation, codecs or external format decode/encode, Skia GMs, dashboard, renders, references, scores, rebaseline, `jpg-color-cube`, and `:integration-tests:skia` are outside every gate.
- W6a excludes backdrop and every spatial image/mask filter. Those inputs must retain a precise pre-publication diagnostic and remain W6d/W6b work; do not approximate them.
- RGBA8 is the only positive W6a color target. F16/HDR remains a precise capability gap; never substitute RGBA8 silently.
- Serialize all Gradle invocations. Run shell commands through `rtk` or `rtk proxy` in `/Users/chaos/.codex/worktrees/cbf6/kanvas`.
- A native process exit 133 has cause `UNKNOWN` unless concrete evidence proves otherwise. Record Gradle exit, XML methods, native exit, and source custody separately.
- Preserve W4/W5 authorities: geometry coverage, material tables, color-filter numeric graphs, final `BlendPlan`, destination versions, image ownership, runtime catalogue, pessimistic budget, cache generation, and leases to completion/quarantine.
- A W6a-admitted frame is atomic: no healthy sibling is publicly returned if any scope refuses. No route may fall back to the prepared/legacy layer path after W6a selection.
- Durable tracking is limited to this plan, `refactor/README.md`, `refactor/waves/W06-layers-effects/status.md`, and the approved spec. Agent scratch reports remain gitignored.
- Task implementation agents: Terra is acceptable for Task 1 and documentation-only work; use Astra for Tasks 2–7 because they change architecture, numeric bounds, resource lifetimes, or native ordering. Sol is review-only. Each task ends in one implementation commit, one Sol review, at most one bounded correction commit, and one Sol re-review when correction is needed.

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
RES = kanvas/src/test/resources/picture/
```

| Existing owner | W6a responsibility |
| --- | --- |
| `API/canvas/SaveLayerRec.kt`, `Canvas.kt`, `DisplayOp.kt`, `DisplayOpSnapshot.kt` | Add and deeply snapshot public `initWithPrevious=false`; keep Canvas as capture only. |
| `IR/SceneCommand.kt`, `API/render/ir/DisplayOpSceneAdapter.kt`, `SceneDisplayOpAdapter.kt` | Carry the flag and immutable layer descriptor; preserve flat command order as capture truth. |
| `IR/SceneArchiveCodec.kt`, `API/picture/Picture.kt` | Write Picture 13/schema 7; read 8–13; default the absent field to false for schema ≤6. |
| `GEOM/RectF64.kt`, `RectI32.kt`, `MATRIX/Matrix3x3F64.kt` | Supply checked outward projection and layer/device mapping without renderer-local geometry. |
| `PLAN/GpuPlanCompiler.kt`, `CapabilityCompilerChain.kt` | Select one W6a candidate for the complete layered scene and bind the same runtime catalogue used by W5 children. |
| `PLAN/RenderGraph.kt`, `RenderGraphConstruction.kt`, `PlanResources.kt`, `PlanPasses.kt` | Freeze per-target resources, passes, dependencies, destination versions, IDs, lifetimes, and peak budget for the whole frame. |
| `PLAN/FrameSourceLayoutV4.kt`, `MaterialSourceConstructionV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt` | Extend the existing W5 source/material issuer from one common target extent to explicit pre-publication target bindings while retaining one frame inventory and permit. |
| `PLAN/W5aCompositePlanCompiler.kt`, `SourceDeferredRenderConstructionV4.kt`, `W5bDestinationGraph.kt` | Reuse W4/W5 child lanes before publication and bind them into a scope target; never publish independent child graphs with colliding IDs. |
| `GPU/planning/GpuRenderContext.kt`, `GpuPlanTaskListLowerer.kt` | Add W6a compiler/lowering dispatch and validate the exact compiler witness. |
| `GPU/execution/GPUFramePreflighter.kt`, `GPUWgpu4kFramePayloadMaterializerDispatcher.kt`, `GPUWgpu4kFrameEncodingBackend.kt` | Reserve/materialize the sealed targets and encode the already ordered render/copy/restore passes behind one draft/ready lifecycle. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `GPUPlanSurfaceRouter.kt` | Recognize W6a layers, route the full Scene through plan-first, and make W6a refusal terminal instead of continuing to legacy. |

The following legacy files are not W6a authorities and must not gain new semantic decisions:

- `GPU/layers/LayerContracts.kt`
- `GPU/layers/GPUPreparedCompositeLowerer.kt`
- `API/surface/gpu/GPUPreparedCompositeCapture.kt`
- `API/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- `GPU/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- `GPU/execution/GPUPreparedSurfaceNativePreflight.kt`
- `GPU/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`

They may continue serving non-W6 legacy calls until W8, but W6a must not copy their label-based scope, `.toInt()` bounds, nesting refusal, target-size rewrite, or late packet retargeting.

---

## Frozen W6a Contracts

### Geometry contracts in `:math`

`GEOM/RectProjectionF64.kt` owns checked texel projection:

```kotlin
public fun RectF64.roundOutToRectI32OrNull(): RectI32?
public fun RectI32.translateCheckedOrNull(delta: Vector2I32): RectI32?
```

`roundOutToRectI32OrNull` rejects non-finite, unsorted, empty-after-rounding, and I32-overflow inputs. It applies `floor` to left/top and `ceil` to right/bottom before narrowing. `translateCheckedOrNull` uses I64 intermediates and never saturates.

`MATRIX/LayerMappingF64.kt` owns the mapping snapshot:

```kotlin
public class LayerMappingF64 private constructor(
    localToDeviceF64: Matrix3x3F64,
    deviceToLayerF64: Matrix3x3F64,
    localToLayerF64: Matrix3x3F64,
    layerOriginDeviceI32: Point2I32,
) {
    public fun copyLocalToDeviceF64(): Matrix3x3F64
    public fun copyDeviceToLayerF64(): Matrix3x3F64
    public fun copyLocalToLayerF64(): Matrix3x3F64
    public fun copyLayerOriginDeviceI32(): Point2I32

    public companion object {
        public fun ofOrNull(
            localToDeviceF64: Matrix3x3F64,
            layerOriginDeviceI32: Point2I32,
        ): LayerMappingF64?
    }
}

public fun Matrix3x3F64.mapRectBoundsF64OrNull(boundsF64: RectF64): RectF64?
```

Perspective mapping rejects non-finite homogeneous coordinates and any edge whose endpoint W values are zero or have opposite signs; it never bounds a horizon-crossing rectangle from four divided corners.

### Semantic layer contracts in `:gpu-plan`

`PLAN/LayerScopePlanV1.kt` contains these exact owners:

```kotlin
@JvmInline
public value class LayerScopeIdI32(public val valueI32: Int)

public sealed interface LayerInitializationPlanV1 {
    public data object TransparentBlack : LayerInitializationPlanV1
    public class PreviousCopy internal constructor(
        public val parentTarget: PlanResourceId,
        public val layerTarget: PlanResourceId,
        public val capturedParentVersion: DestinationVersionI64,
        sourceBoundsParentI32: RectI32,
        destinationOriginLayerI32: Point2I32,
    ) : LayerInitializationPlanV1 {
        public fun copySourceBoundsParentI32(): RectI32
        public fun copyDestinationOriginLayerI32(): Point2I32
    }
}

public class LayerBoundsPlanV1 internal constructor(
    requestedHintDeviceF64: RectF64?,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    requiredInputDeviceI32: RectI32,
    producedOutputDeviceI32: RectI32?,
    compositeDomainDeviceI32: RectI32,
) {
    public fun copyRequestedHintDeviceF64(): RectF64?
    public fun copyKnownContentDeviceI32(): RectI32?
    public fun copyDesiredOutputDeviceI32(): RectI32
    public fun copyRequiredInputDeviceI32(): RectI32
    public fun copyProducedOutputDeviceI32(): RectI32?
    public fun copyCompositeDomainDeviceI32(): RectI32
}

public class LayerRestorePlanV1 internal constructor(
    public val alphaF32: Float,
    public val colorFilter: ColorFilterExecutionPlanV1?,
    public val blend: BlendPlan,
    public val readsPriorDevice: Boolean,
    public val restoreAffectsTransparentBlack: Boolean,
    public val parentVersionBefore: DestinationVersionI64,
    public val parentVersionAfter: DestinationVersionI64,
)

public class LayerScopePlanV1 internal constructor(
    public val id: LayerScopeIdI32,
    public val parentId: LayerScopeIdI32?,
    public val beginCommandIndexI32: Int,
    public val endCommandIndexI32: Int,
    childIds: List<LayerScopeIdI32>,
    public val mapping: LayerMappingF64,
    public val bounds: LayerBoundsPlanV1,
    public val initialization: LayerInitializationPlanV1,
    public val restore: LayerRestorePlanV1,
    public val targetResource: PlanResourceId,
) {
    public fun childIds(): List<LayerScopeIdI32>
}

public sealed interface LayerExecutionStepV1 {
    public val scopeId: LayerScopeIdI32
    public val passId: PlanPassId
    public data class Initialize(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1
    public data class RenderChildren(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1
    public data class Restore(
        override val scopeId: LayerScopeIdI32,
        override val passId: PlanPassId,
    ) : LayerExecutionStepV1
}

public class LayerFramePlanV1 internal constructor(
    scopes: List<LayerScopePlanV1>,
    executionSteps: List<LayerExecutionStepV1>,
) {
    public fun scopes(): List<LayerScopePlanV1>
    public fun executionSteps(): List<LayerExecutionStepV1>
}
```

The constructors snapshot mutable math values and expose defensive copies. Scope IDs are assigned by BeginLayer occurrence in source order, starting at zero; equal descriptors never share an ID. Execution order preserves parent draws before/after a child and restores inner scopes before their parent.

`PLAN/FrameSourceLayoutV4.kt` remains the only W5 material/source issuer. Add an explicit pre-publication binding value:

```kotlin
internal class FrameSourceTargetBindingV1(
    val scopeId: LayerScopeIdI32?,
    val targetResource: PlanResourceId,
    targetExtentI32: SizeI32,
    targetOriginDeviceI32: Point2I32,
    laneOrdinalsI32: List<Int>,
) {
    fun copyTargetExtentI32(): SizeI32
    fun copyTargetOriginDeviceI32(): Point2I32
    fun laneOrdinalsI32(): List<Int>
}
```

`FrameSourceLayoutV4.layeredFrame(...)` accepts all deferred W4/W5 lanes plus their target bindings, interns one material table, authenticates one image/noise/runtime resource inventory and one pessimistic W5 permit, then returns unpublished bound constructions. Unlike `ordinaryComposite`/`nativeComposite`, it does not require equal target extents and does not synthesize a target or readback. `W6aLayerGraphConstruction` alone assigns physical targets/readback and publishes the final graph. No layer-specific material issuer is allowed in Task 7.

`PLAN/W6aLayerPlanBudget.kt` also seals the physical allocation inventory:

```kotlin
@JvmInline
internal value class LayerPhysicalAllocationSlotI32(val valueI32: Int)

internal class LayerPhysicalAllocationPlanV1(
    val slot: LayerPhysicalAllocationSlotI32,
    resourceIds: List<PlanResourceId>,
    val reservedBytesI64: Long,
) {
    fun resourceIds(): List<PlanResourceId>
}
```

Every resource is mapped to exactly one slot before freeze. Two resources may share a slot only when the plan proves non-overlapping semantic lifetimes and identical descriptor, usages, format, sample count, alignment, and reservation size. Until that alias proof is implemented, every layer target owns a distinct slot and every such slot is charged from native preparation through completion/quarantine.

### Physical graph contracts

- Add `PlanResourceRole.LayerTarget` for single-sample RGBA8 offscreen targets.
- Extend `PlanPass.TextureCopy` with a defensively copied `destinationOriginI32: Point2I32 = Point2I32.Origin`.
- Add `PlanPassRole.LayerComposite` and `PlanPass.LayerComposite` carrying scope ID, source target, destination parent target, source bounds, destination origin, `LayerRestorePlanV1`, load/store, and destination version after.

```kotlin
public class LayerComposite(
    override val ordinal: Int,
    public val scopeId: LayerScopeIdI32,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    sourceBoundsLayerI32: RectI32,
    destinationOriginParentI32: Point2I32,
    public val restore: LayerRestorePlanV1,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val destinationVersionAfter: DestinationVersionI64,
) : PlanPass {
    public fun copySourceBoundsLayerI32(): RectI32
    public fun copyDestinationOriginParentI32(): Point2I32
}
```

- Add `RenderGraph.layerFramePlanOrNull()` and `RenderGraph.verifyW6aLayerCompilerWitness()`; only `W6aLayerPlanCompiler` may issue that witness.
- `RenderGraph` remains the sole physical resource/pass DAG. `LayerFramePlanV1` is semantic metadata referencing its IDs, not a second resource graph.
- Validate attachment state per target, not globally: each target begins with transparent clear or an explicit previous-content copy, then loads; the root remains `LogicalTarget`, and offscreen targets are `LayerTarget` with their own extent/origin.
- Every physical copy/composite rectangle is sealed in the texel space of the resource it addresses. Convert device-space bounds to parent-target texels in `:gpu-plan` by checked subtraction of `FrameSourceTargetBindingV1.targetOriginDeviceI32`; convert child output to layer-target texels the same way. `:gpu-renderer` receives `sourceBoundsParentI32`, `sourceBoundsLayerI32`, and `destinationOriginParentI32` and performs no device↔target coordinate conversion.
- Allocate final `PlanResourceId` and `PlanPassId` ordinals once for the whole frame before publication. Child W4/W5 constructions are rebound while still unpublished; independently published child graphs are forbidden.
- Track semantic pass lifetimes and physical allocation lifetimes separately. Every distinct target/view prepared in one native draft is physically live from preparation through completion/quarantine and is charged for that whole interval, even when sibling pass lifetimes do not overlap. Reducing this sum to a usage peak is legal only when the frozen plan assigns compatible non-overlapping resources to the same explicit physical allocation slot.
- Peak frame-local bytes are the checked maximum of the semantic pass-lifetime peak and the sealed physical-allocation peak, across parent/child targets, previous copies, destination snapshots, draw buffers, images, uniforms, and staging. All I64 additions/multiplications are checked before `Ready`.

---

## Task 1: Public Capture and Picture 13 Compatibility

**Agent:** Terra implementation; Sol review.

**Outcome:** `initWithPrevious` is a public immutable captured value, Picture 13/schema 7 writes it, and Picture 8–12 read it as false when absent. No positive W6 execution is claimed yet.

**Files — Create:**

- `TEST/picture/W6aLayerPictureTest.kt`
- `RES/format-12-layer-default.base64`
- `RES/format-8-schema-1-layer-default.base64`

**Files — Modify:**

- `API/canvas/SaveLayerRec.kt`
- `IR/SceneCommand.kt`
- `API/render/ir/DisplayOpSceneAdapter.kt`
- `API/render/ir/SceneDisplayOpAdapter.kt`
- `IR/SceneArchiveCodec.kt`
- `API/picture/Picture.kt`
- `TEST/picture/PictureTest.kt`
- `TEST/picture/W5hRuntimeEffectPictureTest.kt`

### Contract

- Append `public val initWithPrevious: Boolean = false` after the existing internal `compositeClip` constructor parameter in `SaveLayerRec` so internal historical positional calls keep their fourth argument.
- Add the boolean to `LayerDescriptor.of(...)`, its immutable state, and canonical identity domain `layer-descriptor-v4`.
- Writer version becomes Picture 13/schema 7. Preserve every currently decoded pair: version 8 accepts schema 1..2, 9 accepts 1..3, 10 accepts 1..4, 11 accepts 1..5, 12 accepts 1..6, and 13 accepts 1..7. Keep `FORMAT_VERSION=10`; set `STABLE_WIRE_VERSION=13` and `PREVIOUS_STABLE_WIRE_VERSION=12`, and keep explicit facade dispatch for versions 10, 11, and 12 rather than replacing the old previous-version case.
- Schema 7 writes one boolean in each layer record after the existing layer fields. Schema ≤6, legacy v1–7, and old v8 construct false. Do not change `PictureWireV8.kt`.
- `DisplayOpSnapshot` must preserve the flag through its existing `copy`; bounds and referenced color matrices remain deep snapshots.

### Steps

- [ ] Add a compile-safe `currentWriterUsesPicture13Schema7` assertion through public Picture bytes and run it against unchanged production. Record its causal RED on the existing 12/6 header; do not use a missing source symbol or compilation failure as RED evidence.
- [ ] Before any production edit, generate `format-12-layer-default.base64` from the W5h source with a bounded, SRC_OVER, default-false layer. Capture `format-8-schema-1-layer-default.base64` from the repository commit whose public writer emitted version 8/schema 1. Record both provenances, add only `historicalV12LayerDefaultsPreviousToFalse` and `historicalVersion8Schema1StillDecodes`, and render both through `Picture.fromByteArray` to prove GREEN controls rather than mere parsing. Keep one-off producers out of the commit.
- [ ] Add only the public `SaveLayerRec.initWithPrevious=false` signature so the behavioral tests compile; do not yet encode, adapt, canonicalize, or render it.
- [ ] Add `writer13DistinguishesPreviousFalseAndTrue`, `roundTripReencodesTheSamePreviousFlag`, and `postAppendMutationDoesNotChangeLayerBytes` using only public Picture APIs and public bytes. Run them and record a behavioral RED on lost/equal bytes, never a compilation RED.
- [ ] Implement the capture, IR, adapter, canonical identity, writer, reader, and facade version changes.
- [ ] Update only assertions that intentionally pin the current writer version/schema. Preserve real historical fixtures and their expected behavior.
- [ ] Run:

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.PictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest'
```

- [ ] Commit: `feat(picture): capture w6a init-with-previous`
- [ ] Request Sol review on the Task 1 commit; if findings exist, apply one bounded correction and request one scoped Sol re-review.

---

## Task 2: Single-Layer Plan Authority and Transparent Offscreen Execution

**Agent:** Astra implementation; Sol review.

**Outcome:** One simple layer is captured from Scene IR, planned into a typed offscreen target, rendered by existing W4/W5 child lanes, restored once into the root, and read back without invoking legacy layer planning.

**Files — Create:**

- `GEOM/RectProjectionF64.kt`
- `MATRIX/LayerMappingF64.kt`
- `PLAN/LayerScopePlanV1.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/W6aPlanDiagnostics.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `TEST/surface/W6aLayerSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/CapabilityCompilerChain.kt`
- `PLAN/RenderGraph.kt`
- `PLAN/RenderGraphConstruction.kt`
- `PLAN/PlanIdentity.kt`
- `PLAN/PlanResources.kt`
- `PLAN/PlanPasses.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `PLAN/MaterialSourceConstructionV4.kt`
- `PLAN/MaterialSourceFootprintV4.kt`
- `PLAN/RawMaterialRequirementsV2.kt`
- `PLAN/SourceDeferredRenderConstructionV4.kt`
- `PLAN/W5aCompositePlanCompiler.kt`
- `GPU/planning/GpuRenderContext.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/execution/GPUFramePreflighter.kt`
- `GPU/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- `API/surface/gpu/GPUPlanSurfaceRouter.kt`

### Contract

- `W6aLayerPlanCompiler` implements `GpuPlanCompiler`, owns every Scene containing BeginLayer/EndLayer regardless of positive support, and carries `CAPABILITY_ID = "w6a.layer.v1"`. Ownership recognition happens before color format, effects, bounds, and capability admission.
- Selection positively admits only balanced W6a scopes. It rejects backdrop, image filter, mask filter, preserve-LCD requests, unsupported target format including F16, malformed stack, non-finite transforms, and W6b/W6d inputs with stable W6 diagnostics before a graph is published. Because the layered scene is already W6-owned, each refusal is terminal and cannot continue to legacy.
- The compiler partitions commands into root/layer segments by occurrence, asks the existing chain for unpublished W4/W5 source constructions, and binds each segment to its scope target with one frame-wide ID allocator.
- All segments cross `FrameSourceLayoutV4.layeredFrame(...)` before publication. That existing owner interns one material table and authenticates the frame-wide image/noise/runtime inventory without imposing a common target extent or manufacturing a root target/readback.
- The initial positive slice is transparent-black initialization plus a single SRC_OVER restore with alpha 1 and no restore color filter. It supports bounded and unbounded layers; unbounded means the effective parent clip/target, not `unsupported.layer.bounds_unbounded`.
- The renderer receives `LayerFramePlanV1` plus exact graph passes/resources. It may translate math values to native descriptors, but cannot recompute scope, bounds, origin, pass order, target size, format, usages, or byte estimate.
- Use existing draft → ready → consumed → submitted → completion/quarantine lifecycle. Do not call `GPUBackendRuntimeNative.copyTargetToOffscreenTexture`, submit a side command buffer, or reuse label-clamped offscreen helpers.

### Public RED/GREEN cases

- `boundedLayerIsolatesOverlappingChildren`
- `unboundedLayerUsesTheParentClip`
- `emptyTransparentLayerIsAVisualNoOp`
- `twoEqualLayerDescriptorsRemainDistinctOccurrences`
- `ordinaryLayerDoesNotUseLegacyFallback`
- `unsupportedBackdropRefusesTerminallyAndRecovers`
- `unsupportedSpatialFilterRefusesTerminallyAndRecovers`
- `unsupportedF16LayerRefusesTerminallyAndRecovers`

`ordinaryLayerDoesNotUseLegacyFallback` proves route ownership using only public behavior: choose a child material already rejected by the legacy prepared composite but accepted by W5, and assert exact pixels; do not inspect evidence counters or internal scope kinds.

### Steps

- [ ] Add the eight public tests and run them on unchanged production. Confirm REDs are the existing layer refusal/wrong route or legacy continuation, while a direct no-layer W5 control remains GREEN. For each unsupported case, assert the public diagnostic, unchanged sentinel readback, balanced discard/re-record, and same-Surface recovery.
- [ ] Implement checked F64→I32 projection and immutable layer mapping in `:math`; do not add direct math/infrastructure tests. The public translated/fractional cases in Task 3 own their behavioral proof.
- [ ] Implement the frozen semantic contracts and W6a compiler selection. Bind the runtime catalogue in `CapabilityCompilerChain.bindRuntimeCatalog`.
- [ ] Add `LayerTarget`, typed layer passes, per-target validation, final global ID allocation, W6a witness, and peak-lifetime budget calculation before `RenderGraph` publication.
- [ ] Extend `FrameSourceLayoutV4` with the explicit multi-target binding contract, then rebind its unpublished W4/W5 child constructions to their scope targets. Preserve one material/source permit, material references, draw command order, clip plans, destination versions, and compiler witnesses.
- [ ] Lower the W6a graph into a handle-free `GPUW6aLayerFramePlan`; materialize exact root/layer targets and ordered clear/render/restore/readback operands behind one native draft.
- [ ] Make the candidate gate recognize every layered scene before checking format/effects. In the router, send that owned scene to W6 selection and make every selection result/refusal terminal; only scenes with no layers retain the existing continuation.
- [ ] Run sequentially:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
```

- [ ] Commit: `feat(gpu): execute simple layers from w6a plans`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 3: Bounds, Origins, Transforms, Hints, and Clips

**Agent:** Astra implementation; Sol review.

**Outcome:** Layer bounds and mapping semantics match the approved Skia-aligned rules for W6a, including nonzero origins, transforms, unbounded layers, and hints that are not child clips.

**Files — Create:**

- `TEST/surface/W6aLayerBoundsSurfacePixelTest.kt`

**Files — Modify:**

- `GEOM/RectProjectionF64.kt`
- `MATRIX/LayerMappingF64.kt`
- `PLAN/LayerScopePlanV1.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`

### Contract

- Preserve four separate device-space regions: `knownContent`, `desiredOutput`, `requiredInput`, and `producedOutput`, then derive `compositeDomain`; never collapse them to the hint.
- `saveLayer(bounds=...)` is a hint/filter region, never an implicit clip on children.
- The effective target domain is intersected only with the parent target/explicit composite clip according to restore semantics.
- Target extent is the outward-rounded effective device rect; layer origin is its device-space top-left. All child geometry, clips, copy rectangles, UVs, and restore quads use the sealed `LayerMappingF64`.
- Reflection is valid when finite/invertible; perspective crossing a W=0 horizon is rejected with `w6a.layer.mapping_horizon` before allocation.
- Empty explicit clip elides safely. Empty content alone does not establish elision for later nontrivial restore cases.

### Public RED/GREEN cases

- `translatedFractionalBoundsRoundOutward`
- `explicitClipLimitsRestore`
- `hintDoesNotClipChildren`
- `nonzeroLayerOriginPreservesSampling`
- `reflectedLayerPreservesPixels`
- `horizonCrossingTransformRefusesAndSameSurfaceRecovers`

### Steps

- [ ] Add the six public cases with exact pixels and a same-Surface recovery control; run the causal REDs.
- [ ] Complete checked translation/projection and horizon validation in `:math`.
- [ ] Carry all four regions and the origin/mapping unchanged through semantic plan, physical passes, lowering, uniforms, target descriptor, and restore quad.
- [ ] Remove W6a dependence on renderer `.toInt()`, saturating `RectI32.offset`, scene-sized layer allocation, or scissor-as-bounds reconstruction. Leave legacy code unchanged when still used by non-W6 routes.
- [ ] Run:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
```

- [ ] Commit: `feat(gpu): seal w6a layer bounds and mappings`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 4: Restore Alpha, Color Filter, and W5 Blend Plans

**Agent:** Astra implementation; Sol review.

**Outcome:** Restore consumes group alpha, the W5 color-filter execution plan, and the W5 final blend exactly once, including transparent-black semantics and destination-read modes.

**Files — Create:**

- `PLAN/LayerRestorePlanV1.kt`
- `TEST/surface/W6aLayerRestoreSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/FinalBlendPlan.kt`
- `PLAN/ColorFilterExecutionPlanV1.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/PlanPasses.kt`
- `PLAN/W5bDestinationGraph.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/materials/W5fColorOperationEmitterV1.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`

### Contract

- Restore order is layer result → group alpha → color filter → one final blend into the immediate parent.
- Style, path effect, mask filter, and image filter are not reinterpreted as restore geometry. Image/mask filters remain W6b/W6d refusals.
- Compile restore color filters with `ColorFilterPlanCompilerV1`; emit their existing numeric graph and dynamic bytes. Do not create a layer-specific color-filter table or shader evaluator.
- Select restore blends with `FinalBlendPlanner`; preserve `FixedFunctionV1`, `DestinationReadV1`, and `NoOpV1`. Destination-read snapshots are versioned per parent target and occur immediately before the restore that consumes them.
- Derive `readsPriorDevice` and `restoreAffectsTransparentBlack` in `:gpu-plan` from the selected numeric color operation and blend formula. The renderer receives booleans and plans; it does not keep a second blend-mode classification.
- Alpha is finite and clamped exactly by the existing Paint capture semantics. It is group alpha, never multiplied into each child draw.

### Public RED/GREEN cases

- `restoreAlphaAppliesOnceToOverlappingChildren`
- `restoreColorFilterRunsAfterGroupAlpha`
- `fixedFunctionRestoreBlendTargetsImmediateParent`
- `destinationReadRestoreUsesFreshParentVersion`
- `emptyLayerColorFilterThatCreatesAlphaIsNotElided`
- `transparentSourceClearSrcAndDstInUseTheParentClip`
- `restoreIgnoresGeometryPaintAttributes`

### Steps

- [ ] Add the seven public cases with an independent CPU oracle for group composition and selected W5 blend formulas; run causal REDs.
- [ ] Implement the restore plan and transparent-black facts by reusing W5 numeric/color/blend authorities.
- [ ] Extend frame construction with per-target destination versions/snapshots and restore passes. Include snapshots/uniforms in final lifetimes and peak budget before freeze.
- [ ] Extend W6a lowering/materialization to consume the sealed color graph and `BlendPlan`; no blend or color-filter selection may occur in the renderer.
- [ ] Run:

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest.nestedFiltersPreserveBothOpacityOrdersAndDirectPathSources'
```

- [ ] Commit: `feat(gpu): execute w6a restore effects once`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 5: Nested Scopes, Paint Order, and Frame-Wide Identity

**Agent:** Astra implementation; Sol review.

**Outcome:** Arbitrarily nested admitted W6a scopes restore into their immediate parent in exact paint order, with distinct occurrence IDs, global physical IDs, and correct overlapping lifetimes.

**Files — Create:**

- `TEST/surface/W6aNestedLayerSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/LayerScopePlanV1.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/RenderGraph.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`

### Contract

- Source event order is preserved exactly: parent-before → child-init → child-before → nested-init/render/restore → child-after → child-restore → parent-after.
- Every BeginLayer occurrence receives its own `LayerScopeIdI32`, target resource, lifetime, and destination-version stream even when descriptor canonical IDs are equal.
- A child restores into its parent layer target; only root-scope restores target the scene target.
- A parent may have several render-pass segments around children. The renderer cannot flatten, split, sort by depth, or retarget them after freeze.
- The final graph validates no active render attachment is sampled. Target reuse is permitted only when sealed lifetimes do not overlap and descriptor/usage/format/sample count are identical; initial implementation may conservatively allocate distinct targets.
- Nesting depth and command limits use the captured `GraphLimits`; refusal occurs during selection, before resources or a native draft.

### Public RED/GREEN cases

- `nestedRestoreKeepsParentPaintOrder`
- `siblingsWithEqualDescriptorsRemainIndependent`
- `nestedDestinationReadBlendReadsItsImmediateParent`
- `parentDrawAfterChildIsNotMovedBeforeRestore`
- `restoreToCountClosesLayersInOrder`
- `depthLimitRefusesBeforeReadbackAndSameSurfaceRecovers`

### Steps

- [ ] Add the six public cases and run causal REDs against the current nesting refusal/incorrect order.
- [ ] Extend scope construction, execution steps, per-target destination versions, lifetimes, and ID allocation to the complete nested stack.
- [ ] Remove any W6a lowering dependency on `sortedByDescending(depth)`, `splitCompositeChildrenRenders`, `mergeCompositeCommands`, or scene-sized byte rewrites.
- [ ] Materialize parent/child target views and ordered render/copy/restore operands from exact graph IDs. Retain every target/view/lease until frame completion or quarantine.
- [ ] Run:

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'
```

- [ ] Commit: `feat(gpu): execute nested w6a layer scopes`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 6: `initWithPrevious` at the Logical Save Point

**Agent:** Astra implementation; Sol review.

**Outcome:** `initWithPrevious=true` copies the correct parent target/version at save time, before child draws, for root and nested scopes; false remains transparent black.

**Files — Create:**

- `TEST/surface/W6aInitWithPreviousSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/LayerScopePlanV1.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/PlanPasses.kt`
- `PLAN/PlanResources.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `GPU/execution/GPUWgpu4kFrameEncodingBackend.kt`
- `TEST/picture/W6aLayerPictureTest.kt`

### Contract

- Exclusive initialization order for W6a is `initWithPrevious` copy or transparent clear. Backdrop is still refused and will supersede previous only in W6d.
- Previous copy reads the immediate parent target/version at the BeginLayer event, after earlier parent draws and before any child draw. It never samples an active attachment; end the parent render segment, issue a copy, then begin/load the child segment.
- Source rectangle is sealed in immediate-parent target texels after checked subtraction of the parent device origin; destination origin is sealed in layer-target texels and carried by `TextureCopy.destinationOriginI32`. Nested previous copies never reuse root/device coordinates.
- Parent uses `CopySource`; layer target uses `CopyDestination`, `RenderAttachment`, and `Sampled`. Copy and target bytes/usages/lifetimes are budgeted before publication.
- `initWithPrevious` alone may use an identity passthrough proof, but `initWithPrevious + alpha<1`, color filter, or nontrivial blender sets `readsPriorDevice` and uses the full required parent domain; a restrictive hint cannot discard pixels outside itself.
- Empty previous layer preserves the parent for trivial restore; it is not dropped before this fact is proven.

### Public RED/GREEN cases

- `defaultFalseStartsTransparent`
- `copiesParentAtSaveTime`
- `nestedPreviousReadsItsParentLayer`
- `emptyPreviousLayerPreservesParent`
- `previousPlusAlphaIgnoresRestrictiveHint`
- `previousPlusColorFilterUsesFullDesiredOutput`
- `previousPlusDestinationReadBlendUsesFreshParentVersion`

Extend Picture evidence with:

- `memoryAndWireReplayPreservePreviousPixels`
- `drawPictureInsidePreviousLayerPreservesHostClip`
- `translatedPictureLayerKeepsNonzeroOrigin`

### Steps

- [ ] Add the seven Surface cases and three Picture cases; require `assertNotNull(Picture.fromByteArray(...))` before wire replay. Run causal REDs.
- [ ] Emit `LayerInitializationPlanV1.PreviousCopy` at BeginLayer with sealed source version/bounds/destination origin and update final resource usages/lifetimes/budget.
- [ ] Lower the exact copy to `GPUPreparedNativeScopeOperand.Copy`/`GPUWgpu4kFrameEncodingBackend.encodeCopy`; do not use the immediate-submit legacy helper.
- [ ] Ensure the following child render starts with Load after the copy and that root/parent render segments resume with Load after child restore.
- [ ] Run:

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aInitWithPreviousSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest'
```

- [ ] Commit: `feat(gpu): initialize w6a layers from parent content`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 7: W4/W5 Child Coverage, Atomic Budget, Terminal Routing, and Recovery

**Agent:** Astra implementation; Sol review.

**Outcome:** W6a accepts the applicable W4/W5 child lanes under the same authorities, budgets the whole frame at B/B−1, refuses unsupported layers before public output, and recovers on the same Surface.

**Files — Create:**

- `TEST/surface/W6aLayerBudgetRecoverySurfacePixelTest.kt`
- `TEST/surface/W6aLayerW4W5SurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/W6aPlanDiagnostics.kt`
- `PLAN/CapabilityCompilerChain.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `PLAN/MaterialSourceConstructionV4.kt`
- `PLAN/MaterialSourceFootprintV4.kt`
- `PLAN/RawMaterialRequirementsV2.kt`
- `GPU/planning/GpuRenderContext.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/execution/GPUFramePreflighter.kt`
- `GPU/execution/GPURuntimeResourceAdapter.kt`
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- `API/surface/gpu/GPUPlanSurfaceRouter.kt`

### Contract

- Applicable children include the already promoted Rect/RRect/Path fill/stroke/hairline/point(s)/Vertices/Mesh/direct-image/image-shader lanes and W5 solid, opacity, gradients, decoded images, color filters, composed procedural material, and registered runtime effect. Preserve each lane's existing physical capability gaps.
- Frame construction interns the existing material table and inventories every owner once. It does not create a layer-specific material compiler, repack dynamic data after freeze, or re-charge shared owners per scope.
- Budget includes root target/readback, every live layer target, previous copies, destination-read snapshots, W4/W5 buffers/textures/uniforms, alignment/reservation, and leases. B succeeds and B−1 refuses with `w6a.layer.frame_budget_exceeded` before native allocation.
- Cache warmth never reduces admission budget. A warm replay has the same pessimistic B/B−1 result.
- Backdrop/image/mask/spatial filters and F16 refuse with stable W6 diagnostics. After W6a ownership, `GapNotMigrated`, capture-limit errors, budget refusal, or late material refusal are terminal and cannot continue into `GPUPreparedSurfaceProductEntry`.
- On failure, public readback sentinel bytes remain unchanged. After balanced Canvas state and `discardRecordedOperations()`, a valid scene on the same Surface renders exact pixels with no sibling from the refused frame.

### Public cases

`W6aLayerBudgetRecoverySurfacePixelTest`:

- `exactBudgetBoundaryAcceptsBAndRefusesBMinusOne`
- `nestedLiveTargetsRemainBudgeted`
- `successiveSiblingTargetsChargeDistinctPreparedAllocations`
- `previousCopyAndDestinationSnapshotAreBudgeted`
- `warmReplayKeepsPessimisticBudget`
- `lateSiblingRefusalLeavesReadbackSentinelAndSameSurfaceRecovers`

`W6aLayerW4W5SurfacePixelTest` is a small discriminating covering, not a Cartesian product. It contains one positive layer case for each applicable geometry lane and one each for gradient, image shader/direct image, color filter, Noise, composed Blend, and registered runtime material, plus a nested mixed-lane case.

### Steps

- [ ] Add both public shards and run the new methods against current production. Record which are RED for routing, accounting, or material ownership; preserve direct no-layer controls.
- [ ] Close compiler-chain construction for every applicable child lane without publishing child graphs early. Preserve exact W4/W5 diagnostics for physical gaps.
- [ ] Complete frame-wide material/source inventory, semantic lifetimes, explicit physical allocation slots, and checked physical peak. Distinct sibling targets prepared in the same draft are summed through completion even when their render passes do not overlap. Derive public B from the frozen documented formula/fixture dimensions, not by calling the planner in the test.
- [ ] Seal terminal ownership in gate/router. Verify every post-selection failure discards the whole draft/ready token and does not expose partial readback.
- [ ] Run:

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hConvergenceSurfacePixelTest.lateSiblingRefusalDoesNotPublishAndSameSurfaceRecovers'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hConvergenceSurfacePixelTest.independentEqualRuntimeOwnersKeepTheirImageBudgets'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest.materialRefusalThenSameSurfaceRecovers'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hImageOriginSurfacePixelTest.rgbaHalfAlphaPublicControl'
```

- [ ] Commit: `feat(gpu): close w6a layered frame ownership`
- [ ] Request Sol review; permit one bounded correction and one scoped re-review.

---

## Task 8: W6a Convergence, Durable Status, Independent Review, and Stacked Draft PR

**Agent:** Astra for convergence triage; Terra for documentation; Sol review only.

**Outcome:** All W6a public shards and targeted W4/W5 preservation are qualified on immutable source, limitations are explicit, and one Draft PR is stacked directly on W5h.

**Files — Create:**

- `refactor/waves/W06-layers-effects/status.md`

**Files — Modify:**

- `refactor/README.md`
- `refactor/plans/2026-09-16-w6a-layer-authority-implementation-plan.md`

### Steps

- [ ] Run every W6a shard sequentially and record exact method/PASS/failure/error/skip counts, Gradle exit, XML custody, source commit/hash, and native exit separately:

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aInitWithPreviousSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'
```

- [ ] Run the targeted W4/W5 preservation selectors named in Tasks 4 and 7. Do not run GMs, the global Skia integration suite, or multi-hour `jpg-color-cube`.
- [ ] Run all touched-module compilations separately and sequentially:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
```

- [ ] Search the W6a production path for forbidden late authorities: `GPULayerSaveRecord`, `GPUPreparedCompositeLowerer`, `splitCompositeChildrenRenders`, `mergeCompositeCommands`, `copyTargetToOffscreenTexture`, renderer-local `.toInt()` bounds, and post-freeze resource/pass creation. Classify every legitimate legacy/W8 reference; do not add a static test for this audit.
- [ ] Update `status.md` with achieved W6a cells, exact exclusions (backdrop/spatial filters/F16/device-loss visibility), retained legacy/W8 paths, budgets, native status, and no ISO/global claim.
- [ ] Update `refactor/README.md` with links to this plan/status and a concise W6a checkpoint. Do not preserve transient agent reports.
- [ ] Commit: `docs(refactor): record w6a layer authority status`
- [ ] Request one independent whole-branch Sol review against `8bdc730c9..HEAD`, covering spec compliance, architecture, numeric bounds, lifetimes/budgets, native ordering, public evidence, and legacy fallback risk.
- [ ] If the review reports Critical/Important findings, run one bounded correction wave with an adapted non-Sol agent, rerun only causally affected public shards plus targeted preservation, commit once, and request one final scoped Sol re-review. Do not iterate indefinitely.
- [ ] Verify clean worktree and stack base; push `codex/w6a-layer-authority`; create or update one Draft PR targeting `codex/w5h-registered-runtime-effects`. Include scope, architecture, Task 1–8 commits, exact gates/results, exclusions, native `UNKNOWN` where applicable, and the W6b next step. Do not merge.

---

## W6a Definition of Done

- [ ] Public `SaveLayerRec.initWithPrevious` defaults to false and is immutable through Scene/Picture capture.
- [ ] Picture 13/schema 7 round-trips the flag; Picture 8–12 remain readable with false.
- [ ] Simple, bounded, unbounded, transformed, clipped, and nested layers render exact public pixels.
- [ ] Group alpha, color filter, fixed-function blend, and destination-read blend execute once at restore into the immediate parent.
- [ ] `initWithPrevious` copies the immediate parent/version at the logical save point and survives memory/wire Picture replay.
- [ ] Scope occurrence IDs, physical resource/pass IDs, target origins, mappings, destination versions, lifetimes, and budgets are frozen frame-wide in `:gpu-plan`.
- [ ] `:gpu-renderer` performs no specialization, bounds reconstruction, target resize, pass insertion, ID remap, or budget rewrite after freeze.
- [ ] A W6a-owned failure is terminal, exposes no partial readback, and the same Surface recovers after public discard/re-record.
- [ ] Applicable W4/W5 child lanes retain their existing authorities and targeted public preservation.
- [ ] Backdrop, image/mask/spatial filters, F16, fonts, codecs, GMs, and device-loss proof remain explicit exclusions, not silent approximations.
- [ ] Whole-branch Sol review has no open Critical/Important finding.
- [ ] W6a has one open Draft PR stacked directly on W5h, with no merge and no ISO/global completion claim.

## Execution Handoff

The plan is ready for `superpowers:subagent-driven-development`, task by task, with Astra on architectural/numeric implementation and Sol only on reviews. Do not start W6b until Task 8 closes W6a or records a concrete blocker.

## Task 8 durable checkpoint — 2026-09-22

- [x] Les huit shards publics W6a, les selectors ciblés W4/W5 et les sept
  compilations séparées ont été exécutés séquentiellement. La custody complète
  (méthodes XML, PASS/failure/error/skip, exits Gradle et natifs) est dans
  `refactor/waves/W06-layers-effects/status.md`.
- [x] Une correction bornée de `FrameSourceLayoutV4` route les dépassements de
  budget agrégé sous `layeredInput` vers
  `w6a.layer.frame_budget_exceeded`; les deux REDs publics de convergence sont
  repassés GREEN. Les routes W4/W5 non-layer restent inchangées et leur
  préservation ciblée est consignée.
- [x] L'audit de source classe les chemins prepared historiques comme
  legacy/W8, hors route W6a; les ressources/passes W6a restent planifiées et
  gelées dans `:gpu-plan` avant leur matérialisation native.
- [x] Budgets : root/readback, targets de layers, previous copies, snapshots
  destination, owners W4/W5, alignement/réservation et leases restent dans le
  peak frame-wide; B/B−1 et recovery sont couverts publiquement.
- [x] Exclusions explicites : backdrop, filtres image/mask/spatial, F16/HDR,
  device-loss/visibilité native, fonts, codecs, GMs et gates globales. Aucun
  GM/global Skia/`jpg-color-cube` n'a été lancé.
- [x] La revue whole-branch Sol est clean. La correction finale
  `4509aa9c6` préserve le diagnostic owner W5g sous warm replay; sa re-review
  scoped est clean et les huit shards combinés sont 91/91 JUnit PASS.
- [x] La branche est poussée et la Draft PR
  [#2403](https://github.com/ygdrasil-io/kanvas/pull/2403) est empilée
  directement sur `codex/w5h-registered-runtime-effects`. Les exits natifs
  133 restent `UNKNOWN`, jamais GREEN; aucun claim ISO ni global n'est fait.
