# Bitmap Type — Design Spec

## Motivation

Kanvas has an immutable `Image` type, but no mutable pixel buffer. This blocks migration of ~15 Skia GMs that use `SkBitmap` APIs (`setPixel`, `eraseColor`, `extractSubset`, `makeShader` with sampling/matrix). Adding a `Bitmap` class to the core Kanvas image package unblocks these GMs and provides a clean mutable-pixel primitive for future use.

## Design Decisions

### Location

`org.graphiks.kanvas.image` — same package as `Image`, in `kanvas/src/main/kotlin/`.

### Mutability

`Bitmap` is a plain `class` (not `data class`), fully mutable. `Image` stays immutable. One-way conversion: `Bitmap.toImage()` produces an immutable snapshot.

### ByteArray backing (Approach 3)

A single `ByteArray pixels` for all `ColorType` variants, like `Image` already does. Conversion between byte representation and typed values happens inside `getPixel`/`setPixel`. This keeps the type surface simple at a minor performance cost for `RGBA_F16` (half-float ↔ float conversion per pixel), acceptable for GM/test usage.

## Bitmap API

```kotlin
package org.graphiks.kanvas.image

class Bitmap(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    val pixels: ByteArray

    fun getPixel(x: Int, y: Int): Color
    fun setPixel(x: Int, y: Int, color: Color)
    fun eraseColor(color: Color)
    fun eraseArea(rect: Rect, color: Color)
    fun extractSubset(rect: Rect): Bitmap
    fun toImage(): Image
    fun makeShader(
        tileX: TileMode = TileMode.CLAMP,
        tileY: TileMode = TileMode.CLAMP,
        sampling: SamplingOptions = SamplingOptions.NEAREST,
        localMatrix: Matrix33 = Matrix33.identity(),
    ): Shader.Image

    companion object {
        fun fromImage(image: Image): Bitmap
    }
}
```

### getPixel / setPixel

Read/write a single pixel at `(x, y)` as a `Color` (sRGBA float). The byte-to-Color conversion depends on `colorType`:

- `RGBA_8888`, `BGRA_8888`: 4 bytes → float `[0,1]`, ABGR LE byte order
- `ALPHA_8`: 1 byte alpha, RGB=0
- `GRAY_8`: 1 byte luminance → R=G=B=luma, A=1
- `RGBA_F16`: 8 bytes (4× half-float LE) → `halfToFloat()` → premul float → unpremul Color
- `RGB_565`: 2 bytes → 5/6/5 bits → 8-bit R/G/B, A=1
- `ARGB_4444`: 2 bytes → 4×4 bits premul → unpremul Color

Out-of-bounds: `setPixel` silently returns; `getPixel` throws `IndexOutOfBoundsException`.

### eraseColor / eraseArea

`eraseColor(color)` fills all pixels with `color`, converted to the bitmap's `colorType`/`colorSpace`.
`eraseArea(rect, color)` fills a sub-region, clipped to bitmap bounds.

### extractSubset

Returns a new `Bitmap` with pixel data copied from the given `Rect` region. Same `colorType` and `colorSpace`.

```kotlin
fun extractSubset(rect: Rect): Bitmap
```

Value-style return (not Skia's `dst`-parameter pattern).

### toImage

Returns a new `Image` with a **copy** of the pixel data. No shared backing array — the `Image` is fully independent.

```kotlin
fun toImage(): Image =
    Image(width, height, colorType, "bitmap", pixels.copyOf(), colorSpace)
```

### makeShader

Returns a `Shader.Image` with the specified tiling, sampling, and local matrix.

```kotlin
fun makeShader(
    tileX: TileMode = TileMode.CLAMP,
    tileY: TileMode = TileMode.CLAMP,
    sampling: SamplingOptions = SamplingOptions.NEAREST,
    localMatrix: Matrix33 = Matrix33.identity(),
): Shader.Image
```

### fromImage (companion)

Construct a mutable `Bitmap` from an immutable `Image` by copying its pixels.

```kotlin
fun fromImage(image: Image): Bitmap =
    Bitmap(image.width, image.height, image.colorType, image.colorSpace).also { bmp ->
        image.pixels?.let { src -> src.copyInto(bmp.pixels) }
    }
```

## ColorType — Extended Enum

```kotlin
enum class ColorType(val bytesPerPixel: Int) {
    RGBA_8888(4),
    BGRA_8888(4),
    ALPHA_8(1),
    GRAY_8(1),
    RGBA_F16(8),
    RGB_565(2),
    ARGB_4444(2),
}
```

`RGBA_8888` and `BGRA_8888` share the same in-memory layout (Pascal ARGB int in LE, identical to the existing `Image` convention). The distinction is external byte order for encoding/decoding.

## SamplingOptions — New Type

```kotlin
package org.graphiks.kanvas.paint

sealed interface SamplingOptions {
    data object NEAREST : SamplingOptions
    data object LINEAR : SamplingOptions

    data class Cubic(
        val B: Float,
        val C: Float,
    ) : SamplingOptions {
        companion object {
            val Mitchell = Cubic(1/3f, 1/3f)
            val CatmullRom = Cubic(0f, 1/2f)
        }
    }
}
```

### Shader.Image update

The existing `Shader.Image` data class gains a `sampling` field:

```kotlin
data class Image(
    val image: org.graphiks.kanvas.image.Image,
    val tileModeX: TileMode = TileMode.CLAMP,
    val tileModeY: TileMode = TileMode.CLAMP,
    val sampling: SamplingOptions = SamplingOptions.NEAREST,
) : Shader
```

### Pipeline threading

- **CPU raster MVP**: `SamplingOptions.NEAREST` and `SamplingOptions.LINEAR` are honoured by the raster pipeline. `Cubic` falls back to `LINEAR` with a dev warning.
- **GPU WGSL**: `SamplingOptions.NEAREST` and `SamplingOptions.LINEAR` map directly to WGSL `textureSample` / `textureSampleLevel`. Cubic is deferred.

## Half-float Utilities

Extract the existing `halfToFloat`/`floatToHalf` from `SkBitmap.Companion` in `kanvas-skia` into a shared utility:

```kotlin
// kanvas/src/main/kotlin/org/graphiks/kanvas/image/HalfFloat.kt
internal fun halfToFloat(h: Short): Float
internal fun floatToHalf(f: Float): Short
```

Both `Bitmap` (core Kanvas) and `SkBitmap` (kanvas-skia) use this shared implementation.

## Impact on Image

- `Image.makeShader` gains an optional `sampling` parameter (default `SamplingOptions.NEAREST` for backward compatibility).
- `Image` stays immutable — no `setPixel`, no `extractSubset`.

## Not in Scope (First Iteration)

- `extractAlpha` — deferred, not needed for current blocked GMs
- `installPixels` / `writePixels` — external buffer management, deferred
- `peekPixels` / pixelRef sharing — deferred
- `Bitmap` as a `Canvas` target (i.e. drawing into a Bitmap via Canvas commands) — `Surface` already fills this role
- `MipmapMode` in SamplingOptions — deferred, not needed for GMs

## GM Impact

GMs that become portable once `Bitmap` and `SamplingOptions` ship:

| GM | Key API Used | Status |
|---|---|---|
| `BitmapImageGM` | `Codec.getImage()` → `SkBitmap` → offscreen → `asImage()` | Replace with `Bitmap.fromImage(decode)` |
| `BitmapSubsetShaderGM` | `extractSubset` + `makeShader(sampling, matrix)` | Direct `Bitmap.extractSubset` + `makeShader` |
| `BicubicGM` | `SkCubicResampler` + `makeShader(sampling)` | `SamplingOptions.Cubic` |
| `ClipShaderGM` | `SkRadialGradient` + `clipShader` | Separate gap (clipShader not in this spec) |
| `ClipSierpinskiRegionGM` | `SkRegion` + `clipRegion` | Separate gap |
