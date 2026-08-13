# FP-11 Close Bounded Native-Rendering Gaps — Terminal Refusal Evidence

Status: **Task 8 skeleton** (B-table + justified terminal refusals with pinned
evidence; finalized in Task 9 with the before/after route split and the full
regression proof).

Branch: `codex/graphite-dawn-frame-fp11`, HEAD `df5e084e0` (Task 7), working tree
clean at capture. All `file:line` references below were re-verified at HEAD on
2026-08-13.

## 1. The B-table: justified terminal refusals (cost ≫ value)

FP-11 acceptance requires every unsupported case to retain a stable typed refusal
with a documented cost/value justification. The B family is the set of rows FP-11
re-documents instead of covering: the plan's original four rows (#3 analytic-clip
non-direct geometry, #4 dst-read formula on mapped routes, #6 analytic-shape
multi-key dst-read, complex-clip blur) plus the two real reclassifications
produced during execution — the Task 5 path-destination-read A→B and the Task 6
verify-gate mixed-layout PARTIAL (both verified below in §3-§4).

| code | cases | emission (HEAD) | root-cause level | refusal level | justification (cost ≫ value) |
| --- | --- | --- | --- | --- | --- |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (+ intersection twin) | 2 (FP-09 measured; see §2 for the Task-3 re-route) | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009` (twin `:2016`) | recording (clip authority requires direct shading geometry) | recording | analytic clip over stencil-shaded geometry is a new execution feature; 2 matrix cases |
| `unsupported.native-core-primitive.dst-read-formula` | 2 | `GPUCorePrimitiveNativeRoute.kt:415` (multi-key), `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:917` (single-key) | execution (formula program availability) | execution | scalar-coverage dst-read formula programs on the analytic-shape lane need a separate AA oracle; the covered full-coverage formulas (DARKEN/SCREEN) exist |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 2 | `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1453` | execution (AA coverage semantics unverified) | execution | AA multi-key analytic-shape blend semantics (e.g. CLEAR) unverifiable by the coverage-modulating shader |
| `invalid.preflight.core_primitive_clip_producer_authority` (complex-clip blur) | bounded (2 clip-suite pins + preflighter) | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:883` | preflight (clip-producer/consumer authority not sealed) | preflight | general clip-producer authority, not blur-specific; coverage requires a new clip-execution shape |
| `unsupported.native-core-primitive.path-destination-read` — **RECLASSIFIED A→B (Task 5)** | 60 | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2572` (refusal block `:2556-2571`) | recording (dst-read cover consumer ref + own-pass) | recording | path-stencil execution model cannot express the dst-read shape; a wired cover pass would clear a fresh stencil and blend over its whole bounds (wrong pixels); coverage requires a dedicated stencil-continuation feature (§3) |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` (+ preflight twin) — **verify-gate PARTIAL (Task 6)** | ~141 of 202 remain refused | `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617` (shape+clip mix gate), `:2132` (analytic-clip 64/160 gate); preflight twin **removed** at HEAD | recording/preflight (one uniform layout per pass) | recording | uniform80 split wired and Covered; the analytic-clip 64/160 split stays unwired pending the per-step continuation/ownership design (§4) |
| `invalid.preflight.core_primitive_direct_geometry_resources` — **residual re-point (Task 6)** | 2 | `GPUFramePreflighter.kt:3326` | preflight (direct-resource seal) | preflight | DrawRRect DST rows re-pointed from the mixed-layout code when the split landed; stable typed refusal retained (§5) |
| `unsupported.native-core-primitive.frame-global-pipeline` — **residual re-point (Task 6)** | 28 | `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:151` | execution (frame-global run component identity) | execution | DrawRRect dst-read rows re-pointed from the mixed-layout code; no closed analytic-shape dst-read formula pipeline on the prepared lane (§5) |

## 2. Per-row detail (the plan's original B family: #3, #4, #6, complex-clip blur)

### `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (+ intersection twin) — 2 cases, recording

- Emission: `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009` (clip twin) and
  `:2016` (intersection twin
  `unsupported.recording.core_primitive_analytic_intersection_non_direct_geometry`).
- Case count: 2 measured at the FP-09 evidence run (blend suite, DrawRect/DrawColor
  ALPHA_MASK × DST; FP-09 evidence §2). **Oracle-map gap documented:** the
  intersection twin is NOT in the FP-11 oracle map (`reports/fp11-gap-map.txt`
  has no `intersection` match; the FP-09 record cited the site at `:2013`), so the
  twin rides this B-row with the same justification.
- Re-route note: FP-11 Task 3 removed the hairline-point refusal, so the
  DrawPoint/DrawPoints ALPHA_MASK × DST rows now route onto this same code in the
  blend-suite matrix (`GPUAllApiBlendSurfaceTest.kt:607-611`); the plan's B-family
  count of 2 refers to the FP-09 measured distribution.
- Justification: analytic clip over stencil-shaded (non-direct) geometry is a new
  clip-execution feature, not a gap in the existing direct-shading clip lane; the
  value is 2 matrix cases. Coverage cost (clip-coverage folding for stencil-shaded
  geometry) ≫ value.
- Pinned: `GPUPreparedSurfaceProductRouterTest.kt:470` (terminal-family matrix)
  and the blend-suite route matrix (`GPUAllApiBlendSurfaceTest.kt:1033`).

### `unsupported.native-core-primitive.dst-read-formula` — 2 cases, execution

- Emission: `GPUCorePrimitiveNativeRoute.kt:415` (multi-key) and
  `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:917` (single-key; the FP-09
  record cited `:893` — drifted to `:917` at HEAD).
- Case count: 2 (clip suite, FP-09 evidence §2).
- Justification: the analytic-shape lane's scalar-coverage dst-read formula
  programs would need a separate AA oracle to be verifiable; the full-coverage
  formulas (DARKEN/SCREEN) are already covered on the direct lane. Coverage cost
  (new AA formula programs + oracle) ≫ value at 2 cases.
- Pinned: `GPUClipCoverageSurfaceTest.kt:67` (constant), pins at `:409`, `:433`;
  residual-set membership `GPUPreparedSurfaceFrameExecution.kt:1088`
  (`preparedRouteResidualRefusalCodes`, `:1087-1089`).

### `unsupported.native-core-primitive.analytic-shape-multi-key` — 2 cases, execution

- Emission: `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1453` (the FP-09
  record cited `:1429` — drifted to `:1453` at HEAD).
- Case count: 2 (clip suite, FP-09 evidence §2).
- Justification: AA multi-key analytic-shape blend semantics (e.g. CLEAR) cannot be
  verified by the coverage-modulating shader without a dedicated reference
  oracle. Coverage cost ≫ value at 2 cases.
- Pinned: `GPUClipCoverageSurfaceTest.kt:65` (constant), pins at `:331`, `:354`;
  residual-set membership `GPUPreparedSurfaceFrameExecution.kt:1089`.

### `invalid.preflight.core_primitive_clip_producer_authority` (complex-clip blur) — bounded, preflight

- Emission: `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:883` (clip-producer
  validation `refuse`).
- Case count: bounded — 2 clip-suite pins (`GPUClipCoverageSurfaceTest.kt:175`,
  `:193`) plus the preflighter coverage.
- Justification: the clip-producer authority is a general clip-execution feature
  (it fires for any frame whose clip producer/consumer authority is not sealed),
  not blur-specific; the complex-clip blur family only rides it. Coverage requires
  a new clip-execution shape for the coverage-mask clip producer route, which is
  out of FP-11 scope.
- Pinned: `GPUClipCoverageSurfaceTest.kt:63` (constant), pins at `:175`/`:193`
  (the `:193` pin additionally asserts no destination readback is allocated before
  refusal).

## 3. Reclassification evidence — path destination-read (gap #7, RECLASSIFIED A→B in Task 5)

Plan classification was A (depends on #5, priority 4). Task 5 verified the
recording-level fix (keying the `TextureCopy` consumer ref to the assembled
path-cover packet id) is correct — the destination-contract test went green — but
the path-stencil EXECUTION model cannot express the dst-read shape:

- `GPUFramePreflighter.kt:2401`/`:2434-2441` — path stencil requires exactly ONE
  core render pass (`invalid.preflight.core_primitive_path_stencil`; the Task 6
  split admission only relaxes this to one path pass + direct-only split passes,
  never a second path pass);
- `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:163-174` — the run materializer
  excludes the dst-read component identity from `supportedPathComponents`
  (refusal `invalid.native-core-primitive.frame-global-path-pipeline`,
  `:169-173`);
- the run materializer hardcodes per-run stencil Clear+Discard with no
  stencil-continuation mechanism — a wired cover pass would clear a fresh stencil
  and blend over its whole bounds (wrong pixels, caught by `assertPixelsNear`).

Verification of the A→B flip: with the recording refusal removed, the 60
blend-suite rows regressed to `invalid.preflight.core_primitive_path_stencil`
(execution-level), so the recording refusal was REVERTED to keep the stable typed
code `unsupported.native-core-primitive.path-destination-read` at the recording
authority. No Task 5 commit landed; the refusal block at
`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2556-2571` (return at `:2572`)
is the current HEAD state.

Coverage cost: a dedicated path-stencil stencil-continuation feature — producer
pass stores the fan to the frame-local `pathDepthStencil` D24S8 with
`WritableStencil(Clear, Store)`, cover pass loads it read-only, cross-step pair
admissions (precedent `materializePreparedClipStencilCore`) — plus a second
admitted path render pass in the preflighter and a dst-read path cover pipeline.
Cost ≫ value for this FP (60 cases, but the shape is a new execution feature).

Pinned: `GPUPreparedSurfaceProductRouterTest.kt:471` (terminal-family matrix) and
the blend-suite route matrix (`GPUAllApiBlendSurfaceTest.kt:1037`).

## 4. Reclassification evidence — mixed uniform layouts (gap #2 verify-gate, PARTIAL in Task 6)

The Task 6 verify-gate fired with a deterministic execution residual: the uniform80
split IS wired end-to-end (DrawRRect UNCLIPPED/SCISSOR fixed-blend rows render
Prepared, CPU-oracle verified; the builder 32+80 split test is green), but the
analytic-clip 64/160 split is NOT wired. Bypassing the gate and running the blend
suite's fixed-function non-SRC_OVER analytic-clip rows fails deterministically
with `failed.surface.prepared.session-close` →
`GPUOwnedNativeCloseIncompleteException: prepared-scene-child-cache close remains
incomplete with 1 native owner(s)` (48 rows = 4 APIs × 12 fixed-function
non-SRC_OVER modes; SRC_OVER renders clean).

Root cause and fix: the split-lane materializer's mid-loop refusal path skipped
the lease cleanup, leaking pooled frame slots; that cleanup gap is NOW FIXED in
commit `3bd78e180` (`materializeDirectMultiRenderSplitCore` releases/quarantines
already-materialized run lifecycles and restores the materializer ledger,
`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:5639-5676`, mirroring the
dst-copy lane cleanup at `:5251-5261`). The 64/160 split itself remains unwired
pending a per-step continuation/ownership design, so the refusal stays pinned.

Route split at HEAD (measured Task 6 record, verified in the blend-suite matrix
`GPUAllApiBlendSurfaceTest.kt:606-643`):

- ~141 of the 202 rows remain refused on `unsupported.recording.core_primitive_
  mixed_uniform_layouts`;
- 28 DrawRRect fixed rows + 1 scissor-dst-read clip case flipped to Prepared
  (`GPUClipAdvancedBlendSurfaceTest.kt:78-101` — the scissored dst-read rect now
  rides the admitted two-render dst-copy shape);
- 2 DrawRRect DST rows re-pointed to `invalid.preflight.core_primitive_direct_
  geometry_resources` (§5);
- 28 DrawRRect dst-read rows re-pointed to `unsupported.native-core-primitive.
  frame-global-pipeline` (§5).

Emission at HEAD: `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1617` (the
analytic-shape uniform80 + analytic-clip uniform64/160 mix gate — the gate now
admits uniform80+32 mixes and refuses only the analytic-clip mixes) and `:2132`
(the `activeDirectUniformLayouts > 1` gate re-scoped to the analytic-clip
uniform64/160 case; the gate comment at `:2114-2130` pins the deterministic
residual evidence). The preflight twin `unsupported.preflight.core_primitive_
mixed_uniform_layouts` (plan-time `GPUFramePreflighter.kt:3311`) is REMOVED at
HEAD — verified by `rg` over `gpu-renderer/src` + `kanvas/src` (no matches) — the
preflighter now accepts N direct renders when each owns one layout.

Pinned: `GPUPreparedSurfaceProductRouterTest.kt:469` (terminal-family matrix),
`GPUClipCoverageSurfaceTest.kt:112`, the blend-suite ALPHA_MASK rows
(`GPUAllApiBlendSurfaceTest.kt:1029`), and the clip-suite
`GPUClipAdvancedBlendSurfaceTest.kt:53-57` (AA-clip dst-read stays on the code).

## 5. Residual re-points from the Task 6 split (stable typed refusals, kept)

Two pre-existing refusal codes gained rows when the Task 6 split landed. They are
residual rows of the same re-documentation, not new coverage promises:

| code | cases | emission (HEAD) | rows | note |
| --- | --- | --- | --- | --- |
| `invalid.preflight.core_primitive_direct_geometry_resources` | 2 | `GPUFramePreflighter.kt:3326` | DrawRRect × DST (UNCLIPPED/SCISSOR) | the DST rrect pass cannot exact its shared geometry slab authority after the split; stable typed refusal retained |
| `unsupported.native-core-primitive.frame-global-pipeline` | 28 | `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:151` | DrawRRect × dst-read modes (UNCLIPPED/SCISSOR) | the split dst-read rrect consumer stays on the frame-global pipeline boundary: no closed analytic-shape dst-read formula pipeline on the prepared lane |

Both are pinned by the blend-suite route matrix (`GPUAllApiBlendSurfaceTest.kt:1031,
:1035`). They keep the acceptance contract: stable typed refusal, no destination
readback allocated before refusal.

## 6. Semantic-builder line drift (Task 8 note)

- FP-09/roadmap record: hairline `409/411/465`; plan-time HEAD `f14656988`:
  `557/559/613` (`GPUCorePrimitiveSemanticBuilder.kt`).
- HEAD `df5e084e0` after Task 3: `unsupported.core_primitive.point.hairline_exact_
  lowering` is GONE from the semantic builder — hairline points now lower to the
  canonical one-device-pixel square (`hairlinePointDeviceGeometry`, `:578`/`:624`).
  The surviving round-cap twins are at `:565` (points) and `:681` (strokes);
  `GPUFramePathApiInventoryTest.kt:751` keeps them pinned.
- Same drift class, execution: `dst-read-formula` single-key `:893` → `:917` and
  `analytic-shape-multi-key` `:1429` → `:1453`
  (`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`); path-destination-read
  `:2564` → `:2572`; mixed-layout second gate `:2120` → `:2132`.

## 7. B-pin confirmation run (Task 8 Step 2)

Command:

```bash
./gradlew -F off :kanvas:test --tests "*GPUPreparedSurfaceProductRouterTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

Result: BUILD SUCCESSFUL in 1m 25s (run 2026-08-13 at HEAD `df5e084e0`;
39 actionable tasks: 2 executed, 37 up-to-date). Measured from the JUnit XML:
`GPUPreparedSurfaceProductRouterTest` 15/15 and `GPUClipCoverageSurfaceTest`
41/41 — 0 failures, 0 errors, 0 skipped. WebGPU is present on this host (Intel
UHD Graphics 630 + AMD Radeon Pro 5500M), so the GPU-dependent clip rows ran for
real rather than skipping. The B rows still assert `Terminal(code)`:

- `GPUPreparedSurfaceProductRouterTest.kt:464-475` — the terminal-family matrix
  asserts `GPUPreparedSurfaceProductRoute.Terminal` with the exact B codes
  (`mixed_uniform_layouts`, `analytic_clip_non_direct_geometry`,
  `analytic-shape-multi-key`, `path-destination-read`); the hairline and
  multi-render codes left the matrix in Tasks 3/4 (covered), documented in the
  test comment.
- `GPUClipCoverageSurfaceTest.kt:63, 65, 67` — the three B constants
  (`clip_producer_authority`, `analytic-shape-multi-key`, `dst-read-formula`)
  remain pinned at `:112`, `:175`, `:193`, `:331`, `:354`, `:409`, `:433`;
  the `:193` pin asserts no destination readback is allocated before refusal.
- `GPUPreparedSurfaceFrameExecution.kt:1087-1089` — `preparedRouteResidualRefusal
  Codes` keeps the B execution codes (`dst-read-formula`,
  `analytic-shape-multi-key`; `multi-key-component` stays; `multi-render-dst-copy`
  left in Task 4 as covered).

## 8. Commit trail (FP-11, through Task 8)

`6dfe7e5ef` (plan) · `7e763c869` (Task 1: gap map + green baseline) ·
`29949f297` + `f0b95fb4b` (Task 2: retained-session leading-blur clear) ·
`ed46a95fd` (Task 3: hairline point lowering) · `b13d01a70` +
`9c0338592` (Task 4: multi-render dst-copy direct lane) · `d7c15b4b0` +
`178c681fa` + `3bd78e180` (Task 6: uniform-layout pass split + residual pin) ·
`554986cfe` + `df5e084e0` (Task 7: analytic rect clips on the mask-blur
composite) · **this commit** (Task 8: justified terminal refusal
re-documentation). Task 5 landed no commit (recording fix verified then reverted
to keep the stable typed refusal, §3).
