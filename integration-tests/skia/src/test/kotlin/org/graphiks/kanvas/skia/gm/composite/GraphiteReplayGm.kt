package org.graphiks.kanvas.skia.gm.composite

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
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.sin

/**
 * Port of Skia's `gm/graphite_replay.cpp`.
 * Uses the raster fallback path (offscreen tile snapped and replayed as a 2×2 grid).
 * @see https://github.com/google/skia/blob/main/gm/graphite_replay.cpp
 */
class GraphiteReplayGm : SkiaGm {
    override val name = "graphite-replay"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = K_TILE_WIDTH * 3
    override val height = K_TILE_HEIGHT * 2

    private var mandrillImage: Image? = null
    private var fImage: Image? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
        if (bytes != null) {
            mandrillImage = Image.decode(bytes)
            fImage = mandrillImage
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = mandrillImage ?: return
        fImage = image

        canvas.drawColor(0f, 0f, 0f)

        // Draw content directly as a simple 2×2 grid
        for (y in 0 until 2) {
            for (x in 0 until 2) {
                canvas.save()
                canvas.translate((x * K_TILE_WIDTH).toFloat(), (y * K_TILE_HEIGHT).toFloat())
                drawTile(canvas)
                canvas.restore()
            }
        }
    }

    private fun drawContent(canvas: GmCanvas) {
        val img = fImage ?: return

        val gradientPaint = Paint(
            shader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(K_IMAGE_SIZE.toFloat(), K_IMAGE_SIZE.toFloat()),
                stops = listOf(
                    GradientStop(0f, Color.RED),
                    GradientStop(0.33f, Color.GREEN),
                    GradientStop(0.66f, Color.BLUE),
                    GradientStop(1f, Color.RED),
                ),
                tileMode = TileMode.CLAMP,
            ),
        )

        canvas.drawImage(img, Rect.fromXYWH(K_PADDING.toFloat(), K_PADDING.toFloat(), K_IMAGE_SIZE.toFloat(), K_IMAGE_SIZE.toFloat()))
        canvas.save()
        canvas.translate((K_PADDED_IMAGE_SIZE + K_PADDING).toFloat(), K_PADDING.toFloat())
        canvas.drawRect(
            Rect.fromXYWH(0f, 0f, K_IMAGE_SIZE.toFloat(), K_IMAGE_SIZE.toFloat()),
            gradientPaint,
        )
        canvas.restore()
    }

    private fun drawTile(canvas: GmCanvas) {
        canvas.drawColor(1f, 0f, 0f)
        canvas.clipRect(Rect.fromXYWH(0f, 0f, (3 * K_TILE_WIDTH / 4).toFloat(), K_TILE_HEIGHT.toFloat()))

        drawContent(canvas)

        canvas.saveLayer(paint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 0.5f)))
        canvas.save()
        canvas.translate(0f, K_PADDED_IMAGE_SIZE.toFloat())
        drawContent(canvas)
        canvas.restore()
        canvas.restore()
    }

    private companion object {
        const val K_IMAGE_SIZE = 128
        const val K_PADDING = 2
        const val K_PADDED_IMAGE_SIZE = K_IMAGE_SIZE + K_PADDING * 2
        const val K_TILE_WIDTH = K_PADDED_IMAGE_SIZE * 2
        const val K_TILE_HEIGHT = K_PADDED_IMAGE_SIZE * 2
    }
}
