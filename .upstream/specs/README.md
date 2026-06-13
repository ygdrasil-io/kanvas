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
| `skia-like-realtime/` | Active post-MEP target: missing rendering features, Skia-like fidelity, real-time runtime, performance tiering, PM demos, and release-candidate planning. |
| `gpu-renderer/` | Proposed GPU-first renderer direction: Graphite-inspired inline `GPU` architecture, normalized draw commands, WGSL material keys, pipeline keys, route policy, legacy adapter cleanup, and validation gates. |
| `release-readiness-mvp.md` | Historical MVP release candidate gate tying M32 image-rect, M33 Path AA, M34 image filters, required CI, inventory classification, and PM evidence together. |

Use the pack README as the entry point before planning or reviewing work in
that area.

## Active Target

| Target | Scope |
|---|---|
| `../target/skia-like-realtime-renderer-target.md` | Active M60-M70 ambition: add missing features, converge toward Skia CPU output, and deliver a real-time renderer with PM-visible demos. |
| `../target/high-performance-wgsl-pipeline-target.md` | Parent architecture for PipelineIR, WGSL parser/generator, CPU/GPU convergence, and runtime-effect constraints. |

## Historical Target Archive

| Archive | Historical scope |
|---|---|
| `../../archives/target-closeout-2026-05-31/rendering-conformance-performance-target.md` | Completed MEP conformance/performance target, closed at 100% after M59. |
| `../../archives/target-closeout-2026-05-31/post-mvp-conformance-backlog.md` | Closed M41-M45 generated conformance, adapter-backed capture, benchmark, Path AA, and image-filter DAG backlog seed. |
| `../../archives/target-closeout-2026-05-31/post-mvp-pipeline-backlog.md` | Closed M36-M40 static dashboard and post-MVP pipeline evidence backlog. |
| `../../archives/target-closeout-2026-05-31/task-prompt-m0-m11.md` | Historical generic executor prompt for the old milestone range. |

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
