package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorCYAN
import org.skia.math.SK_ColorGRAY
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorMAGENTA
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.math.SK_ColorYELLOW
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/transparency.cpp` (`DEF_SIMPLE_GM(transparency_check,
 * canvas, 1792, 1080)`).
 *
 * Verifies that a transparent bitmap drawn over a checkerboard pattern
 * looks correct. Upstream first paints a `(0xFF999999, 0xFF666666)`
 * 8-pixel checker over the entire canvas, then for the foreground:
 *  1. Creates a 256×9 raster surface (N32 premul).
 *  2. Draws 9 rows of horizontal black-to-colour `LinearGradient` strips
 *     into it (kClamp, colours = BLACK, GRAY, WHITE, RED, YELLOW, GREEN,
 *     CYAN, BLUE, MAGENTA).
 *  3. Scales the destination canvas by `(7, 120)` and composites the
 *     surface at `(0, 0)` — i.e. the 256×9 thumbnail balloons to
 *     1792×1080 with linear (sample-based) upscaling.
 *
 * `ToolUtils.draw_checkerboard` is the direct mirror of Skia's
 * `checkerboard` helper (it uses the same `2 × size`-tiled bitmap shader
 * recipe) so the BG matches byte-for-byte. `SkSurface.MakeRasterN32Premul`
 * + `SkSurface.draw` mirror `SkSurfaces::Raster` + `SkSurface::draw`.
 *
 * Upstream uses `SkColor4f` gradient endpoints `{0, 0, 0, 0}` →
 * `SkColor4f::FromColor(c)`. The Kotlin port uses the packed-ARGB
 * `IntArray` overload of [SkLinearGradient.Make] — both `0x00000000`
 * (fully transparent black) and the colour constants are bit-identical
 * to upstream after the same premul step the rasteriser performs.
 *
 * C++ original:
 * ```cpp
 * static void make_transparency(SkCanvas* canvas, SkScalar width, SkScalar height) {
 *     SkPoint pts[2] = {{0,0}, {width, 0}};
 *     const SkColor kColors[] = {BLACK, GRAY, WHITE, RED, YELLOW, GREEN, CYAN, BLUE, MAGENTA};
 *     const SkScalar kRowHeight = height / 9;
 *     for (size_t i = 0; i < 9; ++i) {
 *         SkColor4f shaderColors[] = {{0,0,0,0}, SkColor4f::FromColor(kColors[i])};
 *         SkPaint p;
 *         p.setShader(SkShaders::LinearGradient(pts, {{shaderColors, {}, SkTileMode::kClamp}, {}}));
 *         canvas->drawRect(SkRect::MakeXYWH(0, i*kRowHeight, width, kRowHeight), p);
 *     }
 * }
 *
 * DEF_SIMPLE_GM(transparency_check, canvas, 1792, 1080) {
 *     checkerboard(canvas, 0xFF999999, 0xFF666666, 8);
 *     {
 *         SkAutoCanvasRestore acr(canvas, true);
 *         auto surface = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(256, 9));
 *         make_transparency(surface->getCanvas(), 256.0f, 9.0f);
 *         canvas->scale(7.0f, 120.0f);
 *         surface->draw(canvas, 0, 0);
 *     }
 * }
 * ```
 */
public class TransparencyCheckGM : GM() {
    override fun getName(): String = "transparency_check"
    override fun getISize(): SkISize = SkISize.Make(1792, 1080)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // BG: 8-px checker between two greys.
        ToolUtils.draw_checkerboard(c, 0xFF999999.toInt(), 0xFF666666.toInt(), 8)

        // FG: scoped CTM via save / restore — mirrors `SkAutoCanvasRestore`.
        c.save()
        try {
            val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(256, 9))
            makeTransparency(surface.canvas, 256.0f, 9.0f)
            c.scale(7.0f, 120.0f)
            surface.draw(c, 0f, 0f)
        } finally {
            c.restore()
        }
    }

    /**
     * Mirrors the upstream `make_transparency` helper. 9 stacked
     * `kRowHeight`-tall rects, each filled with a horizontal linear
     * gradient running `(0, 0, 0, 0)` → `kColors[i]` left-to-right.
     */
    private fun makeTransparency(canvas: SkCanvas, width: Float, height: Float) {
        val p0 = SkPoint.Make(0f, 0f)
        val p1 = SkPoint.Make(width, 0f)
        val kColors = intArrayOf(
            SK_ColorBLACK,
            SK_ColorGRAY,
            SK_ColorWHITE,
            SK_ColorRED,
            SK_ColorYELLOW,
            SK_ColorGREEN,
            SK_ColorCYAN,
            SK_ColorBLUE,
            SK_ColorMAGENTA,
        )
        val rowHeight = height / kColors.size
        for (i in kColors.indices) {
            // Transparent black (0x00_00_00_00) → opaque kColors[i]. The
            // C++ uses two-stop SkColor4f arrays — packed ARGB matches
            // bit-for-bit through our IntArray overload.
            val paint = SkPaint().apply {
                shader = SkLinearGradient.Make(
                    p0 = p0,
                    p1 = p1,
                    colors = intArrayOf(0x00000000, kColors[i]),
                    positions = null,
                    tileMode = SkTileMode.kClamp,
                )
            }
            canvas.drawRect(
                SkRect.MakeXYWH(0f, i * rowHeight, width, rowHeight),
                paint,
            )
        }
    }
}
