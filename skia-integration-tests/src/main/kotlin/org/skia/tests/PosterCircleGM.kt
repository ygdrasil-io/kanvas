package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkColorSetRGB
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkV3
import org.skia.tools.ToolUtils
import kotlin.math.PI

/**
 * Port of Skia's `gm/postercircle.cpp::PosterCircleGM` (`poster_circle`,
 * 600 × 450).
 *
 * Mimics https://output.jsbin.com/falefice/1/quiet?CC_POSTER_CIRCLE, which
 * can't be captured as an SKP due to many 3D layers being composited
 * post-SKP capture (see skbug.com/40040313).
 *
 * Pre-builds 12 numbered rounded-rect "posters" (alternating mauve and
 * violet) into raster snapshots, then draws them as the back / front
 * passes of three vertically-stacked rings rotating around the world
 * Y axis through a CSS-style perspective projection
 * (`SkM44::Translate(0, 0, kRingRadius)` after a Y rotation, projected
 * by `proj.setRC(3, 2, -1/800)`).
 *
 * `onAnimate` is honoured by the upstream GM ; we render the static
 * `fTime = 0` frame (matches the reference PNG which captures the same
 * pose).
 */
public class PosterCircleGM : GM() {

    override fun getName(): String = "poster_circle"
    override fun getISize(): SkISize = SkISize.Make(kStageWidth, kStageHeight + 50)

    private val fPosterImages: Array<SkImage?> = arrayOfNulls(kNumAngles)
    private val fTime: SkScalar = 0f

    override fun onOnceBeforeDraw() {
        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kAntiAlias
            isEmbolden = true
            size = 24f
        }

        for (i in 0 until kNumAngles) {
            // Skia uses a single SkSurface reused 12 times — each draw
            // overwrites the previous, and a snapshot is taken after every
            // i. We replicate that exactly (separate surfaces would
            // sidestep the implicit COW that upstream relies on for
            // makeImageSnapshot to be cheap).
            val info = SkImageInfo.MakeN32Premul(kPosterSize, kPosterSize)
            val surface: SkSurface = SkSurface.MakeRaster(info)
            val canvas = surface.canvas

            val fillPaint = SkPaint().apply {
                isAntiAlias = true
                color = if (i % 2 == 0) SkColorSetRGB(0x99, 0x5C, 0x7F)
                        else SkColorSetRGB(0x83, 0x5A, 0x99)
            }
            canvas.drawRRect(
                SkRRect.MakeRectXY(SkRect.MakeWH(kPosterSize.toFloat(), kPosterSize.toFloat()), 10f, 10f),
                fillPaint,
            )

            val label = i.toString()
            val labelBounds = SkRect.MakeEmpty()
            font.measureText(label, label.length, SkTextEncoding.kUTF8, labelBounds)
            val labelX = 0.5f * kPosterSize - 0.5f * labelBounds.width()
            val labelY = 0.5f * kPosterSize + 0.5f * labelBounds.height()

            val labelPaint = SkPaint().apply { isAntiAlias = true }
            canvas.drawString(label, labelX, labelY, font, labelPaint)

            fPosterImages[i] = surface.makeImageSnapshot()
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // See https://developer.mozilla.org/en-US/docs/Web/CSS/transform-function/perspective
        // for the projection matrix when `--webkit-perspective: 800px`.
        val proj = SkM44().apply { setRC(3, 2, -1f / 800f) }

        for (pass in 0 until 2) {
            // Want to draw 90 to 270 first (the back), then 270 to 90 (the front),
            // but do all 3 ring backsides then their frontsides (front projections
            // overlap across rings). Skip the X-axis rotation so back-to-front
            // ordering stays simple.
            val drawFront = pass > 0

            for (y in 0 until 3) {
                val ringY = (y - 1).toFloat() * (kPosterSize + 10f)
                for (i in 0 until kNumAngles) {
                    val yDuration = 5f - y
                    val timeMod = scalarMod(fTime / yDuration, yDuration)
                    val yRotation = scalarMod(kAngleStep * i + 360f * timeMod, 360f)

                    // Manually-tuned limits aligning with the projection.
                    val kBackMinAngle = 70f
                    val kBackMaxAngle = 290f

                    if (drawFront) {
                        if (yRotation >= kBackMinAngle && yRotation <= kBackMaxAngle) continue
                    } else {
                        if (yRotation < kBackMinAngle || yRotation > kBackMaxAngle) continue
                    }

                    c.save()

                    // model = Translate(W/2, H/2 + 25) · proj
                    //       · Translate(0, ringY, 0)
                    //       · RotateY(yRotation)
                    //       · Translate(0, 0, kRingRadius)
                    val model = SkM44.translate(kStageWidth / 2f, kStageHeight / 2f + 25f, 0f)
                        .preConcat(proj)
                        .preConcat(SkM44.translate(0f, ringY, 0f))
                        .preConcat(SkM44.rotate(SkV3(0f, 1f, 0f), degreesToRadians(yRotation)))
                        .preConcat(SkM44.translate(0f, 0f, kRingRadius.toFloat()))
                    c.concat(model)

                    val poster = SkRect.MakeLTRB(
                        -0.5f * kPosterSize, -0.5f * kPosterSize,
                        0.5f * kPosterSize,  0.5f * kPosterSize,
                    )
                    val fillPaint = SkPaint().apply {
                        isAntiAlias = true
                        alphaf = 0.7f
                    }
                    fPosterImages[i]?.let { img ->
                        val srcFull = SkRect.MakeIWH(img.width, img.height)
                        c.drawImageRect(
                            image = img,
                            src = srcFull,
                            dst = poster,
                            sampling = SkSamplingOptions(SkFilterMode.kLinear),
                            paint = fillPaint,
                        )
                    }

                    c.restore()
                }
            }
        }
    }

    private companion object {
        const val kAngleStep: Int = 30
        const val kNumAngles: Int = 12 // 0 through 330 degrees

        const val kStageWidth: Int = 600
        const val kStageHeight: Int = 400
        const val kRingRadius: Int = 200
        const val kPosterSize: Int = 100

        /** Mirrors Skia's `SkScalarMod(x, y) = x - floor(x/y) * y`. */
        fun scalarMod(x: Float, y: Float): Float {
            if (y == 0f) return 0f
            return x - kotlin.math.floor(x / y) * y
        }

        fun degreesToRadians(deg: Float): Float = (deg.toDouble() * PI / 180.0).toFloat()
    }
}
