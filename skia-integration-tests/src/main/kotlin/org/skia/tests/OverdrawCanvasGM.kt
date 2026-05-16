package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.effects.SkOverdrawColorFilter
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkOverdrawCanvas

/**
 * Port of upstream Skia's
 * [`gm/overdrawcanvas.cpp`](https://github.com/google/skia/blob/main/gm/overdrawcanvas.cpp)
 * (registered `DEF_SIMPLE_GM_BG(overdraw_canvas, …, 500, 500, SK_ColorWHITE)`).
 *
 * Renders six concentric rectangles (decreasing by 10 px on each edge) plus
 * a short string into an `Alpha_8` offscreen, wraps it with an
 * [SkOverdrawCanvas] so each draw call adds `+1` to the alpha counter, and
 * blits the snapshot back through an [SkOverdrawColorFilter] keyed by the
 * standard six-stop heat-map palette
 * `{0x00000000, 0x5fff0000, 0x2f0000ff, 0x2f00ff00, 0x3fff0000, 0x7fff0000}`.
 *
 * The result is a heatmap of overlapping rectangles plus a similar heatmap
 * over the text glyphs (which overdraw themselves around composite glyph
 * pieces). The GM also overlays a "This is some text:" label drawn through
 * the regular (non-overdraw) canvas, in pure black.
 *
 * Reference : `overdraw_canvas.png`, 500 × 500, white background.
 */
public class OverdrawCanvasGM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String = "overdraw_canvas"

    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Set up the overdraw offscreen — Alpha_8 raster surface.
        val offscreen = SkSurface.MakeRaster(SkImageInfo.MakeA8(WIDTH, HEIGHT))
        val font = ToolUtils.DefaultPortableFont()
        val overdrawCanvas = SkOverdrawCanvas(offscreen.canvas)

        // Six concentric rectangles, each shrinking by 10 px on every edge.
        overdrawCanvas.drawRect(SkRect.MakeLTRB(10f, 10f, 200f, 200f), SkPaint())
        overdrawCanvas.drawRect(SkRect.MakeLTRB(20f, 20f, 190f, 190f), SkPaint())
        overdrawCanvas.drawRect(SkRect.MakeLTRB(30f, 30f, 180f, 180f), SkPaint())
        overdrawCanvas.drawRect(SkRect.MakeLTRB(40f, 40f, 170f, 170f), SkPaint())
        overdrawCanvas.drawRect(SkRect.MakeLTRB(50f, 50f, 160f, 160f), SkPaint())
        overdrawCanvas.drawRect(SkRect.MakeLTRB(60f, 60f, 150f, 150f), SkPaint())

        // Single short string into the overdraw canvas. The substituted
        // overdraw paint discards the original colour/AA but still adds the
        // per-pixel +1 wherever the glyph covers a fragment.
        overdrawCanvas.drawString("Ae_p", 300f, 300f, font, SkPaint())

        val counts = offscreen.makeImageSnapshot()

        // Map the alpha-counter snapshot to colours via SkOverdrawColorFilter.
        val paint = SkPaint().apply {
            colorFilter = SkOverdrawColorFilter.MakeWithSkColors(OVERDRAW_COLORS)
        }
        c.drawImage(counts, 0f, 0f, SkSamplingOptions.Default, paint)
        c.drawString("This is some text:", 180f, 300f, font, SkPaint())
    }

    private companion object {
        private const val WIDTH: Int = 500
        private const val HEIGHT: Int = 500

        /**
         * Heat-map palette upstream uses : transparent then five red/blue/
         * green stops. The first slot is fully transparent because the GM
         * tolerates the "untouched" interior of the bitmap remaining the
         * background colour rather than a dark shade.
         */
        private val OVERDRAW_COLORS: IntArray = intArrayOf(
            0x00000000,
            0x5FFF0000,
            0x2F0000FF,
            0x2F00FF00,
            0x3FFF0000,
            0x7FFF0000,
        )
    }
}
