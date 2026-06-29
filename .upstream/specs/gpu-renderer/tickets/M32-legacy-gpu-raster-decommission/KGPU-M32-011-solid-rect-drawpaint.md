---
id: KGPU-M32-011
title: "Legacy decommission: solid-rect-drawpaint port (FillRect / drawPaint rect)"
<<<<<<< HEAD
status: done
=======
status: review
>>>>>>> master
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M1-004]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-011 - Legacy decommission: solid-rect-drawpaint port (FillRect / drawPaint rect)

## PM Note

Le rectangle plein (`drawRect` / `drawPaint` plein) est entièrement porté sur le
bridge Kanvas et prouvé pixel-à-pixel contre le device legacy. Ce ticket lie
cette preuve à la famille `solid-rect-drawpaint` pour autoriser le retrait du
legacy. Le PM le suit car c'est la famille la plus simple et la première à
pouvoir franchir la porte de retrait après revue indépendante.

## Problem

`GpuRendererLegacyRouteFamily.solid-rect-drawpaint` is a **port** family (decision
matrix row 2). `FillRect` is dispatched at `Surface.kt:149,172-224` under the
supported constraints (SolidColor, Identity, WideOpen/DeviceRect, Root,
SrcOver). Pixel parity is already proven, but the family still requires
independent review and explicit retirement-gate binding before legacy removal.

## Scope

- Bind the existing FillRect / solid drawPaint-rect bridge dispatch (route_kind
  GPUNative) to the `solid-rect-drawpaint` family for
  `GpuRendererLegacyRetirementGate` authorization.
- Confirm the draw dispatches on the Kanvas bridge with real GPU pixel parity
  vs an independent CPU reference and vs the legacy `SkWebGpuDevice`.

## Non-Goals

- No stroke (tracked by KGPU-M32-013), no non-SolidColor material, no
  non-SrcOver blend, no complex clip — those belong to their own families.
- Do not add hidden CPU-rendered texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 2)

## Graphite Algorithm References

- n/a — not required for this slice (solid analytic rect coverage already
  dispatched; no Graphite study required).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative"
    val dumpRefs: List<String>,       // reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md
    val diagnostics: List<String>,    // none for the ported solid-rect route
)
```

## Acceptance Criteria

- [ ] Solid rect / drawPaint-rect fill dispatched on the Kanvas bridge with real
      GPU pixel parity vs an INDEPENDENT CPU reference (similarity %, max channel
      delta, diff artifact) committed under `reports/gpu-renderer/`.
- [ ] Family is bound to `GpuRendererLegacyRetirementGate` (authorization handled
      by KGPU-M32-003) with the parity evidence linked.

## Required Evidence

- Already satisfied (independent review still required): bridge↔legacy parity in
  `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
  (Rect solid fill 100.00%, 40000/40000, maxDiff 0) and `KGPU-M31-005` §3, §9
  (100% similarity vs independent geometric reference + 100% vs Skia raster).
- No new evidence is required for retirement; this ticket binds existing proven
  parity to the family and requests independent review.

## Fallback / Refusal Behavior

- Out-of-constraint rects (non-SolidColor material, non-SrcOver blend,
  non-Identity transform, complex clip, non-Root layer) emit their existing
  stable refuse diagnostics and are owned by the corresponding families.
- Silent CPU-rendered fallback is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.solid-rect-drawpaint`
- Expected classification: `ImplementationCandidate` (parity proven; pending
  independent review)
- Claim promotion allowed: only after independent review of the linked parity
  evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=solid-rect-fill -PsceneOutput=build/gpu-renderer-scenes/solid-rect-fill.png
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 2 (`port`). FillRect
  parity already proven via KGPU-M32-002 (`c5b7387`) and KGPU-M31-005; kept
  `proposed` pending independent review. No new evidence produced here.
- 2026-06-26 (Phase 2.C, still `proposed`): port-evidence consolidation. FillRect
  is fully proven — bridge↔legacy Rect **100% (40000/40000, maxDiff 0)** (m32-002,
  re-confirmed in this Phase 2.C run) plus independent-CPU and Skia-raster
  **100%** (M31-005). See
  `reports/gpu-renderer/2026-06-26-m32-port-evidence.md` §KGPU-M32-011.
  Documentation-only; no new evidence; independent review still owed.


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
<<<<<<< HEAD
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
