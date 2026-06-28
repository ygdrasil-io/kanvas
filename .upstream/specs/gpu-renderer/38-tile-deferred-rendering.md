# Tile-Deferred Rendering Architecture

Status: Draft
Date: 2026-06-28

## Purpose

Define the tile-based deferred rendering architecture for the GPU-first
renderer. Large render targets are subdivided into fixed-size tiles. Each tile
is rendered independently, enabling bounded intermediate memory, early tile
culling, and parallel tile execution where the adapter permits.

This spec owns the tile grid, tile pass partitioning, tile binning strategy,
resolve/composite final pass, interaction with destination-read and clip plans,
and tile memory budget. It is a target architecture spec, not an implementation
slice.

The target is Graphite-inspired but Kanvas-owned:
- Graphite uses a tile-aligned render pass model with `DrawList` per tile,
  scissor-restricted rendering, and atlases partitioned by tile visibility;
- Kanvas keeps the tile subdivision idea but owns tile pass construction,
  binning, budget, and diagnostics explicitly.

## Source Specs

This spec depends on:
- `00-architecture-kernel.md` for module, naming, and Graphite equivalence policy;
- `02-gpu-recording-task-graph.md` for `GPURecording`, `GPUTaskList`, `GPUDrawPass`, and ordering rules;
- `04-pipeline-key-cache-resources.md` for resource provider and cache policy;
- `12-blend-color-target-state.md` for `GPUTargetState` and `GPUMultisamplePlan`;
- `20-destination-read-strategy.md` for destination-read interaction across tile boundaries;
- `37-draw-packet-command-stream.md` for packet and pass-command materialization.

## Contracts

| Contract | Purpose |
|---|---|
| `GPUTileGridPlan` | Tile grid configuration: tile size (e.g., 256x256), target dimensions, tile count X/Y, and tile padding (for filter expansion overlap). |
| `GPUTileGridPolicy` | Tile size selection policy: adapter-preferred size, memory budget per tile, and minimum tile count threshold for enabling tiled rendering. |
| `GPUTileBin` | Per-tile accumulated draw invocations: subset of `GPUDrawInvocation` objects whose conservative bounds intersect the tile. |
| `GPUTileBinningPass` | Analysis pass that distributes accepted draw invocations into per-tile bins. Runs after `GPUDrawAnalysis` and `GPUOcclusionTracker`. |
| `GPUTilePass` | Per-tile render pass: scissor to tile bounds, sorted packets for tile-local draws, and tile-private intermediate resources. |
| `GPUTileCompositePass` | Final composite pass: merges rendered tiles into a single target. May be a GPU copy/blit or resolve when tiles are direct sub-rectangles of the target. |
| `GPUTileMemoryBudget` | Per-tile memory budget: max intermediate bytes per tile, max tile count in flight, and total tile intermediate pool size. |
| `GPUTileResourcePlan` | Per-tile resource allocation: tile intermediate texture, tile-bound atlas page leasing, and tile-scoped uniform buffers. |
| `GPUTileDiagnostic` | Reason codes for tile budget exceeded, tile too small (below kernel footprint), empty tile culling, and tile composite mismatch. |

## Tile Grid Construction

### Grid Sizing

```
tileSize = adapter.preferredTileSize ?: 256
tileCountX = ceil(targetWidth / tileSize)
tileCountY = ceil(targetHeight / tileSize)
```

Tiles at the right and bottom edges may be smaller than `tileSize`. Partial
tiles are valid but must correctly scissor their render pass.

### Activation Policy

Tiled rendering is activated when:
1. `GPUTileGridPolicy` decides: target area exceeds threshold (e.g., targetWidth * targetHeight > 1024 * 1024).
2. Adapter supports multiple render passes per frame with correct attachment load/store.
3. Tile memory budget can satisfy at least one full row of tiles concurrently.

If any condition fails, the renderer falls back to single-pass full-target
rendering for the current frame.

## Tile Binning

### Binning Algorithm

```
for each accepted GPUDrawInvocation:
    conservativeBounds = invocation.boundsProof.conservativeDeviceBounds
    for each tile in grid:
        if conservativeBounds.intersects(tile.rect):
            tile.bin.add(invocation)
```

A draw invocation may be added to multiple tiles when its bounds straddle tile
boundaries.

### Binning Rules

1. Binning uses `GPUBoundsProof` conservative bounds, not exact per-pixel coverage.
2. Empty tiles (zero draws) are culled: no render pass is created for them.
3. Draws with `fullTarget` bounds (e.g., clear, full-frame backdrop) are added
   to every non-culled tile.
4. Ordering tokens (`GPUClipOrderingToken`, `GPUDestinationReadToken`, etc.)
   that span tile boundaries must be respected: draws in the same atomic group
   are binned to the same tile set.

### Interaction With Occlusion Culling

`GPUOcclusionTracker` runs before binning. Culled draws are excluded from all
tile bins. When `40-hi-z-occlusion-culling.md` provides per-tile occlusion
queries, individual tiles may cull draws that are fully occluded within that
tile.

## Tile Pass Construction

Each non-empty tile produces one `GPUTilePass` that wraps a standard
`GPUDrawPass` with tile-specific state:

```
for each non-empty tile:
    tilePass = GPUTilePass(
        tileID = (tx, ty),
        scissorRect = tile.clipRect(),
        drawPass = GPUDrawPass.from(tile.bin),
        targetAttachment = tileIntermediate or directTargetSlice
    )
```

### Tile Intermediate Strategy

Two strategies for tile target attachment:

| Strategy | Description | When used |
|---|---|---|
| `DirectTargetSlice` | Tile renders directly into a sub-rectangle of the final target via scissor + viewport offset. | When target supports multiple render passes with `Load` and `Store` ops per region. |
| `TileIntermediateTexture` | Tile renders into a tile-sized intermediate texture. After all tiles complete, a composite pass copies or resolves tiles into the final target. | When direct target slicing is unavailable, or when tile parallelism requires independent targets. |

### Tile Ordering

Tiles are independent and may execute in parallel where the adapter and resource
provider support concurrent render pass construction. Serial execution preserves
tile order (row-major, left-to-right, top-to-bottom).

## Composite Pass

When `TileIntermediateTexture` strategy is used, a composite pass copies each
tile intermediate to its destination rectangle in the final target.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUTileCompositeCommand` | Per-tile copy: source tile intermediate texture, source rect (0,0,tileW,tileH), destination rect in target. |
| `GPUTileCompositeBlitPlan` | WGPU blit/copy command plan for tile-to-target transfer. One blit per tile. |
| `GPUTileCompositeDiagnostic` | Refusal for mismatched tile intermediate format, target format incompatibility, or blit capability unavailable. |

## Tile Memory Budget

### Budget Model

```
totalTilePoolBytes = adapter.maxTextureMemoryBytes * tilePoolRatio
perTileBytes = tileSize * tileSize * bytesPerPixel * attachmentCount
maxConcurrentTiles = totalTilePoolBytes / perTileBytes
```

Default `tilePoolRatio`: 0.25 (25% of available texture memory).

### Budget Enforcement

1. Before tile pass construction, reserve `perTileBytes` from the tile memory pool.
2. If pool is exhausted, serialise remaining tiles (reuse freed intermediates).
3. If `perTileBytes` exceeds `totalTilePoolBytes`, refuse tiled rendering
   for this frame and fall back to single-pass.
4. Tile intermediate textures are released back to the pool after the composite
   pass consumes them.

## Interaction With Destination Reads

When a draw requires destination reads (e.g., shader blend, backdrop filter)
and the destination spans tile boundaries:

1. `DirectTargetSlice` strategy: destination read is valid only within the
   current tile's scissor region. Cross-tile destination reads are refused
   unless the source region is fully contained in the tile.
2. `TileIntermediateTexture` strategy: destination reads are not available
   during tile rendering. The draw must be deferred to the composite pass or
   refused.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUTileDestinationReadPlan` | Tile-aware destination-read: bounds test for tile containment, deferral to composite pass when source crosses tiles, or refusal. |
| `GPUTileDestinationReadDiagnostic` | Refusal when destination read spans tiles and cannot be deferred. |

## Interaction With Clip Stencil

Stencil-based clip operations that span tile boundaries:

1. Stencil state (clear/load/store) is per-tile.
2. Clip atomic groups must not span tiles. If a clip producer-consumer pair
   would cross tiles, binning splits the atomic group or refuses.
3. `GPUClipOrderingToken` is evaluated per-tile.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUTileClipPlan` | Tile-aware clip: determine if clip stack is tile-local or requires full-target clip preparation before tiling. |
| `GPUTileClipDiagnostic` | Refusal when clip atomic group cannot be split across tiles and full-target clip preparation is unavailable. |

## Acceptance Gates

- At least one 2048x2048 target rendered as 8x8 tiles (256x256) with correct output (CPU oracle parity).
- Empty tiles culled: tile count in telemetry < grid tile count.
- Tile intermediate composite produces pixel-exact match with single-pass rendering.
- Destination read confined to single tile accepted; cross-tile destination read refused or deferred.
- Tile memory budget enforced: large tile count triggers serialization, not crash.
- Telemetry reports: tile count, culled tile count, tile pass count, composite pass time, and tile pool pressure.

## Non-Goals

- Do not implement compute-shader tile binning (CPU binning is the first path).
- Do not expose tile grid as a product API.
- Do not claim parallel tile execution without measured evidence.
- Do not implement tile-based texture atlasing (path/coverage/glyph atlases remain global).
- Do not replace single-pass rendering for small targets.
