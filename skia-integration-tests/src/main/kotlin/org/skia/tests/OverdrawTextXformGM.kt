package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import org.skia.utils.SkOverdrawCanvas

/**
 * Port of upstream Skia's
 * [`gm/overdrawcanvas.cpp`](https://github.com/google/skia/blob/main/gm/overdrawcanvas.cpp)
 * (registered `DEF_SIMPLE_GM_BG(overdraw_text_xform, …, 512, 512, SK_ColorBLACK)`).
 *
 * Tests the bug from skbug.com/40044818: text drawn through an overdraw canvas
 * would have the CTM applied twice. If everything is working, both images generated
 * should look identical. When the bug was present, the second image would have the
 * lines "double spaced", because the translations were applied twice.
 *
 * Two 256×512 "text-density" images are produced via [overdrawTextGrid]:
 *  - Left  (x=0)   : `useCTM=false` — text drawn with absolute (x, y).
 *  - Right (x=256) : `useCTM=true`  — text drawn after `save/translate/restore`.
 *
 * Both are blit via white paint + `kNearest` filter onto the black background.
 * If CTM is not double-applied the two halves must be pixel-identical.
 *
 * Reference : `overdraw_text_xform.png`, 512 × 512, black background.
 */
public class OverdrawTextXformGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "overdraw_text_xform"

    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val imgPaint = SkPaint().apply { color = SK_ColorWHITE }
        val sampling = SkSamplingOptions(SkFilterMode.kNearest)

        c.drawImage(overdrawTextGrid(useCTM = false),   0f, 0f, sampling, imgPaint)
        c.drawImage(overdrawTextGrid(useCTM = true),  256f, 0f, sampling, imgPaint)
    }

    private companion object {
        private const val WIDTH: Int = 512
        private const val HEIGHT: Int = 512

        /**
         * Mirrors upstream's static `overdraw_text_grid(bool useCTM)`.
         *
         * Renders 20 rows of text, each row i (1..20) draws the alphabet
         * string `i * 10` times at vertical position `i * 20f`.
         * When [useCTM] is true the canvas transform is used (save +
         * translate to (x, y) then drawString at origin); otherwise
         * drawString is called directly at (x, y).
         */
        private fun overdrawTextGrid(useCTM: Boolean): SkImage {
            val surface = SkSurface.MakeRaster(SkImageInfo.MakeA8(256, 512))
            val overdraw = SkOverdrawCanvas(surface.canvas)

            val paint = SkPaint().apply { color = SK_ColorWHITE }
            val font = ToolUtils.DefaultPortableFont()
            val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

            for (n in 1..20) {
                val x = 10f
                val y = n * 20f

                for (i in 0 until n * 10) {
                    if (useCTM) {
                        overdraw.save()
                        overdraw.translate(x, y)
                        overdraw.drawString(text, 0f, 0f, font, paint)
                        overdraw.restore()
                    } else {
                        overdraw.drawString(text, x, y, font, paint)
                    }
                }
            }

            return surface.makeImageSnapshot()
        }
    }
}
