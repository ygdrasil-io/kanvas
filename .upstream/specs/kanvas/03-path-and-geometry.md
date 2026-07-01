# Path and Geometry

Status: Draft
Date: 2026-07-01

## Purpose

Defines the `Path` class for vector geometry, the `FillType` and `PathVerb` enums, and the `ClipStack` sealed hierarchy for clipping state. Path is the central geometry type used by `Canvas.drawPath()` and clip operations.

## Contracts

### Path

```kotlin
class Path private constructor()
```

- **Verbs (fluent, return `this`):** `moveTo(x,y)`, `lineTo(x,y)`, `quadTo(cx,cy,x,y)`, `cubicTo(c1x,c1y,c2x,c2y,x,y)`, `arcTo(rx,ry,xRot,largeArc,sweep,x,y)`, `close()`
- **Convenience:** `addRect(Rect)`, `addOval(Rect)`, `addCircle(cx,cy,r)`, `addRRect(RRect)`, `addPath(Path)`
- **Transform:** `transform(tx,ty,sx,sy): Path`, `transform(Matrix33): Path` — returns new Path (immutable)
- **DSL constructor:** `Path { moveTo(...); lineTo(...); close() }` via `PathScope`
- **Internal:** `verbs(): List<PathVerb>`, `points(): List<Point>`
- **Property:** `fillType: FillType` (default `WINDING`)

### PathVerb

```kotlin
enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }
```

### FillType

```kotlin
enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
```

- Maps to Skia's PathFillType: kWinding, kEvenOdd, kInverseWinding, kInverseEvenOdd

### ClipStack

```kotlin
sealed interface ClipStack {
    data object WideOpen : ClipStack
    data class DeviceRect(val rect: Rect) : ClipStack
    data class Complex(val ops: List<ClipStackOp>) : ClipStack
}

sealed interface ClipStackOp {
    data class Rect(val rect: Rect, val op: ClipOp) : ClipStackOp
    data class RRect(val rrect: RRect, val op: ClipOp) : ClipStackOp
    data class Path(val path: Path, val op: ClipOp) : ClipStackOp
}
```

- `WideOpen`: no clipping (default)
- `DeviceRect`: single axis-aligned rectangle clip — fast path on GPU
- `Complex`: arbitrary clip stack — may trigger stencil or fallback
- `ClipOp` is defined in `05-gpu-pipeline.md`

## Non-Goals

- Path measurement (Skia PathMeasure) — deferred
- Path boolean operations (union, intersect, difference, xor, simplify) — deferred
- Path queries: `isConvex`, `isOval`, `isRRect`, `isRect`, `isLine`, `isInterpolatable` — deferred
- `conicTo` — arcTo handles conic-like curves; conics can be approximated by quads
- `Direction` enum (CW/CCW) — circle/oval always use clockwise
- Region (Skia) — not included; clip uses `ClipStack`
- Path serialization (`serialize` / `ReadFromMemory`) — deferred
