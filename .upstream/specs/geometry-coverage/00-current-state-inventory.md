# Spec 00: Current State Inventory

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Purpose

Document what Kanvas does today before introducing new geometry/coverage
contracts. This avoids writing specs against an imagined renderer.

## Current Module Boundaries

| Module | Current role |
|---|---|
| `kanvas-skia` | Public Skia-like API, `SkCanvas`, CPU raster device, paths, clips, stroker, masks. |
| `cpu-raster` | Extra CPU raster/effects utilities, pathops/SVG/tools/test support, and possible future consumer of shared contracts. |
| `gpu-raster` | WebGPU device, handwritten/generated WGSL resources, GPU tests and cross-backend harness. |
| `render-pipeline` | Emerging paint/pipeline IR, not yet a full geometry owner. |

`kanvas/src/main/kotlin` legacy code may be read as historical context only.
It must not become a dependency for this target.

## Current CPU Geometry/Coverage

Primary entry points:

- `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`
- `kanvas-skia/src/main/kotlin/org/skia/core/SkDevice.kt`
- `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkAAClip.kt`
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkStroker.kt`
- `kanvas-skia/src/main/kotlin/org/skia/core/SkClipShape.kt`

Current behavior:

- `SkCanvas` owns CTM, save/restore, saveLayer, and clip state.
- Axis-aligned solid `drawRect` can route to a fast rect rasterizer.
- Non-axis-aligned rects, shaders, path effects, and mask filters route
  through `drawPath`.
- `drawPath` applies path effect before stroking and mask filtering.
- `SkStroker` converts stroke paths into fillable outline paths.
- CPU path fill flattens curves, builds scanline edges, and applies winding,
  even-odd, and inverse fill rules.
- AA path fill uses supersampled coverage.
- Rect AA uses analytic overlap.
- `SkAAClip` stores clip coverage as run-length encoded alpha bands.
- Clip shader coverage is sampled during blending.

Current risks:

- Geometry and paint are interleaved in `SkBitmapDevice.drawPath`.
- Flattening and stroking rules are partly duplicated between CPU and GPU.
- Coverage is not represented as a standalone contract before execution.
- Unsupported paths often depend on local behavior instead of one diagnostic
  taxonomy.
- `cpu-raster` has substantial CPU-side utility code, but it is not currently
  the active owner of draw coverage execution.

## Current WebGPU Geometry/Coverage

Primary entry point:

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`

Current behavior:

- Rect fill uses full-screen triangle/scissor plus fragment analytic coverage.
- Rect stroke decomposes into rect-like coverage paths.
- Path fill flattens CPU-side into device-space vertices.
- Convex single-contour paths use fan tessellation.
- Concave, inverse, or multi-contour paths can route through stencil-cover.
- AA path coverage can be computed from edge segments in WGSL.
- Gradients and bitmap shaders have dedicated rect and stencil-cover variants.
- `SkClipShape` supports analytic simple-shape clips for WebGPU paths that
  explicitly consume the clip shape.
- Blur mask filters render a shape mask into an offscreen WebGPU device, blur
  it, then composite.

Current risks:

- GPU geometry selection is encoded directly in `SkWebGpuDevice.drawPath`.
- CPU-prepared fan/stencil data does not pass through a named `CoveragePlan`.
- Pipeline-key axes are exposed in local GPU code, not derived from a shared
  geometry/coverage descriptor.
- Coverage atlas behavior is not formalized.

## Current Test Evidence

Useful existing evidence families:

- `kanvas-skia/src/test/kotlin/org/skia/core/*Path*`
- `kanvas-skia/src/test/kotlin/org/skia/core/*Clip*`
- `kanvas-skia/src/test/kotlin/org/skia/core/*Stroke*`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/*Path*`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/*Stroke*`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/*Clip*`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/*`
- `skia-integration-tests/src/test/kotlin/org/skia/tests/*Path*`
- `skia-integration-tests/src/test/kotlin/org/skia/tests/*Stroke*`
- `skia-integration-tests/src/test/kotlin/org/skia/tests/*Clip*`

The spec work should identify a small baseline set before implementation:

- rect fill/stroke, AA and non-AA;
- rrect/oval/circle fill;
- simple path fill with winding and even-odd;
- inverse fill;
- multi-contour path with hole;
- stroke caps/joins/miter;
- clip rect/path/rrect intersect and difference;
- glyph mask path;
- image rect path;
- path-heavy GPU cross-backend scenes.

## Inventory Acceptance Criteria

- The source files above are mapped to responsibilities.
- Current CPU and GPU execution strategies are named.
- Gaps are expressed as contracts to add, not as implementation blame.
- Baseline test families are selected before implementation tickets start.
