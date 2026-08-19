# FP-07 Prepared Composite Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate pictures, layers, every public image filter, every public mask filter, backdrop reads, and restore composites to one atomic prepared Surface frame with native WebGPU execution and no legacy continuation.

**Architecture:** A transactional capture stage expands pictures and builds an immutable layer-scope tree. Public filters lower into one typed DAG, a small proof-producing normalizer folds metadata-only operations, and the common prepared frame plans all child draws, intermediates, destination reads, resources, and pass dependencies before native work begins. One full-frame preflight validates the closed plan; the wgpu4k materializer then records ordered copy, render, compute, and composite work without reinterpreting semantics.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, Java toolchain 25, wgpu4k/WebGPU, parser-validated WGSL through wgsl4k, `kotlin.test`, JUnit Jupiter, existing prepared Surface, layer, filter, destination-read, color, clip, and ownership contracts.

## Global Constraints

- Read `docs/superpowers/specs/2026-07-29-fp-07-prepared-composite-route-design.md` before implementation.
- Do not start Task 1 until FP-04, FP-05, and FP-06 are accepted, committed, and the target branch is clean.
- Do not port Ganesh or Graphite.
- Use Skia filtering, Graphite, and Dawn only as bounded references at commit `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`.
- Keep WebGPU as the only GPU backend and WGSL as the shader target.
- Do not create `skif::Backend`, `SkSpecialImage`, a Canvas device stack, a Graphite `Recorder`, or a backend-polymorphic resource provider.
- Support every current public `ImageFilter` and `MaskFilter` kind in this frame plan.
- Use registered Kotlin/WGSL descriptors for runtime effects; do not compile arbitrary SkSL.
- Reuse `GPULayerPlan`, `GPUFilterPlan`, `GPUDestinationReadPlan`, `GPUPreparedMaterialProgramCompiler`, clip authorities, color authorities, task graph, preflight, and ownership ledger.
- Keep filter normalization typed and proof-producing; do not add a generic lazy-image abstraction.
- Never sample the texture view currently bound as a writable attachment.
- A refusal after prepared-route admission is terminal: zero native allocation, zero queue write, zero encoding, zero submit, and no immediate or CPU-texture continuation.
- Keep all intermediate resources frame-owned in FP-07; persistent residency belongs to FP-09.
- Keep the product gate closed until Task 20.
- Preserve FP-04 image, FP-05 text/emoji, and FP-06 vertices/mesh behavior.
- Do not regenerate GM renders or scores before FP-11.
- If wgpu4k or wgsl4k behavior contradicts its public contract, minimize the evidence and report it upstream; do not add a hidden workaround.
- Do not add `.superpowers/sdd/` to commits.
- Run shell commands through `rtk`; run Gradle through `rtk proxy ./gradlew`.
- Every task uses TDD, ends with focused green tests, `rtk git diff --check`, and one reviewable commit.

---

## Prerequisite Gate

Before Task 1, run:

```bash
rtk git status --short --branch
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.(text|vertices)" \
  kanvas/src/main gpu-renderer/src/main font
rtk proxy ./gradlew \
  :font:core:test :font:glyph:test :font:gpu-api:test :font:test \
  :gpu-renderer:test :kanvas:test --no-parallel
```

Expected:

- the worktree is clean;
- FP-04, FP-05, and FP-06 closure evidence is committed;
- production contains no legacy text or vertices family;
- the accepted prepared-image, text, emoji, and vertices aggregates are green;
- no concurrent worker is modifying the target branch.

If the gate is not satisfied, stop. Do not adapt FP-07 around an unfinished
child-draw interface.

---

## File And Interface Map

### Typed filter and composite authorities

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterDescriptors.kt`
  - exact node kinds, typed parameters, graph edges, and graph identity.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterRefusalCodes.kt`
  - one canonical source for prepared filter and mask-filter refusals.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterNormalizer.kt`
  - proof-producing metadata folding and materialization boundaries.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeContracts.kt`
  - immutable scope tree, ordered entries, and composite identity.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeRefusalCodes.kt`
  - canonical picture, layer, and native-composite refusals.

### Surface capture, lowering, and inventory

- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCapture.kt`
  - transactional picture expansion and balanced scope capture.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageFilterLowerer.kt`
  - public `ImageFilter` to typed DAG mapping.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedMaskFilterLowerer.kt`
  - public `MaskFilter` to canonical coverage plans.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeLowerer.kt`
  - scope, child-draw, layer, filter, and restore orchestration.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedCompositeFrameInventory.kt`
  - exact frame-local deduplication and budget accounting.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFramePreparer.kt`
  - pure capture/lowering/inventory entry point.

### Filter execution programs

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBasicFilterPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMultiInputFilterPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBlurMorphologyPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedLightingPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedSampledKernelPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedRuntimeFilterPlanner.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMaskFilterPlanner.kt`.
- Create focused WGSL files under
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`.

### Payload, resources, tasks, and execution

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedCompositePayload.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUCompositeFrameResourcePlan.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedCompositeMaterializer.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`.

### Product integration

- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`.
- Remove superseded branches from
  `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`,
  `GPUImageFilterDispatch.kt`, and `GPUMaskBlurDispatch.kt` only after the
  product cutover is green.

### Stable interfaces carried across tasks

```kotlin
@JvmInline
value class GPUPreparedFilterNodeId(val value: String)

enum class GPUPreparedFilterKind {
    Crop,
    Blur,
    DropShadow,
    ColorFilter,
    Compose,
    Blend,
    Dilate,
    Erode,
    DistantLitDiffuse,
    PointLitDiffuse,
    SpotLitDiffuse,
    DistantLitSpecular,
    PointLitSpecular,
    SpotLitSpecular,
    Offset,
    Tile,
    Merge,
    DisplacementMap,
    Picture,
    Magnifier,
    MatrixConvolution,
    RuntimeEffect,
}

sealed interface GPUPreparedFilterInputRef {
    data object ImplicitSource : GPUPreparedFilterInputRef
    data object TransparentBlack : GPUPreparedFilterInputRef
    data class Node(val id: GPUPreparedFilterNodeId) : GPUPreparedFilterInputRef
    data class Picture(val pictureIdentity: String) : GPUPreparedFilterInputRef
    data class Backdrop(val destinationPlanIdentity: String) : GPUPreparedFilterInputRef
}

sealed interface GPUPreparedFilterParameters

data class GPUPreparedFilterNode(
    val id: GPUPreparedFilterNodeId,
    val kind: GPUPreparedFilterKind,
    val inputs: List<GPUPreparedFilterInputRef>,
    val parameters: GPUPreparedFilterParameters,
    val provenance: String,
)

data class GPUPreparedFilterGraph(
    val nodes: List<GPUPreparedFilterNode>,
    val output: GPUPreparedFilterInputRef,
    val identity: String,
)

data class GPUPreparedFilterRewriteProof(
    val rule: String,
    val sourceNodeIds: List<GPUPreparedFilterNodeId>,
    val resultNodeIds: List<GPUPreparedFilterNodeId>,
    val removedIntermediateCount: Int,
    val inputBoundsIdentity: String,
    val outputBoundsIdentity: String,
)

data class GPUPreparedFilterNormalization(
    val graph: GPUPreparedFilterGraph,
    val rewrites: List<GPUPreparedFilterRewriteProof>,
    val materializationNodeIds: Set<GPUPreparedFilterNodeId>,
)

@JvmInline
value class GPUPreparedCompositeScopeId(val value: String)

sealed interface GPUPreparedCompositeEntry {
    data class Draw(val operationIndex: Int) : GPUPreparedCompositeEntry
    data class Scope(val id: GPUPreparedCompositeScopeId) : GPUPreparedCompositeEntry
}

data class GPUPreparedCompositeScope(
    val id: GPUPreparedCompositeScopeId,
    val parentId: GPUPreparedCompositeScopeId?,
    val saveOperationIndex: Int?,
    val restoreOperationIndex: Int?,
    val entries: List<GPUPreparedCompositeEntry>,
    val sourceKind: GPUPreparedCompositeScopeKind,
    val provenance: String,
)

enum class GPUPreparedCompositeScopeKind {
    Root,
    SaveLayer,
    PaintedPicture,
    FilterPictureSource,
}

data class GPUPreparedCapturedOperation(
    val sourceOperationIndex: Int,
    val snapshot: DisplayOp,
    val identity: String,
)

data class GPUPreparedCompositeCapture(
    val rootScopeId: GPUPreparedCompositeScopeId,
    val scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    val expandedOperations: List<GPUPreparedCapturedOperation>,
    val identity: String,
)

sealed interface GPUPreparedCompositeCaptureResult {
    data class Ready(val capture: GPUPreparedCompositeCapture) :
        GPUPreparedCompositeCaptureResult

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedCompositeCaptureResult
}

sealed interface GPUPreparedImageFilterLowering {
    data class Ready(val graph: GPUPreparedFilterGraph) :
        GPUPreparedImageFilterLowering

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedImageFilterLowering
}

data class GPUPreparedCompositePlan(
    val captureIdentity: String,
    val rootScopeId: GPUPreparedCompositeScopeId,
    val layers: List<GPULayerPlan>,
    val normalizedFilters: Map<GPUPreparedCompositeScopeId, GPUPreparedFilterNormalization>,
    val identity: String,
)

sealed interface GPUPreparedCompositeLowering {
    data class Ready(val plan: GPUPreparedCompositePlan) :
        GPUPreparedCompositeLowering

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedCompositeLowering
}

data class GPUPreparedMaskFilterPlan(
    val kind: String,
    val coverageFormat: String,
    val executionIdentity: String,
    val tableEntries: List<Int> = emptyList(),
)

sealed interface GPUPreparedMaskFilterLowering {
    data class Ready(val plan: GPUPreparedMaskFilterPlan) :
        GPUPreparedMaskFilterLowering

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedMaskFilterLowering
}

sealed interface GPUPreparedCompositePreparation {
    data class Ready(val inventory: PreparedCompositeFrameInventory) :
        GPUPreparedCompositePreparation

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedCompositePreparation
}
```

The implementations may add exact fields, but later tasks must consume these
names and meanings. Renaming requires updating this plan and all dependent
tasks in the same reviewed commit.

---

### Task 1: Canonical typed graph, scope, and refusal authorities

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterDescriptors.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterRefusalCodes.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeContracts.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeRefusalCodes.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/FilterContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterDescriptorsTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeContractsTest.kt`

**Interfaces:**

- Consumes: existing `GPUFilterPlan`, `GPULayerPlan`, and structured diagnostics.
- Produces: the stable interfaces listed above and canonical refusal-code sets.

- [ ] **Step 1: Write failing immutability, identity, and refusal tests**

```kotlin
@Test
fun `graph snapshots mutable kernel input and identity changes by exact bits`() {
    val kernel = floatArrayOf(1f, 2f, 3f, 4f)
    val first = matrixConvolutionNode("n0", kernel)
    kernel[0] = 99f
    val second = matrixConvolutionNode("n0", floatArrayOf(1f, 2f, 3f, 4f))
    assertEquals(first.parameters, second.parameters)
    assertEquals(first.canonicalIdentity(), second.canonicalIdentity())
}

@Test
fun `prepared refusal authorities contain no duplicate code`() {
    val all = GPUPreparedFilterRefusalCodes.ALL +
        GPUPreparedCompositeRefusalCodes.ALL
    assertEquals(all.size, all.toSet().size)
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedFilterDescriptorsTest" \
  --tests "*.GPUPreparedCompositeContractsTest" --no-parallel
```

Expected: compilation fails because the typed authorities do not exist.

- [ ] **Step 3: Implement immutable descriptors and canonical identities**

Implement the stable interfaces above. Parameter classes containing
`FloatArray`, `ByteArray`, lists, or maps must copy on construction, compare by
content, and encode floats with `toRawBits()`. Add canonical codes from the
design spec as constants and expose immutable `ALL` sets. `GPUFilterPlan`
references the typed graph identity; it must not compute identity from
`nodeKind: String`.

```kotlin
object GPUPreparedFilterRefusalCodes {
    const val GRAPH_CYCLE = "unsupported.filter.graph.cycle"
    const val GRAPH_BUDGET = "unsupported.filter.graph.budget"
    const val PARAMETER_NON_FINITE = "unsupported.filter.parameter.non_finite"
    const val BOUNDS_OVERFLOW = "unsupported.filter.bounds.overflow"
    const val INTERMEDIATE_BUDGET = "unsupported.filter.intermediate.budget"

    val ALL: Set<String> = setOf(
        GRAPH_CYCLE,
        GRAPH_BUDGET,
        PARAMETER_NON_FINITE,
        BOUNDS_OVERFLOW,
        INTERMEDIATE_BUDGET,
    )
}
```

- [ ] **Step 4: Run tests and contract regressions**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedFilterDescriptorsTest" \
  --tests "*.GPUPreparedCompositeContractsTest" \
  --tests "*.GPUFilterDAGExecutorTest" \
  --tests "*.SaveLayerExecutorTest" --no-parallel
rtk git diff --check
```

Expected: all selected tests pass and the diff is clean.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): add prepared composite authorities"
```

---

### Task 2: Transactional picture expansion and balanced scope capture

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCapture.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCaptureTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`

**Interfaces:**

- Consumes: `List<DisplayOp>`, `GPUPreparedCompositeScope`, and picture transform/clip helpers.
- Produces: `capture(operations, limits): GPUPreparedCompositeCaptureResult`.

- [ ] **Step 1: Write failing transactional capture tests**

```kotlin
@Test
fun `painted picture becomes one synthetic child scope`() {
    val result = capture(listOf(drawPaintedPicture(twoRectPicture())))
    val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
    val child = ready.capture.scopes.values.single {
        it.sourceKind == GPUPreparedCompositeScopeKind.PaintedPicture
    }
    assertEquals(2, child.entries.size)
    assertNotNull(child.saveOperationIndex)
    assertNotNull(child.restoreOperationIndex)
}

@Test
fun `unmatched restore refuses without partial expansion`() {
    val result = capture(listOf(DisplayOp.EndLayer))
    val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
    assertEquals("unsupported.composite.layer.unbalanced", refused.code)
    assertEquals(0, refused.operationIndex)
}
```

Also cover nested pictures, cycles, transform composition, outer/child clip
intersection, recursion budget, expanded-operation budget, and unclosed layer.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeCaptureTest" --no-parallel
```

Expected: compilation fails because the capture entry point is absent.

- [ ] **Step 3: Implement pure capture**

```kotlin
internal object GPUPreparedCompositeCapturer {
    fun capture(
        operations: List<DisplayOp>,
        limits: GPUPreparedCompositeCaptureLimits,
    ): GPUPreparedCompositeCaptureResult
}
```

Use an explicit scope stack. Expand unpainted pictures inline. Wrap painted
pictures in a synthetic scope and keep their paint on the scope record.
Detect picture recursion by stable picture identity on the active recursion
stack. Build temporary mutable collections locally, then publish copied,
unmodifiable lists/maps only after the entire stream succeeds. Construct each
`GPUPreparedCapturedOperation.snapshot` with a per-`DisplayOp` deep-copy helper
that copies paints, arrays, kernels, tables, uniforms, and child collections.

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeCaptureTest" \
  --tests "*.PictureTest" --no-parallel
rtk git diff --check
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): capture prepared composite scopes"
```

---

### Task 3: Exhaustive public ImageFilter lowering

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageFilterLowerer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageFilterLowererTest.kt`

**Interfaces:**

- Consumes: public `ImageFilter`, typed node descriptors, material mapper, and registered runtime-effect resolver.
- Produces: `lower(filter, sourceRole, provenance): GPUPreparedImageFilterLowering`.

- [ ] **Step 1: Write a failing exhaustive kind matrix**

```kotlin
@TestFactory
fun `every public image filter maps to its canonical typed kind`() =
    publicImageFilterCases().map { case ->
        DynamicTest.dynamicTest(case.label) {
            val ready = assertIs<GPUPreparedImageFilterLowering.Ready>(
                lowerer.lower(case.filter, GPUPreparedFilterInputRef.ImplicitSource, case.label),
            )
            assertEquals(case.expectedKinds, ready.graph.nodes.map { it.kind }.toSet())
        }
    }
```

`publicImageFilterCases()` must contain exactly: Crop, Blur, DropShadow,
ColorFilter, Compose, Blend, Dilate, Erode, all six lighting filters, Offset,
Tile, Merge, DisplacementMap, Picture, Magnifier, MatrixConvolution, and
RuntimeEffect. Add tests for deterministic map order, mutable kernel snapshot,
implicit source rules, invalid finite values, malformed kernel dimensions, and
graph depth/node budgets.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedImageFilterLowererTest" --no-parallel
```

Expected: compilation fails because the lowerer and parameter descriptors are
not implemented.

- [ ] **Step 3: Implement recursive lowering**

Use one monotonic graph-local node-ID issuer. Lower children before parents,
emit nodes in deterministic topological order, and map a null input to
`ImplicitSource`. Snapshot kernels, uniforms, maps, and child lists. Reject
cycles by active object identity only for traversal safety; never encode object
identity in the result. Runtime effects retain their exact registered effect
ID, uniforms, shader child name, and deterministically ordered child-filter
slots.

```kotlin
internal class GPUPreparedImageFilterLowerer {
    fun lower(
        filter: ImageFilter,
        source: GPUPreparedFilterInputRef,
        provenance: String,
    ): GPUPreparedImageFilterLowering
}
```

- [ ] **Step 4: Run tests and public API regressions**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedImageFilterLowererTest" \
  --tests "*.ImageFilterTest" --no-parallel
rtk git diff --check
```

Expected: every current public kind maps, invalid input refuses stably, and
existing public tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): lower public image filter graphs"
```

---

### Task 4: Layer bounds, restore, and child-draw lowering

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/LayerContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/SaveLayerExecutor.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeLowererTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/SaveLayerExecutorTest.kt`

**Interfaces:**

- Consumes: captured scope tree, child prepared draws, target facts, filter graphs, clip plans, color plans, and blend plans.
- Produces: `GPUPreparedCompositePlan` with exact `GPULayerPlan` per scope.

- [ ] **Step 1: Write failing semantic-boundary tests**

```kotlin
@Test
fun `bounds hint does not clip required blur halo`() {
    val plan = lower(layerWithBlur(boundsHint = rect(10f, 10f, 20f, 20f), sigma = 4f))
    val ready = assertIs<GPUPreparedCompositeLowering.Ready>(plan)
    val bounds = ready.plan.layers.single().bounds
    assertTrue(bounds.width > 10)
    assertTrue(bounds.height > 10)
}

@Test
fun `restore alpha and blend remain group operations`() {
    val ready = assertIs<GPUPreparedCompositeLowering.Ready>(
        lower(overlappingChildrenLayer(alpha = 0.5f, blend = BlendMode.SRC_OVER)),
    )
    assertEquals(0.5f, ready.plan.layers.single().restore.alpha)
    assertEquals("SRC_OVER", ready.plan.layers.single().restore.blendMode)
}
```

Also cover nested ordering, creation clip versus restore clip, source bounds,
backdrop bounds, F16 request, init-with-previous, empty-source filter, and
direct-to-parent proof refusal.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedCompositeLowererTest" \
  --tests "*.SaveLayerExecutorTest" --no-parallel
```

Expected: RED because no product composite lowerer exists.

- [ ] **Step 3: Implement exact layer lowering**

Construct a `GPULayerPlan` for every non-root scope. Preserve separate bounds
for hint, children, filter input/output, backdrop, target, composite, and
restore clip. Reuse the existing clip, blend, color, destination, and layer
planners. Lower every child through the accepted prepared family authority; a
child refusal refuses the enclosing scope.

```kotlin
internal class GPUPreparedCompositeLowerer {
    fun lower(
        capture: GPUPreparedCompositeCapture,
        childDraws: Map<Int, GPUDrawSemanticPayload>,
        target: GPUTargetFacts,
        limits: GPUPreparedCompositeLimits,
    ): GPUPreparedCompositeLowering
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedCompositeLowererTest" \
  --tests "*.SaveLayerExecutorTest" \
  --tests "*.SaveLayerIsolatedTargetGateTest" --no-parallel
rtk git diff --check
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test \
  kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): lower prepared layer semantics"
```

---

### Task 5: Proof-producing filter normalization and fusion

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterNormalizer.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterNormalizerTest.kt`

**Interfaces:**

- Consumes: `GPUPreparedFilterGraph`, exact bounds, color facts, and sampling facts.
- Produces: `GPUPreparedFilterNormalization`.

- [ ] **Step 1: Write failing rewrite and boundary tests**

```kotlin
@Test
fun `offset crop and color matrix fold without an intermediate`() {
    val normalized = normalize(graphOf(offset(2f, 3f), crop(rect), colorMatrix(matrix)))
    assertEquals(0, normalized.materializationNodeIds.size)
    assertEquals(listOf("compose-offset", "intersect-crop", "fold-color-filter"),
        normalized.rewrites.map { it.rule })
}

@Test
fun `blur and two-input blend remain materialization boundaries`() {
    val normalized = normalize(graphOf(blur(3f), blend(source(), node("blur"))))
    assertEquals(setOf(nodeId("blur"), nodeId("blend")), normalized.materializationNodeIds)
}
```

Cover identity removal, adjacent offset composition, crop intersection,
compatible color-matrix composition, incompatible color order, tile/sampling
barriers, color-space barriers, backdrop barriers, read/write hazards, and
proof identity.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedFilterNormalizerTest" --no-parallel
```

Expected: compilation fails because the normalizer is absent.

- [ ] **Step 3: Implement the closed rewrite table**

Use an explicit `when` over adjacent typed kinds. Each accepted rewrite returns
the replacement node metadata and `GPUPreparedFilterRewriteProof`. Unknown
pairs are not rewritten. Mark blur, morphology, lighting, convolution,
magnifier, displacement, multi-input, backdrop, color-space, sampling-order,
and hazard nodes as materialization boundaries.

```kotlin
class GPUPreparedFilterNormalizer {
    fun normalize(
        graph: GPUPreparedFilterGraph,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
        colorFacts: GPUFilterColorPlan,
    ): GPUPreparedFilterNormalization
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedFilterNormalizerTest" \
  --tests "*.GPUFilterDAGExecutorTest" --no-parallel
rtk git diff --check
```

Expected: deterministic normalized graphs and proofs pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): normalize prepared filter graphs"
```

---

### Task 6: Crop, offset, tile, color-filter, and compose execution

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBasicFilterPlanner.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedFilterSampleWgsl.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBasicFilterPlannerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedFilterSampleWgslTest.kt`

**Interfaces:**

- Consumes: normalized Crop, Offset, Tile, ColorFilter, and Compose nodes.
- Produces: exact folded metadata or `GPUFilterRenderNodePlan`.

- [ ] **Step 1: Write failing route and WGSL reflection tests**

```kotlin
@TestFactory
fun `basic filters choose folded or sampled route`() = listOf(
    case(crop(), expected = GPUPreparedBasicFilterRoute.Folded),
    case(offset(), expected = GPUPreparedBasicFilterRoute.Folded),
    case(tile(TileMode.REPEAT), expected = GPUPreparedBasicFilterRoute.Render),
    case(colorMatrix(), expected = GPUPreparedBasicFilterRoute.Folded),
    case(compose(offset(), tile()), expected = GPUPreparedBasicFilterRoute.Render),
).map { it.dynamicTest(planner) }

@Test
fun `sample program reflection matches the packed uniform ABI`() {
    val reflected = validateWgsl(PREPARED_FILTER_SAMPLE_WGSL)
    assertEquals(PreparedFilterSampleUniforms.SIZE_BYTES, reflected.uniformSize)
    assertEquals(listOf(0, 1, 2), reflected.bindings.map { it.binding })
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedBasicFilterPlannerTest" \
  --tests "*.PreparedFilterSampleWgslTest" --no-parallel
```

Expected: RED because the planner and WGSL program are absent.

- [ ] **Step 3: Implement exact basic routes**

Fold pixel-aligned crop/offset and compatible color-filter work into normalized
metadata. Use the sampled render program for non-trivial tile modes, sampling,
or color operations that cannot remain metadata. Pack source/destination rect,
transform, tile modes, alpha, and color parameters from parser-reflected
offsets; do not hard-code a second ABI.

```kotlin
enum class GPUPreparedBasicFilterRoute {
    Folded,
    Render,
}

data class GPUPreparedBasicFilterPlan(
    val route: GPUPreparedBasicFilterRoute,
    val renderNode: GPUFilterRenderNodePlan?,
    val identity: String,
)

class GPUPreparedBasicFilterPlanner {
    fun plan(node: GPUPreparedFilterNode): GPUPreparedBasicFilterPlan
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedBasicFilterPlannerTest" \
  --tests "*.PreparedFilterSampleWgslTest" \
  --tests "*.GPUFilterTileTest" \
  --tests "*.ColorMatrixFilterTest" --no-parallel
rtk git diff --check
```

Expected: folded routes create no intermediate and sampled routes have a valid
pipeline/binding plan.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): plan prepared basic filters"
```

---

### Task 7: Blend, merge, and picture-source filters

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMultiInputFilterPlanner.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedFilterBlendWgsl.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeLowerer.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMultiInputFilterPlannerTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedFilterPictureSourceTest.kt`

**Interfaces:**

- Consumes: normalized Blend, Merge, and Picture nodes plus prepared picture capture.
- Produces: ordered multi-input render nodes and picture-source layer plans.

- [ ] **Step 1: Write failing input-order and source tests**

```kotlin
@Test
fun `blend preserves background and foreground roles`() {
    val plan = planBlend(BlendMode.SRC_OVER, background("a"), foreground("b"))
    val ready = assertIs<GPUPreparedMultiInputRoute.Ready>(plan)
    assertEquals(listOf("background:a", "foreground:b"), ready.bindingRoles)
}

@Test
fun `picture filter renders only the reverse-required source bounds`() {
    val ready = preparePictureFilter(checkerPicture(), desired = pixelBounds(8, 8, 24, 24))
    assertEquals(pixelBounds(8, 8, 24, 24), ready.pictureTarget.bounds)
    assertEquals(GPUPreparedCompositeScopeKind.FilterPictureSource, ready.scope.sourceKind)
}
```

Also cover all accepted blend modes, shader blend routes, Merge
input order, empty Merge, repeated input deduplication without lost bindings,
picture transform/clip, and cyclic picture refusal.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedMultiInputFilterPlannerTest" \
  --tests "*.GPUPreparedFilterPictureSourceTest" --no-parallel
```

Expected: RED because multi-input and picture-source planners are absent.

- [ ] **Step 3: Implement multi-input plans**

Map fixed-function-compatible Blend modes to the existing blend authority.
For complex modes, sample the background and foreground inputs in a two-input
WGSL node writing a distinct output. `ImageFilter.Blend` does not observe the
parent target and therefore does not issue a `GPUDestinationReadPlan`. Merge
draws inputs in public list order with transparent-black initial contents.
Picture creates a filter-owned prepared scope and target using reverse-required
bounds; it never invokes picture replay from the legacy renderer.

```kotlin
sealed interface GPUPreparedMultiInputRoute {
    data class Ready(
        val bindingRoles: List<String>,
        val renderNodes: List<GPUFilterRenderNodePlan>,
        val blendPlan: GPUBlendPlan,
    ) : GPUPreparedMultiInputRoute

    data class Refused(val diagnostic: GPUFilterDiagnostic) :
        GPUPreparedMultiInputRoute
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedMultiInputFilterPlannerTest" \
  --tests "*.GPUPreparedFilterPictureSourceTest" \
  --tests "*.GPUAllApiBlendSurfaceTest" --no-parallel
rtk git diff --check
```

Expected: input roles and order remain exact, and blend regressions pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test \
  kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): plan prepared multi-input filters"
```

---

### Task 8: Blur, drop-shadow, dilate, and erode filters

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBlurMorphologyPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/BlurFilter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskBlurPlan.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedBlurMorphologyPlannerTest.kt`

**Interfaces:**

- Consumes: Blur, DropShadow, Dilate, and Erode nodes.
- Produces: ordered separable blur, shadow DAG, and morphology pass plans.

- [ ] **Step 1: Write failing kernel, bounds, and pass tests**

```kotlin
@Test
fun `drop shadow expands into alpha blur color offset and source composite`() {
    val ready = assertIs<GPUPreparedFilterNodeExecution.Ready>(
        planner.plan(dropShadow(dx = 4f, dy = -2f, sigmaX = 3f, sigmaY = 5f)),
    )
    assertEquals(
        listOf("extract-alpha", "blur-x", "blur-y", "colorize", "offset", "src-over-source"),
        ready.passes.map { it.kind },
    )
}

@TestFactory
fun `morphology radii produce bounded passes`() = listOf(
    dilate(0f, 0f) to 0,
    dilate(3f, 5f) to 2,
    erode(3f, 5f) to 2,
).map { (node, count) -> dynamicPassCountTest(node, count) }
```

Cover non-finite and negative sigma/radius, zero identity, large sigma
downsampling policy, halo bounds, transparent edges, intermediate budget, and
exact pass dependencies.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedBlurMorphologyPlannerTest" --no-parallel
```

Expected: RED because the common prepared planner is absent.

- [ ] **Step 3: Integrate existing native authorities**

Use the existing Gaussian blur, mask blur, drop-shadow, and morphology
authorities as the only kernel implementations. Build typed pass plans around
them. Preserve exact public tile mode, blur radii, color, offset, and source
composition order. Do not copy their math into the Surface module.

```kotlin
data class GPUPreparedFilterPass(
    val kind: String,
    val route: GPUFilterNodeRoute,
    val dependencies: List<String>,
)

sealed interface GPUPreparedFilterNodeExecution {
    data class Ready(val passes: List<GPUPreparedFilterPass>) :
        GPUPreparedFilterNodeExecution

    data class Refused(val diagnostic: GPUFilterDiagnostic) :
        GPUPreparedFilterNodeExecution
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedBlurMorphologyPlannerTest" \
  --tests "*.GaussianBlurFilterTest" \
  --tests "*.MaskBlurPlanTest" --no-parallel
rtk git diff --check
```

Expected: all selected tests pass and no second blur authority exists.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): plan prepared blur and morphology"
```

---

### Task 9: All diffuse and specular lighting filters

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedLightingPlanner.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedLightingFilterWgsl.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedLightingPlannerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedLightingFilterWgslTest.kt`

**Interfaces:**

- Consumes: all six typed lighting kinds and exact light parameters.
- Produces: parser-reflected native lighting render plans.

- [ ] **Step 1: Write a failing six-kind matrix**

```kotlin
@TestFactory
fun `all public lighting filters select exact light and response modes`() =
    lightingCases().map { case ->
        DynamicTest.dynamicTest(case.label) {
            val ready = assertIs<GPUPreparedLightingRoute.Ready>(planner.plan(case.node))
            assertEquals(case.lightKind, ready.uniforms.lightKind)
            assertEquals(case.responseKind, ready.uniforms.responseKind)
        }
    }
```

The matrix contains distant/point/spot crossed with diffuse/specular. Add
tests for normalized distant direction, point position, spot target, cutoff,
exponent, surface scale, `kd`, `ks`, shininess, alpha output, finite values,
normal-map sampling halo, and exact uniform offsets.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedLightingPlannerTest" \
  --tests "*.PreparedLightingFilterWgslTest" --no-parallel
```

Expected: RED because the common program is absent.

- [ ] **Step 3: Implement one reflected program with typed modes**

Reuse existing lighting math. Use typed integer modes only at the reflected
ABI boundary; the planner remains enum-based. Validate every vector and scalar
before packing. Compute normals from the planned source alpha neighborhood and
encode the exact diffuse or specular response.

```kotlin
enum class GPUPreparedLightKind {
    Distant,
    Point,
    Spot,
}

enum class GPUPreparedLightingResponse {
    Diffuse,
    Specular,
}

data class GPUPreparedLightingUniforms(
    val lightKind: GPUPreparedLightKind,
    val responseKind: GPUPreparedLightingResponse,
    val packedBytes: List<Byte>,
)

sealed interface GPUPreparedLightingRoute {
    data class Ready(
        val uniforms: GPUPreparedLightingUniforms,
        val renderNode: GPUFilterRenderNodePlan,
    ) : GPUPreparedLightingRoute

    data class Refused(val diagnostic: GPUFilterDiagnostic) :
        GPUPreparedLightingRoute
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedLightingPlannerTest" \
  --tests "*.PreparedLightingFilterWgslTest" --no-parallel
rtk git diff --check
```

Expected: all six kinds pass parser, ABI, and planner tests.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): plan prepared lighting filters"
```

---

### Task 10: Displacement, magnifier, and matrix convolution

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedSampledKernelPlanner.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedDisplacementFilterWgsl.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedMagnifierFilterWgsl.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedConvolutionFilterWgsl.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedSampledKernelPlannerTest.kt`

**Interfaces:**

- Consumes: DisplacementMap, Magnifier, and MatrixConvolution nodes.
- Produces: native sampled/kernel node plans with immutable parameter uploads.

- [ ] **Step 1: Write failing parameter and ABI tests**

```kotlin
@Test
fun `displacement binds map and color inputs in exact roles`() {
    val ready = assertIs<GPUPreparedSampledKernelRoute.Ready>(
        planner.plan(displacement(x = ColorChannel.R, y = ColorChannel.A, scale = 12f)),
    )
    assertEquals(listOf("displacement", "color"), ready.sampledBindings.map { it.role })
}

@Test
fun `convolution snapshots kernel and preserves convolve-alpha`() {
    val kernel = floatArrayOf(0f, 1f, 0f, 1f, -4f, 1f, 0f, 1f, 0f)
    val ready = planConvolution(kernel, convolveAlpha = false)
    kernel[4] = 99f
    assertEquals(-4f.toRawBits(), ready.kernel.rawBits[4])
    assertFalse(ready.uniforms.convolveAlpha)
}
```

Cover all displacement channel pairs, scale zero, magnifier source/zoom/inset,
kernel dimension/product validation, gain, bias, offset, tile modes, alpha
policy, kernel upload alignment, sample radius, and budgets.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedSampledKernelPlannerTest" --no-parallel
```

Expected: RED because the common planner and programs are absent.

- [ ] **Step 3: Implement exact native plans**

Reuse existing displacement support. Magnifier maps device output to its
source rect with the public zoom/inset contract. Convolution uploads immutable
float coefficients to an aligned read-only buffer and uses exact kernel offset,
gain, bias, tile mode, and alpha mode. Use separable passes only when exact
kernel factorization is proven.

```kotlin
data class GPUPreparedFilterBinding(
    val role: String,
    val resourceIdentity: String,
)

data class GPUPreparedKernelUpload(
    val rawBits: List<Int>,
    val byteSize: Int,
    val identity: String,
)

data class GPUPreparedSampledKernelUniforms(
    val packedBytes: List<Byte>,
    val convolveAlpha: Boolean,
)

sealed interface GPUPreparedSampledKernelRoute {
    data class Ready(
        val sampledBindings: List<GPUPreparedFilterBinding>,
        val kernel: GPUPreparedKernelUpload?,
        val uniforms: GPUPreparedSampledKernelUniforms,
        val renderNodes: List<GPUFilterRenderNodePlan>,
    ) : GPUPreparedSampledKernelRoute

    data class Refused(val diagnostic: GPUFilterDiagnostic) :
        GPUPreparedSampledKernelRoute
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedSampledKernelPlannerTest" \
  --tests "*.GPUFilterDAGExecutorTest" --no-parallel
rtk git diff --check
```

Expected: planner, snapshot, bounds, and ABI tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): plan prepared sampled kernel filters"
```

---

### Task 11: Registered runtime image-filter execution

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedRuntimeFilterPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedRuntimeFilterPlannerTest.kt`

**Interfaces:**

- Consumes: typed RuntimeEffect node and the common registered descriptor authority.
- Produces: `GPUFilterRuntimeEffectPlan` with exact WGSL, ABI, uniforms, children, bindings, and sample radius.

- [ ] **Step 1: Write failing three-state and child-schema tests**

```kotlin
@TestFactory
fun `runtime filter registry states are terminal and explicit`() = listOf(
    runtimeCase(unregistered(), "unsupported.filter.runtime_effect.descriptor"),
    runtimeCase(registeredWithoutWgsl(), "unsupported.filter.runtime_effect.wgsl_not_available"),
    runtimeCase(registeredValid(), null),
).map { it.dynamicTest(planner) }

@Test
fun `runtime filter rejects missing and extra child slots`() {
    assertRefused("unsupported.filter.runtime_effect.child",
        planRuntime(descriptorWithChildren("source", "mask"), supplied = mapOf("source" to source())))
}
```

Also cover Kotlin/CPU behavior presence, parser failure, entry point, uniform
reflection, ABI hash, shader child, image-filter children, deterministic map
order, sample radius, color/alpha contract, and package boundary.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedRuntimeFilterPlannerTest" --no-parallel
```

Expected: RED because filter-specific registered planning is absent.

- [ ] **Step 3: Implement by delegating to the common compiler**

Resolve the descriptor exactly once through
`KanvasPreparedRuntimeEffectResolver`. Reuse
`GPUPreparedMaterialProgramCompiler` for WGSL validation, uniform reflection,
and child compilation. Add only filter-specific source texture bindings,
sample radius, and output contract. Do not introduce a second registry or
placeholder shader.

```kotlin
class GPUPreparedRuntimeFilterPlanner(
    private val resolver: KanvasPreparedRuntimeEffectResolver,
    private val compiler: GPUPreparedMaterialProgramCompiler,
) {
    fun plan(node: GPUPreparedFilterNode): GPUFilterNodePlan
}
```

- [ ] **Step 4: Run focused and boundary tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedRuntimeFilterPlannerTest" \
  --tests "*.GPURendererPackageBoundaryTest" --no-parallel
rtk git diff --check
```

Expected: three states and exact child/ABI tests pass with no forbidden package
dependency.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): plan registered runtime filters"
```

---

### Task 12: Blur, shader, and table mask filters

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedMaskFilterLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMaskFilterPlanner.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedMaskTableWgsl.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/MaskFilterContracts.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedMaskFilterLowererTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMaskFilterPlannerTest.kt`

**Interfaces:**

- Consumes: public `MaskFilter`, family-specific canonical coverage, and prepared material compiler.
- Produces: one coverage-before-color plan for Blur, Shader, or Table.

- [ ] **Step 1: Write failing exhaustive coverage tests**

```kotlin
@TestFactory
fun `every public mask filter lowers to one canonical coverage plan`() = listOf(
    MaskFilter.Blur(BlurStyle.NORMAL, 3f),
    MaskFilter.Shader(linearGradientShader()),
    MaskFilter.Table(UByteArray(256) { it.toUByte() }),
).map { filter -> dynamicMaskLoweringTest(filter) }

@Test
fun `table snapshots exactly 256 coverage entries`() {
    val bytes = UByteArray(256) { it.toUByte() }
    val ready = assertIs<GPUPreparedMaskFilterLowering.Ready>(lower(bytes))
    bytes[0] = 255u
    assertEquals(0, ready.plan.tableEntries[0])
}
```

Cover four blur styles, zero sigma, invalid sigma, table sizes 0/255/257,
shader material success/refusal, runtime shader registration, transform,
coverage bounds, and the exact ordering coverage → mask → coloration → blend.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedMaskFilterLowererTest" \
  --tests "*.GPUPreparedMaskFilterPlannerTest" --no-parallel
```

Expected: RED because Shader and Table have no prepared authority.

- [ ] **Step 3: Implement one mask-filter authority**

Delegate Blur to `MaskBlurPlanner`. Compile Shader through the common prepared
material compiler and evaluate its scalar alpha as coverage. Snapshot exactly
256 Table bytes and bind them as one immutable lookup resource; index with
UNORM A8 coverage. All child families provide coverage to this planner instead
of branching by public mask-filter type.

```kotlin
data class GPUPreparedCoverageSource(
    val bounds: GPUPixelBounds,
    val resourceIdentity: String,
    val format: String,
)

internal class GPUPreparedMaskFilterLowerer {
    fun lower(filter: MaskFilter, provenance: String): GPUPreparedMaskFilterLowering
}

class GPUPreparedMaskFilterPlanner {
    fun plan(
        lowered: GPUPreparedMaskFilterPlan,
        sourceCoverage: GPUPreparedCoverageSource,
    ): GPUFilterNodePlan
}
```

- [ ] **Step 4: Run focused tests and text/core regressions**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*.GPUPreparedMaskFilterLowererTest" \
  --tests "*.GPUPreparedMaskFilterPlannerTest" \
  --tests "*.MaskBlurPlanTest" \
  --tests "*.GPUMaskBlurSurfaceTest" \
  --tests "*.GPUPreparedSurfaceFrameBuilderTextTest" --no-parallel
rtk git diff --check
```

Expected: all three public kinds pass and existing blur pixels remain green.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test \
  kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): prepare all mask filters"
```

---

### Task 13: Frame inventory, deduplication, and configurable budgets

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedCompositeFrameInventory.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFramePreparer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/PreparedCompositeFrameInventoryTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/RenderConfigCompositeLimitsTest.kt`

**Interfaces:**

- Consumes: capture, lowered layers, normalized graphs, child prepared resources, target limits, and `RenderConfig`.
- Produces: immutable `PreparedCompositeFrameInventory`.

- [ ] **Step 1: Write failing exact inventory and budget tests**

```kotlin
@Test
fun `identical immutable intermediates deduplicate within one frame`() {
    val ready = prepare(twoLayersUsingSameTableAndKernel())
    assertEquals(1, ready.inventory.tableUploads.size)
    assertEquals(1, ready.inventory.kernelUploads.size)
}

@Test
fun `raising configured intermediate budget accepts device-valid frame`() {
    val request = frameRequiringIntermediateBytes(96L * 1024 * 1024)
    assertRefused("unsupported.filter.intermediate.budget", prepare(request, maxBytes = 64L * MIB))
    assertIs<GPUPreparedCompositePreparation.Ready>(prepare(request, maxBytes = 128L * MIB))
}
```

Cover graph nodes/edges, picture recursion/ops, layer nesting/count, kernel
size/radius, texture dimensions, live-byte peak, destination-copy bytes,
coverage bytes, pass counts, dispatch dimensions, uniform bytes, device limit,
and overflow-safe arithmetic.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.PreparedCompositeFrameInventoryTest" --no-parallel
```

Expected: RED because inventory and limits are absent.

- [ ] **Step 3: Implement immutable inventory**

Use exact structural keys plus byte-content equality; never deduplicate on hash
alone. Calculate resource lifetimes and peak simultaneous bytes from task
intervals, not from a simple sum. Extend `RenderConfig` with named composite
limits and retain existing defaults where an equivalent limit already exists.
Configured increases remain bounded by native device limits.

```kotlin
internal object GPUPreparedCompositeFramePreparer {
    fun prepare(
        operations: List<DisplayOp>,
        childDraws: Map<Int, GPUDrawSemanticPayload>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
    ): GPUPreparedCompositePreparation
}
```

- [ ] **Step 4: Run focused tests**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.PreparedCompositeFrameInventoryTest" \
  --tests "*.RenderConfigCompositeLimitsTest" --no-parallel
rtk git diff --check
```

Expected: deduplication, raised-limit acceptance, and exact refusal facts pass.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): inventory prepared composites"
```

---

### Task 14: Composite semantic payloads, resources, and task graph

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedCompositePayload.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUCompositeFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedCompositePayloadTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedCompositeFrameTaskListBuilderTest.kt`

**Interfaces:**

- Consumes: inventory and exact child semantic payloads.
- Produces: closed composite payloads, resource plans, and ordered task list.

- [ ] **Step 1: Write failing snapshot and dependency tests**

```kotlin
@Test
fun `composite payload snapshots all node uniforms and resource references`() {
    val input = compositePayloadInput()
    val payload = GPUPreparedCompositePayloadGatherer().gather(input)
    input.mutableUniformBytes[0] = 99
    assertEquals(0, payload.filterNodes.single().uniformBytes[0])
}

@Test
fun `task graph orders allocate init children filters composite release`() {
    val tasks = buildTasks(nestedFilteredBackdropLayer()).tasks.map { it.label }
    assertEquals(
        listOf("allocate", "copy-backdrop", "initialize", "children",
            "filter-0", "filter-1", "composite", "release"),
        tasks,
    )
}
```

Cover exact resource operand identities, texture usages, layer nesting,
producer-before-consumer, release-after-last-use, read/write alias rejection,
child-family payload preservation, and deterministic task identity.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositePayloadTest" \
  --tests "*.GPUPreparedCompositeFrameTaskListBuilderTest" --no-parallel
```

Expected: RED because composite payloads and tasks are absent.

- [ ] **Step 3: Implement closed payload and task products**

Add a composite semantic payload variant that references the exact layer,
filter, child, destination, and resource identities. Build explicit
`AllocateTarget`, `ClearTarget`, `CopyDestination`, `RenderChild`,
`RunFilterRenderNode`, `RunFilterComputeNode`, `CompositeLayer`, and
`ReleaseIntermediate` tasks. Resource plans contain the union of every planned
WebGPU usage before materialization.

```kotlin
class GPUPreparedCompositePayloadGatherer {
    fun gather(input: GPUPreparedCompositePayloadInput):
        GPUDrawSemanticPayload.Composite
}

fun GPUPreparedSurfaceFrameTaskListBuilder.buildCompositeTasks(
    payload: GPUDrawSemanticPayload.Composite,
    resources: GPUCompositeFrameResourcePlan,
): GPUPreparedCompositeTaskResult
```

- [ ] **Step 4: Run focused and prepared-frame regressions**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositePayloadTest" \
  --tests "*.GPUPreparedCompositeFrameTaskListBuilderTest" \
  --tests "*.GPUPreparedSurfaceFrameTaskListBuilderTest" --no-parallel
rtk git diff --check
```

Expected: task dependencies and operand identities pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): build prepared composite tasks"
```

---

### Task 15: Backdrop, destination reads, and layer ordering

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/DestinationContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationReadExecutor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/LayerContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUPreparedCompositeDestinationReadTest.kt`

**Interfaces:**

- Consumes: exact layer/backdrop bounds, parent target generation, blend requirements, and task graph.
- Produces: copy/intermediate bindings and strict ordering tokens.

- [ ] **Step 1: Write failing destination correctness tests**

```kotlin
@Test
fun `backdrop copy happens after prior parent draw and before layer children`() {
    val graph = buildTasks(parentDrawThenBackdropLayer())
    assertBefore(graph, "parent-draw", "backdrop-copy")
    assertBefore(graph, "backdrop-copy", "layer-child")
}

@Test
fun `active writable attachment can never be sampled`() {
    val refusal = assertIs<GPUPreparedCompositeTaskResult.Refused>(
        buildTasks(compositeSamplingItsWritableAttachment()),
    )
    assertEquals("unsupported.composite.native.alias", refusal.code)
}
```

Cover exact read bounds, copy texture usages, destination generation, grouped
copies only when contents/bounds are equivalent, restore blend reads, nested
backdrops, init-with-previous, copy budgets, and no CPU snapshot.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositeDestinationReadTest" --no-parallel
```

Expected: existing generic destination contracts do not yet bind composite
ordering.

- [ ] **Step 3: Integrate the existing destination authority**

Issue one `GPUDestinationReadPlan` for each semantic read. Retain exact target
generation, bounds, strategy, copy descriptor, and binding token in the
composite payload. Add task dependencies from the final prior parent writer to
copy, from copy to consumer, and from consumer to later parent writes. Reject
all attachment/view aliasing before materialization.

```kotlin
data class GPUPreparedCompositeDestinationBinding(
    val plan: GPUDestinationReadPlan,
    val producerTaskId: String,
    val copyTaskId: String,
    val consumerTaskId: String,
    val targetGeneration: GPUDeviceGenerationID,
)
```

- [ ] **Step 4: Run focused destination and layer tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositeDestinationReadTest" \
  --tests "*.GPUDestinationReadExecutorTest" \
  --tests "*.GPUDestinationSnapshotGroupingTest" \
  --tests "*.SaveLayerExecutorTest" --no-parallel
rtk git diff --check
```

Expected: destination ordering, grouping, and alias guards pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): order prepared backdrop reads"
```

---

### Task 16: Full-frame native preflight and zero-side-effect refusal

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedCompositeNativePreflightTest.kt`

**Interfaces:**

- Consumes: complete composite task list, payloads, resource plans, pipeline/WGSL reflection, capabilities, and device generation.
- Produces: accepted exact materialization preimage or terminal diagnostic.

- [ ] **Step 1: Write a failing refusal matrix**

```kotlin
@TestFactory
fun `every composite preflight refusal has zero native side effects`() =
    compositePreflightRefusalCases().map { case ->
        DynamicTest.dynamicTest(case.label) {
            val probe = NativeSideEffectProbe()
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                preflight(case.request, probe),
            )
            assertEquals(case.code, refused.diagnostic.code.value)
            assertEquals(NativeSideEffectCounts.ZERO, probe.counts)
        }
    }
```

Cases include unbalanced scope, child refusal, graph cycle, missing node input,
invalid rewrite proof, bounds overflow, target usage mismatch, parser failure,
uniform size/alignment, binding mismatch, runtime child mismatch, destination
generation, aliasing, budget, task cycle, release-before-use, and operand
preimage mismatch.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositeNativePreflightTest" --no-parallel
```

Expected: RED because composite validation is incomplete.

- [ ] **Step 3: Extend preflight without native calls**

Validate the complete frame using pure descriptors only. Return a closed
materialization preimage containing exact pipeline, binding, resource,
generation, range, and task identities. Do not pass factories, queues, devices,
or native handles into validation helpers.

```kotlin
fun GPUPreparedSurfaceNativePreflight.preflightComposite(
    taskList: GPUTaskList,
    payload: GPUDrawSemanticPayload.Composite,
    resources: GPUCompositeFrameResourcePlan,
    capabilities: GPUCapabilities,
    generation: GPUDeviceGenerationID,
): GPUPreparedSurfaceNativePreflightResult
```

- [ ] **Step 4: Run focused and existing preflight tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUPreparedCompositeNativePreflightTest" \
  --tests "*.GPUPreparedSurfaceNativePreflightTest" --no-parallel
rtk git diff --check
```

Expected: all refusals are atomic and accepted plans retain exact identities.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): preflight prepared composites"
```

---

### Task 17: wgpu4k composite materializer and ownership

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedCompositeMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFrameEncodingBackend.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedCompositeMaterializerTest.kt`

**Interfaces:**

- Consumes: accepted materialization preimage only.
- Produces: one ordered native command buffer and ownership report.

- [ ] **Step 1: Write failing native-call sequence tests**

```kotlin
@Test
fun `materializer creates exact resources and closes every scope once`() {
    val backend = RecordingWgpuBackend()
    materialize(acceptedNestedComposite(), backend)
    assertEquals(
        listOf("createTexture", "createTexture", "beginRenderPass", "endRenderPass",
            "beginComputePass", "endComputePass", "beginRenderPass", "endRenderPass",
            "finish", "submit"),
        backend.lifecycleCalls,
    )
    assertEquals(backend.createdHandles.toSet(), backend.closedHandles.toSet())
}
```

Cover usage flags, views, samplers, uniform/table/kernel uploads, bind-group
layouts, copy commands, render/compute transitions, load/store operations,
scissors, child draws, composite draw, one submit, close-once, device
generation, and failure cleanup.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUWgpu4kPreparedCompositeMaterializerTest" --no-parallel
```

Expected: RED because no composite materializer exists.

- [ ] **Step 3: Implement operand-only materialization**

Create resources from preflight descriptors in task order. Use
`queue.writeBuffer` and texture writes only for exact planned immutable
payloads. Reuse accepted prepared child materializers inside the current target
scope. Close render/compute passes, encoders, temporary views, bind groups, and
frame-local textures exactly once. Do not recompute filters, bounds, or routes.

```kotlin
class GPUWgpu4kPreparedCompositeMaterializer(
    private val backend: GPUWgpu4kFrameEncodingBackend,
) {
    fun materialize(
        preflight: GPUPreparedSurfaceNativePreflightResult.Accepted,
        frame: GPUPreparedCompositeMaterializationInput,
    ): GPUPreparedCompositeMaterializationResult
}
```

- [ ] **Step 4: Run focused and ownership regressions**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*.GPUWgpu4kPreparedCompositeMaterializerTest" \
  --tests "*.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" \
  --tests "*.SaveLayerLiveMaterializationTest" \
  --tests "*.DestinationReadLiveMaterializationTest" --no-parallel
rtk git diff --check
```

Expected: call ordering, close-once, and exact ownership pass.

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): materialize prepared composites"
```

---

### Task 18: Deterministic fixtures and CPU oracles

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeTestFixtures.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedFilterCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedMaskFilterCpuOracle.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeTestFixturesTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedFilterCpuOracleTest.kt`

**Interfaces:**

- Consumes: immutable RGBA8/A8 fixtures and public filter descriptors.
- Produces: isolated fixture bytes, exact/raw geometry oracles, and sRGB-aware pixel comparisons.

- [ ] **Step 1: Write failing fixture isolation and known-vector tests**

```kotlin
@Test
fun `every fixture access returns isolated bytes`() {
    val first = fixtures.rgbaChecker()
    first[0] = 99
    assertEquals(0, fixtures.rgbaChecker()[0].toInt())
}

@Test
fun `srgb oracle applies decode sample premultiply tint and encode in order`() {
    val actual = oracle.sampleSrgbStraightToEncodedPremul(sample, tint)
    assertTrue(actual.matchesWithinOneLsb(expected))
}
```

Add known vectors for crop, offset, all tile modes, blur styles, drop shadow,
color matrix, compose, blend, morphology, six lighting kinds, merge,
displacement channels, picture, magnifier, convolution alpha modes, runtime
descriptor, mask shader, mask table, transparent halos, and nested restore.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeTestFixturesTest" \
  --tests "*.GPUPreparedFilterCpuOracleTest" --no-parallel
```

Expected: RED because fixtures and complete oracles are absent.

- [ ] **Step 3: Implement independent scalar oracles**

Keep raw nearest/linear helpers clearly named for geometry and clamp tests.
Implement GPU comparison oracles in linear working space with explicit sRGB
decode/encode, UNORM alpha, premultiplication, and tint ordering. Do not call
production filter planners or WGSL helpers from oracle math.

```kotlin
internal object GPUPreparedFilterCpuOracle {
    fun sampleRawLinearForGeometry(
        rgba: ByteArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
    ): IntArray

    fun sampleSrgbStraightToEncodedPremul(
        sample: IntArray,
        tint: FloatArray,
    ): IntArray
}
```

- [ ] **Step 4: Run oracle tests**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeTestFixturesTest" \
  --tests "*.GPUPreparedFilterCpuOracleTest" --no-parallel
rtk git diff --check
```

Expected: all fixtures self-validate and known vectors pass.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/test
rtk git commit -m "test(surface): add prepared composite oracles"
```

---

### Task 19: Native pixels, mixed frames, and refusal evidence

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeAllFiltersNativeTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeMixedFrameNativeTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeRefusalMatrixTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`

**Interfaces:**

- Consumes: complete prepared builder, preflight, materializer, and CPU oracles.
- Produces: native WebGPU acceptance evidence for every current public filter and mixed composite frames.

- [ ] **Step 1: Write failing native test matrices**

```kotlin
@TestFactory
fun `every public image and mask filter has native bounded pixel evidence`() =
    allPublicFilterNativeCases().map { case ->
        DynamicTest.dynamicTest(case.label) {
            val result = renderPrepared(case.frame)
            assertEquals(0, result.legacyCompositeInvocations)
            assertPixelsWithin(case.expected, result.pixels, case.maxChannelDelta)
        }
    }

@Test
fun `mixed frame preserves draw and scope order in one submission`() {
    val result = renderPrepared(coreImageTextEmojiVerticesNestedFilteredBackdropFrame())
    assertEquals(1, result.submitCount)
    assertEquals(0, result.legacyCompositeInvocations)
    assertPixelsWithinOneLsb(expectedMixedFrame(), result.pixels)
}
```

Add refusal cases for every canonical code and assert zero allocation/write/
encode/submit/fallback. Native cases include shapes, images, text, emoji,
vertices, meshes, unpainted picture, painted picture, nested saveLayer,
backdrop, destination-reading restore, and draws before/after scopes.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeAllFiltersNativeTest" \
  --tests "*.GPUPreparedCompositeMixedFrameNativeTest" \
  --tests "*.GPUPreparedCompositeRefusalMatrixTest" --no-parallel
```

Expected: product routing remains closed, so native acceptance tests fail.

- [ ] **Step 3: Connect the prepared builder without opening the gate**

Extend `GPUPreparedSurfaceFrameBuilder` and
`GPUPreparedSurfaceSemanticBuilder` to call composite preparation, merge child
semantics, attach exact composite payloads, and invoke full preflight/
materialization through test-only explicit entry points. Keep production
eligibility unchanged in this task.

```kotlin
val composite = GPUPreparedCompositeFramePreparer.prepare(
    operations = request.candidate.operations,
    childDraws = childSemantics,
    target = request.targetFacts,
    config = request.candidate.config,
    capabilities = request.capabilities,
)
```

- [ ] **Step 4: Run native tests in series**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedCompositeAllFiltersNativeTest" \
  --tests "*.GPUPreparedCompositeMixedFrameNativeTest" \
  --tests "*.GPUPreparedCompositeRefusalMatrixTest" \
  --tests "*.GPUPreparedSurfaceProductNativeSmokeTest" --no-parallel
rtk git diff --check
```

Expected: all native pixels, one-submit, no-legacy, and zero-side-effect
refusals pass.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "test(surface): prove prepared composite route"
```

---

### Task 20: Product cutover and legacy composite retirement

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Delete: superseded product-only code in `GPUImageFilterDispatch.kt` and `GPUMaskBlurDispatch.kt` when `rtk rg` proves no accepted consumer.
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt`

**Interfaces:**

- Consumes: green Task 19 evidence.
- Produces: production prepared routing for composites and no composite legacy allowlist.

- [ ] **Step 1: Write failing cutover assertions**

```kotlin
@Test
fun `composite operations are prepared candidates`() {
    val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
        GPUPreparedSurfaceFrameGate.classify(compositeFrame(), RenderConfig.DEFAULT),
    )
    assertTrue(candidate.operations.any { it is DisplayOp.BeginLayer })
}

@Test
fun `legacy adapter has no composite family`() {
    assertNull(GPULegacyImmediatePathAdapter.familyOrNull(paintedPictureOp()))
    assertFalse(GPULegacyImmediatePathAdapter.allowedFamilies.any {
        it.name == "Composites"
    })
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*.GPUPreparedSurfaceFrameGateTest" \
  --tests "*.GPUPreparedSurfaceProductRouterTest" --no-parallel
```

Expected: RED with `legacy.surface.prepared.family.composites`.

- [ ] **Step 3: Open the one-way product route**

Classify `DrawPicture`, `BeginLayer`, and `EndLayer` as prepared operations.
Remove `LegacyDisplayOpFamily.Composites` and its code mapping. Route admitted
composite frames only through the prepared builder. Delete legacy branches only
after production search proves they have no accepted consumer; retain reusable
low-level blur/filter authorities consumed by the prepared route.

```kotlin
is DisplayOp.DrawPicture,
is DisplayOp.BeginLayer,
DisplayOp.EndLayer,
-> hasVisual = true
```

- [ ] **Step 4: Run cutover and production absence checks**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.composites|LegacyDisplayOpFamily\\.Composites" \
  kanvas/src/main gpu-renderer/src/main
rtk rg -n \
  "readPixels|snapshot.*CPU|CPU.*layer.*texture|legacy.*composite" \
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu \
  gpu-renderer/src/main/kotlin
rtk git diff --check
```

Expected:

- all module tests pass;
- the composite legacy family search has no production match;
- no product CPU full-layer continuation remains;
- the diff is clean.

- [ ] **Step 5: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test \
  gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(surface): activate prepared composite route"
```

---

### Task 21: Closure evidence, roadmap update, and final review gate

**Files:**

- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-prepared-composite-evidence.md`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`

**Interfaces:**

- Consumes: all Task 1–20 commits and fresh validation output.
- Produces: reproducible FP-07 closure evidence; no production behavior change.

- [ ] **Step 1: Run the fresh serial validation**

```bash
rtk git status --short --branch
rtk proxy ./gradlew \
  :font:core:test :font:glyph:test :font:gpu-api:test :font:test \
  :gpu-renderer:test :kanvas:test --no-parallel
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.composites|LegacyDisplayOpFamily\\.Composites" \
  kanvas/src/main gpu-renderer/src/main
rtk git diff --check
```

Expected: clean worktree before evidence edits, all tests green, no production
legacy composite family, and clean diff.

- [ ] **Step 2: Record exact evidence**

The evidence report records:

- tested commit SHA;
- JDK, Gradle, OS, WebGPU adapter, and wgpu4k snapshot;
- exact commands and test counts;
- all public ImageFilter and MaskFilter evidence rows;
- native mixed-frame pixels and tolerance;
- allocation/write/encode/submit counts for refusals;
- task/pass/intermediate counts before and after normalization;
- destination-read strategy and ownership facts;
- one-submit and close-once facts;
- remaining bounded gaps or dependency issues;
- explicit statement that GM/performance scores remain FP-11 work.

- [ ] **Step 3: Perform the final review gates**

Review the complete range against:

1. the FP-07 design spec;
2. exact public filter coverage;
3. Graphite/Skia filtering semantic invariants;
4. Dawn/WebGPU legality;
5. mono-backend pragmatism;
6. no duplicate filter/layer/runtime authority;
7. no hidden CPU or legacy fallback;
8. wgpu4k/wgsl4k API correctness.

Correct every legitimate Critical or Important finding in a dedicated commit,
rerun its focused tests, then rerun Step 1. Do not mark FP-07 complete while
such a finding remains open.

- [ ] **Step 4: Mark FP-07 complete only after evidence is fresh**

Change only the FP-07 status and current evidence in `active-todo.md`. Do not
reactivate removed historical phases or checkboxes.

- [ ] **Step 5: Commit closure**

```bash
rtk git add \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-prepared-composite-evidence.md \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md
rtk git commit -m "docs(surface): close prepared composite route"
rtk git status --short --branch
```

Expected: the closure commit exists and the worktree is clean.

---

## Execution Order And Review Checkpoints

Execute Tasks 1–21 strictly in order on the target branch after the prerequisite
gate passes.

Mandatory review checkpoints:

1. after Task 5: typed graph, layer semantics, and normalization;
2. after Task 12: complete public image-filter and mask-filter coverage;
3. after Task 15: task graph, ownership, and destination-read legality;
4. after Task 17: native materialization and close-once behavior;
5. after Task 19: pixels, mixed frames, and atomic refusals;
6. after Task 20: one-way product cutover and legacy absence;
7. Task 21: complete independent closure review.

Do not start the next checkpoint group with an unresolved Critical or Important
finding.

## Expected Final State

After Task 21:

- pictures and layers are part of the common prepared frame;
- all 22 current public `ImageFilter` kinds have native bounded evidence;
- all three current public `MaskFilter` kinds have native evidence;
- filter metadata is fused when exact and materialized only at proven
  boundaries;
- every prepared child family can feed filters and layer composites;
- backdrop and destination reads remain GPU-owned and non-aliasing;
- full-frame preflight is atomic;
- the native path uses exact resource ownership and ordered WebGPU passes;
- `LegacyDisplayOpFamily.Composites` and
  `legacy.surface.prepared.family.composites` are absent from production;
- no CPU full-layer compatibility upload exists;
- FP-08 can remove the remaining global immediate/CPU continuation machinery;
- FP-09 can add reusable sessions without changing composite semantics;
- FP-11 remains responsible for regenerated GM scores and measured
  performance claims.
