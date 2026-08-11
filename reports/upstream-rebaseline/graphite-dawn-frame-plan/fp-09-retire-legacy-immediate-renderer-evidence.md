# FP-09 Retire Legacy Immediate Renderer — Terminal/Prepared Evidence

Status: **final** — Task 10 closure (full regression, stale-pin re-points, roadmap FP-09 completed).

Branch: `codex/graphite-dawn-frame-fp09`, HEAD `571ba6e23` (Task 5 route collapse); closure commit adds the Task 10 evidence and roadmap update.

## 1. Evidence runs

Red capture (Task 6 Step 1):

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

- Run 1 (2026-08-10): 1905 tests, 513 failed (498 blend + 15 clip). Codes grouped
  from the JUnit XML `GPUPreparedSurfaceTerminalException` messages.
- Run 2 (blend suite only, same day): 1864 tests, 497 failed — identical per-code
  counts; the single extra Run-1 failure was the known environmental
  `failed.surface.prepared.session-close` flake on `Clear/EXCLUSION/UNCLIPPED`
  (passes in isolation / on re-run; no assertion weakened for it).
- Run 3 (clip suite only, same day): 41 tests, 15 failed — identical per-test codes.

## 2. Per-code case counts (the Task 6 evidence)

| code | blend suite | clip suite | total | family |
| --- | --- | --- | --- | --- |
| `unsupported.core_primitive.point.hairline_exact_lowering` | 174 | 1 | 175 | hairline points |
| `unsupported.recording.core_primitive_mixed_uniform_layouts` | 201 | 1 | 202 | mixed uniform layouts |
| `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | 2 | 0 | 2 | analytic clip, non-direct geometry |
| `unsupported.recording.core_primitive_analytic_shape_clip` | 0 | 7 | 7 | analytic shape under analytic clip |
| `unsupported.native-core-primitive.multi-render-dst-copy` | 60 | 0 | 60 | dst-read multi-render (rect/color) |
| `unsupported.native-core-primitive.analytic-shape-multi-key` | 0 | 2 | 2 | AA multi-key dst-read shapes |
| `unsupported.native-core-primitive.dst-read-formula` | 0 | 2 | 2 | single-op dst-read formula (mapped routes) |
| `unsupported.native-core-primitive.path-destination-read` | 60 | 0 | 60 | path/drrect dst-read (designed refusal, §4) |
| `unsupported.core_primitive.rect.analysis_authority_missing` | 0 | 2 | 2 | mask-blur rect authority |
| `failed.surface.prepared.session-close` | 1 | 0 | 1 | environmental flake (Run 1 only) |
| **total** | **498** | **15** | **513** | |

Cross-reference to FP-08 evidence §3 (the executed-then-reverted collapse):
the reverted run reported 5 code families (`destination_read.required` 630,
`native-core-primitive.blend` 330, `hairline_exact_lowering` 168,
`mixed_uniform_layouts` 92, `analytic_clip_non_direct_geometry` 52). The
post-collapse route surfaces the same three Task-4 recording codes
(hairline / mixed-uniform / analytic-clip-non-direct) plus the Task-3c
dst-read residuals (`multi-render-dst-copy`, `analytic-shape-multi-key`,
`dst-read-formula`, `multi-key-component` documented but not exercised by
these suites), and two stragglers (§4). Case counts differ from the reverted
run because the current matrix fixture set is smaller than the reverted
full-suite aggregate.

Count reconciliation: the FP-08 counts above (hairline 168, mixed-uniform 92,
analytic-clip 52) are the historical full-suite aggregate of a different matrix
era, while this report's per-code table is the measured per-test distribution
across the blend/clip suites (hairline 175, mixed-uniform 202, analytic-clip 2
in this table). Per-family counts therefore differ in both directions; the
measured Task 6 numbers are authoritative for the current matrix (used in §9,
§15-§16 and the FP-11 transfer lists), and the FP-08 figures remain labeled as
historical where quoted.

## 3. Blend suite re-pointing (GPUAllApiBlendSurfaceTest)

`expectedPreparedProductRoute` now maps (per the evidence run):

| family | contexts | code |
| --- | --- | --- |
| DrawPoint, DrawPoints | all 29 modes x 3 contexts | `unsupported.core_primitive.point.hairline_exact_lowering` |
| DrawRRect | all 29 modes x 3 contexts | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawRect, DrawColor | ALPHA_MASK, DST | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` |
| DrawRect, DrawColor | ALPHA_MASK, other modes | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawRect, DrawColor | UNCLIPPED/SCISSOR, artistic + PLUS | `unsupported.native-core-primitive.multi-render-dst-copy` |
| DrawRect, DrawColor | UNCLIPPED/SCISSOR, fixed/SCREEN/MODULATE | Prepared (pixel oracle) |
| DrawPath, DrawDRRect | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` |
| DrawPath, DrawDRRect | UNCLIPPED/SCISSOR, artistic + PLUS | `unsupported.native-core-primitive.path-destination-read` (§4) |
| DrawPath, DrawDRRect | UNCLIPPED/SCISSOR, fixed/SCREEN/MODULATE | Prepared (pixel oracle) |
| Clear | all | Prepared (mode is ignored by design) |

The dead `ProductRouteExpectation.LegacyRefused` branch and data object were
deleted; the terminal branch still asserts `decisions == [Terminal(code)]` and
that NO destination readback was allocated before refusal.

## 4. Stragglers (unexpected codes, classified explicitly)

- `unsupported.native-core-primitive.path-destination-read` (60, designed
  refusal added by the Task 6 fix commit): DrawPath/DrawDRRect with a
  dst-read blend outside an analytic clip. Root cause of the original
  `invalid.surface.prepared.frame-build-contract` wrapper: the snapshot
  planner (`buildCorePrimitiveDestinationSnapshotPlans`) is family-agnostic
  and records a `TextureCopy` consumer ref at the BASE packet id, but the
  assembler lowers path-stencil (`StencilEdgeFan`) sources into
  producer/cover packets with fresh ids
  (`<base>.path-stencil-cover`), so the dst-read cover can never be resolved
  (`renderByPacketId.getValue(plan.packet.packetId)` throws
  `NoSuchElementException`, GPUCorePrimitivePreparedFrameTaskListBuilder.kt
  :3341) and the frame-builder catch-all wrapped it as
  `invalid.surface.prepared.frame-build-contract`. Even with the consumer
  resolved, the dst-read formula forces the cover into its own render pass,
  which the path-stencil authority rejects, so the shape cannot execute
  prepared. The recording authority now refuses it by name before the
  assembler. `invalid.surface.prepared.frame-build-contract` remains only as
  the catch-all guard for genuine builder bugs (pinned by
  `GPUPreparedSurfaceFrameBuilderTest.unexpected construction exception…`).
- `unsupported.recording.core_primitive_analytic_shape_clip` (7, clip suite):
  designed refusal at the task-list builder ("Prepared analytic shapes require
  NoClip or ScissorOnly") reached when a rrect/analytic-shape frame sits under
  an analytic (AA) clip without a mixed-layout predecessor.
- `unsupported.core_primitive.rect.analysis_authority_missing` (2, clip suite):
  designed refusal at the core-semantic builder when the recording analysis
  record carries no rect route authority (mask-blur rect frames).

## 5. Clip suite re-pointing (GPUClipCoverageSurfaceTest)

15 pixel-oracle tests re-pointed to terminal assertions with the exact codes
from the run; the 7 stale "…before legacy" test names were renamed; the
identical `assertPreparedImageTerminal`/new helper was merged into one
`assertTerminal`. Composite refusals (`unsupported.composite.*`) untouched.

## 6. Green verification (Task 6 Step 3)

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" \
  --tests "*GPUClipCoverageSurfaceTest" --no-parallel --console=plain
```

- Green run 1: 1905 tests, BUILD SUCCESSFUL (1m 5s).
- Forced re-run 1 (`--rerun-tasks`): 1 failure —
  `failed.surface.prepared.session-close` on `DrawVertices/SRC_OUT/UNCLIPPED`.
- Forced re-run 2 (blend suite, `--rerun-tasks`): 1 failure —
  `failed.surface.prepared.session-close` on `Clear/DST/SCISSOR`.
- Re-runs of the flaked cases without the full-suite churn: BUILD SUCCESSFUL.

Conclusion: the `failed.surface.prepared.session-close` flake lands on a
different random non-dst-read frame whenever the full suite re-runs under GPU
churn and passes in isolation — the documented environmental behavior. No
assertion was weakened for it.

## 7. Task 6 follow-up (path dst-read residual resolution)

The 60 `invalid.surface.prepared.frame-build-contract` cases were resolved in
commit (Task 6 fix): the recording authority now refuses path-stencil
dst-read frames with the designed `unsupported.native-core-primitive.path-
destination-read` code, and the blend suite re-points those cases to it.
Verification:

```bash
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" \
  --tests "*GPUClipCoverageSurfaceTest" --tests "*GPUPreparedSurfaceFrameBuilderTest" \
  --no-parallel --console=plain
```

- 1864 + 41 + 30 tests, 0 failures, BUILD SUCCESSFUL.
- `grep frame-build-contract` in tests now matches only the genuine-bug
  catch-all pin (`GPUPreparedSurfaceFrameBuilderTest`); the code is no longer
  produced for dst-read core frames.

## 8. Before/after legacy map (Task 1 inventory → final state)

The Task 1 before-snapshot (`reports/fp09-legacy-map.txt`, committed at `42ef8a093`)
lists every production `Legacy` site at the FP-08 tip `accaea616`:

| site | retired by |
| --- | --- |
| `GPUPreparedSurfaceFrameGate.kt:28,60-61,67-68` (`Eligibility.Legacy`, `legacy.surface.prepared.flush-snapshot`/`empty-frame`) | Task 5 (`b1163ae9b`) — eligibility variant deleted; `FlushAndSnapshot`/empty frames become state-event `Candidate` → executor `NoOp` |
| `GPUPreparedSurfaceProductRouter.kt:36,61,64,152` (`Route.Legacy`, `hasTerminalPreparedFamily`) | Task 5 — `BeforePreparedEntryRefused` → always `Terminal`; `hasTerminalPreparedFamily` deleted |
| `GPUPreparedSurfaceProductEntry.kt:21,56,70-72` (`GPUPreparedSurfaceLegacyPort`, `RouteDecision.Legacy`, `legacyPort`) | Task 5 — port interface and legacy branch deleted |
| `GPURenderer.kt:714,724-725,729` (`preparedSurfaceLegacyPort`, `renderViaGpuLegacy`) | Task 7 (`292861921`) — legacy renderer body (~2,300 lines) deleted |
| `GPURenderer.kt:87-696, 3043-3262` (legacy-only helpers + CPU text-atlas builders) | Task 7 |

Final state (Task 10 Step 6): `rg` over `kanvas/src/main` for
`GPUPreparedSurfaceProductRoute.Legacy|GPUPreparedSurfaceEligibility.Legacy|GPUPreparedSurfaceRouteDecision.Legacy|GPUPreparedSurfaceLegacyPort|renderViaGpuLegacy|legacy.surface.prepared|hasTerminalPreparedFamily|legacyPort`
returns **nothing**; the second sweep
(`GPUClipExecution|renderWithClip|GPUClipRouteTrace|LayerScissorOffscreenTarget|buildTextAtlasMesh|GPUClipUsePrepass|GPUClipCoverageFrameCache|expandPicturesForGpuReplay`)
returns only substring matches of the live prepared clip-execution contracts
(`GPUClipExecutionPlan`/`GPUClipExecutionGeometry`/`GPUClipExecutionIdentityBuilder`,
all in `gpu-renderer/.../clips/GPUClipExecutionPlan.kt`) — zero matches for the
deleted `GPUClipExecution.kt` symbols (`renderWithClip`, `GPUClipRouteTrace`,
`GPUClipSourceSurface`, `GPUClipRouteContext`, `copyForClipSource`,
`GPUClipDestinationReadComposer`, …). `GPUPreparedSurfaceLegacyAbsenceTest` pins
all 16 retired tokens (the 4 FP-08 tokens + the 12 FP-09 tokens) out of
`surface/gpu` production sources.

## 9. Per-family decision table (the FP-09 policy, as executed)

| family | plan §2 code | cases (Task 6 evidence) | decision | FP-11 note |
| --- | --- | --- | --- | --- |
| 1. destination-read blends | `unsupported.destination_read.required` (630) | covered | **Prepared coverage** (Tasks 3/3b/3c): `ShaderBlendWithDstRead` + GPU-owned `TextureCopy` + `GPUBlendFormulaLibrary` on core primitives; `GPUAllApiBlendSurfaceTest` routes render prepared with `route:destination-read:<op>` evidence (`reason == "gpu-copy-then-formula"`) and match the CPU pixel oracle | — |
| 2. non-SrcOver core blends | `unsupported.native-core-primitive.blend` (330) | covered | **Prepared coverage** (Task 2): `FixedFunctionBlend`/`ShaderBlendNoDstRead` admission via the direct-native-route classifier + multi-pipeline per pass (Task 3b) | — |
| 3. hairline points | `unsupported.core_primitive.point.hairline_exact_lowering` (175) | terminal | **Stable terminal refusal** (Task 4 policy; pinned by `GPUFramePathApiInventoryTest.kt:735,751` and the router matrix) | hairline points |
| 4. mixed uniform layouts | `unsupported.recording.core_primitive_mixed_uniform_layouts` (202) | terminal | **Stable terminal refusal** — multi-layout pass splitting is a recording feature (emission `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1602,2104`) | mixed uniform layouts |
| 5. analytic clip non-direct geometry | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (2) | terminal | **Stable terminal refusal** (emission `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1994`) | analytic-clip non-direct geometry |
| 6. multi-render dst-copy | `unsupported.native-core-primitive.multi-render-dst-copy` (60) | terminal | **Stable terminal refusal** — the prepared lane executes only single-render dst-read frames (snapshot scheduled before its one pass); destination-then-consumer frames refuse (executor residual, documented at `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` `validateCorePrimitiveDestinationCopy`) | multi-render dst-copy |
| 7. analytic-shape multi-key | `unsupported.native-core-primitive.analytic-shape-multi-key` (2) | terminal | **Stable terminal refusal** — AA multi-key dst-read shapes | analytic-shape multi-key |
| 8. dst-read formula mapped routes | `unsupported.native-core-primitive.dst-read-formula` (2) | terminal | **Stable terminal refusal** — single-op dst-read formula frames on mapped routes | dst-read formula |
| 9. path/drrect dst-read | `unsupported.native-core-primitive.path-destination-read` (60) | terminal | **Designed refusal** (§4 below): path-stencil dst-read cannot resolve the snapshot consumer ref; the recording authority refuses by name before the assembler | path destination-read |
| 10. mask-blur rect frames | `unsupported.core_primitive.rect.analysis_authority_missing` | terminal | **Stable terminal refusal** (evidence §4): top-level mask-blur frames carry no rect route authority after the legacy mask machinery deletion (Task 8) | mask-blur/filter gap (FP-11 "filter") |
| 11. mask-blur path/rrect | `unsupported.pipeline.capability_missing` (path), `invalid.recording.core_primitive_semantic_authority` (rrect) | terminal | **Stable refusals** — the path frame refuses at the FillPath capability gate; the rrect frame refuses at the recording authority | mask-blur/filter gap (FP-11 "filter") |

Coverage for families 1-2 required the Graphite-faithful multi-pipeline work
(Tasks 3b/3c — see §14): a single prepared pass now materializes N structural
pipelines with per-pipeline bind groups, exactly like Graphite's `DrawPass`
pipeline array. The mixed-key gate (`core_primitive_mixed_pipeline_keys`) was
removed in Task 3c; frames that STILL mix incompatible uniform layouts refuse
with the family-4 code rather than rendering on a single-key pass.

## 10. Task 6 evidence run recap (red capture + re-points)

§1-§7 above record the Task 6 capture: red run 513 failures grouped into the
per-code table (§2), the blend/clip suite re-points (§3, §5), the straggler
classification (§4), the green verification (§6), and the path-dst-read
residual resolution (§7). Nothing in those sections changed during Task 10;
the full-run discovery in §15 (18 stale legacy pins in three files the Task 6
inventory did not cover) is the only re-point extension the full-run proof
added. Task 11 (Phase 4b amendment) then RESTORED top-level mask blur: 11 of
the 18 pins assert prepared with the CPU pixel oracle, 1 re-points to the now
reachable budget gate, and 6 stay terminal (classified per-case in §15).

## 11. No-legacy-fated-frame regression proof (Task 3c fix 3 verification)

The route collapse (`b1163ae9b`) guarantees **no frame is fated to the legacy
route**: `BeforePreparedEntryRefused` routes to `Terminal` unconditionally, and
the `hasTerminalPreparedFamily` split is deleted. Pinned by
`GPUPreparedSurfaceProductRouterTest.before-entry refusals for the terminal
families are never legacy` — a matrix of the three Task-4 codes plus a generic
core refusal, each returning `GPUPreparedSurfaceProductRoute.Terminal` with the
exact code.

The Task 3c executor residuals are **designed refusal codes, not hidden
fallbacks**: the dst-read core lane executes only frames whose snapshot copy is
scheduled before the frame's single render pass; destination-then-consumer
frames refuse with `unsupported.native-core-primitive.multi-render-dst-copy`
(the limitation is documented at
`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.validateCorePrimitiveDestinationCopy`,
and the SolidRect destination-copy lane carries the background-then-consumer
pixel oracle in `GPUWgpu4kDestinationCopyFrameSmokeTest`). A `rg` sweep for
legacy identifiers in `gpu-renderer/src` executor sources returns nothing.

## 12. NoOp / flush-snapshot parity proof

`FlushAndSnapshot` and empty/state-only frames classify as `Candidate`
(`GPUPreparedSurfaceFrameGateTest.empty and state only frames classify as
candidate and complete as noop`, `flush snapshot frames classify as candidate
state event frames`) and the executor's `GPUPreparedSurfacePreBackendNoOpGate`
returns `NoOp` with zero native work; `completeNoOp` returns transparent
zero-filled pixels for `ReadbackRgba` — exact parity with the legacy renderer's
cleared-target result (pinned in `GPUPreparedSurfaceFrameExecutorTest`). The
`legacy.surface.prepared.flush-snapshot`/`legacy.surface.prepared.empty-frame`
codes no longer exist (absence guard pins `legacy.surface.prepared`).

## 13. Guard-retention proof (surviving symbols + pinned tests)

| surviving symbol | pinned by |
| --- | --- |
| `DisplayOp.coreRoutePreflightRefusalReason` (`nested_vertices` guard) | `GPUPreparedSurfaceProductRouterTest.kt:295-301` (exact `unsupported.picture.nested_vertices` code for vertices/meshes + null preflight for other families) — zero production callers at HEAD, unit pin only; the composite capture refuses vertices/meshes children with its own `unsupported.composite.operation` (KDoc corrected in Task 10) |
| `DisplayOp.coveragePlaneTask4RefusalOrNull` | plan-mandated named boundary for future visual operations |
| `DisplayOp.DrawPicture.picturePreflightRefusalReason` + `Picture.containsLayer` + `SaveLayerRec.gpuCompositePreflightRefusalOrNull` | `GPUPreparedCompositeCaptureSemanticTest` (composite capture preflight semantics), `GPUPreparedCompositeFrameRouteIntegrationTest` |
| `GPUOpMapper.withPictureReplayState`/`clipForPictureReplay`/`transformForPictureReplay` | prepared composite capture (`GPUPreparedCompositeCapture.kt:323`) — `expandPicturesForGpuReplay` deleted (Task 8) |
| `unavailable.surface.prepared.runtime-capabilities` (FP-08 rename) | untouched |

Guard verification (Task 10 Step 3): `GPUPreparedSurfaceProductRouterTest`
(15) + `GPUPreparedCompositeCaptureSemanticTest` (19) +
`GPUPreparedCompositeFrameRouteIntegrationTest` (8) + `GPUAllApiBlendSurfaceTest`
(1,864) + `GPUClipCoverageSurfaceTest` (41) = **1,947 tests, 0 failures**.

## 14. Graphite C++ evidence (multi-pipeline per-pass — the FP-09 amendment rationale)

Verified at `/Users/chaos/workspace/kanvas-forge/skia-main` (2026-08-08, recorded
in the plan amendment `f45d4fc6f`): a Graphite `DrawPass` holds an ARRAY of
pipelines with draws referencing them by index via `BindGraphicsPipeline`
commands emitted MID-PASS (`DrawPass.h:103-113`, `DrawCommands.h:108-109`,
`DrawList.cpp:203-206`); Dawn executes `SetPipeline` inside one render pass
(`DawnCommandBuffer.cpp:675-679, 775-784`); blend mode is per-pipeline
(`GraphicsPipelineDesc.h:27-43`), and `RenderPassDesc.h:87-91` anticipates
mixed pipelines in one pass — Graphite never splits a pass on blend mode.
Destination reads use a per-pass `kTextureCopy` decision at flush
(`Device.cpp:2176`), the GPU-only copy is ordered BEFORE the consuming
`RenderPassTask` in the same encoder (`DrawContext.cpp:198-204, 270-315`,
`Image_Graphite.cpp:113-137`), and the dst texture+sampler append at the END of
the fragment bind group (`DawnCommandBuffer.cpp:927-938`) with
`rebindTexturesOnPipelineChange` per-pipeline bind groups
(`DrawList.cpp:140-174`). FP-09's Tasks 3b/3c implement exactly this model for
the prepared core lane: N structural pipelines per direct pass, per-pipeline
bind groups, dst bindings at the fragment-layout end, dst copy before the
consuming pass.

## 15. Full-run regression proof (Task 10 Step 2) and test-score deltas

Before (FP-08 close, `accaea616`, FP-08 evidence):
`:kanvas:test` 3,230/3,230 green; `:gpu-renderer:test` 3,257 tests, 2 failed
(both documented pre-existing).

After (FP-09 close, Task 10):
`:kanvas:test` **3,210 tests, 0 failures, 0 errors, 0 skipped** (count drops
because Task 9 deleted the four legacy-pinning test files); `:gpu-renderer:test`
**3,273 tests, 2 failed** — both documented pre-existing and unchanged:
`GPURendererPackageBoundaryTest` package-boundary case (exactly 20 cycle
violations, 0 rule violations) and `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`
(AA-4x partial-premultiplied red edge, `assertPartialPremultipliedRedEdge`,
reproduces at base SHA).

Full-run discovery — 18 stale legacy pins in three files missed by the Task 6
inventory were re-pointed in Task 10 to the designed terminal codes (identical
failures reproduced at pristine HEAD `571ba6e23`; all 21 tests in the three
classes pass at the FP-08 tip `accaea616`, proving the frames were
legacy-rendered pre-FP-09):

| file | tests re-pointed | codes |
| --- | --- | --- |
| `GPUClipAdvancedBlendSurfaceTest` | 3 | `unsupported.recording.core_primitive_mixed_uniform_layouts` (AA-clip dst-read; scissor dst-read), `unsupported.core_primitive.rect.analysis_authority_missing` (mask-blur dst-read) |
| `GPUMaskBlurSurfaceTest` | 11 | `unsupported.core_primitive.rect.analysis_authority_missing` (top-level mask-blur rect frames), `unsupported.pipeline.capability_missing` (triangle), `invalid.recording.core_primitive_semantic_authority` (rrect) |
| `GPUPathClipRegressionTest` | 4 | `unsupported.recording.core_primitive_mixed_uniform_layouts` ×3, `unsupported.native-core-primitive.multi-render-dst-copy` ×1 |

Every re-point asserts the exact terminal code with the fixture retained; the
AA-clip dst-read re-point additionally asserts no destination snapshot is
allocated before refusal (`destinationReadbackSnapshots`/`destinationCopies`
unchanged). Mask blur inside a saveLayer scope still renders through the
composite capture's `GPUPreparedMaskFilterLowerer` (FP-07 composite route,
unchanged).

Task 11 (Phase 4b amendment, user decision) restored top-level mask blur in
the prepared route: the top-level blur lane (semantic admission + closed
five-stage chain recording + native materialization, commit `3e2a71b5e`)
flips the Task 10 re-points back to prepared:

| file | Task 10 re-points | Task 11 final |
| --- | --- | --- |
| `GPUMaskBlurSurfaceTest` | 11 terminal | 10 prepared (CPU pixel oracle vs `TopLevelMaskBlurPixelOracle`, tolerance 24/255: NORMAL/SOLID/INNER/OUTER rects incl. σ48 reduced-resolution, triangle path, rrect, SRC_OVER/SRC composites, DARKEN via the copy-then-formula lane, device-rect scissored composite, wide-open and decal-edge frames) + 1 budget re-point (`unsupported.mask-filter.blur.intermediate-budget`, the legacy budget gate is reachable again) |
| `GPUClipAdvancedBlendSurfaceTest` | 3 terminal | 1 prepared (DARKEN mask blur over destination via copy-then-formula) + 2 stay terminal (AA-clip dst-read and scissor dst-read: no mask filter — genuine mixed-uniform-layouts family) |
| `GPUPathClipRegressionTest` | 4 terminal | 4 stay terminal (rect+clipped path, dst-in path, darken rect, DIFFERENCE path: no mask filter — genuine mixed-uniform-layouts / multi-render-dst-copy families) |
| `GPUClipCoverageSurfaceTest` (blur pins) | 2 terminal (`analysis_authority_missing`) | 2 stay terminal, re-classified to `invalid.preflight.core_primitive_clip_producer_authority` (complex clip + DARKEN + blur: the coverage-mask clip producer route rejects the blur composite consumer — lane scope) |

The 10 prepared rect/rrect/path cases and the two DARKEN cases compare GPU
pixels against the documented CPU oracle (legacy dispatcher math: MaskBlurPlanner
plans, blurKernelUniform kernel with decal sampling, style formulas, encoded-space
fixed-function SRC_OVER/SRC and linear formula DARKEN composites).

Documented deviation — `GPUSaveLayerCompositeRegressionTest` was RE-POINTED,
not deleted: the plan File Map listed the file for deletion (it pinned
`LayerScissorOffscreenTarget`/`LayerBounds`), but Task 9's evidence showed its
remaining cases were live prepared-route coverage (bounded saveLayer pixel
oracles and prepared-route terminal-refusal pins), so the five
legacy-pinning tests were deleted (227 lines; the scissor/recorder-forwarding
cases that exercised the deleted layer-target machinery) and the 22 live tests
were kept. The file pins the composite route's bounded-layer rendering and its
documented refusal codes; its Task 10 review additionally removed four dead
private helpers.

## 16. FP-11 tracking notes (terminal families, per the roadmap)

Per `active-todo.md` FP-11 "FP-09 transfers", each with its emission site and
case count:

- exact hairline point lowering — `unsupported.core_primitive.point.hairline_exact_lowering` (`GPUCorePrimitiveSemanticBuilder.kt:409, 411, 465`; 175 cases);
- multi-uniform-layout direct passes — `unsupported.recording.core_primitive_mixed_uniform_layouts` (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1602, 2104`; 202 cases);
- analytic clips over non-direct shading geometry — `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1994`; 2 cases);
- dst-read formula on mapped routes — `unsupported.native-core-primitive.dst-read-formula` (2 cases);
- multi-render dst-copy (destination-then-consumer dst-read frames) — `unsupported.native-core-primitive.multi-render-dst-copy` (60 cases);
- analytic-shape multi-key dst-read — `unsupported.native-core-primitive.analytic-shape-multi-key` (2 cases);
- path destination-read — `unsupported.native-core-primitive.path-destination-read` (60 cases; §4);
- complex-clip mask blur — `invalid.preflight.core_primitive_clip_producer_authority` (2 cases; Task 11 lane scope: the blur composite applies NoClip or integer ScissorOnly clips; coverage-mask and analytic clips over the blur composite stay terminal).

## 17. Known environmental flake documentation

`failed.surface.prepared.session-close` continues to land on a different random
non-dst-read frame whenever a GPU-heavy suite re-runs under churn. Observed
again during Task 10 (the guard suite run, then 4 further frames on an isolated
`GPUAllApiBlendSurfaceTest` re-run under elevated machine GPU pressure); a
second isolated re-run passed 1,864/1,864, and the full `:kanvas:test`
aggregate passed 3,210/3,210 with zero failures. No assertion was weakened for
it; the class passes in isolation on re-run.

### GPUMaskBlurDispatch fate (Task 11)

`GPUMaskBlurDispatch.kt` remains orphaned (zero production consumers) but is
superseded-not-deleted: its `MaskBlurPlanner` / `blurKernelUniform` /
localization math is the planning and kernel authority REUSED by the prepared
top-level blur lane, and `GPUMaskBlurDispatchTest` pins that math (plan bounds,
halo, scale, budget gate, local command lowering). The legacy `MASK_BLUR_*`
WGSL survives as the port source for the lane's blur/style/composite modules.
The deletion itself remains tracked with the broader legacy retirement.

### Task 11 review-fix saga (2026-08-11) — root causes, for the record

The DARKEN fix round (commits `af0193617` + `e8031c6c2` + `318204d9e`)
surfaced two distinct root causes, both worth recording:

1. **Leftover debug probes from an interrupted agent session.** The fix-round
   debugger left a CONSTANT return inside the blit shader
   (`return vec4f(0.5, 0.25, 0.0, 1.0);` = the observed `(128,64,0)` snapshot
   corruption) and a mask-probe return in the composite WGSL (the observed
   `cov×255` gray output). The blit approach itself was then removed in favor
   of the original native `copyTextureToTexture` with a TARGET-sized layout
   (the reviewer's Critical-1 fix: the recording's target-bounds copy
   authority), which is the committed state. Lesson: interrupted GPU-debugging
   sessions can leave probe code that looks like a deliberate change.
2. **Double quotes inside a WGSL `//` comment break the naga parser.**
   The sRGB-decode rationale comment added to `MASK_BLUR_COMPOSITE_DST_WGSL`
   contained the phrase `"complete"` (with quotes); wgpu-naga's lexer treats
   the quote as a string start inside the comment, consuming the rest of the
   module and failing with `parsing error: expected statement, found ""` at
   the module tail — surfaced as a hard SIGABRT in
   `_wgpuDeviceCreateShaderModule` (uncaptured-error panic), which killed the
   test worker (exit 134) before any test ran. Fix `318204d9e` removed the
   quotes. Lesson: NEVER use double quotes inside WGSL string-literal comments.

### Two-GPU environment note (flake context)

This Mac carries two Metal GPUs: Intel UHD Graphics 630 (integrated) and AMD
Radeon Pro 5500M (discrete). The `failed.surface.prepared.session-close`
flake + the worker SIGABRTs observed during the Task 11 fix round occurred
under heavy multi-worker GPU churn; a machine reboot alone did NOT clear the
crash (it was the naga parse panic above, not the flake). The flake class
itself (`session-close` on random non-dst-read frames, green in isolation)
remains documented as environmental; whether wgpu-native's adapter selection
between the two GPUs contributes is untracked and worth a wgpu4k ticket.

## 18. Commit trail (FP-09)

`42ef8a093` (inventory) · `9dcc4d36c`+`e5263bed4` (Task 2) · `9799fdeb2` (Task 3)
· `f45d4fc6f` (plan amendment) · `efbd9332a`+`e0d164824` (Task 3b) ·
`b17251b2c`+`746d97f93`+`1dbf2e5f7` (Task 3c) · `b1163ae9b` (Task 5) ·
`56cf93f30`+`ac3b989a4`+`469a850bb` (Task 6) · `292861921` (Task 7) ·
`6c44db0e9` (Task 8) · `4dc764bc8`+`571ba6e23` (Task 9) · Task 10 closure commit
(`56cf93f30`..`00c6327b4`) · `e0e58137e` (Task 11 red tests + pixel oracle) ·
`3e2a71b5e` (Task 11 implementation) · `docs(surface): fp09 top level mask blur
closure evidence` (Task 11 evidence/roadmap closure).
