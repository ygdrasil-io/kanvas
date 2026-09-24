# W6d Filter-Owned Picture Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute `ImageFilter.Picture` from its immutable captured scene in the one frozen W6 graph, with public pixels, memory/wire replay, atomic refusal and resource custody.

**Architecture:** Extend the existing Picture stream aggregate with a typed filter owner and a source-only terminal. Its existing `PictureAggregateSource` is populated and sealed before one `FilterPassOperationV1.Picture` samples it into a `FilterTarget`; the renderer only encodes frozen passes. This plan replaces Task 4 of `refactor/plans/2026-09-22-w6d-advanced-effects-implementation-plan.md` and leaves Tasks 5–7 in that plan intact.

**Tech Stack:** Kotlin/JVM, `:math`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit 5, public `Surface`/`Picture`/`RenderResult`.

**Spec:** `refactor/specs/2026-09-24-w6d-picture-filter-source-design.md`, plus the W6/W6b/W6d specs named by the parent plan.

## Global Constraints

- Use the reviewed `codex/w6d-advanced-effects` HEAD after Task 33c; remain stacked on `codex/w6c-spatial-dag`. Do not create or merge a PR until parent-plan Task 7.
- This is the **only** W6d exception to the parent plan's FilterPass/FilterTarget-only physical clause: reuse existing `PictureAggregateBeginPass`, `PictureSourcePass`, `PictureAggregateSealPass` and `PictureAggregateSource`. Create no new `PlanPass` subtype, resource role, frame graph, allocator, cache, submit path or renderer-side plan.
- Keep the draw `GeometryNode.Picture` aggregate and validators unchanged in meaning. The filter owner has no invented draw, command index, parent paint, W5 graph-texture consumer or composite terminal.
- `FilterPass.Picture` alone may read its sealed `PictureAggregateSource`; it writes one RGBA8 `FilterTarget`. Its only input is that source, never the draw/layer `ImplicitSource`.
- All scene discovery, nested W4/W5/W6c work, bounds, IDs, generations, programs, usages, slots, lifetimes and checked-I64 budget freeze before native preparation. Renderer replay of `SceneSnapshot` is forbidden.
- `src` is a crop in Picture coordinates without rescale or rebase; absent `src` uses cull. Empty intersection is transparent black without a zero-sized texture. Demand propagates before terminal clips; an outer filter may produce pixels beyond the Picture cull.
- New geometry lives only in `:math`, with precision in I/F32/64 names; no renderer-local rectangles or unchecked `.toInt()` bounds.
- RED must reach a semantic public assertion with unchanged production; missing selector, compile error, timeout or native 133/134 is not RED/GREEN evidence. Each positive checks public pixels and `nativeEvidenceScopeKinds` containing `Render` and `Readback`.
- Tests use only public `Surface`, `Picture`, canvas/paint/filter APIs, `render()`, `readPixels`, diagnostics and `discardRecordedOperations()`. No infrastructure/static-source/private/reflection/mock/fake-device tests, GMs, fonts, codecs, dashboard, renders or scores.
- Each Task 4a–4c uses a fresh Terra implementer and a Sol reviewer. Resolve Critical/Important findings before the next slice; run Gradle selectors sequentially, record command exit, XML methods/F/E/S, source commit and native 133/134 as `UNKNOWN` separately.
- Baseline before Task 4: `:gpu-plan:compileKotlin` succeeded. The existing full `W6aLayerPictureTest` class has 9 XML methods, 1 pre-existing stale writer-version assertion (`expected 14`, current writer `15`) and a native worker exit 133; do not misreport that class as GREEN or as a Task 4 regression. Use its named public drawPicture pixel/wire methods below for preservation, and carry the known class failure to the W6d closure report.

## Review Focus

- `filterPictureIgnoresCarrierPixelsAndSamplesOnlySealedScene`: leaf Picture must replace, not blend with, the carrier source.
- `pictureFilterNestedSceneKeepsSiblingAndDstOutOrder`: source-only aggregate must not inherit the drawPicture terminal composite or reorder descendants.
- `pictureFilterSrcIsCropWithoutRescaleAndEmptyRemainsTransparent`: translated crop, empty crop and outer alpha-producing wrapper must retain correct demand.
- `equalPictureFiltersRemainDistinctAcrossWireReplay`: two equal captured values must not alias by canonical content, while a shared captured node keeps one reference.
- `latePictureChildRefusalIsAtomicAndSameSurfaceRecovers`: no prefix publication or partial readback after a later nested failure.

---

## File map

| Owner | Responsibility in Task 4 |
| --- | --- |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PictureStreamAggregateV1.kt` | Typed `DrawPicture`/`FilterPicture` owner, source-only discovery and owner-specific aggregate validation. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt` | Emit filter-owned Begin → ordered entries → Seal in the existing frame queue and share its ID/resource allocators. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bFilterGraphConstruction.kt` | Traverse filter-held scenes; bind the `CapturedFilterNodeV1.Picture` leaf to its sealed source, not `CapturedFilterInputV1.Picture(id)`. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bFilterPlanV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphValidation.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bFilterGraphWitnessV1.kt` | Frozen Picture operand, precise source-role exception, generation/producer/lifetime witness. |
| `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W6aLayerGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt` | Bind existing source texture and Picture crop sampling exactly as frozen; no scene replay. |
| `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt` | Inspect existing deep snapshot and Picture 15/schema 9 codec first; change only for a demonstrated capture/wire defect. |
| `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dPictureFilterSurfacePixelTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6dPictureRuntimeEffectPictureTest.kt` | Public pixels, diagnostics, memory/wire replay and mutation witnesses. |

## Shared typed boundary

The implementation may refine names to match existing declarations, but must keep these exact facts in one immutable plan-owned value before `RenderGraph.construct`:

```kotlin
internal sealed interface PictureAggregateOwnerV1 {
    class DrawPicture(
        val occurrence: FilterOccurrenceSourceV1,
        val draw: DrawNode,
        val plannedCommandId: FramePlannedCommandIdI32,
        val pictureOccurrenceIdI32: Int,
    ) : PictureAggregateOwnerV1
    class FilterPicture(
        val filterNodeId: CapturedFilterNodeIdI32,
        val filterOccurrenceIdI32: Int,
        val sourceSceneCanonicalId: String,
        val picturePathI32: List<Int>,
    ) : PictureAggregateOwnerV1
}

internal sealed interface PictureAggregateDraftOwnerV1 {
    class DrawPicture(
        val occurrence: FilterOccurrenceSourceV1,
        val draw: DrawNode,
        val plannedCommandId: FramePlannedCommandIdI32,
        val filterOccurrence: W6bFilterGraphConstruction.PositiveOccurrence?,
    ) : PictureAggregateDraftOwnerV1
    class FilterPicture(
        val nodeId: CapturedFilterNodeIdI32,
        val node: CapturedFilterNodeV1.Picture,
        val occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
        val sourceContext: W6bFilterGraphConstruction.SourceBinding,
        val picturePathI32: List<Int>,
    ) : PictureAggregateDraftOwnerV1
}

internal class W6FramePassSinkV1(
    private val passes: MutableList<PlanPass>,
    private val cursor: W6bFilterGraphConstruction.FreezeCursor,
) {
    fun append(build: (Int) -> PlanPass): PlanPass {
        val ordinal = passes.size
        val pass = build(ordinal)
        require(pass.ordinal == ordinal)
        passes += pass
        cursor.passOrdinalI32 = passes.size
        return pass
    }
}

public class SealedPictureFilterSourceV1(
    public val aggregateId: PictureStreamAggregateIdI32,
    public val resourceId: PlanResourceId,
    public val sourceGenerationI64: Long,
    public val sampling: FilterInputSamplingV1,
) {
    public fun copy(): SealedPictureFilterSourceV1 = SealedPictureFilterSourceV1(
        aggregateId, resourceId, sourceGenerationI64, sampling,
    )
}
```

The draft receives `PictureAggregateDraftOwnerV1` and the frozen aggregate receives `PictureAggregateOwnerV1`; both are immutable at their boundary. Move draft `draw`, `source: FilterOccurrenceSourceV1`, `sourcePlannedCommandId` and draw-only clip/transform facts into `DraftOwner.DrawPicture`; do not leave nullable placeholders on the common draft. Its common fields are the actual `sourceScene`, aggregate ID, aggregate-local Picture occurrence ID, entry IDs/entries and frame-owned counters. `DraftOwner.FilterPicture` obtains scene/cull/src, mapping, desired domain and filter table from the real node/occurrence/source context; `pictureAggregateDomain` branches by draft owner, using `sourceContext.mapping` and the node cull for the filter branch, never `aggregate.draw`. The frozen filter owner carries only IDs/path/canonical provenance, no executable `SceneSnapshot`; it is tied to the actual `PositiveOccurrence.table` by occurrence ID plus node ID, not by canonical equality. `FilterPassOperationV1.Picture` snapshots the sealed-source operand, cull, optional `src`, and output bounds. It must not expose a `SceneSnapshot` to native lowering. The graph records exactly one matching `PictureAggregateSealPass` before the consuming `FilterPass`, and the Picture aggregate validator retains the existing draw-owner branch verbatim.

`W6FramePassSinkV1` is a planner-local façade over W6a's **existing** frame pass list and W6b `FreezeCursor`, not a second graph or queue. `freezeImageOccurrence` takes this sink and an `emitFilterPictureSource(nodeId, node, occurrence, sourceContext)` callback, appends each filter pass through the sink, and returns resource specs plus output/key but no buffered pass list. W6a's callback emits Begin, descendants and Seal through that **same** sink; nested `appendFrozenOccurrence` reuses it. W6a appends the W6b resource specs once on return but does not append passes a second time. All direct W6a `passes +=` on this reentrant source path are routed through the sink, so one `passes.size` determines each ordinal and the cursor cannot lag behind nested work. If planning fails, the unpublished frame builder is discarded atomically.

### Task 4a: Flat Filter-Owned Picture Source, End to End

**Agent:** Fresh Terra implementation, then fresh Sol task review.

**Outcome:** A flat captured Picture with one opaque draw replaces the carrier's pixels through Begin → draw → Seal → `FilterPass.Picture`, with a public Render+Readback witness. Nested Picture/layer/filter content may remain an explicit terminal refusal until Task 4b, never an implicit fallback.

**Files:** Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dPictureFilterSurfacePixelTest.kt`; modify `PictureStreamAggregateV1.kt`, `W6aLayerGraphConstruction.kt`, `W6bFilterGraphConstruction.kt`, `W6bFilterPlanV1.kt`, `RenderGraph.kt`, `W6aLayerGraphValidation.kt`, `W6bFilterGraphWitnessV1.kt`, `W6aLayerGraphLowerer.kt`, `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt` at the paths in File map.

**Interfaces:** Consumes `CapturedFilterNodeV1.Picture(scene,cull,src)`, W6b `SourceBinding`, `FilterEvaluationKeyV1`, `PictureStreamAggregateV1`, existing Picture passes and `FilterTarget`. Produces `PictureAggregateOwnerV1.FilterPicture` and `SealedPictureFilterSourceV1` consumed by Task 4b/4c, plus a frozen `FilterPassOperationV1.Picture` with one sealed source input.

- [ ] **Step 1: Add one public causal RED.** In `W6dPictureFilterSurfacePixelTest`, create this exact leaf fixture; the expected red pixel is fixed before either recorder or surface, so the blue carrier cannot satisfy the assertion. Import `PictureRecorder`, `ImageFilter`, `Paint`, `ColorARGB`, `RectF32`, `assertContentEquals`, `assertTrue` and `Test` as in the adjacent public test classes.

```kotlin
@Test fun filterPictureIgnoresCarrierPixelsAndSamplesOnlySealedScene() {
    val expected = ubyteArrayOf(255u, 0u, 0u, 255u)
    val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    val recorder = PictureRecorder()
    recorder.beginRecording(rect).drawRect(rect, Paint(ColorARGB.Red, antiAlias = false))
    val picture = recorder.finishRecordingAsPicture()
    val surface = Surface(1, 1)
    surface.canvas {
        drawRect(rect, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(picture), antiAlias = false))
    }
    val result = surface.render()
    assertContentEquals(expected, result.pixels)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}
```
- [ ] **Step 2: Run the RED selector.** `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest.filterPictureIgnoresCarrierPixelsAndSamplesOnlySealedScene'`. Require a compiled test reaching the wrong pixel or existing W6d terminal Picture diagnostic; if it fails to compile or says “No tests found”, repair the fixture before proceeding. Record XML and native exit separately.
- [ ] **Step 3: Add the owner and source-only lifecycle.** Introduce both typed owner stages from Shared typed boundary in `PictureStreamAggregateV1.kt`. Move draft draw-only fields into `DraftOwner.DrawPicture`; let `DraftOwner.FilterPicture` carry its real captured node, occurrence and source context. In `pictureAggregateDomain`, retain the existing draw branch and use the filter node's captured cull plus `sourceContext.mapping`/demand for the filter branch. Give discovery an entry from the real filter-table node/occurrence, sharing the frame's aggregate/command/resource counters but allocating no public draw command ID for the filter owner. Emit existing Begin, entry draw, Seal into the same ordered pass list. Branch owner-specific terminal validation as follows; all current draw-owner checks stay in the draw branch. Do not synthesize `DrawNode` or call `Picture.playback`.

```kotlin
when (aggregate.owner) {
    is PictureAggregateOwnerV1.DrawPicture -> validateDrawPictureTerminal(aggregate, passes)
    is PictureAggregateOwnerV1.FilterPicture -> {
        require(aggregate.executionMode == PictureStreamExecutionModeV1.ISOLATED_SOURCE)
        require(aggregate.terminalPassId == aggregate.sealPassId)
        require(aggregate.rootSourceCommandIndexI32 == null)
        require(aggregate.terminalCompositeScissorAuthority == null)
    }
}
```

`validateDrawPictureTerminal` denotes extraction of the **unchanged** existing draw-terminal checks from `PictureStreamAggregateV1.kt`, not a second validation algorithm.
- [ ] **Step 4: Bind the Picture leaf to the sealed generation.** Refactor `freezeImageOccurrence` to accept the shared `W6FramePassSinkV1` and `emitFilterPictureSource` callback described above; its existing `append` delegates to `sink.append`, so earlier filter passes are already in frame order when a Picture leaf emits Begin/children/Seal. In `W6bFilterGraphConstruction.materializeNode`, request the filter-owned aggregate through that callback; create the operand only after its seal has a resource ID and generation. Here `mappedCull`, `mappedSourceRect` and `pictureBounds` are the once-mapped, checked F64/I32 values from this source context:

```kotlin
val operation = FilterPassOperationV1.Picture(
    sealedSource = sealed,
    cullRectF64 = mappedCull,
    sourceRectF64 = mappedSourceRect,
    bounds = pictureBounds,
)
sink.append { ordinal -> PlanPass.FilterPass(
    ordinal, listOf(sealed.resourceId), outputFilterTarget, evaluationKey, operation,
) }
```

Keep `bindInput(CapturedFilterInputV1.Picture(id))` refused. The operation snapshots mapping/crop/output bounds before publication; `RenderGraph.construct` admits `PictureAggregateSource` only for this arm, and `W6aLayerGraphValidation` plus `W6bFilterGraphWitnessV1` prove matching owner, seal, generation, order and lifetime.
- [ ] **Step 5: Materialize the already-frozen operation.** Lower the Picture arm to its existing typed FilterPass payload; bind the sealed texture and source-to-target sampling/crop uniforms in `GPUWgpu4kW6aLayerFramePayloadMaterializer`. The payload must carry this exact resource/generation pair. In the plan validator, after resolving `pass` and its unique matching `seal`, assert the following; the renderer must not inspect the captured scene:

```kotlin
require(pass.inputs == listOf(operation.copySealedSource().resourceId))
require(seal.sealedSource == operation.copySealedSource().resourceId)
require(seal.sourceGenerationI64 == operation.copySealedSource().sourceGenerationI64)
```

An unsupported nested command remains a specific W6 terminal refusal before publication. Do not insert a render pass, resize a target or choose a program after freeze.
- [ ] **Step 6: Run GREEN and drawPicture preservation, sequentially.** `rtk ./gradlew :gpu-plan:compileKotlin`; `rtk ./gradlew :gpu-renderer:compileKotlin`; `rtk ./gradlew :kanvas:compileTestKotlin`; the Step 2 selector; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.translatedPictureLayerKeepsNonzeroOrigin'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.drawPictureInsidePreviousLayerPreservesHostClip'`. Require public pixel and Render+Readback, not only a Gradle exit. Record XML method/F/E/S and native status; retain the pre-existing full-class failure in custody.
- [ ] **Step 7: Commit and Sol review.** Commit as `feat(gpu): execute flat w6d picture filter source`. Reviewer checks no fake draw identity, one graph/allocator, source-only seal, precise role exception, actual renderer pixels and unchanged drawPicture validators. Resolve Critical/Important before 4b.

### Task 4b: Nested Scene, Crop, Empty and Wrapper Semantics

**Agent:** Fresh Terra implementation, then fresh Sol task review.

**Outcome:** Filter-held scenes use existing recursive W4/W5/W6c discovery and source contexts; crop/empty and outer demand work without clipping valid output or producing zero-sized targets.

**Files:** Modify `PictureStreamAggregateV1.kt`, `W6aLayerGraphConstruction.kt`, `W6bFilterGraphConstruction.kt`, `W6bFilterPlanV1.kt`, `W6aLayerGraphValidation.kt`, `W6bFilterGraphWitnessV1.kt`, `W6aLayerGraphLowerer.kt`, `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `W6dPictureFilterSurfacePixelTest.kt`; modify `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/AdvancedFilterBoundsF64.kt` only if current intersection/projection helpers cannot express the domain without duplicating geometry.

**Interfaces:** Consumes Task 4a owner and sealed-source operand. Produces bounded recursive filter-scene discovery, F64 crop/demand and transparent-black leaf behavior consumed by Task 4c and parent-plan Task 7.

- [ ] **Step 1: Add public RED fixtures with independent byte oracles.** Add `pictureFilterNestedSceneKeepsSiblingAndDstOutOrder`: a 2×1 source Picture clears to red, draws green in the second texel and erases that whole texel with `DST_OUT`, then is used as the filter on a blue 2×1 carrier; assert first red and second transparent, plus Render+Readback. Add `pictureFilterNestedPictureLayerAndDrawColorKeepOrder`: a nested Picture containing a 1×1 blue child, a `drawColor` sibling and a saveLayer child; assert their recorded order from a separately written expected array. Add `pictureFilterReentrantPassOrderKeepsEarlierAndInnerFilters`: a Compose/ColorFilter pass before the Picture leaf, whose scene itself has a filtered child; assert independently expected pixels so wrong ordinal/interleaving fails publicly. Add `pictureFilterSrcIsCropWithoutRescaleAndEmptyRemainsTransparent`: a 3×1 RGB Picture with `src=[1,0,2,1]` must leave only the green texel at x=1, not move it to x=0 or stretch it; `src=[3,0,4,1]` returns transparent. Add `emptyPictureStillRunsComposeColorFilterAndSrcComposite`: a Compose/ColorFilter wrapper that creates nonzero alpha from transparent input and a `SRC` composite over opaque green; assert those independently computed pixels. Add `transformedPictureCropKeepsBlurHaloOutsideCull`: a translated/scaled crop and blur wrapper whose halo extends beyond Picture cull. Each expected array precedes `PictureRecorder`/`Surface` creation. For the 3×1 crop, the exact leaf oracle is:

```kotlin
val croppedExpected = ubyteArrayOf(
    0u, 0u, 0u, 0u,
    0u, 255u, 0u, 255u,
    0u, 0u, 0u, 0u,
)
val emptyExpected = UByteArray(3 * 4)
val src = RectF32.ofLTRB(1f, 0f, 2f, 1f)
val emptySrc = RectF32.ofLTRB(3f, 0f, 4f, 1f)
```
- [ ] **Step 2: Run RED selectors one at a time.** Run `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest.pictureFilterNestedSceneKeepsSiblingAndDstOutOrder'`, then `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest.pictureFilterReentrantPassOrderKeepsEarlierAndInnerFilters'`, then `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest.pictureFilterSrcIsCropWithoutRescaleAndEmptyRemainsTransparent'`. Count only semantic pixel/diagnostic failures as causal RED; the other named fixtures must each be run by the class selector in Step 5 even if they are already correct.
- [ ] **Step 3: Reuse recursive discovery and source contexts.** Extend W6b `visitScenes`/positive occurrence traversal to descend into `CapturedFilterNodeV1.Picture.scene` using the actual filter node ID and path; add active-path object-identity cycle detection to the existing bounded traversal, never canonical-ID equality. Carry W4/W5 lanes, nested Picture and layer scopes through the same source-only aggregate. A malformed ID/cycle/size returns a W6 terminal refusal before resource publication. Preserve captured sharing without physical deduplication of equal values. The recursion guard must compare scene object identities on the active path, and unwind on return:

```kotlin
if (activeScenes.any { it === nestedScene }) return boundedRefusal
activeScenes.add(nestedScene)
try {
    visitOrdered(nestedScene, depthI32 + 1, pathI32)
} finally {
    activeScenes.removeAt(activeScenes.lastIndex)
}
```
- [ ] **Step 4: Freeze F64 domain and transparent path.** Compute `effectiveLocal = cull ∩ (src ?: cull)` in `:math`/planner, map once by the filter context, round outward checked I32, and reverse-propagate consumer demand before terminal clip. Sample decal outside known Picture content. For empty scene/cull/intersection, bind the existing transparent-black source instead of constructing `FilterPassOperationV1.Picture` with empty bounds or a zero-extent texture; continue evaluating ColorFilter/Compose/Blend/restore so outer alpha and `SRC` remain observable. Use this planner branch before allocating the aggregate:

```kotlin
private fun pictureKnownContentDeviceI32OrNull(
    scene: SceneSnapshot, cullF64: RectF64, srcF64: RectF64?, mapping: LayerMappingF64,
): RectI32? {
    val effectiveLocalF64 = cullF64.intersectF64OrNull(srcF64 ?: cullF64) ?: return null
    if (scene.toList().isEmpty()) return null
    return mapping.mapLocalRectToDeviceI32OrNull(effectiveLocalF64)
        ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.InvalidBounds, "Picture crop cannot fit checked I32 texels."))
}
```

In the existing W6b `materializeNode` context, a null result selects its existing `transparentBlack(currentSource)` and `ContextualFilterResult(transparent, identityBounds(transparent), null)` path; a non-null result is known Picture content, not the outer-filter output demand. Preserve the current W6b diagnostic shape and use the existing `RectF64.intersectF64OrNull` / `LayerMappingF64.mapLocalRectToDeviceI32OrNull` helpers; do not add geometry to `:gpu-plan`.
- [ ] **Step 5: Run GREEN and W6b preservation sequentially.** `rtk proxy ./gradlew :math:geometry:compileKotlinJvm` only if touched; `rtk ./gradlew :gpu-plan:compileKotlin`; `rtk ./gradlew :gpu-renderer:compileKotlin`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.translatedPictureLayerKeepsNonzeroOrigin'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.drawPictureInsidePreviousLayerPreservesHostClip'`. Record XML and native custody, including the known full-class baseline failure separately.
- [ ] **Step 6: Commit and Sol review.** Commit as `feat(gpu): preserve nested w6d picture filter semantics`. Reviewer checks recursion/cycle boundary, no false cycle for equal values, crop mapping once, empty/wrapper behavior, exact source generation, no relaxed drawPicture invariant and no post-freeze renderer choice.

### Task 4c: Capture/Wire Isolation, Atomic Pressure and Preservation

**Agent:** Fresh Terra implementation, then fresh Sol task review.

**Outcome:** The captured scene and mutable rects remain stable through Picture memory/wire replay; equal versus shared node identity is preserved; nested late refusal is atomic. Parent-plan Task 7 retains the numeric B/B−1 gate.

**Files:** Create `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6dPictureRuntimeEffectPictureTest.kt`; modify `W6dPictureFilterSurfacePixelTest.kt`, and only on demonstrated defect modify `render-ir/.../SceneArchiveCodec.kt`, `kanvas/.../render/ir/PaintSceneAdapter.kt`, `kanvas/.../picture/Picture.kt`, `W6aLayerGraphConstruction.kt`, `W6bFilterGraphConstruction.kt`, `RenderGraph.kt`, `W6aLayerGraphValidation.kt` or renderer materializer.

**Interfaces:** Consumes 4a/b frozen source and existing Picture 15/schema 9 captured filter table. Produces public memory/wire/mutation and atomicity evidence for parent-plan Task 7, without changing W5h descriptors or historical Picture readers.

- [ ] **Step 1: Add public memory/wire tests.** Create `equalPictureFiltersRemainDistinctAcrossWireReplay`: record a 2×1 Picture carrying two equal-valued but separately created `ImageFilter.Picture` values evaluated under different translations. Add `sharedPictureFilterNodeReplaysInTwoSourceContexts` reusing the same `ImageFilter.Picture` object in two source contexts. Serialize with `toByteArray()`, decode with `Picture.fromByteArray`, replay original and decoded on new `Surface`s, assert byte-exact expected pixels and Render+Readback for each. The public pixels detect erroneous physical sharing across contexts; code review verifies structural table IDs because no public API exposes them. Add `pictureFilterCaptureIgnoresLaterSrcMutation`: mutate caller-owned `RectF32` cull/src after capture and assert replay unchanged. Add real historical non-lighting Picture 14/schema 8 decode-and-pixel preservation and old 2D-lighting fail-closed assertions. **Review correction:** `W6dLightingPictureTest.historicalNonLightingFixture()` has a wire-v8 header; its byte value 14 is an opcode, not a v14 fixture. Keep that v8 preservation if useful, but obtain and check in a separate immutable v14/schema8 non-lighting fixture from the historical v14 writer at the pre-`cf57e09d2` revision (or another verifiable old-writer commit), record its provenance, and assert its decoded header before replay. Do not generate historical bytes with the current writer. Replay each candidate with this public shape:

```kotlin
val decoded = assertNotNull(Picture.fromByteArray(recorded.toByteArray()))
for (candidate in listOf(recorded, decoded)) {
    val surface = Surface(2, 1)
    surface.canvas { drawPicture(candidate) }
    val result = surface.render()
    assertContentEquals(expected, result.pixels)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}
```
- [ ] **Step 2: Add public atomic refusal/recovery test.** In `latePictureChildRefusalIsAtomicAndSameSurfaceRecovers`, place a valid Picture-filter draw before a later filter-owned Picture whose child has a perspective `Matrix3x3F32(persp0 = .25f)` around a saveLayer with `ImageFilter.DistantLitDiffuse`; reset the matrix before the child draw exactly as in `W6dLightingSurfacePixelTest.perspective lighting mapping refuses without readback mutation then recovers`. Assert terminal `w6a.layer.unsupported_lighting_mapping:` with no public successful readback or sentinel mutation, then call `discardRecordedOperations()` and render one opaque green pixel on the same `Surface`, with Render+Readback. Compute expected/sentinel bytes before creating the surface; do not call a private validator. The public refusal/recovery tail is:

```kotlin
val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
val sentinel = UByteArray(4) { 0x5au }
val before = sentinel.copyOf()
val expectedRecovery = ubyteArrayOf(0u, 255u, 0u, 255u)
val failure = assertFailsWith<IllegalStateException> { surface.readPixels(rect, sentinel) }
assertTrue(failure.message?.startsWith("w6a.layer.unsupported_lighting_mapping:") == true)
assertContentEquals(before, sentinel)
surface.discardRecordedOperations()
surface.canvas { drawRect(rect, Paint(ColorARGB.Green, antiAlias = false)) }
val recovered = surface.render()
assertContentEquals(expectedRecovery, recovered.pixels)
assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
```
- [ ] **Step 3: Run causal REDs.** Run separately `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'` and the Step 2 method selector. If existing capture/wire already passes, classify it as preservation evidence, not fabricated RED; the atomic method must reach its intended diagnostic/pixel assertion to qualify.
- [ ] **Step 4: Correct only observed gaps.** Retain the existing `SceneSnapshot` deep capture and Picture 15/schema 9 codec when green. Otherwise repair the specific copy/identity/decoder path; never add a second archive or deduplicate by content. Ensure every nested source image/program/uniform/staging allocation joins checked-I64 frame budget, the sealed source lifetime reaches its last reader, and materialization errors discard/quarantine the complete ready token. Keep `CapturedFilterInputV1.Picture(id)` refused.
- [ ] **Step 5: Run GREEN and historical gates sequentially.** `rtk ./gradlew :render-ir:compileKotlin`; `rtk ./gradlew :gpu-plan:compileKotlin`; `rtk ./gradlew :gpu-renderer:compileKotlin`; `rtk ./gradlew :kanvas:compileTestKotlin`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dLightingPictureTest'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.memoryAndWireReplayPreservePreviousPixels'`; `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6aLayerPictureTest.drawPictureInsidePreviousLayerPreservesHostClip'`. Record every XML class/method F/E/S and native status; carry the known stale writer-version class failure explicitly, not as a Task 4 regression.
- [ ] **Step 6: Commit and Sol review.** Commit as `test(gpu): prove w6d picture filter replay and recovery`. Reviewer checks copied mutable rects, stable table IDs, real historical fixtures, exact generation/lease/budget accounting, terminal no-publication and unchanged drawPicture/W5h contracts. Resolve Critical/Important before parent-plan Task 5.

## Definition of Done

- [ ] Flat and nested `ImageFilter.Picture` sources execute only via filter-owned Begin → children → Seal → FilterPass in one frozen W6 graph; drawPicture behavior remains unchanged.
- [ ] `src` crop, empty/transparent, transformed demand and outer wrappers have public pixel witnesses; all positives include Render+Readback.
- [ ] Memory/wire replay, mutation isolation, distinct/shared capture identity, historical readers, late refusal and same-Surface recovery have public evidence.
- [ ] Every resource and last reader is frozen/budgeted before native publication; no renderer replay, new graph vocabulary, fallback or prohibited test exists.
- [ ] Task 4a, 4b and 4c each have a clean Sol review; the parent plan continues at Task 5, and numeric B/B−1 remains Task 7.

## Plan Self-Review

- **Spec coverage:** 4a establishes the typed owner, source-only seal, role/generation validation and actual flat pixels. 4b adds bounded filter-held traversal, ordered W4/W5 descendants, F64 crop/demand, empty/decal and wrappers. 4c covers capture identity, memory/wire, mutable input isolation, historical readers, atomic refusal and recovery; the numeric B/B−1 proof stays in parent Task 7. The spec's conceptual owner/discovery → binding/execution → evidence sequence is grouped into independently runnable vertical slices: 4a includes flat execution so its Sol review can reject a broken physical path before nested semantics are added.
- **Type consistency:** `PictureAggregateDraftOwnerV1.FilterPicture` carries the captured node and source context only during planning; `PictureAggregateOwnerV1.FilterPicture` freezes IDs/path without a scene. `W6FramePassSinkV1` is the one frame pass/ordinal authority across reentrant W6a/W6b emission. `SealedPictureFilterSourceV1` snapshots the existing aggregate ID, resource ID, generation and `FilterInputSamplingV1` for the final `FilterPassOperationV1.Picture`. The `SceneSnapshot` planning provenance never enters renderer lowering. Existing draw-owner terminal validation remains separate.
- **Review Focus:** the five header cases map respectively to 4a's leaf pixel, 4b's nested-order fixture, 4b's crop/empty/wrapper fixtures, 4c's equal/shared replay fixtures, and 4c's late refusal/recovery fixture.
- **Placeholder scan:** all named test selectors, existing API calls and changed files are explicit. New helper names in snippets are defined in the same task; conditional file edits are gated by observed capture/wire or geometry defects, not deferred requirements. No private/infrastructure test is prescribed.

## Execution Handoff

After plan review, use `superpowers:subagent-driven-development`: Terra implements 4a then Sol reviews; repeat for 4b and 4c. Keep SDD progress in the existing W6d ledger, and update the parent plan's Task 4 status only when all three slices are reviewed. Do not start Task 5 or open the W6d PR before Task 4c is clean.
