# 00 Current State And Gap Analysis

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Summary

M59 completed the MEP evidence target. Kanvas can now prove selected support
and refusal claims with dashboard artifacts, generated reports, and
release-blocking performance gates for selected measured rows. The next target
is broader: increase real rendering capability and package it as an
interactive renderer.

## What Is Already Strong

| Capability | Current state | Reuse in next target |
|---|---|---|
| Generated dashboard | Stable PM-visible evidence rows, artifacts, routes, counters. | Continue as conformance/reporting layer for every promoted feature. |
| CPU/GPU/reference comparison | Selected scenes compare CPU, WebGPU, reference, and diff. | Required for all new rendering claims. |
| Expected unsupported policy | Unsupported rows carry stable fallback reasons. | Required for broad Skia-like scope control. |
| Performance release gate | M59 gates seven selected rows and 14 lanes. | Expand to family and frame budgets. |
| WGSL/WebGPU architecture | Parser/generator direction, PipelineIR target, WebGPU backend. | Foundation for feature expansion and real-time runtime. |
| Agent workflow | Reviews, PM reports, and subagent execution pattern. | Continue for M60-M70 without a Linear dependency. |

## What Is Not Done

The 100% MEP score does not mean:

- broad Skia GM parity;
- arbitrary Path AA or complex clip support;
- arbitrary image-filter DAG support;
- full text shaping, font fallback, emoji, color fonts, or glyph atlas
  productization;
- complete blend/color/color-space parity;
- arbitrary runtime effect support;
- a native interactive renderer;
- frame-budgeted real-time release readiness.

## New Readiness Baseline

The new target starts at approximately 25%.

| Area | Weight | Initial progress | Evidence |
|---|---:|---:|---|
| Rendering feature breadth | 30% | 20% | Selected rows exist, broad families remain missing. |
| Skia-like fidelity | 20% | 25% | Diff infrastructure exists, broad burn-down is not done. |
| Real-time runtime | 20% | 10% | Dashboard exists, frame loop does not. |
| Performance and cache readiness | 15% | 35% | M59 selected gate exists, family/frame budgets do not. |
| PM/demo operability | 15% | 35% | PM bundle exists, live demo is missing. |

## Active Evidence Sources

- `reports/wgsl-pipeline/2026-05-31-m59-pm-report.md`
- `reports/wgsl-pipeline/2026-05-31-m59-sprint-review.md`
- `reports/wgsl-pipeline/2026-05-31-m59-performance-release-gate-selection.md`
- `reports/wgsl-pipeline/performance/m59-performance-release-gate.json`
- `reports/wgsl-pipeline/scenes/`
- `.upstream/source/map/`
- `.upstream/specs/wgsl-pipeline/`
- `.upstream/specs/geometry-coverage/`
- `.upstream/specs/font/`
- `.upstream/specs/front/`

## Historical Evidence

The previous active MEP target/backlog archive was removed from the working
tree. Recover it from Git history only if needed as evidence, not active
backlog.

## Planning Principles

- Promote rendering families by bounded slices.
- Keep unsupported breadth visible.
- Never copy artifacts to prove unrelated support.
- Prefer row-specific captures and route diagnostics.
- Add runtime features only with frame telemetry and cache counters.
- Tie every sprint to a PM-visible demo.
