package org.graphiks.kanvas.gpu.renderer.clips

import org.graphiks.kanvas.gpu.renderer.collections.immutableList

/** Conservative scalar bounds used by clip transport and planning contracts. */
data class GPUBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Boolean operation applied by an ordered clip coverage element. */
enum class GPUClipCoverageOperation {
    Intersect,
    Difference,
}

/** Geometry encoding carried by a clip coverage element. */
enum class GPUClipCoverageElementKind {
    Rect,
    RRect,
    Path,
}

/** Fill rule used to evaluate a path-like clip coverage element. */
enum class GPUClipFillRule {
    Winding,
    EvenOdd,
}

/**
 * Geometry-neutral, value-only clip element transport.
 *
 * [values] contains the canonical geometry payload for [kind]. It deliberately
 * carries scalar data rather than a mutable shape object or object identity.
 */
class GPUClipCoverageElement(
    val operation: GPUClipCoverageOperation,
    val kind: GPUClipCoverageElementKind,
    values: List<Float>,
    val vertexCount: Int,
    val antiAlias: Boolean,
    val fillRule: GPUClipFillRule,
    val inverseFill: Boolean,
) {
    val values: List<Float> = immutableList(values)

    init {
        require(vertexCount >= 0) { "GPUClipCoverageElement.vertexCount must be non-negative" }
        when (kind) {
            GPUClipCoverageElementKind.Rect -> {
                require(values.size == 4) { "Rect clips require [left, top, right, bottom]" }
                require(vertexCount == 0) { "Rect clips must not carry path vertices" }
                require(fillRule == GPUClipFillRule.Winding && !inverseFill) {
                    "Rect clips are winding and non-inverse"
                }
            }
            GPUClipCoverageElementKind.RRect -> {
                require(values.size == 12) {
                    "RRect clips require bounds followed by four corner radii"
                }
                require(vertexCount == 0) { "RRect clips must not carry path vertices" }
                require(fillRule == GPUClipFillRule.Winding && !inverseFill) {
                    "RRect clips are winding and non-inverse"
                }
            }
            GPUClipCoverageElementKind.Path -> validatePathPayload(values, vertexCount)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is GPUClipCoverageElement &&
            operation == other.operation &&
            kind == other.kind &&
            values == other.values &&
            vertexCount == other.vertexCount &&
            antiAlias == other.antiAlias &&
            fillRule == other.fillRule &&
            inverseFill == other.inverseFill

    override fun hashCode(): Int =
        listOf(operation, kind, values, vertexCount, antiAlias, fillRule, inverseFill).hashCode()

    private fun validatePathPayload(values: List<Float>, vertexCount: Int) {
        require(values.isNotEmpty()) { "Path clips require a contour count" }
        val contourCount = values.first().toCanonicalIndex("Path contour count")
        require((contourCount == 0) == (vertexCount == 0)) {
            "Path clips require contours if and only if they carry vertices"
        }
        val coordinateStart = 1 + contourCount
        require(values.size == coordinateStart + vertexCount * 2) {
            "Path clips require contour starts followed by exactly two values per vertex"
        }
        var previousStart = -1
        for (index in 0 until contourCount) {
            val start = values[1 + index].toCanonicalIndex("Path contour start")
            require(start in 0 until vertexCount && start > previousStart && (index != 0 || start == 0)) {
                "Path contour starts must be strictly increasing vertex indices"
            }
            previousStart = start
        }
    }

    private fun Float.toCanonicalIndex(label: String): Int {
        require(isFinite() && this >= 0f && this <= Int.MAX_VALUE.toFloat() && this == toInt().toFloat()) {
            "$label must be a finite non-negative integer"
        }
        return toInt()
    }
}

/**
 * Immutable transport submitted by command normalization to GPU clip planning.
 *
 * This request intentionally has no draw-bounds field. Its [contentKey] is a
 * canonical serialization of only its scalar and ordered geometry values.
 */
class GPUClipCoverageRequest(
    val targetWidth: Int,
    val targetHeight: Int,
    elements: List<GPUClipCoverageElement>,
    /** True only for a captured device rectangle eligible for a native scissor. */
    val scissorEligible: Boolean = false,
) {
    val elements: List<GPUClipCoverageElement> = elements.toList()

    init {
        require(targetWidth > 0) { "GPUClipCoverageRequest.targetWidth must be positive" }
        require(targetHeight > 0) { "GPUClipCoverageRequest.targetHeight must be positive" }
    }

    /** Stable cache key whose serialization cannot depend on object identity. */
    val contentKey: String = buildString {
        append("gpu-clip-coverage-v1")
        append('|').append(targetWidth)
        append('|').append(targetHeight)
        append('|').append(scissorEligible)
        elements.forEach { element ->
            append('|').append(element.operation.name)
            append('|').append(element.kind.name)
            append('|').append(element.vertexCount)
            append('|').append(element.antiAlias)
            append('|').append(element.fillRule.name)
            append('|').append(element.inverseFill)
            element.values.forEach { value ->
                append('|').append(value.toRawBits().toUInt().toString(16))
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is GPUClipCoverageRequest &&
            targetWidth == other.targetWidth &&
            targetHeight == other.targetHeight &&
            elements == other.elements &&
            scissorEligible == other.scissorEligible

    override fun hashCode(): Int =
        listOf(targetWidth, targetHeight, elements, scissorEligible).hashCode()
}

/** Stable refusal reasons emitted by both clip planning and clip routes. */
object GPUClipCoverageRefusalCodes {
    const val NONFINITE_INPUT = "unsupported.clip.nonfinite_input"
    const val TEXTURE_LIMIT = "unsupported.clip.texture_limit"
    const val INTERMEDIATE_BUDGET = "unsupported.clip.intermediate_budget"
    const val VERTEX_BUDGET = "unsupported.clip.vertex_budget"
}

/** Backend-neutral clip coverage plan consumed by later WebGPU execution work. */
sealed interface GPUClipCoveragePlan {
    data object NoClip : GPUClipCoveragePlan

    data class Scissor(val bounds: GPUBounds) : GPUClipCoveragePlan

    /** Two to four ordered simple intersections that need no texture intermediate. */
    class AnalyticIntersection(
        elements: List<GPUClipCoverageElement>,
    ) : GPUClipCoveragePlan {
        val elements: List<GPUClipCoverageElement> = immutableList(elements)

        init {
            require(elements.size in 2..4) { "AnalyticIntersection requires two to four elements" }
            require(elements.all(GPUClipCoverageElement::isSimpleAnalyticIntersection)) {
                "AnalyticIntersection accepts only finite intersect rects and simple rrects"
            }
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is AnalyticIntersection && elements == other.elements

        override fun hashCode(): Int = elements.hashCode()

        override fun toString(): String = "AnalyticIntersection(elements=$elements)"
    }

    data class Mask(
        val contentKey: String,
        val width: Int,
        val height: Int,
        val sampleCount: Int,
        val resolvedBytes: Long,
        val requiredBytes: Long,
        val elements: List<GPUClipCoverageElement>,
    ) : GPUClipCoveragePlan

    data class Refused(val code: String) : GPUClipCoveragePlan
}

fun GPUClipCoverageElement.isSimpleAnalyticIntersection(): Boolean {
    if (
        operation != GPUClipCoverageOperation.Intersect ||
        inverseFill ||
        values.any { !it.isFinite() }
    ) {
        return false
    }
    if (kind == GPUClipCoverageElementKind.Path) return false
    val width = values[2] - values[0]
    val height = values[3] - values[1]
    if (width <= 0f || height <= 0f) return false
    return when (kind) {
        GPUClipCoverageElementKind.Rect -> true
        GPUClipCoverageElementKind.Path -> false
        GPUClipCoverageElementKind.RRect -> {
            val radii = values.subList(4, 12)
            val firstX = radii[0]
            val firstY = radii[1]
            radii.chunked(2).all { pair -> pair[0] == firstX && pair[1] == firstY } &&
                firstX >= 0f && firstY >= 0f && firstX * 2f <= width && firstY * 2f <= height
        }
    }
}
