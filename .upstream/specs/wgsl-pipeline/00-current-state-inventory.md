# Spec 00: Current State Inventory

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Purpose

Record the current M0-M11 paint-pipeline state before future work builds on it.
This avoids treating Linear summaries as the only technical record.

## Current Modules

| Module | Current ownership |
|---|---|
| `:render-pipeline` | `KanvasPipelineIR`, value contracts, fallback plans, CPU scalar pilot, Vector API gate, Geometry/Coverage adapter code. |
| `:gpu-renderer` | WebGPU device, handwritten WGSL resources, generated WGSL pilots, parser validation report, pipeline key diagnostics, cache telemetry, `BlendPlan`. |
| `:kanvas` CPU/reference route | Existing CPU shader families and runtime-effect registry surface. |
| `:kanvas` compatibility facade | CPU Skia-like behavior oracle and device/canvas surface used by tests. |

## Current M0-M11 Evidence

| Milestone | Status | Evidence pointer | Remaining gap |
|---|---|---|---|
| M0 parser dependency integration | Done | `WgslParserSmokeMain`; `gpu-raster` parser dependencies; Linear `GRA-18` | Remote publication policy is not settled. |
| M1 `KanvasPipelineIR` foundation | Done | `KanvasPipelineIR.kt`; `KanvasPipelineIRTest` | IR is intentionally a pilot subset. |
| M2 WGSL validation and reflection | Accepted for generated/registered modules | `WgslValidationReport`; `WgslValidationReportTest`; `pipelineConformance` | Existing handwritten resources still surface parser diagnostics in the report. |
| M3 CPU scalar pilot | Done | `CpuScalarPipelineExecutor`; `CpuScalarPipelineExecutorTest` | CPU backend is not yet a full compiled pipeline. |
| M4 generated solid WGSL pilot | Done | `GeneratedSolidRectWgsl`; `GeneratedSolidRectWgslTest`; `GeneratedSolidRectMigrationTest` | General WGSL assembly is still limited. |
| M5 uniform packer verification | Accepted for current packers | `WgslValidationReport` uniform reflection; focused packer checks; `pipelineConformance` | Broad generated packers are not yet implemented. |
| M6 pipeline key and cache telemetry | Done | `PipelineKeyClassification`; `GpuCacheTelemetrySnapshot`; `PipelineKeyTelemetryTest` | Shared public `PipelineKey` type is still future work. |
| M7 blend diagnostics | Done | `BlendPlan`; `BlendPlanTest` | Shader/layer composite paths need per-family promotion evidence. |
| M8 generated gradient WGSL | Done | `GeneratedLinearGradientWgsl`; `GeneratedLinearGradientWgslTest`; gradient GPU tests | Current source is template-based rather than full IR assembly. |
| M9 runtime-effect descriptor pilot | Done | `SkRuntimeEffectDescriptor`; `RuntimeEffectDescriptorWebGpuTest`; runtime-effect dispatch tests; support matrix report | New runtime effects still require registration and parser evidence. |
| M10 Java 25 Vector pilot | Rejected for default | `CpuVectorSolidRectKernel`; vector benchmark/test evidence; PR #1137; PR #1138 | Default gating still requires `>= 1.5x` speedup evidence. |
| M11 migration batch 1 | Done | `GeneratedSolidRectMigrationTest`; generated solid rect default path | Retirement remains evidence-driven per family. |

## Remaining Gaps

- This inventory remains `Draft` because it records evolving state, not a
  frozen implementation contract.
- Existing handwritten WGSL parser diagnostics remain visible in
  `:gpu-renderer:wgslValidateAll`; generated and registered WGSL modules are the
  accepted M24 conformance scope.
- Geometry/Coverage convergence is tracked separately under
  `.upstream/specs/geometry-coverage/` and only the conformance-covered handoff
  is accepted here.

## M24 Evidence Links

- PR #1142 / `12684fb7259644bb2932e930026c7134177e1964`:
  `pipelineConformance`.
- PR #1143 / `637e42344a335504bfe8d95b63351dfc40ebd872`:
  PM convergence report.
- PR #1144 / `2035b455535e35452097154d9b5d0f05eea8a866`:
  report regeneration fix.
- PR #1137 and PR #1138: vector benchmark and promotion decision.
- PR #1139, PR #1140, PR #1141: runtime-effect descriptor hardening,
  support matrix, and implementation-id validation.

## Non-Goals

- Do not backfill a Skia Graphite paint-key model.
- Do not turn WGSL IR into the semantic renderer IR.
- Do not add a CPU interpreter for arbitrary WGSL.
- Do not add a catch-all runtime-effect compiler.
- Do not count local parser snapshot availability as a remote dependency
  guarantee.
