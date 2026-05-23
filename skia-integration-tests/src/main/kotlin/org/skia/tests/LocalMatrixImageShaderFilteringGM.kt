package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/localmatriximageshader.cpp::localmatriximageshader_filtering`
 * (`DEF_SIMPLE_GM(localmatriximageshader_filtering, …, 256, 256)`).
 *
 * Tests that filtering decisions (e.g. bicubic for upscale) are made correctly when
 * the scale comes from a local matrix shader.
 *
 * A 256×256 mandrill image is displayed through a Mitchell-bicubic shader wrapped
 * in a 2× scale local matrix. The key property tested is that the sampling quality
 * (bicubic / Mitchell) is selected correctly even when the upscale factor originates
 * from the local matrix rather than from the canvas CTM.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(localmatriximageshader_filtering, canvas, 256, 256) {
 *     auto image = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *     SkPaint p;
 *     SkMatrix m = SkMatrix::Scale(2, 2);
 *     p.setShader(image->makeShader(SkSamplingOptions(SkCubicResampler::Mitchell()))
 *                 ->makeWithLocalMatrix(m));
 *     canvas->drawRect(SkRect::MakeXYWH(0, 0, 256, 256), p);
 * }
 * ```
 */
public class LocalMatrixImageShaderFilteringGM : GM() {
    override fun getName(): String = "localmatriximageshader_filtering"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val image = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return
        val m = SkMatrix.MakeScale(2f, 2f)
        val shader = image
            .makeShader(SkSamplingOptions(SkCubicResampler.Mitchell))
            .makeWithLocalMatrix(m)
        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeXYWH(0f, 0f, 256f, 256f), paint)
    }
}
