package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center oracle for one hard winding polygon clip and ordered opaque rectangles. */
class SurfaceSrgbClipPathCpuOracle(
    background: IntArray,
    contours: List<Contour>,
    draws: List<OpaqueRect>,
) : CpuOracle {
    companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
        private const val EDGE_EPSILON = 1.0e-9
    }

    data class Point(val x: Float, val y: Float) {
        init {
            require(x.isFinite() && y.isFinite()) { "clip path points must be finite" }
        }
    }

    class Contour(points: List<Point>) {
        val points: List<Point> = points.toList()

        init {
            require(this.points.size >= 3) { "clip path contours require at least three points" }
            require(this.points.indices.all { index ->
                this.points[index] != this.points[(index + 1) % this.points.size]
            }) { "clip path contours must not contain adjacent duplicate points" }
        }
    }

    data class OpaqueRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: IntArray,
    ) {
        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                "draw bounds must be finite"
            }
            require(right > left && bottom > top) { "draw bounds must be non-empty" }
            require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
        }

        fun copyColor(): OpaqueRect = copy(color = color.copyOf())
    }

    private val background = background.copyOf().also(::requireRgba)
    private val contours = contours.map { Contour(it.points) }
    private val draws = draws.map(OpaqueRect::copyColor)

    init {
        require(this.contours.isNotEmpty()) { "clip path oracle requires at least one contour" }
        require(this.draws.isNotEmpty()) { "clip path oracle requires at least one draw" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5
            val py = y + 0.5
            var color = background
            if (containsClip(px, py)) {
                draws.forEach { draw ->
                    if (px >= draw.left && px < draw.right && py >= draw.top && py < draw.bottom) {
                        color = draw.color
                    }
                }
            }
            val offset = (y * width + x) * 4
            for (channel in 0 until 4) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun containsClip(pointX: Double, pointY: Double): Boolean {
        var winding = 0
        var onEdge = false
        contours.forEach { contour ->
            contour.points.indices.forEach { index ->
                val start = contour.points[index]
                val end = contour.points[(index + 1) % contour.points.size]
                val startX = start.x.toDouble()
                val startY = start.y.toDouble()
                val endX = end.x.toDouble()
                val endY = end.y.toDouble()
                if (pointOnSegment(pointX, pointY, startX, startY, endX, endY)) {
                    onEdge = true
                } else {
                    val left = (endX - startX) * (pointY - startY) -
                        (pointX - startX) * (endY - startY)
                    if (startY <= pointY) {
                        if (endY > pointY && left > 0.0) winding++
                    } else if (endY <= pointY && left < 0.0) {
                        winding--
                    }
                }
            }
        }
        return onEdge || winding != 0
    }

    private fun pointOnSegment(
        pointX: Double,
        pointY: Double,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): Boolean {
        val cross = (pointX - startX) * (endY - startY) - (pointY - startY) * (endX - startX)
        if (kotlin.math.abs(cross) > EDGE_EPSILON) return false
        return pointX >= minOf(startX, endX) - EDGE_EPSILON &&
            pointX <= maxOf(startX, endX) + EDGE_EPSILON &&
            pointY >= minOf(startY, endY) - EDGE_EPSILON &&
            pointY <= maxOf(startY, endY) + EDGE_EPSILON
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
    }

}
