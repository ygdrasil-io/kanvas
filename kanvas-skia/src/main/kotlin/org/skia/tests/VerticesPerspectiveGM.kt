package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/vertices.cpp::vertices_perspective`
 * (`DEF_SIMPLE_GM(vertices_perspective, canvas, 256, 256)`).
 *
 * Regression test for [skbug.com/40041407](https://issues.skia.org/40041407).
 * Draws the same 128×128 quad twice with `SkVertices.kTriangleFan`
 * + a perspective matrix, plus two `drawRect` reference cells in
 * the upper row.
 *
 * **Note** : the upstream `vertices.cpp` includes
 * `<SkRuntimeEffect.h>` but never uses `SkRuntimeEffect` — pure
 * faux-positive. Fully portable today.
 *
 * The shader is a hand-built 32×32 black/white checkerboard via a
 * one-shot bitmap (substitutes upstream's `ToolUtils::create_checkerboard_shader`).
 */
public class VerticesPerspectiveGM : GM() {

    override fun getName(): String = "vertices_perspective"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val r = SkRect.MakeWH(128f, 128f)

        val checker = makeCheckerboardShader(SK_ColorBLACK, SK_ColorWHITE, 32)
        val paint = SkPaint().apply { shader = checker }

        // Quad triangle fan : 4 corners with UV = position (so the
        // texture maps 1:1).
        val pos = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(128f, 0f),
            SkPoint(128f, 128f),
            SkPoint(0f, 128f),
        )
        val verts = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            positions = pos,
            texCoords = pos,
        )

        // Perspective matrix : minor Y-axis perspective.
        val persp = SkMatrix.Identity.copy(persp1 = 1f / 100f)

        // Top-left : drawRect with persp, no vertices.
        c.save()
        c.concat(persp)
        c.drawRect(r, paint)
        c.restore()

        // Top-right : second drawRect at +128 X.
        c.save()
        c.translate(r.width(), 0f)
        c.concat(persp)
        c.drawRect(r, paint)
        c.restore()

        // Bottom-left : drawVertices.
        c.save()
        c.translate(0f, r.height())
        c.concat(persp)
        c.drawVertices(verts, SkBlendMode.kModulate, paint)
        c.restore()

        // Bottom-right : second drawVertices at +128, +128.
        c.save()
        c.translate(r.width(), r.height())
        c.concat(persp)
        c.drawVertices(verts, SkBlendMode.kModulate, paint)
        c.restore()
    }

    /**
     * Cheap stand-in for upstream's
     * `ToolUtils::create_checkerboard_shader(c1, c2, size)` — builds a
     * `2 × size` × `2 × size` bitmap with a single checker tile then
     * tiles it via `kRepeat`.
     */
    private fun makeCheckerboardShader(c1: Int, c2: Int, size: Int): org.skia.foundation.SkShader {
        val side = 2 * size
        val bm = SkBitmap(side, side)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val onTopLeft = (x < size) xor (y < size)
                bm.setPixel(x, y, if (onTopLeft) c1 else c2)
            }
        }
        return bm.makeShader(tileX = SkTileMode.kRepeat, tileY = SkTileMode.kRepeat)
    }
}
