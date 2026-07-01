# Image and Text Types

Status: Draft
Date: 2026-07-01

## Purpose

Defines `Image`, `ColorType`, `TextBlob`, `KanvasTypeface`, and `KanvasGlyphRun` — the data types for image content and pre-shaped text glyph runs used by `Canvas.drawImage()` and `Canvas.drawText()`.

## Contracts

### Image

```kotlin
data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val sourceId: String,
) {
    companion object {
        fun decode(bytes: ByteArray, mimeType: String? = null): Image
    }
}
```

- Metadata carrier — the actual pixel data is managed by the GPU backend
- `sourceId`: unique identifier for texture lookup in GPU resource cache
- `colorSpace`: forwarded to `GPUColorSpaceDescriptor` in `:gpu-renderer` for color conversion; defaults to sRGB
- `decode()`: placeholder in this phase (returns width=0, height=0); real implementation via codec SPI deferred (codec will extract color space from image metadata — e.g., ICC profile from JPEG, gAMA/cHRM from PNG, NCLC/colr from HEIF)

### ColorType

```kotlin
enum class ColorType { RGBA_8888, BGRA_8888, ALPHA_8, GRAY_8 }
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

- Multi-run text container (mutable-free, like Skia's TextBlob)
- When `typeface` is non-null, the font pipeline can produce an A8 glyph atlas
- Without `typeface`: placeholder rendering with empty atlas (diagnostic: `DEGRADE`)

## Non-Goals

- `Image.decode` real implementation — requires codec SPI integration (deferred)
- Image encode beyond PNG — JPEG, WebP, etc. require codec SPI (deferred)
- Font / FontMgr (Skia) — font management is not in the Kanvas facade
- Text shaping (Bidi, Kerning, GPOS/GSUB) — delegated to `:font`
- `TextBlob.bounds()`, `TextBlob.serialize()` — deferred
- `makeFromString`, `makeFromRSXform` — text shaping not yet in facade
- `TextBlob.getIntercepts()` — deferred
- `Image.makeShader()` convenience — deferred (use `Shader.Image` directly)
