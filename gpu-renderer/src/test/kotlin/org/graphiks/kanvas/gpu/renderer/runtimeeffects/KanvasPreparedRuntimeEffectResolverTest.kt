package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildRole
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectChildSlot
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectSourceColorContract
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformField
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformType
import org.graphiks.kanvas.gpu.renderer.materials.preparedRuntimeEffectChildAbiHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTUniformSchemaHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

class KanvasPreparedRuntimeEffectResolverTest {
    private val descriptor = SimpleRTDescriptor.createDescriptor()

    @Test
    fun `simple runtime CPU behavior decodes two nontrivial little endian colors`() {
        val first = assertIs<GPURuntimeEffectMaterialEvaluationResult.Color>(
            SimpleRTCPUOracle.evaluateMaterial(
                materialInput(0.125f, 0.375f, 0.625f, 0.875f),
            ),
        )
        val second = assertIs<GPURuntimeEffectMaterialEvaluationResult.Color>(
            SimpleRTCPUOracle.evaluateMaterial(
                materialInput(0.9f, 0.7f, 0.3f, 0.1f),
            ),
        )

        assertColorBits(listOf(0.125f, 0.375f, 0.625f, 0.875f), first)
        assertColorBits(listOf(0.9f, 0.7f, 0.3f, 0.1f), second)
        assertNotEquals(first.evidenceHash, second.evidenceHash)
    }

    @Test
    fun `simple runtime CPU behavior rejects wrong payload size and nonfinite channels`() {
        val wrongSize = SimpleRTCPUOracle.evaluateMaterial(
            GPURuntimeEffectMaterialEvaluationInput(
                uniformBytes = ByteArray(12),
                localPositionX = 0.25f,
                localPositionY = 0.75f,
            ),
        )
        val nonFinite = SimpleRTCPUOracle.evaluateMaterial(
            materialInput(Float.NaN, 0.25f, 0.5f, 1f),
        )

        assertEquals(
            GPURuntimeEffectMaterialEvaluationRefusal.PAYLOAD_SIZE,
            assertIs<GPURuntimeEffectMaterialEvaluationResult.Unsupported>(wrongSize).reason,
        )
        assertEquals(
            GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT,
            assertIs<GPURuntimeEffectMaterialEvaluationResult.Unsupported>(nonFinite).reason,
        )
    }

    @Test
    fun `canonical registered program has CPU behavior and parser reflected ABI`() {
        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Valid>(validation)
        assertEquals(
            descriptor.sourceColorContract,
            simpleProgram().sourceColorContract,
        )
    }

    @Test
    fun `runtime source color contract mismatch never exposes a ready program`() {
        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram().copy(
                sourceColorContract =
                    GPUPreparedRuntimeEffectSourceColorContract.LinearPremultipliedRgba,
            ),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(validation)
    }

    @Test
    fun `runtime source color contract contributes to module binding and route hashes`() {
        val straight = GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba
        val premultiplied =
            GPUPreparedRuntimeEffectSourceColorContract.LinearPremultipliedRgba

        assertNotEquals(
            preparedRuntimeEffectModuleContractHash(SimpleRTModuleHash, straight),
            preparedRuntimeEffectModuleContractHash(SimpleRTModuleHash, premultiplied),
        )
        assertNotEquals(
            preparedRuntimeEffectBindingContractHash(SimpleRTBindingPlanHash, straight),
            preparedRuntimeEffectBindingContractHash(SimpleRTBindingPlanHash, premultiplied),
        )
        assertNotEquals(
            preparedRuntimeEffectRouteContractHash(descriptor, straight),
            preparedRuntimeEffectRouteContractHash(descriptor, premultiplied),
        )
    }

    @Test
    fun `ordered registered child slots contribute to every prepared program hash`() {
        val slots = childSlots()
        val sourceColorContract = requireNotNull(descriptor.sourceColorContract)

        assertNotEquals(
            preparedRuntimeEffectModuleContractHash(SimpleRTModuleHash, sourceColorContract),
            preparedRuntimeEffectModuleContractHash(
                SimpleRTModuleHash,
                sourceColorContract,
                slots,
            ),
        )
        assertNotEquals(
            preparedRuntimeEffectReflectionContractHash(SimpleRTReflectionHash, emptyList()),
            preparedRuntimeEffectReflectionContractHash(SimpleRTReflectionHash, slots),
        )
        assertNotEquals(
            preparedRuntimeEffectBindingContractHash(
                SimpleRTBindingPlanHash,
                sourceColorContract,
            ),
            preparedRuntimeEffectBindingContractHash(
                SimpleRTBindingPlanHash,
                sourceColorContract,
                slots,
            ),
        )
        assertNotEquals(
            preparedRuntimeEffectRouteContractHash(descriptor, sourceColorContract),
            preparedRuntimeEffectRouteContractHash(
                descriptor.copy(childSlots = descriptorSlots()),
                sourceColorContract,
            ),
        )
    }

    @Test
    fun `validator admits exact ordered child slots and refuses order role and ABI mismatch`() {
        val registered = descriptor.copy(childSlots = descriptorSlots())
        val exact = childSlots()
        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(registeredDescriptor = registered, childSlots = exact),
            descriptor = registered,
            cpuOracle = SimpleRTCPUOracle,
        )
        val reordered = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(
                registeredDescriptor = registered,
                childSlots = exact.reversed(),
            ),
            descriptor = registered,
            cpuOracle = SimpleRTCPUOracle,
        )
        val wrongRole = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(
                registeredDescriptor = registered,
                childSlots = exact.toMutableList().apply {
                    this[0] = this[0].copy(role = GPUPreparedRuntimeEffectChildRole.Blender)
                },
            ),
            descriptor = registered,
            cpuOracle = SimpleRTCPUOracle,
        )
        val wrongAbi = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(
                registeredDescriptor = registered,
                childSlots = exact.toMutableList().apply {
                    this[0] = this[0].copy(abiHash = "sha256:${"f".repeat(64)}")
                },
            ),
            descriptor = registered,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Valid>(validation)
        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(reordered)
        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(wrongRole)
        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(wrongAbi)
    }

    @Test
    fun `parser unavailable and parser failure never expose a ready program`() {
        val unavailable = KanvasPreparedRuntimeEffectProgramValidator(
            beforeParserUse = { throw NoClassDefFoundError("wgsl4k unavailable") },
        ).validate(
            program = simpleProgram(),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )
        val parseFailure = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(wgslSource = "this is not WGSL"),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Unavailable>(unavailable)
        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(parseFailure)
    }

    @Test
    fun `entry point and reflected uniform ABI mismatches never expose a ready program`() {
        val entryMismatch = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(sourceFunction = "missing_source"),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )
        val reflectionMismatch = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(
                wgslSource = SimpleRTWgsl
                    .replace("gColor: vec4<f32>", "gColor: f32")
                    .replace(
                        "return uSimpleRT.gColor;",
                        "return vec4<f32>(uSimpleRT.gColor);",
                    ),
            ),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(entryMismatch)
        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(reflectionMismatch)
    }

    @Test
    fun `semantic WGSL mutation with unchanged ABI and copied labels is refused`() {
        val mutation = SimpleRTWgsl.replace(
            "return uSimpleRT.gColor;",
            "return vec4<f32>(uSimpleRT.gColor.bgra);",
        )

        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(wgslSource = mutation),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(validation)
    }

    @Test
    fun `copied false content and reflection hashes cannot expose a ready program`() {
        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(
                sourceHash = "sha256:${"0".repeat(64)}",
                moduleHash = "sha256:${"1".repeat(64)}",
                reflectionHash = "sha256:${"2".repeat(64)}",
            ),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Invalid>(validation)
    }

    private fun simpleProgram(
        registeredDescriptor: GPURuntimeEffectDescriptor = descriptor,
        childSlots: List<GPUPreparedRuntimeEffectChildSlot> = emptyList(),
        wgslSource: String = SimpleRTWgsl,
        sourceFunction: String = SimpleRTEntryPoint,
        sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract =
            GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
        sourceHash: String = SimpleRTSourceHash,
        moduleHash: String = preparedRuntimeEffectModuleContractHash(
            wgslModuleHash = SimpleRTModuleHash,
            sourceColorContract = sourceColorContract,
            childSlots = childSlots,
        ),
        reflectionHash: String = preparedRuntimeEffectReflectionContractHash(
            reflectedAbiHash = SimpleRTReflectionHash,
            childSlots = childSlots,
        ),
    ): GPUPreparedRuntimeEffectProgram =
        GPUPreparedRuntimeEffectProgram(
            effectId = registeredDescriptor.id.value,
            descriptorVersion = registeredDescriptor.version.value,
            wgslSource = wgslSource,
            sourceFunction = sourceFunction,
            sourceColorContract = sourceColorContract,
            sourceHash = sourceHash,
            moduleHash = moduleHash,
            reflectionHash = reflectionHash,
            uniformSchemaHash = SimpleRTUniformSchemaHash,
            uniformBlockSizeBytes = 16,
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
                    minBindingSizeBytes = 16,
                ),
            ),
            childSlots = childSlots,
            bindingPlanHash = preparedRuntimeEffectBindingContractHash(
                descriptorBindingPlanHash = SimpleRTBindingPlanHash,
                sourceColorContract = sourceColorContract,
                childSlots = childSlots,
            ),
            routeContractHash = preparedRuntimeEffectRouteContractHash(
                descriptor = registeredDescriptor,
                sourceColorContract = sourceColorContract,
            ),
        )

    private fun descriptorSlots(): List<GPURuntimeEffectChildSlotPlan> = listOf(
        GPURuntimeEffectChildSlotPlan("source", setOf("shader"), required = true),
        GPURuntimeEffectChildSlotPlan("filter", setOf("color-filter"), required = true),
        GPURuntimeEffectChildSlotPlan("blender", setOf("blender"), required = true),
    )

    private fun childSlots(): List<GPUPreparedRuntimeEffectChildSlot> = listOf(
        childSlot("source", GPUPreparedRuntimeEffectChildRole.Shader, 0),
        childSlot("filter", GPUPreparedRuntimeEffectChildRole.ColorFilter, 1),
        childSlot("blender", GPUPreparedRuntimeEffectChildRole.Blender, 2),
    )

    private fun childSlot(
        name: String,
        role: GPUPreparedRuntimeEffectChildRole,
        bindingIndex: Int,
    ): GPUPreparedRuntimeEffectChildSlot = GPUPreparedRuntimeEffectChildSlot(
        name = name,
        role = role,
        bindingIndex = bindingIndex,
        abiHash = preparedRuntimeEffectChildAbiHash(role),
    )

    private fun materialInput(
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ): GPURuntimeEffectMaterialEvaluationInput =
        GPURuntimeEffectMaterialEvaluationInput(
            uniformBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                putFloat(r)
                putFloat(g)
                putFloat(b)
                putFloat(a)
            }.array(),
            localPositionX = 0.25f,
            localPositionY = 0.75f,
        )

    private fun assertColorBits(
        expected: List<Float>,
        actual: GPURuntimeEffectMaterialEvaluationResult.Color,
    ) {
        assertEquals(
            expected.map(Float::toRawBits),
            listOf(actual.r, actual.g, actual.b, actual.a).map(Float::toRawBits),
        )
    }
}
