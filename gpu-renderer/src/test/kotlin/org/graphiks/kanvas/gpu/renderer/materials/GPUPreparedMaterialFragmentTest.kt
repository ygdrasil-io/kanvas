package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialColorContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialCoordinateContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialSampledBinding
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

class GPUPreparedMaterialFragmentTest {
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-material:v1",
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )

    @Test
    fun `every accepted prepared material exposes one premultiplied composable fragment`() {
        acceptedDescriptors().forEach { descriptor ->
            val ready = compile(descriptor, paintAlpha = 0.5f)

            assertEquals(
                GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
                ready.composableFragment.colorContract,
                descriptor.kind.toString(),
            )
            assertEquals(
                GPUPreparedMaterialCoordinateContract.LocalPosition2D,
                ready.composableFragment.coordinateContract,
                descriptor.kind.toString(),
            )
            assertEquals(
                "kanvas_evaluate_material",
                ready.composableFragment.evaluationFunction,
                descriptor.kind.toString(),
            )
            val fragmentSource = ready.composableFragment.declarationsWgsl +
                "\n\n" +
                ready.composableFragment.evaluationFunctionWgsl
            val parsed = KanvasWGSLValidator().parse(fragmentSource)
            assertTrue(
                parsed.syntaxErrors.isEmpty(),
                "${descriptor.kind}: ${parsed.syntaxErrors.joinToString()}",
            )
        }
    }

    @Test
    fun `uniform values and paint alpha do not alter fragment ABI`() {
        val first = compile(solid(r = 0.25f), paintAlpha = 0.5f)
        val second = compile(solid(r = 0.75f), paintAlpha = 0.25f)

        assertNotEquals(first.materialKey, second.materialKey)
        assertEquals(
            first.composableFragment.fragmentHash,
            second.composableFragment.fragmentHash,
        )
        assertEquals(
            first.composableFragment.abiHash,
            second.composableFragment.abiHash,
        )
    }

    @Test
    fun `texture content changes material identity but not fragment identity or ABI`() {
        val first = compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f)
        val second = compile(image(byteArrayOf(1, 2, 3, 5)), paintAlpha = 1f)

        assertNotEquals(first.materialKey, second.materialKey)
        assertEquals(
            first.composableFragment.fragmentHash,
            second.composableFragment.fragmentHash,
        )
        assertEquals(
            first.composableFragment.abiHash,
            second.composableFragment.abiHash,
        )
    }

    @Test
    fun `sampled resource topology changes the fragment ABI and uses canonical bindings`() {
        val unsampled = compile(solid(), paintAlpha = 1f).composableFragment
        val sampled = compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f)
            .composableFragment

        assertNotEquals(unsampled.abiHash, sampled.abiHash)
        assertEquals(emptyList(), unsampled.sampledBindings)
        assertEquals(
            listOf(
                GPUPreparedMaterialSampledBinding(
                    resourceIndex = 0,
                    textureBinding = 1,
                    samplerBinding = 2,
                ),
            ),
            sampled.sampledBindings,
        )
        assertEquals(1, sampled.uniformBinding?.group)
        assertEquals(0, sampled.uniformBinding?.binding)

        val parsed = KanvasWGSLValidator().parse(
            sampled.declarationsWgsl + "\n\n" + sampled.evaluationFunctionWgsl,
        )
        val report = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report)
        assertEquals(
            listOf(
                Triple(1, 0, "uniformBuffer"),
                Triple(1, 1, "sampledTexture"),
                Triple(1, 2, "sampler"),
            ),
            report.bindings.map { Triple(it.group, it.binding, it.resourceKind) },
        )
    }

    @Test
    fun `straight and premultiplied sources select their declared normalization`() {
        val straight = compile(registeredRuntimeEffect(), paintAlpha = 1f).composableFragment
        val premultiplied = compile(linearGradient(), paintAlpha = 1f).composableFragment

        assertTrue(
            "source.rgb * source.a" in straight.evaluationFunctionWgsl,
            straight.evaluationFunctionWgsl,
        )
        assertTrue(
            "return source;" in premultiplied.evaluationFunctionWgsl,
            premultiplied.evaluationFunctionWgsl,
        )
    }

    @Test
    fun `fragment constructor rejects noncanonical identities and binding topology`() {
        fun fragment(
            evaluationFunction: String = "kanvas_evaluate_material",
            fragmentHash: String = "0".repeat(64),
            abiHash: String = "sha256:${"1".repeat(64)}",
            sampledBindings: List<GPUPreparedMaterialSampledBinding> = emptyList(),
        ) = GPUPreparedMaterialFragment(
            declarationsWgsl = "fn kanvas_material_source(p: vec2<f32>) -> vec4<f32> { return vec4f(p, 0.0, 1.0); }",
            evaluationFunctionWgsl = "fn kanvas_evaluate_material(p: vec2<f32>) -> vec4<f32> { return kanvas_material_source(p); }",
            evaluationFunction = evaluationFunction,
            uniformBinding = null,
            sampledBindings = sampledBindings,
            colorContract = GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
            coordinateContract = GPUPreparedMaterialCoordinateContract.LocalPosition2D,
            fragmentHash = fragmentHash,
            abiHash = abiHash,
        )

        assertFailsWith<IllegalArgumentException> {
            fragment(evaluationFunction = "other")
        }
        assertFailsWith<IllegalArgumentException> {
            fragment(fragmentHash = "sha256:${"0".repeat(64)}")
        }
        assertFailsWith<IllegalArgumentException> {
            fragment(abiHash = "")
        }
        assertFailsWith<IllegalArgumentException> {
            fragment(
                sampledBindings = listOf(
                    GPUPreparedMaterialSampledBinding(
                        resourceIndex = 1,
                        textureBinding = 1,
                        samplerBinding = 2,
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            fragment(
                sampledBindings = listOf(
                    GPUPreparedMaterialSampledBinding(
                        resourceIndex = 0,
                        textureBinding = 1,
                        samplerBinding = 1,
                    ),
                ),
            )
        }
    }

    @Test
    fun `fragment snapshots sampled bindings`() {
        val bindings = mutableListOf(
            GPUPreparedMaterialSampledBinding(
                resourceIndex = 0,
                textureBinding = 1,
                samplerBinding = 2,
            ),
        )
        val fragment = GPUPreparedMaterialFragment(
            declarationsWgsl = "fn kanvas_material_source(p: vec2<f32>) -> vec4<f32> { return vec4f(p, 0.0, 1.0); }",
            evaluationFunctionWgsl = "fn kanvas_evaluate_material(p: vec2<f32>) -> vec4<f32> { return kanvas_material_source(p); }",
            evaluationFunction = "kanvas_evaluate_material",
            uniformBinding = null,
            sampledBindings = bindings,
            colorContract = GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
            coordinateContract = GPUPreparedMaterialCoordinateContract.LocalPosition2D,
            fragmentHash = "0".repeat(64),
            abiHash = "sha256:${"1".repeat(64)}",
        )

        bindings.clear()
        assertEquals(1, fragment.sampledBindings.size)
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (fragment.sampledBindings as MutableList<GPUPreparedMaterialSampledBinding>).clear()
            }.isFailure,
        )
    }

    private fun compile(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float,
    ): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(descriptor, paintAlpha, context),
            descriptor.toString(),
        ).program

    private fun acceptedDescriptors(): List<GPUMaterialDescriptor> = listOf(
        solid(),
        linearGradient(),
        radialGradient(),
        sweepGradient(),
        conicalGradient(),
        image(byteArrayOf(1, 2, 3, 4)),
        supportedBlend(),
        registeredRuntimeEffect(),
    )

    private fun solid(r: Float = 0.25f) =
        GPUMaterialDescriptor.SolidColor(r = r, g = 0.5f, b = 0.75f, a = 0.8f)

    private fun linearGradient() =
        GPUMaterialDescriptor.LinearGradient(
            startX = 0f,
            startY = 0f,
            endX = 32f,
            endY = 8f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 0.25f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 0.75f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 0.25f, 0f, 0f, 1f, 0.75f),
        )

    private fun radialGradient() =
        GPUMaterialDescriptor.RadialGradient(
            centerX = 8f,
            centerY = 9f,
            radius = 10f,
            startR = 1f,
            startG = 1f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 1f,
            endB = 1f,
            endA = 0.5f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 1f, 0f, 1f, 0f, 1f, 1f, 0.5f),
        )

    private fun sweepGradient() =
        GPUMaterialDescriptor.SweepGradient(
            centerX = 4f,
            centerY = 5f,
            startAngle = 0f,
            endAngle = 270f,
            startR = 1f,
            startG = 0f,
            startB = 1f,
            startA = 1f,
            endR = 0f,
            endG = 1f,
            endB = 0f,
            endA = 0.4f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 1f, 1f, 0f, 1f, 0f, 0.4f),
        )

    private fun conicalGradient() =
        GPUMaterialDescriptor.ConicalGradient(
            startX = 1f,
            startY = 2f,
            endX = 8f,
            endY = 9f,
            startRadius = 1f,
            endRadius = 12f,
            startR = 0.2f,
            startG = 0.4f,
            startB = 0.6f,
            startA = 0.8f,
            endR = 0.8f,
            endG = 0.6f,
            endB = 0.4f,
            endA = 0.2f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f, 0.2f),
        )

    private fun image(pixels: ByteArray) =
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "prepared-material-image",
            imageWidth = 1,
            imageHeight = 1,
            rgbaPixels = pixels,
            samplingFilterMode = "nearest",
            alphaOnly = false,
            tintR = 0.25f,
            tintG = 0.5f,
            tintB = 0.75f,
            tintA = 0.6f,
        )

    private fun supportedBlend() =
        GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = solid(),
            src = solid(r = 0.8f),
        )

    private fun registeredRuntimeEffect() =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = mapOf(
                "gColor" to GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 0.8f),
            ),
        )
}
