package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-centre oracle for a winding triangle clip and opaque double rounded rect. */
class SurfaceSrgbClipPathDRRectCpuOracle(
    private val triangle: List<Point>, private val outer: RRect, private val inner: RRect, fill: IntArray,
) : CpuOracle {
    data class Point(val x: Float, val y: Float)
    data class Radii(val x: Float, val y: Float)
    data class RRect(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val topLeft: Radii, val topRight: Radii, val bottomRight: Radii, val bottomLeft: Radii,
    ) {
        constructor(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) :
            this(left, top, right, bottom, Radii(radiusX, radiusY), Radii(radiusX, radiusY), Radii(radiusX, radiusY), Radii(radiusX, radiusY))
    }
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
        val (radii, cx, cy) = when {
            x < r.left + r.topLeft.x && y < r.top + r.topLeft.y -> Triple(r.topLeft, r.left + r.topLeft.x, r.top + r.topLeft.y)
            x >= r.right - r.topRight.x && y < r.top + r.topRight.y -> Triple(r.topRight, r.right - r.topRight.x, r.top + r.topRight.y)
            x >= r.right - r.bottomRight.x && y >= r.bottom - r.bottomRight.y -> Triple(r.bottomRight, r.right - r.bottomRight.x, r.bottom - r.bottomRight.y)
            x < r.left + r.bottomLeft.x && y >= r.bottom - r.bottomLeft.y -> Triple(r.bottomLeft, r.left + r.bottomLeft.x, r.bottom - r.bottomLeft.y)
            else -> return true
        }
        val dx = (x - cx) / radii.x; val dy = (y - cy) / radii.y
        return dx * dx + dy * dy <= 1.0
    }
}
