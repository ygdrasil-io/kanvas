package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class NearestHalfPixelImageGm : SkiaGm {
    override val name = "nearest_half_pixel_image"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 264
    override val height = 235

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rgbaX = make2x1(0xFFFF0000u, 0xFF0000FFu)
        val rgbaY = make1x2(0xFFFF0000u, 0xFF0000FFu)
        val alphaX = makeAlpha2x1(0xFF.toByte(), 0xAA.toByte())
        val alphaY = makeAlpha1x2(0xFF.toByte(), 0xAA.toByte())

        data class ImagePair(val imageX: Image, val imageY: Image)
        val images = arrayOf(ImagePair(rgbaX, rgbaY), ImagePair(alphaX, alphaY))

        val surf = Surface(80, 80)
        surf.canvas {
            drawColor(Color.WHITE)

            val kOffAxisScale = 4f

            for (shader in arrayOf(false, true)) {
                for (alpha in intArrayOf(0xFF, 0x70)) {
                    save()
                    for (ip in images) {
                        for (mirror in arrayOf(false, true)) {
                            drawImageVariant(this, ip.imageX, shader, doX = true, mirror, alpha, kOffAxisScale)
                            save()
                            translate(4f, 0f)
                            drawImageVariant(this, ip.imageY, shader, doX = false, mirror, alpha, kOffAxisScale)
                            restore()
                            translate(0f, kOffAxisScale * 2f)
                        }
                    }
                    restore()
                    translate(kOffAxisScale * 2f, 0f)
                }
            }
        }

        canvas.scale(8f, 8f)
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 10f, 10f))
    }

    companion object {
        private fun drawImageVariant(
            c: org.graphiks.kanvas.canvas.Canvas,
            image: Image, shader: Boolean, doX: Boolean, mirror: Boolean, alpha: Int, kOffAxisScale: Float,
        ) {
            c.save()
            val alphaPaint = if (alpha < 0xFF) Paint(color = Color.fromRGBA(1f, 1f, 1f, alpha / 255f)) else null
            if (shader) {
                if (doX) {
                    c.scale(if (mirror) -1f else 1f, kOffAxisScale)
                    c.translate(if (mirror) -2.5f else 0.5f, 0f)
                } else {
                    c.scale(kOffAxisScale, if (mirror) -1f else 1f)
                    c.translate(0f, if (mirror) -2.5f else 0.5f)
                }
                val s = Shader.Image(image)
                if (alphaPaint != null) {
                    c.drawRect(
                        Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
                        alphaPaint.copy(shader = s),
                    )
                } else {
                    c.drawRect(Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), Paint(shader = s))
                }
            } else {
                if (doX) {
                    c.scale(if (mirror) -1f else 1f, kOffAxisScale)
                    c.translate(if (mirror) -2.5f else 0.5f, 0f)
                } else {
                    c.scale(kOffAxisScale, if (mirror) -1f else 1f)
                    c.translate(0f, if (mirror) -2.5f else 0.5f)
                }
                c.drawImage(
                    image,
                    Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
                    alphaPaint,
                )
            }
            c.restore()
        }

        private fun make2x1(c0: UInt, c1: UInt): Image {
            val pixels = ByteArray(8)
            fun writePixel(arr: ByteArray, off: Int, packed: UInt) {
                arr[off] = ((packed shr 16) and 0xFFu).toByte()
                arr[off + 1] = ((packed shr 8) and 0xFFu).toByte()
                arr[off + 2] = (packed and 0xFFu).toByte()
                arr[off + 3] = ((packed shr 24) and 0xFFu).toByte()
            }
            writePixel(pixels, 0, c0)
            writePixel(pixels, 4, c1)
            return Image.fromPixels(2, 1, pixels)
        }

        private fun make1x2(c0: UInt, c1: UInt): Image {
            val pixels = ByteArray(8)
            fun writePixel(arr: ByteArray, off: Int, packed: UInt) {
                arr[off] = ((packed shr 16) and 0xFFu).toByte()
                arr[off + 1] = ((packed shr 8) and 0xFFu).toByte()
                arr[off + 2] = (packed and 0xFFu).toByte()
                arr[off + 3] = ((packed shr 24) and 0xFFu).toByte()
            }
            writePixel(pixels, 0, c0)
            writePixel(pixels, 4, c1)
            return Image.fromPixels(1, 2, pixels)
        }

        private fun makeAlpha2x1(a0: Byte, a1: Byte): Image {
            val pixels = byteArrayOf(a0, a1)
            return Image.fromPixels(2, 1, pixels, ColorType.ALPHA_8, "alpha2x1")
        }

        private fun makeAlpha1x2(a0: Byte, a1: Byte): Image {
            val pixels = byteArrayOf(a0, a1)
            return Image.fromPixels(1, 2, pixels, ColorType.ALPHA_8, "alpha1x2")
        }
    }
}
