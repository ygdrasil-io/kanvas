package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class GPUMsaaCoverageMode {
    Standard,
    AlphaToCoverage,
}

enum class GPUMultisampleResolveStrategy {
    WGPU_BUILTIN,
    CUSTOM_WGSL,
    COMPUTE_SHADER,
}

data class GPUMultisamplePlan(
    val sampleCount: Int,
    val sampleMask: UInt,
    val alphaToCoverageEnabled: Boolean,
) {
    init {
        require(sampleCount in setOf(1, 4, 8)) { "GPUMultisamplePlan.sampleCount must be 1, 4, or 8" }
    }
}

data class GPUMultisampleResolvePlan(
    val strategy: GPUMultisampleResolveStrategy,
)

data class GPUMultisampleTargetDescriptor(
    val sampleCount: Int,
    val resolvePlan: GPUMultisampleResolvePlan,
) {
    init {
        require(sampleCount in setOf(1, 4, 8)) {
            "GPUMultisampleTargetDescriptor.sampleCount must be 1, 4, or 8"
        }
    }
}

data class GPUMsaaAdapterCapability(
    val adapterLabel: String,
    val maxSampleCount: Int,
    val supportsAlphaToCoverage: Boolean,
    val supportsNativeResolve: Boolean = false,
) {
    init {
        require(adapterLabel.isNotBlank()) { "GPUMsaaAdapterCapability.adapterLabel must not be blank" }
        require(maxSampleCount > 0) { "GPUMsaaAdapterCapability.maxSampleCount must be positive" }
    }
}

data class GPUMsaaRequest(
    val requestedSampleCount: Int,
    val coverageMode: GPUMsaaCoverageMode,
    val adapter: GPUMsaaAdapterCapability? = null,
)

data class GPUMsaaPsnrEvidence(
    val sampleCount: Int,
    val psnrDb: Double,
    val referenceLabel: String,
)

data class GPUMsaaAccepted(
    val sampleCount: Int,
    val coverageMode: GPUMsaaCoverageMode,
    val adapter: GPUMsaaAdapterCapability,
    val psnrEvidence: GPUMsaaPsnrEvidence? = null,
) {
    init {
        require(sampleCount in setOf(4, 8)) { "MSAA sampleCount must be 4 or 8" }
        require(adapter.maxSampleCount >= sampleCount) {
            "Adapter maxSampleCount ${adapter.maxSampleCount} < requested $sampleCount"
        }
        require(adapter.supportsNativeResolve) {
            "Adapter ${adapter.adapterLabel} does not expose native MSAA resolve support"
        }
    }
}

sealed interface GPUMsaaRoute {
    data class Accepted(val resolved: GPUMsaaAccepted) : GPUMsaaRoute {
        val sampleCount: Int get() = resolved.sampleCount
        val coverageMode: GPUMsaaCoverageMode get() = resolved.coverageMode

        fun dumpLines(): List<String> {
            val psnrLabel = resolved.psnrEvidence?.let {
                "psnrDb=%.4f reference=%s".format(it.psnrDb, it.referenceLabel)
            } ?: "psnrDb=none reference=none"
            return listOf(
                "msaa.resolve sampleCount=${resolved.sampleCount} " +
                    "coverageMode=${resolved.coverageMode} " +
                    "adapter=${resolved.adapter.adapterLabel} " +
                    "maxSampleCount=${resolved.adapter.maxSampleCount} " +
                    "alphaToCoverage=${resolved.adapter.supportsAlphaToCoverage} " +
                    "route=accepted",
                "msaa.resolve.adapter label=${resolved.adapter.adapterLabel} " +
                    "maxSamples=${resolved.adapter.maxSampleCount} " +
                    "alphaToCoverage=${resolved.adapter.supportsAlphaToCoverage}",
                "msaa.resolve.psnr sampleCount=${resolved.sampleCount} $psnrLabel",
            )
        }
    }

    data class Refused(
        val diagnostic: RefuseDiagnostic,
        val request: GPUMsaaRequest? = null,
    ) : GPUMsaaRoute {
        fun dumpLines(): List<String> {
            val contextLine = if (request != null && request.adapter != null) {
                "msaa.resolve sampleCount=${request.requestedSampleCount} " +
                    "adapter=${request.adapter.adapterLabel} " +
                    "maxSampleCount=${request.adapter.maxSampleCount} " +
                    "route=refused"
            } else {
                "msaa.resolve route=refused"
            }
            return listOf(
                contextLine,
                "msaa.resolve.diagnostic code=${diagnostic.code} " +
                    "message=${diagnostic.message} " +
                    "stage=${diagnostic.stage} " +
                    "terminal=${diagnostic.terminal}",
            )
        }
    }
}

fun computeMsaaPsnr(reference: FloatArray, resolved: FloatArray, sampleCount: Int): Double {
    require(reference.size == resolved.size) {
        "Reference and resolved arrays must have same length"
    }
    require(sampleCount in setOf(1, 4, 8)) { "sampleCount must be 1, 4, or 8" }
    if (reference.isEmpty()) return 0.0
    var mse = 0.0
    for (i in reference.indices) {
        val diff = (reference[i] - resolved[i]).toDouble()
        mse += diff * diff
    }
    mse /= reference.size
    if (mse == 0.0) return Double.POSITIVE_INFINITY
    return 10.0 * kotlin.math.log10(1.0 / mse)
}

object GPUMsaa {
    object Reason {
        const val UNSUPPORTED_MSAA_WEBGPU_MISSING_ADAPTER = "unsupported.msaa.webgpu_missing_adapter"
        const val UNSUPPORTED_SAMPLE_COUNT = "unsupported.msaa.sample_count"
        const val ADAPTER_CAPABILITY_INSUFFICIENT = "unsupported.msaa.adapter_capability"
        const val ALPHA_TO_COVERAGE_UNSUPPORTED = "unsupported.msaa.alpha_to_coverage"
        const val NATIVE_RESOLVE_UNAVAILABLE = "unsupported.msaa.native_resolve_unavailable"
        const val MULTISAMPLE_RESOLVE_FORMAT = "unsupported.target.multisample_resolve_format"
    }

    fun resolve4x(
        adapter: GPUMsaaAdapterCapability,
        coverageMode: GPUMsaaCoverageMode = GPUMsaaCoverageMode.Standard,
        referencePixels: FloatArray = floatArrayOf(),
        resolvedPixels: FloatArray = floatArrayOf(),
    ): GPUMsaaRoute = resolve(
        GPUMsaaRequest(
            requestedSampleCount = 4,
            coverageMode = coverageMode,
            adapter = adapter,
        ),
        referencePixels,
        resolvedPixels,
    )

    fun resolve8x(
        adapter: GPUMsaaAdapterCapability,
        coverageMode: GPUMsaaCoverageMode = GPUMsaaCoverageMode.Standard,
        referencePixels: FloatArray = floatArrayOf(),
        resolvedPixels: FloatArray = floatArrayOf(),
    ): GPUMsaaRoute = resolve(
        GPUMsaaRequest(
            requestedSampleCount = 8,
            coverageMode = coverageMode,
            adapter = adapter,
        ),
        referencePixels,
        resolvedPixels,
    )

    fun resolve(
        request: GPUMsaaRequest,
        referencePixels: FloatArray = floatArrayOf(),
        resolvedPixels: FloatArray = floatArrayOf(),
    ): GPUMsaaRoute {
        if (!request.requestedSampleCount.isSupportedMsaaSampleCount()) {
            return GPUMsaaRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = Reason.UNSUPPORTED_SAMPLE_COUNT,
                    message = "MSAA sampleCount ${request.requestedSampleCount} must be 1, 4, or 8",
                    stage = "msaa.resolve",
                    terminal = true,
                ),
                request = request,
            )
        }

        val adapter = request.adapter ?: return GPUMsaaRoute.Refused(
            diagnostic = RefuseDiagnostic(
                code = Reason.UNSUPPORTED_MSAA_WEBGPU_MISSING_ADAPTER,
                message = "MSAA resolve requires adapter capability evidence",
                stage = "msaa.resolve",
                terminal = true,
            ),
            request = request,
        )

        if (!adapter.supportsNativeResolve) {
            return GPUMsaaRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = Reason.NATIVE_RESOLVE_UNAVAILABLE,
                    message = "Adapter does not expose native MSAA resolve support",
                    stage = "msaa.resolve",
                    terminal = true,
                ),
                request = request,
            )
        }

        if (adapter.maxSampleCount < request.requestedSampleCount) {
            return GPUMsaaRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = Reason.ADAPTER_CAPABILITY_INSUFFICIENT,
                    message = "Adapter maxSampleCount ${adapter.maxSampleCount} < requested ${request.requestedSampleCount}",
                    stage = "msaa.resolve",
                    terminal = true,
                ),
                request = request,
            )
        }

        if (request.coverageMode == GPUMsaaCoverageMode.AlphaToCoverage && !adapter.supportsAlphaToCoverage) {
            return GPUMsaaRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = Reason.ALPHA_TO_COVERAGE_UNSUPPORTED,
                    message = "Adapter does not support alpha-to-coverage",
                    stage = "msaa.resolve",
                    terminal = true,
                ),
                request = request,
            )
        }

        val psnr = if (referencePixels.isNotEmpty() && resolvedPixels.isNotEmpty()) {
            GPUMsaaPsnrEvidence(
                sampleCount = request.requestedSampleCount,
                psnrDb = computeMsaaPsnr(referencePixels, resolvedPixels, request.requestedSampleCount),
                referenceLabel = "reference-${request.requestedSampleCount}x",
            )
        } else null

        return GPUMsaaRoute.Accepted(
            GPUMsaaAccepted(
                sampleCount = request.requestedSampleCount,
                coverageMode = request.coverageMode,
                adapter = adapter,
                psnrEvidence = psnr,
            )
        )
    }
}

private fun Int.isSupportedMsaaSampleCount(): Boolean = this in setOf(1, 4, 8)

fun GPUMultisamplePlan.dumpLines(): List<String> =
    listOf(
        "msaa.plan sampleCount=$sampleCount sampleMask=$sampleMask alphaToCoverage=$alphaToCoverageEnabled",
    )

fun GPUMultisampleResolvePlan.dumpLines(): List<String> =
    listOf(
        "msaa.resolve-plan strategy=$strategy",
    )

fun GPUMultisampleTargetDescriptor.dumpLines(): List<String> =
    listOf(
        "msaa.target-desc sampleCount=$sampleCount resolveStrategy=${resolvePlan.strategy}",
    )
