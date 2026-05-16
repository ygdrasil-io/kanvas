package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorYELLOW
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/gradient_matrix.cpp`
 * (`DEF_SIMPLE_GM_BG(gradient_matrix, ...)`).
 *
 * Exercises the gradient-shader local-matrix path: each cell of an 8-wide
 * grid renders a red→yellow gradient into a 138×106 rectangle at offset
 * (43, 61). The gradient's stop points are normalized to the unit square
 * `[0,1]×[0,1]`, then transformed by a non-square local matrix
 * (`setScale(width, height); postTranslate(left, top)`) to land on the
 * rectangle — this was the regression site where x/y scaling got swapped.
 *
 * Two rows are drawn (linear and radial), each with 8 different stop
 * configurations.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG(gradient_matrix, canvas, 800, 800, 0xFFDDDDDD) {
 *     draw_gradients(canvas, &make_linear_gradient,
 *                    linearPts, std::size(linearPts));
 *     canvas->translate(0, TESTGRID_Y);
 *     draw_gradients(canvas, &make_radial_gradient,
 *                    radialPts, std::size(radialPts));
 * }
 * ```
 */
public class GradientMatrixGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "gradient_matrix"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawGradients(c, ::makeLinearGradient, linearPts, linearPts.size)
        c.translate(0f, TESTGRID_Y)
        drawGradients(c, ::makeRadialGradient, radialPts, radialPts.size)
    }

    private fun drawGradients(
        canvas: SkCanvas,
        makeShader: (Array<SkPoint>, SkMatrix) -> SkShader?,
        ptsArray: Array<Array<SkPoint>>,
        numImages: Int,
    ) {
        val rectGrad = SkRect.MakeLTRB(43f, 61f, 181f, 167f)
        // setScale(width, height) then postTranslate(left, top).
        val shaderMat = SkMatrix.MakeScale(rectGrad.width(), rectGrad.height())
            .postTranslate(rectGrad.left, rectGrad.top)

        canvas.save()
        for (i in 0 until numImages) {
            if (i % IMAGES_X == 0 && i != 0) {
                canvas.restore()
                canvas.translate(0f, TESTGRID_Y)
                canvas.save()
            }
            val paint = SkPaint()
            paint.shader = makeShader(ptsArray[i], shaderMat)
            canvas.drawRect(rectGrad, paint)
            canvas.translate(TESTGRID_X, 0f)
        }
        canvas.restore()
    }

    private fun makeLinearGradient(pts: Array<SkPoint>, localMatrix: SkMatrix): SkShader {
        return SkLinearGradient.Make(
            pts[0], pts[1],
            gColors, null,
            SkTileMode.kClamp,
            localMatrix,
        )
    }

    private fun makeRadialGradient(pts: Array<SkPoint>, localMatrix: SkMatrix): SkShader {
        val cx = (pts[0].fX + pts[1].fX) * 0.5f
        val cy = (pts[0].fY + pts[1].fY) * 0.5f
        val dx = cx - pts[0].fX
        val dy = cy - pts[0].fY
        val radius = sqrt(dx * dx + dy * dy)
        return SkRadialGradient.Make(
            SkPoint(cx, cy), radius,
            gColors, null,
            SkTileMode.kClamp,
            localMatrix,
        )
    }

    private companion object {
        const val TESTGRID_X: Float = 200f
        const val TESTGRID_Y: Float = 200f
        const val IMAGES_X: Int = 4

        val gColors: IntArray = intArrayOf(SK_ColorRED, SK_ColorYELLOW)

        const val sZero: Float = 0f
        const val sHalf: Float = 0.5f
        const val sOne: Float = 1f

        val linearPts: Array<Array<SkPoint>> = arrayOf(
            arrayOf(SkPoint(sZero, sZero), SkPoint(sOne, sZero)),
            arrayOf(SkPoint(sZero, sZero), SkPoint(sZero, sOne)),
            arrayOf(SkPoint(sOne, sZero), SkPoint(sZero, sZero)),
            arrayOf(SkPoint(sZero, sOne), SkPoint(sZero, sZero)),

            arrayOf(SkPoint(sZero, sZero), SkPoint(sOne, sOne)),
            arrayOf(SkPoint(sOne, sOne), SkPoint(sZero, sZero)),
            arrayOf(SkPoint(sOne, sZero), SkPoint(sZero, sOne)),
            arrayOf(SkPoint(sZero, sOne), SkPoint(sOne, sZero)),
        )

        val radialPts: Array<Array<SkPoint>> = arrayOf(
            arrayOf(SkPoint(sZero, sHalf), SkPoint(sOne, sHalf)),
            arrayOf(SkPoint(sHalf, sZero), SkPoint(sHalf, sOne)),
            arrayOf(SkPoint(sOne, sHalf), SkPoint(sZero, sHalf)),
            arrayOf(SkPoint(sHalf, sOne), SkPoint(sHalf, sZero)),

            arrayOf(SkPoint(sZero, sZero), SkPoint(sOne, sOne)),
            arrayOf(SkPoint(sOne, sOne), SkPoint(sZero, sZero)),
            arrayOf(SkPoint(sOne, sZero), SkPoint(sZero, sOne)),
            arrayOf(SkPoint(sZero, sOne), SkPoint(sOne, sZero)),
        )
    }
}
