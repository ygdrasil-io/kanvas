package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.BOUNDED_CLIP_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextClipVariant
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

class GPUPreparedSurfaceFrameBuilderTextTest {
    @Test
    fun `empty text is accepted and elided before typeface clip and blend work`() {
        val operation = textOperation().copy(
            blob = TextBlob(emptyList()),
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.DARKEN),
            clip = orderedCoverageMaskClip(),
        )

        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(operation),
                target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(1),
            ),
        )

        assertEquals(setOf(0), prepared.inventory.acceptedTextOperationIndices)
        assertEquals(setOf(0), prepared.inventory.elidedTextOperationIndices)
        assertTrue(prepared.mapping.visualCommands.isEmpty())
        assertTrue(prepared.inventory.pages.isEmpty())
        assertTrue(prepared.inventory.subRunsByOperationIndex.isEmpty())
        assertTrue(prepared.inventory.strokePathsByOperationIndex.isEmpty())
        assertEquals(0, prepared.inventory.metrics.glyphCount)
        assertEquals(0, prepared.inventory.metrics.instanceCount)
    }

    @Test
    fun `rect plus empty text builds only the core packet and no text resource`() {
        val rect = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(Color.WHITE),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val emptyText = textOperation().copy(
            blob = TextBlob(emptyList()),
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.DARKEN),
            clip = orderedCoverageMaskClip(),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(rect, emptyText),
                    identity = "empty-text",
                ),
            ),
        )
        val packets = ready.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)

        assertEquals(1, packets.size)
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(packets.single().semanticPayload)
        assertTrue(ready.taskList.tasks.none { it is GPUTask.DestinationSnapshots })
    }

    @Test
    fun `destination read color glyph as first visual op synthesizes leading clear before the copy`() {
        val destinationReadText = colorTextOperation(fontSize = 28f).copy(
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.COLOR_DODGE),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(destinationReadText),
                    identity = "dst-read-clear-synthesis",
                ),
            ),
        )
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val clearRender = renders.first()
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(clearRender.drawPackets.single().semanticPayload)
        assertEquals("clear", clearRender.loadStore.loadOp)
        val consumer = ready.taskList.tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
            .flatMap { task -> task.payload.operations }
            .flatMap(GPUDestinationSnapshotOperation::consumers)
            .single()
        val consumerRender = renders.single { render ->
            render.drawPackets.any { packet -> packet.packetId == consumer.packetId }
        }
        assertTrue(
            renders.indexOf(consumerRender) > renders.indexOf(clearRender),
            "destination snapshot consumer must render after the synthesized scene clear",
        )
        assertEquals("load", consumerRender.loadStore.loadOp)
        assertIs<GPUDrawSemanticPayload.ColorGlyph>(consumerRender.drawPackets.single().semanticPayload)
        // The synthesized clear is a REAL visual op in the stream: one extra visual command
        // (and one extra dispatched draw/render pass/pipeline bind at native execution).
        assertEquals(2, ready.visualOperationCount)
    }

    @Test
    fun `empty blob destination read text stays builder no op without synthesized clear`() {
        val emptyText = textOperation().copy(
            blob = TextBlob(emptyList()),
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.COLOR_DODGE),
        )

        // A frame whose only visual op is an elided empty text is a builder NoOp. If the
        // synthesis fired for the empty blob, the injected clear rect would make the frame
        // Ready with one core packet, so the NoOp classification pins the empty-glyph guard.
        val noOp = GPUPreparedSurfaceFrameBuilder.build(
            preparedFrameRequest(
                operations = listOf(emptyText),
                identity = "empty-dst-read-no-synthesis",
            ),
        )
        assertIs<GPUPreparedSurfaceFrameBuildResult.NoOp>(noOp)
    }

    @Test
    fun `empty text before destination read text still synthesizes leading clear`() {
        // The empty text elides to nothing, so the destination-reading text is the first
        // painted op: the synthesis must skip the empty blob when locating the first visual.
        val emptyText = textOperation().copy(blob = TextBlob(emptyList()))
        val destinationReadText = colorTextOperation(fontSize = 28f).copy(
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.COLOR_DODGE),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(emptyText, destinationReadText),
                    identity = "empty-then-dst-read-synthesis",
                ),
            ),
        )
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val clearRender = renders.first()
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(clearRender.drawPackets.single().semanticPayload)
        assertEquals("clear", clearRender.loadStore.loadOp)
        val consumer = ready.taskList.tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
            .flatMap { task -> task.payload.operations }
            .flatMap(GPUDestinationSnapshotOperation::consumers)
            .single()
        val consumerRender = renders.single { render ->
            render.drawPackets.any { packet -> packet.packetId == consumer.packetId }
        }
        assertTrue(
            renders.indexOf(consumerRender) > renders.indexOf(clearRender),
            "destination snapshot consumer must render after the synthesized scene clear",
        )
        assertEquals(2, ready.visualOperationCount)
    }

    @Test
    fun `destination read text blend mirror equals the planner scalar coverage set`() {
        // The synthesis condition mirrors GPUBlendPlanner's scalar-coverage dst-read fallback
        // for text semantics; pin the mirror to the planner itself so drift fails loudly.
        val plannerRequired = GPUBlendMode.entries.filter { mode ->
            GPUBlendPlanner().plan(
                GPUBlendSpecializationRequest(
                    mode = mode,
                    coverage = GPUCoverageConsumption.ScalarCoverage,
                    sourceAlpha = GPUSourceAlphaClassification.Translucent,
                    target = GPUTargetBlendFacts(
                        formatClass = "rgba8unorm",
                        clampsNormalizedColorWrites = true,
                        premultipliedAlpha = true,
                    ),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                ),
            ).destinationReadRequirement == GPUBlendDestinationReadRequirement.DestinationTextureRequired
        }.map { it.name }.toSet()
        assertEquals(
            plannerRequired,
            PREPARED_DST_READ_TEXT_BLEND_MODES.map { mode -> mode.name }.toSet(),
        )
    }

    @Test
    fun `non destination read color glyph first visual op synthesizes no leading clear`() {
        val sourceOverText = colorTextOperation(fontSize = 28f)

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(sourceOverText),
                    identity = "src-over-no-clear-synthesis",
                ),
            ),
        )
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(1, renders.size)
        assertIs<GPUDrawSemanticPayload.ColorGlyph>(renders.single().drawPackets.single().semanticPayload)
        assertTrue(ready.taskList.tasks.none { it is GPUTask.DestinationSnapshots })
        assertTrue(renders.single().drawPackets.none { packet ->
            packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        })
    }

    @Test
    fun `opaque DST_IN text is elided before atlas binding and native allocation`() {
        val rect = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(Color.RED),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val noOpText = textOperation().copy(
            paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.DST_IN),
        )
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(rect, noOpText),
                target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(1),
            ),
        )

        assertEquals(setOf(1), prepared.inventory.elidedTextOperationIndices)
        assertTrue(prepared.inventory.pages.isEmpty())
        assertTrue(prepared.inventory.subRunsByOperationIndex.isEmpty())
        assertEquals(0, prepared.inventory.metrics.instanceCount)

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(rect, noOpText),
                    identity = "opaque-dst-in-text-noop",
                ),
            ),
        )
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val packets = renders.flatMap(GPUTask.Render::drawPackets)
        val preparations = ready.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)

        assertEquals(1, packets.size)
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(packets.single().semanticPayload)
        assertTrue(renders.all { render -> render.preparedTextBindingsByPacketId.isEmpty() })
        assertTrue(preparations.none { request ->
            request.role == GPUFrameResourceRole.GlyphAtlas ||
                request.diagnosticLabel.startsWith("prepared-text.")
        })
        assertTrue(ready.taskList.tasks.none { it is GPUTask.DestinationSnapshots })
    }

    @Test
    fun `target empty destination read text remains terminal before mapping`() {
        val rect = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 64f, 64f),
            Paint.fill(Color.WHITE),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val culledText = textOperation().copy(
            paint = Paint.fill(Color.BLACK).copy(blendMode = BlendMode.DARKEN),
            clip = ClipStack.DeviceRect(
                rect = RectF32.ofLTRB(80f, 80f, 96f, 96f),
                antiAlias = false,
            ),
        )

        val build = GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(rect, culledText),
                    identity = "culled-text",
                ),
            )
        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(build, build.toString())
        assertEquals("invalid.preflight.text.blend", refused.diagnostic.code.value)
    }

    @Test
    fun `prepared text AA device rect retains analytic coverage strategy`() {
        val operation = textOperation().copy(
            clip = ClipStack.DeviceRect(
                rect = RectF32.ofLTRB(16.5f, 0f, 40f, 64f),
                antiAlias = true,
            ),
        )
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(
                operations = listOf(operation),
                config = RenderConfig.DEFAULT,
            ),
        )
        val prepared = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                GPUPreparedSurfaceFrameBuildRequest(
                    candidate = candidate,
                    targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                    targetBounds = GPUPixelBounds(0, 0, 64, 64),
                    capabilities = capabilities(
                        facts = listOf(
                            GPUCapabilityFact(
                                name = BOUNDED_CLIP_NATIVE,
                                source = "test",
                                value = "supported",
                                affectsValidity = true,
                                evidenceLabel = "test:$BOUNDED_CLIP_NATIVE",
                            ),
                        ),
                    ),
                    deviceGeneration = GPUDeviceGenerationID(1),
                    target = GPUFrameTargetRef("prepared-text-coverage-mask-target"),
                    recordingId = GPURecordingID("prepared-text-coverage-mask-recording"),
                    frameId = GPUFrameID(18),
                    readbackRequestId =
                        GPUReadbackRequestID("prepared-text-coverage-mask-readback"),
                ),
            ),
        )
        val renders = prepared.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val textRender = renders.single { render ->
            render.drawPackets.any { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
        }
        val textPacket = textRender.drawPackets.single()
        val binding = textRender.preparedTextBindingsByPacketId.getValue(textPacket.packetId)

        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(textPacket.clipExecutionPlan)
        assertEquals(80L, binding.drawUniformBufferPlan.logicalSliceSizeBytes)
        assertEquals(
            GPUPreparedTextClipVariant.AnalyticRectAA,
            binding.compositeProgram.clipVariant,
        )
        assertEquals(
            GPUPreparedTextClipVariant.AnalyticRectAA,
            requireNotNull(binding.preflightSeal.textA8Composite).clipPlan.variant,
        )
    }

    @Test
    fun `prepared text ordered complex clip emits one coverage mask producer and sampled consumer`() {
        val operation = textOperation().copy(clip = orderedCoverageMaskClip())
        val build = GPUPreparedSurfaceFrameBuilder.build(
            preparedFrameRequest(
                operations = listOf(operation),
                identity = "prepared-text-coverage-mask",
            ),
        )
        val prepared = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            build,
            "CoverageMask TextA8 build=$build",
        )
        val renders = prepared.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val textRender = renders.single { render ->
            render.drawPackets.any { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
        }
        val textPacket = textRender.drawPackets.single()

        assertIs<GPUClipExecutionPlan.CoverageMask>(textPacket.clipExecutionPlan)
        assertTrue(
            renders.any { render ->
                render.drawPackets.any { packet -> packet.role == GPUDrawPacketRole.ClipProducer }
            },
            "CoverageMask text must retain a producer render before its text consumer",
        )
        assertTrue(
            textRender.resourceUses.any { use ->
                use.role == GPUFrameResourceRole.ClipMask && !use.write
            },
            "CoverageMask text must sample the produced mask instead of widening to target scissor",
        )
    }

    @Test
    fun `core text core share one ordered coverage mask producer and resource`() {
        val clip = orderedCoverageMaskClip()
        val operations = listOf(
            DisplayOp.DrawRect(
                rect = RectF32.ofLTRB(0f, 0f, 8f, 8f),
                paint = Paint.fill(Color.RED).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = clip,
            ),
            textOperation().copy(clip = clip),
            DisplayOp.DrawRect(
                rect = RectF32.ofLTRB(32f, 32f, 40f, 40f),
                paint = Paint.fill(Color.BLUE).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = clip,
            ),
        )
        val build = GPUPreparedSurfaceFrameBuilder.build(
            preparedFrameRequest(
                operations = operations,
                identity = "prepared-core-text-core-coverage-mask",
            ),
        )
        val prepared = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            build,
            "Core/Text/Core shared CoverageMask build=$build",
        )
        val renders = prepared.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val producers = renders.filter { render ->
            render.drawPackets.any { packet -> packet.role == GPUDrawPacketRole.ClipProducer }
        }
        val consumers = renders.filter { render ->
            render.drawPackets.any { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
        }
        val maskWrites = producers.flatMap(GPUTask.Render::resourceUses)
            .filter { use -> use.role == GPUFrameResourceRole.ClipMask && use.write }
        val maskReads = consumers.flatMap(GPUTask.Render::resourceUses)
            .filter { use -> use.role == GPUFrameResourceRole.ClipMask && !use.write }

        assertEquals(1, producers.size)
        assertEquals(3, consumers.size)
        assertEquals(listOf(0, 1, 2), consumers.map { render ->
            render.drawPackets.single { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive ||
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8
            }.originalPaintOrder
        })
        assertEquals(1, maskWrites.map { use -> use.resource }.distinct().size)
        assertEquals(
            maskWrites.single().resource,
            maskReads.map { use -> use.resource }.distinct().single(),
        )
    }

    @Test
    fun `two distinct text coverage masks retain two resources and original consumer order`() {
        val firstClip = orderedCoverageMaskClip()
        val secondClip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(
                    rect = RectF32.ofLTRB(4f, 4f, 52f, 52f),
                    op = ClipOp.INTERSECT,
                    antiAlias = false,
                ),
                ClipStackOp.RectOp(
                    rect = RectF32.ofLTRB(20f, 8f, 24f, 56f),
                    op = ClipOp.DIFFERENCE,
                    antiAlias = false,
                ),
            ),
        )
        val prepared = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                preparedFrameRequest(
                    operations = listOf(
                        textOperation().copy(clip = secondClip),
                        textOperation().copy(clip = firstClip),
                    ),
                    identity = "prepared-text-two-coverage-masks",
                ),
            ),
        )
        val renders = prepared.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val producers = renders.filter { render ->
            render.drawPackets.any { packet -> packet.role == GPUDrawPacketRole.ClipProducer }
        }
        val consumers = renders.filter { render ->
            render.drawPackets.any { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
        }
        val writes = producers.flatMap(GPUTask.Render::resourceUses)
            .filter { use -> use.role == GPUFrameResourceRole.ClipMask && use.write }
        val reads = consumers.flatMap(GPUTask.Render::resourceUses)
            .filter { use -> use.role == GPUFrameResourceRole.ClipMask && !use.write }

        assertEquals(2, producers.size)
        assertEquals(2, writes.map(GPUFrameResourceUse::resource).distinct().size)
        assertEquals(2, reads.map(GPUFrameResourceUse::resource).distinct().size)
        val bindings = consumers.map { render ->
            val packet = render.drawPackets.single { candidate ->
                candidate.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
            render.preparedTextBindingsByPacketId.getValue(packet.packetId)
        }
        assertEquals(2, bindings.mapNotNull { it.coverageMaskResource }.distinct().size)
        assertEquals(
            1,
            bindings.map { it.compositeProgram.pipelineKey }.distinct().size,
            "concrete CoverageMask resource identity must not specialize the TextA8 pipeline",
        )
        assertEquals(
            listOf(0, 1),
            consumers.map { render ->
                render.drawPackets.single { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                }.originalPaintOrder
            },
            "consumer order must remain display-list order, never global mask-identity order",
        )
    }

    @Test
    fun `prepared text transports the exact compiled gradient program without descriptor reconstruction`() {
        val operation = textOperation().copy(
            paint = Paint.fill(Color.WHITE).copy(
                shader = Shader.LinearGradient(
                    start = Point2F32(0f, 0f),
                    end = Point2F32(32f, 0f),
                    stops = listOf(
                        GradientStop(0f, Color.RED),
                        GradientStop(1f, Color.BLUE),
                    ),
                    tileMode = TileMode.CLAMP,
                ),
            ),
        )
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(operation),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(16),
            ),
        )
        val visual = prepared.mapping.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.DrawTextRun>(visual.normalized)

        assertNull(command.material)
        assertSame(requireNotNull(visual.preparedText).draw.material, command.preparedMaterial)
        assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, target().width, target().height),
            ),
        )

        val mapperSource = java.io.File(
            repositoryRoot(),
            "kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt",
        ).readText()
        val textVisualSource = mapperSource.substringAfter(
            "private fun GPUPreparedTextSubRun.toPreparedTextVisual(",
        ).substringBefore("\nprivate fun GPUBlendMode.toPaintBlendMode()")
        val semanticSource = java.io.File(
            repositoryRoot(),
            "kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt",
        ).readText()
        assertTrue(".paint.toMaterial()" !in textVisualSource)
        assertTrue(".paint.toMaterial()" !in semanticSource)
    }

    @Test
    fun `prepared visual source indexing is linear and recorder documentation is current`() {
        val builderSource = java.io.File(
            repositoryRoot(),
            "kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt",
        ).readText()
        val collectionSource = builderSource.substringAfter("private fun collectPreparedImageVisuals(")
            .substringBefore("\nprivate fun ")
        assertTrue("inventory.subRunsByOperationIndex" in collectionSource)
        assertTrue("mapping.visualCommands.mapNotNull" !in collectionSource)

        val commandSource = java.io.File(
            repositoryRoot(),
            "gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt",
        ).readText()
        assertTrue("recording still refuses text runs" !in commandSource)
    }

    @Test
    fun `admitted empty glyph operation emits no visual and never continues through legacy`() {
        val typeface = liberationTypeface()
        val spaceGlyph = typeface.glyphIdForCodepoint(' '.code)
        val operation = DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(spaceGlyph.toUShort()),
                        positions = listOf(Point2F32(0f, 0f)),
                        fontSize = 16f,
                    ),
                ),
                typeface = typeface,
                fontSize = 16f,
            ),
            x = 4f,
            y = 24f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(operation),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(1),
            ),
        )

        assertEquals(setOf(0), prepared.inventory.acceptedTextOperationIndices)
        assertEquals(emptyList(), prepared.mapping.visualCommands)
    }

    @Test
    fun `prepared A8 and COLRv0 packets retain semantic clip provenance scissor and blend authorities`() {
        val clip = ClipStack.DeviceRect(
            rect = RectF32.ofLTRB(2f, 3f, 40f, 45f),
            antiAlias = false,
        )
        val textCapabilities = capabilities(
            facts = listOf(
                GPUCapabilityFact(
                    name = SCISSOR_NATIVE,
                    source = "test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "test:$SCISSOR_NATIVE",
                ),
            ),
        )
        val operations = listOf(
            DisplayOp.Annotation(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                GPUFrameProvenance.GmContent.annotationValue,
            ),
            textOperation().copy(
                paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.SRC),
                clip = clip,
            ),
            colorTextOperation(fontSize = 8f).copy(
                paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.SRC),
                clip = clip,
            ),
        )
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = operations,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = textCapabilities,
                generation = GPUTextArtifactGeneration(15),
                limits = mixedLimits(),
            ),
        )
        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, target().width, target().height),
            ),
        )
        val recorder = GPURecorder(
            recordingId = GPURecordingID("task7-text-authorities"),
            frameId = GPUFrameID(15),
            capabilities = textCapabilities,
            deviceGeneration = GPUDeviceGenerationID(1),
        )
        prepared.mapping.visualCommands.forEach { visual -> recorder.record(visual.normalized) }
        val packets = recorder.close().taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .associateBy { packet -> packet.commandIdValue }

        prepared.mapping.visualCommands.forEach { visual ->
            val commandId = visual.normalized.commandId.value
            val packet = packets.getValue(commandId)
            val semantic = gathered.semanticsByCommandId.getValue(commandId)
            val semanticClipIdentity = when (semantic) {
                is GPUDrawSemanticPayload.TextA8 -> semantic.clipIdentity
                is GPUDrawSemanticPayload.ColorGlyph -> semantic.clipIdentity
                else -> error("Unexpected prepared text semantic ${semantic.canonicalType}")
            }

            assertEquals(visual.clipCoverage, packet.clipCoveragePlan)
            assertEquals(visual.clipExecutionPlan, packet.clipExecutionPlan)
            assertEquals(visual.provenance, packet.frameProvenance)
            assertTrue(
                packet.scissorBoundsHash != null,
                "execution=${packet.clipExecutionPlan}, normalized=${visual.normalized.clip.executionPlan}",
            )
            assertEquals(visual.blendPlan, packet.blendPlan)
            assertEquals(visual.preparedText!!.draw.clipContentKey, semanticClipIdentity)
        }
    }

    @Test
    fun `diagnostic inventory delegates to the same two pass preparation authority`() {
        val operations = listOf(textOperation())
        val direct = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = operations,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(0),
            ),
        )
        val diagnostic = GPUFramePathApiInventory.plan(
            operations = operations,
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
        )

        val directVisuals =
            direct.mapping.visualCommands.map { visual -> visual.copy(preparedText = null) }
        val diagnosticVisuals =
            diagnostic.visualCommands.map { visual -> visual.copy(preparedText = null) }
        directVisuals.zip(diagnosticVisuals).forEach { (expected, actual) ->
            val expectedMaterial =
                assertIs<NormalizedDrawCommand.DrawTextRun>(expected.normalized).preparedMaterial!!
            val actualMaterial =
                assertIs<NormalizedDrawCommand.DrawTextRun>(actual.normalized).preparedMaterial!!
            assertEquals(expectedMaterial.materialKey, actualMaterial.materialKey)
            assertEquals(expectedMaterial.wgslSource, actualMaterial.wgslSource)
            assertEquals(expectedMaterial.entryPoint, actualMaterial.entryPoint)
            assertEquals(expectedMaterial.uniformBytes, actualMaterial.uniformBytes)
            assertEquals(expectedMaterial.paintAlpha.toRawBits(), actualMaterial.paintAlpha.toRawBits())
            assertEquals(expectedMaterial.sourceKind, actualMaterial.sourceKind)
            assertEquals(expectedMaterial.abiHash, actualMaterial.abiHash)
            assertEquals(
                expectedMaterial.composableFragment.fragmentHash,
                actualMaterial.composableFragment.fragmentHash,
            )
            assertEquals(
                expectedMaterial.composableFragment.abiHash,
                actualMaterial.composableFragment.abiHash,
            )
        }
        assertEquals(
            directVisuals,
            diagnosticVisuals.zip(directVisuals).map { (actual, expected) ->
                val expectedMaterial =
                    assertIs<NormalizedDrawCommand.DrawTextRun>(expected.normalized).preparedMaterial
                actual.copy(
                    normalized =
                        assertIs<NormalizedDrawCommand.DrawTextRun>(actual.normalized).copy(
                            preparedMaterial = expectedMaterial,
                        ),
                )
            },
        )
        assertEquals(
            direct.inventory.contentSha256,
            diagnostic.preparedTextInventory!!.contentSha256,
        )
    }

    @Test
    fun `lowerer and inventory refusals publish no partial mapping`() {
        val lowererRefusal = GPUPreparedTextFramePreparer.prepare(
            operations = listOf(textOperation(fontSize = Float.NaN)),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
            generation = GPUTextArtifactGeneration(1),
            limits = mixedLimits(),
        )
        val inventoryRefusal = GPUPreparedTextFramePreparer.prepare(
            operations = listOf(textOperation()),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
            generation = GPUTextArtifactGeneration(1),
            limits = mixedLimits().copy(maxGlyphs = 0),
        )

        assertIs<GPUPreparedTextFramePreparation.Refused>(lowererRefusal)
        assertIs<GPUPreparedTextFramePreparation.Refused>(inventoryRefusal)
    }

    @Test
    fun `core two A8 image and COLRv0 expand to five exact ordered commands`() {
        val operations = listOf(
            DisplayOp.Annotation(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                GPUFrameProvenance.GmContent.annotationValue,
            ),
            DisplayOp.DrawColor(
                Color.BLUE,
                BlendMode.SRC_OVER,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            textOperation(fontSize = 28f),
            imageOperation(),
            colorTextOperation(fontSize = 8f),
        )

        val ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = operations,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(10),
                limits = mixedLimits(),
            ),
        )
        val visuals = ready.mapping.visualCommands
        val textVisuals = visuals.filter { visual -> visual.preparedText != null }

        assertEquals(listOf(0, 1, 2, 3, 4), visuals.map { it.normalized.commandId.value })
        assertEquals(listOf(0, 1, 2, 3, 4), visuals.map { it.normalized.ordering.paintOrder })
        assertEquals(
            listOf(
                false,
                true,
                true,
                false,
                true,
            ),
            visuals.map { visual -> visual.preparedText != null },
        )
        assertEquals(listOf(2, 2, 4), textVisuals.map { it.preparedText!!.operationIndex })
        assertEquals(listOf(0, 1, 0), textVisuals.map { it.preparedText!!.subRunIndex })
        assertEquals(
            listOf(
                GPUPreparedTextRepresentation.A8_MASK,
                GPUPreparedTextRepresentation.A8_MASK,
                GPUPreparedTextRepresentation.COLRV0,
            ),
            textVisuals.map { it.preparedText!!.representation },
        )
        assertTrue(visuals[3].normalized is NormalizedDrawCommand.DrawImageRect)
        assertTrue(textVisuals.all { it.provenance == GPUFrameProvenance.GmContent })
        assertTrue(textVisuals.none { visual ->
            visual.normalized.bounds.let { bounds ->
                bounds.right == visual.preparedText!!.draw.originX +
                    visual.preparedText!!.draw.glyphs.first().fontSize * 10f
            }
        })
    }

    @Test
    fun `two pass preparation expands exact text subruns without reordering core commands`() {
        val operations = listOf(
            DisplayOp.DrawColor(
                Color.BLUE,
                BlendMode.SRC_OVER,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.Annotation(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                GPUFrameProvenance.GmContent.annotationValue,
            ),
            textOperation(),
            DisplayOp.DrawColor(
                Color.RED,
                BlendMode.SRC_OVER,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
        )

        val ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = operations,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(9),
                limits = tinyPageLimits(),
            ),
        )
        val visuals = ready.mapping.visualCommands
        val textVisuals = visuals.filter { it.preparedText != null }

        assertEquals(listOf(0, 1, 2, 3), visuals.map { it.normalized.commandId.value })
        assertEquals(listOf(0, 1, 2, 3), visuals.map { it.normalized.ordering.paintOrder })
        assertEquals(2, textVisuals.size)
        assertEquals(listOf(0, 1), textVisuals.map { it.preparedText!!.subRunIndex })
        assertEquals(listOf(2, 2), textVisuals.map { it.preparedText!!.operationIndex })
        assertEquals(
            listOf(GPUFrameProvenance.GmContent, GPUFrameProvenance.GmContent),
            textVisuals.map(GPUFramePathVisualCommand::provenance),
        )
        assertSame(textVisuals[0].preparedText!!.draw, textVisuals[1].preparedText!!.draw)
    }

    private fun textOperation(fontSize: Float = 16f): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(36u, 37u),
                    positions = listOf(Point2F32(0f, 0f), Point2F32(18f, 0f)),
                    fontSize = fontSize,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = fontSize,
        ),
        x = 4f,
        y = 24f,
        paint = Paint.fill(Color.WHITE),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun orderedCoverageMaskClip(): ClipStack.Complex = ClipStack.Complex(
        listOf(
            ClipStackOp.RectOp(
                rect = RectF32.ofLTRB(8f, 8f, 56f, 56f),
                op = ClipOp.INTERSECT,
                antiAlias = false,
            ),
            ClipStackOp.RectOp(
                rect = RectF32.ofLTRB(14f, 16f, 18f, 48f),
                op = ClipOp.DIFFERENCE,
                antiAlias = false,
            ),
        ),
    )

    private fun preparedFrameRequest(
        operations: List<DisplayOp>,
        identity: String,
    ): GPUPreparedSurfaceFrameBuildRequest {
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(
                operations = operations,
                config = RenderConfig.DEFAULT,
            ),
        )
        return GPUPreparedSurfaceFrameBuildRequest(
            candidate = candidate,
            targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
            targetBounds = GPUPixelBounds(0, 0, 64, 64),
            capabilities = capabilities(
                facts = listOf(
                    GPUCapabilityFact(
                        name = BOUNDED_CLIP_NATIVE,
                        source = "test",
                        value = "supported",
                        affectsValidity = true,
                        evidenceLabel = "test:$BOUNDED_CLIP_NATIVE",
                    ),
                    GPUCapabilityFact(
                        name = "first_slice.fill_rect.native",
                        source = "test",
                        value = "supported",
                        affectsValidity = true,
                        evidenceLabel = "test:first_slice.fill_rect.native",
                    ),
                ),
            ),
            deviceGeneration = GPUDeviceGenerationID(1),
            target = GPUFrameTargetRef("$identity-target"),
            recordingId = GPURecordingID("$identity-recording"),
            frameId = GPUFrameID(18),
            readbackRequestId = GPUReadbackRequestID("$identity-readback"),
        )
    }

    private fun colorTextOperation(fontSize: Float): DisplayOp.DrawText {
        val typeface = FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { stream -> stream.readBytes() },
            fontName = "Skia COLRv0 mixed-frame font",
        )
        return DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(2u),
                        positions = listOf(Point2F32(8f, 32f)),
                        fontSize = fontSize,
                    ),
                ),
                typeface = typeface,
                fontSize = fontSize,
            ),
            x = 0f,
            y = 0f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
    }

    private fun imageOperation(): DisplayOp.DrawImage {
        val image = Image(
            width = 2,
            height = 2,
            colorType = ColorType.RGBA_8888,
            sourceId = "fp05-task7-mixed-image",
            pixels = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                0, 255.toByte(), 0, 255.toByte(),
                0, 0, 255.toByte(), 255.toByte(),
                255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            ),
            alphaType = AlphaType.PREMUL,
        )
        return DisplayOp.DrawImage(
            image = image,
            src = RectF32.ofLTRB(0f, 0f, 2f, 2f),
            dst = RectF32.ofLTRB(40f, 4f, 48f, 12f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
    }

    private fun tinyPageLimits(): PreparedTextFrameInventoryLimits =
        PreparedTextFrameInventoryLimits(
            pageWidth = 16,
            pageHeight = 16,
            maxPages = 4,
            maxPageBytes = 256,
            maxTotalPageBytes = 1_024,
            maxGlyphs = 16,
            maxInstances = 16,
            maxSubRuns = 16,
            maxInstanceBytes = 4_096,
            maxTextureDimension2D = 8_192,
        )

    private fun mixedLimits(): PreparedTextFrameInventoryLimits =
        PreparedTextFrameInventoryLimits(
            pageWidth = 32,
            pageHeight = 32,
            maxPages = 8,
            maxPageBytes = 1_024,
            maxTotalPageBytes = 8_192,
            maxGlyphs = 64,
            maxInstances = 64,
            maxSubRuns = 64,
            maxInstanceBytes = 8_192,
            maxTextureDimension2D = 8_192,
        )

    private fun repositoryRoot(): java.io.File =
        generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { candidate -> java.io.File(candidate, "settings.gradle.kts").isFile }
}
