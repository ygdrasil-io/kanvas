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

- Covers all 29 Porter-Duff compositing operators and separable/non-separable blend modes
- Porter-Duff compositing operators + separable/non-separable blend modes

### Shader

```kotlin
sealed interface Shader {
    // Color and gradients
    data class SolidColor(val color: Color) : Shader
    data class LinearGradient(val start: Point, val end: Point, val stops: List<GradientStop>, val tileMode: TileMode, val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB) : Shader
    data class RadialGradient(val center: Point, val radius: Float, val stops: List<GradientStop>, val tileMode: TileMode, val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB) : Shader
    data class SweepGradient(val center: Point, val startAngle: Float, val endAngle: Float, val stops: List<GradientStop>, val tileMode: TileMode, val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB) : Shader
    data class ConicalGradient(val start: Point, val startRadius: Float, val end: Point, val endRadius: Float, val stops: List<GradientStop>, val tileMode: TileMode, val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB) : Shader

    // Image-based
    data class Image(val image: Image, val tileModeX: TileMode, val tileModeY: TileMode) : Shader

    // Procedural noise
    data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
    data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader

    // Compositing
    data class Blend(val mode: BlendMode, val dst: Shader, val src: Shader) : Shader

    // Runtime effects
    data class RuntimeEffect(val effect: RuntimeEffect, val uniforms: UniformBlock) : Shader

    // Wrappers
    data class WithLocalMatrix(val shader: Shader, val matrix: Matrix33) : Shader
    data class WithColorFilter(val shader: Shader, val filter: ColorFilter) : Shader
    data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader
    data class CoordClamp(val shader: Shader, val subset: Rect) : Shader
}

enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }
```

- 15 shader subtypes covering solid colors, four gradient types, image, procedural noise, blend, runtime effects, local matrix, color filter, working color space, and coordinate clamping
- `WithLocalMatrix`, `WithColorFilter`, `WithWorkingColorSpace`, and `CoordClamp` are composables (wrapping an inner shader)
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
    data class HSLAMatrix(val values: FloatArray) : ColorFilter // 20 floats, operates in HSLA
    data class Lerp(val t: Float, val dst: ColorFilter, val src: ColorFilter) : ColorFilter
    data object HighContrast : ColorFilter
    data object Luma : ColorFilter
    data object Overdraw : ColorFilter
}
```

- 12 subtypes covering RGBA matrix, blend, compose, lookup table, lighting, gamma conversion, HSLA matrix, filter interpolation, high-contrast, luma extraction, and overdraw visualization

### MaskFilter

```kotlin
sealed interface MaskFilter {
    data class Blur(val style: BlurStyle, val sigma: Float) : MaskFilter
    data class Shader(val shader: Shader) : MaskFilter
    data class Table(val table: UByteArray) : MaskFilter       // 256 entries
}
enum class BlurStyle { NORMAL, SOLID, OUTER, INNER }
```

- 3 subtypes: gaussian blur, shader-based masks, and lookup-table masks

### PathEffect

```kotlin
sealed interface PathEffect {
    data class Dash(val intervals: FloatArray, val phase: Float) : PathEffect
    data class Corner(val radius: Float) : PathEffect
    data class Discrete(val segmentLength: Float, val deviation: Float) : PathEffect
    data class Path1D(val path: Path, val advance: Float, val phase: Float, val style: Path1DStyle) : PathEffect
    data class Path2D(val matrix: Matrix33, val path: Path) : PathEffect
    data class Trim(val start: Float, val stop: Float) : PathEffect
}
enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }
```

- 6 subtypes: dash pattern, rounded corners, discrete scattering, 1D path repeat (patterned stroke), 2D path deformation, and path trimming (progressive drawing)

### ImageFilter

```kotlin
sealed interface ImageFilter {
    // Core
    data class Blur(val sigmaX: Float, val sigmaY: Float, val tileMode: TileMode, val input: ImageFilter?) : ImageFilter
    data class DropShadow(val dx: Float, val dy: Float, val sigmaX: Float, val sigmaY: Float, val color: Color, val input: ImageFilter?) : ImageFilter
    data class ColorFilter(val filter: ColorFilter, val input: ImageFilter?) : ImageFilter
    data class Compose(val outer: ImageFilter, val inner: ImageFilter) : ImageFilter
    data class Blend(val mode: BlendMode, val background: ImageFilter, val foreground: ImageFilter) : ImageFilter

    // Morphology
    data class Dilate(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter
    data class Erode(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter

    // Lighting — diffuse
    data class DistantLitDiffuse(val direction: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
    data class PointLitDiffuse(val location: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
    data class SpotLitDiffuse(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter

    // Lighting — specular
    data class DistantLitSpecular(val direction: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
    data class PointLitSpecular(val location: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
    data class SpotLitSpecular(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter

    // Transform and compositing
    data class Offset(val dx: Float, val dy: Float, val input: ImageFilter?) : ImageFilter
    data class Tile(val src: Rect, val dst: Rect, val input: ImageFilter?) : ImageFilter
    data class Merge(val inputs: List<ImageFilter>) : ImageFilter

    // Advanced
    data class DisplacementMap(val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel, val scale: Float, val displacement: ImageFilter, val input: ImageFilter?) : ImageFilter
    data class Magnifier(val src: Rect, val zoom: Float, val inset: Float, val input: ImageFilter?) : ImageFilter
    data class MatrixConvolution(val kernelSize: Size, val kernel: FloatArray, val gain: Float, val bias: Float, val kernelOffset: Point, val tileMode: TileMode, val convolveAlpha: Boolean, val input: ImageFilter?) : ImageFilter
}

enum class ColorChannel { R, G, B, A }
```

- Each filter may carry an optional `input: ImageFilter?` forming a DAG
- 20 subtypes covering the full composable filter graph: core compositing, morphological operations, diffuse and specular lighting (distant, point, spot), coordinate offset, tile repetition, merge flattening, displacement mapping, magnifier lens effect, and matrix convolution

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

- Color filter types beyond the 12 defined here (the sealed interface is the complete set)
- Image filter types beyond the 20 defined here
- Shader types beyond the 15 defined here
- Path effect types beyond the 6 defined here
- Mask filter types beyond the 3 defined here
- Non-separable blend modes as blenders (use `Blender.Mode` with the appropriate `BlendMode`)
