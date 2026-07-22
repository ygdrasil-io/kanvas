package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.ConcurrentHashMap
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.isSimpleAnalyticIntersection
import org.graphiks.kanvas.surface.RenderConfig

/** Chooses the immutable coverage strategy before any GPU intermediate allocation. */
object GPUClipCoveragePlanner {
    private const val RGBA8_BYTES_PER_PIXEL = 4L
    private const val DEPTH24_STENCIL8_BYTES_PER_PIXEL = 4L
    private const val AA_SAMPLE_COUNT = 4
    private const val NON_AA_SAMPLE_COUNT = 1

    fun plan(
        request: GPUClipCoverageRequest,
        config: RenderConfig,
        maxTextureDimension2D: Int,
    ): GPUClipCoveragePlan {
        preIntermediatePlanOrNull(request, config, maxTextureDimension2D)?.let { return it }
        return maskPlan(request, config, enforceIntermediateBudget = true)
    }

    /** Frame-only route that recognizes bounded analytic execution before texture budgeting. */
    fun planForFrameRoute(
        request: GPUClipCoverageRequest,
        config: RenderConfig,
        maxTextureDimension2D: Int,
    ): GPUClipCoveragePlan {
        preIntermediatePlanOrNull(request, config, maxTextureDimension2D)?.let { return it }
        val simpleAnalytic = request.elements.all(GPUClipCoverageElement::isSimpleAnalyticIntersection)
        if (request.elements.size in 2..4 && simpleAnalytic) {
            return GPUClipCoveragePlan.AnalyticIntersection(request.elements)
        }
        return maskPlan(
            request,
            config,
            enforceIntermediateBudget = !(request.elements.size == 1 && simpleAnalytic),
        )
    }

    private fun preIntermediatePlanOrNull(
        request: GPUClipCoverageRequest,
        config: RenderConfig,
        maxTextureDimension2D: Int,
    ): GPUClipCoveragePlan? {
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
        return scissorFor(request)
    }

    private fun maskPlan(
        request: GPUClipCoverageRequest,
        config: RenderConfig,
        enforceIntermediateBudget: Boolean,
    ): GPUClipCoveragePlan {
        val sampleCount = if (request.elements.any(GPUClipCoverageElement::antiAlias)) {
            AA_SAMPLE_COUNT
        } else {
            NON_AA_SAMPLE_COUNT
        }
        val intermediateBytes = intermediateBytes(
            width = request.targetWidth,
            height = request.targetHeight,
            sampleCount = sampleCount,
        ) ?: return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.INTERMEDIATE_BUDGET)
        if (enforceIntermediateBudget && intermediateBytes.required > config.maxClipIntermediateBytes.toLong()) {
            return GPUClipCoveragePlan.Refused(GPUClipCoverageRefusalCodes.INTERMEDIATE_BUDGET)
        }
        return GPUClipCoveragePlan.Mask(
            contentKey = request.contentKey,
            width = request.targetWidth,
            height = request.targetHeight,
            sampleCount = sampleCount,
            resolvedBytes = intermediateBytes.resolved,
            requiredBytes = intermediateBytes.required,
            elements = request.elements.toList(),
        )
    }

    private fun intermediateBytes(width: Int, height: Int, sampleCount: Int): IntermediateBytes? = try {
        val pixelCount = Math.multiplyExact(width.toLong(), height.toLong())
        val resolved = Math.multiplyExact(pixelCount, RGBA8_BYTES_PER_PIXEL)
        val colorRender = Math.multiplyExact(resolved, sampleCount.toLong())
        val resolve = if (sampleCount == 1) 0L else resolved
        val depthStencil = Math.multiplyExact(
            Math.multiplyExact(pixelCount, DEPTH24_STENCIL8_BYTES_PER_PIXEL),
            sampleCount.toLong(),
        )
        IntermediateBytes(
            resolved = resolved,
            required = Math.addExact(Math.addExact(colorRender, resolve), depthStencil),
        )
    } catch (_: ArithmeticException) {
        null
    }

    private fun scissorFor(request: GPUClipCoverageRequest): GPUClipCoveragePlan.Scissor? {
        if (!request.scissorEligible || request.elements.size != 1) return null
        val element = request.elements.single()
        if (
            element.operation != GPUClipCoverageOperation.Intersect ||
            element.kind != GPUClipCoverageElementKind.Rect ||
            element.antiAlias ||
            element.inverseFill ||
            element.values.any { it != it.toInt().toFloat() }
        ) {
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

    private data class IntermediateBytes(
        val resolved: Long,
        val required: Long,
    )
}

/**
 * Legacy immutable-plan deduplication kept for callers that only inspect plans.
 * Materialized WebGPU masks must use [GPUClipCoverageFrameCache], whose scope
 * is one frame and whose releases are driven by the use prepass.
 */
class GPUClipCoverageCache {
    private val masks = ConcurrentHashMap<String, GPUClipCoveragePlan.Mask>()

    fun acquire(mask: GPUClipCoveragePlan.Mask): GPUClipCoveragePlan.Mask =
        masks.putIfAbsent(mask.contentKey, mask) ?: mask
}
