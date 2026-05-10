# Geometry & Math

Skia's geometry layer is a small set of value types: scalars, points,
rectangles, rounded rectangles, regions, paths, matrices (2-D and 3-D), and
parametric curve helpers. None of them are refcounted (with the special
exception of `SkContourMeasure`, `SkPathData`, and `SkVertices` — see below);
they are cheap to copy and to pass by value. Most live in `include/core/` and
have implementations split between `src/core/` (high-level helpers) and
`src/base/` (Bézier / cubic / quadratic primitives).

```
                                    SkPoint, SkVector
                                          │
                            ┌─────────────┴─────────────┐
                       SkRect                        SkRRect
                            │                            │
                            └────────────┬───────────────┘
                                         │
                                       SkPath ◄── SkPathBuilder
                                         │ ▲          │
                                         │ │          │
                          (SkPathRef shared storage)  │
                                         │            │
                              SkPathIter / RawIter / Iter
                                         │
                       ┌─────────────────┼─────────────────┐
                  SkPathMeasure    SkContourMeasureIter / SkContourMeasure
                                         │
                                  (parametric walk along a contour)

   SkMatrix (3x3) ⇄ SkM44 (4x4)    SkRSXform (rot+scale+translate, 4 floats)
                          │
                          ▼
               SkVertices (immutable triangle mesh)

   src/base/SkBezierCurves.h  SkCubics.h  SkQuads.h  (numeric primitives)
   src/core/SkGeometry.h                              (single-precision Bézier helpers)
```

---

## SkScalar

`include/core/SkScalar.h`. `SkScalar` is `float` (Skia abandoned 16.16 fixed
point years ago). Constants and helpers:

- `SK_Scalar1` (1.0f), `SK_ScalarHalf` (0.5f), `SK_ScalarPI`,
  `SK_ScalarSqrt2`, `SK_ScalarMax`, `SK_ScalarMin`, `SK_ScalarInfinity`,
  `SK_ScalarNegativeInfinity`, `SK_ScalarNaN`.
- `SK_ScalarNearlyZero = 1.0f / (1 << 12)`; `SkScalarNearlyEqual(x, y[, tol])`,
  `SkScalarNearlyZero(x[, tol])`.
- Conversions: `SkScalarFromInt(i)`, `SkScalarRoundToInt(s)`,
  `SkScalarFloorToInt(s)`, `SkScalarCeilToInt(s)`, `SkScalarTruncToInt(s)`,
  `SkScalarFraction(s)`, plus `Half`/`Float` versions for half-precision.
- Trig and elementary functions: `SkScalarSin`, `SkScalarCos`,
  `SkScalarTan`, `SkScalarASin`, `SkScalarACos`, `SkScalarATan2`,
  `SkScalarExp`, `SkScalarLog`, `SkScalarSqrt`, `SkScalarPow`.
- Angle helpers: `SkRadiansToDegrees(r)`, `SkDegreesToRadians(d)`.
- `SkScalarAve(a, b)` is `(a + b) * 0.5f`.
- `SkScalarAbs`, `SkScalarSquare`, `SkScalarInvert`.

`SkIntToScalar(i)` is a macro alias for the static cast — pervasive in
older code.

---

## Points and vectors

### SkPoint / SkVector / SkIPoint / SkIVector

`include/core/SkPoint.h` (re-export from `include/private/base/SkPoint_impl.h`).

`SkPoint` is `(SkScalar fX, SkScalar fY)`. `SkVector` is a typedef alias —
both share the same memory layout but the two names communicate intent.
`SkIPoint` and `SkIVector` are integer pairs `(int32_t fX, int32_t fY)`.

Construction:

- `SkPoint{x, y}`, `SkPoint::Make(x, y)`.
- `SkPoint::Length(x, y)` — magnitude of `(x, y)`.
- `SkPoint::Normalize(SkPoint*)` — normalize in place; returns the original
  length, or 0 if the input was too small to normalize.
- `SkPoint::CanNormalize(x, y)`.
- `SkPoint::Distance(a, b)`, `SkPoint::DistanceToSqd(a, b)`.
- `SkPoint::DotProduct(a, b)`, `SkPoint::CrossProduct(a, b)`.

Members and overloads: `x()`, `y()`, `set(x, y)`, `iset(int, int)`,
`isZero()`, `isFinite()`, `length()`, `setLength(...)`, `scale(...)`,
`normalize()`, `cross(v)`, `dot(v)`, `equalsWithinTolerance(...)`, plus
`operator==`, `+=`, `-=`, `*=`, unary `-`, and equality.

### SkPoint3

`include/core/SkPoint3.h`. `(SkScalar fX, SkScalar fY, SkScalar fZ)`. Used
by lighting filters and shadow utilities; not used by the 2-D drawing API
itself.

---

## Rectangles

### SkIRect

Integer rectangle, half-open: `[fLeft, fRight) × [fTop, fBottom)`. Lives at
`include/core/SkRect.h:37` along with `SkRect`.

| Construction | Notes |
|---|---|
| `SkIRect::MakeEmpty()` | All four members zero. |
| `SkIRect::MakeWH(w, h)` | `(0, 0, w, h)`. |
| `SkIRect::MakeSize(SkISize)` | Same. |
| `SkIRect::MakeLTRB(l, t, r, b)` | Direct. |
| `SkIRect::MakeXYWH(x, y, w, h)` | `(x, y, x+w, y+h)`. |

Common queries: `width()`, `height()`, `size()`, `isEmpty()`, `is64()`
(unsigned width/height conversions), `contains(x, y)`, `contains(SkIRect)`,
`containsNoEmptyCheck`, `intersect`, `Intersects(a, b)`,
`IntersectsNoEmptyCheck`, `join(SkIRect)`, `outset(dx, dy)`, `inset(dx, dy)`,
`offset(dx, dy)`, `offsetTo(x, y)`, `sort()`, `makeOffset`,
`makeInset`, `makeOutset`.

### SkRect

Floating-point rectangle, with the same half-open convention. The most
commonly used Skia geometry type.

Construction:

- `SkRect::MakeEmpty()`, `SkRect::MakeLTRB(l,t,r,b)`,
  `SkRect::MakeXYWH(x,y,w,h)`, `SkRect::MakeWH(w,h)`,
  `SkRect::MakeSize(SkSize)`, `SkRect::MakeIWH(w,h)`,
  `SkRect::Make(SkIRect)`, `SkRect::Make(SkISize)`.
- `SkRect::BoundsOrEmpty(SkSpan<const SkPoint>)` — returns a non-empty
  bounds if any point is finite, else empty.

Containment / intersection:

- `contains(x, y)`, `contains(SkRect)`, `contains(SkIRect)`.
- `intersect(SkRect)`, `intersect(SkRect a, SkRect b)` (writes into `*this`),
  `Intersects(a, b)` (free static), `intersectNoEmptyCheck`.
- `join(SkRect)`, `joinNonEmptyArg(SkRect)`, `joinPossiblyEmptyRect`.

Mutation: `setEmpty`, `set(SkIRect)`, `setLTRB`, `setXYWH`, `setWH`,
`setIWH`, `setBounds(SkSpan<const SkPoint>)`, `setBoundsCheck(...)`,
`setBoundsNoCheck(...)`, `sort()`, `roundOut(SkRect*)`, `roundOut(SkIRect*)`,
`roundIn(SkIRect*)`, `roundIn(SkRect*)`, `round(SkIRect*)`, `roundOut(...)`,
`offset(dx, dy)`, `offsetTo(x, y)`, `outset(dx, dy)`, `inset(dx, dy)`.

Conversions: `asScalars()`, `toQuad(SkPoint quad[4])`, `centerX()`,
`centerY()`, `center()`, `width()`, `height()`, `size()`, `isFinite()`,
`isEmpty()`, `isSorted()`.

Arithmetic / chaining: `makeOffset`, `makeInset`, `makeOutset`,
`makeSorted`.

---

## Sizes

`include/core/SkSize.h` defines `SkISize { int32_t fWidth, fHeight }` and
`SkSize { float fWidth, fHeight }`. Both have `Make`, `set`, `isEmpty`,
`isZero`, `width()`, `height()`, comparison operators, and `SkSize::toCeil()`,
`toFloor()`, `toRound()`.

---

## Rounded rectangles

### SkRRect

`include/core/SkRRect.h`. A rectangle with eight scalar corner radii (X and Y
radius for each of the four corners). The `Type` enum is the canonical way to
reason about how complex the shape is:

| `SkRRect::Type` | Meaning |
|---|---|
| `kEmpty_Type` | Zero width or height. |
| `kRect_Type` | Non-zero size, all radii zero (i.e. a rectangle). |
| `kOval_Type` | Radii fill the bounds completely (i.e. an oval). |
| `kSimple_Type` | All four corners share one (rx, ry) pair. |
| `kNinePatch_Type` | Axis-aligned: top-left.x = top-right.x, top-left.y = bottom-left.y, etc. |
| `kComplex_Type` | Truly arbitrary radii. |

Factories: `MakeEmpty`, `MakeRect`, `MakeOval`, `MakeRectXY(rect, rx, ry)`,
`MakeFromRadii(rect, SkVector radii[4])`, `MakeNinePatch(rect, leftRad,
topRad, rightRad, bottomRad)`. The `setRectXY` / `setNinePatch` /
`setRectRadii` / `setOval` / `setRect` methods are mutation-form equivalents.

Queries: `getType()`, `rect()`, `getBounds()`, `width()`, `height()`,
`getSimpleRadii()`, `radii(Corner)` for `kUpperLeft_Corner`,
`kUpperRight_Corner`, `kLowerRight_Corner`, `kLowerLeft_Corner`. `isEmpty()`,
`isRect()`, `isOval()`, `isSimple()`, `isNinePatch()`, `isComplex()`.

Geometry: `contains(SkRect)`, `transform(SkMatrix, SkRRect* out)` (returns
false if the matrix would produce a non-`SkRRect` shape), `inset`, `outset`,
`offset`, `makeOffset`. `radii(corner)` returns the corner's `(rx, ry)`.

`writeToMemory` / `readFromMemory` provide a 12-float (`kSizeInMemory`)
serialization.

### SkRoundRect

There is no `SkRoundRect` distinct from `SkRRect` — the term is sometimes
used informally for "rectangle with corner radii". `SkCanvas::drawRoundRect`
takes a `SkRect` plus a single `(rx, ry)` and constructs the simple `SkRRect`
internally.

---

## Regions

### SkRegion

`include/core/SkRegion.h`. Pixel-aligned region — a set of integer-bounded
rectangles describing the union of one or more areas. Used internally by
`SkCanvas::clipRegion` and the AA-clip code, and externally by anything that
needs to compose pixel-quantized clips.

Construction: default empty, `SkRegion(SkIRect)`, `SkRegion(const SkRegion&)`,
copy/move semantics. `setEmpty`, `setRect`, `setRects`, `setRegion`,
`setPath(path, clip)` (rasterizes a path, intersected with a clip region).

Queries: `isEmpty()`, `isRect()`, `isComplex()`, `getBounds()`,
`computeRegionComplexity()`, `getBoundaryPath(SkPath*)`,
`contains(int32_t x, int32_t y)`, `contains(SkIRect)`, `contains(SkRegion)`,
`intersects(SkIRect)`, `intersects(SkRegion)`, `quickContains(SkIRect)`,
`quickReject(SkIRect)`, `quickReject(SkRegion)`.

Boolean operations via `op(...)` / `Op(a, b, op, dst)`:

```
enum Op { kDifference_Op, kIntersect_Op, kUnion_Op, kXOR_Op,
          kReverseDifference_Op, kReplace_Op };
```

Translation: `translate(dx, dy)` (in-place), `translate(dx, dy, *dst)`.

Iterators (all in `SkRegion`):

- `Iterator` — walks all rectangles in scan order. `done()`, `next()`,
  `rect()`, `rgn()`, `rewind()`, `reset(region)`.
- `Cliperator` — walks the rectangles intersecting a given `SkIRect` clip.
- `Spanerator` — walks the horizontal spans on a given `y`, between `left`
  and `right`. Used by `SkScanClipper`.

---

## Paths

### SkPath

`include/core/SkPath.h`. A `SkPath` is a sequence of contours, each being a
`Move` followed by zero or more `Line` / `Quad` / `Conic` / `Cubic` segments
and an optional `Close`. Internally it shares a copy-on-write `SkPathRef`,
so copying a path is constant-time and never allocates; modifications detach
into a new ref.

#### Path verbs

`include/core/SkPathTypes.h`:

| Verb | Points consumed | Conic weights consumed |
|---|---:|---:|
| `kMove` | 1 | 0 |
| `kLine` | 1 | 0 |
| `kQuad` | 2 | 0 |
| `kConic` | 2 | 1 |
| `kCubic` | 3 | 0 |
| `kClose` | 0 | 0 |

`SkPathFillType` is the rule used to determine inside/outside:

| Value | Rule |
|---|---|
| `kWinding` | Non-zero winding number is "inside". |
| `kEvenOdd` | Odd number of edge crossings is "inside". |
| `kInverseWinding` / `kInverseEvenOdd` | Same, but draw the *outside*. |

`SkPathDirection`: `kCW` or `kCCW`. `SkPathSegmentMask` bit flags
indicate which kinds of segments a path actually contains.

#### Construction

- `SkPath()` — empty, default fill type.
- `SkPath(SkPathFillType)` — empty, given fill type.
- `SkPath::Raw(SkSpan<const SkPoint>, SkSpan<const SkPathVerb>,
  SkSpan<const SkScalar>, SkPathFillType, bool isVolatile=false)` —
  build directly from raw arrays. Returns an empty path on illegal verb
  sequences.
- Geometric constructors:
  - `SkPath::Rect(SkRect[, fillType, dir, startIndex])`.
  - `SkPath::Oval(SkRect[, dir[, startIndex]])`.
  - `SkPath::Circle(cx, cy, r[, dir])`.
  - `SkPath::RRect(SkRRect[, dir[, startIndex]])` — and a `(SkRect, rx, ry,
    dir)` overload.
  - `SkPath::Polygon(SkSpan<const SkPoint>, isClosed[, fillType, isVolatile])`.
  - `SkPath::Line(a, b)`.
- `SkPath::Make(...)` — deprecated alias for `Raw` taking `uint8_t` verbs.

#### Querying

- `getFillType()` / `setFillType()`, `isInverseFillType()`,
  `toggleInverseFillType()`.
- `countVerbs()`, `countPoints()`, `countConicWeights()`,
  `getVerbs(SkSpan<uint8_t>)`, `getPoints(SkSpan<SkPoint>)`,
  `getConicWeights(SkSpan<SkScalar>)`.
- `getBounds()` — cached conservative bounds (uses control points).
- `computeTightBounds()` — recomputes tight bounds (X/Y extents of curves
  themselves, not control points).
- `getSegmentMasks()` — bit mask of `SkPathSegmentMask` values.
- `getGenerationID()` — non-zero, unique to the underlying `SkPathRef`.
- Shape probes: `isLine(SkPoint[2])`, `isRect(SkRect*[, isClosed,
  direction])`, `isOval(SkRect*)`, `isRRect(SkRRect*)`, `isArc(SkArc*)`.
- `isConvex()` — fast cached probe.
- `isFinite()` — every coordinate is finite.
- `isEmpty()`, `isLastContourClosed()`.
- `contains(x, y)` — winding-rule containment query.
- `interpolate(other, t, *out)` and `makeInterpolate(other, t)` — linearly
  blend two paths that have the same verb sequence and conic weights
  (`isInterpolatable(other)` checks).

#### Mutation

`SkPath` has the full set of mutation methods (`moveTo`, `lineTo`, `quadTo`,
`conicTo`, `cubicTo`, `arcTo`, `rArcTo`, `close`, `addRect`, `addOval`,
`addCircle`, `addRRect`, `addPath`, `reverseAddPath`, `addPoly`,
`addArc`, `incReserve`, `transform`, `offset`, `reset`, `rewind`,
`shrinkToFit`, `setIsVolatile`, `swap`). However the recommended pattern in
new code is to build through `SkPathBuilder` and call `detach()` once.

`isVolatile` is a hint: volatile paths skip GPU caching/tessellation
caches.

#### Iterators

| Iterator | Returns |
|---|---|
| `SkPath::Iter` | High-level: emits `kDone_Verb` at end; auto-injects synthetic moves for stroke generation; can return `kClose_Verb` and the final close point. |
| `SkPath::RawIter` | Low-level: walks the underlying buffers directly. |
| `SkPathIter` (`include/core/SkPathIter.h`) | Modern alternative — a range-for-iterable view yielding `SkPathSegment { verb, points, weight }`. Replaces the older nested `Iter`. |

#### Volatility, ownership, sharing

- Multiple `SkPath` instances can share an `SkPathRef` until one is mutated;
  on first mutation, `writable_ref` clones the path data into a new ref.
- `SkPath::snapshot()` is just `*this` (cheap copy).
- `SkPathData` (`include/private/SkPathRef.h`-adjacent `SkPathData.h`) is the
  refcounted storage; `SkPathBuilder` can produce one directly via
  `snapshotData()` / `detachData()`.

### SkPathBuilder

`include/core/SkPathBuilder.h`. The recommended way to build paths.

Storage: `STArray<4, SkPoint>`, `STArray<4, SkPathVerb>`, `STArray<2, float>`
inline-stored verbs/points/weights. Construct empty, with a `SkPathFillType`,
or as a copy of an existing `SkPath` (which replays its verbs).

| Method | Notes |
|---|---|
| `moveTo(p)` | Each contour can have only one `Move` — calling `moveTo` twice replaces the previous move's point. |
| `lineTo(p)`, `quadTo(c, p)`, `conicTo(c, p, w)`, `cubicTo(c1, c2, p)` | Standard segment appends. |
| `rMoveTo`, `rLineTo`, `rQuadTo`, `rConicTo`, `rCubicTo` | "relative" — coordinates are deltas from the previous point. |
| `arcTo(...)`, `rArcTo(...)` | Several overloads: through-tangent point, SVG-style (rx, ry, xAxisRotate, large, sweep, end), and oval-center forms. |
| `close()` | Append a `Close` if the current contour has any segments. |
| `addRect / addOval / addCircle / addRRect / addPolygon / addPath / reverseAddPath` | Whole-shape adds. |
| `incReserve(extraPts, extraVerbs, extraConics)` | Pre-grow the storage. |
| `setFillType(ft)`, `fillType()` | Set/get the fill rule. |
| `setIsVolatile(true)` | Mark as volatile (no path cache). |
| `computeFiniteBounds()` / `computeTightBounds()` | `std::optional<SkRect>` — empty optional if any point was non-finite. |
| `snapshot([SkMatrix*])` | Return a `SkPath` without disturbing the builder. The matrix, if present, transforms the points as they are copied. |
| `detach([SkMatrix*])` | Same, but clears the builder. |
| `snapshotData()` / `detachData()` | Same forms returning `sk_sp<SkPathData>`. |
| `reset()` | Empty-out the builder, keeping its allocations. |

Builders also implement `operator==` and copy/move semantics.

### Path utilities

`include/core/SkPathUtils.h` provides free helpers — most importantly
`SkPathPriv::CreateDrawArcPath(SkPath*, SkArc, ...)` and `SkPath::ConvertToNonInverseFillType`.

`include/core/SkParsePath.h` (in `include/utils/`) parses and emits SVG path
strings: `SkParsePath::FromSVGString`, `SkParsePath::ToSVGString`.

For path operations (boolean ops, simplification, stroke-to-fill conversion),
see [path-operations.md](path-operations.md).

For path effects (dashing, corner rounding, trimming), see
[path-effects.md](path-effects.md).

### SkPathMeasure / SkContourMeasure

`include/core/SkPathMeasure.h` and `include/core/SkContourMeasure.h`. Walk
along a path and read off positions, tangents, and arc lengths.

`SkContourMeasure` (refcounted) holds the parametric measurement of a single
contour. The `SkContourMeasureIter` produces one measure per contour:

```c++
SkContourMeasureIter iter(path, /*forceClosed=*/false, resScale);
while (sk_sp<SkContourMeasure> m = iter.next()) {
    SkScalar length = m->length();
    SkPoint pos; SkVector tan;
    m->getPosTan(length * 0.5f, &pos, &tan);   // midpoint pose
    sk_sp<SkPath> sub = ...;
    m->getSegment(0, length, sub.get(), /*startWithMoveTo=*/true);
}
```

`SkPathMeasure` is the older single-contour wrapper (effectively an
`SkContourMeasureIter` plus the current `SkContourMeasure`). `nextContour()`
advances to the next contour; `length()`, `getPosTan(distance, *pos, *tan)`,
`getMatrix(distance, *m, MatrixFlags)`, `getSegment(start, stop, *dst,
startWithMoveTo)`, `isClosed()`.

These are how Skia implements text-on-path rendering and the
`SkPath1DPathEffect` family.

### SkCubicMap

`include/core/SkCubicMap.h`. A 1-D parametric mapping from `[0, 1]` to
`[0, 1]` defined by two control points (think CSS `cubic-bezier(x1, y1, x2,
y2)`). Used by easing and animation. Methods: `computePosition(x)`,
`computeYFromX(x)`, `computeFromT(t)`. The construction validates that the
control X coordinates lie in `[0, 1]`.

---

## Matrices

### SkMatrix (3×3)

`include/core/SkMatrix.h`. The standard 2-D affine + perspective transform
used by `SkCanvas`, `SkShader`, and most of the public API.

```
| scaleX  skewX   transX |
| skewY   scaleY  transY |
| pers0   pers1   pers2  |
```

Storage is row-major; element indices `kMScaleX`, `kMSkewX`, `kMTransX`,
`kMSkewY`, `kMScaleY`, `kMTransY`, `kMPersp0`, `kMPersp1`, `kMPersp2`.

Type classification (`TypeMask`, conservative) caches what the matrix can
do:

| Bit | Meaning |
|---|---|
| `kIdentity_Mask = 0` | Pure identity. |
| `kTranslate_Mask` | Has translation. |
| `kScale_Mask` | Has non-1 scale. |
| `kAffine_Mask` | Has skew or rotation. |
| `kPerspective_Mask` | Has perspective row. |

Factories: `SkMatrix()` (identity), `SkMatrix::Scale(sx, sy)`,
`SkMatrix::Translate(dx, dy)` / `Translate(SkVector)` / `Translate(SkIVector)`,
`SkMatrix::ScaleTranslate(sx, sy, tx, ty)`, `SkMatrix::RotateDeg(deg[, pt])`,
`SkMatrix::RotateRad(rad)`, `SkMatrix::Skew(kx, ky)`, `SkMatrix::MakeAll(9
floats)`, `SkMatrix::I()` (identity singleton),
`SkMatrix::InvalidMatrix()`,
`SkMatrix::RectToRectOrIdentity(src, dst, ScaleToFit)`,
`SkMatrix::MakeRectToRect(src, dst, ScaleToFit)`,
`SkMatrix::Concat(a, b)`.

`ScaleToFit`: `kFill_ScaleToFit`, `kStart_ScaleToFit`, `kCenter_ScaleToFit`,
`kEnd_ScaleToFit`.

Queries:
- Per-element accessors: `getScaleX/Y`, `getSkewX/Y`, `getTranslateX/Y`,
  `getPerspX/Y`, plus indexed `get(i)` and `rc(r, c)`.
- Properties: `isIdentity`, `isTranslate`, `isScaleTranslate`,
  `rectStaysRect`, `preservesAxisAlignment`, `isSimilarity(tol)`,
  `preservesRightAngles(tol)`, `hasPerspective`.
- Decomposition: `decomposeScale(SkSize*[, SkMatrix*])`.

Mutation: `set9(const SkScalar[9])`, `set(...)`, `setAll(...)`,
`setIdentity()`, `setScale`, `setRotate`, `setSkew`, `setTranslate`,
`setSinCos`, `setRSXform(SkRSXform)`, `setConcat`, `setRectToRect`,
`setPolyToPoly(SkSpan<const SkPoint> src, SkSpan<const SkPoint> dst)` (1–4
points), `preTranslate`, `preScale`, `preRotate`, `preSkew`, `preConcat`,
`postTranslate`, `postScale`, `postRotate`, `postSkew`, `postConcat`,
`reset()`, `normalizePerspective()`.

Inversion: `invert(SkMatrix* dst)`. For non-invertible matrices, returns
false and leaves `*dst` untouched.

Mapping:
- `mapPoints(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src)` — and
  in-place `mapPoints(SkSpan<SkPoint>)`.
- `mapPoint(SkPoint)` — single-point return.
- `mapPointAffine(SkPoint)` — assumes no perspective; faster.
- `mapOrigin()` — equivalent to `mapPoint({0, 0})`.
- `mapVectors(...)`, `mapVector(...)` — vectors ignore translation.
- `mapHomogeneousPoints(SkSpan<SkPoint3> dst, SkSpan<const SkPoint3> src)`,
  `mapPointsToHomogeneous(SkSpan<SkPoint3>, SkSpan<const SkPoint>)`.
- `mapRect(SkRect* dst, const SkRect& src)` returns true if the result is
  axis-aligned; `mapRect(const SkRect&)` returns the bounding rect of the
  transformed quad.
- `mapRadius(SkScalar)`, `mapHomogeneousPoints`.

`SkMatrix` is *not* thread-safe unless `getType()` has been called first
(the lazy type-cache is the only mutable state).

### SkM44 (4×4)

`include/core/SkM44.h`. Used by `SkCanvas` for 3-D-aware draws (perspective
shadows, layer transforms, hardware compositors). Storage is column-major
in memory but the parameterized constructors accept row-major arguments.

Helper value types:

- `SkV2 { float x, y }` — 2-vector with `length`, `dot`, `+/-/*`.
- `SkV3 { float x, y, z }` — 3-vector with `length`, `dot`, `cross`,
  `normalize`, `Cross(a, b)`.
- `SkV4 { float x, y, z, w }` — 4-vector with `length`, `dot`, `Normalize`.

Factories:

- `SkM44()` — identity.
- `SkM44(NaN_Constructor)` — fills with NaN; useful for catching uninit use.
- `SkM44(Uninitialized_Constructor)` — leaves memory uninitialized.
- 16-arg row-major constructor.
- `SkM44::Rows(SkV4, SkV4, SkV4, SkV4)`, `SkM44::Cols(...)`,
  `SkM44::RowMajor(const float[16])`, `SkM44::ColMajor(const float[16])`.
- `SkM44::Translate(x, y[, z=0])`, `SkM44::Scale(x, y[, z=1])`,
  `SkM44::Rotate(SkV3 axis, radians)`,
  `SkM44::RectToRect(src, dst)` (2-D scale+translate),
  `SkM44::LookAt(eye, center, up)`, `SkM44::Perspective(near, far, angle)`.

Queries: `rc(r, c)`, `row(i)`, `col(i)`, `setRow`, `setCol`, `getColMajor`,
`getRowMajor`, `setIdentity`, `asM33()` (drop Z and W), `setConcat`,
`postConcat`, `preConcat`, `invert(SkM44*)`, `transpose`,
`mapPoint(SkPoint)`, `map(x, y, z, w)`, `map(SkV4)`.

The right-handed coordinate convention used by `SkM44` is: +X right, +Y down
(matching 2-D Skia), +Z into the screen.

`SkCanvas::concat(SkM44)` and `setMatrix(SkM44)` are the entry points; the
3×3 path goes through `SkMatrix` and is widened to `SkM44` internally.

### SkRSXform

`include/core/SkRSXform.h`. A compressed rotation-scale-translation matrix
stored as four floats `(fSCos, fSSin, fTx, fTy)`:

```
| fSCos  -fSSin  fTx |
| fSSin   fSCos  fTy |
|     0       0    1 |
```

Used for `SkCanvas::drawAtlas` (per-sprite transforms) and
`SkCanvas::drawGlyphsRSXform` (per-glyph rotations). Helpers:

- `SkRSXform::Make(scos, ssin, tx, ty)`.
- `SkRSXform::MakeFromRadians(scale, radians, tx, ty, ax, ay)` — anchor-point
  form; `(ax, ay)` is the pivot inside the source quad.
- `setIdentity`, `set(scos, ssin, tx, ty)`, `rectStaysRect()`.
- `toQuad(width, height, SkPoint quad[4])` — write the four transformed
  corners.
- `toTriStrip(w, h, SkPoint strip[4])` — write four corners in tri-strip
  order.

---

## Triangle meshes

### SkVertices

`include/core/SkVertices.h`. Immutable, `SkNVRefCnt`-backed triangle mesh.
Used by `SkCanvas::drawVertices`.

Vertex modes: `kTriangles_VertexMode`, `kTriangleStrip_VertexMode`,
`kTriangleFan_VertexMode`.

Construction by copy:

```c++
sk_sp<SkVertices> v = SkVertices::MakeCopy(
    mode, vertexCount,
    positions,                       // [vertexCount]
    /*texs=*/nullptr,                // [vertexCount] or nullptr
    /*colors=*/nullptr,              // [vertexCount] or nullptr
    indexCount,
    /*indices=*/nullptr);            // [indexCount] or nullptr
```

A `SkVertices::Builder` allows in-place population without an extra copy:

```c++
SkVertices::Builder b(mode, vertexCount, indexCount,
                      SkVertices::kHasTexCoords_BuilderFlag |
                      SkVertices::kHasColors_BuilderFlag);
SkPoint*  pos    = b.positions();
SkPoint*  texs   = b.texCoords();
SkColor*  colors = b.colors();
uint16_t* idx    = b.indices();
... fill them in ...
sk_sp<SkVertices> v = b.detach();
```

Queries: `uniqueID()`, `bounds()`, `approximateSize()`. Internally
`SkVertices` allocates one block sized to fit the positions, optional
indices, optional tex coords, and optional colors; deletion uses a custom
`operator delete` because of the manual sizing.

The `SkBlendMode` argument to `SkCanvas::drawVertices` is ignored if the
mesh has no colors; otherwise it combines the mesh colors with the paint's
shader (or its solid color, if no shader). `SkMaskFilter`, `SkPathEffect`,
and antialiasing are ignored.

`drawVertices` interprets shader sampling via `texCoords()` if present;
otherwise it samples by position. For a fully programmable equivalent see
`SkCanvas::drawMesh` and `SkMesh` (covered under
[runtime-effects.md](runtime-effects.md)).

---

## Bézier and polynomial helpers

### Single-precision: src/core/SkGeometry.h

`SkGeometry.h` is the project-wide single-precision Bézier toolbox used by
the rasterizer and the GPU tessellator.

Highlights (function names):

- `SkChopQuadAt`, `SkChopQuadAtHalf`, `SkChopQuadAtMaxCurvature`,
  `SkFindQuadExtrema`, `SkEvalQuadAt`, `SkEvalQuadTangentAt`.
- `SkChopCubicAt`, `SkChopCubicAtHalf`, `SkChopCubicAtMaxCurvature`,
  `SkFindCubicExtrema`, `SkEvalCubicAt`, `SkChopCubicAtInflections`,
  `SkConvertCubicToQuads`.
- `SkClassifyCubic` returns `SkCubicType` (`kSerpentine`, `kLoop`, `kLocalCusp`,
  `kCuspAtInfinity`, `kQuadratic`, `kLineOrPoint`).
- `SkConic` — quadratic-rational helper with `evalAt`, `chopAt`,
  `computeAsQuadError`, `asQuadTol`, `BuildUnitArc`.
- `SkConvertConicToQuads` — approximate a conic with a power-of-two number
  of quadratics.
- `SkBuildQuadArc`, `SkConicSectionToQuads`.

These are all 32-bit-float and used in performance-critical paths (the
rasterizer, the GPU draw path).

### Double-precision: src/base/SkBezierCurves.h

`SkBezierCubic` (eight-double cubic) and `SkBezierQuad`. These power the
"slow but accurate" code paths — bounds intersection, path-op clipping,
coincidence checks. Selected entry points:

- `SkBezierCubic::EvalAt(const double curve[8], double t)` returns
  `std::array<double, 2>`.
- `SkBezierCubic::Subdivide(const double curve[8], double t,
   double twoCurves[14])` — De Casteljau split, sharing the midpoint at
   indices 6 and 7.
- `SkBezierCubic::ConvertToPolynomial(...)` — turn `(P0, P1, P2, P3)` into
  `f(t) = A t^3 + B t^2 + C t + D`.

### Polynomial root finders: src/base/SkCubics.h, SkQuads.h

`SkCubics::RootsReal(A, B, C, D, double[3])` — up to three real roots of
`A t^3 + B t^2 + C t + D = 0`.

`SkCubics::RootsValidT(A, B, C, D, double[3])` — same restricted to
`t ∈ [0, 1]`.

`SkCubics::BinarySearchRootsValidT(A, B, C, D, double[3])` — slower but
more accurate when floating-point cancellation is severe.

`SkCubics::EvalAt(A, B, C, D, t)` — Horner-form `fma`-accelerated evaluation.

`SkQuads::Discriminant(A, B, C)` and `SkQuads::Roots(A, B, C)` use Kahan's
"On the Cost of Floating-Point Computation Without Extra-Precise Arithmetic"
formulation; the quadratic is parameterized as `A t^2 - 2 B t + C = 0` to
reduce rounding error (callers convert by passing `B/-2`).

### Misc

- `src/base/SkChecksum.h` — CRC-style hashing utilities for path keys.
- `src/base/SkHalf.h` — IEEE 754 half-float ↔ float conversions.
- `src/base/SkVx.h` — `skvx::Vec<N, T>` portable SIMD wrapper used in the
  raster pipeline and by the curve evaluators.

---

## Cross-references

- [`SkCanvas`](canvas-and-recording.md) — every public mention of geometry
  here is consumed by a draw call.
- [`SkPaint`](paint-color-and-blending.md) — strokes, joins, miter limits
  interact with path geometry.
- [Path effects](path-effects.md) and [path operations](path-operations.md)
  — path-specific transforms and boolean operations.
- [`SkColorSpace`](color-management.md) — orthogonal to geometry, but every
  draw threads a color space through.
- [`SkShader`](shaders.md), [`SkSurface`](surface-and-output.md) — geometry
  produces coverage; shaders produce color; surfaces consume both.
