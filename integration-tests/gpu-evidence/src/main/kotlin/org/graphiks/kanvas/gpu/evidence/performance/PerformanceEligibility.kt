package org.graphiks.kanvas.gpu.evidence.performance

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary

object PerformanceEligibility {
    private val softwareTokens = listOf("llvmpipe", "swiftshader", "software rasterizer")

    fun evaluate(adapter: GPUBackendAdapterSummary?): PerformanceVerdict {
        if (adapter == null) return PerformanceVerdict.Unavailable("adapter identity unavailable")
        if (adapter.isFallbackAdapter == true) return PerformanceVerdict.DiagnosticOnly("fallback adapter")
        val normalized = adapter.summary.lowercase()
        if (softwareTokens.any(normalized::contains)) return PerformanceVerdict.DiagnosticOnly("software adapter")
        val hasIdentity = listOf(adapter.vendor, adapter.device, adapter.architecture, adapter.description)
            .any { !it.isNullOrBlank() }
        return if (hasIdentity) PerformanceVerdict.EligibleMeasurement("hardware adapter")
        else PerformanceVerdict.Unavailable("adapter identity unavailable")
    }
}
