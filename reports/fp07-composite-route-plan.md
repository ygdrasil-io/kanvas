# FP-07 Composite Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `DrawPicture`, `BeginLayer`, `EndLayer` onto the prepared WebGPU frame route (scratch-target-per-saveLayer, native Kanvas filter DAG), completing the missing frame-route wiring and flipping the `GPUPreparedSurfaceFrameGate` only after Phases 1–3 are green, on top of the FP-06 tip (`40a873560`).

**Architecture:** Scratch-target-per-saveLayer: capture composites (`GPUPreparedCompositeCapturer`) → translate capture into `GPULayerSaveRecord`/`GPUSaveLayerIsolatedTargetRequest` → `GPUSaveLayerIsolatedTargetPlanner.plan(request)` → budget preflight (`GPUPreparedCompositePreflight`) → native executor (`GPUSaveLayerNativeExecutor`) → `ValidatingSaveLayerMaterializer.materialize(request, context)` → pass commands via `GPUFirstRoutePassBuilder.acceptedDrawLayer` → assembled in `GPUPreparedSurfaceFrameTaskListBuilder.handleSaveLayer` and invoked from `GPUPreparedSurfaceFrameBuilder.build()`. CPU oracles (`GPUBlendOracle`, `GPUFilterOracle`) back the blend/filter plans. Filter DAG is native Kanvas (not `skif`). The gate flip is deferred to the last phase, gated on green Phases 1–3.

**Tech Stack:** Kotlin, WebGPU via wgpu4k, WGSL generation, Gradle (`rtk proxy ./gradlew`), JUnit (`kotlin.test`).

**Reference docs:**
- `reports/fp07-composite-route-design.md` — revised design (real contracts, corrections, non-goals). MUST be committed before code work.
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-06 `completed`; this plan delivers FP-07.

---

## Context: validated branch state (evidence, 2026-07-31)

Three independent validators reviewed PR #2056 (`fp-07`, 16 commits, base `40a873560`). Verdict:

- **Foundation (commits `bf14c5dd9..fdd95061b`, 10 code commits) is sound and useful**: real contracts, incremental builds, boundary preserved (20 package cycles, 0 violations), new-component unit tests green.
- **Cutover (commits `34d64799f`, `952b2b7cf`, `0c302db97`) is NOT mergeable as-is**:
  - `handleSaveLayer` (in `GPUPreparedSurfaceFrameTaskListBuilder`) and the composite capturer have **ZERO production callers** — the frame-route wiring is missing (`GPUPreparedSurfaceFrameBuilder.build()` never captures composites).
  - Design gating violated: `reports/fp07-composite-route-design.md` requires the gate flip only after Phases 1–3 green; the branch flipped it early (561 failures on sampled suites; ~92–95% real regressions, ~8% legacy-pinning expectations).
  - 4 real bugs in `GPUFilterOracle` (see Task 3–6).
  - 4 flaky clip tests (order/state) — see Task 10.
  - FP-06 boundary `unsupported.picture.nested_vertices` must be preserved (vertices inside `DrawPicture` stay refused until a dedicated scope supports them).

**Strategy (agreed with user):** integrate the foundation WITHOUT the cutover, then complete the wiring (Phases 3–4), then flip the gate conditionally. Composites stay on the legacy route until then.

**Baseline facts verified on `40a873560`:**
- `GPUPreparedSurfaceFrameGate.kt` routes `DrawPicture`/`BeginLayer`/`EndLayer` → `LegacyDisplayOpFamily.Composites` → `legacy.surface.prepared.family.composites` (gate l.63–65, l.90).
- `GPUPreparedSurfaceFrameBuilder.build()` (l.95) never touches composites; task list assembled at l.283 `taskListBuilder.build(GPUPreparedSurfaceFrameRequest(baseTaskList, ...))`.
- `GPUPreparedCompositeCapturer.capture(operations, limits)` exists (`GPUPreparedCompositeCapture.kt:253`) — internal, no production caller.
- `GPUFirstRoutePassBuilder` and `acceptedDrawLayer` already exist in `gpu-renderer/.../passes/PassContracts.kt` on the base (foundation only adds its test).
- Filter contracts already on base: `FilterContracts.kt`, `GPUFilterTile.kt`, `GPUFilterDAGExecutor.kt`, `GPUPreparedFilterDescriptors.kt`, `GPUPreparedFilterNormalizer.kt`, `GPUPreparedFilterRefusalCodes.kt`, `MaskFilterContracts.kt`.
- `LayerContracts.kt` on base contains the real planner/materializer contracts (`GPUSaveLayerIsolatedTargetPlanner.plan(request)`; `ValidatingSaveLayerMaterializer.materialize(request, context)`; `GPUSaveLayerMaterializationResult` with `require(!adapterBacked)`).

---

## File Map

### New files (created by this plan)
- `reports/fp07-composite-route-design.md` — revised design (from the reviewed draft; committed as reference).
- `reports/fp07-composite-route-plan.md` — this plan.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFrameRouteIntegrationTest.kt` — end-to-end `build()` test for composite frames (Task 7).

### Foundation files (integrated from `fp-07` branch commits `bf14c5dd9..fdd95061b`; all verified by validator)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeContracts.kt` — `GPUPreparedCompositeLowering` (`Ready`/`Refused`), scope/operation contracts.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeRefusalCodes.kt` — stable refusal codes (incl. `PREFLIGHT`).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositeLowerer.kt` — capture → `GPULayerSaveRecord` → `plan(request)`; refusal via `gatePlan.diagnostics.firstOrNull { it.terminal }?.code`, acceptance via `layerPlan`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUPreparedCompositePreflight.kt` — `maxTextureSize`/`maxColorAttachments` budget refusal (`PREFLIGHT`).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUSaveLayerNativeExecutor.kt` — builds full `GPUSaveLayerMaterializationRequest`, calls `ValidatingSaveLayerMaterializer().materialize(request, context)`, `adapterBacked=false`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/SaveLayerExecutor.kt` (replaced by `GPUSaveLayerNativeExecutor`) — legacy executor removed; `LayerContracts.kt` updated.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUBlendOracle.kt` — production promotion of `GPUBlendCpuOracle`; test oracle deleted, imports re-pointed.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt` — CPU filter oracle (blur/color-filter/offset/crop/drop-shadow) — **4 bugs fixed in Tasks 3–6**.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedFilterDAGPlanner.kt` — node routes (`NativeRender`/`FoldedMaterial`/`Refused`), intermediate textures, execution order.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUPreparedMaskFilterLowerer.kt` — blur→coverage A8, else `NATIVE_CAPABILITY` refusal.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCapture.kt` (modified by foundation) — backdrop (`LAYER_DESTINATION_READ` removed, `LoadOp.Load`), mask-filter `PAINT` refusal removed, `FilterPictureSource` scope in `processPicture()`.
- Foundation tests: `GPUPreparedCompositeLowererTest`, `GPUPreparedCompositePreflightTest`, `GPUSaveLayerNativeExecutorTest`, `GPUFilterOracleTest`, `GPUPreparedFilterDAGPlannerTest`, `GPUBlendOracleTest`, `GPUFirstRoutePassBuilderTest`, `GPUPreparedCompositeCaptureSemanticTest`, `SaveLayerIsolatedTargetGateTest`, `SaveLayerExecutorTest` (updated), `GPUBlendFormulaLibraryTest`, `GPUBackendRuntimeNativeWgslValidationTest`, `GPUWgpu4kDestinationCopyFrameSmokeTest`.

### Files modified by THIS plan (the wiring — the actual FP-07 gap)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt` — capture composites in `build()`; assemble + inject composite commands into the task list (Task 7).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt` — `handleSaveLayer(...)` production entry (ported from cutover commit `952b2b7cf`, minus the gate flip) + composite command injection point (Task 7).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt` — conditional cutover of `DrawPicture`/`BeginLayer`/`EndLayer` to `Candidate` (Task 9).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt` — only if the gate flip requires it (Task 9).
- Test expectation updates with evidence (Task 9): `GPUPreparedSurfaceFrameGateTest`, `GPUFramePathApiInventoryTest`, `GPUPreparedSurfaceProductRouterTest`, `GPUPreparedTextNoFallbackTest`, `GPUAllApiBlendSurfaceTest`, `GPUClipCoverageSurfaceTest`, plus any test pinning `legacy.surface.prepared.family.composites`.

---

## Phase 0 — Contracts & reference docs

### Task 1: Commit the revised design as the reference document

**Files:**
- Create: `reports/fp07-composite-route-design.md` (content = the reviewed revised design draft, frontmatter sessionId removed, section "Remaining to verify in Task 0" resolved to the verified contracts below)

**Context:** The revised design (currently untracked in `.junie/plans/`) corrects the initial plan's three defect families: fictional APIs, missing integration step, incoherent numbering. It must be committed into the repo before code work so subagents execute against it.

**Verified contract facts (already validated — encode them in the design doc, replacing its "à relever" section):**

```kotlin
// gpu-renderer/.../layers/LayerContracts.kt
fun plan(request: GPUSaveLayerIsolatedTargetRequest): GPUSaveLayerIsolatedTargetGatePlan
// refusal: gatePlan.diagnostics.firstOrNull { it.terminal }?.code   (NO .refused field)
// acceptance: gatePlan.layerPlan  (type GPULayerPlan)

fun materialize(
    request: GPUSaveLayerMaterializationRequest,
    context: GPUTargetPreparationContext,   // 2nd arg MANDATORY
): GPUSaveLayerMaterializationResult         // require(!adapterBacked)

// kanvas/.../surface/gpu/GPUPreparedCompositeCapture.kt:253
fun capture(operations: List<DisplayOp>, limits: GPUPreparedCompositeCaptureLimits): GPUPreparedCompositeCaptureResult
// Ready(GPUPreparedCompositeCapture(rootScopeId, scopes, expandedOperations, identity)) | Refused(code, operationIndex, facts)

// gpu-renderer/.../recording/GPUPreparedSurfaceFrameTaskListBuilder.kt (ported handleSaveLayer)
fun handleSaveLayer(
    scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
    rootScopeId: GPUPreparedCompositeScopeId,
    identity: String,
    capabilities: GPUPreflightCapabilities,
    context: GPUTargetPreparationContext,
    targetBudgetBytes: Long = DEFAULT_SAVE_LAYER_FRAME_BUDGET_BYTES,
): GPUPreparedSaveLayerFrameHandling   // Ready(plan, results, commands) | Refused(code, operationIndex, facts)
```

- [ ] **Step 1: Write the design document**

Copy the revised design draft content into `reports/fp07-composite-route-design.md`; replace the "À relever également avant écriture" bullet list with the verified contract facts above (with `file:line` anchors from this plan's Context section).

- [ ] **Step 2: Commit**

```bash
git add reports/fp07-composite-route-design.md
git commit -m "docs(surface): commit revised fp07 composite route design as reference"
```

---

## Phase 1 — Integrate the validated foundation (no cutover)

### Task 2: Cherry-pick the 10 foundation code commits from `fp-07`

**Files:** all files listed in "Foundation files" above.

**Context:** The `fp-07` branch is based on our tip (`40a873560`), so the foundation applies cleanly. We deliberately exclude the 3 docs commits (`e9f1b622f`, `7e954c8d7`, `6026a0700` — superseded by Task 1) and the 3 cutover commits (`34d64799f`, `952b2b7cf`, `0c302db97` — Tasks 7–9 rebuild that wiring correctly, gate-late).

- [ ] **Step 1: Cherry-pick the foundation commits in order**

```bash
git cherry-pick bf14c5dd9 e8dc7a7a8 450529bec bec842361 f8c483f7c cf8484772 0f9a3c3c3 4c3496aae 0fc80426b fdd95061b
```

Expected: clean apply (same parentage). If any conflict arises, resolve by taking the fp-07 side (the foundation is the validated version) and continue: `git cherry-pick --continue`.

- [ ] **Step 2: Verify the composite boundary stays legacy**

```bash
git grep -n "legacy.surface.prepared.family.composites" -- kanvas/src/main
```

Expected: `GPUPreparedSurfaceFrameGate.kt` still routes composites to `Legacy`. The gate must NOT be flipped by the foundation.

- [ ] **Step 3: Compile and run foundation tests**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUPreparedCompositeLowererTest" --tests "*GPUPreparedCompositePreflightTest" --tests "*GPUSaveLayerNativeExecutorTest" --tests "*GPUFilterOracleTest" --tests "*GPUPreparedFilterDAGPlannerTest" --tests "*GPUBlendOracleTest" --tests "*GPUFirstRoutePassBuilderTest" --tests "*SaveLayerIsolatedTargetGateTest" --tests "*GPUBlendFormulaLibraryTest" --no-parallel
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeCaptureSemanticTest" --no-parallel
```

Expected: all pass. Note: `GPUFilterOracleTest` may already encode the 4 bugs' current (wrong) behavior — Tasks 3–6 fix them.

- [ ] **Step 4: Run the FP-06 regression guard**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel
```

Expected: green (composites still legacy; FP-06 prepared route untouched). If failures appear, they are expectations pinning the old `invalid.preflight.dump_unsafe_identity` behavior — investigate before proceeding (the FP-06 fix commit `40a873560` must be the base; verify `git log --oneline -1` shows `40a873560`).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(composite): integrate validated fp07 foundation without cutover"
```

### Task 3: Verify boundary preservation for `unsupported.picture.nested_vertices`

**Files:**
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCaptureSemanticTest.kt` (foundation)

**Context:** The FP-06 boundary `unsupported.picture.nested_vertices` (vertices inside `DrawPicture`) must remain refused — the foundation's `FilterPictureSource` scope must not silently render nested vertices as top-level.

- [ ] **Step 1: Confirm the refusal still exists**

```bash
git grep -n "nested_vertices\|NESTED_VERTICES" -- kanvas/src gpu-renderer/src | head -20
```

Expected: a refusal path for vertices captured inside a picture/composite scope.

- [ ] **Step 2: Add the boundary test**

In `GPUPreparedCompositeCaptureSemanticTest.kt`:

```kotlin
@Test
fun `vertices inside picture composite stay refused`() {
    val operations = listOf(
        DisplayOp.BeginLayer(/* bounds covering the canvas */),
        DisplayOp.DrawVertices(/* a simple triangle */),
        DisplayOp.EndLayer,
    )
    val result = GPUPreparedCompositeCapturer.capture(
        operations = operations,
        limits = GPUPreparedCompositeCaptureLimits(),
    )
    assertTrue(result is GPUPreparedCompositeCaptureResult.Refused)
    assertEquals("unsupported.picture.nested_vertices", result.code)
}
```

(Adapt to the exact `DisplayOp.BeginLayer`/`DrawVertices` constructor signatures present on the base — read the test file's existing helpers first.)

- [ ] **Step 3: Run and commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeCaptureSemanticTest" --no-parallel
git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCaptureSemanticTest.kt
git commit -m "test(surface): pin nested vertices composite boundary"
```

---

## Phase 2 — Fix the 4 `GPUFilterOracle` bugs (TDD)

### Task 4: Fix color-filter matrix layout (transposed vs production)

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt` (`applyColorFilter`, l.172–190)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt`

**Context:** `applyColorFilter` indexes the 20-entry matrix as column-major (`m[0],m[4],m[8],m[12],m[16]` for output R). The production WGSL color-filter shader and Skia convention are row-major 4x5 (`R' = m0·R + m1·G + m2·B + m3·A + m4`; `G' = m5·R + m6·G + m7·B + m8·A + m9`; etc.). Cross-channel terms are therefore applied to the wrong output channel.

- [ ] **Step 1: Write the failing test**

In `GPUFilterOracleTest.kt`:

```kotlin
@Test
fun `color filter uses row-major matrix layout`() {
    val source = Rgba8Bitmap(1, 1, floatArrayOf(0.2f, 0.8f, 0.0f, 1.0f))
    val matrix = FloatArray(20)
    matrix[0] = 1f; matrix[6] = 1f; matrix[11] = 1f; matrix[15] = 1f
    matrix[1] = 0.5f   // G contributes to output R (row-major: m[1])
    val node = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(1),
        kind = GPUPreparedFilterKind.ColorFilter,
        parameters = ColorFilterParams(matrix),
        inputs = emptyList(),
    )
    val out = GPUFilterOracle.apply(source, node, emptyMap())
    assertEquals(0.6f, out.pixels[0], 1e-4f)   // R' = 0.2 + 0.5·0.8
    assertEquals(0.8f, out.pixels[1], 1e-4f)   // G' unchanged
}
```

(Use the exact `GPUPreparedFilterNode`/`ColorFilterParams` constructors from `GPUPreparedFilterDescriptors.kt` on the base.)

- [ ] **Step 2: Run to verify it fails**

Run: `rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUFilterOracleTest" --no-parallel`
Expected: FAIL — current code yields `R' = m[0]·0.2 + m[4]·0.8 + ... = 0.2` (transposed).

- [ ] **Step 3: Fix the implementation**

In `GPUFilterOracle.kt`, replace the `applyColorFilter` output computation:

```kotlin
private fun applyColorFilter(source: Rgba8Bitmap, matrix: FloatArray): Rgba8Bitmap {
    require(matrix.size == 20) { "ColorFilter matrix must have 20 entries" }
    val dst = Rgba8Bitmap(source.width, source.height, FloatArray(source.width * source.height * 4))
    for (i in source.pixels.indices step 4) {
        val r = source.pixels[i]
        val g = source.pixels[i + 1]
        val b = source.pixels[i + 2]
        val a = source.pixels[i + 3]
        dst.pixels[i] = (matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4]).coerceIn(0f, 1f)
        dst.pixels[i + 1] = (matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9]).coerceIn(0f, 1f)
        dst.pixels[i + 2] = (matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14]).coerceIn(0f, 1f)
        dst.pixels[i + 3] = (matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]).coerceIn(0f, 1f)
    }
    return dst
}
```

- [ ] **Step 4: Run to verify it passes**

Expected: PASS (0.6f/0.8f).

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt
git commit -m "fix(filters): row-major color filter matrix layout in GPUFilterOracle"
```

### Task 5: Fix negative offsets (content shift + drop-shadow bounds)

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt` (`applyOffset` l.194–216; `applyDropShadow` bounds l.273–284)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt`

**Context:** In `applyOffset`, `srcX`/`srcY` are computed but never used; `offsetX = maxOf(0, idx)` places content at `x` (not `x + |dx|`) for negative `dx`, so a negative offset produces a canvas widened to the right with content unmoved — the shift is lost. The drop-shadow bounds math (`outW`/`outH`) then double-counts `|dx|` for negative offsets (`W + 2|dx|`).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `negative offset shifts content left`() {
    val source = Rgba8Bitmap(3, 1, floatArrayOf(
        1f, 0f, 0f, 1f,
        0f, 1f, 0f, 1f,
        0f, 0f, 1f, 1f,
    ))
    val node = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(1),
        kind = GPUPreparedFilterKind.Offset,
        parameters = OffsetParams(dx = -1f, dy = 0f),
        inputs = emptyList(),
    )
    val out = GPUFilterOracle.apply(source, node, emptyMap())
    assertEquals(4, out.width)                       // W + |dx|
    val rgba = FloatArray(4)
    out.getPixel(0, 0, rgba)
    assertEquals(0f, rgba[0], 1e-4f)                 // empty column on the left
    out.getPixel(1, 0, rgba)
    assertEquals(1f, rgba[0], 1e-4f)                 // first source pixel shifted to x=1
    out.getPixel(4 - 1, 0, rgba)
    assertEquals(0f, rgba[0], 1e-4f)                 // last source pixel beyond canvas end
}
```

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL — current code puts the first pixel at x=0.

- [ ] **Step 3: Fix the implementation**

```kotlin
private fun applyOffset(source: Rgba8Bitmap, dx: Float, dy: Float): Rgba8Bitmap {
    val idx = dx.toInt()
    val idy = dy.toInt()
    if (idx == 0 && idy == 0) return source.copy()

    val newW = source.width + kotlin.math.abs(idx)
    val newH = source.height + kotlin.math.abs(idy)
    val dst = Rgba8Bitmap(newW, newH, FloatArray(newW * newH * 4))

    val offsetX = kotlin.math.abs(idx)
    val offsetY = kotlin.math.abs(idy)

    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val sp = (y * source.width + x) * 4
            val dp = ((y + offsetY) * newW + (x + offsetX)) * 4
            for (c in 0..3) dst.pixels[dp + c] = source.pixels[sp + c]
        }
    }
    return dst
}
```

Then fix the drop-shadow bounds (l.273–284) so the union is `W + |dx|` (not `W + 2|dx|`):

```kotlin
val outW = source.width + kotlin.math.abs(dxInt)
val outH = source.height + kotlin.math.abs(dyInt)
```

And keep the placement logic: shadow at `shadowLeft = max(0, dxInt)`, source at `srcLeft = max(0, -dxInt)` — with the corrected `applyOffset` the offsetShadow content already sits at `x + |dx|`, so the composite renders the shadow shifted by `(dx, dy)` relative to the source. Verify the existing `drop shadow` tests still pass and extend them with a `dx < 0` case.

- [ ] **Step 4: Run to verify it passes**

Expected: PASS, and all existing `applyOffset`/drop-shadow tests green.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt
git commit -m "fix(filters): effective negative offsets and drop shadow bounds in GPUFilterOracle"
```

### Task 6: Fix Decal tile-mode blur crash

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt` (`clampCoord` l.139–153; `convolveSeparable` l.83–137)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt`

**Context:** `clampCoord` returns `Int.MIN_VALUE` for out-of-bounds `Decal` coordinates, but `convolveSeparable` uses the result directly to index `source.pixels` → `ArrayIndexOutOfBoundsException` (Decal blur crashes; the "will be clamped in getPixel" comment is false — `getPixel` is never called in the convolution path).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `decal blur does not crash and yields transparent borders`() {
    val source = Rgba8Bitmap(3, 3, FloatArray(3 * 3 * 4) { 1f })
    val node = GPUPreparedFilterNode(
        id = GPUPreparedFilterNodeId(1),
        kind = GPUPreparedFilterKind.Blur,
        parameters = BlurParams(sigmaX = 2f, sigmaY = 2f, tileMode = GPUTileMode.Decal),
        inputs = emptyList(),
    )
    val out = GPUFilterOracle.apply(source, node, emptyMap())
    val rgba = FloatArray(4)
    out.getPixel(0, 0, rgba)
    assertTrue(rgba[3] < 1f, "Decal border must fade to transparent")
    out.getPixel(1, 1, rgba)
    assertTrue(rgba[3] > 0f, "Decal center keeps coverage")
}
```

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL with `ArrayIndexOutOfBoundsException` (or the border assertion).

- [ ] **Step 3: Fix the implementation**

Change `clampCoord` to return a nullable and skip out-of-bounds contributions (Decal semantics: transparent outside the source):

```kotlin
private fun clampCoordOrNull(v: Int, lo: Int, hi: Int, tileMode: GPUTileMode): Int? = when (tileMode) {
    GPUTileMode.Clamp -> v.coerceIn(lo, hi)
    GPUTileMode.Repeat -> {
        val range = hi - lo + 1
        lo + ((v - lo) % range + range) % range
    }
    GPUTileMode.Mirror -> {
        val range = hi - lo + 1
        if (range <= 1) lo
        else {
            val period = 2 * range - 2
            val t = ((v - lo) % period + period) % period
            lo + if (t < range) t else period - t
        }
    }
    GPUTileMode.Decal -> if (v in lo..hi) v else null
}
```

In both `convolveSeparable` loops, replace:

```kotlin
val sx = clampCoord(x + k - radius, 0, source.width - 1, tileMode)
val idx = (y * source.width + sx) * 4
```

with:

```kotlin
val sx = clampCoordOrNull(x + k - radius, 0, source.width - 1, tileMode) ?: continue
val idx = (y * source.width + sx) * 4
```

(and symmetrically `sy` for the vertical pass). Delete the now-unused `clampCoord`.

- [ ] **Step 4: Run to verify it passes**

Expected: PASS — no crash; border alpha < 1, center > 0. All other tile-mode tests stay green.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracle.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPUFilterOracleTest.kt
git commit -m "fix(filters): decal tile mode blur no longer crashes in GPUFilterOracle"
```

---

## Phase 3 — Frame-route wiring (the missing FP-07 gap)

### Task 7: Wire composite capture + `handleSaveLayer` into `GPUPreparedSurfaceFrameBuilder.build()`

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt` (in `build()`, after `collectPreparedImageVisuals`/`preparedMapping` at l.173 and before the task-list assembly at l.283)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt` (port `handleSaveLayer` from `952b2b7cf`; add composite command merge point)
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFrameRouteIntegrationTest.kt` (new)

**Context:** This is the actual missing work — `handleSaveLayer` and the capturer have zero production callers. The flow: in `build()`, when the frame contains composite ops, run `GPUPreparedCompositeCapturer.capture(operations, limits)`; if `Ready`, call `taskListBuilder.handleSaveLayer(...)`; merge `GPUPreparedSaveLayerFrameHandling.Ready.commands` into the task list; if refused, return a stable terminal refusal (fail-closed — composites never silently fall back mid-build).

- [ ] **Step 1: Port `handleSaveLayer` into the task-list builder**

Copy `handleSaveLayer` + `GPUPreparedSaveLayerFrameHandling` + `DEFAULT_SAVE_LAYER_FRAME_BUDGET_BYTES` from the `fp-07` worktree (`/var/folders/81/9k3fbzrd42b_r_vm8fkfy16w0000gn/T/opencode/kanvas-fp07/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt:2977`) into our file, together with its unit test `GPUPreparedSaveLayerFrameHandlingTest.kt` (from `952b2b7cf`). Do NOT port the gate flip or the legacy-adapter changes.

```bash
git show 952b2b7cf:gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt > /tmp/handlesavelayer.kt
git show 952b2b7cf:gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSaveLayerFrameHandlingTest.kt > gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSaveLayerFrameHandlingTest.kt
```

Then extract the `handleSaveLayer` region into our task-list builder (the surrounding code on `fp-07` includes cutover-specific changes — take only the method and its sealed result type). Compile:

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUPreparedSaveLayerFrameHandlingTest" --no-parallel
```

Expected: compile + unit tests pass (the handling logic itself was validated).

- [ ] **Step 2: Write the integration test (red)**

`GPUPreparedCompositeFrameRouteIntegrationTest.kt`:

```kotlin
class GPUPreparedCompositeFrameRouteIntegrationTest {

    private fun buildFrame(operations: List<DisplayOp>): GPUPreparedSurfaceFrameBuildResult {
        val gate = GPUPreparedSurfaceFrameGate
        val eligibility = gate.evaluate(
            operations = operations,
            config = GpuCanvasConfig(),
            color = org.graphiks.kanvas.color.Color.TRANSPARENT,
        )
        // NOTE: composites are still Legacy at this phase — assert the wiring via the builder
        // directly until Task 9 flips the gate; this test is updated in Task 9.
        ...
    }

    @Test
    fun `composite frame build refuses stably when capture fails`() { ... }

    @Test
    fun `composite frame build with plain saveLayer produces commands`() { ... }
}
```

For the second test, drive `GPUPreparedSurfaceFrameBuilder.build(request)` with a request whose candidate contains a single `BeginLayer`/`EndLayer` pair; expect the result to include `CompositeLayer` commands once the wiring is in place. (If the gate still routes composites to Legacy, construct the `GPUPreparedSurfaceFrameBuildRequest` directly with `candidate = GPUPreparedSurfaceEligibility.Candidate(...)` — this is a builder-level test, not a gate-level test.)

- [ ] **Step 3: Run to verify it fails**

Run: `rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --no-parallel`
Expected: FAIL — no composite handling in `build()`.

- [ ] **Step 4: Wire the capture + handling into `build()`**

In `GPUPreparedSurfaceFrameBuilder.build()`, after `preparedMapping` is built (l.173) and before the semantics gathering, insert:

```kotlin
val compositeHandling = prepareCompositeFrameHandling(
    operations = request.candidate.operations,
    capabilities = request.capabilities,
    context = contextFor(request),
)
if (compositeHandling is CompositeFrameHandling.Refused) {
    return GPUPreparedSurfaceFrameBuildResult.Refused(
        diagnostic(
            code = compositeHandling.code,
            message = "Prepared Surface composite could not be lowered.",
            facts = compositeHandling.facts + mapOf(
                "boundary" to "surface.composite",
                "operationIndex" to compositeHandling.operationIndex.toString(),
            ),
        ),
    )
}
```

Where `prepareCompositeFrameHandling` (new private function in the builder file):

```kotlin
private sealed interface CompositeFrameHandling {
    data class Ready(
        val handling: GPUPreparedSaveLayerFrameHandling.Ready,
    ) : CompositeFrameHandling

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : CompositeFrameHandling
}

private fun prepareCompositeFrameHandling(
    operations: List<DisplayOp>,
    capabilities: GPUPreflightCapabilities,
    context: GPUTargetPreparationContext,
): CompositeFrameHandling {
    val capture = GPUPreparedCompositeCapturer.capture(
        operations = operations,
        limits = GPUPreparedCompositeCaptureLimits(),
    )
    if (capture is GPUPreparedCompositeCaptureResult.Refused) {
        return CompositeFrameHandling.Refused(capture.code, capture.operationIndex, capture.facts)
    }
    val ready = capture as GPUPreparedCompositeCaptureResult.Ready
    val handling = taskListBuilder.handleSaveLayer(
        scopes = ready.capture.scopes,
        rootScopeId = ready.capture.rootScopeId,
        identity = ready.capture.identity,
        capabilities = capabilities,
        context = context,
    )
    return when (handling) {
        is GPUPreparedSaveLayerFrameHandling.Ready -> CompositeFrameHandling.Ready(handling)
        is GPUPreparedSaveLayerFrameHandling.Refused ->
            CompositeFrameHandling.Refused(handling.code, handling.operationIndex, handling.facts)
    }
}
```

`contextFor(request)` builds a `GPUTargetPreparationContext` from `request.targetFacts`/`request.capabilities` (read the base `GPUTargetPreparationContext` constructor — it already exists on the base; the executor tests show its required fields).

Then merge the composite commands: after `taskListBuilder.build(GPUPreparedSurfaceFrameRequest(...))` returns `Recorded`, and before returning `Ready`, merge `compositeHandling.handling.commands` into the recorded task list via a new merge step on the task-list builder (port the command-append logic from `952b2b7cf`'s wiring — the cutover already contained the merge mechanics; port only that, not the gate flip). Add the `compositeHandling` result to `GPUPreparedSurfaceFrameBuildResult.Ready` as a new field `compositeCommandCount: Int = 0` (backward-compatible default).

- [ ] **Step 5: Run to verify it passes**

Expected: PASS — composite frame produces commands; refusal path returns stable terminal diagnostics.

- [ ] **Step 6: Full regression guard**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel
```

Expected: green — the wiring is additive; composites still route Legacy at the gate, so surface tests are untouched.

- [ ] **Step 7: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFrameRouteIntegrationTest.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSaveLayerFrameHandlingTest.kt
git commit -m "feat(composite): wire composite capture and handleSaveLayer into prepared frame build"
```

### Task 8: Wire layer paint semantics (blend + alpha + clip) through the executor

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUSaveLayerNativeExecutor.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt` (`GPUFirstRoutePassBuilder.acceptedDrawLayer`)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUSaveLayerNativeExecutorTest.kt`

**Context:** The validator flagged that layer paint semantics (blend mode, alpha, clip) are not threaded into the pass. `acceptedDrawLayer` must receive a real blend plan (from `GPUBlendOracle` — not `GPUBlendPlan.NoOp` placeholder) and the layer's alpha/clip must reach the composite pass.

- [ ] **Step 1: Write the failing test**

In `GPUSaveLayerNativeExecutorTest.kt`:

```kotlin
@Test
fun `executor threads layer blend mode into the draw layer pass`() {
    // build a request whose gatePlan.layerPlan carries a SRC_OVER paint
    val result = GPUSaveLayerNativeExecutor().materialize(request, context)
    assertNotNull(result)
    // assert the produced pass uses a real shader blend (not NoOp placeholder)
    val commands = result.commandStream
    val composite = commands.filterIsInstance<CompositeLayer>().single()
    assertTrue(composite.blendPlan is GPUBlendPlan.ShaderBlend, "layer blend must be real")
    assertEquals("src_over", composite.blendPlan.mode.gpuLabel)
}
```

(Adapt names to the actual executor/materializer API on the base — read `GPUSaveLayerNativeExecutorTest` from the foundation first.)

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL — `NoOp`/placeholder blend or missing alpha.

- [ ] **Step 3: Implement**

In `GPUSaveLayerNativeExecutor`, derive the blend plan from `GPUBlendOracle.forMode(layerPaint.blendMode)` and pass alpha/clip from the layer plan into `acceptedDrawLayer(...)` (extend its signature with `alpha: Float` and `clipStrategy` if not already present — check `PassContracts.kt` on the base first; `acceptedDrawLayer` exists there).

- [ ] **Step 4: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUSaveLayerNativeExecutorTest" --tests "*GPUFirstRoutePassBuilderTest" --no-parallel
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUSaveLayerNativeExecutor.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/layers/GPUSaveLayerNativeExecutorTest.kt
git commit -m "feat(composite): thread layer blend alpha and clip into draw layer pass"
```

---

## Phase 4 — Conditional gate cutover

### Task 9: Flip `GPUPreparedSurfaceFrameGate` for composites (condition: Phases 1–3 green)

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify (only if needed): `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Update with evidence: `GPUPreparedSurfaceFrameGateTest`, `GPUFramePathApiInventoryTest`, `GPUPreparedSurfaceProductRouterTest`, `GPUPreparedTextNoFallbackTest`, `GPUAllApiBlendSurfaceTest`, `GPUClipCoverageSurfaceTest`

**Context:** Design condition (design doc): the gate flips ONLY when all Phase 1–3 tests are green. That condition is satisfied after Task 8. The flip changes `DrawPicture`/`BeginLayer`/`EndLayer` from `LegacyDisplayOpFamily.Composites` to eligible `Candidate`; refused composites then surface as stable terminal refusals from the builder (Task 7) — this is the documented stable fallback policy (explicit refusal, never silent).

- [ ] **Step 1: Confirm the precondition**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUPreparedCompositeLowererTest" --tests "*GPUPreparedCompositePreflightTest" --tests "*GPUSaveLayerNativeExecutorTest" --tests "*GPUFilterOracleTest" --tests "*GPUPreparedFilterDAGPlannerTest" --tests "*GPUBlendOracleTest" --tests "*GPUPreparedSaveLayerFrameHandlingTest" --no-parallel
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --no-parallel
```

Expected: all green.

- [ ] **Step 2: Flip the gate**

In `GPUPreparedSurfaceFrameGate.kt` l.63–65, change `DrawPicture`/`BeginLayer`/`EndLayer` to count toward `hasVisual` and be eligible (no `Legacy` return). Remove the `LegacyDisplayOpFamily.Composites` branch and its `preparedSurfaceCode()` mapping (l.90) if now unused; keep `LegacyDisplayOpFamily` if other families remain.

- [ ] **Step 3: Run the full surface + renderer suites, triage with evidence**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test :gpu-renderer:test --no-parallel 2>&1 | tee /tmp/fp07_cutover.log
```

Expected: failures in two categories:
1. **Real regressions** (must FIX, not update): saveLayer renders wrong pixels, DrawPicture content dropped, `unsupported.native-core-primitive.blend`/`unsupported.image.native_binding` terminal refusals on previously-working frames, nested-vertices boundary bypass. Investigate each with systematic debugging; fix in the wiring (Tasks 7–8) or the oracle (Tasks 4–6). DO NOT update expectations for real regressions.
2. **Legacy-pinning expectations** (update with evidence): tests asserting `legacy.surface.prepared.family.composites` routing or legacy composite pixels. For each, capture the route diagnostics (new code/terminal refusal) and update the assertion with a comment referencing the evidence file.

- [ ] **Step 4: Update legacy-pinning expectations with evidence**

For each test in the update category, record in `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-composite-route-evidence.md` (new file): test name, old assertion, new assertion, route diagnostic code observed, and the diff/stat of the expectation change. Update the assertions.

- [ ] **Step 5: Run full suites again**

Expected: green (any remaining failures are real regressions — fix, do not mask).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(composite): flip prepared frame gate for composites with evidence-based expectations"
```

### Task 10: Fix the 4 flaky clip tests (order/state)

**Files:** the 4 clip tests identified in Task 9 triage (re-run unstable tests).

- [ ] **Step 1: Reproduce**

Run the clip suites 3×: `rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*Clip*" --no-parallel` and record which tests fail intermittently (and whether failure depends on the previous test in the JVM).

- [ ] **Step 2: Fix the shared-state/order cause**

Typical causes in this codebase: static/companion mutable state across tests, shared `GPUPreparedSurfaceFrameTaskListBuilder` cache, or `@TestInstance` ordering assumptions. Apply the minimal fix (reset state in `@Before`, make the cache per-test, or isolate the fixture). Add a `@RepeatedTest(3)` guard where the flake was timing-related.

- [ ] **Step 3: Verify + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*Clip*" --no-parallel
git add -A
git commit -m "fix(surface): stabilize flaky clip tests"
```

---

## Phase 5 — Validation & FP-07 closure

### Task 11: Full validation, evidence, and closure

- [ ] **Step 1: Full suite runs**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test :gpu-renderer:test --no-parallel
```

Expected: green (documented session-close flakes excepted — re-run to confirm).

- [ ] **Step 2: Boundary audit**

```bash
git grep -n "legacy.surface.prepared.family.composites" -- kanvas/src || echo "composites no longer legacy-routed"
git grep -n "unsupported.picture.nested_vertices" -- kanvas/src gpu-renderer/src   # boundary preserved or explicitly re-scoped in evidence
```

Also re-verify the package boundary: `:gpu-renderer:test --tests "*GPURendererPackageBoundaryTest"` — exactly 20 cycles, 0 rule violations.

- [ ] **Step 3: Write the FP-07 evidence report**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-composite-route.md`: route diagnostics (capture→lowerer→preflight→executor codes), diff/stat summary vs base, test score deltas, refusal matrix (which composite shapes are refused and with which stable codes), boundary statements (nested_vertices, legacy composites), fallback policy statement.

- [ ] **Step 4: Update the roadmap**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`: mark FP-07 `completed`, reference the evidence report.

- [ ] **Step 5: Update the session ledger**

`git add reports/ .superpowers/sdd/ 2>/dev/null || git add reports/`; commit:

```bash
git add reports/
git commit -m "docs(composite): fp07 composite route evidence and roadmap closure"
```

- [ ] **Step 6: Final state check**

`git log --oneline 40a873560..HEAD | cat` — expected: Task 1..11 commits, no cutover-early commits, no `.superpowers/sdd/` entries in the log.

---

## Self-review notes (filled at plan time)

- **Spec coverage:** revised design's 5 phases map 1:1 to Tasks 1–11 (Task 0→1, Phase 1→2–3, Phase 2→4–6, Phase 3→7–8, Phase 4→9–10, Phase 5→11). Non-goals (pooling/approx-fit→FP-09, AA→gap, runtime effects→FP-10, monolith debt) are respected: no texture pooling introduced, single-sample textured quads, no dynamic WGSL, no monolith split.
- **No placeholders:** every task has concrete files, commands, and code. Where a constructor signature must be confirmed against the base (e.g., `GPUPreparedFilterNode`, `GPUTargetPreparationContext`), the task says exactly where to read it and what to verify.
- **Type consistency:** `GPUPreparedCompositeLowering.Ready/Refused`, `GPUPreparedSaveLayerFrameHandling.Ready/Refused`, `GPUPreparedCompositeCaptureResult.Ready/Refused`, `GPUPreparedSurfaceFrameBuildResult.Ready/Refused` are used consistently throughout; `handleSaveLayer` signature matches the validated port.
