package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBlock

import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUVertexFormat
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilConsumerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilProducerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUWgpu4kCorePrimitivePipelineDescriptorTest {
    @Test
    fun `indexed path covers map every admitted fixed blend to distinct exact descriptors`() {
        data class Case(
            val mode: GPUBlendMode,
            val colorSource: String,
            val colorDestination: String,
            val alphaSource: String,
            val alphaDestination: String,
            val expectedColorSource: GPUBlendFactor,
            val expectedColorDestination: GPUBlendFactor,
            val expectedAlphaSource: GPUBlendFactor,
            val expectedAlphaDestination: GPUBlendFactor,
        )

        val cases = listOf(
            Case(
                GPUBlendMode.CLEAR,
                "zero",
                "zero",
                "zero",
                "zero",
                GPUBlendFactor.Zero,
                GPUBlendFactor.Zero,
                GPUBlendFactor.Zero,
                GPUBlendFactor.Zero,
            ),
            Case(
                GPUBlendMode.SRC,
                "one",
                "zero",
                "one",
                "zero",
                GPUBlendFactor.One,
                GPUBlendFactor.Zero,
                GPUBlendFactor.One,
                GPUBlendFactor.Zero,
            ),
            Case(
                GPUBlendMode.DST_OVER,
                "one-minus-dst-alpha",
                "one",
                "one-minus-dst-alpha",
                "one",
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.One,
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.One,
            ),
            Case(
                GPUBlendMode.SRC_IN,
                "dst-alpha",
                "zero",
                "dst-alpha",
                "zero",
                GPUBlendFactor.DstAlpha,
                GPUBlendFactor.Zero,
                GPUBlendFactor.DstAlpha,
                GPUBlendFactor.Zero,
            ),
            Case(
                GPUBlendMode.DST_IN,
                "zero",
                "src-alpha",
                "zero",
                "src-alpha",
                GPUBlendFactor.Zero,
                GPUBlendFactor.SrcAlpha,
                GPUBlendFactor.Zero,
                GPUBlendFactor.SrcAlpha,
            ),
            Case(
                GPUBlendMode.SRC_OUT,
                "one-minus-dst-alpha",
                "zero",
                "one-minus-dst-alpha",
                "zero",
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.Zero,
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.Zero,
            ),
            Case(
                GPUBlendMode.DST_OUT,
                "zero",
                "one-minus-src-alpha",
                "zero",
                "one-minus-src-alpha",
                GPUBlendFactor.Zero,
                GPUBlendFactor.OneMinusSrcAlpha,
                GPUBlendFactor.Zero,
                GPUBlendFactor.OneMinusSrcAlpha,
            ),
            Case(
                GPUBlendMode.SRC_ATOP,
                "dst-alpha",
                "one-minus-src-alpha",
                "dst-alpha",
                "one-minus-src-alpha",
                GPUBlendFactor.DstAlpha,
                GPUBlendFactor.OneMinusSrcAlpha,
                GPUBlendFactor.DstAlpha,
                GPUBlendFactor.OneMinusSrcAlpha,
            ),
            Case(
                GPUBlendMode.DST_ATOP,
                "one-minus-dst-alpha",
                "src-alpha",
                "one-minus-dst-alpha",
                "src-alpha",
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.SrcAlpha,
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.SrcAlpha,
            ),
            Case(
                GPUBlendMode.XOR,
                "one-minus-dst-alpha",
                "one-minus-src-alpha",
                "one-minus-dst-alpha",
                "one-minus-src-alpha",
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.OneMinusSrcAlpha,
                GPUBlendFactor.OneMinusDstAlpha,
                GPUBlendFactor.OneMinusSrcAlpha,
            ),
            Case(
                GPUBlendMode.MODULATE,
                "zero",
                "src",
                "zero",
                "src-alpha",
                GPUBlendFactor.Zero,
                GPUBlendFactor.Src,
                GPUBlendFactor.Zero,
                GPUBlendFactor.SrcAlpha,
            ),
            Case(
                GPUBlendMode.SCREEN,
                "one",
                "one-minus-src",
                "one",
                "one-minus-src-alpha",
                GPUBlendFactor.One,
                GPUBlendFactor.OneMinusSrc,
                GPUBlendFactor.One,
                GPUBlendFactor.OneMinusSrcAlpha,
            ),
        )
        val identities = linkedSetOf<GPUWgpu4kCorePrimitiveRenderPipelineIdentity>()

        cases.forEach { case ->
            listOf(regularCover(), inverseCover()).forEach { stencil ->
                val key = pathKey(stencil, cover = true).copy(
                    blend = fixedBlend(
                        mode = case.mode,
                        colorSource = case.colorSource,
                        colorDestination = case.colorDestination,
                        alphaSource = case.alphaSource,
                        alphaDestination = case.alphaDestination,
                    ),
                )
                val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                    mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
                    case.mode.name,
                )
                identities += mapped.identity
                val target = assertIs<ColorTargetState>(
                    requireNotNull(
                        corePrimitiveWgpu4kRenderPipelineDescriptor(
                            mapped.identity,
                            shader,
                            pipelineLayout,
                        ).fragment,
                    ).targets.single(),
                )
                val blend = requireNotNull(target.blend)

                assertEquals(GPUColorWrite.All, target.writeMask, case.mode.name)
                assertEquals(GPUBlendOperation.Add, blend.color.operation, case.mode.name)
                assertEquals(case.expectedColorSource, blend.color.srcFactor, case.mode.name)
                assertEquals(case.expectedColorDestination, blend.color.dstFactor, case.mode.name)
                assertEquals(GPUBlendOperation.Add, blend.alpha.operation, case.mode.name)
                assertEquals(case.expectedAlphaSource, blend.alpha.srcFactor, case.mode.name)
                assertEquals(case.expectedAlphaDestination, blend.alpha.dstFactor, case.mode.name)
            }
            listOf(regularCover(), inverseCover()).forEach { stencil ->
                val analytic = pathKey(stencil, cover = true).copy(
                    blend = fixedBlend(
                        mode = case.mode,
                        colorSource = case.colorSource,
                        colorDestination = case.colorDestination,
                        alphaSource = case.alphaSource,
                        alphaDestination = case.alphaDestination,
                    ),
                    clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                        antiAlias = true,
                    ),
                )
                assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
                    mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(analytic),
                    "analytic-${case.mode.name}",
                )
            }
        }

        assertEquals(cases.size * 2, identities.size)
        val malformed = pathKey(regularCover(), cover = true).copy(
            blend = fixedBlend(
                mode = GPUBlendMode.SRC,
                colorSource = "one",
                colorDestination = "one",
                alphaSource = "one",
                alphaDestination = "zero",
            ),
        )
        assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(malformed),
        )
    }

    @Test
    fun `indexed destination no op cover keeps stencil reset while disabling color writes`() {
        val identities = listOf(regularCover(), inverseCover()).map { stencil ->
            val key = pathKey(stencil, cover = true).copy(
                blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp(GPUBlendMode.DST),
            )
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
                mapped.identity,
                shader,
                pipelineLayout,
            )
            val target = assertIs<ColorTargetState>(requireNotNull(descriptor.fragment).targets.single())

            assertNull(target.blend)
            assertEquals(GPUColorWrite.None, target.writeMask)
            assertTrue(requireNotNull(descriptor.depthStencil).stencilWriteMask != 0u)
            mapped.identity
        }

        assertNotEquals(identities[0], identities[1])
        listOf(regularCover(), inverseCover()).forEach { stencil ->
            val analyticNoOp = pathKey(stencil, cover = true).copy(
                blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp(GPUBlendMode.DST),
                clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                    antiAlias = true,
                ),
            )
            assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(analyticNoOp),
            )
        }
    }

    @Test
    fun `scene sRGB structural authority maps exact native target while mask producer stays unorm`() {
        val sceneKeys = listOf(
            directKey(),
            pathKey(producerWinding()),
            pathKey(regularCover(), cover = true),
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding),
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan()),
            corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan()),
        )

        sceneKeys.forEach { legacy ->
            val srgb = legacy.copy(
                colorFormat = GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb,
            )
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(srgb),
            )
            val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
                mapped.identity,
                shader,
                pipelineLayout,
            )

            assertEquals("rgba8unorm-srgb", mapped.identity.targetFormat)
            assertEquals(
                GPUTextureFormat.RGBA8UnormSrgb,
                assertIs<ColorTargetState>(requireNotNull(descriptor.fragment).targets.single()).format,
            )
        }

        val producer = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            GPUClipMaskCombine.Intersect,
        )
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm,
            producer.colorFormat,
        )
        assertEquals("rgba8unorm", mappedIdentity(producer).targetFormat)
    }

    @Test
    fun `direct color only pipeline maps exact 4x structural sample state`() {
        val key = directKey().copy(sampleCount = 4)
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
        )
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
            mapped.identity,
            shader,
            pipelineLayout,
        )

        assertEquals(4, mapped.identity.sampleCount)
        assertEquals(4u, descriptor.multisample.count)
        assertNull(descriptor.depthStencil)
        val withPathAttachment = directWithPathDepthStencilKey().copy(sampleCount = 4)
        val attachedMapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(withPathAttachment),
        )
        val attachedDescriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
            attachedMapped.identity,
            shader,
            pipelineLayout,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            attachedMapped.identity.program,
        )
        assertEquals(4u, attachedDescriptor.multisample.count)
        assertDepthStencil(
            attachedDescriptor,
            front = face(pass = GPUStencilOperation.Keep),
            back = face(pass = GPUStencilOperation.Keep),
            readMask = 0u,
            writeMask = 0u,
        )
    }

    @Test
    fun `path and clip stencil programs map distinct 4x identities with unchanged D24S8 semantics`() {
        val singleSampleKeys = listOf(
            pathKey(producerWinding()),
            pathKey(producerEvenOdd()),
            pathKey(regularCover(), cover = true),
            pathKey(inverseCover(), cover = true),
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding),
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.EvenOdd),
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan()),
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(true, srcOverBlendPlan()),
        )

        singleSampleKeys.forEach { singleSampleKey ->
            val multisampleKey = singleSampleKey.copy(sampleCount = 4)
            val singleIdentity = mappedIdentity(singleSampleKey)
            val multisampleIdentity = mappedIdentity(multisampleKey)
            val singleDescriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
                singleIdentity,
                shader,
                pipelineLayout,
            )
            val multisampleDescriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
                multisampleIdentity,
                shader,
                pipelineLayout,
            )

            assertNotEquals(singleIdentity, multisampleIdentity)
            assertEquals(singleIdentity.program, multisampleIdentity.program)
            assertEquals(1u, singleDescriptor.multisample.count)
            assertEquals(4u, multisampleDescriptor.multisample.count)
            assertEquals(singleDescriptor.depthStencil, multisampleDescriptor.depthStencil)
            assertEquals(GPUTextureFormat.Depth24PlusStencil8, requireNotNull(multisampleDescriptor.depthStencil).format)
        }
    }

    @Test
    fun `analytic shape has one unique uniform80 src over descriptor and twenty one total programs`() {
        val key = analyticShapeKey()
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
        )
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
            mapped.identity,
            shader,
            pipelineLayout,
        )

        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver, mapped.identity.program)
        assertEquals(PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY, mapped.componentIdentity)
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
            key.uniformLayout,
        )
        assertEquals(37, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertTrue(CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES >= GPUWgpu4kCorePrimitivePipelineProgram.entries.size + 1)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT, descriptor.vertex.entryPoint)
        assertEquals(1, descriptor.vertex.buffers.size)
        assertEquals(8uL, descriptor.vertex.buffers.single().arrayStride)
        assertEquals(GPUVertexFormat.Float32x2, descriptor.vertex.buffers.single().attributes.single().format)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT, requireNotNull(descriptor.fragment).entryPoint)
        assertNull(descriptor.depthStencil)
        assertEquals(1u, descriptor.multisample.count)
        assertEquals(false, descriptor.multisample.alphaToCoverageEnabled)
        val target = assertIs<ColorTargetState>(requireNotNull(descriptor.fragment).targets.single())
        val blend = requireNotNull(target.blend)
        assertEquals(GPUBlendFactor.One, blend.color.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.color.dstFactor)
        assertEquals(GPUBlendFactor.One, blend.alpha.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.alpha.dstFactor)
    }

    @Test
    fun `gradient structural shaders map to distinct native programs and uniform bindings`() {
        val variants = listOf(
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient to 592uL,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient to 592uL,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient to 592uL,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient to 656uL,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRadialGradient to 656uL,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticSweepGradient to 656uL,
        )

        val mapped = variants.map { (shader, expectedBindingSize) ->
            val key = directKey().copy(shader = shader)
            val result = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            assertEquals(
                expectedBindingSize,
                requireNotNull(
                    corePrimitiveBindGroupLayoutDescriptor(result.componentIdentity)
                        .entries.single()
                        .buffer,
                ).minBindingSize,
            )
            result
        }

        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient,
                GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient,
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient,
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient,
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient,
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient,
            ),
            mapped.map { it.identity.program },
        )
        assertEquals(6, mapped.map { it.componentIdentity }.distinct().size)
    }

    @Test
    fun `analytic shape bounds radii color aa and target stay outside structural cache identity`() {
        val key = analyticShapeKey()
        val first = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 32f,
            targetHeight = 32f,
            antiAlias = true,
            premultipliedRgba = listOf(1f, 0f, 0f, 1f),
            deviceBounds = listOf(1f, 2f, 20f, 21f),
            normalizedRadii = List(8) { 0f },
        )
        val second = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 640f,
            targetHeight = 480f,
            antiAlias = false,
            premultipliedRgba = listOf(0f, 0.5f, 0.25f, 0.5f),
            deviceBounds = listOf(100f, 120f, 500f, 400f),
            normalizedRadii = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f),
        )

        assertTrue(!first.packedBytes().contentEquals(second.packedBytes()))
        assertEquals(
            key.stableRenderPipelineKey("core-primitive"),
            key.copy().stableRenderPipelineKey("core-primitive"),
        )
        assertEquals(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key.copy()),
        )
    }

    @Test
    fun `analytic shape explicitly refuses analytic stencil and mask clips`() {
        val incompatibleClips = listOf(
            GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                antiAlias = true,
            ),
            GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4,
            GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Stencil(
                compare = GPUClipStencilCompare.NotEqual,
                passOperation = GPUClipStencilOperation.Keep,
                failOperation = GPUClipStencilOperation.Keep,
                depthFailOperation = GPUClipStencilOperation.Keep,
                readMask = 0xffu,
                writeMask = 0u,
            ),
            GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Mask(
                sampling = GPUClipMaskSampling.Nearest,
                invert = false,
                depthStencilRequired = false,
            ),
            GPUCorePrimitiveRenderPipelineStructuralKey.Clip.CoverageMaskNearest,
        )

        incompatibleClips.forEach { clip ->
            val refusal = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(analyticShapeKey().copy(clip = clip)),
            )
            assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_INCOMPATIBLE_CLIP_REASON, refusal.reason)
        }
    }

    @Test
    fun `analytic shape refuses sample blend depth and topology mutations`() {
        val key = analyticShapeKey()
        val mutations = listOf(
            key.copy(sampleCount = 4),
            key.copy(blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone),
            key.copy(depthStencil = directWithPathDepthStencilKey().depthStencil),
            key.copy(topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect),
        )

        mutations.forEach { mutation ->
            assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(mutation),
            )
        }
    }

    @Test
    fun `coverage mask structural keys map to four producers and one nearest consumer`() {
        val cases = listOf(
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                GPUClipMaskCombine.Intersect,
            ) to GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                GPUClipMaskCombine.Difference,
            ) to GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                GPUClipMaskCombine.Intersect,
            ) to GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                GPUClipMaskCombine.Difference,
            ) to GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
            corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan()) to
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        )

        cases.forEach { (key, expectedProgram) ->
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            assertEquals(expectedProgram, mapped.identity.program)
            assertEquals(
                if (key.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer) {
                    PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY
                } else {
                    PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
                },
                mapped.componentIdentity,
            )
        }
        assertEquals(37, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertTrue(CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES >= GPUWgpu4kCorePrimitivePipelineProgram.entries.size + 1)
    }

    @Test
    fun `coverage mask keys retain the strict nearest token and reject topology mutation before mapping`() {
        val producer = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            GPUClipMaskCombine.Intersect,
        )
        val consumer = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan())

        assertSame(GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None, producer.clip)
        assertSame(GPUCorePrimitiveRenderPipelineStructuralKey.Clip.CoverageMaskNearest, consumer.clip)
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
            producer.topology,
        )
        assertFailsWith<IllegalArgumentException> {
            producer.copy(
                topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect,
            )
        }
    }

    @Test
    fun `coverage mask producer descriptors use fullscreen hard coverage and exact dst composition`() {
        val rectIntersect = descriptor(
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                GPUClipMaskCombine.Intersect,
            ),
        )
        val rrectDifference = descriptor(
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                GPUClipMaskCombine.Difference,
            ),
        )

        listOf(rectIntersect, rrectDifference).forEach { descriptor ->
            assertEquals(CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_ENTRY_POINT, descriptor.vertex.entryPoint)
            assertEquals(emptyList(), descriptor.vertex.buffers)
            assertNull(descriptor.depthStencil)
            val target = assertIs<ColorTargetState>(requireNotNull(descriptor.fragment).targets.single())
            assertEquals(GPUColorWrite.All, target.writeMask)
            val blend = requireNotNull(target.blend)
            assertEquals(GPUBlendFactor.Zero, blend.color.srcFactor)
            assertEquals(GPUBlendFactor.Zero, blend.alpha.srcFactor)
        }
        assertEquals(
            CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RECT_FRAGMENT_ENTRY_POINT,
            requireNotNull(rectIntersect.fragment).entryPoint,
        )
        assertEquals(
            CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RRECT_FRAGMENT_ENTRY_POINT,
            requireNotNull(rrectDifference.fragment).entryPoint,
        )
        assertEquals(
            GPUBlendFactor.SrcAlpha,
            requireNotNull(
                assertIs<ColorTargetState>(requireNotNull(rectIntersect.fragment).targets.single()).blend,
            ).color.dstFactor,
        )
        assertEquals(
            GPUBlendFactor.OneMinusSrcAlpha,
            requireNotNull(
                assertIs<ColorTargetState>(requireNotNull(rrectDifference.fragment).targets.single()).blend,
            ).color.dstFactor,
        )
    }

    @Test
    fun `coverage mask consumer descriptor keeps nearest sampling in shader and src over in state`() {
        val key = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan())
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
        )
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
            mapped.identity,
            shader,
            pipelineLayout,
        )

        assertEquals(PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY, mapped.componentIdentity)
        assertEquals(CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_VERTEX_ENTRY_POINT, descriptor.vertex.entryPoint)
        assertEquals(1, descriptor.vertex.buffers.size)
        assertEquals(
            CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_FRAGMENT_ENTRY_POINT,
            requireNotNull(descriptor.fragment).entryPoint,
        )
        assertNull(descriptor.depthStencil)
        val target = assertIs<ColorTargetState>(requireNotNull(descriptor.fragment).targets.single())
        val blend = requireNotNull(target.blend)
        assertEquals(GPUBlendFactor.One, blend.color.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.color.dstFactor)
    }

    @Test
    fun `path stencil producers retain the general dynamic uniform component`() {
        listOf(
            pathKey(producerWinding()),
            pathKey(producerEvenOdd()),
        ).forEach { key ->
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )

            assertEquals(
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                mapped.componentIdentity,
            )
            assertEquals(
                GPUWgpu4kCorePrimitiveBindingPolicy.DynamicUniformRequired,
                mapped.componentIdentity.bindingPolicy,
            )
            assertEquals(
                CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT,
                corePrimitiveWgpu4kRenderPipelineDescriptor(
                    mapped.identity,
                    shader,
                    pipelineLayout,
                ).vertex.entryPoint,
            )
        }
    }

    @Test
    fun `four clip stencil structural keys map to four exact native programs and binding policies`() {
        val cases = listOf(
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding) to
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.EvenOdd) to
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan()) to
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular,
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(true, srcOverBlendPlan()) to
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse,
        )

        cases.forEach { (key, program) ->
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            assertEquals(program, mapped.identity.program)
            assertTrue(
                isSupportedCorePrimitivePipelineCacheKey(
                    GPUWgpu4kCorePrimitivePipelineCacheKey(mapped.componentIdentity, mapped.identity),
                ),
            )
            if (key.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer) {
                assertEquals(PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY, mapped.componentIdentity)
                assertEquals(GPUWgpu4kCorePrimitiveBindingPolicy.NoBindings, mapped.componentIdentity.bindingPolicy)
            } else {
                assertEquals(PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY, mapped.componentIdentity)
                assertEquals(GPUWgpu4kCorePrimitiveBindingPolicy.DynamicUniformRequired, mapped.componentIdentity.bindingPolicy)
            }
        }
        assertEquals(37, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertTrue(CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES >= GPUWgpu4kCorePrimitivePipelineProgram.entries.size + 1)
    }

    @Test
    fun `clip stencil producer has an exact empty binding and pipeline layout`() {
        val producer = PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY

        assertEquals(emptyList(), corePrimitiveBindGroupLayoutDescriptor(producer).entries)
        assertEquals(emptyList(), corePrimitivePipelineLayoutDescriptor(producer, bindGroupLayout).bindGroupLayouts)
        assertEquals(1, corePrimitiveBindGroupLayoutDescriptor(PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY).entries.size)
        assertEquals(
            listOf(bindGroupLayout),
            corePrimitivePipelineLayoutDescriptor(
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                bindGroupLayout,
            ).bindGroupLayouts,
        )
    }

    @Test
    fun `clip stencil descriptors keep producer colorless and consumers read only`() {
        val winding = descriptor(
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding),
        )
        val evenOdd = descriptor(
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.EvenOdd),
        )
        val regular = descriptor(
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan()),
        )
        val inverse = descriptor(
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(true, srcOverBlendPlan()),
        )

        listOf(winding, evenOdd).forEach { producer ->
            assertProducerCommon(producer)
            assertEquals(CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_VERTEX_ENTRY_POINT, producer.vertex.entryPoint)
            assertEquals(
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_FRAGMENT_ENTRY_POINT,
                requireNotNull(producer.fragment).entryPoint,
            )
        }
        assertDepthStencil(
            winding,
            front = face(pass = GPUStencilOperation.IncrementWrap),
            back = face(pass = GPUStencilOperation.DecrementWrap),
            readMask = 0xffu,
            writeMask = 0xffu,
        )
        assertDepthStencil(
            evenOdd,
            front = face(pass = GPUStencilOperation.Invert),
            back = face(pass = GPUStencilOperation.Invert),
            readMask = 0xffu,
            writeMask = 0xffu,
        )
        assertDepthStencil(
            regular,
            front = face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
            back = face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
            readMask = 0xffu,
            writeMask = 0u,
        )
        assertDepthStencil(
            inverse,
            front = face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
            back = face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
            readMask = 0xffu,
            writeMask = 0u,
        )
    }

    @Test
    fun `four structural stencil variants normalize to four material pipeline programs`() {
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding,
            mappedIdentity(pathKey(producerWinding())).program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd,
            mappedIdentity(pathKey(producerEvenOdd())).program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
            mappedIdentity(pathKey(regularCover(), cover = true)).program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
            mappedIdentity(pathKey(inverseCover(), cover = true)).program,
        )
    }

    @Test
    fun `direct shading has distinct native identities with and without the path attachment`() {
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
            mappedIdentity(directKey()).program,
        )
        assertEquals(
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            mappedIdentity(directWithPathDepthStencilKey()).program,
        )

        val withoutAttachment = descriptor(directKey())
        val withAttachment = descriptor(directWithPathDepthStencilKey())
        assertNull(withoutAttachment.depthStencil)
        assertCoverCommon(withAttachment)
        assertDepthStencil(
            withAttachment,
            front = face(pass = GPUStencilOperation.Keep),
            back = face(pass = GPUStencilOperation.Keep),
            readMask = 0u,
            writeMask = 0u,
        )
    }

    @Test
    fun `dst read shading keys map to a formula program with appended dst texture and sampler slots`() {
        val srcOverComponent = requireNotNull(
            directKey().corePrimitiveNativeComponentIdentityOrNull(),
        )
        assertEquals(PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY, srcOverComponent)

        val dstReadKey = directKey().copy(
            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
                GPUBlendMode.DARKEN,
                "darken@v1",
                GPUSourceCoverageEncoding.None,
            ),
        )
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(dstReadKey),
        )
        // The Graphite dst-read recipe blends in the shader with fixed-function Src, so the
        // program keeps the direct geometry entry points and the formula lives in the shader.
        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver, mapped.identity.program)
        assertEquals(GPUWgpu4kCorePrimitiveBlendProgram.DstReadDarken, mapped.identity.blendProgram)
        assertTrue(isSupportedCorePrimitiveRenderPipelineIdentity(mapped.identity))
        assertEquals(
            "$CORE_PRIMITIVE_DST_READ_NATIVE_SHADER_IDENTITY:darken",
            mapped.componentIdentity.shaderIdentity,
        )
        assertEquals(
            CORE_PRIMITIVE_DST_READ_NATIVE_BINDING_LAYOUT_IDENTITY,
            mapped.componentIdentity.bindingLayoutIdentity,
        )
        // The session cache admits the dst-read formula component identity (Task 3c decision point).
        assertTrue(isSupportedCorePrimitivePipelineCacheKey(
            GPUWgpu4kCorePrimitivePipelineCacheKey(mapped.componentIdentity, mapped.identity),
        ))

        val layout = corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity)
        val entries = layout.entries
        assertEquals(3, entries.size)
        assertEquals(0u, entries[0].binding)
        assertEquals(GPUBufferBindingType.Uniform, requireNotNull(entries[0].buffer).type)
        assertEquals(1u, entries[1].binding)
        assertEquals(GPUTextureSampleType.Float, requireNotNull(entries[1].texture).sampleType)
        assertEquals(2u, entries[2].binding)
        assertEquals(GPUSamplerBindingType.Filtering, requireNotNull(entries[2].sampler).type)
        assertEquals(
            GPUShaderStage.Fragment,
            entries[1].visibility,
        )
        assertEquals(
            GPUShaderStage.Fragment,
            entries[2].visibility,
        )
        val srcOverLayout = corePrimitiveBindGroupLayoutDescriptor(srcOverComponent)
        assertEquals(1, srcOverLayout.entries.size)
        assertEquals(0u, srcOverLayout.entries.single().binding)

        // Fixed-function state is exact Src; the formula applies in the fragment shader.
        val target = assertIs<ColorTargetState>(
            requireNotNull(
                corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).fragment,
            ).targets.single(),
        )
        assertEquals(GPUBlendFactor.One, requireNotNull(target.blend).color.srcFactor)
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).color.dstFactor)
        assertEquals(GPUBlendOperation.Add, requireNotNull(target.blend).color.operation)
        assertEquals(GPUBlendFactor.One, requireNotNull(target.blend).alpha.srcFactor)
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).alpha.dstFactor)
    }

    @Test
    fun `analytic shape dst read shading keys map to the analytic dst read formula program`() {
        val dstReadKey = analyticShapeKey().copy(
            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
                GPUBlendMode.DARKEN,
                "darken@v1",
                GPUSourceCoverageEncoding.None,
            ),
        )
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(dstReadKey),
        )
        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead, mapped.identity.program)
        assertEquals(GPUWgpu4kCorePrimitiveBlendProgram.DstReadDarken, mapped.identity.blendProgram)
        assertTrue(isSupportedCorePrimitiveRenderPipelineIdentity(mapped.identity))
        assertEquals(
            "$CORE_PRIMITIVE_ANALYTIC_SHAPE_DST_READ_NATIVE_SHADER_IDENTITY:darken",
            mapped.componentIdentity.shaderIdentity,
        )
        assertEquals(
            CORE_PRIMITIVE_ANALYTIC_SHAPE_DST_READ_NATIVE_BINDING_LAYOUT_IDENTITY,
            mapped.componentIdentity.bindingLayoutIdentity,
        )
        assertTrue(
            isSupportedCorePrimitivePipelineCacheKey(
                GPUWgpu4kCorePrimitivePipelineCacheKey(mapped.componentIdentity, mapped.identity),
            ),
        )

        val layout = corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity)
        val entries = layout.entries
        assertEquals(3, entries.size)
        assertEquals(0u, entries[0].binding)
        assertEquals(GPUBufferBindingType.Uniform, requireNotNull(entries[0].buffer).type)
        assertEquals(80uL, requireNotNull(entries[0].buffer).minBindingSize)
        assertEquals(1u, entries[1].binding)
        assertEquals(GPUTextureSampleType.Float, requireNotNull(entries[1].texture).sampleType)
        assertEquals(2u, entries[2].binding)
        assertEquals(GPUSamplerBindingType.Filtering, requireNotNull(entries[2].sampler).type)

        // Fixed-function state is exact Src; the formula + analytic coverage apply in the shader.
        val target = assertIs<ColorTargetState>(
            requireNotNull(
                corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).fragment,
            ).targets.single(),
        )
        assertEquals(GPUBlendFactor.One, requireNotNull(target.blend).color.srcFactor)
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).color.dstFactor)
        assertEquals(GPUBlendOperation.Add, requireNotNull(target.blend).color.operation)
        assertEquals(GPUBlendFactor.One, requireNotNull(target.blend).alpha.srcFactor)
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).alpha.dstFactor)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT, corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).vertex.entryPoint)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT, requireNotNull(corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).fragment).entryPoint)

        // Scalar-coverage (AA) analytic-shape dst-read maps onto the same closed program.
        val scalarDstReadKey = analyticShapeKey().copy(
            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
                GPUBlendMode.COLOR_DODGE,
                "color_dodge@v1",
                GPUSourceCoverageEncoding.ScalarCoverageInShader,
            ),
        )
        val scalarMapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(scalarDstReadKey),
        )
        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead, scalarMapped.identity.program)
        assertEquals(GPUWgpu4kCorePrimitiveBlendProgram.DstReadColorDodge, scalarMapped.identity.blendProgram)
        assertEquals(
            "$CORE_PRIMITIVE_ANALYTIC_SHAPE_DST_READ_NATIVE_SHADER_IDENTITY:color_dodge",
            scalarMapped.componentIdentity.shaderIdentity,
        )

        // LCD-coverage dst-read on the analytic-shape lane stays refused.
        val lcdDstReadKey = analyticShapeKey().copy(
            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
                GPUBlendMode.DARKEN,
                "lcd.darken@v1",
                GPUSourceCoverageEncoding.LCDCoverageInShader,
            ),
        )
        assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(lcdDstReadKey),
        )
        assertNull(lcdDstReadKey.corePrimitiveNativeComponentIdentityOrNull())
    }

    @Test
    fun `direct shading maps non src over fixed blends to exact fixed function descriptors`() {
        val cases = listOf(
            fixedBlend(
                GPUBlendMode.CLEAR, "zero", "zero", "zero", "zero",
            ) to GPUWgpu4kCorePrimitiveBlendProgram.PremulClear,
            fixedBlend(
                GPUBlendMode.SRC, "one", "zero", "one", "zero",
            ) to GPUWgpu4kCorePrimitiveBlendProgram.PremulSrc,
            fixedBlend(
                GPUBlendMode.DST_OVER, "one-minus-dst-alpha", "one", "one-minus-dst-alpha", "one",
            ) to GPUWgpu4kCorePrimitiveBlendProgram.PremulDstOver,
        )

        cases.forEach { (blend, expectedBlendProgram) ->
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
                    directKey().copy(blend = blend),
                ),
            )
            assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver, mapped.identity.program)
            assertEquals(expectedBlendProgram, mapped.identity.blendProgram)
            assertTrue(isSupportedCorePrimitiveRenderPipelineIdentity(mapped.identity))
            val target = assertIs<ColorTargetState>(
                requireNotNull(
                    corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).fragment,
                ).targets.single(),
            )
            assertEquals(
                requireNotNull(expectedBlendProgram.colorSourceFactor).toWgpuFactor(),
                requireNotNull(target.blend).color.srcFactor,
            )
            assertEquals(
                requireNotNull(expectedBlendProgram.colorDestinationFactor).toWgpuFactor(),
                requireNotNull(target.blend).color.dstFactor,
            )
        }
    }

    @Test
    fun `analytic shape shading maps non src over fixed blends with the direct shader blend state`() {
        val clearKey = analyticShapeKey().copy(
            blend = fixedBlend(
                GPUBlendMode.CLEAR, "zero", "zero", "zero", "zero",
            ),
        )
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(clearKey),
        )
        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver, mapped.identity.program)
        assertEquals(GPUWgpu4kCorePrimitiveBlendProgram.PremulClear, mapped.identity.blendProgram)
        assertTrue(isSupportedCorePrimitiveRenderPipelineIdentity(mapped.identity))
        val target = assertIs<ColorTargetState>(
            requireNotNull(
                corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).fragment,
            ).targets.single(),
        )
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).color.srcFactor)
        assertEquals(GPUBlendFactor.Zero, requireNotNull(target.blend).color.dstFactor)
    }

    @Test
    fun `direct shading keeps unsupported and coverage encoded blends refused`() {
        val mutations = listOf(
            directKey().copy(blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Unsupported(GPUBlendMode.CLEAR)),
            directKey().copy(blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderNoDestination(
                GPUBlendMode.MODULATE, "modulate@v1", GPUSourceCoverageEncoding.ModulateRGBA,
            )),
            directKey().copy(blend = srcOverBlend().copy(
                sourceCoverage = GPUSourceCoverageEncoding.ModulateRGBA,
            )),
        )

        mutations.forEach { mutation ->
            assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(mutation),
            )
        }
    }

    @Test
    fun `analytic rect and rrect hard and aa keys map to four exact uniform64 programs`() {
        val cases = listOf(
            analyticKey(GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect, false) to
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
            analyticKey(GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect, true) to
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
            analyticKey(GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect, false) to
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
            analyticKey(GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect, true) to
                GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
        )

        cases.forEach { (key, expectedProgram) ->
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            assertEquals(expectedProgram, mapped.identity.program)
            assertEquals(CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_SHADER_IDENTITY, mapped.componentIdentity.shaderIdentity)
            assertEquals(
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_BINDING_LAYOUT_IDENTITY,
                mapped.componentIdentity.bindingLayoutIdentity,
            )
            assertNull(corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).depthStencil)
        }
    }

    @Test
    fun `analytic path covers retain rect rrect aa and stencil polarity in closed programs`() {
        data class Case(
            val geometry: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
            val antiAlias: Boolean,
            val inverse: Boolean,
            val expected: GPUWgpu4kCorePrimitivePipelineProgram,
        )

        val cases = listOf(
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                false,
                false,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                false,
                true,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                true,
                false,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                true,
                true,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                false,
                false,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                false,
                true,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                true,
                false,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular,
            ),
            Case(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
                true,
                true,
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse,
            ),
        )

        cases.forEach { case ->
            val key = pathKey(
                if (case.inverse) inverseCover() else regularCover(),
                cover = true,
            ).copy(
                clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
                    case.geometry,
                    case.antiAlias,
                ),
                sampleCount = 4,
            )
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
            )
            assertEquals(case.expected, mapped.identity.program)
            assertEquals(PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY, mapped.componentIdentity)
            val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
                mapped.identity,
                shader,
                pipelineLayout,
            )
            assertEquals(
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_VERTEX_ENTRY_POINT,
                descriptor.vertex.entryPoint,
            )
            assertEquals(
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_FRAGMENT_ENTRY_POINT,
                requireNotNull(descriptor.fragment).entryPoint,
            )
            assertDepthStencil(
                descriptor,
                front = if (case.inverse) {
                    face(
                        compare = GPUCompareFunction.Equal,
                        fail = GPUStencilOperation.Zero,
                        pass = GPUStencilOperation.Keep,
                    )
                } else {
                    face(
                        compare = GPUCompareFunction.NotEqual,
                        depthFail = GPUStencilOperation.Zero,
                        pass = GPUStencilOperation.Zero,
                    )
                },
                back = if (case.inverse) {
                    face(
                        compare = GPUCompareFunction.Equal,
                        fail = GPUStencilOperation.Zero,
                        pass = GPUStencilOperation.Keep,
                    )
                } else {
                    face(
                        compare = GPUCompareFunction.NotEqual,
                        depthFail = GPUStencilOperation.Zero,
                        pass = GPUStencilOperation.Zero,
                    )
                },
                readMask = 0xffu,
                writeMask = 0xffu,
            )
        }
    }

    @Test
    fun `analytic intersection4 is one uniform160 structural program with runtime stack facts outside key`() {
        val key = directKey().copy(
            clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4,
        )

        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1,
            key.uniformLayout,
        )
        val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key),
        )
        assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4, mapped.identity.program)
        assertEquals(PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY, mapped.componentIdentity)
        assertNull(corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, shader, pipelineLayout).depthStencil)
        assertSame(GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4, key.clip)
    }

    @Test
    fun `winding and even odd producers lower exact stencil8 state with no color writes`() {
        val winding = descriptor(pathKey(producerWinding()))
        val evenOdd = descriptor(pathKey(producerEvenOdd()))

        assertProducerCommon(winding)
        assertProducerCommon(evenOdd)
        assertDepthStencil(
            winding,
            front = face(pass = GPUStencilOperation.IncrementWrap),
            back = face(pass = GPUStencilOperation.DecrementWrap),
            readMask = 0xffu,
            writeMask = 0xffu,
        )
        assertDepthStencil(
            evenOdd,
            front = face(pass = GPUStencilOperation.Invert),
            back = face(pass = GPUStencilOperation.Invert),
            readMask = 0xffu,
            writeMask = 0x01u,
        )
    }

    @Test
    fun `regular and inverse covers lower graphite reset operations with premul src over`() {
        val regular = descriptor(pathKey(regularCover(), cover = true))
        val inverse = descriptor(pathKey(inverseCover(), cover = true))

        assertCoverCommon(regular)
        assertCoverCommon(inverse)
        assertDepthStencil(
            regular,
            front = face(
                compare = GPUCompareFunction.NotEqual,
                depthFail = GPUStencilOperation.Zero,
                pass = GPUStencilOperation.Zero,
            ),
            back = face(
                compare = GPUCompareFunction.NotEqual,
                depthFail = GPUStencilOperation.Zero,
                pass = GPUStencilOperation.Zero,
            ),
            readMask = 0xffu,
            writeMask = 0xffu,
        )
        assertDepthStencil(
            inverse,
            front = face(
                compare = GPUCompareFunction.Equal,
                fail = GPUStencilOperation.Zero,
                pass = GPUStencilOperation.Keep,
            ),
            back = face(
                compare = GPUCompareFunction.Equal,
                fail = GPUStencilOperation.Zero,
                pass = GPUStencilOperation.Keep,
            ),
            readMask = 0xffu,
            writeMask = 0xffu,
        )
    }

    @Test
    fun `direct and all stencil programs share exact vertex and primitive state`() {
        val descriptors = listOf(
            descriptor(directKey()),
            descriptor(directWithPathDepthStencilKey()),
            descriptor(pathKey(producerWinding())),
            descriptor(pathKey(producerEvenOdd())),
            descriptor(pathKey(regularCover(), cover = true)),
            descriptor(pathKey(inverseCover(), cover = true)),
        )

        descriptors.forEach { descriptor ->
            assertSame(shader, descriptor.vertex.module)
            assertEquals(CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT, descriptor.vertex.entryPoint)
            assertEquals(1, descriptor.vertex.buffers.size)
            assertEquals(8uL, descriptor.vertex.buffers.single().arrayStride)
            assertEquals(GPUVertexFormat.Float32x2, descriptor.vertex.buffers.single().attributes.single().format)
            assertEquals(GPUPrimitiveTopology.TriangleList, descriptor.primitive.topology)
            assertEquals(GPUFrontFace.CCW, descriptor.primitive.frontFace)
            assertEquals(GPUCullMode.None, descriptor.primitive.cullMode)
            assertEquals(1u, descriptor.multisample.count)
            assertSame(pipelineLayout, descriptor.layout)
        }
        assertNull(descriptors.first().depthStencil)
    }

    @Test
    fun `structural contradictions are typed refusals before native descriptor creation`() {
        val wrongBlend = pathKey(producerWinding()).copy(blend = srcOverBlend())

        assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(wrongBlend),
        )
    }

    private fun assertProducerCommon(descriptor: io.ygdrasil.webgpu.RenderPipelineDescriptor) {
        val fragment = requireNotNull(descriptor.fragment)
        assertEquals(CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT, fragment.entryPoint)
        val target = assertIs<ColorTargetState>(fragment.targets.single())
        assertEquals(GPUTextureFormat.RGBA8Unorm, target.format)
        assertNull(target.blend)
        assertEquals(GPUColorWrite.None, target.writeMask)
    }

    private fun assertCoverCommon(descriptor: io.ygdrasil.webgpu.RenderPipelineDescriptor) {
        val fragment = requireNotNull(descriptor.fragment)
        assertEquals(CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT, fragment.entryPoint)
        val target = assertIs<ColorTargetState>(fragment.targets.single())
        assertEquals(GPUColorWrite.All, target.writeMask)
        val blend = requireNotNull(target.blend)
        assertEquals(GPUBlendFactor.One, blend.color.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.color.dstFactor)
        assertEquals(GPUBlendFactor.One, blend.alpha.srcFactor)
        assertEquals(GPUBlendFactor.OneMinusSrcAlpha, blend.alpha.dstFactor)
    }

    private fun assertDepthStencil(
        descriptor: io.ygdrasil.webgpu.RenderPipelineDescriptor,
        front: io.ygdrasil.webgpu.StencilFaceState,
        back: io.ygdrasil.webgpu.StencilFaceState,
        readMask: UInt,
        writeMask: UInt,
    ) {
        val state = assertIs<DepthStencilState>(descriptor.depthStencil)
        assertEquals(GPUTextureFormat.Depth24PlusStencil8, state.format)
        assertEquals(false, state.depthWriteEnabled)
        assertEquals(GPUCompareFunction.Always, state.depthCompare)
        assertEquals(front, state.stencilFront)
        assertEquals(back, state.stencilBack)
        assertEquals(readMask, state.stencilReadMask)
        assertEquals(writeMask, state.stencilWriteMask)
    }

    private fun face(
        compare: GPUCompareFunction = GPUCompareFunction.Always,
        fail: GPUStencilOperation = GPUStencilOperation.Keep,
        depthFail: GPUStencilOperation = GPUStencilOperation.Keep,
        pass: GPUStencilOperation,
    ) = io.ygdrasil.webgpu.StencilFaceState(compare, fail, depthFail, pass)

    private fun descriptor(
        structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ): io.ygdrasil.webgpu.RenderPipelineDescriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(
        mappedIdentity(structuralKey),
        shader,
        pipelineLayout,
    )

    private fun mappedIdentity(
        structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ): GPUWgpu4kCorePrimitiveRenderPipelineIdentity = assertIs<
        GPUWgpu4kCorePrimitivePipelineMapping.Mapped,
    >(mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey)).identity

    private fun directKey() = GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
        topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
        blend = srcOverBlend(),
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
    )

    private fun analyticShapeKey() = directKey().copy(
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape,
    )

    private fun analyticKey(
        geometry: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
        antiAlias: Boolean,
    ) = directKey().copy(
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(geometry, antiAlias),
    )

    private fun srcOverBlendPlan() = org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "src-over",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun directWithPathDepthStencilKey() = directKey().copy(
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
            format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
            front = structuralFace(pass = GPUClipStencilOperation.Keep),
            back = structuralFace(pass = GPUClipStencilOperation.Keep),
            readMask = 0u,
            writeMask = 0u,
        ),
    )

    private fun pathKey(
        stencil: GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil,
        cover: Boolean = false,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil,
        topology = if (cover) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        } else {
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
        },
        blend = if (cover) srcOverBlend() else GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone,
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        role = if (cover) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover
        } else {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
        },
        depthStencil = stencil,
    )

    private fun producerWinding() = stencil(
        front = structuralFace(pass = GPUClipStencilOperation.IncrementWrap),
        back = structuralFace(pass = GPUClipStencilOperation.DecrementWrap),
        writeMask = 0xffu,
    )

    private fun producerEvenOdd() = stencil(
        front = structuralFace(pass = GPUClipStencilOperation.Invert),
        back = structuralFace(pass = GPUClipStencilOperation.Invert),
        writeMask = 0x01u,
    )

    private fun regularCover() = stencil(
        front = structuralFace(
            compare = GPUClipStencilCompare.NotEqual,
            pass = GPUClipStencilOperation.Zero,
            depthFail = GPUClipStencilOperation.Zero,
        ),
        back = structuralFace(
            compare = GPUClipStencilCompare.NotEqual,
            pass = GPUClipStencilOperation.Zero,
            depthFail = GPUClipStencilOperation.Zero,
        ),
        writeMask = 0xffu,
    )

    private fun inverseCover() = stencil(
        front = structuralFace(
            compare = GPUClipStencilCompare.Equal,
            pass = GPUClipStencilOperation.Keep,
            fail = GPUClipStencilOperation.Zero,
        ),
        back = structuralFace(
            compare = GPUClipStencilCompare.Equal,
            pass = GPUClipStencilOperation.Keep,
            fail = GPUClipStencilOperation.Zero,
        ),
        writeMask = 0xffu,
    )

    private fun stencil(
        front: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
        back: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
        writeMask: UInt,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = front,
        back = back,
        readMask = 0xffu,
        writeMask = writeMask,
    )

    private fun structuralFace(
        compare: GPUClipStencilCompare = GPUClipStencilCompare.Always,
        pass: GPUClipStencilOperation,
        fail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
        depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(compare, pass, fail, depthFail)

    private fun srcOverBlend() = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode = GPUBlendMode.SRC_OVER,
        sourceCoverage = GPUSourceCoverageEncoding.None,
        state = GPUFixedFunctionBlendState(
            stateId = "src-over",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
    )

    private fun fixedBlend(
        mode: GPUBlendMode,
        colorSource: String,
        colorDestination: String,
        alphaSource: String,
        alphaDestination: String,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode = mode,
        sourceCoverage = GPUSourceCoverageEncoding.None,
        state = GPUFixedFunctionBlendState(
            stateId = "test-${mode.gpuLabel}",
            color = GPUFixedFunctionBlendComponent(colorSource, colorDestination, "add"),
            alpha = GPUFixedFunctionBlendComponent(alphaSource, alphaDestination, "add"),
            writeMask = "rgba",
        ),
    )

    private fun String.toWgpuFactor(): GPUBlendFactor = when (this) {
        "zero" -> GPUBlendFactor.Zero
        "one" -> GPUBlendFactor.One
        "one-minus-dst-alpha" -> GPUBlendFactor.OneMinusDstAlpha
        else -> error("Unexpected test blend factor: $this")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>): T = Proxy.newProxyInstance(
        type.classLoader,
        arrayOf(type),
    ) { instance, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.firstOrNull()
            "toString" -> type.simpleName
            "close" -> null
            else -> null
        }
    } as T

    private val shader: GPUShaderModule = proxy(GPUShaderModule::class.java)
    private val bindGroupLayout: GPUBindGroupLayout = proxy(GPUBindGroupLayout::class.java)
    private val pipelineLayout: GPUPipelineLayout = proxy(GPUPipelineLayout::class.java)
}
