package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/** Severity level for a WGSL security violation. */
enum class WGSLSecurityErrorSeverity {
    ERROR,
    WARNING,
}

/** A single WGSL security violation found during validation. */
data class WGSLSecurityError(
    val code: String,
    val message: String,
    val severity: WGSLSecurityErrorSeverity,
)

/** Result of a WGSL security validation pass. */
data class WGSLSecurityValidationReport(
    val isSecure: Boolean,
    val errors: List<WGSLSecurityError>,
)

/** Device capability constraints used during security validation. */
data class WGSLDeviceCapabilities(
    val supportsRayQuery: Boolean = false,
    val supportsStorageBuffers: Boolean = true,
    val supportsAtomics: Boolean = false,
    val maxTextureDimensions: Int = 4096,
    val maxUniformBufferSize: Int = 16 * 1024,
    val maxStorageBufferSize: Int = 64 * 1024,
)

/** Minimal stub for a wgsl4k-parsed module consumed by security validation. */
data class WGSLParsedModule(
    val sourceHash: String,
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

/** Validates custom WGSL against blocked features, resource limits, device capabilities, and buffer/texture bounds. */
class WGSLSecurityValidator(
    private val deviceCapabilities: WGSLDeviceCapabilities = WGSLDeviceCapabilities(),
) {
    fun validateSecurity(module: WGSLParsedModule): WGSLSecurityValidationReport {
        val errors = mutableListOf<WGSLSecurityError>()
        errors.addAll(checkBlockedFeatures(module))
        errors.addAll(checkResourceLimits(module))
        errors.addAll(checkDeviceCapabilities(module, deviceCapabilities))
        errors.addAll(checkBufferTextureAccessBounds(module))
        return WGSLSecurityValidationReport(isSecure = errors.isEmpty(), errors = errors)
    }

    private fun checkBlockedFeatures(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.usesAtomics)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-atomic", "Atomic operations not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesUnboundedStorageBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-storage-buffer", "Storage buffers must have explicit size", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesReadWriteBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-read-write-buffer", "Read-write storage buffers not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesPtrOperations)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-ptr", "Pointer operations not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.hasRecursiveFunctions)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-recursion", "Recursive functions not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.hasUnboundedLoops)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-loop", "Unbounded loops not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesDynamicSampling)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-dynamic-sampling", "Dynamic texture sampling not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesTextureStore)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-texture-store", "Texture storage not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesDynamicBinding)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-dynamic-binding", "Dynamic bind groups not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesComputeShader)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-compute", "Compute shaders not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesWorkgroupBuiltins)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-workgroup", "Workgroup builtins not allowed", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkResourceLimits(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.uniforms.size > MAX_CUSTOM_UNIFORMS)
            errors.add(WGSLSecurityError("custom-wgsl.uniform-count-exceeded", "${module.uniforms.size} > $MAX_CUSTOM_UNIFORMS", WGSLSecurityErrorSeverity.ERROR))
        if (module.textures.size > MAX_CUSTOM_TEXTURES)
            errors.add(WGSLSecurityError("custom-wgsl.texture-count-exceeded", "${module.textures.size} > $MAX_CUSTOM_TEXTURES", WGSLSecurityErrorSeverity.ERROR))
        if (module.bindGroups.size > MAX_CUSTOM_BIND_GROUPS)
            errors.add(WGSLSecurityError("custom-wgsl.bind-group-count-exceeded", "${module.bindGroups.size} > $MAX_CUSTOM_BIND_GROUPS", WGSLSecurityErrorSeverity.ERROR))
        if (module.loopIterationCount > MAX_CUSTOM_LOOP_ITERATIONS)
            errors.add(WGSLSecurityError("custom-wgsl.loop-iteration-exceeded", "${module.loopIterationCount} > $MAX_CUSTOM_LOOP_ITERATIONS", WGSLSecurityErrorSeverity.ERROR))
        if (module.functionDepth > MAX_CUSTOM_FUNCTION_DEPTH)
            errors.add(WGSLSecurityError("custom-wgsl.function-depth-exceeded", "${module.functionDepth} > $MAX_CUSTOM_FUNCTION_DEPTH", WGSLSecurityErrorSeverity.ERROR))
        if (module.maxTextureDimensions > deviceCapabilities.maxTextureDimensions)
            errors.add(WGSLSecurityError("custom-wgsl.texture-dimension-exceeded", "${module.maxTextureDimensions} > ${deviceCapabilities.maxTextureDimensions}", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkDeviceCapabilities(module: WGSLParsedModule, capabilities: WGSLDeviceCapabilities): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.usesRayQuery && !capabilities.supportsRayQuery)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-ray-query", "Device does not support ray query", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesAtomics && !capabilities.supportsAtomics)
            errors.add(WGSLSecurityError("custom-wgsl.device-unsupported", "Device does not support atomic operations", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkBufferTextureAccessBounds(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.storageBuffers.isNotEmpty() && module.usesUnboundedStorageBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-storage-buffer", "Unbounded storage buffers risk out-of-bounds memory access", WGSLSecurityErrorSeverity.ERROR))
        if (module.textures.isNotEmpty() && module.usesDynamicSampling && module.usesTextureStore)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-texture-store", "Dynamic texture sampling with storage risks out-of-bounds access", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    companion object {
        const val MAX_CUSTOM_UNIFORMS = 16
        const val MAX_CUSTOM_TEXTURES = 8
        const val MAX_CUSTOM_BIND_GROUPS = 4
        const val MAX_CUSTOM_BINDINGS_PER_GROUP = 8
        const val MAX_CUSTOM_UNIFORM_BUFFER_SIZE = 16 * 1024
        const val MAX_CUSTOM_STORAGE_BUFFER_SIZE = 64 * 1024
        const val MAX_CUSTOM_LOOP_ITERATIONS = 1024
        const val MAX_CUSTOM_FUNCTION_DEPTH = 8
    }
}
