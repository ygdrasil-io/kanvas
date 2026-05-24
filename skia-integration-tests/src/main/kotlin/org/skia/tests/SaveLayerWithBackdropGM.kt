package org.skia.tests

import org.graphiks.math.SkColorMatrix
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefilters.cpp::SaveLayerWithBackdropGM`
 * (registered as `savelayer_with_backdrop`, 830 x 550).
 *
 * Verifies [SkCanvas.saveLayer] interaction with a non-null backdrop
 * [SkImageFilter]. Each panel draws `mandrill_512`, clips a rounded rect,
 * seeds a saveLayer from the already-rendered backdrop through one of four
 * filters, then draws a translucent white overlay into the seeded layer.
 */
public class SaveLayerWithBackdropGM : GM() {

    override fun getName(): String = "savelayer_with_backdrop"
    override fun getISize(): SkISize = SkISize.Make(830, 550)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/mandrill_512.png") ?: return

        val cm = SkColorMatrix()
        cm.setSaturation(10f)
        val colorFilter = SkColorFilters.Matrix(cm)
        val kernel = floatArrayOf(
            4f, 0f, 4f,
            0f, -15f, 0f,
            4f, 0f, 4f,
        )
        val filters: Array<SkImageFilter?> = arrayOf(
            SkImageFilters.Blur(10f, 10f, null),
            SkImageFilters.Dilate(8, 8, null),
            SkImageFilters.MatrixConvolution(
                SkISize.Make(3, 3),
                kernel,
                gain = 1f,
                bias = 0f,
                kernelOffset = SkIPoint.Make(0, 0),
                tileMode = SkTileMode.kDecal,
                convolveAlpha = true,
                input = null,
            ),
            SkImageFilters.ColorFilter(colorFilter, null),
        )
        val xforms = arrayOf(
            Xform(1f, 1f, 0f, 0f),
            Xform(0.5f, 0.5f, 530f, 0f),
            Xform(0.25f, 0.25f, 530f, 275f),
            Xform(0.125f, 0.125f, 530f, 420f),
        )
        val sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)

        c.translate(20f, 20f)
        for (xform in xforms) {
            c.save()
            c.translate(xform.tx, xform.ty)
            c.scale(xform.sx, xform.sy)
            c.drawImage(image, 0f, 0f, sampling, null)
            drawSet(c, filters)
            c.restore()
        }
    }

    private fun drawSet(canvas: SkCanvas, filters: Array<SkImageFilter?>) {
        val rect = SkRect.MakeXYWH(30f, 30f, 200f, 200f)
        val offset = 250f
        var dx = 0f
        var dy = 0f
        for (filter in filters) {
            canvas.save()
            val rr = SkRRect.MakeRectXY(rect.makeOffset(dx, dy), 20f, 20f)
            canvas.clipRRect(rr, doAntiAlias = true)
            canvas.saveLayer(SaveLayerRec(bounds = rr.getBounds(), paint = null, backdrop = filter, flags = 0))
            canvas.drawColor(0x40FFFFFF)
            canvas.restore()
            canvas.restore()
            if (dx == 0f) {
                dx = offset
            } else {
                dx = 0f
                dy = offset
            }
        }
    }

    private data class Xform(
        val sx: Float,
        val sy: Float,
        val tx: Float,
        val ty: Float,
    )
}
