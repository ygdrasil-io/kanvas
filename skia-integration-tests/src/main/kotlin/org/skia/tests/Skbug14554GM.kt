package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.core.SrcRectConstraint
import org.skia.core.withSave
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkVertices
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(skbug_14554, canvas, 310, 630)`.
 *
 * Exercises a Skia picture-optimisation bug: when a `saveLayer` with alpha
 * contains a single drawing op, Skia's picture player tries to collapse the
 * layer by pushing the alpha into the draw op itself. This is only valid when
 * the op logically touches each pixel at most once. Several ops are *not*
 * valid for this optimisation — they behave like independent repeated draws:
 *
 *  - `drawAtlas`      — each sprite may overlap another.
 *  - `drawVertices`   — each triangle may overlap another.
 *  - `drawPoints`     — points / caps may overlap.
 *  - `experimental_DrawEdgeAAImageSet` — batched image draw with per-entry
 *    alpha; the collapse would multiply alpha twice.
 *
 * The GM renders 4 rows × 2 columns:
 *  - Column 1: the optimisation fires (incorrect — too transparent).
 *  - Column 2: an injected `translate(1, 0)` prevents the collapse
 *    (correct alpha).
 *
 * **Missing API** — the fourth draw procedure uses
 * [SkCanvas.experimental_DrawEdgeAAImageSet] which is stubbed as
 * `TODO("STUB.EDGE_AA_IMAGE_SET")` and will throw [NotImplementedError]
 * at runtime. The GM is therefore `@Disabled` until that API is
 * implemented.
 *
 * TODO: missing API — [SkCanvas.experimental_DrawEdgeAAImageSet]
 * (STUB.EDGE_AA_IMAGE_SET). When implemented, remove `@Disabled` from
 * [Skbug14554Test] and enable the ratchet comparison.
 */
public class Skbug14554GM : GM() {

    override fun getName(): String = "skbug_14554"
    override fun getISize(): SkISize = SkISize.Make(310, 630)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return
        val rec = SkPictureRecorder()

        val drawProcs: List<(SkCanvas, SkImage) -> Unit> = listOf(
            ::drawAtlas,
            ::drawVertices,
            ::drawPoints,
            ::drawImageSet,
        )

        for (proc in drawProcs) {
            c.withSave {
                for (injectExtraOp in listOf(false, true)) {
                    val recordCanvas = rec.beginRecording(SkRect.MakeWH(150f, 150f))
                    recordCanvas.saveLayerAlphaf(null, 0.6f)
                    proc(recordCanvas, image)
                    if (injectExtraOp) {
                        recordCanvas.translate(1f, 0f)
                    }
                    recordCanvas.restore()

                    val pic = rec.finishRecordingAsPicture()
                    c.drawPicture(pic)
                    c.translate(160f, 0f)
                }
            }
            c.translate(0f, 160f)
        }
    }

    private fun drawAtlas(canvas: SkCanvas, image: SkImage) {
        val xforms = arrayOf(
            SkRSXform(1f, 0f, 0f, 0f),
            SkRSXform(1f, 0f, 50f, 50f),
        )
        val tex = arrayOf(
            SkRect.MakeXYWH(0f, 0f, 100f, 100f),
            SkRect.MakeXYWH(0f, 0f, 100f, 100f),
        )
        val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        canvas.drawAtlas(
            image = image,
            xform = xforms,
            src = tex,
            colors = colors,
            blendMode = SkBlendMode.kSrcIn,
            sampling = SkSamplingOptions(SkFilterMode.kNearest),
        )
    }

    private fun drawVertices(canvas: SkCanvas, image: SkImage) {
        val pts = arrayOf(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(0f, 100f),
            SkPoint.Make(100f, 100f),
            SkPoint.Make(100f, 0f),
            SkPoint.Make(100f, 100f),
            SkPoint.Make(0f, 100f),
        )
        val verts = SkVertices.MakeCopy(SkVertices.VertexMode.kTriangles, pts)
        val paint = SkPaint().apply {
            shader = image.makeShader(SkSamplingOptions(SkFilterMode.kNearest))
        }
        canvas.drawVertices(verts, SkBlendMode.kSrc, paint)
    }

    private fun drawPoints(canvas: SkCanvas, image: SkImage) {
        val pts = arrayOf(
            SkPoint.Make(50f, 50f),
            SkPoint.Make(75f, 75f),
        )
        val paint = SkPaint().apply {
            shader = image.makeShader(SkSamplingOptions(SkFilterMode.kNearest))
            strokeWidth = 100f
            strokeCap = SkPaint.Cap.kSquare_Cap
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, pts, paint)
    }

    private fun drawImageSet(canvas: SkCanvas, image: SkImage) {
        val r = SkRect.MakeWH(100f, 100f)
        val entries = arrayOf(
            SkCanvas.ImageSetEntry(image, r, r),
            SkCanvas.ImageSetEntry(image, r, r.makeOffset(50f, 50f)),
        )
        // TODO("STUB.EDGE_AA_IMAGE_SET: SkCanvas::experimental_DrawEdgeAAImageSet")
        canvas.experimental_DrawEdgeAAImageSet(
            set = entries,
            count = 2,
            dstClips = null,
            preViewMatrices = null,
            sampling = SkSamplingOptions(SkFilterMode.kNearest),
            paint = SkPaint(),
            constraint = SrcRectConstraint.kFast,
        )
    }
}
