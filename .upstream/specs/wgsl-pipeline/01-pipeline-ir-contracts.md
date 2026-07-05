# Spec 01: PipelineIR Contracts

Status: Accepted
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## M24 Acceptance Evidence

Accepted on 2026-05-27 for the scope covered by the M24 conformance gate.

Evidence links:

- PR #1142 / `12684fb7259644bb2932e930026c7134177e1964`: `pipelineConformance`.
- PR #1143 / `637e42344a335504bfe8d95b63351dfc40ebd872`: PM convergence report.
- PR #1144 / `2035b455535e35452097154d9b5d0f05eea8a866`: report regeneration fix.

Acceptance is limited to the implemented and tested families named in the
conformance report. Future shader, blend, runtime-effect, or migration families
must add their own evidence before default promotion.


## Purpose

Define the backend-neutral paint pipeline contract consumed by CPU and WebGPU.
`KanvasPipelineIR` describes Skia-like paint semantics after draw geometry has
provided coverage. It is a semantic plan, not the final CPU loop and not the
final WGSL program.

## Ownership

Current owner:

- `render-pipeline/src/main/kotlin/org/skia/pipeline/KanvasPipelineIR.kt`

Consumers:

- CPU execution in `:render-pipeline`, moving toward `:kanvas` CPU/reference route ownership as
  the backend broadens.
- WebGPU generated pipeline selection in `:gpu-renderer`.
- Runtime-effect descriptors in `:kanvas` CPU/reference route and `:gpu-renderer`.
- Geometry/Coverage adapters from `.upstream/specs/geometry-coverage/`.

## Pipeline Boundary

```mermaid
flowchart TD
    draw["SkCanvas draw*"] --> geom["Geometry/Coverage"]
    geom --> coverage["CoverageModel or CoveragePlan adapter"]
    paint["SkPaint shader/color/filter/blend"] --> append["IR appenders"]
    coverage --> ir["KanvasPipelineIR"]
    append --> ir
    ir --> cpu["CPU plan"]
    ir --> gpu["GPU plan"]
```

`KanvasPipelineIR` starts after geometry has identified coverage. The paint
pipeline may consume a `CoverageModel` directly or consume the result of a
`CoveragePlan` lowering adapter. It must not inspect raw clip-stack operations
or backend-specific path tessellation details.

## Required Contracts

`PipelineOp` records normalized operations in semantic order:

1. seed coordinates;
2. shader or constant color evaluation;
3. paint-color modulation;
4. color-filter or color-space operations;
5. coverage modulation;
6. destination load when needed;
7. blend or composite;
8. store.

The current implementation contains the first pilot subset:

- `SeedDeviceCoords`;
- `ConstantColor`;
- `LinearGradient`;
- `PaintColorModulate`;
- `ApplyCoverage`;
- `BlendMode`;
- `ColorSpaceXform`;
- `LoadDst`;
- `Store`.

Future operations must state whether they are semantic, backend-specific, or
compatibility-only. Backend-specific operations do not belong in
`KanvasPipelineIR`; they belong in CPU/GPU plans.

## Value Semantics

`ColorValueSpec` names the color value domain:

- alpha domain: unpremul, premul, raw, destination;
- color-space role: sRGB, destination, working, explicit, raw bytes;
- precision domain: U8, F16, F32.

New shader, color-filter, image-filter, and runtime-effect appenders must name
their input and output value specs when precision, alpha, or color space can
change behavior.

`CoverageModel` is the paint-side coverage input. It currently supports:

- full coverage;
- span coverage;
- alpha mask;
- analytic rect coverage.

Detailed geometry semantics, path facts, clip facts, and backend coverage
strategies belong to `CoveragePlan`, not directly to paint ops.

## Append Semantics

Appending must be transactional:

- `AppendResult.Success` commits the appended operations.
- `AppendResult.Unsupported` leaves the builder unchanged and lets the caller
  select a fallback.
- `AppendResult.Fatal` leaves the builder unchanged and reports a hard failure.

Appender code must not partially mutate the IR before discovering unsupported
state. This is required so CPU and GPU backends can make independent fallback
decisions from the same semantic input.

## Stable Ids

All externally visible ids in diagnostics, dumps, telemetry, generated module
registries, and runtime-effect descriptors use this shape:

```text
{layer}.{backend}.{family}.{variant}@v{n}
```

Rules:

- `layer` is one of `pipeline`, `cpu`, `gpu`, `runtime-effect`, `coverage`,
  `migration`, or another reviewed namespace.
- `backend` is the route or implementation family for that layer, such as
  `shared`, `scalar`, `vector`, `generated`, `handwritten`, `cpu`, `gpu`, or
  `compat`.
- `family` is a grep-friendly feature family such as `solid-rect`,
  `linear-gradient`, `blend`, or `runtime-effect`.
- `variant` names the semantic or backend variant, such as `srcover-clear`,
  `srcover`, `fixed-function`, or `descriptor`.
- `v{n}` bumps when the id contract changes incompatibly.

Examples:

- `cpu.scalar.solid-rect.srcover-clear@v1`
- `gpu.generated.linear-gradient.srcover@v2`
- `runtime-effect.cpu.bloom-3x3.shader@v1`
- `runtime-effect.gpu.bloom-3x3.shader@v1`

Existing pre-spec ids may be grandfathered temporarily, but new promoted ids
must follow this namespace and dumps must expose the full id.

## FallbackPlan

`FallbackPlan` records the selected compatibility or refusal path:

- `CpuShadeRow`: use legacy CPU shader row execution.
- `HandwrittenGpuCompat`: use a named handwritten WGSL path.
- `RefuseDiagnostic`: fail explicitly with a stable diagnostic.
- `ExplicitLayerOrReadbackCompat`: use a known compatibility layer/readback
  path.

Fallbacks are part of the IR dump. They are not silent branches in backend
code.

## Dump Stability

`KanvasPipelineIR.dump()` is a developer and PM evidence artifact. It must:

- include a version marker;
- output ops in stable order;
- use stable enum/data names;
- include fallback state;
- avoid memory addresses, unordered map traversal, or object identity.

Any dump format change that breaks expected snapshots must be reviewed as a
contract change.

Dump versions follow a simple compatibility policy:

| Change type | Version action |
|---|---|
| Add optional field, new op appended to a documented extension point, or extra diagnostic line | minor bump |
| Rename, remove, reorder, or semantic reinterpretation of an existing field/op | major bump |
| Formatting-only readability change that preserves parsed facts | patch note, no version bump |

Snapshot readers and review tools should remain compatible with the current
major version and the immediately previous major version. Snapshots older than
that must be regenerated by a named script or explicit review action.

## Coverage Transition

Direct `CoverageModel` input is a transition path for M0-M11 paint-pipeline
work. The long-lived boundary is:

```text
Geometry/Coverage -> CoveragePlan -> lowering adapter -> CoverageModel/backend strategy -> Paint PipelineIR
```

Once the Geometry/Coverage milestones cover rect, path, glyph, image, and clip
coverage, new paint-pipeline work should consume coverage through the adapter
instead of constructing `CoverageModel` directly. Direct construction remains
allowed only in focused unit tests and compatibility shims with an explicit
retirement issue.

## Backend Normalization

Backends may fuse operations into specialized plans:

- CPU can collapse `ConstantColor + ApplyCoverage + SrcOver + Store` into one
  scalar/vector kernel.
- GPU can lower the same sequence into one generated WGSL fragment and one
  WebGPU pipeline.

The fused backend plan must be semantically equivalent to the normalized IR or
return an explicit fallback.

## Non-Goals

- Do not encode WebGPU bind groups, WGSL structs, or entry-point names in
  `PipelineOp`.
- Do not encode CPU loop shape or vector lane width in `PipelineOp`.
- Do not treat a generated WGSL module as the source of truth for paint
  semantics.
- Do not add a general SkSL representation.

## Acceptance Criteria

- IR contracts live in a backend-neutral module.
- Snapshot tests pin dump ordering for each new op.
- Unsupported append cases leave the prior IR unchanged.
- CPU and GPU plans can consume the same IR or refuse with explicit fallback.
- Geometry/Coverage handoff is through `CoverageModel` or a documented
  `CoveragePlan` adapter.
- Stable ids follow the namespaced `{layer}.{backend}.{family}.{variant}@v{n}`
  convention for newly promoted paths.
