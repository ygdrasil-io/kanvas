# FP-11 Close Bounded Native-Rendering Gaps — Terminal Refusal Evidence

Status: **completed** (Task 9 closure: full regression, guards, evidence
finalization, roadmap update).

Branch: `codex/graphite-dawn-frame-fp11`. The Task 8 guard run executed at HEAD
`df5e084e0` (Task 7) with the B-table edits in the working tree (committed as
`39e55d60a`). HEAD at the Task 9 closure: `39e55d60a` (Task 8) plus the Task 9
evidence/roadmap commit. All `file:line` references were re-verified during
the Task 9 finalization pass at the closure HEAD (2026-08-13).

## 0. Task 9 closure summary

### 0.1 Full regression (Step 1)

Command:

```bash
./gradlew -F off :kanvas:test :gpu-renderer:test --no-parallel --console=plain
```

Result: 6,534 tests total, 2 failed — both the documented pre-existing
failures, unchanged:

| module | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `:kanvas:test` | 3,234 | 0 | 0 | 0 |
| `:gpu-renderer:test` | 3,300 | 2 | 0 | 0 |
| total | 6,534 | 2 | 0 | 0 |

The two failures (measured from the JUnit XML, run 2026-08-13):

1. `GPURendererPackageBoundaryTest > gpu renderer production source satisfies
   package boundary rules()` — `AssertionFailedError` at
   `GPURendererPackageBoundaryTest.kt:54`; the message lists exactly **20
   package cycle violations and 0 rule violations** (unchanged pre-existing
   baseline; the other 21 cases in the class pass).
2. `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest > native clip stencil AA
   4x retains color and stencil across three passes and reuses its pair()` —
   `diagonal 4x clip edge must contain one premultiplied partial red pixel` at
   `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest.kt:422` (reproduces at
   base SHA; pre-existing).

The `failed.surface.prepared.session-close` flake was **NOT observed** in this
run. Neither the FP-09 §17 environmental random flake (no occurrence across
6,534 tests) nor the Task 6 deterministic bypass probe landed (the probe was
removed with the Task 6 debris in `178c681fa`; its deterministic evidence is
pinned in the gate comments, §4.2). The FP-10 crash class
(`EXCEPTION_ACCESS_VIOLATION` on backend dispose churn) was not re-exposed;
`GPUPreparedSurfaceLifetimeStressTest` passed 6/6 inside the full run.

### 0.2 Guard verification (Step 2)

| suite | command scope | result |
| --- | --- | --- |
| `GPURendererPackageBoundaryTest` | `:gpu-renderer:test --tests "*GPURendererPackageBoundaryTest"` | 22 tests, 1 failure — only the documented pre-existing case (20 cycles, 0 rules); 21/22 pass |
| `GPUPreparedSurfaceLegacyAbsenceTest` | `:kanvas:test` (guard set) | 1/1 |
| `GPUPreparedSurfaceProductRouterTest` | `:kanvas:test` (guard set) | 15/15 (matches the Task 8 record) |
| `GPUPreparedCompositeCaptureSemanticTest` | `:kanvas:test` (guard set) | 19/19 |
| `GPUPreparedCompositeFrameRouteIntegrationTest` | `:kanvas:test` (guard set) | 8/8 |
| `GPUAllApiBlendSurfaceTest` | `:kanvas:test --rerun-tasks` | 1,864/1,864 |
| `GPUClipCoverageSurfaceTest` | `:kanvas:test --rerun-tasks` | 41/41 (matches the Task 8 record) |
| `GPUClipAdvancedBlendSurfaceTest` | `:kanvas:test --rerun-tasks` | 5/5 |
| `GPUPathClipRegressionTest` | `:kanvas:test --rerun-tasks` | 4/4 |
| `GPUMaskBlurSurfaceTest` | `:kanvas:test --rerun-tasks` | 19/19 |

GPU suite total: 1,933 tests, 0 failures, 0 errors, **0 skipped**. WebGPU is
present on this host (Intel UHD Graphics 630 + AMD Radeon Pro 5500M), so the
GPU-dependent pixel rows ran for real rather than skipping. All new FP-11
tests ride the retained-session checkin (session reuse across frames); the
FP-10 `GPUPreparedSurfaceLifetimeStressTest` stayed green 6/6 — no per-frame
close was introduced.

### 0.3 Re-measured per-code route split (Step 3, replaces the Task 6 approximations)

Re-derived from the blend-suite matrix (`GPUAllApiBlendSurfaceTest.kt:582-644`,
7 core APIs × 29 modes × 3 non-SAVE_LAYER contexts = 609 rows; the SAVE_LAYER
rows return the layer refusals before the FP-11 branches) and verified by the
green 1,864-row run: every Terminal row asserts its exact code and every
Prepared row renders pixels, so a green matrix is a measured distribution.

| code | rows (blend suite) | rows detail |
| --- | --- | --- |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` | **199** | DrawRRect 29 (ALPHA_MASK) + DrawRect/DrawColor 56 (ALPHA_MASK non-DST) + DrawPath/DrawDRRect 58 (ALPHA_MASK) + DrawPoint/DrawPoints 56 (ALPHA_MASK non-DST) |
| `unsupported.native-core-primitive.path-destination-read` | **60** | DrawPath/DrawDRRect × 15 dst-copy modes × 2 contexts |
| `invalid.preflight.core_primitive_direct_geometry_resources` | **32** | DrawRRect DST 2 (Task 6 residual) + DrawPoint dst-copy 30 (four-render shape; Task 3-era re-route) |
| `unsupported.native-core-primitive.frame-global-pipeline` | **30** | DrawRRect dst-read 30 (15 modes × 2 contexts) |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | **4** | DrawRect/DrawColor/DrawPoint/DrawPoints ALPHA_MASK × DST |
| Prepared | **284** | DrawRect 58, DrawColor 58, DrawPoints 58, DrawPoint 28, DrawPath 28, DrawDRRect 28, DrawRRect 26 |
| total | 609 | ✓ |

Clip-suite pins on the mixed-layout code at HEAD:
`GPUClipCoverageSurfaceTest.kt:112` (1), `GPUClipAdvancedBlendSurfaceTest.kt:
53-57` (8 AA-clip dst-read cases), `GPUPathClipRegressionTest.kt:25` (1
device-rect clip path).

Reconciliation with the FP-09 measured 202 (201 blend + 1 clip):
201 − 58 (DrawRRect leaves mixed: 26 fixed rows → Prepared, 2 DST rows →
`direct_geometry_resources`, 30 dst-read rows → `frame-global-pipeline`) + 56
(DrawPoint/DrawPoints ALPHA_MASK non-DST rows enter mixed after the Task 3
hairline removal) = **199 blend rows**. The Task 6 record's "~141 of 202" and
"28 frame-global rows" were mid-task approximations: the exact HEAD counts are
199 blend (+ clip pins) and 30 (the 2 DrawRRect PLUS rows are dst-read-refused
per `recordsDestinationRead()`, `GPUAllApiBlendSurfaceTest.kt:935-936`, and
the green run proves the refusal fires).

### 0.4 Environmental-classification note (this closure session)

`GPUBackendRuntimeNativeSmokeTest > session dispose closes queue completion
before device without hanging` (`GPUBackendRuntimeNativeSmokeTest.kt:790`) is
a 10s child-JVM timing probe observed intermittently on this host. It passes
in isolation, passed the FP-10 full regression, and passed this full run; it
is NOT task-introduced. Classification: **environmental timing**, distinct
from the two documented pre-existing failures (0.1) and from the
`failed.surface.prepared.session-close` flake family (0.1/§4.2).

## 1. Before/after route-split table (the plan §1 map vs. the post-Tasks-2-7 route split)

"Before" = the plan §1 refusal-code map as measured by the FP-09 evidence run
(`reports/fp11-gap-map.txt` is the Task 1 saved before-snapshot of the
production emission sites at plan-time HEAD `f14656988`). "After" = HEAD at
the Task 9 closure, re-measured as in §0.3.

| code | before (FP-09 measured) | after (Task 9 re-measured) | outcome |
| --- | --- | --- | --- |
| `unsupported.core_primitive.point.hairline_exact_lowering` | 175 (174 blend + 1 clip) | 0 | **covered** (Task 3): one-device-pixel square lowering; round-cap twins `:565`/`:681` stay pinned |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` | 202 (201 blend + 1 clip) | 199 blend + clip pins (Coverage 1, Advanced 8, PathClip 1) | **partial** (Task 6): uniform80 split covered; analytic-clip 64/160 split stays refused (B) |
| `unsupported.native-core-primitive.multi-render-dst-copy` | 60 (blend) | 0 | **covered** (Task 4): two-render dst-copy direct lane; code left production and the residual set |
| `unsupported.native-core-primitive.path-destination-read` | 60 (blend) | 60 | **stays Terminal** (B): A→B reclassification (Task 5), §3 |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | 2 (blend) | 4 (blend) | **stays Terminal** (B); +2 Point/Points rows re-routed at Task 3, §2 |
| `unsupported.native-core-primitive.dst-read-formula` | 2 (clip) | 2 (clip) | **stays Terminal** (B) |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 2 (clip) | 2 (clip) | **stays Terminal** (B) |
| `invalid.preflight.core_primitive_clip_producer_authority` (complex-clip blur) | bounded (2 clip pins + preflighter) | bounded, unchanged | **stays Terminal** (B) |
| `unsupported.native-mask-blur.clip` | bounded (coverage-mask + analytic clips over the blur composite) | bounded (coverage-mask/stacked clips only; `GPUMaskBlurSurfaceTest.kt:222-236`) | **shrunk** (Task 7): analytic device-rect clips on the top-level mask-blur composite covered |
| `invalid.preflight.core_primitive_direct_geometry_resources` | 0 | 32 (2 Task-6 residual + 30 DrawPoint) | new terminal rows on an existing stable typed code; no destination readback before refusal |
| `unsupported.native-core-primitive.frame-global-pipeline` | 0 | 30 (DrawRRect dst-read) | new terminal rows on an existing stable typed code; no destination readback before refusal |

Residual-family shrinks only: no production line introduced or re-introduced
any legacy/Graphite/Ganesh/SkSL path, and no B row was removed — every code
that stayed refused still refuses with its stable typed code
(`GPUPreparedSurfaceLegacyAbsenceTest` 1/1 pins the retired legacy tokens).

## 2. The B-table: justified terminal refusals (cost ≫ value)

FP-11 acceptance requires every unsupported case to retain a stable typed
refusal with a documented cost/value justification. The B family is the set of
rows FP-11 re-documents instead of covering: the plan's original four rows
(#3 analytic-clip non-direct geometry, #4 dst-read formula on mapped routes,
#6 analytic-shape multi-key dst-read, complex-clip blur) plus the two real
reclassifications produced during execution — the Task 5 path-destination-read
A→B and the Task 6 verify-gate mixed-layout PARTIAL (both verified in §3-§4).

| code | cases | emission (HEAD) | root-cause level | refusal level | justification (cost ≫ value) |
| --- | --- | --- | --- | --- | --- |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (+ intersection twin) | 2 at FP-09; **4 at closure HEAD** (§0.3) | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009` (twin `:2016`) | recording (clip authority requires direct shading geometry) | recording | analytic clip over stencil-shaded geometry is a new execution feature; 2 matrix cases at FP-09, 4 after the Task 3 hairline re-route |
| `unsupported.native-core-primitive.dst-read-formula` | 2 | `GPUCorePrimitiveNativeRoute.kt:415` (multi-key), `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:917` (single-key) | execution (formula program availability) | execution | scalar-coverage dst-read formula programs on the analytic-shape lane need a separate AA oracle; the covered full-coverage formulas (DARKEN/SCREEN) exist |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 2 | `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1453` | execution (AA coverage semantics unverified) | execution | AA multi-key analytic-shape blend semantics (e.g. CLEAR) unverifiable by the coverage-modulating shader |
| `invalid.preflight.core_primitive_clip_producer_authority` (complex-clip blur) | bounded (2 clip-suite pins + preflighter) | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:883` | preflight (clip-producer/consumer authority not sealed) | preflight | general clip-producer authority, not blur-specific; coverage requires a new clip-execution shape |
| `unsupported.native-core-primitive.path-destination-read` — **RECLASSIFIED A→B (Task 5)** | 60 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2572` (refusal block `:2565-2575`) | recording (dst-read cover consumer ref + own-pass) | recording | path-stencil execution model cannot express the dst-read shape; a wired cover pass would clear a fresh stencil and blend over its whole bounds (wrong pixels); coverage requires a dedicated stencil-continuation feature (§3) |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` (+ preflight twin) — **verify-gate PARTIAL (Task 6)** | 199 blend rows + clip pins remain refused (§0.3) | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617` (shape+clip mix gate), `:2132` (analytic-clip 64/160 gate); preflight twin **removed** at HEAD | recording/preflight (one uniform layout per pass) | recording | uniform80 split wired and Covered; the analytic-clip 64/160 split stays unwired pending the per-step continuation/ownership design (§4) |
| `invalid.preflight.core_primitive_direct_geometry_resources` — **residual re-point (Task 6)** | 2 (+ 30 DrawPoint rows on the same code since Task 3, §0.3) | `GPUFramePreflighter.kt:3326` | preflight (direct-resource seal) | preflight | DrawRRect DST rows re-pointed from the mixed-layout code when the split landed; stable typed refusal retained (§5) |
| `unsupported.native-core-primitive.frame-global-pipeline` — **residual re-point (Task 6)** | 30 (Task 6 record said 28; the 2 DrawRRect PLUS rows are dst-read-refused per §0.3) | `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:151` | execution (frame-global run component identity) | execution | DrawRRect dst-read rows re-pointed from the mixed-layout code; no closed analytic-shape dst-read formula pipeline on the prepared lane (§5) |

## 3. Reclassification evidence — path destination-read (gap #7, RECLASSIFIED A→B in Task 5)

Plan classification was A (depends on #5, priority 4). Task 5 verified the
recording-level fix (keying the `TextureCopy` consumer ref to the assembled
path-cover packet id) is correct — the destination-contract test went green —
but the path-stencil EXECUTION model cannot express the dst-read shape:

- `GPUFramePreflighter.kt:2401` — the path-stencil code string
  (`invalid.preflight.core_primitive_path_stencil`; the `refused(message)`
  helper's message arg sits at `:2402`); the "exactly one pass" check is the
  `if (indexedCoreRenders.isEmpty() || (!mixedPreparedSurface &&
  !splitPathAdmission))` gate at `:2437-2440`, refusing at `:2440`. The Task 6
  split admission only relaxes this to one path pass + direct-only split
  passes, never a second path pass;
- `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:163-174` — the run
  materializer excludes the dst-read component identity from
  `supportedPathComponents` (refusal `invalid.native-core-primitive.frame-
  global-path-pipeline`, `:169-173`);
- the run materializer hardcodes per-run stencil Clear+Discard with no
  stencil-continuation mechanism — a wired cover pass would clear a fresh
  stencil and blend over its whole bounds (wrong pixels, caught by
  `assertPixelsNear`).

Verification of the A→B flip: with the recording refusal removed, the 60
blend-suite rows regressed to `invalid.preflight.core_primitive_path_stencil`
(execution-level), so the recording refusal was REVERTED to keep the stable
typed code `unsupported.native-core-primitive.path-destination-read` at the
recording authority. No Task 5 commit landed; the refusal block at
`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2565-2575` (code string at
`:2572`) is the current HEAD state. The `destination_snapshot` overflow catch
at `:2553-2558` is a separate, unrelated refusal and is NOT part of this
block.

Coverage cost: a dedicated path-stencil stencil-continuation feature — producer
pass stores the fan to the frame-local `pathDepthStencil` D24S8 with
`WritableStencil(Clear, Store)`, cover pass loads it read-only, cross-step pair
admissions (precedent `materializePreparedClipStencilCore`) — plus a second
admitted path render pass in the preflighter and a dst-read path cover
pipeline. Cost ≫ value for this FP (60 cases, but the shape is a new
execution feature).

Pinned: `GPUPreparedSurfaceProductRouterTest.kt:471` (terminal-family matrix)
and the blend-suite route matrix (`GPUAllApiBlendSurfaceTest.kt:640`).

## 4. Reclassification evidence — mixed uniform layouts (gap #2 verify-gate, PARTIAL in Task 6)

### 4.1 The deterministic residual and its fix

The Task 6 verify-gate fired with a deterministic execution residual: the
uniform80 split IS wired end-to-end (DrawRRect UNCLIPPED/SCISSOR fixed-blend
rows render Prepared, CPU-oracle verified; the builder 32+80 split test is
green), but the analytic-clip 64/160 split is NOT wired. Bypassing the gate
and running the blend suite's fixed-function non-SRC_OVER analytic-clip rows
fails deterministically with `failed.surface.prepared.session-close` →
`GPUOwnedNativeCloseIncompleteException: prepared-scene-child-cache close
remains incomplete with 1 native owner(s)` (48 rows = 4 APIs × 12
fixed-function non-SRC_OVER modes; SRC_OVER renders clean).

Root cause and fix: the split-lane materializer's mid-loop refusal path
skipped the lease cleanup, leaking pooled frame slots; that cleanup gap is NOW
FIXED in commit `3bd78e180` (`materializeDirectMultiRenderSplitCore` releases/
quarantines already-materialized run lifecycles and restores the materializer
ledger, `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:5639-5676`,
mirroring the dst-copy lane cleanup at `:5251-5261`). The 64/160 split itself
remains unwired pending a per-step continuation/ownership design, so the
refusal stays pinned. The bypass probe was removed with the Task 6 debris
(`178c681fa`); the gate comment at
`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2108-2120` pins this
deterministic-residual evidence in production.

### 4.2 Route split at closure HEAD (re-measured, §0.3)

- 199 blend rows remain refused on `unsupported.recording.core_primitive_
  mixed_uniform_layouts` (+ clip-suite pins: `GPUClipCoverageSurfaceTest.kt:
  112`, `GPUClipAdvancedBlendSurfaceTest.kt:53-57`, `GPUPathClipRegressionTest
  .kt:25`);
- 26 DrawRRect fixed-blend rows (UNCLIPPED/SCISSOR, 13 modes × 2) + 1
  scissor-dst-read clip case flipped to Prepared (`GPUClipAdvancedBlendSurface
  Test.kt:78-101` — the scissored dst-read rect now rides the admitted
  two-render dst-copy shape);
- 2 DrawRRect DST rows re-pointed to `invalid.preflight.core_primitive_direct_
  geometry_resources` (§5);
- 30 DrawRRect dst-read rows re-pointed to `unsupported.native-core-primitive.
  frame-global-pipeline` (§5; the Task 6 record said 28 — the 2 DrawRRect PLUS
  rows are dst-read-refused per `recordsDestinationRead()`,
  `GPUAllApiBlendSurfaceTest.kt:935-936`, proven by the green run).

Emission at HEAD: `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617` (the
analytic-shape uniform80 + analytic-clip uniform64/160 mix gate — the gate now
admits uniform80+32 mixes and refuses only the analytic-clip mixes) and `:2132`
(the `activeDirectUniformLayouts > 1` gate re-scoped to the analytic-clip
uniform64/160 case; gate comment at `:2108-2120` pins the deterministic
residual evidence). The preflight twin `unsupported.preflight.core_primitive_
mixed_uniform_layouts` (plan-time `GPUFramePreflighter.kt:3311`) is REMOVED at
HEAD — verified by `rg` over `gpu-renderer/src` + `kanvas/src` (no matches) —
the preflighter now accepts N direct renders when each owns one layout.

Pinned: `GPUPreparedSurfaceProductRouterTest.kt:469` (terminal-family matrix),
`GPUClipCoverageSurfaceTest.kt:112`, the blend-suite ALPHA_MASK rows
(`GPUAllApiBlendSurfaceTest.kt:588`, `:603-606`, `:624-627`, `:635-638`), and
the clip-suite `GPUClipAdvancedBlendSurfaceTest.kt:53-57` (AA-clip dst-read
stays on the code).

### 4.3 Flake-family distinction (Task 9)

- **Deterministic (Task 6 probe)**: bypassing the 64/160 gate → guaranteed
  `failed.surface.prepared.session-close`/`GPUOwnedNativeCloseIncomplete
  Exception` on the 48 fixed-function non-SRC_OVER analytic-clip rows. Root
  cause fixed (`3bd78e180`); the probe is removed; the gate comment pins the
  residual evidence. This is NOT a flake.
- **Environmental random (FP-09 §17)**: `failed.surface.prepared.session-
  close` landing on a different random non-dst-read frame under churn. Not
  observed in the Task 9 full run (0 occurrences in 6,534 tests) nor the
  guard runs. No assertion was weakened; the class passes in isolation on
  re-run.
- **Timing probe (this session)**: `GPUBackendRuntimeNativeSmokeTest` session-
  dispose child-JVM probe (§0.4) — environmental timing, unrelated.

## 5. Residual re-points from the Task 6 split (stable typed refusals, kept)

Two pre-existing refusal codes gained rows when the Task 6 split landed. They
are residual rows of the same re-documentation, not new coverage promises:

| code | cases | emission (HEAD) | rows | note |
| --- | --- | --- | --- | --- |
| `invalid.preflight.core_primitive_direct_geometry_resources` | 2 (Task 6 residual; +30 DrawPoint four-render rows since Task 3) | `GPUFramePreflighter.kt:3326` | DrawRRect × DST (UNCLIPPED/SCISSOR); DrawPoint × dst-copy modes | the DST rrect pass cannot exact its shared geometry slab authority after the split; the DrawPoint fixture's three separate point commands make a four-render shape that fails the same seal (pinned at `GPUAllApiBlendSurfaceTest.kt:589-599`); stable typed refusal retained |
| `unsupported.native-core-primitive.frame-global-pipeline` | 30 | `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:151` | DrawRRect × dst-read modes (UNCLIPPED/SCISSOR) | the split dst-read rrect consumer stays on the frame-global pipeline boundary: no closed analytic-shape dst-read formula pipeline on the prepared lane |

Both are pinned by the blend-suite route matrix (`GPUAllApiBlendSurfaceTest.
kt:607-614`). They keep the acceptance contract: stable typed refusal, no
destination readback allocated before refusal (asserted per row,
`GPUAllApiBlendSurfaceTest.kt:135-139`).

## 6. Per-task proof (Tasks 2-7: red-run code capture + green-run counters)

| task | red capture (pre-fix) | green proof (post-fix) | commits |
| --- | --- | --- | --- |
| 2: retained-session leading-blur clear | FP-10 transfer: `GPUTopLevelMaskBlurFrameRecording` `firstCompositeClears = sceneRenders.isEmpty()` cleared only when the frame had a scene clear; a leading blur composite on a retained session sampled the previous frame's pixels (`loadOp="load"`) | `GPUMaskBlurSurfaceTest.kt:362` (leading blur on a mixed retained frame clears instead of sampling the previous frame) and `:388` (second blur composite loads the composited scene instead of clearing it) render CPU-oracle-exact; full run green | `29949f297`, `f0b95fb4b` |
| 3: exact hairline point lowering | 175 rows terminal `unsupported.core_primitive.point.hairline_exact_lowering` | code removed from production (rg: only the round-cap twins `:565`/`:681` remain); `GPUFramePathApiInventoryTest.kt:724-735` pins the span-1 device-pixel-square geometry (DirectTriangles, FullOrScissor); blend-suite DrawPoint/DrawPoints UNCLIPPED/SCISSOR rows render Prepared against the CPU oracle (0 skipped rows); `GPUClipCoverageSurfaceTest.kt:744` pins hairline points under the complex AA clip rendering prepared. **Known divergence (documented):** AA hairline points render as hard one-device-pixel squares (FullOrScissor); Skia would AA-soften; no AA hairline test pins pixels | `ed46a95fd` |
| 4: multi-render dst-copy direct lane | 60 rows terminal `unsupported.native-core-primitive.multi-render-dst-copy` | code left production and left `preparedRouteResidualRefusalCodes` (HEAD set = `multi-key-component`, `dst-read-formula`, `analytic-shape-multi-key`, `GPUPreparedSurfaceFrameExecution.kt:1087-1091`); `GPUPathClipRegressionTest.kt:99` (darken rect over destination renders prepared via the multi render dst copy lane) flipped; DrawRect/DrawColor/DrawPoints dst-copy rows Prepared (matrix `GPUAllApiBlendSurfaceTest.kt:628-631`); DrawPoint four-render stays terminal on `direct_geometry_resources` | `b13d01a70`, `9c0338592` |
| 5: path destination-read A→B | destination-contract test red; recording fix green; then 60 rows regressed to `invalid.preflight.core_primitive_path_stencil` with the recording refusal removed | fix REVERTED; stable typed recording refusal retained (`:2572`); 60 rows stay on `path-destination-read`; no commit landed | — (verify-then-revert, §3) |
| 6: direct pass uniform-layout split | 202-row mixed-layout family (plan §1); bypass probe → deterministic `session-close`/`GPUOwnedNativeCloseIncompleteException` (48 rows, 4 APIs × 12 fixed-function non-SRC_OVER analytic-clip modes; SRC_OVER clean) — root cause: split-lane materializer mid-loop refusal skipped lease cleanup | cleanup fixed (`:5639-5676`, mirror `:5251-5261`); 26 DrawRRect fixed rows + 1 scissor-dst-read clip case render Prepared; 2 DrawRRect DST → `direct_geometry_resources`; 30 dst-read → `frame-global-pipeline`; 199 blend rows stay on the mixed code (§0.3); headless preflighter coverage for the N-render path; probe debris removed | `d7c15b4b0`, `178c681fa`, `3bd78e180` |
| 7: analytic rect clips on the top-level mask-blur composite | analytic device-rect clip over the blur composite refused `unsupported.native-mask-blur.clip` | clip variants admitted (uniform64 clip block mirroring the core lane's `CorePrimitiveAnalyticClipBlock` ABI; composite shaders multiply the blurred mask coverage by the analytic clip coverage); `GPUMaskBlurSurfaceTest.kt:240` (analytic rect clip renders prepared) and `:254` (clip coverage ramp at half-integer bounds) CPU-oracle-exact; coverage-mask/stacked clips stay terminal `:222-236` | `554986cfe`, `df5e084e0` |

Task 7 doc notes (pinned in the tests/production):
- the clip-coverage corner-falloff exactness holds when neither fractional
  bound part lies in (0, 0.5) — test-side doc note (`df5e084e0`);
- multi-chain dst-read heterogeneous clip admission is a known latent
  wrong-render limitation (first-chain clip authority binds the shared dst
  bind group for ALL dst-read chains) — documented at
  `GPUWgpu4kMaskBlurFramePayloadMaterializer.kt:794-803`; do not widen
  dst-read clip admission without splitting the bind group per chain.

## 7. Semantic-builder line drift and review-fix log (Tasks 8-9)

- FP-09/roadmap record: hairline `409/411/465`; plan-time HEAD `f14656988`:
  `557/559/613` (`GPUCorePrimitiveSemanticBuilder.kt`, `kanvas/src`).
- HEAD after Task 3: `unsupported.core_primitive.point.hairline_exact_
  lowering` is GONE from the semantic builder — hairline points now lower to
  the canonical one-device-pixel square (`hairlinePointDeviceGeometry`,
  `:578`/`:624`). The surviving round-cap twins are at `:565` (points) and
  `:681` (strokes); `GPUFramePathApiInventoryTest.kt:750` keeps them pinned
  (the pin moved from `:751`; the hairline lowering assertion sits at
  `:724-735`).
- Same drift class, execution: `dst-read-formula` single-key `:893` → `:917`
  and `analytic-shape-multi-key` `:1429` → `:1453`
  (`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`); path-destination-read
  `:2564` → `:2572`; mixed-layout second gate `:2120` → `:2132`.
- Fix attribution (Task 8 review): the analytic-clip intersection twin was
  cited as "the FP-09 record cited `:2013`" — that is a false attribution. The
  FP-11 plan cited `:2013` (`reports/fp11-close-bounded-native-rendering-gaps-
  plan.md` §1); FP-09's plan cited `:2001` (intersection twin) and FP-09's
  evidence cited `:1994`. Corrected in §2.
- The blend-suite re-route branch for DrawPoint/DrawPoints ALPHA_MASK × DST is
  at `GPUAllApiBlendSurfaceTest.kt:585-586` (not `:607-611`; the DrawRRect DST
  branch is `:607-609`).

## 8. Guard verification runs (Task 8 + Task 9)

Task 8 Step 2 (recorded 2026-08-13 at HEAD `df5e084e0`):

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

Result: BUILD SUCCESSFUL in 1m 25s. `GPUPreparedSurfaceProductRouterTest`
15/15 and `GPUClipCoverageSurfaceTest` 41/41 — 0 failures, 0 errors, 0
skipped. The B rows still assert `Terminal(code)`:

- `GPUPreparedSurfaceProductRouterTest.kt:464-475` — the terminal-family
  matrix asserts `GPUPreparedSurfaceProductRoute.Terminal` with the exact B
  codes (`mixed_uniform_layouts`, `analytic_clip_non_direct_geometry`,
  `analytic-shape-multi-key`, `path-destination-read`); the hairline and
  multi-render codes left the matrix in Tasks 3/4 (covered), documented in
  the test comment.
- `GPUClipCoverageSurfaceTest.kt:62-67` — the three B constants
  (`clip_producer_authority`, `analytic-shape-multi-key`, `dst-read-formula`)
  remain pinned at `:112`, `:175`, `:193`, `:331`, `:354`, `:409`, `:433`;
  the `:193` pin asserts no destination readback is allocated before refusal.
- `GPUPreparedSurfaceFrameExecution.kt:1087-1091` — `preparedRouteResidualRefusal
  Codes` keeps the B execution codes (`dst-read-formula`,
  `analytic-shape-multi-key`; `multi-key-component` stays; `multi-render-dst-copy`
  left in Task 4 as covered).

Task 9 Step 2 re-ran the boundary guard, the four Kanvas guards, and the five
GPU suites (`--rerun-tasks`) — results in §0.2, all matching or extending the
Task 8 record.

## 9. Commit trail (FP-11, through Task 9)

`6dfe7e5ef` (plan) · `7e763c869` (Task 1: gap map + green baseline) ·
`29949f297` + `f0b95fb4b` (Task 2: retained-session leading-blur clear) ·
`ed46a95fd` (Task 3: hairline point lowering) · `b13d01a70` +
`9c0338592` (Task 4: multi-render dst-copy direct lane) · `d7c15b4b0` +
`178c681fa` + `3bd78e180` (Task 6: uniform-layout pass split + residual pin) ·
`554986cfe` + `df5e084e0` (Task 7: analytic rect clips on the mask-blur
composite) · `39e55d60a` (Task 8: justified terminal refusal
re-documentation) · **closure commit** (Task 9: evidence finalization +
roadmap FP-11 completed + FP-12 transfers). Task 5 landed no commit
(recording fix verified then reverted to keep the stable typed refusal, §3).

## 10. FP-12+ transfers (residual-refusal tracking)

Bounded future work tracked for FP-12+ (the FP-12 roadmap entry itself stays
"Current visual and performance evidence"; this list is a residual-refusal
tracking note):

- analytic clips over non-direct shading geometry (4 at closure HEAD; 2 at
  FP-09 — the +2 are the Task 3 DrawPoint/DrawPoints ALPHA_MASK × DST
  re-route);
- dst-read formula on mapped routes (2);
- analytic-shape multi-key dst-read (2);
- complex-clip blur / `core_primitive_clip_producer_authority`;
- path destination-read (60; reclassified A→B in Task 5 — requires a
  path-stencil stencil-continuation feature, §3);
- multi-uniform-layout analytic-clip 64/160 split residual (the unwired
  split; 199 blend rows stay on `mixed_uniform_layouts` at closure HEAD, §4).
