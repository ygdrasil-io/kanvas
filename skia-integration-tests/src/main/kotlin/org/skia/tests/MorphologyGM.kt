package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/morphology.cpp::MorphologyGM`
 * (`DEF_GM(return new MorphologyGM;)`).
 *
 * 4-row × 5-column grid of `Erode` / `Dilate` filters at various
 * radii applied to a 135×135 input bitmap. Upstream uses text
 * ("ABC" / "XYZ") as the input ; we substitute simple white
 * rectangles since text rendering is non-iso.
 *
 * **Adaptations** :
 *  - Text "ABC"/"XYZ" replaced by two solid white rectangles.
 *    Specific glyph edges of upstream are gone, but the
 *    morphology operator's behaviour on solid-shape edges is
 *    still observable.
 *  - Our `SkImageFilters.Erode` / `Dilate` factories don't
 *    accept a `cropRect` parameter ; the j&2 cells (with crop)
 *    fall back to the no-crop variant. Visible drift on rows
 *    2 and 3.
 */
public class MorphologyGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0, 0, 0))
    }

    override fun getName(): String = "morphology"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private lateinit var fImage: SkImage

    override fun onOnceBeforeDraw() {
        val surf = SkSurface.MakeRasterN32Premul(135, 135)
        val canvas = surf.canvas
        // Black background then two white rectangles substituting for "ABC"/"XYZ".
        canvas.drawColor(SkColorSetARGB(0xFF, 0, 0, 0))
        val paint = SkPaint().apply { color = SK_ColorWHITE }
        canvas.drawRect(SkRect.MakeXYWH(10f, 10f, 110f, 50f), paint)
        canvas.drawRect(SkRect.MakeXYWH(10f, 65f, 110f, 50f), paint)
        fImage = surf.makeImageSnapshot()
    }

    private fun drawClippedBitmap(canvas: SkCanvas, paint: SkPaint, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(SkRect.Make(SkIRect.MakeWH(fImage.width, fImage.height)))
        canvas.drawImage(fImage, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        data class Sample(val rx: Int, val ry: Int)
        val samples = listOf(
            Sample(0, 0),
            Sample(0, 2),
            Sample(2, 0),
            Sample(2, 2),
            Sample(25, 25),
        )
        for (j in 0 until 4) {
            for (i in samples.indices) {
                val s = samples[i]
                // Skip cropRect — our factories don't expose it.
                val paint = SkPaint().apply {
                    imageFilter = if (j and 0x01 != 0) {
                        SkImageFilters.Erode(s.rx, s.ry, null)
                    } else {
                        SkImageFilters.Dilate(s.rx, s.ry, null)
                    }
                }
                drawClippedBitmap(c, paint, i * 140, j * 140)
            }
        }
    }

    private companion object {
        private const val WIDTH: Int = 700
        private const val HEIGHT: Int = 560
    }
}
