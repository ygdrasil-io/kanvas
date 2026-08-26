package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-centre oracle for a winding triangle clip and opaque double rounded rect. */
class SurfaceSrgbClipPathDRRectCpuOracle(
    private val triangle: List<Point>, private val outer: RRect, private val inner: RRect, fill: IntArray,
) : CpuOracle {
    data class Point(val x: Float, val y: Float)
    data class RRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val radiusX: Float, val radiusY: Float)
    private val fill = fill.copyOf().also { require(it.size == 4 && it.all { channel -> channel in 0..255 }) }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64)
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val visible = windingTriangle(x + .5, y + .5) && inRRect(x + .5, y + .5, outer) && !inRRect(x + .5, y + .5, inner)
                if (visible) fill.indices.forEach { channel -> output[(y * width + x) * 4 + channel] = fill[channel].toByte() }
            }
        }
    }

    private fun windingTriangle(x: Double, y: Double): Boolean {
        fun edge(a: Point, b: Point) = (b.x - a.x).toDouble() * (y - a.y) - (b.y - a.y).toDouble() * (x - a.x)
        val ab = edge(triangle[0], triangle[1]); val bc = edge(triangle[1], triangle[2]); val ca = edge(triangle[2], triangle[0])
        return (ab >= 0 && bc >= 0 && ca >= 0) || (ab <= 0 && bc <= 0 && ca <= 0)
    }

    private fun inRRect(x: Double, y: Double, r: RRect): Boolean {
        if (x < r.left || x >= r.right || y < r.top || y >= r.bottom) return false
        val cx = x.coerceIn((r.left + r.radiusX).toDouble(), (r.right - r.radiusX).toDouble())
        val cy = y.coerceIn((r.top + r.radiusY).toDouble(), (r.bottom - r.radiusY).toDouble())
        val dx = (x - cx) / r.radiusX; val dy = (y - cy) / r.radiusY
        return dx * dx + dy * dy <= 1.0
    }
}
