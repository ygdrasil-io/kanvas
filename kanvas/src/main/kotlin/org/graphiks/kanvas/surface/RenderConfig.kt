package org.graphiks.kanvas.surface

data class RenderConfig(
    val gpuColorFormat: GPUColorFormat = GPUColorFormat.RGBA8_UNORM,
    val maxPathVertices: UInt = 16384u,
    val curveTolerance: Float = 0.25f,
    val maxImagePixels: UInt = 67_108_864u,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.WARN,
) {
    companion object {
        val DEFAULT = RenderConfig()

        fun fromEnvironment(): RenderConfig {
            val p = System.getProperties()
            return RenderConfig(
                gpuColorFormat = p.getProperty("kanvas.render.gpuColorFormat")
                    ?.let { runCatching { GPUColorFormat.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.gpuColorFormat,
                maxPathVertices = p.getProperty("kanvas.render.maxPathVertices")
                    ?.toUIntOrNull() ?: DEFAULT.maxPathVertices,
                curveTolerance = p.getProperty("kanvas.render.curveTolerance")
                    ?.toFloatOrNull() ?: DEFAULT.curveTolerance,
                maxImagePixels = p.getProperty("kanvas.render.maxImagePixels")
                    ?.toUIntOrNull() ?: DEFAULT.maxImagePixels,
                diagnosticLevel = p.getProperty("kanvas.render.diagnosticLevel")
                    ?.let { runCatching { DiagnosticLevel.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.diagnosticLevel,
            )
        }
    }
}
