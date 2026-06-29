package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.math.max
import kotlin.math.min

// -- Depth format --

enum class GPUDepthFormat {
    DEPTH32FLOAT,
    DEPTH24PLUS,
    DEPTH16UNORM,
}

// -- Depth source --

enum class GPUHiZDepthSource {
    ZPREPASS,
    PREVIOUS_FRAME,
    HYBRID,
}

// -- Pyramid level --

data class GPUHiZPyramidLevel(
    val index: Int,
    val width: Int,
    val height: Int,
    val depthData: FloatArray,
) {
    init {
        require(index >= 0) { "GPUHiZPyramidLevel.index must be non-negative" }
        require(width > 0) { "GPUHiZPyramidLevel.width must be positive" }
        require(height > 0) { "GPUHiZPyramidLevel.height must be positive" }
        require(depthData.size == width * height) {
            "GPUHiZPyramidLevel.depthData size ${depthData.size} must equal width*height ${width * height}"
        }
    }

    fun maxDepth(): Float = depthData.max()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUHiZPyramidLevel) return false
        return index == other.index &&
            width == other.width &&
            height == other.height &&
            depthData.contentEquals(other.depthData)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + depthData.contentHashCode()
        return result
    }
}

// -- Pyramid --

data class GPUHiZPyramid(
    val levels: List<GPUHiZPyramidLevel>,
    val baseWidth: Int,
    val baseHeight: Int,
    val sourceDepthFormat: GPUDepthFormat,
) {
    init {
        require(baseWidth >= 0) { "GPUHiZPyramid.baseWidth must be non-negative" }
        require(baseHeight >= 0) { "GPUHiZPyramid.baseHeight must be non-negative" }
        if (levels.isNotEmpty()) {
            require(levels[0].width == baseWidth && levels[0].height == baseHeight) {
                "GPUHiZPyramid base level dimensions mismatch"
            }
            for (i in 1 until levels.size) {
                val prev = levels[i - 1]
                val curr = levels[i]
                require(curr.width == max(prev.width / 2, 1)) {
                    "GPUHiZPyramid level $i width ${curr.width} must be max(${prev.width}/2, 1)"
                }
                require(curr.height == max(prev.height / 2, 1)) {
                    "GPUHiZPyramid level $i height ${curr.height} must be max(${prev.height}/2, 1)"
                }
            }
        }
    }

    fun dumpLines(): List<String> = listOf(
        "passes.hi-z-pyramid format=$sourceDepthFormat base=${baseWidth}x${baseHeight} " +
            "levels=${levels.size}",
    ) + levels.map { level ->
        "passes.hi-z-pyramid.level index=${level.index} dims=${level.width}x${level.height} " +
            "maxDepth=${level.maxDepth()}"
    }
}

// -- Occlusion result --

sealed class GPUHiZOcclusionResult {
    object Visible : GPUHiZOcclusionResult()
    object Occluded : GPUHiZOcclusionResult()
    object Uncertain : GPUHiZOcclusionResult()
}

// -- Occlusion decision --

sealed interface GPUHiZOcclusionDecision {
    data class Accepted(val pyramid: GPUHiZPyramid) : GPUHiZOcclusionDecision
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUHiZOcclusionDecision
}

// -- Pyramid build plan --

data class GPUHiZPyramidBuildPlan(
    val source: GPUHiZDepthSource,
    val maxLevels: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val format: GPUDepthFormat,
) {
    init {
        require(sourceWidth > 0) { "GPUHiZPyramidBuildPlan.sourceWidth must be positive" }
        require(sourceHeight > 0) { "GPUHiZPyramidBuildPlan.sourceHeight must be positive" }
        require(maxLevels > 0) { "GPUHiZPyramidBuildPlan.maxLevels must be positive" }
    }
}

// -- Pyramid construction --

fun buildHiZPyramid(
    depthData: FloatArray,
    width: Int,
    height: Int,
    format: GPUDepthFormat,
): GPUHiZPyramid {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(depthData.size == width * height) {
        "depthData size ${depthData.size} must equal width*height ${width * height}"
    }

    val levels = mutableListOf<GPUHiZPyramidLevel>()
    var currentData = depthData.copyOf()
    var currentWidth = width
    var currentHeight = height
    var levelIndex = 0

    while (currentWidth >= 1 && currentHeight >= 1) {
        levels.add(
            GPUHiZPyramidLevel(
                index = levelIndex,
                width = currentWidth,
                height = currentHeight,
                depthData = currentData,
            ),
        )

        if (currentWidth == 1 && currentHeight == 1) break

        val nextWidth = max(currentWidth / 2, 1)
        val nextHeight = max(currentHeight / 2, 1)
        val nextData = FloatArray(nextWidth * nextHeight)

        for (y in 0 until nextHeight) {
            for (x in 0 until nextWidth) {
                val srcX = x * 2
                val srcY = y * 2
                var maxDepth = currentData[srcY * currentWidth + srcX]

                if (srcX + 1 < currentWidth) {
                    maxDepth = max(maxDepth, currentData[srcY * currentWidth + (srcX + 1)])
                }
                if (srcY + 1 < currentHeight) {
                    maxDepth = max(maxDepth, currentData[(srcY + 1) * currentWidth + srcX])
                    if (srcX + 1 < currentWidth) {
                        maxDepth = max(maxDepth, currentData[(srcY + 1) * currentWidth + (srcX + 1)])
                    }
                }

                nextData[y * nextWidth + x] = maxDepth
            }
        }

        currentData = nextData
        currentWidth = nextWidth
        currentHeight = nextHeight
        levelIndex++
    }

    return GPUHiZPyramid(
        levels = levels,
        baseWidth = width,
        baseHeight = height,
        sourceDepthFormat = format,
    )
}

// -- Occlusion test --

/**
 * Selects the highest pyramid level whose projected draw-bounds footprint is <= 4 texels.
 *
 * Returns `null` when the result is `Uncertain` (empty pyramid, bounds fully outside the base, or a
 * level whose projected footprint is zero texels).
 */
private fun selectHiZLevel(
    pyramid: GPUHiZPyramid,
    drawBounds: GpuTileBounds,
): Int? {
    if (pyramid.levels.isEmpty()) return null

    val baseW = pyramid.baseWidth
    val baseH = pyramid.baseHeight

    val right = drawBounds.x + drawBounds.width
    val bottom = drawBounds.y + drawBounds.height

    if (right <= 0 || bottom <= 0 || drawBounds.x >= baseW || drawBounds.y >= baseH) {
        return null
    }

    var selectedLevel = pyramid.levels.size - 1

    for (li in (pyramid.levels.size - 1) downTo 0) {
        val scale = 1 shl li

        val projX0 = drawBounds.x / scale
        val projY0 = drawBounds.y / scale
        val projX1 = max(0, (right + scale - 1) / scale)
        val projY1 = max(0, (bottom + scale - 1) / scale)

        val texelCountX = max(0, projX1 - projX0)
        val texelCountY = max(0, projY1 - projY0)
        val texelCount = texelCountX * texelCountY

        if (texelCount in 1..4) {
            selectedLevel = li
            break
        }
        if (texelCount == 0) {
            return null
        }
    }

    return selectedLevel
}

/** Maximum pyramid depth over the projected draw-bounds region at [selectedLevel]. */
private fun hiZRegionMaxDepth(
    pyramid: GPUHiZPyramid,
    drawBounds: GpuTileBounds,
    selectedLevel: Int,
): Float {
    val level = pyramid.levels[selectedLevel]
    val scale = 1 shl selectedLevel

    val right = drawBounds.x + drawBounds.width
    val bottom = drawBounds.y + drawBounds.height

    val projX0 = max(0, drawBounds.x / scale)
    val projY0 = max(0, drawBounds.y / scale)
    val projX1 = min(level.width, max(projX0 + 1, (right + scale - 1) / scale))
    val projY1 = min(level.height, max(projY0 + 1, (bottom + scale - 1) / scale))

    var regionMaxDepth = Float.MIN_VALUE
    for (py in max(0, projY0) until min(level.height, projY1)) {
        for (px in max(0, projX0) until min(level.width, projX1)) {
            regionMaxDepth = max(regionMaxDepth, level.depthData[py * level.width + px])
        }
    }
    return regionMaxDepth
}

fun testHiZOcclusion(
    pyramid: GPUHiZPyramid,
    drawBounds: GpuTileBounds,
    drawMinDepth: Float,
): GPUHiZOcclusionResult {
    val selectedLevel = selectHiZLevel(pyramid, drawBounds) ?: return GPUHiZOcclusionResult.Uncertain
    val regionMaxDepth = hiZRegionMaxDepth(pyramid, drawBounds, selectedLevel)

    return if (drawMinDepth > regionMaxDepth) {
        GPUHiZOcclusionResult.Occluded
    } else {
        GPUHiZOcclusionResult.Visible
    }
}

// -- Occlusion test descriptor (design sketch type) --

/**
 * Per-draw Hi-Z occlusion test descriptor (spec design-sketch `GPUHiZOcclusionTest`; suffixed
 * `Descriptor` to avoid a case-insensitive filesystem collision with the `GpuHiZOcclusionTest`
 * JUnit class): the pyramid, projected draw bounds, draw minimum depth, and the pyramid level
 * selected for the comparison (`-1` when the test is `Uncertain`).
 */
data class GPUHiZOcclusionTestDescriptor(
    val pyramid: GPUHiZPyramid,
    val drawBounds: GpuTileBounds,
    val drawMinDepth: Float,
    val selectedLevel: Int,
) {
    /** Evaluates the occlusion result against the selected pyramid level. */
    fun evaluate(): GPUHiZOcclusionResult {
        if (selectedLevel < 0 || selectedLevel >= pyramid.levels.size) {
            return GPUHiZOcclusionResult.Uncertain
        }
        val regionMaxDepth = hiZRegionMaxDepth(pyramid, drawBounds, selectedLevel)
        return if (drawMinDepth > regionMaxDepth) {
            GPUHiZOcclusionResult.Occluded
        } else {
            GPUHiZOcclusionResult.Visible
        }
    }
}

/** Builds a [GPUHiZOcclusionTestDescriptor], recording the selected pyramid level for evidence. */
fun buildHiZOcclusionTest(
    pyramid: GPUHiZPyramid,
    drawBounds: GpuTileBounds,
    drawMinDepth: Float,
): GPUHiZOcclusionTestDescriptor = GPUHiZOcclusionTestDescriptor(
    pyramid = pyramid,
    drawBounds = drawBounds,
    drawMinDepth = drawMinDepth,
    selectedLevel = selectHiZLevel(pyramid, drawBounds) ?: -1,
)

// -- Culling rate --

fun computeHiZCullingRate(results: List<GPUHiZOcclusionResult>): Float {
    if (results.isEmpty()) return 0.0f
    val occludedCount = results.count { it is GPUHiZOcclusionResult.Occluded }
    return occludedCount.toFloat() / results.size.toFloat()
}

// -- False-negative efficiency --

/**
 * Hi-Z occlusion efficiency relative to a ground-truth occlusion oracle.
 *
 * A false negative is a truly-occluded draw that Hi-Z failed to cull (reported `Visible` or
 * `Uncertain`). Efficiency is the fraction of truly-occluded draws that Hi-Z correctly culled.
 */
data class GPUHiZEfficiencyReport(
    val trulyOccludedCount: Int,
    val correctlyCulledCount: Int,
    val falseNegativeCount: Int,
    val efficiency: Float,
) {
    fun dumpLines(): List<String> = listOf(
        "passes.hi-z-efficiency trulyOccluded=$trulyOccludedCount correctlyCulled=$correctlyCulledCount " +
            "falseNegatives=$falseNegativeCount efficiency=$efficiency",
    )
}

/**
 * Measures false negatives and culling efficiency against a ground-truth occlusion oracle.
 *
 * [groundTruthOccluded] is the per-draw oracle verdict (`true` = the draw is actually occluded);
 * its size must match [results].
 */
fun computeHiZFalseNegativeEfficiency(
    results: List<GPUHiZOcclusionResult>,
    groundTruthOccluded: List<Boolean>,
): GPUHiZEfficiencyReport {
    require(results.size == groundTruthOccluded.size) {
        "results and groundTruthOccluded must have the same size"
    }

    var trulyOccluded = 0
    var correctlyCulled = 0
    var falseNegatives = 0

    for (i in results.indices) {
        if (groundTruthOccluded[i]) {
            trulyOccluded++
            if (results[i] is GPUHiZOcclusionResult.Occluded) {
                correctlyCulled++
            } else {
                falseNegatives++
            }
        }
    }

    val efficiency = if (trulyOccluded == 0) 1.0f else correctlyCulled.toFloat() / trulyOccluded.toFloat()

    return GPUHiZEfficiencyReport(
        trulyOccludedCount = trulyOccluded,
        correctlyCulledCount = correctlyCulled,
        falseNegativeCount = falseNegatives,
        efficiency = efficiency,
    )
}

// -- Memory budget --

fun checkHiZPyramidMemoryBudget(
    pyramid: GPUHiZPyramid,
    maxBudgetBytes: Long,
    bytesPerTexel: Int = 4,
): Boolean {
    val totalBytes = pyramidTotalBytes(pyramid, bytesPerTexel)
    return totalBytes <= maxBudgetBytes
}

fun pyramidTotalBytes(
    pyramid: GPUHiZPyramid,
    bytesPerTexel: Int = 4,
): Long {
    var total: Long = 0
    for (level in pyramid.levels) {
        total += level.width.toLong() * level.height.toLong() * bytesPerTexel.toLong()
    }
    return total
}

// -- Depth format validation --

fun validateHiZDepthFormat(
    format: GPUDepthFormat,
    depthReadable: Boolean,
): GPUHiZOcclusionDecision {
    if (format == GPUDepthFormat.DEPTH16UNORM) {
        return GPUHiZOcclusionDecision.Refused(
            RefuseDiagnostic(
                code = "unsupported.occlusion.depth_format_unsupported",
                message = "Depth16Unorm is not supported for Hi-Z occlusion",
                stage = "occlusion.format",
                terminal = true,
            ),
        )
    }

    if (!depthReadable) {
        return GPUHiZOcclusionDecision.Refused(
            RefuseDiagnostic(
                code = "unsupported.occlusion.depth_not_readable",
                message = "Depth target is not readable; Hi-Z occlusion disabled",
                stage = "occlusion.format",
                terminal = true,
            ),
        )
    }

    return GPUHiZOcclusionDecision.Accepted(
        GPUHiZPyramid(emptyList(), 0, 0, format),
    )
}

// -- Pyramid build plan --

fun planHiZPyramidBuild(
    source: GPUHiZDepthSource,
    sourceWidth: Int,
    sourceHeight: Int,
    maxLevels: Int,
    format: GPUDepthFormat,
): GPUHiZPyramidBuildPlan {
    require(sourceWidth > 0) { "sourceWidth must be positive" }
    require(sourceHeight > 0) { "sourceHeight must be positive" }
    require(maxLevels >= 0) { "maxLevels must be non-negative" }

    val computedMaxLevels = if (maxLevels <= 0) {
        var dim = max(sourceWidth, sourceHeight)
        var levels = 0
        while (dim > 0) {
            levels++
            dim /= 2
        }
        levels
    } else {
        maxLevels
    }

    return GPUHiZPyramidBuildPlan(
        source = source,
        maxLevels = computedMaxLevels,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        format = format,
    )
}
