package org.skia.tests

import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions

/**
 * Port of `DEF_SIMPLE_GM(textureimage_and_shader, canvas, 100, 50)` from
 * `gm/image_shader.cpp` (line 220).
 *
 * Test case for skbug.com/40043768 — texture-backed image shaders silently
 * fail drawing to CPU canvas.
 *
 * The GM exercises two paths (drawImage and image-shader) via a 50×50
 * offscreen CPU-raster green surface. Since `:kanvas-skia` is raster-only,
 * the GPU texture branch is never taken — `canvas.getSurface()` is not
 * available, so we always follow the fallback raster path from the original
 * C++ (`SkSurfaces::Raster`). Both left and right halves should be
 * solid green when the implementation is correct.
 *
 * All APIs exercised here are fully implemented in `:kanvas-skia` — no
 * STUB TODO is required.
 */
public class TextureimageAndShaderGM : GM() {

    override fun getName(): String = "textureimage_and_shader"
    override fun getISize(): SkISize = SkISize.Make(100, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Upstream clears green, then tries to snapshot the canvas surface
        // (GPU path) or falls back to a fresh raster surface (CPU path).
        // kanvas-skia is raster-only — always use the CPU fallback.
        c.clear(SK_ColorGREEN)

        // Fallback path: create a 50×50 green raster image.
        val greenSurface = SkSurface.MakeRaster(SkImageInfo.MakeN32(50, 50, SkAlphaType.kPremul))
        greenSurface.canvas.clear(SK_ColorGREEN)
        val image = greenSurface.makeImageSnapshot()

        // At this point image holds a green 50×50 raster snapshot.
        // Upstream: draw to a separate CPU surface and blit back to canvas.

        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32(50, 50, SkAlphaType.kPremul))

        // Left half: drawImage path
        surface.canvas.clear(SK_ColorRED)
        surface.canvas.drawImage(image, 0f, 0f)
        c.drawImage(surface.makeImageSnapshot(), 0f, 0f)

        // Right half: image-shader path
        val paint = SkPaint()
        paint.shader = image.makeShader(SkSamplingOptions.Default)
        surface.canvas.clear(SK_ColorRED)
        surface.canvas.drawPaint(paint)
        c.drawImage(surface.makeImageSnapshot(), 50f, 0f)
    }
}
