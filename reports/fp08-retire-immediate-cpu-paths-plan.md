# FP-08 — Retire Immediate and CPU Continuation Paths Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the superseded high-level immediate renderer (`renderViaGpuLegacy`), the empty legacy adapter (`GPULegacyImmediatePathAdapter`), the legacy route authority variants, and the CPU-owned destination snapshot/upload machinery — leaving a single Prepared/Terminal surface route where the destination continuation stays GPU-owned, and proving the retired paths are absent via production searches and regression tests.

**Architecture:** The prepared Surface route becomes the only route. `GPUPreparedSurfaceProductRouter` returns `Prepared | Terminal` only (no `Legacy`); the gate classifies `Candidate | Refused`; `GPUPreparedSurfaceProductEntry` drops the `legacyPort` and the `GPUPreparedSurfaceLegacyPort` fun interface is deleted. Empty/state-only frames and `FlushAndSnapshot` route to Prepared (transparent NoOp / state event). BGRA8 is supported natively in the prepared route by rendering into a `bgra8unorm` target (Graphite/Dawn model: `kBGRA_8888_SkColorType ↔ TF::kBGRA8` with `X::kIdentity`), so the readback yields BGRA-ordered bytes with no CPU swizzle. The GPU-owned destination continuation (`GPUDestinationSnapshotOperation.TextureCopy`/`CopyAsDraw`, `gpu-renderer/.../destination/`, prepared readback) is retained; `GPUTextCPUUploadTelemetryRecord` is descriptive telemetry and is retained.

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
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt` — accept `GPUColorFormat.BGRA8_UNORM → Ready(BGRA8Unorm, EncodedPremulSrgb)`. (Task 8)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` — `validatePreparedSceneTargetRequest` accepts `GPUColorFormat.BGRA8Unorm`. (Task 8)
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

## Phase 2 — Route authority collapse (no Legacy route)

### Task 4: Collapse the route authorities to Prepared/Terminal

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (NoOp gate extension — empty/state-only → `NoOp`)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (`renderViaGpu` signature: drop `routeTrace`; drop `legacyPort` arg)

**Context:** This is the "duplicate route authorities" consolidation. Three files currently each carry a `Legacy` notion: `GPUPreparedSurfaceEligibility.Legacy` (gate), `GPUPreparedSurfaceProductRoute.Legacy` (router), `GPUPreparedSurfaceRouteDecision.Legacy` (entry). After this task, only `Prepared | Terminal` (product) and `Candidate | Refused` (gate) remain, and `BeforePreparedEntryRefused` always becomes `Terminal` — the `hasTerminalPreparedFamily` distinction existed solely to choose legacy continuation for non-terminal families, which no longer exists.

Per the approved scope decisions:
- **FlushAndSnapshot** → a state event in the gate (like `SetTransform`/`SetClip`/`Annotation`): it no longer triggers `Legacy`; a frame with visuals+FlushAndSnapshot is `Candidate`; a pure state-only frame hits the empty-frame path.
- **Empty/state-only frames** → `Candidate`; the executor's `GPUPreparedSurfacePreBackendNoOpGate` gains an empty/state-only classification returning `NoOp` (transparent readback, no backend open) so `Surface().render()` on an empty surface keeps returning transparent pixels.

- [ ] **Step 1: Write the failing tests (red) — gate classification**

In `GPUPreparedSurfaceFrameGateTest.kt`, re-point:
- `empty and state only frames use the stable empty frame diagnostic` (l.88-102) → these frames are now `Candidate` (assert `assertIs<GPUPreparedSurfaceEligibility.Candidate>`).
- `first refused operation wins with its exact index family and code` (l.104-124) → FlushAndSnapshot no longer wins; assert the frame is `Candidate` and FlushAndSnapshot contributes a state event, not a refusal.
- `both public color refusals are propagated before candidate construction` (l.127-143) → assert a `Refused` eligibility with the stable `unsupported.surface.gpu-color-format.*` code (RGBA8_UNORM stays refused; BGRA8_UNORM flips to `Ready` in Task 8 — keep this test asserting only RGBA8_UNORM refusal for now and add the BGRA8 flip in Task 8).
- `all display op variants have one exact whole frame classification` (l.58-85, fixtures l.181-211) → `FlushAndSnapshot` and the state-only fixtures become `Candidate`; no fixture may remain `Expected.Legacy`.

Add a new test:

```kotlin
@Test
fun `flush snapshot is a state event and empty frames enter prepared candidate`() {
    val withVisual = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
        GPUPreparedSurfaceFrameGate.classify(
            listOf(DisplayOp.FlushAndSnapshot(RECT), visualRect()),
            RenderConfig.DEFAULT,
        ),
    )
    assertEquals(2, withVisual.operations.size)

    val empty = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
        GPUPreparedSurfaceFrameGate.classify(emptyList(), RenderConfig.DEFAULT),
    )
    assertEquals(0, empty.operations.size)
}
```

(Adapt names to the exact `classify` signature on HEAD.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --no-parallel --console=plain`
Expected: FAIL — the gate still returns `GPUPreparedSurfaceEligibility.Legacy` for FlushAndSnapshot and empty frames.

- [ ] **Step 3: Implement the gate collapse**

In `GPUPreparedSurfaceFrameGate.kt`:
- Replace `GPUPreparedSurfaceEligibility.Legacy(code, operationIndex, family)` with `GPUPreparedSurfaceEligibility.Refused(code, operationIndex)` (no `family`).
- Remove `FlushAndSnapshot -> return Legacy("legacy.surface.prepared.flush-snapshot", ...)`; treat it as a state op (`-> Unit`) in the state bucket.
- Remove the `if (!hasVisual) return Legacy("legacy.surface.prepared.empty-frame")` branch; empty/state-only frames fall through to `Candidate` (keep the `hasVisual` accumulation for the general case — Candidate may carry zero visual ops, which the executor NoOp-gates).
- Color-mapping refusal (`mapPreparedGpuColorConfig()` `Refused`) → `GPUPreparedSurfaceEligibility.Refused(mapping.code)`.

- [ ] **Step 4: Implement the executor NoOp-gate extension**

In `GPUPreparedSurfaceFrameExecution.kt` `GPUPreparedSurfacePreBackendNoOpGate.classify`: after the existing text loop, if the frame contains **no** `DrawText` and **no** visual ops (only `SetTransform`/`SetClip`/`Annotation`/`FlushAndSnapshot` or nothing), return a `NoOp` with empty text metrics and the correct `stateEventCount` (count the state ops). This keeps `Surface().render()` on an empty surface transparent without opening a WebGPU backend.

- [ ] **Step 5: Collapse the router**

In `GPUPreparedSurfaceProductRouter.kt`:
- Delete `GPUPreparedSurfaceProductRoute.Legacy`.
- Delete the `if (format == PixelFormat.BGRA8) return Legacy(...)` short-circuit (BGRA8 handled in Task 8 — for now remove the Legacy return and let the gate/executor decide; Task 8 adds the native color mapping).
- `is GPUPreparedSurfaceEligibility.Refused -> return GPUPreparedSurfaceProductRoute.Terminal(GPUDiagnostic(...code=eligibility.code...))`.
- `BeforePreparedEntryRefused` → always `GPUPreparedSurfaceProductRoute.Terminal(execution.diagnostic)` (remove the `candidate.operations.any(DisplayOp::hasTerminalPreparedFamily)` ternary and the `hasTerminalPreparedFamily` extension).

- [ ] **Step 6: Collapse the entry**

In `GPUPreparedSurfaceProductEntry.kt`:
- Delete `GPUPreparedSurfaceLegacyPort` and `GPUPreparedSurfaceRouteDecision.Legacy`.
- Remove `legacyPort: GPUPreparedSurfaceLegacyPort` and `legacyRouteTrace: GPUClipRouteTrace?` params.
- Remove the `Legacy -> legacyPort.render(...)` branch (it becomes unreachable once `route()` never returns Legacy); keep `Prepared` and `Terminal`.

In `GPURenderer.kt`:
- Remove `preparedSurfaceLegacyPort` (l.723-726) and the `legacyPort = ...` / `legacyRouteTrace = routeTrace` args in `renderViaGpu`; drop the `routeTrace: GPUClipRouteTrace?` param from `renderViaGpu`.

- [ ] **Step 7: Run the pure-route suites (green)**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedTextNoFallbackTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL after the corresponding test re-points (Task 5).

- [ ] **Step 8: Commit**

```bash
git add -A kanvas/src/main kanvas/src/test
git commit -m "feat(surface): collapse prepared route authorities to prepared and terminal only"
```

### Task 5: Re-point the route/gate/executor tests

**Files:**
- Modify: `GPUPreparedSurfaceProductRouterTest.kt` (l.56-81 BGRA8/FlushAndSnapshot; l.422-441 before-entry refusal → Terminal)
- Modify: `GPUPreparedSurfaceProductEntryTest.kt` (all `legacyPort = GPUPreparedSurfaceLegacyPort {...}` args removed; `owner serializes prepared with gate legacy` test → rename to empty-frame serialization using `GPUPreparedSurfaceEligibility.Refused` or empty ops)
- Modify: `GPUPreparedTextNoFallbackTest.kt` (the 4 ProductEntry tests — remove `legacyPort` args; the `legacyCalls == 0` counters become `assertEquals(0, preparedCalls)`-style or are deleted)
- Modify: `GPUPreparedSurfaceProductNativeSmokeTest.kt` (l.816-848 and any `legacyPort`/`legacyRouteTrace` args)
- Modify: `GPUPreparedSurfaceFrameExecutorTest.kt` (empty/state-only frames now produce a `NoOp` — assert transparent readback and no backend open)
- Modify: `GPUClipCoverageSurfaceTest.kt` (remove `legacyPort` args in the `GPUPreparedSurfaceProductEntry.render` calls; remove `legacyRouteTrace = trace` args; the `trace.logicalDrawCount == 0` asserts that were counting legacy-only route traces are deleted; keep all refusal-code assertions)

**Context:** These tests exercise the entry/router/gate with a legacy port stub that must now be removed. The core assertions (prepared success, terminal refusals, exact codes) are unchanged; only the `legacyPort`/`legacyRouteTrace` plumbing and legacy-trace counters disappear.

- [ ] **Step 1: Remove `legacyPort`/`legacyRouteTrace` from every test call**

Use `rg -n "legacyPort|legacyRouteTrace|GPUPreparedSurfaceLegacyPort" kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu` to enumerate, then delete each argument. For `GPUClipCoverageSurfaceTest`, also delete `val trace = GPUClipRouteTrace()` lines and the trailing `assertEquals(0, trace.logicalDrawCount)` assertions.

- [ ] **Step 2: Re-point the router's before-entry-refusal test**

`before-entry refusal is legacy while terminal failure remains terminal` (l.422-441) → rename to `before-entry refusal is terminal`; both branches assert `assertIs<GPUPreparedSurfaceProductRoute.Terminal>`.

- [ ] **Step 3: Run the full route suite (green)**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedTextNoFallbackTest" --tests "*GPUPreparedSurfaceProductNativeSmokeTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL. NOTE: `*GPUPreparedSurfaceProductNativeSmokeTest` requires a WebGPU environment; without one it is skipped via `requireWebGpu()`/`assumeTrue` (a skip is NOT a pass signal). In a non-GPU environment, rely on the pure-route suites for green and run the native smoke suite separately in the WebGPU-enabled environment.

- [ ] **Step 4: Commit**

```bash
git add -A kanvas/src/test
git commit -m "test(surface): re-point route gate and executor tests to prepared only route"
```

---

## Phase 3 — Legacy renderer removal

### Task 6: Delete `renderViaGpuLegacy`, the legacy port, and the picture-replay expansion

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (delete `renderViaGpuLegacy` l.729–~2950, its private helpers, `preparedSurfaceLegacyPort`, and the `GPUClipRouteTrace` import)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` (delete `expandPicturesForGpuReplay` l.2300-~2380 and its private `replayPicture`/`expandPicture` lambdas; KEEP `withPictureReplayState` l.2268 and its helpers)

**Context:** After Task 4 nothing in production calls `legacyPort`, so `renderViaGpuLegacy` is unreachable and becomes the deletion target. `expandPicturesForGpuReplay` is called only by `renderViaGpuLegacy` (GPURenderer.kt:739). `withPictureReplayState` is used by the prepared composite capture (`GPUPreparedCompositeCapture.kt:323`) — keep it.

- [ ] **Step 1: Delete `renderViaGpuLegacy` and `preparedSurfaceLegacyPort`**

Delete the whole legacy renderer function body (l.729 through the last line of its local scopes, before `computeAtlasDst` at l.3043) and the `preparedSurfaceLegacyPort` val (l.723-726). Run `rg -n "renderViaGpuLegacy|preparedSurfaceLegacyPort" kanvas/src/main` — must be empty.

- [ ] **Step 2: Delete `expandPicturesForGpuReplay`**

Delete ONLY `expandPicturesForGpuReplay` (l.2300-2376, including its private `replayPicture`/`expandPicture` lambdas). **KEEP** `withPictureReplayState` (l.2268) AND its private helpers `clipForPictureReplay` (l.2378), `ClipStack?.transformForPictureReplay` (l.2397), `ClipStack.Complex.collapsedIntersectingRectOrNull` (l.2408), `ClipStack.DeviceRect.rectForPictureReplay` (l.2427), `ClipStackOp.transformForPictureReplay` (l.2437) — these five are called by `withPictureReplayState` (l.2272, 2291) and MUST survive (the composite capture calls `withPictureReplayState` at `GPUPreparedCompositeCapture.kt:323`). Run `rg -n "expandPicturesForGpuReplay" kanvas/src/main` — must be empty.

- [ ] **Step 3: Compile to confirm the deletion is self-consistent**

```bash
./gradlew -F off :kanvas:compileKotlin --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL. NOTE: `compileKotlin` will NOT flag the legacy-only helpers that remain (unused internal/private declarations are warnings only in this build, and test sources are not compiled here). The actual dead-code detection is the `rg` sweep in Task 7 Step 1 — do not attempt to "unused-symbol" your way through this task; the sweep is the evidence authority.

- [ ] **Step 4: Commit**

```bash
git add -A kanvas/src/main
git commit -m "feat(surface): delete the legacy immediate renderer and picture replay expansion"
```

### Task 7: Delete the legacy-only helper machinery (compiler-driven sweep)

**Files (delete/trim, evidence required per symbol):**
- Delete: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipExecution.kt` (whole file — `renderWithClip`, `GPUClipRouteTrace`, `GPUClipRouteContext`, `GPUClipSourceSurface`, `GPUClipDestinationReadComposer`/`RefusalComposer`, `copyForClipSource`, `copyForDestinationReadSource`; the prepared route uses `gpu-renderer`'s `GPUClipExecutionPlan`, not this file)
- Trim `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`: `LayerScissorOffscreenTarget`, `LayerScissorRenderRecorder`, `LayerPlan`/`LayerCompositePlan`/`BackdropPlan`/`SceneTargetFrame`, `LayerBounds`/`intersectLayerScissor`/`intersectScissor`, `renderDestinationReadBlend`, `clipCoverageBlendUniformDraw`/`destinationReadBlendUniformDraw`/`destinationReadScissorBlendUniformDraw`/`coverageCombineUniformDraw`, `cachePixels`, `computeAtlasDst`, `hasColorGlyphs`, `buildTextAtlasMesh`, `drawTextAtlasPass`, `resolveTextColor`, `extractSolidShaderColor`, `ctmEffectiveScale`, `scaledForRasterization`, `normalizeGlyphRects`
- Trim `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`: legacy mask-lease machinery (`GPUClipCoverageFrameCache`, `GPUClipCoverageFrameLease`, `GPUClipUsePrepass`, `GPUClipUsePrepassResult`, `GPUClipPreAcquireRefusal`, `preAcquireRefusalOrNull`, `acquireClipMask`, `ClipMaskLease`, `renderClipElement`, `applyConstantCoverage`, `GPUClipCoverageFrameBudgetExceededException`) **only if** `rg` proves no prepared-route caller. **KEEP** the FP-06 guard functions `coreRoutePreflightRefusalReason` (l.340) and `picturePreflightRefusalReason` (l.355) — the FP-06 `nested_vertices` guard is test-pinned (`GPUPreparedSurfaceProductRouterTest.kt:279-280`) and must stay green.

**Context:** Every symbol deleted here must be proven legacy-only first: `rg` its name across `kanvas/src/main` and confirm the only remaining references are within the functions being deleted. The prepared route's shared clip machinery is `GPUClipCoveragePlanner`/`GPUClipCoveragePlan` (gpu-renderer `clips` types) and `GPUClipExecutionPlan` — these are retained. The FP-06 `nested_vertices` guard functions are explicitly retained (scope correction vs "delete all dead code": the mission requires the FP-06 boundary to stay green, and the RouterTest pins those functions directly).

- [ ] **Step 1: Prove deadness per symbol (mandatory evidence)**

```bash
for s in renderWithClip GPUClipRouteTrace GPUClipDestinationReadComposer GPUClipSourceSurface LayerScissorOffscreenTarget renderDestinationReadBlend cachePixels buildTextAtlasMesh drawTextAtlasPass computeAtlasDst hasColorGlyphs normalizeGlyphRects GPUClipCoverageFrameCache GPUClipUsePrepass acquireClipMask preAcquireRefusalOrNull GPUClipPreAcquireRefusal; do
  echo "== $s =="; rg -l "\b$s\b" kanvas/src/main --type kotlin | tr '\n' ' '; echo
done
```

Record the output to `/tmp/fp08_dead_sweep.txt`. A symbol is safe to delete iff its only `kanvas/src/main` references are inside other symbols being deleted in this plan.

**Documented exceptions (surviving internals — same status as the FP-06 guards):** the following `GPURenderer.kt` internals are legacy-only in production but pinned by tests, so they are retained as dead-but-pinned helpers and documented in the FP-08 evidence report rather than swept (do NOT delete them in this task): `modulateCpalLayerAlpha` (l.87), `colorGlyphSourceColor` (l.91), `productIntermediatePlannerScopeDiagnostics` (l.94), `selectPathVerticesForCommand` (l.102), `hasActiveMaskBlur` (l.109,116), `GPUClipSourcePlane` (l.120), `requiresSeparateGeometryCoverage` (l.125), `forGeometryCoverage` (l.150-160), `layerOpacityUniformDraw` (l.192), `maskBlurDiagnosticFacts` (l.206), `destinationReadBlendModeIndex` (l.222), `clipCoverageBlendModeIndex` (l.242) — each pinned by one of `GPUBlendFormulaSurfaceTest`, `GPUPathStrokeInputTest`, `GPUColorGlyphPaintAlphaTest`, `GPUProductIntermediatePlannerScopeTest`. Their retirement is a follow-up decision outside FP-08 scope.

- [ ] **Step 2: Delete the proven-dead symbols**

Delete each symbol whose reference set is confined to the deletion set. Confirm `./gradlew -F off :kanvas:compileKotlin --no-parallel --console=plain` succeeds.

- [ ] **Step 3: Re-point or delete the direct-machinery tests**

- `GPUClipCoverageDispatchTest.kt`: the case at l.281 (`target.renderWithClip(...)`) tests deleted machinery → delete that test. Any remaining cases that only exercise deleted symbols are deleted too; cases that still exercise retained clip planning (e.g. `GPUClipCoverageFrameCache` register/acquire semantics) — check: the cache itself is legacy-only, so those cases are deleted and their coverage moves to `GPUClipCoveragePlanner` tests if applicable. **KEEP** the case at l.314 (`complex clip is materialized and composited by the real GPU route`) — it is an end-to-end Surface/clip test referencing no deleted symbol; verify it compiles and stays green after the sweep.
- `GPUTextAtlasGeometryTest.kt`: `buildTextAtlasMesh`/`normalizeGlyphRects`/`hasColorGlyphs` are legacy text-atlas builders → delete the file (the prepared text route has its own atlas tests: `GPUPreparedTextPixelTest`, `GPUPreparedColorGlyphSourceNativeOracleTest`, `GPUTextAtlasGeometryTest` is superseded).
- `GPUSaveLayerCompositeRegressionTest.kt`: uses `LayerScissorOffscreenTarget` (l.539-629) → delete the file; its saveLayer regression coverage is owned by `GPUPreparedCompositeFrameRouteIntegrationTest`/`GPUPreparedCompositeCaptureSemanticTest` on the prepared route.
- `GPUClipCoverageSurfaceTest.kt`: after Task 5 it no longer uses `GPUClipRouteTrace`/`renderWithClip`; verify `rg "GPUClipRouteTrace|renderWithClip" kanvas/src/test` only lists `GPUClipCoverageDispatchTest` (to be deleted).

- [ ] **Step 4: Run the surviving clip/blend suites**

```bash
./gradlew -F off :kanvas:test --tests "*GPUClipCoverageSurfaceTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL (GPU-backed clip tests require WebGPU; run with the environment that has it).

- [ ] **Step 5: Commit**

```bash
git add -A kanvas/src/main kanvas/src/test
git commit -m "refactor(surface): remove legacy clip and text atlas helper machinery"
```

---

## Phase 4 — Native BGRA8 in the prepared route

### Task 8: Enable BGRA8 in the prepared route (Graphite/Dawn model)

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt` — add `GPUColorFormat.BGRA8_UNORM -> Ready(CanonicalGPUColorFormat.BGRA8Unorm, GPUColorInterpretation.EncodedPremulSrgb)`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` — `validatePreparedSceneTargetRequest` (l.968-990) accepts `GPUColorFormat.BGRA8Unorm` → `GPUTextureFormat.BGRA8Unorm` with `EncodedPremulSrgb`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt` — remove the BGRA8 Legacy short-circuit (done in Task 4); `success()` returns `format = PixelFormat.BGRA8` when the requested surface format is BGRA8; the executor's target uses `candidate.color.physicalFormat` (already `BGRA8Unorm`)

**Context:** Graphite renders directly into a `kBGRA8`/`BGRA8Unorm` target with identity swizzle; WebGPU stores fragment output `(r,g,b,a)` into a `BGRA8Unorm` attachment as memory `[B,G,R,A]`, so the prepared readback of a `bgra8unorm` target is naturally BGRA-ordered — no CPU channel swap. The prepared materializer already maps `"bgra8unorm" → BGRA8Unorm` (`GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt:1479`). The surface `format` (PixelFormat) must be threaded to `success()` so the returned `RenderResult.format` and byte order match the requested surface format.

- [ ] **Step 1: Write the failing tests (red)**

In `GPUPreparedSurfaceFrameGateTest.kt`, extend `both public color refusals are propagated before candidate construction` (l.127-143): `GPUColorFormat.BGRA8_UNORM` must now produce `Candidate` with `color.physicalFormat == CanonicalGPUColorFormat.BGRA8Unorm` (no longer `Refused("unsupported.surface.gpu-color-format.bgra8-unorm")`).

In `GPUPreparedSurfaceProductRouterTest.kt`, re-point `non-image gate legacy and BGRA never call the execution port` (l.56-81): the BGRA8 route must be `Prepared` (not Legacy) and reach the execution port; add a `RenderResult.format == PixelFormat.BGRA8` assertion using a fake executor whose readback returns BGRA-ordered bytes.

In `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt`, the existing `BGRA render and snapshots preserve exact channel order and color type` (l.23-45) becomes the native proof: it must render via the prepared route (no legacy) and still produce `byteArrayOf(0, 0, -1, -1)` for `drawColor(Color.RED)` (B=0, G=0, R=255, A=255), `ColorType.BGRA_8888`, and the subset snapshot. Keep the test as-is; it will fail if the route throws a terminal for BGRA8 today.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameGateTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*SurfaceTest" --no-parallel --console=plain`
Expected: FAIL for two distinct reasons:
- `GPUPreparedSurfaceFrameGateTest`/`GPUPreparedSurfaceProductRouterTest` — BGRA8 still maps to `Refused`/no `Prepared` route with `format == PixelFormat.BGRA8`.
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

## Phase 5 — Stable code naming

### Task 9: Rename the `runtime-capabilities-unavailable` code

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

## Phase 6 — Regression proof & closure

### Task 10: Full regression, guards, production searches, roadmap update

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLegacyAbsenceTest.kt`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` (FP-08 → `completed`)
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-08-retire-immediate-cpu-paths-evidence.md`

**Context:** FP-08 acceptance requires proof that the retired paths are absent (production searches) and that the FP-06/FP-07 guards stay green.

- [ ] **Step 1: Add the production-search absence test**

`GPUPreparedSurfaceLegacyAbsenceTest.kt` scans `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu` source and asserts none of the retired tokens appear:

```kotlin
class GPUPreparedSurfaceLegacyAbsenceTest {
    @Test
    fun `retired legacy surface symbols are absent from production`() {
        val retired = listOf(
            "GPULegacyImmediatePathAdapter",
            "LegacyDisplayOpFamily",
            "GPULegacyImmediatePathDump",
            "GPUPreparedSurfaceLegacyPort",
            "renderViaGpuLegacy",
            "expandPicturesForGpuReplay",
            "legacy.surface.prepared",
            "GPUClipRouteTrace",
            "renderWithClip",
            "cachePixels",
            "buildTextAtlasMesh",
            "LayerScissorOffscreenTarget",
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

Expected: BUILD SUCCESSFUL except the documented pre-existing `GPURendererPackageBoundaryTest` package-boundary case (unchanged). GPU-backed tests (`*NativeSmokeTest`, `*ClipCoverageSurfaceTest`, `*PixelTest`, `*ColorGlyph*`, `*DestinationCopyFrameSmokeTest`) run in the WebGPU-enabled environment; classify any failure with evidence.

- [ ] **Step 3: Verify the FP-06/FP-07 guards and boundary stay green**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedCompositeCaptureSemanticTest" --tests "*GPUPreparedCompositeFrameRouteIntegrationTest" --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: all green except the pre-existing boundary case (documented; must stay failing with the SAME 20-cycle violations — do not fix). `nested_vertices` pins green.

- [ ] **Step 4: Write the evidence report**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-08-retire-immediate-cpu-paths-evidence.md`: before/after diff of the Task 1 legacy map, per-task route diagnostics, the compiler-driven dead-sweep evidence (`/tmp/fp08_dead_sweep.txt`), the BGRA8 native byte-order proof, test score deltas, and the Graphite/Dawn C++ references (ResourceTypes.h, Caps.cpp, Image_Graphite.cpp, DawnCommandBuffer.cpp) grounding the GPU-owned destination decision.

- [ ] **Step 5: Update the roadmap**

In `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`, mark FP-08 `completed` and reference the evidence report.

- [ ] **Step 6: Final state check**

```bash
git add reports/ kanvas/src/test kanvas/src/main gpu-renderer/src/main
git log --oneline 6b9e273ea..HEAD | cat
git status --short
```

Expected: the log shows the FP-08 task commits (inventory → adapter → route collapse → renderer deletion → helper sweep → BGRA8 → code rename → regression evidence), no stray files, and `rg "renderViaGpuLegacy|GPULegacyImmediatePathAdapter|legacy.surface.prepared|GPUPreparedSurfaceLegacyPort" kanvas/src/main` returns nothing.

- [ ] **Step 7: Commit**

```bash
git add reports/
git commit -m "docs(surface): fp08 retired immediate and cpu paths evidence and roadmap closure"
```

---

## Self-review notes (filled at plan time)

- **Spec coverage:** Every FP-08 acceptance maps to a task: adapter + consumers deleted (Tasks 2–3); no migrated family reaches an immediate high-level dispatch (Tasks 4–7); destination continuation stays GPU-owned (Task 7 keeps `GPUDestinationSnapshotOperation`, `gpu-renderer/.../destination/`, prepared readback; Task 8 keeps the GPU-owned BGRA8 readback; `GPUTextCPUUploadTelemetryRecord` is descriptive telemetry, retained); production searches + regression tests prove absence (Task 10).
- **No placeholders:** every task has concrete files, commands, and expected output. Where a symbol's deadness must be confirmed, the task mandates a `rg`-driven evidence step (Task 7 Step 1) rather than an assumption. `GPUPreparedSurfaceFrameGate.classify`, `GPUPreparedSurfaceProductEntry.render`, and `validatePreparedSceneTargetRequest` signatures must be read at HEAD before editing (they are the exact seams; Task 4/9 call this out).
- **Type consistency:** `GPUPreparedSurfaceEligibility.Candidate | Refused`, `GPUPreparedSurfaceProductRoute.Prepared | Terminal`, `GPUPreparedSurfaceRouteDecision.Prepared | Terminal` are used consistently; `renderViaGpu` and `GPUPreparedSurfaceProductEntry.render` lose the `legacyPort`/`legacyRouteTrace` params together; the new stable code `unavailable.surface.prepared.runtime-capabilities` is introduced in production (Task 9) before any test re-points to it.
- **Known plan corrections recorded (scope decisions vs the roadmap):**
  1. **BGRA8 is native, not refused (decision 1 = b1).** The roadmap's "retire CPU paths" does not include BGRA8; per the Graphite/Dawn model (render into `kBGRA8`/`BGRA8Unorm`, identity swizzle) and the user's note that BGRA8 is the base format of some adapters (Metal prefers `BGRA8Unorm` at `GPUBackendRuntimeNative.kt:7196`), the prepared route gains native BGRA8 support instead of a terminal refusal. This ADDS a small mapping/target-admission change to a retirement plan.
  2. **The `runtime-capabilities-unavailable` code is renamed, not deleted (decision 2 = b).** It is produced by the prepared executor, not a legacy branch, so it survives as a terminal code with a non-legacy name; the other three `legacy.surface.prepared.*` codes are deleted.
  3. **`FlushAndSnapshot` becomes a state event (decision 3 = a)** and **empty frames route to Prepared (decision 4 = a)** — both delete legacy branches and preserve today's transparent-output behavior.
  4. **Legacy helper machinery is deleted with its tests (decision 5 = a)**, EXCEPT the FP-06 `nested_vertices` guard functions (`coreRoutePreflightRefusalReason`/`picturePreflightRefusalReason`) which are retained because the mission explicitly requires the FP-06 guard to stay green and `GPUPreparedSurfaceProductRouterTest:279-280` pins them directly. This is a deliberate, evidence-cited deviation from "delete all dead code".
  5. **`GPUTextCPUUploadTelemetryRecord` is retained**: it is advisory telemetry describing planned uploads ("does not claim that a GPU upload happened"), not a destination-continuation path; removing it would break the public `font/gpu-api` telemetry contract for no FP-08 benefit.
  6. **The `GPURendererPackageBoundaryTest` package-boundary case is a documented pre-existing failure** (4 pre-existing failures on master) and must remain in its exact failing state — the mission forbids fixing it in FP-08, and the boundary check is about `gpu-renderer` package cycles, not the removed `kanvas` legacy code.

**Independent review corrections applied (2026-08-07, subagent audit):**
- **M1 (blocking, fixed):** the gate's `family: LegacyDisplayOpFamily?` field is removed in Task 2 together with the adapter file (the type lives only in the deleted file; leaving the field broke `:kanvas:compileKotlin` between Tasks 2–4). Task 2's failure expectation now targets `compileTestKotlin`, not `compileKotlin`.
- **M2 (blocking, fixed):** Task 8 now forces the `bgra8unorm` target by overriding the candidate color when `format == PixelFormat.BGRA8` (the default `RenderConfig` carries RGBA8_UNORM_SRGB, so the config-derived color never selects BGRA8); the red-test expectation for `SurfaceTest` is the channel-order failure, not a terminal.
- **M3 (fixed):** Task 6 now enumerates the five picture-replay helpers that MUST survive (`clipForPictureReplay`, the two `transformForPictureReplay`, `collapsedIntersectingRectOrNull`, `rectForPictureReplay`) — shared with the retained `withPictureReplayState`.
- **M4 (fixed):** Task 6 Step 3 no longer claims a compiler FAIL for unused helpers; the `rg` sweep of Task 7 is the dead-code evidence authority.
- **M5/M6 (fixed):** Task 1's expected-match list completed; Task 10's absence-test path corrected to the `kanvas/` module working directory.
- **M7 (fixed):** GPU-backed suites explicitly annotated as environment-dependent (skips are not pass signals).
- **M8 (fixed):** Task 7 sweep extended with `preAcquireRefusalOrNull`/`GPUClipPreAcquireRefusal`; the retained-but-pinned legacy internals (`destinationReadBlendModeIndex`, `selectPathVerticesForCommand`, `colorGlyphSourceColor`, `modulateCpalLayerAlpha`, `productIntermediatePlannerScopeDiagnostics`, `hasActiveMaskBlur`, `requiresSeparateGeometryCoverage`, `forGeometryCoverage`, `layerOpacityUniformDraw`, `maskBlurDiagnosticFacts`, `clipCoverageBlendModeIndex`, `GPUClipSourcePlane`) are documented as deliberate exceptions; `GPUClipCoverageDispatchTest` case l.314 is confirmed to survive.
