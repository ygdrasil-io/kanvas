package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of `DEF_SIMPLE_GM(imageshader_tinyscale, canvas, 1000, 1000)` from
 * `gm/image_shader.cpp` (line 253).
 *
 * Loads `images/gainmap_gcontainer_only.jpg` (a 128×128 image with
 * red/black/green/blue quadrants) and draws it via an image-shader with
 * a very small scale factor (0.01). In `kClamp` mode the viewport should
 * fill with four-coloured quadrants rather than repeating patterns.
 *
 * The local matrix is `Translate(500, 500) * Scale(0.01, 0.01)` — equivalent
 * to upstream's `SkMatrix::Translate(500, 500) * SkMatrix::Scale(kScale, kScale)`.
 *
 * If the resource image is unavailable (classpath miss), the GM draws
 * nothing — matching upstream's `auto img = …; if (!img) return;` guard.
 *
 * All APIs exercised here are fully implemented in `:kanvas-skia` — no
 * STUB TODO is required.
 */
public class ImageshaderTinyscaleGM : GM() {

    override fun getName(): String = "imageshader_tinyscale"
    override fun getISize(): SkISize = SkISize.Make(1000, 1000)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val img = ToolUtils.GetResourceAsImage("images/gainmap_gcontainer_only.jpg") ?: return

        // Upstream: const auto m = SkMatrix::Translate(500, 500) * SkMatrix::Scale(kScale, kScale);
        // MakeTrans and MakeScale are the Kotlin equivalents.
        val kScale = 0.01f
        val m = SkMatrix.MakeTrans(500f, 500f) * SkMatrix.MakeScale(kScale, kScale)

        val p = SkPaint()
        p.shader = img.makeShader(
            tileX = SkTileMode.kClamp,
            tileY = SkTileMode.kClamp,
            sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNone),
            localMatrix = m,
        )
        c.drawPaint(p)
    }
}
