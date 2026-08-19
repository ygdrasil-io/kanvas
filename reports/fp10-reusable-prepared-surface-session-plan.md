# FP-10 — Reusable Prepared Surface Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the prepared Surface session lifecycle deterministic and reusable: compatible frames reuse one backend, one prepared scene session (target, invariant pipelines, frame-local pools), and the native crash class (`EXCEPTION_ACCESS_VIOLATION` in `wgpu_native.dll` through `Queue.writeBuffer` after repeated `GPUBackendRuntimeFactory.dispose()`/recreate churn) is eliminated by construction.

**Architecture:** FP-10 is NOT a family migration — it is a native lifetime bug plus an architectural acceptance. The device is already process-shared (`GPUBackendRuntimeNativeFactory.sharedInner`), but EVERY render still tears down and recreates the whole prepared session stack (target + 10 session caches + encoding backend + readback mapper + child teardown), and test teardown (`SurfaceTest.@AfterEach` → `GPUBackendRuntimeFactory.dispose()`) churns the shared device close/recreate repeatedly — the documented predecessor of the Windows native crash. The fix has three layers: (1) the factory becomes a synchronized state machine with an explicit device-generation counter and deterministic dispose (mutual exclusion with create, in-flight registration via the already-existing `GPUPreparedSceneChildRegistry`); (2) the process-wide executor (`GPUPreparedSurfaceFrameExecutor`) caches one prepared scene session keyed by `(deviceGeneration, width, height, colorFormat, interpretation)` and checks in instead of closing per frame, so compatible frames — including completion-only and readback outputs — share one session boundary; (3) every transition (generation/size/format/owner/close) closes exactly one old session and opens exactly one new one, proven by per-frame delta counters and cache creation/reuse counters surfaced in the executor evidence.

**Tech Stack:** Kotlin, WebGPU via wgpu4k, WGSL generation, Gradle (`./gradlew -F off`), JUnit (`kotlin.test`).

**Reference docs:**
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-10 entry (Status `pending`): the acceptance contract and the native crash evidence.
- `reports/fp09-retire-legacy-immediate-renderer-plan.md` — structure template; the executor/session evidence it re-pointed (Tasks 5-6) is FP-10's raw material.
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-09-retire-legacy-immediate-renderer-evidence.md` — §15 (full-run regression proof; `failed.surface.prepared.session-close` flake) and §17 (known environmental flake documentation: the `session-close` flake lands on a different random frame under GPU churn, green in isolation; the two-GPU environment note).
- `reports/upstream-rebaseline/2026-06-29-gpu-renderer-pre-existing-test-failures.md` — `GPURendererPackageBoundaryTest` package-boundary case is a documented pre-existing failure; FP-10 must NOT fix it and must NOT change its failure state. `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` is a documented pre-existing hardware failure (reproduces at base SHA); do not modify.

---

## Context: validated branch state (evidence, 2026-08-11)

**HEAD:** `a335b9a7d` (FP-09 squash-merge #2058, working tree clean). Branch `codex/graphite-dawn-frame-fp10`.

**Build command convention (this worktree):** `rtk proxy` is not on PATH; use `./gradlew -F off <tasks> --no-parallel --console=plain` with dependency verification disabled. Do NOT modify `gradle/verification-metadata.xml`. For the crash repro: `--rerun-tasks --no-daemon` (verified).

**Machine (crash-class-relevant):** Intel UHD Graphics 630 (integrated) + AMD Radeon Pro 5500M (discrete), macOS, WebGPU via Metal. The Windows `wgpu_native.dll`/`nvoglv64.dll` (Vulkan) crash class does NOT reproduce here; the ordered repro instead surfaces the documented `failed.surface.prepared.session-close` flake (FP-09 evidence §17) — itself a lifetime-instability symptom, and therefore part of FP-10's evidence (see §2).

### 1. FP-10 acceptance contract (from the roadmap, verbatim targets)

- repeated frames do not reopen the backend or prepared session;
- generation, size, format, owner, and close transitions are deterministic;
- completion-only and readback outputs share the same session boundary;
- cache creation/reuse counters and lifetime tests pass.

### 2. The native crash class and the repro attempt on this machine

**Windows evidence (roadmap, not reproducible on this host):**
- full `:kanvas:test` reproduces an `EXCEPTION_ACCESS_VIOLATION` in `wgpu_native.dll` through `Queue.writeBuffer` and `WgpuRenderRecorder.materializeFullscreenUniformSlab`;
- the ordered reproduction (`.\gradlew.bat :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks`) passes `SurfaceTest` 10/10, then the blend worker crashes in `Queue.writeBuffer`;
- `SurfaceTest.@AfterEach` repeatedly calls the process-global `GPUBackendRuntimeFactory.dispose()`; teardown/recreation is a sufficient predecessor trigger;
- FP-03 aggregate `gpu-renderer/hs_err_pid18980.log`: `EXCEPTION_ACCESS_VIOLATION` through `nvoglv64.dll`, Vulkan, and `wgpu_native.dll` while `GPUWgpu4kSolidRectFrameSmokeTest` called `Adapter.requestDevice` after earlier native session/resource teardown in the same worker — a second trigger location of the same lifetime/recreation failure class;
- the FP-01 device-limit change is causally excluded (focused tests pass; none of the three crash dumps contains the former alignment validation panic).

**Repro attempts on this machine (verified at plan time, 2026-08-11):**
1. Ordered repro (roadmap shape, FQCNs): `./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks --no-daemon` → **BUILD FAILED in 3m58s**: `SurfaceTest` 10/10 passed, then the blend suite ran 1,872 passed / **2 failed with `failed.surface.prepared.session-close: The prepared Surface session could not close cleanly.`** on `DrawDRRect/DST/UNCLIPPED` and `DrawVertices/DST/UNCLIPPED` — the documented environmental flake class (FP-09 evidence §17), landing this time on destination-read frames under churn. **No native AV crash.** Log: `/tmp/fp10_repro_fqcn.log`; XML failures in `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest.xml`.
2. Same classes via glob (`--tests "*SurfaceTest" --tests "*GPUAllApiBlendSurfaceTest"`): BUILD SUCCESSFUL in 4m24s, 1,946 passed / 0 failed / 0 test skipped (the 14 `SKIPPED` console lines are Gradle `checkKotlinGradlePluginConfigurationErrors` tasks, not tests). Log: `/tmp/fp10_repro_ordered.log`.
3. Isolated class (`--tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest"`): **BUILD SUCCESSFUL in 3m48s, 1,864/1,864 passed** — confirming the two ordered-run failures are the documented flake class (green in isolation), exactly as FP-09 §17 describes. Log: `/tmp/fp10_blend_isolated.log`.

**Conclusion:** the Windows AV crash class is host-specific (Vulkan/NVIDIA wgpu-native); this machine's Metal backend does not crash but DOES exhibit the sibling lifetime symptom (session-close failures under churn). The plan therefore uses the roadmap's minimal TDD reproduction — repeated `dispose()`/recreate followed by fullscreen-uniform-slab frames in one JVM — as a **native crash probe**, and adds **deterministic assertions** (session reuse counters, per-transition deltas, generation monotonicity) that catch the bug class on every host even without a native crash. This is the Task 1 red.

### 3. The lifecycle map at HEAD (verified 2026-08-11; the acceptance oracle)

**3.1 The process-global backend factory** — `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`:
- `GPUBackendRuntimeNativeFactory` object (l.917-966): `sharedInner: GPUBackendSession?` (l.918), `shutdownHook` (l.919), `createOrNull()` (l.925-947) creates the `WgpuBackendSession` (GLFW 1×1 context) ONCE and reuses it, registering a shutdown hook; `dispose()` (l.950-955) removes the hook, closes `sharedInner`, sets it to null. **No synchronization between `createOrNull` and `dispose`** (check-then-act at l.926). `NonClosingSession` (l.957-965) delegates everything except `close()` (no-op) so `.use {}` never destroys the shared device.
- `GPUBackendRuntimeFactory` public object (GPUBackendRuntimeContracts.kt:690-693) delegates to the native factory.
- `WgpuBackendSession` (l.1039+): `deviceGeneration = sessionDeviceGeneration(sessionOrdinal)` (l.1043) — a fresh generation per session; the session-scoped children registry `preparedSceneChildren = GPUPreparedSceneChildRegistry(::closeRuntimeResources)` (l.1054); `GPUPreparedSceneChildRegistry` (l.828-905) ALREADY implements close-wait: `reserve()` (l.847) → `Lease`, `bind(session)` (l.835-844) closes the session immediately if close was requested, `close()` (l.850-883) sets `closeRequested`, closes every bound child, and performs teardown only when all leases are released (`claimTeardownIfReady`, l.885-905).
- The quarantined-cache close retry machinery (l.1057-1116) and the mapping-executor termination wait (`GPUPreparedSceneMappingExecutorCloser`, ~l.790-830) already make backend close best-effort deterministic.

**3.2 The per-frame prepared session** — `WgpuBackendSession.prepareSceneFrameSession` (l.1292-~1630) creates, for EVERY call: a new prepared target (`GPUWgpu4kPreparedSceneTarget.create`, l.1303-1312), **10 session caches** (solidRect, corePrimitive, colorGlyph, registeredUniformRect, separableBlurRect, destinationCopy, maskBlur, surfaceBlit, preparedImage, preparedText — l.1313-1354), the encoding backend (l.1356-1362), the readback mapping executor + mapper (l.1363-1373), the child teardown (l.1377-1395), and the `GPUPreparedSceneFrameSession` wrapper (l.1397+) with:
- the compatibility validator (l.1399-1475): refuses `stale.prepared-scene-session.device-generation` (l.1401-1404), `unsupported.prepared-scene-session.target-count` (l.1423-1434), `render-target-declaration` (l.1453-1456), `stale.prepared-scene-session.target-identity` (l.1457-1460), `unsupported.prepared-scene-session.target-incompatible` (l.1464-1467) — the session is DESIGNED to accept multiple frames over one canonical target;
- the `GPUFrameCoordinatorFactory` (l.1476-1603) — creates a NEW `GPUFrameCoordinator` (preflighter + executor + materializers) per frame, incrementing `coordinatorCreations` (l.1477);
- `closeAction = childTeardown::close` (l.1604).

**3.3 The kanvas executor closes the session every frame** — `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt`:
- process-wide singleton port (GPURenderer.kt:29-30: `preparedSurfaceProductExecutionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)`);
- `GPUPreparedSurfaceBackendPortFactory.open()` (l.226-228) → `GPUPreparedSurfaceNativeBackendPortFactory.open()` (l.920-923) → `GPUBackendRuntimeFactory.createOrNull()`; the executor calls `backendFactory.open()` per frame (l.287);
- per frame: `backend.prepare(request)` (l.503-511) → new session; submit (l.521-534); await completion (l.535-544); `session.close()` in the `finally` (l.356-369); `backend.close()` in the `finally` (l.381-390);
- `finalizeSuccess` (l.648-775) pins PER-FRAME session creation: `beforeSubmit.targetCreations == 1L && targetCloses == 0L` (l.654), `afterCompletion` same (l.655), `afterClose.targetCreations == 1L && afterClose.targetCloses == 1L` (l.656), `afterClose.activeNativePayloads == 0` (l.657), `retentionRegistrations == retentionCompletions` (l.660), `distinctRetentionTickets == 1` (l.662), `evidence.frameCoordinatorCreations == 1L` (l.730), `evidence.encoders == 1L` (l.731), `evidence.submits == 1L` (l.733);
- `GPUPreparedSurfaceNativeSessionPort` (l.938-1015): `submit` (readback, l.941-977) and `submitCompletionOnly` (l.979-1011) both route to `session.renderFrame(...)` — the SAME session boundary machinery, so completion-only and readback can share one session once the executor stops closing per frame;
- serialization: `GPUPreparedSurfaceProductEntry.render` runs under the process-wide `GPUPreparedSurfaceRuntimeOwner.lock` (GPUPreparedSurfaceProductEntry.kt:30-32, 43) — a single cached session in the executor is safe.

**3.4 The crash-site chain (fullscreen uniform slab)** — all verified at HEAD:
- `WgpuRenderRecorder` (GPUBackendRuntimeNative.kt:3173-3345) holds `device` + `queue` (l.3175-3176); `writeTrackedBuffer` (l.3264-3268) = `queue.writeBuffer(buffer, offset, data)` — the AV crash line;
- `drawFullscreenPass`/`drawFullscreenUniformPayloadPass`/`drawFullscreenRawUniformPass` (l.3279/3307/3335) → `recordFullscreenUniformPass` (l.4771-4813) → `materializeFullscreenUniformSlab` (l.5034-5140): `createTrackedBuffer` (l.5122-5132) + `materializeFullscreenUniformSlabLease` (GPUConcreteResourceProvider.kt:706-772, cache key l.739-771) + the upload write;
- slab budget constant `FULLSCREEN_UNIFORM_SLAB_UPLOAD_BUDGET_BYTES = 1_048_576L` (l.158); `FULLSCREEN_UNIFORM_SLAB_SOURCE_LABEL` (l.159); source-label testing hooks (l.253-298); the slab lease records `cacheResult = Create`/`Reuse` (GPUConcreteResourceProvider.kt:740-748, 760-769).

**3.5 The reuse machinery that FP-10 activates** (already present, currently discarded per frame):
- session-scoped invariant caches: the 10 caches above (l.1313-1354), all `setupTransaction.own(...)` closed by `GPUPreparedSceneChildTeardown`;
- frame-local pools INSIDE the session cache: `GPUWgpu4kCorePrimitiveFramePool` owned by `GPUWgpu4kCorePrimitiveSessionCache` (GPUWgpu4kCorePrimitiveSessionCache.kt:371-517: vertices/indices/uniforms/bindGroup/pathDepthStencil/clipDepthStencil/coverageMask/msaaColor pools; `acquire` l.586, `counters()` l.590, pool reuse semantics pinned by `GPUWgpu4kCorePrimitiveFramePoolTest` — "path only growth never shrinks retained buffer capacities or recreates them later");
- the invariant creation/reuse counters already exist on `GPUPreparedSceneNativeCounters` (GPUFrameCoordinator.kt:312-363: `solidRectInvariantCreations/Reuses/Invalidations`, `corePrimitiveInvariantCreations/Reuses/Invalidations`, `registeredUniformInvariantCreations/Reuses`, `separableBlurInvariantCreations/Reuses`, `destinationSnapshotCreations/Reuses`, `colorGlyphAtlasReuses`, pool slot counters `coverageMaskSlotReuses` etc. at l.1613-1623) — these are the "cache creation/reuse counters" of the acceptance, but the executor evidence (`GPUPreparedSurfaceExecutionEvidence`, GPUPreparedSurfaceFrameExecution.kt:56-83) does NOT surface them yet;
- `GPUPreparedSceneFrameSession` state machine (GPUFrameCoordinator.kt:679-830): Idle/InFlight/CloseRequested/Closed, one in-flight frame per session (`unsupported.prepared-scene-session.concurrent-frame`, l.736-739), idempotent close (l.790-806).

### 4. Guards that MUST survive this plan

- `GPURendererPackageBoundaryTest` package-boundary case — documented pre-existing (exactly 20 cycle violations, 0 rule violations); do not fix, do not change failure state.
- `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` — documented pre-existing hardware failure (reproduces at base SHA); do not fix, do not change failure state.
- `GPUPreparedSurfaceLegacyAbsenceTest` — 16 retired tokens; FP-10 adds no legacy tokens, must not change its assertions.
- The `failed.surface.prepared.session-close` flake (FP-09 §17) — documented environmental; FP-10 must NOT weaken its assertions to hide it. If the deterministic session lifecycle removes it, that is evidence to record in Task 6, not a claim to assert in a pinned test (it remains environment-dependent).
- The session validator codes (§3.2) stay terminal; the executor's `preparedRouteResidualRefusalCodes` set (GPUPreparedSurfaceFrameExecution.kt:875-880) stays untouched.
- The FP-08/FP-09 destination-read machinery (GPU-owned `TextureCopy`/formula, `GPUDestinationSnapshotOperation`, `GPUBlendFormulaLibrary`) is untouched.

### 5. Test consumers that pin the per-frame session semantics (re-point inventory for Tasks 4-5)

| Test | pins | disposition |
| --- | --- | --- |
| `GPUPreparedSurfaceProductEntryTest.kt` | evidence `targetCreations = 1, targetCloses = 1, frameCoordinatorCreations = 1` (l.257-259) | re-point (Task 4): first frame on a fresh executor = create+checkin → `targetCloses = 0`; add a session-boundary assertion |
| `GPUPreparedSurfaceProductRouterTest.kt` | fake counter `counterReads == 1 -> Counters(targetCreations = 1)` (l.669), evidence pins (l.558-560, 679-680) | re-point (Task 4): the fake must model cumulative counters with a close-on-read-3 (afterClose) |
| `GPUPreparedTextNoFallbackTest.kt` | evidence pins (l.131-133) | re-point (Task 4) |
| `GPUPreparedSurfaceProductNativeSmokeTest.kt` | evidence pins `targetCreations/targetCloses/frameCoordinatorCreations == 1L` (l.103-105, 251-253, 349) | re-point (Task 4): `targetCloses` → 0 on first frame; keep the pixel assertions |
| `GPUPreparedSurfaceFrameExecutorTest.kt` | per-frame counter fakes + evidence pins (l.140, 1083-1132) | re-point (Task 4): the fake counter function becomes a session-state fake; new reuse/transition cases (Task 5) |
| `GPUClipCoverageSurfaceTest.kt` | constructs `GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)` (l.522-873) | verify-only (fresh executor per test — unaffected); no changes expected |
| `GPUPreparedSurfaceImagePixelTest.kt`, `GPUPreparedSurfaceTextNativeSmokeTest.kt`, `GPUPreparedVerticesRefusalMatrixTest.kt`, `GPUPreparedImageRefusalMatrixTest.kt` | construct executors | verify-only (single frame per executor — unaffected) |
| `SurfaceTest.kt` (kanvas) | `@AfterEach` → `GPUBackendRuntimeFactory.dispose()` (l.15-17) — the churn trigger | KEEP (the crash-class trigger must remain in the tree as the probe); Task 3 must make it safe |

---

## File Map

### New (this plan)
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt` — the Task 1 red: session-reuse contract + dispose/recreate churn probe (Tasks 1, 4, 5).
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeFactoryLifetimeTest.kt` — factory state-machine unit tests with an injectable backend creator (Task 3).
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-10-reusable-prepared-surface-session-evidence.md` — evidence report (Task 6).
- `reports/fp10-lifecycle-map.txt` — the §3 map as a saved before-snapshot (Task 2).

### Modified (this plan)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` — `GPUBackendRuntimeNativeFactory` becomes a synchronized state machine with an explicit generation counter, an internal test seam (`backendCreator`), and a deterministic dispose that waits for registered sessions; `WgpuBackendSession` gains the factory-generation plumbing if the per-session ordinal is replaced (Task 3; verify at HEAD — the ordinal-derived generation at l.1043 may suffice, see Task 3 Step 3).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` — the executor caches one session keyed by `(deviceGeneration, width, height, colorFormat, interpretation)`; per-frame close becomes a checkin; the per-frame counter checks become activity-flag semantics; the evidence gains the invariant reuse counters (Tasks 4, 5).
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt` — only if the executor cache needs explicit lifecycle signals (verify at HEAD; likely untouched — the executor instance already owns the cache and `GPUPreparedSurfaceRuntimeOwner.lock` serializes).
- Tests re-pointed per §5: `GPUPreparedSurfaceProductEntryTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `GPUPreparedTextNoFallbackTest.kt`, `GPUPreparedSurfaceProductNativeSmokeTest.kt`, `GPUPreparedSurfaceFrameExecutorTest.kt` (Tasks 4, 5).
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` — FP-10 → `completed` (Task 6).

### Explicitly NOT touched
- `GPUPreparedSurfaceLegacyAbsenceTest.kt` (16 tokens unchanged), `GPURendererPackageBoundaryTest`, `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`, `GPUPreparedSurfaceNativePreflight.kt`, the destination-read/formula machinery, `gradle/verification-metadata.xml`.

---

## Phase 0 — Crash repro, stress red, lifecycle baseline

### Task 1: Crash repro attempt and the lifetime stress contract (TDD red)

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt`
- Evidence: logs `/tmp/fp10_repro_fqcn.log`, `/tmp/fp10_repro_ordered.log`, `/tmp/fp10_blend_isolated.log` (already captured at plan time — re-run only if HEAD moved).

**Context:** This is the plan's red. The Windows crash class is documented (roadmap §FP-10; §2 above) but does not reproduce on this machine (Metal). The deterministic red is the stress contract: (a) compatible frames must reuse one prepared session — at HEAD every frame creates and closes its own session, so the reuse assertion fails deterministically; (b) repeated `dispose()`/recreate churn must complete every frame without native failure — on Windows this is the AV red, on this machine it pins the churn contract and exercises the exact `Queue.writeBuffer` slab path the crash dumps name.

- [ ] **Step 1: Re-run the ordered repro and record the result**

```bash
./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks --no-daemon 2>&1 | tee /tmp/fp10_repro_fqcn.log
```

Expected on this machine (verified at plan time): `SurfaceTest` 10/10 passed; the blend suite 1,872 passed / up to 2 failed with `failed.surface.prepared.session-close` on random frames (documented environmental flake, FP-09 §17 — do NOT treat as the target failure); **no native AV crash**. On a Windows/NVIDIA-Vulkan host: expect the AV crash in `Queue.writeBuffer`/`materializeFullscreenUniformSlab` — that crash IS the evidence. Record whichever occurs in Task 6's evidence report. Then re-run the isolated class to confirm flake-class behavior:

```bash
./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks --no-daemon 2>&1 | tee /tmp/fp10_blend_isolated.log
```

Expected: BUILD SUCCESSFUL (verified: 1,864/1,864 in 3m48s).

- [ ] **Step 2: Write the failing stress test (red)**

Create `GPUPreparedSurfaceLifetimeStressTest.kt`. The request recipe follows `GPUPreparedSurfaceProductNativeSmokeTest.kt:225-244` (gate-classified `Candidate` + `GPUPreparedSurfaceExecutionRequest`); the GPU-assumption follows `GPUAllApiBlendSurfaceTest.kt:74` (`assumeTrue(GPUBackendRuntimeFactory.createOrNull() != null, ...)`); `finally { GPUBackendRuntimeFactory.dispose() }` follows the blend suite's `@AfterAll` (l.995-998) — but with the AfterAll replaced by a per-test `try/finally` so the churn test controls dispose explicitly. The `rect()` op is `DisplayOp.DrawRect(Rect(0f,0f,64f,64f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)` — a solid-color rect that materializes the fullscreen uniform slab (the crash-site path, §3.4).

```kotlin
package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * FP-10 lifetime stress contract. The native crash class (EXCEPTION_ACCESS_VIOLATION in
 * wgpu_native.dll through Queue.writeBuffer after repeated GPUBackendRuntimeFactory.dispose()
 * /recreate churn — roadmap evidence) is probed with the minimal TDD reproduction: repeated
 * dispose/recreate followed by fullscreen-uniform-slab frames in one JVM. On hosts where the
 * native crash does not fire (this Mac's Metal backend), the assertions pin the FP-10
 * acceptance deterministically: compatible frames reuse one prepared session and every
 * transition closes exactly one session.
 */
class GPUPreparedSurfaceLifetimeStressTest {
    private fun assumeGpu() {
        assumeTrue(
            GPUBackendRuntimeFactory.createOrNull() != null,
            "GPU backend unavailable in current environment",
        )
    }

    private fun lifetimeRequest(width: Int = 64, height: Int = 64): GPUPreparedSurfaceExecutionRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceExecutionRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = listOf(
                    DisplayOp.DrawRect(
                        Rect(0f, 0f, width.toFloat(), height.toFloat()),
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        ClipStack.WideOpen,
                    ),
                ),
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            width = width,
            height = height,
        )
    }

    @Test
    fun `compatible frames reuse one prepared session target across renders`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(1L, first.evidence.targetCreations, "the first frame creates the session target")
            assertEquals(0L, first.evidence.targetCloses, "the session is checked in, not closed, after the first frame")
            val second = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(
                0L, second.evidence.targetCreations,
                "a compatible frame must reuse the prepared session target",
            )
            assertEquals(0L, second.evidence.targetCloses, "a reused frame does not close the session")
            assertEquals(
                1L, second.evidence.frameCoordinatorCreations,
                "each frame still creates exactly one frame-local coordinator",
            )
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `completion only and readback outputs share one session boundary`() {
        assumeGpu()
        try {
            val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
            val completionOnly = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest().copy(
                    output = GPUPreparedSurfaceRequestedOutput.CompletionOnly,
                )),
            )
            assertEquals(1L, completionOnly.evidence.targetCreations)
            val readback = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                executor.execute(lifetimeRequest()),
            )
            assertEquals(
                0L, readback.evidence.targetCreations,
                "a readback frame after a completion-only frame reuses the same session",
            )
            assertEquals(64 * 64 * 4, readback.rgba.size)
        } finally {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `repeated dispose and recreate churn completes every frame without native failure`() {
        assumeGpu()
        repeat(16) { cycle ->
            try {
                val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
                val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
                    executor.execute(lifetimeRequest()),
                    "churn cycle $cycle must render",
                )
                assertEquals(0, result.stateEventCount)
            } finally {
                GPUBackendRuntimeFactory.dispose()
            }
        }
    }
}
```

(Read `GPUPreparedSurfaceEligibility.Candidate`/`GPUPreparedSurfaceColorMapping` at HEAD before editing — the gate-derived `color` field must be the exact `Ready` instance the router uses; if the constructor shape differs from this skeleton, mirror `GPUPreparedSurfaceProductNativeSmokeTest.kt:225-244` verbatim.)

- [ ] **Step 3: Run to verify it fails**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLifetimeStressTest" --no-parallel --console=plain
```

Expected: FAIL — `compatible frames reuse one prepared session target across renders` fails on `targetCreations` (HEAD reports `1L` on the second frame: every frame creates a fresh session) and on `targetCloses` (HEAD closes every frame); `completion only and readback outputs share one session boundary` fails likewise; `repeated dispose and recreate churn` may pass on this host (the crash is host-specific) — that is expected and documented; on a Windows/Vulkan host it reds by native crash.

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt
git commit -m "test(surface): fp10 lifetime stress contract red and crash repro evidence"
```

(Include the repro results from Step 1 as evidence in the commit body; the plan's Context §2 is the saved before-snapshot.)

---

### Task 2: Freeze the lifecycle map and the green baseline

**Files:**
- Create: `reports/fp10-lifecycle-map.txt` (the §3 map as the before-snapshot)
- Evidence only.

**Context:** The §3 lifecycle map is the acceptance oracle: the plan is complete only when (a) the executor's per-frame `prepareSceneFrameSession` + `session.close()` pair is replaced by a session cache, (b) the factory's create/dispose is synchronized and generation-deterministic, (c) the flake-triggering churn is bounded by reuse. Freeze the baseline before any production change.

- [ ] **Step 1: Re-verify the oracle map and save it**

```bash
rg -n "fun createOrNull|fun dispose|NonClosingSession|class GPUPreparedSceneChildRegistry|fun prepareSceneFrameSession|GPUPreparedSceneFrameSession\(|GPUFrameCoordinatorFactory|targetCreations|frameCoordinatorCreations" gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameCoordinator.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt > reports/fp10-lifecycle-map.txt
```

Expected: matches §3 (record any line drift — the diff is evidence).

- [ ] **Step 2: Freeze the green baseline**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceProductRouterTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPUBackendRuntimeContractsTest" --tests "*GPUBackendRuntimePreparedImageCacheLifecycleTest" --tests "*GPUWgpu4kCorePrimitiveFramePoolTest" --no-parallel --console=plain
./gradlew -F off :gpu-renderer:test --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: first two commands BUILD SUCCESSFUL; the third FAILS ONLY on `gpu renderer production source satisfies package boundary rules` (pre-existing, exactly 20 cycle violations — do not modify).

- [ ] **Step 3: Commit**

```bash
git add reports/fp10-lifecycle-map.txt
git commit -m "docs(surface): fp10 lifecycle map and green baseline evidence"
```

---

## Phase 1 — Deterministic backend factory lifetime (the crash-class fix)

### Task 3: Synchronized, generation-deterministic `GPUBackendRuntimeNativeFactory`

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt` (l.917-966)
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNativeFactoryLifetimeTest.kt`

**Context:** At HEAD `createOrNull()` (l.925-947) and `dispose()` (l.950-955) are unsynchronized check-then-act on `sharedInner`, and dispose is unconditional: it closes the device even if a prepared session still holds native work. The close-wait machinery that should protect this ALREADY EXISTS (`GPUPreparedSceneChildRegistry`, l.828-905 — close waits for all child leases; mapping-executor termination is awaited, l.790-830) but the factory neither synchronizes with it nor exposes generation determinism. This task makes the factory a state machine: (1) create/dispose mutual exclusion, (2) an explicit factory-owned generation counter advanced on every post-dispose creation (the per-session `sessionDeviceGeneration(sessionOrdinal)` at l.1043 becomes the source of `NonClosingSession.deviceGeneration`), (3) dispose requests child-session close through the registry and waits for completion before releasing `sharedInner` (verify-then-wire: the `WgpuBackendSession.close()` path already does the waiting — the factory must only serialize against it), (4) an internal test seam so the interleaving is unit-testable without GLFW. No public API change: `GPUBackendRuntimeFactory.createOrNull()/dispose()` (GPUBackendRuntimeContracts.kt:690-693) is unchanged.

- [ ] **Step 1: Write the failing factory tests (red)**

Create `GPUBackendRuntimeNativeFactoryLifetimeTest.kt` in the `gpu-renderer` test source set. It uses the new internal seam exactly as this codebase's other seam tests do (see `GPUBackendRuntimePreparedImageCacheLifecycleTest.kt` for the inert-device pattern — `java.lang.reflect.Proxy` `GPUDevice`s are already used there):

```kotlin
package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUBackendRuntimeNativeFactoryLifetimeTest {

    @Test
    fun `concurrent creates never duplicate the backend session`() {
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val creatorInvocations = AtomicInteger(0)
        val firstSession = AtomicReference<GPUBackendSession?>()
        GPUBackendRuntimeNativeFactory.backendCreator = {
            val invocation = creatorInvocations.incrementAndGet()
            if (invocation == 1) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS), "first creator must be allowed through")
            }
            InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration())
        }
        try {
            val first = Thread {
                firstSession.set(GPUBackendRuntimeNativeFactory.createOrNull())
            }
            first.start()
            assertTrue(entered.await(10, TimeUnit.SECONDS), "the first creator must enter the factory")
            val second = GPUBackendRuntimeNativeFactory.createOrNull()
            gate.countDown()
            first.join(10_000)
            assertNotNull(second, "a concurrent create returns a session")
            assertEquals(1, creatorInvocations.get(), "concurrent creates must share one backend")
            assertEquals(
                firstSession.get()?.deviceGeneration, second.deviceGeneration,
                "both callers receive the same shared device generation",
            )
        } finally {
            gate.countDown()
            GPUBackendRuntimeNativeFactory.dispose()
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }

    @Test
    fun `every post dispose creation advances the device generation exactly once`() {
        GPUBackendRuntimeNativeFactory.backendCreator = {
            InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration())
        }
        try {
            val first = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(first)
            val firstGeneration = first.deviceGeneration.value
            GPUBackendRuntimeNativeFactory.dispose()
            val second = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(second)
            assertEquals(firstGeneration + 1L, second.deviceGeneration.value, "one dispose advances the generation by exactly one")
            GPUBackendRuntimeNativeFactory.dispose()
            val third = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(third)
            assertEquals(firstGeneration + 2L, third.deviceGeneration.value)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }

    @Test
    fun `repeated dispose is idempotent and never leaks the session after recreation`() {
        GPUBackendRuntimeNativeFactory.backendCreator = { InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration()) }
        try {
            repeat(3) { GPUBackendRuntimeNativeFactory.dispose() }
            val created = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(created)
            repeat(3) { GPUBackendRuntimeNativeFactory.dispose() }
        } finally {
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }
}
```

`InertBackendSession` is a minimal `GPUBackendSession` fake exposing `deviceGeneration` and `close()`; see `GPUBackendRuntimeContractsTest.kt` for the existing fake-session conventions. At HEAD the first test fails deterministically (`creatorInvocations == 2`: the unsynchronized check-then-act at l.926 lets the concurrent caller re-enter creation and both callers receive DIFFERENT device generations); the generation and idempotency tests fail by compilation (the seam does not exist).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew -F off :gpu-renderer:test --tests "*GPUBackendRuntimeNativeFactoryLifetimeTest" --no-parallel --console=plain`
Expected: FAIL — `backendCreator`/`nextDeviceGeneration` do not exist at HEAD (compilation error). The race semantics the tests pin are verified at HEAD by reading l.925-955 (unsynchronized check-then-act; `dispose()` can interleave between two concurrent `createOrNull()` callers, handing the second caller a just-closed or double-created backend).

- [ ] **Step 3: Implement the factory state machine**

1. `GPUBackendRuntimeNativeFactory` (l.917-966): synchronize every public function on the object; extract the session creation into `internal var backendCreator: () -> GPUBackendSession? = ::createGlfwBackendSession` (moved from the current l.927-939 body) and keep `internal val defaultBackendCreator` as the original implementation; add `internal val generationCounter = AtomicLong(0L)` and `internal fun nextDeviceGeneration(): GPUDeviceGenerationID`; `createOrNull()` creates under the object lock and stamps the session's generation (verify at HEAD whether `WgpuBackendSession` should take the generation as a constructor parameter — the ordinal derivation at l.1043 is session-local and must be replaced by the factory stamp so two sessions never share a generation across dispose cycles).
2. `dispose()`: under the lock, remove the shutdown hook, close `sharedInner`, set null — but FIRST request the child-session close through the existing registry path and WAIT for teardown completion. Verify at HEAD how `WgpuBackendSession.close()` (l.1710-1714 → `closeRuntimeResources` → `preparedSceneChildren.close()`) reports completion: if it already blocks until all child leases are released and the mapping executor terminated (the registry + `GPUPreparedSceneMappingExecutorCloser` at l.790-830 say yes), then the factory only needs to serialize and must NOT double-close; if a gap is found (e.g. the queue-completion adapter's pending deliveries at GPUQueueCompletionAdapter.kt:449-477), wait on that signal too. Note the exact `file:line` in the commit message.
3. `NonClosingSession` (l.957-965) carries the factory-stamped `deviceGeneration`; the shutdown hook registration (l.940-944) moves inside the lock.
4. Keep `GPUBackendRuntimeFactory` (GPUBackendRuntimeContracts.kt:690-693) unchanged — the public surface is stable.

- [ ] **Step 4: Run to verify green**

```bash
./gradlew -F off :gpu-renderer:test --tests "*GPUBackendRuntimeNativeFactoryLifetimeTest" --tests "*GPUBackendRuntimeNativeSmokeTest" --tests "*GPUBackendRuntimeContractsTest" --tests "*GPUBackendRuntimePreparedImageCacheLifecycleTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL — the three new tests pass (in a GPU environment the smoke tests run their native paths; without GPU they skip), and no pre-existing runtime test regresses.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main gpu-renderer/src/test
git commit -m "fix(gpu): deterministic backend factory lifetime with in flight registration"
```

---

## Phase 2 — Reusable prepared session across compatible frames

### Task 4: The executor caches one session per compatible request

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (execute l.276-412, session ports l.920-1015, evidence l.56-83, finalizeSuccess l.648-775)
- Re-point: `GPUPreparedSurfaceProductEntryTest.kt`, `GPUPreparedSurfaceProductRouterTest.kt`, `GPUPreparedTextNoFallbackTest.kt`, `GPUPreparedSurfaceProductNativeSmokeTest.kt`, `GPUPreparedSurfaceFrameExecutorTest.kt`

**Context:** The executor opens a backend port and a brand-new prepared session per frame, closes the session in the `finally` (l.356-369), and pins per-frame creation in `finalizeSuccess` (l.654-662). All consumers are serialized by `GPUPreparedSurfaceRuntimeOwner.lock` and the executor is a process-wide singleton, so ONE cached session is safe. This task: (1) caches the session + its key; (2) turns the per-frame close into a checkin; (3) converts the per-frame counter evidence to per-frame activity flags. The stress test's first two cases (Task 1) flip green here; the invariant reuse counters surface in Task 5 (their red lives there).

- [ ] **Step 1: Confirm the red is still exactly the stress contract**

Run: `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLifetimeStressTest" --no-parallel --console=plain`
Expected: FAIL on `targetCreations`/`targetCloses` of the second frame (HEAD creates+closes per frame) — the same failures as Task 1 Step 3.

- [ ] **Step 2: Implement the session cache in the executor**

In `GPUPreparedSurfaceFrameExecutor` (l.276-412):

```kotlin
private data class GPUPreparedSurfaceSessionKey(
    val deviceGeneration: Long,
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val colorInterpretation: String,
)

private var cachedKey: GPUPreparedSurfaceSessionKey? = null
private var cachedSession: GPUPreparedSurfaceSessionPort? = null
```

In `execute` (the `try` block that currently opens the backend at l.287):

1. `val backend = backendFactory.open()` — unchanged (cheap wrapper; the DEVICE is not reopened — that is the Task 3 factory's contract).
2. **Move the session preparation out of `executePrepared` (today at l.503-511) into the `execute` block** so the cache owns the session; `executePrepared` receives the already-prepared session as a parameter. After the build is `Ready` and before `executePrepared`, compute `key = GPUPreparedSurfaceSessionKey(backend.deviceGeneration.value, request.width, request.height, request.candidate.color.physicalFormat.value, request.candidate.color.interpretation.name)`. If `key != cachedKey`, close the OLD `cachedSession` FIRST (idempotent — the session state machine at GPUFrameCoordinator.kt:790-806 tolerates an already-closed session), then prepare the new session (the moved l.503-511 body: `backend.prepare(GPUOffscreenTargetRequest(...))`), set `cachedKey`/`cachedSession`, and mark `sessionClosedByFrame = oldSession != null` (the transition closed exactly one old session) and `sessionCreatedByFrame = true` for THIS frame's evidence. If `key == cachedKey`, reuse `cachedSession` with `sessionCreatedByFrame = false`/`sessionClosedByFrame = false`.
3. The `finally` (l.355-391): close the session ONLY when the frame failed (poisoned session — terminal path, `sessionClosedByFrame = true` + `cachedSession = null`) or when the backend port is null (unavailable path); otherwise CHECK IN (keep `cachedSession` open) and still read `postCloseCounters = cachedSession.counters()` for the delta computation. The `backend.close()` (l.381-390) stays.
4. Track `sessionCreatedByFrame`/`sessionClosedByFrame` through `PendingPreparedSuccess`.

In `finalizeSuccess` (l.648-775) — the per-frame checks validate the CURRENT session's cumulative counters (the session this frame used; its own counters never show a close, because the frame's own session is checked in and any closed session is the PREVIOUS one, whose counters are not re-read):

```kotlin
check(pending.beforeSubmit.targetCreations == 1L && pending.beforeSubmit.targetCloses == 0L)   // current session cumulative state
check(pending.afterCompletion.targetCreations == 1L && pending.afterCompletion.targetCloses == 0L)
check(pending.afterClose.targetCreations == 1L && pending.afterClose.targetCloses == 0L)        // the frame's own session is never closed by its frame
check(pending.afterClose.activeNativePayloads == 0)
check(pending.afterClose.outputOwnedNativePayloads == 0)
check(pending.afterClose.quarantinedNativePayloads == 0)
check(delta(pending.beforeSubmit.retentionRegistrations, pending.afterClose.retentionRegistrations) ==
    delta(pending.beforeSubmit.retentionCompletions, pending.afterClose.retentionCompletions))
check(delta(pending.beforeSubmit.distinctRetentionTickets, pending.afterClose.distinctRetentionTickets) == 1)
```

And the evidence fields become flag-derived per-frame activity: `targetCreations = if (pending.sessionCreatedByFrame) 1L else 0L`, `targetCloses = if (pending.sessionClosedByFrame) 1L else 0L` (the closed session is the previous one, tracked by the executor, not by the current session's counters); `frameCoordinatorCreations == 1L` (l.730) stays. Keep every other existing check that is already delta-based (encoders/commandBuffers/submits/readbackCopies/renderPasses/draws/pipelineBinds — they were already computed via `delta()`).

- [ ] **Step 3: Re-point the per-frame pins**

Apply the §5 re-points: every `targetCloses == 1` pin on a single-frame executor becomes `targetCloses == 0` (checkin semantics); `GPUPreparedSurfaceProductRouterTest`'s fake counter function (l.669) becomes a session-state fake returning cumulative counters (create on first read of a new session, close on the afterClose read of a session the executor closes); keep every pixel and route assertion intact. The fake-counter shape in `GPUPreparedSurfaceFrameExecutorTest` (l.1083-1132) models the same session state.

- [ ] **Step 4: Run to verify green**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLifetimeStressTest" --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedTextNoFallbackTest" --tests "*GPUPreparedSurfaceProductNativeSmokeTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL — the two reuse stress cases and all re-pointed suites green; the churn stress case passes on this host (probe). Then run the ordered repro again and compare with Task 1 Step 1:

```bash
./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks --no-daemon 2>&1 | tee /tmp/fp10_repro_after_t4.log
```

Expected: no native crash on any host; on this machine the documented `session-close` flake MAY still appear (environmental — do not weaken anything for it; if it disappears across several re-runs, record that in Task 6 as evidence).

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main kanvas/src/test
git commit -m "feat(surface): reuse prepared scene sessions across compatible frames"
```

---

### Task 5: Deterministic transition matrix and cache reuse counters

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` (the `invariantCounters` evidence field, Step 2)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt` (transition cases)
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecutorTest.kt` (transition cases with a fake session port)

**Context:** The FP-10 acceptance requires generation, size, format, owner, and close transitions to be deterministic. After Task 4 the executor's cache key decides every transition; this task pins the matrix: each transition closes exactly one old session and creates exactly one new one (per-frame delta (1,1)), every other frame is a reuse (delta (0,0)), and the invariant reuse counters grow monotonically within a session. The unit-level matrix uses a fake backend/session port (the `GPUPreparedSurfaceFrameExecutorTest` conventions) so the matrix is host-independent; the native cases extend the stress test.

- [ ] **Step 1: Write the failing transition tests (red)**

In `GPUPreparedSurfaceFrameExecutorTest.kt`, add (against a fake backend whose `deviceGeneration` can be advanced and whose session ports record create/close calls):

```kotlin
@Test
fun `size transition closes the old session and creates exactly one new session`() {
    // executor.execute(request(64, 64)) then executor.execute(request(32, 32)):
    // frame 1 evidence = (targetCreations 1, targetCloses 0, frameCoordinatorCreations 1)
    // frame 2 evidence = (targetCreations 1, targetCloses 1, frameCoordinatorCreations 1)
    // the fake port records: prepare called twice, first session closed exactly once
}

@Test
fun `format transition closes the old session and creates exactly one new session`() {
    // RGBA8 request then BGRA8 request on the same executor (mirror the gate's
    // GPUPreparedSurfaceColorMapping Ready cases)
}

@Test
fun `device generation transition closes the stale session before creating the new one`() {
    // advance the fake backend's deviceGeneration between the two executes;
    // the second frame must close the stale session and prepare on the new generation
}

@Test
fun `owner transition creates a fresh session on a new executor instance`() {
    // executor A renders once; executor B (a second owner) renders once;
    // B's first frame evidence = (1, 0) and A's session is untouched
}

@Test
fun `close transition after dispose completes a subsequent frame on a new generation`() {
    // executor renders; fake factory disposes (closes the session); executor renders again
    // with the advanced generation: the second frame completes with (1, 1) — stale closed, new created
}
```

In `GPUPreparedSurfaceLifetimeStressTest.kt`, extend the native probe with the two observable native transitions:

```kotlin
@Test
fun `size transition is deterministic and reuses after the transition`() {
    assumeGpu()
    try {
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest(width = 64, height = 64)))
        assertEquals(1L, first.evidence.targetCreations)
        val transition = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest(width = 32, height = 32)))
        assertEquals(1L, transition.evidence.targetCreations, "size change creates a new session target")
        assertEquals(1L, transition.evidence.targetCloses, "size change closes the old session exactly once")
        val after = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest(width = 32, height = 32)))
        assertEquals(0L, after.evidence.targetCreations, "the new size is reused after the transition")
        assertEquals(0L, after.evidence.targetCloses)
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

@Test
fun `dispose between frames advances the generation and reuses the new session`() {
    assumeGpu()
    try {
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
        val before = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        GPUBackendRuntimeFactory.dispose()
        val after = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        assertEquals(1L, after.evidence.targetCreations, "a disposed backend reopens one fresh session")
        assertEquals(1L, after.evidence.targetCloses, "the stale session is closed exactly once")
        val reused = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        assertEquals(0L, reused.evidence.targetCreations, "the reopened session is reused")
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

@Test
fun `cache creation and reuse counters grow monotonically within one session`() {
    assumeGpu()
    try {
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory)
        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        val third = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(lifetimeRequest()))
        // the invariant counters evidence group (surfaced by this task's Step 2):
        // - corePrimitive invariant creations on frame 1 > 0
        // - corePrimitive invariant creations on frame 3 == 0 (reused pipelines)
        // - corePrimitive invariant reuses on frame 3 > 0
        // (exact field names from GPUPreparedSceneNativeCounters at GPUFrameCoordinator.kt:312-363)
        assertTrue(first.evidence.invariantCounters.corePrimitiveCreations > 0L)
        assertEquals(0L, third.evidence.invariantCounters.corePrimitiveCreations)
        assertTrue(third.evidence.invariantCounters.corePrimitiveReuses > 0L)
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}
```

- [ ] **Step 2: Surface the invariant reuse counters in the evidence (red by compilation)**

Add to `GPUPreparedSurfaceExecutionEvidence` (GPUPreparedSurfaceFrameExecution.kt:56-83) a grouped counter field:

```kotlin
val invariantCounters: GPUPreparedSceneInvariantCounterDeltas = GPUPreparedSceneInvariantCounterDeltas(),
```

where `GPUPreparedSceneInvariantCounterDeltas` is a small data class carrying per-frame deltas of: `solidRectCreations/Reuses`, `corePrimitiveCreations/Reuses`, `registeredUniformCreations/Reuses`, `separableBlurCreations/Reuses`, `destinationSnapshotCreations/Reuses`, `colorGlyphAtlasReuses`, and the pool slot counters (`coverageMaskSlotReuses`, `msaaColorSlotReuses`, `pathDepthStencilSlotReuses`, `clipDepthStencilSlotReuses` — see GPUFrameCoordinator.kt:1613-1623). Populate from `delta(pending.beforeSubmit.<counter>, pending.afterCompletion.<counter>)` for each field that exists on `GPUPreparedSceneNativeCounters` (the `copy()` at GPUFrameCoordinator.kt:367-410 is the authoritative field list). Run `./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLifetimeStressTest" --no-parallel --console=plain` — expected FAIL: the counters test cannot compile against `invariantCounters`.

- [ ] **Step 3: Run to verify the full red set**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedSurfaceLifetimeStressTest" --no-parallel --console=plain
```

Expected: FAIL — the counters test fails by compilation (Step 2 red); the unit matrix tests may already pass after Task 4's cache (the executor behavior exists); the native transition cases (size/dispose) should pass after Task 4 — if so, they stay as regression pins and only the counters red drives this task's production work. Record what is red vs already-green; do NOT weaken any assertion.

- [ ] **Step 4: Make the matrix deterministic (verify-then-wire)**

Verify the executor key against the matrix: the key already carries generation/size/format (Task 4 Step 2); interpretation is the fifth axis (the two RGBA ready-mappings at GPUPreparedSurfaceColorMapping — `RGBA8Unorm`/`RGBA8UnormSrgb` — must NOT collide in the key). If any matrix row breaks (e.g. the color `Ready` instance differs between otherwise-identical requests), fix the key construction, not the test. Confirm the invariant counters' delta computation reports zeros on the creating frame's reuse fields and positive reuse on subsequent frames — if a counter source is session-cumulative, keep the delta machinery; do not weaken the assertion.

- [ ] **Step 5: Run to verify green**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceFrameExecutorTest" --tests "*GPUPreparedSurfaceLifetimeStressTest" --tests "*GPUPreparedSurfaceProductEntryTest" --tests "*GPUPreparedSurfaceProductRouterTest" --tests "*GPUPreparedSurfaceProductNativeSmokeTest" --tests "*GPUPreparedTextNoFallbackTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
```

Expected: BUILD SUCCESSFUL (the blend suite green in isolation — 1,864/1,864 on this host).

- [ ] **Step 6: Commit**

```bash
git add kanvas/src/main kanvas/src/test
git commit -m "feat(surface): deterministic session transitions and invariant reuse counters"
```

---

## Phase 3 — Regression proof & closure

### Task 6: Full regression, stress green, evidence report, roadmap FP-10 completed

**Files:**
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` (FP-10 → `completed`)
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-10-reusable-prepared-surface-session-evidence.md`
- Evidence: `/tmp/fp10_repro_fqcn.log`, `/tmp/fp10_repro_after_t4.log` (re-run), `/tmp/fp10_full.log`

**Context:** FP-10 acceptance: repeated frames reuse the backend and prepared session (Tasks 3-4); generation/size/format/owner/close transitions deterministic (Task 5); completion-only and readback share one session boundary (Task 4 + stress case); cache creation/reuse counters and lifetime tests pass (Tasks 1, 5). The Task 1 red is now green, and the crash-class probe ran before (no AV on this host; the ordered-repro red run + Windows roadmap evidence recorded).

- [ ] **Step 1: Run the full regression**

```bash
./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain 2>&1 | tee /tmp/fp10_full.log
```

Expected: BUILD SUCCESSFUL except the two documented pre-existing failures — `GPURendererPackageBoundaryTest` package-boundary case (exactly 20 cycle violations, 0 rule violations; unchanged) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` (reproduces at base SHA). The `failed.surface.prepared.session-close` flake may land on a random frame under churn (FP-09 §17, environment-dependent) — classify any such failure with evidence (run the affected class in isolation to confirm); do NOT weaken assertions.

- [ ] **Step 2: Verify the stress contract and the repro after the fix**

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceLifetimeStressTest" --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --no-parallel --console=plain --rerun-tasks --no-daemon 2>&1 | tee /tmp/fp10_repro_after_t4.log
./gradlew -F off :gpu-renderer:test --tests "*GPUBackendRuntimeNativeFactoryLifetimeTest" --tests "*GPURendererPackageBoundaryTest" --no-parallel --console=plain
```

Expected: stress suite green; the ordered repro completes without a native crash (on this host: SurfaceTest 10/10 + blend suite green or at most the documented flake); the factory lifetime suite green; the boundary case in its unchanged pre-existing failure state.

- [ ] **Step 3: Write the evidence report**

In `fp-10-reusable-prepared-surface-session-evidence.md`: the repro table (Windows roadmap evidence vs this machine's ordered-run/isolated-run results — §2 of this plan, with the before/after of the ordered repro); the lifecycle map before/after (Task 2 snapshot vs the post-Task-4 executor: per-frame `prepareSceneFrameSession`+close → one cached session + checkin); the crash-site chain (§3.4) and what the fix changed around it (factory synchronization + generation stamp; executor no longer churns sessions); the transition matrix results (Task 5: each axis's per-frame deltas); the invariant counter evidence (Task 5 reuse growth across frames in one session); the stress-test scores; the flake note (session-close remains documented environmental; observed/not-observed during this FP's runs); test score deltas (before/after of the full run).

- [ ] **Step 4: Update the roadmap**

In `active-todo.md`, mark FP-10 `completed` with the evidence report reference:

```markdown
### FP-10 — Reusable prepared Surface session

Status: `completed`

Resolution evidence (`fp-10-reusable-prepared-surface-session-evidence.md`):
- the backend factory is a synchronized state machine: create/dispose mutual exclusion,
  explicit per-dispose device-generation stamping, idempotent dispose that waits for
  registered prepared-session children (the existing `GPUPreparedSceneChildRegistry`
  close-wait) before releasing the shared device — the `EXCEPTION_ACCESS_VIOLATION`
  lifetime/recreation failure class (Queue.writeBuffer + materializeFullscreenUniformSlab
  after `GPUBackendRuntimeFactory.dispose()` churn) is closed by construction;
- the process-wide executor caches one prepared scene session keyed by
  (deviceGeneration, size, format, interpretation): compatible frames reuse the target,
  the invariant pipeline caches, and the frame-local pools (creation/reuse counters
  surfaced in the executor evidence), and completion-only + readback outputs share the
  same session boundary;
- generation/size/format/owner/close transitions are deterministic — each closes exactly
  one old session and creates exactly one new one, pinned by a transition matrix and the
  `GPUPreparedSurfaceLifetimeStressTest` (session reuse, output-sharing, churn probe);
- full run: `:kanvas:test`/`:gpu-renderer:test` green except the two documented
  pre-existing failures (package boundary, stencil smoke); the `failed.surface.prepared.session-close`
  flake remains documented environmental (FP-09 evidence §17).
```

- [ ] **Step 5: Final state check**

```bash
git add reports/ kanvas/src/main kanvas/src/test gpu-renderer/src/main gpu-renderer/src/test
git log --oneline a335b9a7d..HEAD | cat
git status --short
```

Expected: the log shows the FP-10 task commits (stress red → lifecycle map → factory lifetime → session reuse → transition matrix → evidence closure); `git status --short` shows only the intended files.

- [ ] **Step 6: Commit**

```bash
git add reports/ kanvas/src/test kanvas/src/main gpu-renderer/src/main gpu-renderer/src/test
git commit -m "docs(surface): fp10 reusable prepared session evidence and roadmap closure"
```

---

## Self-review notes (filled at plan time, 2026-08-11)

**Spec coverage vs. the roadmap FP-10 entry and the mission:**

1. **SCOPE: FP-10 is a native lifetime bug + architectural acceptance, NOT a family migration.** The roadmap's `current evidence` and `acceptance` were treated as the authority: every task maps to one acceptance line — repeated frames reuse the backend/session (Tasks 3-4), deterministic transitions (Task 5), shared completion/readback boundary (Task 4 + stress case), counters/lifetime tests (Tasks 1, 4, 5, 6). No family-migration framing was invented; the FP-09-style "precondition coverage" phases do not apply.
2. **Repro outcome on this machine (mission question "repro fiable ou non"): NOT reliable — documented with evidence.** The ordered repro (roadmap shape, FQCNs) fails on this Mac with the documented environmental `failed.surface.prepared.session-close` flake on 2 random DST frames (1,872 passed), passes 1,946/1,946 in the glob variant and 1,864/1,864 isolated — and never crashes natively (Metal). The Windows AV evidence (roadmap + `hs_err_pid18980.log`) is reproduced verbatim as the crash class. The plan therefore makes the crash probe (16× dispose/recreate + slab frames) a native-only probe and the DETERMINISTIC red is the reuse contract (second-frame `targetCreations == 0`), which fails at HEAD on every GPU host — satisfying "un test de contrainte de lifetime qui attrape la classe de bug même sans crash natif".
3. **Added vs the roadmap evidence (verified at HEAD):** the per-frame session stack inventory (§3.2: target + 10 caches + encoding backend + readback mapper + child teardown per `prepareSceneFrameSession`), the crash-site chain (§3.4: `writeTrackedBuffer`/`recordFullscreenUniformPass`/`materializeFullscreenUniformSlab`/`materializeFullscreenUniformSlabLease`), the discovery that `GPUPreparedSceneChildRegistry` (l.828-905) ALREADY implements the close-wait that the factory never uses (Task 3 is therefore "synchronize + stamp + wait", not "invent a registry"), and the discovery that the invariant creation/reuse counters already exist on `GPUPreparedSceneNativeCounters` but are not surfaced in the executor evidence (Task 5 Step 2).
4. **Removed vs a naive "reuse everything" reading:** the device is ALREADY process-shared (`sharedInner`); "one device per render" is false at the device level and true at the prepared-session level — the plan targets the session stack, not the device. `GPUPreparedSurfaceProductEntry` is left untouched (the lock + process-wide executor make the executor cache safe); `GPURenderer.kt` is untouched (the singleton port already exists).
5. **The flake discipline:** the `failed.surface.prepared.session-close` flake (FP-09 §15/§17) is treated as environmental and NEVER papered over — no assertion is weakened for it; its absence/persistence after the fix is recorded as evidence, not asserted (it is host-dependent).
6. **Untouched baselines:** `GPURendererPackageBoundaryTest` (20 cycles, 0 violations) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` stay in their documented pre-existing failing states. `GPUPreparedSurfaceLegacyAbsenceTest` (16 tokens) is untouched. The destination-read/formula machinery and the session validator codes are untouched.
7. **Honest discovery points (no placeholders, but verify-then-wire, FP-09 style):** Task 3's exact dispose-wait wiring (the registry path at `WgpuBackendSession.close()`/`preparedSceneChildren.close()` vs the queue-completion adapter) and the factory-generation plumbing (whether `WgpuBackendSession` takes the stamped generation as a constructor parameter); Task 4's `Candidate.color` construction shape (mirror `GPUPreparedSurfaceProductNativeSmokeTest.kt:225-244`); Task 5 Step 2's exact counter field names (the `copy()` at GPUFrameCoordinator.kt:367-410 is authoritative). Each is pinned by the observable tests in the task; a residual discovered at execution time is documented in the Task 6 evidence run, never hidden.
8. **Commit hygiene:** Task 4 commits the production change together with its test re-points (the tree stays green at every commit); the Task 1 red test commit is intentionally red (TDD) and isolated; Task 3's commit is gpu-renderer-only.

**Deliverable mapping (mission items (1)-(7)):** (1) repro minimale + evidence → Task 1 (ordered repro attempted on this machine, Windows evidence documented, stress contract red); (2) cartographie des cycles de vie → Task 2 + Context §3; (3) consolidation backend/session réutilisable → Tasks 3-4; (4) déterminisme des transitions → Task 5; (5) session boundary completion/readback partagée → Task 4 + stress case; (6) counters cache + tests lifetime → Tasks 1, 5, 6; (7) preuves de régression + roadmap FP-10 completed → Task 6.
