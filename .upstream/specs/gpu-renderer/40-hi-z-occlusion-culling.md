# Hi-Z Occlusion Culling

Status: Draft
Date: 2026-06-28

## Purpose

Define the Hi-Z (hierarchical Z-buffer) occlusion culling architecture for the
GPU-first renderer. A GPU compute pass builds a depth pyramid from the previous
frame's depth buffer or from an early Z-prepass. Subsequent draw analysis uses
the pyramid to conservatively cull draw invocations whose bounds are fully
occluded.

This spec extends the conservative `GPUOcclusionTracker` defined in
`02-gpu-recording-task-graph.md` with a GPU-computed depth pyramid and
per-draw occlusion queries.

This is a target architecture spec, not an implementation slice.

## Source Specs

This spec depends on:
- `00-architecture-kernel.md` for module and naming policy;
- `02-gpu-recording-task-graph.md` for `GPUOcclusionTracker` and draw analysis;
- `04-pipeline-key-cache-resources.md` for compute pipeline and buffer resources;
- `12-blend-color-target-state.md` for depth/stencil target state;
- `38-tile-deferred-rendering.md` for per-tile depth pyramid interaction.

## Contracts

| Contract | Purpose |
|---|---|
| `GPUHiZPyramid` | Hierarchical depth pyramid: mip chain of depth buffers, each level is the max depth of the corresponding 2x2 block from the level below. |
| `GPUHiZPyramidBuildPlan` | Compute pass that generates the depth pyramid from the current depth buffer or Z-prepass output. |
| `GPUHiZOcclusionTest` | Per-draw occlusion query: project draw bounds to screen, sample highest pyramid level where the bounds occupy at most K texels, compare conservative min depth against pyramid max depth. |
| `GPUHiZOcclusionResult` | Per-draw result: `Visible`, `Occluded`, or `Uncertain` (when bounds projection crosses pyramid edges or depth comparison is inconclusive). |
| `GPUHiZBudgetPolicy` | Memory budget for depth pyramid buffers, compute dispatch budget, and max pyramid level count. |
| `GPUHiZDiagnostic` | Reason codes for pyramid build failure, insufficient depth precision, unsupported depth format, or occlusion test timeout. |

## Depth Pyramid Construction

### Pyramid Build

```
level 0 = depth buffer from Z-prepass or previous frame
level 1 = reduce 2x2 blocks of level 0: max(depth[0..3])
level 2 = reduce 2x2 blocks of level 1: max(depth[0..3])
...
level N = 1x1 texel (max depth of entire target)
```

Reduction uses a compute shader with `workgroupSize(8, 8)` for level 0->1,
down to `workgroupSize(1, 1)` for the final level. Each compute dispatch
produces one pyramid level from the previous level.

### Source Depth

Hi-Z culling uses depth from one of:
1. **Z-prepass**: dedicated render pass that writes only depth for opaque draws
   before the main color pass. Most accurate, costs an extra pass.
2. **Previous frame depth**: re-projected depth from the prior frame. Faster
   (no extra pass) but inaccurate for animated or camera-moving scenes.
3. **Hybrid**: Z-prepass for moving objects, previous-frame depth for static.

### Depth Format

Depth buffer must be readable as a texture for compute sampling. Accepted formats:
- `depth32float` (preferred, highest precision)
- `depth24plus` (with `texture_depth_2d` sampling)
- Refused: `depth16unorm` (insufficient precision for Hi-Z)

## Occlusion Test

### Per-Draw Test Algorithm

```
bounds3D = transform draw's bounding volume to NDC
screenRect = project bounds3D to screen-space rect
pyramidLevel = determineLevel(screenRect) // highest level where rect covers <= 4 texels
sampleDepth = samplePyramidMax(pyramidLevel, screenRect)
drawMinDepth = bounds3D.minZ in NDC [0..1]
visible = drawMinDepth < sampleDepth || sampleDepth == 1.0
```

### Level Selection

```
determineLevel(screenRect):
    texelCountX = ceil(screenRect.width * pyramidWidth[level] / targetWidth)
    texelCountY = ceil(screenRect.height * pyramidHeight[level] / targetHeight)
    if texelCountX <= 2 && texelCountY <= 2: return level
    else: try next coarser level
```

Draws covering large screen areas use coarser pyramid levels. Draws covering
small areas use finer levels for tighter culling.

### Conservative Test

The test is conservative: a draw is culled only when its entire bounding volume
is behind the maximum depth in the sampled pyramid region. If the bounding
volume is partially visible or the depth comparison is ambiguous, the result is
`Uncertain` and the draw is rendered.

## Integration With Draw Analysis

`GPUOcclusionTracker` is extended with `GPUHiZOcclusionTest`:

```
GPUOcclusionTracker:
    if hizEnabled:
        for each draw in opaque passes:
            result = GPUHiZOcclusionTest.test(draw.boundsProof, hizPyramid)
            if result == Occluded:
                markCulled(draw)
            else:
                markVisible(draw)
    else:
        // fall back to conservative CPU occlusion tracking
```

Culling is valid only for opaque draws. Translucent draws, draws with
destination-read dependencies, and draws inside saveLayer scopes are never
culled by Hi-Z.

### Ordering Constraints

When Z-prepass is used:
1. Z-prepass pass renders all opaque draws (depth-only, no color).
2. Hi-Z pyramid is built from the Z-prepass depth buffer.
3. Occlusion tests cull draws for the main color pass.
4. Main color pass renders only non-culled draws.

When previous-frame depth is used:
1. Hi-Z pyramid is built from the previous frame's depth buffer.
2. Occlusion tests cull draws before recording.
3. Current frame renders with depth test enabled (writes depth for next frame's pyramid).

## Tile Interaction

With `38-tile-deferred-rendering.md`, each tile may have its own Hi-Z test:

```
for each tile:
    tileDepthBuf = extract tile region from full depth buffer
    tilePyramid = buildPyramid(tileDepthBuf)
    for each draw in tile.bin:
        if isOpaque(draw):
            test occlusion against tilePyramid
```

Tile-level pyramids are cheaper to build and sample than a full-target pyramid
for large targets. The trade-off is pyramid build overhead per tile.

## Acceptance Gates

- At least one scene with 50+ opaque rects, 50% occluded, with Hi-Z culling
  reducing rendered draw count by >= 40%.
- Z-prepass depth buffer produces correct Hi-Z pyramid (CPU oracle comparison
  of pyramid levels).
- Occluded draws are not visible in final output.
- False positives (visible draws incorrectly culled): ZERO tolerance.
- False negatives (occluded draws rendered): acceptable, measured as culling
  efficiency percentage.
- Depth pyramid memory budget enforced: refusal when pyramid size exceeds limit.
- Stable refusal for unsupported depth format, depth buffer not readable as
  texture, or compute shader capability missing.

## Non-Goals

- Do not implement occlusion queries via WebGPU `occlusionQuery` API (use only
  compute-shader Hi-Z).
- Do not cull translucent draws.
- Do not implement multi-view or stereo pyramid.
- Do not claim occlusion culling for first-frame rendering (only Z-prepass or
  second+ frames).
- Do not replace `GPUOcclusionTracker` completely; Hi-Z extends it.
