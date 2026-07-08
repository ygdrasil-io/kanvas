package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perspshaders.cpp::PerspShadersGM`.
 * Two variants: `persp_shaders_aa` and `persp_shaders_bw`.
 * Draws image and gradient shaders under perspective transforms.
 * @see https://github.com/google/skia/blob/main/gm/perspshaders.cpp
 */
open class PerspShadersGm(private val doAA: Boolean = true) : SkiaGm {
    override val name = if (doAA) "persp_shaders_aa" else "persp_shaders_bw"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = kCellSize * kNumCols
    override val height = kCellSize * kNumRows

    private var bitmapImage: Image = createCheckerboardImage(kCellSize, kCellSize, Color.BLUE, Color.fromRGBA(1f, 1f, 0f), kCellSize / 10)
    private val linearGrad1 = Shader.LinearGradient(
        start = Point(0f, 0f), end = Point(kCellSize.toFloat(), kCellSize.toFloat()),
        stops = listOf(
            GradientStop(0f, Color.RED), GradientStop(0.25f, Color.GREEN),
            GradientStop(0.5f, Color.RED), GradientStop(0.75f, Color.GREEN),
            GradientStop(1f, Color.RED),
        ),
        tileMode = TileMode.CLAMP,
    )
    private val linearGrad2 = Shader.LinearGradient(
        start = Point(0f, 0f), end = Point(0f, kCellSize.toFloat()),
        stops = listOf(
            GradientStop(0f, Color.RED), GradientStop(0.25f, Color.GREEN),
            GradientStop(0.5f, Color.RED), GradientStop(0.75f, Color.GREEN),
            GradientStop(1f, Color.RED),
        ),
        tileMode = TileMode.CLAMP,
    )
    private val perspMatrix = Matrix33.makeAll(
        1f, 0f, 0f,
        0f, 1f, 0f,
        1f / 50f, 0f, 1f,
    )
    private val path = Path {
        moveTo(0f, 0f)
        lineTo(0f, kCellSize.toFloat())
        lineTo(kCellSize / 2f, kCellSize / 2f)
        lineTo(kCellSize.toFloat(), kCellSize.toFloat())
        lineTo(kCellSize.toFloat(), 0f)
        close()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawRow(canvas, canvas, doAA)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, canvas, doAA)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, canvas, doAA)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, canvas, doAA)
        canvas.translate(0f, kCellSize.toFloat())
        drawRow(canvas, canvas, doAA)
    }

    private fun drawRow(canvas: GmCanvas, _unused: GmCanvas, aa: Boolean) {
        val aaPaint = Paint(antiAlias = aa)
        val pathPaint = Paint(
            shader = bitmapImage.makeShader(TileMode.CLAMP, TileMode.CLAMP),
            antiAlias = aa,
        )
        val gradPaint1 = Paint(shader = linearGrad1, antiAlias = aa)
        val gradPaint2 = Paint(shader = linearGrad2, antiAlias = aa)

        val r = Rect.fromXYWH(0f, 0f, kCellSize.toFloat(), kCellSize.toFloat())

        canvas.save()

        // Cell 1 - drawImageRect
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawImageRect(bitmapImage, r, r, aaPaint); canvas.restore()
        canvas.translate(kCellSize.toFloat(), 0f)

        // Cell 2 - drawImage
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawImage(bitmapImage, r, aaPaint); canvas.restore()
        canvas.translate(kCellSize.toFloat(), 0f)

        // Cell 3 - drawRect with bitmap shader
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawRect(r, pathPaint); canvas.restore()
        canvas.translate(kCellSize.toFloat(), 0f)

        // Cell 4 - drawPath with bitmap shader
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawPath(path, pathPaint); canvas.restore()
        canvas.translate(kCellSize.toFloat(), 0f)

        // Cell 5 - drawRect with linear gradient
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawRect(r, gradPaint1); canvas.restore()
        canvas.translate(kCellSize.toFloat(), 0f)

        // Cell 6 - drawPath with linear gradient
        canvas.save(); canvas.concat(perspMatrix)
        canvas.drawPath(path, gradPaint2); canvas.restore()

        canvas.restore()
    }

    private fun createCheckerboardImage(w: Int, h: Int, c0: Color, c1: Color, size: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val cellX = x / size; val cellY = y / size
                val pickC0 = (cellX + cellY) % 2 == 0
                val c = if (pickC0) c0 else c1
                val i = (y * w + x) * 4
                val packed = c.packed.toInt()
                pixels[i] = (packed and 0xFF).toByte()
                pixels[i + 1] = ((packed ushr 8) and 0xFF).toByte()
                pixels[i + 2] = ((packed ushr 16) and 0xFF).toByte()
                pixels[i + 3] = ((packed ushr 24) and 0xFF).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, sourceId = "checkerboard")
    }

    companion object {
        internal const val kCellSize = 50
        internal const val kNumRows = 5
        internal const val kNumCols = 6

        fun bw(): PerspShadersGm = PerspShadersGm(doAA = false)
    }
}

class PerspShadersBwGm : PerspShadersGm(false)
