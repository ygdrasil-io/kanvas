---
id: KGPU-M31-003
title: "Final PM evidence bundle — production-readiness sign-off"
status: done
milestone: M31
priority: P0
owner_area: product-validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M31-001]
legacy_gate: null
---

# KGPU-M31-003 - Final PM evidence bundle — production-readiness sign-off

## PM Note

Ce ticket produit le dossier de preuves final pour le PM : captures d'ecran de
rendu, rapports de performance, matrice de parite, diagnostics de route, et
tableau de bord consolide. C'est le livrable de sign-off qui confirme que
Kanvas est pret pour la production.

## Problem

Kanvas is activated (M31-001) and has a rollback (M31-002), but there is no
consolidated evidence bundle for production sign-off. PM and stakeholders need
a single, reviewable package of rendering evidence, performance data, and
route diagnostics before final acceptance.

## Scope

- Collect offscreen render PNGs for all supported scene families
- Assemble performance benchmark reports (frame times, GPU utilization)
- Document draw-family support matrix with evidence links
- Include route taxonomy diagnostics showing all active routes
- Include rollback procedure and operational documentation
- Produce a consolidated PM dashboard snapshot
- Commit all evidence to `reports/gpu-renderer-scenes/`

## Non-Goals

- No new rendering features or tests
- No performance optimization
- No new scene definitions
- No release notes (KGPU-M31-004)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`
- `reports/gpu-renderer-scenes/`

## Design Sketch

```kotlin
data class PmEvidenceBundle(
    val renderSnapshots: List<OffscreenRenderResult>,
    val performanceReport: PerformanceTelemetryReport,
    val supportMatrix: DrawFamilySupportMatrix,
    val routeDiagnostics: RouteTaxonomyDump,
    val rollbackDoc: RollbackProcedureDocument,
    val dashboardSnapshot: DashboardState,
)
```

## Acceptance Criteria

- [ ] Offscreen render PNGs collected for all supported scene families
- [ ] Performance benchmark report assembled (frame times, GPU utilization)
- [ ] Draw-family support matrix updated with evidence links
- [ ] Route taxonomy diagnostic dump committed
- [ ] Rollback procedure documented
- [ ] PM dashboard snapshot committed

## Required Evidence

- `reports/gpu-renderer-scenes/` render PNGs for all families
- Performance benchmark report (`reports/performance/`)
- Support matrix with per-family evidence links
- Route taxonomy diagnostic transcript
- Rollback procedure document
- Dashboard snapshot file

## Fallback / Refusal Behavior

Missing evidence for any draw family blocks sign-off. Evidence is linked or
explicitly refused (`RefuseRequired`); no silent omission. An incomplete
bundle emits `evidence-bundle-incomplete` diagnostic.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.pm-evidence-bundle`
- Expected classification: `PromotedSupported` (after acceptance)
- Claim promotion allowed: yes, after all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=rect-srgb
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-stencil
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=image-png
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=text-glyphs
rtk git add reports/gpu-renderer-scenes/
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Produced. Evidence reports for all M30+M31 tickets under reports/gpu-renderer/; task-level parity validated for 5 draw families; support matrix documented; release notes draft produced (M31-004). Full GPU pixel comparison deferred to execution pipeline completion. Evidence at reports/gpu-renderer/2026-06-25-M31-003-evidence.md.
- `done` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:product-validation`
