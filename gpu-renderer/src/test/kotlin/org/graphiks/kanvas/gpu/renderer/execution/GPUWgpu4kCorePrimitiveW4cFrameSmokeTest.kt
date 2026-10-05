package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Minimal public entry-point smoke coverage for the sealed Task5 W4c frame. */
class GPUWgpu4kCorePrimitiveW4cFrameSmokeTest {
    @Test
    fun `W4c Task5 graph reaches the executable preflight entry point`() {
        val frame = W4cExecutionFixture.framePlan()
        val scratch = assertNotNull(
            frame.steps.filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep>()
                .first().drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch,
        )
        frame.steps.filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep>()
            .flatMap { it.drawPackets }
            .forEach { packet ->
                val authority = requireNotNull(packet.corePrimitivePreparedAuthority)
                assertTrue(
                    scratch.matchesPreparedPacket(
                        scratch.planId,
                        frame.capabilitySeal.sealHash,
                        packet,
                        authority.structuralPipelineKey,
                        authority.renderPipelineKey,
                    ),
                    packet.packetId.value,
                )
            }

        val result = W4cExecutionFixture.preflight(frame)
        assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)
                ?.let {
                    "${it.diagnostic.code.value}: ${it.diagnostic.message}\n" +
                        frame.dumpLines().joinToString("\n")
                }
                .orEmpty(),
        )
    }
}
