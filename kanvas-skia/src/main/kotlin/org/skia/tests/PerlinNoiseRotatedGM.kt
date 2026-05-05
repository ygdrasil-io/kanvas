package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPerlinNoiseShader
import org.skia.foundation.SkShader
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseRotatedGM`. Repro for
 * `skbug.com/40045243` — Perlin shader was failing to track canvas
 * rotation, producing visible non-rotated noise inside a rotated rect.
 *
 * Layout: 3 × 2 grid of `100 × 100` cells. Two rows = `kFractalNoise` /
 * `kTurbulence`, three columns = rotations `{0°, 10°, 80°}`. Each cell
 * contains a `60 × 60` rect rendered with the noise shader, plus a 5×5
 * outline marker at origin and a stroke-outlined frame, all under the
 * rotation. Total `(2·10 + 3·100) × (2·10 + 2·100) = 320 × 220`.
 */
public class PerlinNoiseRotatedGM : GM() {

    override fun getName(): String = "perlinnoise_rotated"
    override fun getISize(): SkISize = SkISize.Make(
        2 * kPad + kCellsX * kCellSize.width,
        2 * kPad + kCellsY * kCellSize.height,
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val outline = SkPaint().apply {
            color = SK_ColorBLACK
            strokeWidth = 2.0f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
        }

        val rectToDraw = SkRect.MakeWH(kRectSize.width.toFloat(), kRectSize.height.toFloat())
        val marker = SkRect.MakeWH(5f, 5f)

        var yOffset = kPad.toFloat()
        for (type in listOf(Type.FRACTAL, Type.TURBULENCE)) {
            var xOffset = kPad.toFloat()
            val noisePaint = SkPaint().apply {
                shader = makeShader(type, 0.05f, 0.05f, 1, 0f, kRectSize)
            }
            for (rotation in listOf(0f, 10f, 80f)) {
                val saveCount = c.save()
                c.translate(xOffset, yOffset)
                c.drawRect(SkRect.MakeWH(kCellSize.width.toFloat(), kCellSize.height.toFloat()), outline)

                c.save()
                c.translate(kCellSize.width / 2f, kCellSize.height / 2f)
                c.rotate(rotation)
                c.translate(-kRectSize.width / 2f, -kRectSize.height / 2f)
                c.drawRect(rectToDraw, noisePaint)
                c.drawRect(rectToDraw, outline)
                c.drawRect(marker, outline)
                c.restoreToCount(saveCount)

                xOffset += kCellSize.width
            }
            yOffset += kCellSize.height.toFloat()
        }
    }

    private enum class Type { FRACTAL, TURBULENCE }

    private fun makeShader(type: Type, fx: Float, fy: Float, oct: Int, seed: Float, size: SkISize): SkShader =
        when (type) {
            Type.FRACTAL    -> SkPerlinNoiseShader.MakeFractalNoise(fx, fy, oct, seed, null)
            Type.TURBULENCE -> SkPerlinNoiseShader.MakeTurbulence(fx, fy, oct, seed, null)
        }

    private companion object {
        val kCellSize: SkISize = SkISize.Make(100, 100)
        val kRectSize: SkISize = SkISize.Make(60, 60)
        const val kPad: Int = 10
        const val kCellsX: Int = 3
        const val kCellsY: Int = 2
    }
}
