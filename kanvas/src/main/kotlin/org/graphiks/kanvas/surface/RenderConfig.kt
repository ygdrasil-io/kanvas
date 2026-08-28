package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.geometry.GPUPathEdgeFanPayloadContract
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageRouteCapability

data class RenderConfig(
    val gpuColorFormat: GPUColorFormat = GPUColorFormat.RGBA8_UNORM_SRGB,
    val maxPathVertices: UInt = 131072u,
    /** Maximum stencil edge-fan triangles admitted by the public Surface path route. */
    val maxPathFanTriangles: UInt = GPUPathEdgeFanPayloadContract.MAX_TRIANGLES,
    /** Maximum bytes for the public Surface path edge-fan position/index buffers. */
    val maxPathGeometryBytes: UInt = GPUPathEdgeFanPayloadContract.MAX_GEOMETRY_BYTES,
    val curveTolerance: Float = 0.25f,
    val maxImagePixels: UInt = 67_108_864u,
    val maxMaskBlurIntermediateBytes: UInt = 67_108_864u,
    val maxClipIntermediateBytes: UInt = 67_108_864u,
    /** Maximum ordered clip elements admitted by the public Surface coverage-mask route. */
    val maxClipStackDepth: UInt = 8u,
    /** Selects the explicit prepared-image capability admitted by this Surface. */
    val preparedImageRouteCapability: GPUPreparedImageRouteCapability =
        GPUPreparedImageRouteCapability.GenericNative,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.WARN,
    val debugLevel: DebugLevel = DebugLevel.OFF,
) {
    /**
     * Validates the public path edge-fan configuration before the mapper can
     * convert unsigned limits to backend `Int` values or allocate geometry.
     */
    internal fun pathEdgeFanBudgetRefusalCodeOrNull(): String? = when {
        maxPathVertices > Int.MAX_VALUE.toUInt() ->
            "unsupported.core_primitive.path_vertex_budget_config_out_of_int_range"
        maxPathFanTriangles > Int.MAX_VALUE.toUInt() ->
            "geometry.path.fan_budget_config_out_of_int_range"
        maxPathGeometryBytes > Int.MAX_VALUE.toUInt() ->
            "geometry.path.memory_budget_config_out_of_int_range"
        maxPathFanTriangles > GPUPathEdgeFanPayloadContract.MAX_TRIANGLES ->
            "geometry.path.fan_budget_config_exceeded"
        maxPathGeometryBytes > GPUPathEdgeFanPayloadContract.MAX_GEOMETRY_BYTES ->
            "geometry.path.memory_budget_config_exceeded"
        else -> null
    }

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
                maxPathFanTriangles = p.getProperty("kanvas.render.maxPathFanTriangles")
                    ?.toUIntOrNull() ?: DEFAULT.maxPathFanTriangles,
                maxPathGeometryBytes = p.getProperty("kanvas.render.maxPathGeometryBytes")
                    ?.toUIntOrNull() ?: DEFAULT.maxPathGeometryBytes,
                curveTolerance = p.getProperty("kanvas.render.curveTolerance")
                    ?.toFloatOrNull() ?: DEFAULT.curveTolerance,
                maxImagePixels = p.getProperty("kanvas.render.maxImagePixels")
                    ?.toUIntOrNull() ?: DEFAULT.maxImagePixels,
                maxMaskBlurIntermediateBytes = p.getProperty("kanvas.render.maxMaskBlurIntermediateBytes")
                    ?.toUIntOrNull() ?: DEFAULT.maxMaskBlurIntermediateBytes,
                maxClipIntermediateBytes = p.getProperty("kanvas.render.maxClipIntermediateBytes")
                    ?.toUIntOrNull() ?: DEFAULT.maxClipIntermediateBytes,
                maxClipStackDepth = p.getProperty("kanvas.render.maxClipStackDepth")
                    ?.toUIntOrNull() ?: DEFAULT.maxClipStackDepth,
                preparedImageRouteCapability = p.getProperty("kanvas.render.preparedImageRouteCapability")
                    ?.let { runCatching { GPUPreparedImageRouteCapability.valueOf(it) }.getOrNull() }
                    ?: DEFAULT.preparedImageRouteCapability,
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
