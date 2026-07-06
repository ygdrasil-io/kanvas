package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.math.exp

enum class SeparableBlurQualityTier {
    FAST,
    NORMAL,
    HIGH;

    fun tapCount(sigma: Float): Int = when (this) {
        FAST -> 5
        NORMAL -> {
            if (sigma <= 0.5f) return 1
            val base = kotlin.math.ceil(sigma).toInt() * 2 + 1
            if (base % 2 == 0) base + 1 else base.coerceAtLeast(1)
        }
        HIGH -> {
            if (sigma <= 0.5f) return 1
            val base = (sigma * 3f).toInt() * 2 + 1
            if (base % 2 == 0) base + 1 else base.coerceAtLeast(1)
        }
    }

    fun effectiveSigma(sigma: Float): Float = when (this) {
        FAST -> 0.5f
        NORMAL -> sigma.coerceAtLeast(0.5f)
        HIGH -> sigma.coerceAtLeast(0.5f)
    }

    companion object {
        fun ordinalOf(quality: Int): SeparableBlurQualityTier =
            entries.firstOrNull { it.ordinal == quality } ?: HIGH
    }
}

data class GaussianKernel(val weights: FloatArray, val offset: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GaussianKernel) return false
        return weights.contentEquals(other.weights) && offset == other.offset
    }

    override fun hashCode(): Int {
        var result = weights.contentHashCode()
        result = 31 * result + offset
        return result
    }
}

class GaussianKernelCache(private val maxEntries: Int = 64) {
    private data class CacheKey(val sigma: Float, val taps: Int)
    private val cache = LinkedHashMap<CacheKey, GaussianKernel>(maxEntries, 0.75f, true)

    fun getOrCompute(sigma: Float, taps: Int): GaussianKernel {
        val key = CacheKey(sigma, taps)
        return cache.getOrPut(key) { computeGaussianKernel(sigma, taps) }
    }

    fun clear() = cache.clear()

    fun size(): Int = cache.size

    private fun computeGaussianKernel(sigma: Float, taps: Int): GaussianKernel {
        val half = taps / 2
        val weights = FloatArray(taps) { i ->
            val x = (i - half).toFloat()
            exp(-(x * x) / (2f * sigma * sigma))
        }
        val sum = weights.sum()
        if (sum > 0f) {
            for (i in weights.indices) {
                weights[i] /= sum
            }
        }
        return GaussianKernel(weights, half)
    }
}

enum class BlurAxis { HORIZONTAL, VERTICAL }

data class BlurPassPlan(
    val axis: BlurAxis,
    val kernelTaps: Int,
    val programKeyHash: String,
)

data class IntermediateBlurArtifact(
    val artifactKey: String,
    val formatClass: String,
    val byteEstimate: Long,
)

data class SeparableBlurPlan(
    val passes: List<BlurPassPlan>,
    val intermediateArtifact: IntermediateBlurArtifact?,
    val diagnostics: List<GPUFilterDiagnostic>,
)

class GPUSeparableBlurPlanner(
    private val kernelCache: GaussianKernelCache = GaussianKernelCache(),
    private val intermediateWidth: Int = 1024,
    private val intermediateHeight: Int = 1024,
    private val bytesPerPixel: Long = 4L,
) {
    fun plan(
        sigmaX: Float,
        sigmaY: Float,
        qualityTier: SeparableBlurQualityTier = SeparableBlurQualityTier.NORMAL,
        tileMode: GPUTileMode = GPUTileMode.Clamp,
    ): SeparableBlurPlan {
        if (sigmaX < 0f || sigmaY < 0f) {
            return SeparableBlurPlan(
                passes = emptyList(),
                intermediateArtifact = null,
                diagnostics = listOf(
                    GPUFilterDiagnostic(
                        code = "unsupported.filter.blur_sigma_range",
                        message = "Sigma $sigmaX/$sigmaY out of range; refuse execution.",
                        terminal = true,
                    ),
                ),
            )
        }

        if (sigmaX <= 0f && sigmaY <= 0f) {
            return SeparableBlurPlan(
                passes = emptyList(),
                intermediateArtifact = null,
                diagnostics = listOf(
                    GPUFilterDiagnostic(
                        code = "elision.identity_pass",
                        message = "Zero-sigma blur is a no-op; elision identity pass.",
                        terminal = false,
                    ),
                ),
            )
        }

        val hTaps = qualityTier.tapCount(sigmaX)
        val vTaps = qualityTier.tapCount(sigmaY)
        val hSigma = qualityTier.effectiveSigma(sigmaX)
        val vSigma = qualityTier.effectiveSigma(sigmaY)

        val hKernel = kernelCache.getOrCompute(hSigma, hTaps)
        val vKernel = kernelCache.getOrCompute(vSigma, vTaps)

        val artifactKey = "blur-intermediate:${sigmaX}x${sigmaY}:${qualityTier.name}:${tileMode}"

        val passes = listOf(
            BlurPassPlan(
                axis = BlurAxis.HORIZONTAL,
                kernelTaps = hTaps,
                programKeyHash = programKeyHash("blur-h", hTaps, hKernel),
            ),
            BlurPassPlan(
                axis = BlurAxis.VERTICAL,
                kernelTaps = vTaps,
                programKeyHash = programKeyHash("blur-v", vTaps, vKernel),
            ),
        )

        val byteEstimate = intermediateWidth.toLong() * intermediateHeight * bytesPerPixel

        return SeparableBlurPlan(
            passes = passes,
            intermediateArtifact = IntermediateBlurArtifact(
                artifactKey = artifactKey,
                formatClass = "rgba8",
                byteEstimate = byteEstimate,
            ),
            diagnostics = listOf(
                GPUFilterDiagnostic(
                    code = "accepted.filter.separable_blur",
                    message = "Separable blur plan accepted: ${qualityTier.name} sigma=($sigmaX,$sigmaY) x=$hTaps/$vTaps taps tile=$tileMode",
                    terminal = false,
                ),
            ),
        )
    }

    private fun programKeyHash(prefix: String, taps: Int, kernel: GaussianKernel): String {
        val kernelHex = kernel.weights.take(4).joinToString("") { "%02x".format((it * 255f).toInt() and 0xFF) }
        return "sha256:$prefix:taps=$taps:kernel=$kernelHex"
    }
}
