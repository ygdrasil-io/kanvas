package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTrimPathEffect
import org.skia.utils.SkParsePath

/**
 * Port of Skia's `gm/trimpatheffect.cpp::TrimGM`
 * (`trimpatheffect`, 1400 x 1000).
 */
public class TrimGM : GM() {
    private val paths: List<SkPath> = listOfNotNull(
        SkParsePath.FromSVGString(
            "M   0,100 C  10, 50 190, 50 200,100" +
                "M 200,100 C 210,150 390,150 400,100" +
                "M 400,100 C 390, 50 210, 50 200,100" +
                "M 200,100 C 190,150  10,150   0,100",
        ),
        SkParsePath.FromSVGString(
            "M   0, 75 L 200, 75" +
                "M 200, 91 L 200, 91" +
                "M 200,108 L 200,108" +
                "M 200,125 L 400,125",
        ),
        SkParsePath.FromSVGString(
            "M   0,100 L  50, 50" +
                "M  50, 50 L 150,150" +
                "M 150,150 L 250, 50" +
                "M 250, 50 L 350,150" +
                "M 350,150 L 400,100",
        ),
    )

    override fun getName(): String = "trimpatheffect"

    override fun getISize(): SkISize = SkISize.Make(1400, 1000)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val hairlinePaint = SkPaint().apply {
            isAntiAlias = true
            setStroke(true)
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeWidth = 2f
        }
        val normalPaint = hairlinePaint.copy().apply {
            strokeWidth = 10f
            color = 0x8000FF00.toInt()
        }
        val invertedPaint = normalPaint.copy().apply {
            color = 0x80FF0000.toInt()
        }

        for (offset in OFFSETS) {
            val start = offset[0]
            val stop = offset[1]

            normalPaint.pathEffect = SkTrimPathEffect.Make(
                start,
                stop,
                SkTrimPathEffect.Mode.kNormal,
            )
            invertedPaint.pathEffect = SkTrimPathEffect.Make(
                start,
                stop,
                SkTrimPathEffect.Mode.kInverted,
            )

            c.save()
            for (path in paths) {
                c.drawPath(path, normalPaint)
                c.drawPath(path, invertedPaint)
                c.drawPath(path, hairlinePaint)
                c.translate(CELL_WIDTH, 0f)
            }
            c.restore()
            c.translate(0f, CELL_HEIGHT)
        }
    }

    public companion object {
        private const val CELL_WIDTH = 440f
        private const val CELL_HEIGHT = 150f
        private val OFFSETS = arrayOf(
            floatArrayOf(-0.33f, -0.66f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, 0.25f),
            floatArrayOf(0.25f, 0.75f),
            floatArrayOf(0.75f, 1f),
            floatArrayOf(1f, 0.75f),
        )
    }
}
