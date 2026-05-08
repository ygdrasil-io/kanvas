package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.pathops.SkOpBuilder
import org.skia.pathops.SkPathOp

/**
 * Port of Skia's `gm/pathopsinverse.cpp::pathops_skbug_10155`
 * (256 × 256). Regression cover for skbug.com/10155.
 *
 * Two SVG-defined cubic-Bézier paths (small numerical-precision
 * shapes near `(475, 27)`) are unioned via [SkOpBuilder]. The
 * scene zooms into the bounds of the first path so the small
 * features fill the viewport ; the GM checks that the blue
 * `Op` result (nearly) overdraws the red outlines of both inputs
 * — except where the two paths intersect.
 *
 * Skia's source uses `SkParsePath::FromSVGString` to materialise
 * the paths ; we don't ship an SVG parser, so the verbs are
 * spelled out explicitly via [SkPathBuilder]. Coordinates are
 * verbatim from the upstream SVG strings.
 */
public class PathOpsSkbug10155GM : GM() {

    override fun getName(): String = "pathops_skbug_10155"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val path0 = path0()
        val path1 = path1()

        val builder = SkOpBuilder()
        builder.add(path0, SkPathOp.kUnion)
        builder.add(path1, SkPathOp.kUnion)
        val resultPath = builder.resolve() ?: SkPathBuilder().detach()

        val r = path0.computeBounds()
        c.translate(30f, 30f)
        c.scale(200f / r.width(), 200f / r.width())
        c.translate(-r.left, -r.top)

        val paint = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        c.drawPath(path0, paint)
        c.drawPath(path1, paint)

        // Blue should (nearly) overdraw all red outline pixels — except
        // at the path intersection where the union doesn't fully cover.
        paint.color = SK_ColorBLUE
        c.drawPath(resultPath, paint)
    }

    /**
     * SVG : `M474.889 27.0952C474.889 27.1002 474.888 27.1018 474.889 27.1004
     *        L479.872 27.5019 C479.883 27.3656 479.889 27.2299 479.889 27.0952
     *        L474.889 27.0952L474.889 27.0952Z`
     */
    private fun path0(): SkPath = SkPathBuilder()
        .moveTo(474.889f, 27.0952f)
        .cubicTo(474.889f, 27.1002f, 474.888f, 27.1018f, 474.889f, 27.1004f)
        .lineTo(479.872f, 27.5019f)
        .cubicTo(479.883f, 27.3656f, 479.889f, 27.2299f, 479.889f, 27.0952f)
        .lineTo(474.889f, 27.0952f)
        .lineTo(474.889f, 27.0952f)
        .close()
        .detach()

    /**
     * SVG : `M474.94 26.9405C474.93 26.9482 474.917 26.9576 474.901 26.9683
     *        L477.689 31.1186 C477.789 31.0512 477.888 30.9804 477.985 30.9059
     *        L474.94 26.9405L474.94 26.9405Z`
     */
    private fun path1(): SkPath = SkPathBuilder()
        .moveTo(474.94f, 26.9405f)
        .cubicTo(474.93f, 26.9482f, 474.917f, 26.9576f, 474.901f, 26.9683f)
        .lineTo(477.689f, 31.1186f)
        .cubicTo(477.789f, 31.0512f, 477.888f, 30.9804f, 477.985f, 30.9059f)
        .lineTo(474.94f, 26.9405f)
        .lineTo(474.94f, 26.9405f)
        .close()
        .detach()
}
