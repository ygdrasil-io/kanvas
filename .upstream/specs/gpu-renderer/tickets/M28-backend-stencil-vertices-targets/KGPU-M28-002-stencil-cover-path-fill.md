---
id: KGPU-M28-002
title: "Wire stencil-cover real GPU rendering for path fill"
status: proposed
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M28-001]
legacy_gate: null
---

# KGPU-M28-002 - Wire stencil-cover real GPU rendering for path fill

## PM Note

Les remplissages de path dessinent encore le rectangle englobant au lieu de la
vraie geometrie parce que le backend n'a ni stencil ni vertex buffers. Avec le
stencil disponible (M28-001), ce ticket branche le vrai stencil-cover pour que
le PM voie les formes concaves et convexes remplies correctement.

## Problem

KGPU-M25-005 wired the `PathTessellator`, `StencilCoverExecutor`, and
`ConvexFanExecutor` into the offscreen renderer, but the visual still draws the
bounding rectangle because the offscreen backend lacked stencil attachments.
Now that M28-001 delivers the depth-stencil attachment and stencil state
management, the path fill path must be upgraded from bounding-rect fill to real
stencil-cover rendering using the tessellator output.

## Scope

- Replace bounding-rect fill in `RectOnlyOffscreenRenderer` path-fill path with real stencil-cover
- Route tessellated path output through stencil write pass (draw triangles into stencil buffer)
- Route stencil-tested cover pass (draw fullscreen quad with stencil test enabled)
- Route convex paths through indexed triangle draw using convex-fan mesh output
- Regenerate path-fill-stencil and convex-fan-mesh scene PNGs with real geometry output
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No vertex/index buffer implementation (assumed available via M28-003 for convex fan)
- No stroke or dash geometry (M16 owns stroke)
- No secondary render targets (KGPU-M28-005)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M15-path-fill-stencil-cover/README.md`
- `.upstream/specs/gpu-renderer/tickets/M25-missing-wiring/KGPU-M25-005-pathfill-stencil-cover.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GpuNativeOffscreenRenderer.kt`

## Design Sketch

```kotlin
when (command) {
    is SceneCommand.PathFill -> {
        val tess = PathTessellator.tessellate(command.path)
        if (tess.isConvex) {
            // Upload tess fan vertices to GPU buffer, draw indexed triangle fan
            val vertexBuffer = createVertexBuffer(tess.vertices)
            val indexBuffer = createIndexBuffer(tess.indices)
            drawIndexed(vertexBuffer, indexBuffer, command.paint)
        } else {
            // Stencil write pass: draw tess triangles into stencil buffer
            beginStencilPass(clear = true)
            setStencilWriteMode()
            drawFullscreenStencilPass(/* tess triangles */)
            endStencilPass()

            // Cover resolve pass: draw fullscreen quad with stencil test
            setStencilTestMode()
            drawFullscreenUniformPass(/* cover color */)
        }
    }
}
```

## Acceptance Criteria

- [ ] Concave/complex path fills no longer draw the bounding rectangle
- [ ] Stencil write pass renders tessellated triangles into the stencil buffer
- [ ] Cover resolve pass draws the fullscreen quad with stencil test enabled
- [ ] Convex paths render through indexed triangle draw (convex fan mesh)
- [ ] `path-fill-stencil` scene PNG shows real stencil-cover output
- [ ] `convex-fan-mesh` scene PNG shows real convex mesh output
- [ ] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Stencil write pass dispatch log (triangle count, stencil state)
- Cover resolve pass dispatch log (stencil test mode, blend state)
- Offscreen render output showing a concave path that is not a rectangle
- Offscreen render output showing a convex path with real triangle mesh
- Coverage diff against the CPU reference for a representative path scene

## Fallback / Refusal Behavior

If stencil attachment creation failed (M28-001), the renderer emits a
`stencil-unavailable` diagnostic and keeps the bounding-rect visual as
diagnostic output. If the GPU is unavailable, emit `gpu-unavailable`. No silent
fallback to bounding-rect fills as support claims.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.stencil-cover-path-fill`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-stencil
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=convex-fan-mesh
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
