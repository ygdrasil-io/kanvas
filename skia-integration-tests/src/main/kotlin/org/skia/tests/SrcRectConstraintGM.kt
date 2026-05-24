package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkSamplingOptions

/**
 * Focused port of Skia's `gm/srcrectconstraint.cpp::SrcRectConstraintGM`.
 *
 * The source image is a green center surrounded by red guard pixels. `kStrict`
 * must keep linear-filter taps inside the source rect, while `kFast` may read
 * the red guard pixels around the subset.
 */
public class SrcRectConstraintGM : GM() {

    override fun getName(): String = "srcrectconstraint"
    override fun getISize(): SkISize = SkISize.Make(96, 48)

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        canvas.clear(SK_ColorGRAY)

        val image = makeGuardedImage()
        val src = SkRect.MakeLTRB(1f, 1f, 5f, 5f)
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)

        canvas.drawImageRect(
            image,
            src,
            SkRect.MakeXYWH(8f, 8f, 32f, 32f),
            sampling,
            constraint = SrcRectConstraint.kStrict,
        )
        canvas.drawImageRect(
            image,
            src,
            SkRect.MakeXYWH(56f, 8f, 32f, 32f),
            sampling,
            constraint = SrcRectConstraint.kFast,
        )
    }

    private fun makeGuardedImage() = SkBitmap(6, 6).apply {
        eraseColor(SK_ColorRED)
        for (y in 1 until 5) {
            for (x in 1 until 5) {
                setPixel(x, y, SK_ColorGREEN)
            }
        }
    }.asImage()
}
