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
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

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
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
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
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
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
    fun `composite frame splits children into a layer targeted render`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    rect(),
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
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
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val layerRenders = renders.filter { render ->
            render.target.value.startsWith("layer-target:")
        }
        assertEquals(
            1,
            layerRenders.size,
            "expected exactly one layer-targeted children render in $renders",
        )
        assertEquals(
            listOf(1),
            layerRenders.single().drawPackets.map { packet -> packet.commandIdValue },
            "the layer children render must carry exactly the captured child packets",
        )
        val sceneRender = renders.singleOrNull { render ->
            render.target.value == "surface-frame-target"
        }
        assertEquals(
            listOf(0),
            sceneRender?.drawPackets?.map { packet -> packet.commandIdValue },
            "root visuals must stay on the surface target render",
        )
    }

    @Test
    fun `draw picture inside a saveLayer scope refuses instead of silently dropping`() {
        val picture = org.graphiks.kanvas.picture.Picture(
            RectF32.ofLTRB(0f, 0f, 64f, 48f),
            listOf(rect()),
        )
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        // Task 17 follow-up: the capturer refuses an unpainted DrawPicture inside a saveLayer
        // scope at the capture boundary with unsupported.composite.operation (like every
        // other non-core child). Its expanded children cannot ride the composite commands —
        // the flat mapper never maps them (commandIdsByOperationIndex records only top-level
        // mapped ops), so the previous Ready branch silently dropped the picture content
        // (pixel evidence: pure red, no blue) and the picture-only case died on the internal
        // invalid.prepared-surface.layer-target invariant. Both must be this loud refusal.
        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result)
        assertEquals("unsupported.composite.operation", refused.diagnostic.code.value)
        assertEquals("surface.composite", refused.diagnostic.facts["boundary"])
    }

    @Test
    fun `mixed rect and picture inside a saveLayer scope refuses loudly`() {
        val picture = org.graphiks.kanvas.picture.Picture(
            RectF32.ofLTRB(0f, 0f, 64f, 48f),
            listOf(rect()),
        )
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
                    rect(),
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                    DisplayOp.EndLayer,
                ),
            ),
        )

        // Same capture-boundary refusal as the picture-only layer: a covered DrawPicture can
        // never be materialized by the composite commands, so the frame refuses loudly
        // instead of dropping the blue picture behind the red rect.
        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result)
        assertEquals("unsupported.composite.operation", refused.diagnostic.code.value)
        assertEquals("1", refused.diagnostic.facts["operationIndex"])
    }

    @Test
    fun `mixed composite and visual frame keeps root visuals flat and composites layers`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    rect(),
                    DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null),
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
            RectF32.ofLTRB(0f, 0f, 64f, 48f),
            listOf(rect()),
        )
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix3x3F32.Identity,
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
                listOf(DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 64f, 48f), null)),
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
        RectF32.ofLTRB(2f, 3f, 12f, 11f),
        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )
}
