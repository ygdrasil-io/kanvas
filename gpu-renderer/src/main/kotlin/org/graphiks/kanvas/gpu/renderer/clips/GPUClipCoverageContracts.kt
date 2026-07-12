package org.graphiks.kanvas.gpu.renderer.clips

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
) {
    val values: List<Float> = values.toList()

    init {
        require(vertexCount >= 0) { "GPUClipCoverageElement.vertexCount must be non-negative" }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is GPUClipCoverageElement &&
            operation == other.operation &&
            kind == other.kind &&
            values == other.values &&
            vertexCount == other.vertexCount

    override fun hashCode(): Int =
        listOf(operation, kind, values, vertexCount).hashCode()
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
    val fillRule: GPUClipFillRule,
    val inverseFill: Boolean,
    val antiAlias: Boolean,
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
        append('|').append(fillRule.name)
        append('|').append(inverseFill)
        append('|').append(antiAlias)
        elements.forEach { element ->
            append('|').append(element.operation.name)
            append('|').append(element.kind.name)
            append('|').append(element.vertexCount)
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
            fillRule == other.fillRule &&
            inverseFill == other.inverseFill &&
            antiAlias == other.antiAlias

    override fun hashCode(): Int =
        listOf(targetWidth, targetHeight, elements, fillRule, inverseFill, antiAlias).hashCode()
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
