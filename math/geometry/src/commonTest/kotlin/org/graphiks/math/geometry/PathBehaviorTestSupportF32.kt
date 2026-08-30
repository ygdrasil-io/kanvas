package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.test.assertEquals

internal data class AffineTransformF32(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
)

internal data class PathOpCaseF32(
    val name: String,
    val first: PathF32,
    val second: PathF32,
    val probes: List<Point2F32>,
)

internal fun transformPointF32(point: Point2F32, transform: AffineTransformF32): Point2F32 = Point2F32(
    x = point.x * transform.scale + transform.translateX,
    y = point.y * transform.scale + transform.translateY,
)

internal fun transformPathF32(path: PathF32, transform: AffineTransformF32): PathF32 {
    val builder = PathBuilder(path.fillRule)
    val radiusScale = abs(transform.scale)
    path.forEach { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> builder.moveTo(
                transformPointF32(segment.point, transform).x,
                transformPointF32(segment.point, transform).y,
            )
            is PathSegmentF32.LineTo -> builder.lineTo(
                transformPointF32(segment.point, transform).x,
                transformPointF32(segment.point, transform).y,
            )
            is PathSegmentF32.QuadTo -> {
                val control = transformPointF32(segment.control, transform)
                val point = transformPointF32(segment.point, transform)
                builder.quadTo(control.x, control.y, point.x, point.y)
            }
            is PathSegmentF32.CubicTo -> {
                val control1 = transformPointF32(segment.control1, transform)
                val control2 = transformPointF32(segment.control2, transform)
                val point = transformPointF32(segment.point, transform)
                builder.cubicTo(control1.x, control1.y, control2.x, control2.y, point.x, point.y)
            }
            is PathSegmentF32.ArcTo -> {
                val point = transformPointF32(segment.point, transform)
                builder.arcTo(
                    radiusX = segment.radius.x * radiusScale,
                    radiusY = segment.radius.y * radiusScale,
                    xAxisRotation = segment.xAxisRotation,
                    largeArc = segment.largeArc,
                    sweep = segment.sweep,
                    x = point.x,
                    y = point.y,
                )
            }
            PathSegmentF32.Close -> builder.close()
        }
    }
    return builder.build()
}

internal fun probeGridF32(bounds: RectF32, steps: Int): List<Point2F32> {
    require(steps > 0)
    val width = bounds.right - bounds.left
    val height = bounds.bottom - bounds.top
    return buildList(steps * steps) {
        repeat(steps) { row ->
            repeat(steps) { column ->
                add(
                    Point2F32(
                        x = bounds.left + width * ((column + 0.5f) / steps),
                        y = bounds.top + height * ((row + 0.5f) / steps),
                    ),
                )
            }
        }
    }
}

internal fun expectedMembership(operation: PathBooleanOp, inFirst: Boolean, inSecond: Boolean): Boolean = when (operation) {
    PathBooleanOp.DIFFERENCE -> inFirst && !inSecond
    PathBooleanOp.INTERSECT -> inFirst && inSecond
    PathBooleanOp.UNION -> inFirst || inSecond
    PathBooleanOp.XOR -> inFirst != inSecond
    PathBooleanOp.REVERSE_DIFFERENCE -> inSecond && !inFirst
}

internal fun pathOpCasesF32(): List<PathOpCaseF32> = listOf(
    pathOpCaseF32(
        name = "tangent ovals",
        first = PathBuilder().addOval(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build(),
        second = PathBuilder().addOval(RectF32.ofLTRB(10f, 0f, 20f, 10f)).build(),
        // The explicit probes below cover every semantic region. A compact grid avoids spending
        // most of this metamorphic regression rescanning 8,192 flattened output edges at
        // redundant points while still sampling both interiors and the exterior.
        gridSteps = 4,
        interiorProbes = listOf(
            Point2F32(-1f, -1f),
            Point2F32(3f, 5f),
            Point2F32(17f, 5f),
            // This is the shared tangent boundary. PathAnalysisF32 classifies boundaries outside;
            // the metamorphic assertion therefore checks that classification remains stable.
            Point2F32(10f, 5f),
            Point2F32(10f, 4f),
        ),
    ),
    pathOpCaseF32(
        name = "collinear rectangles",
        first = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build(),
        second = PathBuilder().addRect(RectF32.ofLTRB(5f, 0f, 15f, 10f)).build(),
        interiorProbes = listOf(
            Point2F32(-1f, -1f),
            Point2F32(2f, 5f),
            Point2F32(7f, 5f),
            Point2F32(12f, 5f),
        ),
    ),
    pathOpCaseF32(
        name = "overlapping oblique triangles",
        first = PathBuilder().moveTo(0f, 0f).lineTo(12f, 2f).lineTo(4f, 14f).close().build(),
        second = PathBuilder().moveTo(4f, -2f).lineTo(16f, 6f).lineTo(6f, 16f).close().build(),
        gridSteps = 11,
        interiorProbes = listOf(
            Point2F32(-1f, -1f),
            Point2F32(2f, 2f),
            Point2F32(7f, 5f),
            Point2F32(13f, 6f),
        ),
    ),
    pathOpCaseF32(
        name = "nested donut",
        first = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
            .addRect(RectF32.ofLTRB(6f, 6f, 14f, 14f))
            .build(),
        second = PathBuilder().addRect(RectF32.ofLTRB(8f, 8f, 16f, 16f)).build(),
        interiorProbes = listOf(
            Point2F32(-1f, -1f),
            Point2F32(2f, 2f),
            Point2F32(10f, 10f),
            Point2F32(15f, 10f),
        ),
    ),
    pathOpCaseF32(
        name = "self intersecting bow ties",
        first = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(0f, 0f).lineTo(12f, 12f).lineTo(0f, 12f).lineTo(12f, 0f).close()
            .build(),
        second = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(4f, -2f).lineTo(16f, 10f).lineTo(4f, 10f).lineTo(16f, -2f).close()
            .build(),
        interiorProbes = listOf(
            Point2F32(-1f, -1f),
            Point2F32(4f, 9f),
            Point2F32(7f, 8f),
            Point2F32(12f, 8f),
        ),
    ),
)

internal fun assertMembershipEquivalentF32(expected: PathF32, actual: PathF32, probes: List<Point2F32>) {
    probes.forEach { point ->
        assertEquals(
            PathAnalysisF32.contains(expected, point),
            PathAnalysisF32.contains(actual, point),
            "membership differs at $point",
        )
    }
}

internal fun assertMetamorphicMembershipF32(
    case: PathOpCaseF32,
    operation: PathBooleanOp,
    transforms: List<AffineTransformF32>,
) {
    val base = PathOpsF32.op(case.first, case.second, operation)
    val baseMembership = case.probes.map { point -> PathAnalysisF32.contains(base, point) }
    if (case.name == "tangent ovals") {
        val tangentIndex = case.probes.indexOf(Point2F32(10f, 5f))
        check(tangentIndex >= 0)
        assertEquals(false, baseMembership[tangentIndex], "tangent boundary must remain outside")
    }
    transforms.forEach { transform ->
        val transformed = PathOpsF32.op(
            transformPathF32(case.first, transform),
            transformPathF32(case.second, transform),
            operation,
        )
        case.probes.forEachIndexed { index, point ->
            assertEquals(
                baseMembership[index],
                PathAnalysisF32.contains(transformed, transformPointF32(point, transform)),
                "${case.name} $operation at $point with $transform",
            )
        }
    }
}

private fun pathOpCaseF32(
    name: String,
    first: PathF32,
    second: PathF32,
    gridSteps: Int = 12,
    interiorProbes: List<Point2F32>,
): PathOpCaseF32 {
    val firstBounds = checkNotNull(PathAnalysisF32.bounds(first))
    val secondBounds = checkNotNull(PathAnalysisF32.bounds(second))
    val bounds = RectF32.ofLTRB(
        minOf(firstBounds.left, secondBounds.left) - 2f,
        minOf(firstBounds.top, secondBounds.top) - 2f,
        maxOf(firstBounds.right, secondBounds.right) + 2f,
        maxOf(firstBounds.bottom, secondBounds.bottom) + 2f,
    )
    return PathOpCaseF32(
        name = name,
        first = first,
        second = second,
        probes = probeGridF32(bounds, steps = gridSteps) + interiorProbes,
    )
}
