package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/aaxfermodes.cpp::AAXfermodesGM` (`aaxfermodes`,
 * 984 × 625).
 *
 * Verifies AA on every Porter-Duff coefficient mode and every "Advanced"
 * (separable + HSL) mode, against both an opaque-input shape colour
 * (`0xff00ffff`) and a translucent-input one (`0x82ff0080`). Rendered as
 * two columns:
 *  - **Porter Duff** — modes 0..14 (Clear..Screen).
 *  - **Advanced** — modes 15..28 (Overlay..Luminosity).
 *
 * Each row × column shows 4 shapes (square, diamond, oval, concave path)
 * laid out left→right per "Src" sub-column ("Src Unknown" then "Src
 * Opaque"). The shapes paint with the row's blend mode over a partly-
 * transparent background colour (`0xc8d2b887`), itself drawn over an
 * outer checkerboard.
 *
 * Compositing pipeline upstream — three "passes" of the same iteration:
 *  1. **Checkerboard pass** — at the root canvas state, each cell `clipRect`s
 *     a shape-sized square and fills it with a 10-px white/grey checker.
 *  2. `saveLayer(nullptr, nullptr)` opens an offscreen layer covering the
 *     whole canvas.
 *  3. **Background pass** — same clipped cells, but `drawColor(kBGColor,
 *     kSrc)` paints the translucent BG colour into each cell.
 *  4. **Shape pass** — same iteration, but draws the actual shape
 *     (with the under-test blend mode) over the BG.
 *  5. `restore()` flattens the layer (kSrcOver) onto the checkerboard.
 *
 * The `kPlus` mode dim-paint protection is preserved verbatim: the
 * BG and shape's RGBA channels can't sum past 255 without saturating, so
 * upstream pre-attenuates both via a `kDstIn` rect drawn just before the
 * shape iteration. Without this, the kPlus row would saturate to white
 * everywhere — visually identical to a `kScreen` row, hiding the actual
 * mode behaviour.
 */
public class AAXfermodesGM : GM() {

    override fun getName(): String = "aaxfermodes"

    override fun getISize(): SkISize {
        // 2 * kMargin + 2 * kXfermodeTypeSpacing -
        //     (kXfermodeTypeSpacing - (kLabelSpacing + 2 * kPaintSpacing))
        // = 36 + 996 - (498 - 450) = 984
        val width = 2 * kMargin + 2 * kXfermodeTypeSpacing -
            (kXfermodeTypeSpacing - (kLabelSpacing + 2 * kPaintSpacing))
        // 2 * kMargin + kTitleSpacing + kSubtitleSpacing
        //     + (1 + kLastCoeffMode.ordinal) * kShapeSpacing
        // = 36 + 27 + 22 + 15 * 36 = 625
        val height = 2 * kMargin + kTitleSpacing + kSubtitleSpacing +
            (1 + SkBlendMode.kLastCoeffMode.ordinal) * kShapeSpacing
        return SkISize.Make(width, height)
    }

    private lateinit var labelFont: SkFont
    private lateinit var oval: SkPath
    private lateinit var concave: SkPath

    private fun ensureOnceBeforeDraw() {
        if (::labelFont.isInitialized) return
        labelFont = SkFont(ToolUtils.DefaultPortableTypeface(), 5f * kShapeSize / 8f).apply {
            isSubpixel = true
        }

        // Oval: 4-vertex quad-Bezier closed contour (matches upstream
        // SkPathBuilder().moveTo/quadTo/quadTo).
        val radius = -1.4f * kShapeSize / 2f       // negative on purpose (matches upstream)
        val pts = arrayOf(
            -radius to 0f,
            0f to -1.33f * radius,
            radius to 0f,
            0f to 1.33f * radius,
        )
        oval = SkPathBuilder()
            .moveTo(pts[0].first, pts[0].second)
            .quadTo(pts[1].first, pts[1].second, pts[2].first, pts[2].second)
            .quadTo(pts[3].first, pts[3].second, pts[0].first, pts[0].second)
            .detach()

        // Concave 4-pointed star: each quadratic pinches at the origin
        // (off-curve control = (0, 0)) between adjacent cardinal tips.
        // Upstream reuses the same negative `radius` from the oval, hence
        // the `-radius` / `radius` mix below.
        concave = SkPathBuilder()
            .moveTo(-radius, 0f)
            .quadTo(0f, 0f, 0f, -radius)
            .quadTo(0f, 0f, radius, 0f)
            .quadTo(0f, 0f, 0f, radius)
            .quadTo(0f, 0f, -radius, 0f)
            .close()
            .detach()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        ensureOnceBeforeDraw()

        drawPass(c, Pass.Checkerboard)

        c.saveLayer(null, null)

        c.translate(kMargin.toFloat(), kMargin.toFloat())
        drawPass(c, Pass.Background)

        // Title strip, drawn between the BG pass and the shape pass so
        // we don't have to manage another pass enum just for two strings.
        val titleFont = SkFont(labelFont).apply {
            size = 9f * labelFont.size / 8f
            isEmbolden = true
        }
        SkTextUtils.DrawString(
            c, "Porter Duff",
            kLabelSpacing + 4f * kShapeTypeSpacing,
            kTitleSpacing / 2f + titleFont.size / 3f,
            titleFont, SkPaint(),
            SkTextUtils.Align.kCenter_Align,
        )
        SkTextUtils.DrawString(
            c, "Advanced",
            kXfermodeTypeSpacing + kLabelSpacing + 4f * kShapeTypeSpacing,
            kTitleSpacing / 2f + titleFont.size / 3f,
            titleFont, SkPaint(),
            SkTextUtils.Align.kCenter_Align,
        )

        drawPass(c, Pass.Shape)
        c.restore()
    }

    private fun drawPass(canvas: SkCanvas, pass: Pass) {
        val cellClip = SkRect.MakeLTRB(
            -kShapeSize * 11f / 16f, -kShapeSize * 11f / 16f,
            kShapeSize * 11f / 16f, kShapeSize * 11f / 16f,
        )

        canvas.save()
        if (pass == Pass.Checkerboard) {
            canvas.translate(kMargin.toFloat(), kMargin.toFloat())
        }
        canvas.translate(0f, kTitleSpacing.toFloat())

        for (xfermodeSet in 0..1) {
            val firstMode = (SkBlendMode.kLastCoeffMode.ordinal + 1) * xfermodeSet
            canvas.save()

            if (pass == Pass.Shape) {
                SkTextUtils.DrawString(
                    canvas, "Src Unknown",
                    kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2f,
                    kSubtitleSpacing / 2f + labelFont.size / 3f,
                    labelFont, SkPaint(),
                    SkTextUtils.Align.kCenter_Align,
                )
                SkTextUtils.DrawString(
                    canvas, "Src Opaque",
                    kLabelSpacing + kShapeTypeSpacing * 1.5f + kShapeSpacing / 2f + kPaintSpacing,
                    kSubtitleSpacing / 2f + labelFont.size / 3f,
                    labelFont, SkPaint(),
                    SkTextUtils.Align.kCenter_Align,
                )
            }

            canvas.translate(0f, kSubtitleSpacing + kShapeSpacing / 2f)

            var m = 0
            while (m <= SkBlendMode.kLastCoeffMode.ordinal) {
                val modeIndex = firstMode + m
                if (modeIndex > SkBlendMode.kLastMode.ordinal) break
                val mode = SkBlendMode.entries[modeIndex]
                canvas.save()

                if (pass == Pass.Shape) {
                    drawModeName(canvas, mode)
                }
                canvas.translate(kLabelSpacing + kShapeSpacing / 2f, 0f)

                for (colorIdx in kShapeColors.indices) {
                    val paint = SkPaint()
                    setupShapePaint(canvas, kShapeColors[colorIdx], mode, paint)
                    canvas.save()

                    for (shapeIdx in 0..kLast_Shape) {
                        if (pass != Pass.Shape) {
                            canvas.save()
                            canvas.clipRect(cellClip)
                            if (pass == Pass.Checkerboard) {
                                drawCheckerboard(canvas, 0xFFFFFFFF.toInt(), 0xFFC6C3C6.toInt(), 10)
                            } else {
                                canvas.drawColor(kBGColor, SkBlendMode.kSrc)
                            }
                            canvas.restore()
                        } else {
                            drawShape(canvas, shapeIdx, paint, mode)
                        }
                        canvas.translate(kShapeTypeSpacing.toFloat(), 0f)
                    }

                    canvas.restore()
                    canvas.translate(kPaintSpacing.toFloat(), 0f)
                }

                canvas.restore()
                canvas.translate(0f, kShapeSpacing.toFloat())
                m++
            }

            canvas.restore()
            canvas.translate(kXfermodeTypeSpacing.toFloat(), 0f)
        }
        canvas.restore()
    }

    private fun drawModeName(canvas: SkCanvas, mode: SkBlendMode) {
        SkTextUtils.DrawString(
            canvas, SkBlendMode_Name(mode),
            kLabelSpacing - kShapeSize / 4f,
            labelFont.size / 4f,
            labelFont, SkPaint(),
            SkTextUtils.Align.kRight_Align,
        )
    }

    private fun setupShapePaint(canvas: SkCanvas, color: SkColor, mode: SkBlendMode, paint: SkPaint) {
        paint.color = color
        if (mode != SkBlendMode.kPlus) return

        // Detect overflow on any channel; if any channel sums past 255 we'd
        // saturate to white and lose mode visibility — pre-attenuate via a
        // kDstIn rect so the BG colour and shape alpha stay in range.
        val maxSum = maxOf(
            SkColorGetA(kBGColor) + SkColorGetA(color),
            SkColorGetR(kBGColor) + SkColorGetR(color),
            SkColorGetG(kBGColor) + SkColorGetG(color),
            SkColorGetB(kBGColor) + SkColorGetB(color),
        )
        if (maxSum <= 255) return

        val dimPaint = SkPaint().apply {
            isAntiAlias = false
            blendMode = SkBlendMode.kDstIn
        }
        if (SkColorGetA(paint.color) != 0xFF) {
            dimPaint.color = SkColorSetARGB(255 * 255 / maxSum, 0, 0, 0)
            paint.color = SkColorSetARGB(
                255 * SkColorGetA(paint.color) / maxSum,
                SkColorGetR(paint.color),
                SkColorGetG(paint.color),
                SkColorGetB(paint.color),
            )
        } else {
            // Just clear the dst — preserve the paint's opacity.
            dimPaint.color = SkColorSetARGB(0, 0, 0, 0)
        }
        canvas.drawRect(
            SkRect.MakeLTRB(
                -kShapeSpacing / 2f, -kShapeSpacing / 2f,
                kShapeSpacing / 2f + 3f * kShapeTypeSpacing, kShapeSpacing / 2f,
            ),
            dimPaint,
        )
    }

    private fun drawShape(canvas: SkCanvas, shapeIdx: Int, paint: SkPaint, mode: SkBlendMode) {
        val shapePaint = paint.copy().apply {
            isAntiAlias = shapeIdx != kSquare_Shape
            blendMode = mode
        }
        val s = kShapeSize / 2f
        val rect = SkRect.MakeLTRB(-s, -s, s, s)
        when (shapeIdx) {
            kSquare_Shape -> canvas.drawRect(rect, shapePaint)
            kDiamond_Shape -> {
                canvas.save()
                canvas.rotate(45f)
                canvas.drawRect(rect, shapePaint)
                canvas.restore()
            }
            kOval_Shape -> {
                canvas.save()
                canvas.rotate(((511 * mode.ordinal + 257) % 360).toFloat())
                canvas.drawPath(oval, shapePaint)
                canvas.restore()
            }
            kConcave_Shape -> canvas.drawPath(concave, shapePaint)
        }
    }

    /**
     * Inline checkerboard fill mirroring `ToolUtils::draw_checkerboard(canvas,
     * c1, c2, size)` upstream. Anchored to canvas-space (0, 0): the cell
     * `(0..size, 0..size)` carries [c2] (matching the eraseArea calls in
     * `create_checkerboard_shader`), the cell `(size..2*size, 0..size)`
     * carries [c1], etc. — same parity as a `kRepeat` × `kRepeat` shader
     * fed from a 2×2-cell source bitmap with c2 on the main diagonal and
     * c1 on the anti-diagonal.
     *
     * Drawn cell-by-cell rather than via a real bitmap shader — both
     * produce the same pixels under integer-aligned pixel coverage, but
     * the inline version keeps the BG pass independent from the bitmap-
     * shader F16 follow-ups (Phase 5g).
     */
    private fun drawCheckerboard(canvas: SkCanvas, c1: SkColor, c2: SkColor, size: Int) {
        val solid = SkPaint().apply { isAntiAlias = false }
        val span = kShapeSize  // > cellClip half-extent (15.125 px)
        val xMin = -span - size
        val xMax = span + size
        val yMin = -span - size
        val yMax = span + size
        var y = Math.floorDiv(yMin, size) * size
        while (y < yMax) {
            var x = Math.floorDiv(xMin, size) * size
            while (x < xMax) {
                val cx = Math.floorDiv(x, size)
                val cy = Math.floorDiv(y, size)
                solid.color = if (((cx + cy) and 1) == 0) c2 else c1
                canvas.drawRect(
                    SkRect.MakeLTRB(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    solid,
                )
                x += size
            }
            y += size
        }
    }

    private enum class Pass { Checkerboard, Background, Shape }

    private companion object {
        const val kShapeSize: Int = 22
        const val kShapeSpacing: Int = 36
        const val kShapeTypeSpacing: Int = 4 * kShapeSpacing / 3            // 48
        const val kPaintSpacing: Int = 4 * kShapeTypeSpacing                // 192
        const val kLabelSpacing: Int = 3 * kShapeSize                       // 66
        const val kMargin: Int = kShapeSpacing / 2                          // 18
        const val kXfermodeTypeSpacing: Int =
            kLabelSpacing + 2 * kPaintSpacing + kShapeTypeSpacing            // 498
        const val kTitleSpacing: Int = 3 * kShapeSpacing / 4                // 27
        const val kSubtitleSpacing: Int = 5 * kShapeSpacing / 8             // 22

        const val kBGColor: SkColor = 0xC8D2B887.toInt()

        // Shape colour palette: (translucent magenta) / (opaque cyan).
        val kShapeColors: IntArray = intArrayOf(
            0x82FF0080.toInt(),    // input color unknown (alpha 0x82)
            0xFF00FFFF.toInt(),    // input color opaque
        )

        const val kSquare_Shape: Int = 0
        const val kDiamond_Shape: Int = 1
        const val kOval_Shape: Int = 2
        const val kConcave_Shape: Int = 3
        const val kLast_Shape: Int = kConcave_Shape
    }
}
