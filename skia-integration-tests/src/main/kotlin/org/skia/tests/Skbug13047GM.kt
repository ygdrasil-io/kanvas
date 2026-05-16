package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.skia.math.SkColorSetARGB
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * Port of upstream Skia's `gm/vertices.cpp::skbug_13047`
 * (`DEF_SIMPLE_GM(skbug_13047, canvas, 200, 200)`).
 *
 * Regression for [skbug.com/13047](https://issues.skia.org/13047) :
 * `drawVertices` with a shader carrying a `localMatrix` should
 * apply the matrix on the texture-sampling side. CPU implementations
 * historically dropped it ; this GM pins the expected behaviour.
 *
 * **Note** : the upstream `vertices.cpp` includes
 * `<SkRuntimeEffect.h>` but never uses `SkRuntimeEffect` — pure
 * faux-positive. Fully portable today.
 *
 * **Adaptation** : upstream loads `images/mandrill_128.png` via
 * `ToolUtils::GetResourceAsImage`. We don't have that resource in
 * the kanvas-skia test classpath, so we synthesise a 128×128
 * gradient-filled bitmap as a stand-in. Iso-fidelity vs upstream's
 * mandrill is therefore impossible — similarity floor is set very
 * low (the GM remains a regression test for the CPU localMatrix
 * dropping bug, not a pixel-iso check).
 */
public class Skbug13047GM : GM() {

    override fun getName(): String = "skbug_13047"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // 128×128 stand-in for `images/mandrill_128.png` —
        // checkerboard gradient. The exact pixel content doesn't
        // matter for the regression check ; only that the localMatrix
        // is honoured.
        val w = 128
        val h = 128
        val image = SkBitmap(w, h).apply {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = (x * 255 / (w - 1)) and 0xFF
                    val g = (y * 255 / (h - 1)) and 0xFF
                    val b = ((x + y) * 255 / (w + h - 2)) and 0xFF
                    setPixel(x, y, SkColorSetARGB(0xFF, r, g, b))
                }
            }
        }.asImage()

        val verts = arrayOf(
            SkPoint(0f, 0f), SkPoint(200f, 0f), SkPoint(200f, 200f), SkPoint(0f, 200f),
        )
        val texs = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(w.toFloat(), 0f),
            SkPoint(w.toFloat(), h.toFloat()),
            SkPoint(0f, h.toFloat()),
        )
        val indices = shortArrayOf(0, 1, 2, 2, 3, 0)
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            positions = verts,
            texCoords = texs,
            indices = indices,
        )

        // localMatrix: scale 2×2 — upstream comment says "ignored in CPU ???".
        // Our impl is expected to honour it.
        val m = SkMatrix.Identity.preScale(2f, 2f)
        val s = image.makeShader(
            tileX = SkTileMode.kClamp, tileY = SkTileMode.kClamp,
            sampling = SkSamplingOptions.Default,
            localMatrix = m,
        )

        val paint = SkPaint().apply { shader = s }
        c.drawVertices(v, SkBlendMode.kModulate, paint)
    }
}
