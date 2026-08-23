package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.isOpaque
import org.graphiks.kanvas.types.redByte

/**
 * Independent RGBA8 reference for the two-stop, opaque, clamp gradient subset used by evidence scenes.
 *
 * This rasterizer deliberately owns its geometry and interpolation math; it does not share renderer
 * materials, shader packing, or WGSL implementation details.
 */
class GradientCpuOracle private constructor(
    private val drawBounds: Rect,
    stops: List<GradientStop>,
    private val rawTAt: (Float, Float) -> Float,
) : CpuOracle {
    private val startColor: Color
    private val endColor: Color

    init {
        require(drawBounds.left.isFinite() && drawBounds.top.isFinite() && drawBounds.right.isFinite() && drawBounds.bottom.isFinite()) {
            "draw bounds must be finite"
        }
        require(drawBounds.left <= drawBounds.right && drawBounds.top <= drawBounds.bottom) { "draw bounds must not be inverted" }
        require(stops.size == 2 && stops[0].position == 0f && stops[1].position == 1f) {
            "oracle supports exactly two stops at positions zero and one"
        }
        require(stops.all { it.color.isOpaque }) { "oracle requires opaque stops" }
        startColor = stops[0].color
        endColor = stops[1].color
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "target dimensions must be positive" }
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5f
            val py = y + 0.5f
            if (px !in drawBounds.left..<drawBounds.right || py !in drawBounds.top..<drawBounds.bottom) continue
            val offset = (y * width + x) * 4
            val t = rawTAt(px, py).coerceIn(0f, 1f)
            output[offset] = interpolate(startColor.redByte, endColor.redByte, t).toByte()
            output[offset + 1] = interpolate(startColor.greenByte, endColor.greenByte, t).toByte()
            output[offset + 2] = interpolate(startColor.blueByte, endColor.blueByte, t).toByte()
            output[offset + 3] = 255.toByte()
        }
        return output
    }

    private fun interpolate(start: Int, end: Int, t: Float): Int =
        (start + (end - start) * t).roundToInt().coerceIn(0, 255)

    companion object {
        fun linear(drawBounds: Rect, start: Point, end: Point, stops: List<GradientStop>): GradientCpuOracle {
            require(start.x.isFinite() && start.y.isFinite() && end.x.isFinite() && end.y.isFinite()) { "linear points must be finite" }
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy
            return GradientCpuOracle(drawBounds, stops) { x, y ->
                if (lengthSquared <= 0f) 0f else ((x - start.x) * dx + (y - start.y) * dy) / lengthSquared
            }
        }

        fun radial(drawBounds: Rect, center: Point, radius: Float, stops: List<GradientStop>): GradientCpuOracle {
            require(center.x.isFinite() && center.y.isFinite() && radius.isFinite()) { "radial geometry must be finite" }
            return GradientCpuOracle(drawBounds, stops) { x, y ->
                if (radius <= 0f) 0f else sqrt((x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)) / radius
            }
        }

        fun sweep(
            drawBounds: Rect,
            center: Point,
            startAngle: Float,
            endAngle: Float,
            stops: List<GradientStop>,
        ): GradientCpuOracle {
            require(center.x.isFinite() && center.y.isFinite() && startAngle.isFinite() && endAngle.isFinite()) { "sweep geometry must be finite" }
            val sweep = endAngle - startAngle
            return GradientCpuOracle(drawBounds, stops) { x, y ->
                if (sweep <= 0f) 0f else {
                    val normalizedAngle = (atan2(-(y - center.y), x - center.x) / (2.0 * PI)).let { if (it < 0.0) it + 1.0 else it }
                    ((normalizedAngle - startAngle / 360.0) * 360.0 / sweep).toFloat()
                }
            }
        }
    }
}
