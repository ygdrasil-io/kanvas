package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/spritebitmap.cpp::drawimagerect_filter` (DEF_SIMPLE_GM_BG, 180 × 60,
 * white background).
 *
 * Background: b/41322892 — the CPU raster back-end tries to detect when an image draw
 * lands perfectly on pixel-centres and switches to a fast sprite-blitting path.  That
 * path used to ignore the linear filter and snap to nearest, producing a sharp black-
 * and-white checkerboard instead of the expected grey average.  This GM verifies that
 * ALL three drawing methods honour linear filtering when given a 0.5-pixel sub-pixel
 * offset:
 *
 *  1. `drawImage` with an explicit (0.5, 0.5) translation
 *  2. `drawImageRect` with a 0.5-pixel-offset `dst` rect
 *  3. `drawRect` with an image shader carrying a 0.5-px `SkMatrix::Translate` local matrix
 *
 * The correct result is three 50 × 50 patches that all average to grey, not a
 * pixel-snapped checkerboard.  A 1-px checker grid on a 50 × 50 black/white source
 * image (cell size = 1) is used to make filtering artefacts visible.
 */
public class DrawImagerectFilterGM : GM() {

    init {
        // DEF_SIMPLE_GM_BG background colour is SK_ColorWHITE — the default, but
        // set explicitly here to document the upstream intent.
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "drawimagerect_filter"

    override fun getISize(): SkISize = SkISize.Make(180, 60)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Upstream: `ToolUtils::create_checkerboard_image(50, 50, SK_ColorWHITE, SK_ColorBLACK, 1)`
        // Produces a 50×50 image with 1-px alternating white/black cells.
        val image = ToolUtils.create_checkerboard_image(50, 50, SK_ColorWHITE, SK_ColorBLACK, 1)
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)

        c.translate(5f, 5f)

        // 1. drawImage — subpixel offset via the canvas translation.
        c.drawImage(image, 0.5f, 0.5f, sampling)

        c.translate(60f, 0f)

        // 2. drawImageRect — dst rect shifted by 0.5 px.
        c.drawImageRect(
            image,
            SkRect.MakeLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            SkRect.MakeLTRB(0.5f, 0.5f, image.width + 0.5f, image.height + 0.5f),
            sampling,
        )

        // 3. drawRect with an image shader using a 0.5-px local-matrix translate.
        c.translate(60f, 0f)
        val offset = SkMatrix(tx = 0.5f, ty = 0.5f)
        val shader = image.makeShader(sampling, offset)
        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
    }
}
