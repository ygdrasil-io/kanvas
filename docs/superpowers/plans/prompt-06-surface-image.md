You are implementing the remaining Surface, Image, and RenderResult features for Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Read these files first:
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderResult.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt

### TASK 1: Surface.readPixels

Add to `Surface.kt`:

```kotlin
/**
 * Copy rendered pixels from a rectangular region into [dstBuffer].
 * Calls [render] if needed, then copies the pixel region.
 *
 * @param src the source rectangle in surface coordinates
 * @param dstBuffer pre-allocated buffer of size (src.width * src.height * 4)
 * @return true on success, false if the region is out of bounds
 */
fun readPixels(src: Rect, dstBuffer: UByteArray): Boolean {
    val result = render()
    val sx = src.left.toInt().coerceIn(0, width)
    val sy = src.top.toInt().coerceIn(0, height)
    val sw = src.width.toInt().coerceAtMost(width - sx)
    val sh = src.height.toInt().coerceAtMost(height - sy)
    if (sw <= 0 || sh <= 0) return false
    val stride = 4
    val expectedSize = sw * sh * stride
    if (dstBuffer.size < expectedSize) return false
    for (row in 0 until sh) {
        val srcOffset = ((sy + row) * width + sx) * stride
        val dstOffset = row * sw * stride
        result.pixels.copyInto(dstBuffer, dstOffset, srcOffset, srcOffset + sw * stride)
    }
    return true
}
```

### TASK 2: RenderResult.toJpeg / toWebP

In `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt`, add after the `toPng()` extension:

```kotlin
/** Encode this render result as JPEG with the given quality (0-100). */
fun RenderResult.toJpeg(quality: Int = 92): ByteArray {
    val encoder = ImageEncoderRegistry.find("jpeg")
        ?: throw IllegalStateException("Add :codec:jpeg to your dependencies to enable JPEG export")
    return encoder.encode(pixels, width, height, ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, colorSpace), mapOf("quality" to quality.toString()))
}

/** Encode this render result as WebP with the given quality (0-100). */
fun RenderResult.toWebP(quality: Int = 80): ByteArray {
    val encoder = ImageEncoderRegistry.find("webp")
        ?: throw IllegalStateException("Add :codec:webp to your dependencies to enable WebP export")
    return encoder.encode(pixels, width, height, ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, colorSpace), mapOf("quality" to quality.toString()))
}
```

### TASK 3: Image.decode real implementation

In `Image.kt`, replace the placeholder `decode()`:

```kotlin
fun decode(bytes: ByteArray, mimeType: String? = null): Image {
    // Try registered codec first
    val format = mimeType?.substringAfter("image/")?.lowercase()
        ?: detectFormatFromMagicBytes(bytes)
    if (format != null) {
        val encoder = ImageEncoderRegistry.find("decode-$format")
        if (encoder != null) {
            val metadata = ImageEncoder.Metadata(ImageEncoder.PixelLayout.RGBA8, ColorSpace.SRGB)
            // encoder.decode returns raw RGBA bytes; we need width/height from metadata
            // For now: decode PNG specifically, other formats return placeholder
            if (format == "png") {
                return decodePng(bytes)
            }
        }
    }
    // Fallback placeholder
    return Image(0, 0, ColorType.RGBA_8888, ColorSpace.SRGB, "decode-placeholder:${bytes.size}")
}

private fun detectFormatFromMagicBytes(bytes: ByteArray): String? {
    if (bytes.size < 4) return null
    // PNG: 89 50 4E 47
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "png"
    // JPEG: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "jpeg"
    // WebP: 52 49 46 46 ... 57 45 42 50
    if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) return "webp"
    // GIF: 47 49 46 38
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return "gif"
    // BMP: 42 4D
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
    return null
}

// Minimal PNG decoder (reads IHDR for width/height, extracts raw RGBA)
private fun decodePng(bytes: ByteArray): Image {
    // PNG signature check
    // Read IHDR at offset 16: width(4) height(4) bitDepth(1) colorType(1)
    // For now, return placeholder with correct format tag
    return Image(0, 0, ColorType.RGBA_8888, ColorSpace.SRGB, "decode-png:${bytes.size}")
}
```

Note: Full PNG/JPEG/WebP decoding requires the `:codec:*` SPI modules. The above is a scaffolding that:
- Correctly detects image format from magic bytes
- Returns the right format tag in sourceId
- Leaves actual pixel decoding to the codec SPI (separate work)

### TASK 4: Tests

Add to `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt` (create if not exists):

```kotlin
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceTest {
    @Test
    fun `readPixels copies correct region`() {
        val surface = Surface(100, 100)
        surface.canvas { drawRect(Rect.fromLTRB(0f, 0f, 100f, 100f), Paint.fill(Color.RED)) }
        val buffer = UByteArray(10 * 10 * 4)
        val ok = surface.readPixels(Rect.fromLTRB(0f, 0f, 10f, 10f), buffer)
        assertTrue(ok)
    }

    @Test
    fun `Image decode detects PNG magic bytes`() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val img = Image.decode(pngHeader)
        assertTrue(img.sourceId.contains("png"))
    }

    @Test
    fun `Image decode detects JPEG magic bytes`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val img = Image.decode(jpegHeader)
        assertTrue(img.sourceId.contains("jpeg"))
    }
}
```

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.surface.SurfaceTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 6 — Surface.readPixels, toJpeg/toWebP, Image.decode magic bytes"`

Return: compilation result, test results, summary of what was implemented.
