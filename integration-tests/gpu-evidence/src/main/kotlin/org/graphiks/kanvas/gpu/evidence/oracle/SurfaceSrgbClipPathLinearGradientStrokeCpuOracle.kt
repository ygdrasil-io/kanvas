package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent device-space oracle for a two-stop clamp linear-gradient stroke in a path clip. */
class SurfaceSrgbClipPathLinearGradientStrokeCpuOracle(
    private val background: IntArray,
    points: List<Point>,
    private val strokeStart: Point,
    private val strokeEnd: Point,
    private val strokeWidth: Double,
    private val gradientStart: Point,
    private val gradientEnd: Point,
    private val startColor: IntArray,
    private val endColor: IntArray,
    private val clipInverted: Boolean = false,
    private val squareCaps: Boolean = true,
) : CpuOracle {
    data class Point(val x: Double, val y: Double)

    private val contour = points.toList()
    private val strokeDx = strokeEnd.x - strokeStart.x
    private val strokeDy = strokeEnd.y - strokeStart.y
    private val strokeLengthSquared = strokeDx * strokeDx + strokeDy * strokeDy
    private val gradientDx = gradientEnd.x - gradientStart.x
    private val gradientDy = gradientEnd.y - gradientStart.y
    private val gradientLengthSquared = gradientDx * gradientDx + gradientDy * gradientDy

    init {
        require(contour.size == 3) { "fixture requires a triangle clip" }
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) { "colors must be RGBA" }
        require((background + startColor + endColor).all { it in 0..255 }) { "colors must be byte channels" }
        require(strokeWidth.isFinite() && strokeWidth > 0.0) { "stroke width must be finite and positive" }
        require(strokeLengthSquared.isFinite() && strokeLengthSquared > 0.0) { "stroke must be non-degenerate" }
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
            val color = if (contains(px, py) && coversStroke(px, py, halfWidthSquared)) {
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

    private fun contains(x: Double, y: Double): Boolean {
        var winding = 0
        contour.indices.forEach { index ->
            val a = contour[index]
            val b = contour[(index + 1) % contour.size]
            val cross = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
            if (a.y <= y) {
                if (b.y > y && cross > 0.0) winding++
            } else if (b.y <= y && cross < 0.0) winding--
        }
        val inside = winding != 0
        return if (clipInverted) !inside else inside
    }

    private fun coversStroke(x: Double, y: Double, halfWidthSquared: Double): Boolean {
        val projection = ((x - strokeStart.x) * strokeDx + (y - strokeStart.y) * strokeDy) / strokeLengthSquared
        val capExtension = if (squareCaps) sqrt(halfWidthSquared / strokeLengthSquared) else 0.0
        if (projection !in -capExtension..(1.0 + capExtension)) return false
        val closestX = strokeStart.x + projection * strokeDx
        val closestY = strokeStart.y + projection * strokeDy
        val distanceX = x - closestX
        val distanceY = y - closestY
        return distanceX * distanceX + distanceY * distanceY <= halfWidthSquared
    }
}
