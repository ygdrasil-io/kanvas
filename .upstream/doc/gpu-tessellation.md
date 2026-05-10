# GPU Tessellation

`src/gpu/tessellate/` is the curve-tessellation toolkit shared by both
[Ganesh](ganesh-backend.md) and [Graphite](graphite-backend.md). Its job
is to take an `SkPath` plus a `SkMatrix` and produce a stream of
GPU-ready *patches* — small, fixed-size instance records that the vertex
shader expands into triangles. Filling, stroking, wedge-fans, and
middle-out polygon decomposition all live here, alongside the math that
decides how finely each curve must be subdivided so the final image
stays within a fraction of a pixel of the true Bézier.

The headers are deliberately backend-agnostic: nothing in
`skgpu::tess` knows about Vulkan, Metal, GL, or Dawn. The same
`PatchWriter` is instantiated by `GrPath...Op` in Ganesh and by the
`Tessellate{Curves,Strokes,Wedges}RenderStep`s in Graphite. For the
geometry primitives consumed (cubic / conic / quad / triangle) and the
wider math vocabulary (`SkPoint`, `SkM44`, `SkVx`), see
[Geometry & Math](geometry-and-math.md).

## Pipeline at a glance

```
       SkPath + SkMatrix + cull rect
                 │
                 ▼
       ┌──────────────────────┐
       │ MidpointContour-     │  per-contour midpoint
       │ Parser               │  (used as wedge fan-point)
       └────────┬─────────────┘
                │  per verb: line / quad / conic / cubic
                ▼
       ┌──────────────────────┐
       │   CullTest            │  drop verbs entirely outside viewport
       └────────┬─────────────┘
                │
                ▼
       ┌──────────────────────┐
       │ wangs_formula::      │  numParametricSegments_p4
       │   {quadratic,        │  (= n^4 minimum subdivisions)
       │    cubic, conic}     │
       └────────┬─────────────┘
                │
                ▼
       ┌──────────────────────┐
       │ LinearTolerances     │  resolveLevel = log16(n^4) for fills
       │                      │  edges-per-stroke for strokes
       └────────┬─────────────┘
                │
                ▼
       ┌──────────────────────┐
       │   PatchWriter        │  emit instance: 4 control points + attribs
       │   (templated by      │  auto-chop if Wang's formula > 1024
       │    PatchAllocator,   │  segments
       │    PatchAttribs)     │
       └────────┬─────────────┘
                │
                ▼
       GPU instance buffer  ─►  vertex shader expands to triangles
                                  using a static fixed-count
                                  vertex/index buffer
```

The output is a single `VertexWriter`-fed instance stream plus, for
fills using the wedge or middle-out strategies, a triangle stream that
glues curve patches together along the contour midpoint.

---

## Source map

| Header | Role |
|---|---|
| `Tessellation.h` | Constants (`kPrecision = 4`, `kMaxResolveLevel = 5`, `kMaxSegmentsPerCurve = 1024`), `PatchAttribs` flags, and `PreChopPathCurves` for paths that exceed the device limit. |
| `WangsFormula.h` | Curve-degree-aware bound on subdivisions needed to stay within `1/precision` pixels of the true curve. |
| `LinearTolerances.h` | Aggregates per-patch Wang's-formula results into a single resolveLevel (fills) or edge count (strokes). |
| `PatchWriter.h` | Templated, trait-driven instance writer — single source of truth for both backends. |
| `MidpointContourParser.h` | Two-pass-free contour iterator that also computes the geometric midpoint used as the wedge fan-point. |
| `MiddleOutPolygonTriangulator.h` | Stack-based middle-out triangulation of a polygon, invoked while parsing curves. |
| `StrokeIterator.h` | Walks a path's stroke geometry, exposing each verb together with its `prevVerb` so joins/caps can be emitted inline. |
| `CullTest.h` | Cheap bounding-box visibility test in a transformed space; degenerates that fall outside become lines. |
| `FixedCountBufferUtils.h` | Static vertex/index buffers for the three rendering modes (`FixedCountCurves`, `FixedCountWedges`, `FixedCountStrokes`). |
| `AffineMatrix.h` | 2x2 + translate matrix optimized for the inner-loop transforms `PatchWriter` performs. |
| `Tessellation.cpp` | Implements `PreChopPathCurves` and the table-driven curve type encoding. |

Backend integrations live outside this directory:

- Ganesh: `src/gpu/ganesh/tessellate/PathTessellator.cpp`,
  `StrokeTessellator.cpp`, plus the `GrPathTessellate*Op` family.
- Graphite: `src/gpu/graphite/render/TessellateCurvesRenderStep.cpp`,
  `TessellateStrokesRenderStep.cpp`,
  `TessellateWedgesRenderStep.cpp`,
  `MiddleOutFanRenderStep.cpp`,
  `DynamicInstancesPatchAllocator.h`.

---

## Wang's formula — how many segments is enough?

Wang's formula (Goldman 2003) gives the minimum number of evenly spaced
parametric line segments required to keep a degree-`n` Bézier within a
chosen pixel tolerance. For the in-tree default `kPrecision = 4`
(quarter-pixel), the implementation in `WangsFormula.h` exposes:

- `wangs_formula::quadratic`, `cubic`, `conic` — return the segment
  count or its 4th power (`_p4` variant) so we can defer the expensive
  `pow(x, 1/4)` until we actually need it.
- `wangs_formula::worst_case_cubic` — used by `PreChopPathCurves` to
  decide whether a curve must be pre-chopped on the CPU before reaching
  the GPU.
- Helpers `nextlog2`, `nextlog4`, `nextlog16` — branchless integer
  approximations used to convert a 4th-power segment count into a
  resolveLevel via a single bit-twiddling sequence on the IEEE
  exponent.

The fourth-power encoding is everywhere: it lets the code combine
multiple curves' worst-case requirements with a single `max()` and only
take the root once at the end. The result feeds straight into
`LinearTolerances::requiredResolveLevel()` (`log16(n^4) == log2(n)`),
which then clamps to `kMaxResolveLevel = 5` (32 segments per curve).

If a single curve would need more than `kMaxSegmentsPerCurve = 1024`
segments, `PatchWriter` chops it on the CPU; if it would need more than
that even after chopping (e.g. extreme zoom on a long cubic), the path
must first be passed through `Tessellation::PreChopPathCurves`, which
splits curves and culls the chops that fall outside the viewport.

---

## LinearTolerances — fold many curves into one number

`LinearTolerances` (`LinearTolerances.h`) carries three tracked values:

- `fNumParametricSegments_p4` — the running max of Wang's formula in
  4th-power form.
- `fNumRadialSegmentsPerRadian` — for strokes, how many radial segments
  per radian of curve rotation are needed to stay within tolerance.
- `fEdgesInJoins` — accumulated join geometry for stroked patches.

From these it derives `requiredResolveLevel()` for fill patches and
`requiredStrokeEdges()` for strokes (parametric + radial − overlap +
join contribution). The class is the bridge between per-curve
mathematics and the buffer-sizing logic in `FixedCountBufferUtils`.

---

## PatchWriter — the templated heart

`PatchWriter` is a class template whose first parameter is the
`PatchAllocator` (a backend-supplied object that hands out
`VertexWriter`s for a single instance) and whose remaining parameters
are *traits* picking which `PatchAttribs` are required, optional, or
disabled at compile time. The trait list also turns on:

- `TrackJoinControlPoints` — defer the first stroke patch until the
  preceding control point is known, then back-fill the join attribute.
- `AddTrianglesWhenChopping` — when a cubic chop is needed, also emit a
  glue triangle so the wedge fan stays watertight.
- `DiscardFlatCurves` — skip patches whose Wang's-formula segment count
  is 1 (i.e. line segments) — the cover step handles those.

Every patch is a fixed 4-control-point record (8 floats). Quadratics
are promoted to equivalent cubics on the CPU; conics store `{w, +inf}`
in the last point; triangles store `{+inf, +inf}`. The vertex shader
reads the last point and dispatches on the infinity bits. This is why
`PatchAttribs::kExplicitCurveType` is needed only on GPUs that cannot
cheaply test for `+inf`.

The backend-supplied `PatchAllocator` is what differs between Ganesh
and Graphite. Ganesh writes directly into a managed vertex buffer;
Graphite uses `DynamicInstancesPatchAllocator` to grow / shrink the
instance count per `DrawWriter` flush.

`PatchAttribs` enumerates every optional per-instance field —
`kJoinControlPoint`, `kFanPoint`, `kStrokeParams`, `kColor` (LDR or
wide), `kPaintDepth` (Graphite's depth attachment), `kExplicitCurveType`,
and `kSsboIndex` (Graphite's per-patch SSBO index). The bit-flag layout
matches the layout written into the instance, so a single mask doubles
as both the runtime feature switch and the on-GPU offset table.

---

## Stroke and contour iteration

`StrokeIterator` walks a path's verbs while exposing both the current
and previous verbs, so the consumer always has enough context to emit
a join. It synthesizes `kCircle` for round caps and silently converts
square caps and `kClose` into the appropriate line segments. The
`kMoveWithinContour` and `kContourFinished` pseudo-verbs let consumers
reset their join state without scanning ahead.

`MidpointContourParser` is the fill counterpart. It runs in a single
pass, summing point coordinates as it goes so that
`currentMidpoint()` returns the geometric mean of the contour's
vertices — cheap, deterministic, and good enough to seed a wedge fan.

---

## Middle-out triangulation

`MiddleOutPolygonTriangulator` decomposes a polygon by emitting one
large triangle that splits it in half, then recursing on each half.
Compared with a fan or strip, middle-out produces a roughly balanced
binary tree of triangles, which dramatically reduces overdraw on the
GPU (no skinny slivers radiating from a single vertex). The class
intentionally never materializes the polygon; instead it maintains an
`O(log N)` stack and emits triangles incrementally as new vertices are
pushed. The `PoppedTriangleStack` RAII helper lets the caller iterate
the freshly popped triangles inside a `for (auto [p0, p1, p2] : ...)`
loop and updates the stack on destruction.

This same technique is used by Graphite's `MiddleOutFanRenderStep` for
stencil-only inner-fan passes when filling with the curves strategy.

---

## CullTest — early visibility rejection

`CullTest` precomputes a transformed view matrix that maps a local
point `p` to a 4-vector `[x, y, -x, -y]` in device space, with the
translation absorbed into the cull bounds. A single SIMD compare then
suffices to reject a single point or a 3-/4-point convex hull. This is
called per verb so that off-screen pieces of a long curve can be
collapsed into a single segment instead of being chopped finely.

---

## Fixed-count buffers

`FixedCountBufferUtils.h` declares three closely related types:

- `FixedCountCurves` — fill mode that tessellates only the curves; the
  polygonal interior is covered separately by middle-out fans or stencil
  cover bounds.
- `FixedCountWedges` — fill mode where each curve patch carries a
  shared `kFanPoint` and is drawn as a wedge that includes its
  baseline triangle. Single-pass, no separate cover.
- `FixedCountStrokes` — stroke mode, paired with `kStrokeParams` and
  `kJoinControlPoint`.

Each type provides a `PreallocCount` heuristic for sizing the instance
arena, a `VertexCount(const LinearTolerances&)` to compute the actual
indexed-draw vertex count, and `WriteVertexBuffer` /
`WriteIndexBuffer` writers for the static template buffer that the
vertex shader reads while expanding each patch. The static buffer is
shared per backend per Skia process and only needs to be built once.

---

## Cross-references

- The two consumers of this toolkit are documented in
  [Ganesh Backend](ganesh-backend.md) (legacy GL/Vk/Mtl/D3D path ops)
  and [Graphite Backend](graphite-backend.md) (modern `RenderStep`s).
- Higher-level GPU concepts (atlases, paint key system, SkSL
  pipeline) live in [GPU Overview](gpu-overview.md).
- The geometry primitives consumed (`SkPath`, `SkMatrix`, `SkM44`,
  `SkVx`, conics, cubics) are described in
  [Geometry & Math](geometry-and-math.md).
- Compute-shader path rasterization (Vello, GPU coverage atlas) is an
  alternative covered under
  [Graphite Backend](graphite-backend.md#compute-atlases-and-clip).
