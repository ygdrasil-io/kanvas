# GPU Renderer M1/M2 Ticket Wave

Date: 2026-06-14
Branch: `codex/gpu-renderer-m1-wave`
Ticket scope: M1 policy gate audit and KGPU-M2-001 isolated `FillRRect`
evidence.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M1-001 | `blocked` | `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md` still records `Product route activated: false`, `Readiness delta: 0.0`, and `Promotion decision required: true`. | Explicit release/product activation decision plus reviewed non-skipped adapter-backed R6 evidence. |
| KGPU-M1-002 | `blocked` | Root PM bundle remains refusal-first and non-promotional. | Accepted KGPU-M1-001 policy before activation-candidate PM packaging. |
| KGPU-M1-003 | `blocked` | No product flag added. | Accepted policy and activation-candidate PM packaging. |
| KGPU-M1-004 | `blocked` | No rollback/parity route added. | Controlled product flag from KGPU-M1-003. |
| KGPU-M2-001 | `review` | Added isolated `:gpu-renderer` `FillRRect` command, planner, native/refused route decisions, pass/task evidence, command-shape version bump, and validation fixture ownership line. Independent review found no blocking claim/status issue. | Adapter-backed or explicitly skipped GPU evidence before `done`; no `gpu-raster` product activation. |

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

- No M1 product activation decision was made.
- No product flag, default route, or rollback path was added.
- No `gpu-raster` route was enabled or modified for `FillRRect`.
- M2-001 is not `done`; adapter-backed or explicitly skipped GPU evidence and
  independent review remain required.
- No rrect path/stroke, gradient, complex clip, image, text, filter, saveLayer,
  destination-read, runtime-effect, or broad Skia parity support is claimed.
