package org.graphiks.kanvas.surface

data class RenderConfig(
    val gpuColorFormat: GPUColorFormat = GPUColorFormat.RGBA8_UNORM_SRGB,
    val sampleCount: Int = 1,
    val maxPathVertices: UInt = 131072u,
    val curveTolerance: Float = 0.25f,
    val maxImagePixels: UInt = 67_108_864u,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.WARN,
    val debugLevel: DebugLevel = DebugLevel.OFF,
) {
    init {
        require(sampleCount in setOf(1, 4)) { "sampleCount must be 1 or 4, got $sampleCount" }
    }
    companion object {
        val DEFAULT = RenderConfig()

        fun fromEnvironment(): RenderConfig {
            val p = System.getProperties()
            return RenderConfig(
                gpuColorFormat = p.getProperty("kanvas.render.gpuColorFormat")
                    ?.let { runCatching { GPUColorFormat.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.gpuColorFormat,
                sampleCount = p.getProperty("kanvas.render.sampleCount")?.toIntOrNull() ?: DEFAULT.sampleCount,
                maxPathVertices = p.getProperty("kanvas.render.maxPathVertices")
                    ?.toUIntOrNull() ?: DEFAULT.maxPathVertices,
                curveTolerance = p.getProperty("kanvas.render.curveTolerance")
                    ?.toFloatOrNull() ?: DEFAULT.curveTolerance,
                maxImagePixels = p.getProperty("kanvas.render.maxImagePixels")
                    ?.toUIntOrNull() ?: DEFAULT.maxImagePixels,
                diagnosticLevel = p.getProperty("kanvas.render.diagnosticLevel")
                    ?.let { runCatching { DiagnosticLevel.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.diagnosticLevel,
                debugLevel = p.getProperty("kanvas.render.debugLevel")
                    ?.let { runCatching { DebugLevel.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.debugLevel,
            )
        }
    }
}
