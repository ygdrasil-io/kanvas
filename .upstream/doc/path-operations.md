# Path Operations

Path operations are Skia's boolean algebra on `SkPath`: given two filled
regions A and B, compute `A ∪ B`, `A ∩ B`, `A − B`, `B − A`, or
`A ⊕ B`, returning a new path whose contours describe exactly that
region. The same machinery also powers `Simplify` (collapse
self-intersecting / overlapping contours into a non-overlapping
equivalent) and `AsWinding` (rewrite an even-odd path so it draws the
same area under the non-zero winding rule).

The implementation lives entirely in `src/pathops/`, behind a tiny
five-function public API in `include/pathops/SkPathOps.h`. The path
data structure itself, plus the curve types (`SkPathBuilder`,
`SkPathRef`, conics, the `SkPath::Verb` stream) are described in
[Geometry & Math](geometry-and-math.md). This document covers what
happens once you call `Op(a, b, kUnion_SkPathOp)`.

```
   SkPath A ─┐
             ├──► SkOpEdgeBuilder ──► SkOpContour list ──► AddIntersections ──┐
   SkPath B ─┘     (verbs → segments)   (curves bucketed)   (T-section solve) │
                                                                              ▼
                                                                 SkOpCoincidence
                                                                 (collinear runs)
                                                                              │
                                                                              ▼
                                                                  HandleCoincidence
                                                                  (mark spans, fix
                                                                   winding rules)
                                                                              │
                                                                              ▼
                                                                  Walk segments,
                                                                  follow active
                                                                  angles, emit verbs
                                                                              │
                                                                              ▼
                                                                       SkPathWriter
                                                                              │
                                                                              ▼
                                                                       result SkPath
```

---

## Public API — `include/pathops/SkPathOps.h`

The entire surface is five free functions plus one helper class.

| Symbol | Purpose |
|---|---|
| `enum SkPathOp` | Selects the boolean: difference, intersect, union, XOR, reverse-difference. |
| `Op(a, b, op)` | `std::optional<SkPath>` — combine `a` and `b`. |
| `Simplify(path)` | `std::optional<SkPath>` — return a non-overlapping equivalent. |
| `AsWinding(path)` | Rewrite an even-odd path as a winding path covering the same area. |
| `TightBounds(path, *)` | Deprecated wrapper around `SkPath::computeTightBounds()`. |
| `class SkOpBuilder` | Batches many `add(path, op)` calls and resolves them once — the cheap form when unioning hundreds of paths. |

The `SkPathOp` enum:

```cpp
enum SkPathOp {
    kDifference_SkPathOp,         // this − operand
    kIntersect_SkPathOp,          // this ∩ operand
    kUnion_SkPathOp,              // this ∪ operand
    kXOR_SkPathOp,                // (this ∪ operand) − (this ∩ operand)
    kReverseDifference_SkPathOp,  // operand − this
};
```

The function returns `std::nullopt` (or `false` on the deprecated
out-pointer overloads) when the algorithm fails to converge or detects
inconsistency. Callers must check; pathops is not infallible on
pathological numerically-degenerate input.

`Simplify` is `Op(path, empty, kUnion)` in spirit — it walks the same
contour graph but with a single operand, so coincident edges within one
path collapse and self-intersections become explicit contours. The
output uses non-zero winding regardless of the input fill type.

`AsWinding` uses an entirely different code path
(`SkPathOpsAsWinding.cpp`): no segment graph is built; instead each
contour is classified by computing winding numbers at sample points
and the appropriate contours are reversed so the resulting path, drawn
with `SkPathFillType::kWinding`, fills the same region as the original
even-odd path.

### `SkOpBuilder`

```cpp
SkOpBuilder b;
for (const SkPath& p : many) b.add(p, kUnion_SkPathOp);
auto merged = b.resolve();
```

`SkOpBuilder` accumulates `(path, op)` pairs and runs the boolean
engine once across all of them in `resolve()`. For pure-union batches
it short-circuits through `FixWinding` / `ReversePath` helpers and
avoids re-running the full segment intersector for every pair, making
it the right choice when you have N paths and want their union.

---

## Internal architecture

### Source-file map

| File | Role |
|---|---|
| `src/pathops/SkPathOpsOp.cpp` | Entry point for `Op` / `OpDebug`. Sets up two contour heads, drives the four phases (intersect, coincidence, walk, emit). |
| `src/pathops/SkPathOpsSimplify.cpp` | Entry point for `Simplify`; same machinery with one operand. |
| `src/pathops/SkPathOpsAsWinding.cpp` | Stand-alone winding-rule converter. |
| `src/pathops/SkPathOpsCommon.{h,cpp}` | Shared helpers: `SortContourList`, `FindSortableTop`, `HandleCoincidence`, `AngleWinding`. |
| `src/pathops/SkPathOpsTypes.{h,cpp}` | `SkOpGlobalState`, `SkOpPhase` (`kIntersecting` / `kWalking` / `kFixWinding`), the `SkPathOpsMask` (winding/even-odd) enum, scalar tolerance constants. |
| `src/pathops/SkOpEdgeBuilder.{h,cpp}` | Verb stream → reduced segments. Closes contours, collapses degenerate cubics to quads/lines via `SkReduceOrder`. |
| `src/pathops/SkOpContour.{h,cpp}` | A linked list of segments forming one closed contour; carries the `operand()` flag (which input path it came from). |
| `src/pathops/SkOpSegment.{h,cpp}` | One curve span with windings, T-values, neighbor pointers, "done" flags. The richest object in the system. |
| `src/pathops/SkOpSpan.{h,cpp}` | A point on a segment at a given T; spans are the graph nodes joined by `SkOpPtT` chains. |
| `src/pathops/SkOpAngle.{h,cpp}` | Sorted angular order around a vertex — picks "next edge" when walking the boundary. |
| `src/pathops/SkOpCoincidence.{h,cpp}` | Detects and merges runs where two curves overlap (a 1-D rather than 0-D intersection). |
| `src/pathops/SkAddIntersections.{h,cpp}` | Quadratic loop that intersects every segment of A with every segment of B (and segments of one path with each other), inserting span-pairs at each crossing. |
| `src/pathops/SkIntersections.{h,cpp}` | Up-to-12 root buffer and helpers that the curve-curve solvers append into. |
| `src/pathops/SkPathOpsTSect.{h,cpp}` | Topological T-section: subdivision-based curve/curve intersection that frames the cubic-cubic case. |
| `src/pathops/SkPathOpsCubic.{cpp,h}` / `Quad` / `Conic` / `Line` | Per-curve math: parameter-space coefficients, evaluator, monotonic split, `findInflections`, `chopAt`. |
| `src/pathops/SkDLineIntersection.cpp`, `SkDQuadLineIntersection.cpp`, `SkDConicLineIntersection.cpp`, `SkDCubicLineIntersection.cpp`, `SkDCubicToQuads.cpp` | The closed-form line/curve intersectors and a cubic→quad approximator used by the T-section solver. |
| `src/pathops/SkOpCubicHull.cpp` | Convex-hull pruning to skip curve pairs whose hulls don't overlap. |
| `src/pathops/SkPathOpsBounds.h`, `SkPathOpsRect.{h,cpp}`, `SkPathOpsPoint.h`, `SkLineParameters.h` | Double-precision geometry primitives. Everything inside pathops uses `double`, not `SkScalar`. |
| `src/pathops/SkPathWriter.{h,cpp}` | Output sink: receives contour-walking events and emits an `SkPath`. |
| `src/pathops/SkPathOpsTightBounds.cpp` | Tight-bounds via the same edge intersector. |
| `src/pathops/SkPathOpsDebug.{h,cpp}` | Optional dumpers (`SK_DEBUG`-only); the `DEBUG_VALIDATE`, `DEBUG_COIN`, `DEBUG_T_SECT_LOOP_COUNT` knobs are wired through here. |

### Phases of `Op`

`SkPathOpsOp.cpp` and `SkPathOpsCommon.cpp` together orchestrate the
following phases (`SkOpGlobalState::fPhase` tracks the current one):

1. **Build** — `SkOpEdgeBuilder::finish()` consumes the two input
   `SkPath`s. Each verb becomes a segment in an `SkOpContour`; the
   second path's contours are flagged `operand = true`. Cubics are
   reduced to quads and quads to lines where the curvature collapses
   below tolerance (`SkReduceOrder`).
2. **Sort** — `SortContourList` orders contours by topmost-then-leftmost
   so the first walked contour is always on the outside.
3. **Intersect** (`SkOpPhase::kIntersecting`) — `AddIntersections`
   pairs every curve with every other, calling into the T-section
   solver (`SkPathOpsTSect`) for curve/curve and the closed-form
   intersectors for line/curve and line/line. Each crossing inserts a
   matched pair of `SkOpSpan`s on the two segments at the same world
   point.
4. **Coincidence** — `SkOpCoincidence` collects runs where curves
   overlap rather than cross; `HandleCoincidence` merges those runs,
   marks the affected spans, and propagates winding deltas.
5. **Walk** (`SkOpPhase::kWalking`) — starting from a guaranteed-outer
   span found by `FindSortableTop`, the algorithm picks the next edge
   using `SkOpAngle::next()` and emits verbs to `SkPathWriter`. The
   "winding rule" for the chosen op is encoded in
   `SkOpSegment::activeOp` — it asks "given the in/out winding state on
   each side of this edge, is it on the boundary of the result?"
6. **Fix winding** (`SkOpPhase::kFixWinding`) — final cleanup pass via
   `FixWinding` to make the emitted contours consistent with the
   non-zero winding rule.

### Numerical robustness

Pathops is the part of Skia where ordinary `float` arithmetic stops
working. `SkPathOpsTypes.h` defines tolerances (`FLT_EPSILON_CUBED`,
`FLT_EPSILON_HALF`, `FLT_EPSILON_LARGE`, `WAY_ROUGHLY_EQUAL`,
`MORE_ROUGHLY_EQUAL`) used at every comparison; all internal math is
double-precision. `SkOpGlobalState::kMaxWindingTries = 10` caps
re-entrant winding solves, after which the operation gives up rather
than loop forever. Debug builds compile with `DEBUG_VALIDATE` and
`DEBUG_COIN` to assert structural invariants on every transition.

When pathops fails it returns `std::nullopt`. Higher layers (notably
the path-effect stage in [Path Effects](path-effects.md), and stroke
expansion) treat that as a soft failure and fall back to the original
geometry.

---

## Module: `modules/pathops/`

There is no `modules/pathops/` directory in current Skia — boolean
operations are core. The module subtree historically held a small
SkOpBuilder convenience layer that has since merged into
`include/pathops/SkPathOps.h`. The Bazel target lives at
`src/pathops/BUILD.bazel`.

---

## Cross-references

- [Geometry & Math](geometry-and-math.md) — `SkPath`, `SkPathBuilder`,
  `SkPathRef`, the verb stream, `SkPathFillType`, conic weights. The
  inputs and outputs of `Op` / `Simplify` are these objects.
- [Path Effects](path-effects.md) — `SkDashPathEffect`,
  `SkCornerPathEffect`, etc. Some path effects internally call
  `Simplify` on the result, and stroke-to-fill conversion uses the
  same boolean machinery to merge inner and outer contours.
- [Paint, Color & Blending](paint-color-and-blending.md) — fill rules
  (`SkPathFillType::kWinding` vs `kEvenOdd`) interpreted at draw time;
  `AsWinding` lets you bake an even-odd path into a winding one.
- [CPU Rendering Pipeline](cpu-rendering-pipeline.md) — once the
  result of `Op` is drawn, the raster scan-converter takes over.
