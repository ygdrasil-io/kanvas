package org.graphiks.kanvas.svg

import java.io.File
import java.io.InputStream
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

class SvgParser {
    private val factory: XMLInputFactory = XMLInputFactory.newInstance()

    fun parse(svg: String): Svg {
        return try {
            val reader = factory.createXMLStreamReader(StringReader(svg))
            parseSvg(reader)
        } catch (e: Exception) {
            System.err.println("Failed to parse SVG: ${e.message}")
            Svg()
        }
    }

    fun parse(file: File): Svg {
        return try {
            val reader = factory.createXMLStreamReader(file.inputStream())
            parseSvg(reader)
        } catch (e: Exception) {
            System.err.println("Failed to parse SVG file ${file.name}: ${e.message}")
            Svg()
        }
    }

    fun parse(input: InputStream): Svg {
        return try {
            val reader = factory.createXMLStreamReader(input)
            parseSvg(reader)
        } catch (e: Exception) {
            System.err.println("Failed to parse SVG input stream: ${e.message}")
            Svg()
        }
    }

    private fun parseSvg(reader: XMLStreamReader): Svg {
        var width: String? = null
        var height: String? = null
        var viewBox: String? = null
        var xmlns: String? = null
        
        val rects = mutableListOf<SvgRect>()
        val paths = mutableListOf<SvgPath>()
        val circles = mutableListOf<SvgCircle>()
        val ellipses = mutableListOf<SvgEllipse>()
        val lines = mutableListOf<SvgLine>()
        val polygons = mutableListOf<SvgPolygon>()
        val polylines = mutableListOf<SvgPolyline>()
        val groups = mutableListOf<SvgGroup>()
        val defs = mutableListOf<SvgDefs>()

        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "svg" -> {
                            width = reader.getAttributeValue(null, "width")
                            height = reader.getAttributeValue(null, "height")
                            viewBox = reader.getAttributeValue(null, "viewBox")
                            xmlns = reader.getAttributeValue(null, "xmlns")
                        }
                        "rect" -> rects.add(parseRect(reader))
                        "path" -> paths.add(parsePath(reader))
                        "circle" -> circles.add(parseCircle(reader))
                        "ellipse" -> ellipses.add(parseEllipse(reader))
                        "line" -> lines.add(parseLine(reader))
                        "polygon" -> polygons.add(parsePolygon(reader))
                        "polyline" -> polylines.add(parsePolyline(reader))
                        "g" -> groups.add(parseGroup(reader))
                        "defs" -> defs.add(parseDefs(reader))
                    }
                }
            }
        }

        return Svg(
            rects = rects,
            paths = paths,
            circles = circles,
            ellipses = ellipses,
            lines = lines,
            polygons = polygons,
            polylines = polylines,
            groups = groups,
            defs = defs,
            width = width,
            height = height,
            viewBox = viewBox,
            xmlns = xmlns
        )
    }

    private fun parseRect(reader: XMLStreamReader): SvgRect {
        val x = reader.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
        val y = reader.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
        val width = reader.getAttributeValue(null, "width")?.toFloatOrNull() ?: 0f
        val height = reader.getAttributeValue(null, "height")?.toFloatOrNull() ?: 0f
        val rx = reader.getAttributeValue(null, "rx")?.toFloatOrNull()
        val ry = reader.getAttributeValue(null, "ry")?.toFloatOrNull()
        val fill = reader.getAttributeValue(null, "fill")
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgRect(
            x = x, y = y, width = width, height = height,
            rx = rx, ry = ry, fill = fill, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            fillOpacity = fillOpacity, transform = transform, opacity = opacity
        )
    }

    private fun parsePath(reader: XMLStreamReader): SvgPath {
        val d = reader.getAttributeValue(null, "d") ?: ""
        val fill = reader.getAttributeValue(null, "fill")
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgPath(
            d = d, fill = fill, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            fillOpacity = fillOpacity, transform = transform, opacity = opacity
        )
    }

    private fun parseCircle(reader: XMLStreamReader): SvgCircle {
        val cx = reader.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
        val cy = reader.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
        val r = reader.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0f
        val fill = reader.getAttributeValue(null, "fill")
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgCircle(
            cx = cx, cy = cy, r = r, fill = fill, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            fillOpacity = fillOpacity, transform = transform, opacity = opacity
        )
    }

    private fun parseEllipse(reader: XMLStreamReader): SvgEllipse {
        val cx = reader.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
        val cy = reader.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
        val rx = reader.getAttributeValue(null, "rx")?.toFloatOrNull() ?: 0f
        val ry = reader.getAttributeValue(null, "ry")?.toFloatOrNull() ?: 0f
        val fill = reader.getAttributeValue(null, "fill")
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgEllipse(
            cx = cx, cy = cy, rx = rx, ry = ry, fill = fill, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            fillOpacity = fillOpacity, transform = transform, opacity = opacity
        )
    }

    private fun parseLine(reader: XMLStreamReader): SvgLine {
        val x1 = reader.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
        val y1 = reader.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
        val x2 = reader.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 0f
        val y2 = reader.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgLine(
            x1 = x1, y1 = y1, x2 = x2, y2 = y2, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            transform = transform, opacity = opacity
        )
    }

    private fun parsePolygon(reader: XMLStreamReader): SvgPolygon {
        val points = reader.getAttributeValue(null, "points") ?: ""
        val fill = reader.getAttributeValue(null, "fill")
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgPolygon(
            points = points, fill = fill, stroke = stroke,
            strokeWidth = strokeWidth, strokeOpacity = strokeOpacity,
            fillOpacity = fillOpacity, transform = transform, opacity = opacity
        )
    }

    private fun parsePolyline(reader: XMLStreamReader): SvgPolyline {
        val points = reader.getAttributeValue(null, "points") ?: ""
        val stroke = reader.getAttributeValue(null, "stroke")
        val strokeWidth = reader.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
        val strokeOpacity = reader.getAttributeValue(null, "stroke-opacity")?.toFloatOrNull()
        val fill = reader.getAttributeValue(null, "fill")
        val fillOpacity = reader.getAttributeValue(null, "fill-opacity")?.toFloatOrNull()
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgPolyline(
            points = points, stroke = stroke, strokeWidth = strokeWidth,
            strokeOpacity = strokeOpacity, fill = fill, fillOpacity = fillOpacity,
            transform = transform, opacity = opacity
        )
    }

    private fun parseGroup(reader: XMLStreamReader): SvgGroup {
        val transform = reader.getAttributeValue(null, "transform")
        val opacity = reader.getAttributeValue(null, "opacity")?.toFloatOrNull()

        val rects = mutableListOf<SvgRect>()
        val paths = mutableListOf<SvgPath>()
        val circles = mutableListOf<SvgCircle>()
        val ellipses = mutableListOf<SvgEllipse>()
        val lines = mutableListOf<SvgLine>()
        val polygons = mutableListOf<SvgPolygon>()
        val polylines = mutableListOf<SvgPolyline>()
        val groups = mutableListOf<SvgGroup>()

        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "rect" -> rects.add(parseRect(reader))
                        "path" -> paths.add(parsePath(reader))
                        "circle" -> circles.add(parseCircle(reader))
                        "ellipse" -> ellipses.add(parseEllipse(reader))
                        "line" -> lines.add(parseLine(reader))
                        "polygon" -> polygons.add(parsePolygon(reader))
                        "polyline" -> polylines.add(parsePolyline(reader))
                        "g" -> groups.add(parseGroup(reader))
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "g") break
                }
            }
        }

        return SvgGroup(
            transform = transform, opacity = opacity,
            rects = rects, paths = paths, circles = circles,
            ellipses = ellipses, lines = lines, polygons = polygons,
            polylines = polylines, groups = groups
        )
    }

    private fun parseDefs(reader: XMLStreamReader): SvgDefs {
        val gradients = mutableListOf<SvgGradient>()

        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "linearGradient" -> gradients.add(parseLinearGradient(reader))
                        "radialGradient" -> gradients.add(parseRadialGradient(reader))
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "defs") break
                }
            }
        }

        return SvgDefs(gradients = gradients)
    }

    private fun parseLinearGradient(reader: XMLStreamReader): SvgGradient.LinearGradient {
        val id = reader.getAttributeValue(null, "id") ?: ""
        val x1 = reader.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
        val y1 = reader.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
        val x2 = reader.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 1f
        val y2 = reader.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
        val gradientUnits = reader.getAttributeValue(null, "gradientUnits")
        val gradientTransform = reader.getAttributeValue(null, "gradientTransform")
        val spread = reader.getAttributeValue(null, "spread")

        val stops = mutableListOf<SvgStop>()
        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName == "stop") {
                        stops.add(parseStop(reader))
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "linearGradient") break
                }
            }
        }

        return SvgGradient.LinearGradient(
            id = id, x1 = x1, y1 = y1, x2 = x2, y2 = y2,
            gradientUnits = gradientUnits, gradientTransform = gradientTransform,
            spread = spread, stops = stops
        )
    }

    private fun parseRadialGradient(reader: XMLStreamReader): SvgGradient.RadialGradient {
        val id = reader.getAttributeValue(null, "id") ?: ""
        val cx = reader.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0.5f
        val cy = reader.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0.5f
        val r = reader.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0.5f
        val fx = reader.getAttributeValue(null, "fx")?.toFloatOrNull()
        val fy = reader.getAttributeValue(null, "fy")?.toFloatOrNull()
        val gradientUnits = reader.getAttributeValue(null, "gradientUnits")
        val gradientTransform = reader.getAttributeValue(null, "gradientTransform")
        val spread = reader.getAttributeValue(null, "spread")

        val stops = mutableListOf<SvgStop>()
        while (reader.hasNext()) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName == "stop") {
                        stops.add(parseStop(reader))
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "radialGradient") break
                }
            }
        }

        return SvgGradient.RadialGradient(
            id = id, cx = cx, cy = cy, r = r, fx = fx, fy = fy,
            gradientUnits = gradientUnits, gradientTransform = gradientTransform,
            spread = spread, stops = stops
        )
    }

    private fun parseStop(reader: XMLStreamReader): SvgStop {
        val offset = reader.getAttributeValue(null, "offset")?.toFloatOrNull()
        val stopColor = reader.getAttributeValue(null, "stop-color") ?: "#000000"
        val stopOpacity = reader.getAttributeValue(null, "stop-opacity")?.toFloatOrNull()

        skipElement(reader)
        
        return SvgStop(offset = offset, stopColor = stopColor, stopOpacity = stopOpacity)
    }

    private fun skipElement(reader: XMLStreamReader) {
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            val event = reader.next()
            when (event) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }
}
