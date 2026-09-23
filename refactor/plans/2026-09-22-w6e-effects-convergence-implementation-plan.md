# W6e Effects Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Qualify the reviewed W6b–W6d stack with six sequential public convergence shards covering all 22 image-filter families, their W4/W5/layer interactions, and atomic budget/cache/recovery behavior, without adding an API, planner, algorithm, or wire format.

**Architecture:** W6e is a covering and integration wave, not a fourth spatial implementation. It consumes W6b's immutable capture and frozen physical-graph contracts, runs independently computed public `Surface`/`Picture` witnesses against the W6d branch, and permits only a causal, bounded correction in an existing W6b–W6d owner when a public RED identifies an integration defect. The one authority remains `Surface/Picture → render-ir → :math → :gpu-plan → :gpu-renderer → one submit → public visibility`; W6e never forks it.

**Tech Stack:** Kotlin/JVM; `:render-ir`, `:math:geometry`, `:math:matrix`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; JUnit 5; public `Surface`, `Canvas`, `Picture`, paint/filter APIs, Render, Readback, pixels, and diagnostics.

**Spec:** `refactor/specs/2026-09-16-w6-layers-effects-design.md` §§5–16; `refactor/specs/2026-09-22-w6b-w6e-stacked-delivery-design.md` §§3 and 7–10; `refactor/plans/2026-09-22-w6b-blur-masks-shadows-implementation-plan.md`; `refactor/plans/2026-09-22-w6c-spatial-dag-implementation-plan.md`; `refactor/plans/2026-09-22-w6d-advanced-effects-implementation-plan.md`. Base: reviewed `codex/w6d-advanced-effects`; implementation branch: `codex/w6e-effects-convergence`; its Draft PR targets `codex/w6d-advanced-effects`.

## Global Constraints

- Before Task 1, record `rtk git rev-parse codex/w6d-advanced-effects`; stop if its reviewed W6b–W6d contracts or public selectors are absent. W6e does not repair a missing base by recreating one.
- W6e adds no public API, filter family, planner, algorithm, wire/Picture format, graph, allocator, cache, submit route, or renderer-side planning type. A production correction is allowed only in the existing W6b/W6c/W6d owner named by a causal public RED and may not alter its published contract.
- The canonical shared names are exactly `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `CapturedFilterTableV1`, `FilterEvaluationKeyV1`, `FilterBoundsPlanV1`, `FilterPassOperationV1`, `PlanPass.FilterPass`, and `PlanResourceRole.FilterTarget`. W6e consumes these names; it does not introduce aliases or equivalents. The four written plans use this same nomenclature before implementation begins.
- Each checkbox is one 2–5 minute action. Use public RED → minimal GREEN → local refactor. A RED is valid only when unchanged production reaches the stated public pixel, diagnostic, or recovery assertion; compilation, fixture, permission, native availability, timeout, reflection, mock, fake, or infrastructure failure is not evidence.
- Tests use only public `Surface`, `Canvas`, `Picture`, public filter/paint/layer APIs, public Picture bytes, public diagnostics, `render()`, `readPixels`, `discardRecordedOperations()`, and same-Surface re-recording. Do not add private/internal, reflection, mock, fake-device/backend, counter, hook, static-source, test-infrastructure, or planner-inspection tests.
- Calculate every expected byte array or CPU oracle result before creating a `Surface` or `PictureRecorder`. Identity, copy, Crop, Offset, and composite witnesses are byte-exact. Blur, MatrixConvolution, DisplacementMap, Magnifier, and lighting use a separately written family CPU oracle and a family-local tolerance; never add or widen a global tolerance.
- Every positive witness asserts `result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback"))`. It is not sufficient that a pixel happens to match through a fallback.
- `:math` remains the only owner of bounds/mapping geometry. F64 calculates and checked outward I32 projection seals bounds; no W6e renderer or `:kanvas` geometry type, `.toInt()` bound calculation, target resize, or replan is allowed.
- Preserve W6a's `knownContent`, `desiredOutput`, `requiredInput`, and `producedOutput` separately. Preserve W6b's contextual input binding and W6c's cache semantics. A renderer only materializes frozen `PlanPass.FilterPass` and `PlanResourceRole.FilterTarget` records.
- Use only RGBA8 as the positive format. F16/HDR remains an exact capability refusal. Fonts/glyph generation, codecs/external formats, GMs, dashboard, renders, references, baselines, scores, rebaseline, global Skia, `jpg-color-cube`, arbitrary SkSL/WGSL frontend, W8 legacy removal, and unobservable device-loss proof are excluded.
- A W6-owned refusal is frame-terminal: no partial readback or healthy sibling is public; a balanced public Canvas followed by `discardRecordedOperations()` must allow a correct scene on the same `Surface`. Native exits 133/134 are `UNKNOWN`, never PASS evidence.
- Run each Gradle invocation serially from `/Users/chaos/.codex/worktrees/cbf6/kanvas` via `rtk` (or `rtk proxy` for KMP compile selectors). Record Gradle exit, JUnit XML method custody, native exit, and source commit separately.
- Terra implements every task. Sol reviews every task and the whole branch. Per task permit one bounded Terra correction and one scoped Sol re-review; for the whole branch permit one bounded non-Sol correction wave and one scoped Sol re-review. Create one stacked Draft PR only after green gates; do not merge.

## Review Focus

- **Cross-family source context:** a nested `Compose` containing `Blur` and `Offset` must bind the outer node to the inner result, not the layer source; Task 5 test `allFamiliesPreserveContextualImplicitSourceAcrossNestedLayers` owns it.
- **Destination timing:** a backdrop and filtered previous layer with an advanced filter must observe the immediate parent at save and parent-plus-children after child rendering respectively; Task 5 test `backdropPreviousAndDestinationReadKeepTheirSpecifiedOrder` owns it.
- **Exact versus approximate policy:** Crop/Offset/Blend remain byte-exact next to a blurred or convolution result that uses only its local oracle tolerance; Task 1 test `coreShardKeepsExactAndBlurOracleAssertionsSeparate` owns it.
- **Cache pressure:** an identical warm Picture replay must retain the cold B/B-1 admission result and leases even when a filter result is reusable; Task 6 test `warmCacheRemainsPessimisticAtExactBudgetBoundary` owns it.
- **Late terminality:** a valid earlier sibling plus a late unsupported/corrupt W6 owner must leave sentinel readback unchanged and recover on that `Surface`; Task 6 test `lateRefusalDoesNotPublishHealthySiblingAndSameSurfaceRecovers` owns it.

---

## Actual Code Map and Public Fixture/Oracle Interfaces

```text
IR   = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
GEOM = math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU  = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API  = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
```

| Existing owner | W6e responsibility |
| --- | --- |
| `IR/EffectNode.kt`, `IR/SceneArchiveCodec.kt`, `API/picture/Picture.kt`, `API/picture/PictureWireV8.kt` | Consume `CapturedFilterTableV1` and Picture 14/schema 8 only through public replay. Correct a W6b–W6d integration bug here only when a public memory/wire RED identifies it; no format bump. |
| `PLAN/W6aLayerPlanCompiler.kt`, `PLAN/W6aLayerGraphConstruction.kt`, `PLAN/PlanPasses.kt`, `PLAN/PlanResources.kt`, `PLAN/RenderGraph.kt`, `PLAN/W6aLayerPlanBudget.kt` | Existing sole owners of `FilterEvaluationKeyV1`, `FilterPassOperationV1`, `PlanPass.FilterPass`, `PlanResourceRole.FilterTarget`, order, lifetime, cache, and budget. W6e may correct only a RED-proven joining defect. |
| `GPU/planning/W6aLayerGraphLowerer.kt`, `GPU/recording/GPUW6aLayerFramePlan.kt`, `GPU/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt` | Existing frozen-plan lower/materialize owners. No W6e planning, bounds, resource, pass, or fallback choice may be added. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `API/surface/gpu/GPUPlanSurfaceRouter.kt` | Existing terminal-owner boundary; only RED-proven W6b–W6d integration corrections are allowed. |
| `TEST/surface/W6e*SurfacePixelTest.kt`, `TEST/picture/W6eEffectsConvergencePictureTest.kt`, `TEST/surface/W6e*CpuOracle.kt` | New public covering shards and independent expected/oracle helpers. They do not inspect planner/renderer state. |

All new fixtures use this public-only shape; expected construction happens before the `Surface` exists:

```kotlin
private fun assertRenderedExactly(expected: UByteArray, draw: Canvas.() -> Unit) {
    val surface = Surface(width = 4, height = 2)
    surface.canvas(draw)
    val result = surface.render()
    assertContentEquals(expected, result.pixels)
    assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
}

private fun assertFamilyNear(expected: UByteArray, actual: UByteArray, maxDelta: Int) {
    expected.indices.forEach { index ->
        assertTrue(kotlin.math.abs(expected[index].toInt() - actual[index].toInt()) <= maxDelta)
    }
}
```

The six shard test interfaces are intentionally public-output-only:

```kotlin
internal object W6eBlurShadowCpuOracle { fun render(case: CoreCase): UByteArray }
internal object W6eAdvancedCpuOracle { fun convolution(case: SamplingCase): UByteArray; fun magnifier(case: SamplingCase): UByteArray }
internal object W6eLightingCpuOracle { fun render(case: LightingCase): UByteArray }
private fun assertTerminalWithoutReadbackMutation(surface: Surface, expectedDiagnostic: String, sentinel: UByteArray)
```

## Frozen W6e Consumption Contract

Every shard passes filters through the reviewed W6b contracts; it must not declare replacements:

```kotlin
// Owned by W6b and consumed unchanged by W6e.
val evaluationKey: FilterEvaluationKeyV1 = FilterEvaluationKeyV1.of(
    capturedNodeId = CapturedFilterNodeIdI32(0),
    boundSourceId = sourceTarget,
    mapping = layerMapping,
    desiredOutputDeviceI32 = desiredOutput,
)

// W6e may observe pixels only. W6b–W6d remain the exclusive producer of:
// CapturedFilterInputV1, CapturedFilterTableV1,
// FilterPassOperationV1, PlanPass.FilterPass, PlanResourceRole.FilterTarget.
```

An integration fix is admissible only after this causal chain is documented in the task commit: public expected → public RED on reviewed W6d → one existing owner and defect → minimal correction preserving the frozen contract → same public GREEN plus the owning prior-wave selector. If this chain cannot be written, record the gap in W06 status and do not edit production.

### Task 1: Shard 1 — Crop, Blur, DropShadow, Offset, and Tile

**Agent:** Terra implementation; Sol review.

**Outcome:** One public sequential shard attributes byte-exact Crop/Offset/Tile and family-oracled Blur/DropShadow behavior, with Surface and Picture replay, before W6e makes any integration correction.

**Files:**

- Create: `TEST/surface/W6eCoreEffectsSurfacePixelTest.kt`
- Create: `TEST/surface/W6eBlurShadowCpuOracle.kt`
- Create: `TEST/picture/W6eEffectsConvergencePictureTest.kt`
- Modify only if a causal RED proves it: existing W6b owner from the code map.

**Interfaces:**

- Consumes: `CapturedFilterTableV1`, `CapturedFilterInputV1.ImplicitSource`, `FilterEvaluationKeyV1`, W6b blur/shadow `FilterPassOperationV1` arms, Picture 14/schema 8.
- Produces: `coreShardKeepsExactAndBlurOracleAssertionsSeparate`, `coreShardMemoryAndWireReplayAreStable`, and public custody for five named families; Task 5 reuses their fixture builders without changing their oracle policy.

- [ ] **Step 1 (2–5 min): Record the reviewed W6d base hash and verify W6b canonical names are present; stop and report a base-contract gap if any name is missing.**

```sh
rtk git rev-parse codex/w6d-advanced-effects
rtk rg -n 'CapturedFilterNodeIdI32|CapturedFilterInputV1|CapturedFilterTableV1|FilterEvaluationKeyV1|FilterPassOperationV1|PlanPass.FilterPass|PlanResourceRole.FilterTarget' render-ir gpu-plan
```

- [ ] **Step 2 (2–5 min): Write public expected arrays and the independent blur/shadow oracle before every `Surface` creation.**

```kotlin
@Test fun coreShardKeepsExactAndBlurOracleAssertionsSeparate() {
    val cropExpected = rgbaRow(transparent, blue, transparent, transparent)
    val offsetExpected = rgbaRow(transparent, blue, transparent, transparent)
    val tileExpected = rgbaRow(blue, transparent, blue, transparent)
    val blurExpected = W6eBlurShadowCpuOracle.render(CoreCase.BlurDecalEdge)
    assertRenderedExactly(cropExpected) { drawCore(ImageFilter.Crop(cropRect)) }
    assertRenderedExactly(offsetExpected) { drawCore(ImageFilter.Offset(1f, 0f)) }
    assertRenderedExactly(tileExpected) { drawCore(ImageFilter.Tile(srcRect, dstRect)) }
    assertFamilyNear(blurExpected, renderCore(ImageFilter.Blur(1f, 1f, TileMode.DECAL)), maxDelta = 1)
}
```

- [ ] **Step 3 (2–5 min): Run the new shard against unchanged W6d and record a behavioral RED only if one named family, replay, or scope assertion fails.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCoreEffectsSurfacePixelTest'`

Expected: PASS if W6d already converges; otherwise FAIL at the named public assertion, never an infrastructure failure.

- [ ] **Step 4 (2–5 min): Add memory/wire Picture replay and post-capture mutation witnesses for the five captured filters.**

```kotlin
@Test fun coreShardMemoryAndWireReplayAreStable() {
    val picture = recordCoreShardPicture()
    val expected = expectedCoreShardPixels()
    listOf(picture, requireNotNull(Picture.fromByteArray(picture.toByteArray()))).forEach { replay ->
        assertPicturePixels(expected, replay)
    }
}
```

- [ ] **Step 5 (2–5 min): If and only if Step 3 or 4 is a causal RED, make one minimal correction in the named W6b owner without changing the frozen contract; otherwise make no production edit.**

```kotlin
// Permitted correction shape: preserve the existing selected operation and key.
require(pass.evaluationKey == existingKey)
encodeFrozen(pass) // no new pass, target, bounds, route, or fallback
```

- [ ] **Step 6 (2–5 min): Run GREEN and focused W6b preservation serially.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCoreEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eEffectsConvergencePictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bDropShadowSurfacePixelTest'
```

- [ ] **Step 7 (2–5 min): Refactor only duplicate public fixture construction; retain distinct exact and family-oracle assertions.**

- [ ] **Step 8 (2–5 min): Commit and request Sol review.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6eCoreEffectsSurfacePixelTest.kt,surface/W6eBlurShadowCpuOracle.kt,picture/W6eEffectsConvergencePictureTest.kt}
git commit -m 'test(kanvas): cover w6e core spatial effects'
```

Sol reviews public-only evidence, exact/tolerance separation, canonical contract consumption, and any bounded W6b correction.

### Task 2: Shard 2 — ColorFilter, Compose, Blend, Dilate, Erode, and Merge

**Agent:** Terra implementation; Sol review.

**Outcome:** The composition shard proves W5 ColorFilter/Blend are consumed once and W6c Compose/Merge ordering, duplication, and morphology remain publicly correct.

**Files:**

- Create: `TEST/surface/W6eCompositionEffectsSurfacePixelTest.kt`
- Create: `TEST/picture/W6eCompositionEffectsPictureTest.kt`
- Modify only if causal: existing W6c owner in `PLAN/` or `GPU/`.

**Interfaces:**

- Consumes: W6b canonical capture/pass contracts and W6c’s existing ColorFilter, Compose, Blend, Morphology, and ordered-input operation arms.
- Produces: public byte-exact `compositionShardPreservesContextAndInputOrder` and replay custody; Task 5 composes these nodes with W4/W5 and layers.

- [ ] **Step 1 (2–5 min): Write all byte-exact expected arrays before `Surface`, including a contextual Compose and duplicated Merge input.**

```kotlin
@Test fun compositionShardPreservesContextAndInputOrder() {
    val compose = ImageFilter.Compose(ImageFilter.ColorFilter(ColorFilter.Luma), ImageFilter.Offset(1f, 0f))
    assertRenderedExactly(expectedComposeBytes()) { drawFiltered(compose) }
    assertRenderedExactly(expectedMergeBytes()) { drawFiltered(mergeWithRepeatedInput()) }
    assertRenderedExactly(expectedBlendBytes()) { drawFiltered(backgroundForegroundBlend()) }
    assertRenderedExactly(expectedDilateBytes()) { drawFiltered(ImageFilter.Dilate(1f, 0f)) }
    assertRenderedExactly(expectedErodeBytes()) { drawFiltered(ImageFilter.Erode(1f, 0f)) }
}
```

- [ ] **Step 2 (2–5 min): Run the composition selector before production edits and record any semantic RED separately by family.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCompositionEffectsSurfacePixelTest'`

Expected: PASS, or a public RED naming ColorFilter, Compose, Blend, Dilate, Erode, or Merge.

- [ ] **Step 3 (2–5 min): Add Surface/Picture memory-wire replay and mutation witnesses without reading a filter table or planner.**

```kotlin
@Test fun compositionShardPictureReplayKeepsBytesAndPixels() {
    val bytes = recordCompositionPicture().toByteArray()
    val replay = requireNotNull(Picture.fromByteArray(bytes))
    assertPicturePixels(expectedCompositionBytes(), replay)
}
```

- [ ] **Step 4 (2–5 min): If a causal RED identifies a W6c integration defect, correct only the existing owner while preserving ordered inputs, the contextual source, and the frozen pass.**

```kotlin
// Existing owner invariant preserved by any correction.
val orderedInputs = pass.inputs()
require(orderedInputs == orderedInputs.toList())
```

- [ ] **Step 5 (2–5 min): Run GREEN and W5/W6c preservation serially.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCompositionEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eCompositionEffectsPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cComposeSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6cMorphologySurfaceTest'
```

- [ ] **Step 6 (2–5 min): Refactor shared expected-array construction only; retain a separate assertion for each public family.**

- [ ] **Step 7 (2–5 min): Commit and request Sol review.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6eCompositionEffectsSurfacePixelTest.kt,picture/W6eCompositionEffectsPictureTest.kt}
git commit -m 'test(kanvas): cover w6e composition effects'
```

### Task 3: Shard 3 — Six Lighting Families and Picture

**Agent:** Terra implementation; Sol review.

**Outcome:** All six lighting variants and Picture filter have independent public oracles/replay witnesses, with each lighting tolerance local to its own family.

**Files:**

- Create: `TEST/surface/W6eLightingPictureSurfacePixelTest.kt`
- Create: `TEST/surface/W6eLightingCpuOracle.kt`
- Create: `TEST/picture/W6eLightingPictureReplayTest.kt`
- Modify only if causal: existing W6d lighting or Picture owner.

**Interfaces:**

- Consumes: W6b canonical capture records, W6d existing lighting/Picture `FilterPassOperationV1` arms, immutable `SceneSnapshot`, and public Picture readers.
- Produces: six-family oracle custody and `pictureFilterPreservesImmutableReplay`; Task 5 uses these filter values inside cross-lane layers.

- [ ] **Step 1 (2–5 min): Implement independent lighting/Picture expected builders before opening a Surface.**

```kotlin
@Test fun sixLightingFamiliesAndPictureMatchPublicOracles() {
    lightingCases().forEach { case ->
        val expected = W6eLightingCpuOracle.render(case)
        val actual = renderFiltered(case.filter)
        assertFamilyNear(expected, actual.pixels, case.maxChannelDelta)
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }
    assertRenderedExactly(expectedPicturePixels()) { drawFiltered(recordedPictureFilter()) }
}
```

- [ ] **Step 2 (2–5 min): Run the shard before production edits and retain per-family public failure custody.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eLightingPictureSurfacePixelTest'`

Expected: PASS or an attributable public RED for one lighting family or Picture.

- [ ] **Step 3 (2–5 min): Add memory/wire replay plus post-capture mutation of Picture source/cull inputs, observing only bytes and pixels.**

```kotlin
@Test fun pictureFilterPreservesImmutableReplay() {
    val picture = recordPictureFilter()
    val expected = expectedPicturePixels()
    mutateOriginalPictureInputs()
    listOf(picture, requireNotNull(Picture.fromByteArray(picture.toByteArray()))).forEach { assertPicturePixels(expected, it) }
}
```

- [ ] **Step 4 (2–5 min): Make at most one causal W6d integration correction if a public RED proves one; preserve snapshots, bounds, and the already selected pass/resource IDs.**

```kotlin
check(payload.copyOutputBoundsDeviceI32() == frozenOutputBounds)
materializeFrozen(payload) // no Picture playback or dynamic lighting selection
```

- [ ] **Step 5 (2–5 min): Run GREEN and W6d preservation serially.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eLightingPictureSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eLightingPictureReplayTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'
```

- [ ] **Step 6 (2–5 min): Refactor only shared public test setup, then commit and request Sol review.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6eLightingPictureSurfacePixelTest.kt,surface/W6eLightingCpuOracle.kt,picture/W6eLightingPictureReplayTest.kt}
git commit -m 'test(kanvas): cover w6e lighting and picture effects'
```

### Task 4: Shard 4 — Displacement, Magnifier, MatrixConvolution, Runtime IMAGE_FILTER

**Agent:** Terra implementation; Sol review.

**Outcome:** Advanced sampling and the registered `IMAGE_FILTER` runtime path are publicly converged, retaining their independent sampling-oracle or exact same-pixel policy.

**Files:**

- Create: `TEST/surface/W6eAdvancedSamplingRuntimeSurfacePixelTest.kt`
- Create: `TEST/surface/W6eAdvancedSamplingCpuOracle.kt`
- Create: `TEST/picture/W6eRuntimeImageFilterPictureTest.kt`
- Modify only if causal: existing W6d advanced-sampling/runtime owner.

**Interfaces:**

- Consumes: `CapturedFilterInputV1.ImplicitSource`, W6d existing operation arms, `kanvas.runtime.image-opacity`, and the W5h runtime catalogue unchanged.
- Produces: four-family public witnesses; Task 5 can combine their public values but does not expand their bounds/tolerance rules.

- [ ] **Step 1 (2–5 min): Calculate independent sampling oracles and the exact image-opacity expected pixel before creating a Surface.**

```kotlin
@Test fun advancedSamplingAndRuntimeImageFilterConverge() {
    assertFamilyNear(W6eAdvancedSamplingCpuOracle.convolution(convolutionCase), renderFiltered(convolution).pixels, 1)
    assertFamilyNear(W6eAdvancedSamplingCpuOracle.displacement(displacementCase), renderFiltered(displacement).pixels, 1)
    assertFamilyNear(W6eAdvancedSamplingCpuOracle.magnifier(magnifierCase), renderFiltered(magnifier).pixels, 1)
    assertRenderedExactly(rgba(60, 30, 15, 128)) { drawFiltered(imageOpacity(alpha = .5f, input = null)) }
}
```

- [ ] **Step 2 (2–5 min): Run the shard unchanged and record a public RED only by named family/diagnostic.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eAdvancedSamplingRuntimeSurfacePixelTest'`

Expected: PASS, or a public RED attributable to one of the four named families.

- [ ] **Step 3 (2–5 min): Add memory/wire replay and wrong-ABI/unregistered runtime terminal controls through public diagnostics/readback.**

```kotlin
@Test fun runtimeImageFilterReplayAndRefusalRemainPublic() {
    assertPicturePixels(expectedImageOpacity(), decode(recordRuntimeImageOpacity()))
    assertTerminalWithoutReadbackMutation(surfaceWithWrongRuntimeAbi(), "w6d.runtime_effect.abi_unsupported", sentinelBytes())
}
```

- [ ] **Step 4 (2–5 min): If causally required, correct one existing W6d owner without mutating W5h descriptor hashes or adding sampling/program selection.**

```kotlin
require(descriptor.id.value == "kanvas.runtime.image-opacity")
require(descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER)
```

- [ ] **Step 5 (2–5 min): Run GREEN and prior-wave preservation serially.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eAdvancedSamplingRuntimeSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eRuntimeImageFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dAdvancedSamplingSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dRuntimeImageOpacitySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest'
```

- [ ] **Step 6 (2–5 min): Refactor duplicated oracle input setup only, then commit and request Sol review.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6eAdvancedSamplingRuntimeSurfacePixelTest.kt,surface/W6eAdvancedSamplingCpuOracle.kt,picture/W6eRuntimeImageFilterPictureTest.kt}
git commit -m 'test(kanvas): cover w6e advanced sampling runtime effects'
```

### Task 5: Shard 5 — Cross-Lane Covering Matrix

**Agent:** Terra implementation; Sol review.

**Outcome:** A single sequential public covering shard reaches all 22 families across W4 geometry, W5 materials, nesting, backdrop, previous, crop, and destination-read without hiding family attribution.

**Files:**

- Create: `TEST/surface/W6eCrossLaneEffectsSurfacePixelTest.kt`
- Create: `TEST/picture/W6eCrossLaneEffectsPictureTest.kt`
- Modify only if causal: one named W6b–W6d integration owner.

**Interfaces:**

- Consumes: the previous four shards' public builders; W4 geometry, W5 material/color/blend authority, W6a nesting, W6d backdrop/previous, and canonical W6b contracts.
- Produces: `allFamiliesPreserveContextualImplicitSourceAcrossNestedLayers`, `backdropPreviousAndDestinationReadKeepTheirSpecifiedOrder`, and the complete 22-family attribution matrix; Task 6 consumes this fixture for budget/recovery only.

- [ ] **Step 1 (2–5 min): Declare the 22-family covering matrix in test data, one public case per family, before any Surface is created.**

```kotlin
private val allFamilies = listOf(
    CropCase, BlurCase, DropShadowCase, ColorFilterCase, ComposeCase, BlendCase, DilateCase, ErodeCase,
    DistantDiffuseCase, PointDiffuseCase, SpotDiffuseCase, DistantSpecularCase, PointSpecularCase, SpotSpecularCase,
    OffsetCase, TileCase, MergeCase, DisplacementCase, PictureCase, MagnifierCase, MatrixConvolutionCase, RuntimeImageFilterCase,
)
```

- [ ] **Step 2 (2–5 min): Write public nested-layer, W4 geometry, W5 material, crop, destination-read, backdrop, and filtered-previous witnesses with precomputed expected bytes/oracles.**

```kotlin
@Test fun allFamiliesPreserveContextualImplicitSourceAcrossNestedLayers() {
    allFamilies.forEach { case -> assertCaseThroughW4W5NestedLayer(case) }
}

@Test fun backdropPreviousAndDestinationReadKeepTheirSpecifiedOrder() {
    val expected = expectedParentChildSequence()
    assertRenderedExactly(expected) { drawDestinationReadBackdropPreviousWitness() }
}
```

- [ ] **Step 3 (2–5 min): Run the cross-lane selector unchanged; record an independent public failure for each case rather than broadening a tolerance or skipping it.**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCrossLaneEffectsSurfacePixelTest'`

Expected: PASS, or one/more named family-case REDs with public pixels/diagnostics.

- [ ] **Step 4 (2–5 min): Add Picture memory/wire replay for a representative nested graph whose table has shared and equal-distinct public nodes.**

```kotlin
@Test fun crossLanePictureKeepsSharingWithoutValueAliasing() {
    val picture = recordCrossLanePictureWithSharedAndDistinctFilters()
    assertPicturePixels(expectedCrossLanePixels(), requireNotNull(Picture.fromByteArray(picture.toByteArray())))
}
```

- [ ] **Step 5 (2–5 min): For each causal RED, make no more than one bounded correction in its actual W6b–W6d owner, preserving canonical interfaces and frozen authority; otherwise leave production untouched.**

```kotlin
// Required review note for each correction.
// RED: W6eCrossLaneEffectsSurfacePixelTest.<method>
// Owner: <existing W6b/W6c/W6d file>
// Preserved: CapturedFilterTableV1 / FilterEvaluationKeyV1 / FilterPass / FilterTarget
```

- [ ] **Step 6 (2–5 min): Run GREEN, W4/W5/W6a/W6d preservation, and all preceding W6e shards serially.**

```sh
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCrossLaneEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eCrossLaneEffectsPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dBackdropPreviousSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCoreEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCompositionEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eLightingPictureSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eAdvancedSamplingRuntimeSurfacePixelTest'
```

- [ ] **Step 7 (2–5 min): Refactor only test-data/fixture duplication; preserve one independently reported case per family.**

- [ ] **Step 8 (2–5 min): Commit and request Sol review.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/{surface/W6eCrossLaneEffectsSurfacePixelTest.kt,picture/W6eCrossLaneEffectsPictureTest.kt}
git commit -m 'test(kanvas): cover w6e cross lane effect matrix'
```

### Task 6: Shard 6 — Budgets, Cache, Terminal Refusal, Recovery, and Delivery

**Agent:** Terra implementation; Sol review.

**Outcome:** Public B/B-1, pessimistic cache, sentinel/no-publication, and same-Surface recovery prove the frozen W6 stack remains transactional; W6e then qualifies one Draft PR without merge.

**Files:**

- Create: `TEST/surface/W6eEffectsBudgetCacheRecoverySurfacePixelTest.kt`
- Modify only if causal: existing W6b–W6d budget/cache/router/materializer owner.
- Modify: `refactor/waves/W06-layers-effects/status.md`
- Modify: `refactor/README.md`
- Modify: `refactor/plans/2026-09-22-w6e-effects-convergence-implementation-plan.md`

**Interfaces:**

- Consumes: Task 5 public all-family fixture; existing pessimistic budget/cache/lease lifecycle and terminal public `Surface` APIs.
- Produces: public B/B-1/cache/recovery custody, status/README delivery, whole-branch review, and one unmerged Draft PR on W6d.

- [ ] **Step 1 (2–5 min): Derive B independently from documented fixture dimensions and frozen target/resource formula before creating a Surface; write B and B−1 public cases.**

```kotlin
@Test fun warmCacheRemainsPessimisticAtExactBudgetBoundary() {
    val budgetB = documentedW6eFixtureBudgetBytes()
    assertContentEquals(expectedAllFamilyPixels(), renderAllFamilies(budgetB))
    primeByPublicRenderOnly()
    assertTerminalWithoutReadbackMutation(renderAllFamiliesSurface(budgetB - 1L), "w6d.layer.frame_budget_exceeded", sentinelBytes())
}
```

- [ ] **Step 2 (2–5 min): Write a late-refusal sentinel and same-Surface recovery witness using only public operations.**

```kotlin
@Test fun lateRefusalDoesNotPublishHealthySiblingAndSameSurfaceRecovers() {
    val surface = surfaceWithEarlierValidSiblingAndLateRefusedW6Input()
    assertTerminalWithoutReadbackMutation(surface, expectedLateOwnerDiagnostic(), sentinelBytes())
    surface.discardRecordedOperations()
    surface.canvas { drawRect(recoveryRect, Paint(ColorARGB.Green, antiAlias = false)) }
    assertContentEquals(recoveryGreenBytes(), surface.render().pixels)
}
```

- [ ] **Step 3 (2–5 min): Run the budget/cache/recovery selector unchanged and distinguish a semantic RED from native exit 133/134 (`UNKNOWN`).**

Run: `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eEffectsBudgetCacheRecoverySurfacePixelTest'`

Expected: PASS, or a causal public B/B-1/cache/sentinel/recovery RED; native 133/134 is recorded `UNKNOWN`.

- [ ] **Step 4 (2–5 min): If a causal RED proves an integration bug, correct only its established W6b–W6d owner without changing cache key, budget contract, resource role, lease lifecycle, or route.**

```kotlin
// Existing invariant retained by a permitted correction.
require(peakBytesI64 <= frameBudgetBytesI64)
publishReadyTokenOnlyAfterAllFrozenResourcesValidate()
```

- [ ] **Step 5 (2–5 min): Run all six W6e shards and touched-module compilation serially.**

```sh
rtk proxy ./gradlew :math:geometry:compileKotlinJvm
rtk proxy ./gradlew :math:matrix:compileKotlinJvm
rtk ./gradlew :render-ir:compileKotlin
rtk ./gradlew :gpu-plan:compileKotlin
rtk ./gradlew :gpu-renderer:compileKotlin
rtk ./gradlew :kanvas:compileKotlin
rtk ./gradlew :kanvas:compileTestKotlin
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCoreEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCompositionEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eLightingPictureSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eAdvancedSamplingRuntimeSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eCrossLaneEffectsSurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6eEffectsBudgetCacheRecoverySurfacePixelTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eEffectsConvergencePictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eCompositionEffectsPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eLightingPictureReplayTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eRuntimeImageFilterPictureTest'
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6eCrossLaneEffectsPictureTest'
```

- [ ] **Step 6 (2–5 min): Manually audit changed production paths for a post-freeze pass/resource/ID/bounds/cache-key creation or legacy fallback; classify an existing W8 reference in review notes and do not add a static-source test.**

```sh
rtk rg -n 'GPUPreparedCompositeLowerer|GPUPreparedSurfaceProductEntry|GPUImageFilterPlan|\.toInt\(\)|PlanPass\(|PlanResource\(' gpu-plan gpu-renderer kanvas
```

- [ ] **Step 7 (2–5 min): Update W06 status/README with exact base and source commits, B formula, named public shards, Gradle/XML/native custody, native `UNKNOWN` policy, exclusions, and no ISO/global convergence claim.**

- [ ] **Step 8 (2–5 min): Run a red-flag placeholder scan using `rtk rg -n --pcre2 '(?:TO)(?:DO)|(?:TB)(?:D)|implement[[:space:]]later|fill[[:space:]]in[[:space:]]details|appropriate[[:space:]]error[[:space:]]handling|similar[[:space:]]to[[:space:]]Task' refactor/plans/2026-09-22-w6e-effects-convergence-implementation-plan.md`, repair every accidental placeholder, then run `rtk git diff --check` and `rtk git diff -- refactor/plans/2026-09-22-w6e-effects-convergence-implementation-plan.md`.**

- [ ] **Step 9 (2–5 min): Commit Task 6 and request whole-branch Sol review against the exact W6d base.**

```sh
git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6eEffectsBudgetCacheRecoverySurfacePixelTest.kt refactor
git commit -m 'test(kanvas): close w6e effects convergence'
```

Sol reviews all 22 family witnesses, exact/oracle policy, canonical W6b-contract consumption, W4/W5/layer ordering, snapshots, cache pessimism/leases, terminal visibility, and absence of a new authority. If it finds Critical/Important findings, Terra makes exactly one bounded correction commit, reruns only causal selectors plus their named preservation selectors, and requests one scoped Sol re-review.

- [ ] **Step 10 (2–5 min): Verify the stack/diff and create or update the unmerged Draft PR targeting W6d.**

```sh
rtk git status --short
rtk git diff --check
rtk git log --oneline codex/w6d-advanced-effects..HEAD
git push origin codex/w6e-effects-convergence
```

The Draft PR description states its exact base, six task commits, 22-family shard mapping, green/unknown custody, B/B-1 formula, exclusions, Sol review status, and that no merge occurred.

## W6e Definition of Done

- [ ] Six public shards run sequentially with exact membership: (1) Crop/Blur/DropShadow/Offset/Tile; (2) ColorFilter/Compose/Blend/Dilate/Erode/Merge; (3) six lighting families plus Picture; (4) Displacement/Magnifier/MatrixConvolution/runtime IMAGE_FILTER; (5) all 22 with W4/W5/nesting/backdrop/previous/crop/destination-read; (6) B/B-1/cache/recovery.
- [ ] W6e consumes exactly `CapturedFilterNodeIdI32`, `CapturedFilterInputV1`, `CapturedFilterTableV1`, `FilterEvaluationKeyV1`, `FilterPassOperationV1`, `PlanPass.FilterPass`, and `PlanResourceRole.FilterTarget`; W6c/W6d wording is corrected to these names without creating a new contract.
- [ ] Every expected precedes Surface/Picture construction; exact cases remain byte-exact; only family-local independent CPU oracles tolerate Blur, convolution, lighting, magnifier, and displacement; every positive case carries Render+Readback evidence.
- [ ] Tests contain no private/reflection/mock/fake/counter/static-source/infrastructure assertion and prove terminal sentinel/no-publication and same-Surface recovery publicly.
- [ ] Any production change is a one-owner causal correction in W6b–W6d preserving frozen authority; no API, planner, algorithm, format, cache, graph, or fallback is added.
- [ ] Fonts, codecs, GMs/dashboard/renders/baselines/scores, global Skia, `jpg-color-cube`, arbitrary frontends, F16/HDR positive rendering, and native 133/134 success claims remain excluded.
- [ ] Sol task reviews and a whole-branch Sol review leave no Critical/Important finding after at most one bounded correction wave; one unmerged Draft PR is stacked on W6d.

## Plan Self-Review

- **Spec coverage:** Tasks 1–4 cover exactly 5 + 6 + 7 + 4 = 22 families. Task 5 covers their W4/W5/layer/destination interactions. Task 6 covers budgets, cache pessimism, terminality, recovery, qualification, and delivery. The explicit exclusions remain exclusions.
- **Type consistency:** Every task consumes the W6b canonical seven names; none declares a replacement. All permitted production corrections remain in existing W6b–W6d owners and must preserve the frozen records.
- **Review focus:** The five header risks are pinned to public tests in Tasks 5, 5, 1, 6, and 6 respectively.
- **Placeholder scan:** Task 6 provides the exact scan; this plan contains concrete files, interfaces, test names, snippets, serialized commands, commits, reviews, and PR criteria.
- **Diff check:** Task 6 runs `git diff --check`; planning changes are confined to this plan and delivery documentation/tests or causal W6b–W6d integration fixes during execution.

## Execution Handoff

Plan complete and saved to `refactor/plans/2026-09-22-w6e-effects-convergence-implementation-plan.md`. The required execution method is subagent-driven: Terra implements Tasks 1–6 in order, Sol reviews each task and the whole branch, with one bounded correction wave only. Review the plan before implementation; do not merge the Draft PR.
