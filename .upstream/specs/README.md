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

Use the pack README as the entry point before planning or reviewing work in
that area.
