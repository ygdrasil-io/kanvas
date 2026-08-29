package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent device-space oracle for one opaque butt/square stroke inside a winding triangle clip. */
class SurfaceSrgbClipPathSolidStrokeCpuOracle(
    private val background: IntArray,
    points: List<Point>,
    private val strokeStart: Point,
    private val strokeEnd: Point,
    private val strokeWidth: Double,
    private val color: IntArray,
    private val clipInverted: Boolean = false,
    private val squareCaps: Boolean = false,
) : CpuOracle {
    data class Point(val x: Double, val y: Double)

    private val contour = points.toList()
    private val dx = strokeEnd.x - strokeStart.x
    private val dy = strokeEnd.y - strokeStart.y
    private val lengthSquared = dx * dx + dy * dy

    init {
        require(contour.size == 3) { "fixture requires a triangle clip" }
        require(background.size == 4 && color.size == 4) { "colors must be RGBA" }
        require((background + color).all { it in 0..255 }) { "colors must be byte channels" }
        require(strokeWidth.isFinite() && strokeWidth > 0.0) {
            "stroke width must be finite and positive"
        }
        require(lengthSquared.isFinite() && lengthSquared > 0.0) {
            "stroke segment must be finite and non-degenerate"
        }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        val halfWidthSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val pixelColor = if (contains(px, py) && coversStroke(px, py, halfWidthSquared)) color else background
            val offset = (y * width + x) * 4
            for (channel in 0..3) output[offset + channel] = pixelColor[channel].toByte()
        }
        return output
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
        val inside = winding != 0
        return if (clipInverted) !inside else inside
    }
}
