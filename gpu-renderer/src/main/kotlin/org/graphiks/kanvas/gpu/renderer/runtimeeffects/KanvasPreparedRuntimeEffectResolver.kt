package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildRole
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildSlot
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectSourceColorContract
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformField
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformType
import org.graphiks.kanvas.gpu.renderer.materials.preparedRuntimeEffectChildAbiHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformBlockSizeBytes
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTUniformBlockSizeBytes
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.hasMaterialColorFunctionSignature
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectionFactsHash
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.kanvas.gpu.renderer.wgsl.wgslModuleContentHash
import org.graphiks.kanvas.gpu.renderer.wgsl.wgslSourceContentHash
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
                GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.CpuUnavailable,
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
                GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                    validation.message,
                    GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslUnavailable,
                )
            is GPUPreparedRuntimeEffectProgramValidation.Invalid ->
                GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                    validation.message,
                    validation.reason.toProgramUnavailableReason(),
                )
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
    private val simpleRTDescriptor = SimpleRTDescriptor.createDescriptor()
    private val simpleRTSourceColorContract =
        requireNotNull(simpleRTDescriptor.sourceColorContract) {
            "SimpleRT descriptor must register a prepared source color contract"
        }
    private val linearGradientRTDescriptor = LinearGradientRTDescriptor.createDescriptor()
    private val linearGradientRTSourceColorContract =
        requireNotNull(linearGradientRTDescriptor.sourceColorContract) {
            "LinearGradientRT descriptor must register a prepared source color contract"
        }

    private val candidates = mapOf(
        RuntimeEffectProgramKey(
            id = simpleRTDescriptor.id,
            version = simpleRTDescriptor.version,
        ) to KanvasPreparedRuntimeEffectProgramCandidate(
            program = GPUPreparedRuntimeEffectProgram(
                effectId = simpleRTDescriptor.id.value,
                descriptorVersion = simpleRTDescriptor.version.value,
                wgslSource = SimpleRTWgsl,
                sourceFunction = simpleRTDescriptor.wgslPlan.entryPoint,
                sourceColorContract = simpleRTSourceColorContract,
                sourceHash = SimpleRTSourceHash,
                moduleHash = preparedRuntimeEffectModuleContractHash(
                    wgslModuleHash = simpleRTDescriptor.wgslPlan.moduleHash,
                    sourceColorContract = simpleRTSourceColorContract,
                    childSlots = emptyList(),
                ),
                reflectionHash = preparedRuntimeEffectReflectionContractHash(
                    reflectedAbiHash = simpleRTDescriptor.wgslPlan.reflectionHash,
                    childSlots = emptyList(),
                ),
                uniformSchemaHash = simpleRTDescriptor.uniformSchema.schemaHash,
                uniformBlockSizeBytes = simpleRTDescriptor.uniformBlockPlan.blockSizeBytes.toInt(),
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
                bindingPlanHash = preparedRuntimeEffectBindingContractHash(
                    descriptorBindingPlanHash = simpleRTDescriptor.resources.bindingPlanHash,
                    sourceColorContract = simpleRTSourceColorContract,
                    childSlots = emptyList(),
                ),
                routeContractHash = preparedRuntimeEffectRouteContractHash(
                    descriptor = simpleRTDescriptor,
                    sourceColorContract = simpleRTSourceColorContract,
                ),
                childSlots = emptyList(),
            ),
            cpuOracle = SimpleRTCPUOracle,
        ),
        RuntimeEffectProgramKey(
            id = linearGradientRTDescriptor.id,
            version = linearGradientRTDescriptor.version,
        ) to KanvasPreparedRuntimeEffectProgramCandidate(
            program = GPUPreparedRuntimeEffectProgram(
                effectId = linearGradientRTDescriptor.id.value,
                descriptorVersion = linearGradientRTDescriptor.version.value,
                wgslSource = LinearGradientRTWgsl,
                sourceFunction = linearGradientRTDescriptor.wgslPlan.entryPoint,
                sourceColorContract = linearGradientRTSourceColorContract,
                sourceHash = LinearGradientRTSourceHash,
                moduleHash = preparedRuntimeEffectModuleContractHash(
                    wgslModuleHash = linearGradientRTDescriptor.wgslPlan.moduleHash,
                    sourceColorContract = linearGradientRTSourceColorContract,
                    childSlots = emptyList(),
                ),
                reflectionHash = preparedRuntimeEffectReflectionContractHash(
                    reflectedAbiHash = linearGradientRTDescriptor.wgslPlan.reflectionHash,
                    childSlots = emptyList(),
                ),
                uniformSchemaHash = linearGradientRTDescriptor.uniformSchema.schemaHash,
                uniformBlockSizeBytes = LinearGradientRTUniformBlockSizeBytes,
                uniformFields = listOf(
                    runtimeUniformField("start", 0),
                    runtimeUniformField("end", 16),
                    runtimeUniformField("startColor", 32),
                    runtimeUniformField("endColor", 48),
                ),
                bindings = listOf(
                    GPUPreparedRuntimeEffectBinding(
                        group = 1,
                        binding = 0,
                        resourceKind = "uniformBuffer",
                        minBindingSizeBytes = LinearGradientRTUniformBlockSizeBytes,
                    ),
                ),
                bindingPlanHash = preparedRuntimeEffectBindingContractHash(
                    descriptorBindingPlanHash = linearGradientRTDescriptor.resources.bindingPlanHash,
                    sourceColorContract = linearGradientRTSourceColorContract,
                    childSlots = emptyList(),
                ),
                routeContractHash = preparedRuntimeEffectRouteContractHash(
                    descriptor = linearGradientRTDescriptor,
                    sourceColorContract = linearGradientRTSourceColorContract,
                ),
                childSlots = emptyList(),
            ),
            cpuOracle = LinearGradientRTCPUOracle,
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
    data class Invalid(
        val message: String,
        val reason: InvalidReason = InvalidReason.Unknown,
    ) : GPUPreparedRuntimeEffectProgramValidation
}

internal enum class InvalidReason { CpuOracle, WgslValidation, Abi, Unknown }

private fun InvalidReason.toProgramUnavailableReason():
    GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason = when (this) {
        InvalidReason.CpuOracle -> GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.CpuUnavailable
        InvalidReason.WgslValidation -> GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslValidation
        InvalidReason.Abi -> GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.Abi
        InvalidReason.Unknown -> GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.Unknown
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
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(message, InvalidReason.Abi)
        }
        val expectedSourceHash = wgslSourceContentHash(program.wgslSource)
        val expectedWgslModuleHash = wgslModuleContentHash(
            source = program.wgslSource,
            sourceFunction = program.sourceFunction,
        )
        if (program.sourceHash != expectedSourceHash) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect source hash is not derived from WGSL content",
                InvalidReason.WgslValidation,
            )
        }
        val expectedModuleHash = preparedRuntimeEffectModuleContractHash(
            wgslModuleHash = expectedWgslModuleHash,
            sourceColorContract = program.sourceColorContract,
            childSlots = program.childSlots,
        )
        if (
            program.moduleHash != expectedModuleHash ||
            descriptor.wgslPlan.moduleHash != expectedWgslModuleHash
        ) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect module hash is not derived from WGSL content",
                InvalidReason.WgslValidation,
            )
        }
        val oracle = runCatching { cpuOracle.evaluate() }.getOrElse { failure ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect CPU behavior failed: ${failure::class.simpleName.orEmpty()}",
                InvalidReason.CpuOracle,
            )
        }
        if (oracle.effectId != descriptor.id) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect CPU behavior does not match the descriptor",
                InvalidReason.CpuOracle,
            )
        }
        validateMaterialCPUBehavior(descriptor, cpuOracle)?.let { message ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(message, InvalidReason.CpuOracle)
        }

        val (lowered, report) = try {
            beforeParserUse()
            val parsed = parseWgslResult(program.wgslSource)
            if (!parsed.isSuccess) {
                return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                    "Runtime-effect WGSL parser diagnostics: " +
                        parsed.errors.joinToString { it.message },
                    InvalidReason.WgslValidation,
                )
            }
            val lowered = Lowerer().lower(parsed.translationUnit)
            lowered to lowered.reflectWgslModule(sourceId = program.sourceHash)
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
                InvalidReason.WgslValidation,
            )
        }

        if (!lowered.hasMaterialColorFunctionSignature(program.sourceFunction)) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect WGSL does not prove its registered source function signature",
                InvalidReason.WgslValidation,
            )
        }
        reflectedAbiMismatch(program, descriptor, report)?.let { message ->
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(message, InvalidReason.Abi)
        }
        val reflectedHash = report.reflectionFactsHash()
        val reflectedContractHash = preparedRuntimeEffectReflectionContractHash(
            reflectedAbiHash = reflectedHash,
            childSlots = program.childSlots,
        )
        if (
            program.reflectionHash != reflectedContractHash ||
            descriptor.wgslPlan.reflectionHash != reflectedHash
        ) {
            return GPUPreparedRuntimeEffectProgramValidation.Invalid(
                "Runtime-effect reflection hash is not derived from reflected ABI facts",
                InvalidReason.Abi,
            )
        }
        return GPUPreparedRuntimeEffectProgramValidation.Valid
    }
}

private fun validateMaterialCPUBehavior(
    descriptor: GPURuntimeEffectDescriptor,
    cpuOracle: GPURuntimeEffectCPUOracle,
): String? {
    val fixtures = when (descriptor.id) {
        SimpleRTDescriptor.effectId -> listOf(
            MaterialBehaviorFixture(
                uniformValues = listOf(0.125f, 0.375f, 0.625f, 0.875f),
                localPositionX = 0.25f,
                localPositionY = 0.75f,
                expectedColor = listOf(0.125f, 0.375f, 0.625f, 0.875f),
            ),
            MaterialBehaviorFixture(
                uniformValues = listOf(0.9f, 0.7f, 0.3f, 0.1f),
                localPositionX = 0.25f,
                localPositionY = 0.75f,
                expectedColor = listOf(0.9f, 0.7f, 0.3f, 0.1f),
            ),
        )
        LinearGradientRTDescriptor.effectId -> listOf(
            MaterialBehaviorFixture(
                uniformValues = listOf(
                    0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    1f, 0f, 0f, 1f,
                    0f, 0f, 1f, 1f,
                ),
                localPositionX = 0.5f,
                localPositionY = 0.25f,
                expectedColor = listOf(0.75f, 0f, 0.25f, 1f),
            ),
        )
        else -> return "Runtime-effect CPU behavior has no registered validation fixtures"
    }
    for (fixture in fixtures) {
        val uniformBytes = ByteBuffer.allocate(fixture.uniformValues.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { fixture.uniformValues.forEach(::putFloat) }
            .array()
        val result = runCatching {
            cpuOracle.evaluateMaterial(
                GPURuntimeEffectMaterialEvaluationInput(
                    uniformBytes = uniformBytes,
                    localPositionX = fixture.localPositionX,
                    localPositionY = fixture.localPositionY,
                ),
            )
        }.getOrElse { failure ->
            return "Runtime-effect material CPU behavior failed: ${failure::class.simpleName.orEmpty()}"
        }
        val color = result as? GPURuntimeEffectMaterialEvaluationResult.Color
            ?: return "Runtime-effect material CPU behavior is unavailable"
        if (
            listOf(color.r, color.g, color.b, color.a).map(Float::toRawBits) !=
            fixture.expectedColor.map(Float::toRawBits)
        ) {
            return "Runtime-effect material CPU behavior does not match registered fixtures"
        }
        if (!SHA256_IDENTITY.matches(color.evidenceHash)) {
            return "Runtime-effect material CPU evidence is not content-derived"
        }
    }
    return null
}

private data class MaterialBehaviorFixture(
    val uniformValues: List<Float>,
    val localPositionX: Float,
    val localPositionY: Float,
    val expectedColor: List<Float>,
)

private fun runtimeUniformField(name: String, offsetBytes: Int): GPUPreparedRuntimeEffectUniformField =
    GPUPreparedRuntimeEffectUniformField(
        name = name,
        type = GPUPreparedRuntimeEffectUniformType.Float4,
        offsetBytes = offsetBytes,
        sizeBytes = 16,
        alignmentBytes = 16,
    )

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
    val registeredSourceColorContract = descriptor.sourceColorContract
        ?: return "Runtime-effect descriptor has no registered source color contract"
    if (program.sourceColorContract != registeredSourceColorContract) {
        return "Runtime-effect source color contract does not match the descriptor"
    }
    val expectedChildSlots = reflectedChildSlots(descriptor)
        ?: return "Runtime-effect descriptor child slots do not define exact prepared roles"
    if (program.childSlots != expectedChildSlots) {
        return "Runtime-effect child slots do not match the descriptor schema"
    }
    val expectedModuleHash = preparedRuntimeEffectModuleContractHash(
        wgslModuleHash = descriptor.wgslPlan.moduleHash,
        sourceColorContract = registeredSourceColorContract,
        childSlots = expectedChildSlots,
    )
    val expectedReflectionHash = preparedRuntimeEffectReflectionContractHash(
        reflectedAbiHash = descriptor.wgslPlan.reflectionHash,
        childSlots = expectedChildSlots,
    )
    val expectedBindingPlanHash = preparedRuntimeEffectBindingContractHash(
        descriptorBindingPlanHash = descriptor.resources.bindingPlanHash,
        sourceColorContract = registeredSourceColorContract,
        childSlots = expectedChildSlots,
    )
    if (
        program.moduleHash != expectedModuleHash ||
        program.reflectionHash != expectedReflectionHash ||
        program.uniformSchemaHash != descriptor.uniformSchema.schemaHash ||
        program.bindingPlanHash != expectedBindingPlanHash ||
        program.routeContractHash != preparedRuntimeEffectRouteContractHash(
            descriptor = descriptor,
            sourceColorContract = registeredSourceColorContract,
            childSlots = expectedChildSlots,
        )
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

internal fun preparedRuntimeEffectRouteContractHash(
    descriptor: GPURuntimeEffectDescriptor,
    sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract,
    childSlots: List<GPUPreparedRuntimeEffectChildSlot> =
        requireNotNull(reflectedChildSlots(descriptor)) {
            "Runtime-effect descriptor child slots do not define an exact prepared schema"
        },
): String =
    CanonicalIdentityEncoder("prepared-runtime-effect-route-v5")
        .text("effectId", descriptor.id.value)
        .int("descriptorVersion", descriptor.version.value)
        .text("uniformSchemaHash", descriptor.uniformSchema.schemaHash)
        .text("bindingPlanHash", descriptor.resources.bindingPlanHash)
        .text("moduleHash", descriptor.wgslPlan.moduleHash)
        .text("sourceColorContract", sourceColorContract.name)
        .text("entryPoint", descriptor.wgslPlan.entryPoint)
        .text("reflectionHash", descriptor.wgslPlan.reflectionHash)
        .texts("childSlots", childSlots.preparedChildSlotFacts())
        .digestIdentity()

internal fun preparedRuntimeEffectModuleContractHash(
    wgslModuleHash: String,
    sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract,
    childSlots: List<GPUPreparedRuntimeEffectChildSlot> = emptyList(),
): String =
    CanonicalIdentityEncoder("prepared-runtime-effect-module-v4")
        .text("wgslModuleHash", wgslModuleHash)
        .text("sourceColorContract", sourceColorContract.name)
        .texts("childSlots", childSlots.preparedChildSlotFacts())
        .digestIdentity()

internal fun preparedRuntimeEffectReflectionContractHash(
    reflectedAbiHash: String,
    childSlots: List<GPUPreparedRuntimeEffectChildSlot> = emptyList(),
): String = CanonicalIdentityEncoder("prepared-runtime-effect-reflection-v1")
    .text("reflectedAbiHash", reflectedAbiHash)
    .texts("childSlots", childSlots.preparedChildSlotFacts())
    .digestIdentity()

internal fun preparedRuntimeEffectBindingContractHash(
    descriptorBindingPlanHash: String,
    sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract,
    childSlots: List<GPUPreparedRuntimeEffectChildSlot> = emptyList(),
): String =
    CanonicalIdentityEncoder("prepared-runtime-effect-bindings-v4")
        .text("descriptorBindingPlanHash", descriptorBindingPlanHash)
        .text("sourceColorContract", sourceColorContract.name)
        .texts("childSlots", childSlots.preparedChildSlotFacts())
        .digestIdentity()

private fun reflectedChildSlots(
    descriptor: GPURuntimeEffectDescriptor,
): List<GPUPreparedRuntimeEffectChildSlot>? {
    val names = descriptor.childSlots.map { slot -> slot.slotName }
    if (names.any(String::isBlank) || names.distinct().size != names.size) return null
    return descriptor.childSlots.mapIndexed { index, slot ->
        if (!slot.required) return null
        val role = when (slot.acceptedSourceKinds) {
            setOf("shader") -> GPUPreparedRuntimeEffectChildRole.Shader
            setOf("color-filter") -> GPUPreparedRuntimeEffectChildRole.ColorFilter
            setOf("blender") -> GPUPreparedRuntimeEffectChildRole.Blender
            else -> return null
        }
        GPUPreparedRuntimeEffectChildSlot(
            name = slot.slotName,
            role = role,
            bindingIndex = index,
            abiHash = preparedRuntimeEffectChildAbiHash(role),
        )
    }
}

private fun List<GPUPreparedRuntimeEffectChildSlot>.preparedChildSlotFacts(): List<String> =
    mapIndexed { index, slot ->
        "slot[$index]=${slot.name}:${slot.role.name}:${slot.bindingIndex}:${slot.abiHash}"
    }


private val DESCRIPTOR_FIELD = Regex("""^([^:]+):(.+)@(\d+):(\d+)$""")
private val SHA256_IDENTITY = Regex("""^sha256:[0-9a-f]{64}$""")
