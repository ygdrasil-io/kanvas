package org.graphiks.kanvas.gpu.renderer.coordinates

/** Checked target-local pixel bounds with exclusive right and bottom edges. */
data class GPUPixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "GPUPixelBounds.left must be non-negative" }
        require(top >= 0) { "GPUPixelBounds.top must be non-negative" }
        require(right >= left) { "GPUPixelBounds.right must not be less than left" }
        require(bottom >= top) { "GPUPixelBounds.bottom must not be less than top" }
    }

    val width: Int get() = Math.subtractExact(right, left)
    val height: Int get() = Math.subtractExact(bottom, top)
    val isEmpty: Boolean get() = width == 0 || height == 0

    /** Computes allocation bytes without allowing integer overflow. */
    fun checkedByteSize(bytesPerPixel: Int, sampleCount: Int): Long {
        require(bytesPerPixel > 0) { "bytesPerPixel must be positive" }
        require(sampleCount > 0) { "sampleCount must be positive" }
        val pixels = Math.multiplyExact(width.toLong(), height.toLong())
        return Math.multiplyExact(Math.multiplyExact(pixels, bytesPerPixel.toLong()), sampleCount.toLong())
    }
}

/** Coordinate spaces used by GPU renderer planning. */
enum class GPUCoordinateSpace {
    /** Local command coordinates before captured transforms. */
    Local,
    /** Layer-local coordinates after saveLayer transforms. */
    Layer,
    /** Device pixel coordinates. */
    Device,
    /** Texture coordinates in texel space. */
    Texture,
    /** Normalized device coordinates. */
    NormalizedDevice,
}

/** Bounds rounding policy for conservative GPU planning. */
enum class GPURoundingPlan {
    /** Leave bounds unchanged. */
    None,
    /** Round lower edges down and upper edges up. */
    Out,
    /** Round to nearest pixel grid. */
    Nearest,
}

/** Proof that bounds remain conservative after transformations. */
data class GPUBoundsProof(
    val sourceLabel: String,
    val operations: List<String>,
    val conservative: Boolean,
    val overflowChecked: Boolean,
)

/** Conservative bounds plan in one coordinate space. */
data class GPUBoundsPlan(
    val space: GPUCoordinateSpace,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rounding: GPURoundingPlan,
    val proof: GPUBoundsProof,
)

/** Inverse transform facts used for shader-local evaluation. */
data class GPUInverseTransformPlan(
    val available: Boolean,
    val matrixValues: List<Float>,
    val precision: String,
    val refusalCode: String? = null,
)

/** Transform plan between two renderer coordinate spaces. */
data class GPUTransformPlan(
    val from: GPUCoordinateSpace,
    val to: GPUCoordinateSpace,
    val classification: String,
    val matrixValues: List<Float>,
    val inverse: GPUInverseTransformPlan? = null,
)

/** Pixel-grid convention used by coverage and sampling. */
data class GPUPixelGridPlan(
    val space: GPUCoordinateSpace,
    val originX: Float,
    val originY: Float,
    val pixelCenterConvention: String,
    val snappingPolicy: String,
)

/** Proof that clip reduction preserves coverage. */
data class GPUClipReductionProof(
    val originalBoundsLabel: String,
    val reducedBoundsLabel: String,
    val preservesCoverage: Boolean,
    val proofFacts: Map<String, String> = emptyMap(),
)

/** Coordinate payload required by a shader or render step. */
data class GPUCoordinatePayloadPlan(
    val space: GPUCoordinateSpace,
    val transformKey: String,
    val uniformNames: List<String>,
    val precisionPolicy: String,
)

/** Diagnostic emitted by transform or coordinate planning. */
data class GPUTransformDiagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val space: GPUCoordinateSpace? = null,
    val isTerminal: Boolean,
)
