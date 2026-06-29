---
id: KGPU-M32-013
title: "Legacy decommission: rect-rrect-stroke formal refusal"
status: done
milestone: M32
priority: P1
owner_area: legacy-cleanup
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M32-001, KGPU-M3-003]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-013 - Legacy decommission: rect-rrect-stroke formal refusal

## PM Note

Le tracé (stroke) de rectangles et de rectangles arrondis n'a aucun chemin GPU
sur le bridge Kanvas : ce ticket le refuse formellement, avec un diagnostic
stable, et renvoie le futur portage à KGPU-M3-003. Le PM suit ce ticket pour
savoir que le stroke n'est pas supporté et qu'aucun substitut temporaire ne sera
ajouté.

## Problem

`GpuRendererLegacyRouteFamily.rect-rrect-stroke` is a **full refuse** family
(decision matrix row 4). No stroke dispatch path exists: `FillRect`/`FillRRect`
cover fill only, there is no `StrokeRect`/`StrokeRRect` command type in the
`NormalizedDrawCommand` sealed interface, and no `dispatchStrokeX` function in
`Surface.kt`. Legacy retirement for this family requires a stable refuse
diagnostic rather than a silent drop, plus a dependency link to the future
stroke port (KGPU-M3-003).

## Scope

- Emit a stable `refuse:stroke:unsupported_stroke_command` diagnostic on the
  bridge when a rect/rrect stroke draw is attempted (so the production-default
  path cannot silently drop or mis-render it).
- Add a hermetic regression test that asserts the diagnostic and links
  KGPU-M3-003 as the future port owner.

## Non-Goals

- Do NOT add a short-lived stroke substitute (per AGENTS.md: refused families
  stay formal refusals linked to their dependency ticket; no short-lived
  substitute).
- Do not implement stroke geometry, stroke expansion, or analytic-SDF stroke
  here — those belong to KGPU-M3-003.
- Do not add hidden CPU-rendered stroke texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 4)

## Graphite Algorithm References

- n/a — not required for this slice (refuse-only; stroke algorithm study belongs
  to KGPU-M3-003).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "RefuseDiagnostic"
    val dumpRefs: List<String>,       // hermetic refuse-test log
    val diagnostics: List<String>,    // refuse:stroke:unsupported_stroke_command
)
```

## Acceptance Criteria

- [ ] Rect/rrect stroke draws attempted via the bridge emit a stable
      `refuse:stroke:unsupported_stroke_command` diagnostic (no silent drop, no
      mis-render as fill).
- [ ] A hermetic regression test asserts the diagnostic and references
      KGPU-M3-003 as the future port ticket.
- [ ] No short-lived stroke substitute is introduced (per AGENTS.md).

## Required Evidence

- Hermetic refuse regression test capturing the stable stroke refuse diagnostic
  — **not produced yet** (`proposed`).
- Dependency link to KGPU-M3-003 for the future stroke port.

## Fallback / Refusal Behavior

- Stroke routes emit a stable `refuse:stroke:unsupported_stroke_command`
  diagnostic.
- Silent fallback to CPU-rendered stroke compatibility is not allowed.
- The `gpu-raster legacy` gate remains visible for this family until KGPU-M3-003
  lands a real stroke port with parity evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.rect-rrect-stroke`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no — refuse-only; support is claimed only by a future
  KGPU-M3-003 port.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test --tests "*RectRRectStrokeRefuse*"
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 4 (`refuse`). No
  stroke dispatch path exists; formal refusal + hermetic test required, future
  port deferred to KGPU-M3-003. Not dependency/codec-gated — refusal is because
  stroke is unimplemented, so `claim_impact: RefuseRequired`. No evidence yet.
- `proposed` (2026-06-26, Phase 2.B(i)): Silent stroke-fill bug fixed. The bridge
  `toKanvasPaint()` now reads `SkPaint.style`; stroke / stroke-and-fill draws are
  carried as `stroke=true` on `NormalizedDrawCommand.FillRect/FillRRect` and
  REFUSED in `Surface.dispatchFillRect/RRect` with the stable reason
  `unsupported_stroke`, surfaced via `SurfaceRenderResult.diagnostics` →
  `SkiaKanvasSurface.emitRefusedDiagnostics` (emitted form
  `refuse:<command>:unsupported_stroke`). No stroke renderer was added; real port
  stays KGPU-M3-003. Hermetic + GPU-gated regression tests pass. Evidence:
  `reports/gpu-renderer/2026-06-26-m32-013-stroke-refusal.md`. NOTE: implemented
  reason token is `unsupported_stroke` (per task instruction + existing
  `unsupported_material`/`unsupported_blend` pattern), not the pre-implementation
  literal `unsupported_stroke_command` above. Kept `proposed` — independent review
  owed.


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
