package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/**
 * Concrete custom runtime-effect registry wired to wgsl4k validation and security checks.
 * Isolated from [KanvasRuntimeEffectRegistry]; does not share caches with registered effects.
 */
class KanvasCustomRuntimeEffectRegistry(
    private val securityValidator: WGSLSecurityValidator = WGSLSecurityValidator(),
    private val deviceCapabilities: WGSLDeviceCapabilities = WGSLDeviceCapabilities(),
) : GPUCustomRuntimeEffectRegistry {
    private val descriptors: MutableMap<GPUCustomRuntimeEffectID, GPUCustomRuntimeEffectDescriptor> = mutableMapOf()

    override fun register(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID> =
        TODO("Wire KanvasCustomRuntimeEffectRegistry.register to wgsl4k parse + securityValidator.validateSecurity + descriptor creation")

    override fun lookup(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = descriptors[id]

    override fun unregister(id: GPUCustomRuntimeEffectID) {
        descriptors.remove(id)
    }

    override fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = descriptors.containsKey(id)
}
