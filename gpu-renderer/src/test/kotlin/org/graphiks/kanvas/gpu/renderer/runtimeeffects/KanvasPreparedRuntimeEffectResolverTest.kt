package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectProgram
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
    fun `canonical registered program has CPU behavior and parser reflected ABI`() {
        val validation = KanvasPreparedRuntimeEffectProgramValidator().validate(
            program = simpleProgram(),
            descriptor = descriptor,
            cpuOracle = SimpleRTCPUOracle,
        )

        assertIs<GPUPreparedRuntimeEffectProgramValidation.Valid>(validation)
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

    private fun simpleProgram(
        wgslSource: String = SimpleRTWgsl,
        sourceFunction: String = SimpleRTEntryPoint,
    ): GPUPreparedRuntimeEffectProgram =
        GPUPreparedRuntimeEffectProgram(
            effectId = SimpleRTDescriptor.effectId.value,
            descriptorVersion = SimpleRTDescriptor.descriptorVersion.value,
            wgslSource = wgslSource,
            sourceFunction = sourceFunction,
            sourceHash = SimpleRTSourceHash,
            moduleHash = SimpleRTModuleHash,
            reflectionHash = SimpleRTReflectionHash,
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
            bindingPlanHash = SimpleRTBindingPlanHash,
            routeContractHash = "route:simple_rt:test",
        )
}
