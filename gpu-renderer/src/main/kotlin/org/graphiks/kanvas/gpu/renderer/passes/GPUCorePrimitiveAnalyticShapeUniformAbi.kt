package org.graphiks.kanvas.gpu.renderer.passes

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput

internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES = 80

/** Pure, handle-free result of sealing one prepared Rect/RRect into uniform80 bytes. */
internal sealed interface GPUCorePrimitiveAnalyticShapeUniformBuildResult {
    class Accepted internal constructor(
        val block: GPUCorePrimitiveAnalyticShapeUniformBlock,
    ) : GPUCorePrimitiveAnalyticShapeUniformBuildResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUCorePrimitiveAnalyticShapeUniformBuildResult
}

/**
 * Builds the analytic ABI only from one exact prepared semantic and its analysis-issued geometry.
 *
 * RRect radii are read from the opaque signed authority after proving that the semantic retains
 * that exact device geometry. This prevents later callers from re-normalizing or substituting raw
 * source radii in the execution path.
 */
internal fun buildCorePrimitiveAnalyticShapeUniform(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
): GPUCorePrimitiveAnalyticShapeUniformBuildResult {
    fun refused(code: String, message: String) =
        GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused(code, message)

    if (!semanticAuthority.matches(semantic)) {
        return refused(
            "invalid.native-core-primitive.analytic-shape.semantic-authority",
            "Analytic shape uniform construction requires the exact prepared semantic object.",
        )
    }

    val commandId = semantic.payloadRef.commandIdValue
    val geometry = semantic.geometry
    val signedBounds: List<Float>
    val signedRadii: List<Float>
    when (semantic.sourceFamily) {
        GPUCorePrimitiveSourceFamily.Rect -> {
            val rect = geometry as? GPUCorePrimitiveGeometry.Rect ?: return refused(
                "invalid.native-core-primitive.analytic-shape.source",
                "Analytic Rect uniform construction requires Rect source and device geometry.",
            )
            if (semantic.analysisRecordId != "analysis.fill_rect.$commandId" ||
                semantic.analysisCommandFamily != "FillRect"
            ) {
                return refused(
                    "invalid.native-core-primitive.analytic-shape.source",
                    "Analytic Rect uniform construction requires exact FillRect analysis identity.",
                )
            }
            val rectAuthority = semantic.rectGeometryAuthority
            if (semantic.rectRouteAuthority != GPUCorePrimitiveRectRouteAuthority.RectAxisAligned ||
                rectAuthority == null ||
                semantic.rrectGeometryAuthority != null ||
                !GPUCorePrimitiveRectGeometryAuthority.hasExactAxisAlignedDeviceGeometry(
                    rectAuthority,
                    rect,
                )
            ) {
                return refused(
                    "invalid.native-core-primitive.analytic-shape.geometry-authority",
                    "Analytic Rect uniform construction requires exact axis-aligned geometry authority.",
                )
            }
            signedBounds = listOf(rect.left, rect.top, rect.right, rect.bottom)
            signedRadii = List(8) { 0f }
        }
        GPUCorePrimitiveSourceFamily.RRect -> {
            val rrect = geometry as? GPUCorePrimitiveGeometry.RRect ?: return refused(
                "invalid.native-core-primitive.analytic-shape.source",
                "Analytic RRect uniform construction requires RRect source and device geometry.",
            )
            if (semantic.analysisRecordId != "analysis.fill_rrect.$commandId" ||
                semantic.analysisCommandFamily != "FillRRect"
            ) {
                return refused(
                    "invalid.native-core-primitive.analytic-shape.source",
                    "Analytic RRect uniform construction requires exact FillRRect analysis identity.",
                )
            }
            val rrectAuthority = semantic.rrectGeometryAuthority
            if (semantic.rectRouteAuthority != null ||
                semantic.rectGeometryAuthority != null ||
                rrectAuthority == null ||
                !GPUCorePrimitiveRRectGeometryAuthority.hasExactDeviceGeometry(rrectAuthority, rrect)
            ) {
                return refused(
                    "invalid.native-core-primitive.analytic-shape.geometry-authority",
                    "Analytic RRect uniform construction requires exact signed normalized geometry authority.",
                )
            }
            val signed = rrectAuthority.sealedDeviceGeometryInput()
            signedBounds = listOf(signed.left, signed.top, signed.right, signed.bottom)
            signedRadii = signed.radii
        }
        else -> return refused(
            "invalid.native-core-primitive.analytic-shape.source",
            "Analytic shape uniform construction accepts only Rect and RRect sources.",
        )
    }

    val antiAlias = when (semantic.coverageMode) {
        GPUCorePrimitiveCoverageMode.FullOrScissor -> false
        GPUCorePrimitiveCoverageMode.ScalarAA -> true
        GPUCorePrimitiveCoverageMode.Stencil1x,
        GPUCorePrimitiveCoverageMode.StencilAA,
        -> return refused(
            "unsupported.native-core-primitive.analytic-shape.coverage",
            "Analytic shape uniform construction accepts hard or scalar-AA coverage only.",
        )
    }

    if (semantic.targetBounds.left != 0 || semantic.targetBounds.top != 0) {
        return refused(
            "invalid.native-core-primitive.analytic-shape.semantic",
            "Analytic shape uniform construction requires a zero-origin target.",
        )
    }
    val block = try {
        GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = semantic.targetBounds.width.toFloat(),
            targetHeight = semantic.targetBounds.height.toFloat(),
            antiAlias = antiAlias,
            premultipliedRgba = semantic.premultipliedRgba,
            deviceBounds = signedBounds,
            normalizedRadii = signedRadii,
        )
    } catch (_: IllegalArgumentException) {
        return refused(
            "invalid.native-core-primitive.analytic-shape.semantic",
            "Analytic shape semantic values do not satisfy the uniform80 ABI.",
        )
    }
    return GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted(block)
}

/**
 * Versioned host representation of the analytic Rect/RRect shader ABI.
 *
 * All shape and paint values remain dynamic uniforms. Caller-owned lists are copied so later
 * mutation cannot alter a prepared upload. Builder, preflight, and materialization rebuild this
 * block only from sealed semantic and normalized-geometry authority.
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
