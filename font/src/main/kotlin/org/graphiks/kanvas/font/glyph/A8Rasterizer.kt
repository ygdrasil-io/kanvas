package org.graphiks.kanvas.font.glyph

import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class A8Bitmap(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A8Bitmap) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

fun A8Bitmap?.occupancySize(): Long {
    if (this == null) return 0L
    return width.toLong() * height.toLong()
}

class A8Rasterizer(
    private val flatness: Double = 0.25,
) {
    fun rasterize(glyph: ScaledGlyph): A8Bitmap? {
        if (glyph.commands.isEmpty()) return null

        val edges = flattenToEdges(glyph.commands)
        if (edges.isEmpty()) return null

        val minX = floor(edges.minOf { min(it.x0, it.x1) }).toInt()
        val maxX = ceil(edges.maxOf { max(it.x0, it.x1) }).toInt()
        val minY = floor(edges.minOf { min(it.y0, it.y1) }).toInt()
        val maxY = ceil(edges.maxOf { max(it.y0, it.y1) }).toInt()

        val width = maxX - minX
        val height = maxY - minY
        if (width <= 0 || height <= 0) return null

        val pixels = ByteArray(width * height)

        for (row in 0 until height) {
            val scanY = minY + row + 0.5
            val intersections = mutableListOf<Double>()

            for (edge in edges) {
                val yMin = min(edge.y0, edge.y1)
                val yMax = max(edge.y0, edge.y1)
                if (scanY < yMin || scanY > yMax) continue
                if (abs(edge.y1 - edge.y0) < 1e-9) continue

                val t = (scanY - edge.y0) / (edge.y1 - edge.y0)
                val x = edge.x0 + t * (edge.x1 - edge.x0)
                intersections.add(x)
            }

            intersections.sort()

            var i = 0
            while (i + 1 < intersections.size) {
                val startCol = max(0, ceil(intersections[i] - minX - 0.5).toInt())
                val endCol = min(width, ceil(intersections[i + 1] - minX - 0.5).toInt())
                for (c in startCol until endCol) {
                    pixels[row * width + c] = 255.toByte()
                }
                i += 2
            }
        }

        val nonZeroCount = pixels.count { it != 0.toByte() }
        if (nonZeroCount == 0) return null

        return A8Bitmap(width, height, pixels)
    }

    private fun flattenToEdges(commands: List<OutlineCommand>): List<Edge> {
        val edges = mutableListOf<Edge>()
        val contours = mutableListOf<MutableList<Point>>()
        var current = mutableListOf<Point>()

        for (cmd in commands) {
            when (cmd) {
                is OutlineCommand.MoveTo -> {
                    if (current.isNotEmpty()) {
                        contours.add(current)
                    }
                    current = mutableListOf<Point>()
                    current.add(Point(cmd.x, cmd.y))
                }
                is OutlineCommand.LineTo -> {
                    current.add(Point(cmd.x, cmd.y))
                }
                is OutlineCommand.QuadraticTo -> {
                    val p0 = current.last()
                    val ctrl = Point(cmd.controlX, cmd.controlY)
                    val p1 = Point(cmd.x, cmd.y)
                    flattenQuadratic(p0, ctrl, p1, current)
                }
                is OutlineCommand.CubicTo -> {
                    val p0 = current.last()
                    val c1 = Point(cmd.controlX1, cmd.controlY1)
                    val c2 = Point(cmd.controlX2, cmd.controlY2)
                    val p1 = Point(cmd.x, cmd.y)
                    flattenCubic(p0, c1, c2, p1, current)
                }
                OutlineCommand.Close -> {
                    if (current.size >= 2) {
                        val first = current.first()
                        val last = current.last()
                        if (first.x != last.x || first.y != last.y) {
                            current.add(first)
                        }
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            contours.add(current)
        }

        for (contour in contours) {
            for (i in 0 until contour.size - 1) {
                val a = contour[i]
                val b = contour[i + 1]
                if (abs(a.y - b.y) < 1e-9) continue
                edges.add(Edge(a.x, a.y, b.x, b.y))
            }
        }

        return edges
    }

    private fun flattenQuadratic(p0: Point, c: Point, p1: Point, output: MutableList<Point>) {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val dist = abs(dx) + abs(dy)
        if (dist <= flatness) {
            output.add(p1)
            return
        }
        val m01x = (p0.x + c.x) / 2.0
        val m01y = (p0.y + c.y) / 2.0
        val m12x = (c.x + p1.x) / 2.0
        val m12y = (c.y + p1.y) / 2.0
        val mx = (m01x + m12x) / 2.0
        val my = (m01y + m12y) / 2.0
        val midCtrl = Point(m01x, m01y)
        val midPt = Point(mx, my)

        val ctrlErr = abs(c.x - (p0.x + p1.x) / 2.0) + abs(c.y - (p0.y + p1.y) / 2.0)
        if (ctrlErr <= flatness) {
            output.add(p1)
            return
        }

        flattenQuadratic(p0, midCtrl, midPt, output)
        val endCtrl = Point(m12x, m12y)
        flattenQuadratic(midPt, endCtrl, p1, output)
    }

    private fun flattenCubic(p0: Point, c1: Point, c2: Point, p1: Point, output: MutableList<Point>) {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val dist = abs(dx) + abs(dy)
        if (dist <= flatness) {
            output.add(p1)
            return
        }
        val m01x = (p0.x + c1.x) / 2.0
        val m01y = (p0.y + c1.y) / 2.0
        val m12x = (c1.x + c2.x) / 2.0
        val m12y = (c1.y + c2.y) / 2.0
        val m23x = (c2.x + p1.x) / 2.0
        val m23y = (c2.y + p1.y) / 2.0
        val m012x = (m01x + m12x) / 2.0
        val m012y = (m01y + m12y) / 2.0
        val m123x = (m12x + m23x) / 2.0
        val m123y = (m12y + m23y) / 2.0
        val mx = (m012x + m123x) / 2.0
        val my = (m012y + m123y) / 2.0

        val ctrlErr1 = abs(c1.x - p0.x) + abs(c1.y - p0.y) + abs(c2.x - p1.x) + abs(c2.y - p1.y)
        if (ctrlErr1 <= flatness) {
            output.add(p1)
            return
        }

        flattenCubic(p0, Point(m01x, m01y), Point(m012x, m012y), Point(mx, my), output)
        flattenCubic(Point(mx, my), Point(m123x, m123y), Point(m23x, m23y), p1, output)
    }

    private data class Point(val x: Double, val y: Double)

    private data class Edge(
        val x0: Double,
        val y0: Double,
        val x1: Double,
        val y1: Double,
    )
}
