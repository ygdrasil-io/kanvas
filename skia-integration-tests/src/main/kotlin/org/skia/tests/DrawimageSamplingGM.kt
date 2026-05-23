package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of `DEF_SIMPLE_GM(drawimage_sampling, canvas, 500, 500)` from
 * `gm/image_shader.cpp` (line 179).
 *
 * Creates a 256×256 checkerboard image (7-px tiles, black/white),
 * builds a default mip pyramid with [withDefaultMipmaps], then draws a
 * 3 × 2 grid (mip-mode × filter-mode) exercising three render paths per
 * cell :
 *  1. [SkCanvas.drawImage] with the src→dst matrix applied via canvas concat.
 *  2. Image shader on a plain [SkCanvas.drawRect] (same matrix as local-matrix).
 *  3. [SkCanvas.drawImageRect] mapping `src` → `dst`.
 *
 * The cells are separated by 4-px gaps. Output size: 500 × 500.
 *
 * All APIs exercised here are fully implemented in `:kanvas-skia` — no
 * STUB TODO is required.
 */
public class DrawimageSamplingGM : GM() {

    override fun getName(): String = "drawimage_sampling"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val n = 256
        val kScale = 1.0f / 6
        val dst = SkRect.MakeLTRB(0f, 0f, kScale * n, kScale * n)

        // Upstream: auto img = make_checker_img(N, N, SK_ColorBLACK, SK_ColorWHITE, 7)
        //                             ->withDefaultMipmaps();
        val img = ToolUtils.create_checkerboard_image(n, n, SK_ColorBLACK, SK_ColorWHITE, 7)
            .withDefaultMipmaps()

        val src = SkRect.MakeIWH(img.width, img.height)

        // Upstream: SkMatrix mx = SkMatrix::RectToRectOrIdentity(src, dst);
        val mx = SkMatrix.RectToRectOrIdentity(src, dst)

        val paint = SkPaint()

        for (mm in SkMipmapMode.values()) {
            for (fm in SkFilterMode.values()) {
                val sampling = SkSamplingOptions(fm, mm)

                c.save()

                // 1. drawImage with local matrix applied via canvas concat
                c.save()
                c.concat(mx)
                c.drawImage(img, 0f, 0f, sampling)
                c.restore()

                c.translate(dst.width() + 4f, 0f)

                // 2. Image shader drawn on a rect
                paint.shader = img.makeShader(
                    SkTileMode.kClamp, SkTileMode.kClamp, sampling, mx,
                )
                c.drawRect(dst, paint)

                c.translate(dst.width() + 4f, 0f)

                // 3. drawImageRect src -> dst (kFast constraint mirrors upstream)
                c.drawImageRect(
                    img, src, dst, sampling, null,
                    SrcRectConstraint.kFast,
                )

                c.restore()

                c.translate(0f, dst.height() + 8f)
            }
        }

        paint.shader = null
    }
}
