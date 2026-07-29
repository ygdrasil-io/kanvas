package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.floor
import kotlin.math.sqrt
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.glyph.GlyphArtifactRoute
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformValue
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point

private const val PREPARED_TEXT_MATERIAL_DICTIONARY_VERSION =
    "material-dictionary:prepared-material:v1"

/** Pure Kotlin prepared-text lowering with one terminal result per operation. */
object GPUPreparedTextLowerer {
    /** Canonical product entry; font identity and bytes are always resolved by the built-in authority. */
    fun lower(
        operation: DisplayOp.DrawText,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
    ): GPUPreparedTextLowering = lower(
        operation = operation,
        operationIndex = operationIndex,
        target = target,
        capabilities = capabilities,
        fontResolver = GPUPreparedFontTypefaceResolver,
    )

    /**
     * Injectable authority seam required by the prepared-text contract.
     *
     * External resolvers may delegate, filter, or return a canonical refusal.
     * They cannot construct or copy a ready face snapshot.
     */
    fun lower(
        operation: DisplayOp.DrawText,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
        fontResolver: GPUPreparedTextFontResolver,
    ): GPUPreparedTextLowering {
        val fontResolution = try {
            fontResolver.resolve(operation.blob.typeface)
        } catch (_: Exception) {
            return refused(
                GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                operationIndex,
                "Prepared text font resolution failed",
                facts = mapOf(
                    "stage" to "font-resolution",
                    "reason" to "resolver-exception",
                    "authority" to "GPUPreparedTextFontResolver",
                    "expected" to "one canonical font resolution",
                    "observed" to "resolver threw",
                ),
            )
        }
        val resolved = when (val result = fontResolution) {
            is GPUPreparedTextFontResolution.Ready -> result
            is GPUPreparedTextFontResolution.Refused ->
                return refused(
                    result.code,
                    operationIndex,
                    result.message,
                    facts = mapOf("authority" to "GPUPreparedTextFontResolver"),
                )
        }
        val requestedTypeface = operation.blob.typeface
            ?: return refused(
                GPUTextRefusalCodes.TYPEFACE_MISSING,
                operationIndex,
                "Prepared text requires an exact requested typeface",
            )
        val exactRequestedTypeface = requestedTypeface as? FontTypeface
            ?: return refused(
                GPUTextRefusalCodes.TYPEFACE_UNSUPPORTED,
                operationIndex,
                "Prepared text requires FontTypeface identity and source bytes",
            )
        val requestedTypefaceId = exactRequestedTypeface.typefaceId
            ?: return refused(
                GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                operationIndex,
                "Requested prepared typeface identity is unavailable",
            )
        val requestedBytes = exactRequestedTypeface.fontBytes
        if (
            resolved.face.sourceId != exactRequestedTypeface.sourceId ||
            resolved.face.typefaceId != requestedTypefaceId ||
            resolved.face.faceIndex != exactRequestedTypeface.faceIndex ||
            resolved.face.bytes.size != requestedBytes.size ||
            resolved.face.bytes.indices.any { index ->
                resolved.face.bytes[index] != (requestedBytes[index].toInt() and 0xff)
            }
        ) {
            return refused(
                GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                operationIndex,
                "Injected prepared font resolution does not match the requested typeface",
                facts = mapOf(
                    "stage" to "font-resolution",
                    "reason" to "identity-mismatch",
                    "authority" to "GPUPreparedTextFontResolver",
                    "expected" to "exact requested source, typeface, face index, and bytes",
                    "observed" to "resolved face differs from requested typeface",
                ),
            )
        }

        val runs = operation.blob.glyphRuns
        operation.blob.variationCoordinates.forEach { (axis, value) ->
            if (
                axis.length != 4 ||
                axis.any { character -> character.code !in 0x20..0x7e } ||
                !value.isFinite()
            ) {
                return refused(
                    GPUTextRefusalCodes.FONT_SIZE_INVALID,
                    operationIndex,
                    "Text variation coordinates must be finite four-character axes",
                )
            }
        }
        val variationCoordinates = immutablePreparedTextMap(
            operation.blob.variationCoordinates.toSortedMap(),
        )
        for (run in runs) {
            if (run.glyphs.size != run.positions.size) {
                return refused(
                    GPUTextRefusalCodes.POSITION_COUNT_MISMATCH,
                    operationIndex,
                    "Each shaped glyph must have exactly one position",
                )
            }
        }
        if (!operation.blob.fontSize.isFinite() || operation.blob.fontSize < 0f) {
            return refused(
                GPUTextRefusalCodes.FONT_SIZE_INVALID,
                operationIndex,
                "Text blob font size must be finite and non-negative",
            )
        }
        for (run in runs) {
            if (!run.fontSize.isFinite() || run.fontSize < 0f) {
                return refused(
                    GPUTextRefusalCodes.FONT_SIZE_INVALID,
                    operationIndex,
                    "Glyph run font size must be finite and non-negative",
                )
            }
        }
        for (run in runs) {
            if (run.positions.any { position -> !position.x.isFinite() || !position.y.isFinite() }) {
                return refused(
                    GPUTextRefusalCodes.POSITION_NONFINITE,
                    operationIndex,
                    "Glyph positions must be finite",
                )
            }
        }
        for (run in runs) {
            for (sourceGlyphId in run.glyphs) {
                val glyphId = sourceGlyphId.toInt()
                if (glyphId !in 0 until resolved.glyphCount) {
                    return refused(
                        GPUTextRefusalCodes.GLYPH_ID_INVALID,
                        operationIndex,
                        "Glyph $glyphId is outside the parsed face range",
                    )
                }
            }
        }
        val transformValues = operation.transform.valuesForPreparedText()
        if (transformValues.any { value -> !value.isFinite() }) {
            return refused(
                GPUTextRefusalCodes.TRANSFORM_NONFINITE,
                operationIndex,
                "Text transform must contain only finite values",
            )
        }
        if (
            operation.transform.persp0 != 0f ||
            operation.transform.persp1 != 0f ||
            operation.transform.persp2 != 1f
        ) {
            return refused(
                GPUTextRefusalCodes.TRANSFORM_PERSPECTIVE,
                operationIndex,
                "Prepared text requires an affine transform",
            )
        }
        val determinant =
            operation.transform.scaleX * operation.transform.scaleY -
                operation.transform.skewX * operation.transform.skewY
        if (!determinant.isFinite()) {
            return refused(
                GPUTextRefusalCodes.TRANSFORM_NONFINITE,
                operationIndex,
                "Text transform determinant must be finite",
            )
        }
        if (determinant == 0f) {
            return refused(
                GPUTextRefusalCodes.TRANSFORM_SINGULAR,
                operationIndex,
                "Prepared text transform must be invertible",
            )
        }
        val affineScaleX = sqrt(
            operation.transform.scaleX.toDouble() * operation.transform.scaleX.toDouble() +
                operation.transform.skewY.toDouble() * operation.transform.skewY.toDouble(),
        ).toFloat()
        val affineScaleY = sqrt(
            operation.transform.skewX.toDouble() * operation.transform.skewX.toDouble() +
                operation.transform.scaleY.toDouble() * operation.transform.scaleY.toDouble(),
        ).toFloat()
        if (
            !affineScaleX.isFinite() || affineScaleX <= 0f ||
            !affineScaleY.isFinite() || affineScaleY <= 0f
        ) {
            return refused(
                GPUTextRefusalCodes.TRANSFORM_NONFINITE,
                operationIndex,
                "Text affine basis lengths must be finite and positive",
            )
        }
        val transformBucket = operation.transform.preparedTextLinearTransformBucket()

        val glyphCandidates = ArrayList<PreparedTextGlyphCandidate>()
        for (run in runs) {
            for (index in run.glyphs.indices) {
                glyphCandidates += PreparedTextGlyphCandidate(
                    glyphId = run.glyphs[index].toInt(),
                    positionX = run.positions[index].x,
                    positionY = run.positions[index].y,
                    fontSize = run.fontSize,
                )
            }
        }

        val representationDecisions = glyphCandidates.map { glyph ->
            resolvePreparedTextRepresentation(
                glyph = glyph,
                variationCoordinates = variationCoordinates,
                resolver = resolved.representationResolver,
            )
        }
        representationDecisions.asSequence()
            .filterIsInstance<PreparedTextRepresentationDecision.Refused>()
            .minWithOrNull(
                compareBy<PreparedTextRepresentationDecision.Refused>(
                    { refusal -> refusal.priority },
                    { refusal -> refusal.code },
                ),
            )
            ?.let { refusal ->
                return refused(
                    code = refusal.code,
                    operationIndex = operationIndex,
                    message = refusal.message,
                    facts = refusal.facts,
                )
            }

        if (!operation.x.isFinite() || !operation.y.isFinite()) {
            return refused(
                GPUTextRefusalCodes.ORIGIN_NONFINITE,
                operationIndex,
                "Text origin must be finite",
            )
        }
        val preparedGlyphs = ArrayList<GPUPreparedGlyphInput>(glyphCandidates.size)
        val representations = ArrayList<GPUPreparedTextRepresentation>(glyphCandidates.size)
        glyphCandidates.forEachIndexed { index, glyph ->
            val preparedRepresentation =
                (representationDecisions[index] as PreparedTextRepresentationDecision.Ready)
                    .representation
            val effectiveX = (glyph.positionX.toDouble() + operation.x.toDouble()).toFloat()
            val effectiveY = (glyph.positionY.toDouble() + operation.y.toDouble()).toFloat()
            if (!effectiveX.isFinite() || !effectiveY.isFinite()) {
                return refused(
                    GPUTextRefusalCodes.POSITION_NONFINITE,
                    operationIndex,
                    "Effective glyph positions must remain finite after applying the text origin",
                )
            }
            val deviceAnchor = operation.transform * Point(effectiveX, effectiveY)
            if (!deviceAnchor.x.isFinite() || !deviceAnchor.y.isFinite()) {
                return refused(
                    GPUTextRefusalCodes.POSITION_NONFINITE,
                    operationIndex,
                    "Effective glyph positions must remain finite in device space",
                )
            }
            preparedGlyphs += GPUPreparedGlyphInput.create(
                glyphId = glyph.glyphId,
                positionX = glyph.positionX,
                positionY = glyph.positionY,
                fontSize = glyph.fontSize,
                strikeKey = GlyphStrikeKey(
                    typefaceId = resolved.face.typefaceId,
                    glyphId = glyph.glyphId,
                    sizePx = glyph.fontSize,
                    scaleX = affineScaleX,
                    scaleY = affineScaleY,
                    subpixelX = fractionalPreparedTextPosition(deviceAnchor.x.toDouble()),
                    subpixelY = fractionalPreparedTextPosition(deviceAnchor.y.toDouble()),
                    variationCoordinates = variationCoordinates,
                    representationRoute = when (preparedRepresentation) {
                        GPUPreparedTextRepresentation.A8_MASK ->
                            GlyphArtifactRoute.A8.diagnosticName
                        GPUPreparedTextRepresentation.COLRV0 ->
                            GlyphArtifactRoute.COLOR.diagnosticName
                    },
                    maskFormat = when (preparedRepresentation) {
                        GPUPreparedTextRepresentation.A8_MASK ->
                            GlyphArtifactRoute.A8.diagnosticName
                        GPUPreparedTextRepresentation.COLRV0 ->
                            GlyphStrikeKey.NoMaskFormat
                    },
                    transformBucket = transformBucket,
                ),
            )
            representations += preparedRepresentation
        }

        val clipProof = when (
            val clipResult = validateAndSnapshotPreparedTextClip(operation.clip, target, capabilities)
        ) {
            is PreparedTextClipResult.Ready -> clipResult
            is PreparedTextClipResult.Refused ->
                return refused(
                    GPUTextRefusalCodes.CLIP_ROUTE_UNACCEPTED,
                    operationIndex,
                    clipResult.message,
                )
        }

        val paint = when (val blender = operation.paint.blender) {
            null -> operation.paint
            is Blender.Mode -> operation.paint.copy(
                blendMode = blender.mode,
                blender = null,
            )
            is Blender.Arithmetic -> return refused(
                GPUTextRefusalCodes.BLEND_UNSUPPORTED,
                operationIndex,
                "Prepared text cannot normalize an arithmetic blender to BlendMode",
            )
        }
        if (paint.style != PaintStyle.FILL) {
            return refused(
                GPUTextRefusalCodes.PAINT_STYLE_UNSUPPORTED,
                operationIndex,
                "Prepared text accepts fill paint only",
            )
        }
        if (paint.pathEffect != null) {
            return refused(
                GPUTextRefusalCodes.PATH_EFFECT_UNSUPPORTED,
                operationIndex,
                "Prepared text path effects require geometry expansion",
            )
        }
        if (paint.imageFilter != null) {
            return refused(
                GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE,
                operationIndex,
                "Text image filters require a composite route",
            )
        }
        val blendPlan = paint.blendMode.toGpuBlendFacts().canonicalBlendPlan(
            coverage = GPUCoverageConsumption.ScalarCoverage,
            targetFormatClass = target.colorFormat,
        )
        if (blendPlan is GPUBlendPlan.UnsupportedBlend) {
            return refused(
                GPUTextRefusalCodes.BLEND_UNSUPPORTED,
                operationIndex,
                "Common blend authority refused ${blendPlan.diagnostic.code}: " +
                    blendPlan.diagnostic.message,
                facts = mapOf(
                    "commonDiagnosticCode" to blendPlan.diagnostic.code,
                    "commonDiagnosticMessage" to blendPlan.diagnostic.message,
                    "blendMode" to paint.blendMode.name,
                    "targetFormatClass" to target.colorFormat,
                    "coverage" to GPUCoverageConsumption.ScalarCoverage.name,
                ),
            )
        }
        when (val maskFilter = paint.maskFilter) {
            null -> Unit
            is MaskFilter.Blur -> if (!maskFilter.sigma.isFinite() || maskFilter.sigma < 0f) {
                return refused(
                    GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED,
                    operationIndex,
                    "Prepared text blur sigma must be finite and non-negative",
                )
            }
            is MaskFilter.Shader,
            is MaskFilter.Table,
            -> return refused(
                GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED,
                operationIndex,
                "Prepared text supports only the canonical blur mask filter",
            )
        }

        val mapped = runCatching { paint.toPreparedMaterialMapping() }.getOrElse {
            return refused(
                GPUTextRefusalCodes.MATERIAL_UNSUPPORTED,
                operationIndex,
                "Prepared text material mapping failed",
                facts = mapOf(
                    "stage" to "material-mapping",
                    "reason" to "mapper-exception",
                    "authority" to "GPUMaterialMapper",
                    "expected" to "one canonical material descriptor",
                    "observed" to "mapper threw",
                ),
            )
        }
        val material = when (
            val result = GPUPreparedMaterialProgramCompiler.compile(
                descriptor = mapped.descriptor,
                paintAlpha = mapped.paintAlpha,
                context = preparedTextMaterialContext(target, capabilities),
            )
        ) {
            is GPUPreparedMaterialProgramResult.Ready -> result.program
            is GPUPreparedMaterialProgramResult.Refused ->
                return refused(
                    result.code,
                    operationIndex,
                    result.message,
                    facts = mapOf(
                        "sourceKind" to result.sourceKind.name,
                    ),
                )
        }
        val capabilitySnapshotHash = capabilities.canonicalSnapshotHash()

        return GPUPreparedTextLowering.Ready.create(
            GPUPreparedTextDraw.create(
                operationIndex = operationIndex,
                face = resolved.face,
                glyphs = preparedGlyphs,
                originX = operation.x,
                originY = operation.y,
                transform = operation.transform.snapshotForPreparedText(),
                clipContentKey = clipProof.contentKey,
                clip = clipProof.clip,
                paint = paint.snapshotForPreparedText(),
                material = material,
                blendPlan = blendPlan,
                targetColorFormat = target.colorFormat,
                capabilitySnapshotHash = capabilitySnapshotHash,
                representationPolicy = GPUPreparedTextRepresentationPolicy.create(
                    representations,
                ),
            ),
        )
    }
}

private data class PreparedTextGlyphCandidate(
    val glyphId: Int,
    val positionX: Float,
    val positionY: Float,
    val fontSize: Float,
)

private sealed interface PreparedTextRepresentationDecision {
    data class Ready(
        val representation: GPUPreparedTextRepresentation,
    ) : PreparedTextRepresentationDecision

    data class Refused(
        val code: String,
        val message: String,
        val priority: Int,
        val facts: Map<String, String>,
    ) : PreparedTextRepresentationDecision
}

private fun resolvePreparedTextRepresentation(
    glyph: PreparedTextGlyphCandidate,
    variationCoordinates: Map<String, Float>,
    resolver: GPUPreparedTextGlyphRepresentationResolver,
): PreparedTextRepresentationDecision {
    val sourceRepresentation = try {
        resolver.resolve(
            glyph.glyphId,
            glyph.fontSize,
            variationCoordinates,
        )
    } catch (_: Exception) {
        return preparedTextRepresentationRefusal(
            code = if (glyph.glyphId == 0) {
                GPUTextRefusalCodes.NOTDEF_UNAVAILABLE
            } else {
                GPUTextRefusalCodes.REPRESENTATION_MISSING
            },
            glyphId = glyph.glyphId,
            sourceRepresentation = null,
            reason = "resolver-exception",
        )
    }
    return when (sourceRepresentation) {
        GPUPreparedTextSourceRepresentation.OUTLINE ->
            PreparedTextRepresentationDecision.Ready(GPUPreparedTextRepresentation.A8_MASK)
        GPUPreparedTextSourceRepresentation.COLRV0 ->
            PreparedTextRepresentationDecision.Ready(GPUPreparedTextRepresentation.COLRV0)
        GPUPreparedTextSourceRepresentation.CBDT_CBLC ->
            preparedTextRepresentationRefusal(
                GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED,
                glyph.glyphId,
                sourceRepresentation,
            )
        GPUPreparedTextSourceRepresentation.SBIX ->
            preparedTextRepresentationRefusal(
                GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED,
                glyph.glyphId,
                sourceRepresentation,
            )
        GPUPreparedTextSourceRepresentation.SVG ->
            preparedTextRepresentationRefusal(
                GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
                glyph.glyphId,
                sourceRepresentation,
            )
        GPUPreparedTextSourceRepresentation.COLRV1 ->
            preparedTextRepresentationRefusal(
                GPUTextRefusalCodes.COLRV1_UNPROVED,
                glyph.glyphId,
                sourceRepresentation,
            )
        GPUPreparedTextSourceRepresentation.MISSING ->
            preparedTextRepresentationRefusal(
                if (glyph.glyphId == 0) {
                    GPUTextRefusalCodes.NOTDEF_UNAVAILABLE
                } else {
                    GPUTextRefusalCodes.REPRESENTATION_MISSING
                },
                glyph.glyphId,
                sourceRepresentation,
            )
    }
}

private fun preparedTextRepresentationRefusal(
    code: String,
    glyphId: Int,
    sourceRepresentation: GPUPreparedTextSourceRepresentation?,
    reason: String = "representation-unaccepted",
): PreparedTextRepresentationDecision.Refused =
    PreparedTextRepresentationDecision.Refused(
        code = code,
        message = when (code) {
            GPUTextRefusalCodes.NOTDEF_UNAVAILABLE ->
                "Glyph zero has no admitted visible .notdef representation"
            GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED ->
                "CBDT/CBLC glyphs are not admitted by prepared text Task 4"
            GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED ->
                "sbix glyphs are not admitted by prepared text Task 4"
            GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED ->
                "SVG glyphs are not admitted by prepared text Task 4"
            GPUTextRefusalCodes.COLRV1_UNPROVED ->
                "COLRv1 glyph execution is not proved"
            GPUTextRefusalCodes.REPRESENTATION_MISSING ->
                "A shaped glyph has no admitted internal representation"
            else -> error("Unknown prepared text representation refusal code $code")
        },
        facts = immutablePreparedTextMap(
            linkedMapOf(
                "stage" to "representation",
                "reason" to reason,
                "authority" to "GPUPreparedTextGlyphRepresentationResolver",
                "glyphId" to glyphId.toString(),
                "format" to (sourceRepresentation?.name ?: "resolver-exception"),
                "expected" to "OUTLINE or COLRV0",
                "observed" to (sourceRepresentation?.name ?: "resolver exception"),
            ),
        ),
        priority = when (code) {
            GPUTextRefusalCodes.NOTDEF_UNAVAILABLE -> 0
            GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED -> 1
            GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED -> 2
            GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED -> 3
            GPUTextRefusalCodes.COLRV1_UNPROVED -> 4
            GPUTextRefusalCodes.REPRESENTATION_MISSING -> 5
            else -> error("Unknown prepared text representation refusal code $code")
        },
    )

internal fun preparedTextMaterialContext(
    target: GPUTargetFacts,
    capabilities: GPUCapabilities,
): GPUMaterialLoweringContext {
    return GPUMaterialLoweringContext(
        capabilityClass = capabilities.canonicalSnapshotHash(),
        targetFormatClass = target.colorFormat,
        dictionaryVersion = PREPARED_TEXT_MATERIAL_DICTIONARY_VERSION,
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )
}

private sealed interface PreparedTextClipResult {
    data class Ready(
        val clip: ClipStack,
        val contentKey: String,
    ) : PreparedTextClipResult
    data class Refused(val message: String) : PreparedTextClipResult
}

private fun validateAndSnapshotPreparedTextClip(
    clip: ClipStack,
    target: GPUTargetFacts,
    capabilities: GPUCapabilities,
): PreparedTextClipResult {
    if (clip.perspectiveCaptureRefusal) {
        return PreparedTextClipResult.Refused("Prepared text clip was captured under perspective")
    }
    if (clip == ClipStack.WideOpen) {
        return PreparedTextClipResult.Ready(
            clip = ClipStack.WideOpen,
            contentKey = "prepared-text-clip:wide-open",
        )
    }
    val request = runCatching { clip.toGPUClipFacts(target).coverageRequest }.getOrNull()
        ?: return PreparedTextClipResult.Refused("Prepared text clip has no common coverage request")
    val maxTextureDimension = capabilities.limits?.maxTextureDimension2D
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: maxOf(target.width, target.height)
    val plan = runCatching {
        GPUClipCoveragePlanner.planForFrameRoute(
            request = request,
            config = RenderConfig.DEFAULT,
            maxTextureDimension2D = maxTextureDimension,
        )
    }.getOrElse {
        return PreparedTextClipResult.Refused("Prepared text clip cannot be classified")
    }
    return when (plan) {
        is GPUClipCoveragePlan.Refused ->
            PreparedTextClipResult.Refused("Common clip authority refused ${plan.code}")
        else -> PreparedTextClipResult.Ready(
            clip = clip.snapshotForPreparedText(),
            contentKey = request.contentKey,
        )
    }
}

private fun Matrix33.valuesForPreparedText(): List<Float> = listOf(
    scaleX, skewX, transX,
    skewY, scaleY, transY,
    persp0, persp1, persp2,
)

private fun fractionalPreparedTextPosition(value: Double): Float =
    (value - floor(value)).toFloat()

private fun Matrix33.preparedTextLinearTransformBucket(): String =
    "affine.linear.v1:" + listOf(scaleX, skewX, skewY, scaleY)
        .joinToString(separator = ":") { value ->
            Integer.toUnsignedString(value.toRawBits(), 16).padStart(8, '0')
        }

private fun Matrix33.snapshotForPreparedText(): Matrix33 = Matrix33.makeAll(
    scaleX, skewX, transX,
    skewY, scaleY, transY,
    persp0, persp1, persp2,
)

internal fun ClipStack.snapshotForPreparedText(): ClipStack = when (this) {
    ClipStack.WideOpen -> ClipStack.WideOpen
    is ClipStack.DeviceRect -> copy(rect = rect.copy())
    is ClipStack.Complex -> ClipStack.Complex(
        immutablePreparedTextList(
            ops.map { operation ->
                when (operation) {
                    is ClipStackOp.RectOp -> operation.copy(rect = operation.rect.copy())
                    is ClipStackOp.RRectOp -> operation.copy(rrect = operation.rrect.copy())
                    is ClipStackOp.PathOp -> operation.copy(path = operation.path.snapshotForPreparedText())
                }
            },
        ),
    )
}

private fun Path.snapshotForPreparedText(): Path = Path().also { snapshot ->
    snapshot.fillType = fillType
    snapshot.addPath(this)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun Paint.snapshotForPreparedText(): Paint =
    PreparedTextPaintSnapshotter().snapshot(this)

@OptIn(ExperimentalUnsignedTypes::class)
private class PreparedTextPaintSnapshotter {
    private val shaderSnapshots = IdentityHashMap<Shader, Shader>()
    private val colorFilterSnapshots = IdentityHashMap<ColorFilter, ColorFilter>()
    private val imageSnapshots = IdentityHashMap<Image, Image>()
    private val runtimeEffectSnapshots = IdentityHashMap<RuntimeEffect, RuntimeEffect>()

    fun snapshot(paint: Paint): Paint = paint.copy(
        shader = paint.shader?.let(::snapshotShader),
        colorFilter = paint.colorFilter?.let(::snapshotColorFilter),
        maskFilter = when (val filter = paint.maskFilter) {
            null -> null
            is MaskFilter.Blur -> filter.copy()
            is MaskFilter.Shader -> MaskFilter.Shader(snapshotShader(filter.shader))
            is MaskFilter.Table -> MaskFilter.Table(filter.table.copyOf())
        },
    )

    private fun snapshotShader(shader: Shader, depth: Int = 0): Shader {
        require(depth <= 64) {
            "Prepared text shader snapshot exceeds the canonical graph budget"
        }
        shaderSnapshots[shader]?.let { return it }
        val snapshot = when (shader) {
            is Shader.SolidColor -> shader.copy()
            is Shader.LinearGradient -> shader.copy(
                stops = immutablePreparedTextList(shader.stops.map(GradientStop::copy)),
            )
            is Shader.RadialGradient -> shader.copy(
                stops = immutablePreparedTextList(shader.stops.map(GradientStop::copy)),
            )
            is Shader.SweepGradient -> shader.copy(
                stops = immutablePreparedTextList(shader.stops.map(GradientStop::copy)),
            )
            is Shader.ConicalGradient -> shader.copy(
                stops = immutablePreparedTextList(shader.stops.map(GradientStop::copy)),
            )
            is Shader.Image -> shader.copy(image = snapshotImage(shader.image))
            is Shader.Blend -> shader.copy(
                dst = snapshotShader(shader.dst, depth + 1),
                src = snapshotShader(shader.src, depth + 1),
            )
            is Shader.RuntimeEffect -> shader.copy(
                effect = snapshotRuntimeEffect(shader.effect),
                uniforms = shader.uniforms.snapshotForPreparedText(),
                children = immutablePreparedTextMap(
                    shader.children.entries.sortedBy { entry -> entry.key }
                        .associate { (name, child) ->
                            name to snapshotShader(child, depth + 1)
                        },
                ),
            )
            is Shader.WithLocalMatrix -> shader.copy(
                shader = snapshotShader(shader.shader, depth + 1),
                matrix = shader.matrix.snapshotForPreparedText(),
            )
            is Shader.WithColorFilter -> shader.copy(
                shader = snapshotShader(shader.shader, depth + 1),
                filter = snapshotColorFilter(shader.filter, depth + 1),
            )
            is Shader.PerlinNoise -> shader.copy()
            is Shader.FractalNoise -> shader.copy()
            is Shader.WithWorkingColorSpace -> shader.copy(
                shader = snapshotShader(shader.shader, depth + 1),
            )
            is Shader.CoordClamp -> shader.copy(
                shader = snapshotShader(shader.shader, depth + 1),
            )
        }
        shaderSnapshots[shader] = snapshot
        return snapshot
    }

    private fun snapshotColorFilter(filter: ColorFilter, depth: Int = 0): ColorFilter {
        require(depth <= 64) {
            "Prepared text color-filter snapshot exceeds the canonical graph budget"
        }
        colorFilterSnapshots[filter]?.let { return it }
        val snapshot = when (filter) {
            is ColorFilter.Matrix -> ColorFilter.Matrix(filter.values.copyOf())
            is ColorFilter.Blend -> filter.copy()
            is ColorFilter.Compose -> filter.copy(
                outer = snapshotColorFilter(filter.outer, depth + 1),
                inner = snapshotColorFilter(filter.inner, depth + 1),
            )
            is ColorFilter.Table -> ColorFilter.Table(filter.table.copyOf())
            is ColorFilter.Lighting -> filter.copy()
            ColorFilter.SRGBToLinear -> ColorFilter.SRGBToLinear
            ColorFilter.LinearToSRGB -> ColorFilter.LinearToSRGB
            is ColorFilter.HSLAMatrix -> ColorFilter.HSLAMatrix(filter.values.copyOf())
            is ColorFilter.Lerp -> filter.copy(
                dst = snapshotColorFilter(filter.dst, depth + 1),
                src = snapshotColorFilter(filter.src, depth + 1),
            )
            ColorFilter.HighContrast -> ColorFilter.HighContrast
            ColorFilter.Luma -> ColorFilter.Luma
            ColorFilter.Overdraw -> ColorFilter.Overdraw
            is ColorFilter.RuntimeEffect -> filter.copy(
                effect = snapshotRuntimeEffect(filter.effect),
                uniforms = filter.uniforms.snapshotForPreparedText(),
                children = immutablePreparedTextMap(
                    filter.children.entries.sortedBy { entry -> entry.key }
                        .associate { (name, child) ->
                            name to snapshotColorFilter(child, depth + 1)
                        },
                ),
            )
        }
        colorFilterSnapshots[filter] = snapshot
        return snapshot
    }

    private fun snapshotImage(image: Image): Image {
        imageSnapshots[image]?.let { return it }
        return image.snapshotForPreparedText().also { snapshot ->
            imageSnapshots[image] = snapshot
        }
    }

    private fun snapshotRuntimeEffect(effect: RuntimeEffect): RuntimeEffect {
        runtimeEffectSnapshots[effect]?.let { return it }
        return RuntimeEffect(
            id = effect.id,
            module = effect.module.immutableSnapshot(),
            uniformLayout = UniformLayout(
                immutablePreparedTextList(effect.uniformLayout.slots),
            ),
            children = immutablePreparedTextList(effect.children),
        ).also { snapshot ->
            runtimeEffectSnapshots[effect] = snapshot
        }
    }
}

private fun Image.snapshotForPreparedText(): Image = copy(pixels = pixels?.copyOf())

private fun UniformBlock.snapshotForPreparedText(): UniformBlock = UniformBlock {
    this@snapshotForPreparedText.entries.toSortedMap().forEach { (name, value) ->
        when (value) {
            is UniformValue.F1 -> float1(name, value.v)
            is UniformValue.F2 -> float2(name, value.x, value.y)
            is UniformValue.F3 -> float3(name, value.x, value.y, value.z)
            is UniformValue.F4 -> float4(name, value.x, value.y, value.z, value.w)
            is UniformValue.I1 -> int1(name, value.v)
            is UniformValue.M3 -> mat3x3(name, value.m.snapshotForPreparedText())
            is UniformValue.M4 -> mat4x4(name, value.values.copyOf())
        }
    }
}

private fun <K, V> immutablePreparedTextMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

private fun refused(
    code: String,
    operationIndex: Int,
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUPreparedTextLowering.Refused =
    GPUPreparedTextLowering.Refused.create(
        code = code,
        operationIndex = operationIndex,
        facts = LinkedHashMap<String, String>(facts.size + 4).apply {
            val stage = preparedTextRefusalStage(code)
            put("stage", stage)
            put("reason", code.substringAfterLast('.').replace('_', '-'))
            put("authority", preparedTextRefusalAuthority(stage))
            putAll(facts)
            put("message", message)
        },
    )

private fun preparedTextRefusalStage(code: String): String = when (code) {
    GPUTextRefusalCodes.TYPEFACE_MISSING,
    GPUTextRefusalCodes.TYPEFACE_UNSUPPORTED,
    GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
    GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
    -> "font-resolution"
    GPUTextRefusalCodes.POSITION_COUNT_MISMATCH,
    GPUTextRefusalCodes.FONT_SIZE_INVALID,
    GPUTextRefusalCodes.POSITION_NONFINITE,
    GPUTextRefusalCodes.GLYPH_ID_INVALID,
    -> "glyph-input"
    GPUTextRefusalCodes.NOTDEF_UNAVAILABLE,
    GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED,
    GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED,
    GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
    GPUTextRefusalCodes.COLRV1_UNPROVED,
    GPUTextRefusalCodes.REPRESENTATION_MISSING,
    -> "representation"
    GPUTextRefusalCodes.ORIGIN_NONFINITE,
    GPUTextRefusalCodes.TRANSFORM_NONFINITE,
    GPUTextRefusalCodes.TRANSFORM_SINGULAR,
    GPUTextRefusalCodes.TRANSFORM_PERSPECTIVE,
    -> "geometry"
    GPUTextRefusalCodes.CLIP_ROUTE_UNACCEPTED -> "clip"
    GPUTextRefusalCodes.BLEND_UNSUPPORTED -> "blend"
    GPUTextRefusalCodes.PAINT_STYLE_UNSUPPORTED,
    GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE,
    GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED,
    GPUTextRefusalCodes.PATH_EFFECT_UNSUPPORTED,
    -> "paint"
    else -> if (code.startsWith("unsupported.material.")) "material" else "material"
}

private fun preparedTextRefusalAuthority(stage: String): String = when (stage) {
    "font-resolution" -> "GPUPreparedFontTypefaceResolver"
    "representation" -> "GPUPreparedTextGlyphRepresentationResolver"
    "clip" -> "GPUClipCoveragePlanner"
    "blend" -> "GPUBlendPlan"
    "material", "material-mapping" -> "GPUPreparedMaterialProgramCompiler"
    else -> "GPUPreparedTextLowerer"
}
