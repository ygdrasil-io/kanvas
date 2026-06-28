package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/**
 * Concrete custom runtime-effect registry wired to wgsl4k validation and security checks.
 * Isolated from [KanvasRuntimeEffectRegistry]; does not share caches with registered effects.
 */
class KanvasCustomRuntimeEffectRegistry(
    private val wgslValidator: WGSLValidator,
    private val reflectionProvider: WGSLReflectionProvider,
    private val securityValidator: WGSLSecurityValidator = WGSLSecurityValidator(),
    private val deviceCapabilities: WGSLDeviceCapabilities = WGSLDeviceCapabilities(),
) : GPUCustomRuntimeEffectRegistry {
    private val descriptors: MutableMap<GPUCustomRuntimeEffectID, GPUCustomRuntimeEffectDescriptor> = mutableMapOf()

    override fun registerCustomEffect(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID> =
        TODO("Wire KanvasCustomRuntimeEffectRegistry.registerCustomEffect to wgslValidator.parse + securityValidator.validateSecurity + descriptor creation")

    override fun lookup(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = descriptors[id]

    override fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) {
        descriptors.remove(id)
    }

    override fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = descriptors.containsKey(id)
}

/** Stub for wgsl4k WGSL parser/validator consumed by KanvasCustomRuntimeEffectRegistry. */
interface WGSLValidator {
    fun parse(source: String): WGSLParsedModule =
        TODO("Wire WGSLValidator to wgsl4k parser")
}

/** Stub for wgsl4k reflection provider consumed by KanvasCustomRuntimeEffectRegistry. */
interface WGSLReflectionProvider {
    fun reflect(module: WGSLParsedModule): WGSLReflectionResult =
        TODO("Wire WGSLReflectionProvider to wgsl4k reflection")
}

/** Stub for wgsl4k reflection result — hash-based placeholder until wgsl4k integration. */
data class WGSLReflectionResult(
    val moduleHash: String,
    val entryPoint: String,
    val uniformCount: Int,
    val textureCount: Int,
    val bindGroupCount: Int,
    val reflectionHash: String,
)
