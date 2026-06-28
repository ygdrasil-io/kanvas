package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest

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
    ): Result<GPUCustomRuntimeEffectID> {
        val childSlotHash = childSlots.joinToString(",") { slot ->
            "${slot.slotName}:${slot.acceptedSourceKinds.sorted().joinToString("+")}"
        }
        val id = GPUCustomRuntimeEffectID.generate(source, uniformSchema.schemaHash, sha256(childSlotHash))

        val module = wgslValidator.parse(source)
        if (module.syntaxErrors.isNotEmpty()) {
            return Result.failure(
                GPUCustomRuntimeEffectValidationError(
                    code = "custom-wgsl.syntax-error",
                    message = "WGSL syntax error: ${module.syntaxErrors.joinToString()}",
                )
            )
        }

        val securityReport = securityValidator.validateSecurity(module)
        if (!securityReport.isSecure) {
            return Result.failure(
                GPUCustomRuntimeEffectValidationError(
                    code = securityReport.errors.first().code,
                    message = "Security validation failed: ${securityReport.errors.joinToString()}",
                )
            )
        }

        val reflection = reflectionProvider.reflect(module)
        val validationReportHash = sha256(
            securityReport.errors.joinToString("|") { "${it.code}:${it.severity}" }
        )
        val wgslPlan = GPUCustomRuntimeEffectWGSLPlan(
            source = source,
            entryPoint = reflection.entryPoint,
            moduleHash = reflection.moduleHash,
            reflectionHash = reflection.reflectionHash,
            validationReportHash = validationReportHash,
        )

        val resources = GPURuntimeEffectResourcePlan(
            resourceLabels = listOf("group0.binding0.uniformBuffer"),
            bindingPlanHash = "binding:custom:${id.value}",
        )

        val descriptor = GPUCustomRuntimeEffectDescriptor(
            id = id,
            uniformSchema = uniformSchema,
            childSlots = childSlots,
            resources = resources,
            wgslPlan = wgslPlan,
            sourceProvenance = sourceProvenance,
            validationStatus = GPUCustomRuntimeEffectValidationStatus.VALID,
        )

        descriptors[id] = descriptor
        return Result.success(id)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)

    override fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = descriptors[id]

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
