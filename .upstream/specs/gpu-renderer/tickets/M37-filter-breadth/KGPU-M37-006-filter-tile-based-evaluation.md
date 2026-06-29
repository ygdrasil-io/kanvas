---
id: KGPU-M37-006
title: "Filter tile-based evaluation"
<<<<<<< HEAD
status: done
=======
status: proposed
>>>>>>> master
milestone: M37
priority: P1
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M37-006 - Filter tile-based evaluation

## PM Note

L'evaluation par tuiles permet d'appliquer des filtres sur de grandes surfaces
sans depasser le budget de memoire GPU intermediaire. C'est critique pour le
blur sur des sources 4K et pour les futures compositions de calques haute
resolution.

## Problem

Current GPU renderer has no tile-based filter evaluation route. Applying a
filter (e.g., Gaussian blur with sigma=20) to a large source (e.g., 4K texture)
requires an intermediate texture of the same size, which may exceed GPU memory
budgets. Tiled evaluation subdivides the source into smaller overlapping tiles,
renders each tile through the filter pipeline independently, and composites the
results. The overlap must be at least `sigma * 3` pixels (for Gaussian) to
ensure seamless boundaries between adjacent tiles. Without tiled evaluation,
large-kernel or large-source filters cannot be promoted to TargetNative.

## Scope

- `GPUFilterTilePlan` — tile grid dimensions (columns × rows), tile size
  (width × height in pixels), overlap (sigma * 3 for Gaussian, radius for
  morphology, general configurable margin), source region per tile, target
  region per tile with overlap trim.
- `GPUFilterTileRenderPlan` — per-tile render loop: allocate tile-sized
  intermediate texture, render source subregion through filter pipeline into
  tile intermediate, composite tile into output texture at correct position
  with overlap trim (discard overlap border, keep tile interior).
- `GPUFilterTileBudgetPolicy` — max memory per tile intermediate, max tile
  count (limit total GPU memory usage across concurrent tiles), policy for
  serial vs batched tile execution.

## Non-Goals

- Do not implement adaptive tile sizing based on source content complexity.
- Do not implement multi-GPU or distributed tile evaluation.
- Do not implement tile evaluation for non-filter passes (draws, layers,
  composites).
- Do not activate product routing for tile-based evaluation.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Provide scratch-device, special-image, cached-bitmap proxy, and blur-device hooks used by image filters.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Decide when a filter result must resolve to texture versus remain deferred as shader logic.
- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - source [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20); Wrap Graphite-backed images with subset metadata and convert non-Graphite images through the recorder image provider before filter use.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUFilterTilePlan(
    val tileWidth: Int,
    val tileHeight: Int,
    val overlap: Int, // sigma * 3 for Gaussian, radius for morphology
    val columns: Int,
    val rows: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

data class GPUFilterTileRenderPlan(
    val tilePlan: GPUFilterTilePlan,
    val tileIndex: GPUFilterTileIndex,
    val sourceRegion: GPUFilterTileRegion, // includes overlap
    val targetRegion: GPUFilterTileRegion, // excludes overlap (interior only)
    val intermediate: GPUBlurIntermediateArtifact, // tile-sized
)

data class GPUFilterTileIndex(val column: Int, val row: Int)

data class GPUFilterTileRegion(val x: Int, val y: Int, val width: Int, val height: Int)

data class GPUFilterTileBudgetPolicy(
    val maxIntermediateBytes: Long,
    val maxActiveTileCount: Int,
)
```

### Route

```
FilterTileEvaluation(filter, source, tileSize, overlap) → tile plan
    → tileSize > 0 && overlap >= 0 && (tileSize - 2*overlap) > 0:
        → compute tile grid: ceil(sourceWidth / (tileSize - 2*overlap)) columns
          × ceil(sourceHeight / (tileSize - 2*overlap)) rows
        → for each tile:
            → source region = tile interior expanded by overlap on all sides
            → render source region through filter into tile intermediate
            → composite tile interior (trim overlap) into output at correct position
    → tileSize <= 2*overlap → refusal (tile smaller than kernel)
    → result (full-size filtered output assembled from tiles)

Filter → elision (identity) → no tiling needed
Overlap == 0 → no overlap, may produce seams; allowed but not recommended
```

## Acceptance Criteria

- [ ] Gaussian blur sigma=20 on a 4096×4096 source, tiled with 1024×1024 tiles,
      overlap=60 (sigma*3), produces output matching non-tiled (single-pass full
      source) blur within precision (per-pixel MSE < 1e-3).
- [ ] Overlap region correctly sampled: pixels near tile boundaries match
      non-tiled output exactly (no visible seams). Verify at tile boundaries at
      columns [1, cols-1] and rows [1, rows-1].
- [ ] Tile interior trimmed correctly: no overlap pixels leak into final output.
- [ ] Tile count check: 4096×4096 source with 1024×1024 tiles produces correct
      number of tiles — ceil(4096 / (1024 - 120)) ≈ 5×5 = 25 tiles.
- [ ] Refusal when tile size (after trim) is smaller than kernel radius:
      tileWidth - 2*overlap <= 0 produces `unsupported.filter.tile_smaller_than_kernel`.
- [ ] Single tile covering full source (tile size >= source size) produces
      correct degenerate case (no tiling overhead).

## Required Evidence

- Contract tests for tiled blur at sigma [2, 10, 20] with tile sizes
  [256, 512, 1024].
- Parity dumps: tiled output vs non-tiled output per-pixel comparison.
- Tile interior dumps: per-tile output before composition, verifying overlap
  trim correctness.
- Refusal fixtures for tile-smaller-than-kernel and zero-tile-size.
- Budget policy test: verify per-tile intermediate memory does not exceed
  maxIntermediateBytes.
- WGSL validation: tiling logic does not affect filter shader codegen; only
  passes smaller source regions.
- Full pipeline integration test with `GPUSeparableBlurPlan` from M37-001.

## Fallback / Refusal Behavior

- Tile interior (after trim) is zero or negative:
  `unsupported.filter.tile_smaller_than_kernel` diagnostic.
- Tile count exceeds maximum:
  `unsupported.filter.tile_count_exceeds_budget` diagnostic.
- Per-tile intermediate memory exceeds budget:
  `unsupported.filter.tile_intermediate_memory_budget` diagnostic.
- Source dimensions not divisible into integer tile grid:
  `unsupported.filter.tile_source_not_divisible` diagnostic (or clamp with
  documented boundary behavior).
- No silent CPU-rendered complete tiled-filter-to-texture fallback is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.tile-eval`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FilterTile*'
```

## Status Notes

- `proposed`: Initial ticket.
<<<<<<< HEAD
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted to done after independent review accepted linked evidence; no hidden product activation.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M37`
- `area:filters`
