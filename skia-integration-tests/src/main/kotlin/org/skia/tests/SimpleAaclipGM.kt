package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/simpleaaclip.cpp::SimpleClipGM`. The
 * C++ source registers three `DEF_GM` instances differing only in
 * the `SkGeomTypes` parameter :
 *
 *  - `kRect_GeomType`   → `simpleaaclip_rect` (this port)
 *  - `kPath_GeomType`   → `simpleaaclip_path` (this port)
 *  - `kAAClip_GeomType` → `simpleaaclip_aaclip` (NOT ported : the
 *    `aaclip` variant calls into Skia's internal `SkAAClip` /
 *    `SkMask` mask-builder API which `:kanvas-skia` does not yet
 *    expose. It can be added once `SkAAClip` is wired up.)
 *
 * Each pass draws two "ops" rows (`Difference`, `Intersect`) of
 * clip operations applied to a fixed base / inset pair (rect or
 * RRect path). The rect and path variants only use `clipRect` /
 * `clipPath`, both already available on [SkCanvas].
 *
 * Layout (500 × 240, BG `0xFFDDDDDD`) :
 *  - For each op iteration we draw a label ("Difference" /
 *    "Intersect"), stroke the base + inset outlines, apply the
 *    `clipRect` (or `clipPath`) op-pair, then fill a covering rect
 *    in the op-specific colour.
 *  - Between ops we translate by `(200, 0)`. The upstream wrap
 *    threshold (`xOff >= 400`) is mirrored exactly but never fires
 *    here (only two ops in the table).
 */
public sealed class SimpleAaclipBaseGM(
    private val gmName: String,
    private val usePath: Boolean,
) : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(500, 240)

    private lateinit var fBase: SkRect
    private lateinit var fRect: SkRect
    private lateinit var fBasePath: SkPath
    private lateinit var fRectPath: SkPath

    override fun onOnceBeforeDraw() {
        fBase = SkRect.MakeLTRB(100.65f, 100.65f, 150.65f, 150.65f)
        fRect = fBase.makeInset(5f, 5f).makeOffset(25f, 25f)
        fBasePath = SkPath.RRect(SkRRect.MakeRectXY(fBase, 5f, 5f))
        fRectPath = SkPath.RRect(SkRRect.MakeRectXY(fRect, 5f, 5f))
    }

    private fun drawOrig(canvas: SkCanvas) {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorBLACK
        }
        canvas.drawRect(fBase, paint)
        canvas.drawRect(fRect, paint)
    }

    private fun drawPathsOped(canvas: SkCanvas, op: SkClipOp, color: Int) {
        drawOrig(canvas)
        canvas.save()
        if (usePath) {
            canvas.clipPath(fBasePath, doAntiAlias = true)
            canvas.clipPath(fRectPath, op, doAntiAlias = true)
        } else {
            canvas.clipRect(fBase, doAntiAlias = true)
            canvas.clipRect(fRect, op, doAntiAlias = true)
        }
        val paint = SkPaint().apply { this.color = color }
        canvas.drawRect(SkRect.MakeLTRB(90f, 90f, 180f, 180f), paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val gOps = listOf(
            Triple("Difference", SkClipOp.kDifference, SK_ColorBLACK),
            Triple("Intersect", SkClipOp.kIntersect, SK_ColorRED),
        )

        val textPaint = SkPaint()
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 24f)
        var xOff = 0

        for ((label, op, color) in gOps) {
            c.drawString(label, 75f, 50f, font, textPaint)
            drawPathsOped(c, op, color)

            if (xOff >= 400) {
                c.translate(-400f, 250f)
                xOff = 0
            } else {
                c.translate(200f, 0f)
                xOff += 200
            }
        }
    }
}

/** `simpleaaclip_rect` — clip via [SkCanvas.clipRect]. */
public class SimpleAaclipRectGM : SimpleAaclipBaseGM("simpleaaclip_rect", usePath = false)

/** `simpleaaclip_path` — clip via [SkCanvas.clipPath]. */
public class SimpleAaclipPathGM : SimpleAaclipBaseGM("simpleaaclip_path", usePath = true)
