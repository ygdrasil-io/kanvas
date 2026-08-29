package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center oracle for an opaque round-cap stroke inside a winding triangle clip. */
class SurfaceSrgbClipPathRoundCapStrokeCpuOracle(
    private val background: IntArray,
    points: List<Point>,
    private val strokeStart: Point,
    private val strokeEnd: Point,
    private val strokeWidth: Double,
    private val color: IntArray,
    private val clipInverted: Boolean = false,
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
        require(strokeWidth.isFinite() && strokeWidth > 0.0) { "stroke width must be finite and positive" }
        require(lengthSquared.isFinite() && lengthSquared > 0.0) { "stroke segment must be finite and non-degenerate" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 32 && height == 32) { "fixture requires 32x32 target" }
        val output = ByteArray(width * height * 4)
        val radiusSquared = (strokeWidth / 2.0) * (strokeWidth / 2.0)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val pixelColor = if (contains(px, py) && coversRoundStroke(px, py, radiusSquared)) color else background
            val offset = (y * width + x) * 4
            for (channel in 0..3) output[offset + channel] = pixelColor[channel].toByte()
        }
        return output
    }

    private fun coversRoundStroke(x: Double, y: Double, radiusSquared: Double): Boolean {
        val projection = ((x - strokeStart.x) * dx + (y - strokeStart.y) * dy) / lengthSquared
        val clamped = projection.coerceIn(0.0, 1.0)
        val closestX = strokeStart.x + clamped * dx
        val closestY = strokeStart.y + clamped * dy
        val distanceX = x - closestX
        val distanceY = y - closestY
        return distanceX * distanceX + distanceY * distanceY <= radiusSquared
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
