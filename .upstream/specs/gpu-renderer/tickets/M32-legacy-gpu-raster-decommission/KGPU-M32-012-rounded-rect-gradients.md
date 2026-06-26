---
id: KGPU-M32-012
title: "Legacy decommission: rounded-rect-gradients port (solid uniform rrect) / refuse (gradients + non-uniform radii)"
status: proposed
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M2-002]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-012 - Legacy decommission: rounded-rect-gradients port (solid uniform rrect) / refuse (gradients + non-uniform radii)

## PM Note

Le rectangle arrondi plein à rayons uniformes est porté sur le bridge Kanvas et
prouvé contre le legacy ; les dégradés et les rayons non uniformes restent
refusés et dépendent de KGPU-M2-002. Le PM suit ce ticket car il sépare ce qui
peut déjà franchir la porte de retrait (rrect plein) de ce qui doit attendre
(dégradés / rayons par coin).

## Problem

`GpuRendererLegacyRouteFamily.rounded-rect-gradients` is a partial family
(decision matrix row 3):

- Solid-color uniform-radii `FillRRect` is dispatched at `Surface.kt:150,226-304`
  — **ported** (SDF coverage), proven at 99.84% vs an independent geometric
  reference (`KGPU-M31-005` §3).
- Non-uniform radii are **refused** at `Surface.kt:263-265`.
- Gradient materials (Linear/Radial/Sweep) are **refused** at dispatch.

Legacy retirement for this family requires the solid uniform rrect baseline to
be reviewed and the gradient + non-uniform-radii sub-cases to each carry a
stable refuse diagnostic linked to KGPU-M2-002.

## Scope

Ported sub-cases (route_kind GPUNative):

- Solid-color, uniform-radii rounded-rect fill dispatched on the Kanvas bridge
  with real GPU pixel parity vs an independent CPU reference.

## Non-Goals

- Do not port gradient-filled rounded rects (Linear/Radial/Sweep) here.
- Do not port non-uniform / per-corner radii here.
- Do not add a short-lived substitute for gradients or per-corner radii (per
  AGENTS.md: refused sub-cases stay formal refusals linked to KGPU-M2-002, no
  short-lived substitute).
- Refused sub-cases emit stable `refuse:rrect:unsupported_material:<Gradient>`
  and `refuse:rrect:non_uniform_radii` diagnostics; they must not be silently
  served.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 3)

## Graphite Algorithm References

- n/a — not required for this slice (uniform-radii SDF rrect coverage already
  dispatched; per-corner SDF + gradient sampling algorithm study belongs to
  KGPU-M2-002).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (solid uniform) / "RefuseDiagnostic" (gradient, non-uniform)
    val dumpRefs: List<String>,       // reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md
    val diagnostics: List<String>,    // refuse:rrect:unsupported_material:RadialGradient, refuse:rrect:non_uniform_radii
)
```

## Acceptance Criteria

- [ ] Solid-color uniform-radii rounded-rect fill dispatched on the Kanvas bridge
      with real GPU pixel parity vs an INDEPENDENT CPU reference (similarity %,
      max channel delta, diff artifact) committed under `reports/gpu-renderer/`.
- [ ] Gradient-filled and non-uniform-radii rounded rects emit stable refuse
      diagnostics; a hermetic regression test asserts each diagnostic and links
      KGPU-M2-002.
- [ ] No short-lived gradient / per-corner-radii substitute is introduced.

## Required Evidence

- Solid uniform rrect already satisfied (independent review still required):
  `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
  (RRect solid fill 99.77%, 39908/40000, maxDiff 123, threshold >= 99.0%) and
  `KGPU-M31-005` §3 (99.84% vs independent geometric reference; AA edge pixels
  expected to differ).
- Gradient rrect port + per-corner-radii SDF extension: independent CPU pixel
  parity per gradient type + WGSL gradient-sampling dispatch — **not produced
  yet** (refused; tracked by KGPU-M2-002).
- Hermetic refuse regression tests — **not produced yet**.

## Fallback / Refusal Behavior

- Gradient-filled and non-uniform-radii rounded rects emit stable refuse
  diagnostics; no silent CPU-rendered fallback.
- The `gpu-raster legacy` gate remains visible for the refused sub-cases until
  KGPU-M2-002 lands real port evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.rounded-rect-gradients`
- Expected classification: `ImplementationCandidate` (solid uniform rrect) +
  `RefuseRequired`/`DependencyGated` (gradients, non-uniform radii)
- Claim promotion allowed: no, unless Required Evidence is attached and reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=solid-uniform-rrect -PsceneOutput=build/gpu-renderer-scenes/solid-uniform-rrect.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*RRectGradientRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 3 (partial — port
  solid uniform rrect / refuse gradients + non-uniform radii). Solid sub-case
  parity already proven via KGPU-M32-002 and KGPU-M31-005; kept `proposed`
  pending independent review. Gradient + non-uniform remainder refused and
  dependency-linked to KGPU-M2-002. No new evidence produced here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): added hermetic refuse coverage —
  gradient rrect refuses `unsupported_material:<gradientKind>`, and non-uniform
  radii refuse `non_uniform_radii` (including a test proving non-uniform radii are
  reachable end-to-end via `Canvas.drawRRect`). Tests: `MaterialRefuseTest`,
  `RRectRadiiRefuseTest`. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Gradient/non-uniform
  port remains dependency-gated.
- 2026-06-26 (Phase 2.C, still `proposed`): port-evidence consolidation. The
  **solid uniform rrect** sub-case is proven — bridge↔legacy RRect **99.77%
  (39908/40000, maxDiff 123)** (m32-002) and independent-CPU **99.84%
  (76680/76800)** (M31-005); the sub-1% delta is the expected SDF-vs-analytic AA
  edge. Gradients and **non-uniform radii** are refused. See
  `reports/gpu-renderer/2026-06-26-m32-port-evidence.md` §KGPU-M32-012.
  Documentation-only; no new evidence; independent review still owed.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
