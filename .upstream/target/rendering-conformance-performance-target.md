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

## PM Readiness

Current Post-MVP Big Target readiness for MEP: 40%.

This is a PM readiness score for the full target, not an effort estimate and not
the completion state of the latest Linear sprint. M41-M47 completed the evidence
foundation. M48 expanded representative Skia integration breadth enough to move
coverage readiness from 15% to 35%, but the platform is not yet MEP-ready
because CI gates, performance trends, broader adapter-backed coverage, and
repeatable PM demo/reporting still need dedicated work.

| Area | Weight | Current state | Progress |
|---|---:|---|---:|
| Evidence foundation | 25% | M41-M48 complete: generated dashboard, 21 generated rows, 0 tracked-gap, 0 fail. | 100% |
| Skia integration coverage | 25% | M48 adds 10 selected rows across paint, clip, transform, bitmap, gradient, Path AA, and image-filter breadth while keeping unsupported scope explicit. | 35% |
| CI and release gates | 20% | Dashboard generation is validated; release-grade promotion and inventory gates are not complete. | 10% |
| Performance readiness | 15% | Measured payloads exist, but trends remain reporting-only and thresholds are not release gates. | 15% |
| PM demo and reporting workflow | 15% | Static local dashboard exists; deployable/repeatable PM workflow is still missing. | 15% |

The resulting weighted readiness rounds to 40%. Evidence-hardening through M47
is 100% complete, and M48 coverage expansion is complete for its selected scene
pack. These are still only parts of the larger MEP target.

Before MEP, Kanvas still needs:

- broader Skia integration scene coverage;
- generated evidence as the default path for new support claims;
- CI gates for required conformance, allowed expected-unsupported rows, and
  non-blocking inventory;
- performance trends with approved release thresholds;
- a repeatable PM demo/reporting workflow that is not only local/static;
- a final MEP acceptance checklist linking Linear, CI, dashboard output,
  reports, and known limitations.

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
| M47 | Remaining Static Evidence Hardening | Convert remaining static pass rows to generated evidence and keep Path AA expected-unsupported rows explicit as policy evidence. |
| M48 | MEP Scene Coverage Expansion | Add representative P0/P1 Skia scene breadth across paint, clip, transform, bitmap, gradient, Path AA, and image-filter planning rows. |

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

After M48, the merged dashboard export has:

- 23 scene rows;
- 18 pass;
- 0 tracked-gap;
- 5 expected-unsupported;
- 0 fail;
- 21 generated evidence rows;
- 2 static evidence rows;
- 2 adapter-backed P0 rows;
- tag aggregates for `feature.*`, `maturity.*`, and `risk.*`.

M48 support and refusal evidence is linked from:

- `reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md`.

The two static rows remain deliberate Path AA policy sentinels, not unowned
conversion debt. M48 adds three generated expected-unsupported breadth rows so
future planning sees the high-value unsupported surface area instead of hiding
it.

The current expected unsupported rows are:

- `path-aa-stroke-outline-fallback` with
  `coverage.stroke-outline-edge-count-exceeded`;
- `path-aa-edge-budget-boundary` with `coverage.edge-count-exceeded`.
- `path-aa-convexpaths-edge-budget` with `coverage.edge-count-exceeded`;
- `path-aa-dashing-edge-budget` with `coverage.edge-count-exceeded`;
- `image-filter-crop-nonnull-prepass-required` with
  `image-filter.crop-input-nonnull-prepass-required`.

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
