# Surface and Rendering

Status: Draft
Date: 2026-07-01

## Purpose

Defines the `Surface` class — the rendering target that owns the display list buffer, provides Canvas access, and executes rendering. Also defines `RenderResult`, `RenderStats`, and the `toPng()` image export bridge.

## Contracts

### Surface

```kotlin
class Surface(
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
)
```

- **Canvas access:**
  - `canvas(): Canvas` — returns (or creates) the Canvas for this surface
  - `canvas { ... }` — DSL convenience: creates canvas, runs block
- **Rendering:**
  - `render(): RenderResult` — compiles DisplayList → executes on GPU → returns pixels + diagnostics
- **GPU access:**
  - `gpu: GPUContext` — provides direct GPU pipeline access (advanced)
  - `renderPass(desc: RenderPassDescriptor, block: RenderPass.() -> Unit)` — shortcut to begin + execute + end a render pass
- **Color space:** `colorSpace` defines the target color space for the render target. Forwarded to `GPUColorSpaceDescriptor` and `GPUColorStorePlan` in `:gpu-renderer`. Defaults to sRGB.

### PixelFormat

```kotlin
enum class PixelFormat { RGBA8, BGRA8 }
```

### RenderResult

```kotlin
data class RenderResult(
    val pixels: UByteArray,
    val width: Int,
    val height: Int,
    val colorSpace: ColorSpace,
    val diagnostics: Diagnostics,
    val stats: RenderStats,
) {
    val isClean: Boolean
    val hasIssues: Boolean
    fun assertClean()  // throws IllegalArgumentException if !isClean
}
```

- `pixels`: raw RGBA bytes (width × height × 4)
- `colorSpace`: the color space of the returned pixel data (reflects Surface's target color space)
- `assertClean()`: guard for tests — ensures no diagnostics were emitted

### RenderStats

```kotlin
data class RenderStats(
    val opsDispatched: Int,
    val opsRefused: Int,
    val pipelineCount: Int,
    val drawCallCount: Int,
    val coverage: Float,
)
```

- `coverage`: ratio of non-transparent pixels (0.0–1.0)

### toPng() Image Export

```kotlin
fun RenderResult.toPng(): ByteArray
```

- Extension on `RenderResult`
- Uses `ImageEncoderRegistry.find("png")` via ServiceLoader on `:codec:api`
- Call site: `encoder.encode(pixels, width, height, ImageEncoder.Metadata(PixelLayout.RGBA8, colorSpace))`
- The encoder is responsible for tagging the PNG with the correct color profile chunk (sRGB chunk for `ColorSpace.SRGB`, iCCP chunk for `DisplayP3`/`Custom` profiles)
- If no PNG encoder registered: throws `IllegalStateException("Add :codec:png to your dependencies to enable PNG export")`
- No hard dependency on `:codec:png` in `:kanvas`

### ImageEncoder Bridge

```kotlin
interface ImageEncoder {
    fun encode(pixels: ByteArray, width: Int, height: Int, metadata: Metadata): ByteArray
    data class Metadata(val format: PixelLayout, val colorSpace: ColorSpace)
    enum class PixelLayout { RGBA8, BGRA8 }
}

object ImageEncoderRegistry {
    fun find(format: String): ImageEncoder?
    fun register(format: String, encoder: ImageEncoder)
}
```

- `Metadata.colorSpace` tells the encoder which profile to embed in the output
- `ImageEncoder` is defined in `:codec:api`
- `ImageEncoderRegistry` is a ServiceLoader-backed registry
- Concrete encoders (`PngEncoder`) live in `:codec:png`

## Non-Goals

- `makeSurface()` / `makeImageSnapshot()` — multi-surface composition is not a Kanvas concern
- Pixel readback beyond full RGBA frame
- Image encoding beyond PNG (`toPng`); other formats follow the same SPI pattern but are not part of Kanvas
