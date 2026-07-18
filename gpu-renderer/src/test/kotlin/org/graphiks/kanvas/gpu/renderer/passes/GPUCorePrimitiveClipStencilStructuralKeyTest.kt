package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUCorePrimitiveClipStencilStructuralKeyTest {
    @Test
    fun `clip stencil producer carries the explicit no bindings structural ABI`() {
        val winding = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val sameWinding = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)

        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.NoBindingsV1,
            winding.uniformLayout,
        )
        assertEquals("no-bindings-v1", winding.uniformLayout.stableIdentity)
        assertEquals(
            "pipeline.test.7be4b984518907628770b5d3e15deeffd4bbebe2357143bcf2a7be535bbf5ca2",
            winding.stableRenderPipelineKey("pipeline.test").value,
        )
        assertEquals(
            winding.stableRenderPipelineKey("pipeline.test"),
            sameWinding.stableRenderPipelineKey("pipeline.test"),
        )
    }

    @Test
    fun `only clip stencil producer may retain clip fill rule authority`() {
        val clipProducer = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val clipConsumer = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan())
        val shading = clipConsumer.copy(
            role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading,
            depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
        )
        val pathProducer = clipProducer.copy(
            role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
            shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil,
            clipStencilFillRule = null,
        )
        val pathCover = pathProducer.copy(
            role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
        )

        assertFailsWith<IllegalArgumentException>("Shading must reject clip fill authority") {
            shading.copy(clipStencilFillRule = GPUClipFillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException>("Path producer must reject clip fill authority") {
            pathProducer.copy(clipStencilFillRule = GPUClipFillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException>("Path cover must reject clip fill authority") {
            pathCover.copy(clipStencilFillRule = GPUClipFillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException>("Clip consumer must reject clip fill authority") {
            clipConsumer.copy(clipStencilFillRule = GPUClipFillRule.Winding)
        }
    }

    @Test
    fun `clip stencil taxonomy exposes four exact structural programs`() {
        val winding = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val evenOdd = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.EvenOdd)
        val regular = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
            inverseFill = false,
            blendPlan = srcOverBlendPlan(),
        )
        val inverse = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
            inverseFill = true,
            blendPlan = srcOverBlendPlan(),
        )

        assertEquals(GPUCorePrimitiveClipStencilStructuralProgram.ProducerWinding, winding.clipStencilStructuralProgramOrNull())
        assertEquals(GPUCorePrimitiveClipStencilStructuralProgram.ProducerEvenOdd, evenOdd.clipStencilStructuralProgramOrNull())
        assertEquals(GPUCorePrimitiveClipStencilStructuralProgram.ConsumerRegular, regular.clipStencilStructuralProgramOrNull())
        assertEquals(GPUCorePrimitiveClipStencilStructuralProgram.ConsumerInverse, inverse.clipStencilStructuralProgramOrNull())

        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer, winding.role)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer, regular.role)
        assertEquals(GPUClipFillRule.Winding, winding.clipStencilFillRule)
        assertEquals(GPUClipFillRule.EvenOdd, evenOdd.clipStencilFillRule)
        assertEquals(null, regular.clipStencilFillRule)
        assertNotEquals(winding, evenOdd)
        assertNotEquals(regular, inverse)
    }

    @Test
    fun `clip stencil producer and consumers retain exact D24S8 state`() {
        val winding = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val evenOdd = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.EvenOdd)
        val regular = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan())
        val inverse = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(true, srcOverBlendPlan())

        assertEquals(
            stencilState(
                front = face(pass = GPUClipStencilOperation.IncrementWrap),
                back = face(pass = GPUClipStencilOperation.DecrementWrap),
                readMask = 0xffu,
                writeMask = 0xffu,
            ),
            winding.depthStencil,
        )
        assertEquals(
            stencilState(
                front = face(pass = GPUClipStencilOperation.Invert),
                back = face(pass = GPUClipStencilOperation.Invert),
                readMask = 0xffu,
                writeMask = 0xffu,
            ),
            evenOdd.depthStencil,
        )
        assertEquals(
            stencilState(
                front = face(compare = GPUClipStencilCompare.NotEqual),
                back = face(compare = GPUClipStencilCompare.NotEqual),
                readMask = 0xffu,
                writeMask = 0u,
            ),
            regular.depthStencil,
        )
        assertEquals(
            stencilState(
                front = face(compare = GPUClipStencilCompare.Equal),
                back = face(compare = GPUClipStencilCompare.Equal),
                readMask = 0xffu,
                writeMask = 0u,
            ),
            inverse.depthStencil,
        )
    }

    @Test
    fun `dynamic clip facts stay outside structural keys`() {
        val firstProducer = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val secondProducer = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(GPUClipFillRule.Winding)
        val firstConsumer = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan())
        val secondConsumer = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(false, srcOverBlendPlan())

        assertEquals(firstProducer, secondProducer)
        assertEquals(firstConsumer, secondConsumer)
        val forbiddenFields = setOf(
            "bounds", "vertices", "indices", "contourStarts", "stencilReference",
            "order", "scissor", "geometry", "uniforms", "targetWidth", "targetHeight",
        )
        assertEquals(
            emptySet(),
            GPUCorePrimitiveRenderPipelineStructuralKey::class.java.declaredFields
                .map { it.name }
                .filterTo(linkedSetOf()) { it in forbiddenFields },
        )
    }

    private fun stencilState(
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
        compare: GPUClipStencilCompare = GPUClipStencilCompare.Always,
        pass: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = compare,
        passOperation = pass,
        failOperation = GPUClipStencilOperation.Keep,
        depthFailOperation = GPUClipStencilOperation.Keep,
    )

    private fun srcOverBlendPlan() = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "src-over",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )
}
