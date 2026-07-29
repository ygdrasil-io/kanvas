package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectSourceColorContract
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformField
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectUniformType
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
            GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
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
        wgslSource: String = SimpleRTWgsl,
        sourceFunction: String = SimpleRTEntryPoint,
        sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract =
            GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
        sourceHash: String = SimpleRTSourceHash,
        moduleHash: String = preparedRuntimeEffectModuleContractHash(
            wgslModuleHash = SimpleRTModuleHash,
            sourceColorContract = sourceColorContract,
        ),
        reflectionHash: String = SimpleRTReflectionHash,
    ): GPUPreparedRuntimeEffectProgram =
        GPUPreparedRuntimeEffectProgram(
            effectId = SimpleRTDescriptor.effectId.value,
            descriptorVersion = SimpleRTDescriptor.descriptorVersion.value,
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
            bindingPlanHash = preparedRuntimeEffectBindingContractHash(
                descriptorBindingPlanHash = SimpleRTBindingPlanHash,
                sourceColorContract = sourceColorContract,
            ),
            routeContractHash = preparedRuntimeEffectRouteContractHash(
                descriptor = descriptor,
                sourceColorContract = sourceColorContract,
            ),
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
