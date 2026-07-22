package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

class GPUCorePrimitiveCoverageMaskStructuralKeyTest {
    @Test
    fun `coverage mask producer exposes four exact structural programs`() {
        val rectIntersect = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            GPUClipMaskCombine.Intersect,
        )
        val rectDifference = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            GPUClipMaskCombine.Difference,
        )
        val rrectIntersect = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
            GPUClipMaskCombine.Intersect,
        )
        val rrectDifference = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect,
            GPUClipMaskCombine.Difference,
        )

        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectIntersect,
            rectIntersect.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectDifference,
            rectDifference.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectIntersect,
            rrectIntersect.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectDifference,
            rrectDifference.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskProducerUniform64V1,
            rectIntersect.uniformLayout,
        )
        assertEquals(1, rectIntersect.sampleCount)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None, rectIntersect.depthStencil)
        assertNotEquals(rectIntersect, rectDifference)
        assertNotEquals(rectIntersect, rrectIntersect)
    }

    @Test
    fun `coverage mask consumer key is independent of dynamic invert and mask values`() {
        val first = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan())
        val second = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan())

        assertEquals(first, second)
        assertEquals(
            GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest,
            first.coverageMaskStructuralProgramOrNull(),
        )
        assertEquals(
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.CoverageMaskConsumerUniform64V1,
            first.uniformLayout,
        )
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Clip.CoverageMaskNearest, first.clip)
        assertNotEquals(
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
                GPUClipMaskCombine.Intersect,
            ),
            first,
        )
        val forbiddenFields = setOf(
            "bounds", "radii", "origin", "width", "height", "invert", "sourceOrder",
            "orderingToken", "contentKey", "resourceGeneration", "deviceGeneration",
        )
        assertEquals(
            emptySet(),
            GPUCorePrimitiveRenderPipelineStructuralKey::class.java.declaredFields
                .map { it.name }
                .filterTo(linkedSetOf()) { it in forbiddenFields },
        )
    }

    @Test
    fun `coverage mask structural programs require every exact fixed axis`() {
        val producer = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            GPUClipMaskCombine.Intersect,
        )
        val consumer = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(srcOverBlendPlan())

        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList, producer.topology)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw, producer.frontFace)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None, producer.cullMode)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList, consumer.topology)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw, consumer.frontFace)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None, consumer.cullMode)
        assertFailsWith<IllegalArgumentException> {
            producer.copy(topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect)
        }
        assertFailsWith<IllegalArgumentException> {
            consumer.copy(sampleCount = 4)
        }
        assertFailsWith<IllegalArgumentException> {
            consumer.copy(blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone)
        }
        assertNull(
            producer.copy(role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading)
                .coverageMaskStructuralProgramOrNull(),
        )
    }

    @Test
    fun `packet state id and nested dynamic clip values cannot enter coverage mask keys`() {
        val first = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(
            srcOverBlendPlan(stateId = "packet.dynamic.first"),
        )
        val second = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(
            srcOverBlendPlan(stateId = "packet.dynamic.second"),
        )

        assertEquals(first, second)
        val fixed = first.blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed
        assertEquals("coverage-mask-consumer-src-over-v1", fixed.state.stateId)
        val forbiddenNestedFields = setOf(
            "bounds", "radii", "origin", "width", "height", "invert", "depthStencilRequired",
            "sourceOrder", "orderingToken", "contentKey", "resourceGeneration", "deviceGeneration",
        )
        assertEquals(
            emptySet(),
            listOf(first.clip, first.blend, fixed.state)
                .flatMap { it::class.java.declaredFields.toList() }
                .map { it.name }
                .filterTo(linkedSetOf()) { it in forbiddenNestedFields },
        )
    }

    private fun srcOverBlendPlan(stateId: String = "src-over") = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = stateId,
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )
}
