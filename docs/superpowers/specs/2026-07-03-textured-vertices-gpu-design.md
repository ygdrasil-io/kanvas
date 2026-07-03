# Textured Vertices GPU Pipeline Design

Date: 2026-07-03
Status: Draft

## Scope

Implement the GPU pipeline for textured vertices (`drawVertices` with `texCoords`)
to remove the `dependency-gated` status on mesh GMs. This covers single-texture,
color-filtered, and dual-texture blended vertices dispatch.

## WGSL Shaders

Three new snippet files in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`:

### 1. TexturedVerticesSnippet.kt — single texture

```
Vertex inputs: @location(0) position: vec2<f32>, @location(1) uv: vec2<f32>
Vertex outputs: @builtin(position) position, @location(0) uv
Fragment: sample texture at uv, apply paint alpha, blend
Uniforms: transform matrix (optional), paint alpha
Textures: @group(1) @binding(0) tex: texture_2d<f32>, @binding(1) samp: sampler
```

Covers: `mesh_with_image`, `mesh_with_paint_color`, `mesh_with_paint_image`.

### 2. TexturedVerticesColorFilterSnippet.kt — texture + color filter

```
Same as #1 plus:
Uniform block: colorFilterMatrix (4 floats for matrix row, 16 total)
Fragment: sample texture → apply color matrix → output
```

Covers: `mesh_with_effects` inverse color filter path.

### 3. TexturedVerticesDualBlendSnippet.kt — dual texture + blender

```
Vertex inputs: @location(0) position, @location(1) uv1, @location(2) uv2
Textures: @group(1) @binding(0) tex1, @binding(1) samp1, @binding(2) tex2, @binding(3) samp2
Fragment: sample tex1(uv1) → sample tex2(uv2) → blend → output
Blend mode: passed via uniform (ordinal value), applied as switch in WGSL
```

Covers: `mesh_with_effects` dual texture + DstOver blend.

## GPU Dispatch

New file: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt`

### dispatchTexturedVertices()

```
Inputs:
  positions: FloatArray     — flat [x0,y0, x1,y1, ...]
  uvs: FloatArray           — flat [u0,v0, u1,v1, ...]
  uvs2: FloatArray?         — optional second UV channel
  indices: IntArray?        — optional index buffer
  paint: Paint              — color, alpha, blendMode, colorFilter, blender
  textureBytes: ByteArray   — RGBA pixel data for texture
  textureWidth, textureHeight: Int
```

### Routing logic

```
if paint.colorFilter != null && uvs2 == null:
  → ColorFilterSnippet, upload colorFilterMatrix uniform
else if uvs2 != null:
  → DualBlendSnippet, upload both textures + blendMode uniform
else:
  → TexturedVerticesSnippet, single texture
```

### Pipeline upload pattern

Follows existing `GPUBackendRenderRecorder` patterns:
1. Create vertex buffers (positions, uv, uv2)
2. Create index buffer (if indices present)
3. Upload texture as `texture_2d<f32>` rgba8unorm
4. Upload uniform buffer (alpha, blendMode, colorFilterMatrix, targetSize)
5. Dispatch `drawIndexed` or `draw` with scissor rect

## Integration

Modify `GPURenderer.kt:604-658` — replace the current degrade block:

```
// Before:
if (verts.texCoords != null) {
    diagnostics.degrade("unimplemented:drawVertices:textured:...")
}

// After:
if (verts.texCoords != null) {
    dispatchTexturedVertices(positions, uvs, indices, paint, ...)
    sceneHasContent = true
    return
}
```

Same replacement in the `DisplayOp.DrawMesh` handler for mesh with program
but no GPU runtime-effect support (uses paint-level colorFilter/blender fallback).

## Non-goals

- WGSL-level runtime-effect children (`myShader.eval()`) — remains dependency-gated
  on the full RuntimeEffect GPU executor
- Mesh-specific uniforms (uniform block from MeshProgram) — use paint uniforms only
- Animated texture updates — textures are static for GM renders
- GPU buffer zero-init (MeshZeroInitGm) — stays STUB

## GMs unblocked

| GM | Before | After |
|---|---|---|
| mesh_with_image | ⚠ degrade CPU | ✓ GPU textured |
| mesh_with_paint_color | ⚠ degrade CPU | ✓ GPU textured |
| mesh_with_paint_image | ⚠ degrade CPU | ✓ GPU textured |
| mesh_with_effects | ⚠ degrade CPU | ✓ GPU dual-texture + color filter |
| mesh_zero_init | ✗ STUB | ✗ STUB (unchanged) |

## Implementation order

1. Create `TexturedVerticesSnippet.kt` — single texture WGSL
2. Create `TexturedVerticesColorFilterSnippet.kt` — texture + color filter WGSL
3. Create `TexturedVerticesDualBlendSnippet.kt` — dual texture + blend WGSL
4. Create `GPUDispatchVertices.kt` — dispatch logic + routing
5. Update `GPURenderer.kt` — replace degrade with dispatch calls
6. Verify compilation + existing GM tests
