# FP-08 — Retire Immediate and CPU Continuation Paths Implementation Plan (REDUCED SCOPE)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **SCOPE REVISION (2026-08-08, after Task 4/5 execution):** the original plan's Tasks 4–7 (route-authority collapse to Terminal-only, deletion of `renderViaGpuLegacy` and all legacy-only helper machinery) were **executed and REVERTED** — they caused ~636 GPU test failures (`GPUAllApiBlendSurfaceTest`): the prepared route does NOT yet cover destination-read blends, non-SrcOver core-primitive blends, hairline points, mixed uniform layouts, or analytic-clip non-direct geometry. Those families were genuinely rendered by `renderViaGpuLegacy` (not "legacy-pinning test expectations"). The full legacy retirement is deferred to **FP-09** (new roadmap entry). This plan now covers only the safe retirements: the empty adapter + its plumbing (Tasks 1–3, DONE), native BGRA8 in the prepared route (Task 8), the stable code rename (Task 9), and reduced-scope evidence/closure (Task 10). Tasks 4–7 of the original plan are superseded by FP-09.

**Goal:** Retire the empty legacy adapter (`GPULegacyImmediatePathAdapter`) and its production plumbing, add native BGRA8 support to the prepared route, rename the last `legacy.surface.prepared.*` code, and prove via production searches + regression tests that the retired adapter paths are absent — while KEEPING `renderViaGpuLegacy` as the fallback for families the prepared route cannot yet render (destination-reads, non-SrcOver core blends, hairline). Full legacy retirement is tracked as FP-09.

**Architecture:** The adapter (`GPULegacyImmediatePathAdapter`, `LegacyDisplayOpFamily`, `GPULegacyImmediatePathDump`) is deleted; `legacyDump` leaves `GPUOpMapping`/`GPUFramePathInventoryPlan`; the `family` field leaves `GPUPreparedSurfaceEligibility.Legacy`. The router keeps its `hasTerminalPreparedFamily` split: terminal families (image/text/vertices/composites) refuse with stable codes; non-terminal families that the prepared builder cannot lower fall back to `renderViaGpuLegacy` (destination-reads, non-SrcOver blends, hairline — currently ~636 GPU cases). BGRA8 is supported natively in the prepared route by rendering into a `bgra8unorm` target (Graphite/Dawn model: `kBGRA_8888_SkColorType ↔ TF::kBGRA8` with `X::kIdentity`), so the readback yields BGRA-ordered bytes with no CPU swizzle. The GPU-owned destination continuation (`GPUDestinationSnapshotOperation.TextureCopy`/`CopyAsDraw`, `gpu-renderer/.../destination/`, prepared readback) is retained; `GPUTextCPUUploadTelemetryRecord` is descriptive telemetry and is retained.

**Tech Stack:** Kotlin, WebGPU via wgpu4k, WGSL generation, Gradle (`./gradlew -F off`), JUnit (`kotlin.test`).

**Reference docs:**
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-07 `completed`; this plan delivers FP-08.
- `reports/upstream-rebaseline/2026-06-29-gpu-renderer-pre-existing-test-failures.md` — the `GPURendererPackageBoundaryTest` package-boundary case is a **documented pre-existing failure** (4 failures on master). FP-08 must NOT fix it and must NOT change its failure state.
- `/Users/chaos/workspace/kanvas-forge/skia-main` — Graphite/Dawn C++ evidence for the GPU-owned destination-read model (quoted below in Context).

---

## Context: validated branch state (evidence, 2026-08-07)

**HEAD:** `6b9e273ea` (FP-07 closed, working tree clean). Branch `codex/graphite-dawn-frame-fp08`.

**Graphite/Dawn destination-read model (skia-main, verified):** destination reads stay 100 % GPU-owned — never a CPU readback:
- `src/gpu/graphite/ResourceTypes.h:58-67` — `DstReadStrategy { kNoneRequired, kTextureCopy, kTextureSample, kReadFromInput, kFramebufferFetch }`.
- `src/gpu/graphite/Caps.cpp:340-348` — strategy is `kFramebufferFetch` or `kTextureCopy`; neither touches the CPU.
- `src/gpu/graphite/DrawContext.cpp:271-291` — `kTextureCopy` uses `Image::Copy` (GPU texture copy), sampled in the shader.
- `src/gpu/graphite/Image_Graphite.cpp:105-130` — `Image::Copy` = `CopyTextureToTextureTask` (blit) or `CopyAsDraw` (render). Always GPU.
- `src/gpu/graphite/dawn/DawnCommandBuffer.cpp:927-938` — the dst copy is bound as an extra `sampler + textureView` in the fragment shader.
- `src/gpu/graphite/Context.cpp:764-780` — the only CPU readback (`CopyTextureToBufferTask` + `SynchronizeToCpuTask`) is the public `readPixels` API, never the blend destination continuation.

**Graphite/Dawn BGRA8 model (skia-main, verified):** BGRA8 is a first-class render format, not a post-hoc conversion:
- `src/gpu/graphite/TextureFormat.h:97` — `kBGRA8` is a native `TextureFormat`.
- `src/gpu/graphite/TextureFormat.cpp:482` — `CASE(kBGRA_8888_SkColorType, TF::kBGRA8, TF::kRGBA8)`.
- `src/gpu/graphite/TextureFormat.cpp:548` — `CASE(TF::kBGRA8, kBGRA_8888_SkColorType, X::kIdentity)` — no swizzle, render directly into BGRA8.
- `src/gpu/graphite/dawn/DawnGraphiteUtils.cpp:182,355` — `kBGRA8 ↔ wgpu::TextureFormat::BGRA8Unorm`, renderable/blendable.

Kanvas already prefers `BGRA8Unorm` for native Metal surfaces (`gpu-renderer/.../execution/GPUBackendRuntimeNative.kt:7196,7249`) and the prepared materializer already maps `"bgra8unorm" → GPUTextureFormat.BGRA8Unorm` (`GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt:1479`). WebGPU stores fragment output `(r,g,b,a)` into a `BGRA8Unorm` attachment as memory `[B,G,R,A]`, so a shader written in RGBA semantics produces correctly-ordered BGRA bytes on readback — no CPU swizzle, matching Graphite's identity.

**FP-06/FP-07 guards that MUST stay green after this plan:**
- `unsupported.picture.nested_vertices` — pinned by `GPUPreparedSurfaceProductRouterTest.kt:279-280` (guard functions `coreRoutePreflightRefusalReason`/`picturePreflightRefusalReason` in `GPUClipCoverage.kt:340-353`) and enforced in the prepared route by the composite capture (`GPUPreparedCompositeCaptureSemanticTest.kt:398-431`, code `unsupported.composite.operation`). The guard FUNCTIONS are retained (test-pinned) even though their legacy-only production callers are removed — see Task 7 scope note.
- Composite route + blend/clip guards — covered by `GPUPreparedSurfaceProductRouterTest`, `GPUClipCoverageSurfaceTest`, `GPUAllApiBlendSurfaceTest`, `GPUClipCoverageDispatchTest`, `GPUPreparedSurfaceProductNativeSmokeTest`.
- `GPURendererPackageBoundaryTest` — documented pre-existing failure; leave as-is.

**Baseline verified on `6b9e273ea`:**
- `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --no-parallel --console=plain` → BUILD SUCCESSFUL (11 s).
- `./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain` → FAILS ONLY on `gpu renderer production source satisfies package boundary rules` (pre-existing, see doc). All other 21 cases pass. **Do not modify.**

**Build command convention (this worktree):** `rtk proxy` is not on PATH; use `./gradlew -F off <tasks> --no-parallel --console=plain` with dependency verification disabled. Do NOT modify `gradle/verification-metadata.xml`.

---

## File Map

### Deleted (new to this plan)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt` — the adapter, `LegacyDisplayOpFamily`, `GPULegacyImmediatePathDump` (Tasks 2).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt` — legacy clip execution (`renderWithClip`, `GPUClipRouteTrace`, `GPUClipDestinationReadComposer`, `GPUClipSourceSurface`, `copyForClipSource`, `copyForDestinationReadSource`) (Task 7).
- Legacy-only production helpers (Task 7): `LayerScissorOffscreenTarget`, `LayerScissorRenderRecorder`, `LayerPlan`/`LayerCompositePlan`/`BackdropPlan`/`SceneTargetFrame`, `renderDestinationReadBlend` + its uniform-draw helpers, `cachePixels`, CPU text-atlas helpers (`computeAtlasDst`, `hasColorGlyphs`, `buildTextAtlasMesh`, `drawTextAtlasPass`, `resolveTextColor`, `extractSolidShaderColor`, `ctmEffectiveScale`, `scaledForRasterization`, `normalizeGlyphRects`), `expandPicturesForGpuReplay` (with its private replay lambdas), legacy mask-lease machinery in `GPUClipCoverage.kt` that has no prepared-route caller.

### Modified (new to this plan)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` — remove `legacyDump` from `GPUOpMapping`, remove the `GPULegacyImmediatePathAdapter()` instance + `legacy.accepts/recordInvocation/dump` call sites (~15), remove `expandPicturesForGpuReplay` + its private helpers (keep `withPictureReplayState` — used by the prepared composite capture at `GPUPreparedCompositeCapture.kt:323`). (Tasks 2, 7)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt` — remove `legacyDump` from `GPUFramePathInventoryPlan` and its two construction sites. (Task 2)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt` — remove `LegacyDisplayOpFamily`/`family`; treat `FlushAndSnapshot` as a state event (no longer a Legacy trigger); empty/state-only frames → `Candidate`; color-format refusals → a stable `Refused(code)` eligibility. (Tasks 4, 5)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt` — remove `GPUPreparedSurfaceProductRoute.Legacy`; BGRA8 → prepared with `BGRA8Unorm` color; `BeforePreparedEntryRefused` → always `Terminal` (remove `hasTerminalPreparedFamily` split); `success()` returns the requested `PixelFormat`. (Tasks 4, 9, 10)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt` — remove `GPUPreparedSurfaceLegacyPort`, the `legacyPort`/`legacyRouteTrace` params, the `Legacy` decision branch, and `GPUPreparedSurfaceRouteDecision.Legacy`. (Tasks 4)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` — delete `renderViaGpuLegacy` (l.729–~2950) + `preparedSurfaceLegacyPort` + `renderViaGpu`'s `routeTrace`/legacy plumbing + legacy-only helpers. (Tasks 6, 7)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` — rename `legacy.surface.prepared.runtime-capabilities-unavailable` → `unavailable.surface.prepared.runtime-capabilities` (stable terminal code). (Task 12)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt` — accept `GPUColorFormat.BGRA8_UNORM → Ready(BGRA8Unorm, EncodedPremulSrgb)`. (Task 4)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` — `validatePreparedSceneTargetRequest` accepts `GPUColorFormat.BGRA8Unorm`. (Task 4)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` — (NoOp gate) classify empty/state-only frames as `NoOp` so empty renders stay backend-free. (Task 5)

### Tests (new / modified / deleted — Task 3, 5, 8, 10, 11)
- Modified: `GPUPreparedTextNoFallbackTest.kt`, `GPUFramePathApiInventoryTest.kt`, `GPUPreparedVerticesFramePreparerTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `GPUPreparedSurfaceFrameGateTest.kt`, `GPUPreparedSurfaceProductEntryTest.kt`, `GPUPreparedSurfaceFrameExecutorTest.kt`, `GPUPreparedSurfaceFrameBuilderTextTest.kt`, `GPUPreparedImageRefusalMatrixTest.kt`, `GPUPreparedSurfaceProductNativeSmokeTest.kt`, `GPUPreparedSurfaceImagePixelTest.kt`, `GPUAlphaImageMaterialTest.kt`, `GPUPreparedTextFilterBoundaryTest.kt`, `GPUClipCoverageSurfaceTest.kt`, `SurfaceTest.kt`.
- Deleted or re-pointed (legacy-only machinery): `GPUClipCoverageDispatchTest.kt` (renderWithClip case), `GPUTextAtlasGeometryTest.kt` (buildTextAtlasMesh), `GPUSaveLayerCompositeRegressionTest.kt` (LayerScissorOffscreenTarget).
- New: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLegacyAbsenceTest.kt` — production-search proof of absence (Task 13).

---

## Phase 0 — Baseline & dead-path detection

### Task 1: Inventory every remaining path to the legacy renderer and freeze the green baseline

**Files:** none (evidence only).

**Context:** Before touching code, the implementer must produce an exhaustive, `file:line`-anchored map of every production branch that can still reach `renderViaGpuLegacy`/`legacyPort`, plus every test that pins a legacy expectation. This map is the acceptance oracle for the whole plan.

- [ ] **Step 1: Enumerate every `Legacy` return site in production**

```bash
cd /Users/chaos/workspace/kanvas/.worktrees/graphite-dawn-frame-fp08
rg -n "GPUPreparedSurfaceProductRoute.Legacy|GPUPreparedSurfaceEligibility.Legacy|GPUPreparedSurfaceRouteDecision.Legacy|GPUPreparedSurfaceLegacyPort|renderViaGpuLegacy|legacy\.surface\.prepared|legacy\.accepts|legacy\.recordInvocation|legacy\.dump|legacyDump|\.Legacy\(" kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu --type kotlin
```

Expected: matches the sites listed in the File Map (Router.kt:34,37,47; FrameGate.kt:17,29,61-64,68-71; FrameExecution.kt:275; ProductEntry.kt:12,21,56-57,70-71; GPUOpMapper.kt:97,133,157,186,193,238,252,295,338,376,415,454,468,477,497,519,541,563,571; GPUFramePathApiInventory.kt:92,174,187; GPURenderer.kt:703,714-715,723-726,729). The grep will also match `ProductEntry.kt:21` (the fun interface declaration) and `:56-57` (the `legacyPort`/`legacyRouteTrace` params), and the gate's `Legacy(...)` construction sites at l.62 and l.69 — record whatever the grep returns verbatim; the goal is a complete before-snapshot, not an exact count. Save the full output to `/tmp/fp08_legacy_map.txt` — this is the before-snapshot.

- [ ] **Step 2: Enumerate every test consumer**

```bash
rg -l "GPULegacyImmediatePathAdapter|GPUPreparedSurfaceLegacyPort|legacyDump|GPUClipRouteTrace|renderWithClip|buildTextAtlasMesh|LayerScissorOffscreenTarget" kanvas/src/test --type kotlin | sort
```

Expected: the 15 test files listed in the File Map's Tests section.

- [ ] **Step 3: Freeze the green baseline**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL. (GPU-backed tests like `*NativeSmokeTest`/`*ClipCoverageSurfaceTest` require WebGPU and are run later; the pure-route suites above must be green here.)

- [ ] **Step 4: Commit**

```bash
git add reports/
git commit -m "docs(surface): fp08 legacy path inventory and green baseline evidence"
```

---

## Phase 1 — Adapter removal

### Task 2: Delete `GPULegacyImmediatePathAdapter` and its production plumbing

**Files:**
- Delete: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` (l.94-103, 128-165, 184-186, 230-252, 290-376, 410-541, 560-575)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt` (l.92, 174, 187)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt` (l.17 — `family` field removed in Task 4; remove only the type usage now via compiler-driven cleanup)

**Context:** The adapter is diagnostic-only: `LegacyDisplayOpFamily` is an empty enum, `allowedFamilies = emptySet()`, `familyOrNull` returns null for every op. The `legacyDump` field on `GPUOpMapping` and `GPUFramePathInventoryPlan` therefore always records zero invocations. This task deletes the file and the field plumbing so the codebase cannot compile against them.

- [ ] **Step 1: Delete the adapter file and its `legacyDump` plumbing**

Delete `GPULegacyImmediatePathAdapter.kt`. In `GPUOpMapper.kt`: remove `legacyDump` from `GPUOpMapping` (l.97); remove `val legacy = GPULegacyImmediatePathAdapter()` (l.133); remove every `legacyDump = legacy.dump()` named argument; remove `if (legacy.accepts(operation)) legacy.recordInvocation(operation)` blocks (l.186, 468, 519). In `GPUFramePathApiInventory.kt`: remove `legacyDump` from `GPUFramePathInventoryPlan` (l.92), `legacyDump = mapping.legacyDump` (l.174), and `legacyDump = GPULegacyImmediatePathDump(0, emptyMap())` (l.187).

In `GPUPreparedSurfaceFrameGate.kt`, remove the `family: LegacyDisplayOpFamily? = null` field from `GPUPreparedSurfaceEligibility.Legacy` (l.17) **in the same edit** — `LegacyDisplayOpFamily` is defined only in the deleted adapter file, so leaving the field would break `:kanvas:compileKotlin` on a production file (the gate constructs `Legacy` without `family` at l.29/61/68, and the tests use their own local `Expected.Legacy` — no other reference exists).

- [ ] **Step 2: Compile the main source set**

```bash
./gradlew -F off :kanvas:compileKotlin --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL — `compileKotlin` does NOT compile test sources and does NOT fail on unused internal/private declarations, so the only failures to expect now are test-source references to the deleted adapter/`legacyDump`, surfaced by `:kanvas:compileTestKotlin`/`:kanvas:test`. Run:

```bash
./gradlew -F off :kanvas:compileTestKotlin --no-parallel --console=plain
```

Expected: FAIL with a list of test-file references still using `GPULegacyImmediatePathAdapter`/`legacyDump`. This is the driving list for Task 3.

- [ ] **Step 3: Re-point the test consumers (with Task 3)**

The test edits in Task 3 unblock compilation. After Task 3, re-run:

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedVerticesFramePreparerTest" --tests "*GPUPreparedTextNoFallbackTest" --tests "*GPUFramePathApiInventoryTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A kanvas/src/main kanvas/src/test
git commit -m "refactor(surface): delete legacy immediate path adapter and legacyDump plumbing"
```

### Task 3: Re-point adapter-consumer tests

**Files:**
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextNoFallbackTest.kt` (l.26-48 — delete the `legacy adapter exposes no families` test; keep the 4 ProductEntry tests, removing the `legacyPort` arg in Task 4)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt` (l.2289-2312 — delete the adapter test; keep the surrounding tests, remove `legacyDump` assertions at l.120, 1467, 1537)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesFramePreparerTest.kt` (l.47, 108, 170 — drop `legacyDump = GPULegacyImmediatePathDump(...)` args and the `legacyDump.invocationCount == 0` assertion)
- Modify (assertion-only removal): `GPUPreparedSurfaceFrameBuilderTextTest.kt` (l.514-515), `GPUPreparedImageRefusalMatrixTest.kt` (l.193, 358), `GPUPreparedSurfaceProductNativeSmokeTest.kt` (l.393), `GPUPreparedSurfaceImagePixelTest.kt` (l.158), `GPUAlphaImageMaterialTest.kt` (l.141), `GPUPreparedTextFilterBoundaryTest.kt` (l.43, 72), `GPUPreparedSurfaceProductRouterTest.kt` (l.363, 385)

**Context:** Every test asserting `legacyDump.invocationCount == 0` asserts a tautology once the field is gone — delete the assertion. The two tests that directly construct `GPULegacyImmediatePathAdapter` test an empty, deleted class — delete the tests. Keep every behavior assertion (prepared route, refusal codes) intact.

- [ ] **Step 1: Apply the re-points above**

Remove the tautological `legacyDump` assertions and delete the two adapter-construction tests. Do NOT change any refusal-code assertions.

- [ ] **Step 2: Run the affected suites**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedVerticesFramePreparerTest" --tests "*GPUPreparedTextNoFallbackTest" --tests "*GPUFramePathApiInventoryTest" --tests "*GPUPreparedSurfaceFrameBuilderTextTest" --tests "*GPUPreparedImageRefusalMatrixTest" --tests "*GPUAlphaImageMaterialTest" --tests "*GPUPreparedTextFilterBoundaryTest" --tests "*GPUPreparedSurfaceImagePixelTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL (Task 2 Step 3 re-ran; these are the same suites).

- [ ] **Step 3: Commit**

```bash
git add -A kanvas/src/test
git commit -m "test(surface): drop legacy adapter tautologies from adapter consumer tests"
```

---

## Phase 2 — SUPERSEDED: route collapse + legacy renderer removal → FP-09

### Tasks 4–7 (original plan): COLLAPSED ROUTE AUTHORITIES / LEGACY RENDERER DELETION — EXECUTED AND REVERTED

**Status: EXECUTED → REVERTED (2026-08-08).** Commits `9e79eb857` (Task 4, route collapse) and `c5325a3d0` (Task 5, test re-points) were created, then reverted (`3150fc3fe`, `0f1106800`).

**Evidence of the reversal cause (verified on the executed commits):** `GPUAllApiBlendSurfaceTest` failed ~636 GPU cases with 5 refusal codes that the prepared route cannot cover:
- `unsupported.destination_read.required` — 630 cases (destination-read blends: DARKEN, MULTIPLY, …)
- `unsupported.native-core-primitive.blend` — 330 cases (non-SrcOver blends on core primitives)
- `unsupported.core_primitive.point.hairline_exact_lowering` — 168 cases (hairline points)
- `unsupported.recording.core_primitive_mixed_uniform_layouts` — 92 cases
- `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` — 52 cases

Before the collapse, `GPUPreparedSurfaceProductRouter.route` mapped `BeforePreparedEntryRefused` → `Legacy` for non-terminal families, and `renderViaGpuLegacy` RENDERED those frames (pixel oracle passed). The collapse made them terminal refusals — a functional regression, not legacy-pinning expectations. The plan's assumption ("the prepared route covers everything the legacy rendered") was wrong.

**Decision (user, 2026-08-08):** keep `renderViaGpuLegacy` as the fallback for non-covered families; the full legacy retirement is deferred to **FP-09** (new roadmap entry): "retire `renderViaGpuLegacy` and the legacy-only helper machinery once the prepared route covers destination-reads, non-SrcOver core blends, hairline points, mixed uniform layouts, and analytic-clip non-direct geometry."

**What FP-09 will contain (transferred from the original Tasks 4–7):**
- Collapse `GPUPreparedSurfaceEligibility.Legacy` → `Refused`, `GPUPreparedSurfaceProductRoute.Legacy` → Terminal-only, `GPUPreparedSurfaceRouteDecision.Legacy` removal.
- `BeforePreparedEntryRefused` → always `Terminal` (once every fallback family is prepared-covered).
- Delete `renderViaGpuLegacy` + `preparedSurfaceLegacyPort` + `GPUPreparedSurfaceLegacyPort` + `expandPicturesForGpuReplay` + the legacy-only helper machinery (GPUClipExecution.kt, LayerScissorOffscreenTarget, CPU text-atlas builders, legacy mask-lease machinery).
- The FP-06 `nested_vertices` guard functions stay (test-pinned).
- Re-point `GPUAllApiBlendSurfaceTest`/`GPUClipCoverageSurfaceTest` expectations with evidence per the FP-08 evidence methodology.

**Do NOT execute the original Tasks 4–7 in FP-08.**

---
## Phase 3 — Native BGRA8 in the prepared route

### Task 4: Enable BGRA8 in the prepared route (Graphite/Dawn model)

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt` — add `GPUColorFormat.BGRA8_UNORM -> Ready(CanonicalGPUColorFormat.BGRA8Unorm, GPUColorInterpretation.EncodedPremulSrgb)`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` — `validatePreparedSceneTargetRequest` (l.968-1003) accepts `GPUColorFormat.BGRA8Unorm` → `GPUTextureFormat.BGRA8Unorm` with `EncodedPremulSrgb`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt` — remove the BGRA8 Legacy short-circuit (l.33-35); `success()` returns `format = PixelFormat.BGRA8` when the requested surface format is BGRA8; the executor's target uses `candidate.color.physicalFormat`
- Test: `GPUPreparedSurfaceFrameGateTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `SurfaceTest.kt`

**Context:** Graphite renders directly into a `kBGRA8`/`BGRA8Unorm` target with identity swizzle; WebGPU stores fragment output `(r,g,b,a)` into a `BGRA8Unorm` attachment as memory `[B,G,R,A]`, so the prepared readback of a `bgra8unorm` target is naturally BGRA-ordered — no CPU channel swap. The prepared materializer already maps `"bgra8unorm" → BGRA8Unorm` (`GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt:1479`). The surface `format` (PixelFormat) must be threaded to `success()` so the returned `RenderResult.format` and byte order match the requested surface format.

NOTE on current state (post-revert): at HEAD, `GPUPreparedSurfaceEligibility.Legacy` still exists (the collapse was reverted) and `mapPreparedGpuColorConfig` refuses `BGRA8_UNORM` with `unsupported.surface.gpu-color-format.bgra8-unorm`; the router's `format == PixelFormat.BGRA8` short-circuit routes BGRA8 surfaces to `renderViaGpuLegacy`. This task removes ONLY the BGRA8 short-circuit and enables the prepared BGRA8 path — it does NOT touch the `Legacy` eligibility/route variants (those are FP-09).

- [ ] **Step 1: Write the failing tests (red)**

In `GPUPreparedSurfaceFrameGateTest.kt`, extend `both public color refusals are propagated before candidate construction` (l.127-143): `GPUColorFormat.BGRA8_UNORM` must now produce `Candidate` with `color.physicalFormat == CanonicalGPUColorFormat.BGRA8Unorm` (no longer `Legacy("unsupported.surface.gpu-color-format.bgra8-unorm")`); `GPUColorFormat.RGBA8_UNORM` stays `Legacy`/refused at HEAD.

In `GPUPreparedSurfaceProductRouterTest.kt`, re-point `non-image gate legacy and BGRA never call the execution port` (l.56-81): the BGRA8 route must be `Prepared` (not Legacy) and reach the execution port; add a `RenderResult.format == PixelFormat.BGRA8` assertion using a fake executor whose readback returns BGRA-ordered bytes.

In `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt`, the existing `BGRA render and snapshots preserve exact channel order and color type` (l.23-45) becomes the native proof: it must render via the prepared route (no legacy) and still produce `byteArrayOf(0, 0, -1, -1)` for `drawColor(Color.RED)` (B=0, G=0, R=255, A=255), `ColorType.BGRA_8888`, and the subset snapshot. Keep the test as-is; it will fail if the route throws a terminal for BGRA8 today.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*SurfaceTest" --no-parallel --console=plain`
Expected: FAIL for two distinct reasons:
- `GPUPreparedSurfaceFrameGateTest`/`GPUPreparedSurfaceProductRouterTest` — BGRA8 still maps to `Legacy` (gate refusal / router short-circuit) instead of a `Prepared` route with `format == PixelFormat.BGRA8`.
- `SurfaceTest` — with no color override, the prepared route opens an RGBA target and returns RGBA-ordered bytes labelled BGRA8, so `assertArrayEquals(byteArrayOf(0, 0, -1, -1, ...))` fails on channel order (NOT a terminal throw). This is the red that proves the target-selection gap.

- [ ] **Step 3: Implement the mapping + target admission**

The default `Surface(format = PixelFormat.BGRA8, config = RenderConfig.DEFAULT)` carries `gpuColorFormat = RGBA8_UNORM_SRGB`, so the gate's color derivation (`config.mapPreparedGpuColorConfig()`) would produce `Ready(RGBA8UnormSrgb)` — the executor would open an RGBA target and the readback would be RGBA-ordered. The `bgra8unorm` target is therefore NEVER selected by the config path; the surface `format` must drive it (Graphite model: surface color type → texture format).

Implement, in this order:

1. In `GPUPreparedSurfaceColorMapping.kt`, add the mapping case:
```kotlin
GPUColorFormat.BGRA8_UNORM -> GPUPreparedSurfaceColorMapping.Ready(
    physicalFormat = CanonicalGPUColorFormat.BGRA8Unorm,
    interpretation = GPUColorInterpretation.EncodedPremulSrgb,
)
```
2. In `GPUPreparedSurfaceProductRouter.route()` (or via `GPUPreparedSurfaceFrameGate.classify` gaining a `format` param), **override the candidate color when the requested surface format is BGRA8**: after the gate returns `Candidate`, build `candidate.copy(color = Ready(CanonicalGPUColorFormat.BGRA8Unorm, GPUColorInterpretation.EncodedPremulSrgb))` whenever `format == PixelFormat.BGRA8`. This is what actually selects the `bgra8unorm` render target (the executor uses `request.candidate.color.physicalFormat` at `GPUPreparedSurfaceFrameExecution.kt:290,479`).
3. In `validatePreparedSceneTargetRequest` (`GPUBackendRuntimeNative.kt:968` — the function body runs to ~l.1003), add the `GPUColorFormat.BGRA8Unorm` branch accepting `EncodedPremulSrgb` and returning `GPUTextureFormat.BGRA8Unorm`.
4. Thread the requested `format: PixelFormat` from `route()` into `success()`, which returns `RenderResult(format = format, ...)` instead of the hardcoded `PixelFormat.RGBA8` (l.75). No CPU channel swap: WebGPU stores fragment output `(r,g,b,a)` into a `BGRA8Unorm` attachment as memory `[B,G,R,A]`, so the readback of the `bgra8unorm` target is naturally BGRA-ordered.

- [ ] **Step 4: Run to verify they pass + commit**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*SurfaceTest" --no-parallel --console=plain
git add -A kanvas/src/main kanvas/src/test gpu-renderer/src/main
git commit -m "feat(surface): native BGRA8 rendering in the prepared route"
```

---

## Phase 4 — Stable code naming

### Task 5: Rename the `runtime-capabilities-unavailable` code

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (l.275)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecutorTest.kt` (l.279, 659)

**Context:** Per the approved scope decision, the only `legacy.surface.prepared.*` code that survives becomes a terminal code with a non-legacy name. `legacy.surface.prepared.runtime-capabilities-unavailable` is produced by the prepared executor itself (not a legacy branch) and becomes a terminal refusal after Task 4 → rename to `unavailable.surface.prepared.runtime-capabilities`. The `flush-snapshot` and `empty-frame` codes were deleted in Task 4; `pixel-format.bgra8` was deleted in Task 4 (BGRA8 is now native).

- [ ] **Step 1: Write the failing test (red)**

In `GPUPreparedSurfaceFrameExecutorTest.kt`, change the two pinned strings to the new code and assert the executor returns `BeforePreparedEntryRefused` (now mapped to Terminal by the router):

```kotlin
assertEquals(
    "unavailable.surface.prepared.runtime-capabilities",
    assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
        noCapabilities.execute(request),
    ).diagnostic.code.value,
)
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameExecutorTest" --no-parallel --console=plain`
Expected: FAIL — old string still emitted.

- [ ] **Step 3: Rename in production + verify green**

Replace the string at `GPUPreparedSurfaceFrameExecution.kt:275`. Re-run the executor test — PASS.

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecutorTest.kt
git commit -m "fix(surface): rename runtime capabilities refusal to a non legacy terminal code"
```

---

## Phase 5 — Regression proof & closure

### Task 6: Full regression, guards, production searches, roadmap update, FP-09 entry

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLegacyAbsenceTest.kt`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` (FP-08 → `completed`; ADD FP-09 entry)
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-08-retire-immediate-cpu-paths-evidence.md`

**Context:** FP-08 (reduced scope) acceptance requires proof that the RETIRED ADAPTER paths are absent (production searches), that the FP-06/FP-07 guards stay green, and that `renderViaGpuLegacy` still serves the non-covered families. The absence test asserts only the symbols actually deleted in this plan — NOT `renderViaGpuLegacy`/`GPUClipRouteTrace`/`renderWithClip`/`cachePixels`/`buildTextAtlasMesh`/`LayerScissorOffscreenTarget`, which remain until FP-09.

- [ ] **Step 1: Add the production-search absence test**

`GPUPreparedSurfaceLegacyAbsenceTest.kt` scans `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu` source and asserts none of the retired tokens appear:

```kotlin
class GPUPreparedSurfaceLegacyAbsenceTest {
    @Test
    fun `retired legacy adapter symbols are absent from production`() {
        val retired = listOf(
            "GPULegacyImmediatePathAdapter",
            "LegacyDisplayOpFamily",
            "GPULegacyImmediatePathDump",
            "legacyDump",
        )
        val root = java.io.File("src/main/kotlin/org/graphiks/kanvas/surface/gpu")
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { line -> retired.any { token -> token in line } } }
            .toList()
        assertEquals(emptyList(), offenders)
    }
}
```

(Path note: the `:kanvas:test` working directory is the `kanvas/` module directory, so the root is `src/main/...` relative to the module — `File("kanvas/src/main/...")` would resolve to the non-existent `kanvas/kanvas/...`. If the module working dir assumption changes, resolve the path via the test class's location or a Gradle-provided property.)

- [ ] **Step 2: Run the full `:kanvas:test` and `:gpu-renderer:test`**

```bash
./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain 2>&1 | tee /tmp/fp08_full.log
```

Expected: BUILD SUCCESSFUL except the documented pre-existing `GPURendererPackageBoundaryTest` package-boundary case (unchanged). GPU-backed tests (`*NativeSmokeTest`, `*ClipCoverageSurfaceTest`, `*AllApiBlendSurfaceTest`, `*PixelTest`, `*ColorGlyph*`, `*DestinationCopyFrameSmokeTest`) run in the WebGPU-enabled environment; classify any failure with evidence.

- [ ] **Step 3: Verify the FP-06/FP-07 guards, the blend fallback, and the boundary**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: all green except the pre-existing boundary case (documented; must stay failing with the SAME 20-cycle violations — do not fix). `nested_vertices` pins green. `GPUAllApiBlendSurfaceTest` green means the legacy fallback still renders destination-read/non-SrcOver families (FP-09 precondition).

- [ ] **Step 4: Write the evidence report**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-08-retire-immediate-cpu-paths-evidence.md`: before/after diff of the Task 1 legacy map, the executed-then-reverted Task 4/5 commits with the 636-failure evidence (5 refusal codes table), the BGRA8 native byte-order proof, test score deltas, the FP-09 precondition list, and the Graphite/Dawn C++ references (ResourceTypes.h, Caps.cpp, Image_Graphite.cpp, DawnCommandBuffer.cpp) grounding the GPU-owned destination decision.

- [ ] **Step 5: Update the roadmap**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`: mark FP-08 `completed` (reference the evidence report), and ADD a new FP-09 entry:

```markdown
### FP-09 — Retire the legacy immediate renderer (deferred from FP-08)

Status: `pending`

Goal: retire `renderViaGpuLegacy`, the legacy port, and the legacy-only helper
machinery once the prepared route covers every currently-fallback family.

Preconditions (all proven by the FP-08 evidence report):
- destination-read blends (unsupported.destination_read.required — 630 cases);
- non-SrcOver core-primitive blends (unsupported.native-core-primitive.blend — 330);
- hairline points (unsupported.core_primitive.point.hairline_exact_lowering — 168);
- mixed uniform layouts (unsupported.recording.core_primitive_mixed_uniform_layouts — 92);
- analytic-clip non-direct geometry (…analytic_clip_non_direct_geometry — 52).

Acceptance (transferred from FP-08 original Tasks 4–7):
- route authorities collapse to Prepared/Terminal (BeforePreparedEntryRefused → always Terminal);
- `renderViaGpuLegacy`/`GPUPreparedSurfaceLegacyPort`/`preparedSurfaceLegacyPort` deleted;
- legacy-only helper machinery deleted (GPUClipExecution.kt, LayerScissorOffscreenTarget,
  CPU text-atlas builders, legacy mask-lease machinery), EXCEPT the FP-06
  `nested_vertices` guard functions (test-pinned);
- `GPUAllApiBlendSurfaceTest`/`GPUClipCoverageSurfaceTest` expectations re-pointed
  with evidence; regression suites green.
```

- [ ] **Step 6: Final state check**

```bash
git add reports/ kanvas/src/test kanvas/src/main gpu-renderer/src/main
git log --oneline 6b9e273ea..HEAD | cat
git status --short
```

Expected: the log shows the FP-08 task commits (inventory → adapter → test re-points → [reverted route collapse] → BGRA8 → code rename → regression evidence), no stray files, and `rg "GPULegacyImmediatePathAdapter|LegacyDisplayOpFamily|GPULegacyImmediatePathDump|legacyDump" kanvas/src/main` returns nothing while `rg -c "renderViaGpuLegacy" kanvas/src/main` returns a nonzero count (fallback retained until FP-09).

- [ ] **Step 7: Commit**

```bash
git add reports/
git commit -m "docs(surface): fp08 retired adapter paths evidence, fp09 entry, roadmap closure"
```

---

## Self-review notes (filled at plan time; revised 2026-08-08 after the reverted collapse)

- **Spec coverage (revised scope):** the reduced FP-08 maps to tasks: adapter + consumers deleted (Tasks 1–3, DONE at `1dd769d01`+`dbf725d61`+`51071ffa6`+`fed1a95d8`); native BGRA8 in the prepared route (Task 4); stable code rename (Task 5); production searches + regression tests + FP-09 roadmap entry (Task 6). Destination continuation stays GPU-owned (unchanged); `GPUTextCPUUploadTelemetryRecord` retained. The original acceptance item "no migrated family reaches an immediate high-level dispatch" is now scoped to the ADAPTER: terminal families (image/text/vertices/composites) already refuse before any dispatch; non-covered families keep the legacy fallback until FP-09.
- **No placeholders:** every task has concrete files, commands, and expected output. `GPUPreparedSurfaceFrameGate.classify` and `validatePreparedSceneTargetRequest` signatures must be read at HEAD before editing (Task 4).
- **Type consistency:** the legacy types (`GPUPreparedSurfaceEligibility.Legacy`, `GPUPreparedSurfaceProductRoute.Legacy`, `GPUPreparedSurfaceRouteDecision.Legacy`, `GPUPreparedSurfaceLegacyPort`) remain in use until FP-09; Task 4 does not touch them. The new stable code `unavailable.surface.prepared.runtime-capabilities` is introduced in production (Task 5) before any test re-points to it.
- **Known plan corrections recorded (scope decisions vs the roadmap):**
  1. **MAJOR — the route collapse (original Tasks 4–5) was executed and REVERTED.** The prepared route does not yet cover destination-read blends (630), non-SrcOver core blends (330), hairline points (168), mixed uniform layouts (92), and analytic-clip non-direct geometry (52) — ~636 GPU cases in `GPUAllApiBlendSurfaceTest` regressed from pixels to terminal refusals. The plan's assumption that the prepared route covered everything the legacy rendered was wrong. Deferred to FP-09 (new roadmap entry). Commits `9e79eb857`/`c5325a3d0` reverted by `3150fc3fe`/`0f1106800`.
  2. **BGRA8 is native, not refused (decision 1 = b1).** Per the Graphite/Dawn model (render into `kBGRA8`/`BGRA8Unorm`, identity swizzle) and the user's note that BGRA8 is the base format of some adapters (Metal prefers `BGRA8Unorm` at `GPUBackendRuntimeNative.kt:7196`), the prepared route gains native BGRA8 support instead of a terminal refusal.
  3. **The `runtime-capabilities-unavailable` code is renamed, not deleted (decision 2 = b).** It is produced by the prepared executor, not a legacy branch, so it survives as a terminal code with a non-legacy name.
  4. **`GPUTextCPUUploadTelemetryRecord` is retained**: advisory telemetry describing planned uploads ("does not claim that a GPU upload happened"), not a destination-continuation path; removing it would break the public `font/gpu-api` telemetry contract.
  5. **The `GPURendererPackageBoundaryTest` package-boundary case is a documented pre-existing failure** (4 pre-existing failures on master) and must remain in its exact failing state — the mission forbids fixing it in FP-08.

**Independent review corrections applied (2026-08-07, subagent audit):**
- **M1 (blocking, fixed):** the gate's `family: LegacyDisplayOpFamily?` field is removed in Task 2 together with the adapter file (the type lives only in the deleted file; leaving the field broke `:kanvas:compileKotlin`). Task 2's failure expectation now targets `compileTestKotlin`, not `compileKotlin`.
- **M2 (blocking, fixed):** Task 4 (BGRA8) forces the `bgra8unorm` target by overriding the candidate color when `format == PixelFormat.BGRA8` (the default `RenderConfig` carries RGBA8_UNORM_SRGB, so the config-derived color never selects BGRA8); the red-test expectation for `SurfaceTest` is the channel-order failure, not a terminal.
- **M3 (fixed):** Task 6 (original) enumerated the five picture-replay helpers that MUST survive — superseded by the revert, retained in the FP-09 acceptance note.
- **M5/M6 (fixed):** Task 1's expected-match list completed; the absence-test path corrected to the `kanvas/` module working directory.
- **M7 (fixed):** GPU-backed suites annotated as environment-dependent.
- **M8 (fixed):** the sweep extended; the retained-but-pinned legacy internals are documented as deliberate exceptions — all superseded by the revert and transferred to FP-09's acceptance.
