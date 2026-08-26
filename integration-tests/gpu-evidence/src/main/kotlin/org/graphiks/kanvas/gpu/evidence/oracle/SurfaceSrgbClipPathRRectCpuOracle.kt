package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-centre oracle for one hard triangle clip and an opaque analytic RRect. */
class SurfaceSrgbClipPathRRectCpuOracle(
    background: IntArray,
    private val triangle: List<Point>,
    private val rrect: DeviceRRect,
    fill: IntArray,
    private val triangleClip: TriangleClip = TriangleClip.Winding,
) : CpuOracle {
    enum class TriangleClip { Winding, InverseWinding }
    data class Point(val x: Float, val y: Float)
    data class Radii(val x: Float, val y: Float)
    data class DeviceRRect(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val topLeft: Radii, val topRight: Radii, val bottomRight: Radii, val bottomLeft: Radii,
    )

    private val background = background.copyOf().also(::requireRgba)
    private val fill = fill.copyOf().also(::requireRgba)

    init {
        require(triangle.size == 3 && triangle.all { it.x.isFinite() && it.y.isFinite() })
        require(rrect.left.isFinite() && rrect.top.isFinite() && rrect.right.isFinite() && rrect.bottom.isFinite())
        require(rrect.right > rrect.left && rrect.bottom > rrect.top)
        listOf(rrect.topLeft, rrect.topRight, rrect.bottomRight, rrect.bottomLeft).forEach {
            require(it.x >= 0f && it.y >= 0f && it.x.isFinite() && it.y.isFinite())
        }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == 64 && height == 64) { "fixture requires 64x64 target" }
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                val px = x + 0.5
                val py = y + 0.5
                val inClip = when (triangleClip) {
                    TriangleClip.Winding -> inTriangle(px, py)
                    TriangleClip.InverseWinding -> !inTriangle(px, py)
                }
                val color = if (inClip && inRRect(px, py)) fill else background
                val offset = (y * width + x) * 4
                color.indices.forEach { channel -> output[offset + channel] = color[channel].toByte() }
            }
        }
    }

    private fun inTriangle(x: Double, y: Double): Boolean {
        fun edge(a: Point, b: Point) =
            (b.x - a.x).toDouble() * (y - a.y) - (b.y - a.y).toDouble() * (x - a.x)
        val ab = edge(triangle[0], triangle[1])
        val bc = edge(triangle[1], triangle[2])
        val ca = edge(triangle[2], triangle[0])
        return (ab >= 0.0 && bc >= 0.0 && ca >= 0.0) || (ab <= 0.0 && bc <= 0.0 && ca <= 0.0)
    }

    private fun inRRect(x: Double, y: Double): Boolean {
        if (x < rrect.left || x >= rrect.right || y < rrect.top || y >= rrect.bottom) return false
        val corner = when {
            x < rrect.left + rrect.topLeft.x && y < rrect.top + rrect.topLeft.y ->
                rrect.topLeft to Pair(rrect.left + rrect.topLeft.x, rrect.top + rrect.topLeft.y)
            x >= rrect.right - rrect.topRight.x && y < rrect.top + rrect.topRight.y ->
                rrect.topRight to Pair(rrect.right - rrect.topRight.x, rrect.top + rrect.topRight.y)
            x >= rrect.right - rrect.bottomRight.x && y >= rrect.bottom - rrect.bottomRight.y ->
                rrect.bottomRight to Pair(rrect.right - rrect.bottomRight.x, rrect.bottom - rrect.bottomRight.y)
            x < rrect.left + rrect.bottomLeft.x && y >= rrect.bottom - rrect.bottomLeft.y ->
                rrect.bottomLeft to Pair(rrect.left + rrect.bottomLeft.x, rrect.bottom - rrect.bottomLeft.y)
            else -> return true
        }
        val radii = corner.first
        if (radii.x == 0f || radii.y == 0f) return true
        val dx = (x - corner.second.first) / radii.x
        val dy = (y - corner.second.second) / radii.y
        return dx * dx + dy * dy <= 1.0
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 })
    }
}
