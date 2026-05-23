package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

// ── backdrop_imagefilter_croprect_rotated ───────────────────────────────────

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::
 * DEF_SIMPLE_GM(backdrop_imagefilter_croprect_rotated, 600, 500)`.
 *
 * Draws correctly if there's a blurred red rectangle inside a cyan
 * rectangle, above a blurred green rectangle inside a larger magenta
 * rectangle. All rectangles and the blur direction are consistently
 * rotated.
 *
 * Translates by `(140, -180)`, rotates `30°`, then delegates to
 * [drawBackdropFilterGm] with a Gaussian blur filter (`sigmaX = 16`,
 * `sigmaY = 4`) and a `32 × 32` crop outset.
 */
public class BackdropImagefilterCroprectRotatedGM : GM() {

    override fun getName(): String = "backdrop_imagefilter_croprect_rotated"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(140f, -180f)
        c.rotate(30f)
        drawBackdropFilterGm(c, 32f, 32f) { crop ->
            makeBlurFilter(crop)
        }
    }
}

// ── backdrop_imagefilter_croprect_persp ─────────────────────────────────────

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::
 * DEF_SIMPLE_GM(backdrop_imagefilter_croprect_persp, 600, 500)`.
 *
 * Draws correctly if there's a blurred red rectangle inside a cyan
 * rectangle, above a blurred green rectangle inside a larger magenta
 * rectangle. All rectangles and the blur direction are under consistent
 * perspective.
 *
 * **NOTE**: The upstream comment indicates this currently renders
 * incorrectly in Skia itself (skbug.com/40040358).
 *
 * Applies a perspective matrix (`perspY = 0.001`, `skewX = 8/25`),
 * then delegates to [drawBackdropFilterGm] with a Gaussian blur filter.
 */
public class BackdropImagefilterCroprectPerspGM : GM() {

    override fun getName(): String = "backdrop_imagefilter_croprect_persp"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Mirrors C++: SkMatrix persp = SkMatrix::I(); persp.setPerspY(0.001f);
        // persp.setSkewX(8.f / 25.f); canvas->concat(persp);
        // SkMatrix is immutable in Kotlin — construct directly with the fields.
        val persp = SkMatrix(
            sx = 1f, kx = 8f / 25f, tx = 0f,
            ky = 0f, sy = 1f, ty = 0f,
            persp0 = 0f, persp1 = 0.001f, persp2 = 1f,
        )
        c.concat(persp)
        drawBackdropFilterGm(c, 32f, 32f) { crop ->
            makeBlurFilter(crop)
        }
    }
}

// ── backdrop_imagefilter_croprect_nested ────────────────────────────────────

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::
 * DEF_SIMPLE_GM(backdrop_imagefilter_croprect_nested, 600, 500)`.
 *
 * Draws correctly if there's a small cyan rectangle above a much larger
 * magenta rectangle. There should be no red around the cyan rectangle
 * and no green within the magenta rectangle, and everything should be
 * 50% transparent.
 *
 * Wraps [drawBackdropFilterGm] in an outer `saveLayer` with `alpha = 0.5`
 * and a non-zero clip origin, ensuring the backdrop filter is exercised
 * with a non-root device on the stack.
 */
public class BackdropImagefilterCroprectNestedGM : GM() {

    override fun getName(): String = "backdrop_imagefilter_croprect_nested"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint()
        p.alpha = (0.5f * 255 + 0.5f).toInt()
        // Non-zero origin on the parent device ensures a non-root device on the
        // stack — mirrors C++: canvas->translate(15, 10) before saveLayer.
        c.translate(15f, 10f)
        c.clipRect(SkRect.MakeWH(600f, 500f))
        c.saveLayer(null, p)
        drawBackdropFilterGm(c, 0f, 0f) { crop ->
            makeInvertFilter(crop)
        }
        c.restore()
    }
}

// ── backdrop_layer_tilemode ─────────────────────────────────────────────────

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::
 * DEF_SIMPLE_GM(backdrop_layer_tilemode, 512, 128)`.
 *
 * Draws 4 side-by-side 128×128 panels, each showing a backdrop blur applied
 * to a red-and-white stripe pattern with a different backdrop tile mode:
 * `kClamp`, `kDecal`, `kRepeat`, `kMirror`.
 *
 * The [SaveLayerRec.backdropTileMode] field is accepted but currently
 * not honoured by the CPU-raster backend (all panels will look like
 * `kClamp`). The test is still enabled — similarity will be partial.
 */
public class BackdropLayerTilemodeGM : GM() {

    override fun getName(): String = "backdrop_layer_tilemode"
    override fun getISize(): SkISize = SkISize.Make(512, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawBackdropTileMode(c, SkTileMode.kClamp)
        drawBackdropTileMode(c, SkTileMode.kDecal)
        drawBackdropTileMode(c, SkTileMode.kRepeat)
        drawBackdropTileMode(c, SkTileMode.kMirror)
    }
}

private fun drawBackdropTileMode(canvas: SkCanvas, backdropTileMode: SkTileMode) {
    canvas.save()
    // Restrict the canvas before starting a new layer to control its size.
    canvas.clipRect(SkRect.MakeIWH(128, 128))
    // This layer will be the backdrop content; without additional effects its
    // size will match the clip (128×128).
    canvas.saveLayer(null, null)
    // Fill the layer with high-frequency content (stripes of red and white).
    val fill = SkPaint()
    var y = 0
    while (y < 128) {
        fill.color = if (y % 16 != 0) SK_ColorRED else SK_ColorWHITE
        canvas.drawRect(SkRect.MakeXYWH(0f, y.toFloat(), 128f, 8f), fill)
        y += 8
    }
    // Perform a backdrop blur layer with the specified backdrop tile mode.
    val blur = SkImageFilters.Blur(32f, 32f, null)
    canvas.saveLayer(SaveLayerRec(
        bounds = null,
        paint = null,
        backdrop = blur,
        backdropTileMode = backdropTileMode,
        flags = 0,
    ))
    canvas.restore()
    canvas.restore()
    canvas.restore()

    canvas.translate(128f, 0f)
}
