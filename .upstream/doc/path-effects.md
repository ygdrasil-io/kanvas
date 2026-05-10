# Path Effects

`SkPathEffect` is the *geometry* stage of an `SkPaint`: it rewrites the
path or stroke that the rasteriser will scan-convert. Unlike
[Shaders](shaders.md) (which produce per-pixel color) and
[Image Filters](image-filters-and-mask-filters.md) (which transform
already-rasterised images), a path effect operates on `SkPath` data
*before* a single pixel is touched. The output of a path effect is fed
straight back into the raster path as if the user had drawn it directly.

Concrete path effects in Skia fall into a small handful of categories:

- **Stroke decorators** — `SkDashPathEffect`, `SkCornerPathEffect`,
  `SkDiscretePathEffect`. These rewrite contours produced by stroking,
  typically without changing the stroke style.
- **Path-along-path** — `SkPath1DPathEffect` (replicate a small path along
  a contour) and the 2D variants `SkLine2DPathEffect` /
  `SkPath2DPathEffect` (replicate over a 2D lattice intersected with the
  path's interior).
- **Sub-path extraction** — `SkTrimPathEffect` returns a `[startT, stopT]`
  slice of the contour, used heavily for path-animation effects.
- **Composition** — `SkPathEffect::MakeSum` (concatenation) and
  `MakeCompose` (`outer(inner(path))`), implemented by the internal
  `SkSumPathEffect` and `SkComposePathEffect`.

Path effects are immutable, ref-counted, flattenable
(`SkFlattenable`-derived), and may use the CTM if they declare so via
`onNeedsCTM`. They never own a thread-mutable cache; identical paths
produce identical results.

## Pipeline at a glance

```
                     ┌──────────────┐
   SkPath ─────────► │ SkPathEffect │  filterPath(builder, src, &rec, cull, ctm)
                     │ (immutable)  │
                     └──────┬───────┘
                            │ rewrites geometry; may
                            │ change SkStrokeRec (e.g.
                            │ SkPath1DPathEffect → fill)
                            ▼
                     ┌──────────────┐
                     │ SkStrokeRec  │  if still stroked, the rasteriser
                     │ (style+width)│  asks SkStroke to convert stroke→fill
                     └──────┬───────┘
                            │
                            ▼
                     ┌──────────────┐
                     │ rasteriser   │  scan-convert the now-final path
                     └──────────────┘
```

Path effects are invoked from `skcpu::Draw` / GPU equivalents. The
`SkStrokeRec` argument is *both* an input and an output: a `1D` path
effect, for example, rewrites a stroked outline into a filled
ribbon-of-stamps and forces the rec to fill style.

## Public API surface

| Header | Purpose |
|---|---|
| `include/core/SkPathEffect.h` | Base + `MakeSum` / `MakeCompose` composition factories. |
| `include/core/SkStrokeRec.h` | The mutable stroke descriptor (style, width, cap, join, miter, res-scale) passed through `filterPath`. |
| `include/effects/SkDashPathEffect.h` | `SkDashPathEffect::Make(intervals, phase)` — stroked dashed lines. |
| `include/effects/SkCornerPathEffect.h` | `SkCornerPathEffect::Make(radius)` — replace sharp corners with quad arcs. |
| `include/effects/SkDiscretePathEffect.h` | `SkDiscretePathEffect::Make(segLength, deviation, seedAssist)` — chop into segments and randomly displace endpoints. |
| `include/effects/SkTrimPathEffect.h` | `SkTrimPathEffect::Make(startT, stopT, Mode)` — extract a (`Normal` or `Inverted`) sub-arc. |
| `include/effects/Sk1DPathEffect.h` | `SkPath1DPathEffect::Make(path, advance, phase, Style)` — translate / rotate / morph a stamp along the contour. |
| `include/effects/Sk2DPathEffect.h` | `SkLine2DPathEffect::Make(width, matrix)` — strokes that follow a 2D lattice; `SkPath2DPathEffect::Make(matrix, path)` — stamp at every lattice cell intersected by the input path. |

## SkPathEffect — `include/core/SkPathEffect.h`

The public API is intentionally narrow:

```cpp
bool filterPath(SkPathBuilder* dst, const SkPath& src,
                SkStrokeRec*, const SkRect* cullR,
                const SkMatrix& ctm) const;
bool filterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*) const;
bool needsCTM() const;

static sk_sp<SkPathEffect> MakeSum    (sk_sp<SkPathEffect> first, sk_sp<SkPathEffect> second);
static sk_sp<SkPathEffect> MakeCompose(sk_sp<SkPathEffect> outer, sk_sp<SkPathEffect> inner);
```

`filterPath` returns true if the effect produced a meaningful output. The
return value lets callers know whether to use `dst` or fall back to
`src`. The `cullR` rect is in *local* space (post-CTM-inverse if the
caller knows the CTM). `MakeSum` returns `first(path) + second(path)`,
i.e. both effects' outputs concatenated. `MakeCompose` returns
`outer(inner(path))`, i.e. pipelined.

`needsCTM()` answers "do I read the CTM in `filterPath`?" — used by
`SkCanvas` to decide whether to flatten the CTM into the path before
calling the effect (which preserves arbitrary backends that don't carry a
CTM into rasterisation).

## SkPathEffectBase — `src/core/SkPathEffectBase.h`

Concrete subclasses extend `SkPathEffectBase`, which adds:

```cpp
virtual bool onFilterPath(SkPathBuilder* dst, const SkPath& src,
                          SkStrokeRec*, const SkRect* cullR,
                          const SkMatrix& ctm) const = 0;

virtual bool onNeedsCTM()   const { return false; }
virtual bool computeFastBounds(SkRect*) const = 0;
virtual std::optional<DashInfo> asADash() const { return {}; }
virtual bool onAsPoints(PointData*, const SkPath& src,
                        const SkStrokeRec&, const SkMatrix&,
                        const SkRect* cullR) const { return false; }
```

`computeFastBounds` rewrites a conservative bounding rectangle for the
output (or returns false if no fast bound can be computed). `asADash`
declares whether the effect can be expressed as a dash pattern — the
GPU stroke tessellator uses this to take a faster path. `onAsPoints` is
the legacy entry point that lets dash effects collapse a series of dots
into a `PointData` containing centre points + radii rather than a real
stroked path; `kCircles_PointFlag` / `kUsePath_PointFlag` /
`kUseClip_PointFlag` describe the resulting representation.

The free function `as_PEB(SkPathEffect*)` is the standard down-cast,
mirroring `as_SB`/`as_MFB` from the shader and mask-filter hierarchies.

## Composition — `SkComposePathEffect` and `SkSumPathEffect`

Both live in `src/core/SkPathEffect.cpp` as private subclasses of an
internal `SkPairPathEffect`. They are constructed via
`SkPathEffect::MakeCompose` / `MakeSum`:

- **Compose** (`outer(inner(path))`) — `inner.filterPath` is called
  first, the result is moved into a new `SkPath`, then
  `outer.filterPath` consumes it. If either effect declines to filter,
  the source is forwarded through untouched.
- **Sum** (`first(path) + second(path)`) — both effects are called on
  the original input and their outputs are concatenated into the
  destination builder. `filterPath` returns true if at least one effect
  produced output.

Both factories collapse identity: `MakeSum(nullptr, x) == x`,
`MakeCompose(outer, nullptr) == outer`, etc. Both register a
flattenable type in `SkPathEffectBase::RegisterFlattenables` so they
serialise transparently.

## SkDashPathEffect — `include/effects/SkDashPathEffect.h` + `src/effects/SkDashPathEffect.cpp` + `src/effects/SkDashImpl.h`

`SkDashPathEffect::Make(SkSpan<const SkScalar> intervals, SkScalar phase)`
returns an effect that emits alternating on/off intervals along the
stroked path. The intervals must be of even count and ≥ 2 entries; the
even indices are "on" lengths, the odd indices are "off". `phase` is the
offset into the cyclic interval pattern.

Internally `SkDashImpl` precomputes the cyclic interval data
(`fInitialDashLength`, `fInitialDashIndex`, `fIntervalLength`, `fPhase`)
in its constructor so each `onFilterPath` is amortised. Filtering goes
through `SkDashPath::InternalFilter` (`src/utils/SkDashPathPriv.h`),
which:

1. Optionally culls horizontal/vertical line segments to the visible
   region (`cull_line` in the cpp file) — this avoids generating millions
   of tiny dashes for long off-screen lines while keeping the dash
   pattern in phase.
2. Walks each contour with `SkPathMeasure`, emitting line segments or
   short curves for the "on" intervals.

The dash effect declines to filter if it would produce no segments
(e.g. all "off"); `asADash` returns the original interval array, which
lets the GPU stroke pipeline render dashes without first materialising
the path. `asPoints` lets a series of dots collapse into a `PointData`
list of circle/square centres so the rasteriser can render them as
points instead of a tiny stroked path.

> Dashing only affects stroked paths; on filled paths the effect is
> typically a no-op (the rasteriser fills the original geometry).

## SkCornerPathEffect — `include/effects/SkCornerPathEffect.h` + `src/effects/SkCornerPathEffect.cpp`

`SkCornerPathEffect::Make(radius)` rounds the corners of a polygonal
path by replacing each sharp corner with a quadratic Bézier arc of
`radius` units of leg-shortening. Internally `SkCornerPathEffectImpl`
walks the path verb-by-verb:

- For each `kLine` segment it computes a step vector (`ComputeStep`)
  of length `radius` toward the next vertex, replacing the corner with
  a quad whose control point is the original corner.
- Quad / conic / cubic verbs are emitted unchanged (the corner radius
  doesn't apply to curves) but they reset the `firstStep` so closing
  works out.
- `kMove` and `kClose` reseed the contour state. If the contour is
  closed and the first segment was a line, the start vertex receives
  a closing arc as well.

`computeFastBounds` returns the input bounds unchanged — rounding a
corner only ever shrinks toward the existing geometry. The radius is
internally clamped so legs shorter than `2*radius` are bisected; the
arcs degrade gracefully on dense polygons.

## SkDiscretePathEffect — `include/effects/SkDiscretePathEffect.h` + `src/effects/SkDiscretePathEffect.cpp`

`SkDiscretePathEffect::Make(segLength, deviation, seedAssist=0)` chops
a path into `segLength`-long sub-segments and randomly perturbs the
endpoints by up to `deviation` along the path normal. Useful for
sketching / wiggle effects.

The implementation walks the path with `SkPathMeasure` and at each
position computes a perturbation `Perterb(point, tangent, scale)` where
`scale` is drawn from a custom `LCGRandom` (a fast 32-bit linear
congruential generator that matches the historical `SkRandom`). The
generator's seed combines the input parameters with the `seedAssist`,
so passing different `seedAssist` values produces different visually-
distinct perturbations of the same path. This is documented as a
testing convenience: the default `seedAssist=0` makes repeated
filtering deterministic.

`onAsPoints` and `asADash` both return false; `computeFastBounds`
inflates the input by `deviation` in both axes.

## SkTrimPathEffect — `include/effects/SkTrimPathEffect.h` + `src/effects/SkTrimPathEffect.cpp` + `src/effects/SkTrimPE.h`

`SkTrimPathEffect::Make(startT, stopT, Mode)` returns an effect that
extracts the sub-arc `[startT, stopT]` (where `startT, stopT ∈ [0, 1]`
of the *total path length*, summed across all contours).

The two modes:

- `Mode::kNormal` — return only the sub-arc.
- `Mode::kInverted` — return the complement: `[stopT, 1] + [0, startT]`.

The factory pins out-of-range Ts to [0, 1], rejects NaN, and rejects
degenerate `Normal` requests for the entire path. `kInverted` requires
`startT < stopT` (otherwise the result would be empty).

The implementation does two passes with `SkPathMeasure`: the first
sums total length so `startT * len` and `stopT * len` can be expressed
as arc-distances; the second walks each contour and uses
`SkPathMeasure::getSegment` to extract the slices. The inverted-mode
path takes special care to preserve closed-contour continuity by
emitting the tail span first and skipping a duplicate `moveTo` if the
input was a single closed contour.

This effect is the building block for many path animations: animating
`stopT` from 0 to 1 makes a path appear as if drawn over time.

## SkPath1DPathEffect — `include/effects/Sk1DPathEffect.h` + `src/effects/Sk1DPathEffect.cpp`

`SkPath1DPathEffect::Make(path, advance, phase, Style)` replicates a
small `path` (the "stamp") at intervals of `advance` units along each
contour of the input. `phase` is the offset along the path for the
first stamp.

`Style` controls how each stamp is positioned:

| Style | Effect |
|---|---|
| `kTranslate_Style` | Each stamp is translated to the contour position (no rotation). |
| `kRotate_Style`    | Each stamp is rotated by the path tangent at its position. |
| `kMorph_Style`     | The stamp is *morphed* by mapping each of its points onto the contour at the corresponding distance, turning lines into curves. |

`Sk1DPathEffect` (the internal base, also in `Sk1DPathEffect.cpp`)
provides the contour-walking machinery: for each contour it calls a
virtual `next(SkPathBuilder*, dist, SkPathMeasure)` until the contour
is exhausted (or a 100k-iteration safety governor fires).
`SkPath1DPathEffectImpl` overrides `next` to dispatch on style; the
morph implementation calls `morphpath` / `morphpoints`, which evaluate
each Bézier control point via the path measure and rebuild the stamp
out of curves. The morph case turns every `kLine` verb into a quad so
straight stamp segments curve along the contour.

`SkPath1DPathEffectImpl::onFilterPath` *forces fill style* — a 1D
effect's stamps are themselves geometry, not strokes. The serialised
form is `(advance, path, phase, style)`.

## Sk2DPathEffect — `include/effects/Sk2DPathEffect.h` + `src/effects/Sk2DPathEffect.cpp`

The 2D family stamps a pattern over a 2D lattice intersected with the
input path's interior. The lattice is defined by an arbitrary `SkMatrix`
(rotation, scale, skew), and the lattice cell is the unit square mapped
through that matrix.

The internal base `Sk2DPathEffect`:

1. Inverts the matrix and transforms the input path into the lattice's
   parameter space.
2. Computes the integer-rect bounds of the transformed path.
3. Calls a virtual `begin(uvBounds, dst)` then `nextSpan(x, y, ucount,
   dst)` for each scanline of pixels inside the path's region (using
   `SkRegion::Iterator` on the path-region intersection), then
   `end(dst)`.
4. The default `nextSpan` calls `next(loc, u, v, dst)` for each pixel,
   transforming `(u + 0.5, v + 0.5)` back through the matrix to compute
   the lattice point in input space.

Two concrete subclasses live in the same file:

- **`SkLine2DPathEffect`** (`SkLine2DPathEffectImpl`) — emits horizontal
  line segments connecting adjacent lattice cells in each row, then
  switches the stroke rec to a stroke style of `width` pixels. The result
  is a hatching / cross-hatch pattern; combine with a rotated matrix or
  via `MakeSum` of two perpendicular effects for cross-hatching.
- **`SkPath2DPathEffect`** (`SkPath2DPathEffectImpl`) — adds a copy of a
  user-supplied `path` at every lattice point that lies inside the input
  path. The default style is "fill" because the 2D effect's output is
  itself fully shaped geometry.

`computeFastBounds` returns false on both — the 2D effects can grow the
bounding box beyond the input, and computing an exact bound would
require iterating the lattice anyway.

## SkStrokeRec — `include/core/SkStrokeRec.h`

The `SkStrokeRec` parameter that travels through every `filterPath`
call is the rasteriser's view of the stroke configuration:

```cpp
enum InitStyle { kHairline_InitStyle, kFill_InitStyle };
enum Style     { kHairline_Style, kFill_Style, kStroke_Style, kStrokeAndFill_Style };
```

Members include `getWidth`, `getMiter`, `getCap`, `getJoin`,
`getResScale` (the resolution scale used to round corners faster on
high-DPI), and the mutators `setStrokeStyle(width, andFill)`,
`setStrokeParams(cap, join, miter)`, `setFillStyle()`,
`setHairlineStyle()`. A `SkStrokeRec` is initialised from an `SkPaint`
and is the single source of truth the canvas hands to path effects.

Path effects can rewrite the rec — the 1D path effect calls
`rec->setFillStyle()` because its stamps are filled geometry, the 2D
*line* effect calls `rec->setStrokeStyle(width)` to convert what was
originally a fill request into a stroked grid. Most other effects
(corner, dash, discrete, trim) leave the rec alone because their
output is still meant to be stroked the same way as the input.

The class is declared `SK_BEGIN_REQUIRE_DENSE` because it is copied by
value into ops/ records frequently; keeping its layout packed avoids
wasted padding in those structures.

## How effects compose with strokes & fills

The order of operations the canvas uses for a typical paint with a
stroke + path effect is:

1. Resolve the paint into a `SkStrokeRec`.
2. Call `effect->filterPath(builder, src, &rec, cullRect, ctm)`.
3. If the rec is still stroked, call `SkStroke` to convert the stroke
   to a filled outline.
4. Hand the filled path to the rasteriser.

This means `SkDashPathEffect` runs *before* the stroke tessellation
(splitting one long stroke into many short ones); `SkCornerPathEffect`
runs *before* the stroke (so stroked corners are rounded as well);
`SkPath1DPathEffect` short-circuits the stroke entirely by setting the
rec to fill. `MakeCompose(outer, inner)` lets you e.g. dash a
corner-rounded path: `MakeCompose(SkDashPathEffect::Make(...),
SkCornerPathEffect::Make(...))`.

For the path representation itself see [Geometry & Math](geometry-and-math.md);
for the rest of the rasterisation pipeline see
[CPU Rendering Pipeline](cpu-rendering-pipeline.md) and
[Path Operations](path-operations.md).
