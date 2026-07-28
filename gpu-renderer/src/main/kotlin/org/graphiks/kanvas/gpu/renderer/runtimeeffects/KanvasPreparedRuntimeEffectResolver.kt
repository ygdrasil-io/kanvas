package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformField
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformType
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformBlockSizeBytes
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformSchemaHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

/**
 * Resolves prepared programs through the canonical descriptor registry and
 * module-owned Kanvas CPU/WGSL implementations.
 */
class KanvasPreparedRuntimeEffectResolver internal constructor(
    private val registry: GPURuntimeEffectRegistry,
    private val programAuthority: KanvasPreparedRuntimeEffectProgramAuthority,
    private val validator: KanvasPreparedRuntimeEffectProgramValidator,
) : GPUPreparedRuntimeEffectResolver {
    constructor() : this(
        registry = KanvasRuntimeEffectRegistry(),
        programAuthority = KanvasPreparedRuntimeEffectProgramAuthority(),
        validator = KanvasPreparedRuntimeEffectProgramValidator(),
    )

    override fun resolve(
        effectId: String,
        descriptorVersion: Int,
    ): GPUPreparedRuntimeEffectResolution {
        val id = runCatching { GPURuntimeEffectID(effectId) }.getOrNull()
            ?: return GPUPreparedRuntimeEffectResolution.DescriptorUnavailable(
                "Runtime-effect descriptor ID must not be blank",
            )
        val descriptor = registry.lookup(id)
            ?: return GPUPreparedRuntimeEffectResolution.DescriptorUnavailable(
                "Runtime-effect descriptor is not registered",
            )
        if (descriptor.version.value != descriptorVersion) {
            return GPUPreparedRuntimeEffectResolution.DescriptorUnavailable(
                "Runtime-effect descriptor version does not match the registry",
            )
        }
        val candidate = programAuthority.lookup(id, descriptor.version)
            ?: return GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "Registered runtime effect has no proven Kanvas CPU/WGSL program",
            )

        return when (
            val validation = validator.validate(
                program = candidate.program,
                descriptor = descriptor,
                cpuOracle = candidate.cpuOracle,
            )
        ) {
            is GPUPreparedRuntimeEffectProgramValidation.Valid ->
                GPUPreparedRuntimeEffectResolution.Ready(candidate.program)
            is GPUPreparedRuntimeEffectProgramValidation.Unavailable ->
                GPUPreparedRuntimeEffectResolution.ProgramUnavailable(validation.message)
            is GPUPreparedRuntimeEffectProgramValidation.Invalid ->
                GPUPreparedRuntimeEffectResolution.ProgramUnavailable(validation.message)
        }
    }
}

internal data class KanvasPreparedRuntimeEffectProgramCandidate(
    val program: GPUPreparedRuntimeEffectProgram,
    val cpuOracle: GPURuntimeEffectCPUOracle,
)

/**
 * Keyed executable-program authority. This is not a descriptor registry: every
 * candidate is still verified against [GPURuntimeEffectRegistry] at lookup.
 */
internal class KanvasPreparedRuntimeEffectProgramAuthority {
    private val candidates = mapOf(
        RuntimeEffectProgramKey(
            id = SimpleRTDescriptor.effectId,
            version = SimpleRTDescriptor.descriptorVersion,
        ) to KanvasPreparedRuntimeEffectProgramCandidate(
            program = GPUPreparedRuntimeEffectProgram(
                effectId = SimpleRTDescriptor.effectId.value,
                descriptorVersion = SimpleRTDescriptor.descriptorVersion.value,
                wgslSource = SimpleRTWgsl,
                sourceFunction = SimpleRTEntryPoint,
                sourceHash = SimpleRTSourceHash,
                moduleHash = SimpleRTModuleHash,
                reflectionHash = SimpleRTReflectionHash,
                uniformSchemaHash = SimpleRTUniformSchemaHash,
                uniformBlockSizeBytes = SimpleRTUniformBlockSizeBytes,
                uniformFields = listOf(
                    GPUPreparedRuntimeEffectUniformField(
                        name = "gColor",
                        type = GPUPreparedRuntimeEffectUniformType.Float4,
                        offsetBytes = 0,
                        sizeBytes = 16,
                        alignmentBytes = 16,
                    ),
                ),
                bindings = listOf(
                    GPUPreparedRuntimeEffectBinding(
                        group = 1,
                        binding = 0,
                        resourceKind = "uniformBuffer",
                        minBindingSizeBytes = SimpleRTUniformBlockSizeBytes,
                    ),
                ),
                bindingPlanHash = SimpleRTBindingPlanHash,
                routeContractHash = preparedRuntimeEffectRouteContractHash(
                    descriptor = SimpleRTDescriptor.createDescriptor(),
                ),
            ),
            cpuOracle = SimpleRTCPUOracle,
        ),
    )

    fun lookup(
        id: GPURuntimeEffectID,
        version: GPURuntimeEffectDescriptorVersion,
    ): KanvasPreparedRuntimeEffectProgramCandidate? =
        candidates[RuntimeEffectProgramKey(id, version)]
}

internal data class RuntimeEffectProgramKey(
    val id: GPURuntimeEffectID,
    val version: GPURuntimeEffectDescriptorVersion,
)

internal sealed interface GPUPreparedRuntimeEffectProgramValidation {
    data object Valid : GPUPreparedRuntimeEffectProgramValidation
    data class Unavailable(val message: String) : GPUPreparedRuntimeEffectProgramValidation
    data class Invalid(val message: String) : GPUPreparedRuntimeEffectProgramValidation
}

/** Parser/reflection gate used only by the module-owned executable authority. */
internal class KanvasPreparedRuntimeEffectProgramValidator internal constructor(
    private val beforeParserUse: () -> Unit = {},
) {
    fun validate(
        program: GPUPreparedRuntimeEffectProgram,
        descriptor: GPURuntimeEffectDescriptor,
        cpuOracle: GPURuntimeEffectCPUOracle,
    ): GPUPreparedRuntimeEffectProgramValidation {
        descriptorProgramMismatch(program, descriptor)?.let { message ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(message)
        }
        val oracle = runCatching { cpuOracle.evaluate() }.getOrElse { failure ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect CPU behavior failed: ${failure::class.simpleName.orEmpty()}",
            )
        }
        if (oracle.effectId != descriptor.id) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect CPU behavior does not match the descriptor",
            )
        }

        val report = try {
            beforeParserUse()
            val parsed = parseWgslResult(program.wgslSource)
            if (!parsed.isSuccess) {
                return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                    "Runtime-effect WGSL parser diagnostics: " +
                        parsed.errors.joinToString { it.message },
                )
            }
            val lowered = Lowerer().lower(parsed.translationUnit)
            lowered.reflectWgslModule(sourceId = program.sourceHash)
        } catch (_: NoClassDefFoundError) {
            return GPUPreparedRuntimeEffectProgramValidation.Unavailable(
                "wgsl4k parser/reflection is unavailable",
            )
        } catch (_: ClassNotFoundException) {
            return GPUPreparedRuntimeEffectProgramValidation.Unavailable(
                "wgsl4k parser/reflection is unavailable",
            )
        } catch (failure: Throwable) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect WGSL parser/reflection failed: " +
                    failure::class.simpleName.orEmpty(),
            )
        }

        if (!sourceDeclaresFunction(program.wgslSource, program.sourceFunction)) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect WGSL does not declare its registered source function",
            )
        }
        reflectedAbiMismatch(program, descriptor, report)?.let { message ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(message)
        }
        return GPUPreparedRuntimeEffectProgramValidation.Valid
    }
}

private fun descriptorProgramMismatch(
    program: GPUPreparedRuntimeEffectProgram,
    descriptor: GPURuntimeEffectDescriptor,
): String? {
    if (
        program.effectId != descriptor.id.value ||
        program.descriptorVersion != descriptor.version.value
    ) {
        return "Runtime-effect program identity does not match the descriptor"
    }
    if (program.sourceFunction != descriptor.wgslPlan.entryPoint) {
        return "Runtime-effect source function does not match the descriptor entry point"
    }
    if (
        program.moduleHash != descriptor.wgslPlan.moduleHash ||
        program.reflectionHash != descriptor.wgslPlan.reflectionHash ||
        program.uniformSchemaHash != descriptor.uniformSchema.schemaHash ||
        program.bindingPlanHash != descriptor.resources.bindingPlanHash
    ) {
        return "Runtime-effect registered hashes do not match the descriptor"
    }
    if (
        !descriptor.routeContract.nativeSupported ||
        descriptor.routeContract.cpuOracleOnly ||
        GPURuntimeEffectRoutePlacement.MaterialSource !in
        descriptor.routeContract.acceptedPlacements
    ) {
        return "Runtime-effect descriptor does not admit native material-source placement"
    }
    if (descriptor.uniformBlockPlan.blockSizeBytes != program.uniformBlockSizeBytes.toLong()) {
        return "Runtime-effect uniform block size does not match the descriptor"
    }
    val descriptorFields = descriptor.uniformSchema.fields.mapNotNull(::parseDescriptorField)
    if (
        descriptorFields.size != descriptor.uniformSchema.fields.size ||
        descriptorFields.map { it.name to it.type } !=
        program.uniformFields.map { it.name to it.type } ||
        descriptorFields.map { it.offsetBytes to it.sizeBytes } !=
        program.uniformFields.map { it.offsetBytes to it.sizeBytes }
    ) {
        return "Runtime-effect uniform fields do not match the descriptor schema"
    }
    val resourceLabels = program.bindings.map { binding ->
        "group${binding.group}.binding${binding.binding}.${binding.resourceKind}"
    }
    if (resourceLabels != descriptor.resources.resourceLabels) {
        return "Runtime-effect resource topology does not match the descriptor"
    }
    return null
}

private fun reflectedAbiMismatch(
    program: GPUPreparedRuntimeEffectProgram,
    descriptor: GPURuntimeEffectDescriptor,
    report: org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport,
): String? {
    val reflectedBindings = report.bindings.map { binding ->
        GPUPreparedRuntimeEffectBinding(
            group = binding.group,
            binding = binding.binding,
            resourceKind = binding.resourceKind,
            minBindingSizeBytes = binding.minBindingSize,
        )
    }
    if (reflectedBindings != program.bindings) {
        return "Runtime-effect reflected resource bindings do not match the registered ABI"
    }
    val uniformLayouts = report.layouts.filter { it.addressSpace == "uniform" }
    if (uniformLayouts.size != 1) {
        return "Runtime-effect WGSL must reflect exactly one uniform layout"
    }
    val layout = uniformLayouts.single()
    if (layout.size != program.uniformBlockSizeBytes) {
        return "Runtime-effect reflected uniform size does not match the registered ABI"
    }
    val reflectedFields = layout.members.mapNotNull { member ->
        val type = member.type.toPreparedUniformType() ?: return@mapNotNull null
        GPUPreparedRuntimeEffectUniformField(
            name = member.name,
            type = type,
            offsetBytes = member.offset,
            sizeBytes = member.size,
            alignmentBytes = member.alignment,
            strideBytes = member.stride,
        )
    }
    if (reflectedFields.size != layout.members.size || reflectedFields != program.uniformFields) {
        return "Runtime-effect reflected uniform fields do not match the registered ABI"
    }
    if (descriptor.uniformBlockPlan.schema != descriptor.uniformSchema) {
        return "Runtime-effect descriptor uniform plans are internally inconsistent"
    }
    return null
}

private fun parseDescriptorField(field: String): GPUPreparedRuntimeEffectUniformField? {
    val match = DESCRIPTOR_FIELD.matchEntire(field) ?: return null
    val type = match.groupValues[2].toPreparedUniformType() ?: return null
    return GPUPreparedRuntimeEffectUniformField(
        name = match.groupValues[1],
        type = type,
        offsetBytes = match.groupValues[3].toIntOrNull() ?: return null,
        sizeBytes = match.groupValues[4].toIntOrNull() ?: return null,
        alignmentBytes = type.requiredAlignmentBytes,
    )
}

private fun String.toPreparedUniformType(): GPUPreparedRuntimeEffectUniformType? =
    when (this) {
        "f32" -> GPUPreparedRuntimeEffectUniformType.Float1
        "vec2<f32>" -> GPUPreparedRuntimeEffectUniformType.Float2
        "vec3<f32>" -> GPUPreparedRuntimeEffectUniformType.Float3
        "vec4<f32>" -> GPUPreparedRuntimeEffectUniformType.Float4
        "i32" -> GPUPreparedRuntimeEffectUniformType.Int1
        "mat3x3<f32>" -> GPUPreparedRuntimeEffectUniformType.Matrix3x3
        "mat4x4<f32>" -> GPUPreparedRuntimeEffectUniformType.Matrix4x4
        else -> null
    }

private val GPUPreparedRuntimeEffectUniformType.requiredAlignmentBytes: Int
    get() = when (this) {
        GPUPreparedRuntimeEffectUniformType.Float1,
        GPUPreparedRuntimeEffectUniformType.Int1,
        -> 4
        GPUPreparedRuntimeEffectUniformType.Float2 -> 8
        GPUPreparedRuntimeEffectUniformType.Float3,
        GPUPreparedRuntimeEffectUniformType.Float4,
        GPUPreparedRuntimeEffectUniformType.Matrix3x3,
        GPUPreparedRuntimeEffectUniformType.Matrix4x4,
        -> 16
    }

private fun sourceDeclaresFunction(source: String, function: String): Boolean =
    Regex("""\bfn\s+${Regex.escape(function)}\s*\(""").containsMatchIn(source)

private fun preparedRuntimeEffectRouteContractHash(
    descriptor: GPURuntimeEffectDescriptor,
): String {
    val preimage = buildList {
        add("prepared-runtime-effect-route-v1")
        add("effect=${descriptor.id.value}@${descriptor.version.value}")
        add("uniform=${descriptor.uniformSchema.schemaHash}")
        add("bindings=${descriptor.resources.bindingPlanHash}")
        add("module=${descriptor.wgslPlan.moduleHash}")
        add("entry=${descriptor.wgslPlan.entryPoint}")
        add("reflection=${descriptor.wgslPlan.reflectionHash}")
    }.joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(preimage.encodeToByteArray())
    return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}

private val DESCRIPTOR_FIELD = Regex("""^([^:]+):(.+)@(\d+):(\d+)$""")
