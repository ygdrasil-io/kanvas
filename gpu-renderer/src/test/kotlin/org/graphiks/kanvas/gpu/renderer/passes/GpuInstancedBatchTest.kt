package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

class GpuInstancedBatchTest {
    @Test
    fun `instanced packet group enforces shared render step pipeline and binding layout`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-3", commandId = 3, sortKey = 300L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-4", commandId = 4, sortKey = 400L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val group = GPUInstancedPacketGroup(
            packets = packets,
            renderStepId = sharedStepId,
            renderPipelineKey = sharedPipeline,
            bindingLayoutKey = sharedLayout,
        )

        assertEquals(4, group.packetCount)
        assertEquals(sharedStepId, group.renderStepId)
        assertEquals(sharedPipeline, group.renderPipelineKey)
        assertEquals(sharedLayout, group.bindingLayoutKey)
        assertEquals(listOf(GPUDrawPacketID("packet-1"), GPUDrawPacketID("packet-2"), GPUDrawPacketID("packet-3"), GPUDrawPacketID("packet-4")), group.packetIds)
        assertEquals(listOf(1, 2, 3, 4), group.commandIds)
        assertEquals(listOf(100L, 200L, 300L, 400L), group.sortKeys)
        assertEquals("fill-rect", group.renderStepId.value)
        assertEquals("render:solid-fill", group.renderPipelineKey.value)
    }

    @Test
    fun `instanced packet group validates matching criteria and rejects mismatched members`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"
        val mismatchedPipeline = GPURenderPipelineKey("render:gradient-fill")

        val validPackets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val mismatchPacket = rectPacket(packetId = "packet-3", commandId = 3, sortKey = 300L, stepId = sharedStepId, pipeline = mismatchedPipeline, layout = sharedLayout)

        val group = GPUInstancedPacketGroup(
            packets = validPackets,
            renderStepId = sharedStepId,
            renderPipelineKey = sharedPipeline,
            bindingLayoutKey = sharedLayout,
        )

        assertFalse(mismatchPacket.renderPipelineKey.toString() == group.renderPipelineKey.toString())
        assertTrue(mismatchPacket.renderPipelineKey != group.renderPipelineKey)
        assertTrue(validPackets.all { packet -> packet.renderStepId == group.renderStepId })
        assertTrue(validPackets.all { packet -> packet.renderPipelineKey == group.renderPipelineKey })
        assertTrue(validPackets.all { packet -> packet.bindingLayoutHash == group.bindingLayoutKey })
    }

    @Test
    fun `uniform strategy carries buffer label and configurable byte stride`() {
        val strategy = GPUInstancedUniformStrategy(
            bufferLabel = "uniform:instance-data",
            strideBytes = 64,
        )

        assertEquals("uniform:instance-data", strategy.bufferLabel)
        assertEquals(64, strategy.strideBytes)
        assertTrue(strategy.dumpLines().isNotEmpty())
        assertContains(strategy.dumpLines().first(), "instanced.uniform buffer=uniform:instance-data stride=64")
    }

    @Test
    fun `vertex strategy delivers per-instance vertex buffers with divisor`() {
        val buffers = listOf(
            GPUInstancedVertexBuffer(bufferLabel = "vertex:per-instance-transform", divisor = 1),
            GPUInstancedVertexBuffer(bufferLabel = "vertex:per-instance-color", divisor = 1),
        )
        val strategy = GPUInstancedVertexStrategy(vertexBuffers = buffers)

        assertEquals(2, strategy.vertexBuffers.size)
        assertEquals("vertex:per-instance-transform", strategy.vertexBuffers[0].bufferLabel)
        assertEquals(1, strategy.vertexBuffers[0].divisor)
        assertEquals("vertex:per-instance-color", strategy.vertexBuffers[1].bufferLabel)
        assertEquals(1, strategy.vertexBuffers[1].divisor)
        assertContains(strategy.dumpLines().first(), "instanced.vertex buffers=vertex:per-instance-transform:div1,vertex:per-instance-color:div1")
        assertEquals(listOf("vertex:per-instance-transform:div1", "vertex:per-instance-color:div1"), strategy.bufferSummaries)
    }

    @Test
    fun `instanced draw command computes instance count and selects strategies`() {
        val draw = GPUInstancedDrawCommand(
            indexCount = 6,
            instanceCount = 4,
            uniformStrategy = GPUInstancedUniformStrategy(bufferLabel = "uniform:instance-data", strideBytes = 64),
            vertexStrategy = GPUInstancedVertexStrategy(
                vertexBuffers = listOf(GPUInstancedVertexBuffer(bufferLabel = "vertex:per-instance-transform", divisor = 1)),
            ),
        )

        assertEquals(6, draw.indexCount)
        assertEquals(4, draw.instanceCount)
        assertNotNull(draw.uniformStrategy)
        assertEquals(64, draw.uniformStrategy!!.strideBytes)
        assertNotNull(draw.vertexStrategy)
        assertEquals(1, draw.vertexStrategy!!.vertexBuffers.size)
        assertContains(draw.dumpLines().first(), "instanced.draw indexCount=6 instanceCount=4")
        assertContains(draw.dumpLines().first(), "uniform=buffer:uniform:instance-data stride=64")
        assertContains(draw.dumpLines().first(), "vertex=buffers:vertex:per-instance-transform:div1")
    }

    @Test
    fun `instanced draw command without strategies uses fallback labels`() {
        val draw = GPUInstancedDrawCommand(
            indexCount = 3,
            instanceCount = 1,
            uniformStrategy = null,
            vertexStrategy = null,
        )

        assertEquals(3, draw.indexCount)
        assertEquals(1, draw.instanceCount)
        assertNull(draw.uniformStrategy)
        assertNull(draw.vertexStrategy)
        assertContains(draw.dumpLines().first(), "uniform=none")
        assertContains(draw.dumpLines().first(), "vertex=none")
    }

    @Test
    fun `batch grouper combines compatible consecutive packets into single group`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-3", commandId = 3, sortKey = 300L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-4", commandId = 4, sortKey = 400L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val result = batchForInstancedDraw(packets)

        assertEquals(1, result.size)
        val group = result[0] as GpuInstancedBatchResult.Grouped
        assertEquals(4, group.group.packetCount)
        assertEquals(sharedStepId, group.group.renderStepId)
        assertEquals(listOf(GPUDrawPacketID("packet-1"), GPUDrawPacketID("packet-2"), GPUDrawPacketID("packet-3"), GPUDrawPacketID("packet-4")), group.group.packetIds)
        assertEquals(listOf(1, 2, 3, 4), group.group.commandIds)
    }

    @Test
    fun `batch grouper splits groups on pipeline key change`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val pipelineA = GPURenderPipelineKey("render:solid-fill")
        val pipelineB = GPURenderPipelineKey("render:gradient-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = pipelineA, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = pipelineA, layout = sharedLayout),
            rectPacket(packetId = "packet-3", commandId = 3, sortKey = 300L, stepId = sharedStepId, pipeline = pipelineB, layout = sharedLayout),
            rectPacket(packetId = "packet-4", commandId = 4, sortKey = 400L, stepId = sharedStepId, pipeline = pipelineB, layout = sharedLayout),
        )

        val result = batchForInstancedDraw(packets)

        assertEquals(2, result.size)
        val group0 = assertIs<GpuInstancedBatchResult.Grouped>(result[0])
        val group1 = assertIs<GpuInstancedBatchResult.Grouped>(result[1])
        assertEquals(2, group0.group.packetCount)
        assertEquals(pipelineA, group0.group.renderPipelineKey)
        assertEquals(2, group1.group.packetCount)
        assertEquals(pipelineB, group1.group.renderPipelineKey)
    }

    @Test
    fun `batch grouper splits groups on render step identifier change`() {
        val fillStepId = GPURenderStepID("fill-rect")
        val pathStepId = GPURenderStepID("fill-path")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = fillStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = fillStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-3", commandId = 3, sortKey = 300L, stepId = pathStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val result = batchForInstancedDraw(packets)

        assertEquals(2, result.size)
        val group0 = assertIs<GpuInstancedBatchResult.Grouped>(result[0])
        val group1 = assertIs<GpuInstancedBatchResult.Grouped>(result[1])
        assertEquals(fillStepId, group0.group.renderStepId)
        assertEquals(pathStepId, group1.group.renderStepId)
        assertEquals(2, group0.group.packetCount)
        assertEquals(1, group1.group.packetCount)
    }

    @Test
    fun `batch grouper splits groups on binding layout change`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val layoutA = "layout-solid-v1"
        val layoutB = "layout-textured-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = layoutA),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = layoutB),
        )

        val result = batchForInstancedDraw(packets)

        assertEquals(2, result.size)
        val group0 = assertIs<GpuInstancedBatchResult.Grouped>(result[0])
        val group1 = assertIs<GpuInstancedBatchResult.Grouped>(result[1])
        assertEquals(layoutA, group0.group.bindingLayoutKey)
        assertEquals(layoutB, group1.group.bindingLayoutKey)
        assertEquals(1, group0.group.packetCount)
        assertEquals(1, group1.group.packetCount)
    }

    @Test
    fun `single packet produces a group of size one`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val result = batchForInstancedDraw(packets)

        assertEquals(1, result.size)
        val group = assertIs<GpuInstancedBatchResult.Grouped>(result[0])
        assertEquals(1, group.group.packetCount)
        assertEquals(listOf(GPUDrawPacketID("packet-1")), group.group.packetIds)
    }

    @Test
    fun `empty packet list produces empty batch result`() {
        val result = batchForInstancedDraw(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `batch grouper dump lines produce deterministic evidence`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val packets = listOf(
            rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
        )

        val result = batchForInstancedDraw(packets)

        val lines = result.flatMap { batchResult -> batchResult.dumpLines() }
        assertContains(lines.first(), "passes.instanced-batch group size=2 step=fill-rect pipeline=render:solid-fill layout=layout-solid-v1 packets=packet-1,packet-2")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.any { line -> line.contains("backend") && line.contains("handle") })
    }

    @Test
    fun `packet group dump lines reference member packets in declaration order`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"

        val group = GPUInstancedPacketGroup(
            packets = listOf(
                rectPacket(packetId = "packet-A", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
                rectPacket(packetId = "packet-B", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout),
            ),
            renderStepId = sharedStepId,
            renderPipelineKey = sharedPipeline,
            bindingLayoutKey = sharedLayout,
        )

        val lines = group.dumpLines()
        assertContains(lines.first(), "passes.instanced-group step=fill-rect pipeline=render:solid-fill layout=layout-solid-v1 size=2 commands=1,2 sortKeys=100,200")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
    }

    @Test
    fun `instanced packet group validates against empty packets`() {
        assertIllegalArgument("GPUInstancedPacketGroup.packets must not be empty") {
            GPUInstancedPacketGroup(
                packets = emptyList(),
                renderStepId = GPURenderStepID("fill-rect"),
                renderPipelineKey = GPURenderPipelineKey("render:solid-fill"),
                bindingLayoutKey = "layout-solid-v1",
            )
        }
    }

    @Test
    fun `instanced packet group refuses mismatched render step identifier`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"
        val mismatchedStepId = GPURenderStepID("fill-path")

        val matchingPacket = rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout)
        val mismatchedPacket = rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = mismatchedStepId, pipeline = sharedPipeline, layout = sharedLayout)

        assertIllegalArgument("GPUInstancedPacketGroup packet packet-2 renderStepId fill-path does not match group renderStepId fill-rect") {
            GPUInstancedPacketGroup(
                packets = listOf(matchingPacket, mismatchedPacket),
                renderStepId = sharedStepId,
                renderPipelineKey = sharedPipeline,
                bindingLayoutKey = sharedLayout,
            )
        }
    }

    @Test
    fun `instanced packet group refuses mismatched render pipeline key`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"
        val mismatchedPipeline = GPURenderPipelineKey("render:gradient-fill")

        val matchingPacket = rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout)
        val mismatchedPacket = rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = mismatchedPipeline, layout = sharedLayout)

        assertIllegalArgument("GPUInstancedPacketGroup packet packet-2 renderPipelineKey render:gradient-fill does not match group renderPipelineKey render:solid-fill") {
            GPUInstancedPacketGroup(
                packets = listOf(matchingPacket, mismatchedPacket),
                renderStepId = sharedStepId,
                renderPipelineKey = sharedPipeline,
                bindingLayoutKey = sharedLayout,
            )
        }
    }

    @Test
    fun `instanced packet group refuses mismatched binding layout key`() {
        val sharedStepId = GPURenderStepID("fill-rect")
        val sharedPipeline = GPURenderPipelineKey("render:solid-fill")
        val sharedLayout = "layout-solid-v1"
        val mismatchedLayout = "layout-textured-v1"

        val matchingPacket = rectPacket(packetId = "packet-1", commandId = 1, sortKey = 100L, stepId = sharedStepId, pipeline = sharedPipeline, layout = sharedLayout)
        val mismatchedPacket = rectPacket(packetId = "packet-2", commandId = 2, sortKey = 200L, stepId = sharedStepId, pipeline = sharedPipeline, layout = mismatchedLayout)

        assertIllegalArgument("GPUInstancedPacketGroup packet packet-2 bindingLayoutKey layout-textured-v1 does not match group bindingLayoutKey layout-solid-v1") {
            GPUInstancedPacketGroup(
                packets = listOf(matchingPacket, mismatchedPacket),
                renderStepId = sharedStepId,
                renderPipelineKey = sharedPipeline,
                bindingLayoutKey = sharedLayout,
            )
        }
    }

    @Test
    fun `instanced vertex buffer validates divisor`() {
        val valid = GPUInstancedVertexBuffer(bufferLabel = "vertex:instance-data", divisor = 1)
        assertEquals("vertex:instance-data", valid.bufferLabel)
        assertEquals(1, valid.divisor)

        assertIllegalArgument("GPUInstancedVertexBuffer.divisor must be positive") {
            GPUInstancedVertexBuffer(bufferLabel = "vertex:bad", divisor = 0)
        }

        assertIllegalArgument("GPUInstancedVertexBuffer.bufferLabel must not be blank") {
            GPUInstancedVertexBuffer(bufferLabel = "", divisor = 1)
        }
    }

    @Test
    fun `instanced uniform strategy validates stride and label`() {
        assertIllegalArgument("GPUInstancedUniformStrategy.strideBytes must be positive") {
            GPUInstancedUniformStrategy(bufferLabel = "uniform:data", strideBytes = 0)
        }

        assertIllegalArgument("GPUInstancedUniformStrategy.strideBytes must be positive") {
            GPUInstancedUniformStrategy(bufferLabel = "uniform:data", strideBytes = -1)
        }

        assertIllegalArgument("GPUInstancedUniformStrategy.bufferLabel must not be blank") {
            GPUInstancedUniformStrategy(bufferLabel = "", strideBytes = 64)
        }
    }

    @Test
    fun `instanced draw command validates counts`() {
        assertIllegalArgument("GPUInstancedDrawCommand.indexCount must be non-negative") {
            GPUInstancedDrawCommand(indexCount = -1, instanceCount = 1, uniformStrategy = null, vertexStrategy = null)
        }

        assertIllegalArgument("GPUInstancedDrawCommand.instanceCount must be positive") {
            GPUInstancedDrawCommand(indexCount = 6, instanceCount = 0, uniformStrategy = null, vertexStrategy = null)
        }
    }

    @Test
    fun `instanced vertex strategy handles empty buffer list`() {
        val strategy = GPUInstancedVertexStrategy(vertexBuffers = emptyList())
        assertEquals(0, strategy.vertexBuffers.size)
        assertContains(strategy.dumpLines().first(), "instanced.vertex buffers=none")
        assertEquals(emptyList(), strategy.bufferSummaries)
    }

    private fun rectPacket(
        packetId: String,
        commandId: Int,
        sortKey: Long,
        stepId: GPURenderStepID,
        pipeline: GPURenderPipelineKey,
        layout: String,
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID(packetId),
            commandIdValue = commandId,
            analysisRecordId = "analysis-$commandId",
            passId = "main-pass",
            layerId = "root-layer",
            bindingListId = "bindings-$commandId",
            insertionReasonCode = "native-fill-rect",
            sortKey = sortKey,
            sortKeyPreimage = "paint|clip|transform|$commandId",
            renderStepId = stepId,
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            renderPipelineKey = pipeline,
            bindingLayoutHash = layout,
            uniformSlot = uniformSlot(commandId),
            resourceSlot = resourceSlot(commandId),
            vertexSourceLabel = "solid-quad",
            scissorBoundsHash = "scissor-0-0-64-64",
            targetStateHash = "rgba8-premul-msaa1",
            originalPaintOrder = commandId,
            resourceGeneration = 7L,
        )

    private fun uniformSlot(commandId: Int): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("uniform-$commandId"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-$commandId"),
            byteOffset = ((commandId - 1) * 64).toLong(),
        )

    private fun resourceSlot(commandId: Int): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("resource-$commandId"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-$commandId"),
            bindingIndex = 0,
        )
}

private fun assertIllegalArgument(expectedMessageFragment: String, block: () -> Unit) {
    try {
        block()
        throw AssertionError("Expected IllegalArgumentException with message containing: $expectedMessageFragment")
    } catch (e: IllegalArgumentException) {
        assertContains(e.message ?: "", expectedMessageFragment)
    }
}
