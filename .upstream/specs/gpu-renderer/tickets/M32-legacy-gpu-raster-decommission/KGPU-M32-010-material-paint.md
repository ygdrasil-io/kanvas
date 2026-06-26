---
id: KGPU-M32-010
title: "Legacy decommission: material-paint port (SolidColor) / refuse (gradients + shader pipeline)"
status: proposed
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M11-009]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-010 - Legacy decommission: material-paint port (SolidColor) / refuse (gradients + shader pipeline)

## PM Note

Cette famille décide du sort du « pipeline de matière / paint » legacy. Le cas
`SolidColor` est déjà rendu par le bridge Kanvas (couvert par rect/rrect/path),
donc il est porté ; les dégradés et le pipeline de shaders restent refusés et
dépendent de KGPU-M11-009. Le PM doit suivre ce ticket car il conditionne le
retrait de la route matière legacy : on ne supprime pas le legacy tant que le
baseline porté n'est pas revu et que les sous-cas refusés n'ont pas de
diagnostic stable.

## Problem

`GpuRendererLegacyRouteFamily.material-paint` is a partial family. Per the
accepted decision matrix (`reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md`
row 1):

- `SolidColor` material is dispatched across all 4 Kanvas commands (FillRect,
  FillRRect, FillPath, DrawTextRun) — this baseline is **ported**.
- `LinearGradient` / `RadialGradient` / `SweepGradient` materials are lowered in
  `Canvas.kt:40-71` but **refused** at dispatch with `unsupported_material`.
  No shader/material pipeline dispatch exists.

Legacy `gpu-raster` cannot be retired for this family until the SolidColor
baseline is independently reviewed and the gradient/shader sub-cases each carry
a stable refuse diagnostic linked to their future port (KGPU-M11-009).

## Scope

Ported sub-cases (this ticket asserts/reviews the baseline route_kind=GPUNative):

- SolidColor material dispatched on the Kanvas bridge for the supported draw
  commands, with real GPU pixel parity vs an independent CPU reference. The
  SolidColor baseline is already exercised by the rect/rrect/path/A8-text
  evidence (see Required Evidence); this ticket binds that evidence to the
  `material-paint` family for retirement-gate authorization.

## Non-Goals

- Do not port gradient materials (Linear/Radial/Sweep) or the general
  shader/material pipeline here — those are refused and tracked by KGPU-M11-009.
- Do not add a short-lived substitute for gradients/shaders (per AGENTS.md:
  dependency-gated families must not get short-lived substitutes; they remain
  formal refusals linked to their dependency ticket).
- Do not add hidden CPU-rendered texture compatibility.
- Refused sub-cases emit a stable `refuse:material:unsupported_material:<Kind>`
  diagnostic; they must not be silently served by the production-default path.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 1)

## Graphite Algorithm References

- n/a — not required for this slice (SolidColor baseline reuses existing
  solid-coverage dispatch; gradient/shader algorithm study belongs to
  KGPU-M11-009).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (SolidColor) / "RefuseDiagnostic" (gradient+shader)
    val dumpRefs: List<String>,       // reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md
    val diagnostics: List<String>,    // refuse:material:unsupported_material:LinearGradient, ...
)
```

## Acceptance Criteria

- [ ] SolidColor material draw dispatched on the Kanvas bridge with real GPU
      pixel parity vs an INDEPENDENT CPU reference (similarity %, max channel
      delta, diff artifact) committed under `reports/gpu-renderer/`.
- [ ] Gradient (Linear/Radial/Sweep) and general shader-material draws emit a
      stable `refuse:material:unsupported_material:<Kind>` diagnostic; a hermetic
      regression test asserts the diagnostic and links KGPU-M11-009.
- [ ] No short-lived gradient/shader substitute is introduced (per AGENTS.md).

## Required Evidence

- SolidColor baseline parity is already satisfied (independent review still
  required): rect/rrect/path bridge↔legacy parity in
  `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
  (Rect 100.00% 40000/40000 maxDiff 0; RRect 99.77% 39908/40000 maxDiff 123;
  Path 100.00% 40000/40000 maxDiff 0) and `KGPU-M31-005` §3-5.
- Gradient/shader port: independent CPU pixel parity per gradient type + WGSL
  gradient/shader dispatch in `Surface.kt` — **not produced yet** (refused;
  tracked by KGPU-M11-009).
- Hermetic refuse regression test capturing the `unsupported_material`
  diagnostic — **not produced yet**.

## Fallback / Refusal Behavior

- Gradient and shader-material routes emit stable
  `refuse:material:unsupported_material:<Kind>` diagnostics.
- Silent fallback to CPU-rendered material/texture compatibility is not allowed.
- The `gpu-raster legacy` gate remains visible for the refused sub-cases until
  KGPU-M11-009 lands real port evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.material-paint`
- Expected classification: `ImplementationCandidate` (SolidColor baseline) +
  `RefuseRequired`/`DependencyGated` (gradients/shaders)
- Claim promotion allowed: no, unless Required Evidence is attached and
  independently reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=solid-paint-fill -PsceneOutput=build/gpu-renderer-scenes/solid-paint-fill.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*MaterialRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from the accepted KGPU-M32-001 decision
  matrix (row 1: partial — port SolidColor / refuse gradients + shader
  pipeline). SolidColor baseline already covered by KGPU-M32-002 evidence
  (independent review still required). Gradient/shader remainder is refused and
  dependency-linked to KGPU-M11-009. No new evidence produced by this ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
