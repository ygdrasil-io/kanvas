package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent device-space pixel-center oracle for an opaque clamp linear gradient in a hard winding path clip. */
class SurfaceSrgbClipPathLinearGradientCpuOracle(
    private val background: IntArray,
    points: List<SurfaceSrgbClipPathCpuOracle.Point>,
    private val drawBounds: SurfaceSrgbGradientCpuOracle.Rect,
    private val start: SurfaceSrgbGradientCpuOracle.Point,
    private val end: SurfaceSrgbGradientCpuOracle.Point,
    private val startColor: IntArray,
    private val endColor: IntArray,
) : CpuOracle {
    private val contour = SurfaceSrgbClipPathCpuOracle.Contour(points).points

    init {
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) { "colors must be RGBA" }
        require((background + startColor + endColor).all { it in 0..255 }) { "colors must be byte channels" }
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
            val color = if (drawBounds.contains(px, py) && contains(px, py)) {
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
        return winding != 0
    }
}
