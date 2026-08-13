# FP-12 Current Visual and Performance Evidence

Status: **completed** (GM evidence regeneration, prepared-candidate benchmark
measurement, evidence documentation, roadmap update).

Branch: `codex/graphite-dawn-frame-fp12`. Base SHA: `820c8956e` (FP-11 closure,
PR #2061). Machine: Linux, JDK Temurin 25, GPU = Vulkan **llvmpipe** (software,
CPU), Xvfb `:99`. All GPU suite runs used `DISPLAY=:99`.

## 0. Executive summary

FP-12 acceptance (roadmap `active-todo.md` FP-12 entry) required:

- current render images and similarity scores regenerated — **done**;
- benchmark inputs, raw samples, hashes, p50/p95 results, and verdicts
  recorded — **done**;
- headless validation independent from opt-in Kadre execution — **done**
  (Kadre submodule uninitialized; every lane below is headless WebGPU offscreen);
- measured lanes and explicit non-claims documented — **done** (§5, §6).

Two pre-existing `:gpu-renderer-scenes` defects surfaced and were fixed because
the module owns the FP-12 benchmark lanes and had not compiled since commit
`06844bc08` (2026-07-31, the `SaveLayerExecutor` rename):

1. **Compile break** — `RectOnlyOffscreenRenderer.kt` still imported the deleted
   `SaveLayerExecutor` (`06844bc08`). The two `saveLayerWiringDiagnostics`
   functions were migrated to emit the identical M25-pinned diagnostics inline
   (no production path touched); `M25ExecutorWiringTest` stays green.
2. **COLRv0 offscreen target-format mismatch** — the color-glyph frame records
   an `RGBA8UnormSrgb` scene target (`GPUColorGlyphPreparedTaskListBuilder.kt:
   126`, since `3d18231ce`), but the offscreen session request used the default
   `RGBA8Unorm`, producing `unsupported.prepared-scene-session.target-
   incompatible`. The request now passes `RGBA8UnormSrgb` +
   `LinearPremul`, matching the `:gpu-renderer` color-glyph smoke-test contract.

The `:gpu-renderer-scenes:test` suite is green except one pre-existing latent
oracle divergence (the `colr-v0-color-glyph` scene's CPU oracle fills an opaque
background while the current product color-glyph lane clears transparent — a
stale-oracle artifact of the July 29-30 clear-semantics change, §5.3). It is
not FP-12-introduced and is not llvmpipe-specific.

## 1. GM evidence regeneration

Commands (headless, `DISPLAY=:99`):

```bash
./gradlew -F off :integration-tests:skia:generateSkiaDashboard --no-parallel --console=plain
./gradlew -F off :integration-tests:skia:test --no-parallel --console=plain
```

Results:

- Dashboard: `Total 615, Pass 540, Fail 6, No score 30, Avg sim 54.4%`
  (summary in `integration-tests/skia/build/reports/skia-gm-dashboard/data/
  gms.json`: `total 576, passing 540, failing 6, noScore 30`). Reconciliation:
  the console `Total 615` is the full registry fed to the generator, while
  `gms.json` holds 576 comparison entries — the 39-GM gap is the
  `RenderCost.BLOCKING` set the dashboard generator excludes
  (`SkiaDashboardGenerator.kt:49`). The runner aborts 40 cases (BLOCKING GMs
  plus untrustable-reference aborts such as `custommesh_uniforms`).
- 23 generated-render PNGs changed vs. the committed baseline (last committed at
  PR #2051, pre-FP-08); the regenerated renders reflect the current prepared
  renderer after FP-08/09/10/11 retirement. Regeneration is deterministic
  (re-render of `text_scale_skew` matched the dashboard copy byte-for-byte,
  SHA-256 `2f64c7d6ba21e3bf299a9772cbe2bc981b22512e4945cb99747af5c0dd86be76`).
- `test-similarity-scores.properties`: 17 score entries changed. All changes are
  llvmpipe-scale score drift except `text_scale_skew` (82.82 → 77.75), whose
  committed render predates FP-08's legacy retirement; the regenerated value is
  the current-state score.

### 1.1 Below-threshold GMs (dashboard `isPassing=false`)

| GM | similarity | minSimilarity | classification |
| --- | --- | --- | --- |
| `emboss` | 55.45 | 55.5 | committed score unchanged (55.45); pre-existing |
| `inverseclip` | 27.55 | 52.1 | committed score unchanged (27.55); pre-existing |
| `picture_mesh` | 27.07 | 99.0 | committed score 90.98 was stale (pre-retirement); fresh test render **refuses** `unsupported.composite.operation` |
| `simpleshapes_bw` | 65.57 | 80.0 | committed score unchanged (65.57); pre-existing |
| `widebuttcaps` | 25.91 | 26.3 | committed score unchanged (25.91); pre-existing |
| `text_scale_skew` | 77.75 | 80.0 | committed 82.82 was a pre-retirement render; regenerated current-state score below threshold |

### 1.2 `SkiaGmRunner` suite state

`:integration-tests:skia:test` reports 686 tests, 504 failed, 40 skipped.
Breakdown: the `SkiaGmRunner` contributes 615 GM cases (498 failures + 40
blocked aborts + 77 passing); the other 71 tests across the module's remaining
classes contribute 6 failures (`GradientColorFilterGpuSmokeTest` 1,
`ImageFilterBlurContractTest` 2, `AAXfermodesRegressionTest` 2,
`ThinRectsGpuCoverageTest` 1); 498 + 6 = 504.
Every GM failure is a stable `GPUPreparedSurfaceTerminalException` refusal from
a **fresh render** (the runner does not read `generated-renders/` PNGs). FP-12
changes no production code, so this refusal profile is inherited verbatim from
FP-11 HEAD. Of the 498 runner failures, 493 GMs have no committed score (never
rendered) and 3 (`BlurDrawImage`, `BlurSmallSigma`, `OverStroke`) refuse on the
post-retirement lane (`unsupported.image.native_binding` /
`unsupported.geometry.path_key_nondeterministic`) while their committed scores
date from PR #2051; 2 more have a zero committed score (`TiledBlurBigSigma`,
`AlternateLuma`).

No assertion was weakened; no GM threshold was changed.

## 2. Prepared-candidate benchmark lanes

All lanes ran headless (`DISPLAY=:99`, WebGPU offscreen) on llvmpipe. Artifacts
are committed under `reports/gpu-renderer-scenes/`.

### 2.1 Per-family benchmark

Command:

```bash
./gradlew -F off :gpu-renderer-scenes:runPerFamilyBenchmark \
  -PwarmupFrames=10 -PmeasuredFrames=90 \
  -PperformanceOutput=reports/gpu-renderer-scenes/performance --no-parallel --console=plain
```

Adapter: `llvmpipe/llvmpipe (LLVM 21.1.8, 256 bits) desc=Mesa 26.0.3-1ubuntu1
(LLVM 21.1.8)`. Backend: `webgpu-offscreen`. Frames: `10 warmup + 90 measured`
(101 native frames per family). Metric source:
`wall-clock-prepared-submit-completion`, 0 measured readbacks, 1 final
validation readback.

| family | status | p50 (medianMs) | fps | frame gate |
| --- | --- | --- | --- | --- |
| FillRect | sampled | 4.4850 | 214.2 | pass |
| LinearGradient | sampled | 4.0750 | 213.7 | pass |
| RadialGradient | sampled | 3.6001 | 250.3 | pass |
| SweepGradient | sampled | 3.4429 | 269.3 | pass |
| PathFill | **unsupported** | — | — | — |
| BitmapRect | **unsupported** | — | — | — |
| Text | **unsupported** | — | — | — |
| Blur | sampled | 6.5689 | 148.6 | pass |
| ColorMatrix | sampled | 2.8403 | 322.2 | pass |
| Stroke | sampled | 2.8961 | 307.4 | pass |

3/10 families are **explicitly unsupported** by the prepared scene route:
`unsupported.prepared-scene.family: <scene> has no typed prepared semantic
route` for PathFill, BitmapRect, and Text.

Artifacts (SHA-256):

- `per-family-benchmark.json` `579d2e799bc277eb426de1cfa6aa6ca64a008db6b21c02a67fdb56fdd0295faf`
- `frame-gate-policy.json` `42f97cd2976c9772a98a4578201fdc026ee20528534bf992c2cc10e1891e8947`
- `pipeline-cache-telemetry.json` `a3e088debd376efb720b7fcb6d8dd637e4c712d6478da5d4d7c94bccaade95c1`
- `*diagnostics.txt` transcripts alongside each JSON

### 2.2 Frame sample lane

Command:

```bash
./gradlew -F off :gpu-renderer-scenes:sampleGpuRendererSceneFrames \
  -PsceneId=solid-card-stack -Pframes=60 \
  -PsceneOutput=reports/gpu-renderer-scenes/frame-samples --no-parallel --console=plain
```

Scene: `solid-card-stack` (the rect-only offscreen lane; `frame-gate-blocker-
board` is not rect-only on this route and returns `not-yet-rendered`). Raw
samples: **60**, warmup: **3**, stable: **57**. Metric:
`frame-time-ms` / `wall-clock-prepared-submit-completion` (0 measured
readbacks, 1 final validation readback — the same source as §2.1).

| statistic | value (ms) |
| --- | --- |
| mean | 5.2800 |
| **p50** (median) | **4.8563** |
| **p95** | **7.3170** |
| min | 3.1767 |
| max | 9.4228 |
| stdev | 1.1630 |
| coefficient of variation | 0.2203 |

Artifact: `reports/gpu-renderer-scenes/frame-samples/solid-card-stack/
frame-samples.json` SHA-256
`98c925bda59bac8af60f6db67c840285e2f03e3620e8cdbc39471e885d8b9956`
(replaces the stale pre-retirement Apple M2 Max artifact).

### 2.3 Pipeline-cache telemetry (draw-plan-derived)

`pipeline-cache-telemetry.json` covers all 10 benchmark families at 100 frames:
per-scene `hitRate=0.99`, 1-2 cold misses, `evictionCount=0`, 1-2 modules,
`productActivation=true`. This is **draw-plan-derived** ledger telemetry (M85
class), not observed WebGPU runtime cache telemetry.

## 3. Baseline regression confirmation

`./gradlew -F off :kanvas:test :gpu-renderer:test` on `DISPLAY=:99`:

| module | tests | failures | documented baseline |
| --- | --- | --- | --- |
| `:kanvas:test` | 3,234 | 1 | `GPUPreparedSurfaceImagePixelTest` — UNORM 1-LSB on llvmpipe (documented FP-03, unchanged) |
| `:gpu-renderer:test` | 3,300 | 1 | `GPURendererPackageBoundaryTest` — exactly 20 cycle / 0 rule violations (documented, unchanged) |

`GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` passes on llvmpipe. The
`failed.surface.prepared.session-close` flake was not observed. Baseline
unchanged by FP-12; no production prepared-renderer code was touched.

## 4. `:gpu-renderer-scenes` fixes (pre-existing defects unblocking the lanes)

### 4.1 Compile break — deleted `SaveLayerExecutor` reference

`06844bc08` (2026-07-31) replaced `SaveLayerExecutor` with
`GPUSaveLayerNativeExecutor` but never updated `RectOnlyOffscreenRenderer.kt`
(the only remaining consumer). No FP since (08/09/10/11) compiled
`:gpu-renderer-scenes` — FP-11's scope was `:kanvas:test` +
`:gpu-renderer:test` only. Fix: `saveLayerWiringDiagnostics` now emits the
identical `savelayer:executor`/`savelayer:executor.nonclaim` lines via a local
`saveLayerExecutorWiringLines(childrenRendered)` helper; the `M25ExecutorWiringTest`
KGPU-M25-004 assertions (`targetAllocated=true`,
`compositeSnippetSourceHash=fragment:layer_composite:v1`,
`secondaryTargetAllocated=true`) stay green. The unused no-fills
`saveLayerWiringDiagnostics(sceneId, width, height)` overload was removed
(dead at base; only the fills overload is referenced).

### 4.2 COLRv0 offscreen target-format mismatch

`3d18231ce` (2026-07-30) declared the color-glyph scene target `RGBA8UnormSrgb`,
but the offscreen session request kept the default `RGBA8Unorm` →
`unsupported.prepared-scene-session.target-incompatible` on every render. Fix:
the `renderPreparedColorGlyphScene` session request now passes
`GPUColorFormat.RGBA8UnormSrgb` + `GPUColorInterpretation.LinearPremul`,
matching `GPUColorGlyphPreparedFrameSmokeTest.targetRequest()`.

### 4.3 Remaining latent oracle divergence (documented, not fixed)

`RenderGpuRendererSceneOffscreenMainTest > real COLRv0 scene uses one prepared
encoder submit and matches its CPU reference` still fails after §4.2: the scene
renders (status `rendered`) but `pixelExact=38/4096`. Root cause: the scene's
CPU oracle (`PreparedColorGlyphSceneFrame.composeCpuReference`) fills an opaque
background (`alpha=1`), while the current product color-glyph lane clears
transparent (`GPULoadStorePlan("clear", ...)`; the `:gpu-renderer` smoke test
asserts `0,0,0,0` background and passes byte-exact on llvmpipe). The oracle
predates the July 29-30 clear-semantics change (`231679695` removed the
`"opaque-black"` loadStore; `3d18231ce` added SRGB) and the module stopped
compiling the next day, so the stale oracle was never exercised. It is a
pre-existing latent issue, **not** FP-12-introduced and **not**
llvmpipe-specific. Left as a tracked item, not weakened.

## 5. Measured lanes and non-claims

Measured lanes on llvmpipe (software GPU, CPU rasterization):

- `frame.per-family` (10 families, 10+90 frames, p50/fps/gate verdicts) — §2.1;
- `frame.offscreen-scene` (`solid-card-stack`, 60 raw samples, p50/p95) — §2.2;
- pipeline-cache ledger (draw-plan-derived) — §2.3.

Explicit non-claims:

- No cross-machine or hardware-GPU performance claim: every number above is an
  **llvmpipe software rasterizer** measurement on one Linux host, not an Apple
  M-series or discrete-GPU result. The committed pre-retirement Apple M2 Max
  artifacts are replaced by these current-state llvmpipe artifacts.
- No release-blocking performance gate is promoted: 60fps gate verdicts are
  frame-gate-policy **reporting** output (`productActivation=true`,
  `releaseBlocking` not asserted here), consistent with M67/M84 candidate
  semantics.
- PathFill / BitmapRect / Text families are **unsupported** on the prepared
  scene route (`unsupported.prepared-scene.family`) — no measurement claimed.
- `pipeline-cache-telemetry.json` is draw-plan-derived ledger evidence (M85
  class), not observed WebGPU runtime cache telemetry.
- The `colr-v0-color-glyph` scene oracle divergence (§4.3) is a pre-existing
  latent issue, not a claim of color-glyph support regression.
- No Kadre/windowed lane was run: `external/poc-koreos` submodule is
  uninitialized, headless validation stays fully independent of opt-in Kadre
  execution.
- No production prepared-renderer code was modified by FP-12; the two
  `:gpu-renderer-scenes` fixes are harness/scenes-module only.

## 6. FP-12+ transfers (unchanged)

The FP-11 evidence's residual-refusal tracking list (§10) carries forward
unchanged: analytic clips over non-direct geometry (4), dst-read formula on
mapped routes (2), analytic-shape multi-key dst-read (2), complex-clip blur,
path destination-read (60), and the analytic-clip 64/160 split residual (199
blend rows on `mixed_uniform_layouts`). FP-12 adds the COLRv0 scene oracle
divergence (§4.3) to that tracking list.

## 7. Commit trail

The FP-12 branch contains: GM evidence regeneration (23 render PNGs + 17 score
lines), the two `:gpu-renderer-scenes` fixes (§4.1, §4.2), the benchmark
artifacts (§2), this evidence doc, and the roadmap update. The regenerated
dashboard HTML/JSON lives under the gitignored
`integration-tests/skia/build/reports/skia-gm-dashboard/` build output and is
reproducible via `:integration-tests:skia:generateSkiaDashboard`, not a
committed deliverable.
