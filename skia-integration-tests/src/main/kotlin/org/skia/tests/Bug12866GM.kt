package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkStroker
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/bug12866.cpp::bug12866` (DEF_SIMPLE_GM, 128 × 64).
 *
 * Reproduces the underlying problem from skbug.com/40043963 :
 * `skpathutils::FillPathWithPaint(strokePath, strokePaint, &builder,
 * nullptr, SkMatrix::Scale(1200, 1200))` produces an incorrect filled
 * path because of a stroker recursion-limit issue triggered by the
 * giant resScale.
 *
 * Substitute : we don't expose `FillPathWithPaint` directly, but the
 * stroker is reachable via `SkStroker.fromPaint(paint, resScale)`.
 * Passing `resScale = 1200` drives the same subdivision path as
 * upstream's `Scale(1200, 1200)` matrix.
 *
 * Side-by-side : the left rendering uses `drawPath(strokePath,
 * strokePaint)` (resScale = 1.0 internally — looks good) ; the right
 * rendering shows the result of stroking at resScale = 1200 then
 * filling — demonstrates the bug.
 */
public class Bug12866GM : GM() {

    override fun getName(): String = "bug12866"
    override fun getISize(): SkISize = SkISize.Make(128, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val strokePath = buildPath()
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 3f
        }
        val fillPath = SkStroker.fromPaint(strokePaint, resScale = 1200f).stroke(strokePath)
        val fillPaint = SkPaint().apply { isAntiAlias = true }

        val strokeBounds = strokePath.computeBounds()
        val fillBounds = fillPath.computeBounds()

        c.save()
        c.translate(10f - strokeBounds.left, 10f - strokeBounds.top)
        c.drawPath(strokePath, strokePaint)
        c.restore()

        c.save()
        c.translate(74f - fillBounds.left, 10f - fillBounds.top)
        c.drawPath(fillPath, fillPaint)
        c.restore()
    }

    private fun buildPath(): SkPath = SkPathBuilder()
        .setFillType(SkPathFillType.kWinding)
        .moveTo(2100.92f, 115.991f)
        .quadTo(2063.28f, 179.199f, 2063.28f, 159.058f)
        .quadTo(2063.28f, 138.843f, 2073.27f, 127.417f)
        .quadTo(2083.27f, 115.991f, 2100.92f, 115.991f)
        .close()
        .detach()
}
