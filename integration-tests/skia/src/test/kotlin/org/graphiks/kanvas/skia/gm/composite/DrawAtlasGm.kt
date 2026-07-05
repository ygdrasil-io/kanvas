package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Port of Skia's `gm/drawatlas.cpp::DrawAtlasGM` (name `draw-atlas`, 640 × 480).
 * Builds a 100×100 atlas where everything is red except the target
 * rect (50, 50, 80, 90) — that rect carries a 1-pixel-clear inset
 * around a blue antialiased oval. Then drawAtlas is called twice.
 * @see https://github.com/google/skia/blob/main/gm/drawatlas.cpp
 */
class DrawAtlasGm : SkiaGm {
    override val name = "draw-atlas"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val target = Rect.fromLTRB(50f, 50f, 80f, 90f)
        val atlas = makeAtlas()

        data class Rec(val scale: Float, val degrees: Float, val tx: Float, val ty: Float)
        val rec = arrayOf(
            Rec(1f, 0f, 10f, 10f),
            Rec(2f, 0f, 110f, 10f),
            Rec(1f, 30f, 210f, 10f),
            Rec(2f, -30f, 310f, 30f),
        )
        val n = rec.size
        val transforms = List(n) {
            val r = rec[it]
            val rad = r.degrees * (PI.toFloat() / 180f)
            val scos = r.scale * cos(rad)
            val ssin = r.scale * sin(rad)
            Matrix33.makeAll(scos, -ssin, r.tx, ssin, scos, r.ty)
        }
        val tex = List(n) { target }
        val colors = List(n) { Color.fromRGBA(0.5f, 1f, 0f, 0f) } // colors ignored by kanvas-skia

        val paint = Paint()

        canvas.drawAtlas(atlas, transforms, tex, colors = null, blendMode = BlendMode.DST, paint = paint)
        canvas.translate(0f, 100f)
        canvas.drawAtlas(atlas, transforms, tex, colors = colors, blendMode = BlendMode.SRC_IN, paint = paint)
    }

    private fun makeAtlas(): Image {
        val w = 100
        val h = 100
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                // Fill red background
                pixels[i] = (-1).toByte()     // R
                pixels[i + 1] = 0.toByte()    // G
                pixels[i + 2] = 0.toByte()    // B
                pixels[i + 3] = (-1).toByte() // A
            }
        }
        // Clear the rect around target (outset by 1px)
        for (y in 49..91) {
            for (x in 49..81) {
                val i = (y * w + x) * 4
                pixels[i] = 0.toByte()
                pixels[i + 1] = 0.toByte()
                pixels[i + 2] = 0.toByte()
                pixels[i + 3] = 0.toByte()
            }
        }
        // Draw blue oval (51, 51, 79, 89) in the cleared area
        val cx = 65f
        val cy = 70f
        val rx = 14f
        val ry = 19f
        for (y in 51 until 89) {
            for (x in 51 until 79) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1f) {
                    val i = (y * w + x) * 4
                    pixels[i] = 0.toByte()     // R
                    pixels[i + 1] = 0.toByte() // G
                    pixels[i + 2] = (-1).toByte() // B
                    pixels[i + 3] = (-1).toByte() // A
                }
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "atlas")
    }
}
