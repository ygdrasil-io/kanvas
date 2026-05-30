# Target: Rendering Conformance And Performance Platform

Date: 2026-05-28
Status: Proposed
Parent target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Input evidence:

- `reports/wgsl-pipeline/scenes/data/scenes.json`
- `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md`
- `.upstream/specs/wgsl-pipeline/10-scene-evidence-dashboard.md`

## Purpose

The WGSL/WebGPU MVP proved that Kanvas can expose CPU/GPU scene evidence with
references, diffs, route diagnostics, fallback reasons, and early performance
fields. The next big target is to turn that static evidence into a conformance
and performance platform.

The target is not just more dashboard rows. It is a generated evidence system
that makes every rendering support claim reviewable by product, engineering,
and release owners.

## Big Target

Kanvas should provide a CPU/WebGPU rendering conformance and performance
platform where promoted scenes are generated from tests, compared against
Skia/upstream or CPU-oracle references, annotated with route diagnostics, and
tracked over time for correctness and performance regressions.

The platform must answer five questions for every promoted scene:

- what reference is being compared;
- what CPU route produced the oracle or CPU lane;
- what GPU route rendered or refused the scene;
- what visual/statistical result was produced;
- whether the result is support, a tracked gap, or expected unsupported scope.

## Evidence Levels

| Level | Meaning | May claim support? |
|---|---|---|
| Static registry | Hand-authored or checked-in `scenes.json` plus artifacts. | No, unless backed by linked test evidence. |
| Generated conformance | Test run generates image, diff, route, and stats artifacts. | Yes, for the exact scene contract. |
| Adapter-backed GPU | GPU artifacts were produced on a named adapter/backend. | Yes, for GPU support when thresholds pass. |
| Measured performance | Benchmark harness writes host/JDK/adapter/baseline timing. | Yes, for performance trend claims. |
| Estimated performance | Static seed metrics used for dashboard shape. | No, informational only. |

## Support Claim Rules

A scene may be `pass` only when:

- reference artifact exists;
- CPU evidence exists;
- GPU evidence exists when the scene is GPU-eligible;
- CPU/GPU route diagnostics exist;
- diff and stats artifacts exist;
- fallback reason is `none` for supported GPU rows.

A scene must be `tracked-gap` when:

- at least one route is known, but a required capture, diff, metric, or adapter
  artifact is missing;
- the limitation is temporary and has a concrete closure path.

A scene must be `expected-unsupported` when:

- GPU intentionally refuses the scene;
- the refusal has a stable fallback reason;
- the unsupported breadth remains useful evidence for future planning.

Do not mark a rendering family supported from route diagnostics alone. A support
claim needs rendered evidence or a documented CPU-only non-goal.

## Non-Goals

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Do not turn estimated metrics into performance gates.
- Do not hide unsupported rows by removing them from inventory or dashboard
  evidence.
- Do not broaden required GPU smoke without adapter-backed evidence.
- Do not add short-lived font/codec substitutes for dependency-gated gaps.

## Proposed Milestones

| Milestone | Name | Target outcome |
|---|---|---|
| M41 | Generated Conformance Dashboard | Dashboard rows are produced from test outputs instead of only static registry artifacts. |
| M42 | Adapter-Backed P0 GPU Captures | Existing P0 `tracked-gap` rows become pass or intentionally scoped expected unsupported with adapter-backed evidence. |
| M43 | Real Benchmark Harness | CPU/GPU `performanceTrend` fields are written by benchmark runs with host/JDK/adapter metadata. |
| M44 | First Real Path AA Family Promotion | One narrow Path AA family moves from expected unsupported to rendered GPU support with CPU/GPU/reference evidence. |
| M45 | Image-Filter DAG Subset V1 | A bounded image-filter DAG subset renders through explicit pre-pass/layer contracts and dashboard evidence. |
| M46 | Generated Evidence Expansion | Convert the next high-value static dashboard rows to generated evidence while keeping zero tracked gaps and zero failing support claims. |

## Current Baseline

The M40 static dashboard started with:

- 11 scene rows;
- 7 pass;
- 2 tracked-gap;
- 2 expected-unsupported;
- 0 fail;
- 5 CPU performance rows marked `estimated`;
- 5 GPU/cache performance rows marked `estimated`.

The two current `tracked-gap` P0 rows are:

- `solid-rect`;
- `analytic-aa-convex`.

Both have route evidence but are missing adapter-backed GPU render captures in
the current dashboard evidence.

After M46, the merged dashboard export has:

- 13 scene rows;
- 11 pass;
- 0 tracked-gap;
- 2 expected-unsupported;
- 0 fail;
- 8 generated evidence rows;
- 5 static evidence rows;
- 2 adapter-backed P0 rows;
- tag aggregates for `feature.*`, `maturity.*`, and `risk.*`.

M46 closed on 2026-05-30 by
`reports/wgsl-pipeline/2026-05-30-m46-generated-evidence-expansion-closeout.md`.
The remaining static rows are explicitly owned by follow-up scope and are not
hidden from the dashboard.

The two remaining expected unsupported rows are:

- `path-aa-stroke-outline-fallback` with
  `coverage.stroke-outline-edge-count-exceeded`;
- `path-aa-edge-budget-boundary` with `coverage.edge-count-exceeded`.

## Agent Execution Policy

Agents working on M41+ must:

- read `.upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md`
  before modifying dashboard generation or scene evidence;
- read `.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`
  before modifying performance fields, benchmark output, or regression gates;
- keep `reports/wgsl-pipeline/scenes/data/scenes.json` machine-readable and
  deterministic;
- keep fallback reasons stable and visible;
- report whether evidence is static, generated, adapter-backed, measured, or
  estimated;
- link Linear issues, PRs, commands, and generated artifacts in closeout
  comments.

## Validation

Any milestone that changes dashboard generation or scene evidence must run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Milestones that claim runtime support or performance must also run the owning
test or benchmark task and link the generated raw artifacts.
