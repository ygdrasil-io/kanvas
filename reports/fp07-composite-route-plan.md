# FP-07 Composite Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `DrawPicture`, `BeginLayer`, `EndLayer` onto the prepared WebGPU frame route (scratch-target-per-saveLayer, native Kanvas filter DAG): complete the frame-route wiring (Tasks 1–8), then the **layer-target execution** (Tasks 12–17) that makes the executor actually schedule the merged `compositeCommands`, then flip the `GPUPreparedSurfaceFrameGate` only when consumption works, on top of the FP-06 tip (`40a873560`).

**Architecture:** Scratch-target-per-saveLayer: capture composites (`GPUPreparedCompositeCapturer`) → translate capture into `GPULayerSaveRecord`/`GPUSaveLayerIsolatedTargetRequest` → `GPUSaveLayerIsolatedTargetPlanner.plan(request)` → budget preflight (`GPUPreparedCompositePreflight`) → native executor (`GPUSaveLayerNativeExecutor`) → `ValidatingSaveLayerMaterializer.materialize(request, context)` → pass commands via `GPUFirstRoutePassBuilder.acceptedDrawLayer` → assembled in `GPUPreparedSurfaceFrameTaskListBuilder.handleSaveLayer` and invoked from `GPUPreparedSurfaceFrameBuilder.build()`. CPU oracles (`GPUBlendOracle`, `GPUFilterOracle`) back the blend/filter plans. Filter DAG is native Kanvas (not `skif`).

**Execution model (Phase 5, modeled on Graphite/Dawn — evidence from Skia main exploration, 2026-08-02):** one render pass per layer target + one root pass, in a single command encoder: `PrepareLayerTarget` → frame-pool leased texture (RGBA8, `RenderAttachment|TextureBinding` — the A8 coverage-mask template, `GPUWgpu4kCoverageMaskProducerMaterializer`); `RenderLayerChildren` → child render scopes targeting the layer texture in the SAME encoder as the scene pass; `CompositeLayer` → a textured-quad draw sampling the layer texture with the real blend plan + alpha + clip (the `GPUPreparedImageShader`/`preparedImageAtlasSourceBlend` template) into the parent pass. `GPUPreparedWindowOutput.attachToFrame` must carry `compositeCommands` forward; the session validator's single-scene-target invariant is relaxed to admit declared layer targets; the planner lowers the commands into frame steps.

The gate flip (Task 17) is the last step, conditioned on Tasks 1–16 green. **Task 9 was executed and the flip WITHHELD with evidence** (`863e4351e`, see `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-composite-route-evidence.md`): 298 composite-frame failures, 100% traced to the executor not consuming `compositeCommands`. That gap is closed by Tasks 12–16 before the flip is retried.

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

### Task 9: Gate cutover TRIAL — flip attempted and **WITHHELD** (executor gap, evidence recorded)

**Status: EXECUTED → WITHHELD.** See `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-composite-route-evidence.md` (commit `863e4351e`). The flip precondition (Phases 1–3 green) passed; the flip was applied and reverted after full-suite triage showed **298 composite-frame failures, 100% traced to one root cause**: the executor does not consume `compositeCommands` (`GPUPreparedWindowOutput.attachToFrame` drops them at `GPUPreparedWindowOutput.kt:97`; no consumption path in `GPUFrameCoordinator`/`GPUBackendRuntimeNative`). Consequence categories: DrawPicture content dropped (85 wrong-pixel), saveLayer bounds/alpha lost (7), non-core children terminal refusals (203), silent-drop-replacing-loud-refusal (1). The FP-06 guards and the `nested_vertices` pin stayed green. **Decision: the flip ships only after Tasks 12–17 (executor consumption) land.** Task 17 re-attempts this flip in the correct order; Tasks 12–16 are the execution build-out that closes the gap.

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

## Phase 5 — Layer-target execution (the cutover enabler)

> **Grounding:** Modeled on Graphite/Dawn (Skia main exploration, 2026-08-02, evidence in the plan Context/Architecture): one render pass per layer target + one root pass in a single command encoder; composite = textured-quad draw with the real blend plan + alpha in the parent pass. Kanvas reuse templates: the A8 coverage-mask producer pattern (`GPUWgpu4kCoverageMaskProducerMaterializer`, pooled RGBA8 texture `RenderAttachment|TextureBinding`, render-scope operands in the same encoder, Clear→Load, pool lease) and the prepared image shader (`GPUPreparedImageShader`/`preparedImageAtlasSourceBlend`) for the textured-quad composite.
>
> **Reusability verdicts (fp-07 cutover exploration):** NOT reusable — `GPUFilterDAGExecutor` (counting stub, deleted by cutover), filter-contract moves. PARTIALLY reusable — the 4-line `GPUPreparedSurfaceProductRouter.hasTerminalPreparedFamily` delta (adopt LAST, as the loud-refusal safety net), `RectOnlyOffscreenRenderer.materializeSaveLayerScene` (worked example already mirrored in our builder). ALREADY PORTED — `handleSaveLayer`/`materializeSaveLayer`/generation/innermost-first ordering (we have a superset incl. `mergeCompositeCommands` + production caller). The real gaps are OUR TODOs: `RecordingContracts.kt:1104`, `GPUPreparedWindowOutput.kt:97`.

### Task 12: Carry `compositeCommands` through `GPUPreparedWindowOutput.attachToFrame`

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedWindowOutput.kt` (attachToFrame ~l.71-119, TODO at l.97)
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedWindowOutputTest.kt` (find the existing test for this class first — or add the assertion to the closest existing window-output test)

**Context:** `attachToFrame` rebuilds the `GPUTaskList` at l.98-119 and **drops `compositeCommands`** — the merged layer commands never reach the planner/executor. This is the first carriage gap.

- [ ] **Step 1: Write the failing test (red)**

Read `GPUPreparedWindowOutput.kt` first — how `attachToFrame` builds the rebuilt task list (which fields it preserves: tasks, dependencies, readback…). Find the existing test file (`rtk glob kanvas/src/test/**/GPUPreparedWindowOutput*`). Add a test: build a `GPUTaskList` with `withCompositeCommands(listOf(compositeCommand))`, attach it, assert the rebuilt task list carries the same `compositeCommands` (non-empty, same commands).

```kotlin
@Test
fun `attachToFrame carries composite commands forward`() {
    val taskList = GPUTaskList(...) // as the existing tests construct it
        .withCompositeCommands(listOf(/* a CompositeLayer command from the Task 7/8 test fixtures */))
    val output = GPUPreparedWindowOutput(...) // existing fixture
    val attached = output.attachToFrame(taskList, ...)
    assertEquals(listOf(/* the command */), attached.compositeCommands)
}
```

Adapt to the real constructor shapes (read the existing tests). If `attachToFrame`'s signature doesn't take a task list directly, mirror how the existing test drives it.

- [ ] **Step 2: Run to verify it fails**

Run: `rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedWindowOutput*" --no-parallel`
Expected: FAIL — rebuilt list has empty `compositeCommands`.

- [ ] **Step 3: Implement**

In `attachToFrame`, preserve the field on the rebuilt task list (pass `compositeCommands = sourceTaskList.compositeCommands` into the rebuild — read the rebuild call at l.98-119 and add the parameter; if `GPUTaskList` is immutable-with-copy, use the existing `withCompositeCommands` accessor to copy). Remove the TODO at l.97.

- [ ] **Step 4: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedWindowOutput*" --no-parallel
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedWindowOutput.kt <test file>
git commit -m "fix(composite): carry composite commands through window output attach"
```

### Task 13: Planner lowering of `compositeCommands` into frame steps

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt` (step mapping at l.793-896; multi-target child-scope support at l.270-334)
- Modify (only if needed): `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt` (frame-step vocabulary if a new step kind is needed)
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerTest.kt` (find existing)

**Context:** `GPUFramePlanner.plan` never reads `compositeCommands` (grep: zero references) — it lowers `tasks` only. The commands must become frame steps: `PrepareLayerTarget` → a layer-target preparation step (texture allocation spec); `RenderLayerChildren` → child render steps targeted at the layer texture; `CompositeLayer` → a composite render step (after children). The planner already supports multi-target child scopes (l.270-334) and `TargetTransitionStep` (l.825) — reuse that machinery.

**Design decision for the implementer (trace and report):** does the existing `GPUFrameStep` vocabulary (RenderPassStep/PrepareResourcesStep/CopyResourceStep/…) already express "render into a non-scene target" (the coverage-mask producer does exactly this — find how its scopes become steps), or does a new step kind (e.g., `LayerTargetRenderStep` / `CompositeRenderStep`) need to be added? Prefer reusing the existing multi-target machinery; add a step kind only if the trace proves necessary.

- [ ] **Step 1: Read first (mandatory)**

Trace how the coverage-mask producer scopes flow: `GPUCorePrimitiveCoverageMaskPreparedExecutionRoute.kt:545-714` (seal pattern) → planner → steps → `GPUPreparedNativeScopeOperand.Render` (per-pass operands with `colorTarget`). Report the exact step types used for "render to mask texture" and whether they can carry layer targets.

- [ ] **Step 2: Write the failing test (red)**

In the planner test: build a task list carrying `compositeCommands` (one `PrepareLayerTarget` + one `RenderLayerChildren` + one `CompositeLayer` — use the real command constructors from PassContracts.kt:656-830, e.g. from the Task 8 test fixtures) and assert the planned steps include the layer-target preparation + child render step targeting the layer + the composite step, in order (children before composite).

- [ ] **Step 3: Run to verify it fails**

Expected: FAIL — planner produces no composite steps.

- [ ] **Step 4: Implement**

In `GPUFramePlanner.plan`, after the task lowering, lower `taskList.compositeCommands` into steps: resolve each command (labels → the layer-target preparation + render + composite steps) using the existing multi-target step machinery. Order: for each layer (innermost-first, as the lowerer already sorts), `PrepareLayerTarget` → its `RenderLayerChildren` step(s) → after all layers, the `CompositeLayer` step(s).

- [ ] **Step 5: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUFramePlannerTest" --no-parallel
git add -A
git commit -m "feat(composite): plan composite commands into frame steps"
```

### Task 14: Session validator accepts declared layer targets

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSceneCompatibilityValidator` in `GPUBackendRuntimeNative.kt` (single-scene-target invariant at l.1392-1413; preflight context at l.1486-1491; generation map at l.1433; executor staleness at `GPUFrameExecutor.kt:1024-1045`)
- Test: the existing validator test (find it: `rtk grep -rln "target-count\|scene-target\|CompatibilityValidator" kanvas/src/test gpu-renderer/src/test`)

**Context:** The validator rejects frames whose render targets exceed the one scene target (`unsupported.prepared-scene-session.target-count`, l.1392-1395). Layer targets are declared via `PrepareLayerTarget` commands (exact bounds, sampleCount 1, format, `RenderAttachment|TextureBinding`). The validator must admit: one scene target + N declared layer targets, and still fail-closed for anything undeclared.

- [ ] **Step 1: Write the failing test (red)**

In the validator test: a frame whose task list carries one scene target + one layer-target render (via `compositeCommands`) must validate; a frame with an undeclared extra target must still refuse with the target-count code.

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL — layer-target frame refused by the target-count check.

- [ ] **Step 3: Implement**

Extend the target-count check: collect the declared layer targets from `taskList.compositeCommands` (`PrepareLayerTarget.targetLabel` + descriptor), admit render targets that are (a) the single scene target OR (b) a declared layer target with compatible descriptor (exact bounds, sampleCount 1, format, `RenderAttachment|TextureBinding` — validate like the scene target does at l.1396-1413). Keep the preflight context + generation map keyed per target (scene target + each layer target).

- [ ] **Step 4: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*CompatibilityValidator*" --no-parallel
git add -A
git commit -m "feat(composite): accept declared layer targets in prepared scene validation"
```

### Task 15: Layer-target materialization — allocation, children render, composite draw

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt` (image shader preflight at ~l.13-130 — the layer composite reuses `preparedImageAtlasSourceBlend`)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt` (the prepared surface frame materializer — where the layer-target scopes and composite draw get encoded)
- Modify (template reuse): `GPUWgpu4kCoverageMaskProducerMaterializer.kt` pattern (l.115-199) for the layer-target texture; `GPUPreparedImageShader.kt` for the composite quad
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kLayerTargetCompositeSmokeTest.kt` (new — modeled on `GPUWgpu4kDestinationCopyFrameSmokeTest` which proves multi-scope one-submission rendering)

**Context:** This is the core execution task. The prepared runtime already renders multiple scopes in ONE encoder/ONE submit (the mask producer + destination-copy smoke test prove it). The materializer must: (1) allocate each layer texture (pool lease — reuse the coverage-mask pool pattern: RGBA8, sampleCount 1, `RenderAttachment|TextureBinding`, exact bounds); (2) encode child render scopes targeting the layer texture (Clear→children→Store — the mask producer's Clear-white→Load pattern, with the layer's clip applied per child draw); (3) encode the composite render scope: a textured-quad draw sampling the layer texture onto the parent (scene) target with the real `GPUBlendPlan` + `alpha` from the `CompositeLayer` command, clip via scissor or the existing clip machinery.

**Evidence of the textured-quad+blend template:** `GPUPreparedImageShader` (`GPUPreparedImageShader.kt:13-130`) — `preparedImageAtlasSourceBlend(mode)` maps a blend mode to an atlas source blend; `GPUPreparedSurfaceNativePreflight` and `GPUWgpu4kPreparedSurfaceFramePayloadMaterializer` are its consumers. The composite draw reuses this image-shader path with the layer texture as the source.

- [ ] **Step 1: Read first (mandatory)**

Read: `GPUWgpu4kPreparedSurfaceFramePayloadMaterializer` (how render scopes become wgpu4k passes), `GPUWgpu4kCoverageMaskProducerMaterializer` (pool texture + Clear→Load scopes + consumer bind group — l.115-199, 846-867), `GPUPreparedImageShader` + its two consumers (how a textured image draw with blend is encoded today), and `GPUWgpu4kDestinationCopyFrameSmokeTest` (the multi-scope one-submission test template). Report the exact seam where layer-target scopes plug into the materializer (after the coverage-mask producer scopes, before/around the scene pass).

- [ ] **Step 2: Write the failing test (red)**

`GPUWgpu4kLayerTargetCompositeSmokeTest`: a frame with one saveLayer (rect child, srcOver blend, alpha 1) → execute → read back → assert the layer target pass ran and the composite produced the expected pixel (child rect color at the layer's position). Model the fixture on the destination-copy smoke test (offline backend, readback). Also assert a translucent alpha (0.5) case composites correctly over a background rect.

- [ ] **Step 3: Run to verify it fails**

Expected: FAIL — no layer-target scopes encoded (or wrong pixels).

- [ ] **Step 4: Implement**

In the materializer + preflight:
1. `PrepareLayerTarget` → pool lease for the layer texture (reuse/extend the coverage-mask pool slot machinery — `GPUWgpu4kCorePrimitiveFramePool.kt:846-867` shows the texture+view+bind-group creation).
2. `RenderLayerChildren` → render scope with `colorTarget = layerTexture`, `loadOp = Clear` (transparent) on the first child scope, `Load` after, `storeOp = Store`; child draws as in the coverage-mask producer (`GPUPreparedNativeScopeOperand.Render`, `GPUWgpu4kFrameEncodingBackend.kt:166-211, 411-450`).
3. `CompositeLayer` → a textured-quad render scope in the parent pass: bind the layer texture view, use the real `blendPlan` (already on the command) via the image-shader/pipeline path (`preparedImageAtlasSourceBlend`), apply `alpha` (premultiply or blend-factor), apply `clipLabel` (scissor bounds if the clip is a device-rect — the label carries `l,t,r,b,aa`).

- [ ] **Step 5: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUWgpu4kLayerTargetCompositeSmokeTest" --tests "*GPUWgpu4kDestinationCopyFrameSmokeTest" --no-parallel
git add -A
git commit -m "feat(composite): materialize layer targets and composite draws"
```

### Task 16: Flat-render elision for composite frames

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt` (composite-first block, TODO at l.118)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` (`mapCoreOperation` returns null for `DrawPicture` — the silent-drop path)
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeFrameRouteIntegrationTest.kt` (extend) + `GPUAllApiBlendSurfaceTest` regression guard

**Context:** Two defects from the Task 9 triage: (1) composite-only frames double-render (children drawn flat AND via the layer pass); (2) `DrawPicture` content is silently dropped by the flat mapper. With execution in place (Task 15), the elision rule is: when `compositeCommands` are scheduled, elide the flat child render for the composite scope (children render once, into the layer target); a composite-only frame must not emit the flat child draws. Mixed composite+visual frames: explicit topology decision — either full coverage via composite commands or a stable refusal (never a silent drop).

- [ ] **Step 1: Write the failing test (red)**

Extend the integration test: a composite-only frame (BeginLayer + rect + EndLayer) → assert the result does NOT double-emit the child rect as a flat draw (the flat visual command is elided) while `compositeCommandCount > 0`. And: a `DrawPicture` in a composite frame → assert either correct rendering via the composite commands or a stable terminal refusal — NOT a silent drop (assert the refusal code or the composite commands cover it).

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL — flat render still emitted / DrawPicture silently dropped.

- [ ] **Step 3: Implement**

In `build()`: when `compositeHandling` is `Ready` with commands, skip the flat visual lowering for the composite scope's children (elide `mapCoreOperation` for those ops); `DrawPicture` inside a composite frame routes through the composite commands only. If a mixed topology cannot be fully covered, return a stable terminal refusal (documented code) instead of a silent drop.

- [ ] **Step 4: Run to verify it passes + commit**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUAllApiBlendSurfaceTest" --no-parallel
git add -A
git commit -m "fix(composite): elide flat child render when composite commands are scheduled"
```

### Task 17: The gate cutover — flip in the correct order (consumption first)

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt` (flip — recipe from the withheld trial, evidence file §2)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt` (empty `LegacyDisplayOpFamily`, `allowedFamilies = emptySet()`)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt` (add `DrawPicture`/`BeginLayer`/`EndLayer` to `hasTerminalPreparedFamily()` — the loud-refusal safety net, ADOPTED LAST)
- Update with evidence: the 5 legacy-pinning test files from the trial (§2 of the evidence file) + re-run the 4 composite classes from the triage (§3)
- Update: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-07-composite-route-evidence.md` (append the re-flip results)

**Context:** The precondition is now Tasks 1–16 green (execution works). This re-attempts the Task 9 flip in the correct order — the failure mode of the withheld trial (298 failures, executor gap) is closed. Expected triage after this flip: composite frames render CORRECTLY (Task 15) or refuse LOUDLY via the router's terminal family + the capture's explicit refusals (203 non-core-children cases become documented stable refusals, NOT silent drops — per the evidence file §5 item 3, re-point those expectations to the observed terminal codes with evidence).

- [ ] **Step 1: Confirm the precondition (Tasks 1–16 green)**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :gpu-renderer:test --tests "*GPUPreparedCompositeLowererTest" --tests "*GPUPreparedCompositePreflightTest" --tests "*GPUSaveLayerNativeExecutorTest" --tests "*GPUFilterOracleTest" --tests "*GPUPreparedFilterDAGPlannerTest" --tests "*GPUBlendOracleTest" --tests "*GPUPreparedSaveLayerFrameHandlingTest" --tests "*GPUWgpu4kLayerTargetCompositeSmokeTest" --no-parallel
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --no-parallel
```

- [ ] **Step 2: Flip gate + adapter + router as one unit** (recipe from the withheld trial §2; adopt the router terminal-family delta last).

- [ ] **Step 3: Full-suite triage with evidence**

```bash
rtk proxy ./gradlew -p /Users/chaos/.codex/worktrees/da7e/kanvas :kanvas:test :gpu-renderer:test --no-parallel 2>&1 | tee /tmp/fp17_cutover.log
```

Classify: (a) real regressions → FIX in Tasks 15–16 code; (b) legacy-pinning expectations → update with evidence (test name, old/new assertion, route diagnostic, diff/stat) appended to the evidence file; (c) non-core-children terminal refusals → re-point expectations to the stable codes with evidence. The `nested_vertices` pin and FP-06 guards must stay green.

- [ ] **Step 4: Run full suites again** — green (no masking).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(composite): flip prepared frame gate for composites with evidence-based expectations"
```

---

## Phase 6 — Validation & FP-07 closure

### Task 18: Full validation, evidence, and closure

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

`git log --oneline 40a873560..HEAD | cat` — expected: Tasks 1..17 commits (docs → foundation → oracle fixes → wiring → execution → cutover), no early-cutover commits, no `.superpowers/sdd/` entries in the log.

---

## Self-review notes (filled at plan time)

- **Spec coverage:** revised design's 5 phases map to Tasks 1–17: Task 0→1, Phase 1→2–3, Phase 2→4–6, Phase 3→7–8, Phase 4→9–10 (Task 9 trial executed and WITHHELD with evidence — the gate flip moved to Task 17 after the execution phase), Phase 5 (execution, added 2026-08-02 from the Graphite/Dawn grounding)→12–16, cutover→17, validation→18 (renumbered from 11). Non-goals respected: the execution phase deliberately does NOT introduce Graphite's approx-fit (we keep exact-size pool allocation — the coverage-mask pool template), no EdgeAA quad (single-sample textured quad, documented fidelity gap), no dynamic WGSL, no monolith split.
- **Execution-phase grounding:** Tasks 12–17 are anchored on the Skia main exploration (Graphite layer model + Dawn pass encoding, 2026-08-02) and the Kanvas reuse templates (coverage-mask producer, prepared image shader, destination-copy smoke test). Every task says where to read the template and what the seam is; where a constructor or step kind must be confirmed (e.g., planner step vocabulary, pool-slot API), the task mandates a read-first step with a report.
- **No placeholders:** every task has concrete files, commands, and code. Where a signature must be confirmed against the base (e.g., `GPUPreparedFilterNode`, `GPUTargetPreparationContext`, planner step kinds, pool-slot API), the task says exactly where to read it and what to verify.
- **Type consistency:** `GPUPreparedCompositeLowering.Ready/Refused`, `GPUPreparedSaveLayerFrameHandling.Ready/Refused`, `GPUPreparedCompositeCaptureResult.Ready/Refused`, `GPUPreparedSurfaceFrameBuildResult.Ready/Refused` are used consistently throughout; `handleSaveLayer` signature matches the validated port; `CompositeLayer` carries the real `GPUBlendPlan` + `alpha` + `clipLabel` (Tasks 8) that the executor consumes (Task 15).
- **Known plan corrections recorded:** (1) Task 7's "cutover already contained the merge mechanics" was wrong — the merge design is new and additive (`compositeCommands` carrier); (2) Task 9's flip was executed and WITHHELD with evidence (298 failures, executor gap) — the flip is retried at Task 17 in the correct order; (3) the executor consumption is a real multi-file build-out (validator relaxation, planner lowering, materializer scopes, elision), not a 10-line fix.
