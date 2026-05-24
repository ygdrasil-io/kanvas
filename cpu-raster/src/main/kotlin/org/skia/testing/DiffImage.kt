package org.skia.testing

import org.skia.foundation.SkBitmap
import kotlin.math.abs
import kotlin.math.min

/**
 * Renders a `rendered | diff | reference` triptych as a single PNG-ready
 * [SkBitmap]. The diff panel highlights mismatches in magenta with
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
    ): SkBitmap {
        require(rendered.width == reference.width && rendered.height == reference.height) {
            "Triptych requires same-size bitmaps (rendered=${rendered.width}x${rendered.height}, " +
                "reference=${reference.width}x${reference.height})"
        }
        val w = rendered.width
        val h = rendered.height
        val totalW = 3 * w + 2 * GUTTER
        val totalH = h + LABEL_HEIGHT
        val img = SkBitmap(totalW, totalH)
        img.eraseColor(PANEL_BACKGROUND)
        drawPanelMarkers(img, 0, w)
        drawPanelMarkers(img, w + GUTTER, min(w, 8 + comparison.maxChannelDiff.max() / 16))
        drawPanelMarkers(img, 2 * (w + GUTTER), w)
        copyBitmap(rendered, img, 0, LABEL_HEIGHT)
        copyBitmap(buildDiffPanel(rendered, reference, tolerance), img, w + GUTTER, LABEL_HEIGHT)
        copyBitmap(reference, img, 2 * (w + GUTTER), LABEL_HEIGHT)
        return img
    }

    private fun buildDiffPanel(a: SkBitmap, b: SkBitmap, tolerance: Int): SkBitmap {
        val w = a.width
        val h = a.height
        val img = SkBitmap(w, h)
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
                img.setPixel(x, y, if (maxD <= tolerance) {
                    MATCHING_COLOR
                } else {
                    val sev = min(255, (maxD - tolerance) * 4)
                    (0xFF000000.toInt()) or (sev shl 16) or sev
                })
            }
        }
        return img
    }

    private fun copyBitmap(src: SkBitmap, dst: SkBitmap, dx: Int, dy: Int) {
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                dst.setPixel(dx + x, dy + y, src.getPixel(x, y))
            }
        }
    }

    private fun drawPanelMarkers(dst: SkBitmap, startX: Int, width: Int) {
        val markerColor = 0xFFFFFFFF.toInt()
        val y = LABEL_HEIGHT / 2
        val endX = startX + width
        for (x in startX + 4 until min(endX - 4, startX + 24)) {
            dst.setPixel(x, y, markerColor)
        }
    }
}
