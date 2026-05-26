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
| `:gpu-raster` | WebGPU device, handwritten WGSL resources, generated WGSL pilots, parser validation report, pipeline key diagnostics, cache telemetry, `BlendPlan`. |
| `:cpu-raster` | Existing CPU shader families and runtime-effect registry surface. |
| `:kanvas-skia` | CPU Skia-like behavior oracle and device/canvas surface used by tests. |

## Current M0-M11 Evidence

| Milestone | Status | Evidence pointer | Remaining gap |
|---|---|---|---|
| M0 parser dependency integration | Done | `WgslParserSmokeMain`; `gpu-raster` parser dependencies; Linear `GRA-18` | Remote publication policy is not settled. |
| M1 `KanvasPipelineIR` foundation | Done | `KanvasPipelineIR.kt`; `KanvasPipelineIRTest` | IR is intentionally a pilot subset. |
| M2 WGSL validation and reflection | Partial | `WgslValidationReport`; `WgslValidationReportTest` | Reflection does not yet cover every layout shape. |
| M3 CPU scalar pilot | Done | `CpuScalarPipelineExecutor`; `CpuScalarPipelineExecutorTest` | CPU backend is not yet a full compiled pipeline. |
| M4 generated solid WGSL pilot | Done | `GeneratedSolidRectWgsl`; `GeneratedSolidRectWgslTest`; `GeneratedSolidRectMigrationTest` | General WGSL assembly is still limited. |
| M5 uniform packer verification | Partial | `WgslValidationReport` uniform reflection; focused packer checks | Broad generated packers are not yet implemented. |
| M6 pipeline key and cache telemetry | Done | `PipelineKeyClassification`; `GpuCacheTelemetrySnapshot`; `PipelineKeyTelemetryTest` | Shared public `PipelineKey` type is still future work. |
| M7 blend diagnostics | Done | `BlendPlan`; `BlendPlanTest` | Shader/layer composite paths need per-family promotion evidence. |
| M8 generated gradient WGSL | Done | `GeneratedLinearGradientWgsl`; `GeneratedLinearGradientWgslTest`; gradient GPU tests | Current source is template-based rather than full IR assembly. |
| M9 runtime-effect descriptor pilot | Done | `SkRuntimeEffectDescriptor`; `RuntimeEffectDescriptorWebGpuTest`; runtime-effect dispatch tests | Support matrix export is not centralized yet. |
| M10 Java 25 Vector pilot | Done | `CpuVectorSolidRectKernel`; vector benchmark/test evidence | Default gating still requires machine-specific speedup evidence. |
| M11 migration batch 1 | Done | `GeneratedSolidRectMigrationTest`; generated solid rect default path | Retirement remains evidence-driven per family. |

## Known Gaps

- This spec pack is the first checked-in implementation spec for M0-M11; older
  acceptance lived mostly in Linear and the target document.
- Geometry/Coverage convergence is a separate active track and must not be
  inferred from M0-M11 paint-pipeline tickets.

## Non-Goals

- Do not backfill a Skia Graphite paint-key model.
- Do not turn WGSL IR into the semantic renderer IR.
- Do not add a CPU interpreter for arbitrary WGSL.
- Do not add a catch-all runtime-effect compiler.
- Do not count local parser snapshot availability as a remote dependency
  guarantee.
