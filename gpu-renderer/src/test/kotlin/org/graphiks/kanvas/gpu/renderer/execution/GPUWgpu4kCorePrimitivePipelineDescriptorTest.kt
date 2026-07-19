package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBlock

import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Refused>(
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
                key.copy(depthStencil = directWithPathDepthStencilKey().depthStencil),
            ),
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
        assertEquals(21, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertEquals(30, CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES)
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
        assertEquals(21, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertEquals(30, CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES)
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
        assertEquals(21, GPUWgpu4kCorePrimitivePipelineProgram.entries.size)
        assertEquals(30, CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES)
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
