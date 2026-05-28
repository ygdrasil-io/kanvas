# Conformance Dashboard Generation

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`
Milestone: M41 -- Generated Conformance Dashboard

## Purpose

Move the scene dashboard from static registry evidence toward generated
conformance evidence produced by tests and report tasks.

The dashboard remains PM-readable, but the source of truth should become
machine-generated scene results where possible. Static rows may remain during
migration, but they must not be confused with generated conformance.

## Inputs

Each generated scene result must provide:

- scene id and title;
- source test or report task;
- reference artifact and `referenceKind`;
- CPU render, diff, route JSON, and stats;
- GPU render, diff, route JSON, and stats when GPU renders;
- stable fallback reason when GPU refuses;
- optional `performanceTrend`;
- raw evidence links.

## Scene Status Rules

`pass` requires:

- reference artifact;
- CPU render and route diagnostics;
- GPU render and route diagnostics for GPU-eligible scenes;
- diff and stats artifacts;
- `fallbackReason=none` for supported GPU rows.

`tracked-gap` requires:

- at least one meaningful lane of evidence;
- an explicit missing artifact or environment reason;
- a closeout note or follow-up ticket.

`expected-unsupported` requires:

- a GPU section;
- stable fallback reason;
- CPU/reference evidence when possible;
- inventory or spec reference explaining why the refusal is intentional.

`fail` is reserved for scenes that are claimed supported but miss thresholds,
artifacts, or route invariants.

## Generated Artifact Layout

Generated outputs should keep the existing static shape:

```text
build/reports/wgsl-pipeline-scenes/
  index.html
  data/scenes.json
  artifacts/
    <scene-id>/
      skia.png
      cpu.png
      gpu.png
      cpu-diff.png
      gpu-diff.png
      route-cpu.json
      route-gpu.json
      stats.json
```

Additional raw files may be linked from `evidence`, but the dashboard must keep
the canonical artifact names stable when a file exists.

## Reference Policy

References must be explicit:

- `skia-upstream`: preferred when upstream or imported Skia evidence exists;
- `cpu-oracle`: valid when no upstream image exists and the CPU route is the
  comparison oracle;
- `test-oracle`: valid when a test fixture provides a deterministic reference.

The dashboard must explain the reference type inline and must not imply full
Skia parity from one scene row.

## Route Diagnostics

Every generated row must expose:

- CPU selected route;
- GPU selected route or coverage strategy;
- compatibility fallback route if used;
- `CoveragePlan` or `PipelineIR` participation when applicable;
- `PipelineKey` or shader/module identity when GPU WGSL participates;
- stable fallback reason or `none`;
- source test name.

Route diagnostics are not enough for support claims without rendered evidence.

## Filters And PM Readability

The dashboard should provide filters for:

- status;
- priority;
- reference type;
- GPU fallback reason;
- performance status;
- text search across scene id, title, route, and fallback.

Artifact lists should be collapsed by default. The primary visual evidence
should show reference, CPU, GPU, CPU diff, and GPU diff panels directly.

## Validation

Required validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Generated scene tasks must also validate that every referenced artifact exists
and that unsupported GPU rows include stable fallback reasons.

## Non-Goals

- Do not build the native live editor in M41.
- Do not require all rows to be generated before allowing mixed static/generated
  evidence.
- Do not make adapter-missing rows pass.
- Do not lower image similarity floors to hide regressions.
