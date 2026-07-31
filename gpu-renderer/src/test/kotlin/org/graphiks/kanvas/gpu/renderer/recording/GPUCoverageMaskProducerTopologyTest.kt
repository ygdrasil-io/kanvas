package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCoverageMaskProducerTopologyTest {
    @Test
    fun `common factory emits one exact topology for Core Text ColorGlyph shared consumers`() {
        val plan = plan()
        val producerPackets = plan.producers.mapIndexed { index, producer ->
            packet(index, plan, GPUDrawPacketRole.ClipProducer, producer)
        }
        val core = GPUCoverageMaskConsumerDescriptor.Core(
            packet(10, plan, GPUDrawPacketRole.Shading, null),
        )
        val text = GPUCoverageMaskConsumerDescriptor.TextA8(
            packet(11, plan, GPUDrawPacketRole.Shading, null),
        )
        val colorGlyph = GPUCoverageMaskConsumerDescriptor.ColorGlyph(
            packet(12, plan, GPUDrawPacketRole.Shading, null),
        )

        listOf(
            "core" to listOf(core),
            "text" to listOf(text),
            "color-glyph" to listOf(colorGlyph),
            "shared" to listOf(core, text, colorGlyph),
        ).forEach { (name, consumers) ->
            val mask = GPUFrameTargetRef("target.$name.mask")
            val topology = buildCoverageMaskProducerTopology(
                plan,
                GPUCoverageMaskProducerAttachment(
                    resource = mask,
                    diagnosticLabel = "$name.mask",
                    recordingId = GPURecordingID("recording.$name"),
                    producerTaskIds = listOf(
                        GPUTaskID("task.$name.mask.0"),
                        GPUTaskID("task.$name.mask.1"),
                    ),
                    producerPacketPartitions = producerPackets.map(::listOf),
                ),
                consumers,
            )

            assertEquals(1, listOf(topology.preparation).size, name)
            assertEquals(1, listOf(topology.allocation).size, name)
            assertEquals(mask, topology.preparation.resource, name)
            assertEquals(mask, topology.consumerUse.resource, name)
            assertEquals(GPUFrameResourceRole.ClipMask, topology.preparation.role, name)
            assertEquals(
                setOf(
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceUsage.TextureBinding,
                ),
                topology.preparation.usages,
                name,
            )
            assertEquals(GPUFrameMemoryCategory.ReusableScratch, topology.allocation.category, name)
            assertEquals(2, topology.producerRenders.size, name)
            assertEquals(1, topology.producerDependencies.size, name)
            assertEquals(
                listOf("clear", "load"),
                topology.producerRenders.map { render -> render.loadStore.loadOp },
                name,
            )
            assertEquals(
                plan.producers,
                topology.producerRenders.flatMap(GPUTask.Render::drawPackets).map { packet ->
                    (packet.clipProducerAuthority as GPUClipProducerAuthority.Mask).producer
                },
                name,
            )
            assertTrue(topology.producerRenders.all { render -> render.target == mask }, name)
        }
    }

    private fun packet(
        id: Int,
        plan: GPUClipExecutionPlan.CoverageMask,
        role: GPUDrawPacketRole,
        producer: GPUClipMaskProducerPlan?,
    ): GPUDrawPacket {
        val authority = GPUClipProducerAuthority.Mask(producer ?: plan.producers.first())
        return GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.$id"),
            commandIdValue = id,
            analysisRecordId = "analysis.$id",
            passId = "pass.$id",
            layerId = "root",
            bindingListId = "bindings.$id",
            insertionReasonCode = "coverage-mask-test",
            sortKey = id.toLong(),
            sortKeyPreimage = "order:$id",
            renderStepId = GPURenderStepID(
                if (role == GPUDrawPacketRole.ClipProducer) {
                    "clip.mask.producer"
                } else {
                    "test.consumer"
                },
            ),
            renderStepVersion = 1,
            role = role,
            blendPlan = corePrimitiveClipProducerBlendPlan(authority),
            renderPipelineKey = corePrimitiveClipProducerPipelineKey(plan, authority),
            bindingLayoutHash = "layout.test",
            vertexSourceLabel = "test",
            targetStateHash = "target.test",
            originalPaintOrder = id,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = GPUFrameProvenance.GmContent,
            clipExecutionPlan = plan,
            clipProducerAuthority = producer?.let(GPUClipProducerAuthority::Mask),
        )
    }

    private fun plan() = GPUClipExecutionPlan.CoverageMask(
        contentKey = "topology-mask",
        bounds = GPUPixelBounds(0, 0, 16, 16),
        sampleCount = 1,
        depthStencilRequired = false,
        orderingToken = GPUClipOrderingToken("topology-mask-order"),
        producers = listOf(
            GPUClipMaskProducerPlan(
                0,
                GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 14f, 14f)),
                GPUClipMaskCombine.Intersect,
                true,
            ),
            GPUClipMaskProducerPlan(
                1,
                GPUClipExecutionGeometry.Rect(GPUBounds(2f, 2f, 12f, 12f)),
                GPUClipMaskCombine.Difference,
                true,
            ),
        ),
        consumer = GPUClipMaskConsumerPlan(),
    )
}
