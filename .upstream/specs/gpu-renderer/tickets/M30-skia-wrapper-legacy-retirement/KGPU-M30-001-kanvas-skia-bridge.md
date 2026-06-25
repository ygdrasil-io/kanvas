---
id: KGPU-M30-001
title: "KanvasSkiaBridge — SkCanvas to KanvasCanvas command translation"
status: review
milestone: M30
priority: P0
owner_area: kanvas-skia-bridge
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M29-008]
legacy_gate: null
---

# KGPU-M30-001 - KanvasSkiaBridge — SkCanvas to KanvasCanvas command translation

## PM Note

`KanvasSkiaBridge` traduit les appels `SkCanvas` existants en commandes
`KanvasCanvas` natives, permettant au code Skia existant de fonctionner sans
modification tout en utilisant le pipeline GPU Kanvas en dessous. Ce ticket est
la piece centrale de la migration sans rupture.

## Problem

Existing render paths use `SkCanvas` (wrapped by `:kanvas-skia`) for all drawing
operations. To retire the `gpu-raster` legacy path and route through native
Kanvas, there must be a bridge that translates `SkCanvas` draw calls into
`KanvasCanvas` commands without rewriting all calling code.

## Scope

- Define `KanvasSkiaBridge` class that wraps a `KanvasCanvas` and exposes a `SkCanvas`-compatible interface
- Implement draw command translation for: drawRect, drawRRect, drawPath, drawImage, drawText
- Implement paint translation: SkPaint → KanvasPaint
- Implement path translation: SkPath → KanvasPath
- Implement image translation: SkImage → KanvasImage
- Implement text blob translation: SkTextBlob → KanvasTextBlob
- Emit diagnostics for unsupported SkCanvas features

## Non-Goals

- No Skia API completeness (only drawer methods needed for existing routes)
- No SkShader → KanvasShader translation for runtime effects
- No SkSurface swapchain or presentation integration
- No save/restore, clip, or transform bridge (future if needed)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/tickets/M10-legacy-gpu-raster-migration/README.md`
- `.upstream/specs/gpu-renderer/tickets/M29-kanvas-native-api/README.md`

## Design Sketch

```kotlin
class KanvasSkiaBridge(private val kanvasCanvas: KanvasCanvas) {
    fun drawRect(rect: SkRect, paint: SkPaint) {
        kanvasCanvas.drawRect(rect.toKanvasRect(), paint.toKanvasPaint())
    }
    fun drawPath(path: SkPath, paint: SkPaint) {
        kanvasCanvas.drawPath(path.toKanvasPath(), paint.toKanvasPaint())
    }
    // ... other translation methods
}
```

## Acceptance Criteria

- [ ] `KanvasSkiaBridge` wraps a `KanvasCanvas` and accepts all five draw families
- [ ] Skia→Kanvas paint translation produces correct GPU material keys
- [ ] Skia→Kanvas path translation preserves all verbs and fill type
- [ ] Skia→Kanvas image translation preserves texture reference and dimensions
- [ ] Skia→Kanvas text translation preserves glyph positions
- [ ] Unsupported SkCanvas features emit `unsupported-skia-bridge-feature` diagnostics

## Required Evidence

- `KanvasSkiaBridge.kt` committed
- Translation transcript for each draw family: rect, rrect, path, image, text
- Paint translation dump (SkPaint → KanvasPaint → GPU material key)
- Diagnostic output for unsupported SkCanvas features

## Fallback / Refusal Behavior

Unsupported SkCanvas draw calls emit `unsupported-skia-bridge-draw` diagnostics
and are not routed. No silent fallback to legacy `gpu-raster` behind the bridge.
No partial translation that produces incorrect output.

## Dashboard Impact

- Expected row: `gpu-renderer.m30.kanvas-skia-bridge`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Implemented. Added SkRRect, SkTextBlob translation; created KanvasSkiaBridge class; extended SkiaKanvasSurface with drawRRect/drawTextBlob; added diagnostic helpers. 26 tests pass. Existing code was extended, not recreated — gaps from ticket AC are filled. Evidence at `reports/gpu-renderer/2026-06-25-M30-001-evidence.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M30`
- `area:kanvas-skia-bridge`
