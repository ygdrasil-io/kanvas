package org.graphiks.kanvas.gpu.renderer.wgsl.validation

import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport

/** Parser-backed WGSL facts shared by generic validation and semantic consumers. */
data class WGSLParsedModule(
    val sourceHash: String,
    val source: String = "",
    val uniforms: List<String> = emptyList(),
    val textures: List<String> = emptyList(),
    val bindGroups: List<String> = emptyList(),
    val storageBuffers: List<String> = emptyList(),
    val usesAtomics: Boolean = false,
    val usesUnboundedStorageBuffers: Boolean = false,
    val usesReadWriteBuffers: Boolean = false,
    val usesPtrOperations: Boolean = false,
    val hasRecursiveFunctions: Boolean = false,
    val hasUnboundedLoops: Boolean = false,
    val usesDynamicSampling: Boolean = false,
    val usesTextureStore: Boolean = false,
    val usesDynamicBinding: Boolean = false,
    val usesRayQuery: Boolean = false,
    val usesComputeShader: Boolean = false,
    val usesWorkgroupBuiltins: Boolean = false,
    val loopIterationCount: Int = 0,
    val functionDepth: Int = 0,
    val maxTextureDimensions: Int = 0,
    val syntaxErrors: List<String> = emptyList(),
)

/** Validates WGSL source and produces a parsed module carrying resource and feature information. */
interface WGSLValidator {
    /** Parses the WGSL [source] into a [WGSLParsedModule] with syntax errors and resource usage. */
    fun parse(source: String): WGSLParsedModule
}

/** Reflects a parsed WGSL module into generic entry-point and resource metadata. */
interface WGSLReflectionProvider {
    fun reflect(module: WGSLParsedModule): WGSLModuleReflection
}

/**
 * Parser-backed reflection facts for a WGSL module.
 *
 * [report] is absent only on the existing fixture fallback path.
 */
data class WGSLModuleReflection(
    val moduleHash: String,
    val entryPoint: String,
    val uniformCount: Int,
    val textureCount: Int,
    val bindGroupCount: Int,
    val reflectionHash: String,
    val report: WgslReflectionReport? = null,
)
