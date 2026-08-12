# FP-11 — Close Bounded Native-Rendering Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the bounded native-rendering gaps explicitly retained by FP-08/FP-09/FP-10: the FP-09 terminal refusals (hairline points, mixed uniform layouts, analytic-clip non-direct geometry, dst-read residuals, path destination-read) and the FP-10-exposed mask-blur retained-target ordering bug, each either covered with CPU/reference + native GPU evidence or re-documented as a stable justified terminal refusal, without touching the two pre-existing baseline failures.

**Architecture:** FP-11 is a basket of 8 independent technical gaps, not a single migration. Each gap refuses at a different level (semantic builder, recording task-list builder, preflighter, or execution materializer); the plan classifies each gap by root-cause level and prioritizes by frequency and technical dependency. Coverage work reuses the machinery already built: the FP-09 multi-pipeline per-pass direct materializer (`BindGraphicsPipeline` mid-pass), the GPU-owned `TextureCopy` destination snapshot + `GPUBlendFormulaLibrary` formula lane, the path-stencil producer/cover lowering, the `GPUPreparedMaskFilterLowerer`/`GPUSeparableBlur` blur lane, and the FP-10 retained session checkin. The retained-session contract (checkin, never close-per-frame) is preserved so the FP-10 crash class cannot re-expose. All new tests respect the session checkin; GPU-dependent suites are annotated as WebGPU-environment-dependent.

**Tech Stack:** Kotlin, WebGPU via wgpu4k, WGSL generation, Gradle (`./gradlew -F off`), JUnit (`kotlin.test`), CPU pixel oracles (`TopLevelMaskBlurPixelOracle`, `GPUBlendOracle`/`GPUBlendFormulaLibrary`).

**Reference docs:**
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-11 entry (`pending`): the 7 FP-09 transfers + 1 FP-10 transfer, and the acceptance contract.
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-09-retire-legacy-immediate-renderer-evidence.md` — §2 (per-code case counts), §3 (blend-suite route table), §4 (path-dst-read designed refusal root cause), §5 (clip-suite re-points).
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-10-reusable-prepared-surface-session-evidence.md` — §11 (the retained-target ordering gap: root cause, why it is a pre-existing semantic gap exposed by session reuse, "no test covers the leading-blur-mixed shape").
- `reports/fp09-retire-legacy-immediate-renderer-plan.md` / `reports/fp10-reusable-prepared-surface-session-plan.md` — structure template; the Task 3b/3c multi-pipeline and the executor session-checkin evidence are FP-11's raw material.
- `reports/upstream-rebaseline/2026-06-29-gpu-renderer-pre-existing-test-failures.md` — `GPURendererPackageBoundaryTest` package-boundary case is a documented pre-existing failure (exactly 20 cycle violations, 0 rule violations); FP-11 must NOT fix it and must NOT change its failure state. `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` is a documented pre-existing hardware failure (reproduces at base SHA); do not modify.

---

## Context: validated branch state (evidence, 2026-08-12)

**HEAD:** `f14656988` (FP-10 squash-merge #2060, working tree clean). Branch `codex/graphite-dawn-frame-fp11`.

**Baseline verified at plan time (2026-08-12, this worktree):**
- `./gradlew -F off :kanvas:test --tests "*GPUFramePathApiInventoryTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (12 s). Pins the hairline/round-cap point refusal codes.
- `./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (14 s). Pins `core_primitive_mixed_uniform_layouts` (5 sites) and the path-dst-read / analytic-clip refusals.
- `./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kMaskBlurFramePayloadMaterializerTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (9 s). Pins `unsupported.native-mask-blur.clip` (composite clip scenarios + the `topLevelMaskBlurCompositeClipRefusal` predicate).
- `./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (11 s). Pins `dst-read-formula` (single-key) and `analytic-shape-multi-key` (multi-key) execution refusals.
- `./gradlew -F off :gpu-renderer:test --tests "*GPUFramePreflighterTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (14 s). Pins `unsupported.preflight.core_primitive_mixed_uniform_layouts` and `unsupported.native-core-primitive.multi-render-dst-copy`.
- `./gradlew -F off :kanvas:test --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks` → BUILD SUCCESSFUL (3 m 13 s, WebGPU present on this host: Intel UHD Graphics 630 + AMD Radeon Pro 5500M). GPU-dependent suites run for real here.

**Build command convention (this worktree):** `rtk proxy` is not on PATH; use `./gradlew -F off <tasks> --no-parallel --console=plain` with dependency verification disabled. Do NOT modify `gradle/verification-metadata.xml`. Use `--rerun-tasks` for the GPU pixel suites when real proof is required (they are cached otherwise). GPU-backed suites (`GPUAllApiBlendSurfaceTest`, `GPUClipCoverageSurfaceTest`, `GPUClipAdvancedBlendSurfaceTest`, `GPUMaskBlurSurfaceTest`, `GPUPathClipRegressionTest`, `GPUWgpu4kDestinationCopyFrameSmokeTest`) skip via `assumeTrue` when the backend is unavailable — annotate results accordingly.

### 1. The eight gaps (inventory, root cause, classification) — verified at HEAD `f14656988`

The per-code case counts are the FP-09 evidence §2 measured distribution (authoritative for the current matrix). Emission-site lines were re-verified at HEAD (they moved vs. the FP-09/roadmap record: hairline 409/411/465 → **557/559/613**, mixed-layouts 1602/2104/3307 → **1617/2120/3311**, analytic-clip 1994 → **2009**, path-dst-read → **2564**).

| # | gap | code | cases | emission (HEAD) | refusal level | class |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | mask-blur leading-composite retained-target ordering | none (silent wrong pixels) | 1 shape (untested) | `GPUTopLevelMaskBlurFrameRecording.kt:170` (`firstCompositeClears = sceneRenders.isEmpty()`), consumed at `:222` | recording (composite loadOp decision) | **A** — bug fix, priority 1 |
| 1 | exact hairline point lowering | `unsupported.core_primitive.point.hairline_exact_lowering` | 175 | `GPUCorePrimitiveSemanticBuilder.kt:557` (points); `round_cap` twins at `:559` (points), `:613` (strokes) | semantic (geometry lowering) | **A** — real prepared coverage, priority 2 |
| 5 | multi-render dst-copy (destination-then-consumer dst-read) | `unsupported.native-core-primitive.multi-render-dst-copy` | 60 | `GPUFramePreflighter.kt:3384` (2-render shape), `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:5202` (comment) | preflight/execution (one-render direct lane) | **A** — prerequisite for #7, priority 3 |
| 7 | path destination-read | `unsupported.native-core-primitive.path-destination-read` | 60 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2564` | recording (dst-read cover consumer ref + own-pass) | **A** — depends on #5, priority 4 |
| 2 | multi-uniform-layout direct passes | `unsupported.recording.core_primitive_mixed_uniform_layouts` (+ preflight twin `unsupported.preflight.core_primitive_mixed_uniform_layouts`) | 202 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617, 2120`; `GPUFramePreflighter.kt:3311` | recording/preflight/execution (one uniform layout per pass) | **A** (if the pass split lands cleanly; verify-gate → B otherwise), priority 5 |
| — | mask-blur composite analytic clips | `unsupported.native-mask-blur.clip` | 2+ (clip-suite cases) | `GPUTopLevelMaskBlurFrameRecording.kt:1019` (predicate), `GPUWgpu4kMaskBlurFramePayloadMaterializer.kt:442` | recording/execution (composite clip lane scope) | **A** — analytic device-rect clip coverage, priority 6 |
| 3 | analytic clips over non-direct shading geometry | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (+ intersection twin) | 2 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009, 2013` | recording (clip authority requires direct shading geometry) | **B** — re-document (cost ≫ value) |
| 4 | dst-read formula on mapped routes | `unsupported.native-core-primitive.dst-read-formula` | 2 | `GPUCorePrimitiveNativeRoute.kt:415` (multi-key), `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:893` (single-key) | execution (formula program availability) | **B** — re-document |
| 6 | analytic-shape multi-key dst-read | `unsupported.native-core-primitive.analytic-shape-multi-key` | 2 | `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1429` | execution (AA coverage semantics unverified) | **B** — re-document |

**Complex-clip blur** (`invalid.preflight.core_primitive_clip_producer_authority`, `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:883`) stays **B** across FP-11: the clip-producer authority is a general clip-execution feature (it fires for any frame whose clip producer/consumer authority is not sealed), and the blur family only rides it. It is pinned by `GPUClipCoverageSurfaceTest.kt:63` and `GPUFramePreflighterTest`; it is listed in this plan's Task 8 B-table with justification, not covered.

**Per-gap root-cause detail (the task evidence):**

- **#8 (retained ordering).** `firstCompositeClears = sceneRenders.isEmpty()` decides the FIRST chain's composite `loadOp`. On a retained session (FP-10) a MIXED frame whose first paint op is a mask blur sorts its chain composite before the frame's first scene clear render, so the composite runs `loadOp="load"` and samples the previous frame's pixels outside its scissor. Correct condition is "no scene clear render ordered BEFORE this composite", per chain, not "no scene renders at all". No test covers the leading-blur-mixed shape (FP-10 evidence §11).
- **#1 (hairline points).** `pathDeviceGeometry` (`GPUCorePrimitiveSemanticBuilder.kt:551-604`) refuses any `drawPoint`/`drawPoints.points` with `strokeWidth == 0f`. Non-hairline butt/square points already lower via `strokeDeviceGeometry` as zero-length single-segment strokes (width > 0). The lower-level stroke contract also refuses hairlines: `GPUStrokeDescriptor.hairline` → `unsupported.stroke.hairline_policy` and `width <= 0f` → `unsupported.stroke.width_invalid` (`gpu-renderer/.../geometry/GeometryContracts.kt:1794-1808`). Exact hairline lowering therefore needs (a) a canonical 1-device-px point square at the semantic level and (b) admission of that canonical width in the geometry/stroke authority.
- **#5 (multi-render dst-copy).** The direct lane preflighter requires `coreRenders.size == 1` for non-mixed-boundary frames (`GPUFramePreflighter.kt:3363-3389`). A destination-then-consumer dst-read frame splits into producer render → `CopyDestinationStep` → consumer render (the Graphite `DrawContext.cpp` recipe, documented in FP-09 Task 3c). The recording already emits the `CopyDestinationStep` and the snapshot grouping is family-agnostic; only the preflight admission and executor materialization of the 2-render direct shape are missing.
- **#7 (path destination-read).** `buildCorePrimitiveDestinationSnapshotPlans` records the `TextureCopy` consumer ref at the BASE packet id, but path-stencil (`StencilEdgeFan`) lowers into producer/cover packets with fresh ids (`<base>.path-stencil-cover`), so the dst-read cover can never be resolved; and the dst-read formula forces the cover into its own render pass, which the path-stencil authority rejects (FP-09 evidence §4). The recording refuses by name at `:2556-2567` before the assembler throws.
- **#2 (mixed uniform layouts).** One direct pass materializes ONE shared uniform slab; frames with ≥2 distinct layouts (`uniform32`/`uniform64`/`uniform80`/`uniform160`/path) refuse at `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617` (analytic-shape+clip mix) and `:2120` (`activeDirectUniformLayouts > 1`), with the preflight twin at `GPUFramePreflighter.kt:3311`. The FP-09 multi-pipeline machinery already materializes N structural pipeline KEYS per pass with one shared slab; the gap is pass SPLITTING by uniform layout (each split pass has its own slab), which is the FP-09 `BindGraphicsPipeline`-mid-pass generalization the mission flagged as "le même mécanisme de split de pass".
- **mask-blur composite clips.** `topLevelMaskBlurCompositeClipRefusal` (`GPUTopLevelMaskBlurFrameRecording.kt:1009-1020`) refuses any blur-composite clip beyond `NoClip`/`ScissorOnly`; the materializer enforces it at `GPUWgpu4kMaskBlurFramePayloadMaterializer.kt:435-446`. The composite fragment shader (`fs_main`, `:989-1006`) samples blurred mask coverage and blends over the dst snapshot; an analytic device-rect clip needs a clip-coverage term folded into the composite.
- **#3/#4/#6 (B family).** All three are 2-case residuals whose refusal level is precise (recording for #3, execution for #4/#6) and whose coverage cost (analytic-clip over stencil-shaded geometry; scalar-coverage dst-read formula programs; AA multi-key analytic-shape blend semantics) exceeds their value at 2 cases each. Each stays a stable typed refusal and is re-documented in Task 8 with cost/value.

### 2. Dependency graph (task ordering)

```
#8 retained ordering (independent, bug, visible)      → Task 2
#1 hairline points (independent, 175)                 → Task 3
#5 multi-render dst-copy (60)                         → Task 4 ─┐ prerequisite
#7 path destination-read (60)                         → Task 5 ─┘
#2 multi-layout (202, if feasible)                    → Task 6
mask-blur composite analytic clips (bounded)          → Task 7
#3 #4 #6 (+ complex-clip blur) re-documentation       → Task 8
regression + closure + roadmap FP-11 completed        → Task 9
```

Dependencies verified at HEAD: `#7` requires the #5 two-render dst-read shape (the dst-read cover must run in its own pass); `#2` requires the multi-key direct pass seal + per-layout slabs (FP-09 Task 3b/3c machinery) — a verify-gate in Task 6 decides A-vs-B if the seal breaks; `#8` depends on the FP-10 session loadOp semantics (already landed). Tasks 2/3/4/5/7 are pairwise independent.

### 3. Pinned test suites (re-point inventory, per gap)

| Test | pins | disposition |
| --- | --- | --- |
| `GPUFramePathApiInventoryTest.kt:723-737` | `drawPoint hairline refuses…` (`hairline_exact_lowering` at 735) | Task 3: flip to a geometry assertion (1-device-px square) |
| `GPUAllApiBlendSurfaceTest.kt:981-993` | `PREPARED_POINT_HAIRLINE_REFUSAL`, `PREPARED_MIXED_UNIFORM_LAYOUTS_REFUSAL`, `PREPARED_ANALYTIC_CLIP_NON_DIRECT_REFUSAL`, `PREPARED_MULTI_RENDER_DST_COPY_REFUSAL`, `PREPARED_PATH_DST_READ_REFUSAL` + `expectedPreparedProductRoute` matrix | Tasks 3-7: covered rows flip to `Prepared` (pixel oracle + `route:destination-read:*`), the B rows keep `Terminal(code)` |
| `GPUClipCoverageSurfaceTest.kt:55-68` | `PREPARED_MIXED_UNIFORM_LAYOUTS_REFUSAL`, `PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL`, `PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL`, `PREPARED_DST_READ_FORMULA_REFUSAL`, `PREPARED_HAIRLINE_REFUSAL` | Tasks 3/6: covered codes re-point; B codes keep |
| `GPUClipAdvancedBlendSurfaceTest.kt:28-100` | AA-clip dst-read + scissor dst-read → `mixed_uniform_layouts` (55, 97) | Task 6: flip to Prepared with pixel evidence |
| `GPUPathClipRegressionTest.kt:23-146` | device-rect-clip path (53), dst-in path (86), darken-rect-over-destination (112), advanced path blend (143) | Tasks 4/6: flip to Prepared |
| `GPUMaskBlurSurfaceTest.kt:221-243` | stacked + AA-rect clip blur → `unsupported.native-mask-blur.clip` (235, 242) | Tasks 2/7: leading-blur mixed test added; AA-rect clip flips to Prepared |
| `GPUWgpu4kMaskBlurFramePayloadMaterializerTest.kt:546, 577-595` | `Scenario("clip", …)`, `topLevelMaskBlurCompositeClipRefusal` predicate | Task 7: re-point the analytic-clip scenario; keep a stencil/complex-clip refusal scenario |
| `GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt:1190, 1407, 1431, 1464, 2445` | `core_primitive_mixed_uniform_layouts` | Task 6: re-point to split-pass assertions |
| `GPUPreparedSurfaceFrameBuilderTest.kt:669` | `core_primitive_mixed_uniform_layouts` refusal row | Task 6: flip to Ready + split-pass assertion |
| `GPUPreparedSurfaceProductRouterTest.kt:465-470` | terminal-family matrix (hairline, mixed-layout, analytic-clip, multi-render, multi-key, dst-read-formula, path-dst-read) | Tasks 3-7: covered codes flip to `Prepared`; B codes keep `Terminal` |
| `GPUPreparedSurfaceFrameExecutorTest.kt:793-804` | `multi-render-dst-copy` residual diagnosis | Task 4: re-point to the admitted 2-render shape |
| `GPUFramePreflighterTest.kt:3306-3314, 3363-3389` | mixed-layout + multi-render preflight refusals | Tasks 4/6: re-point to admission |
| `GPUFramePlannerDestinationContractTest.kt` | dst-read snapshot contract | Tasks 4/5: add the path-cover consumer-ref contract |
| `GPUPreparedSurfaceFrameExecution.kt:1086-1091` | `preparedRouteResidualRefusalCodes` (`dst-read-formula`, `multi-render-dst-copy`, `analytic-shape-multi-key`) | Tasks 4/5: remove covered codes (B codes stay) |

### 4. Guards / baselines that MUST survive this plan

- `GPURendererPackageBoundaryTest` package-boundary case — documented pre-existing (exactly 20 cycle violations, 0 rule violations); do not fix, do not change failure state.
- `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` — documented pre-existing hardware failure (reproduces at base SHA); do not modify.
- `GPUPreparedSurfaceLegacyAbsenceTest` — 16 retired tokens unchanged; no legacy production token may reappear (absence guard).
- `GPUPreparedCompositeCaptureSemanticTest` / `GPUPreparedCompositeFrameRouteIntegrationTest` — composite guards (`nested_vertices`, `unsupported.composite.*`) untouched.
- The `failed.surface.prepared.session-close` flake (FP-09 §17) — documented environmental; never weakened for. Do not call `session.close()` per frame anywhere (retained-session checkin contract).
- The FP-08/FP-09 destination-read machinery (GPU-owned `TextureCopy`/formula, `GPUDestinationSnapshotOperation`, `GPUBlendFormulaLibrary`) is reused, not re-plumbed.
- Destination continuation stays GPU-owned — no CPU readback is added for any coverage.

---

## File Map

### New (this plan)
- `reports/fp11-close-bounded-native-rendering-gaps-plan.md` — this plan (Task 1, committed as the FP-11 reference).
- `reports/fp11-gap-map.txt` — the §1 refusal-code map as a saved before-snapshot (Task 1).
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-11-close-bounded-native-rendering-gaps-evidence.md` — evidence report (Tasks 8, 9).

### Modified (production)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUTopLevelMaskBlurFrameRecording.kt` — per-chain composite clear predicate (Task 2); composite clip admission + clip-coverage term in the composite WGSL (Task 7).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt` — hairline point lowering (Task 3).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt` — hairline-width admission for the canonical point square (Task 3, verify-then-wire).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt` — multi-render dst-read admission (Task 4); multi-layout direct-pass admission (Task 6).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt` — path-dst-read consumer-ref resolution + refusal removal (Task 5); direct-pass split by uniform layout (Task 6).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` — 2-render dst-copy core lane materialization (Task 4, verify-then-wire); per-layout slabs (Task 6, verify-then-wire).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kMaskBlurFramePayloadMaterializer.kt` — analytic-clip composite binding (Task 7).
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt` (or the direct-pass seal) — multi-layout seal (Task 6, verify-then-wire).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` — `preparedRouteResidualRefusalCodes` (Tasks 4/5).

### Modified (tests)
- `GPUFramePathApiInventoryTest.kt`, `GPUAllApiBlendSurfaceTest.kt`, `GPUClipCoverageSurfaceTest.kt`, `GPUClipAdvancedBlendSurfaceTest.kt`, `GPUPathClipRegressionTest.kt`, `GPUMaskBlurSurfaceTest.kt`, `GPUWgpu4kMaskBlurFramePayloadMaterializerTest.kt`, `GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt`, `GPUPreparedSurfaceFrameBuilderTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `GPUPreparedSurfaceFrameExecutorTest.kt`, `GPUFramePreflighterTest.kt`, `GPUFramePlannerDestinationContractTest.kt`.

### Explicitly NOT touched
- `GPURendererPackageBoundaryTest`, `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`, `GPUPreparedSurfaceLegacyAbsenceTest`, `GPUPreparedCompositeCaptureSemanticTest`, `GPUPreparedCompositeFrameRouteIntegrationTest`, `GPUPreparedSurfaceNativePreflight.kt`, `gradle/verification-metadata.xml`, `GPUPreparedSurfaceLifetimeStressTest` (FP-10 contract unchanged), the session-close flake assertions.

---

## Phase 0 — Inventory & green baseline

### Task 1: Freeze the FP-11 gap map and the green baseline

**Files:**
- Create: `reports/fp11-gap-map.txt` (the §1 map as the before-snapshot).
- Evidence only otherwise.

**Context:** The §1 gap table is the acceptance oracle: the plan is complete only when every A row is covered (CPU/reference + native evidence) and every B row is re-documented with a cost/value justification while retaining its stable typed code. Freeze the baseline before any production change.

- [ ] **Step 1: Re-run the refusal-code oracle and diff against §1**

```bash
rg -n "hairline_exact_lowering|round_cap_exact_lowering|core_primitive_mixed_uniform_layouts|core_primitive_analytic_clip_non_direct_geometry|dst-read-formula|multi-render-dst-copy|analytic-shape-multi-key|path-destination-read|native-mask-blur.clip|core_primitive_clip_producer_authority" kanvas/src/main gpu-renderer/src/main --type kotlin > reports/fp11-gap-map.txt
```

Expected: matches §1 (record any line drift — the diff is evidence). Commit the map with the plan.

- [ ] **Step 2: Freeze the green baseline**

```bash
./gradlew -F off :kanvas:test --tests "*GPUFramePathApiInventoryTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kMaskBlurFramePayloadMaterializerTest" --tests "*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" --tests "*GPUFramePreflighterTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: the first three BUILD SUCCESSFUL; the fourth FAILS ONLY on `gpu renderer production source satisfies package boundary rules` (pre-existing, exactly 20 cycle violations — do not modify).

- [ ] **Step 3: Commit**

```bash
git add reports/fp11-close-bounded-native-rendering-gaps-plan.md reports/fp11-gap-map.txt
git commit -m "docs(surface): fp11 gap inventory and green baseline evidence"
```

---

## Phase 1 — Retained-target ordering (gap 8, FP-10 transfer)

### Task 2: Mask-blur leading-composite clear predicate on retained sessions

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUTopLevelMaskBlurFrameRecording.kt:170-224`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurSurfaceTest.kt`

**Context:** At HEAD the single boolean `firstCompositeClears = sceneRenders.isEmpty()` (l.170) is applied only to `chainIndex == 0` (l.222). On a retained session a mixed frame whose first paint op is a mask blur sorts its chain composite before the first scene clear render, so the composite uses `loadOp="load"` over the retained previous-frame target. The correct condition is per chain: clear iff NO scene clear render is ordered before that chain's composite.

- [ ] **Step 1: Write the failing test (red)**

Add to `GPUMaskBlurSurfaceTest.kt` (GPU-environment-dependent; uses the existing `requireWebGpu()`, `blurPaint`, and the retained session — both `Surface(32,32)` renders share the FP-10 executor session key `(generation, 32, 32, rgba8unorm-srgb, …)`):

```kotlin
@Test
fun `leading blur composite on a mixed retained frame clears instead of sampling the previous frame`() {
    requireWebGpu()
    // Frame 1 fills the retained session target with blue.
    Surface(width = 32, height = 32).run {
        canvas { drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.BLUE)) }
        render()
    }
    // Frame 2 is the leading-blur-mixed shape: the FIRST paint op is a mask blur, a later
    // scene render draws only a small red rect. The blur composite sorts before the red
    // rect and must clear the scene target itself (no clear scene render is ordered before
    // it). Outside the blur region and the red rect the target must be transparent, never
    // the retained blue.
    val pixels = Surface(width = 32, height = 32).run {
        canvas {
            drawRect(Rect(4f, 4f, 12f, 12f), blurPaint(BlurStyle.NORMAL, 2f))
            drawRect(Rect(20f, 20f, 30f, 30f), Paint.fill(Color.RED))
        }
        render().pixels.toUByteArray()
    }
    assertEquals(0, pixels[(2 * 32 + 2) * 4 + 3].toInt(), "cleared region outside the blur must be transparent")
    assertEquals(0, pixels[(2 * 32 + 2) * 4 + 2].toInt(), "cleared region must carry no retained blue")
    assertEquals(255, pixels[(25 * 32 + 25) * 4 + 0].toInt(), "the later scene render must draw its red rect")
}
```

(Read the retained-target evidence path at `GPUPreparedSurfaceFrameExecutor` before finalizing — if two `Surface(32,32)` renders in one test do not share the executor session (the executor is a process-wide singleton keyed by `(deviceGeneration, width, height, colorFormat, interpretation)`, so they DO — verified in FP-10 Task 4), assert the reuse directly via `GPUPreparedSurfaceLifetimeStressTest`-style evidence counters before the pixel probe.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew -F off :kanvas:test --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks`
Expected: FAIL on the two "cleared region" assertions — at HEAD the leading composite loads the retained blue (`firstCompositeClears == false` because `sceneRenders` is non-empty), so the outside-blur pixels are blue, not transparent.

- [ ] **Step 3: Implement the per-chain clear predicate**

In `GPUTopLevelMaskBlurFrameRecording.kt`, replace `firstCompositeClears` (l.170) and its use at l.222:

```kotlin
// A frame whose first paint op is a mask blur has no clear scene render ordered BEFORE
// the leading composite: on a retained session (FP-10) the composite must clear the scene
// target itself. The condition is per chain — "no scene clear render before THIS
// composite" — not "no scene renders at all".
val sceneRenderPaintOrders = sceneRenders.map { render ->
    render.drawPackets.minOf { it.originalPaintOrder }
}
```

and inside the `for ((chainIndex, packet) in blurPackets.withIndex())` loop replace `compositeLoadOp = if (firstCompositeClears && chainIndex == 0) "clear" else "load"` with:

```kotlin
val compositeClears = sceneRenderPaintOrders.none { it < packet.originalPaintOrder }
compositeLoadOp = if (compositeClears) "clear" else "load",
```

(`chainIndex` remains used elsewhere in the loop; the composite render's `loadStore` at `buildBlurChain` `:758` already honors `compositeLoadOp`.)

- [ ] **Step 4: Run to verify green + regression**

```bash
./gradlew -F off :kanvas:test --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks
./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kMaskBlurFramePayloadMaterializerTest" --no-parallel --console=plain
```

Expected: the new test passes (cleared region transparent, red rect present); the materializer suite stays green (its `composite-load` scenario pins `invalid.native-mask-blur.composite-load` for a forged `"retained"` loadOp, and the legal `"clear"`/`"load"` values are unchanged).

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main kanvas/src/test
git commit -m "fix(surface): mask blur leading composite clears on retained session targets"
```

---

## Phase 2 — Hairline point lowering (gap 1)

### Task 3: Exact hairline point lowering on the prepared core lane

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt:554-572`
- Modify (verify-then-wire): `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt` (the `GPUStrokeDescriptor` hairline/width refusals at `:1794-1808`)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt:723-737`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt` (DrawPoint/DrawPoints rows)

**Context:** 175 cases refuse at `pathDeviceGeometry` with `strokeWidth == 0f` (hairline points). Non-hairline butt/square points already lower via `strokeDeviceGeometry` as zero-length single-segment strokes. Exact hairline lowering needs a canonical 1-device-px point square in device space (a `drawPoint` at `(x, y)` → a `(x±0.5, y±0.5)` square), admitted through the stroke geometry authority (which today refuses `hairline`/`width <= 0f`). The CPU/reference oracle is a 1-px device square centered on the point; the GPU evidence is the blend suite's DrawPoint/DrawPoints rows flipping from `Terminal` to `Prepared` against the existing `assertPixelsNear` CPU oracle.

- [ ] **Step 1: Write the failing tests (red)**

Flip `GPUFramePathApiInventoryTest.kt:723-737` (`drawPoint hairline refuses with a stable geometry diagnostic`) to assert the geometry (re-read `inventoryFor`/`gatheredSemantic`/`semanticFor` helpers at `:2291-2337` before editing):

```kotlin
@Test
fun `drawPoint hairline lowers to a one device pixel square geometry`() {
    val semantic = semanticFor(DisplayOp.DrawPoint(
        10f,
        12f,
        Paint.fill(Color.RED).copy(strokeWidth = 0f, strokeCap = StrokeCap.SQUARE),
        Matrix33.identity(),
        org.graphiks.kanvas.canvas.ClipStack.WideOpen,
    ))
    val geometry = assertIs<GPUCorePrimitiveGeometryInput.TriangulatedPath>(semantic.geometry)
    // The hairline point is one device pixel: cover bounds span 1 px in each axis.
    assertEquals(1, geometry.coverBounds.right - geometry.coverBounds.left, "hairline point spans one device pixel in x")
    assertEquals(1, geometry.coverBounds.bottom - geometry.coverBounds.top, "hairline point spans one device pixel in y")
}
```

In `GPUAllApiBlendSurfaceTest.kt`, re-point the DrawPoint/DrawPoints rows of `expectedPreparedProductRoute` to `ProductRouteExpectation.Prepared` (the existing CPU oracle `assertPixelsNear(cpu, gpu, tolerance = 2)` at `:144` is the reference proof). Keep the round-cap point refusal pinned (`round_cap_exact_lowering`, `GPUFramePathApiInventoryTest.kt:751`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUFramePathApiInventoryTest" --no-parallel --console=plain`
And: `./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain`
Expected: FAIL — the inventory case still throws `GPUCorePrimitiveGeometryRefusalException` with `hairline_exact_lowering`; the blend rows still refuse.

- [ ] **Step 3: Implement the hairline point lowering**

In `GPUCorePrimitiveSemanticBuilder.kt:554-572`, remove the `strokeWidth == 0f` branch from the point refusal `when` (keep dash/round-cap/invalid-width). In `pathDeviceGeometry`/`strokeDeviceGeometry`, before the single-segment stroke lowering, detect the hairline point and lower it as a device-space 1-px square: produce a `TriangulatedPath` with vertices `(x-0.5, y-0.5, x+0.5, y-0.5, x+0.5, y+0.5, x-0.5, y+0.5)`, indices `[0,1,2, 0,2,3]`, `geometryMode = DirectTriangles`, `coverBounds` = the single pixel, and a `GPUCorePrimitiveStrokeStyle`/proof that records the exact hairline lowering (verify the `GPUCorePrimitiveStrokeLoweringProof` enum at `PayloadContracts.kt:751-764` and the stroke authority at `:1908-1909` — add a `HairlinePointV1` proof if the authority requires one). Then admit the canonical hairline in the geometry stroke authority: `GPUStrokeDescriptor.refusalCode` (`GeometryContracts.kt:1805-1824`) and `strokeAndFillRefusalCode` (`:1794-1795`) must accept the hairline point's canonical descriptor instead of emitting `unsupported.stroke.hairline_policy`/`unsupported.stroke.width_invalid` — scope the admission to the canonical 1-device-px point square only, never to arbitrary `width <= 0f` strokes (those stay terminal). Note the exact `file:line` of every authority touched in the commit message.

- [ ] **Step 4: Run to verify green + GPU pixel regression**

```bash
./gradlew -F off :kanvas:test --tests "*GPUFramePathApiInventoryTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks
```

Expected: the inventory geometry case passes; in the WebGPU environment the DrawPoint/DrawPoints rows render `Prepared` and match the CPU oracle (in a non-GPU environment they skip). Record the observed route split per mode/context.

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main gpu-renderer/src/main kanvas/src/test
git commit -m "feat(surface): exact hairline point lowering on the prepared core lane"
```

---

## Phase 3 — Destination-read multi-render and path stencil (gaps 5 and 7)

### Task 4: Prepared multi-render destination-copy direct lane (gap 5)

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt:3363-3389`
- Modify (verify-then-wire): `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (the dst-copy core lane dispatcher referenced at `:5202`)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt:1086-1091` (remove `multi-render-dst-copy` from the residual set)
- Modify: `GPUAllApiBlendSurfaceTest.kt` (DrawRect/DrawColor UNCLIPPED/SCISSOR artistic+PLUS rows), `GPUPathClipRegressionTest.kt:91-115`, `GPUPreparedSurfaceFrameExecutorTest.kt:793-804`, `GPUFramePreflighterTest.kt`

**Context:** The prepared direct lane refuses any non-mixed-boundary frame with more than one core render (`GPUFramePreflighter.kt:3363-3389`), so destination-then-consumer dst-read frames (60 cases) die at `multi-render-dst-copy`. The recording ALREADY emits the producer render → `CopyDestinationStep` → consumer render shape (the preflight comment at `:3370-3374` and the `copySteps`/`consumers` inspection at `:3375-3387` prove the shape is built); only the preflight admission and the executor materialization of the 2-render direct shape are missing. The Graphite recipe is documented in FP-09 Task 3c (per-pass `BindGraphicsPipeline`, copy ordered before the consuming pass).

- [ ] **Step 1: Write the failing tests (red)**

Flip `GPUPathClipRegressionTest.kt:91-115` (`darken rect over destination refuses with the multi render dst copy code`) to assert `Prepared` + pixels against the CPU reference. The concrete reference is exact for DARKEN-over-opaque-white (per-channel min via `GPUBlendOracle.blend(mode, source, destination, coverage = 1f)` at `gpu-renderer/.../materials/GPUBlendOracle.kt:31-40`); `Surface.render()` returns `RenderResult(pixels: UByteArray, diagnostics: Diagnostics, …)`:

```kotlin
@Test
fun `darken rect over destination renders prepared via the multi render dst copy lane`() {
    requireWebGpu()
    val result = Surface(width = 32, height = 32).run {
        canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawRect(Rect(8f, 8f, 24f, 24f), Paint.fill(Color.BLACK).copy(blendMode = BlendMode.DARKEN))
        }
        render()
    }
    val pixels = result.pixels
    // CPU reference: DARKEN over an opaque white destination = per-channel min; the black
    // source yields opaque black inside the rect and retained white outside.
    assertEquals(255, pixels[(12 * 32 + 12) * 4 + 3].toInt(), "in-rect pixel is opaque")
    assertEquals(0, pixels[(12 * 32 + 12) * 4 + 0].toInt(), "in-rect pixel is DARKEN(black, white) = black")
    assertEquals(255, pixels[(2 * 32 + 2) * 4 + 0].toInt(), "outside the rect the white destination is retained")
    assertTrue(
        result.diagnostics.entries.any { entry ->
            entry.code.startsWith("route:destination-read:DrawRect:") && entry.reason == "gpu-copy-then-formula"
        },
        "the dst-read multi-render frame must emit the copy-then-formula route evidence",
    )
}
```

Add a preflight case in `GPUFramePreflighterTest.kt`: a 2-render dst-read frame (producer render → copy → consumer render) classifies `Accepted` (today it classifies `unsupported.native-core-primitive.multi-render-dst-copy`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPathClipRegressionTest" --no-parallel --console=plain --rerun-tasks`
And: `./gradlew -F off :gpu-renderer:test --tests "*GPUFramePreflighterTest" --no-parallel --console=plain`
Expected: FAIL — the path-clip frame still refuses with `multi-render-dst-copy`; the preflight case still refuses.

- [ ] **Step 3: Admit the 2-render dst-read direct shape**

In `GPUFramePreflighter.kt`, replace the `multi-render-dst-copy` diagnostic at `:3383-3387` with admission: when `copySteps` contains the ordered `CopyDestinationStep` whose consumer resolves to a core render packet, and exactly two core renders exist (producer, consumer) on the same target, treat the frame as a valid direct dst-read shape and continue the authority checks for both renders (per-key seals — the multi-key direct pass machinery from FP-09 Task 3b already validates per-key pipelines/bind groups). Verify-then-wire the executor side at `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (the `:5202` comment documents the preflight residual; the dispatcher must route the 2-render + copy shape to the core dst-copy lane exactly as `GPUWgpu4kDestinationCopyFrameSmokeTest`'s multi-scope smoke does). Remove `multi-render-dst-copy` from `preparedRouteResidualRefusalCodes` (`GPUPreparedSurfaceFrameExecution.kt:1086-1091`).

- [ ] **Step 4: Run to verify green + GPU pixel regression**

```bash
./gradlew -F off :gpu-renderer:test --tests "*GPUFramePreflighterTest" --tests "*GPUPreparedSurfaceNativePreflightTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUPathClipRegressionTest" --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUPreparedSurfaceFrameExecutorTest" --no-parallel --console=plain --rerun-tasks
```

Expected: preflight + native-preflight suites green; the path-clip and blend suites green in a WebGPU environment with the artistic+PLUS two-draw rows on the `Prepared` route matching the CPU oracle. In a non-GPU environment the pixel rows skip; the preflight and executor unit cases still green.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main kanvas/src/main gpu-renderer/src/test kanvas/src/test
git commit -m "feat(surface): prepared multi render destination copy direct lane"
```

---

### Task 5: Path destination-read frames on the prepared lane (gap 7)

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2551-2567` (remove the refusal; resolve the dst-read consumer ref at the path cover packet)
- Modify (verify-then-wire): `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/destination/GPUDestinationSnapshotGrouping.kt` and the destination snapshot planning (`buildCorePrimitiveDestinationSnapshotPlans`) — key the `TextureCopy` consumer ref by the assembled path cover packet id (`<base>.path-stencil-cover`) instead of the base packet id
- Modify: `GPUAllApiBlendSurfaceTest.kt` (DrawPath/DrawDRRect UNCLIPPED/SCISSOR artistic+PLUS rows)
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerDestinationContractTest.kt`

**Context:** The 60 path/DrawDRRect dst-read cases refuse by name at `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2564` (FP-09 evidence §4): the snapshot planner records the `TextureCopy` consumer ref at the base packet id, but path-stencil lowering produces fresh producer/cover packet ids, so the cover can never be resolved; and the dst-read formula forces the cover into its own render pass, which the path-stencil authority rejects. Task 4's 2-render dst-read shape is the prerequisite.

- [ ] **Step 1: Write the failing tests (red)**

Flip `GPUAllApiBlendSurfaceTest.kt`'s DrawPath/DrawDRRect rows to `ProductRouteExpectation.Prepared` (CPU oracle at `:144`). Add a destination-contract case in `GPUFramePlannerDestinationContractTest.kt`: a `DrawRect(destination)` + `DrawPath(DARKEN)` frame must plan `Ready` with a `DestinationSnapshots` task whose `TextureCopy` consumer ref resolves to the path cover packet id (today the recording refuses with `path-destination-read` before the assembler, and the contract can only express the refusal).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :gpu-renderer:test --tests "*GPUFramePlannerDestinationContractTest" --no-parallel --console=plain`
And: `./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain`
Expected: FAIL — the path dst-read shape still refuses.

- [ ] **Step 3: Resolve the path-cover dst-read consumer ref**

Remove the `destinationReadPlans.firstOrNull { … StencilEdgeFan … }` refusal block at `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2557-2567`. In the destination snapshot planning, resolve the `TextureCopy` consumer ref against the ASSEMBLED cover packet ids for path-stencil sources (the cover packet `<base>.path-stencil-cover` produced by the path assembler; find the packet-id transformation in the assembler path at `:3341`-area `renderByPacketId.getValue(...)`). Verify-then-wire the path-stencil authority to admit a dst-read cover render pass (the 2-render shape from Task 4: the dst-read cover runs in its own pass after the snapshot copy). Note the exact `file:line` of the consumer-ref mapping and the authority change in the commit message.

- [ ] **Step 4: Run to verify green + GPU pixel regression**

```bash
./gradlew -F off :gpu-renderer:test --tests "*GPUFramePlannerDestinationContractTest" --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks
```

Expected: the destination contract and task-list suites green; in a WebGPU environment the DrawPath/DrawDRRect artistic+PLUS rows render `Prepared` with `route:destination-read:DrawPath:*/DrawDRRect:*` evidence and match the CPU oracle. In a non-GPU environment they skip.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main kanvas/src/test gpu-renderer/src/test
git commit -m "feat(surface): destination read path stencil frames on the prepared lane"
```

---

## Phase 4 — Multi-uniform-layout direct passes (gap 2)

### Task 6: Split direct CorePrimitive passes by uniform layout

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1611-1621, 2111-2124`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt:3306-3314`
- Modify (verify-then-wire): `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` (per-layout slabs per split pass) and the direct-pass seal (`GPUCorePrimitivePreparedAuthority.kt` / the multi-key seal type)
- Modify: `GPUAllApiBlendSurfaceTest.kt` (DrawRRect rows + DrawRect/DrawColor ALPHA_MASK rows), `GPUPathClipRegressionTest.kt:23-89, 117-146`, `GPUClipAdvancedBlendSurfaceTest.kt:28-100`, `GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt:1190, 1407, 1431, 1464, 2445`, `GPUPreparedSurfaceFrameBuilderTest.kt:669`, `GPUClipCoverageSurfaceTest.kt:59`, `GPUPreparedSurfaceProductRouterTest.kt:466`

**Context:** 202 cases refuse because one direct pass carries ONE shared uniform slab. The FP-09 Task 3b multi-key machinery already materializes N structural pipeline keys per pass with one slab (`GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout` distinguishes `DynamicUniform32V2`/`AnalyticClipUniform64V1`/`AnalyticClipUniform160V1`/`AnalyticShapeUniform80V1`/`NoBindingsV1`/coverage-mask variants). This task splits the direct pass by uniform layout: the recording emits one render per layout group (each with its own slab), the preflighter accepts N direct renders when each owns one layout, and the materializer seals each split pass with its own slab. The `core_primitive_mixed_uniform_layouts` refusals at `:1617` (shape+clip mix) and `:2120` (`activeDirectUniformLayouts > 1`) and the preflight twin at `:3311` are removed.

> **Verify-gate:** If the split breaks the sealed multi-key direct pass authority (`validateMultiKeyDirectPassSealAuthority`, the per-pass seal contract) in a way that cannot be repaired within the pass-seal design, RECLASSIFY the residual to B in Task 8 with the exact failing seal evidence — the refusal stays pinned and the B-table gains the justification. Do not hide the failure.

- [ ] **Step 1: Write the failing tests (red)**

Flip `GPUPathClipRegressionTest.kt:23-56` (`device rect clip path frame refuses with the mixed uniform layouts code`) to assert `Ready` + pixels (the clip rect clips the path; CPU reference = the blended clip outcome). Flip `GPUClipAdvancedBlendSurfaceTest.kt:28-100` (AA-clip dst-read + scissor dst-read) similarly. In `GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt`, add a case: a frame with an unclipped `uniform32` rect AND an analytic-clip `uniform64` rect plans `Ready` with TWO direct render tasks (one per layout), each with its own uniform slab (today it plans `Refused`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPathClipRegressionTest" --tests "*GPUClipAdvancedBlendSurfaceTest" --no-parallel --console=plain`
And: `./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --no-parallel --console=plain`
Expected: FAIL — the frames still refuse with `mixed_uniform_layouts`; the task-list case still plans `Refused`.

- [ ] **Step 3: Implement the layout-based pass split**

In `GPUCorePrimitivePreparedFrameTaskListBuilder.kt`, replace the two refusal sites with layout grouping: group `baseRenders`' packets by `UniformLayout` (including the path-layout group), emit one direct render task per layout group, and build one `GPUUniformSlabPlan` per group (reuse `GPUUniformSlabPlanner.plan` per group instead of once for all `legacyUniformPackets`). In `GPUFramePreflighter.kt:3306-3314`, replace the `directUniformLayouts.size > 1` refusal with an authority that accepts N direct renders when each render's packets share exactly one layout and the per-render seals validate (the per-render slab authority + `framePlan.steps` ordering). Verify-then-wire the materializer per-layout slab sealing and the per-pass seal generalization (FP-09 Task 3b's `GPUCorePrimitiveMultiKeyDirectPreparedPassSeal` at `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1400-1454` is the seam). Remove the covered codes from the residual/refusal surface; keep `unsupported.recording.core_primitive_analytic_shape_clip` (the 7-case analytic-shape-under-analytic-clip designed refusal) pinned and note it in Task 8 if it remains outside this split.

- [ ] **Step 4: Run to verify green + GPU pixel regression**

```bash
./gradlew -F off :gpu-renderer:test --tests "*GPUCorePrimitivePreparedFrameTaskListBuilderTest" --tests "*GPUFramePreflighterTest" --tests "*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUPathClipRegressionTest" --tests "*GPUClipAdvancedBlendSurfaceTest" --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUPreparedSurfaceFrameBuilderTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain --rerun-tasks
```

Expected: the gpu-renderer suites green; in a WebGPU environment the DrawRRect and ALPHA_MASK rows render `Prepared` and match the CPU oracle, and the clip/blend/path regression rows flip to `Prepared` with pixel evidence. If the verify-gate fires (seal break), stop, record the failing seal evidence in the Task 8 B-table, keep the refusals pinned, and continue with Task 7.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main kanvas/src/test gpu-renderer/src/test
git commit -m "feat(surface): split direct core primitive passes by uniform layout"
```

---

## Phase 5 — Mask-blur composite analytic clips

### Task 7: Analytic device-rect clips on the top-level mask blur composite

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUTopLevelMaskBlurFrameRecording.kt:1009-1020` (clip admission) and the composite WGSL `fs_main` at `:989-1006` (clip-coverage term)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kMaskBlurFramePayloadMaterializer.kt:435-446` (bind the analytic-clip coverage to the composite)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaskBlurSurfaceTest.kt:221-243`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kMaskBlurFramePayloadMaterializerTest.kt:546, 577-595`

**Context:** The blur composite refuses every clip beyond `NoClip`/`ScissorOnly` (`topLevelMaskBlurCompositeClipRefusal`). The `TopLevelMaskBlurPixelOracle.RectClip(…, antiAlias = true)` reference exists and the materializer already validates the composite scissor. Coverage: admit an analytic device-rect clip on the composite and fold its coverage into the composite shader (multiply the blurred mask coverage by the analytic clip coverage). Stencil/coverage-mask clips over the composite stay terminal (`native-mask-blur.clip`), and complex-clip blur stays at `core_primitive_clip_producer_authority`.

- [ ] **Step 1: Write the failing tests (red)**

Flip `GPUMaskBlurSurfaceTest.kt:221-243` (`mask blur composites under coverage and analytic clips are terminal`): the AA-rect case becomes a `Prepared` pixel assertion (keep the stacked non-AA case terminal — it plans a coverage-mask clip):

```kotlin
@Test
fun `mask blur composite under an analytic rect clip renders prepared`() {
    requireWebGpu()
    val pixels = renderSourceCompositedBlur(RenderConfig.DEFAULT) {
        clipRect(Rect(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = true)
    }
    val expected = TopLevelMaskBlurPixelOracle.render(
        32, 32, rectShape(0f, 0f, 32f, 32f), fullTarget(), BlurStyle.NORMAL, 2f,
        Color.BLACK, BlendMode.SRC_OVER, transparent(),
        clip = TopLevelMaskBlurPixelOracle.RectClip(14f, 14f, 18f, 18f, antiAlias = true),
    )
    TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
}
```

Re-point `GPUWgpu4kMaskBlurFramePayloadMaterializerTest.kt`'s `Scenario("clip", …)` (l.546) to a stencil/complex-clip plan (still `unsupported.native-mask-blur.clip`) and the `composite clip refusal predicate pins the lane scope boundary` (l.577-595) to the new boundary (NoClip/ScissorOnly/analytic-rect admitted; stencil/coverage-mask refused).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks`
And: `./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kMaskBlurFramePayloadMaterializerTest" --no-parallel --console=plain`
Expected: FAIL — the AA-rect composite still refuses with `unsupported.native-mask-blur.clip`.

- [ ] **Step 3: Admit the analytic clip on the blur composite**

In `topLevelMaskBlurCompositeClipRefusal` (`GPUTopLevelMaskBlurFrameRecording.kt:1009-1020`), admit `GPUClipExecutionPlan.AnalyticCoverage` when its geometry is a device rect (mirror the `RectClip(antiAlias = true)` reference); keep `StencilCoverage`/`CoverageMask`/complex-clip plans refused. Extend the composite `fs_main` (`:989-1006`) with a clip-coverage term computed from the analytic-clip uniform (the `uniform64` analytic-clip block already exists on the core lane; the composite bind group must bind it per the component identity at `GPUWgpu4kMaskBlurFramePayloadMaterializer.kt:435-446`). Multiply `coverage` by the clip coverage before the blend. Verify the composite pipeline/layout authority (the dst-read composite layout `TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST` vs a new clip variant) at HEAD and note the exact `file:line` in the commit message.

- [ ] **Step 4: Run to verify green + GPU pixel regression**

```bash
./gradlew -F off :kanvas:test --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks
./gradlew -F off :gpu-renderer:test --tests "*GPUWgpu4kMaskBlurFramePayloadMaterializerTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

Expected: the AA-rect clip blur renders `Prepared` and matches the oracle; the materializer suite green with the re-pointed clip scenarios; the clip coverage suite green (in a non-GPU environment the pixel rows skip).

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main kanvas/src/test gpu-renderer/src/test
git commit -m "feat(surface): analytic rect clips on the top level mask blur composite"
```

---

## Phase 6 — B-family re-documentation

### Task 8: Re-document the justified terminal refusals with pinned evidence

**Files:**
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-11-close-bounded-native-rendering-gaps-evidence.md` (B-table skeleton; finalized in Task 9)
- Verify-only: `GPUPreparedSurfaceProductRouterTest.kt:465-470` (B rows keep `Terminal`), `GPUClipCoverageSurfaceTest.kt:63, 65, 67` (B pins keep), `GPUPreparedSurfaceFrameExecution.kt:1086-1091` (B residual codes keep)

**Context:** Every gap must be either covered or re-documented with a cost/value justification while retaining its stable typed refusal (FP-11 acceptance: "unsupported cases retain stable typed refusals"). The B family: #3 (analytic clips over non-direct shading geometry, 2), #4 (dst-read formula on mapped routes, 2), #6 (analytic-shape multi-key dst-read, 2), plus the complex-clip blur residual (`invalid.preflight.core_primitive_clip_producer_authority`) and, if the Task 6 verify-gate fires, the mixed-layout residual. This task writes the B-table into the evidence skeleton and confirms every B code remains pinned and terminal.

- [ ] **Step 1: Write the B-table skeleton**

In `fp-11-close-bounded-native-rendering-gaps-evidence.md`, add the B-table: code, case count, emission site (HEAD), root-cause level, refusal level, and the cost/value justification:

| code | cases | justification (cost ≫ value) |
| --- | --- | --- |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (+ intersection twin) | 2 | analytic clip over stencil-shaded geometry is a new execution feature; 2 matrix cases |
| `unsupported.native-core-primitive.dst-read-formula` | 2 | scalar-coverage dst-read formula programs on the analytic-shape lane need a separate AA oracle; the covered full-coverage formulas (DARKEN/SCREEN) exist |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 2 | AA multi-key analytic-shape blend semantics (e.g. CLEAR) unverifiable by the coverage-modulating shader |
| `invalid.preflight.core_primitive_clip_producer_authority` (complex-clip blur) | bounded | general clip-producer authority, not blur-specific; coverage requires a new clip-execution shape |
| (verify-gate) `unsupported.recording.core_primitive_mixed_uniform_layouts` | — | ONLY if Task 6 Step 4 records a seal break — append the failing seal evidence here |

- [ ] **Step 2: Confirm the B pins are terminal**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain`
Expected: BUILD SUCCESSFUL — the B rows still assert `Terminal(code)` and no destination readback is allocated before refusal.

- [ ] **Step 3: Commit**

```bash
git add reports/upstream-rebaseline/graphite-dawn-frame-plan
git commit -m "docs(surface): fp11 justified terminal refusal re documentation"
```

---

## Phase 7 — Regression proof & closure

### Task 9: Full regression, guards, evidence report, roadmap FP-11 completed + FP-12 transfers

**Files:**
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-11-close-bounded-native-rendering-gaps-evidence.md` (finalize)
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` (FP-11 → `completed`; add FP-12 transfers)

**Context:** FP-11 acceptance: every accepted expansion has CPU/reference AND native GPU evidence (Tasks 2-7); unsupported cases retain stable typed refusals (Task 8); no hidden fallback or Graphite/Ganesh/SkSL path is introduced (no new production line touches the legacy route; the residual set only shrinks). The retained-session contract and the two baseline failures are preserved.

- [ ] **Step 1: Run the full regression**

```bash
./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain 2>&1 | tee /tmp/fp11_full.log
```

Expected: BUILD SUCCESSFUL except the two documented pre-existing failures — `GPURendererPackageBoundaryTest` package-boundary case (exactly 20 cycle violations, 0 rule violations; unchanged) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (reproduces at base SHA). The `failed.surface.prepared.session-close` flake may land on a random frame under churn (FP-09 §17, environment-dependent) — classify any such failure with evidence (run the affected class in isolation), never weaken an assertion.

- [ ] **Step 2: Verify the guards and the covered families end-to-end**

```bash
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLegacyAbsenceTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --tests "*GPUClipCoverageSurfaceTest" --tests "*GPUClipAdvancedBlendSurfaceTest" --tests "*GPUPathClipRegressionTest" --tests "*GPUMaskBlurSurfaceTest" --no-parallel --console=plain --rerun-tasks
```

Expected: the boundary case in its unchanged pre-existing failure state; the absence guard, router matrix, and composite guards green; the GPU suites green on a WebGPU host (in a non-GPU environment the pixel rows skip). `GPUPreparedSurfaceLifetimeStressTest` (FP-10) stays green — no per-frame close introduced.

- [ ] **Step 3: Write the evidence report**

In `fp-11-close-bounded-native-rendering-gaps-evidence.md`: the before/after gap table (the §1 map vs. the post-Tasks-2-7 route split: which codes flipped to `Prepared`, which stayed `Terminal`); per-task proof (red-run code capture + green-run counters for Tasks 2-7); the B-table (Task 8); the route-split evidence per suite (the `GPUAllApiBlendSurfaceTest` re-pointed matrix, the `GPUPathClipRegressionTest`/`GPUClipAdvancedBlendSurfaceTest` flipped cases, the `GPUMaskBlurSurfaceTest` leading-blur and analytic-clip cases, the destination-contract additions); the retained-session note (all new tests ride the checkin; the FP-10 crash class not re-exposed); the flake note (session-close observed/not observed); test score deltas (before/after of the full run); and the FP-12 transfer list.

- [ ] **Step 4: Update the roadmap**

In `active-todo.md`, mark FP-11 `completed` with the evidence report reference and add the FP-12 transfer list (the remaining B residuals — #3/#4/#6, complex-clip blur, and any Task 6 reclassified residual — are tracked as bounded future work):

```markdown
### FP-11 — Close bounded native-rendering gaps

Status: `completed`

Resolution evidence (`fp-11-close-bounded-native-rendering-gaps-evidence.md`):
- covered with CPU/reference + native GPU evidence: mask-blur leading-composite
  retained-target ordering (per-chain composite clear on retained sessions), exact
  hairline point lowering (175), multi-render dst-copy direct lane (60), path
  destination-read (60), multi-uniform-layout direct passes (202), and analytic
  rect clips on the top-level mask blur composite;
- justified stable terminal refusals re-documented: analytic clips over non-direct
  geometry (2), dst-read formula on mapped routes (2), analytic-shape multi-key
  dst-read (2), complex-clip blur (`core_primitive_clip_producer_authority`);
- full run green except the two documented pre-existing failures (package boundary,
  stencil smoke); the `failed.surface.prepared.session-close` flake remains
  documented environmental; the FP-10 retained-session contract is preserved.

FP-12+ transfers (residual-refusal tracking note — bounded future work, not a new roadmap entry;
the existing FP-12 entry remains "Current visual and performance evidence"):
- analytic clips over non-direct shading geometry (2);
- dst-read formula on mapped routes (2);
- analytic-shape multi-key dst-read (2);
- complex-clip blur / `core_primitive_clip_producer_authority`.
```

- [ ] **Step 5: Final state check**

```bash
git add reports/ kanvas/src/main kanvas/src/test gpu-renderer/src/main gpu-renderer/src/test
git log --oneline f14656988..HEAD | cat
rg -n "hairline_exact_lowering|core_primitive_mixed_uniform_layouts|multi-render-dst-copy|path-destination-read|native-mask-blur.clip" kanvas/src/main gpu-renderer/src/main --type kotlin
git status --short
```

Expected: the log shows the FP-11 task commits (inventory → retained-ordering → hairline → multi-render → path-dst-read → multi-layout → mask-blur clips → B re-doc → evidence closure); the `rg` shows ONLY the B-code sites that remain (Task 8 table) and no covered code in production; `git status --short` shows only the intended files.

- [ ] **Step 6: Commit**

```bash
git add reports/ kanvas/src/test kanvas/src/main gpu-renderer/src/main gpu-renderer/src/test
git commit -m "docs(surface): fp11 bounded native gap closure evidence and roadmap"
```

---

## Self-review notes (filled at plan time, 2026-08-12)

**Spec coverage vs. the roadmap FP-11 entry and the mission:**

1. **The basket is classified, not migrated.** FP-11 is 8 independent gaps at four refusal levels; the plan classifies each by root-cause level (semantic `GPUCorePrimitiveSemanticBuilder.kt:557`; recording `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617/2009/2120/2564` + `GPUTopLevelMaskBlurFrameRecording.kt:170/1019`; preflight `GPUFramePreflighter.kt:3311/3384`; execution `GPUCorePrimitiveNativeRoute.kt:415` + `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:893/1429`) and routes each task at the right level. No task targets the wrong layer.
2. **Roadmap line drift captured at HEAD (self-review finding #1):** the roadmap/FP-09 record says hairline `409/411/465`, mixed-layout `1602/2104/3307`, analytic-clip `1994`; at HEAD `f14656988` these are `557/559/613`, `1617/2120/3311`, `2009`. All §1 sites were re-verified directly (Read) and the plan quotes the HEAD lines; the mission's "la ligne roadmap 409/411/465 a bougé au HEAD, vérifier l'état exact" is answered in §1.
3. **A/B split decision (mission question "gaps qui méritent une vraie couverture vs refus terminaux documentés"):** A = #8 (bug, visible), #1 (175), #5 (60, prerequisite), #7 (60), #2 (202, verify-gated), mask-blur analytic-clip composites. B = #3 (2), #4 (2), #6 (2), complex-clip blur (`core_primitive_clip_producer_authority`). Justification per B row is cost-vs-value (each is 2 cases or a general clip authority), not a hidden fallback — the codes stay terminal and pinned.
4. **Dependency check (mission "Vérifier les dépendances"):** #7 depends on #5 (the dst-read cover must run in its own pass — Task 4 lands before Task 5); #2 is gated on the FP-09 multi-key pass seal (Task 6 verify-gate with an explicit B-reclassification path, never a hidden failure); #8 depends only on the landed FP-10 session semantics. The retained-session constraint (checkin, no close-per-frame) is an explicit hard rule stated in the Context and honored by every new test (all ride the process-wide executor).
5. **Pinned-test re-pointing is per-gap with evidence, not silent:** the re-point inventory (§3) lists every suite that pins a covered code and when it flips; the B codes stay pinned. `GPUFramePathApiInventoryTest:751` (round-cap points) and `GPUClipCoverageSurfaceTest:63/65/67` (B codes) are explicitly kept.
6. **Baselines preserved (self-review finding #2):** `GPURendererPackageBoundaryTest` (20 cycles, 0 violations), `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`, `GPUPreparedSurfaceLegacyAbsenceTest` (16 tokens), the composite guards, and the `session-close` flake documentation are untouched; the plan states "do not fix, do not change failure state" verbatim per FP-09/FP-10 precedent.
7. **Honest discovery points (no placeholders, FP-09/FP-10 verify-then-wire style):** Task 3's exact hairline square geometry representation and the stroke-authority admission site; Task 4's executor dst-copy dispatcher seam (`:5202` comment); Task 5's path-cover consumer-ref mapping (the `:3341`-area assembler); Task 6's per-layout slab seal; Task 7's composite clip uniform/bind-group layout. Each is pinned by the concrete red test in the task; a residual discovered at execution time is documented in the Task 8 B-table or the Task 9 evidence run, never hidden.
8. **Commands verified (self-review finding #3):** every gradle invocation in this plan was run at plan time and is known-green (unit suites) or known-executable on a WebGPU host (GPU suites, `--rerun-tasks`); the full-run and boundary commands follow the exact FP-09/FP-10 shapes. The plan quotes the real observed timings in the Context baseline.
9. **Roadmap wording discipline (AGENTS.md):** all coverage is described in terms of the WGSL/WebGPU prepared route; no Ganesh/Graphite/SkSL path is introduced; `SkRuntimeEffect` remains a compatibility facade. The mission's "Aucun fallback caché" is enforced by the Task 9 residual-set check and the absence guard run.

**Deliverable mapping (mission items (1)-(5)):** (1) inventory of the 8 gaps with root cause + A/B classification → Context §1; (2) prioritized TDD coverage tasks → Tasks 2-7 (retained ordering → hairline → multi-render → path-dst-read → multi-layout → mask-blur clips); (3) pinned-test re-pointing with evidence → Context §3 + per-task re-point steps; (4) regression proofs (full suites + guards) → Task 9 Steps 1-2; (5) roadmap FP-11 completed + FP-12 transfers → Task 9 Step 4.
