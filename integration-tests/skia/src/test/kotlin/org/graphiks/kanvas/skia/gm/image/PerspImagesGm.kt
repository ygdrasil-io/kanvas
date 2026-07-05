package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perspimages.cpp::PerspImages` (1150x1280).
 * Exercises drawImage/drawImageRect under perspective Matrix33 transforms
 * with anti-alias toggles and sampling modes. This is a simplified port —
 * the reference was GPU-captured, so similarity on raster is expected low.
 * @see https://github.com/google/skia/blob/main/gm/perspimages.cpp
 */
class PerspImagesGm : SkiaGm {
    override val name = "persp_images"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1150
    override val height = 1280

    private var fImages: List<Image> = emptyList()

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        fImages = loadImages()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fImages.isEmpty()) return

        val m0 = Matrix33.makeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0.005f, 1f,
        )

        var m1 = Matrix33.makeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0.007f, -0.005f, 1f,
        )
        m1 = Matrix33.skew(0.2f, -0.1f) * m1
        m1 = Matrix33.rotate(-65f) * m1
        m1 = Matrix33.scale(1.2f, 0.8f) * m1
        m1 = m1 * Matrix33.translate(0f, 60f)

        val matrices = listOf(m0, m1)

        val aaValues = listOf(false, true)
        val paint = Paint()
        var n = 0

        canvas.save()
        for (type in DrawType.values()) {
            for (m in matrices) {
                for (aa in aaValues) {
                    for (origImage in fImages) {
                        canvas.save()
                        canvas.concat(m)

                        val w = origImage.width.toFloat()
                        val h = origImage.height.toFloat()
                        val src = Rect.fromLTRB(w / 4f, h / 4f, 3f * w / 4f, 3f * h / 4f)
                        val dst = Rect.fromLTRB(0f, 0f, 3f * w / 4f, 3f * h / 4f)
                        val usePaint = Paint(color = paint.color, antiAlias = aa, shader = paint.shader)

                        when (type) {
                            DrawType.DRAW_IMAGE ->
                                canvas.drawImage(origImage, Rect(0f, 0f, w, h), usePaint)
                            DrawType.DRAW_IMAGE_RECT ->
                                canvas.drawImageRect(origImage, src, dst, usePaint)
                        }

                        canvas.restore()

                        n++
                        if (n < 8) {
                            canvas.translate(w + 10f, 0f)
                        } else {
                            canvas.restore()
                            canvas.translate(0f, h + 10f)
                            canvas.save()
                            n = 0
                        }
                    }
                }
            }
        }
        canvas.restore()
    }

    private fun loadImages(): List<Image> {
        val images = mutableListOf<Image>()
        loadResource("images/mandrill_128.png")?.let { bytes ->
            images.add(Image.decode(bytes))
        }
        loadResource("images/brickwork-texture.jpg")?.let { bytes ->
            val full = Image.decode(bytes)
            if (full.width > 0) {
                images.add(Image(full.width.coerceAtMost(128), full.height.coerceAtMost(128), sourceId = "brickwork-128"))
            }
        }
        return images
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }

    private enum class DrawType {
        DRAW_IMAGE,
        DRAW_IMAGE_RECT,
    }
}
