package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitivePathStencilStructuralKeyTest {
    @Test
    fun `direct shading keeps its historical stable pipeline hash`() {
        val key = GPUCorePrimitiveRenderPipelineStructuralKey(
            shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
            topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
            blend = fixedBlend(),
            clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        )

        assertEquals(
            "pipeline.test.c9154fca6554ac5609573aa375e2d7cd3fb59d95665aa2ea86b22f1b3ff8d3aa",
            key.stableRenderPipelineKey("pipeline.test").value,
        )
    }

    @Test
    fun `path stencil producer and cover retain exact distinct stencil authority`() {
        val semantic = pathSemantic(GPUCorePrimitiveFillRule.Winding, inverseFill = false)
        val producer = pathKey(semantic, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer)
        val cover = pathKey(semantic, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover)

        assertNotEquals(producer, cover)
        assertNotEquals(
            producer.stableRenderPipelineKey("pipeline.test"),
            cover.stableRenderPipelineKey("pipeline.test"),
        )
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone, producer.blend)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan, producer.topology)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList, cover.topology)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw, producer.frontFace)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None, producer.cullMode)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm, producer.colorFormat)
        assertEquals(1, producer.sampleCount)

        assertEquals(
            expectedStencil(
                front = face(GPUClipStencilCompare.Always, GPUClipStencilOperation.IncrementWrap),
                back = face(GPUClipStencilCompare.Always, GPUClipStencilOperation.DecrementWrap),
                readMask = 0xffu,
                writeMask = 0xffu,
            ),
            producer.depthStencil,
        )
        assertEquals(
            expectedStencil(
                front = face(
                    GPUClipStencilCompare.NotEqual,
                    GPUClipStencilOperation.Zero,
                    depthFail = GPUClipStencilOperation.Zero,
                ),
                back = face(
                    GPUClipStencilCompare.NotEqual,
                    GPUClipStencilOperation.Zero,
                    depthFail = GPUClipStencilOperation.Zero,
                ),
                readMask = 0xffu,
                writeMask = 0xffu,
            ),
            cover.depthStencil,
        )
    }

    @Test
    fun `fill rule changes producer while regular and inverse select exact cover policies`() {
        val winding = pathSemantic(GPUCorePrimitiveFillRule.Winding, inverseFill = false)
        val evenOdd = pathSemantic(GPUCorePrimitiveFillRule.EvenOdd, inverseFill = false)
        val windingCover = pathKey(winding, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover)
        val regular = pathKey(evenOdd, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover)
        val inverse = pathKey(
            pathSemantic(GPUCorePrimitiveFillRule.EvenOdd, inverseFill = true),
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
        )
        val windingProducer = pathKey(winding, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer)
        val evenOddProducer = pathKey(evenOdd, GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer)

        assertNotEquals(windingProducer, evenOddProducer)
        assertNotEquals(
            windingProducer.stableRenderPipelineKey("pipeline.test"),
            evenOddProducer.stableRenderPipelineKey("pipeline.test"),
        )
        assertEquals(
            expectedStencil(
                front = face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert),
                back = face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert),
                readMask = 0xffu,
                writeMask = 0x01u,
            ),
            evenOddProducer.depthStencil,
        )
        assertEquals(windingCover, regular)
        assertNotEquals(regular, inverse)
        assertNotEquals(
            regular.stableRenderPipelineKey("pipeline.test"),
            inverse.stableRenderPipelineKey("pipeline.test"),
        )
        assertEquals(
            expectedStencil(
                front = face(GPUClipStencilCompare.Equal, GPUClipStencilOperation.Keep, GPUClipStencilOperation.Zero),
                back = face(GPUClipStencilCompare.Equal, GPUClipStencilOperation.Keep, GPUClipStencilOperation.Zero),
                readMask = 0xffu,
                writeMask = 0xffu,
            ),
            inverse.depthStencil,
        )
    }

    @Test
    fun `content bounds vertices scissor and pass load store are absent from the structural key`() {
        val first = pathSemantic(
            fillRule = GPUCorePrimitiveFillRule.Winding,
            inverseFill = false,
            pivot = -1f,
            xOffset = 0f,
            targetBounds = GPUPixelBounds(0, 0, 16, 16),
            scissorBounds = GPUPixelBounds(0, 0, 8, 8),
        )
        val second = pathSemantic(
            fillRule = GPUCorePrimitiveFillRule.Winding,
            inverseFill = false,
            pivot = -3f,
            xOffset = 2f,
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            scissorBounds = GPUPixelBounds(4, 3, 20, 18),
        )

        val firstKey = corePrimitivePathStencilRenderPipelineStructuralKey(
            first,
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
            GPUClipExecutionPlan.NoClip,
            blendPlan(),
        )
        val secondKey = corePrimitivePathStencilRenderPipelineStructuralKey(
            second,
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
            GPUClipExecutionPlan.ScissorOnly(second.scissorBounds),
            blendPlan(),
        )

        assertEquals(firstKey, secondKey)
        val forbiddenFields = setOf(
            "bounds", "targetBounds", "coverBounds", "vertices", "indices", "scissor", "scissorBounds",
            "load", "loadStore", "store",
        )
        assertFalse(
            GPUCorePrimitiveRenderPipelineStructuralKey::class.java.declaredFields.any { it.name in forbiddenFields },
        )
    }

    private fun pathKey(
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    ) = corePrimitivePathStencilRenderPipelineStructuralKey(
        semantic,
        role,
        GPUClipExecutionPlan.NoClip,
        blendPlan(),
    )

    private fun expectedStencil(
        front: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
        back: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
        readMask: UInt,
        writeMask: UInt,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = front,
        back = back,
        readMask = readMask,
        writeMask = writeMask,
    )

    private fun face(
        compare: GPUClipStencilCompare,
        pass: GPUClipStencilOperation,
        fail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
        depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = compare,
        passOperation = pass,
        failOperation = fail,
        depthFailOperation = depthFail,
    )

    private fun pathSemantic(
        fillRule: GPUCorePrimitiveFillRule,
        inverseFill: Boolean,
        pivot: Float = -1f,
        xOffset: Float = 0f,
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 16, 16),
        scissorBounds: GPUPixelBounds = targetBounds,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = 1,
            sourceFamily = GPUCorePrimitiveSourceFamily.Path,
            geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(
                    pivot, pivot, 1f + xOffset, 1f, 7f + xOffset, 1f,
                    pivot, pivot, 7f + xOffset, 1f, 4f + xOffset, 7f,
                    pivot, pivot, 4f + xOffset, 7f, 1f + xOffset, 1f,
                ),
                indices = (0..8).toList(),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 3,
                coverBounds = GPUPixelBounds(0, 0, 12, 12),
                geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                fillRule = fillRule,
                inverseFill = inverseFill,
            ),
            premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            blendPlanIdentity = "fixed-src-over",
            frameProvenance = GPUFrameProvenance.GmContent,
            coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
        ),
    )

    private fun blendPlan() = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "src-over",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun fixedBlend() = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode = GPUBlendMode.SRC_OVER,
        sourceCoverage = GPUSourceCoverageEncoding.None,
        state = fixedBlendState(),
    )

    private fun fixedBlendState() = GPUFixedFunctionBlendState(
        stateId = "src-over",
        color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
        alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
        writeMask = "rgba",
    )
}
