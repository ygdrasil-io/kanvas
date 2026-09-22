# W6b Blur, Masks, and Shadows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver plan-first image blur, the three public mask-filter families, and composited or shadow-only drop shadows through the W6a authority, with immutable Picture 14/schema 8 capture, atomic public recovery, and no legacy fallback after W6b admission.

**Architecture:** W6b first replaces recursive image-filter capture with one immutable, identity-preserving node table and typed input references in `:render-ir`. `:gpu-plan` binds those captured nodes to a W6a layer or a draw auto-layer, computes all spatial bounds in `:math`, and freezes one extension of the existing W6a `RenderGraph` using typed `FilterPass`/`FilterTarget` resources. `:gpu-renderer` materializes only the frozen pass sequence (coverage/source → X blur → Y blur → mask/shadow/composite), never reconstructing bounds, tile modes, resources, IDs, or budgets.

**Tech Stack:** Kotlin/JVM; `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; JUnit 5; public `Surface`, `Canvas`, `Picture`, paint/filter APIs, Render, and Readback.

**Spec:** `refactor/specs/2026-09-16-w6-layers-effects-design.md` sections 5–8, 10.2, and 11–16; `refactor/specs/2026-09-22-w6b-w6e-stacked-delivery-design.md` sections 1–4 and 8–10. Base: `codex/w6a-layer-authority` at `1ff67ec849e66de4c1ac2767ddb1dffce8d40631` (Draft PR #2403). Implementation branch: `codex/w6b-blur-masks-shadows`; its Draft PR targets `codex/w6a-layer-authority`.

## Global Constraints

- Read both approved W6 design documents in full before every task. If current code contradicts either, stop the task and report the exact file, symbol, and contradiction; do not introduce a competing route.
- Execute every behavior as public RED → GREEN → refactor. A RED is valid only when unchanged production fails the stated public pixel, bytes, diagnostic, or recovery assertion—not because compilation, a fixture, permissions, WebGPU availability, or test infrastructure failed.
- Tests use only public `Surface`, `Canvas`, `Picture`, paint/layer/filter APIs, Picture bytes, public diagnostics, Render, Readback, and public discard/re-record recovery. Do not add private/internal, reflection, mock, fake-device, counter, static-source, or infrastructure tests.
- Compute expected pixels independently before creating a `Surface` or `PictureRecorder`. Blur tests use an independent CPU oracle and a family-local tolerance; identity/copy/composite/table tests are byte-exact. Never widen a global tolerance.
- `:math` is the sole owner of new rectangles, points, sizes, mappings, and transforms. New geometric names use `I32`, `I64`, `F32`, or `F64`; F64 computes bounds, checked outward I32 projection seals texels, and `:gpu-renderer` has no private geometric value or device↔target conversion.
- Preserve W6a's four regions (`knownContent`, `desiredOutput`, `requiredInput`, `producedOutput`) separately per spatial occurrence. Bounds supplied to `saveLayer` remain a hint/filter region, never an implicit child clip.
- The one W6 authority remains `Surface/Picture → immutable render-ir → :math → :gpu-plan RenderGraph → :gpu-renderer → one submit → atomic public visibility`. After W6b selection, any refusal is terminal and may not continue into `GPUPreparedSurfaceProductEntry` or another prepared/legacy route.
- `FilterPass` and `FilterTarget` in `:gpu-plan` are the only W6b spatial pass/resource authorities. Existing `GPUSeparableBlurRectFrameRecorder`, `GPUTopLevelMaskBlurFrameRecording`, `GPUPreparedFilterDAGPlanner`, `GPUPreparedMaskFilterLowerer`, `GPUDropShadow`, and `GPUSaveLayerNativeExecutor` may remain for non-W6 calls through W8 but receive no W6b semantic decision.
- Freeze before native preparation: selected implementation kind, passes, `FilterTarget` descriptors/usages, resource/pass IDs, physical allocation slots, lifetimes, uniforms, samplers, source generations, and checked I64 budget. The renderer only translates this frozen record to native work.
- Positive W6b format is single-sample RGBA8 inherited from W6a. Backdrop, filtered `initWithPrevious`, F16/HDR, W6c/W6d filter families, arbitrary SkSL/WGSL, and unavailable physical capabilities fail before allocation with their owner’s stable diagnostic; never substitute or approximate.
- Image blur supports `CLAMP`, `REPEAT`, `MIRROR`, and `DECAL` in both axes. Its selected X and Y passes carry the requested tile mode and separately sealed source/output rectangles; renderer sampling cannot silently coerce any mode to clamp.
- `MaskFilter.Blur` supports `NORMAL`, `SOLID`, `OUTER`, and `INNER` by transforming coverage before material shading. `MaskFilter.Shader` multiplies that coverage by the W5 material alpha in the same mapping; it must reuse the W5 material table/catalogue, not compile a second shader authority. New `MaskFilter.Table` requires exactly 256 immutable entries.
- Historical Pictures with a table length other than 256 remain decodable. W6b admission refuses them before native preparation with exactly `invalid.mask_filter.table_length`; it neither truncates, pads, clamps, nor mutates the historical table.
- Add public `DropShadowMode` with `COMPOSITE` as the default and `SHADOW_ONLY` as the additive mode. `COMPOSITE` paints shadow then original source exactly once; `SHADOW_ONLY` paints only the shadow. Both values are snapshotted by scene capture and Picture wire; old records decode as `COMPOSITE`.
- A paint carrying an admitted image filter or mask filter creates a typed W6b auto-layer. Paint consumption order is coverage/style/path effect → mask filter → W5 shader/color/alpha/color filter → image filter → clip → one final blend. Remove each consumed attribute from the intermediate draw so it cannot be applied twice at draw, implicit layer, or restore.
- The filter-table wire preserves capture identity. `ImplicitSource`, `TransparentBlack`, and `Node(CapturedFilterNodeIdI32)` are explicit input values; equal recursive values never alias. A legacy recursive archive converts each occurrence into a distinct table node. Reject unknown IDs, cycles, fan-out/depth/size overflow, and malformed roots before large copies or publication.
- Budget all logical and physical layer targets, filter ping-pong targets, coverage targets, destination snapshots, W4/W5 buffers/textures/uniforms/samplers, readback, alignment, source generations, and leases through completion/quarantine. B accepts and B−1 refuses before native allocation; cache warmth cannot reduce admission cost.
- A W6b frame is atomic. A late sibling refusal leaves public readback sentinel bytes unchanged; after balanced Canvas state and `discardRecordedOperations()`, an admitted scene on the same `Surface` renders exactly. Native exits 133 and 134 have cause `UNKNOWN` unless independent concrete evidence proves otherwise.
- Fonts/glyph generation, codecs/external format decode or encode, Skia GMs, dashboard, renders, references, baselines, scores, rebaseline, `jpg-color-cube`, global `:integration-tests:skia`, tests of test infrastructure, and a global Skia/ISO claim are outside every W6b gate.
- Serialize every Gradle invocation and run every shell command in `/Users/chaos/.codex/worktrees/cbf6/kanvas` via `rtk` or `rtk proxy`. Record Gradle exit, JUnit XML method custody, native exit, and source commit independently.
- Use Terra for implementation of every task and Sol only for review. Each task ends with one implementation commit, one Sol task review, at most one bounded correction commit, and one scoped Sol re-review if corrected. The PR receives one whole-branch Sol review, at most one bounded non-Sol correction wave, then one scoped re-review; do not iterate further and do not merge automatically.

## Review Focus

- `DECAL` blur of an opaque edge must fade to transparent outside the source, whereas `CLAMP` retains the edge color; Task 3: `imageBlurTileModesAreDistinctAtTheSourceEdge` uses its CPU oracle.
- An `OUTER` mask blur must be zero inside the original coverage and an `INNER` blur must be zero outside it; Task 4: `maskBlurStylesTransformCoverageBeforeMaterialShading` pins all four styles.
- A shared public filter object must remain one captured node while two equal-but-distinct filter objects remain two nodes after memory and wire Picture replay; Task 1: `picture14PreservesSharedFilterIdentityWithoutValueAliasing` proves it through bytes and pixels.
- A malformed historical table length must decode as Picture data but refuse with `invalid.mask_filter.table_length` without changing readback; Task 5: `historicalInvalidTableRefusesAtomicallyAndSameSurfaceRecovers` owns the behavior.
- `SHADOW_ONLY` must not repaint the original source, including after nonzero offset and an expanded output domain; Task 6: `shadowOnlyOmitsSourceAndExpandsBounds` compares independent expected pixels.

---

## Actual Code Map and Intended Ownership

Repository-relative aliases:

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

| Existing owner | W6b responsibility |
| --- | --- |
| `API/paint/ImageFilter.kt`, `MaskFilter.kt`, `TileMode.kt`, `Paint.kt` | Add public `DropShadowMode`; keep nullable image inputs as public implicit-source requests and preserve existing defaults/positional calls. |
| `API/canvas/DisplayOpSnapshot.kt`, `API/render/ir/PaintSceneAdapter.kt`, `DisplayOpSceneAdapter.kt`, `SceneDisplayOpAdapter.kt` | Deep-snapshot public filter identity, convert recursion to the immutable table, and reconstruct public operations without exposing a planner. |
| `IR/EffectNode.kt`, `SceneCommand.kt`, `SceneArchiveCodec.kt` | Own `CapturedFilterTableV1`, typed references, graph limits, canonical identity, Picture 14/schema 8 encode/decode, legacy recursive conversion, and `DropShadowMode`. |
| `API/picture/Picture.kt`, `PictureWireV8.kt` | Write version 14/schema 8; read historical v8–13 and schema 1–7, defaulting missing shadow mode to `COMPOSITE`; leave read-only historical v8 handling intact. |
| `GEOM/RectProjectionF64.kt`, `RectI32.kt`, `MATRIX/LayerMappingF64.kt` | Reuse W6a checked outward projection/mapping and add only filter-bound expansion/translation helpers with F64 calculation and checked I32 results. |
| `PLAN/PlanResources.kt`, `PlanPasses.kt`, `RenderGraph.kt`, `RenderGraphConstruction.kt`, `W6aLayerPlanBudget.kt` | Add typed `FilterTarget`, typed filter payloads, W6b compiler witness validation, dependencies, slots, semantic/physical lifetimes, and checked peak accounting to the existing single graph. |
| `PLAN/W6aLayerPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`, `LayerScopePlanV1.kt`, `CapabilityCompilerChain.kt` | Upgrade the existing layer owner to recognize W6b filters, bind captured roots to source contexts, freeze auto-layer/layer filter occurrences, and preserve W4/W5 sources before publication. |
| `PLAN/FrameSourceLayoutV4.kt`, `MaterialSourceConstructionV4.kt`, `RawMaterialRequirementsV2.kt` | Remain the only W5 material/source issuer for filtered draws and mask shaders; bind pre-publication source targets without a W6b material compiler. |
| `GPU/planning/W6aLayerGraphLowerer.kt`, `GpuPlanTaskListLowerer.kt`, `GPU/recording/GPUW6aLayerFramePlan.kt` | Lower the sealed W6b graph and pass payloads to handle-free recorded steps; accept no new IDs, bounds, passes, or routes. |
| `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `GPUWgpu4kFramePayloadMaterializerDispatcher.kt`, `GPUW6aEncoderScopesV1.kt` | Reserve/materialize the frozen filter targets and execute the planned samples/renders/composites behind the existing draft → ready → submit → completion lifecycle. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `GPUPlanSurfaceRouter.kt` | Make a scene containing a W6b filter or auto-layer W6-owned and terminal after selection; preserve non-W6 routes unchanged. |
| `GPU/filters/BlurFilter.kt`, `GPUFilterTile.kt`, `GPUDropShadow.kt`, `GPUPreparedFilterDAGPlanner.kt`, `GPUPreparedMaskFilterLowerer.kt`, `GPU/recording/GPUSeparableBlurRectFrameRecorder.kt`, `GPUTopLevelMaskBlurFrameRecording.kt` | Legacy/non-W6 implementation references only. They must not supply W6b bounds, budget, pass choice, or fallback execution. |

## Frozen W6b Contracts

### Immutable capture and wire in `:render-ir`

Create `IR/CapturedFilterTableV1.kt`. The table is part of `SceneSnapshot`, is deeply immutable, assigns IDs in first public identity encounter order, and retains no native handle, WGSL, backend capability, or GPU resource.

```kotlin
@JvmInline
public value class CapturedFilterNodeIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) }
}

@JvmInline
public value class CapturedPictureIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) }
}

@JvmInline
public value class CapturedBackdropIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) }
}

public sealed interface CapturedFilterInputV1 {
    public data object ImplicitSource : CapturedFilterInputV1
    public data object TransparentBlack : CapturedFilterInputV1
    public data class Node(public val id: CapturedFilterNodeIdI32) : CapturedFilterInputV1
    public data class Picture(public val id: CapturedPictureIdI32) : CapturedFilterInputV1
    public data class Backdrop(public val id: CapturedBackdropIdI32) : CapturedFilterInputV1
}

// Imported from org.graphiks.kanvas.paint.DropShadowMode; render-ir owns its snapshot,
// not a second public enum.

public sealed interface CapturedFilterNodeV1 {
    public val id: CapturedFilterNodeIdI32
    public data class Blur(
        override val id: CapturedFilterNodeIdI32,
        public val sigmaXF32: Float,
        public val sigmaYF32: Float,
        public val tileMode: TileMode,
        public val input: CapturedFilterInputV1,
    ) : CapturedFilterNodeV1
    public data class DropShadow(
        override val id: CapturedFilterNodeIdI32,
        public val dxF32: Float,
        public val dyF32: Float,
        public val sigmaXF32: Float,
        public val sigmaYF32: Float,
        public val color: ColorARGB,
        public val mode: DropShadowMode,
        public val input: CapturedFilterInputV1,
    ) : CapturedFilterNodeV1
    // Capture-only typed variants for the remaining public ImageFilter families.
}

public class CapturedFilterTableV1 internal constructor(
    nodes: List<CapturedFilterNodeV1>,
) {
    public fun nodes(): List<CapturedFilterNodeV1>
    public fun nodeOrNull(id: CapturedFilterNodeIdI32): CapturedFilterNodeV1?
    public fun validate(graphLimits: GraphLimits): SceneSemanticValidationResult
}
```

`PaintNode` and layer restore descriptors carry `CapturedFilterInputV1?` roots into this Scene-owned table; a null public input is captured as `ImplicitSource`, not as a global binding. Capture converts every historical recursive occurrence to a fresh node ID unless the same public object identity was shared. Schema 8 writes the table once before commands and layer/paint roots by ID. It reads schemas 1–7 through the recursive reader, assigning fresh IDs in decode order; schema 8 detects unknown references/cycles before publishing `SceneSnapshot`.

### Geometry and physical planning in `:gpu-plan`

Create `PLAN/W6bFilterPlanV1.kt`; extend, rather than parallel, the existing `PlanPass.FilterPass` and `PlanResourceRole`:

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
}

public class FilterBoundsPlanV1 internal constructor(
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    requiredInputDeviceI32: RectI32,
    producedOutputDeviceI32: RectI32?,
    targetOriginDeviceI32: Point2I32,
) {
    public fun copyKnownContentDeviceI32(): RectI32?
    public fun copyDesiredOutputDeviceI32(): RectI32
    public fun copyRequiredInputDeviceI32(): RectI32
    public fun copyProducedOutputDeviceI32(): RectI32?
    public fun copyTargetOriginDeviceI32(): Point2I32
}

public class FilterEvaluationKeyV1 internal constructor(
    public val capturedNodeId: CapturedFilterNodeIdI32,
    public val boundSourceId: PlanResourceId,
    public val mapping: LayerMappingF64,
    desiredOutputDeviceI32: RectI32,
) {
    public fun copyDesiredOutputDeviceI32(): RectI32
    public companion object {
        public fun of(
            capturedNodeId: CapturedFilterNodeIdI32,
            boundSourceId: PlanResourceId,
            mapping: LayerMappingF64,
            desiredOutputDeviceI32: RectI32,
        ): FilterEvaluationKeyV1
    }
}

public sealed interface FilterPassOperationV1 {
    public val kind: FilterImplementationKindV1
    public val bounds: FilterBoundsPlanV1
    public data class SeparableBlur(
        override val kind: FilterImplementationKindV1,
        public val sigmaF32: Float,
        public val axis: FilterAxisV1,
        public val tileMode: TileMode,
        override val bounds: FilterBoundsPlanV1,
    ) : FilterPassOperationV1
    public data class MaskBlurStyle(
        public val style: BlurStyle,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_BLUR_STYLE,
    ) : FilterPassOperationV1
    public data class MaskShader(
        public val material: MaterialPlanRef,
        public val uniformOffsetI64: Long,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_SHADER,
    ) : FilterPassOperationV1
    public class MaskTable(
        table: ImmutableUBytes,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_TABLE,
    ) : FilterPassOperationV1 {
        private val tableSnapshot = ImmutableUBytes.copyOf(table.copyToUByteArray())
        public fun copyTable(): ImmutableUBytes = ImmutableUBytes.copyOf(tableSnapshot.copyToUByteArray())
    }
    public class DropShadowColorize(
        public val color: ColorARGB,
        offsetF64: Vector2F64,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COLORIZE,
    ) : FilterPassOperationV1 {
        private val offsetSnapshotF64 = Vector2F64(offsetF64.x, offsetF64.y)
        public fun copyOffsetF64(): Vector2F64 = Vector2F64(offsetSnapshotF64.x, offsetSnapshotF64.y)
    }
    public data class DropShadowComposite(
        public val mode: DropShadowMode,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COMPOSITE,
    ) : FilterPassOperationV1
}

public class FilterPass(
    override val ordinal: Int,
    inputs: List<PlanResourceId>,
    public val output: PlanResourceId,
    public val evaluationKey: FilterEvaluationKeyV1,
    public val operation: FilterPassOperationV1,
) : PlanPass
```

Add `PlanResourceRole.FilterTarget`. Its texture is RGBA8, single sample, `RenderAttachment + Sampled + CopySource + CopyDestination` only where the sealed pass requires each usage. `W6bFilterGraphConstruction` is an internal collaborator of `W6aLayerGraphConstruction`; it owns all new target descriptors, passes, ID allocation, pass dependencies, allocation slots, and I64 checked budgets. It binds an evaluation only with `(capturedNodeId, boundSourceId, mappingF64, desiredOutputI32)` plus source generations. No equal-by-value node may be merged; a reuse proof requires full key equality and is deferred—initial W6b uses distinct targets.

`FilterBoundsPlanV1` computes expansions in F64 (including blur support) and seals outward `RectI32` only in `:math`. The filter compiler converts device rectangles to filter-target texels using already sealed target origins. `GPUW6aLayerFramePlan` receives target-local rectangles and never performs subtraction, `.toInt()`, saturation, target resize, or a new bounds calculation.

### Auto-layer semantics

For every admitted `SceneCommand.Draw` with a mask or image filter, `W6aLayerPlanCompiler` emits an internal `AutoLayerPlanV1` rooted at the draw’s active W6 target. It captures the raw W4 coverage and W5 source once, runs `MaskFilter` before source shading and `ImageFilter` after source construction, clips the final output, then uses the already selected `BlendPlan` once. It is not public `saveLayer`, has no restore descriptor, and does not change public Canvas save/restore depth.

`MaskFilter.Table` payloads hold `ImmutableUBytes` and require `size == 256` only at W6b plan admission. `MaskFilter.Shader` payloads reference an existing `MaterialPlanTable` entry through `MaterialPlanRef` and its sealed W5 coordinate/mapping authority. The renderer sees a material reference and uniform offsets, never a public `Shader` or a newly compiled material.

### Renderer execution boundary

Extend `GPUW6aLayerFramePlan`, `GPUW6aEncoderScopesV1`, and `GPUWgpu4kW6aLayerFramePayloadMaterializer` with typed filter operands. The materializer allocates every frozen `FilterTarget`, uploads frozen uniforms/LUT bytes, then encodes exactly the graph's ordered `FilterPass` arms. It may select an already named native encoder/pipeline for `FilterImplementationKindV1`; it must reject a missing named mapping as `w6b.filter.native_capability` before publish, never choose a new implementation or route legacy.

## Task 1: Immutable Filter Table, Drop Shadow API, and Picture 14/Schema 8

**Agent:** Terra implementation; Sol review.

**Outcome:** Public image-filter recursion is captured as one immutable node table with typed roots; `DropShadowMode` defaults to `COMPOSITE`; Picture 14/schema 8 round-trips roots, sharing, and shadow mode while Picture 8–13 remains readable.

**Files — Create:**

- `API/paint/DropShadowMode.kt`
- `IR/CapturedFilterTableV1.kt`
- `TEST/picture/W6bFilterPictureTest.kt`
- `RES/format-13-drop-shadow-composite.base64`
- `RES/format-13-recursive-equal-filter-occurrences.base64`

**Files — Modify:**

- `API/paint/ImageFilter.kt`
- `API/canvas/DisplayOpSnapshot.kt`
- `API/render/ir/PaintSceneAdapter.kt`
- `API/render/ir/DisplayOpSceneAdapter.kt`
- `API/render/ir/SceneDisplayOpAdapter.kt`
- `IR/EffectNode.kt`
- `IR/SceneCommand.kt`
- `IR/SceneArchiveCodec.kt`
- `API/picture/Picture.kt`
- `TEST/picture/PictureTest.kt`
- `TEST/picture/W6aLayerPictureTest.kt`

**Interfaces:**

- Consumes: current recursive `ImageFilter`, `ImageFilterNode`, `MaskFilterNode`, `SceneSnapshot`, and `SceneArchiveCodec` v13/schema 7.
- Produces: `CapturedFilterTableV1`, `CapturedFilterInputV1`, `CapturedFilterNodeV1`, `DropShadowMode`, Picture 14/schema 8. Tasks 2–6 consume only table IDs/roots, never recursive filter planning.

- [ ] **Step 1: Add public RED tests for version, default, deep snapshot, and identity.**

```kotlin
@Test
fun picture14PreservesSharedFilterIdentityWithoutValueAliasing() {
    val shared = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
    val equalButDistinct = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
    val picture = pictureWithThreeFilteredDraws(shared, shared, equalButDistinct)
    val bytes = picture.toByteArray()
    assertEquals(14, ByteBuffer.wrap(bytes).getInt(4))
    assertEquals(8, ByteBuffer.wrap(bytes).getInt(28))
    assertContentEquals(bytes, assertNotNull(Picture.fromByteArray(bytes)).toByteArray())
}
```

- [ ] **Step 2: Run only the new selectors against unchanged production and record behavioral REDs on the v13/schema 7 header or lost mode/sharing.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'`

Expected: FAIL because current output is Picture 13/schema 7 and cannot retain the new mode/table identity.

- [ ] **Step 3: Capture real v13 fixtures before changing production and add their public decode/replay controls.**

```kotlin
val decoded = assertNotNull(Picture.fromByteArray(fixture("format-13-drop-shadow-composite.base64")))
assertEquals(DropShadowMode.COMPOSITE, dropShadowOf(decoded).mode)
assertPlaybackRenders(decoded)
```

- [ ] **Step 4: Add `DropShadowMode` and append mode after `input` in the public `ImageFilter.DropShadow` constructor.**

```kotlin
data class DropShadow(
    val dx: Float, val dy: Float, val sigmaX: Float, val sigmaY: Float,
    val color: ColorARGB, val input: ImageFilter? = null,
    val mode: DropShadowMode = DropShadowMode.COMPOSITE,
) : ImageFilter
```

- [ ] **Step 5: Implement the identity table conversion, scene roots, canonical IDs, schema-8 writer/reader, and recursive compatibility conversion.**

```kotlin
private fun legacyInput(value: ImageFilterNode?): CapturedFilterInputV1 =
    value?.let { CapturedFilterInputV1.Node(appendLegacyOccurrence(it)) }
        ?: CapturedFilterInputV1.ImplicitSource
```

- [ ] **Step 6: Reject malformed table references/cycles before Scene publication and default omitted v8–13 shadow mode to `COMPOSITE`.**

```kotlin
val mode = if (sceneArchiveSchemaVersion >= 8) enum<DropShadowMode>() else DropShadowMode.COMPOSITE
```

- [ ] **Step 7: Run compile and Picture gates sequentially.**

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.PictureTest'
```

- [ ] **Step 8: Refactor only duplicate table traversal into one iterative capture/decode helper, preserving encounter-order IDs; rerun Task 1 tests.**

- [ ] **Step 9: Commit Task 1.**

```sh
git add kanvas render-ir
git commit -m "feat(picture): capture w6b filter tables and shadow modes"
```

- [ ] **Step 10: Request Sol review of the Task 1 commit. If it finds an issue, make one bounded correction commit and request one scoped Sol re-review.**

## Task 2: One W6b Filter Authority, Bounds, Targets, and Terminal Admission

**Agent:** Terra implementation; Sol review.

**Outcome:** The existing W6a authority owns any layer or draw containing a captured W6b filter, binds roots to an immutable source context, freezes typed filter passes/targets and budgets in one graph, and refuses unimplemented W6c/W6d/backdrop/F16 inputs terminally without public output.

**Files — Create:**

- `PLAN/W6bFilterPlanV1.kt`
- `PLAN/W6bFilterGraphConstruction.kt`
- `PLAN/W6bFilterDiagnostics.kt`
- `TEST/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`

**Files — Modify:**

- `GEOM/RectProjectionF64.kt`
- `MATRIX/LayerMappingF64.kt`
- `PLAN/PlanResources.kt`
- `PLAN/PlanPasses.kt`
- `PLAN/RenderGraph.kt`
- `PLAN/RenderGraphConstruction.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/CapabilityCompilerChain.kt`
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- `API/surface/gpu/GPUPlanSurfaceRouter.kt`

**Interfaces:**

- Consumes: Task 1 `CapturedFilterTableV1` roots; W6a `LayerMappingF64`, `LayerBoundsPlanV1`, `RenderGraph`, and W5 source lanes.
- Produces: `FilterBoundsPlanV1`, `FilterEvaluationKeyV1`, `FilterPassOperationV1`, `PlanResourceRole.FilterTarget`, and stable W6b diagnostics. Tasks 3–6 add only typed operation arms to this contract.

- [ ] **Step 1: Add public admission/recovery RED tests, including a direct no-filter W6a control.**

```kotlin
@Test
fun w6cFilterRefusesTerminallyAndSameSurfaceRecovers() {
    val surface = Surface(2, 2)
    surface.canvas { drawRect(bounds, Paint(imageFilter = ImageFilter.Offset(1f, 0f))) }
    assertTerminalWithoutReadbackMutation(surface, "w6b.filter.unsupported_family")
    surface.discardRecordedOperations()
    surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
    assertContentEquals(rgba(17, 61, 211), surface.render().pixels)
}
```

- [ ] **Step 2: Run the new shard on unchanged production and record REDs caused by W6a `unsupported_spatial_filter`, not by a test harness failure.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'`

Expected: FAIL with the former W6a spatial-filter refusal or wrong terminal owner.

- [ ] **Step 3: Add the frozen plan types and `FilterTarget` resource role; validate every target-local rectangle and I64 byte calculation before graph publication.**

```kotlin
public enum class FilterAxisV1 { X, Y }

public enum class PlanResourceRole {
    LogicalTarget, LayerTarget, FilterTarget,
    // existing roles remain unchanged
}
```

- [ ] **Step 4: Implement F64 filter expansion and target-local conversion in `:math`, returning null/diagnostics on non-finite, horizon, subtraction, or I32 overflow.**

```kotlin
public fun RectF64.expandForBlurF64OrNull(sigmaXF32: Float, sigmaYF32: Float): RectF64?
public fun RectI32.translateCheckedOrNull(delta: Vector2I32): RectI32?
```

- [ ] **Step 5: Teach `W6aLayerPlanCompiler` to own W6b roots before child-lane planning and have `W6bFilterGraphConstruction` append only frozen resources/passes to the existing graph.**

```kotlin
val key = FilterEvaluationKeyV1.of(
    capturedNodeId = rootId,
    boundSourceId = sourceTarget,
    mapping = layerMapping,
    desiredOutputDeviceI32 = desiredOutput,
)
```

- [ ] **Step 6: Make gate/router terminal after W6b ownership; preserve exact failures for backdrop, filtered previous, F16/HDR, and unimplemented W6c/W6d families.**

- [ ] **Step 7: Run module compilation and admission gates sequentially.**

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest'
```

- [ ] **Step 8: Refactor common refusal construction into `W6bFilterDiagnostics`; do not turn this audit into a private/static test. Rerun Task 2 gates.**

- [ ] **Step 9: Commit Task 2.**

```sh
git add math gpu-plan kanvas
git commit -m "feat(gpu-plan): freeze w6b filter authority"
```

- [ ] **Step 10: Request Sol review of the Task 2 commit. If needed, make one bounded correction commit and one scoped Sol re-review.**

## Task 3: Image Blur X/Y, Four Tile Modes, and W6b Native Materialization

**Agent:** Terra implementation; Sol review.

**Outcome:** An admitted image blur on a layer restore or a draw auto-layer executes frozen horizontal then vertical passes using `CLAMP`, `REPEAT`, `MIRROR`, or `DECAL`, with independently-oracled pixels, correct expansion/origins, and memory/wire Picture replay.

**Files — Create:**

- `GPU/wgsl/W6bSeparableBlurSnippet.kt`
- `TEST/surface/W6bImageBlurSurfacePixelTest.kt`
- `TEST/surface/W6bImageBlurCpuOracle.kt`

**Files — Modify:**

- `PLAN/W6bFilterPlanV1.kt`
- `PLAN/W6bFilterGraphConstruction.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `GPU/planning/W6aLayerGraphLowerer.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUW6aEncoderScopesV1.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `GPU/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- `TEST/picture/W6bFilterPictureTest.kt`

**Interfaces:**

- Consumes: Task 2 `SeparableBlur` operation and frozen filter target IDs/rectangles; existing W5 material source and W6a origin mapping.
- Produces: `IMAGE_BLUR_X`/`IMAGE_BLUR_Y` passes with exact tile mode and a native mapping keyed only by `FilterImplementationKindV1`. Tasks 4 and 6 reuse the same blur passes for coverage/shadow inputs.

- [ ] **Step 1: Add RED cases for impulse blur, all tile modes, nonzero origin, and Picture replay; compute expected arrays before `Surface`.**

```kotlin
@Test
fun imageBlurTileModesAreDistinctAtTheSourceEdge() {
    val clamp = renderEdge(TileMode.CLAMP)
    val decal = renderEdge(TileMode.DECAL)
    ImageBlurCpuOracle.assertNear(ImageBlurCpuOracle.edge(TileMode.CLAMP), clamp)
    ImageBlurCpuOracle.assertNear(ImageBlurCpuOracle.edge(TileMode.DECAL), decal)
    assertTrue(clamp[edgeAlphaOffset] > decal[edgeAlphaOffset])
}
```

- [ ] **Step 2: Run causal REDs against the Task 2 authority; confirm they fail because no positive blur operation is planned/materialized.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'`

Expected: FAIL with a W6b image-blur capability refusal.

- [ ] **Step 3: Plan exactly two typed passes and ping-pong targets after checked bounds propagation; do not use a renderer-local blur planner.**

```kotlin
passes += FilterPass(nextPass(), listOf(source), horizontal, key,
    FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.IMAGE_BLUR_X, sigmaXF32, FilterAxisV1.X, tileMode, bounds))
passes += FilterPass(nextPass(), listOf(horizontal), vertical, key,
    FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.IMAGE_BLUR_Y, sigmaYF32, FilterAxisV1.Y, tileMode, bounds))
```

- [ ] **Step 4: Materialize the named target views/uniforms and encode only frozen X/Y operands; map all four tile modes in WGSL without a default clamp branch.**

```kotlin
when (operation.tileMode) {
    TileMode.CLAMP -> "clamp"
    TileMode.REPEAT -> "repeat"
    TileMode.MIRROR -> "mirror"
    TileMode.DECAL -> "decal"
}
```

- [ ] **Step 5: Bind blur output into the existing W6a restore/auto-layer composite exactly once and retain every target lease through completion or quarantine.**

- [ ] **Step 6: Add memory/wire replay to Task 1's Picture shard and rerun the public pixels after post-capture mutation of source filter fields/arrays where applicable.**

- [ ] **Step 7: Run serial compilation and targeted public tests.**

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest'
```

- [ ] **Step 8: Refactor duplicate X/Y target construction behind the typed `FilterAxisV1` contract only; rerun Task 3 selectors.**

- [ ] **Step 9: Commit Task 3.**

```sh
git add gpu-plan gpu-renderer kanvas
git commit -m "feat(gpu): execute w6b image blur passes"
```

- [ ] **Step 10: Request Sol review of the Task 3 commit. If needed, make one bounded correction commit and one scoped Sol re-review.**

## Task 4: Mask Blur Styles and Public Draw Auto-Layers

**Agent:** Terra implementation; Sol review.

**Outcome:** Filtered draws receive a W6b auto-layer and `MaskFilter.Blur` executes `NORMAL`, `SOLID`, `OUTER`, and `INNER` over raw coverage before W5 shading, without double-applying paint effects or disturbing explicit W6a layer restore order.

**Files — Create:**

- `PLAN/W6bAutoLayerPlanV1.kt`
- `GPU/wgsl/W6bMaskCoverageSnippet.kt`
- `TEST/surface/W6bMaskBlurAutoLayerSurfacePixelTest.kt`
- `TEST/surface/W6bMaskBlurCpuOracle.kt`

**Files — Modify:**

- `PLAN/W6bFilterPlanV1.kt`
- `PLAN/W6bFilterGraphConstruction.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `TEST/surface/W6aLayerSurfacePixelTest.kt`

**Interfaces:**

- Consumes: Task 3 frozen separable blur, existing W4 raw coverage, existing W5 material table, and selected `BlendPlan`.
- Produces: `AutoLayerPlanV1` and `MASK_COVERAGE_BLUR_X/Y` typed pass payloads. Task 5 adds shader/table coverage arms and Task 6 can use auto-layer source output for drop shadows.

- [ ] **Step 1: Add public RED tests for all four blur styles, a translated filtered draw, an explicit layer containing an auto-layer, and “blend once” over a colored destination.**

```kotlin
@Test
fun maskBlurStylesTransformCoverageBeforeMaterialShading() {
    BlurStyle.entries.forEach { style ->
        val actual = renderMaskedRect(style)
        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.render(style), actual)
    }
}
```

- [ ] **Step 2: Run the mask-auto-layer shard against unchanged production and record a behavioral RED on W6a's spatial-filter refusal.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest'`

Expected: FAIL with `w6a.layer.unsupported_spatial_filter` or equivalent pre-W6b refusal.

- [ ] **Step 3: Partition each filtered draw into raw coverage, mask transform, W5 source shading, optional image output, clip, and one selected final blend.**

```kotlin
internal class AutoLayerPlanV1(
    val coverageSource: PlanResourceId,
    val filteredCoverage: PlanResourceId,
    val shadedSource: PlanResourceId,
    val finalBlend: BlendPlan,
)
```

- [ ] **Step 4: Plan mask blur as typed coverage X/Y passes and style formulas, retaining original coverage as an explicit input for `SOLID`, `OUTER`, and `INNER`.**

```kotlin
val styleOutput = when (style) {
    BlurStyle.NORMAL -> blurred
    BlurStyle.SOLID -> maxCoverage(original, blurred)
    BlurStyle.OUTER -> maxCoverage(0f, blurred - original)
    BlurStyle.INNER -> minCoverage(original, blurred)
}
```

- [ ] **Step 5: Lower/materialize frozen coverage targets and apply the already selected W5 source/blend once; do not call `GPUTopLevelMaskBlurFrameRecording` or `GPUPreparedMaskFilterLowerer` for W6b.**

- [ ] **Step 6: Preserve explicit W6a layers as the immediate parent target for auto-layer composites and prove parent-before/child/parent-after order through public pixels.**

- [ ] **Step 7: Run targeted gates serially.**

```sh
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'
```

- [ ] **Step 8: Refactor only common auto-layer target wiring; keep paint-consumption sequencing explicit. Rerun Task 4 tests.**

- [ ] **Step 9: Commit Task 4.**

```sh
git add gpu-plan gpu-renderer kanvas
git commit -m "feat(gpu): execute w6b mask blur auto-layers"
```

- [ ] **Step 10: Request Sol review of the Task 4 commit. If needed, make one bounded correction commit and one scoped Sol re-review.**

## Task 5: Mask Shader and Table Coverage

**Agent:** Terra implementation; Sol review.

**Outcome:** `MaskFilter.Shader` uses the frozen W5 material alpha in the captured mapping and `MaskFilter.Table` uses an immutable 256-entry coverage LUT; malformed historical table lengths refuse atomically with the stable diagnostic and recover on the same Surface.

**Files — Create:**

- `TEST/surface/W6bMaskShaderTableSurfacePixelTest.kt`
- `RES/format-13-invalid-mask-table-length.base64`

**Files — Modify:**

- `PLAN/W6bFilterPlanV1.kt`
- `PLAN/W6bFilterGraphConstruction.kt`
- `PLAN/W6aLayerPlanCompiler.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `PLAN/MaterialSourceConstructionV4.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `GPU/wgsl/W6bMaskCoverageSnippet.kt`
- `TEST/picture/W6bFilterPictureTest.kt`

**Interfaces:**

- Consumes: Task 4 auto-layer coverage source, W5 `MaterialPlanTable`/`MaterialPlanRef` coordinates, `ImmutableUBytes`, and Task 1 historical Picture decoding.
- Produces: typed `MASK_SHADER` and `MASK_TABLE` `FilterPassOperationV1` arms. No task after this one may inspect public masks in the renderer.

- [ ] **Step 1: Add public RED tests for a gradient mask shader, an invert-like 256-entry table, post-capture table mutation, and the historical invalid-length recovery case.**

```kotlin
@Test
fun historicalInvalidTableRefusesAtomicallyAndSameSurfaceRecovers() {
    val surface = Surface(2, 2)
    val picture = assertNotNull(Picture.fromByteArray(fixture("format-13-invalid-mask-table-length.base64")))
    surface.canvas { picture.playback(this) }
    assertTerminalWithoutReadbackMutation(surface, "invalid.mask_filter.table_length")
    surface.discardRecordedOperations()
    surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
    assertContentEquals(rgba(17, 61, 211), surface.render().pixels)
}
```

- [ ] **Step 2: Run the shard against unchanged production and confirm REDs arise from missing W6b mask arms, while the historical fixture decodes before render.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'`

- [ ] **Step 3: Validate table length only at W6b admission and seal a copy of 256 entries into a planned uniform/storage resource and physical allocation budget.**

```kotlin
if (table.size != 256) {
    return W6bFilterDiagnostics.refusal("invalid.mask_filter.table_length", "Mask table must contain 256 entries.")
}
```

- [ ] **Step 4: Bind `MaskFilter.Shader` to its existing W5 material-table entry and map coordinates with the sealed auto-layer mapping; do not create a `Shader` compiler in `:gpu-plan` or renderer.**

```kotlin
FilterPassOperationV1.MaskShader(
    material = requireNotNull(materialTable.referenceFor(maskMaterial)),
    mapping = evaluationKey.mapping,
    bounds = bounds,
)
```

- [ ] **Step 5: Materialize only frozen LUT/material resources and multiply coverage by shader alpha before the existing source blend.**

```wgsl
let tableAlpha = f32(mask_table[u32(round(clamp(coverage, 0.0, 1.0) * 255.0))]) / 255.0;
let maskedCoverage = coverage * tableAlpha;
```

- [ ] **Step 6: Add Picture memory/wire replay for shader/table filters and a mutation-after-recording control; preserve W5 material generation/lease ownership.**

- [ ] **Step 7: Run all required Task 5 gates serially.**

```sh
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'
```

- [ ] **Step 8: Refactor common coverage-source binding without changing table admission or W5 ownership; rerun Task 5 selectors.**

- [ ] **Step 9: Commit Task 5.**

```sh
git add gpu-plan gpu-renderer kanvas
git commit -m "feat(gpu): execute w6b shader and table masks"
```

- [ ] **Step 10: Request Sol review of the Task 5 commit. If needed, make one bounded correction commit and one scoped Sol re-review.**

## Task 6: Drop Shadow Modes, Atomic Budget, and W6b Convergence

**Agent:** Terra implementation; Sol review.

**Outcome:** Drop shadows execute through the frozen blur/filter graph in both public modes, auto-layer/layer bounds expand correctly, B/B−1 and late refusal are atomic, Picture replay is stable, and the W6b Draft PR is qualified against W6a without touching excluded suites.

**Files — Create:**

- `TEST/surface/W6bDropShadowSurfacePixelTest.kt`
- `TEST/surface/W6bBudgetRecoverySurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/W6bFilterPlanV1.kt`
- `PLAN/W6bFilterGraphConstruction.kt`
- `PLAN/W6aLayerGraphConstruction.kt`
- `PLAN/W6aLayerPlanBudget.kt`
- `PLAN/RenderGraph.kt`
- `GPU/recording/GPUW6aLayerFramePlan.kt`
- `GPU/execution/GPUW6aEncoderScopesV1.kt`
- `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `TEST/picture/W6bFilterPictureTest.kt`
- `refactor/README.md`
- `refactor/waves/W06-layers-effects/status.md`

**Interfaces:**

- Consumes: Tasks 1–5 node table, X/Y filter operation, auto-layer source, sealed W6a restore/composite/budget lifecycle.
- Produces: `DROP_SHADOW_COLORIZE` and `DROP_SHADOW_COMPOSITE` operations; public pixel/recovery proof and the documented W6b checkpoint. W6c consumes the same table, evaluation key, `FilterPass`, and `FilterTarget` contracts unchanged.

- [ ] **Step 1: Add independent-oracle RED tests for `COMPOSITE`, `SHADOW_ONLY`, translated/expanded bounds, nested explicit layer ordering, memory/wire replay, B/B−1, and late sibling recovery.**

```kotlin
@Test
fun shadowOnlyOmitsSourceAndExpandsBounds() {
    val shadow = ImageFilter.DropShadow(2f, 1f, 1f, 1f, ColorARGB.of(255, 17, 61, 211), mode = DropShadowMode.SHADOW_ONLY)
    val actual = renderFilteredImpulse(shadow)
    W6bDropShadowCpuOracle.assertNear(W6bDropShadowCpuOracle.shadowOnly(), actual)
    assertEquals(0u, actual[originalSourceAlphaOffset])
}
```

- [ ] **Step 2: Run the new tests against unchanged Task 5 production and record REDs caused by unsupported drop shadow, not by a native exit.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bDropShadowSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bBudgetRecoverySurfacePixelTest'
```

- [ ] **Step 3: Plan shadow source binding, colorization, X/Y blur, offset, and mode-specific composition as typed passes; calculate required and produced bounds in F64 before checked I32 sealing.**

```kotlin
val shadowOutput = appendShadowColorize(blurredCoverage, shadowColor, dxF32, dyF32, bounds)
if (mode == DropShadowMode.COMPOSITE) appendShadowComposite(shadowOutput, originalSource, output, bounds)
else shadowOutput
```

- [ ] **Step 4: Materialize only the frozen shadow passes and bindings; `SHADOW_ONLY` has no original-source composite operand. Preserve one final parent blend from the auto-layer/layer plan.**

- [ ] **Step 5: Charge every filter target and shadow intermediate in semantic and physical peaks; derive public B from fixture dimensions/formula, assert B accepts and B−1 returns `w6b.filter.frame_budget_exceeded` before native allocation.**

```kotlin
val physicalPeak = slots.fold(0L) { total, slot -> Math.addExact(total, slot.reservedBytesI64) }
val peak = maxOf(semanticPeak, physicalPeak)
```

- [ ] **Step 6: Prove a late W6b sibling refusal leaves sentinel bytes untouched and same-Surface discard/re-record succeeds; do not use a fake device or internal allocation counter.**

- [ ] **Step 7: Run W6b public shards, W6a preservation, and touched-module compilation sequentially; record XML/Gradle/native custody separately.**

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bDropShadowSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'
```

- [ ] **Step 8: Audit the W6b production path for `GPUSeparableBlurRectFrameRecorder`, `GPUTopLevelMaskBlurFrameRecording`, `GPUPreparedFilterDAGPlanner`, `GPUPreparedMaskFilterLowerer`, `GPUDropShadow`, renderer-local `.toInt()`, and post-freeze pass/resource creation. Classify legitimate legacy/W8 references in the status document; do not add a source-shape test.**

- [ ] **Step 9: Update the durable W06 status and README with achieved W6b cells, commits/gates, exact exclusions (backdrop, filtered previous, F16/HDR, W6c/W6d families, device-loss visibility), native `UNKNOWN` where applicable, retained legacy/W8 paths, and the W6c handoff. Do not claim ISO/global Skia convergence.**

- [ ] **Step 10: Commit Task 6.**

```sh
git add gpu-plan gpu-renderer kanvas refactor
git commit -m "feat(gpu): close w6b blur masks and shadows"
```

- [ ] **Step 11: Request Sol review of Task 6. If needed, make one bounded correction commit and one scoped Sol re-review.**

- [ ] **Step 12: Request a whole-branch Sol review against `1ff67ec84..HEAD`, covering capture/wire compatibility, table identity, numeric bounds, plan-only authority, W5 ownership, physical/semantic budgets, native ordering, public recovery, and legacy fallback risk. If it reports Critical/Important findings, run exactly one bounded Terra correction wave, rerun only causally affected public selectors plus W6a preservation, commit once, and request one scoped Sol re-review.**

- [ ] **Step 13: Verify clean status and exact stack base, push `codex/w6b-blur-masks-shadows`, then create/update one Draft PR targeting `codex/w6a-layer-authority`. Include scope, architecture, Task 1–6 commits, exact gate custody, exclusions, native status, and W6c handoff. Do not merge.**

## W6b Definition of Done

- [ ] Picture 14/schema 8 writes the immutable filter table, roots, and `DropShadowMode`; v8–13 readers remain public-compatible and default omitted shadow mode to `COMPOSITE`.
- [ ] Explicitly shared filter identity remains shared; equal-but-distinct recursive filters and historical recursive occurrences do not alias.
- [ ] W6b is the single plan-first owner for admitted blur/mask/shadow layer and auto-layer scenes; no admitted path replans or falls back after freeze.
- [ ] Image blur executes typed X/Y passes for `CLAMP`, `REPEAT`, `MIRROR`, and `DECAL`, with F64 bounds and checked target-local I32 rectangles.
- [ ] Mask blur styles, shader coverage, and 256-entry table coverage execute before source shading; malformed historical tables refuse with `invalid.mask_filter.table_length` and recover publicly.
- [ ] `DropShadowMode.COMPOSITE` and `SHADOW_ONLY` have distinct, publicly proven source/composite behavior and correct expanded bounds.
- [ ] Filter targets, passes, IDs, source bindings/generations, slots, semantic/physical lifetimes, pessimistic B/B−1 budget, leases, and diagnostics are frozen in `:gpu-plan` before native preparation.
- [ ] Any W6b-owned refusal leaves no partial readback and same-Surface discard/re-record recovery succeeds.
- [ ] Backdrop, filtered previous, F16/HDR, W6c/W6d families, fonts, codecs, GMs, dashboard/renders/baselines/scores, global Skia, `jpg-color-cube`, and device-loss proof remain explicit exclusions.
- [ ] Sol task reviews and the whole-branch review leave no Critical/Important finding; only one bounded correction wave is used for the PR.
- [ ] One unmerged Draft PR is stacked on W6a and documents its exact gates and W6c handoff.

## Plan Self-Review

- **Spec coverage:** Task 1 owns immutable capture, stable input references, Picture 14/schema 8, historical readers, and `DropShadowMode`; Task 2 owns the one W6 physical filter contract and terminal admission; Tasks 3–5 own blur and all three mask-filter families; Task 6 owns both shadow modes, B/B−1, atomic recovery, custody, review, and the stacked Draft PR.
- **Placeholder scan:** no deferred-work marker or unspecified test step remains; every implementation step names concrete files, public tests, commands, interfaces, and a commit.
- **Type consistency:** every task uses `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `CapturedFilterTableV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `FilterPassOperationV1`, one `FilterPass`, and one `FilterTarget` role. Picture/backdrop input forms are reserved without creating a W6d execution path.
- **Review focus:** the five listed risks are pinned respectively in Tasks 3, 4, 1, 5, and 6 through public Surface/Picture behavior.

## Execution Handoff

Execute this plan task-by-task with `superpowers:subagent-driven-development`: Terra implements Tasks 1–6, Sol reviews each task and the completed branch. Do not start W6c until Task 6 has either closed W6b or recorded a concrete blocker.
