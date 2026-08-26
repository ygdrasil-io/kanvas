package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent device-space pixel-center oracle for a clamp gradient direct triangle in a hard winding path clip. */
class SurfaceSrgbClipPathDirectTriangleLinearGradientCpuOracle(
    private val background: IntArray,
    clipPoints: List<SurfaceSrgbClipPathCpuOracle.Point>,
    private val triangle: Triangle,
    private val start: SurfaceSrgbGradientCpuOracle.Point,
    private val end: SurfaceSrgbGradientCpuOracle.Point,
    private val startColor: IntArray,
    private val endColor: IntArray,
) : CpuOracle {
    data class Triangle(
        val first: SurfaceSrgbClipPathCpuOracle.Point,
        val second: SurfaceSrgbClipPathCpuOracle.Point,
        val third: SurfaceSrgbClipPathCpuOracle.Point,
    )

    private val contour = clipPoints.toList()

    init {
        require(contour.size >= 3) { "clip path requires at least three points" }
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) { "colors must be RGBA" }
        require((background + startColor + endColor).all { it in 0..255 }) { "colors must be byte channels" }
        require(twiceArea(triangle.first, triangle.second, triangle.third) != 0.0) {
            "direct triangle must be non-degenerate"
        }
        require(start.x != end.x || start.y != end.y) { "linear gradient must be non-degenerate" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        val dx = end.x.toDouble() - start.x
        val dy = end.y.toDouble() - start.y
        val lengthSquared = dx * dx + dy * dy
        val a = SurfaceSrgbOracleMath.decodeStraight(startColor)
        val b = SurfaceSrgbOracleMath.decodeStraight(endColor)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val color = if (containsClip(px, py) && containsTriangle(px, py)) {
                val t = if (lengthSquared == 0.0) 0.0 else
                    (((px - start.x) * dx + (py - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
                SurfaceSrgbOracleMath.storeSrgb(
                    SurfaceSrgbOracleMath.LinearPremul(
                        a.red + (b.red - a.red) * t,
                        a.green + (b.green - a.green) * t,
                        a.blue + (b.blue - a.blue) * t,
                        1.0,
                    ),
                )
            } else background
            val offset = (y * width + x) * 4
            for (channel in 0..3) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun containsClip(x: Double, y: Double): Boolean {
        var winding = 0
        contour.indices.forEach { index ->
            val a = contour[index]
            val b = contour[(index + 1) % contour.size]
            val cross = edge(a, b, x, y)
            if (a.y <= y) {
                if (b.y > y && cross > 0.0) winding++
            } else if (b.y <= y && cross < 0.0) winding--
        }
        return winding != 0
    }

    private fun containsTriangle(x: Double, y: Double): Boolean {
        val first = edge(triangle.first, triangle.second, x, y)
        val second = edge(triangle.second, triangle.third, x, y)
        val third = edge(triangle.third, triangle.first, x, y)
        return (first >= 0.0 && second >= 0.0 && third >= 0.0) ||
            (first <= 0.0 && second <= 0.0 && third <= 0.0)
    }

    private fun twiceArea(
        first: SurfaceSrgbClipPathCpuOracle.Point,
        second: SurfaceSrgbClipPathCpuOracle.Point,
        third: SurfaceSrgbClipPathCpuOracle.Point,
    ): Double = edge(first, second, third.x.toDouble(), third.y.toDouble())

    private fun edge(
        first: SurfaceSrgbClipPathCpuOracle.Point,
        second: SurfaceSrgbClipPathCpuOracle.Point,
        x: Double,
        y: Double,
    ): Double = (second.x - first.x).toDouble() * (y - first.y) -
        (second.y - first.y).toDouble() * (x - first.x)

}
