package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.imageLocalMatrixRefusalReasonOrNull
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.recording.buildPreparedImageGeometry
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import kotlin.math.max
import kotlin.math.min

/**
 * Physical prepared-image authority attached to the logical normalized image command.
 *
 * [NormalizedDrawCommand.DrawImageRect] keeps the analysis-facing decoded-pixel contract
 * (`RGBA8Unorm`, sRGB profile, premultiplied alpha). The artifact below separately owns the
 * physical upload contract: straight encoded sRGB in `RGBA8UnormSrgb` for color images, or
 * linear `RGBA8Unorm` coverage for A8 images.
 */
data class GPUPreparedImageDrawFacts(
    val artifact: GPUPreparedImageUploadArtifact,
    val sampling: GPUPreparedImageSampling,
    val geometry: GPUPreparedImageGeometry,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>? = null,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend? = null,
)

sealed interface GPUPreparedDrawImageLowering {
    data class Ready(val command: GPUFramePathVisualCommand) : GPUPreparedDrawImageLowering
    data class Refused(val code: String, val facts: Map<String, String>) :
        GPUPreparedDrawImageLowering
}

internal object GPUPreparedDrawImageLowerer {
    fun lower(
        operation: DisplayOp.DrawImage,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        preparedArtifact: GPUPreparedImageUploadArtifact? = null,
        imageLocalMatrix: List<Float> = listOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        ),
        allowShaderSourceOutsideImage: Boolean = false,
        sourceOperation: String = "drawImage",
    ): GPUPreparedDrawImageLowering {
        val image = operation.image
        operation.paint.unsupportedPreparedImagePaintEffectOrNull()?.let { paintField ->
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                preparedImagePaintEffectRefusalFacts(paintField),
            )
        }
        val blendMode = when (val blender = operation.paint?.blender) {
            null -> operation.paint?.blendMode ?: BlendMode.SRC_OVER
            is Blender.Mode -> blender.mode
            is Blender.Arithmetic -> return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                mapOf(
                    "reason" to "unsupported_blender",
                    "blenderKind" to "Arithmetic",
                ),
            )
        }
        if (blendMode != BlendMode.SRC_OVER) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                mapOf(
                    "sourceId" to image.sourceId,
                    "blendMode" to blendMode.name,
                    "supportedBlendMode" to BlendMode.SRC_OVER.name,
                ),
            )
        }

        if (image.pixels == null && image.width > 0 && image.height > 0) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.PIXELS_MISSING,
                mapOf(
                    "imageWidth" to image.width.toString(),
                    "imageHeight" to image.height.toString(),
                    "sourceId" to image.sourceId,
                ),
            )
        }

        val requestedImageShader = operation.paint?.shader as? Shader.Image
        if (requestedImageShader != null &&
            (requestedImageShader.tileModeX != TileMode.CLAMP ||
                requestedImageShader.tileModeY != TileMode.CLAMP)
        ) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.TILE_MODE,
                mapOf(
                    "sourceId" to image.sourceId,
                    "tileModeX" to requestedImageShader.tileModeX.name,
                    "tileModeY" to requestedImageShader.tileModeY.name,
                ),
            )
        }
        val requestedSampling = requestedImageShader?.sampling
        val boundedW28 = config.preparedImageRouteCapability ==
            GPUPreparedImageRouteCapability.BoundedNearest1To1
        val sampling = when (requestedSampling) {
            SamplingOptions.NEAREST -> GPUPreparedImageSampling.Nearest
            SamplingOptions.LINEAR -> if (boundedW28) {
                return GPUPreparedDrawImageLowering.Refused(
                    GPUPreparedImageRefusalCodes.SAMPLING_FILTER,
                    mapOf("sourceId" to image.sourceId, "sampling" to "linear", "supportedSampling" to "nearest"),
                )
            } else GPUPreparedImageSampling.Linear
            null -> GPUPreparedImageSampling.Nearest
            is SamplingOptions.Cubic -> return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
                mapOf("sourceId" to image.sourceId),
            )
        }

        if (operation.transform.hasPerspective()) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                mapOf(
                    "sourceId" to image.sourceId,
                    "persp0" to operation.transform.persp0.toString(),
                    "persp1" to operation.transform.persp1.toString(),
                    "persp2" to operation.transform.persp2.toString(),
                ),
            )
        }

        val determinant =
            operation.transform.sx * operation.transform.sy -
                operation.transform.kx * operation.transform.ky
        if (determinant == 0f || !determinant.isFinite()) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                mapOf(
                    "sourceId" to image.sourceId,
                    "transformClass" to "singular-affine",
                    "sx" to operation.transform.sx.toString(),
                    "kx" to operation.transform.kx.toString(),
                    "ky" to operation.transform.ky.toString(),
                    "sy" to operation.transform.sy.toString(),
                ),
            )
        }

        val transform = operation.transform
        val integerTranslation =
            transform.sx == 1f && transform.sy == 1f && transform.kx == 0f && transform.ky == 0f &&
                transform.tx.isFinite() && transform.ty.isFinite() &&
                transform.tx == transform.tx.toInt().toFloat() &&
                transform.ty == transform.ty.toInt().toFloat()
        if (!integerTranslation) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.AFFINE_SAMPLING,
                mapOf(
                    "sourceId" to image.sourceId,
                    "supportedTransform" to "identity_or_integer_translation",
                    "sx" to transform.sx.toString(),
                    "kx" to transform.kx.toString(),
                    "ky" to transform.ky.toString(),
                    "sy" to transform.sy.toString(),
                    "tx" to transform.tx.toString(),
                    "ty" to transform.ty.toString(),
                ),
            )
        }

        val dst = operation.dst
        val src = operation.src

        // W28's distinct capability admits one complete immutable bitmap copied at native
        // pixel resolution. The generic native route intentionally retains its established
        // crop, scaling, fractional local-matrix, and grid capabilities.
        val sourceIsWholeImage =
            src.left == 0f && src.top == 0f &&
                src.right == image.width.toFloat() && src.bottom == image.height.toFloat()
        val destinationIsInteger =
            listOf(dst.left, dst.top, dst.right, dst.bottom).all { coordinate ->
                coordinate.isFinite() && coordinate == coordinate.toInt().toFloat()
            }
        val destinationIsNativeSize =
            dst.right - dst.left == image.width.toFloat() &&
                dst.bottom - dst.top == image.height.toFloat()
        if (boundedW28 && (!sourceIsWholeImage || !destinationIsInteger || !destinationIsNativeSize)) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.RECT_GEOMETRY,
                mapOf(
                    "sourceId" to image.sourceId,
                    "supportedSource" to "whole_image",
                    "supportedDestination" to "integer_1_to_1",
                    "src" to "${src.left},${src.top},${src.right},${src.bottom}",
                    "dst" to "${dst.left},${dst.top},${dst.right},${dst.bottom}",
                    "imageSize" to "${image.width}x${image.height}",
                ),
            )
        }

        val dx0 = dst.left
        val dy0 = dst.top
        val dx1 = dst.right
        val dy1 = dst.bottom

        val corners = listOf(
            Point2F32(dx0, dy0),
            Point2F32(dx1, dy0),
            Point2F32(dx1, dy1),
            Point2F32(dx0, dy1),
        )
        val transformedCorners = corners.map { operation.transform.transform(it) }

        val imageW = image.width.toFloat()
        val imageH = image.height.toFloat()

        val sx0 = if (allowShaderSourceOutsideImage) src.left else max(0f, min(src.left, imageW))
        val sy0 = if (allowShaderSourceOutsideImage) src.top else max(0f, min(src.top, imageH))
        val sx1 = if (allowShaderSourceOutsideImage) src.right else max(0f, min(src.right, imageW))
        val sy1 = if (allowShaderSourceOutsideImage) src.bottom else max(0f, min(src.bottom, imageH))

        val uvs = listOf(
            GPUPreparedImageVertex(0f, 0f, sx0 / imageW, sy0 / imageH),
            GPUPreparedImageVertex(0f, 0f, sx1 / imageW, sy0 / imageH),
            GPUPreparedImageVertex(0f, 0f, sx1 / imageW, sy1 / imageH),
            GPUPreparedImageVertex(0f, 0f, sx0 / imageW, sy1 / imageH),
        )

        val vertices = transformedCorners.zip(uvs).map { (pt, uv) ->
            GPUPreparedImageVertex(
                x = pt.x,
                y = pt.y,
                u = uv.u,
                v = uv.v,
            )
        }

        val hasSkew = operation.transform.kx != 0f || operation.transform.ky != 0f
        val geometryClass = if (hasSkew) GPUPreparedImageGeometryClass.Quad
        else GPUPreparedImageGeometryClass.Rect

        val geometry = buildPreparedImageGeometry(
            geometryClass = geometryClass,
            vertices = vertices,
        )

        val artifact = preparedArtifact ?: when (val prepared = GPUPreparedSurfaceImageSource.prepare(image)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return GPUPreparedDrawImageLowering.Refused(
                prepared.code,
                prepared.facts + mapOf("sourceId" to image.sourceId),
            )
        }
        val clipPlans = when (
            val classified = classifyPreparedDrawImageClip(
                operation.clip,
                GPUPreparedImageLoweringContext(
                    provenance = provenance,
                    target = target,
                    config = config,
                    capabilities = capabilities,
                ),
            )
        ) {
            is GPUPreparedDrawImageClipClassification.Ready -> classified
            is GPUPreparedDrawImageClipClassification.Refused ->
                return GPUPreparedDrawImageLowering.Refused(classified.code, classified.facts)
        }

        val samplingFilterMode = when (sampling) {
            GPUPreparedImageSampling.Nearest -> "nearest"
            GPUPreparedImageSampling.Linear -> "linear"
        }
        val alphaOnly = image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8
        val tintR = if (alphaOnly) operation.paint?.color?.r ?: 0f else 1f
        val tintG = if (alphaOnly) operation.paint?.color?.g ?: 0f else 1f
        val tintB = if (alphaOnly) operation.paint?.color?.b ?: 0f else 1f
        val tintA = operation.paint?.color?.a ?: 1f
        val tintPremultipliedRgba = listOf(
            (tintR * tintA).coerceIn(0f, 1f),
            (tintG * tintA).coerceIn(0f, 1f),
            (tintB * tintA).coerceIn(0f, 1f),
            tintA.coerceIn(0f, 1f),
        )

        // Prepared draws retain pixels only in the immutable artifact. The generic material
        // descriptor stays byte-free so an expanded grid cannot retain one full copy per cell.
        val material = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = image.sourceId,
            imageWidth = image.width,
            imageHeight = image.height,
            samplingFilterMode = samplingFilterMode,
            alphaOnly = alphaOnly,
            tintR = tintR,
            tintG = tintG,
            tintB = tintB,
            tintA = tintA,
            localMatrix = imageLocalMatrix,
        )

        val gpuSrc = GPURect(sx0, sy0, sx1, sy1)
        val gpuDst = GPURect(
            dst.left + transform.tx,
            dst.top + transform.ty,
            dst.right + transform.tx,
            dst.bottom + transform.ty,
        )

        val minX = transformedCorners.minOf { it.x }
        val minY = transformedCorners.minOf { it.y }
        val maxX = transformedCorners.maxOf { it.x }
        val maxY = transformedCorners.maxOf { it.y }
        val bounds = GPUBounds(minX, minY, maxX, maxY)

        val blendFacts = blendMode.toGpuBlendFacts()

        val blendPlan = GPUBlendPlanner().plan(
            GPUBlendSpecializationRequest(
                mode = blendFacts.mode,
                coverage = GPUCoverageConsumption.FullOrScissor,
                sourceAlpha = GPUSourceAlphaClassification.Translucent,
                target = GPUTargetBlendFacts(
                    formatClass = "rgba8unorm-srgb",
                    clampsNormalizedColorWrites = true,
                    premultipliedAlpha = true,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
            ),
        )

        val normalized = NormalizedDrawCommand.DrawImageRect(
            commandId = commandId,
            imageSourceId = image.sourceId,
            src = gpuSrc,
            dst = gpuDst,
            // Integer translations are folded into dst above. The native image dispatch
            // consequently receives only an identity transform, which is the contract it
            // actually materializes rather than a deferred recorder refusal.
            transform = GPUTransformFacts.identity(),
            clip = operation.clip.toGPUClipFacts(target),
            layer = GPULayerFacts.root(target),
            material = material,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = GPUCommandSource(
                adapter = "kanvas-surface",
                operation = sourceOperation,
                frameProvenance = provenance,
            ),
            blend = blendFacts,
            samplingFilterMode = samplingFilterMode,
            pixelsWidth = artifact.width,
            pixelsHeight = artifact.height,
            pixelsFormat = "RGBA8Unorm",
            pixelsRowBytes = artifact.pixelLayout.normalizedRgba8RowBytes,
            pixelsAlphaType = "Premul",
            pixelsGeneration = artifact.sourceGeneration,
            pixelsContentHash = artifact.contentHash,
            pixelsProvenance = "prepared-surface-artifact",
        )

        val visual = GPUFramePathVisualCommand(
            normalized = normalized,
            targetSpaceBounds = bounds,
            geometryCoverage = GPUCoverageConsumption.FullOrScissor,
            clipCoverage = clipPlans.coverage,
            clipExecutionPlan = clipPlans.execution,
            blendPlan = blendPlan,
            provenance = provenance,
            preparedImage = GPUPreparedImageDrawFacts(
                artifact = artifact,
                sampling = sampling,
                geometry = geometry,
                tintPremultipliedRgba = tintPremultipliedRgba,
            ),
        )

        return GPUPreparedDrawImageLowering.Ready(visual)
    }

    /**
     * Lowers the deliberately small image-shader rectangle subset through the
     * existing prepared-image route. The bounded local matrix is represented
     * by the source UV rectangle; texture sampling remains clamp/nearest or
     * clamp/linear in the native image pipeline.
     */
    fun lowerImageShaderRect(
        operation: DisplayOp.DrawRect,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
    ): GPUPreparedDrawImageLowering? {
        val imageShader = operation.paint.shader.findBaseImageShaderOrNull() ?: return null
        val mapped = operation.paint.toPreparedMaterialMapping().descriptor
        val material = mapped as? GPUMaterialDescriptor.ImageDraw ?: return when (mapped) {
            is GPUMaterialDescriptor.Unsupported -> GPUPreparedDrawImageLowering.Refused(
                mapped.reason.diagnosticCode,
                mapOf(
                    "materialKind" to mapped.originalKind.name,
                    "reason" to mapped.reason.name,
                ),
            )
            else -> GPUPreparedDrawImageLowering.Refused(
                "unsupported.material.source_unimplemented",
                mapOf("materialKind" to mapped.kind.name),
            )
        }
        material.imageLocalMatrixRefusalReasonOrNull()?.let { reason ->
            return GPUPreparedDrawImageLowering.Refused(
                reason.diagnosticCode,
                mapOf("materialKind" to material.kind.name, "reason" to reason.name),
            )
        }
        if (imageShader.image.sourceId != material.imageSourceId) {
            return GPUPreparedDrawImageLowering.Refused(
                "invalid.surface.prepared.image-shader-source",
                mapOf("shaderSourceId" to imageShader.image.sourceId, "materialSourceId" to material.imageSourceId),
            )
        }
        val matrix = material.localMatrix
        val shaderSrc = GPURect(
            left = matrix[0] * operation.rect.left + matrix[1] * operation.rect.top + matrix[2],
            top = matrix[3] * operation.rect.left + matrix[4] * operation.rect.top + matrix[5],
            right = matrix[0] * operation.rect.right + matrix[1] * operation.rect.bottom + matrix[2],
            bottom = matrix[3] * operation.rect.right + matrix[4] * operation.rect.bottom + matrix[5],
        )
        return lower(
            operation = DisplayOp.DrawImage(
                image = imageShader.image,
                src = org.graphiks.math.geometry.RectF32(
                    shaderSrc.left, shaderSrc.top, shaderSrc.right, shaderSrc.bottom,
                ),
                dst = operation.rect,
                paint = operation.paint.copy(shader = imageShader),
                transform = operation.transform,
                clip = operation.clip,
            ),
            commandId = commandId,
            paintOrder = paintOrder,
            provenance = provenance,
            target = target,
            config = config,
            capabilities = capabilities,
            imageLocalMatrix = material.localMatrix,
            allowShaderSourceOutsideImage = true,
            sourceOperation = "drawRect.imageShader",
        )
    }

    /**
     * GmCanvas materializes an affine rectangle transform as a four-edge path.
     * Keep this recognition intentionally narrow: only an identity-transform,
     * filled axis-aligned rectangle with an image shader is reconstituted.
     */
    fun lowerImageShaderRectPath(
        operation: DisplayOp.DrawPath,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
    ): GPUPreparedDrawImageLowering? {
        if (!operation.transform.isIdentity || operation.paint.isStroke()) return null
        val deviceRect = org.graphiks.math.geometry.RectF32(0f, 0f, 0f, 0f)
        if (!operation.path.isRect(deviceRect)) return null
        val localRect = org.graphiks.math.geometry.RectF32(
            0f, 0f, deviceRect.width(), deviceRect.height(),
        )
        return lowerImageShaderRect(
            operation = DisplayOp.DrawRect(
                rect = localRect,
                paint = operation.paint,
                transform = Matrix3x3F32.translation(deviceRect.left, deviceRect.top),
                clip = operation.clip,
            ),
            commandId = commandId,
            paintOrder = paintOrder,
            provenance = provenance,
            target = target,
            config = config,
            capabilities = capabilities,
        )
    }
}

internal fun Shader?.findBaseImageShaderOrNull(): Shader.Image? = when (this) {
    is Shader.Image -> this
    is Shader.WithLocalMatrix -> shader.findBaseImageShaderOrNull()
    else -> null
}

internal fun Paint?.unsupportedPreparedImagePaintEffectOrNull(): String? = when {
    this?.colorFilter != null -> "colorFilter"
    this?.maskFilter != null -> "maskFilter"
    this?.imageFilter != null -> "imageFilter"
    else -> null
}

internal fun preparedImagePaintEffectRefusalFacts(paintField: String): Map<String, String> =
    mapOf(
        "reason" to "unsupported_paint_effect",
        "paintField" to paintField,
    )

private sealed interface GPUPreparedDrawImageClipClassification {
    data class Ready(
        val coverage: GPUClipCoveragePlan,
        val execution: GPUClipExecutionPlan,
    ) : GPUPreparedDrawImageClipClassification

    data class Refused(
        val code: String = "unsupported.surface.prepared.image-clip",
        val facts: Map<String, String>,
    ) : GPUPreparedDrawImageClipClassification
}

private fun classifyPreparedDrawImageClip(
    clip: ClipStack,
    context: GPUPreparedImageLoweringContext,
): GPUPreparedDrawImageClipClassification {
    if (clip == ClipStack.WideOpen) {
        return GPUPreparedDrawImageClipClassification.Ready(
            coverage = GPUClipCoveragePlan.NoClip,
            execution = GPUClipExecutionPlan.NoClip,
        )
    }
    if (clip is ClipStack.Complex) {
        return refusedPreparedDrawImageClip("unsupported_clip_plan")
    }
    val request = clip.toGPUClipFacts(context.target).coverageRequest
        ?: return refusedPreparedDrawImageClip("missing_clip_request")
    val maxTextureDimension2D = context.capabilities.limits?.maxTextureDimension2D
        ?: maxOf(context.target.width, context.target.height).toLong()
    val coverage = GPUClipCoveragePlanner.plan(
        request = request,
        config = context.config,
        maxTextureDimension2D = maxTextureDimension2D.coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt(),
    )
    if (coverage !is GPUClipCoveragePlan.Scissor ||
        !context.capabilities.supportsPreparedDrawImageScissor()
    ) {
        return refusedPreparedDrawImageClip("unsupported_clip_plan")
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
        return refusedPreparedDrawImageClip("invalid_scissor")
    }
    val left = coverage.bounds.left.toInt().coerceIn(0, context.target.width)
    val top = coverage.bounds.top.toInt().coerceIn(0, context.target.height)
    val right = coverage.bounds.right.toInt().coerceIn(0, context.target.width)
    val bottom = coverage.bounds.bottom.toInt().coerceIn(0, context.target.height)
    if (right <= left || bottom <= top) {
        return refusedPreparedDrawImageClip("empty_scissor")
    }
    return GPUPreparedDrawImageClipClassification.Ready(
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

private fun refusedPreparedDrawImageClip(
    reason: String,
): GPUPreparedDrawImageClipClassification.Refused =
    GPUPreparedDrawImageClipClassification.Refused(
        facts = mapOf("reason" to reason),
    )

private fun GPUCapabilities.supportsPreparedDrawImageScissor(): Boolean =
    knownUnsupportedFacts.none { fact -> fact.name == SCISSOR_NATIVE } &&
        facts.any { fact ->
            fact.name == SCISSOR_NATIVE &&
                fact.value == "supported" &&
                fact.affectsValidity
        }
