package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.sqrt

/** Independent device-space pixel-center oracle for an opaque clamp radial gradient in a hard winding path clip. */
class SurfaceSrgbClipPathRadialGradientCpuOracle(
    private val background: IntArray,
    points: List<SurfaceSrgbClipPathCpuOracle.Point>,
    private val drawBounds: SurfaceSrgbGradientCpuOracle.Rect,
    private val center: SurfaceSrgbGradientCpuOracle.Point,
    private val radius: Float,
    private val startColor: IntArray,
    private val endColor: IntArray,
) : CpuOracle {
    private val contour = SurfaceSrgbClipPathCpuOracle.Contour(points).points

    init {
        require(background.size == 4 && startColor.size == 4 && endColor.size == 4) { "colors must be RGBA" }
        require((background + startColor + endColor).all { it in 0..255 }) { "colors must be byte channels" }
        require(radius.isFinite() && radius > 0f) { "radius must be finite and positive" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        val start = SurfaceSrgbOracleMath.decodeStraight(startColor)
        val end = SurfaceSrgbOracleMath.decodeStraight(endColor)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            val color = if (drawBounds.contains(px, py) && contains(px, py)) {
                val dx = px - center.x
                val dy = py - center.y
                val t = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0.0, 1.0)
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
