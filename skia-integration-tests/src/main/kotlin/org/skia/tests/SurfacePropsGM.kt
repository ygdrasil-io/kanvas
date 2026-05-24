package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/surface.cpp::SurfacePropsGM`
 * (registered as `surfaceprops` / `surfaceprops_df`, 800 x 900).
 *
 * Upstream renders nine offscreen surfaces with different
 * [SkSurfaceProps] pixel geometries and text flags, then composites
 * them back into the root canvas. The raster port exercises the same
 * props propagation through [SkSurface.MakeRaster]. `contrast` /
 * `gamma` inputs are recorded in labels only because the local
 * [SkSurfaceProps] type intentionally carries flags + pixel geometry
 * but not the deprecated text gamma tuning fields.
 */
public class SurfacePropsGM(
    private val useDistanceField: Boolean = false,
) : GM() {

    override fun getName(): String =
        "surfaceprops" + if (useDistanceField) "_df" else ""

    override fun getISize(): SkISize = SkISize.Make(W, H * recs.size)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32(W, H, SkAlphaType.kOpaque)
        var y = 0f
        for (rec in recs) {
            val surface = SkSurface.MakeRaster(
                info,
                SkSurfaceProps(
                    flags = if (useDistanceField) {
                        SkSurfaceProps.kUseDeviceIndependentFonts_Flag
                    } else {
                        0
                    },
                    pixelGeometry = rec.pixelGeometry,
                ),
            )
            testDraw(surface.canvas, rec.label)
            surface.draw(c, 0f, y)
            y += H
        }
    }

    private fun testDraw(canvas: SkCanvas, label: String) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            isDither = true
            shader = makeShader()
        }
        canvas.drawRect(SkRect.MakeWH(W.toFloat(), H.toFloat()), paint)

        paint.shader = null
        paint.color = WHITE
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 32f).apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
        }
        SkTextUtils.DrawString(
            canvas,
            label,
            W / 2f,
            H * 3f / 4f,
            font,
            paint,
            SkTextUtils.Align.kCenter_Align,
        )
    }

    private fun makeShader(): SkLinearGradient = SkLinearGradient.Make(
        SkPoint.Make(0f, 0f),
        SkPoint.Make(W.toFloat(), H.toFloat()),
        intArrayOf(GRAD_A, GRAD_B),
        null,
        SkTileMode.kClamp,
    )

    private data class SurfacePropsInput(
        val pixelGeometry: SkSurfaceProps.SkPixelGeometry,
        val label: String,
    )

    private companion object {
        const val W: Int = 800
        const val H: Int = 100

        const val WHITE: Int = -0x1
        const val GRAD_A: Int = -0x666667
        const val GRAD_B: Int = -0x444445

        val recs: List<SurfacePropsInput> = listOf(
            SurfacePropsInput(
                SkSurfaceProps.SkPixelGeometry.kUnknown,
                "Unknown geometry, default contrast/gamma",
            ),
            SurfacePropsInput(
                SkSurfaceProps.SkPixelGeometry.kRGB_H,
                "RGB_H, default contrast/gamma",
            ),
            SurfacePropsInput(
                SkSurfaceProps.SkPixelGeometry.kBGR_H,
                "BGR_H, default contrast/gamma",
            ),
            SurfacePropsInput(
                SkSurfaceProps.SkPixelGeometry.kRGB_V,
                "RGB_V, default contrast/gamma",
            ),
            SurfacePropsInput(
                SkSurfaceProps.SkPixelGeometry.kBGR_V,
                "BGR_V, default contrast/gamma",
            ),
            SurfacePropsInput(SkSurfaceProps.SkPixelGeometry.kRGB_H, "RGB_H contrast : 0 gamma: 0"),
            SurfacePropsInput(SkSurfaceProps.SkPixelGeometry.kRGB_H, "RGB_H contrast : 1 gamma: 0"),
            SurfacePropsInput(SkSurfaceProps.SkPixelGeometry.kRGB_H, "RGB_H contrast : 0 gamma: 3.9"),
            SurfacePropsInput(SkSurfaceProps.SkPixelGeometry.kRGB_H, "RGB_H contrast : 1 gamma: 3.9"),
        )
    }
}
