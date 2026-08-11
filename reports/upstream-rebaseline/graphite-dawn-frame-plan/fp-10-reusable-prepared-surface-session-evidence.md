# FP-10 Reusable Prepared Surface Session — Closure Evidence

Status: **final** — Task 6 closure (full regression, stress green, evidence report, roadmap FP-10 completed).

Branch: `codex/graphite-dawn-frame-fp10`, HEAD `fcb1798c9` (Task 5); this closure commit adds the Task 6 evidence and the roadmap update.

## 1. The repro table (Windows roadmap evidence vs this machine)

| row | host / run | result |
| --- | --- | --- |
| Windows roadmap | full `:kanvas:test` reproduces an `EXCEPTION_ACCESS_VIOLATION` in `wgpu_native.dll` through `Queue.writeBuffer` and `WgpuRenderRecorder.materializeFullscreenUniformSlab` after repeated `GPUBackendRuntimeFactory.dispose()`/recreate churn | AV (crash class, not reproducible on this Metal host) |
| Windows roadmap | ordered repro `.\gradlew.bat :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" --dependency-verification=off --no-daemon --console=plain --rerun-tasks` passes `SurfaceTest` 10/10, then the blend worker crashes in `Queue.writeBuffer` | AV (crash class) |
| Windows roadmap | `gpu-renderer/hs_err_pid18980.log` (FP-03 aggregate): `EXCEPTION_ACCESS_VIOLATION` through `nvoglv64.dll` (Vulkan) and `wgpu_native.dll` while `GPUWgpu4kSolidRectFrameSmokeTest` called `Adapter.requestDevice` after earlier native session/resource teardown in the same worker | AV (second trigger location of the same lifetime/recreation failure class) |
| this machine, plan time | ordered repro (FQCNs), plan-time capture: `SurfaceTest` 10/10, then blend suite 1,872 passed / **2 failed with `failed.surface.prepared.session-close`** on `DrawDRRect/DST/UNCLIPPED` and `DrawVertices/DST/UNCLIPPED` — the documented environmental flake (FP-09 evidence §17), no native crash | 1,872 + 2 flake failures, **no AV** |
| this machine, Task 1 | ordered repro re-run (committed in `f6eb51ec4`, log `/tmp/fp10_repro_fqcn.log`): `SurfaceTest` 10/10 + blend 1,864/1,864 = **1,874/1,874**, BUILD SUCCESSFUL in 4m23s, no flake this run | green, **no AV** |
| this machine, plan time | glob variant `--tests "*SurfaceTest" --tests "*GPUAllApiBlendSurfaceTest"` (log `/tmp/fp10_repro_ordered.log`): **1,946/1,946**, BUILD SUCCESSFUL in 4m24s | green |
| this machine, plan time | isolated blend class (log `/tmp/fp10_blend_isolated.log`): **1,864/1,864**, BUILD SUCCESSFUL in 3m48s — the two ordered-run failures were the documented flake class, green in isolation | green |
| this machine, Task 6 | **AFTER-fix ordered repro re-run** (Task 6 Step 2, log `/tmp/fp10_repro_after_t4.log`): `SurfaceTest` 10/10 + blend 1,864/1,864 = **1,874/1,874**, BUILD SUCCESSFUL in 4m16s, **no flake observed**, no native crash | green after fix |

Flake classification: the two plan-time ordered-run failures were the documented environmental
`failed.surface.prepared.session-close` flake (FP-09 evidence §17 — lands on a different random
non-dst-read frame under GPU churn, green in isolation), confirmed by the 1,864/1,864 isolated
blend run. No assertion was weakened for it. During FP-10's own runs (Task 1 re-run, the after-T4
evidence, and the Task 6 full regression + ordered re-run) the flake was **not observed**; the
flake note and classification are retained in §8.

## 2. Lifecycle map before/after

**Before (Task 2 snapshot, `reports/fp10-lifecycle-map.txt`, committed at `2886fb1a5`):**

- the process-global `GPUBackendRuntimeNativeFactory` was an unsynchronized check-then-act on
  `sharedInner` (`createOrNull()` GPUBackendRuntimeNative.kt:925-947, `dispose()` :950-955);
  every render still tore down and recreated the whole prepared session stack, and
  `SurfaceTest.@AfterEach` → `GPUBackendRuntimeFactory.dispose()` churned the shared device
  close/recreate repeatedly (the documented predecessor of the Windows AV);
- `WgpuBackendSession.prepareSceneFrameSession` (l.1292-~1630) created, for EVERY call, a new
  prepared target, **10 session caches** (solidRect, corePrimitive, colorGlyph,
  registeredUniformRect, separableBlurRect, destinationCopy, maskBlur, surfaceBlit,
  preparedImage, preparedText), the encoding backend, the readback mapping executor + mapper,
  and the child teardown — plus a fresh `GPUFrameCoordinator` per frame;
- the executor closed the session every frame: `finalizeSuccess` pinned per-frame
  `targetCreations == 1L && targetCloses == 1L` (GPUPreparedSurfaceFrameExecution.kt:654-656)
  and `frameCoordinatorCreations == 1L` (:730).

**After (post-Task-4 executor, HEAD):**

- the executor caches **one** prepared scene session keyed by
  `GPUPreparedSurfaceSessionKey(deviceGeneration, width, height, colorFormat, colorInterpretation)`
  (GPUPreparedSurfaceFrameExecution.kt:301-307; `cachedKey`/`cachedSession` :319-320);
- compatible frames reuse the cached session; the per-frame `finally` becomes a **checkin**
  (post-frame counters read at checkin :487-512; the cached session stays open), and a session
  is closed+evicted only when the frame failed (poisoned session) or the key changed;
- the evidence became per-frame activity flags: `targetCreations = if (sessionCreatedByFrame) 1L else 0L`,
  `targetCloses = if (sessionClosedByFrame) 1L else 0L` (:812-813); every frame still creates
  exactly one frame-local coordinator (`frameCoordinatorCreations == 1L` check retained);
- the per-frame `prepareSceneFrameSession`+close pair is replaced by: first frame = create+checkin
  (1,0), compatible frame = reuse (0,0), transition = close-old-then-prepare-new (1,1) — pinned by
  the re-pointed suites (`GPUPreparedSurfaceProductEntryTest`, `GPUPreparedSurfaceProductRouterTest`,
  `GPUPreparedTextNoFallbackTest`, `GPUPreparedSurfaceProductNativeSmokeTest`,
  `GPUPreparedSurfaceFrameExecutorTest`) and the `GPUPreparedSurfaceLifetimeStressTest`.

## 3. The crash-site chain and what the fix changed around it

**The chain (unchanged, §3.4 of the plan):**

- `WgpuRenderRecorder.writeTrackedBuffer` (GPUBackendRuntimeNative.kt:3264-3268) =
  `queue.writeBuffer(buffer, offset, data)` — the AV crash line;
- `drawFullscreenPass`/`drawFullscreenUniformPayloadPass`/`drawFullscreenRawUniformPass`
  (:3279/:3307/:3335) → `recordFullscreenUniformPass` (:4771-4813) →
  `materializeFullscreenUniformSlab` (:5034-5140): `createTrackedBuffer` (:5122-5132) +
  `materializeFullscreenUniformSlabLease` (GPUConcreteResourceProvider.kt:706-772) + the upload
  write.

**What the fix changed around it (Task 3, commit `3a7947357`):**

- `GPUBackendRuntimeNativeFactory` is now a **synchronized state machine**: `createOrNull()` and
  `dispose()` both run under the object lock (GPUBackendRuntimeNative.kt:950, :972) — mutual
  exclusion between create and dispose (the unsynchronized check-then-act race is closed);
- an explicit factory-owned **device-generation counter** (`generationCounter`, :920;
  `nextDeviceGeneration()` :929-930) stamps every session at creation
  (`WgpuBackendSession(glfw, nextDeviceGeneration())`, :941) — a fresh generation per
  post-dispose creation, so two sessions never share a generation across dispose cycles;
- **idempotent dispose** removes the shutdown hook and closes `sharedInner` under the lock
  (:972-986); the close path waits for registered prepared-session children through the existing
  `GPUPreparedSceneChildRegistry` close-wait (lease drain + `claimTeardownIfReady`) and the
  mapping-executor closer. Dispose-wait verification recorded in the commit message:
  `WgpuBackendSession.close` (GPUBackendRuntimeNative.kt:1734) → `GPUPreparedSceneChildRegistry.close`
  (:872, lease-drain :901) → teardown gate (:598, :653) → mapping-executor closer (:785);
  the queue-completion adapter has no gap (GPUQueueCompletionAdapter.kt:369, 447-466) — the
  factory only serializes, it does not double-close;
- the **executor no longer churns sessions**: after Task 4 the session is checked in per frame and
  reopened only on a key transition, so repeated `dispose()`/recreate followed by
  fullscreen-uniform-slab frames (the minimal TDD reproduction) no longer exercises the
  write-after-teardown interleaving on any host; the crash class is closed by construction on the
  factory side and by session reuse on the executor side;
- the public surface is unchanged: `GPUBackendRuntimeFactory.createOrNull()/dispose()`
  (GPUBackendRuntimeContracts.kt) is stable, `NonClosingSession` carries the factory-stamped
  generation, and the factory behavior is unit-pinned by
  `GPUBackendRuntimeNativeFactoryLifetimeTest` (3/3, §6) with the injectable `backendCreator`
  seam.

## 4. Transition matrix results (Task 5)

Unit matrix (host-independent, fake backend/session port in
`GPUPreparedSurfaceFrameExecutorTest`, 28 tests incl. the five transition cases — deltas are
per-frame `(targetCreations, targetCloses)`):

| axis | frames | per-frame deltas | fake-port facts |
| --- | --- | --- | --- |
| size | 64×64 → 32×32 → 32×32 | (1,0) → (1,1) → (0,0) | 2 prepares; old session closed exactly once; new checked in; widths `[64, 32]` |
| format | RGBA8 → BGRA8 | (1,0) → (1,1) | 2 prepares; old closed exactly once; requests prepared on `RGBA8UnormSrgb` then `BGRA8Unorm`/`EncodedPremulSrgb` |
| device generation | gen 91 → gen 92 | (1,0) → (1,1) | stale-generation session closed exactly once; new session prepared on gen 92 (`prepareGenerations == [91, 92]`) |
| owner | executor A then executor B | A first frame (1,0); B first frame (1,0) | A's session untouched by B (closeCalls 0, submitCalls 1); B prepares its own session |
| close (dispose between frames) | frame → dispose+advance gen → frame | (1,0) → (1,1) | disposed session closed again idempotently by the executor (2 close calls on the old session, matching the native state machine's idempotent close); reopened session checked in |

Native matrix (in `GPUPreparedSurfaceLifetimeStressTest`, both green):

- `size transition is deterministic and reuses after the transition`: 64×64 → 32×32 → 32×32
  yields (1,0) → (1,1) → (0,0) with `targetCreations`/`targetCloses` assertions;
- `dispose between frames advances the generation and reuses the new session`:
  frame → `GPUBackendRuntimeFactory.dispose()` → frame → frame yields
  (1,0) → (1,1) → (0,0) — a disposed backend reopens one fresh session and the stale session is
  closed exactly once.

Every transition closes exactly one old session and creates exactly one new one; every other
frame is a reuse (0,0).

## 5. Invariant counter evidence (Task 5)

`GPUPreparedSurfaceExecutionEvidence` now carries `invariantCounters:
GPUPreparedSceneInvariantCounterDeltas` — per-frame deltas of the session-scoped invariant cache
counters, computed between the session's before-submit and after-completion counter reads
(GPUPreparedSurfaceFrameExecution.kt:885-933). A creating frame reports positive creations and
zero reuses; a compatible later frame reports zero creations and positive reuses.

Native evidence (`GPUPreparedSurfaceLifetimeStressTest.cache creation and reuse counters grow
monotonically within one session`):

- frame 1: `corePrimitiveCreations > 0` (the invariants are created);
- frame 3 (same session, no transition): `corePrimitiveCreations == 0` and
  `corePrimitiveReuses > 0` — the session's core-primitive invariants are reused.

Unit evidence (size-transition case, `GPUPreparedSurfaceFrameExecutorTest`): the transition
frame reports `corePrimitiveCreations == 1, corePrimitiveReuses == 0`; the following frame of the
same session reports `corePrimitiveCreations == 0, corePrimitiveReuses > 0` — the delta
machinery reports zeros on a creating frame's reuse fields and positive reuse only on
subsequent frames of the same session.

Surfaced fields: `solidRect*`, `corePrimitive*`, `registeredUniform*`, `separableBlur*`,
`destinationSnapshot*` creations/reuses, and `colorGlyphAtlasReuses`. The pool slot counters
(`coverageMaskSlotReuses`, `msaaColorSlotReuses`, `pathDepthStencilSlotReuses`,
`clipDepthStencilSlotReuses`) are not surfaced: they live on `GPUPreparedSceneRenderCounters`,
which the executor's session port does not expose (documented in the KDoc at
GPUPreparedSurfaceFrameExecution.kt:56-63).

## 6. Stress-test scores and factory lifetime suite

- `GPUPreparedSurfaceLifetimeStressTest`: **6/6 green** (Task 6 Step 2, `--rerun`) —
  session reuse, completion-only+readback shared boundary, 16× dispose/recreate churn probe,
  size transition, dispose-between-frames transition, invariant counter monotonicity. 0 failures,
  0 skipped; test time 6.04s.
- `GPUBackendRuntimeNativeFactoryLifetimeTest`: **3/3 green** — concurrent creates never
  duplicate the backend session (creator invoked exactly once, both callers receive the same
  generation), every post-dispose creation advances the device generation exactly once, repeated
  dispose is idempotent and never leaks the session after recreation.
- `GPURendererPackageBoundaryTest`: 22 tests, **1 failed** — the pre-existing package-boundary
  case in its unchanged state (exactly 20 cycle violations, 0 rule violations, §7).

## 7. Full-run regression proof (Task 6 Step 1) and test-score deltas

Command: `./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain`
(log `/tmp/fp10_full.log`; warm daemon, compile tasks up-to-date — run time 3m45s).

| module | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `:kanvas:test` | **3,229** | **0** | 0 | 0 |
| `:gpu-renderer:test` | **3,291** | **2** | 0 | 0 |

The two gpu-renderer failures are exactly the documented pre-existing failures, unchanged:

1. `GPURendererPackageBoundaryTest.gpu renderer production source satisfies package boundary rules`
   — package cycle violation list: **exactly 20 cycle violations, 0 rule violations** (verified by
   counting the failure message; unchanged from the FP-08/FP-09 baselines);
2. `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest.native clip stencil AA 4x retains color and
   stencil across three passes and reuses its pair` — `diagonal 4x clip edge must contain one
   premultiplied partial red pixel` (`assertPartialPremultipliedRedEdge`), reproduces at base SHA.

Deltas vs the FP-09 close (a335b9a7d, FP-09 evidence §15: `:kanvas:test` 3,210/3,210;
`:gpu-renderer:test` 3,273 with the same two pre-existing failures):

- `:kanvas:test` 3,210 → 3,229 (**+19 net**, measured): the new FP-10 suites —
  `GPUPreparedSurfaceLifetimeStressTest` (6), the transition matrix cases in
  `GPUPreparedSurfaceFrameExecutorTest` (+5), the retained-session text coverage in
  `GPUPreparedSurfaceFrameBuilderTextTest` (+5), plus re-point/coverage additions in the
  executor/entry/router/smoke suites;
- `:gpu-renderer:test` 3,273 → 3,291 (**+18 net**, measured): `GPUBackendRuntimeNativeFactoryLifetimeTest`
  (3), the mixed masked-text preflight case (+1), and the Task 4/5 materializer/pool additions;
- both modules' failure sets are unchanged (0 and 2, the two pre-existing). The per-suite
  attributions above are the FP-10-added test methods (`git diff a335b9a7d..HEAD`: kanvas +16
  `@Test` methods, gpu-renderer +4); the small residual of the net deltas comes from test-count
  drift between the FP-09 evidence capture and the merged FP-09 tip.

`GPUPreparedSurfaceLegacyAbsenceTest` remains green (16 retired tokens unchanged — no FP-10
legacy tokens).

## 8. Flake note

`failed.surface.prepared.session-close` remains documented as environmental (FP-09 evidence §17:
lands on a different random non-dst-read frame under GPU churn, green in isolation). During this
FP's runs:

- observed only at plan time (ordered repro: 2 DST frames, confirmed flake-class by the
  1,864/1,864 isolated run; §1);
- **not observed** in any post-fix FP-10 run: the Task 1 re-run (1,874/1,874), the after-T4
  evidence, the Task 6 full regression (kanvas 3,229 green), the Task 6 stress run, and the
  Task 6 ordered re-run (1,874/1,874) all completed with zero `failed.surface.prepared.session-close`
  occurrences.

Whether the deterministic session lifecycle contributed to its absence is recorded here as
evidence only — the flake remains environment-dependent and was NOT asserted away (no assertion
was weakened for it anywhere in FP-10). Two-GPU environment context (Intel UHD Graphics 630 +
AMD Radeon Pro 5500M, wgpu adapter selection between the two Metal GPUs untracked) is documented
in FP-09 evidence §17.

## 9. Task 5 review minors (recorded for evidence)

- **duplicate `destinationSnapshotCreations` surface without cross-check** — the counter is
  surfaced both as a top-level `GPUPreparedSurfaceExecutionEvidence.destinationSnapshotCreations`
  field and inside `invariantCounters.destinationSnapshotCreations`, computed from the same
  before-submit/after-completion reads; no assertion cross-checks the two surfaces against each
  other. Accepted as-is (both surfaces are deltas of the same session counter); a future
  consolidation can keep one authority.
- **invalidations counters not surfaced** — `GPUPreparedSceneNativeCounters` carries
  `*InvariantInvalidations` counters, but `GPUPreparedSceneInvariantCounterDeltas` does not
  expose them (the executor session port exposes the counters snapshot it already reads; the
  invalidation fields were not part of the plan's surfacing list). The pool slot counters are
  likewise not surfaced (they live on `GPUPreparedSceneRenderCounters`; §5).
- **interpretation axis currently 1:1 coupled** — the session key carries
  `colorInterpretation` independently, but `GPUPreparedSurfaceColorMapping` maps each physical
  format to exactly one interpretation today (RGBA8 `LinearPremul`, BGRA8 `EncodedPremulSrgb`),
  so the axis cannot vary independently of the format axis in the current gate; the key keeps the
  axis for forward compatibility and the interpretation is asserted on prepared requests
  (format-transition case).

## 10. Commit trail (FP-10)

`f6eb51ec4` (Task 1: stress contract red + crash repro evidence) · `2886fb1a5` (Task 2: lifecycle
map + green baseline evidence) · `3a7947357` (Task 3: deterministic backend factory lifetime with
in-flight registration) · `1258d2bd2` (Task 4: reuse prepared scene sessions across compatible
frames) · `9007ce530` (fix: clear scene target before destination copy on retained sessions) ·
`f526c006d` (fix: harden fp10 session reuse against review findings) · `fcb1798c9` (Task 5:
deterministic session transitions and invariant reuse counters) · **this commit** (Task 6: full
regression proof + stress green + evidence report + roadmap FP-10 completed).

Task 6 runs at HEAD `fcb1798c9`, 2026-08-12, on the Intel UHD Graphics 630 + AMD Radeon Pro 5500M
Mac (Metal backend): full regression (§7), stress 6/6 + factory lifetime 3/3 + boundary unchanged
(§6), AFTER-fix ordered repro 1,874/1,874 with no flake and no native crash (§1).

## 11. Known residual (final review, 2026-08-12)

**Mask-blur leading-composite retained-target gap** (`GPUTopLevelMaskBlurFrameRecording.kt`,
`firstCompositeClears = sceneRenders.isEmpty()`): a MIXED frame whose first paint op is a mask
blur (chain sorts before the frame's first clear scene render in `orderedRenders` paint order)
runs its composite with `loadOp = "load"` over the RETAINED session target — sampling the
previous frame's pixels. Pre-FP-10 this loaded undefined fresh-target content (equally wrong,
silent), so it is a pre-existing semantic gap EXPOSED by session reuse, not a regression of a
correct path; it is now deterministic-stale instead of garbage. The correct condition is "no
scene clear render ordered BEFORE the composite", not "no scene renders at all". No test covers
the leading-blur-mixed shape. Tracked as an FP-11 transfer (see active-todo); the gap was
documented rather than fixed in FP-10 to keep the reuse change reviewable.
