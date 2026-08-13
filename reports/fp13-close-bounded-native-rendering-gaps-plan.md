# FP-13 — Close the Remaining Native-Rendering Refusal Residual (M86 Fidelity Burn-Down Wave 2) Implementation Plan

Status: `pending` (design validated 2026-08-13; implementation follows after plan approval)

Branch: `codex/graphite-dawn-frame-fp13` (new, from `codex/graphite-dawn-frame-plan-design`). Evidence doc:
`reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-13-close-bounded-native-rendering-gaps-evidence.md`
(produced by this plan).

## Context: validated branch state

- HEAD `codex/graphite-dawn-frame-plan-design` = `ae7a772fb` (FP-12 merge `8de5a000f` + FP-12 GM registry docs
  correction `07a112fba` + this plan `ae7a772fb`).
- Roadmap: FP-12 `completed`; the "FP-12+ transfers" tracking note (physically under the FP-11 entry in
  `active-todo.md`, ~lines 537-549) carries 7 items: analytic clips non-direct (4), dst-read formula
  mapped routes (2), analytic-shape multi-key dst-read (2), complex-clip blur, path destination-read
  (60, requires path-stencil stencil-continuation), the analytic-clip 64/160 split residual (199 blend
  rows on `mixed_uniform_layouts`), and the `colr-v0-color-glyph` scenes oracle divergence (FP-12 §4.3).
- Machine/evidence conventions: Linux, Temurin 25, Vulkan llvmpipe via Xvfb `:99` (GPU suites require
  `DISPLAY=:99`); `./gradlew -F off <tasks> --no-parallel --console=plain`; headless validation stays
  independent of opt-in Kadre.
- Target: `.upstream/target/skia-like-realtime-renderer-target.md` M86 "Fidelity Burn-Down Wave 2" —
  ranked candidate list, root-cause classification, full support/refusal row preservation, explicit
  high-value remediation targets, before/after artifacts for any row counted as "fixed", statement that
  CPU-oracle rows do not count as Skia-comparable fidelity, statement that no global threshold was
  weakened, sprint report of whether renderer fixes were actually applied. Spec:
  `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`.

## 1. Closure inventory (root cause per item, verified at HEAD)

| # | item | rows | root cause (evidence ref) | route |
|---|------|------|---------------------------|-------|
| 1 | dst-read formula on mapped routes | 2 | no analytic-shape dst-read formula pipeline on the prepared lane (fp-11 §5 `frame-global-pipeline` 30-row re-point shares this root) | shader-dst-read + formula |
| 2 | analytic-shape multi-key dst-read | 2 | same root; multi-key analytic shape × dst-read matrix rows | shader-dst-read + formula |
| 3 | complex-clip blur | 2 clip-suite pins (+ preflighter) | `core_primitive_clip_producer_authority`: mask-blur composite under complex (multi-rect) analytic clip refused at the clip producer preflight (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:883`) | composite × analytic clip (extend FP-11 Task 7 ABI) |
| 4 | analytic-clip 64/160 split residual | 199 blend (RRect 29 ALPHA_MASK + Rect/Color 56 ALPHA_MASK non-DST + Path/DRRect 58 ALPHA_MASK + Point/Points 56 ALPHA_MASK non-DST) + clip pins (Coverage 1, Advanced 8, PathClip 1) | unwired 64/160 split: gate `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2132` (comment `:2108-2120`); needs per-step continuation/ownership design (fp-11 §4) + lease cleanup on the split-lane mid-loop refusal (`:5639-5676`, mirror `:5251-5261`) | direct split passes (uniform64/160) |
| 5 | analytic clips over non-direct geometry | 4 (2 pre-FP-09 + 2 from Task 3 DrawPoint/DrawPoints ALPHA_MASK × DST re-route) | `analytic_clip_non_direct_geometry` gate `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009` (twin `:2016`): "Prepared analytic clips require one direct CorePrimitive shading geometry" — analytic clip over non-direct/stencil-shaded geometry is a new execution feature (fp-11 §2) | analytic clip × non-direct passes |
| 6 | path destination-read | 60 | path-stencil execution model cannot express dst-read: recording refusal `TaskListBuilder.kt:2565-2575` (`:2572`), preflighter "exactly one pass" gate `GPUFramePreflighter.kt:2437-2440` (`:2401-2402`), materializer excludes dst-read from `supportedPathComponents` `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:163-174`, per-run stencil Clear+Discard with no stencil-continuation (fp-11 §3) | stencil-continuation feature (see Task 8) |
| 7 | colr-v0 scenes oracle divergence | 1 test (38/4096) | stale harness oracle fills opaque; product lane clears transparent (FP-12 §4.3) | harness only |
| 8 | PipelineTypesTest order-dependence | 1 test | `fn main() {}` parses with the wgsl4k hook installed; assertion depends on fork order (test-isolation flake) | test hygiene + wgsl4k ticket |

The re-pointed rows currently on `invalid.preflight.core_primitive_direct_geometry_resources` (2 DrawRRect
DST + 30 DrawPoint) and `unsupported.native-core-primitive.frame-global-pipeline` (30 DrawRRect dst-read)
have **distinct roots** (fp-11 §5): only the 30 `frame-global-pipeline` rows share root cause 1-2 (no
closed analytic-shape dst-read formula pipeline) and are accepted as Task 3 fallout; the 2 DrawRRect DST
rows fail the split-lane geometry-slab authority ("the DST rrect pass cannot exact its shared geometry
slab authority after the split") and the 30 DrawPoint rows fail the same direct-resource seal ("three
separate point commands make a four-render shape") — both are Task 6 split-resource fallout, verified in
Task 6, not presumed. M86 residual-row denominator (full set): 199 + 60 + 4 + 2 + 2 + 2 (complex-clip
blur pins) + 62 re-points (2 + 30 + 30) + 10 clip pins = **341 rows**, plus the 489 SkiaGmRunner GM
refusals, all enumerated per-row in Task 0.

## 2. Dependency graph (task ordering)

```
Task 0 (M86 wave, evidence) — independent, opens the plan
Task 1 (colr-v0 oracle)     — independent, harness
Task 2 (PipelineTypesTest + wgsl4k ticket) — independent, test hygiene
Task 3 (dst-read formula)   — independent root 1 ─┐
Task 4 (multi-key dst-read) — depends on Task 3 ──┤ (same root)
Task 5 (complex-clip blur)  — independent, reuses FP-11 Task 7 ABI
Task 6 (64/160 split)       — independent of 3-5; mechanical, largest row count
Task 7 (analytic clips non-direct) — depends on Task 6 (uniform64/160); rows whose shading geometry is
                                    stencil-shaded (path case, fp-11 §2) defer to Task 8 (partial edge)
Task 8 (stencil-continuation) — independent of 3-6; largest risk, closes #6
Task 9 (evidence reconciliation + roadmap) — closes the plan
```

Risk ordering rationale: small harness/test tasks first; formula tasks close roots 1-2 (with fallout);
the 64/160 split is mechanical and closes 199 rows early; the stencil-continuation feature (new
execution feature) is isolated last so a scope drift cannot block the rest.

## 3. Pinned suites and baselines that MUST survive this plan

- `GPURendererPackageBoundaryTest` — exactly 20 cycle violations / 0 rule violations. NEVER touched.
- `GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` — documented hardware failure class; passes on
  llvmpipe. NEVER modified.
- `GPUPreparedSurfaceImagePixelTest` — UNORM 1-LSB delta on llvmpipe. NEVER weakened.
- `failed.surface.prepared.session-close` — documented environmental flake; classify with evidence,
  never weaken an assertion for it.
- `GPUPreparedSurfaceLifetimeStressTest` — FP-10 retained-session contract (checkin, never
  close-per-frame) must stay green (6/6 on llvmpipe).
- Guards: `GPUPreparedSurfaceLegacyAbsenceTest`, `GPUPreparedSurfaceProductRouterTest`,
  `GPUPreparedCompositeCaptureSemanticTest`, `GPUPreparedCompositeFrameRouteIntegrationTest` stay green.
- No global similarity threshold may be weakened (M86 threshold policy); family-specific threshold
  changes require justification by upstream/reference behavior.
- Headless validation stays independent of Kadre; `pipelinePmBundle`/RC validators must not resolve
  unpublished Kadre artifacts.

## 4. File map

### New (this plan)

- `reports/fp13-close-bounded-native-rendering-gaps-plan.md` (this doc);
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-13-close-bounded-native-rendering-gaps-evidence.md`;
- M86 wave artifacts (ranked list, root-cause buckets) under the evidence doc; before/after renders +
  diagnostics for each closed row (committed, per FP-11/FP-12 convention).

### Modified (production)

- dst-read formula lane: analytic-shape dst-read formula pipeline wiring (core-primitive run
  materializer, `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`);
- complex-clip blur: clip producer authority + composite analytic-clip ABI (FP-11 Task 7 extension);
- 64/160 split: `GPUCorePrimitivePreparedFrameTaskListBuilder.kt` gate `:2132` (the split-lane lease
  cleanup `:5639-5676` already landed in FP-11 `3bd78e180`; preserved and re-verified, not rewritten);
- analytic clips non-direct: non-direct pass admission (Task 6 frame);
- stencil-continuation: `GPUFramePreflighter.kt` second path pass admission, run materializer
  `supportedPathComponents` + stencil Clear/Store/read-only load, path-cover dst-read pipeline,
  `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2565-2575` refusal replaced by the wired route.

### Modified (tests / harness)

- `GPUAllApiBlendSurfaceTest.kt` (route matrix `:640` re-point: 60 + 199 + 4 + 2 + 2 rows refusal →
  Prepared), `GPUClipCoverageSurfaceTest.kt` / `GPUClipAdvancedBlendSurfaceTest.kt` /
  `GPUPathClipRegressionTest.kt` clip pins, `GPUMaskBlurSurfaceTest.kt` complex-clip cases,
  `GPUPreparedSurfaceProductRouterTest.kt:471` terminal-family matrix;
- scenes harness oracle (`PreparedColorGlyphSceneFrame.composeCpuReference` → transparent clear);
- `PipelineTypesTest.kt:13` → unambiguously invalid WGSL sample; fork-order independence.

### Explicitly NOT touched

- The four documented baselines; `gradle/verification-metadata.xml`; the wgsl4k library (behavior
  surprises go to a wgsl4k ticket, never hidden workarounds); `fp-11` evidence §10 (the transfer list
  lives in `active-todo.md` and the FP-13 doc).

## 5. Task detail

### Phase 0 — Wave M86 + small tasks

**Task 0: M86 burn-down wave (evidence, no renderer code).**
Task 0.0 (input snapshot): run `:integration-tests:skia:test` on `DISPLAY=:99`, commit the JUnit XML
under the evidence dir, and generate a machine-readable residual-row inventory (script over the XML +
blend-suite matrix) — the runner XML is gitignored build output today, so the committed snapshot is the
auditable source for "full row preservation". Produce the ranked candidate list from that snapshot:
**341 residual rows** (199 + 60 + 4 + 2 + 2 + 2 blur pins + 62 re-points + 10 clip pins) per-row
(family, referenceKind cpu-oracle/skia-upstream/test-oracle, expected GPU route, PM value, risk) and
the 489 SkiaGmRunner refusals per-row with root-cause bucket (generated programmatically, not
hand-written). Required M86 statements: CPU-oracle rows do not count as Skia-comparable fidelity; no
global threshold weakened; sprint report "renderer fixes applied" tracked task-by-task. Deliverable:
evidence §1 + committed snapshot.

**Task 1: colr-v0 scenes oracle fix.** `PreparedColorGlyphSceneFrame.composeCpuReference` clears
transparent, aligned with the product lane (`GPULoadStorePlan("clear")`);
`RenderGpuRendererSceneOffscreenMainTest` pixel-exact pin closes (38/4096 → exact). Harness only.

**Task 2: PipelineTypesTest hygiene + wgsl4k ticket.** Replace `fn main() {}` at `PipelineTypesTest.kt:13`
with an unambiguously invalid WGSL sample (parse-level failure, independent of the runtime-effect
wiring hook and of fork order). Open the wgsl4k ticket with minimized evidence
(`fn main() {}` accepted by `parseWgslResult`/`Lowerer` when the hook is installed) per AGENTS.md.

### Phase 1 — Dst-read formula roots (2 rows + fallout)

**Task 3: analytic-shape dst-read formula on the prepared lane.** Wire the closed
`GPUBlendFormulaLibrary` formula + shader-dst-read pipeline onto the core-primitive run materializer
for analytic shapes (rect/rrect), so the 2 mapped-route rows render. Verified fallout (fp-11 §5): the
30 DrawRRect dst-read rows on `unsupported.native-core-primitive.frame-global-pipeline` share this
root and close with the pipeline. The 2 DrawRRect DST rows and the 30 DrawPoint rows on
`invalid.preflight.core_primitive_direct_geometry_resources` do NOT share this root (split-lane
geometry-slab authority) and are owned by Task 6. Rows that stay refused after the pipeline lands are
re-classified and documented, not forced. Before/after per row (refusal code → render + diff +
similarity). Update the blend-matrix rows owned by this task (`GPUAllApiBlendSurfaceTest.kt:640`,
multi-key subset).

**Task 4: analytic-shape multi-key dst-read.** The 2 multi-key rows close with the Task 3 pipeline;
separate task for the before/after evidence and the route-matrix re-point
(`GPUPreparedSurfaceProductRouterTest.kt:471` multi-key code and `GPUAllApiBlendSurfaceTest.kt:640`
multi-key subset). Note: four tasks edit these two matrices (4/6/7/8) — each task removes exactly the
rows its own code closes, so merge ordering stays clean and no stale pin survives the guard run.

### Phase 2 — Composite under complex clip

**Task 5: complex-clip blur.** Extend the FP-11 Task 7 analytic-clip ABI (`CorePrimitiveAnalyticClipBlock`,
coverage multiplication in composite shaders) to the clip producer authority
(`core_primitive_clip_producer_authority`): mask-blur composite under complex (multi-rect) analytic
clip renders prepared. Coverage-mask/stacked clips stay terminal (existing pins). CPU-oracle exact
evidence on `GPUMaskBlurSurfaceTest` new cases.

### Phase 3 — Mixed uniform layouts

**Task 6: wire the analytic-clip 64/160 split.** Re-measure the 199-row distribution at the current
HEAD before touching the gate (fp-11 §0.3 was closure-HEAD data). Extend the split admission from
32+80 to the analytic-clip 64/160 combos at `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2132`
(design note `:2108-2120`), with the per-step continuation/ownership design (fp-11 §4). The
split-lane mid-loop lease cleanup (`:5639-5676`, mirror `:5251-5261`) already landed in FP-11
(`3bd78e180`) — Task 6 preserves and re-verifies that invariant (deterministic run, no
`session-close`/`GPUOwnedNativeCloseIncompleteException`), it is not new work. Verify the Task-6
split-resource fallout: the 2 DrawRRect DST rows and the 30 DrawPoint rows on
`invalid.preflight.core_primitive_direct_geometry_resources` (fp-11 §5 roots: split geometry-slab
authority, four-render direct-resource seal) close or are re-pointed with evidence. Re-point the clip
pins (Coverage 1, Advanced 8, PathClip 1) and the `mixed_uniform_layouts` code in
`GPUPreparedSurfaceProductRouterTest.kt:471`. Acceptance: 199 blend rows leave `mixed_uniform_layouts`
(verified distribution — primary gate), `GPUAllApiBlendSurfaceTest` 1864/1864 stays green (regression
guard), deterministic run with no session-close.

### Phase 4 — Analytic clips on non-direct geometry

**Task 7: analytic clips over non-direct passes.** Using the Task 6 uniform64/160 frame, admit the
analytic clip authority for non-direct shading geometry at `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009`
(twin `:2016`) for the 4 rows (DrawRect/DrawColor/DrawPoint/DrawPoints ALPHA_MASK × DST, fp-11 §2). Rows
whose shading geometry is stencil-shaded (the path case) defer to Task 8's stencil-continuation
(partial edge, §2) — not blocked, re-classified. Verify the 30 DrawPoint rows on
`direct_geometry_resources` close here or under Task 6 (four-render shape seal) and re-point the
`analytic_clip_non_direct_geometry` code in `GPUPreparedSurfaceProductRouterTest.kt:471`. Before/after
per row.

### Phase 5 — Stencil-continuation feature (path destination-read, 60 rows)

**Task 8: path-stencil stencil-continuation + dst-read path cover.** Implement the FP-11 §3 feature
pieces in order:
1. producer pass stores the fan to the frame-local `pathDepthStencil` D24S8 with
   `WritableStencil(Clear, Store)`;
2. `TextureCopy` destination snapshot (existing `GPUWgpu4kDestinationCopySessionCache`) between the
   stencil pass and the cover pass (recording keying to the path-cover packet id already green from
   FP-11 Task 5);
3. cover pass loads the stencil read-only and binds the snapshot through the existing shader-dst-read
   path with the blend formula;
4. cross-step pair admission (precedent `materializePreparedClipStencilCore`) + a second path render
   pass admitted by `GPUFramePreflighter.kt:2437-2440`;
5. run materializer accepts the dst-read identity in `supportedPathComponents` and stops
   Clear+Discard per run for continued cover passes.
Replace the recording refusal `TaskListBuilder.kt:2572` with the wired route; update
`GPUPreparedSurfaceProductRouterTest.kt:471` and the route matrix `GPUAllApiBlendSurfaceTest.kt:640`
for the path-destination-read rows only (60 rows refusal → Prepared; the other codes are re-pointed by
their own closing tasks 4/6/7). Acceptance: 60 rows render, CPU-oracle exact on llvmpipe, native
stencil-cover + dst-read smoke green, no regression on existing stencil paths
(`GPUClipCoverageSurfaceTest`, `GPUPathClipRegressionTest`, clip pins).

### Phase 6 — Closure

**Task 9: evidence reconciliation + roadmap.** Reconcile the Task 0 wave against the closed rows
(every row counted "fixed" carries before/after artifacts; rows still refused carry the re-documented
stable code and transfer note). Dashboard gate on closed rows: 0 `tracked-gap`, 0 unexpected `fail`.
Full runs `:kanvas:test` / `:gpu-renderer:test` / `:gpu-renderer-scenes:test` green except the four
documented baselines (unchanged state). Update `active-todo.md`: FP-13 entry `completed` with
resolution evidence; FP-12+ transfers list reduced to the items still open (if any). Branch → PR →
merge, then re-open the roadmap for FP-14.

## 6. Evidence & acceptance (transverse)

- M86 before/after per closed row: refusal diagnostic → render, diff, similarity payload, old/new
  threshold, committed artifacts.
- Full run gates as above; `GPUAllApiBlendSurfaceTest` 1864/1864; guards green.
- Evidence produced on llvmpipe (Xvfb `:99`); real-adapter re-measurement is chantier F (excluded,
  documented non-claim). No release-blocking performance gate is promoted by this plan.
- Non-claims: no global threshold change; CPU-oracle evidence is not Skia-comparable fidelity;
  `PipelineTypesTest` change is test hygiene, not a wgsl4k workaround; Kadre lanes untouched.

## 7. Explicitly deferred

- Chantier B (missing-reference infra + committed gms.json) and chantier F (real-adapter re-measure)
  stay tracked outside FP-13 (review recommendations, non-blocking).
- `external/poc-koreos` submodule work stays opt-in.
