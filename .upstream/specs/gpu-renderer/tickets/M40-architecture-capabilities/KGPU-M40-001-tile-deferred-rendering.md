---
id: KGPU-M40-001
title: "Tile-deferred rendering"
status: done
milestone: M40
priority: P0
owner_area: passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M40-001 - Tile-deferred rendering

## PM Note

Le rendu tuilé différé subdivise les grandes cibles en tuiles de taille fixe pour borner la mémoire intermédiaire et paralléliser les passes.

## Problem

Large render targets produce unbounded intermediate memory and block-level parallelism. Without tile subdivision, the entire target must fit in adapter texture memory and every draw invocation must be evaluated against the full target bounds, wasting recording and execution time on empty regions.

## Scope

- `GPUTileGridPlan` — tile size 256x256 default, target dimensions, tile count X/Y, padding policy.
- `GPUTileGridPolicy` — adapter-preferred tile size, memory per tile, minimum tile count.
- `GPUTileBin` — per-tile draw invocations intersecting tile bounds.
- `GPUTileBinningPass` — distribution pass after `GPUOcclusionTracker`, assigns each live draw to intersecting tiles.
- `GPUTilePass` — scissor to tile bounds, sorted packets, tile-private intermediate targets.
- `GPUTileCompositePass` — merge via blit or direct target slice.
- `GPUTileMemoryBudget` — per-tile bytes, maximum concurrent tiles, budget cap at 25% of adapter texture memory.
- Two strategies: `DirectTargetSlice` (tile renders to final target sub-rect via scissor + viewport) and `TileIntermediateTexture` (tile renders to intermediate, composite copies to target).
- Cross-tile interaction rules: cross-tile dst read refused and deferred to composite; clip atomic groups must not span tiles.

## Non-Goals

- Do not promote support without accepted evidence.
- Do not activate product routing unless this ticket explicitly owns that decision and validation.
- Do not add hidden CPU-rendered texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/38-tile-deferred-rendering.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Drain pending uploads, record compute path-atlas dispatches, derive pass bounds/MSAA/depth-stencil/destination-read strategy, then convert pending draws into an immutable DrawPass.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Instantiate targets, prepare draw passes, recycle scratch resources, allocate MSAA/depth-stencil attachments, and replay the render pass.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Encode compressed painter order, depth ordering, and disjoint stencil indices so batching can reorder compatible work without violating visible draw order.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUTileGridPlan(
    val tileSize: Int = 256,
    val targetWidth: Int,
    val targetHeight: Int,
    val tileCountX: Int,
    val tileCountY: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
)

data class GPUTileGridPolicy(
    val adapterPreferredTileSize: Int,
    val maxMemoryPerTile: Long,
    val minTileCount: Int,
)

data class GPUTileBin(
    val tileIndexX: Int,
    val tileIndexY: Int,
    val drawInvocations: List<GPUDrawInvocation>,
)

data class GPUTileBinningPass(
    val sourcePass: GPUPass,
    val bins: List<GPUTileBin>,
)

data class GPUTilePass(
    val tile: GPUTileBin,
    val scissor: Rect,
    val sortedPackets: List<GPUDrawPacket>,
    val intermediates: List<GPUTileIntermediate>,
)

sealed class GPUTileStrategy {
    object DirectTargetSlice : GPUTileStrategy()
    data class TileIntermediateTexture(
        val intermediate: GPUIntermediateTexture,
        val compositePlan: GPUTileCompositePass,
    ) : GPUTileStrategy()
}

data class GPUTileCompositePass(
    val sourceTiles: List<GPUTilePass>,
    val mergeMode: GPUTileMergeMode,
)

data class GPUTileMemoryBudget(
    val perTileBytes: Long,
    val maxConcurrentTiles: Int,
    val adapterTextureMemoryFraction: Float = 0.25f,
)
```

## Acceptance Criteria

- [ ] 2048x2048 target rendered as 8x8 tiles (256x256 each) with CPU oracle parity.
- [ ] Empty tiles correctly culled (no draw invocation, no GPU submission).
- [ ] Tile composite result pixel-exact vs single-pass render.
- [ ] Dst read within single tile accepted; cross-tile dst read refused with stable diagnostic.
- [ ] Cross-tile clip atomic group refused with stable diagnostic.
- [ ] Memory budget enforced: refusal when per-tile bytes exceed 25% adapter texture memory.
- [ ] Telemetry emitted: tile count, culled tile count, tile pass duration, composite pass duration.

## Required Evidence

- 2048x2048 tiled render diff/stat artifact vs single-pass reference.
- Empty-tile culling diagnostics showing zero submission for fully empty tiles.
- Cross-tile dst read refusal route diagnostic.
- Cross-tile clip atomic group refusal route diagnostic.
- Memory budget exceeded refusal route diagnostic.
- Telemetry dump: `reports/gpu-renderer/telemetry/tile-deferred/`.

## Fallback / Refusal Behavior

- Budget exceeded: `unsupported.tile.budget_exceeded` diagnostic, no silent CPU fallback.
- Cross-tile dst read: `unsupported.tile.cross_tile_destination_read` diagnostic, deferred to composite pass.
- Cross-tile clip atomic group: `unsupported.tile.cross_tile_clip_atomic_group` diagnostic, refused at binning.
- Silent fallback to CPU-rendered complete draw/layer/filter/text texture compatibility is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.architecture.tile-deferred`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TileDeferred*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): milestone activated, starting implementation.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted — independent review accepted.

## Linear Labels

- `gpu-renderer`
- `milestone:M40`
- `area:passes`
