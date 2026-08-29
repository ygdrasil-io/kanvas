package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent device-space oracle for a two-stop clamp linear-gradient stroke through an EvenOdd hole. */
class SurfaceSrgbClipPathEvenOddLinearGradientStrokeCpuOracle(
    private val background: IntArray,
    private val outer: Rect,
    private val inner: Rect,
    private val strokeStart: Point,
    private val strokeEnd: Point,
    private val strokeWidth: Double,
    private val gradientStart: Point,
    private val gradientEnd: Point,
    private val startColor: IntArray,
    private val endColor: IntArray,
    private val inverse: Boolean = false,
    private val squareCaps: Boolean = false,
) : CpuOracle {
    data class Point(val x: Double, val y: Double)
    data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private val dx = strokeEnd.x - strokeStart.x
    private val dy = strokeEnd.y - strokeStart.y
    private val lengthSquared = dx * dx + dy * dy
    private val gradientDx = gradientEnd.x - gradientStart.x
    private val gradientDy = gradientEnd.y - gradientStart.y
    private val gradientLengthSquared = gradientDx * gradientDx + gradientDy * gradientDy

    init {
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) { "colors must be RGBA" }
        require((background + startColor + endColor).all { it in 0..255 }) { "colors must be byte channels" }
        require(outer.left < outer.right && outer.top < outer.bottom) { "outer rect must be ordered" }
        require(inner.left < inner.right && inner.top < inner.bottom) { "inner rect must be ordered" }
        require(strokeWidth.isFinite() && strokeWidth > 0.0) { "stroke width must be finite and positive" }
        require(lengthSquared.isFinite() && lengthSquared > 0.0) { "stroke must be non-degenerate" }
        require(gradientLengthSquared.isFinite() && gradientLengthSquared > 0.0) { "gradient axis must be non-degenerate" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        val start = SurfaceSrgbOracleMath.decodeStraight(startColor)
        val end = SurfaceSrgbOracleMath.decodeStraight(endColor)
        val halfWidthSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val color = if (containsEvenOdd(px, py) && coversStroke(px, py, halfWidthSquared)) {
                val t = (((px - gradientStart.x) * gradientDx + (py - gradientStart.y) * gradientDy) /
                    gradientLengthSquared).coerceIn(0.0, 1.0)
                SurfaceSrgbOracleMath.storeSrgb(
                    SurfaceSrgbOracleMath.LinearPremul(
                        start.red + (end.red - start.red) * t,
                        start.green + (end.green - start.green) * t,
                        start.blue + (end.blue - start.blue) * t,
                        1.0,
                    ),
                )
            } else background
            val offset = (y * width + x) * 4
            for (channel in 0..3) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun containsEvenOdd(x: Double, y: Double): Boolean {
        val evenOdd = outer.contains(x, y).xor(inner.contains(x, y))
        return if (inverse) !evenOdd else evenOdd
    }

    private fun coversStroke(x: Double, y: Double, halfWidthSquared: Double): Boolean {
        val projection = ((x - strokeStart.x) * dx + (y - strokeStart.y) * dy) / lengthSquared
        val capExtension = if (squareCaps) sqrt(halfWidthSquared / lengthSquared) else 0.0
        if (projection !in -capExtension..(1.0 + capExtension)) return false
        val closestX = strokeStart.x + projection * dx
        val closestY = strokeStart.y + projection * dy
        val distanceX = x - closestX
        val distanceY = y - closestY
        return distanceX * distanceX + distanceY * distanceY <= halfWidthSquared
    }

    private fun Rect.contains(x: Double, y: Double): Boolean =
        x > left && x < right && y > top && y < bottom
}
