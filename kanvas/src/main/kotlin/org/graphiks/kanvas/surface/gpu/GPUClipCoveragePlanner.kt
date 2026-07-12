package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.ConcurrentHashMap
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.surface.RenderConfig

/** Chooses the immutable coverage strategy before any GPU intermediate allocation. */
object GPUClipCoveragePlanner {
    private const val BYTES_PER_PIXEL = 4L
    private const val AA_SAMPLE_COUNT = 4
    private const val NON_AA_SAMPLE_COUNT = 1

    fun plan(
        request: GPUClipCoverageRequest,
        config: RenderConfig,
        maxTextureDimension2D: Int,
    ): GPUClipCoveragePlan {
        require(maxTextureDimension2D > 0) { "maxTextureDimension2D must be positive" }

        if (request.elements.isEmpty()) return GPUClipCoveragePlan.NoClip
        if (request.elements.any { element -> element.values.any { !it.isFinite() } }) {
            return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.NONFINITE_INPUT)
        }
        if (request.targetWidth > maxTextureDimension2D || request.targetHeight > maxTextureDimension2D) {
            return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.TEXTURE_LIMIT)
        }

        val vertexCount = request.elements.sumOf { it.vertexCount.toLong() }
        if (vertexCount > config.maxPathVertices.toLong()) {
            return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.VERTEX_BUDGET)
        }

        scissorFor(request)?.let { return it }

        val sampleCount = if (request.antiAlias) AA_SAMPLE_COUNT else NON_AA_SAMPLE_COUNT
        val resolvedBytes = request.targetWidth.toLong() * request.targetHeight.toLong() * BYTES_PER_PIXEL
        val requiredBytes = resolvedBytes * sampleCount.toLong() + resolvedBytes
        if (requiredBytes > config.maxClipIntermediateBytes.toLong()) {
            return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.INTERMEDIATE_BUDGET)
        }

        return GPUClipCoveragePlan.Mask(
            contentKey = request.contentKey,
            width = request.targetWidth,
            height = request.targetHeight,
            sampleCount = sampleCount,
            resolvedBytes = resolvedBytes,
            requiredBytes = requiredBytes,
            elements = request.elements.toList(),
        )
    }

    private fun scissorFor(request: GPUClipCoverageRequest): GPUClipCoveragePlan.Scissor? {
        if (request.antiAlias || request.inverseFill || request.elements.size != 1) return null
        val element = request.elements.single()
        if (element.operation != GPUClipCoverageOperation.Intersect || element.kind != GPUClipCoverageElementKind.Rect) {
            return null
        }
        if (element.values.size != 4) return null

        return GPUClipCoveragePlan.Scissor(
            GPUBounds(
                left = element.values[0],
                top = element.values[1],
                right = element.values[2],
                bottom = element.values[3],
            ),
        )
    }
}

/** Thread-safe cache for immutable mask plans; WebGPU resources are added by later tasks. */
class GPUClipCoverageCache {
    private val masks = ConcurrentHashMap<String, GPUClipCoveragePlan.Mask>()

    fun acquire(mask: GPUClipCoveragePlan.Mask): GPUClipCoveragePlan.Mask =
        masks.putIfAbsent(mask.contentKey, mask) ?: mask
}
