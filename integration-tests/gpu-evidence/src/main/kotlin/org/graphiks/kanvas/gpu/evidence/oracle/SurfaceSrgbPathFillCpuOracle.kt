package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent pixel-center oracle for opaque polygon path fills. */
class SurfaceSrgbPathFillCpuOracle(
    background: IntArray,
    fill: IntArray,
    contours: List<Contour>,
    private val fillRule: FillRule,
) : CpuOracle {
    data class Point(val x: Float, val y: Float) {
        init {
            require(x.isFinite() && y.isFinite()) { "path points must be finite" }
        }
    }

    class Contour(points: List<Point>) {
        val points: List<Point> = points.toList()

        init {
            require(this.points.size >= 3) { "path contours require at least three points" }
            require(this.points.indices.all { index ->
                this.points[index] != this.points[(index + 1) % this.points.size]
            }) { "path contours must not contain adjacent duplicate points" }
        }
    }

    enum class FillRule { Winding, EvenOdd, InverseWinding, InverseEvenOdd }

    private val background = background.copyOf().also(::requireRgba)
    private val fill = fill.copyOf().also(::requireRgba)
    private val contours = contours.map { Contour(it.points) }

    init {
        require(this.contours.isNotEmpty()) { "path oracle requires at least one contour" }
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val pointX = x + 0.5
            val pointY = y + 0.5
            val baseCovered = if (contours.any { contour ->
                    edges(contour).any { (start, end) -> pointOnSegment(pointX, pointY, start, end) }
                }) {
                true
            } else {
                when (fillRule) {
                    FillRule.Winding, FillRule.InverseWinding ->
                        contours.sumOf { windingNumber(pointX, pointY, it) } != 0
                    FillRule.EvenOdd, FillRule.InverseEvenOdd -> contours.fold(false) { inside, contour ->
                        inside.xor(oddCrossings(pointX, pointY, contour))
                    }
                }
            }
            val covered = when (fillRule) {
                FillRule.InverseWinding, FillRule.InverseEvenOdd -> !baseCovered
                else -> baseCovered
            }
            val color = if (covered) fill else background
            val offset = (y * width + x) * 4
            for (channel in 0 until 4) output[offset + channel] = color[channel].toByte()
        }
        return output
    }

    private fun windingNumber(pointX: Double, pointY: Double, contour: Contour): Int {
        var winding = 0
        edges(contour).forEach { (start, end) ->
            val startX = start.x.toDouble()
            val startY = start.y.toDouble()
            val endX = end.x.toDouble()
            val endY = end.y.toDouble()
            val left = (endX - startX) * (pointY - startY) -
                (pointX - startX) * (endY - startY)
            if (startY <= pointY) {
                if (endY > pointY && left > 0.0) winding++
            } else if (endY <= pointY && left < 0.0) {
                winding--
            }
        }
        return winding
    }

    private fun oddCrossings(pointX: Double, pointY: Double, contour: Contour): Boolean {
        var odd = false
        edges(contour).forEach { (start, end) ->
            val startY = start.y.toDouble()
            val endY = end.y.toDouble()
            if ((startY > pointY) != (endY > pointY)) {
                val intersectionX = start.x.toDouble() +
                    (pointY - startY) * (end.x.toDouble() - start.x.toDouble()) / (endY - startY)
                if (pointX < intersectionX) odd = !odd
            }
        }
        return odd
    }

    private fun pointOnSegment(pointX: Double, pointY: Double, start: Point, end: Point): Boolean {
        val startX = start.x.toDouble()
        val startY = start.y.toDouble()
        val endX = end.x.toDouble()
        val endY = end.y.toDouble()
        val cross = (pointX - startX) * (endY - startY) -
            (pointY - startY) * (endX - startX)
        if (cross != 0.0) return false
        return pointX >= minOf(startX, endX) && pointX <= maxOf(startX, endX) &&
            pointY >= minOf(startY, endY) && pointY <= maxOf(startY, endY)
    }

    private fun edges(contour: Contour): Sequence<Pair<Point, Point>> =
        contour.points.indices.asSequence().map { index ->
            contour.points[index] to contour.points[(index + 1) % contour.points.size]
        }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
    }
}
