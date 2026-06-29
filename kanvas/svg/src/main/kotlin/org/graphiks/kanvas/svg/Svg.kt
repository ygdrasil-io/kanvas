package org.graphiks.kanvas.svg

sealed class SvgGradient {
    abstract val id: String
    abstract val stops: List<SvgStop>

    data class LinearGradient(
        override val id: String = "",
        val x1: Float = 0f,
        val y1: Float = 0f,
        val x2: Float = 1f,
        val y2: Float = 0f,
        val gradientUnits: String? = null,
        val gradientTransform: String? = null,
        val spread: String? = null,
        override val stops: List<SvgStop> = emptyList(),
    ) : SvgGradient()

    data class RadialGradient(
        override val id: String = "",
        val cx: Float = 0.5f,
        val cy: Float = 0.5f,
        val r: Float = 0.5f,
        val fx: Float? = null,
        val fy: Float? = null,
        val gradientUnits: String? = null,
        val gradientTransform: String? = null,
        val spread: String? = null,
        override val stops: List<SvgStop> = emptyList(),
    ) : SvgGradient()
}

data class SvgStop(
    val offset: Float? = null,
    val stopColor: String = "#000000",
    val stopOpacity: Float? = null,
)

data class Svg(
    val rects: List<SvgRect> = emptyList(),
    val paths: List<SvgPath> = emptyList(),
    val circles: List<SvgCircle> = emptyList(),
    val ellipses: List<SvgEllipse> = emptyList(),
    val lines: List<SvgLine> = emptyList(),
    val polygons: List<SvgPolygon> = emptyList(),
    val polylines: List<SvgPolyline> = emptyList(),
    val groups: List<SvgGroup> = emptyList(),
    val defs: List<SvgDefs> = emptyList(),
    val width: String? = null,
    val height: String? = null,
    val viewBox: String? = null,
    val xmlns: String? = "http://www.w3.org/2000/svg",
)

data class SvgRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val rx: Float? = null,
    val ry: Float? = null,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgPath(
    val d: String = "",
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgCircle(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val r: Float = 0f,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgEllipse(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgLine(
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgPolygon(
    val points: String = "",
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgPolyline(
    val points: String = "",
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeOpacity: Float? = null,
    val fill: String? = null,
    val fillOpacity: Float? = null,
    val transform: String? = null,
    val opacity: Float? = null,
)

data class SvgGroup(
    val transform: String? = null,
    val opacity: Float? = null,
    val rects: List<SvgRect> = emptyList(),
    val paths: List<SvgPath> = emptyList(),
    val circles: List<SvgCircle> = emptyList(),
    val ellipses: List<SvgEllipse> = emptyList(),
    val lines: List<SvgLine> = emptyList(),
    val polygons: List<SvgPolygon> = emptyList(),
    val polylines: List<SvgPolyline> = emptyList(),
    val groups: List<SvgGroup> = emptyList(),
)

data class SvgDefs(
    val gradients: List<SvgGradient> = emptyList(),
)
