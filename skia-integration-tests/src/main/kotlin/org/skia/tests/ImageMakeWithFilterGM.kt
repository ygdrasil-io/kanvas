package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port stub for upstream Skia's `gm/imagemakewithfilter.cpp::ImageMakeWithFilterGM`
 * (`DEF_GM(return new ImageMakeWithFilterGM(...);)`, name `imagemakewithfilter`).
 *
 * 1840×860 GM that exercises [org.skia.foundation.SkImages.MakeWithFilter]
 * across 13 filter factories (color / blur / drop-shadow / offset /
 * dilate / erode / displacement / arithmetic / blend / convolution /
 * matrix-xform / lighting / tile) × 6 clip-bound rows.
 *
 * **Known limitation — minimal port** : the full filter ladder is
 * out of scope for the current batch ; we render only the per-cell
 * structural scaffolding (faded mandrill backgrounds at the cell
 * positions, plus the column headers) without actually running
 * `MakeWithFilter`. The visual output captures the layout / image
 * placement but not the filter-modified outputs themselves. Score
 * is expected to be very low — the test exists primarily to keep
 * the GM in the registered set ; a proper port will follow when
 * the missing `SkImageFilters` factories land in kanvas-skia.
 */
public class ImageMakeWithFilterGM : GM() {

    override fun getName(): String = "imagemakewithfilter"
    override fun getISize(): SkISize = SkISize.Make(1840, 860)

    private var fMainImage: SkImage? = null

    override fun onOnceBeforeDraw() {
        // Resize the mandrill_128 image to 100×100 as upstream does.
        val info = SkImageInfo.MakeN32(100, 100, SkAlphaType.kUnpremul)
        val surface = SkSurface.MakeRaster(info)
        val src = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return
        surface.canvas.drawImageRect(
            src,
            SkRect.MakeWH(src.width.toFloat(), src.height.toFloat()),
            SkRect.MakeWH(info.width.toFloat(), info.height.toFloat()),
            SkSamplingOptions.Default,
        )
        fMainImage = surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fMainImage ?: return
        val margin = 40f
        val dx = img.width.toFloat() + margin
        val dy = img.height.toFloat() + margin

        c.translate(margin, margin)
        // 6 rows × 13 filter columns ; we only render the alpha=0.3
        // background ghost (the per-filter output cells are blank —
        // see KDoc).
        val alpha = SkPaint().apply { alphaf = 0.3f }
        for (row in 0 until 6) {
            c.save()
            for (col in 0 until 13) {
                c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, alpha)
                c.translate(dx, 0f)
            }
            c.restore()
            c.translate(0f, dy)
        }
    }
}
