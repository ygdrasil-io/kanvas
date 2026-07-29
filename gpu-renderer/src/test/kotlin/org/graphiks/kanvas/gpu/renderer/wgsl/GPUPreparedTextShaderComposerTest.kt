package org.graphiks.kanvas.gpu.renderer.wgsl

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextShaderComposer
import org.graphiks.kanvas.gpu.renderer.materials.preparedTextMaterialBindingMismatch
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class GPUPreparedTextShaderComposerTest {
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-prepared-text-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-text-test:v1",
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )

    @Test
    fun `all admitted material families compose with three collision-free binding groups`() {
        admittedDescriptors().forEach { descriptor ->
            val material = compile(descriptor)
            val ready = compose(material)
            val parsed = parseWgslResult(ready.wgslSource)
            assertTrue(
                parsed.isSuccess,
                "${descriptor.kind}: ${parsed.errors.joinToString { it.message }}",
            )
            val report = Lowerer().lower(parsed.translationUnit)
                .reflectWgslModule(sourceId = descriptor.kind.toString())

            assertEquals(
                listOf("vs_main" to "vertex", "fs_main" to "fragment"),
                report.entryPoints.map { it.name to it.stage },
                descriptor.kind.toString(),
            )
            assertEquals(report.bindings.distinct(), report.bindings, descriptor.kind.toString())
            assertEquals(3, report.bindings.maxOf { it.group } + 1, descriptor.kind.toString())
            assertEquals(
                listOf(Triple(0, 0, "uniformBuffer")),
                report.bindings
                    .filter { it.group == 0 }
                    .map { Triple(it.group, it.binding, it.resourceKind) },
                descriptor.kind.toString(),
            )
            assertEquals(
                expectedMaterialBindings(material),
                report.bindings
                    .filter { it.group == 1 }
                    .map { Triple(it.group, it.binding, it.resourceKind) },
                descriptor.kind.toString(),
            )
            assertEquals(
                listOf(
                    Triple(2, 0, "sampledTexture"),
                    Triple(2, 1, "sampler"),
                ),
                report.bindings
                    .filter { it.group == 2 }
                    .map { Triple(it.group, it.binding, it.resourceKind) },
                descriptor.kind.toString(),
            )
            assertSame(material.composableFragment, ready.bindingPlan.materialFragment)
            assertEquals(0, ready.bindingPlan.drawUniformGroup)
            assertEquals(0, ready.bindingPlan.drawUniformBinding)
            assertEquals(2, ready.bindingPlan.atlasTextureGroup)
            assertEquals(0, ready.bindingPlan.atlasTextureBinding)
            assertEquals(2, ready.bindingPlan.atlasSamplerGroup)
            assertEquals(1, ready.bindingPlan.atlasSamplerBinding)
        }
    }

    @Test
    fun `composition consumes the exact canonical Task 1 fragment without renaming it`() {
        val material = compile(image(byteArrayOf(1, 2, 3, 4)))
        val ready = compose(material)
        val fragment = material.composableFragment

        assertTrue(fragment.declarationsWgsl in ready.wgslSource)
        assertTrue(fragment.evaluationFunctionWgsl in ready.wgslSource)
        assertEquals(
            1,
            Regex("""fn\s+kanvas_evaluate_material\s*\(""")
                .findAll(ready.wgslSource)
                .count(),
        )
    }

    @Test
    fun `uniform values paint alpha and texture content do not specialize a text pipeline`() {
        val solidLow = compile(solid(red = 0.25f), paintAlpha = 0.2f)
        val solidHigh = compile(solid(red = 0.75f), paintAlpha = 0.9f)
        val firstImage = compile(image(byteArrayOf(1, 2, 3, 4)))
        val secondImage = compile(image(byteArrayOf(1, 2, 3, 5)))

        assertNotEquals(solidLow.materialKey, solidHigh.materialKey)
        assertEquals(compose(solidLow).pipelineKey, compose(solidHigh).pipelineKey)
        assertNotEquals(firstImage.materialKey, secondImage.materialKey)
        assertEquals(compose(firstImage).pipelineKey, compose(secondImage).pipelineKey)
    }

    @Test
    fun `code ABI target format and blend plan are the only pipeline identity axes`() {
        val solid = compile(solid())
        val image = compile(image(byteArrayOf(1, 2, 3, 4)))
        val baseline = compose(solid)
        val imageProgram = compose(image)
        val otherTarget = compose(solid, targetFormatClass = "rgba16float")
        val otherBlend = compose(solid, blendPlanIdentity = "fixed-function:src")

        assertNotEquals(baseline.sourceHash, imageProgram.sourceHash)
        assertNotEquals(baseline.pipelineKey, imageProgram.pipelineKey)
        assertNotEquals(baseline.pipelineKey, otherTarget.pipelineKey)
        assertNotEquals(baseline.pipelineKey, otherBlend.pipelineKey)
        assertEquals(baseline.sourceHash, otherTarget.sourceHash)
        assertEquals(baseline.abiHash, otherTarget.abiHash)
    }

    @Test
    fun `pipeline key length-prefixes independent target and blend axes`() {
        val material = compile(solid())
        val first = compose(
            material,
            targetFormatClass = "rgba8\nfixed",
            blendPlanIdentity = "src",
        )
        val second = compose(
            material,
            targetFormatClass = "rgba8",
            blendPlanIdentity = "fixed\nsrc",
        )

        assertNotEquals(first.pipelineKey, second.pipelineKey)
    }

    @Test
    fun `final material binding gate compares every reflected Task 1 ABI fact`() {
        val material = compile(image(byteArrayOf(1, 2, 3, 4)))
        val ready = compose(material)
        val parsed = parseWgslResult(ready.wgslSource)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })
        val reflectedBindings = Lowerer().lower(parsed.translationUnit)
            .reflectWgslModule(sourceId = "prepared-text-binding-gate-test")
            .bindings

        assertEquals(
            null,
            preparedTextMaterialBindingMismatch(
                material.composableFragment,
                reflectedBindings,
            ),
        )
        val materialUniform = reflectedBindings.single {
            it.group == 1 && it.resourceKind == "uniformBuffer"
        }
        val materialTexture = reflectedBindings.single {
            it.group == 1 && it.resourceKind == "sampledTexture"
        }
        val materialSampler = reflectedBindings.single {
            it.group == 1 && it.resourceKind == "sampler"
        }
        val mismatches = listOf(
            reflectedBindings.map {
                if (it == materialUniform) {
                    it.copy(minBindingSize = requireNotNull(it.minBindingSize) + 16)
                } else {
                    it
                }
            },
            reflectedBindings.map {
                if (it == materialTexture) it.copy(sampleType = "uint") else it
            },
            reflectedBindings.map {
                if (it == materialTexture) it.copy(viewDimension = "2d_array") else it
            },
            reflectedBindings.map {
                if (it == materialTexture) it.copy(access = null) else it
            },
            reflectedBindings.map {
                if (it == materialTexture) it.copy(storageFormat = "rgba8unorm") else it
            },
            reflectedBindings.map {
                if (it == materialSampler) it.copy(access = null) else it
            },
        )

        mismatches.forEach { mutation ->
            assertTrue(
                preparedTextMaterialBindingMismatch(
                    material.composableFragment,
                    mutation,
                ) != null,
                mutation.toString(),
            )
        }
    }

    @Test
    fun `source hash is the exact SHA-256 of deterministic final WGSL`() {
        val first = compose(compile(solid()))
        val second = compose(compile(solid()))

        assertEquals(first.wgslSource, second.wgslSource)
        assertEquals(sha256Hex(first.wgslSource.encodeToByteArray()), first.sourceHash)
        assertEquals(first.sourceHash, second.sourceHash)
        assertEquals(first.abiHash, second.abiHash)
        assertEquals(first.pipelineKey, second.pipelineKey)
        assertTrue(first.abiHash.matches(Regex("[0-9a-f]{64}")))
        assertTrue(first.pipelineKey.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `blank pipeline-state identities are explicitly refused`() {
        val material = compile(solid())
        listOf(
            GPUPreparedTextShaderComposer.compose(
                material,
                targetFormatClass = "",
                blendPlanIdentity = "fixed-function:src-over:premul",
            ),
            GPUPreparedTextShaderComposer.compose(
                material,
                targetFormatClass = "rgba8unorm",
                blendPlanIdentity = "",
            ),
        ).forEach { result ->
            val refused = assertIs<GPUPreparedTextCompositeProgramResult.Refused>(result)
            assertEquals("unsupported.material.composition", refused.code)
            assertTrue(refused.message.isNotBlank())
        }
    }

    private fun expectedMaterialBindings(
        material: GPUPreparedMaterialProgram,
    ): List<Triple<Int, Int, String>> = buildList {
        material.composableFragment.uniformBinding?.let {
            add(Triple(it.group, it.binding, "uniformBuffer"))
        }
        material.composableFragment.sampledBindings.forEach {
            add(Triple(it.textureGroup, it.textureBinding, "sampledTexture"))
            add(Triple(it.samplerGroup, it.samplerBinding, "sampler"))
        }
    }

    private fun compile(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float = 0.6f,
    ): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(descriptor, paintAlpha, context),
            descriptor.toString(),
        ).program

    private fun compose(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String = "rgba8unorm",
        blendPlanIdentity: String = "fixed-function:src-over:premul",
    ): GPUPreparedTextCompositeProgram =
        assertIs<GPUPreparedTextCompositeProgramResult.Ready>(
            GPUPreparedTextShaderComposer.compose(
                material = material,
                targetFormatClass = targetFormatClass,
                blendPlanIdentity = blendPlanIdentity,
            ),
        ).program

    private fun admittedDescriptors(): List<GPUMaterialDescriptor> = listOf(
        solid(),
        linearGradient(),
        radialGradient(),
        sweepGradient(),
        conicalGradient(),
        image(byteArrayOf(1, 2, 3, 4)),
        supportedBlend(),
        registeredRuntimeEffect(),
    )

    private fun solid(red: Float = 0.25f) =
        GPUMaterialDescriptor.SolidColor(red, 0.5f, 0.75f, 0.8f)

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
            imageSourceId = "prepared-text-image",
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
            src = solid(red = 0.8f),
        )

    private fun registeredRuntimeEffect() =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = mapOf(
                "gColor" to GPURuntimeEffectUniformValue.Float4(
                    0.25f,
                    0.5f,
                    0.75f,
                    0.8f,
                ),
            ),
        )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
