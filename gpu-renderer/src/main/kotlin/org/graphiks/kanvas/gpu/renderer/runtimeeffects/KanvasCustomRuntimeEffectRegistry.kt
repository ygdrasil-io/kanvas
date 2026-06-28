package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/**
 * Concrete custom runtime-effect registry wired to wgsl4k validation and security checks.
 * Isolated from [KanvasRuntimeEffectRegistry]; does not share caches with registered effects.
 */
class KanvasCustomRuntimeEffectRegistry(
    private val wgslValidator: WGSLValidator,
    private val reflectionProvider: WGSLReflectionProvider,
    /** Security validator wired to [WGSLSecurityContracts]; carries its own [WGSLDeviceCapabilities]. */
    private val securityValidator: WGSLSecurityValidator = WGSLSecurityValidator(),
    /** Deferred: not yet consumed by registry-level checks; security validator owns capability gating. */
    @Suppress("unused")
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
        val id = GPUCustomRuntimeEffectID.generate(source, uniformSchema.schemaHash, WGSLHashUtils.sha256(childSlotHash))

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
        val validationReportHash = WGSLHashUtils.sha256(
            securityReport.errors.joinToString("|") { "${it.code}:${it.severity}" }
        )
        val wgslPlan = GPUCustomRuntimeEffectWGSLPlan(
            source = source,
            entryPoint = reflection.entryPoint,
            moduleHash = reflection.moduleHash,
            reflectionHash = reflection.reflectionHash,
            validationReportHash = validationReportHash,
        )

        val resourceLabels = buildList<String> {
            for (g in 0 until reflection.bindGroupCount) {
                add("group$g.binding0.uniformBuffer")
            }
        }
        val resources = GPURuntimeEffectResourcePlan(
            resourceLabels = resourceLabels.ifEmpty { listOf("group0.binding0.uniformBuffer") },
            bindingPlanHash = "binding:custom:${id.value}:groups=${reflection.bindGroupCount}:uniforms=${reflection.uniformCount}",
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

    override fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = descriptors[id]

    override fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) {
        descriptors.remove(id)
    }

    override fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = descriptors.containsKey(id)

    internal fun forceSetDescriptor(id: GPUCustomRuntimeEffectID, descriptor: GPUCustomRuntimeEffectDescriptor) {
        descriptors[id] = descriptor
    }
}

/** Validates WGSL source and produces a parsed module carrying resource and feature information. */
interface WGSLValidator {
    /** Parses the WGSL [source] into a [WGSLParsedModule] with syntax errors and resource usage. */
    fun parse(source: String): WGSLParsedModule
}

/** Reflects on a parsed WGSL module to extract entry point, uniform/texture counts, and layout metadata. */
interface WGSLReflectionProvider {
    /** Reflects [module] into a [WGSLReflectionResult] carrying layout and entry point metadata. */
    fun reflect(module: WGSLParsedModule): WGSLReflectionResult
}

/** wgsl4k reflection result for a custom runtime-effect WGSL module.
 *  Contains hash-based module identity, entry point name, and resource counts
 *  derived from [WGSLReflectionProvider.reflect]. Fields are populated from
 *  live wgsl4k [Lowerer]/[Layouter] output when available, or from fixture
 *  defaults when wgsl4k is absent from the classpath. */
data class WGSLReflectionResult(
    val moduleHash: String,
    val entryPoint: String,
    val uniformCount: Int,
    val textureCount: Int,
    val bindGroupCount: Int,
    val reflectionHash: String,
)
