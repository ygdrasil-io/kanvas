package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/pictureshadertile.cpp::PictureShaderTileGM`
 * (`pictureshadertile`, 800 × 600).
 *
 * Exercises [SkPicture.makeShader] with a non-default `tile`
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
 * Each shader is sampled into a 100 × 100 fill rect with kNearest
 * filtering, laid out in a 6-wide grid at `1.1 * kFillSize` spacing.
 */
public class PictureShaderTileGM : GM() {

    override fun getName(): String = "pictureshadertile"
    override fun getISize(): SkISize = SkISize.Make(800, 600)

    private val fShaders: Array<SkShader?> = arrayOfNulls(tiles.size)

    override fun onOnceBeforeDraw() {
        val recorder = SkPictureRecorder()

        var pictureCanvas = recorder.beginRecording(kPictureSize, kPictureSize)
        drawScene(pictureCanvas, kPictureSize)
        val picture: SkPicture = recorder.finishRecordingAsPicture()

        val offsetX = 100f
        val offsetY = 100f
        pictureCanvas = recorder.beginRecording(SkRect.MakeXYWH(offsetX, offsetY, kPictureSize, kPictureSize))
        pictureCanvas.translate(offsetX, offsetY)
        drawScene(pictureCanvas, kPictureSize)
        val offsetPicture: SkPicture = recorder.finishRecordingAsPicture()

        for (i in tiles.indices) {
            val t = tiles[i]
            val tile = SkRect.MakeXYWH(
                t.x * kPictureSize, t.y * kPictureSize,
                t.w * kPictureSize, t.h * kPictureSize,
            )
            val localMatrix = SkMatrix.MakeTrans(t.offsetX * kPictureSize, t.offsetY * kPictureSize)
                .postScale(kFillSize / (2f * kPictureSize), kFillSize / (2f * kPictureSize))

            var pictureRef = picture
            var tilePtr: SkRect? = tile

            if (tile == SkRect.MakeWH(kPictureSize, kPictureSize)) {
                // When tile == picture bounds, exercise the picture + offset
                // path (offsetPicture, null tile).
                pictureRef = offsetPicture
                tilePtr = null
            }

            fShaders[i] = pictureRef.makeShader(
                tileX = SkTileMode.kRepeat,
                tileY = SkTileMode.kRepeat,
                filter = SkFilterMode.kNearest,
                localMatrix = localMatrix,
                tile = tilePtr,
            )
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val paint = SkPaint().apply { style = SkPaint.Style.kFill_Style }
        for (i in fShaders.indices) {
            paint.shader = fShaders[i]
            c.save()
            c.translate((i % kRowSize) * kFillSize * 1.1f, (i / kRowSize) * kFillSize * 1.1f)
            c.drawRect(SkRect.MakeWH(kFillSize, kFillSize), paint)
            c.restore()
        }
    }

    private fun drawScene(canvas: SkCanvas, pictureSize: Float) {
        canvas.clear(SK_ColorWHITE)

        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
        }

        paint.color = SK_ColorGREEN
        canvas.drawCircle(pictureSize / 4f, pictureSize / 4f, pictureSize / 4f, paint)
        paint.color = SK_ColorBLUE
        canvas.drawRect(
            SkRect.MakeXYWH(pictureSize / 2f, pictureSize / 2f, pictureSize / 2f, pictureSize / 2f),
            paint,
        )

        paint.color = SK_ColorRED
        canvas.drawLine(
            pictureSize / 2f, pictureSize * 1f / 3f,
            pictureSize / 2f, pictureSize * 2f / 3f, paint,
        )
        canvas.drawLine(
            pictureSize * 1f / 3f, pictureSize / 2f,
            pictureSize * 2f / 3f, pictureSize / 2f, paint,
        )

        paint.color = SK_ColorBLACK
        paint.style = SkPaint.Style.kStroke_Style
        canvas.drawRect(SkRect.MakeWH(pictureSize, pictureSize), paint)
    }

    private data class Tile(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val offsetX: Float, val offsetY: Float,
    )

    private companion object {
        const val kPictureSize: Float = 1f
        const val kFillSize: Float = 100f
        const val kRowSize: Int = 6

        val tiles: Array<Tile> = arrayOf(
            Tile(    0f,     0f,    1f,    1f,     0f,    0f),
            Tile( -0.5f,  -0.5f,    1f,    1f,     0f,    0f),
            Tile(  0.5f,   0.5f,    1f,    1f,     0f,    0f),

            Tile(    0f,     0f,  1.5f,  1.5f,     0f,    0f),
            Tile( -0.5f,  -0.5f,  1.5f,  1.5f,     0f,    0f),
            Tile(  0.5f,   0.5f,  1.5f,  1.5f,     0f,    0f),

            Tile(    0f,     0f,  0.5f,  0.5f,     0f,    0f),
            Tile( 0.25f,  0.25f,  0.5f,  0.5f,     0f,    0f),
            Tile(-0.25f, -0.25f,  0.5f,  0.5f,     0f,    0f),

            Tile(    0f,     0f,    1f,    1f,   0.5f,  0.5f),
            Tile( -0.5f,  -0.5f,    1f,    1f,   0.5f,  0.5f),
            Tile(  0.5f,   0.5f,    1f,    1f,   0.5f,  0.5f),

            Tile(    0f,     0f,  1.5f,  1.5f,   0.5f,  0.5f),
            Tile( -0.5f,  -0.5f,  1.5f,  1.5f,   0.5f,  0.5f),
            Tile(  0.5f,   0.5f,  1.5f,  1.5f,   0.5f,  0.5f),

            Tile(    0f,     0f,  1.5f,    1f,     0f,    0f),
            Tile( -0.5f,  -0.5f,  1.5f,    1f,     0f,    0f),
            Tile(  0.5f,   0.5f,  1.5f,    1f,     0f,    0f),

            Tile(    0f,     0f,  0.5f,    1f,     0f,    0f),
            Tile( 0.25f,  0.25f,  0.5f,    1f,     0f,    0f),
            Tile(-0.25f, -0.25f,  0.5f,    1f,     0f,    0f),

            Tile(    0f,     0f,    1f,  1.5f,     0f,    0f),
            Tile( -0.5f,  -0.5f,    1f,  1.5f,     0f,    0f),
            Tile(  0.5f,   0.5f,    1f,  1.5f,     0f,    0f),

            Tile(    0f,     0f,    1f,  0.5f,     0f,    0f),
            Tile( 0.25f,  0.25f,    1f,  0.5f,     0f,    0f),
            Tile(-0.25f, -0.25f,    1f,  0.5f,     0f,    0f),
        )
    }
}
