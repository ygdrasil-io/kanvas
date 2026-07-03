package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pictureshadertile.cpp::PictureShaderTileGM`
 * (`pictureshadertile`, 800 x 600).
 *
 * Exercises [Picture.asShader] with a non-default `tile`
 * sub-rectangle and `localMatrix` (R2.1 deliverable). The picture
 * itself is unit-sized (`SK_Scalar1`) and draws a green quarter
 * circle + blue lower-right quarter + red plus + black border.
 *
 * 27 tile / offset variants exercise different `tile.{x,y,w,h}` and
 * `offset.{x,y}` combinations — tiles smaller than the picture
 * effectively crop, tiles larger than the picture probe the
 * `kRepeat` mode (transparent margins). When the tile equals the
 * picture bounds, the upstream code swaps to an offset variant of
 * the picture and a null tile pointer (exercises the picture
 * `+ offset` shader path).
 *
 * Each shader is sampled into a 100 x 100 fill rect with kNearest
 * filtering, laid out in a 6-wide grid at `1.1 * kFillSize` spacing.
 */
class PictureShaderTileGm : SkiaGm {

    override val name = "pictureshadertile"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    private val fShaders: Array<Shader?> = arrayOfNulls(Tile.tiles.size)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fShaders[0] == null) {
            buildShaders()
        }

        canvas.drawColor(0f, 0f, 0f)

        for (i in fShaders.indices) {
            val paint = Paint(
                shader = fShaders[i],
            )
            canvas.save()
            canvas.translate(
                (i % kRowSize) * kFillSize * 1.1f,
                (i / kRowSize) * kFillSize * 1.1f,
            )
            canvas.drawRect(Rect.fromLTRB(0f, 0f, kFillSize, kFillSize), paint)
            canvas.restore()
        }
    }

    private fun buildShaders() {
        val recorder = PictureRecorder()

        val pictureCanvas = recorder.beginRecording(
            Rect.fromLTRB(0f, 0f, kPictureSize, kPictureSize),
        )
        drawScene(pictureCanvas, kPictureSize)
        val picture: Picture = recorder.finishRecordingAsPicture()

        val offsetX = 100f
        val offsetY = 100f
        val offsetCanvas = recorder.beginRecording(
            Rect.fromXYWH(offsetX, offsetY, kPictureSize, kPictureSize),
        )
        offsetCanvas.translate(offsetX, offsetY)
        drawScene(offsetCanvas, kPictureSize)
        val offsetPicture: Picture = recorder.finishRecordingAsPicture()

        for (i in Tile.tiles.indices) {
            val t = Tile.tiles[i]
            val tile = Rect.fromXYWH(
                t.x * kPictureSize, t.y * kPictureSize,
                t.w * kPictureSize, t.h * kPictureSize,
            )
            val localMatrix =
                Matrix33.translate(t.offsetX * kPictureSize, t.offsetY * kPictureSize) *
                    Matrix33.scale(kFillSize / (2f * kPictureSize), kFillSize / (2f * kPictureSize))

            var pictureRef = picture
            var tileRect: Rect? = tile

            if (tile == Rect.fromLTRB(0f, 0f, kPictureSize, kPictureSize)) {
                pictureRef = offsetPicture
                tileRect = null
            }

            fShaders[i] = if (tileRect != null) {
                pictureRef.asShader(
                    tileX = TileMode.REPEAT,
                    tileY = TileMode.REPEAT,
                    sampling = SamplingOptions.NEAREST,
                    tile = tileRect,
                    matrix = localMatrix,
                )
            } else {
                pictureRef.asShader(
                    tileX = TileMode.REPEAT,
                    tileY = TileMode.REPEAT,
                    sampling = SamplingOptions.NEAREST,
                    matrix = localMatrix,
                )
            }
        }
    }

    private fun drawScene(canvas: Canvas, pictureSize: Float) {
        canvas.clear(Color.WHITE)

        var paint = Paint(antiAlias = true)

        paint = paint.copy(color = Color.GREEN)
        canvas.drawPath(Path { addCircle(pictureSize / 4f, pictureSize / 4f, pictureSize / 4f) }, paint)

        paint = paint.copy(color = Color.BLUE)
        canvas.drawRect(
            Rect.fromXYWH(pictureSize / 2f, pictureSize / 2f, pictureSize / 2f, pictureSize / 2f),
            paint,
        )

        paint = paint.copy(color = Color.RED, style = PaintStyle.STROKE)
        canvas.drawPath(
            Path { moveTo(pictureSize / 2f, pictureSize * 1f / 3f); lineTo(pictureSize / 2f, pictureSize * 2f / 3f) },
            paint,
        )
        canvas.drawPath(
            Path { moveTo(pictureSize * 1f / 3f, pictureSize / 2f); lineTo(pictureSize * 2f / 3f, pictureSize / 2f) },
            paint,
        )

        paint = paint.copy(
            color = Color.BLACK,
            style = PaintStyle.STROKE,
        )
        canvas.drawRect(Rect.fromLTRB(0f, 0f, pictureSize, pictureSize), paint)
    }

    private data class Tile(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val offsetX: Float, val offsetY: Float,
    ) {
        companion object {
            val tiles: List<Tile> = listOf(
                Tile(0f, 0f, 1f, 1f, 0f, 0f),
                Tile(-0.5f, -0.5f, 1f, 1f, 0f, 0f),
                Tile(0.5f, 0.5f, 1f, 1f, 0f, 0f),

                Tile(0f, 0f, 1.5f, 1.5f, 0f, 0f),
                Tile(-0.5f, -0.5f, 1.5f, 1.5f, 0f, 0f),
                Tile(0.5f, 0.5f, 1.5f, 1.5f, 0f, 0f),

                Tile(0f, 0f, 0.5f, 0.5f, 0f, 0f),
                Tile(0.25f, 0.25f, 0.5f, 0.5f, 0f, 0f),
                Tile(-0.25f, -0.25f, 0.5f, 0.5f, 0f, 0f),

                Tile(0f, 0f, 1f, 1f, 0.5f, 0.5f),
                Tile(-0.5f, -0.5f, 1f, 1f, 0.5f, 0.5f),
                Tile(0.5f, 0.5f, 1f, 1f, 0.5f, 0.5f),

                Tile(0f, 0f, 1.5f, 1.5f, 0.5f, 0.5f),
                Tile(-0.5f, -0.5f, 1.5f, 1.5f, 0.5f, 0.5f),
                Tile(0.5f, 0.5f, 1.5f, 1.5f, 0.5f, 0.5f),

                Tile(0f, 0f, 1.5f, 1f, 0f, 0f),
                Tile(-0.5f, -0.5f, 1.5f, 1f, 0f, 0f),
                Tile(0.5f, 0.5f, 1.5f, 1f, 0f, 0f),

                Tile(0f, 0f, 0.5f, 1f, 0f, 0f),
                Tile(0.25f, 0.25f, 0.5f, 1f, 0f, 0f),
                Tile(-0.25f, -0.25f, 0.5f, 1f, 0f, 0f),

                Tile(0f, 0f, 1f, 1.5f, 0f, 0f),
                Tile(-0.5f, -0.5f, 1f, 1.5f, 0f, 0f),
                Tile(0.5f, 0.5f, 1f, 1.5f, 0f, 0f),

                Tile(0f, 0f, 1f, 0.5f, 0f, 0f),
                Tile(0.25f, 0.25f, 1f, 0.5f, 0f, 0f),
                Tile(-0.25f, -0.25f, 1f, 0.5f, 0f, 0f),
            )
        }
    }

    private companion object {
        const val kPictureSize: Float = 1f
        const val kFillSize: Float = 100f
        const val kRowSize: Int = 6
    }
}
