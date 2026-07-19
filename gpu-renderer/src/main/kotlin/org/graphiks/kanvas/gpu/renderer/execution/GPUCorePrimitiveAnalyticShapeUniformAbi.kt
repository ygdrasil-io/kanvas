package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey

internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES = 80
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_CODE =
    "unsupported.native-core-primitive.analytic-shape-route-closed"
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_MESSAGE =
    "Analytic-shape uniform80 execution remains closed until sealed uniform80 authority and materialization land."

internal fun corePrimitiveAnalyticShapeClosedRouteDiagnostic(
    uniformLayout: GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout,
): Pair<String, String>? =
    if (uniformLayout == GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1) {
        CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_CODE to
            CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_MESSAGE
    } else {
        null
    }

/**
 * Versioned host representation of the analytic Rect/RRect shader ABI.
 *
 * All shape and paint values remain dynamic uniforms. Caller-owned lists are copied so later
 * mutation cannot alter a prepared upload. This closed sub-tier does not let the builder or
 * materializer construct the block yet; the later route-opening sub-tier must build it only from
 * sealed semantic and normalized-geometry authority.
 */
internal class GPUCorePrimitiveAnalyticShapeUniformBlock(
    private val targetWidth: Float,
    private val targetHeight: Float,
    private val antiAlias: Boolean,
    premultipliedRgba: List<Float>,
    deviceBounds: List<Float>,
    normalizedRadii: List<Float>,
) {
    private val premultipliedRgbaSnapshot = premultipliedRgba.toList()
    private val deviceBoundsSnapshot = deviceBounds.toList()
    private val normalizedRadiiSnapshot = normalizedRadii.toList()

    init {
        require(premultipliedRgbaSnapshot.size == 4) {
            "Analytic shape premultiplied RGBA must contain four components"
        }
        require(deviceBoundsSnapshot.size == 4) {
            "Analytic shape device bounds must contain left, top, right, bottom"
        }
        require(normalizedRadiiSnapshot.size == 8) {
            "Analytic shape radii must contain TL, TR, BR, BL pairs"
        }
        require(targetWidth.isFinite() && targetWidth > 0f && targetHeight.isFinite() && targetHeight > 0f) {
            "Analytic shape target dimensions must be finite and positive"
        }
        require(premultipliedRgbaSnapshot.all(Float::isFinite)) {
            "Analytic shape premultiplied RGBA must be finite"
        }
        require(premultipliedRgbaSnapshot.all { it in 0f..1f }) {
            "Analytic shape premultiplied RGBA must remain in [0, 1]"
        }
        val alpha = premultipliedRgbaSnapshot[3]
        require(premultipliedRgbaSnapshot.take(3).all { it <= alpha }) {
            "Analytic shape RGB must be premultiplied by alpha"
        }
        require(deviceBoundsSnapshot.all(Float::isFinite)) {
            "Analytic shape device bounds must be finite"
        }
        val width = deviceBoundsSnapshot[2] - deviceBoundsSnapshot[0]
        val height = deviceBoundsSnapshot[3] - deviceBoundsSnapshot[1]
        require(width.isFinite() && width > 0f && height.isFinite() && height > 0f) {
            "Analytic shape device bounds must have finite positive width and height"
        }
        require(normalizedRadiiSnapshot.all { it.isFinite() && it >= 0f }) {
            "Analytic shape normalized radii must be finite and non-negative"
        }
        require(normalizedRadiiSnapshot.chunked(2).all { (radiusX, radiusY) ->
                (radiusX == 0f) == (radiusY == 0f)
            }
        ) {
            "Analytic shape normalized corner radii must be either both zero or both positive"
        }
        require(normalizedRadiiSnapshot[0] + normalizedRadiiSnapshot[2] <= width) {
            "Analytic shape top horizontal radii must fit the normalized bounds"
        }
        require(normalizedRadiiSnapshot[6] + normalizedRadiiSnapshot[4] <= width) {
            "Analytic shape bottom horizontal radii must fit the normalized bounds"
        }
        require(normalizedRadiiSnapshot[1] + normalizedRadiiSnapshot[7] <= height) {
            "Analytic shape left vertical radii must fit the normalized bounds"
        }
        require(normalizedRadiiSnapshot[3] + normalizedRadiiSnapshot[5] <= height) {
            "Analytic shape right vertical radii must fit the normalized bounds"
        }
    }

    fun packedBytes(): ByteArray =
        ByteBuffer.allocate(CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(targetWidth)
                putFloat(targetHeight)
                putInt(if (antiAlias) 1 else 0)
                putInt(0)
                premultipliedRgbaSnapshot.forEach(::putFloat)
                deviceBoundsSnapshot.forEach(::putFloat)
                normalizedRadiiSnapshot.forEach(::putFloat)
            }
            .array()
}
