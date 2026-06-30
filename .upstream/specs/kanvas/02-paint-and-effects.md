# Paint and Effects

Status: Draft
Date: 2026-07-01

## Purpose

Defines the `Paint` data class and all effect sealed hierarchies: `Shader`, `ColorFilter`, `MaskFilter`, `PathEffect`, `ImageFilter`, and `Blender`. Also defines supporting enums: `BlendMode`, `PaintStyle`, `StrokeCap`, `StrokeJoin`, `TileMode`, and `GradientStop`.

## Contracts

### Paint

```kotlin
data class Paint(
    val color: Color = Color.BLACK,
    val shader: Shader? = null,
    val blendMode: BlendMode = BlendMode.SRC_OVER,
    val colorFilter: ColorFilter? = null,
    val maskFilter: MaskFilter? = null,
    val pathEffect: PathEffect? = null,
    val imageFilter: ImageFilter? = null,
    val blender: Blender? = null,
    val style: PaintStyle = PaintStyle.FILL,
    val strokeWidth: Float = 0f,
    val strokeCap: StrokeCap = StrokeCap.BUTT,
    val strokeJoin: StrokeJoin = StrokeJoin.MITER,
    val strokeMiter: Float = 4f,
    val antiAlias: Boolean = true,
)
```

- **Effect precedence:** When `shader` is non-null, it overrides `color` for fill. When `blender` is non-null, it overrides `blendMode`.
- **Factories:** `fill(color: Color)`, `stroke(color: Color, width: Float)`
- **Immutability:** Uses `copy()` for modification

### BlendMode

```kotlin
enum class BlendMode {
    // Porter-Duff (14)
    CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN,
    SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, PLUS, MODULATE,
    // Separable (11)
    MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN,
    COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION,
    // Non-separable (4)
    HUE, SATURATION, COLOR, LUMINOSITY,
}
```

- Covers all 28 Skia `SkBlendMode` values plus `MODULATE` (29 total)
- Porter-Duff compositing operators + separable/non-separable blend modes

### Shader

```kotlin
sealed interface Shader {
    data class SolidColor(val color: Color) : Shader
    data class LinearGradient(val start: Point, val end: Point, val stops: List<GradientStop>, val tileMode: TileMode) : Shader
    data class RadialGradient(val center: Point, val radius: Float, val stops: List<GradientStop>, val tileMode: TileMode) : Shader
    data class SweepGradient(val center: Point, val startAngle: Float, val endAngle: Float, val stops: List<GradientStop>, val tileMode: TileMode) : Shader
    data class ConicalGradient(val start: Point, val startRadius: Float, val end: Point, val endRadius: Float, val stops: List<GradientStop>, val tileMode: TileMode) : Shader
    data class Image(val image: Image, val tileModeX: TileMode, val tileModeY: TileMode) : Shader
    data class Blend(val mode: BlendMode, val dst: Shader, val src: Shader) : Shader
    data class RuntimeEffect(val effect: RuntimeEffect, val uniforms: UniformBlock) : Shader
    data class WithLocalMatrix(val shader: Shader, val matrix: Matrix33) : Shader
    data class WithColorFilter(val shader: Shader, val filter: ColorFilter) : Shader
}
```

- 10 subtypes covering Skia's `SkShader` factory surface
- `WithLocalMatrix` and `WithColorFilter` are composables (wrapping an inner shader)
- `Image` references `Image` from `09-image-and-text.md`
- `RuntimeEffect` references `RuntimeEffect` and `UniformBlock` from `05-gpu-pipeline.md`

### ColorFilter

```kotlin
sealed interface ColorFilter {
    data class Matrix(val values: FloatArray) : ColorFilter    // 20 floats (4×5)
    data class Blend(val color: Color, val mode: BlendMode) : ColorFilter
    data class Compose(val outer: ColorFilter, val inner: ColorFilter) : ColorFilter
    data class Table(val table: UByteArray) : ColorFilter       // 256 entries
    data class Lighting(val mul: Color, val add: Color) : ColorFilter
    data object LinearToSRGB : ColorFilter
    data object SRGBToLinear : ColorFilter
}
```

### MaskFilter

```kotlin
sealed interface MaskFilter {
    data class Blur(val style: BlurStyle, val sigma: Float) : MaskFilter
}
enum class BlurStyle { NORMAL, SOLID, OUTER, INNER }
```

### PathEffect

```kotlin
sealed interface PathEffect {
    data class Dash(val intervals: FloatArray, val phase: Float) : PathEffect
    data class Corner(val radius: Float) : PathEffect
    data class Discrete(val segmentLength: Float, val deviation: Float) : PathEffect
}
```

### ImageFilter

```kotlin
sealed interface ImageFilter {
    data class Blur(val sigmaX: Float, val sigmaY: Float, val tileMode: TileMode, val input: ImageFilter?) : ImageFilter
    data class DropShadow(val dx: Float, val dy: Float, val sigmaX: Float, val sigmaY: Float, val color: Color, val input: ImageFilter?) : ImageFilter
    data class ColorFilter(val filter: ColorFilter, val input: ImageFilter?) : ImageFilter
    data class Compose(val outer: ImageFilter, val inner: ImageFilter) : ImageFilter
    data class Blend(val mode: BlendMode, val background: ImageFilter, val foreground: ImageFilter) : ImageFilter
}
```

- Each filter may carry an optional `input: ImageFilter?` forming a DAG
- Extensible: Offset, Dilate/Erode, Displacement, Lighting deferred

### Blender

```kotlin
sealed interface Blender {
    data class Mode(val mode: BlendMode) : Blender
    data class Arithmetic(val k1: Float, val k2: Float, val k3: Float, val k4: Float) : Blender
}
```

### Supporting Types

```kotlin
enum class PaintStyle { FILL, STROKE, STROKE_AND_FILL }
enum class StrokeCap { BUTT, ROUND, SQUARE }
enum class StrokeJoin { MITER, ROUND, BEVEL }
enum class TileMode { CLAMP, REPEAT, MIRROR, DECAL }
data class GradientStop(val position: Float, val color: Color)
```

## Non-Goals

- `PerlinNoise` / `FractalNoise` shader subtypes (deferred)
- `Sk1DPathEffect`, `Sk2DPathEffect`, `TrimPathEffect` (deferred)
- Full `SkImageFilters` factory surface (Lighting, Magnifier, MatrixConvolution, Displacement) — extensible later
- Shader color space interpolation (`SkGradient::Interpolation`) — hardcoded sRGB for now
