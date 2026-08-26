package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center oracle for one hard winding polygon clip and ordered opaque primitives. */
class SurfaceSrgbClipPathCpuOracle(
    background: IntArray,
    contours: List<Contour>,
    draws: List<OpaqueDraw>,
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

    sealed interface OpaqueDraw {
        val color: IntArray

        fun contains(pointX: Double, pointY: Double): Boolean

        fun copyColor(): OpaqueDraw
    }

    data class OpaqueRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        override val color: IntArray,
    ) : OpaqueDraw {
        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
                "draw bounds must be finite"
            }
            require(right > left && bottom > top) { "draw bounds must be non-empty" }
            require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
        }

        override fun contains(pointX: Double, pointY: Double): Boolean =
            pointX >= left && pointX < right && pointY >= top && pointY < bottom

        override fun copyColor(): OpaqueRect = copy(color = color.copyOf())
    }

    /** Exact direct-triangle membership is intentionally independent from the renderer lowering. */
    data class OpaqueTriangle(
        val first: Point,
        val second: Point,
        val third: Point,
        override val color: IntArray,
    ) : OpaqueDraw {
        init {
            require(setOf(first, second, third).size == 3) { "direct triangles need three distinct points" }
            require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
            require(twiceArea(first, second, third) != 0.0) { "direct triangles must be non-degenerate" }
        }

        override fun contains(pointX: Double, pointY: Double): Boolean {
            val firstEdge = edge(first, second, pointX, pointY)
            val secondEdge = edge(second, third, pointX, pointY)
            val thirdEdge = edge(third, first, pointX, pointY)
            return (firstEdge >= -EDGE_EPSILON && secondEdge >= -EDGE_EPSILON && thirdEdge >= -EDGE_EPSILON) ||
                (firstEdge <= EDGE_EPSILON && secondEdge <= EDGE_EPSILON && thirdEdge <= EDGE_EPSILON)
        }

        override fun copyColor(): OpaqueTriangle = copy(color = color.copyOf())

        private fun edge(start: Point, end: Point, pointX: Double, pointY: Double): Double =
            (end.x - start.x).toDouble() * (pointY - start.y) -
                (end.y - start.y).toDouble() * (pointX - start.x)

        private fun twiceArea(first: Point, second: Point, third: Point): Double =
            edge(first, second, third.x.toDouble(), third.y.toDouble())
    }

    private val background = background.copyOf().also(::requireRgba)
    private val contours = contours.map { Contour(it.points) }
    private val draws = draws.map(OpaqueDraw::copyColor)

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
                    if (draw.contains(px, py)) {
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
