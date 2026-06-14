# GPU Renderer M1/M2 Ticket Wave

Date: 2026-06-14
Branch: `codex/gpu-renderer-m1-wave`
Ticket scope: M1 policy gate audit and KGPU-M2-001 isolated `FillRRect`
evidence.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M1-001 | `done` | `reports/gpu-renderer/2026-06-14-m1-promotion-policy-decision.md` accepts launching the controlled M1 promotion path in the current state. | None for policy; no product route activation is implied. |
| KGPU-M1-002 | `done` | Root PM bundle now packages `ActivationCandidate` state with `packagingState=activation-candidate`, explicit adapter evidence provenance, `productRouteActivated=false`, `releaseBlocking=false`, and `readinessDelta=0.0`. Independent review `019ec714-40ab-73b1-a242-9dc36c3b2694` approved the evidence. | None for PM packaging; no product route activation is implied. |
| KGPU-M1-003 | `done` | Added explicit `kanvas.gpu.renderer.product.fillRect` flag diagnostics for solid `FillRect`; default remains disabled, legacy rendering remains visible, `StrokeAndFill` product expansion refuses, and no GPU renderer submit/readback is introduced. Independent review `019ec724-9088-7512-b14c-e5c5090e84dd` approved the evidence. | None for controlled flag; rollback/parity remains KGPU-M1-004. |
| KGPU-M1-004 | `done` | Added rollback/parity validator and adapter-backed checksum evidence across legacy-before, product-flagged, and legacy-rollback runs; mismatch failures keep activation false with stable diagnostics. Independent review `019ec731-4bf3-7e60-9ab6-af513036a6e9` approved the evidence. | None for M1 rollback/parity; no default product route activation is implied. |
| KGPU-M2-001 | `review` | Added isolated `:gpu-renderer` `FillRRect` command, planner, native/refused route decisions, pass/task evidence, command-shape version bump, and validation fixture ownership line. Independent review found no blocking claim/status issue. | Adapter-backed or explicitly skipped GPU evidence before `done`; no `gpu-raster` product activation. |

## Remaining Catalog Gates

- M2-002 and M2-003 remain `proposed` because both depend on KGPU-M2-001
  moving past review; M2-004 depends on KGPU-M2-001, KGPU-M2-002, and
  KGPU-M2-003.
- M3, M4, M5, M7, and M8 remain dependency-gated through M2/M4/M5 route
  foundations.
- M6 remains dependency-gated on pure Kotlin text/font deliverables such as
  KFONT-M11-003, KFONT-M11-004, KFONT-M11-007, KFONT-M11-008, and
  KFONT-M11-009.
- M9 and M10 remain later-order and dependency-gated through downstream M1
  activation/rollback tickets and feature-specific promotion evidence.

## Evidence

- `NormalizedDrawCommand.FillRRect` and `GPUFillRRectCommandBuilder` capture
  rounded-rect geometry facts without lowering materials or allocating
  resources.
- `GPUFirstRoutePlanner.plan(FillRRect)` accepts finite, normalized solid rrects
  with `first_slice.fill_rrect.native` and emits `native.fill_rrect.solid`,
  `rrect.fill.coverage`, and `geometry:rrect.radii=...` analysis evidence.
- Unsupported rrect radii, perspective transform, complex clip, unsupported
  blend, unsupported target format, and missing capability refuse with stable
  diagnostics and no pass work.
- `GPURecorder` records accepted `FillRRect` as pre-materialization render-task
  evidence and bumps `commandShapeVersion` to `2`.
- `GPUValidationFixture.firstSliceConceptOwnershipDump()` now includes
  `NormalizedDrawCommand.FillRRect` as first-expansion command ownership
  evidence.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.validation.FirstRouteCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommandTest --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest --tests org.graphiks.kanvas.gpu.renderer.validation.FirstRouteCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
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
- M2-001 is not `done`; adapter-backed or explicitly skipped GPU evidence and
  independent review remain required.
- No rrect path/stroke, gradient, complex clip, image, text, filter, saveLayer,
  destination-read, runtime-effect, or broad Skia parity support is claimed.
