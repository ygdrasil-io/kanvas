package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.SpiralRTDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

class GPUPreparedMaterialProgramTest {
    private val compiler = GPUPreparedMaterialProgramCompiler
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-material:v1",
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )

    @Test
    fun `prepared material compiler accepts exactly common proven sources`() {
        val accepted = listOf(
            solidDescriptor(),
            linearGradientDescriptor(),
            radialGradientDescriptor(),
            sweepGradientDescriptor(),
            conicalGradientDescriptor(),
            supportedBlendShaderDescriptor(),
            registeredRuntimeEffectDescriptor(),
            supportedImageShaderDescriptor(),
        )

        accepted.forEach { descriptor ->
            val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
                compiler.compile(descriptor, 0.5f, context),
                descriptor.toString(),
            )
            val parsed = KanvasWGSLValidator().parse(ready.program.wgslSource)
            assertTrue(
                parsed.syntaxErrors.isEmpty(),
                "${descriptor.kind}: ${parsed.syntaxErrors.joinToString()}",
            )
            assertTrue(ready.program.materialKey.isNotBlank(), descriptor.kind.toString())
            assertTrue(ready.program.abiHash.isNotBlank(), descriptor.kind.toString())
            assertEquals(0.5f, ready.program.paintAlpha)
        }

        val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(unregisteredRuntimeEffectDescriptor(), 1f, context),
        )
        assertEquals("unsupported.material.runtime_effect.descriptor", refused.code)
    }

    @Test
    fun `unsupported blend shader is not replaced by its source child`() {
        val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(unsupportedBlendShaderDescriptor(), 1f, context),
        )

        assertEquals("unsupported.material.blend_shader", refused.code)
    }

    @Test
    fun `paint alpha must be finite and within the unit interval`() {
        listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { alpha ->
            val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
                compiler.compile(solidDescriptor(), alpha, context),
                "alpha=$alpha",
            )
            assertEquals("unsupported.material.paint_alpha", refused.code)
        }
    }

    @Test
    fun `image sampled resource snapshots exact bytes and returns defensive copies`() {
        val pixels = byteArrayOf(
            1, 2, 3, 4,
            5, 6, 7, 8,
        )
        val descriptor = supportedImageShaderDescriptor(
            pixels = pixels,
            width = 2,
            height = 1,
        )
        val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            compiler.compile(descriptor, 0.75f, context),
        )
        val resource = ready.program.sampledResources.single()

        pixels.fill(99)
        descriptor.rgbaPixels.fill(88)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            resource.rgba8Bytes(),
        )

        val escaped = resource.rgba8Bytes()
        escaped.fill(77)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            resource.rgba8Bytes(),
        )
    }

    @Test
    fun `sampled resource keys separate content sampling and alpha topology`() {
        fun key(
            pixels: ByteArray = byteArrayOf(1, 2, 3, 4),
            filter: String = "nearest",
            alphaOnly: Boolean = false,
        ): String {
            val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
                compiler.compile(
                    supportedImageShaderDescriptor(
                        pixels = pixels,
                        samplingFilterMode = filter,
                        alphaOnly = alphaOnly,
                    ),
                    1f,
                    context,
                ),
            )
            return ready.program.sampledResources.single().resourceKey
        }

        val baseline = key()
        assertNotEquals(baseline, key(pixels = byteArrayOf(1, 2, 3, 5)))
        assertNotEquals(baseline, key(filter = "linear"))
        assertNotEquals(baseline, key(alphaOnly = true))
        assertEquals(baseline, key())
    }

    @Test
    fun `image validation refuses invalid dimensions and exact RGBA byte length`() {
        val invalid = listOf(
            supportedImageShaderDescriptor(width = 0),
            supportedImageShaderDescriptor(height = 0),
            supportedImageShaderDescriptor(width = Int.MAX_VALUE, height = Int.MAX_VALUE),
            supportedImageShaderDescriptor(pixels = byteArrayOf(1, 2, 3), width = 1, height = 1),
        )

        invalid.forEach { descriptor ->
            val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
                compiler.compile(descriptor, 1f, context),
                descriptor.toString(),
            )
            assertEquals("unsupported.material.image_resource", refused.code)
        }
    }

    @Test
    fun `uniform and sampled resource collections are deeply immutable snapshots`() {
        val gradient = linearGradientDescriptor()
        val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            compiler.compile(gradient, 1f, context),
        )
        val before = ready.program.uniformBytes.toList()

        gradient.allStopColors!!.fill(0f)
        gradient.allStopPositions!!.fill(0f)
        assertEquals(before, ready.program.uniformBytes)
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (ready.program.uniformBytes as MutableList<Int>)[0] = 255
            }.isFailure,
        )
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (ready.program.sampledResources as MutableList<GPUPreparedMaterialSampledResource>)
                    .clear()
            }.isFailure,
        )
    }

    @Test
    fun `accepted final modules expose the published entry point to parser reflection`() {
        listOf(
            solidDescriptor(),
            linearGradientDescriptor(),
            supportedBlendShaderDescriptor(),
            supportedImageShaderDescriptor(),
        ).forEach { descriptor ->
            val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
                compiler.compile(descriptor, 1f, context),
            )
            val parsed = KanvasWGSLValidator().parse(ready.program.wgslSource)
            val report = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report)

            assertTrue(
                report.entryPoints.any { it.name == ready.program.entryPoint },
                "${descriptor.kind}: ${report.entryPoints}",
            )
        }
    }

    @Test
    fun `material key separates exact uniform payload paint alpha and full hash width`() {
        val firstSolid = ready(solidDescriptor(), paintAlpha = 1f)
        val secondSolid = ready(
            GPUMaterialDescriptor.SolidColor(r = 0.5f, g = 0.5f, b = 0.75f, a = 0.8f),
            paintAlpha = 1f,
        )
        val firstGradient = ready(linearGradientDescriptor(), paintAlpha = 1f)
        val changedGradient = ready(
            linearGradientDescriptor().copy(
                allStopColors = floatArrayOf(1f, 0f, 0f, 0.25f, 0f, 1f, 0f, 0.75f),
            ),
            paintAlpha = 1f,
        )
        val changedPaintAlpha = ready(solidDescriptor(), paintAlpha = 0.5f)

        assertNotEquals(firstSolid.materialKey, secondSolid.materialKey)
        assertNotEquals(firstGradient.materialKey, changedGradient.materialKey)
        assertNotEquals(firstSolid.materialKey, changedPaintAlpha.materialKey)
        listOf(firstSolid, secondSolid, firstGradient, changedGradient, changedPaintAlpha).forEach { program ->
            assertTrue(
                program.materialKey.substringAfterLast(':').matches(Regex("[0-9a-f]{64}")),
                program.materialKey,
            )
            assertTrue(program.abiHash.matches(Regex("sha256:[0-9a-f]{64}")), program.abiHash)
        }
    }

    @Test
    fun `material key separates image pixels sampling alpha topology and tint`() {
        val baseline = ready(
            supportedImageShaderDescriptor(
                pixels = byteArrayOf(1, 2, 3, 4),
                samplingFilterMode = "nearest",
                alphaOnly = false,
            ),
            paintAlpha = 1f,
        )
        val changedPixels = ready(
            supportedImageShaderDescriptor(
                pixels = byteArrayOf(1, 2, 3, 5),
                samplingFilterMode = "nearest",
                alphaOnly = false,
            ),
            paintAlpha = 1f,
        )
        val changedSampling = ready(
            supportedImageShaderDescriptor(
                pixels = byteArrayOf(1, 2, 3, 4),
                samplingFilterMode = "linear",
                alphaOnly = false,
            ),
            paintAlpha = 1f,
        )
        val changedAlphaTopology = ready(
            supportedImageShaderDescriptor(
                pixels = byteArrayOf(1, 2, 3, 4),
                samplingFilterMode = "nearest",
                alphaOnly = true,
            ),
            paintAlpha = 1f,
        )
        val changedTint = ready(
            supportedImageShaderDescriptor(
                pixels = byteArrayOf(1, 2, 3, 4),
                samplingFilterMode = "nearest",
                alphaOnly = false,
                tintR = 0.5f,
            ),
            paintAlpha = 1f,
        )

        listOf(changedPixels, changedSampling, changedAlphaTopology, changedTint).forEach { changed ->
            assertNotEquals(baseline.materialKey, changed.materialKey)
        }
        assertEquals(baseline.abiHash, changedPixels.abiHash)
        assertEquals(baseline.abiHash, changedTint.abiHash)
    }

    @Test
    fun `abi hash excludes solid and gradient uniform values`() {
        val firstSolid = ready(solidDescriptor(), paintAlpha = 1f)
        val secondSolid = ready(
            GPUMaterialDescriptor.SolidColor(r = 0.5f, g = 0.5f, b = 0.75f, a = 0.8f),
            paintAlpha = 0.25f,
        )
        val firstGradient = ready(linearGradientDescriptor(), paintAlpha = 1f)
        val secondGradient = ready(
            linearGradientDescriptor().copy(
                allStopColors = floatArrayOf(0f, 1f, 0f, 0.25f, 1f, 1f, 0f, 0.75f),
            ),
            paintAlpha = 0.25f,
        )

        assertEquals(firstSolid.abiHash, secondSolid.abiHash)
        assertEquals(firstGradient.abiHash, secondGradient.abiHash)
    }

    @Test
    fun `runtime payload packs exact little endian bytes and changes material key not abi`() {
        val first = ready(
            registeredRuntimeEffectDescriptor(
                GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            ),
            paintAlpha = 1f,
        )
        val changed = ready(
            registeredRuntimeEffectDescriptor(
                GPURuntimeEffectUniformValue.Float4(0.5f, 0.5f, 0.75f, 1f),
            ),
            paintAlpha = 1f,
        )

        assertEquals(
            listOf(
                0x00, 0x00, 0x80, 0x3e,
                0x00, 0x00, 0x00, 0x3f,
                0x00, 0x00, 0x40, 0x3f,
                0x00, 0x00, 0x80, 0x3f,
            ),
            first.uniformBytes,
        )
        assertNotEquals(first.materialKey, changed.materialKey)
        assertEquals(first.abiHash, changed.abiHash)
    }

    @Test
    fun `runtime payload rejects missing extra and wrong type fields canonically`() {
        val invalid = listOf(
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.simple_rt",
                descriptorVersion = 1,
                uniforms = emptyMap(),
            ),
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.simple_rt",
                descriptorVersion = 1,
                uniforms = mapOf(
                    "gColor" to GPURuntimeEffectUniformValue.Float4(1f, 0f, 0f, 1f),
                    "extra" to GPURuntimeEffectUniformValue.Float1(2f),
                ),
            ),
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.simple_rt",
                descriptorVersion = 1,
                uniforms = mapOf("gColor" to GPURuntimeEffectUniformValue.Float1(1f)),
            ),
        )

        invalid.forEach { descriptor ->
            val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
                compiler.compile(descriptor, 1f, context),
                descriptor.uniforms.toString(),
            )
            assertEquals("unsupported.material.runtime_effect.uniform_payload", refused.code)
        }
    }

    @Test
    fun `runtime child facts are retained and unsupported children refuse canonically`() {
        val children = linkedMapOf<String, GPUMaterialDescriptor>(
            "input" to solidDescriptor(),
        )
        val descriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = mapOf(
                "gColor" to GPURuntimeEffectUniformValue.Float4(1f, 0f, 0f, 1f),
            ),
            children = children,
        )
        children.clear()

        assertEquals(setOf("input"), descriptor.children.keys)
        val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(descriptor, 1f, context),
        )
        assertEquals("unsupported.material.runtime_effect.children", refused.code)
    }

    @Test
    fun `runtime payload snapshots caller maps and matrix arrays before compile`() {
        val matrix = MutableList(16) { index -> index.toFloat() }
        val uniforms = linkedMapOf<String, GPURuntimeEffectUniformValue>(
            "gColor" to GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            "unusedMatrix" to GPURuntimeEffectUniformValue.Matrix4x4(matrix),
        )
        val descriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = uniforms,
        )

        matrix.fill(99f)
        uniforms.clear()

        val snapshot = assertIs<GPURuntimeEffectUniformValue.Matrix4x4>(
            descriptor.uniforms.getValue("unusedMatrix"),
        )
        assertEquals((0 until 16).map(Int::toFloat), snapshot.values)
        val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(descriptor, 1f, context),
        )
        assertEquals("unsupported.material.runtime_effect.uniform_payload", refused.code)
    }

    @Test
    fun `runtime descriptor version and registered program availability remain distinct`() {
        val versionMismatch = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(
                GPUMaterialDescriptor.RuntimeEffect(
                    effectId = "runtime.simple_rt",
                    descriptorVersion = 2,
                    uniforms = mapOf(
                        "gColor" to GPURuntimeEffectUniformValue.Float4(0f, 0f, 0f, 0f),
                    ),
                ),
                1f,
                context,
            ),
        )
        val registeredWithoutProgram = assertIs<GPUPreparedMaterialProgramResult.Refused>(
            compiler.compile(
                GPUMaterialDescriptor.RuntimeEffect(
                    effectId = SpiralRTDescriptor.effectId.value,
                    descriptorVersion = SpiralRTDescriptor.descriptorVersion.value,
                ),
                1f,
                context,
            ),
        )

        assertEquals("unsupported.material.runtime_effect.descriptor", versionMismatch.code)
        assertEquals(
            "unsupported.material.runtime_effect.wgsl_not_available",
            registeredWithoutProgram.code,
        )
    }

    private fun ready(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float,
    ): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            compiler.compile(descriptor, paintAlpha, context),
        ).program

    private fun solidDescriptor() =
        GPUMaterialDescriptor.SolidColor(r = 0.25f, g = 0.5f, b = 0.75f, a = 0.8f)

    private fun linearGradientDescriptor() =
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

    private fun radialGradientDescriptor() =
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

    private fun sweepGradientDescriptor() =
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

    private fun conicalGradientDescriptor() =
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

    private fun supportedBlendShaderDescriptor() =
        GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = solidDescriptor(),
            src = linearGradientDescriptor(),
        )

    private fun unsupportedBlendShaderDescriptor() =
        GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = registeredRuntimeEffectDescriptor(),
            src = solidDescriptor(),
        )

    private fun registeredRuntimeEffectDescriptor(
        color: GPURuntimeEffectUniformValue =
            GPURuntimeEffectUniformValue.Float4(0f, 0f, 0f, 0f),
    ) =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = mapOf("gColor" to color),
        )

    private fun unregisteredRuntimeEffectDescriptor() =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.not_registered",
            descriptorVersion = 1,
        )

    private fun supportedImageShaderDescriptor(
        pixels: ByteArray = byteArrayOf(1, 2, 3, 4),
        width: Int = 1,
        height: Int = 1,
        samplingFilterMode: String = "nearest",
        alphaOnly: Boolean = false,
        tintR: Float = 0.25f,
    ) =
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "prepared-material-image",
            imageWidth = width,
            imageHeight = height,
            rgbaPixels = pixels,
            samplingFilterMode = samplingFilterMode,
            alphaOnly = alphaOnly,
            tintR = tintR,
            tintG = 0.5f,
            tintB = 0.75f,
            tintA = 0.6f,
        )
}
