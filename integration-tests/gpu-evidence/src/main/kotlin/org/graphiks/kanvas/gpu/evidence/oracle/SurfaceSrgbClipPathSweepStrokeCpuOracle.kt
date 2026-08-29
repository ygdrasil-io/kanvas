package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Independent device-space oracle for the bounded full-turn square-cap sweep stroke. */
class SurfaceSrgbClipPathSweepStrokeCpuOracle(
    private val background: IntArray,
    points: List<Point>,
    private val strokeStart: Point,
    private val strokeEnd: Point,
    private val strokeWidth: Double,
    private val center: Point,
    private val startColor: IntArray,
    private val endColor: IntArray,
) : CpuOracle {
    data class Point(val x: Double, val y: Double)

    private val contour = points.toList()
    private val dx = strokeEnd.x - strokeStart.x
    private val dy = strokeEnd.y - strokeStart.y
    private val lengthSquared = dx * dx + dy * dy

    init {
        require(contour.size == 3) { "fixture requires a triangle clip" }
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) {
            "colors must be RGBA"
        }
        require((background + startColor + endColor).all { it in 0..255 }) {
            "colors must be byte channels"
        }
        require(strokeWidth.isFinite() && strokeWidth == 4.0) {
            "fixture requires a width-four stroke"
        }
        require(lengthSquared.isFinite() && lengthSquared > 0.0) {
            "stroke segment must be finite and non-degenerate"
        }
        require(center.x.isFinite() && center.y.isFinite()) { "center must be finite" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        val start = SurfaceSrgbOracleMath.decodeStraight(startColor)
        val end = SurfaceSrgbOracleMath.decodeStraight(endColor)
        val halfWidthSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        val fullTurn = 2.0 * PI
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val color = if (contains(px, py) && coversSquareStroke(px, py, halfWidthSquared)) {
                val rawTurn = atan2(py - center.y, px - center.x) / fullTurn
                val t = (rawTurn - kotlin.math.floor(rawTurn)).coerceIn(0.0, 1.0)
                SurfaceSrgbOracleMath.storeSrgb(
                    SurfaceSrgbOracleMath.LinearPremul(
                        start.red + (end.red - start.red) * t,
                        start.green + (end.green - start.green) * t,
                        start.blue + (end.blue - start.blue) * t,
                        1.0,
                    ),
                )
            } else {
                background
            }
            val offset = (y * width + x) * 4
            for (channel in 0..3) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun coversSquareStroke(x: Double, y: Double, halfWidthSquared: Double): Boolean {
        val projection = ((x - strokeStart.x) * dx + (y - strokeStart.y) * dy) / lengthSquared
        val capExtension = sqrt(halfWidthSquared / lengthSquared)
        if (projection !in -capExtension..(1.0 + capExtension)) return false
        val closestX = strokeStart.x + projection * dx
        val closestY = strokeStart.y + projection * dy
        val distanceX = x - closestX
        val distanceY = y - closestY
        return distanceX * distanceX + distanceY * distanceY <= halfWidthSquared
    }

    private fun contains(x: Double, y: Double): Boolean {
        var winding = 0
        contour.indices.forEach { index ->
            val a = contour[index]
            val b = contour[(index + 1) % contour.size]
            val cross = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
            if (a.y <= y) {
                if (b.y > y && cross > 0.0) winding++
            } else if (b.y <= y && cross < 0.0) {
                winding--
            }
        }
        return winding != 0
    }
}

