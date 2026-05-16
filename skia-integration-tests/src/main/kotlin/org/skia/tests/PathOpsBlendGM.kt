package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.pathops.SkPathOp
import org.skia.pathops.SkPathOps

/**
 * Port of Skia's `gm/pathopsblend.cpp::pathops_blend` (130 × 310).
 *
 * Side-by-side comparison of two ways to compute a boolean shape :
 *  - **left column** : `SkPathOps.Op` produces a new [SkPath] which is
 *    then drawn directly with the destination paint.
 *  - **right column** : a coverage-channel emulation via
 *    `saveLayer` + per-op blend mode, where the layer alpha encodes
 *    the desired mask and a final `kSrcIn` paint paints it blue.
 *
 * The two columns should agree pixel-for-pixel ; the GM is named
 * "blend" because the right column proves the equivalence between
 * the algebraic `Op` and a chosen blend-mode trick. Useful as a
 * checkerboard-backgrounded sanity check that `Op` results render
 * the same as the layered blend rasteriser.
 *
 * 4 ops covered (kReverseDifference is omitted upstream — see the
 * comment in `kOps` — since it's just `kDifference` with operands
 * swapped).
 */
public class PathOpsBlendGM : GM() {

    private val ops: List<SkPathOp> = listOf(
        SkPathOp.kDifference,
        SkPathOp.kIntersect,
        SkPathOp.kUnion,
        SkPathOp.kXOR,
    )

    override fun getName(): String = "pathops_blend"
    override fun getISize(): SkISize = SkISize.Make(130, 60 * ops.size + 60 + 10)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Default ToolUtils::draw_checkerboard : 8 px tiles in two greys.
        drawCheckerboard(c, 0xFF999999u.toInt(), 0xFF666666u.toInt(), 8)

        val p1 = cross()
        val p2 = circle()
        // kIntersect's blend-mode emulation needs the inverse-fill
        // variant of p2 (per `op_blend_mode` upstream).
        val p2inv = p2.makeToggleInverseFillType()

        val paint = SkPaint().apply { isAntiAlias = true }
        c.translate(10f, 10f)

        // First row : the two source paths side by side.
        c.save()
        c.drawPath(p1, paint)
        c.translate(60f, 0f)
        c.drawPath(p2, paint)
        c.restore()
        c.translate(0f, 60f)

        for (op in ops) {
            c.save()

            // Left column : Op-as-path.
            val opPath = SkPathOps.Op(p1, p2, op)
            if (opPath != null) {
                c.drawPath(opPath, paint)
            }

            c.translate(60f, 0f)

            // Right column : layered blend-mode emulation.
            run {
                val blend = opBlendMode(op)
                c.saveLayer(SkRect.MakeWH(50f, 50f), null)
                val p = SkPaint().apply { isAntiAlias = true }

                // 1) SrcOver paints p1 into the layer (alpha = mask of p1).
                p.blendMode = SkBlendMode.kSrcOver
                c.drawPath(p1, p)

                // 2) Per-op blend with p2 (or p2inv) updates the alpha mask.
                p.blendMode = blend.mode
                c.drawPath(if (blend.inverse) p2inv else p2, p)

                // 3) SrcIn paints SK_ColorBLUE everywhere the layer has
                //    coverage, leaving the mask intact.
                p.blendMode = SkBlendMode.kSrcIn
                p.color = SK_ColorBLUE
                c.drawPaint(p)
                c.restore()
            }

            c.restore()
            c.translate(0f, 60f)
        }
    }

    /**
     * 50×50 plus-sign : two overlapping rectangles forming a `+`.
     * Mirrors the `cross()` helper at the top of `pathopsblend.cpp`.
     */
    private fun cross(): SkPath = SkPathBuilder()
        .addRect(SkRect.MakeLTRB(15f, 0f, 35f, 50f))
        .addRect(SkRect.MakeLTRB(0f, 15f, 50f, 35f))
        .detach()

    private fun circle(): SkPath = SkPath.Circle(25f, 25f, 20f)

    private data class OpAsBlend(val mode: SkBlendMode, val inverse: Boolean = false)

    /**
     * Mirror of `op_blend_mode` (`pathopsblend.cpp:46`). Maps each
     * supported [SkPathOp] to the (blend-mode, inverse-flag) pair
     * that emulates it via the saveLayer trick.
     */
    private fun opBlendMode(op: SkPathOp): OpAsBlend = when (op) {
        SkPathOp.kDifference -> OpAsBlend(SkBlendMode.kClear)
        SkPathOp.kIntersect -> OpAsBlend(SkBlendMode.kClear, inverse = true)
        SkPathOp.kUnion -> OpAsBlend(SkBlendMode.kPlus)
        SkPathOp.kXOR -> OpAsBlend(SkBlendMode.kXor)
        else -> OpAsBlend(SkBlendMode.kSrcOver)
    }

    /**
     * Inline `ToolUtils::draw_checkerboard(canvas, c1, c2, size)`.
     * Skia's helper uses a shader + `drawPaint` ; we expand it into
     * per-tile `drawRect` since our raster sink doesn't ship the
     * checker shader. Visually identical at integer-aligned sizes.
     */
    private fun drawCheckerboard(canvas: SkCanvas, c1: SkColor, c2: SkColor, size: Int) {
        val solid = SkPaint().apply { isAntiAlias = false }
        val w = getISize().width
        val h = getISize().height
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cx = x / size
                val cy = y / size
                solid.color = if (((cx + cy) and 1) == 0) c1 else c2
                canvas.drawRect(
                    SkRect.MakeLTRB(
                        x.toFloat(), y.toFloat(),
                        (x + size).toFloat(), (y + size).toFloat(),
                    ),
                    solid,
                )
                x += size
            }
            y += size
        }
    }
}
