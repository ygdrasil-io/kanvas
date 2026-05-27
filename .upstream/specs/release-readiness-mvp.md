# MVP Release Readiness

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M35 -- MVP Release Candidate

## Purpose

Define the release-readiness gate for the WGSL/WebGPU MVP after M32, M33, and
M34 evidence has landed.

This is a release gate, not a new rendering architecture. It collects the
accepted state, required CI, inventory classifications, smoke scope, and
known limitations so the MVP can be evaluated without reopening archived
migration plans.

## Inputs

| Milestone | Required input |
|---|---|
| M32 | Bitmap/image-rect accepted spec and closeout evidence. |
| M33 | Path AA boundary evidence, edge-budget classification, and smoke decision. |
| M34 | Image-filter MVP decision, accepted `Crop(input = nonNull)` limitation, and inventory/smoke guard evidence. |
| M35 | Final CI, conformance, inventory audit, README/spec sync, and PM package. |

## MVP Release Gates

The MVP release candidate is ready only when:

- required raster and GPU CI checks pass on current `master`;
- `pipelineConformance` and `pipelineConformanceReport` complete or have a
  documented environment blocker;
- `gpuSmokeTest` passes on an adapter-backed lane;
- full GPU inventory is classified with no unowned blocker category;
- bitmap/image-rect similarity regressions remain zero;
- `SaveLayer kScreen` remains out of `unexpected-exception`;
- Path AA edge-budget refusals are expected unsupported or resolved;
- `Crop(input = nonNull)` image-filter pre-pass gaps are accepted MVP
  limitations or resolved, and no `SimpleOffsetImageFilter*` fixture is in
  required GPU smoke while it emits `image-filter.crop-input-nonnull-prepass-required`;
- README and active specs match Linear milestone state;
- PM evidence links PRs, CI, reports, limitations, and follow-ups.

## Current Accepted Limitations

M34 intentionally retains `SkImageFilters.Crop(input = nonNull)` as an MVP
limitation. The stable inventory reason is
`image-filter.crop-input-nonnull-prepass-required`, currently limited to:

- `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest`

The limitation is release-acceptable only while required GPU smoke excludes
those fixtures and the final inventory audit classifies them as expected
unsupported rather than unexpected failures.

## Required Reports

M35 should leave these PM-readable artifacts under `reports/wgsl-pipeline/`:

- final required CI and conformance evidence;
- final full GPU inventory audit;
- MVP evidence package;
- Linear/project closeout note if administrative closure is required.

## Non-Goals

The release candidate does not:

- promise full Skia coverage;
- include Ganesh, Graphite, SkSL compiler, SkSL IR, or SkVM ports;
- hide inventory failures by bulk floor lowering;
- treat dependency-gated font/codec gaps as implemented.

## Status Policy

This spec remains Draft until M35 closeout evidence lands. It can move to
Accepted only after the Linear project state, README status, and reports agree
on the final MVP decision.
