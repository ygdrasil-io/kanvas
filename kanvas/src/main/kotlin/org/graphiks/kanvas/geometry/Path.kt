package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.dsl.PathScope
import org.graphiks.math.geometry.MutableLine2F32
import org.graphiks.math.geometry.PathAnalysisF32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.transformedBy
import org.graphiks.math.vector.Vector2F32

/** Mutable compatibility facade over immutable renderer-neutral [PathF32] values. */
class Path internal constructor(
    internal var geometry: PathF32 = PathBuilder().build(),
) {
    var fillType: FillType
        get() = geometry.fillRule.toCompatibilityFillType()
        set(value) {
            geometry = PathBuilder(value.toFillRule()).addPath(geometry).build()
        }

    fun moveTo(x: Float, y: Float): Path = append { moveTo(x, y) }

    fun lineTo(x: Float, y: Float): Path = append { lineTo(x, y) }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path =
        append { quadTo(cx, cy, x, y) }

    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Path =
        append { cubicTo(cx1, cy1, cx2, cy2, x, y) }

    fun arcTo(
        rx: Float,
        ry: Float,
        xAxisRotation: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
    ): Path = append { arcTo(rx, ry, xAxisRotation, largeArc, sweep, x, y) }

    fun close(): Path = append { close() }

    fun addRect(rect: RectF32): Path = append { addRect(rect) }

    fun addOval(rect: RectF32): Path = append { addOval(rect) }

    fun addCircle(cx: Float, cy: Float, r: Float): Path =
        addOval(RectF32.ofLTRB(cx - r, cy - r, cx + r, cy + r))

    fun addRRect(rrect: RRectF32): Path = append { addRRect(rrect) }

    fun addPath(path: Path): Path = append { addPath(path.toPathF32()) }

    fun reverseAddPath(src: Path): Path {
        val source = src.commands()
        var contourStart = 0
        while (contourStart < source.size) {
            val move = source[contourStart] as? PathCommand.Move
            if (move == null) {
                contourStart++
                continue
            }
            var contourEnd = contourStart + 1
            while (contourEnd < source.size && source[contourEnd] !is PathCommand.Move) contourEnd++
            val contour = source.subList(contourStart, contourEnd)
            val hasClose = contour.lastOrNull() is PathCommand.Close
            val drawable = if (hasClose) contour.dropLast(1) else contour
            val lastEnd = drawable.lastOrNull()?.endpoint ?: move.point
            moveTo(lastEnd.x, lastEnd.y)
            for (index in drawable.lastIndex downTo 1) {
                val command = drawable[index]
                val start = drawable[index - 1].endpoint ?: move.point
                when (command) {
                    is PathCommand.Line -> lineTo(start.x, start.y)
                    is PathCommand.Quad -> quadTo(command.control.x, command.control.y, start.x, start.y)
                    is PathCommand.Cubic -> cubicTo(
                        command.control2.x,
                        command.control2.y,
                        command.control1.x,
                        command.control1.y,
                        start.x,
                        start.y,
                    )
                    is PathCommand.ArcTo -> arcTo(
                        command.radius.x,
                        command.radius.y,
                        command.xAxisRotation,
                        command.largeArc,
                        !command.sweep,
                        start.x,
                        start.y,
                    )
                    else -> Unit
                }
            }
            if (hasClose) close()
            contourStart = contourEnd
        }
        return this
    }

    fun isEmpty(): Boolean = geometry.segmentCount == 0

    fun computeBounds(): RectF32? = PathAnalysisF32.bounds(geometry)

    fun isRect(rect: RectF32? = null): Boolean = PathAnalysisF32.rect(geometry)?.let { value ->
        rect?.setLTRB(value.left, value.top, value.right, value.bottom)
        true
    } ?: false

    fun isOval(bounds: RectF32? = null): Boolean = PathAnalysisF32.oval(geometry)?.let { value ->
        bounds?.setLTRB(value.left, value.top, value.right, value.bottom)
        true
    } ?: false

    fun isRRect(rrect: RRectF32? = null): Boolean = PathAnalysisF32.rrect(geometry)?.let { value ->
        rrect?.rect?.setLTRB(value.rect.left, value.rect.top, value.rect.right, value.rect.bottom)
        true
    } ?: false

    fun isLine(line: MutableLine2F32? = null): Boolean = PathAnalysisF32.line(geometry)?.let { value ->
        line?.start = value.start
        line?.end = value.end
        true
    } ?: false

    fun isConvex(): Boolean = PathAnalysisF32.isConvex(geometry)

    fun isInterpolatable(other: Path): Boolean = PathAnalysisF32.isInterpolatable(geometry, other.geometry)

    fun contains(point: Point2F32): Boolean = PathAnalysisF32.contains(geometry, point)

    fun conservativelyContainsRect(rect: RectF32): Boolean =
        PathAnalysisF32.conservativelyContainsRect(geometry, rect)

    fun transform(tx: Float, ty: Float, sx: Float, sy: Float): Path =
        transform(Matrix3x3F32.translation(tx, ty) * Matrix3x3F32.scaling(sx, sy))

    fun transform(matrix: Matrix3x3F32): Path = geometry.transformedBy(matrix).toCompatibilityPath()

    internal fun commands(): List<PathCommand> = geometry.map { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> PathCommand.Move(segment.point)
            is PathSegmentF32.LineTo -> PathCommand.Line(segment.point)
            is PathSegmentF32.QuadTo -> PathCommand.Quad(segment.control, segment.point)
            is PathSegmentF32.CubicTo -> PathCommand.Cubic(segment.control1, segment.control2, segment.point)
            is PathSegmentF32.ArcTo -> PathCommand.ArcTo(
                segment.radius,
                segment.xAxisRotation,
                segment.largeArc,
                segment.sweep,
                segment.point,
            )
            PathSegmentF32.Close -> PathCommand.Close
        }
    }

    internal fun verbs(): List<PathVerb> = commands().map(PathCommand::verb)

    private fun append(action: PathBuilder.() -> Unit): Path {
        geometry = PathBuilder(geometry.fillRule).addPath(geometry).apply(action).build()
        return this
    }

    companion object {
        operator fun invoke(block: PathScope.() -> Unit): Path {
            val scope = PathScope()
            scope.block()
            return scope.build()
        }
    }
}

internal fun Path.toPathF32(): PathF32 = geometry

internal fun PathF32.toCompatibilityPath(): Path = Path(this)

enum class PathVerb { MOVE, LINE, QUAD, CUBIC, ARC_TO, CLOSE }

internal sealed interface PathCommand {
    val verb: PathVerb
    val endpoint: Point2F32?

    data class Move(val point: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.MOVE
        override val endpoint: Point2F32 = point
    }

    data class Line(override val endpoint: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.LINE
    }

    data class Quad(val control: Point2F32, override val endpoint: Point2F32) : PathCommand {
        override val verb: PathVerb = PathVerb.QUAD
    }

    data class Cubic(
        val control1: Point2F32,
        val control2: Point2F32,
        override val endpoint: Point2F32,
    ) : PathCommand {
        override val verb: PathVerb = PathVerb.CUBIC
    }

    data class ArcTo(
        val radius: Vector2F32,
        val xAxisRotation: Float,
        val largeArc: Boolean,
        val sweep: Boolean,
        override val endpoint: Point2F32,
    ) : PathCommand {
        override val verb: PathVerb = PathVerb.ARC_TO
    }

    data object Close : PathCommand {
        override val verb: PathVerb = PathVerb.CLOSE
        override val endpoint: Point2F32? = null
    }
}
