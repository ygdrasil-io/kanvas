# DSL Builders and Extensions

Status: Draft
Date: 2026-07-01

## Purpose

Defines the DSL layer: `@KanvasDsl` marker annotation, scope classes for Path/Paint/Canvas/Shader construction, and operator extensions for ergonomic math on core types. This layer is pure syntax sugar — everything must be expressible without DSL.

## Contracts

### @KanvasDsl

```kotlin
@DslMarker
annotation class KanvasDsl
```

- Applied to all DSL scope classes
- Prevents implicit receiver leaks across nested scopes
- Example: `drawRect { moveTo(...) }` does not compile (moveTo is in PathScope, not PaintScope)

### PathScope

```kotlin
@KanvasDsl
class PathScope {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float)
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float)
    fun arcTo(rx: Float, ry: Float, xRot: Float, largeArc: Boolean, sweep: Boolean, x: Float, y: Float)
    fun close()
}
```

- Used by `Path { ... }` DSL constructor
- Internally builds a `Path` instance via `build(): Path`

### PaintScope

```kotlin
@KanvasDsl
class PaintScope {
    var color: Color
    var shader: Shader?
    var blendMode: BlendMode
    fun blur(style: BlurStyle, sigma: Float)    // shortcut for maskFilter = MaskFilter.Blur
    fun stroke(width: Float)                     // shortcut for style + strokeWidth
    // ... all Paint properties
}
```

- Used by `drawRect(rect) { color = RED }` inline paint builder
- Internally builds a `Paint` instance

### CanvasScope

```kotlin
@KanvasDsl
class CanvasScope(private val canvas: Canvas)
```

- Delegates all Canvas method calls to the underlying `Canvas`
- Enables `surface.canvas { drawRect(...) }` syntax
- Scene functions are `CanvasScope.() -> Unit` extensions

### ShaderScopes

```kotlin
fun linearGradient(block: LinearGradientScope.() -> Unit): Shader.LinearGradient
fun radialGradient(block: RadialGradientScope.() -> Unit): Shader.RadialGradient
fun sweepGradient(block: SweepGradientScope.() -> Unit): Shader.SweepGradient
fun conicalGradient(block: ConicalGradientScope.() -> Unit): Shader.ConicalGradient
```

- Each scope has `start`, `end`, `center`, `radius`, `stop(position, color)` etc.
- `stop(position: Float, color: Color)` appends a `GradientStop`

### Operator Extensions

```kotlin
// Point
operator fun Point.plus(p: Point): Point
operator fun Point.minus(p: Point): Point
operator fun Point.times(s: Float): Point
operator fun Point.div(s: Float): Point

// Rect
operator fun Rect.contains(p: Point): Boolean

// Matrix33
operator fun Matrix33.times(point: Point): Point
operator fun Matrix33.times(other: Matrix33): Matrix33

// Path
operator fun Path.plus(offset: Point): Path   // transform(offset.x, offset.y, 1f, 1f)
operator fun Path.times(matrix: Matrix33): Path  // transform(matrix)
```

## Non-Goals

- DSL scopes do not define their own error handling — they delegate to the core types
- No Kotlin Multiplatform expect/actual in this phase — JVM-only
- No context receivers (experimental in Kotlin 2.0+) — standard lambda receivers used instead
