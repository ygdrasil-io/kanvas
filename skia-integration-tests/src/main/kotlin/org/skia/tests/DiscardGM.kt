package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/discard.cpp::DiscardGM` (100 × 100).
 *
 * Repeats 100 (10 × 10) iterations of : (1) clear the offscreen
 * surface (upstream calls `surface->getCanvas()->discard()`, the
 * Kotlin port has no `discard()` yet — the subsequent clear /
 * drawColor / drawPaint covers the equivalent semantics on a raster
 * surface, see TODO below), (2) randomly pick a colour and apply it
 * via `drawColor`, `clear`, or `drawPaint`, (3) draw the offscreen
 * onto the main canvas at `(10·x, 10·y)`.
 *
 * The reference is a 10 × 10 mosaic of random ≥`0x404040`-floor
 * colours over a black main-canvas background.
 *
 * **API gap** : [SkSurface] has no `discard()` method. On raster the
 * call is semantically a no-op anyway — the next opaque clear / draw
 * overwrites the entire surface, which is what this GM exercises. If
 * GPU surfaces are added later, port `SkSurface.discard()` as a hint
 * to the backing render target.
 */
public class DiscardGM : GM() {

    override fun getName(): String = "discard"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val w = getISize().width / 10
        val h = getISize().height / 10

        // Raster fallback only — the offscreen surface mirrors the test
        // contract without exercising any GPU-specific discard().
        val surface: SkSurface = SkSurface.MakeRasterN32Premul(w, h)
        val sc: SkCanvas = surface.canvas

        c.clear(SK_ColorBLACK)

        val rand = SkRandom()
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                // TODO : `sc.discard()` — surface hint not exposed yet.
                val color = ToolUtils.colorTo565(rand.nextU() or 0xFF404040.toInt())
                when (rand.nextULessThan(3)) {
                    0 -> sc.drawColor(color)
                    1 -> sc.clear(color)
                    else -> {
                        val p = SkPaint().apply { shader = SkShaders.Color(color) }
                        sc.drawPaint(p)
                    }
                }
                surface.draw(c, 10f * x, 10f * y, null)
            }
        }
        // Trailing `sc.discard()` skipped — no functional impact on raster.
    }
}
