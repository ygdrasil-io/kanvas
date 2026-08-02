package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Builder-level route test for composite frames.
 *
 * The gate still routes composites to Legacy (Task 9 flips it), so the request candidate is
 * constructed directly — this exercises the composite handling inside
 * [GPUPreparedSurfaceFrameBuilder.build], not the gate.
 */
class GPUPreparedCompositeFrameRouteIntegrationTest {

    @Test
    fun `composite frame build with plain saveLayer produces commands`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.BeginLayer(Rect.fromLTRB(0f, 0f, 64f, 48f), null),
                    rect(),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.diagnostic?.code?.value.toString(),
        )
        assertTrue(ready.compositeCommandCount > 0)
        val commandKinds = ready.taskList.compositeCommands.map { it::class.simpleName }.toSet()
        assertTrue(
            commandKinds.contains("PrepareLayerTarget"),
            "missing PrepareLayerTarget in $commandKinds",
        )
        assertTrue(
            commandKinds.contains("RenderLayerChildren"),
            "missing RenderLayerChildren in $commandKinds",
        )
        assertTrue(
            commandKinds.contains("CompositeLayer"),
            "missing CompositeLayer in $commandKinds",
        )
        assertEquals(
            1,
            ready.taskList.compositeCommands
                .filterIsInstance<GPUPassCommand.CompositeLayer>().size,
        )
    }

    @Test
    fun `composite only frame elides flat child render`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.BeginLayer(Rect.fromLTRB(0f, 0f, 64f, 48f), null),
                    rect(),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.diagnostic?.code?.value.toString(),
        )
        assertEquals(0, ready.visualOperationCount)
        assertTrue(ready.compositeCommandCount > 0)
        val commandKinds = ready.taskList.compositeCommands.map { it::class.simpleName }.toSet()
        assertTrue(
            commandKinds.contains("RenderLayerChildren"),
            "missing RenderLayerChildren in $commandKinds",
        )
    }

    @Test
    fun `draw picture in composite frame is not silently dropped`() {
        val picture = org.graphiks.kanvas.picture.Picture(
            Rect.fromLTRB(0f, 0f, 64f, 48f),
            listOf(rect()),
        )
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.BeginLayer(Rect.fromLTRB(0f, 0f, 64f, 48f), null),
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix33.identity(),
                        clip = ClipStack.WideOpen,
                    ),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        when (result) {
            is GPUPreparedSurfaceFrameBuildResult.Ready -> {
                assertTrue(
                    result.compositeCommandCount > 0,
                    "composite commands must cover the picture content",
                )
                val commandKinds =
                    result.taskList.compositeCommands.map { it::class.simpleName }.toSet()
                assertTrue(
                    commandKinds.contains("RenderLayerChildren"),
                    "missing RenderLayerChildren in $commandKinds",
                )
            }
            is GPUPreparedSurfaceFrameBuildResult.Refused -> {
                val code = result.diagnostic.code.value
                assertTrue(
                    code == "unsupported.surface.prepared.mixed-composite-topology" ||
                        code == "unsupported.surface.prepared.draw-picture",
                    "expected a stable terminal refusal, got $code",
                )
            }
            is GPUPreparedSurfaceFrameBuildResult.NoOp -> {
                error("a NoOp result silently drops the picture content")
            }
        }
    }

    @Test
    fun `mixed composite and visual frame keeps root visuals flat and composites layers`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    rect(),
                    DisplayOp.BeginLayer(Rect.fromLTRB(0f, 0f, 64f, 48f), null),
                    rect(),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.diagnostic?.code?.value.toString(),
        )
        assertEquals(1, ready.visualOperationCount)
        assertTrue(ready.compositeCommandCount > 0)
    }

    @Test
    fun `top level draw picture refuses instead of silently dropping`() {
        val picture = org.graphiks.kanvas.picture.Picture(
            Rect.fromLTRB(0f, 0f, 64f, 48f),
            listOf(rect()),
        )
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix33.identity(),
                        clip = ClipStack.WideOpen,
                    ),
                ),
            ),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result)
        assertEquals(
            "unsupported.surface.prepared.mixed-composite-topology",
            refused.diagnostic.code.value,
        )
    }

    @Test
    fun `composite frame build refuses stably when capture fails`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(DisplayOp.BeginLayer(Rect.fromLTRB(0f, 0f, 64f, 48f), null)),
            ),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result)
        assertEquals("unsupported.composite.layer.unbalanced", refused.diagnostic.code.value)
        assertEquals("surface.composite", refused.diagnostic.facts["boundary"])
        assertEquals("0", refused.diagnostic.facts["operationIndex"])
    }

    private fun request(
        operations: List<DisplayOp>,
    ): GPUPreparedSurfaceFrameBuildRequest {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return GPUPreparedSurfaceFrameBuildRequest(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = color,
            ),
            targetFacts = GPUTargetFacts(32, 24, "rgba8unorm-srgb"),
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            capabilities = capabilities(),
            deviceGeneration = GPUDeviceGenerationID(11),
            target = GPUFrameTargetRef("surface-frame-target"),
            recordingId = GPURecordingID("surface-frame-recording"),
            frameId = GPUFrameID(77),
            readbackRequestId = GPUReadbackRequestID("surface-frame-readback"),
        )
    }

    private fun capabilities(): GPUCapabilities {
        val base = GPUProductFlagConfig(boundedClipEnabled = true).buildCapabilities()
        val extra = buildList {
            add(capability("first_slice.fill_rect.native"))
            add(capability(PATH_FILL_STENCIL_COVER))
        }
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts + extra,
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:composite-route-integration",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            rendererFeatures = buildSet {
                add(GPURendererFeature.RenderPass)
                add(GPURendererFeature.Readback)
            },
        )
    }

    private fun capability(name: String) = GPUCapabilityFact(
        name = name,
        source = "test",
        value = "supported",
        affectsValidity = true,
        evidenceLabel = "test:$name",
    )

    private fun rect(): DisplayOp.DrawRect = DisplayOp.DrawRect(
        Rect.fromLTRB(2f, 3f, 12f, 11f),
        Paint.fill(Color.RED).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )
}
