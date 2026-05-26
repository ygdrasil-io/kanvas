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

M0 parser dependency integration:

- `:gpu-raster` consumes the local `webgpu-ktypes` WGSL stack.
- `WgslParserSmokeMain` and parser-backed tests provide a smoke path.
- The dependency remains local/snapshot-driven until remote publication is
  settled.

M1 `KanvasPipelineIR` foundation:

- `render-pipeline/src/main/kotlin/org/skia/pipeline/KanvasPipelineIR.kt`
  defines:
  - `PipelineOp`;
  - `CoverageModel`;
  - `ColorValueSpec`;
  - `FallbackPlan`;
  - `AppendResult`;
  - stable `dump()` output.
- The IR is intentionally small and descriptive. It is not a full Skia paint
  key and not a GPU shader AST.

M2 WGSL validation and reflection:

- `WgslValidationReport` parses shader resources, extracts entry points,
  bindings, and uniform struct offsets where reflection is available.
- Reflection failures are reported as diagnostics instead of hiding layout
  uncertainty.

M3 CPU scalar pilot:

- `CpuScalarPipelineExecutor` executes solid rect and linear gradient pilots.
- Unsupported shader families and blend modes return `LegacyFallback`.
- CPU old-path comparison is available for the first fixtures.

M4 generated WGSL pilot:

- `GeneratedSolidRectWgsl` builds a deterministic WGSL module through
  webgpu-ktypes IR and parser-validates the generated source.
- The WebGPU backend can retain handwritten compatibility until retirement
  evidence is complete.

M5 uniform packer verification:

- Reflection can report uniform bindings and member offsets.
- The spec target is generated or verified packers; current implementation has
  smoke-level coverage and must not be treated as broad layout generation.

M6 pipeline key and cache telemetry:

- `SkWebGpuDevice.PipelineKeyClassification` classifies key axes as layout,
  code, pipeline state, or uniform-only.
- `GpuCacheTelemetrySnapshot` exposes shader module, pipeline, resource, and
  pipeline creation counters.

M7 blend diagnostics:

- `selectWebGpuBlendPlan` and `selectLayerCompositeBlendPlan` route blend
  modes through explicit fixed-function, shader/layer composite, or refusal
  choices.
- Unsupported modes carry a stable diagnostic reason string.

M8 generated gradient WGSL:

- `GeneratedLinearGradientWgsl` provides deterministic generated source for the
  first non-trivial shader family.
- The current implementation is source-template based, not yet a general WGSL
  IR assembly system.

M9 runtime-effect descriptor pilot:

- `SkRuntimeEffectDescriptor` records stable id, kind, uniforms, children,
  flags, CPU implementation id, and optional WGSL implementation id.
- Runtime effects remain registered implementations, not arbitrary SkSL
  compilation.

M10 Java 25 Vector pilot:

- `CpuVectorSolidRectKernel` uses reflection to load the JVM Vector API
  implementation when available.
- Scalar fallback is always available.

M11 migration batch 1:

- Generated solid rect can be selected by default with retained handwritten
  fallback rules and diagnostics.
- Retirement is evidence-driven, not automatic.

## Known Gaps

- There was no implementation spec pack before this document set; M0-M11
  acceptance lived mostly in Linear and the target document.
- Generated WGSL is not yet a complete fragment graph assembler.
- Uniform packer generation is not yet a broad mechanism.
- CPU execution is still a pilot, not a full compiled pipeline backend.
- GPU generated paths coexist with extensive handwritten WebGPU code.
- Cross-backend thresholds and PM evidence are not centralized for every
  shader family.
- Geometry/Coverage convergence is a separate active track and must not be
  inferred from M0-M11 paint-pipeline tickets.

## Non-Goals

- Do not backfill a Skia Graphite paint-key model.
- Do not turn WGSL IR into the semantic renderer IR.
- Do not add a CPU interpreter for arbitrary WGSL.
- Do not add a catch-all runtime-effect compiler.
- Do not count local parser snapshot availability as a remote dependency
  guarantee.
