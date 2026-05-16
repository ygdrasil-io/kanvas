package org.skia.testing

import org.skia.foundation.SkBitmap
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.min

/**
 * Renders a `rendered | diff | reference` triptych as a single PNG-ready
 * `BufferedImage`. The diff panel highlights mismatches in magenta with
 * intensity proportional to severity beyond `tolerance`; matching pixels
 * stay neutral grey so the rendered/reference panels keep visual context.
 */
internal object DiffImage {

    private const val LABEL_HEIGHT: Int = 16
    private const val GUTTER: Int = 4
    private val MATCHING_COLOR: Int = 0xFF202020.toInt()
    private val PANEL_BACKGROUND: Int = 0xFF000000.toInt()

    fun buildTriptych(
        rendered: SkBitmap,
        reference: SkBitmap,
        tolerance: Int,
        comparison: BitmapComparison,
    ): BufferedImage {
        require(rendered.width == reference.width && rendered.height == reference.height) {
            "Triptych requires same-size bitmaps (rendered=${rendered.width}x${rendered.height}, " +
                "reference=${reference.width}x${reference.height})"
        }
        val w = rendered.width
        val h = rendered.height
        val totalW = 3 * w + 2 * GUTTER
        val totalH = h + LABEL_HEIGHT
        val img = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(PANEL_BACKGROUND)
        g.fillRect(0, 0, totalW, totalH)
        g.color = Color.WHITE
        g.font = Font("Monospaced", Font.PLAIN, 11)
        g.drawString("rendered", 4, 12)
        val diffLabel = "diff  max=${comparison.maxChannelDiff.max()}  t=$tolerance  " +
            "miss=${comparison.mismatchingPixels}/${comparison.totalPixels}"
        g.drawString(diffLabel, w + GUTTER + 4, 12)
        g.drawString("reference", 2 * (w + GUTTER) + 4, 12)
        g.drawImage(TestUtils.bitmapToBufferedImage(rendered), 0, LABEL_HEIGHT, null)
        g.drawImage(buildDiffPanel(rendered, reference, tolerance), w + GUTTER, LABEL_HEIGHT, null)
        g.drawImage(TestUtils.bitmapToBufferedImage(reference), 2 * (w + GUTTER), LABEL_HEIGHT, null)
        g.dispose()
        return img
    }

    private fun buildDiffPanel(a: SkBitmap, b: SkBitmap, tolerance: Int): BufferedImage {
        val w = a.width
        val h = a.height
        val pixels = IntArray(w * h)
        // Phase 6 — read both bitmaps via the colorType-aware accessor so
        // F16 ↔ F16 (or mixed) comparisons render the diff panel correctly
        // instead of crashing on the empty `pixels8888` array.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dA = abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
                val dR = abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
                val dG = abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
                val dB = abs((pa and 0xFF) - (pb and 0xFF))
                val maxD = maxOf(dA, maxOf(dR, maxOf(dG, dB)))
                pixels[y * w + x] = if (maxD <= tolerance) {
                    MATCHING_COLOR
                } else {
                    val sev = min(255, (maxD - tolerance) * 4)
                    (0xFF000000.toInt()) or (sev shl 16) or sev
                }
            }
        }
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, pixels, 0, w)
        return img
    }
}
