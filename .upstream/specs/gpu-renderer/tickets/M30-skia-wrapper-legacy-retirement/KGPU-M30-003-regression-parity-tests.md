---
id: KGPU-M30-003
title: "Regression tests — Skia GM parity via Kanvas bridge"
status: review
milestone: M30
priority: P0
owner_area: kanvas-skia-bridge
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M30-002]
legacy_gate: null
---

# KGPU-M30-003 - Regression tests — Skia GM parity via Kanvas bridge

## PM Note

Ce ticket valide que le pont Kanvas produit des resultats identiques au pipeline
Skia natif pour toutes les familles de dessin supportees. Le PM verra une matrice
de parite complete (rect, rrect, path, image, texte) avec des preuves de rendu
comparatif.

## Problem

The Kanvas bridge (M30-002) routes SkSurface through KanvasSurface, but there is
no systematic regression evidence proving visual parity with the original Skia
rendering path. Without parity evidence, the bridge cannot be trusted for
production activation (M31).

## Scope

- Build a regression test suite comparing Skia-native vs Kanvas-bridge output
- Cover all five draw families: drawRect, drawRRect, drawPath, drawImage, drawText
- Test gradient fills (linear, radial, sweep)
- Test blend modes (SrcOver, Src, Dst, Clear, etc.)
- Test stroke styles (width, cap, join)
- Produce pixel-diff reports for any discrepancies
- Document acceptable tolerance thresholds

## Non-Goals

- No performance benchmarks (M23/M27 own performance gates)
- No new draw families or features
- No GM test framework integration beyond Kanvas parity
- No text layout or shaping parity testing

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`

## Design Sketch

```kotlin
class KanvasBridgeParityTest {
    @Test fun `rect fill parity`() {
        val skiaPixels = renderWithSkia { canvas -> canvas.drawRect(...) }
        val kanvasPixels = renderWithKanvasBridge { canvas -> canvas.drawRect(...) }
        assertPixelIdentical(skiaPixels, kanvasPixels)
    }
}
```

## Acceptance Criteria

- [ ] All five draw families have parity tests
- [ ] Gradient fills (linear, radial, sweep) have parity tests
- [ ] Blend mode combinations have parity tests
- [ ] Stroke variations have parity tests
- [ ] Pixel-diff report produced and committed
- [ ] No unexplained pixel differences exceeding tolerance threshold

## Required Evidence

- Parity test source files committed
- Pixel-diff report (pass/fail per draw family)
- Gradient parity render snapshots
- Blend mode parity render snapshots
- Stroke parity render snapshots
- Tolerance threshold documentation

## Fallback / Refusal Behavior

Tests that fail parity emit `kanvas-parity-mismatch` diagnostics with a
per-pixel diff report. Failing tests block M31 activation. No silent
acceptance of incorrect output.

## Dashboard Impact

- Expected row: `gpu-renderer.m30.regression-parity`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :skia-integration-tests:test
rtk ./gradlew --no-daemon :integration-tests:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Implemented. Parity test suite covers all five draw families (rect, rrect, path, text, image via existing tests), blend modes (parameterized over 17 modes), multiple draws, and stroke variations. Pixel-diff GPU comparison deferred to M31-003 evidence bundle. Evidence at `reports/gpu-renderer/2026-06-25-M30-003-evidence.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M30`
- `area:kanvas-skia-bridge`
