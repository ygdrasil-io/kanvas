package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

class GPUPreparedMaterialProgramTest {
    private val compiler = GPUPreparedMaterialProgramCompiler
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-material:v1",
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

    private fun registeredRuntimeEffectDescriptor() =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
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
    ) =
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "prepared-material-image",
            imageWidth = width,
            imageHeight = height,
            rgbaPixels = pixels,
            samplingFilterMode = samplingFilterMode,
            alphaOnly = alphaOnly,
            tintR = 0.25f,
            tintG = 0.5f,
            tintB = 0.75f,
            tintA = 0.6f,
        )
}
