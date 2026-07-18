package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.GPUBlendFactor
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUWgpu4kCorePrimitivePipelineDescriptorTest {
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

    private fun analyticKey(
        geometry: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
        antiAlias: Boolean,
    ) = directKey().copy(
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(geometry, antiAlias),
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
    private val pipelineLayout: GPUPipelineLayout = proxy(GPUPipelineLayout::class.java)
}
