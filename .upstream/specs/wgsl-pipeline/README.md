# WGSL Paint Pipeline Specs

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

This spec pack turns the validated pre-Geometry WGSL paint-pipeline target into
implementation-ready technical contracts. It covers the Linear M0-M11 work:
parser integration, `KanvasPipelineIR`, CPU scalar/vector execution, generated
WGSL, `PipelineKey`, `BlendPlan`, runtime-effect descriptors, validation, and
migration policy.

Geometry and coverage are specified separately under
`.upstream/specs/geometry-coverage/`. The handoff is deliberate: geometry
produces coverage, the paint pipeline consumes coverage.

## Source Of Truth

- Target architecture:
  `.upstream/target/high-performance-wgsl-pipeline-target.md`
- Execution method:
  `.upstream/target/linear-agent-methodology.md`
- Active post-MEP target:
  `.upstream/target/skia-like-realtime-renderer-target.md`
- Historical MEP target/backlog:
  removed from the working tree; recover from Git history only if needed.
- Linear project:
  `Kanvas - WGSL Pipeline Target`, milestones M0-M11
- Geometry handoff:
  `.upstream/specs/geometry-coverage/README.md`
- Upstream/rebaseline evidence:
  `reports/upstream-rebaseline/` and `.upstream/source/map/`

Hard constraints:

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `KanvasPipelineIR` as the Skia-like semantic pipeline contract.
- Use WGSL IR only as the concrete GPU module construction layer.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Treat Java 25 Vector API code as an optional performance path, never as a
  correctness dependency.

## Status Policy

Specs start as `Draft`. A spec can move to `Accepted` only when the owning
Linear milestone has merged implementation evidence, fallback behavior is
asserted in tests or reports, and the PM evidence comment links the relevant
commit or PR. Editorial fixes do not change status.

## M24 Status Review

Review date: 2026-05-27

Evidence:

- Conformance command:
  `rtk ./gradlew --no-daemon pipelineConformance`
- PM report command:
  `rtk ./gradlew --no-daemon pipelineConformanceReport`
- M24 conformance task: PR #1142 / merge
  `12684fb7259644bb2932e930026c7134177e1964`
- PM report generation: PR #1143 / merge
  `637e42344a335504bfe8d95b63351dfc40ebd872`
- Report regeneration fix: PR #1144 / merge
  `2035b455535e35452097154d9b5d0f05eea8a866`
- Runtime-effect production hardening: PR #1139, PR #1140, PR #1141.
- Vector promotion decision: PR #1137, PR #1138.

| Spec | M24 status | Evidence | Remaining gaps |
|---|---|---|---|
| `00-current-state-inventory.md` | Draft | Inventory updated with M24 evidence. | Historical M0-M11 inventory still records dependency and breadth gaps. |
| `01-pipeline-ir-contracts.md` | Accepted | `KanvasPipelineIRTest`, `CpuScalarPipelineExecutorTest`, `pipelineConformance`. | Future paint families must add operations under this contract. |
| `02-wgsl-parser-reflection-module-builder.md` | Draft | `WgslValidationReportTest`, generated WGSL parser tests. | Existing handwritten WGSL still reports parser diagnostics; broad generated packers remain future work. |
| `03-cpu-pipeline-backend.md` | Accepted | CPU scalar tests, vector allocation benchmark decision, PM report. | Vector remains rejected as a default path until the benchmark gate passes. |
| `04-gpu-generated-wgsl-backend.md` | Accepted | Generated solid/linear WGSL tests, PipelineKey tests, WebGPU selector tests. | GPU adapter CI is skipped under current gate and remains residual risk. |
| `05-blend-fallback-diagnostics.md` | Accepted | `BlendPlanTest`, fallback diagnostics in conformance report. | New blend families still need per-family evidence before promotion. |
| `06-runtime-effects-descriptor.md` | Accepted | Duplicate registration rejection, support matrix export, implementation-id validation. | New runtime effects must register descriptors and parser evidence. |
| `07-validation-performance-and-migration.md` | Accepted | `pipelineConformance`, `pipelineConformanceReport`, Linear evidence comments. | Slow benchmarks remain explicitly opt-in. |

## Spec Index

| Spec | Purpose |
|---|---|
| `00-current-state-inventory.md` | Current M0-M11 implementation state, evidence, and gaps. |
| `01-pipeline-ir-contracts.md` | `KanvasPipelineIR`, operation ordering, value semantics, fallback plans, and coverage handoff. |
| `02-wgsl-parser-reflection-module-builder.md` | Parser dependency, validation, reflection, WGSL IR/module builder, and uniform packer rules. |
| `03-cpu-pipeline-backend.md` | CPU scalar/vector execution, memory model, reference behavior, and benchmarks. |
| `04-gpu-generated-wgsl-backend.md` | Generated WGSL pipeline selection, `PipelineKey`, caches, resource lifecycle, and GPU gates. |
| `05-blend-fallback-diagnostics.md` | `BlendPlan`, fallback taxonomy, diagnostic dumps, and refusal behavior. |
| `06-runtime-effects-descriptor.md` | Registered runtime-effect descriptors, support matrix, CPU/GPU implementations, and misses. |
| `07-validation-performance-and-migration.md` | Test layers, PM evidence, migration stages, retirement policy, and milestone acceptance. |
| `08-bitmap-image-rect-sampling.md` | M32 bitmap/image-rect strict sampling, smoke promotion, and closeout evidence. |
| `09-image-filter-mvp-lane.md` | M34 image-filter MVP boundary plus M38 selected `Crop(kDecal, input = Offset(null))` child pre-pass promotion and remaining out-of-scope `Crop(input = nonNull)` limitations. |
| `10-scene-evidence-dashboard.md` | M36 post-MVP scene dashboard with CPU/GPU renders, Skia/reference diffs, route diagnostics, and stats. |
| `11-conformance-dashboard-generation.md` | M41 generated conformance dashboard contract for test-produced scene evidence. |
| `12-benchmark-harness-and-performance-gates.md` | M43 benchmark harness and performance gate policy for measured CPU/GPU metrics. |
| `13-scene-tag-taxonomy.md` | M41 scene tag taxonomy for dashboard filtering, search, and feature/maturity/risk aggregates. |

Decision records live under `adr/`.

## Target Shape

```mermaid
flowchart TD
    canvas["SkCanvas draw*"] --> device["SkDevice operation"]
    device --> geometry["Geometry/Coverage boundary"]
    geometry --> paint["Paint lowering"]
    paint --> ir["KanvasPipelineIR"]
    ir --> normalize["Normalization and specialization"]
    normalize --> cpu["CPU scalar/vector plan"]
    normalize --> gpu["GPU generated WGSL plan"]
    gpu --> parser["Parser validation and reflection"]
    parser --> webgpu["WebGPU shader module and pipeline cache"]
    cpu --> evidence["Old/new pixel diff, dumps, benchmarks"]
    webgpu --> evidence
```

```mermaid
C4Container
    title WGSL Paint Pipeline Spec Pack

    System_Boundary(kanvas, "Kanvas Rendering") {
        Container(canvas, "SkCanvas / SkDevice", "Kotlin", "Draw API, CTM, paint, clip state")
        Container(geometry, "Geometry/Coverage", "Kotlin", "Produces CoveragePlan and coverage modulation input")
        Container(ir, "Paint PipelineIR", "Kotlin", "Shader, color filter, blend, color space, coverage, fallback")
        Container(parser, "WGSL Parser / IR", "webgpu-ktypes", "Validation, reflection, deterministic module construction")
        Container(cpu, "CPU Pipeline Backend", "Kotlin / Java 25", "Scalar plans, optional Vector API kernels, old-path equivalence")
        Container(gpu, "WebGPU Generated Backend", "Kotlin / WGSL", "Generated modules, PipelineKey, BlendPlan, caches, resources")
        Container(runtime, "Runtime Effect Registry", "Kotlin / WGSL", "Registered effect descriptors and explicit misses")
        Container(evidence, "Validation Evidence", "Tests / Reports", "Dumps, goldens, cross-backend diffs, benchmarks, PM artifacts")
    }

    Rel(canvas, geometry, "submits draw geometry")
    Rel(geometry, ir, "passes coverage input")
    Rel(ir, cpu, "specializes CPU plan")
    Rel(ir, gpu, "specializes GPU plan")
    Rel(parser, gpu, "validates and reflects WGSL")
    Rel(runtime, ir, "appends registered effect descriptors")
    Rel(cpu, evidence, "proves CPU reference behavior")
    Rel(gpu, evidence, "proves generated GPU behavior")
```

## Milestone Coverage

| Milestone | Linear | Spec owner |
|---|---|---|
| M0 Parser deps ready | GRA-18 | `02-wgsl-parser-reflection-module-builder.md` |
| M1 Pipeline IR foundation | GRA-19 | `01-pipeline-ir-contracts.md` |
| M2 WGSL validation and reflection | GRA-20 | `02-wgsl-parser-reflection-module-builder.md` |
| M3 CPU scalar pilot | GRA-21 | `03-cpu-pipeline-backend.md` |
| M4 Generated WGSL pilot | GRA-22 | `04-gpu-generated-wgsl-backend.md` |
| M5 Uniform packer generated/verified | GRA-23 | `02-wgsl-parser-reflection-module-builder.md` |
| M6 GPU pipeline key and cache telemetry | GRA-24 | `04-gpu-generated-wgsl-backend.md` |
| M7 BlendPlan and fallback diagnostics | GRA-25 | `05-blend-fallback-diagnostics.md` |
| M8 Generated gradient WGSL | GRA-26 | `04-gpu-generated-wgsl-backend.md` |
| M9 Runtime effect descriptor pilot | GRA-27 | `06-runtime-effects-descriptor.md` |
| M10 Java 25 Vector pilot | GRA-28 | `03-cpu-pipeline-backend.md` |
| M11 Migration batch 1 | GRA-29 | `07-validation-performance-and-migration.md` |
| M32 Bitmap/ImageRect remediation | GRA-93 | `08-bitmap-image-rect-sampling.md` |
| M34 Image-filter MVP lane | GRA-102 | `09-image-filter-mvp-lane.md` |
| M36 Scene evidence dashboard | GRA-162 | `10-scene-evidence-dashboard.md` |
| M41 Generated conformance dashboard | Proposed | `11-conformance-dashboard-generation.md` |
| M43 Real benchmark harness | Proposed | `12-benchmark-harness-and-performance-gates.md` |

## Spec Acceptance Rules

A WGSL paint-pipeline spec is accepted only when it names:

- affected modules and ownership boundaries;
- explicit non-goals;
- data contracts and invariants;
- CPU reference behavior or explicit CPU refusal;
- GPU generated behavior or explicit GPU refusal;
- parser/reflection requirements when WGSL is touched;
- stable fallback reasons and diagnostic dumps;
- tests, visual artifacts, benchmark counters, or generated goldens;
- unresolved questions that must block implementation tickets.

## Design Decisions

The initial design questions are tracked as ADRs so implementation tickets do
not reopen them ad hoc:

- `adr/0001-pipeline-ir-is-semantic-boundary.md`: keep `KanvasPipelineIR` as
  the shared semantic boundary.
- `adr/0002-wgsl-ir-is-gpu-module-layer.md`: use WGSL IR for concrete GPU
  module construction only.
- `adr/0003-java25-vector-is-optional.md`: keep scalar CPU execution as the
  correctness path and Vector API as measured acceleration.
- `adr/0004-pipeline-key-axis-taxonomy.md`: add only layout, code, or pipeline
  state axes to generated GPU keys.
- `adr/0005-runtime-effects-are-registered.md`: keep runtime effects explicit
  and registered instead of compiling arbitrary SkSL.
- `adr/0006-webgpu-device-thread-ownership.md`: keep WebGPU device, caches, and
  telemetry updates on the render/device owner thread until a future ADR
  introduces shared compilation.
