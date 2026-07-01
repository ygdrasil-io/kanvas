# Path and Geometry

Status: Draft
Date: 2026-07-01

## Purpose

Defines the `Path` class for vector geometry, the `FillType` and `PathVerb` enums, the `ClipStack` sealed hierarchy for clipping state, `PathMeasure` for path measurement and segment extraction, path boolean operations, and `Region` for rectangle-based boolean geometry. Path is the central geometry type used by `Canvas.drawPath()` and clip operations.

## Contracts

### Path

```kotlin
class Path private constructor()
```

- **Verbs (fluent, return `this`):** `moveTo(x,y)`, `lineTo(x,y)`, `quadTo(cx,cy,x,y)`, `cubicTo(c1x,c1y,c2x,c2y,x,y)`, `arcTo(rx,ry,xRot,largeArc,sweep,x,y)`, `close()`
- **Convenience:** `addRect(Rect)`, `addOval(Rect)`, `addCircle(cx,cy,r)`, `addRRect(RRect)`, `addPath(Path)`, `reverseAddPath(Path)`
- **Transform:** `transform(tx,ty,sx,sy): Path`, `transform(Matrix33): Path` — returns new Path (immutable)
- **DSL constructor:** `Path { moveTo(...); lineTo(...); close() }` via `PathScope`
- **Internal:** `verbs(): List<PathVerb>`, `points(): List<Point>`
- **Property:** `fillType: FillType` (default `WINDING`)

#### Introspection Queries

| Method | Returns |
|--------|---------|
| `isEmpty(): Boolean` | True if the path has no geometry |
| `isConvex(): Boolean` | True if the path is convex |
| `isRect(rect: Rect?): Boolean` | True if equivalent to an axis-aligned rectangle |
| `isOval(bounds: Rect?): Boolean` | True if equivalent to an oval |
| `isRRect(rrect: RRect?): Boolean` | True if equivalent to a rounded rectangle |
| `isLine(line: Line?): Boolean` | True if equivalent to a line segment |
| `isInterpolatable(other: Path): Boolean` | True if the two paths can be interpolated |
| `contains(point: Point): Boolean` | True if the point is inside the filled path |
| `conservativelyContainsRect(rect: Rect): Boolean` | True if the rect is fully inside |

### PathMeasure

```kotlin
class PathMeasure(path: Path, forceClosed: Boolean = false, resScale: Float = 1f) {
    val length: Float
    val isClosed: Boolean

    fun getPosition(distance: Float, position: Point?, tangent: Point?): Boolean
    fun getSegment(start: Float, stop: Float, dst: Path, startWithMoveTo: Boolean): Boolean
    fun getMatrix(distance: Float, matrix: Matrix33, flags: MatrixFlags): Boolean
    fun nextContour(): Boolean
}
```

- Measures arc length, extracts tangent vectors, positions along a path
- Supports multi-contour paths via `nextContour()`

### PathOps

```kotlin
enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path?
    fun simplify(path: Path): Path?
    fun asWinding(path: Path): Path?
}
```

- Boolean operations on closed paths: union, intersection, difference, exclusive-or, reverse difference
- Returns `null` when the operation fails to produce a valid result
- `simplify` reduces self-overlapping paths to non-overlapping contours
- `asWinding` converts even-odd fill to winding fill

### Region

```kotlin
class Region {
    constructor()
    constructor(rect: Rect)
    constructor(region: Region)

    val isEmpty: Boolean
    val isRect: Boolean
    val isComplex: Boolean
    val bounds: Rect

    fun setEmpty()
    fun setRect(rect: Rect)
    fun setRegion(region: Region)

    fun op(rect: Rect, op: RegionOp): Boolean
    fun op(region: Region, op: RegionOp): Boolean

    fun contains(x: Float, y: Float): Boolean
    fun quickReject(rect: Rect): Boolean
    fun translate(dx: Float, dy: Float)
}

enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }
```

- Boolean operations on sets of axis-aligned rectangles — faster than `PathOps` for rectangular geometry
- Used by the clipping system for optimized clip region tracking

### PathVerb

```kotlin
enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }
```

### FillType

```kotlin
enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
```

- Maps to the four canonical fill type variants: winding, even-odd, inverse winding, inverse even-odd

### ClipStack

```kotlin
sealed interface ClipStack {
    data object WideOpen : ClipStack
    data class DeviceRect(val rect: Rect, val antiAlias: Boolean = true) : ClipStack
    data class Complex(val ops: List<ClipStackOp>) : ClipStack
}

sealed interface ClipStackOp {
    data class RectOp(val rect: Rect, val op: ClipOp, val antiAlias: Boolean = true) : ClipStackOp
    data class RRectOp(val rrect: RRect, val op: ClipOp, val antiAlias: Boolean = true) : ClipStackOp
    data class PathOp(val path: Path, val op: ClipOp, val antiAlias: Boolean = true) : ClipStackOp
}
```

- `WideOpen`: no clipping (default)
- `DeviceRect`: single axis-aligned rectangle clip — fast path on GPU
- `Complex`: arbitrary clip stack — may trigger stencil or fallback
- `ClipOp` is defined in `05-gpu-pipeline.md`

## Non-Goals

- Conic section curves — `arcTo` covers elliptical arcs; conics can be approximated by quadratic Bezier segments
- Explicit winding direction control — circles and ovals always use clockwise orientation
- Path binary serialization
