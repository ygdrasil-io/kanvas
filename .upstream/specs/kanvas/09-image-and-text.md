# Image and Text Types

Status: Draft
Date: 2026-07-01

## Purpose

Defines `Image`, `ColorType`, `ColorSpace`, `TextBlob`, `KanvasTypeface`, and `KanvasGlyphRun` — the data types for image content and pre-shaped text glyph runs used by `Canvas.drawImage()` and `Canvas.drawText()`.

## Contracts

### Image

```kotlin
data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val sourceId: String,
    val pixels: UByteArray? = null,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): Image
        fun fromPixels(width: Int, height: Int, pixels: UByteArray, colorType: ColorType = ColorType.RGBA_8888, colorSpace: ColorSpace = ColorSpace.SRGB, sourceId: String = ""): Image
    }
}
```

- Metadata carrier — the actual pixel data is managed by the GPU backend
- `sourceId`: unique identifier for texture lookup in GPU resource cache
- `pixels`: optional in-memory pixel buffer for CPU-side access or small images
- `colorSpace`: forwarded to `GPUColorSpaceDescriptor` in `:gpu-renderer` for color conversion; defaults to sRGB
- `decode()`: decodes image bytes via codec SPI. Extracts color space from image metadata (ICC profile from JPEG, gAMA/cHRM from PNG, NCLC/colr from HEIF). Supported formats: PNG, JPEG, WebP, GIF, BMP. Returns zero-size placeholder when no codec SPI is registered.

### ColorType

```kotlin
enum class ColorType { RGBA_8888, BGRA_8888, ALPHA_8, GRAY_8 }
```

### ColorSpace

```kotlin
data class ColorSpace(val name: String, val transferFunction: TransferFunction, val gamut: Gamut) {
    companion object {
        val SRGB = ColorSpace("sRGB", TransferFunction.SRGB, Gamut.SRGB)
        val DISPLAY_P3 = ColorSpace("Display P3", TransferFunction.SRGB, Gamut.DISPLAY_P3)
        val LINEAR_SRGB = ColorSpace("Linear sRGB", TransferFunction.LINEAR, Gamut.SRGB)
    }
}

enum class TransferFunction { SRGB, LINEAR, PQ, HLG }
enum class Gamut { SRGB, DISPLAY_P3, REC2020 }
```

### KanvasTypeface

```kotlin
data class KanvasTypeface(val resourcePath: String)
```

- Carries a classpath resource path to a `.ttf` file
- Font management (loading, selection, fallback) is in `:font`
- Kanvas only stores the reference

### KanvasGlyphRun

```kotlin
data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<Point>,
)
```

- Pre-shaped glyph run — glyph IDs + positions
- Text shaping (Unicode→glyph) is delegated to `:font`

### TextBlob

```kotlin
data class TextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
    val typeface: KanvasTypeface? = null,
    val fontSize: Float = 12f,
)
```

- Multi-run text container (mutable-free, pre-shaped glyph runs only)
- When `typeface` is non-null, the font pipeline can produce an A8 glyph atlas
- Without `typeface`: placeholder rendering with empty atlas (diagnostic: `DEGRADE`)

## Non-Goals

- Font and font management — not part of the Kanvas facade; delegated to `:font`
- Text shaping (bidi, kerning, glyph substitution and positioning) — delegated to `:font`
- `TextBlob.bounds()`, `TextBlob.serialize()`, `TextBlob.getIntercepts()`
- String-to-glyph conversion (`makeFromString`, `makeFromRSXform`)
- `Image.makeShader()` convenience — use `Shader.Image` directly
