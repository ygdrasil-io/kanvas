# Upstream-Inspired Implementation Specs

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

This directory contains active implementation specs for the Kanvas WGSL/WebGPU
pipeline target. Archived root plans remain historical evidence only and must
not be treated as active backlog.

## Spec Packs

| Pack | Scope |
|---|---|
| `wgsl-pipeline/` | Pre-Geometry paint-pipeline work: PipelineIR, WGSL parser/reflection/module builder, CPU scalar/vector, generated WGSL, BlendPlan, runtime effects, validation, and migration. |
| `geometry-coverage/` | Geometry and coverage work: GeometryPlan, CoveragePlan, lowering rules, CPU coverage oracle, WebGPU coverage strategies, diagnostics, validation, and migration. |
| `release-readiness-mvp.md` | MVP release candidate gate tying M32 image-rect, M33 Path AA, M34 image filters, required CI, inventory classification, and PM evidence together. |

Use the pack README as the entry point before planning or reviewing work in
that area.

## MVP Tail Specs

| Milestone | Spec | Status |
|---|---|---|
| M32 | `wgsl-pipeline/08-bitmap-image-rect-sampling.md` | Accepted |
| M33 | `geometry-coverage/08-path-aa-mvp-boundary.md` | Accepted |
| M34 | `wgsl-pipeline/09-image-filter-mvp-lane.md` | Accepted |
| M35 | `release-readiness-mvp.md` | Draft (technical gates green, PM closeout pending) |
