package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceFrameBuilderTextTest {
    @Test
    fun `prepared text transports the exact compiled gradient program without descriptor reconstruction`() {
        val operation = textOperation().copy(
            paint = Paint.fill(Color.WHITE).copy(
                shader = Shader.LinearGradient(
                    start = Point(0f, 0f),
                    end = Point(32f, 0f),
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
                        positions = listOf(Point(0f, 0f)),
                        fontSize = 16f,
                    ),
                ),
                typeface = typeface,
                fontSize = 16f,
            ),
            x = 4f,
            y = 24f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
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
        assertEquals(0, prepared.mapping.legacyDump.invocationCount)
        assertEquals(emptyMap(), prepared.mapping.legacyDump.invocationsByFamily)
    }

    @Test
    fun `prepared A8 and COLRv0 packets retain semantic clip provenance scissor and blend authorities`() {
        val clip = ClipStack.DeviceRect(
            rect = Rect.fromLTRB(2f, 3f, 40f, 45f),
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
                Rect.fromLTRB(0f, 0f, 1f, 1f),
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

        assertEquals(
            direct.mapping.visualCommands.map { visual -> visual.copy(preparedText = null) },
            diagnostic.visualCommands.map { visual -> visual.copy(preparedText = null) },
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
                Rect.fromLTRB(0f, 0f, 1f, 1f),
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                GPUFrameProvenance.GmContent.annotationValue,
            ),
            DisplayOp.DrawColor(
                Color.BLUE,
                BlendMode.SRC_OVER,
                Matrix33.identity(),
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
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            DisplayOp.Annotation(
                Rect.fromLTRB(0f, 0f, 1f, 1f),
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                GPUFrameProvenance.GmContent.annotationValue,
            ),
            textOperation(),
            DisplayOp.DrawColor(
                Color.RED,
                BlendMode.SRC_OVER,
                Matrix33.identity(),
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
                    positions = listOf(Point(0f, 0f), Point(18f, 0f)),
                    fontSize = fontSize,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = fontSize,
        ),
        x = 4f,
        y = 24f,
        paint = Paint.fill(Color.WHITE),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

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
                        positions = listOf(Point(8f, 32f)),
                        fontSize = fontSize,
                    ),
                ),
                typeface = typeface,
                fontSize = fontSize,
            ),
            x = 0f,
            y = 0f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
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
            src = Rect.fromLTRB(0f, 0f, 2f, 2f),
            dst = Rect.fromLTRB(40f, 4f, 48f, 12f),
            paint = null,
            transform = Matrix33.identity(),
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
