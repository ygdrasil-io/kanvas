package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkBlenders
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/arithmode.cpp::ArithmodeBlenderGM`
 * (`DEF_GM(return new ArithmodeBlenderGM;)`).
 *
 * Exercises `SkBlenders.Arithmetic` (newly available after D2.0 in
 * kanvas-skia) on three side-by-side scenes :
 *
 *  1. **Blender via paint** : `paint.blender = SkBlenders.Arithmetic(...)`
 *     applied through a `saveLayer` chain.
 *  2. **Blender via image filter** : `SkImageFilters.Blend(blender, …)`.
 *  3. **Blender via shader** : `SkShaders.Blend(blender, dst, src)`
 *     — *deferred* to D2.4.b (cluster runtime shader). Drawn here as
 *     a placeholder gray panel.
 *  4. **Blender via runtime shader** : also deferred to D2.4.b.
 *
 * Upstream uses fixed K-values (`fK1 = -0.25, fK2 = 0.25, fK3 = 0.25,
 * fK4 = 0`) since the GM is statically rendered (the `onAnimate`
 * branch isn't called during DM runs).
 *
 * **Iso-fidelity caveats** :
 *  - Cells 3 / 4 are placeholder fills (gray) instead of the runtime
 *    shader output. Similarity will be ~50 % with cells 1 / 2
 *    matching and cells 3 / 4 differing.
 *  - The checkerboard background is a custom 8×8 tile (substitutes
 *    upstream's `ToolUtils::create_checkerboard_image`).
 */
public class ArithmodeBlenderGM : GM() {

    override fun getName(): String = "arithmode_blender"
    override fun getISize(): SkISize = SkISize.Make((W + 30) * 2, (H + 30) * 4)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val src = makeSrc(W, H)
        val dst = makeDst(W, H)
        val checker = makeChecker(W, H, 0xFFBBBBBB.toInt(), 0xFFEEEEEE.toInt(), 8)

        // Static K-values matching upstream's onOnceBeforeDraw defaults.
        val k1 = -0.25f; val k2 = 0.25f; val k3 = 0.25f; val k4 = 0f

        c.drawImage(src, 10f, 10f, SkSamplingOptions.Default, null)
        c.drawImage(dst, 10f, (10 + H + 10).toFloat(), SkSamplingOptions.Default, null)

        c.translate((10 + W + 10).toFloat(), 10f)

        val blender = SkBlenders.Arithmetic(k1, k2, k3, k4, enforcePremul = true)
        val rect = SkRect.MakeWH(W.toFloat(), H.toFloat())

        // Cell 1 — paint.blender via saveLayer chain.
        c.drawImage(checker, 0f, 0f, SkSamplingOptions.Default, null)
        c.saveLayer(rect, null)
        c.drawImage(dst, 0f, 0f, SkSamplingOptions.Default, null)
        val blenderPaint = SkPaint().apply { this.blender = blender }
        c.drawImage(src, 0f, 0f, SkSamplingOptions.Default, blenderPaint)
        c.restore()

        c.translate(0f, (10 + H).toFloat())

        // Cell 2 — SkImageFilters.Blend with the same blender.
        // Our SkImageFilters.Blend factory takes an SkBlendMode rather
        // than an SkBlender. The arithmetic case can be served by an
        // Arithmetic image filter directly — same math.
        c.drawImage(checker, 0f, 0f, SkSamplingOptions.Default, null)
        val arithIF = SkImageFilters.Arithmetic(
            k1, k2, k3, k4, enforcePMColor = true,
            bg = null,
            fg = SkImageFilters.Image(src, SkSamplingOptions.Default),
        )
        val ifPaint = SkPaint().apply { imageFilter = arithIF }
        c.drawImage(dst, 0f, 0f, SkSamplingOptions.Default, ifPaint)

        c.translate(0f, (10 + H).toFloat())

        // Cell 3 — placeholder for SkShaders.Blend(blender, dst, src) (deferred to D2).
        c.drawImage(checker, 0f, 0f, SkSamplingOptions.Default, null)
        val ph3 = SkPaint().apply { color = SkColorSetARGB(0xFF, 0x88, 0x88, 0x88) }
        c.drawRect(rect, ph3)

        c.translate(0f, (10 + H).toFloat())

        // Cell 4 — placeholder for runtime-effect shader (deferred to D2.4.b).
        c.drawImage(checker, 0f, 0f, SkSamplingOptions.Default, null)
        val ph4 = SkPaint().apply { color = SkColorSetARGB(0xFF, 0x88, 0x88, 0x88) }
        c.drawRect(rect, ph4)
    }

    // ─── Helpers (shared shape with ArithmodeGM but inline-duplicated
    //              to keep each port self-contained) ─────────────────

    private fun makeSrc(w: Int, h: Int): SkImage {
        val surface = SkSurface.MakeRasterN32Premul(w, h)
        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f), SkPoint(w.toFloat(), h.toFloat()),
                intArrayOf(
                    SkColorSetARGB(0, 0, 0, 0),
                    SkColorSetARGB(0xFF, 0, 0xFF, 0),
                    SkColorSetARGB(0xFF, 0, 0xFF, 0xFF),
                    SkColorSetARGB(0xFF, 0xFF, 0, 0),
                    SkColorSetARGB(0xFF, 0xFF, 0, 0xFF),
                    SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),
                ),
                null,
                SkTileMode.kClamp,
            )
        }
        surface.canvas.drawPaint(paint)
        return surface.makeImageSnapshot()
    }

    private fun makeDst(w: Int, h: Int): SkImage {
        val surface = SkSurface.MakeRasterN32Premul(w, h)
        val gray = 0x88
        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, h.toFloat()), SkPoint(w.toFloat(), 0f),
                intArrayOf(
                    SkColorSetARGB(0xFF, 0, 0, 0xFF),
                    SkColorSetARGB(0xFF, 0xFF, 0xFF, 0),
                    SkColorSetARGB(0xFF, 0, 0, 0),
                    SkColorSetARGB(0xFF, 0, 0xFF, 0),
                    SkColorSetARGB(0xFF, gray, gray, gray),
                ),
                null,
                SkTileMode.kClamp,
            )
        }
        surface.canvas.drawPaint(paint)
        return surface.makeImageSnapshot()
    }

    private fun makeChecker(w: Int, h: Int, c1: Int, c2: Int, size: Int): SkImage {
        val bm = org.skia.foundation.SkBitmap(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val cellX = x / size
                val cellY = y / size
                val pickC1 = (cellX + cellY) % 2 == 0
                bm.setPixel(x, y, if (pickC1) c1 else c2)
            }
        }
        return bm.asImage()
    }

    private companion object {
        private const val W: Int = 200
        private const val H: Int = 200
    }
}
