package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point

class GPUPreparedTextSemanticBuilderTest {
    @Test
    fun `shared scissor authority requires proof that a non scissor clip is retained separately`() {
        val target = GPUPixelBounds(0, 0, 64, 64)
        val mask = GPUClipCoveragePlan.Mask(
            contentKey = "clip-mask",
            width = 64,
            height = 64,
            sampleCount = 1,
            resolvedBytes = 4_096,
            requiredBytes = 4_096,
            elements = emptyList(),
        )

        assertNull(mask.toPreparedScissorBounds(target))
        assertEquals(
            target,
            mask.toPreparedScissorBounds(
                targetBounds = target,
                nonScissorClipRetainedSeparately = true,
            ),
        )
    }

    @Test
    fun `bounds material and payload refusal publish no partial semantic map`() {
        val prepared = preparedA8()
        val visual = prepared.mapping.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.DrawTextRun>(visual.normalized)
        val invalidBounds = visual.copy(
            targetSpaceBounds = GPUBounds(0f, 0f, 1f, 1f),
        )
        val invalidMaterial = visual.copy(
            normalized = command.copy(
                material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                preparedMaterial = null,
            ),
        )

        val boundsRefusal = GPUPreparedTextSemanticBuilder.gather(
            visualCommands = listOf(invalidBounds),
            inventory = prepared.inventory,
            targetBounds = GPUPixelBounds(0, 0, 64, 64),
        )
        val materialRefusal = GPUPreparedTextSemanticBuilder.gather(
            visualCommands = listOf(invalidMaterial),
            inventory = prepared.inventory,
            targetBounds = GPUPixelBounds(0, 0, 64, 64),
        )
        val payloadRefusal = GPUPreparedTextSemanticBuilder.gather(
            visualCommands = listOf(visual),
            inventory = prepared.inventory,
            targetBounds = GPUPixelBounds(0, 0, 64, 64),
            gatherA8 = { throw IllegalArgumentException("injected-payload-refusal") },
        )

        assertIs<GPUPreparedTextSemanticGatherResult.Refused>(boundsRefusal)
        assertIs<GPUPreparedTextSemanticGatherResult.Refused>(materialRefusal)
        assertIs<GPUPreparedTextSemanticGatherResult.Refused>(payloadRefusal)
    }

    @Test
    fun `COLRv0 semantics retain shared R8 page and exact non-empty source layer indexes`() {
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(colorTextOperation()),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(13),
                limits = colorLimits(),
            ),
        )

        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        val semantics = gathered.semanticsByCommandId.values.map { semantic ->
            assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantic)
        }

        assertTrue(semantics.isNotEmpty())
        semantics.forEach { semantic ->
            assertEquals(semantic.instances.map { it.colorLayerIndex }, semantic.layers.map { it.colorLayerIndex })
            assertEquals(GPUTextArtifactGeneration(13).value.toLong(), semantic.atlas.generation)
            assertEquals(semantic.atlas.contentHash, semantic.atlasBytesSha256)
            assertEquals(semantic.material!!.materialKey, prepared.mapping.visualCommands
                .single { it.normalized.commandId.value == semantic.payloadRef.commandIdValue }
                .preparedText!!.draw.material.materialKey)
        }
    }

    @Test
    fun `A8 and COLRv0 semantics share one frame local R8 artifact per page`() {
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(textOperation(), colorTextOperation()),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(15),
                limits = colorLimits(),
            ),
        )
        assertEquals(1, prepared.inventory.pages.size)

        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        val atlases = gathered.semanticsByCommandId.values.map { semantic ->
            when (semantic) {
                is GPUDrawSemanticPayload.TextA8 -> semantic.atlas
                is GPUDrawSemanticPayload.ColorGlyph -> semantic.atlas
                else -> error("Unexpected semantic ${semantic.canonicalType}")
            }
        }

        assertTrue(atlases.size >= 2)
        atlases.drop(1).forEach { atlas -> assertSame(atlases.first(), atlas) }
    }

    @Test
    fun `repeated multi layer COLRv0 glyphs expand into bounded ordered commands`() {
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(colorTextOperation(glyphCount = 9)),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(17),
                limits = colorLimits(),
            ),
        )
        val visuals = prepared.mapping.visualCommands
        val subRuns = visuals.map { visual -> requireNotNull(visual.preparedText) }

        assertEquals(listOf(16, 2), subRuns.map { subRun -> subRun.instances.size })
        assertEquals(listOf(0, 1), subRuns.map { subRun -> subRun.subRunIndex })
        assertEquals(listOf(0, 1), visuals.map { visual -> visual.normalized.commandId.value })
        assertEquals(listOf(0, 1), visuals.map { visual -> visual.normalized.ordering.paintOrder })
        val sourceOwners = subRuns.flatMapIndexed { subRunIndex, subRun ->
            subRun.instances.map { instance -> instance.sourceGlyphIndex.value to subRunIndex }
        }.groupBy({ it.first }, { it.second })
        assertTrue(sourceOwners.values.all { owners -> owners.distinct().size == 1 })

        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = visuals,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        assertEquals(2, gathered.semanticsByCommandId.size)
        gathered.semanticsByCommandId.values.forEach { semantic ->
            assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantic)
        }
    }

    @Test
    fun `duplicate COLRv0 source mask layers remain valid distinct semantic layers`() {
        val operation = colorTextOperation()
        val draw = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(operation, 0, target(), capabilities()),
        ).draw
        val exact = assertIs<PreparedTextGlyphArtifact.COLRV0>(
            ExactPreparedTextGlyphArtifactResolver.resolve(
                draw = draw,
                glyphIndex = 0,
                representation = GPUPreparedTextRepresentation.COLRV0,
            ),
        )
        val sourceLayer = assertIs<PreparedTextColorLayerArtifact.A8>(exact.layers.first())
        val sourcePlan = exact.colorPlan.layers.first()
        val duplicateArtifact = PreparedTextGlyphArtifact.COLRV0(
            layers = listOf(
                sourceLayer.copy(layerIndex = 0),
                sourceLayer.copy(layerIndex = 1),
            ),
            colorPlan = exact.colorPlan.copy(
                layers = listOf(
                    sourcePlan.copy(layerIndex = 0),
                    sourcePlan.copy(
                        layerIndex = 1,
                        resolvedColor = sourcePlan.resolvedColor?.xor(0x00010101),
                    ),
                ),
            ),
        )
        val inventory = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(draw),
                generation = GPUTextArtifactGeneration(16),
                limits = colorLimits(),
                artifactResolver = PreparedTextGlyphArtifactResolver { _, _, _ -> duplicateArtifact },
            ),
        ).inventory
        val mapping = GPUOpMapper.mapOperations(
            operations = listOf(operation),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
            preparedTextInventory = inventory,
        )

        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = mapping.visualCommands,
                inventory = inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        val semantic = assertIs<GPUDrawSemanticPayload.ColorGlyph>(
            gathered.semanticsByCommandId.values.single(),
        )

        assertEquals(listOf(0, 1), semantic.instances.map { it.colorLayerIndex })
        assertEquals(listOf(0, 1), semantic.layers.map { it.colorLayerIndex })
        assertEquals(semantic.layers[0].atlasBounds, semantic.layers[1].atlasBounds)
    }

    @Test
    fun `COLRv0 placement proof retains each source glyph strike across sizes and subpixels`() {
        val operation = multiStrikeColorTextOperation()
        val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(operation),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(14),
                limits = colorLimits(),
            ),
        )
        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )

        val observedStrikeSizes = mutableSetOf<Float>()
        gathered.semanticsByCommandId.forEach { (commandId, payload) ->
            val semantic = assertIs<GPUDrawSemanticPayload.ColorGlyph>(payload)
            val subRun = prepared.mapping.visualCommands.single {
                it.normalized.commandId.value == commandId
            }.preparedText!!
            semantic.instances.zip(semantic.layers).forEach { (instance, layer) ->
                val sourceGlyph = subRun.draw.glyphs[instance.sourceGlyphIndex.value]
                val strike = sourceGlyph.strikeKey
                observedStrikeSizes += strike.sizePx
                assertEquals(subRun.colorGlyphLayerPlan!!.baseGlyphID.toInt(), sourceGlyph.glyphId)
                assertEquals(instance.glyphId, layer.placementProof.strikeGlyphId)
                assertEquals(strike.sizePx, layer.placementProof.strikeSize)
                assertEquals(strike.subpixelX.toRawBits(), layer.placementProof.strikeSubpixelX)
                assertEquals(strike.subpixelY.toRawBits(), layer.placementProof.strikeSubpixelY)
            }
        }

        assertEquals(setOf(8f, 16f), observedStrikeSizes)
    }

    @Test
    fun `A8 semantics gather exact page instances material clip blend and generation`() {
        val prepared = preparedA8()

        val gathered = assertIs<GPUPreparedTextSemanticGatherResult.Gathered>(
            GPUPreparedTextSemanticBuilder.gather(
                visualCommands = prepared.mapping.visualCommands,
                inventory = prepared.inventory,
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        val semantic = assertIs<GPUDrawSemanticPayload.TextA8>(
            gathered.semanticsByCommandId.getValue(0),
        )
        val subRun = prepared.mapping.visualCommands.single().preparedText!!
        val page = prepared.inventory.pages.single()

        assertContentEquals(
            page.bytes.map(Int::toByte).toByteArray(),
            semantic.atlas.tightBytesForUpload(),
        )
        assertEquals(page.contentSha256, semantic.atlas.contentHash)
        assertEquals(GPUTextArtifactGeneration(12), semantic.atlasGeneration)
        assertEquals(subRun.instances, semantic.instances)
        assertEquals(subRun.draw.material.materialKey, semantic.material.materialKey)
        assertEquals(subRun.draw.blendPlan.canonicalIdentity(), semantic.blendPlanIdentity)
        assertEquals(subRun.draw.clipContentKey, semantic.clipIdentity)
        assertEquals(subRun.draw.capabilitySnapshotHash, semantic.capabilitySnapshotHash)
        assertTrue(semantic.canonicalHash.matches(Regex("[0-9a-f]{64}")))
    }

    private fun textOperation(): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(36u),
                    positions = listOf(Point(0f, 0f)),
                    fontSize = 16f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 16f,
        ),
        x = 4f,
        y = 24f,
        paint = Paint.fill(Color.WHITE),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun preparedA8(): GPUPreparedTextFramePreparation.Ready =
        assertIs<GPUPreparedTextFramePreparation.Ready>(
            GPUPreparedTextFramePreparer.prepare(
                operations = listOf(textOperation()),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(12),
                limits = limits(),
            ),
        )

    private fun colorTextOperation(glyphCount: Int = 1): DisplayOp.DrawText {
        val typeface = FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { stream -> stream.readBytes() },
            fontName = "Skia COLRv0 semantic test font",
        )
        return DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = List(glyphCount) { 2u },
                        positions = List(glyphCount) { index -> Point(4f + index * 6f, 32f) },
                        fontSize = 16f,
                    ),
                ),
                typeface = typeface,
                fontSize = 16f,
            ),
            x = 0f,
            y = 0f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
    }

    private fun multiStrikeColorTextOperation(): DisplayOp.DrawText {
        val typeface = FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { stream -> stream.readBytes() },
            fontName = "Skia COLRv0 multi-strike semantic test font",
        )
        return DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(2u),
                        positions = listOf(Point(8.25f, 16.25f)),
                        fontSize = 8f,
                    ),
                    KanvasGlyphRun(
                        glyphs = listOf(2u),
                        positions = listOf(Point(24.5f, 32.5f)),
                        fontSize = 16f,
                    ),
                ),
                typeface = typeface,
                fontSize = 16f,
            ),
            x = 0f,
            y = 0f,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )
    }

    private fun limits(): PreparedTextFrameInventoryLimits =
        PreparedTextFrameInventoryLimits(
            pageWidth = 32,
            pageHeight = 32,
            maxPages = 2,
            maxPageBytes = 1_024,
            maxTotalPageBytes = 2_048,
            maxGlyphs = 16,
            maxInstances = 16,
            maxSubRuns = 16,
            maxInstanceBytes = 4_096,
            maxTextureDimension2D = 8_192,
        )

    private fun colorLimits(): PreparedTextFrameInventoryLimits =
        PreparedTextFrameInventoryLimits(
            pageWidth = 128,
            pageHeight = 128,
            maxPages = 8,
            maxPageBytes = 16_384,
            maxTotalPageBytes = 131_072,
            maxGlyphs = 64,
            maxInstances = 64,
            maxSubRuns = 64,
            maxInstanceBytes = 8_192,
            maxTextureDimension2D = 8_192,
        )
}
