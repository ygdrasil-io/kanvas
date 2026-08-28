package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSourceKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLowerer
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLoweringRequest
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLoweringResult
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

internal sealed interface GPUPreparedStrokeRectLowering {
    class Ready(commands: List<GPUFramePathVisualCommand>) : GPUPreparedStrokeRectLowering {
        val commands: List<GPUFramePathVisualCommand> =
            Collections.unmodifiableList(commands.toList())
    }

    class Refused(
        val code: String,
        val operationIndex: Int,
        facts: Map<String, String>,
    ) : GPUPreparedStrokeRectLowering {
        val facts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

/**
 * Turns a closed, geometry-owned annular stroke proof into four ordinary Surface fill commands.
 * The adapter owns only public-operation admission and command ownership; the renderer lowerer
 * remains the authority for the four device-space bands.
 */
internal object GPUPreparedStrokeRectLowerer {
    fun lower(
        operation: DisplayOp.DrawRect,
        firstCommandId: GPUDrawCommandID,
        firstPaintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        operationIndex: Int = 0,
    ): GPUPreparedStrokeRectLowering {
        commandRangeRefusal(firstCommandId, firstPaintOrder, operationIndex)?.let { return it }
        val paint = operation.paint
        if (paint.antiAlias) {
            return refused("unsupported.stroke.rect_anti_alias", operationIndex, mapOf("antiAlias" to "true"))
        }
        if (paint.pathEffect != null) {
            return refused(
                "unsupported.stroke.rect_path_effect",
                operationIndex,
                mapOf("pathEffect" to paint.pathEffect::class.simpleName.orEmpty()),
            )
        }
        if (paint.maskFilter != null || paint.imageFilter != null || paint.blender != null) {
            return refused(
                "unsupported.stroke.rect_material",
                operationIndex,
                materialRefusalFacts(operation),
            )
        }
        val finalMaterial = when (val shader = paint.shader) {
            null -> {
                if (!paint.hasFoldableSolidColorFilter()) {
                    return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
                }
                paint.toMaterial() as? GPUMaterialDescriptor.SolidColor
                    ?: return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
            }
            is Shader.LinearGradient -> {
                if (operation.transform != Matrix3x3F32.Identity) {
                    return refused(
                        "unsupported.stroke.rect_transform",
                        operationIndex,
                        mapOf("transform" to "gradient_requires_identity"),
                    )
                }
                when {
                    shader.stops.size > 3 -> return refused(
                        "unsupported.stroke.rect_gradient_stop_count",
                        operationIndex,
                        mapOf("stopCount" to shader.stops.size.toString()),
                    )
                    shader.stops.size == 3 && target.colorFormat != "rgba8unorm-srgb" -> return refused(
                        "unsupported.stroke.rect_gradient_target",
                        operationIndex,
                        mapOf("targetFormat" to target.colorFormat),
                    )
                    shader.stops.size == 3 && !capabilities.hasSupportedFact(
                        GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE,
                    ) -> return refused(
                        "unsupported.stroke.rect_linear_gradient_three_stop_capability",
                        operationIndex,
                        mapOf(
                            "capability" to
                                GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE,
                        ),
                    )
                }
                if (paint.colorFilter != null || !shader.isAdmittedStrokeGradient()) {
                    return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
                }
                paint.toMaterial() as? GPUMaterialDescriptor.LinearGradient
                    ?: return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
            }
            is Shader.RadialGradient -> {
                if (operation.transform != Matrix3x3F32.Identity) {
                    return refused(
                        "unsupported.stroke.rect_transform",
                        operationIndex,
                        mapOf("transform" to "gradient_requires_identity"),
                    )
                }
                when {
                    shader.stops.size !in 2..3 -> return refused(
                        "unsupported.stroke.rect_gradient_stop_count",
                        operationIndex,
                        mapOf("stopCount" to shader.stops.size.toString()),
                    )
                    shader.tileMode != TileMode.CLAMP -> return refused(
                        "unsupported.stroke.rect_gradient_tile_mode",
                        operationIndex,
                        mapOf("tileMode" to shader.tileMode.name),
                    )
                    target.colorFormat != "rgba8unorm-srgb" -> return refused(
                        "unsupported.stroke.rect_gradient_target",
                        operationIndex,
                        mapOf("targetFormat" to target.colorFormat),
                    )
                    shader.stops.size == 3 && !capabilities.hasSupportedFact(
                        GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_NATIVE,
                    ) -> return refused(
                        "unsupported.stroke.rect_radial_gradient_three_stop_capability",
                        operationIndex,
                        mapOf("capability" to GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_NATIVE),
                    )
                    shader.stops.size == 2 && !capabilities.hasSupportedFact(
                        GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE,
                    ) -> return refused(
                        "unsupported.stroke.rect_radial_gradient_two_stop_capability",
                        operationIndex,
                        mapOf(
                            "capability" to GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE,
                        ),
                    )
                }
                if (paint.colorFilter != null || !shader.isAdmittedStrokeRadialGradient()) {
                    return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
                }
                paint.toMaterial() as? GPUMaterialDescriptor.RadialGradient
                    ?: return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
            }
            is Shader.SweepGradient -> {
                if (operation.transform != Matrix3x3F32.Identity) return refused(
                    "unsupported.stroke.rect_transform", operationIndex, mapOf("transform" to "gradient_requires_identity"),
                )
                when {
                    shader.stops.size !in 2..3 -> return refused("unsupported.stroke.rect_gradient_stop_count", operationIndex, mapOf("stopCount" to shader.stops.size.toString()))
                    shader.tileMode != TileMode.CLAMP -> return refused("unsupported.stroke.rect_gradient_tile_mode", operationIndex, mapOf("tileMode" to shader.tileMode.name))
                    target.colorFormat != "rgba8unorm-srgb" -> return refused("unsupported.stroke.rect_gradient_target", operationIndex, mapOf("targetFormat" to target.colorFormat))
                    shader.startAngle != 0f || shader.endAngle != 360f -> return refused("unsupported.stroke.rect_gradient_angles", operationIndex, mapOf("startAngle" to shader.startAngle.toString(), "endAngle" to shader.endAngle.toString()))
                    shader.stops.size == 3 && !capabilities.hasSupportedFact(GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_THREE_STOP_NATIVE) -> return refused("unsupported.stroke.rect_sweep_gradient_three_stop_capability", operationIndex, mapOf("capability" to GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_THREE_STOP_NATIVE))
                    shader.stops.size == 2 && !capabilities.hasSupportedFact(GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE) -> return refused(
                        "unsupported.stroke.rect_sweep_gradient_two_stop_capability", operationIndex,
                        mapOf("capability" to GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE),
                    )
                }
                if (paint.colorFilter != null || !shader.isAdmittedStrokeSweepGradient()) return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
                paint.toMaterial() as? GPUMaterialDescriptor.SweepGradient
                    ?: return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
            }
            else -> return refused("unsupported.stroke.rect_material", operationIndex, materialRefusalFacts(operation))
        }

        val deviceRect = when (val admission = operation.strokeRectDeviceBounds(target)) {
            is StrokeRectDeviceBounds.Admitted -> admission.bounds
            StrokeRectDeviceBounds.InvalidTransform ->
                return refused(
                    "unsupported.stroke.rect_transform",
                    operationIndex,
                    mapOf("transform" to "non_integral_or_non_translate"),
                )
            StrokeRectDeviceBounds.SubpixelGeometry ->
                return refused(
                    "unsupported.stroke.rect_subpixel_first_slice",
                    operationIndex,
                    mapOf("geometry" to "non_integral_or_non_finite"),
                )
            StrokeRectDeviceBounds.TargetOverflow ->
                return refused(
                    "unsupported.stroke.rect_target_overflow",
                    operationIndex,
                    mapOf("target" to "${target.width}x${target.height}"),
                )
            StrokeRectDeviceBounds.InnerDegenerate ->
                return refused(
                    "unsupported.stroke.rect_inner_degenerate",
                    operationIndex,
                    mapOf("geometry" to "inverted"),
                )
        }
        val transformClass = if (operation.transform == Matrix3x3F32.Identity) "identity" else "translate"
        val lowered = axisAlignedStrokeRectLowerer.lower(
            GPUAxisAlignedStrokeRectLoweringRequest(
                targetBounds = GPUPixelBounds(0, 0, target.width, target.height),
                pathBounds = deviceRect,
                strokeWidth = paint.strokeWidth,
                pathKey = "path:kanvas:drawRect.stroke:analytic:v1",
                provenance = "kanvas-surface.drawRect.stroke.analytic-four-band",
                cap = paint.strokeCap.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                join = paint.strokeJoin.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                miter = paint.strokeMiter,
                transformClass = transformClass,
            ),
        )
        return when (lowered) {
            is GPUAxisAlignedStrokeRectLoweringResult.Refused -> GPUPreparedStrokeRectLowering.Refused(
                code = lowered.diagnostic.code,
                operationIndex = operationIndex,
                facts = linkedMapOf(
                    "authority" to "GPUPreparedStrokeRectLowerer",
                    "geometryAuthority" to "GPUAxisAlignedStrokeRectLowerer",
                    "operation" to "drawRect.stroke",
                    "geometryLabel" to lowered.diagnostic.geometryLabel,
                    "strokeWidth" to paint.strokeWidth.toString(),
                    "cap" to paint.strokeCap.name,
                    "join" to paint.strokeJoin.name,
                    "miter" to paint.strokeMiter.toString(),
                    "pathBounds" to "${deviceRect.left},${deviceRect.top},${deviceRect.right},${deviceRect.bottom}",
                    "target" to "${target.width}x${target.height}",
                ).apply { putAll(lowered.diagnostic.facts) },
            )
            is GPUAxisAlignedStrokeRectLoweringResult.Lowered -> {
                // Resolve the original material once. The temporary fill paint is deliberately
                // inert; the exact immutable descriptor is restored on all four fill commands.
                val fillPaint = paint.copy(
                    color = ColorARGB.Transparent,
                    colorFilter = null,
                    shader = null,
                    style = PaintStyle.FILL,
                    strokeWidth = 0f,
                )
                val context = GPUPreparedImageLoweringContext(
                    provenance = provenance,
                    target = target,
                    config = config,
                    capabilities = capabilities,
                )
                val commands = lowered.coverageBands.mapIndexed { index, band ->
                    val commandId = GPUDrawCommandID(Math.addExact(firstCommandId.value, index))
                    val paintOrder = Math.addExact(firstPaintOrder, index)
                    val fill = DisplayOp.DrawRect(
                        RectF32.ofLTRB(
                            band.left.toFloat(), band.top.toFloat(),
                            band.right.toFloat(), band.bottom.toFloat(),
                        ),
                        fillPaint,
                        Matrix3x3F32.Identity,
                        operation.clip,
                    )
                    GPUOpMapper.lowerPreparedCoreVisual(fill, commandId, paintOrder, context)
                        ?.withAnalyticStrokeRectSource(provenance, finalMaterial)
                        ?: return refused("unsupported.stroke.rect_material", operationIndex)
                }
                GPUPreparedStrokeRectLowering.Ready(commands)
            }
        }
    }

    private fun refused(
        code: String,
        operationIndex: Int,
        facts: Map<String, String> = emptyMap(),
    ) = GPUPreparedStrokeRectLowering.Refused(
        code,
        operationIndex,
        linkedMapOf(
            "authority" to "GPUPreparedStrokeRectLowerer",
            "operation" to "drawRect.stroke",
        ).apply { putAll(facts) },
    )

    private fun commandRangeRefusal(
        firstCommandId: GPUDrawCommandID,
        firstPaintOrder: Int,
        operationIndex: Int,
    ): GPUPreparedStrokeRectLowering.Refused? {
        val lastCommandId = firstCommandId.value.toLong() + COVERAGE_BAND_COUNT - 1L
        val lastPaintOrder = firstPaintOrder.toLong() + COVERAGE_BAND_COUNT - 1L
        if (firstCommandId.value < 0 || firstPaintOrder < 0 ||
            lastCommandId > Int.MAX_VALUE || lastPaintOrder > Int.MAX_VALUE
        ) {
            return refused(
                "unsupported.stroke.rect_command_range",
                operationIndex,
                mapOf(
                    "firstCommandId" to firstCommandId.value.toString(),
                    "firstPaintOrder" to firstPaintOrder.toString(),
                    "coverageBandCount" to COVERAGE_BAND_COUNT.toString(),
                ),
            )
        }
        return null
    }

    private val axisAlignedStrokeRectLowerer = GPUAxisAlignedStrokeRectLowerer()
    private const val COVERAGE_BAND_COUNT = 4
}

private sealed interface StrokeRectDeviceBounds {
    data class Admitted(val bounds: GPUPixelBounds) : StrokeRectDeviceBounds
    data object InvalidTransform : StrokeRectDeviceBounds
    data object SubpixelGeometry : StrokeRectDeviceBounds
    data object TargetOverflow : StrokeRectDeviceBounds
    data object InnerDegenerate : StrokeRectDeviceBounds
}

private fun DisplayOp.DrawRect.strokeRectDeviceBounds(target: GPUTargetFacts): StrokeRectDeviceBounds {
    val matrix = transform
    val transformValues = floatArrayOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
        matrix.persp0, matrix.persp1, matrix.persp2,
    )
    if (!transformValues.all(Float::isFinite) || matrix.hasPerspective() || matrix.sx != 1f ||
        matrix.sy != 1f || matrix.kx != 0f || matrix.ky != 0f || matrix.persp0 != 0f ||
        matrix.persp1 != 0f || matrix.persp2 != 1f ||
        matrix.tx.toInt().toFloat() != matrix.tx || matrix.ty.toInt().toFloat() != matrix.ty
    ) return StrokeRectDeviceBounds.InvalidTransform
    val coordinates = floatArrayOf(
        rect.left + matrix.tx,
        rect.top + matrix.ty,
        rect.right + matrix.tx,
        rect.bottom + matrix.ty,
    )
    if (!coordinates.all { coordinate ->
            coordinate.isFinite() && coordinate.toInt().toFloat() == coordinate
        }
    ) return StrokeRectDeviceBounds.SubpixelGeometry
    if (coordinates.any { coordinate ->
            coordinate < 0f || coordinate > Int.MAX_VALUE.toFloat()
        }
    ) return StrokeRectDeviceBounds.TargetOverflow
    val left = coordinates[0].toInt()
    val top = coordinates[1].toInt()
    val right = coordinates[2].toInt()
    val bottom = coordinates[3].toInt()
    if (right > target.width || bottom > target.height) {
        return StrokeRectDeviceBounds.TargetOverflow
    }
    if (right < left || bottom < top) return StrokeRectDeviceBounds.InnerDegenerate
    return StrokeRectDeviceBounds.Admitted(
        GPUPixelBounds(left, top, right, bottom),
    )
}

private fun materialRefusalFacts(operation: DisplayOp.DrawRect): Map<String, String> = buildMap {
    operation.paint.shader?.let { put("shader", it::class.simpleName.orEmpty()) }
    operation.paint.colorFilter?.let { put("colorFilter", it::class.simpleName.orEmpty()) }
    operation.paint.maskFilter?.let { put("maskFilter", it::class.simpleName.orEmpty()) }
    operation.paint.imageFilter?.let { put("imageFilter", it::class.simpleName.orEmpty()) }
    operation.paint.blender?.let { put("blender", it::class.simpleName.orEmpty()) }
    if (isEmpty()) put("material", operation.paint.toMaterial()::class.simpleName.orEmpty())
}

/** The direct four-band route may retain only color filters already folded into one solid color. */
private fun org.graphiks.kanvas.paint.Paint.hasFoldableSolidColorFilter(): Boolean =
    colorFilter?.isFoldableSolidColorFilter() ?: true

/** The four-band route deliberately accepts only the exact material ABI executed by the prepared frame. */
private fun Shader.LinearGradient.isAdmittedStrokeGradient(): Boolean {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    return tileMode == TileMode.CLAMP &&
        interpolation == ColorSpaceInterpolation.SRGB &&
        start.x.isFinite() && start.y.isFinite() && end.x.isFinite() && end.y.isFinite() &&
        dx.isFinite() && dy.isFinite() && lengthSquared.isFinite() && lengthSquared > 0f &&
        stops.size in 1..16 &&
        stops.all { stop ->
            stop.position.isFinite() && stop.position in 0f..1f &&
                stop.color.r.isFinite() && stop.color.g.isFinite() &&
                stop.color.b.isFinite() && stop.color.a.isFinite()
        } &&
        stops.zipWithNext().all { (left, right) -> left.position <= right.position }
}

private fun Shader.RadialGradient.isAdmittedStrokeRadialGradient(): Boolean =
    tileMode == TileMode.CLAMP &&
        interpolation == ColorSpaceInterpolation.SRGB &&
        center.x.isFinite() && center.y.isFinite() && radius.isFinite() && radius > 0f &&
        stops.size in 2..3 &&
        stops.first().position == 0f && stops.last().position == 1f &&
        stops.all { stop ->
            stop.position.isFinite() && stop.position in 0f..1f &&
                stop.color.r.isFinite() && stop.color.g.isFinite() &&
                stop.color.b.isFinite() && stop.color.a.isFinite()
        } && stops.zipWithNext().all { (left, right) -> left.position < right.position }

private fun Shader.SweepGradient.isAdmittedStrokeSweepGradient(): Boolean =
    tileMode == TileMode.CLAMP && interpolation == ColorSpaceInterpolation.SRGB &&
        center.x.isFinite() && center.y.isFinite() && startAngle == 0f && endAngle == 360f &&
        stops.size in 2..3 && stops.first().position == 0f && stops.last().position == 1f &&
        stops.all { it.position.isFinite() && it.position in 0f..1f && it.color.r.isFinite() && it.color.g.isFinite() && it.color.b.isFinite() && it.color.a.isFinite() } &&
        stops.zipWithNext().all { (left, right) -> left.position < right.position }

@OptIn(ExperimentalUnsignedTypes::class)
private fun ColorFilter.isFoldableSolidColorFilter(): Boolean = when (this) {
    is ColorFilter.Matrix -> matrix.toFloatArray().all(Float::isFinite)
    is ColorFilter.Table -> table.size >= 256
    is ColorFilter.Lighting,
    ColorFilter.Luma,
    ColorFilter.SRGBToLinear,
    ColorFilter.LinearToSRGB,
    -> true
    is ColorFilter.Compose -> outer.isFoldableSolidColorFilter() && inner.isFoldableSolidColorFilter()
    is ColorFilter.Lerp -> t.isFinite() &&
        dst.isFoldableSolidColorFilter() && src.isFoldableSolidColorFilter()
    is ColorFilter.HSLAMatrix,
    ColorFilter.HighContrast,
    ColorFilter.Overdraw,
    is ColorFilter.RuntimeEffect,
    -> false
    is ColorFilter.Blend -> mode in foldableSolidColorBlendModes
}

private val foldableSolidColorBlendModes = setOf(
    org.graphiks.kanvas.paint.BlendMode.CLEAR,
    org.graphiks.kanvas.paint.BlendMode.SRC,
    org.graphiks.kanvas.paint.BlendMode.DST,
    org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
    org.graphiks.kanvas.paint.BlendMode.DST_OVER,
    org.graphiks.kanvas.paint.BlendMode.SRC_IN,
    org.graphiks.kanvas.paint.BlendMode.DST_IN,
    org.graphiks.kanvas.paint.BlendMode.SRC_OUT,
    org.graphiks.kanvas.paint.BlendMode.DST_OUT,
    org.graphiks.kanvas.paint.BlendMode.SRC_ATOP,
    org.graphiks.kanvas.paint.BlendMode.DST_ATOP,
    org.graphiks.kanvas.paint.BlendMode.XOR,
    org.graphiks.kanvas.paint.BlendMode.PLUS,
    org.graphiks.kanvas.paint.BlendMode.MODULATE,
)

private fun GPUCapabilities.hasSupportedFact(name: String): Boolean =
    facts.any { fact -> fact.name == name && fact.value == "supported" && fact.affectsValidity }

private fun GPUFramePathVisualCommand.withAnalyticStrokeRectSource(
    provenance: GPUFrameProvenance,
    material: GPUMaterialDescriptor,
): GPUFramePathVisualCommand = copy(
    normalized = when (val command = normalized) {
        is org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect -> command.copy(
            source = GPUCommandSource(
                adapter = "kanvas-surface",
                operation = "drawRect.stroke.analytic-four-band",
                frameProvenance = provenance,
                kind = GPUCommandSourceKind.AnalyticStrokeRectBand,
            ),
            material = material,
        )
        else -> error("Analytic rectangular stroke bands must be filled rectangles")
    },
)
