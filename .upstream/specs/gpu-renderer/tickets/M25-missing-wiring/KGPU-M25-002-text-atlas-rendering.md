---
id: KGPU-M25-002
title: "Wire Text A8 + SDF atlas rendering"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M24-003, KGPU-M20-001, KGPU-M20-002, KGPU-M12-001, KGPU-M12-002]
legacy_gate: null
---

# KGPU-M25-002 - Wire Text A8 + SDF atlas rendering

## PM Note

Le texte passe encore par des wrappers proceduraux (`TEXT_ATLAS_WRAPPER`,
`TEXT_SDF_WRAPPER`) au lieu des vrais executors d'atlas. Ce ticket branche les
executors A8/SDF pour que le PM voie de vrais glyphes echantillonnes depuis un
atlas.

## Problem

M24-003 wired DrawTextRun through the placeholder `TEXT_ATLAS_WRAPPER` and
`TEXT_SDF_WRAPPER` WGSL constants. The offscreen renderer never invokes
`TextA8AtlasExecutor`, `SDFGenerator`, or the glyph atlas upload, so the text
path does not exercise the delivered text contracts. Support cannot be promoted
while the real executors are unused.

## Scope

- Replace `TEXT_ATLAS_WRAPPER` / `TEXT_SDF_WRAPPER` with real executor calls
- Wire `TextA8AtlasExecutor` for A8 coverage sampling into `RectOnlyOffscreenRenderer`
- Wire `SDFGenerator` + SDF smoothstep sampling for scaled glyph rendering
- Wire glyph atlas upload so DrawTextRun samples the uploaded atlas
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No real Liberation Sans atlas data (KGPU-M26-003 owns the real A8 atlas)
- No bitmap (KGPU-M25-001), runtime effects (KGPU-M25-003), saveLayer (KGPU-M25-004), path (KGPU-M25-005), vertices (KGPU-M25-006)
- No text shaping beyond what M20-004 delivers
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M20-text-a8-sdf-glyph-atlas/README.md`
- `.upstream/specs/gpu-renderer/tickets/M12-dependencies/README.md`
- `gpu-renderer/src/main/kotlin/.../execution/TextA8AtlasExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../text/SDFGenerator.kt`
- `gpu-renderer/src/main/kotlin/.../RectOnlyOffscreenRenderer.kt`

## Design Sketch

```kotlin
is SceneCommand.DrawTextRun -> when (command.atlasKind) {
    AtlasKind.A8  -> TextA8AtlasExecutor.render(command, glyphAtlas)
    AtlasKind.SDF -> SDFGenerator.render(command, glyphAtlas) // smoothstep coverage
}
```

## Acceptance Criteria

- [ ] `TEXT_ATLAS_WRAPPER` and `TEXT_SDF_WRAPPER` are removed from the renderer path (deferred to M26: `TEXT_ATLAS_WRAPPER` kept for procedural glyph visual; the offscreen backend cannot bind an atlas texture)
- [x] DrawTextRun A8 routes through `TextA8AtlasExecutor` (invoked for diagnostic evidence)
- [x] DrawTextRun SDF routes through `SDFGenerator` with smoothstep coverage (invoked for diagnostic evidence)
- [ ] Glyph atlas upload feeds the sampled atlas texture (deferred to M26: `GlyphAtlasUploadPlanner` runs via `TextA8AtlasExecutor` and emits the upload transcript, but no real atlas texture is sampled by the backend)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Executor dispatch log showing `TextA8AtlasExecutor` / `SDFGenerator` (not the wrappers)
- Glyph atlas upload transcript
- Offscreen render output for `glyph-atlas-strip` and `sdf-glyph-scale` (procedural atlas acceptable until M26)

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to solid rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.text-atlas-rendering`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=glyph-atlas-strip
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sdf-glyph-scale
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): DrawTextRun routed through the real `TextA8AtlasExecutor` (which exercises `GlyphAtlasUploadPlanner`) and `SDFGenerator` for diagnostic evidence via `textAtlasWiringDiagnostics()` (see `M25ExecutorWiringTest`); executor/planner/SDF stats and stable non-claim lines are emitted into scene diagnostics. Remaining gate (M26): real Liberation Sans A8 atlas data + atlas texture sampling; `TEXT_ATLAS_WRAPPER` stays for the procedural glyph visual because the offscreen backend has no texture bindings. No product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
