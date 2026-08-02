# FP-07 prepared composite product-route closure evidence

Date: 2026-08-02

Verdict: **completed** — composites (`DrawPicture`, `BeginLayer`, `EndLayer`)
are now either executed by the common prepared WebGPU frame route (layer-target
execution with scratch-target-per-saveLayer materialization) or refused
terminally with stable, documented codes. They cannot return to the immediate,
CPU, or legacy composite renderer after product admission. The FP-06 boundary
(`unsupported.picture.nested_vertices`) is preserved, the legacy composite
allowlist is emptied, and the historical package-boundary baseline (exactly 20
cycles, 0 rule violations) is confirmed.

## Scope and accepting head

This report closes only FP-07. Per-task trial records (the withheld Task 9 flip
and the Task 17 re-flip triage) live in
`fp-07-composite-route-evidence.md`; this report is the route closure record:
route diagnostics, execution architecture, diff/stat vs the FP-06 tip, test
score deltas, the refusal matrix, boundary statements, the fallback policy, and
the deferred items.

The accepting implementation head is:

```text
aebfd94fe
fix(composite): remove redundant split logic and pin composite blend admission
```

The branch sequence from the FP-06 tip (`40a873560`) to the FP-07 accepting
head is **42 commits**: plan + design docs (4), foundation integration (10),
nested-vertices boundary pin (2), the four `GPUFilterOracle` bug fixes (7),
frame-route wiring + executor paint semantics (5), the withheld Task 9 flip
record (1), the flake stabilization (1), the layer-target execution build-out
(Tasks 12–16, 8), the Task 17 cutover (2), and follow-up hardening (2). No
early-cutover commits and no `.superpowers/sdd/` entries are in the range.

## 1. Route diagnostics (stable refusal surface)

The composite route is a capture → lowerer → preflight → executor pipeline.
Each stage refuses with a stable code; the router's terminal-family check is
the loud-refusal safety net at product admission.

| Stage | Entry | Terminal refusal codes emitted |
|---|---|---|
| Product admission | `GPUPreparedSurfaceProductRouter.hasTerminalPreparedFamily` (`DrawPicture`/`BeginLayer`/`EndLayer` in the prepared family) | none (admits), `unsupported.surface.prepared.draw-picture` (GPUOpMapper flat fallback), `unsupported.surface.prepared.mixed-composite-topology` (builder boundary, `GPUPreparedSurfaceFrameBuilder.kt:765`) |
| Capture | `GPUPreparedCompositeCapturer.capture(operations, limits)` | `unsupported.composite.operation`, `unsupported.composite.paint`, `unsupported.composite.clip`, `unsupported.composite.picture.cycle`, `unsupported.composite.picture.budget`, `unsupported.composite.layer.unbalanced`, `unsupported.composite.layer.bounds`, `unsupported.composite.layer.budget`, `unsupported.composite.layer.gate_missing`, `unsupported.picture.nested_vertices` |
| Lowerer | `GPUPreparedCompositeLowerer` (`capture → GPULayerSaveRecord → plan(request)`) | `unsupported.prepared-surface.layer-nesting` (nested layer scopes), `unsupported.composite.native.capability` (non-core child kinds) |
| Preflight | `GPUPreparedCompositePreflight` (maxTextureSize / maxColorAttachments budget) | `unsupported.composite.preflight` |
| Native preflight | `GPUPreparedSurfaceNativePreflight` (composite admission) | `unsupported.prepared-surface.layer-composite-blend` (non-SRC_OVER), `unsupported.prepared-surface.layer-nesting`, `unsupported.layer.bounds_unbounded` (`LayerContracts.kt:689`) |
| Executor | `GPUSaveLayerNativeExecutor` → `ValidatingSaveLayerMaterializer.materialize(request, context)` (`adapterBacked=false`) | none (runs) — refusals surface upstream |

`unsupported.filter.native.capability` (`GPUPreparedFilterRefusalCodes`) is the
mask-filter/filter-DAG lowerer's refusal code; `GPUPreparedFilterDAGPlanner`
and `GPUPreparedMaskFilterLowerer` both emit it (see §7, boundary restoration).

## 2. Execution architecture (layer-target steps + materialization + composite draw)

Modeled on the Graphite/Dawn layer model (Skia main exploration, 2026-08-02):
one render pass per layer target plus one root pass in a single command
encoder. Kanvas reuses the coverage-mask producer template (pooled RGBA8
texture, `RenderAttachment|TextureBinding`, render-scope operands in the same
encoder) and the prepared image shader template for the textured-quad
composite.

1. **Carriage** — `GPUPreparedSurfaceFrameTaskListBuilder.handleSaveLayer`
   produces the merged `compositeCommands` (`PrepareLayerTarget`,
   `RenderLayerChildren`, `CompositeLayer` triplets, innermost-first);
   `GPUPreparedWindowOutput.attachToFrame` carries them forward on the rebuilt
   task list (Task 12 — closes the first carriage gap, `GPUPreparedWindowOutput.kt`).
2. **Planning** — `GPUFramePlanner.plan` lowers `compositeCommands` into frame
   steps using the existing multi-target machinery: layer-target preparation,
   child renders targeted at each layer texture, and the composite render
   after all children (Task 13).
3. **Validation** — `GPUPreparedSceneCompatibilityValidator` admits one scene
   target + N declared layer targets (`PrepareLayerTarget.targetLabel` +
   descriptor: exact bounds, sampleCount 1, format,
   `RenderAttachment|TextureBinding`); anything undeclared still fails
   fail-closed (Task 14).
4. **Materialization** — `GPUWgpu4kPreparedSurfaceFramePayloadMaterializer`
   allocates frame-local RGBA8 layer textures, encodes child render scopes
   targeting the layer texture (Clear on first child scope, Load after,
   Store), and encodes the composite render scope: a textured-quad draw
   sampling the layer texture with the real `GPUBlendPlan` + `alpha` + clip
   (scissor intersection with the layer device bounds). Layer textures are
   sampled as premultiplied (`premultipliedSource` ABI flag) to avoid
   double-multiply against the straight-alpha image path (Task 15).
5. **Elision** — when `compositeCommands` are scheduled, the flat child render
   is elided (children render once, into the layer target); composite-only
   frames carry an empty flat base task list (`allowEmptyBaseTaskList`
   deviation recorded in `edd462810`); mixed topologies are either fully
   covered or refused loudly (Task 16).

Executor evidence: `GPUWgpu4kLayerTargetCompositeSmokeTest` (Task 15) executes
bounded saveLayer frames on the Apple M2 Max adapter with CPU-vs-GPU pixel
comparison; `GPUWgpu4kDestinationCopyFrameSmokeTest` continues to prove the
multi-scope one-submission template.

## 3. Diff/stat summary vs the FP-06 tip (`40a873560`)

```text
72 files changed, 8701 insertions(+), 554 deletions(-)
```

- New production route files: `GPUPreparedCompositeLowerer`, `GPUPreparedCompositePreflight`,
  `GPUSaveLayerNativeExecutor`, `GPUBlendOracle` (production promotion),
  `GPUFilterOracle`, `GPUPreparedFilterDAGPlanner`, `GPUPreparedMaskFilterLowerer`,
  `GPUPreparedMaskFilterContracts`, first-route pass wiring, composite contracts.
- Wired production entry: `GPUPreparedSurfaceFrameBuilder.build()` (capture +
  `handleSaveLayer` + command merge + elision), `GPUPreparedSurfaceFrameTaskListBuilder`
  (`handleSaveLayer` production entry + `splitCompositeChildrenRenders`),
  `GPUPreparedWindowOutput.attachToFrame`, `GPUFramePlanner`, the session
  validator, the native preflight, and the payload materializer.
- Gate: `GPUPreparedSurfaceFrameGate` routes composites to `hasVisual`;
  `GPULegacyImmediatePathAdapter` has an empty `LegacyDisplayOpFamily` and
  `allowedFamilies = emptySet()`.
- Router: `DrawPicture`/`BeginLayer`/`EndLayer` added to
  `hasTerminalPreparedFamily()` (the loud-refusal safety net).
- Tests: +13 new/ported test classes (lowerer, preflight, native executor,
  filter oracle, DAG planner, blend oracle, first-route pass, capture
  semantics, save-layer handling, layer-target smoke, frame-route
  integration, boundary pins) plus re-pointed expectations in the blend
  matrix, clip coverage, save-layer regression, gate, router, entry,
  inventory, and text-no-fallback suites.

## 4. Test score deltas

| State | `:kanvas:test` | `:gpu-renderer:test` |
|---|---|---|
| FP-06 tip baseline (`40a873560`) | 3,210 pass | 3,182 pass + boundary baseline |
| Task 9 withheld-flip trial (flip applied) | **298 failures** across 4 classes (203 terminal `unsupported.composite.operation` + 85 wrong-pixel + 8 saveLayer + 2 clip) — 100 % composite frames, executor gap | green + boundary baseline |
| FP-07 accepting head (`aebfd94fe`) | **3,230 pass, 0 failures** | **3,256 pass** + 1 pre-existing baseline (`GPURendererPackageBoundaryTest`, exactly 20 package cycles / 0 rule violations — the historical baseline documented in `2026-06-29-gpu-renderer-pre-existing-test-failures.md`) |

Clean re-run confirmation (Task 18 Step 1, `--rerun-tasks`): the two
`session-close` flake failures observed on the first full-suite run
(`GPUAllApiBlendSurfaceTest.DrawText/DST_OUT/ALPHA_MASK`,
`DrawMesh(program=null)/DST_ATOP/SCISSOR` — the documented session-close flake
family, fp-06 evidence § Known unrelated flakes) passed on class-level
re-run, and the subsequent full `:kanvas:test` run was fully green (3,230/3,230).

## 5. Refusal matrix

| Composite shape | Verdict | Code |
|---|---|---|
| Bounded saveLayer, rect/rrect/path children, SRC_OVER, alpha 1 | **renders** (layer-target execution, exact pixels) | — |
| Bounded saveLayer with alpha < 1 / translated / scaled / partially offscreen / empty | **renders** (premultiplied layer composite, layer-bounds scissor, empty-layer elision) | — |
| saveLayer with mask-blur paint | **renders** (blur → coverage A8, `GPUPreparedMaskFilterLowerer`) | — |
| Unbounded saveLayer | refused | `unsupported.layer.bounds_unbounded` |
| saveLayer with clip inside the layer scope | refused | `unsupported.composite.clip` |
| Nested saveLayer scopes | refused (both builder and native preflight) | `unsupported.prepared-surface.layer-nesting` |
| saveLayer with non-SRC_OVER blend | refused | `unsupported.prepared-surface.layer-composite-blend` |
| saveLayer with non-core children (image/text/atlas/vertices/mesh/DrawColor, non-finite transforms) | refused | `unsupported.composite.operation` |
| Unpainted DrawPicture inside a bounded saveLayer | refused at the capture boundary (no silent drop, no internal-invariant path) | `unsupported.composite.operation` |
| Painted picture / mixed picture topologies the route cannot cover | refused at the builder boundary | `unsupported.surface.prepared.mixed-composite-topology` |
| Vertices inside a picture/composite scope | refused (FP-06 boundary preserved) | `unsupported.picture.nested_vertices` |
| Capture budget exhaustion (pictures, layers) | refused | `unsupported.composite.picture.budget` / `unsupported.composite.layer.budget` |
| Unbalanced BeginLayer/EndLayer | refused | `unsupported.composite.layer.unbalanced` |
| Picture cycles | refused | `unsupported.composite.picture.cycle` |

The 203 Task-9-trial `unsupported.composite.operation` failures became
documented, re-pointed terminal expectations (evidence file §9.3): the blend
matrix's SAVE_LAYER rows now assert the observed terminal codes
(`bounds_unbounded`, `composite.operation`, `mixed-composite-topology`,
`composite.clip`, `layer-nesting`) instead of `Legacy`.

## 6. Boundary statements

- **`unsupported.picture.nested_vertices` preserved** — vertices inside a
  composite/picture scope stay refused (`GPUPreparedCompositeCapture.kt:343`,
  pinned by `GPUPreparedCompositeCaptureSemanticTest` and
  `GPUPreparedSurfaceProductRouterTest`); no FP-07 change relaxed the FP-06
  boundary.
- **Composites are no longer legacy-routed** — zero occurrences of
  `legacy.surface.prepared.family.composites` in `kanvas/src` /
  `gpu-renderer/src`; the gate routes `DrawPicture`/`BeginLayer`/`EndLayer` to
  the prepared family.
- **Legacy allowlist emptied** — `GPULegacyImmediatePathAdapter` has no
  display family and `allowedFamilies = emptySet()`; every migrated family is
  absent from the temporary immediate renderer.
- **Package boundary restored to the historical baseline** — Task 18's
  boundary audit found the mask-filter contracts had been left in the `layers`
  package (diverging from the validated fp-07 layout where they live in
  `filters/GPUPreparedMaskFilterContracts.kt`), which introduced a
  `filters → layers` import edge and grew the boundary report from 20 to 24
  cycles. Restoring the fp-07 layout (contracts → `filters`; DAG planner and
  mask-filter lowerer emit `GPUPreparedFilterRefusalCodes.NATIVE_CAPABILITY`)
  brings the boundary report back to exactly the base's **20 package cycles,
  0 rule violations** (verified identical cycle set vs `40a873560`). This was
  a closure-stage audit fix, not a route change: no refusal code asserted by a
  test changed (`unsupported.composite.paint` and the terminal surface are
  unchanged), and the full suites stayed green after the move.

## 7. Fallback policy statement

Explicit terminal refusal, never silent:

- Every admission gate on the composite route terminates loudly
  (`GPUPreparedSurfaceTerminalException` with a stable code) or executes —
  there is no silent-drop path left in product code. The covered-unpainted
  `DrawPicture`-in-saveLayer silent drop found in the Task 17 follow-up is
  closed by the capture-boundary refusal (`unsupported.composite.operation`).
- The router's terminal-family membership is the last-line safety net: a
  composite op that reaches product admission but cannot be covered terminates
  instead of falling back.
- Composites cannot return to the immediate, CPU, or legacy renderers:
  `GPULegacyImmediatePathAdapter` carries no composite family, and the flat
  mapper refuses `DrawPicture` (`unsupported.surface.prepared.draw-picture`)
  rather than dropping it.

## 8. Known deferred items (documented follow-ups)

- Nested saveLayer support (`unsupported.prepared-surface.layer-nesting`).
- Non-SRC_OVER composite blend materialization
  (`unsupported.prepared-surface.layer-composite-blend`).
- Tight-bounds layer transform support (layers currently materialize at
  parent-sized bounds; unbounded layers refuse with
  `unsupported.layer.bounds_unbounded`).
- Frame-pool lease for layer textures (coverage-mask pool pre-registration;
  Task 15 allocates frame-local via the destination-snapshot pattern).
- Non-core children inside layer scopes (image/text/atlas/vertices/mesh —
  `unsupported.composite.operation`).

## 9. Evidence inventory

- Per-task trial record (withheld Task 9 flip + Task 17 re-flip triage):
  `fp-07-composite-route-evidence.md`.
- Plan and revised design: `reports/fp07-composite-route-plan.md`,
  `reports/fp07-composite-route-design.md`.
- Executor smoke evidence: `GPUWgpu4kLayerTargetCompositeSmokeTest` (8 tests,
  green).
- Boundary audit artifacts: base-worktree boundary run at `40a873560` (20
  cycles), accepting-head run (20 cycles, identical set) — cycle lists
  compared programmatically during Task 18.
