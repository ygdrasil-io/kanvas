package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/graphitestart.cpp`.
 * Combines image shaders, gradients, color filters, and blend modes in a tile grid.
 * @see https://github.com/google/skia/blob/main/gm/graphitestart.cpp
 */
class GraphiteStartGm : SkiaGm {
    override val name = "graphitestart"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 384

    private val kTileWidth = 128f
    private val kTileHeight = 128f
    private val kClipInset = 4f

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val clipRect = Rect.fromLTRB(
            kClipInset, kClipInset,
            width - kClipInset, height - kClipInset,
        )
        canvas.save()
        canvas.clipRRect(RRect(clipRect, CornerRadii(32f, 32f)))

        drawImageShaderTile(canvas, Rect.fromXYWH(0f, 0f, kTileWidth, kTileHeight))
        drawGradientTile(canvas, Rect.fromXYWH(kTileWidth, 0f, kTileWidth, kTileHeight))
        drawColorFilterSwatches(canvas, Rect.fromXYWH(2f * kTileWidth, 0f, kTileWidth, kTileHeight))

        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(kTileWidth, 2f * kTileHeight, kTileWidth, kTileHeight))
        drawBlendModeSwatches(canvas, Rect.fromXYWH(kTileWidth + 4f, 2f * kTileHeight + 4f, kTileWidth - 8f, kTileHeight - 8f))
        canvas.restore()

        canvas.save()
        val kTile = Rect.fromXYWH(2f * kTileWidth, 2f * kTileHeight, kTileWidth, kTileHeight)
        canvas.clipRect(kTile)
        canvas.drawRect(kTile.inset(10f, 20f), Paint(color = Color.BLUE, blendMode = BlendMode.SRC))
        canvas.saveLayer(kTile, Paint(blendMode = BlendMode.PLUS))
        canvas.drawRect(kTile.inset(15f, 25f), Paint(color = Color.RED, blendMode = BlendMode.SRC))
        canvas.restore()
        canvas.restore()
    }

    private fun drawImageShaderTile(canvas: GmCanvas, clipRect: Rect) {
        val shader = createImageShader(TileMode.CLAMP, TileMode.REPEAT)
        val path = Path {
            moveTo(1f, 1f); lineTo(32f, 127f); lineTo(96f, 127f)
            lineTo(127f, 1f); lineTo(63f, 32f); close()
        }
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.scale(0.5f, 0.5f)
        canvas.drawPath(path, Paint(shader = shader))
        canvas.save()
        canvas.translate(64f, 64f); canvas.rotate(90f); canvas.translate(-64f, -64f)
        canvas.translate(128f, 0f)
        canvas.drawPath(path, Paint(shader = shader))
        canvas.restore()
        canvas.restore()
    }

    private fun drawGradientTile(canvas: GmCanvas, clipRect: Rect) {
        val r = Rect.fromLTRB(1f, 1f, 127f, 127f)
        val shader = Shader.LinearGradient(
            start = Point(r.left, r.top),
            end = Point(r.right, r.top),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.75f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.BLUE),
            ),
            tileMode = TileMode.CLAMP,
        )
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(128f, 0f)
        canvas.scale(0.5f, 0.5f)
        canvas.drawRect(r, Paint(shader = shader))
        canvas.save()
        canvas.translate(64f, 64f); canvas.rotate(90f); canvas.translate(-64f, -64f)
        canvas.translate(128f, 0f)
        canvas.drawRect(r, Paint(shader = shader))
        canvas.restore()
        canvas.restore()
    }

    private fun drawColorFilterSwatches(canvas: GmCanvas, clipRect: Rect) {
        val numTilesPerSide = 3
        val tileW = clipRect.width / numTilesPerSide
        val tileH = clipRect.height / numTilesPerSide

        canvas.save()
        canvas.clipRect(clipRect)
        canvas.translate(clipRect.left, clipRect.top)

        val gradientColors = listOf(
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.TRANSPARENT, Color.fromRGBA(0.5f, 0f, 0f, 0f), Color.fromRGBA(0f, 0f, 0f, 1f)),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
            listOf(Color.BLACK, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f), Color.WHITE),
        )

        for (y in 0 until numTilesPerSide) {
            for (x in 0 until numTilesPerSide) {
                val r = Rect.fromXYWH(
                    x * tileW + 1f, y * tileH + 1f,
                    tileW - 2f, tileH - 2f,
                )
                val colors = gradientColors[x * numTilesPerSide + y]
                val shader = Shader.LinearGradient(
                    start = Point(r.left, r.top),
                    end = Point(r.right, r.top),
                    stops = listOf(
                        GradientStop(0f, colors[0]),
                        GradientStop(0.5f, colors[1]),
                        GradientStop(1f, colors[2]),
                    ),
                    tileMode = TileMode.CLAMP,
                )
                canvas.drawRect(r, Paint(shader = shader))
            }
        }
        canvas.restore()
    }

    private fun drawBlendModeSwatches(canvas: GmCanvas, clipRect: Rect) {
        val tileW = 16f
        val tileH = 16f
        val opaqueWhite = Color.fromRGBA(1f, 1f, 1f, 1f)
        val transBluish = Color.fromRGBA(0f, 0.5f, 1f, 0.5f)
        val transWhite = Color.fromRGBA(1f, 1f, 1f, 0.75f)

        var r = Rect.fromXYWH(clipRect.left, clipRect.top, tileW, tileH)
        val blendModes = listOf(
            BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER, BlendMode.DST_OVER,
            BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT, BlendMode.DST_OUT,
            BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR, BlendMode.PLUS,
            BlendMode.MODULATE,
        )

        for (passes in 0 until 2) {
            for (mode in blendModes) {
                canvas.drawRect(r.inset(1f, 1f), Paint(color = opaqueWhite))
                canvas.save()
                canvas.clipRect(r)
                canvas.drawRect(r.inset(2f, 2f), Paint(color = transBluish, blendMode = mode))
                canvas.restore()
                if (r.left + tileW > clipRect.right) {
                    r = Rect.fromXYWH(clipRect.left, r.top + tileH, tileW, tileH)
                } else {
                    r = Rect.fromXYWH(r.left + tileW, r.top, tileW, tileH)
                }
            }
            r = Rect.fromXYWH(clipRect.left, r.top + tileH, tileW, tileH)
        }
    }

    private fun createImageShader(tmX: TileMode, tmY: TileMode): Shader {
        val pixels = ByteArray(64 * 64 * 4)
        val colors = listOf(
            listOf(0xFF0000, 0x444444, 0x0000FF),
            listOf(0xAAAAAA, 0x00FFFF, 0xFFFF00),
            listOf(0x00FF00, 0xFFFFFF, 0xFF00FF),
        )
        for (y in 0 until 3) {
            for (x in 0 until 3) {
                val color = colors[y][x]
                for (py in 0 until 22) {
                    for (px in 0 until 22) {
                        val ix = x * 22 + px
                        val iy = y * 22 + py
                        if (ix < 64 && iy < 64) {
                            val i = (iy * 64 + ix) * 4
                            pixels[i] = ((color shr 16) and 0xFF).toByte()
                            pixels[i + 1] = ((color shr 8) and 0xFF).toByte()
                            pixels[i + 2] = (color and 0xFF).toByte()
                            pixels[i + 3] = 0xFF.toByte()
                        }
                    }
                }
            }
        }
        val img = Image.fromPixels(64, 64, pixels, ColorType.RGBA_8888)
        return Shader.Image(img, tmX, tmY)
    }
}

private fun Rect.inset(dx: Float, dy: Float): Rect = Rect.fromLTRB(left + dx, top + dy, right - dx, bottom - dy)
