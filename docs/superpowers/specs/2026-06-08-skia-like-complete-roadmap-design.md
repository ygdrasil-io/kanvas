# Skia-Like Complete Roadmap Design

Date: 2026-06-08
Status: approved design

## Purpose

This design records the approved roadmap shape for a complete Kanvas
Skia-like production plan. The detailed specs, milestones, ticket templates,
GM registry policy, and production-readiness criteria are stored in Basic
Memory under:

- `global/kanvas/skia-like-roadmap-2026-06-08/kanvas-skia-like-roadmap-program-spec-2026-06-08`
- `global/kanvas/skia-like-roadmap-2026-06-08/kanvas-skia-like-gm-registry-and-triage-2026-06-08`
- `global/kanvas/skia-like-roadmap-2026-06-08/kanvas-skia-like-milestone-map-m89-m100-2026-06-08`
- `global/kanvas/skia-like-roadmap-2026-06-08/kanvas-skia-like-ticket-backlog-templates-2026-06-08`
- `global/kanvas/skia-like-roadmap-2026-06-08/kanvas-skia-like-production-readiness-criteria-2026-06-08`

## Approved Direction

Use the hybrid roadmap structure:

1. A program-level spec that defines the production goal, hard architecture
   constraints, status vocabulary, and non-claims.
2. A normalized GM registry that includes every supported, unsupported,
   dependency-gated, implementation-gap, reporting-only, and
   below-threshold-excluded row.
3. A milestone map from M89 through M100 that advances from registry
   normalization to a Skia-like production release candidate.
4. Ticket templates organized by technical family so Linear tickets can be
   generated consistently.
5. Production-readiness criteria that explicitly exclude tolerance-only visual
   misses from missing-feature accounting.

## Core Rules

- WGSL remains the WebGPU shader implementation target.
- SkSL is compatibility/refusal wording only; Kanvas does not build dynamic
  SkSL compilation, SkSL IR, or SkSL VM support.
- WebGPU remains the GPU backend; do not port Ganesh or Graphite.
- Runtime effects require registered Kanvas descriptors, Kotlin CPU behavior,
  and parser-validated WGSL GPU modules.
- Kadre remains the native/live host path.
- Unsupported GM rows must stay visible with stable fallback reasons.
- D50/D53 policy-only visibility rows must not count as support until
  row-specific artifacts prove support.
- Rows failing only because a strict similarity/tolerance threshold is not met
  stay in fidelity burn-down and are not counted as production missing
  features.

## Memory Package

The Basic Memory package is the source of truth for planning artifacts:

```text
kanvas/skia-like-roadmap-2026-06-08/
```

The next step is to review those notes and, after approval, translate the
milestone map into concrete Linear tickets or implementation plans.
