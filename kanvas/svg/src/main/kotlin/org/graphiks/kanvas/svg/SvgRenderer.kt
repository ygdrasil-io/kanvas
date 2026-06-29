package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.KanvasPoint
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.PaintStyle
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.Shader
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts

class SvgRenderer(private val canvas: Canvas) {
    private val pathParser = SvgPathParser()
    private val paintParser = SvgPaintParser()
    private val gradientParser = SvgGradientParser()
    private val transformParser = SvgTransformParser()

    private val gradientMap = mutableMapOf<String, Shader>()

    fun render(svg: Svg) {
        processDefs(svg.defs)
        renderElements(svg, GPUTransformFacts.identity())
    }

    private fun processDefs(defs: List<SvgDefs>) {
        defs.forEach { def ->
            def.gradients.forEach { gradient ->
                when (gradient) {
                    is SvgGradient.LinearGradient -> {
                        gradientMap[gradient.id] = gradientParser.parseLinearGradient(
                            gradient.x1, gradient.y1,
                            gradient.x2, gradient.y2,
                            gradient.stops,
                        )
                    }
                    is SvgGradient.RadialGradient -> {
                        gradientMap[gradient.id] = gradientParser.parseRadialGradient(
                            gradient.cx, gradient.cy, gradient.r,
                            gradient.stops,
                        )
                    }
                }
            }
        }
    }

    private fun renderElements(svg: Svg, parentTransform: GPUTransformFacts) {
        svg.rects.forEach { renderRect(it, parentTransform) }
        svg.paths.forEach { renderPath(it, parentTransform) }
        svg.circles.forEach { renderCircle(it, parentTransform) }
        svg.ellipses.forEach { renderEllipse(it, parentTransform) }
        svg.lines.forEach { renderLine(it, parentTransform) }
        svg.polygons.forEach { renderPolygon(it, parentTransform) }
        svg.polylines.forEach { renderPolyline(it, parentTransform) }
        svg.groups.forEach { renderGroup(it, parentTransform) }
    }

    private fun renderGroup(group: SvgGroup, parentTransform: GPUTransformFacts) {
        val groupTransform = transformParser.parse(group.transform)
        val newOpacity = group.opacity ?: 1f

        group.rects.forEach { renderRect(it, groupTransform, newOpacity) }
        group.paths.forEach { renderPath(it, groupTransform, newOpacity) }
        group.circles.forEach { renderCircle(it, groupTransform, newOpacity) }
        group.ellipses.forEach { renderEllipse(it, groupTransform, newOpacity) }
        group.lines.forEach { renderLine(it, groupTransform, newOpacity) }
        group.polygons.forEach { renderPolygon(it, groupTransform, newOpacity) }
        group.polylines.forEach { renderPolyline(it, groupTransform, newOpacity) }
        group.groups.forEach { renderGroup(it, groupTransform) }
    }

    private fun renderRect(rect: SvgRect, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = rect.opacity ?: parentOpacity
        val fillOpacity = rect.fillOpacity ?: 1f
        val strokeOpacity = rect.strokeOpacity ?: 1f

        if (rect.fill != null) {
            val fillPaint = paintParser.parseFill(rect.fill, fillOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(rect.x, rect.y, rect.width, rect.height)
            canvas.drawRect(kanvasRect, fillPaint)
        }

        if (rect.stroke != null && rect.strokeWidth != null && rect.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(rect.stroke, rect.strokeWidth, strokeOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(rect.x, rect.y, rect.width, rect.height)
            canvas.drawRect(kanvasRect, strokePaint)
        }
    }

    private fun renderPath(path: SvgPath, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = path.opacity ?: parentOpacity
        val fillOpacity = path.fillOpacity ?: 1f
        val strokeOpacity = path.strokeOpacity ?: 1f

        val kanvasPath = pathParser.parse(path.d)

        if (path.fill != null) {
            val fillPaint = paintParser.parseFill(path.fill, fillOpacity * opacity)
            canvas.drawPath(kanvasPath, fillPaint)
        }

        if (path.stroke != null && path.strokeWidth != null && path.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(path.stroke, path.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(kanvasPath, strokePaint)
        }
    }

    private fun renderCircle(circle: SvgCircle, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = circle.opacity ?: parentOpacity
        val fillOpacity = circle.fillOpacity ?: 1f
        val strokeOpacity = circle.strokeOpacity ?: 1f

        val path = Path()
        path.addCircle(circle.cx, circle.cy, circle.r)

        if (circle.fill != null) {
            val fillPaint = paintParser.parseFill(circle.fill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        if (circle.stroke != null && circle.strokeWidth != null && circle.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(circle.stroke, circle.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderEllipse(ellipse: SvgEllipse, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = ellipse.opacity ?: parentOpacity
        val fillOpacity = ellipse.fillOpacity ?: 1f
        val strokeOpacity = ellipse.strokeOpacity ?: 1f

        val path = Path()
        path.addOval(Rect.fromXYWH(ellipse.cx - ellipse.rx, ellipse.cy - ellipse.ry, ellipse.rx * 2, ellipse.ry * 2))

        if (ellipse.fill != null) {
            val fillPaint = paintParser.parseFill(ellipse.fill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        if (ellipse.stroke != null && ellipse.strokeWidth != null && ellipse.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(ellipse.stroke, ellipse.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderLine(line: SvgLine, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = line.opacity ?: parentOpacity
        val strokeOpacity = line.strokeOpacity ?: 1f

        if (line.stroke != null && line.strokeWidth != null && line.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(line.stroke, line.strokeWidth, strokeOpacity * opacity)
            val path = Path()
            path.moveTo(line.x1, line.y1)
            path.lineTo(line.x2, line.y2)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderPolygon(polygon: SvgPolygon, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = polygon.opacity ?: parentOpacity
        val fillOpacity = polygon.fillOpacity ?: 1f
        val strokeOpacity = polygon.strokeOpacity ?: 1f

        val path = parsePoints(polygon.points)
        path.close()

        if (polygon.fill != null) {
            val fillPaint = paintParser.parseFill(polygon.fill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        if (polygon.stroke != null && polygon.strokeWidth != null && polygon.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(polygon.stroke, polygon.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderPolyline(polyline: SvgPolyline, transform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val opacity = polyline.opacity ?: parentOpacity
        val strokeOpacity = polyline.strokeOpacity ?: 1f

        val path = parsePoints(polyline.points)

        if (polyline.stroke != null && polyline.strokeWidth != null && polyline.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(polyline.stroke, polyline.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun parsePoints(points: String): Path {
        val path = Path()
        val coords = points.split(Regex("[\\s,]+"))
            .filter { it.isNotEmpty() }
            .map { it.toFloatOrNull() ?: 0f }

        if (coords.size >= 2) {
            path.moveTo(coords[0], coords[1])
            for (i in 2 until coords.size step 2) {
                if (i + 1 < coords.size) {
                    path.lineTo(coords[i], coords[i + 1])
                }
            }
        }
        return path
    }
}
