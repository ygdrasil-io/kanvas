package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix

/**
 * Port of Skia's `gm/manypathatlases.cpp::ManyPathAtlasesGM` (128 × 128).
 *
 * Upstream registers two variants — `manypathatlases_128` and
 * `manypathatlases_2048` — both rendering an identical scene ; the
 * difference (atlas max size) only affects the GPU path-rendering
 * backend. On the raster backend the two variants are bit-identical, so
 * we surface the `128` variant as the default and provide a `2048`
 * subclass for completeness.
 *
 * Scene :
 *  1. Clear to yellow.
 *  2. Apply 4 cumulative `kDifference` clips that carve out four
 *     rotated cubic teardrops around centre (64, 70) at 30°, 60°, 90°,
 *     120° (= 128° / 158° / 188° / 218° using upstream's base + 128
 *     offset). Each carved region becomes transparent.
 *  3. Stroke + fill a 9-point rounded-rect-ish path with teal
 *     `(0.03, 0.91, 0.87, 1.0)`.
 */
public open class ManyPathAtlasesGM(private val fMaxAtlasSize: Int = 128) : GM() {

    override fun getName(): String = "manypathatlases_$fMaxAtlasSize"
    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorYELLOW)

        // Base teardrop clip path, translated to centre at (64, 70).
        val clip = SkPathBuilder()
            .moveTo(-50f, 20f)
            .cubicTo(-50f, -20f, 50f, -20f, 50f, 40f)
            .cubicTo(20f, 0f, -20f, 0f, -50f, 20f)
            .detach()
            .makeTransform(SkMatrix.MakeTrans(64f, 70f))

        for (i in 0 until 4) {
            val rotated = clip.makeTransform(
                SkMatrix.MakeRotate(30f * i + 128f, 64f, 70f),
            )
            c.clipPath(rotated, SkClipOp.kDifference, doAntiAlias = true)
        }

        val path = SkPathBuilder()
            .moveTo(20f, 0f)
            .lineTo(108f, 0f).cubicTo(108f, 20f, 108f, 20f, 128f, 20f)
            .lineTo(128f, 108f).cubicTo(108f, 108f, 108f, 108f, 108f, 128f)
            .lineTo(20f, 128f).cubicTo(20f, 108f, 20f, 108f, 0f, 108f)
            .lineTo(0f, 20f).cubicTo(20f, 20f, 20f, 20f, 20f, 0f)
            .detach()

        val teal = SkPaint().apply {
            color4f = SkColor4f(0.03f, 0.91f, 0.87f, 1f)
            isAntiAlias = true
        }
        c.drawPath(path, teal)
    }
}

/** Convenience subclass : `manypathatlases_2048` — visually identical on raster. */
public class ManyPathAtlases2048GM : ManyPathAtlasesGM(2048)
