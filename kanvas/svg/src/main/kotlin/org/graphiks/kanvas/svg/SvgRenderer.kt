package org.graphiks.kanvas.svg

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.paint.Shader
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

    private data class SvgStyle(
        val fill: String? = null,
        val stroke: String? = null,
        val strokeWidth: Float? = null,
        val strokeOpacity: Float? = null,
        val fillOpacity: Float? = null,
    ) {
        fun merge(child: SvgStyle): SvgStyle = SvgStyle(
            fill = child.fill ?: this.fill,
            stroke = child.stroke ?: this.stroke,
            strokeWidth = child.strokeWidth ?: this.strokeWidth,
            strokeOpacity = child.strokeOpacity ?: this.strokeOpacity,
            fillOpacity = child.fillOpacity ?: this.fillOpacity,
        )
    }

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
        renderElements(svg, viewBoxTransform, 1f, SvgStyle())
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
        defs.forEach { def -> processGradients(def.gradients) }
    }

    private fun processGradients(gradients: List<SvgGradient>) {
        gradients.forEach { gradient ->
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

    private fun renderElements(svg: Svg, parentTransform: GPUTransformFacts, parentOpacity: Float, style: SvgStyle) {
        svg.rects.forEach { renderRect(it, parentTransform, parentOpacity, style) }
        svg.paths.forEach { renderPath(it, parentTransform, parentOpacity, style) }
        svg.circles.forEach { renderCircle(it, parentTransform, parentOpacity, style) }
        svg.ellipses.forEach { renderEllipse(it, parentTransform, parentOpacity, style) }
        svg.lines.forEach { renderLine(it, parentTransform, parentOpacity, style) }
        svg.polygons.forEach { renderPolygon(it, parentTransform, parentOpacity, style) }
        svg.polylines.forEach { renderPolyline(it, parentTransform, parentOpacity, style) }
        svg.groups.forEach { renderGroup(it, parentTransform, parentOpacity, style) }
    }

    private fun renderGroup(group: SvgGroup, parentTransform: GPUTransformFacts, parentOpacity: Float, style: SvgStyle) {
        val groupTransform = transformParser.parse(group.transform)
        val combinedTransform = combineTransforms(parentTransform, groupTransform)
        val groupOpacity = group.opacity ?: 1f
        val newOpacity = parentOpacity * groupOpacity

        processGradients(group.gradients)

        val groupStyle = style.merge(
            SvgStyle(
                fill = group.fill,
                stroke = group.stroke,
                strokeWidth = group.strokeWidth,
                strokeOpacity = group.strokeOpacity,
                fillOpacity = group.fillOpacity,
            )
        )

        group.rects.forEach { renderRect(it, combinedTransform, newOpacity, groupStyle) }
        group.paths.forEach { renderPath(it, combinedTransform, newOpacity, groupStyle) }
        group.circles.forEach { renderCircle(it, combinedTransform, newOpacity, groupStyle) }
        group.ellipses.forEach { renderEllipse(it, combinedTransform, newOpacity, groupStyle) }
        group.lines.forEach { renderLine(it, combinedTransform, newOpacity, groupStyle) }
        group.polygons.forEach { renderPolygon(it, combinedTransform, newOpacity, groupStyle) }
        group.polylines.forEach { renderPolyline(it, combinedTransform, newOpacity, groupStyle) }
        group.groups.forEach { renderGroup(it, combinedTransform, newOpacity, groupStyle) }
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

    private fun renderRect(rect: SvgRect, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(rect.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = rect.opacity ?: parentOpacity
        val fillOpacity = rect.fillOpacity ?: style.fillOpacity ?: 1f
        val strokeOpacity = rect.strokeOpacity ?: style.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val x = rect.x * sx + tx
        val y = rect.y * sy + ty
        val width = rect.width * sx
        val height = rect.height * sy

        val effectiveFill = rect.fill ?: style.fill
        if (effectiveFill != null) {
            val fillPaint = paintParser.parseFill(effectiveFill, fillOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(x, y, width, height)
            canvas.drawRect(kanvasRect, fillPaint)
        }

        val effectiveStroke = rect.stroke ?: style.stroke
        val effectiveStrokeWidth = rect.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            val kanvasRect = Rect.fromXYWH(x, y, width, height)
            canvas.drawRect(kanvasRect, strokePaint)
        }
    }

    private fun renderPath(path: SvgPath, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(path.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = path.opacity ?: parentOpacity
        val fillOpacity = path.fillOpacity ?: style.fillOpacity ?: 1f
        val strokeOpacity = path.strokeOpacity ?: style.strokeOpacity ?: 1f

        val kanvasPath = pathParser.parse(path.d)
        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val transformedPath = kanvasPath.transform(tx, ty, sx, sy)

        val effectiveFill = path.fill ?: style.fill
        if (effectiveFill != null) {
            val fillPaint = paintParser.parseFill(effectiveFill, fillOpacity * opacity)
            canvas.drawPath(transformedPath, fillPaint)
        }

        val effectiveStroke = path.stroke ?: style.stroke
        val effectiveStrokeWidth = path.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            canvas.drawPath(transformedPath, strokePaint)
        }
    }



    private fun renderCircle(circle: SvgCircle, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(circle.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = circle.opacity ?: parentOpacity
        val fillOpacity = circle.fillOpacity ?: style.fillOpacity ?: 1f
        val strokeOpacity = circle.strokeOpacity ?: style.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val cx = circle.cx * sx + tx
        val cy = circle.cy * sy + ty
        val r = circle.r * maxOf(sx, sy)

        val path = Path { }
        path.addCircle(cx, cy, r)

        val effectiveFill = circle.fill ?: style.fill
        if (effectiveFill != null) {
            val fillPaint = paintParser.parseFill(effectiveFill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        val effectiveStroke = circle.stroke ?: style.stroke
        val effectiveStrokeWidth = circle.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderEllipse(ellipse: SvgEllipse, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(ellipse.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = ellipse.opacity ?: parentOpacity
        val fillOpacity = ellipse.fillOpacity ?: style.fillOpacity ?: 1f
        val strokeOpacity = ellipse.strokeOpacity ?: style.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val cx = ellipse.cx * sx + tx
        val cy = ellipse.cy * sy + ty
        val rx = ellipse.rx * sx
        val ry = ellipse.ry * sy

        val path = Path { }
        path.addOval(Rect.fromXYWH(cx - rx, cy - ry, rx * 2, ry * 2))

        val effectiveFill = ellipse.fill ?: style.fill
        if (effectiveFill != null) {
            val fillPaint = paintParser.parseFill(effectiveFill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        val effectiveStroke = ellipse.stroke ?: style.stroke
        val effectiveStrokeWidth = ellipse.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderLine(line: SvgLine, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(line.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = line.opacity ?: parentOpacity
        val strokeOpacity = line.strokeOpacity ?: style.strokeOpacity ?: 1f

        val (tx, ty, sx, sy) = extractTransformComponents(combinedTransform)
        val x1 = line.x1 * sx + tx
        val y1 = line.y1 * sy + ty
        val x2 = line.x2 * sx + tx
        val y2 = line.y2 * sy + ty

        val effectiveStroke = line.stroke ?: style.stroke
        val effectiveStrokeWidth = line.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            val path = Path { }
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderPolygon(polygon: SvgPolygon, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(polygon.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = polygon.opacity ?: parentOpacity
        val fillOpacity = polygon.fillOpacity ?: style.fillOpacity ?: 1f
        val strokeOpacity = polygon.strokeOpacity ?: style.strokeOpacity ?: 1f

        val path = parsePoints(polygon.points, combinedTransform)
        path.close()

        val effectiveFill = polygon.fill ?: style.fill
        if (effectiveFill != null) {
            val fillPaint = paintParser.parseFill(effectiveFill, fillOpacity * opacity)
            canvas.drawPath(path, fillPaint)
        }

        val effectiveStroke = polygon.stroke ?: style.stroke
        val effectiveStrokeWidth = polygon.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun renderPolyline(polyline: SvgPolyline, parentTransform: GPUTransformFacts, parentOpacity: Float = 1f, style: SvgStyle = SvgStyle()) {
        val elementTransform = transformParser.parse(polyline.transform)
        val combinedTransform = combineTransforms(parentTransform, elementTransform)
        
        val opacity = polyline.opacity ?: parentOpacity
        val strokeOpacity = polyline.strokeOpacity ?: style.strokeOpacity ?: 1f

        val path = parsePoints(polyline.points, combinedTransform)

        val effectiveStroke = polyline.stroke ?: style.stroke
        val effectiveStrokeWidth = polyline.strokeWidth ?: style.strokeWidth
        if (effectiveStroke != null && effectiveStrokeWidth != null && effectiveStrokeWidth > 0) {
            val strokePaint = paintParser.parseStroke(effectiveStroke, effectiveStrokeWidth, strokeOpacity * opacity)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun parsePoints(points: String, transform: GPUTransformFacts = GPUTransformFacts.identity()): Path {
        val path = Path { }
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

private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
