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
| `font/` | Font and text work: pure Kotlin OpenType backend, SkFont/SkTypeface/SkFontMgr, shaping boundary, glyph rendering, glyph masks, color fonts, emoji, fixtures, and font conformance gates. |
| `front/` | Front-facing evidence experience: dashboard UX, filters, artifact browsing, PM reporting workflow, accessibility, and export quality gates. Does not define rendering support. |
| `release-readiness-mvp.md` | MVP release candidate gate tying M32 image-rect, M33 Path AA, M34 image filters, required CI, inventory classification, and PM evidence together. |
| `../target/post-mvp-pipeline-backlog.md` | Closed M36-M40 static dashboard and post-MVP pipeline evidence backlog. |
| `../target/rendering-conformance-performance-target.md` | Big target for the post-MVP rendering conformance and performance platform. |
| `../target/post-mvp-conformance-backlog.md` | Proposed M41-M45 backlog for generated conformance, adapter-backed captures, measured benchmarks, Path AA promotion, and image-filter DAG subset. |

Use the pack README as the entry point before planning or reviewing work in
that area.

## MVP Tail Specs

| Milestone | Spec | Status |
|---|---|---|
| M32 | `wgsl-pipeline/08-bitmap-image-rect-sampling.md` | Accepted |
| M33 | `geometry-coverage/08-path-aa-mvp-boundary.md` | Accepted |
| M34 | `wgsl-pipeline/09-image-filter-mvp-lane.md` | Accepted |
| M35 | `release-readiness-mvp.md` | Accepted |
| M36 | `wgsl-pipeline/10-scene-evidence-dashboard.md` | Draft |
| M41 | `wgsl-pipeline/11-conformance-dashboard-generation.md` | Draft |
| M43 | `wgsl-pipeline/12-benchmark-harness-and-performance-gates.md` | Draft |
| Font | `font/README.md` | Draft |
| Front | `front/README.md` | Draft |

## Sprint Evidence

| Scope | Report | Status |
|---|---|---|
| M33-M35 MVP tail closeout | `reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md` | Verified complete |
| M36-M40 dashboard closeout | `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md` | Verified complete |
