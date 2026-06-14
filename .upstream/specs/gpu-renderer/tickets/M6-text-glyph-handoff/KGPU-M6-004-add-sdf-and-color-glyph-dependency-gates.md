---
id: KGPU-M6-004
title: "Add SDF and color glyph dependency gates"
status: proposed
milestone: M6
priority: P1
owner_area: text-validation
claim_impact: DependencyGated
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M6-001]
legacy_gate: "scaledemoji_rendering,coloremoji_blendmodes"
---

# KGPU-M6-004 - Add SDF and color glyph dependency gates

## PM Note

Ce ticket montre clairement que SDF, emoji et glyphes couleur restent gated.

## Problem

SDF and color glyph routes depend on pure Kotlin glyph/color-font deliveries and
must not be claimed by A8 atlas evidence.

## Scope

- Add dependency-gated refusals for SDF, bitmap, SVG, color glyph, emoji, and
  LCD routes.
- Add PM matrix entries for unsupported text representations.

## Non-Goals

- Do not implement those routes.
- Do not retire legacy text gates.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`

## Graphite Algorithm References

- [`GFX-SDF-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-sdf-text-step) - source [SDFTextRenderStep.cpp:95](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/SDFTextRenderStep.cpp:95); Reference SDF atlas sampling and gamma/pixel-geometry parameters as future-gated support.
- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Study color-glyph and primitive-color text variants.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Use glyph mask format conversion as dependency-gate vocabulary.
- [`GFX-TEXT-ATLAS-CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source [TextAtlasManager.cpp:47](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:47); Keep atlas size and format support visible in SDF/color refusals.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class TextRepresentationGate(val representation: String, val diagnostic: String)
```

## Acceptance Criteria

- [ ] Unsupported representations have stable diagnostics.
- [ ] A8 support cannot imply SDF/color/emoji support.
- [ ] Legacy gates remain visible.

## Required Evidence

- Text route matrix and refusal dumps.

## Fallback / Refusal Behavior

Unsupported representations refuse with `dependency.text.*` or
`unsupported.text.*` diagnostics.

## Dashboard Impact

- Expected row: `gpu-renderer.text-representation-gates`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Dependency gates only.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text-validation`
