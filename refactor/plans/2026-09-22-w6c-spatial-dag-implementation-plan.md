# W6c Spatial DAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the nine W6c spatial-DAG image-filter families through the existing W6 plan-first layer authority, including contextual source binding, ordered multi-input execution, pessimistic spatial caching, and public Surface/Picture recovery proof.

**Architecture:** W6b's immutable captured filter table remains the only semantic DAG owner. W6c binds a captured node to a source occurrence, F64 mapping, and I32 desired output in `:gpu-plan`; it freezes typed payloads on the one W6 `FilterPass`/`FilterTarget` physical graph, then `:gpu-renderer` materializes those exact operands without replanning. Existing W5f color numeric graphs, W5 blend plans, W6a layer scopes, source inventory, budget, cache-generation, and lease lifecycles are reused rather than forked.

**Tech Stack:** Kotlin/JVM; `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; JUnit 5; public `Surface`, `Canvas`, `Picture`, `ImageFilter`, and `Paint` APIs.

**Spec:** `refactor/specs/2026-09-16-w6-layers-effects-design.md` §§5–6, 8–9, 10.3, 11–16; `refactor/specs/2026-09-22-w6b-w6e-stacked-delivery-design.md` §§3, 5, 8–10; `refactor/plans/2026-09-16-w6a-layer-authority-implementation-plan.md`.

## Global Constraints

- Base branch: the reviewed delivery HEAD of `codex/w6b-blur-masks-shadows`; implementation branch: `codex/w6c-spatial-dag`; the W6c Draft PR targets `codex/w6b-blur-masks-shadows`, never W6a, W5h, or `main`.
- The reviewed W6b base is `b6412bc161ccb99f1361886ec80a9d96c0f3637f`. It supplies the single captured table, `PlanPass.FilterPass`, `FilterTarget`, and occurrence authority under their actual W6b names. Record and recheck this commit before Task 1 code changes.
- Read both approved specs and the W6a plan before every task. A mismatch with W6b reality stops that task and is reported as a base-contract gap; it never authorizes a second scene graph, allocator, submit path, cache, or planner.
- Use vertical RED → GREEN → refactor work. Each RED is a behavioral public `Surface` or `Picture` failure on unchanged production, never a compilation, fixture, capability-injection, or test-harness failure.
- Tests use only public `Surface`, `Canvas`, `Picture`, `ImageFilter`, `Paint`, public bytes, public diagnostics, public render/readback scopes, discard/re-record, and same-Surface recovery. No private tests, reflection, mocks, fake devices/backends, counters, static-source assertions, or test infrastructure.
- Compute expected bytes independently before creating a `Surface`/`PictureRecorder`. Crop, offset, tile, identity, and composition proofs are byte-exact. Do not add or widen a global tolerance.
- `:math` exclusively owns new rectangles, points, sizes, and mappings. Bounds are F64 until checked outward I32 texel projection; reject NaN, infinity, horizon crossings, invalid/inverted domains, and overflow. `:kanvas` and `:gpu-renderer` add no private geometry type or bounds reconstruction.
- `FilterPass` and `FilterTarget` are the single W6 physical spatial-pass/resource contract in `:gpu-plan`. W6c only adds typed payloads and resource requests to it; it creates no parallel RenderGraph, target pool, allocator, cache, or submit.
- The occurrence key is complete: `(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)`. Cache eligibility additionally requires exact known/required/produced bounds, format/color-space/sample count, all input identity+generation+subset facts, capability/backend generation, and semantic version. Canonical equality alone never aliases work.
- A cache hit remains pessimistically budgeted and keeps every reused resource leased through completion or safe quarantine. Cache warmth never changes B/B−1 admission, diagnostics, output visibility, or failure atomicity.
- `Compose` binds `inner` to the current source, then binds `outer` to the inner result. `Merge` and `Blend` preserve public input order, including duplicated or value-equal inputs. Null public input means W6b's explicit `ImplicitSource`, not a globally resolved source.
- `ColorFilter` compiles only through W5f `ColorFilterPlanCompilerV1` and emits its sealed numeric graph/uniform placement. `Blend` uses W5 `FinalBlendPlanner`; W6c introduces neither a color-filter table nor a blend evaluator.
- The renderer materializes the frozen `RenderGraph`; it cannot select an implementation kind, recompute bounds/mappings, allocate/retarget a filter resource, insert/sort a pass, rewrite a budget, or choose legacy fallback after W6 admission. Any unavailable specialization is selected as W6 generic or precisely refused before freeze.
- RGBA8 is the only positive target. Backdrop, filtered `initWithPrevious`, Picture/runtime image filters, displacement, convolution, magnifier, lighting, F16/HDR, fonts, codecs, GMs, dashboard/renders/references/baselines/scores/rebaseline, global Skia, and `jpg-color-cube` remain excluded.
- Serialize every Gradle invocation and run shell commands from `/Users/chaos/.codex/worktrees/cbf6/kanvas` through `rtk` or `rtk proxy`. Native exits 133 and 134 remain `UNKNOWN`; custody records Gradle exit, XML methods, native exit, and source hash separately.
- W6-owned refusal is terminal and atomic: no partial readback or healthy sibling is public, owner diagnostics survive, and public discard/re-record recovers on the same `Surface`. Preserve W4/W5 source/material authority, W6a layer ordering, destination versions, budgets, and leases.
- Terra implements every W6c task; Sol is review-only. Every task gets one implementation commit and Sol review; the PR gets one whole-branch Sol review, at most one bounded non-Sol correction wave, then one scoped Sol re-review.

## Review Focus

- **Contextual implicit source:** `Compose(outer, inner)` must feed `outer` the inner result rather than the layer source; Task 3 test `composeBindsInnerThenOuterAcrossSurfaceAndPicture` pins it.
- **Ordered repeated inputs:** `Merge`/`Blend` must preserve order and must not collapse equal-looking or duplicated public inputs; Task 4 tests `mergePreservesDuplicateInputOrder` and `blendPreservesBackgroundForegroundOrder` pin it.
- **Fractional nonzero mapping:** crop/offset/tile bounds must round outward once in `:math`, retain a nonzero target origin, and never be re-rounded by the renderer; Task 2 test `fractionalCropOffsetTileKeepsOriginAndClip` pins it.
- **Warm cache admission:** a hit must retain the same B/B−1 result and exact pixels as a cold run; Task 6 test `warmSpatialReplayKeepsPessimisticBudgetBoundary` pins it.
- **Late spatial failure:** a later refused sibling must expose no partial pixels, preserve its public diagnostic, and allow discard/re-record on that `Surface`; Task 6 test `lateSpatialRefusalLeavesSentinelAndSameSurfaceRecovers` pins it.

---

## Actual Code Map and Intended Ownership

```text
GEOM = math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/
MATRIX = math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/
IR = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
```

| Existing/base owner | W6c responsibility |
| --- | --- |
| `API/paint/ImageFilter.kt`, `API/canvas/DisplayOpSnapshot.kt`, `API/render/ir/PaintSceneAdapter.kt` | Keep public constructors and deep capture. Consume W6b's node table/references; never resolve absent input during capture. |
| `IR/EffectNode.kt`, `SceneArchiveCodec.kt`, W6b `CapturedFilterTableV1.kt` | Retain the W6b table/root/wire identity. W6c only recognizes its nine existing node variants and preserves mutation/replay semantics. |
| `GEOM/RectProjectionF64.kt`, `MATRIX/LayerMappingF64.kt` | Add checked F64 crop/offset/tile/morphology bound transforms and target-relative mapping facts. |
| `PLAN/LayerScopePlanV1.kt`, W6b `W6bFilterPlanV1.kt`, `W6aLayerGraphConstruction.kt` | Bind roots at the layer restore/auto-layer seam, calculate four regions, create occurrences, then freeze one physical graph. |
| `PLAN/PlanPasses.kt`, `PlanResources.kt`, `RenderGraph.kt`, W6b `FilterPassOperationV1` | Extend the one typed `FilterPass` operation family and existing `FilterTarget` resources; validate IDs, dependency order, format/usages/lifetimes before publication. |
| `PLAN/ColorFilterPlanCompilerV1.kt`, `ColorFilterExecutionPlanV1.kt`, `FinalBlendPlan.kt` | Remain exclusive W5 authorities for W6c ColorFilter and Blend numerical programs. |
| `PLAN/PlanPhysicalLayoutV1.kt`, `FrameSourceLayoutV4.kt`, W6a `W6aLayerPlanBudget.kt` | Include spatial targets/uniforms/samplers in the same frame inventory and pessimistic physical allocation/lease budget. |
| `GPU/planning/W6aLayerGraphLowerer.kt`, W6b spatial lowerer/frame-plan files | Lower frozen typed passes to one handle-free W6 frame plan; add no W6c semantic choice. |
| `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, W6b spatial materializer/cache bridge, `GPUFramePreflighter.kt` | Reserve/materialize exact resources, bind exact programs/uniforms, acquire/release leases through existing draft/ready/submit/completion/quarantine lifecycle. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `GPUPlanSurfaceRouter.kt` | Keep W6 terminal ownership for admitted spatial layers and auto-layers; no prepared/legacy continuation. |

Legacy `GPU/layers/*`, `GPUPreparedCompositeLowerer.kt`, `GPUPreparedSurface*`, direct offscreen helpers, renderer-local `GPUImageFilterPlan`, `GPUMorphology`, and `GPUFilterTile` are not W6c authorities. They can serve pre-W6 routes through W8 only; W6c may reuse a kernel implementation only behind a frozen W6 `FilterPass` payload.

## Frozen W6c Interfaces

W6b must provide these semantic owners before Task 1. The reviewed implementation names are `CapturedFilterTableV1.nodeAt`, `PlanPass.FilterPass`, `PlanResourceRole.FilterTarget`, `FilterEvaluationKeyV1`, and `W6bFilterGraphConstruction`. The illustrative API below predates W6b's final naming; W6c adapts to the reviewed owners and never duplicates their contract:

```kotlin
@JvmInline
public value class CapturedFilterNodeIdI32(public val valueI32: Int)

public sealed interface CapturedFilterInputV1 {
    public data object ImplicitSource : CapturedFilterInputV1
    public data object TransparentBlack : CapturedFilterInputV1
    public class Node(public val id: CapturedFilterNodeIdI32) : CapturedFilterInputV1
    public class Picture(public val id: CapturedPictureIdI32) : CapturedFilterInputV1
    public class Backdrop(public val id: CapturedBackdropIdI32) : CapturedFilterInputV1
}

public class CapturedFilterTableV1 internal constructor(
    nodes: List<CapturedFilterNodeV1>,
) {
    public fun nodes(): List<CapturedFilterNodeV1>
    public fun nodeOrNull(id: CapturedFilterNodeIdI32): CapturedFilterNodeV1?
    public fun validate(graphLimits: GraphLimits): SceneSemanticValidationResult
}
```

Paint/layer descriptors retain their W6b `CapturedFilterInputV1?` roots into that table. W6c extends the one W6b spatial plan owner with additional typed operations. It uses W6b's `FilterEvaluationKeyV1(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)` and `FilterBoundsPlanV1(knownContent, desiredOutput, requiredInput, producedOutput, targetOrigin)` unchanged. Every mutable `RectF64`, `RectI32`, and list is defensively copied.

```kotlin
public enum class FilterImplementationKindV1 {
    IMAGE_BLUR_X,
    IMAGE_BLUR_Y,
    MASK_COVERAGE_BLUR_X,
    MASK_COVERAGE_BLUR_Y,
    MASK_BLUR_STYLE,
    MASK_SHADER,
    MASK_TABLE,
    DROP_SHADOW_COLORIZE,
    DROP_SHADOW_COMPOSITE,
    CROP_SAMPLE,
    OFFSET_SAMPLE,
    TILE_SAMPLE,
    COLOR_FILTER_APPLY,
    MERGE_COMPOSITE,
    BLEND_COMPOSITE,
    MORPHOLOGY_X,
    MORPHOLOGY_Y,
}

public sealed interface FilterPassOperationV1 {
    public val kind: FilterImplementationKindV1
    public val bounds: FilterBoundsPlanV1
    public data class SeparableBlur(override val kind: FilterImplementationKindV1, public val sigmaF32: Float, public val axis: FilterAxisV1, public val tileMode: TileMode, override val bounds: FilterBoundsPlanV1) : FilterPassOperationV1
    public data class MaskBlurStyle(public val style: BlurStyle, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_BLUR_STYLE) : FilterPassOperationV1
    public data class MaskShader(public val material: MaterialPlanRef, public val uniformOffsetI64: Long, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_SHADER) : FilterPassOperationV1
    public class MaskTable(table: ImmutableUBytes, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_TABLE) : FilterPassOperationV1 {
        private val tableSnapshot = ImmutableUBytes.copyOf(table.copyToUByteArray())
        public fun copyTable(): ImmutableUBytes = ImmutableUBytes.copyOf(tableSnapshot.copyToUByteArray())
    }
    public class DropShadowColorize(public val color: ColorARGB, offsetF64: Vector2F64, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COLORIZE) : FilterPassOperationV1 {
        private val offsetSnapshotF64 = Vector2F64(offsetF64.x, offsetF64.y)
        public fun copyOffsetF64(): Vector2F64 = Vector2F64(offsetSnapshotF64.x, offsetSnapshotF64.y)
    }
    public data class DropShadowComposite(public val mode: CapturedDropShadowModeV1, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COMPOSITE) : FilterPassOperationV1
    public class Crop(cropDeviceF64: RectF64, public val tileMode: TileMode, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class Offset(public val dxF64: Double, public val dyF64: Double, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class Tile(sourceDeviceF64: RectF64, destinationDeviceF64: RectF64, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class ColorFilter(public val execution: ColorFilterExecutionPlanV1, public val uniformOffsetI64: Long, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class Merge(public val inputCountI32: Int, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class Blend(public val blend: BlendPlan, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1
    public class Morphology(public val morphologyKind: Kind, public val radiusXF64: Double, public val radiusYF64: Double, override val bounds: FilterBoundsPlanV1, override val kind: FilterImplementationKindV1) : FilterPassOperationV1 {
        public enum class Kind { DILATE, ERODE }
    }
}

public class FilterPass(
    override val ordinal: Int,
    inputs: List<PlanResourceId>,
    public val output: PlanResourceId,
    public val evaluationKey: FilterEvaluationKeyV1,
    public val operation: FilterPassOperationV1,
) : PlanPass {
    public fun inputs(): List<PlanResourceId>
}
```

`PlanResourceRole.FilterTarget` is added exactly once by W6b and remains the role for W6c results, ping-pong targets and cacheable filter output. Its resources use RGBA8, single sample, `RenderAttachment + Sampled + CopySource` (and `CopyDestination` only when an exact frozen pass needs it). `FilterPass.inputs()` is ordered and preserves duplicates. `RenderGraph` remains the sole physical resource/pass DAG; its validator proves every input’s producing pass precedes the consumer and forbids sampling an active attachment.

The cache key is a frozen planner value, not a renderer string:

```kotlin
public class SpatialFilterCacheKeyV1 internal constructor(
    public val evaluationKey: FilterEvaluationKeyV1,
    public val bounds: FilterBoundsPlanV1,
    public val format: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val capabilityGenerationI64: Long,
    public val backendGenerationI64: Long,
    inputGenerations: List<SpatialFilterInputGenerationV1>,
) {
    public fun inputGenerations(): List<SpatialFilterInputGenerationV1>
}
```

It enters the existing `PlanPhysicalLayoutV1`/renderer cache binding and exact lease machinery; it does not use canonical object identity or bypass `W6aLayerPlanBudget`.

### Task 1: Verify the W6b Foundation and Admit W6c Roots

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `TEST/surface/W6cSpatialDagAdmissionSurfaceTest.kt`
- Modify: `PLAN/W6aLayerPlanCompiler.kt`, `PLAN/CapabilityCompilerChain.kt`, W6b `PLAN/W6bFilterGraphConstruction.kt`, `PLAN/W6bFilterPlanV1.kt`, the existing W6b graph validation/lowerer/materializer, and `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt` / `GPUPlanSurfaceRouter.kt` only if the current ownership route requires it.

**Consumes:** W6b `CapturedFilterTableV1`, `CapturedFilterInputV1`, one extensible `PlanPass.FilterPass`, `PlanResourceRole.FilterTarget`, and W6a terminal layer routing.

**Produces:** W6c root recognition on W6b's single graph owner, with a minimal frozen and natively materialized `Crop` pass sufficient for the public 1×1 positive witness. `Offset`, `Tile`, `ColorFilter`, `Compose`, `Merge`, `Blend`, `Dilate`, and `Erode` remain W6-owned terminal refusals until their later tasks activate their frozen passes. W6d-only nodes remain exact pre-publication refusals.

- [ ] **Step 1: Record the reviewed W6b base contract** — run `rtk git rev-parse codex/w6b-blur-masks-shadows`, inspect the W6b DAG/pass classes, and add the exact hash plus their actual names to this task’s commit message body; stop if table roots, typed inputs, one `FilterPass`, or `FilterTarget` are absent.
- [ ] **Step 2: Write public failing admission/recovery tests** in `W6cSpatialDagAdmissionSurfaceTest.kt` using explicit `Surface.render()` and a sentinel `readPixels` buffer:

```kotlin
@Test fun `crop is W6-owned and unsupported W6d sibling stays terminal`() {
    val before = UByteArray(4) { 0x5au }
    val surface = Surface(1, 1)
    surface.canvas {
        saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(RectF32.ofLTRB(0f, 0f, 1f, 1f)))))
        drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false)); restore()
    }
    assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
}
```

- [ ] **Step 3: Run the new selector before production edits** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialDagAdmissionSurfaceTest'`; record a behavioral W6b refusal or wrong route and retain a no-filter W6a GREEN control.
- [ ] **Step 4: Implement root recognition and the minimal Crop vertical slice** in the existing W6b graph: admit the 1×1 RGBA8 full-domain Crop witness through a frozen `PlanPass.FilterPass`/`FilterTarget`, materialize only its sealed sampling operands in the W6 renderer, and keep the other W6c variants terminal until Tasks 2–5. Preserve W6a’s ownership-first result and W6d diagnostics. Do not introduce a second planner, pass graph, allocation path, or legacy fallback.
- [ ] **Step 5: Refactor selection names only after GREEN** by extracting the W6c variant predicate from `semanticRefusalFor`; retain exactly one candidate/router outcome for a layered scene.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialDagAdmissionSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
```

- [ ] **Step 7: Commit and review** with `git add gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cSpatialDagAdmissionSurfaceTest.kt refactor/plans/2026-09-22-w6c-spatial-dag-implementation-plan.md && git commit -m 'feat(gpu): admit w6c crop root'`; request one Sol review, apply at most one bounded correction, then request scoped Sol re-review.

### Task 2: Crop, Offset, Tile, and F64 Bounds

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `PLAN/W6cSpatialBoundsPlanner.kt`, `GPU/filters/GPUW6cSpatialSamplingPass.kt`, `TEST/surface/W6cSpatialBoundsSurfaceTest.kt`
- Modify: `GEOM/RectProjectionF64.kt`, `MATRIX/LayerMappingF64.kt`, W6b `PLAN/W6bFilterGraphConstruction.kt`, `PLAN/W6bFilterPlanV1.kt`, `PLAN/W6bFilterGraphWitnessV1.kt`, `PLAN/W6aLayerGraphConstruction.kt`, and the W6b materializer. Consume the existing generic `PlanPass.FilterPass` and `FilterTarget` resource contracts without cosmetic edits to `PlanPasses.kt` or `PlanResources.kt`; change those files only if a concrete validator/resource gap is demonstrated.

**Consumes:** W6b `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, W6a four regions/mapping, `roundOutToRectI32OrNull`, and frozen W6b filter target allocation.

**Produces:** `requiredInputBounds(operation, desiredOutput, mapping): RectI32?` and `producedOutputBounds(operation, knownContent, mapping): RectI32?` in `:gpu-plan`; frozen `Crop`/`Offset`/`Tile` `FilterPass` payloads whose resource-relative rectangles are final.

**Public cases:** `cropClipsAndUsesTransparentBlackOutsideSource`, `offsetMovesCapturedLayerSourceExactlyOnce`, `tileRepeatsOnlyInsideDestination`, `fractionalCropOffsetTileKeepsOriginAndClip`.

- [ ] **Step 1: Write failing public exact-byte tests** for crop clipping/transparent out-of-source, integer offset, repeated tile, and review-focus fractional origin/clip:

```kotlin
@Test fun `offset moves the captured layer source exactly once`() {
    val surface = Surface(3, 1)
    surface.canvas {
        saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Offset(1f, 0f))))
        drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false)); restore()
    }
    assertContentEquals(ubyteArrayOf(0u,0u,0u,0u, 0u,0u,255u,255u, 0u,0u,0u,0u), surface.render().pixels)
}
```

- [ ] **Step 2: Run RED** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialBoundsSurfaceTest'`; record the current W6c refusal, not an absent test symbol.
- [ ] **Step 3: Implement F64 bounds propagation** in `W6cSpatialBoundsPlanner`: Crop intersects according to its own tile semantics; Offset translates both required input backwards and produced output forwards with checked F64 values; Tile maps `dst` demand to `src`, producing only within `dst` and transparent black outside it.
- [ ] **Step 4: Freeze typed sampling passes** with outward-rounded target-relative I32 rectangles, sealed samplers and the exact W6c `FilterImplementationKindV1` arm selected before graph freeze; renderer code receives no device rect, mapping, or target-sizing decision.
- [ ] **Step 5: Refactor repeated bound conversion** into one `:math` helper whose input/output types stay `RectF64`/`RectI32`; remove any W6c renderer `.toInt()`, saturating offset, or scissor-as-bounds conversion.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialBoundsSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'
```

- [ ] **Step 7: Commit and review** with `git add math gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cSpatialBoundsSurfaceTest.kt && git commit -m 'feat(gpu): plan w6c crop offset and tile'`; request Sol review and one bounded correction/re-review at most.

### Task 3: ColorFilter and Contextual Compose

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `PLAN/W6cComposePlanner.kt`, `TEST/surface/W6cComposeSurfaceTest.kt`, `TEST/picture/W6cSpatialDagPictureTest.kt`
- Modify: W6b `PLAN/SpatialFilterDagPlanV1.kt`, `PLAN/ColorFilterExecutionPlanV1.kt`, `PLAN/FrameSourceLayoutV4.kt`, `PLAN/PlanPasses.kt`, W6b lowerer/materializer, `API/picture/Picture.kt` only if W6b’s stable table reader needs an additive W6c root dispatch.

**Consumes:** W6b `ImplicitSource` references, `ColorFilterPlanCompilerV1`, `FrameSourceLayoutV4` one source/material/uniform inventory, and Task 2 occurrence/bounds outputs.

**Produces:** `bindInput(input: CapturedFilterInputV1, currentSourceId: PlanResourceId): PlanResourceId`; `Compose` schedules `inner` then `outer`; `ColorFilter` operation retains W5f execution/uniform offset exactly once.

**Public cases:** `composeBindsInnerThenOuterAcrossSurfaceAndPicture`, `imageColorFilterUsesW5fNumericGraphOnce`, `postCaptureMutationDoesNotChangeComposePixels`, `memoryAndWireReplayPreserveComposePixels`.

- [ ] **Step 1: Write failing public tests**, including the review-focus source-binding witness and mutation/replay witness:

```kotlin
val inner = ImageFilter.Offset(1f, 0f)
val filter = ImageFilter.Compose(ImageFilter.ColorFilter(ColorFilter.Luma), inner)
surface.canvas { saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter))); drawW5Rect(ColorARGB.Red); restore() }
// Expected is the luma of the shifted red result, not luma of the original layer source.
assertContentEquals(expectedComposeBytes, surface.render().pixels)
```

- [ ] **Step 2: Run RED** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'` and `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6cSpatialDagPictureTest'`; record separate Surface and Picture causal failures.
- [ ] **Step 3: Implement contextual binding**: null public filter input is `ImplicitSource`; for Compose evaluate `inner` with `currentSourceId`, then evaluate `outer` with the inner result `PlanResourceId`; each distinct `(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)` creates its own `FilterEvaluationKeyV1` and lifetime.
- [ ] **Step 4: Implement W5f reuse** by calling `ColorFilterPlanCompilerV1.compile`, assigning its sealed uniform offset through `FrameSourceLayoutV4`, and encoding that result in `FilterPassOperationV1.ColorFilter`; prohibit a W6c color evaluator.
- [ ] **Step 5: Refactor the evaluator recursion** to return `(PlanResourceId, FilterBoundsPlanV1)` together so no call site can substitute an implicit global source or detach bounds from its result.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6cSpatialDagPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'
```

- [ ] **Step 7: Commit and review** with `git add render-ir gpu-plan gpu-renderer kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6cComposeSurfaceTest.kt,picture/W6cSpatialDagPictureTest.kt} && git commit -m 'feat(gpu): bind w6c compose and color filters'`; request Sol review and one bounded correction/re-review at most.

### Task 4: Ordered Merge and Blend Multi-input Passes

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `PLAN/W6cMultiInputPlanner.kt`, `GPU/filters/GPUW6cMultiInputPass.kt`, `TEST/surface/W6cMultiInputSurfaceTest.kt`
- Modify: `PLAN/PlanPasses.kt`, `PLAN/RenderGraph.kt`, `PLAN/FinalBlendPlan.kt`, W6b `PLAN/SpatialFilterDagPlanV1.kt`, W6b lowerer/materializer.

**Consumes:** Task 3 occurrence results, `FilterPass.inputs()` ordered list, W5 `FinalBlendPlanner`, one frame-wide pass/resource ID allocator.

**Produces:** `planMerge(inputs: List<CapturedFilterInputV1>, currentSourceId: PlanResourceId)` and `planBlend(background, foreground, mode, currentSourceId)` that freeze ordered resource inputs and per-input bounds/dependencies.

**Public cases:** `mergePreservesDuplicateInputOrder`, `blendPreservesBackgroundForegroundOrder`, `equalButDistinctPublicSubtreesDoNotAlias`, `sharedCapturedInputRemainsReplayStable`.

- [ ] **Step 1: Write failing byte-exact tests** for three-input merge order, repeated shared public input, and Blend background/foreground reversal:

```kotlin
@Test fun `blend preserves background foreground order`() {
    val filter = ImageFilter.Blend(BlendMode.SRC_IN, ImageFilter.Offset(0f, 0f), ImageFilter.Offset(1f, 0f))
    val surface = Surface(2, 1)
    surface.canvas { saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter))); drawW5Rect(ColorARGB.Red); restore() }
    assertContentEquals(expectedBackgroundForegroundBytes, surface.render().pixels)
}
```

- [ ] **Step 2: Run RED** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest'`; retain `W5bBlendSurfacePixelTest` as a direct W5 GREEN control.
- [ ] **Step 3: Implement ordered occurrence planning**: evaluate each Merge input left-to-right in the same bound-source context; evaluate Blend background then foreground; do not deduplicate a node unless its full occurrence key and input generations match.
- [ ] **Step 4: Freeze multi-input payloads and W5 BlendPlan**: `FilterPass.inputs()` retains duplicates and order; `RenderGraph` validates every listed dependency and `GPUW6cMultiInputPass` composites in that frozen sequence only.
- [ ] **Step 5: Refactor common ordered-input traversal** into one planner function accepting an explicit `List<CapturedFilterInputV1>`; do not use a `Set`, canonical-ID map, or renderer sort.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'
```

- [ ] **Step 7: Commit and review** with `git add gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cMultiInputSurfaceTest.kt && git commit -m 'feat(gpu): execute ordered w6c merge and blend'`; request Sol review and one bounded correction/re-review at most.

### Task 5: Dilate and Erode

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `PLAN/W6cMorphologyPlanner.kt`, `GPU/filters/GPUW6cMorphologyPass.kt`, `TEST/surface/W6cMorphologySurfaceTest.kt`
- Modify: `GEOM/RectProjectionF64.kt`, W6b `PLAN/SpatialFilterDagPlanV1.kt`, `PLAN/PlanPasses.kt`, `PLAN/PlanResources.kt`, W6a `W6aLayerPlanBudget.kt`, W6b lowerer/materializer.

**Consumes:** Task 2 F64 bounds utilities, W6b `FilterTarget` ping-pong ownership, W6a physical slot/budget/lease lifecycle.

**Produces:** `planMorphology(kind, radiusXF64, radiusYF64, input, desiredOutput)` with frozen horizontal/vertical generic pass sequence, outward-expanded Dilate output and inward-required Erode input bounds.

**Public cases:** `dilateExpandsOnePixelExactly`, `erodeContractsOnePixelExactly`, `asymmetricMorphologyRadiiRespectClip`, `morphologyPictureMemoryAndWireReplayMatch`.

- [ ] **Step 1: Write failing exact public pixel tests** for single-pixel Dilate expansion, Erode contraction, asymmetric radii, explicit clip, and `Picture` replay through the same W6 path.
- [ ] **Step 2: Run RED** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMorphologySurfaceTest'`; record W6c rejection rather than exercising `GPUMorphologyTest` or a private kernel.
- [ ] **Step 3: Implement planner bounds and frozen resources** using F64 radii and checked outward I32 projection; allocate all ping-pong `FilterTarget`s, uniforms, samplers, usages, lifetimes, slots and I64 bytes before `RenderGraph` publication.

```kotlin
val operation = FilterPassOperationV1.Morphology(
    morphologyKind = morphologyKind,
    radiusXF64 = radiusXF64,
    radiusYF64 = radiusYF64,
    bounds = bounds,
    kind = implementationKind,
)
val required = requiredInputBounds(operation, desiredOutputDeviceI32, mapping)
val output = filterTargetAllocator.reserve(required, PlanResourceRole.FilterTarget)
passes += FilterPass(nextPass(), listOf(input), output, evaluationKey, operation)
```
- [ ] **Step 4: Materialize only frozen morphology operands** in `GPUW6cMorphologyPass`; the renderer may use a separable generic kernel but cannot choose a radius, bounds, output target, or fallback implementation.
- [ ] **Step 5: Refactor shared separable-pass issuance** so blur remains a W6b payload and morphology stays a W6c payload on the same `FilterPass`, target allocator, budget, and lease path.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMorphologySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest'
```

- [ ] **Step 7: Commit and review** with `git add math/geometry gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cMorphologySurfaceTest.kt && git commit -m 'feat(gpu): execute w6c morphology filters'`; request Sol review and one bounded correction/re-review at most.

### Task 6: Spatial Cache, B/B−1, Terminal Failure, and Recovery

**Agent:** Terra implementation; Sol review.

**Files:**

- Create: `PLAN/SpatialFilterCacheKeyV1.kt`, `PLAN/SpatialFilterCachePlanV1.kt`, `TEST/surface/W6cSpatialCacheRecoverySurfaceTest.kt`
- Modify: `PLAN/PlanPhysicalLayoutV1.kt`, `PLAN/PlanResources.kt`, W6a `W6aLayerPlanBudget.kt`, W6b spatial graph/lowerer/materializer/cache bridge, `GPU/execution/GPUFramePreflighter.kt`, `GPU/execution/GPUPreparedNativeFramePayload.kt`.

**Consumes:** full occurrence key, Task 2–5 bounds/passes, existing physical cache binding, W6a budget accounting, and completion/quarantine lease framework.

**Produces:** `SpatialFilterCacheKeyV1` plus one sealed cache request/binding per filter result; identical cold/warm plan accounting and one retained lease per consumer frame through completion/quarantine.

**Public cases:** `coldSpatialPlanAcceptsExactBudgetBoundary`, `warmSpatialReplayKeepsPessimisticBudgetBoundary`, `changedPictureSourceGenerationMissesSpatialCache`, `changedDesiredOutputOrClipMissesSpatialCache`, `lateSpatialRefusalLeavesSentinelAndSameSurfaceRecovers`.

- [ ] **Step 1: Write failing public tests** for exact B/B−1 cold and warm replay, changed source generation after Picture capture, changed desired output/clip, and review-focus late refusal recovery. Derive B from 1×1 RGBA8 targets, 256-byte readback row, and documented frozen uniform/target counts before constructing any `Surface`.
- [ ] **Step 2: Run RED** with `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialCacheRecoverySurfaceTest'`; record current cache/accounting behavior through public pixels/diagnostics only.
- [ ] **Step 3: Implement the full immutable cache key** with occurrence tuple, four bounds, format/color space/sample count, all source identity/generation/subset facts, and capability/backend generations; require equality of every field before reusing a result.

```kotlin
val cacheKey = SpatialFilterCacheKeyV1(
    evaluationKey = evaluationKey, bounds = bounds, format = format, sampleCountI32 = 1,
    capabilityGenerationI64 = capabilities.generationI64, backendGenerationI64 = backendGenerationI64,
    inputGenerations = orderedInputGenerations,
)
```
- [ ] **Step 4: Include hit resources in preflight**: issue the same logical/physical budget request on cold and warm paths, allocate/acquire before ready publication, and retain the consumer lease until completion or safe quarantine; never publish a cache hit outside the W6 frame token.
- [ ] **Step 5: Refactor cache admission** so the renderer receives an already chosen cache binding or exact miss allocation and has no cache-key construction, late allocation, or alternate filter plan.
- [ ] **Step 6: Run serialized verification**:

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialCacheRecoverySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6cSpatialDagPictureTest'
```

- [ ] **Step 7: Commit and review** with `git add gpu-plan gpu-renderer kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cSpatialCacheRecoverySurfaceTest.kt && git commit -m 'feat(gpu): seal w6c spatial cache leases'`; request Sol review and one bounded correction/re-review at most.

### Task 7: W6c Convergence, Audit, and Stacked Draft PR

**Agent:** Terra implementation; Sol review-only.

**Files:**

- Modify: `refactor/waves/W06-layers-effects/status.md`, `refactor/README.md`, `refactor/plans/2026-09-22-w6c-spatial-dag-implementation-plan.md`

**Consumes:** Tasks 1–6 commits and the reviewed W6b base hash.

**Produces:** W6c custody/status, one Sol whole-branch review, one bounded correction wave at most, and a Draft PR stacked on W6b without merge.

- [x] **Step 1: Run all W6c public shards sequentially**:

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialDagAdmissionSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialBoundsSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMorphologySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cSpatialCacheRecoverySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6cSpatialDagPictureTest'
```

- [x] **Step 2: Run targeted preservation sequentially**:

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bDropShadowSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bBudgetRecoverySurfacePixelTest'
```

- [x] **Step 3: Compile touched modules sequentially**:

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
```

- [x] **Step 4: Audit production paths manually** for `GPUImageFilterPlan`, `GPUMorphology`, `GPUFilterTile`, `copyTargetToOffscreenTexture`, post-freeze `PlanPass`/`PlanResource` creation, renderer bounds conversion, and legacy prepared composite route. Classify any legitimate W8 reference in status; do not add a static test.
- [x] **Step 5: Update durable status** with exact source/base hashes, tests/XML PASS/failure/error/skip counts, Gradle/native exits, nine admitted families, cache/B/B−1/recovery coverage, explicit W6d exclusions, and no ISO/global claim.
- [x] **Step 6: Commit documentation** with `git add refactor && git commit -m 'docs(refactor): record w6c spatial dag status'`.
- [ ] **Step 7: Request Sol whole-branch review** against the exact reviewed W6b base, covering capture/wire preservation, occurrence bindings, F64 bounds, pass/resource freeze, cache generations/leases, native ordering, public evidence, and fallback risk. If Critical/Important findings exist, apply one non-Sol bounded correction commit, rerun only causal selectors plus preservation, and request one scoped Sol re-review.
- [ ] **Step 8: Verify stack and publish** with `rtk git status --short` and `rtk git log --oneline codex/w6b-blur-masks-shadows..HEAD`; push `codex/w6c-spatial-dag` and create/update a Draft PR targeting `codex/w6b-blur-masks-shadows`, describing exact base, commits, gates, exclusions, and native 133/134 `UNKNOWN`. Do not merge.

## W6c Definition of Done

- [ ] The reviewed W6b table/input/pass/target foundation is used without duplicate authority.
- [ ] Crop, Offset, Tile, ColorFilter, Compose, Merge, Blend, Dilate, and Erode execute through frozen W6 `FilterPass`/`FilterTarget` payloads.
- [ ] Occurrences use the full `(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)` key; Compose and multi-input ordering are public byte-exact witnesses.
- [ ] F64 bounds/mappings and all four regions remain frozen until native materialization; renderer performs no replanning.
- [ ] ColorFilter and Blend reuse W5f/W5 authorities exactly once.
- [ ] Spatial cache keys include all required semantic/generation facts; hits remain pessimistically budgeted and leased to completion/quarantine.
- [ ] Public Surface/Picture tests cover mutation, memory/wire replay, bounds/origins/clips, B/B−1, terminal refusal/sentinel, and same-Surface recovery without prohibited test mechanisms.
- [ ] Backdrop, filtered previous, W6d families, F16/HDR, fonts/codecs/GMs/dashboard/renders/baselines/scores/global Skia/`jpg-color-cube`, and native 133/134 claims remain excluded.
- [ ] Sol whole-branch review has no open Critical/Important finding; at most one bounded correction wave occurred.
- [ ] One unmerged Draft PR is stacked on W6b with no ISO/global convergence claim.

## Plan Self-Review

- **Spec coverage:** Tasks 1–5 cover the nine W6c families and source binding; Task 2 owns F64 spatial bounds; Task 6 owns cache, budgets, leases, refusal visibility, and recovery; Task 7 owns serial qualification/reviews/PR. Backdrop, filtered previous, Picture/runtime, displacement, convolution, magnifier, and lighting are intentionally W6d gaps.
- **Placeholder scan:** No unresolved placeholder marker or unspecified test step remains. The W6b base-contract stop is a concrete prerequisite, not an implementation placeholder.
- **Type consistency:** Every task consumes W6b `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `FilterPassOperationV1`, one `FilterPass`, and `FilterTarget`; no task introduces a second graph/cache/allocator type.
- **Review focus:** The five listed risks are pinned respectively in Task 3, Task 4, Task 2, and Task 6 public tests.

## Execution Handoff

Plan complete and saved to `refactor/plans/2026-09-22-w6c-spatial-dag-implementation-plan.md`. The required execution method is subagent-driven: Terra implements each task and Sol reviews it; W6c must wait for the reviewed W6b foundation and for review of this plan before implementation.
