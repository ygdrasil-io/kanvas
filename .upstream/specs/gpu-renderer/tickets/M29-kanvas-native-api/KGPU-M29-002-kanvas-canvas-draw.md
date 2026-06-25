---
id: KGPU-M29-002
title: "KanvasCanvas — drawRect/drawRRect/drawPath/drawImage/drawText"
status: proposed
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M29-001]
legacy_gate: null
---

# KGPU-M29-002 - KanvasCanvas — drawRect/drawRRect/drawPath/drawImage/drawText

## PM Note

`KanvasCanvas` est l'equivalent natif Kanvas du `SkCanvas`. Il recoit les commandes
de dessin fondamentales (rect, rrect, path, image, texte) et les transforme en
operations GPU sans passer par Skia. Ce ticket donne au PM la surface de dessin
complete pour l'API native.

## Problem

`KanvasSurface` exists as a target but has no drawing surface. Calling code has
no way to issue draw commands through the native Kanvas API. `KanvasCanvas`
bridges the gap by accepting draw operations and routing them to internal GPU
command recording.

## Scope

- Define `KanvasCanvas` as the draw-command target on a `KanvasSurface`
- Implement `drawRect(rect, paint)`
- Implement `drawRRect(rrect, paint)`
- Implement `drawPath(path, paint)`
- Implement `drawImage(image, x, y, paint?)`
- Implement `drawText(textBlob, x, y, paint)`
- Each method delegates to internal GPU command recorder

## Non-Goals

- No paint implementation (KGPU-M29-003)
- No path implementation (KGPU-M29-004)
- No image decoding (KGPU-M29-006)
- No text blob implementation (KGPU-M29-007)
- No GPU submission (KGPU-M29-008)
- No save/restore, clip, or transform (future milestone)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/01-normalized-draw-commands.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/tickets/M24-gpu-native-rendering/README.md`

## Design Sketch

```kotlin
class KanvasCanvas(private val surface: KanvasSurface) {
    fun drawRect(rect: Rect, paint: KanvasPaint)
    fun drawRRect(rrect: RRect, paint: KanvasPaint)
    fun drawPath(path: KanvasPath, paint: KanvasPaint)
    fun drawImage(image: KanvasImage, x: Float, y: Float, paint: KanvasPaint? = null)
    fun drawText(textBlob: KanvasTextBlob, x: Float, y: Float, paint: KanvasPaint)
}
```

## Acceptance Criteria

- [ ] `KanvasCanvas` compiles and accepts a `KanvasSurface`
- [ ] All five draw methods accept their typed arguments
- [ ] Each method produces a valid GPU draw command record (dumped as JSON)
- [ ] Unsupported argument combinations emit stable diagnostics

## Required Evidence

- `KanvasCanvas.kt` committed with all draw methods
- Command dump transcript for: rect, rrect, path, image, text draws
- Diagnostic output for unsupported argument combinations

## Fallback / Refusal Behavior

Unknown paint, path, image, or text types emit typed `unsupported-draw-argument`
diagnostics. No silent discard and no CPU fallback rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-canvas-draw`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-api:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas-api`
