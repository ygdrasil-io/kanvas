package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
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
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.recording.buildPreparedImageGeometry
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.isAffine
import org.graphiks.kanvas.types.r
import kotlin.math.max
import kotlin.math.min

data class GPUPreparedImageDrawFacts(
    val artifact: GPUPreparedImageUploadArtifact,
    val sampling: GPUPreparedImageSampling,
    val geometry: GPUPreparedImageGeometry,
    val tintPremultipliedRgba: List<Float>,
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
    ): GPUPreparedDrawImageLowering {
        val image = operation.image

        val pixels = image.pixels?.copyOf()
        if (pixels == null && image.width > 0 && image.height > 0) {
            return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.PIXELS_MISSING,
                mapOf(
                    "imageWidth" to image.width.toString(),
                    "imageHeight" to image.height.toString(),
                    "sourceId" to image.sourceId,
                ),
            )
        }

        val requestedSampling = operation.paint?.let { p ->
            val sh = p.shader
            (sh as? org.graphiks.kanvas.paint.Shader.Image)?.sampling
        }
        val sampling = when (requestedSampling) {
            SamplingOptions.NEAREST -> GPUPreparedImageSampling.Nearest
            SamplingOptions.LINEAR,
            null,
            -> GPUPreparedImageSampling.Linear
            is SamplingOptions.Cubic -> return GPUPreparedDrawImageLowering.Refused(
                GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
                mapOf("sourceId" to image.sourceId),
            )
        }

        if (!operation.transform.isAffine()) {
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
            operation.transform.scaleX * operation.transform.scaleY -
                operation.transform.skewX * operation.transform.skewY
        if (determinant == 0f || !determinant.isFinite()) {
            return GPUPreparedDrawImageLowering.Refused(
                "unsupported.transform.affine_singular",
                mapOf(
                    "sourceId" to image.sourceId,
                    "scaleX" to operation.transform.scaleX.toString(),
                    "skewX" to operation.transform.skewX.toString(),
                    "skewY" to operation.transform.skewY.toString(),
                    "scaleY" to operation.transform.scaleY.toString(),
                ),
            )
        }

        val dst = operation.dst
        val src = operation.src

        val dx0 = dst.left.toFloat()
        val dy0 = dst.top.toFloat()
        val dx1 = dst.right.toFloat()
        val dy1 = dst.bottom.toFloat()

        val corners = listOf(
            Point(dx0, dy0),
            Point(dx1, dy0),
            Point(dx1, dy1),
            Point(dx0, dy1),
        )
        val transformedCorners = corners.map { operation.transform * it }

        val imageW = image.width.toFloat()
        val imageH = image.height.toFloat()

        val sx0 = max(0f, min(src.left.toFloat(), imageW))
        val sy0 = max(0f, min(src.top.toFloat(), imageH))
        val sx1 = max(0f, min(src.right.toFloat(), imageW))
        val sy1 = max(0f, min(src.bottom.toFloat(), imageH))

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

        val hasSkew = operation.transform.skewX != 0f || operation.transform.skewY != 0f
        val geometryClass = if (hasSkew) GPUPreparedImageGeometryClass.Quad
        else GPUPreparedImageGeometryClass.Rect

        val geometry = buildPreparedImageGeometry(
            geometryClass = geometryClass,
            vertices = vertices,
        )

        val artifact = when (val prepared = GPUPreparedSurfaceImageSource.prepare(image)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return GPUPreparedDrawImageLowering.Refused(
                prepared.code,
                prepared.facts + mapOf("sourceId" to image.sourceId),
            )
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
        )

        val gpuSrc = GPURect(sx0, sy0, sx1, sy1)
        val gpuDst = GPURect(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat())

        val minX = transformedCorners.minOf { it.x }
        val minY = transformedCorners.minOf { it.y }
        val maxX = transformedCorners.maxOf { it.x }
        val maxY = transformedCorners.maxOf { it.y }
        val bounds = GPUBounds(minX, minY, maxX, maxY)

        val blendMode = operation.paint?.blendMode ?: BlendMode.SRC_OVER
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
            transform = GPUTransformFacts.affine(
                scaleX = operation.transform.scaleX,
                skewX = operation.transform.skewX,
                skewY = operation.transform.skewY,
                scaleY = operation.transform.scaleY,
                translateX = operation.transform.transX,
                translateY = operation.transform.transY,
            ),
            clip = operation.clip.toGPUClipFacts(target),
            layer = GPULayerFacts.root(target),
            material = material,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawImage"),
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

        val clipCoverage = if (normalized.clip.coverageRequest == null) {
            GPUClipCoveragePlan.NoClip
        } else {
            GPUClipCoveragePlan.Refused(
                "unsupported.clip.prepared_image_execution_unclassified",
            )
        }
        val clipExecution = if (clipCoverage == GPUClipCoveragePlan.NoClip) {
            GPUClipExecutionPlan.NoClip
        } else {
            GPUClipExecutionPlan.Refused(
                code = "unsupported.clip.prepared_image_execution_unclassified",
                message = "Prepared image clip execution must be classified before recording.",
            )
        }

        val visual = GPUFramePathVisualCommand(
            normalized = normalized,
            targetSpaceBounds = bounds,
            geometryCoverage = GPUCoverageConsumption.FullOrScissor,
            clipCoverage = clipCoverage,
            clipExecutionPlan = clipExecution,
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
}
