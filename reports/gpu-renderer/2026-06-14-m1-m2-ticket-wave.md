# GPU Renderer M1/M2 Ticket Wave

Date: 2026-06-14
Branch: `codex/gpu-renderer-m1-wave`
Ticket scope: M1 policy gate audit, KGPU-M2-001 isolated `FillRRect`
evidence, and M2 simple closeout scene evidence.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M1-001 | `done` | `reports/gpu-renderer/2026-06-14-m1-promotion-policy-decision.md` accepts launching the controlled M1 promotion path in the current state. | None for policy; no product route activation is implied. |
| KGPU-M1-002 | `done` | Root PM bundle now packages `ActivationCandidate` state with `packagingState=activation-candidate`, explicit adapter evidence provenance, `productRouteActivated=false`, `releaseBlocking=false`, and `readinessDelta=0.0`. Independent review `019ec714-40ab-73b1-a242-9dc36c3b2694` approved the evidence. | None for PM packaging; no product route activation is implied. |
| KGPU-M1-003 | `done` | Added explicit `kanvas.gpu.renderer.product.fillRect` flag diagnostics for solid `FillRect`; default remains disabled, legacy rendering remains visible, `StrokeAndFill` product expansion refuses, and no GPU renderer submit/readback is introduced. Independent review `019ec724-9088-7512-b14c-e5c5090e84dd` approved the evidence. | None for controlled flag; rollback/parity remains KGPU-M1-004. |
| KGPU-M1-004 | `done` | Added rollback/parity validator and adapter-backed checksum evidence across legacy-before, product-flagged, and legacy-rollback runs; mismatch failures keep activation false with stable diagnostics. Independent review `019ec731-4bf3-7e60-9ab6-af513036a6e9` approved the evidence. | None for M1 rollback/parity; no default product route activation is implied. |
| KGPU-M2-001 | `done` | Added isolated `:gpu-renderer` `FillRRect` command, planner, route-candidate/refused planning decisions, pass/task evidence, command-shape version bump, and validation fixture ownership line. Graphite-alignment remediation now preserves per-corner rrect radii and explicit unproven scale/affine refusals. `M2SimpleSceneEvidenceTest` adds explicit skipped GPU-lane evidence for the simple scene. | Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` accepted the skipped-GPU lane evidence; no `gpu-raster` product activation. |
| KGPU-M2-002 | `done` | `M2SimpleSceneEvidenceTest` records `material:linear-gradient.clamp.inline2`, `gradient.inline2` payload evidence, fixture-declared WGSL reflection, and stable `unsupported.gradient.tile_mode` refusal. | Independent review accepted this as route-planning proof only; no adapter-backed gradient execution or broad color-management claim. |
| KGPU-M2-003 | `done` | `M2SimpleSceneEvidenceTest` records a `GPUClipPlan`-backed device scissor dump for `m2-simple-device-scissor` and stable `unsupported.clip.non_device_rect` refusal. | Independent review accepted this as scissor-planning proof only; no complex clip, stencil, mask, shader clip, or hidden CPU fallback claim. |
| KGPU-M2-004 | `done` | `M2SimpleSceneEvidenceTest` records two compatible rrect/linear-gradient/scissor draws batched under `batch:rrect.linear-gradient.scissor` and split reasons for material-key, clip-stack, and layer/order boundaries. | Independent review accepted the conservative batching evidence; no payload, resource, layer, or ordering boundary is crossed. |

## Remaining Catalog Gates

- M2-001 through M2-004 are `done` on contract-fixture evidence accepted by
  independent review. This does not imply adapter-backed execution or product
  route activation.
- M3, M4, M5, M7, and M8 remain dependency-gated through M2/M4/M5 route
  foundations.
- M6 remains dependency-gated on pure Kotlin text/font deliverables such as
  KFONT-M11-003, KFONT-M11-004, KFONT-M11-007, KFONT-M11-008, and
  KFONT-M11-009.
- M9 and M10 remain later-order and dependency-gated through downstream M1
  activation/rollback tickets and feature-specific promotion evidence.

## Evidence

- `NormalizedDrawCommand.FillRRect` and `GPUFillRRectCommandBuilder` capture
  per-corner rounded-rect geometry facts without lowering materials or
  allocating resources.
- `GPUFirstRoutePlanner.plan(FillRRect)` accepts finite, normalized solid
  rrect route candidates with `first_slice.fill_rrect.native` and emits
  `native.fill_rrect.solid`, `rrect.fill.coverage`, and
  `geometry:rrect.corner_radii=tl(...);tr(...);br(...);bl(...)` analysis
  evidence. This remains pre-materialization evidence only.
- Unsupported rrect radii, perspective/scale/affine transforms, complex clip,
  unsupported blend, unsupported target format, and missing capability refuse
  with stable diagnostics and no pass work.
- `GPURecorder` records accepted `FillRRect` as pre-materialization render-task
  evidence and bumps `commandShapeVersion` to `2`; it does not materialize
  resources, submit adapter-backed work, or activate a product route.
- `GPUValidationFixture.firstSliceConceptOwnershipDump()` now includes
  `NormalizedDrawCommand.FillRRect` as first-expansion command ownership
  evidence.
- `M2SimpleSceneEvidence.build().dumpLines()` emits the closeout scene:
  `scene:m2.simple.rrect-gradient-scissor-batch mode=contract-fixture`.
- The simple scene includes accepted rrect route-candidate evidence, accepted
  linear-gradient material/payload/WGSL fixture evidence, `GPUClipPlan`
  device-scissor evidence, conservative batching evidence, and stable refusal
  lines for unsupported gradient tile mode and non-device-rect clips.
- The scene records `gpu-lane:explicit-skipped` with
  `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0`.
- Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` approved moving
  M2-001 through M2-004 to `done` after confirming no blocking issues.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.validation.FirstRouteCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommandTest --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest --tests org.graphiks.kanvas.gpu.renderer.validation.FirstRouteCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.validation.M2SimpleSceneEvidenceTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
rtk python3 scripts/test_validate_gpu_renderer_r6_executed_pm_evidence_bundle.py
rtk git diff --check
```

All commands above passed after RED/GREEN implementation and final validation.

## Non-Claims

- M1 product activation policy was accepted as a controlled promotion path in
  `reports/gpu-renderer/2026-06-14-m1-promotion-policy-decision.md`; this is
  not product route activation.
- M1-002 activation-candidate packaging changes the PM bundle state only; the
  root validation report remains `Incomplete` and non-release-blocking.
- M1-003 adds a controlled local product flag diagnostic, but no default route
  or rollback path was added.
- M1-004 adds rollback/parity validation around that flag; it still does not
  enable a default product route or mark the route release-blocking.
- No `gpu-raster` route was enabled or modified for `FillRRect`.
- M2 is `done` only as contract-fixture/skipped-GPU evidence accepted by
  independent review; it is not adapter-backed execution.
- Accepted `FillRRect` entries are isolated route-candidate/pre-materialization
  evidence, not executed GPU lane evidence.
- The M2 simple scene is a validation fixture, not adapter-backed execution.
- No rrect path/stroke, unsupported gradient variant, complex clip, image,
  text, filter, saveLayer, destination-read, runtime-effect, or broad Skia
  parity support is claimed.
