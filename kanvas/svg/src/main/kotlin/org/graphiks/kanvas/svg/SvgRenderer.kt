package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.KanvasPoint
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.PaintStyle
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.Shader
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts

class SvgRenderer(
    private val canvas: Canvas,
    private val targetWidth: Float = 800f,
    private val targetHeight: Float = 600f,
) {
    private val pathParser = SvgPathParser()
    private val paintParser = SvgPaintParser()
    private val gradientParser = SvgGradientParser()
    private val transformParser = SvgTransformParser()

    private val gradientMap = mutableMapOf<String, Shader>()
    private val viewBoxRegex = Regex("^(-?\\d+\\.?\\d*) (-?\\d+\\.?\\d*) (-?\\d+\\.?\\d*) (-?\\d+\\.?\\d*)$")
    private val lengthRegex = Regex("^(-?\\d+\\.?\\d*)(px|mm|cm|in|pt|pc|%)?$")

    private fun extractTransformComponents(transform: GPUTransformFacts): Quadruple<Float, Float, Float, Float> {
        val tx = transform.translateX
        val ty = transform.translateY
        val sx = transform.scaleX
        val sy = transform.scaleY
        return Quadruple(tx, ty, sx, sy)
    }

    fun render(svg: Svg) {
        processDefs(svg.defs)
        paintParser.setGradientMap(gradientMap)
        
        val viewBoxTransform = calculateViewBoxTransform(svg)
        renderElements(svg, viewBoxTransform, 1f)
    }

    private fun parseLength(value: String): Float? {
        val match = lengthRegex.matchEntire(value.trim())
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun calculateViewBoxTransform(svg: Svg): GPUTransformFacts {
        val viewBox = svg.viewBox
        val svgWidth = svg.width?.let { parseLength(it) } ?: 0f
        val svgHeight = svg.height?.let { parseLength(it) } ?: 0f

        val sourceW: Float
        val sourceH: Float
        val minX: Float
        val minY: Float

        if (viewBox != null) {
            val match = viewBoxRegex.matchEntire(viewBox)
            if (match == null) return GPUTransformFacts.identity()
            val gv = match.groupValues
            if (gv.size < 5) return GPUTransformFacts.identity()
            val mx = gv[1].toFloatOrNull() ?: 0f
            val my = gv[2].toFloatOrNull() ?: 0f
            val vw = gv[3].toFloatOrNull() ?: 0f
            val vh = gv[4].toFloatOrNull() ?: 0f
            if (vw <= 0 || vh <= 0) return GPUTransformFacts.identity()
            minX = mx; minY = my
            sourceW = vw; sourceH = vh
        } else if (svgWidth > 0 && svgHeight > 0) {
            minX = 0f; minY = 0f
            sourceW = svgWidth; sourceH = svgHeight
        } else {
            return GPUTransformFacts.identity()
        }

        val scaleX = targetWidth / sourceW
        val scaleY = targetHeight / sourceH
        val translateX = -minX * scaleX
        val translateY = -minY * scaleY

        return GPUTransformFacts.affine(
            scaleX = scaleX,
            skewX = 0f,
            skewY = 0f,
            scaleY = scaleY,
            translateX = translateX,
            translateY = translateY,
        )
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

    private fun renderElements(svg: Svg, parentTransform: GPUTransformFacts, parentOpacity: Float) {
        svg.rects.forEach { renderRect(it, parentTransform, parentOpacity) }
        svg.paths.forEach { renderPath(it, parentTransform, parentOpacity) }
        svg.circles.forEach { renderCircle(it, parentTransform, parentOpacity) }
        svg.ellipses.forEach { renderEllipse(it, parentTransform, parentOpacity) }
        svg.lines.forEach { renderLine(it, parentTransform, parentOpacity) }
        svg.polygons.forEach { renderPolygon(it, parentTransform, parentOpacity) }
        svg.polylines.forEach { renderPolyline(it, parentTransform, parentOpacity) }
        svg.groups.forEach { renderGroup(it, parentTransform, parentOpacity) }
    }

    private fun renderGroup(group: SvgGroup, parentTransform: GPUTransformFacts, parentOpacity: Float) {
        val groupTransform = transformParser.parse(group.transform)
        val combinedTransform = combineTransforms(parentTransform, groupTransform)
        val groupOpacity = group.opacity ?: 1f
        val newOpacity = parentOpacity * groupOpacity

        group.rects.forEach { renderRect(it, combinedTransform, newOpacity) }
        group.paths.forEach { renderPath(it, combinedTransform, newOpacity) }
        group.circles.forEach { renderCircle(it, combinedTransform, newOpacity) }
        group.ellipses.forEach { renderEllipse(it, combinedTransform, newOpacity) }
        group.lines.forEach { renderLine(it, combinedTransform, newOpacity) }
        group.polygons.forEach { renderPolygon(it, combinedTransform, newOpacity) }
        group.polylines.forEach { renderPolyline(it, combinedTransform, newOpacity) }
        group.groups.forEach { renderGroup(it, combinedTransform, newOpacity) }
    }

    private fun combineTransforms(parent: GPUTransformFacts, child: GPUTransformFacts): GPUTransformFacts {
        val tx = parent.translateX + child.translateX
        val ty = parent.translateY + child.translateY
        val sx = parent.scaleX * child.scaleX
        val sy = parent.scaleY * child.scaleY
        return GPUTransformFacts.affine(
            scaleX = sx,
            skewX = 0f,
            skewY = 0f,
            scaleY = sy,
            translateX = tx,
            translateY = ty,
        )
    }

    private fun renderRect(rect: SvgRect, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(rect.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = rect.opacity ?: parentOpacity
        val fillOpacity = rect.fillOpacity ?: 1f
        val strokeOpacity = rect.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val x = rect.x * sx + tx
        val y = rect.y * sy + ty
        val width = rect.width * sx
        val height = rect.height * sy

        if (rect.fill != null) {
            val fillPaint = paintParser.parseFill(rect.fill, fillOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(x, y, width, height)
            canvas.drawRect(kanvasRect, fillPaint)
        }

        if (rect.stroke != null && rect.strokeWidth != null && rect.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(rect.stroke, rect.strokeWidth, strokeOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(x, y, width, height)
            canvas.drawRect(kanvasRect, strokePaint)
        }
    }

    private fun renderPath(path: SvgPath, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(path.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = path.opacity ?: parentOpacity
        val fillOpacity = path.fillOpacity ?: 1f
        val strokeOpacity = path.strokeOpacity ?: 1f

        val kanvasPath = pathParser.parse(path.d)
        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val transformedPath = kanvasPath.transform(tx, ty, sx, sy)

        if (path.fill != null) {
            val fillPaint = paintParser.parseFill(path.fill, fillOpacity * opacity)
            canvas.drawPath(transformedPath, fillPaint)
        }

        if (path.stroke != null && path.strokeWidth != null && path.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(path.stroke, path.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(transformedPath, strokePaint)
        }
    }



    private fun renderCircle(circle: SvgCircle, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(circle.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = circle.opacity ?: parentOpacity
        val fillOpacity = circle.fillOpacity ?: 1f
        val strokeOpacity = circle.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val cx = circle.cx * sx + tx
        val cy = circle.cy * sy + ty
        val r = circle.r * maxOf(sx, sy)

        val path = Path()
        path.addCircle(cx, cy, r)

        if (circle.fill != null) {
            val fillPaint = paintParser.parseFill(circle.fill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        if (circle.stroke != null && circle.strokeWidth != null && circle.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(circle.stroke, circle.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderEllipse(ellipse: SvgEllipse, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(ellipse.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = ellipse.opacity ?: parentOpacity
        val fillOpacity = ellipse.fillOpacity ?: 1f
        val strokeOpacity = ellipse.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val cx = ellipse.cx * sx + tx
        val cy = ellipse.cy * sy + ty
        val rx = ellipse.rx * sx
        val ry = ellipse.ry * sy

        val path = Path()
        path.addOval(Rect.fromXYWH(cx - rx, cy - ry, rx * 2, ry * 2))

        if (ellipse.fill != null) {
            val fillPaint = paintParser.parseFill(ellipse.fill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        if (ellipse.stroke != null && ellipse.strokeWidth != null && ellipse.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(ellipse.stroke, ellipse.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderLine(line: SvgLine, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(line.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = line.opacity ?: parentOpacity
        val strokeOpacity = line.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val x1 = line.x1 * sx + tx
        val y1 = line.y1 * sy + ty
        val x2 = line.x2 * sx + tx
        val y2 = line.y2 * sy + ty

        if (line.stroke != null && line.strokeWidth != null && line.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(line.stroke, line.strokeWidth, strokeOpacity * opacity)
            val path = Path()
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderPolygon(polygon: SvgPolygon, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(polygon.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = polygon.opacity ?: parentOpacity
        val fillOpacity = polygon.fillOpacity ?: 1f
        val strokeOpacity = polygon.strokeOpacity ?: 1f

        val path = parsePoints(polygon.points, combinedTransform)
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

    private fun renderPolyline(polyline: SvgPolyline, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f) {
        val elementTransform = transformParser.parse(polyline.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = polyline.opacity ?: parentOpacity
        val strokeOpacity = polyline.strokeOpacity ?: 1f

        val path = parsePoints(polyline.points, combinedTransform)

        if (polyline.stroke != null && polyline.strokeWidth != null && polyline.strokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(polyline.stroke, polyline.strokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun parsePoints(points: String, transform: GPUTransformFacts = GPUTransformFacts.identity()): Path {
        val path = Path()
        val coords = points.split(Regex("[\\s,]+"))
            .filter { it.isNotEmpty() }
            .map { it.toFloatOrNull() ?: 0f }

        val (tx, ty, sx, sy) = extractTransformComponents(transform)

        if (coords.size >= 2) {
            val x0 = coords[0] * sx + tx
            val y0 = coords[1] * sy + ty
            path.moveTo(x0, y0)
            for (i in 2 until coords.size step 2) {
                if (i + 1 < coords.size) {
                    val x = coords[i] * sx + tx
                    val y = coords[i + 1] * sy + ty
                    path.lineTo(x, y)
                }
            }
        }
        return path
    }
}
