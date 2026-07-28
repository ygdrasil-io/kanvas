package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.isAffine
import org.graphiks.kanvas.types.r

sealed interface GPUPreparedAtlasLowering {
    data class Ready(val commands: List<GPUFramePathVisualCommand>) :
        GPUPreparedAtlasLowering

    data class Refused(
        val code: String,
        val spriteIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedAtlasLowering
}

/**
 * Transactional affine atlas expansion.
 *
 * The complete logical atlas is validated before one artifact is prepared or one command is
 * exposed. Sprites then reuse the regular prepared-image lowerer with the exact composed affine
 * transform, so four-corner position/UV correspondence remains authoritative.
 */
internal object GPUPreparedAtlasLowerer {
    fun lower(
        operation: DisplayOp.DrawAtlas,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedAtlasLowering {
        validateArrays(operation)?.let { return it }
        validateIdentities(
            firstCommandId = firstCommandId,
            firstPaintOrder = firstPaintOrder,
            spriteCount = operation.transforms.size,
        )?.let { return it }
        validateImage(operation, context)?.let { return it }
        val atlasSourceBlend = operation.blendMode.toPreparedAtlasSourceBlend()
            ?: return refused(
                code = GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND,
                spriteIndex = null,
                reason = "unsupported_source_blend",
                extraFacts = mapOf("blendMode" to operation.blendMode.name),
            )
        val paint = when (val resolved = operation.paint.resolvedAtlasPaint(operation.atlas)) {
            is ResolvedAtlasPaint.Ready -> resolved.paint
            is ResolvedAtlasPaint.Refused -> return resolved.refusal
        }

        validateTransform(operation.transform, null)?.let { return it }
        operation.texRects.forEachIndexed { index, rect ->
            validateSourceRect(rect, operation.atlas.width, operation.atlas.height, index)
                ?.let { return it }
            validateTransform(operation.transforms[index], index)?.let { return it }
            val combined = operation.transform * operation.transforms[index]
            validateTransform(combined, index)?.let { return it }
            if (!rect.hasFiniteTransformedCorners(combined)) {
                return refused(
                    code = GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                    spriteIndex = index,
                    reason = "non_finite_transformed_sprite",
                )
            }
        }
        val clipPlans = when (val classified = operation.classifyPreparedAtlasClip(context)) {
            is PreparedAtlasClipClassification.Ready -> classified
            is PreparedAtlasClipClassification.Refused -> return classified.refusal
        }

        val artifact = when (val prepared = GPUPreparedSurfaceImageSource.prepare(operation.atlas)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return GPUPreparedAtlasLowering.Refused(
                code = prepared.code,
                spriteIndex = null,
                facts = prepared.facts + ("sourceId" to operation.atlas.sourceId),
            )
        }

        val temporary = ArrayList<GPUFramePathVisualCommand>(operation.transforms.size)
        operation.transforms.indices.forEach { index ->
            val rect = operation.texRects[index]
            val draw = DisplayOp.DrawImage(
                image = operation.atlas,
                src = rect,
                dst = rect,
                paint = paint,
                transform = operation.transform * operation.transforms[index],
                clip = operation.clip,
            )
            when (
                val lowered = GPUPreparedDrawImageLowerer.lower(
                    operation = draw,
                    commandId = GPUDrawCommandID(0),
                    paintOrder = 0,
                    provenance = context.provenance,
                    target = context.target,
                    config = context.config,
                    capabilities = context.capabilities,
                    preparedArtifact = artifact,
                )
            ) {
                is GPUPreparedDrawImageLowering.Refused -> return GPUPreparedAtlasLowering.Refused(
                    code = lowered.code,
                    spriteIndex = index,
                    facts = lowered.facts,
                )
                is GPUPreparedDrawImageLowering.Ready -> {
                    val atlasColor = operation.colors?.get(index)?.premultipliedRgba()
                    temporary += lowered.command.withAtlasFacts(
                        atlasColor = atlasColor,
                        atlasSourceBlend = atlasSourceBlend.takeIf { atlasColor != null },
                        clipPlans = clipPlans,
                    )
                }
            }
        }
        return GPUPreparedAtlasLowering.Ready(
            temporary.mapIndexed { index, command ->
                command.withAtlasIdentity(
                    commandId = firstCommandId + index,
                    paintOrder = firstPaintOrder + index,
                )
            },
        )
    }

    private fun validateArrays(
        operation: DisplayOp.DrawAtlas,
    ): GPUPreparedAtlasLowering.Refused? {
        val spriteCount = operation.transforms.size
        if (operation.texRects.size != spriteCount ||
            (operation.colors != null && operation.colors.size != spriteCount)
        ) {
            return refused(
                code = GPUPreparedImageRefusalCodes.ATLAS_ARRAY_LENGTHS,
                spriteIndex = null,
                reason = "array_lengths",
                extraFacts = mapOf(
                    "transforms" to spriteCount.toString(),
                    "texRects" to operation.texRects.size.toString(),
                    "colors" to (operation.colors?.size?.toString() ?: "null"),
                ),
            )
        }
        return null
    }

    private fun validateIdentities(
        firstCommandId: Int,
        firstPaintOrder: Int,
        spriteCount: Int,
    ): GPUPreparedAtlasLowering.Refused? {
        val lastCommandId = firstCommandId.toLong() + spriteCount.toLong() - 1L
        val lastPaintOrder = firstPaintOrder.toLong() + spriteCount.toLong() - 1L
        return if (firstCommandId < 0 ||
            firstPaintOrder < 0 ||
            lastCommandId > Int.MAX_VALUE ||
            lastPaintOrder > Int.MAX_VALUE
        ) {
            refused(
                code = GPUPreparedImageRefusalCodes.ATLAS_GEOMETRY,
                spriteIndex = null,
                reason = "command_identity_budget",
            )
        } else {
            null
        }
    }

    private fun validateImage(
        operation: DisplayOp.DrawAtlas,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedAtlasLowering.Refused? {
        if (operation.atlas.width <= 0 || operation.atlas.height <= 0) {
            return refused(
                code = GPUPreparedImageRefusalCodes.DIMENSIONS,
                spriteIndex = null,
                reason = "image_dimensions",
            )
        }
        val limit = context.capabilities.limits?.maxTextureDimension2D
        return if (limit != null &&
            (operation.atlas.width > limit || operation.atlas.height > limit)
        ) {
            refused(
                code = GPUPreparedImageRefusalCodes.TEXTURE_LIMIT,
                spriteIndex = null,
                reason = "texture_limit",
                extraFacts = mapOf("maxTextureDimension2D" to limit.toString()),
            )
        } else {
            null
        }
    }

    private fun validateSourceRect(
        rect: Rect,
        imageWidth: Int,
        imageHeight: Int,
        spriteIndex: Int,
    ): GPUPreparedAtlasLowering.Refused? =
        if (!rect.isFiniteRect() ||
            rect.isEmpty ||
            rect.left < 0f ||
            rect.top < 0f ||
            rect.right > imageWidth.toFloat() ||
            rect.bottom > imageHeight.toFloat()
        ) {
            refused(
                code = GPUPreparedImageRefusalCodes.ATLAS_GEOMETRY,
                spriteIndex = spriteIndex,
                reason = "invalid_source_rect",
            )
        } else {
            null
        }

    private fun validateTransform(
        transform: Matrix33,
        spriteIndex: Int?,
    ): GPUPreparedAtlasLowering.Refused? {
        if (!transform.isAffine()) {
            return refused(
                code = GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                spriteIndex = spriteIndex,
                reason = "perspective_transform",
            )
        }
        val values = listOf(
            transform.scaleX,
            transform.skewX,
            transform.transX,
            transform.skewY,
            transform.scaleY,
            transform.transY,
        )
        val determinant =
            transform.scaleX * transform.scaleY - transform.skewX * transform.skewY
        return if (values.any { !it.isFinite() } || !determinant.isFinite() || determinant == 0f) {
            refused(
                code = GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                spriteIndex = spriteIndex,
                reason = if (values.any { !it.isFinite() }) {
                    "non_finite_transform"
                } else {
                    "singular_transform"
                },
            )
        } else {
            null
        }
    }

    private fun Paint?.resolvedAtlasPaint(
        atlas: org.graphiks.kanvas.image.Image,
    ): ResolvedAtlasPaint {
        val base = this ?: Paint()
        base.unsupportedPreparedImagePaintEffectOrNull()?.let { paintField ->
            return ResolvedAtlasPaint.Refused(
                GPUPreparedAtlasLowering.Refused(
                    code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                    spriteIndex = null,
                    facts = preparedImagePaintEffectRefusalFacts(paintField),
                ),
            )
        }
        val destinationBlend = when (val blender = base.blender) {
            null -> base.blendMode
            is Blender.Mode -> blender.mode
            is Blender.Arithmetic -> return ResolvedAtlasPaint.Refused(
                refused(
                    code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                    spriteIndex = null,
                    reason = "unsupported_blender",
                    extraFacts = mapOf("blenderKind" to "Arithmetic"),
                ),
            )
        }
        if (destinationBlend != BlendMode.SRC_OVER) {
            return ResolvedAtlasPaint.Refused(
                refused(
                    code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                    spriteIndex = null,
                    reason = "unsupported_destination_blend",
                    extraFacts = mapOf(
                        "blendMode" to destinationBlend.name,
                        "supportedBlendMode" to BlendMode.SRC_OVER.name,
                    ),
                ),
            )
        }
        return ResolvedAtlasPaint.Ready(
            base.copy(
                blendMode = destinationBlend,
                blender = null,
                shader = Shader.Image(atlas, sampling = SamplingOptions.LINEAR),
            ),
        )
    }

    private fun GPUFramePathVisualCommand.withAtlasFacts(
        atlasColor: List<Float>?,
        atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
        clipPlans: PreparedAtlasClipClassification.Ready,
    ): GPUFramePathVisualCommand {
        val prepared = requireNotNull(preparedImage)
        return copy(
            normalized = (normalized as NormalizedDrawCommand.DrawImageRect).copy(
                source = normalized.source.copy(operation = "drawAtlas"),
                clip = normalized.clip.copy(
                    coveragePlan = clipPlans.coverage,
                    executionPlan = clipPlans.execution,
                ),
            ),
            clipCoverage = clipPlans.coverage,
            clipExecutionPlan = clipPlans.execution,
            preparedImage = prepared.copy(
                atlasColorPremultipliedRgba = atlasColor,
                atlasSourceBlend = atlasSourceBlend,
            ),
        )
    }

    private fun GPUFramePathVisualCommand.withAtlasIdentity(
        commandId: Int,
        paintOrder: Int,
    ): GPUFramePathVisualCommand {
        val command = normalized as NormalizedDrawCommand.DrawImageRect
        val ordering = GPUOrderingFacts(
            paintOrder = paintOrder,
            dependsOnDestination = command.ordering.dependsOnDestination,
            requiresBarrier = command.ordering.requiresBarrier,
        )
        return copy(
            normalized = command.copy(
                commandId = GPUDrawCommandID(commandId),
                ordering = ordering,
            ),
        )
    }

    private fun Color.premultipliedRgba(): List<Float> = listOf(
        r * a,
        g * a,
        b * a,
        a,
    )

    private fun DisplayOp.DrawAtlas.classifyPreparedAtlasClip(
        context: GPUPreparedImageLoweringContext,
    ): PreparedAtlasClipClassification {
        if (clip == ClipStack.WideOpen) {
            return PreparedAtlasClipClassification.Ready(
                coverage = GPUClipCoveragePlan.NoClip,
                execution = GPUClipExecutionPlan.NoClip,
            )
        }
        if (clip is ClipStack.Complex) {
            return unsupportedPreparedAtlasClip("unsupported_clip_plan")
        }
        val request = clip.toGPUClipFacts(context.target).coverageRequest
            ?: return unsupportedPreparedAtlasClip("missing_clip_request")
        val maxTextureDimension2D = context.capabilities.limits?.maxTextureDimension2D
            ?: maxOf(context.target.width, context.target.height).toLong()
        val coverage = GPUClipCoveragePlanner.plan(
            request = request,
            config = context.config,
            maxTextureDimension2D = maxTextureDimension2D.coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
        )
        if (coverage !is GPUClipCoveragePlan.Scissor ||
            !context.capabilities.supportsPreparedAtlasScissor()
        ) {
            return unsupportedPreparedAtlasClip("unsupported_clip_plan")
        }
        val coordinates = listOf(
            coverage.bounds.left,
            coverage.bounds.top,
            coverage.bounds.right,
            coverage.bounds.bottom,
        )
        if (coordinates.any { !it.isFinite() || it.toInt().toFloat() != it } ||
            coverage.bounds.right < coverage.bounds.left ||
            coverage.bounds.bottom < coverage.bounds.top
        ) {
            return unsupportedPreparedAtlasClip("invalid_scissor")
        }
        val left = coverage.bounds.left.toInt().coerceIn(0, context.target.width)
        val top = coverage.bounds.top.toInt().coerceIn(0, context.target.height)
        val right = coverage.bounds.right.toInt().coerceIn(0, context.target.width)
        val bottom = coverage.bounds.bottom.toInt().coerceIn(0, context.target.height)
        if (right <= left || bottom <= top) {
            return unsupportedPreparedAtlasClip("empty_scissor")
        }
        return PreparedAtlasClipClassification.Ready(
            coverage = GPUClipCoveragePlan.Scissor(
                org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    bottom.toFloat(),
                ),
            ),
            execution = GPUClipExecutionPlan.ScissorOnly(
                GPUPixelBounds(left, top, right, bottom),
            ),
        )
    }

    private fun DisplayOp.DrawAtlas.unsupportedPreparedAtlasClip(
        reason: String,
    ): PreparedAtlasClipClassification.Refused =
        PreparedAtlasClipClassification.Refused(
            refused(
                code = "unsupported.surface.prepared.image-clip",
                spriteIndex = null,
                reason = reason,
            ),
        )

    private fun org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
        .supportsPreparedAtlasScissor(): Boolean =
        knownUnsupportedFacts.none { fact -> fact.name == SCISSOR_NATIVE } &&
            facts.any { fact ->
                fact.name == SCISSOR_NATIVE &&
                    fact.value == "supported" &&
                    fact.affectsValidity
            }

    private fun Rect.isFiniteRect(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    private fun Rect.hasFiniteTransformedCorners(transform: Matrix33): Boolean =
        listOf(
            Point(left, top),
            Point(right, top),
            Point(right, bottom),
            Point(left, bottom),
        ).all { point ->
            val transformed = transform * point
            transformed.x.isFinite() && transformed.y.isFinite()
        }

    private fun BlendMode.toPreparedAtlasSourceBlend(): GPUPreparedAtlasSourceBlend? = when (this) {
        BlendMode.SRC -> GPUPreparedAtlasSourceBlend.Src
        BlendMode.DST -> GPUPreparedAtlasSourceBlend.Dst
        BlendMode.SRC_OVER -> GPUPreparedAtlasSourceBlend.SrcOver
        BlendMode.PLUS -> GPUPreparedAtlasSourceBlend.Plus
        BlendMode.MODULATE -> GPUPreparedAtlasSourceBlend.Modulate
        else -> null
    }

    private fun refused(
        code: String,
        spriteIndex: Int?,
        reason: String,
        extraFacts: Map<String, String> = emptyMap(),
    ) = GPUPreparedAtlasLowering.Refused(
        code = code,
        spriteIndex = spriteIndex,
        facts = mapOf("reason" to reason) + extraFacts,
    )
}

private sealed interface PreparedAtlasClipClassification {
    data class Ready(
        val coverage: GPUClipCoveragePlan,
        val execution: GPUClipExecutionPlan,
    ) : PreparedAtlasClipClassification

    data class Refused(
        val refusal: GPUPreparedAtlasLowering.Refused,
    ) : PreparedAtlasClipClassification
}

private sealed interface ResolvedAtlasPaint {
    data class Ready(val paint: Paint) : ResolvedAtlasPaint
    data class Refused(val refusal: GPUPreparedAtlasLowering.Refused) : ResolvedAtlasPaint
}
