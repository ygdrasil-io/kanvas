---
id: KGPU-M32-015
title: "Legacy decommission: path-fill-stroke port (path fill) / refuse (path stroke)"
status: proposed
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M11-007]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-015 - Legacy decommission: path-fill-stroke port (path fill) / refuse (path stroke)

## PM Note

Le remplissage de chemin (path fill) est porté sur le bridge Kanvas via
stencil-cover et prouvé pixel-à-pixel ; le tracé de chemin (path stroke) reste
refusé et dépend de KGPU-M11-007. Le PM suit ce ticket car il sépare le fill déjà
prouvé du stroke encore non supporté.

## Problem

`GpuRendererLegacyRouteFamily.path-fill-stroke` is a partial family (decision
matrix row 6):

- `FillPath` fill is dispatched at `Surface.kt:151,324-430` via stencil-cover —
  **ported**, proven 100% vs an independent winding reference for triangle and
  star (`KGPU-M31-005` §4-5).
- No path-stroke dispatch exists — **refused**.

Legacy retirement requires the path-fill baseline to be reviewed and the
path-stroke sub-case to carry a stable refuse diagnostic linked to KGPU-M11-007.

## Scope

Ported sub-cases (route_kind GPUNative):

- Path fill (non-zero winding) dispatched on the Kanvas bridge via stencil-cover
  with real GPU pixel parity vs an independent CPU reference.

## Non-Goals

- Do not port path stroke (stroke expansion / analytic-SDF stroke) here.
- Do not add a short-lived path-stroke substitute (per AGENTS.md: refused stroke
  stays a formal refusal linked to KGPU-M11-007).
- Do not add hidden CPU-rendered path texture compatibility.
- Path stroke emits a stable `refuse:path:unsupported_stroke` diagnostic; it must
  not be silently served.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 6)

## Graphite Algorithm References

- n/a — not required for this slice (path fill uses existing stencil-cover
  dispatch; stroke-expansion / SDF-stroke algorithm study belongs to
  KGPU-M11-007).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (fill) / "RefuseDiagnostic" (stroke)
    val dumpRefs: List<String>,       // reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md
    val diagnostics: List<String>,    // refuse:path:unsupported_stroke
)
```

## Acceptance Criteria

- [ ] Path fill dispatched on the Kanvas bridge via stencil-cover with real GPU
      pixel parity vs an INDEPENDENT CPU reference (similarity %, max channel
      delta, diff artifact) committed under `reports/gpu-renderer/`.
- [ ] Path stroke emits a stable `refuse:path:unsupported_stroke` diagnostic; a
      hermetic regression test asserts it and links KGPU-M11-007.
- [ ] No short-lived path-stroke substitute is introduced (per AGENTS.md).

## Required Evidence

- Path fill already satisfied (independent review still required):
  `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
  (Path solid triangle fill 100.00%, 40000/40000, maxDiff 0; non-AA) and
  `KGPU-M31-005` §4-5 (triangle 100%, star 100% vs non-zero winding reference).
- Path-stroke port: independent CPU pixel parity + WGSL stroke dispatch
  (stencil-cover with stroke expansion or analytic SDF) — **not produced yet**
  (refused; tracked by KGPU-M11-007).
- Hermetic refuse regression test — **not produced yet**.

## Fallback / Refusal Behavior

- Path stroke emits a stable `refuse:path:unsupported_stroke` diagnostic; no
  silent CPU-rendered fallback.
- The `gpu-raster legacy` gate remains visible for path stroke until
  KGPU-M11-007 lands real port evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.path-fill-stroke`
- Expected classification: `ImplementationCandidate` (path fill) +
  `RefuseRequired` (path stroke)
- Claim promotion allowed: no, unless Required Evidence is attached and reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=path-fill-triangle -PsceneOutput=build/gpu-renderer-scenes/path-fill-triangle.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*PathStrokeRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 6 (partial — port
  path fill / refuse path stroke). Path-fill parity already proven via
  KGPU-M32-002 and KGPU-M31-005; kept `proposed` pending independent review.
  Path-stroke remainder refused and dependency-linked to KGPU-M11-007. No new
  evidence produced here.
- `proposed` (2026-06-26, Phase 2.B(i)): Path-stroke sub-case now REFUSES instead
  of silently filling. `toKanvasPaint()` reads `SkPaint.style`; stroke /
  stroke-and-fill path draws carry `stroke=true` on
  `NormalizedDrawCommand.FillPath` and are refused in `Surface.dispatchFillPath`
  with the stable reason `unsupported_stroke`, surfaced via
  `SurfaceRenderResult.diagnostics` → `SkiaKanvasSurface.emitRefusedDiagnostics`
  (emitted form `refuse:<command>:unsupported_stroke`). Path *fill* dispatch is
  unchanged (default `stroke=false`); its prior parity evidence still stands. No
  stroke renderer added; real path-stroke port stays KGPU-M11-007. Hermetic +
  GPU-gated regression tests pass. Evidence:
  `reports/gpu-renderer/2026-06-26-m32-013-stroke-refusal.md`. NOTE: implemented
  reason token is `unsupported_stroke`, not the pre-implementation literal
  `path:unsupported_stroke` above. Kept `proposed` — independent review owed.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
