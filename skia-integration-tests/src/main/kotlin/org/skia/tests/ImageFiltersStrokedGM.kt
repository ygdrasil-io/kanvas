package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's [`gm/imagefiltersstroked.cpp`](https://github.com/google/skia/blob/main/gm/imagefiltersstroked.cpp)
 * (`imagefiltersstroked`, 860 × 500).
 *
 * Draws three stroked primitives (line, rect, circle) under four
 * image-filters (blur / drop-shadow / offset / matrix-resize) on a
 * black background. Layout matches upstream: each row is a primitive,
 * each column a filter.
 */
public class ImageFiltersStrokedGM : GM() {

    init {
        setBGColor(0x00000000)
    }

    override fun getName(): String = "imagefiltersstroked"
    override fun getISize(): SkISize = SkISize.Make(860, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val resizeMatrix = SkMatrix.MakeScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y)

        val filters: Array<SkImageFilter?> = arrayOf(
            SkImageFilters.Blur(5f, 5f, null),
            SkImageFilters.DropShadow(10f, 10f, 3f, 3f, SK_ColorGREEN, null),
            SkImageFilters.Offset(-16f, 32f, null),
            SkImageFilters.MatrixTransform(resizeMatrix, SkSamplingOptions.Default, null),
        )

        val drawProcs: Array<(SkCanvas, SkRect, SkPaint) -> Unit> = arrayOf(
            ::drawLine,
            ::drawRect,
            ::drawCircle,
        )

        val r = SkRect.MakeWH(64f, 64f)
        val margin = 32f
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
            strokeWidth = 10f
            style = SkPaint.Style.kStroke_Style
        }

        for (i in drawProcs.indices) {
            c.translate(0f, margin)
            c.save()
            for (j in filters.indices) {
                c.translate(margin, 0f)
                c.save()
                if (j == 2) {
                    c.translate(16f, -32f)
                } else if (j == 3) {
                    c.scale(1f / RESIZE_FACTOR_X, 1f / RESIZE_FACTOR_Y)
                }
                paint.imageFilter = filters[j]
                drawProcs[i](c, r, paint)
                c.restore()
                c.translate(r.width() + margin, 0f)
            }
            c.restore()
            c.translate(0f, r.height())
        }
    }

    private companion object {
        const val RESIZE_FACTOR_X: Float = 2f
        const val RESIZE_FACTOR_Y: Float = 5f

        fun drawCircle(canvas: SkCanvas, r: SkRect, paint: SkPaint) {
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 2f / 5f, paint)
        }

        fun drawLine(canvas: SkCanvas, r: SkRect, paint: SkPaint) {
            canvas.drawLine(r.left, r.bottom, r.right, r.top, paint)
        }

        fun drawRect(canvas: SkCanvas, r: SkRect, paint: SkPaint) {
            canvas.drawRect(r, paint)
        }
    }
}
