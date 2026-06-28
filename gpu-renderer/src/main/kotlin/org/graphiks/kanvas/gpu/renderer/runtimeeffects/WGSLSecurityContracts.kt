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

/** Validates custom WGSL against blocked features, resource limits, and device capabilities. */
class WGSLSecurityValidator(
    private val deviceCapabilities: WGSLDeviceCapabilities = WGSLDeviceCapabilities(),
) {
    fun validateSecurity(source: String): WGSLSecurityValidationReport =
        TODO("Wire WGSLSecurityValidator.validateSecurity to wgsl4k AST + blocked-feature checks + resource limits")

    companion object {
        const val MAX_CUSTOM_UNIFORMS = 16
        const val MAX_CUSTOM_TEXTURES = 8
        const val MAX_CUSTOM_BIND_GROUPS = 4
        const val MAX_CUSTOM_BINDINGS_PER_GROUP = 8
        const val MAX_CUSTOM_UNIFORM_BUFFER_SIZE = 16 * 1024
        const val MAX_CUSTOM_STORAGE_BUFFER_SIZE = 64 * 1024
    }
}
