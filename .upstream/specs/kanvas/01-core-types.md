# Core Types

Status: Draft
Date: 2026-07-01

## Purpose

Defines the fundamental value types used throughout the Kanvas API: Color, Point, Size, Rect, RRect (with CornerRadii), and Matrix33. These are the building blocks that all other API types depend on.

## Contracts

### Color

```kotlin
@JvmInline
value class Color(val packed: UInt)
```

- **Encoding:** 32-bit ARGB (A in bits 31-24, R in 23-16, G in 15-8, B in 7-0)
- **Accessors:** `r: Float`, `g: Float`, `b: Float`, `a: Float` (extension properties, 0.0–1.0)
- **Factory:** `Color.fromRGBA(r: Float, g: Float, b: Float, a: Float = 1f): Color`
- **Companion constants:** `BLACK`, `WHITE`, `RED`, `GREEN`, `BLUE`, `TRANSPARENT`
- **Rationale:** Value class = zero allocation at runtime. ARGB encoding matches GPU uniform layout (u32). Companion extension properties simulate companion object on value class.

### Point

```kotlin
data class Point(val x: Float, val y: Float)
companion object { val ZERO: Point }
```

- Immutable 2D coordinate
- Operations via operator extensions: `plus`, `minus`, `times`, `div` (see `08-dsl-and-extensions.md`)

### Size

```kotlin
data class Size(val width: Float, val height: Float)
```

- Simple dimensions container

### Rect

```kotlin
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)
```

- **Computed properties:** `width: Float`, `height: Float`, `isEmpty: Boolean`, `center: Point`
- **Factories:** `fromLTRB(l,t,r,b)`, `fromXYWH(x,y,w,h)`
- **Companion:** `EMPTY`
- **Operator:** `contains(point: Point): Boolean`

### RRect

```kotlin
data class CornerRadii(val x: Float, val y: Float)
data class RRect(
    val rect: Rect,
    val topLeft: CornerRadii, val topRight: CornerRadii,
    val bottomRight: CornerRadii, val bottomLeft: CornerRadii,
)
```

- **Convenience constructor:** `RRect(rect: Rect, radius: Float)` — uniform radii
- **Default:** all radii default to `CornerRadii(0f, 0f)` (sharp corners)

### Matrix33

```kotlin
class Matrix33 private constructor(private val values: FloatArray)
```

- **Backing:** `FloatArray(9)` — row-major 3×3
- **Named accessors:** `scaleX`, `skewX`, `transX`, `skewY`, `scaleY`, `transY`, `persp0`, `persp1`, `persp2`
- **Factories:** `identity()`, `translate(x,y)`, `scale(sx,sy)`, `rotate(degrees)`, `skew(kx,ky)`
- **Operators:** `times(Matrix33)`, `times(Point)`
- **Equality:** content-based via `values.contentEquals()`

## Non-Goals

- Color spaces beyond sRGB are not modeled in this pack
- Matrix44 (4×4) is not included — the 3×3 covers all 2D affine + perspective transforms
- Point does not support integer variants (Skia's SkIPoint) — use Float coordinates universally
- Rect does not include integer variant (Skia's SkIRect) — see `04-canvas-and-drawing.md` for clip bounds
