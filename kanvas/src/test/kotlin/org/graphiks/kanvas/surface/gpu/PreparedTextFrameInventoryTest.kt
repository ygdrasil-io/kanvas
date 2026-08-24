package org.graphiks.kanvas.surface.gpu

import kotlin.math.sqrt
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.A8GlyphMask
import org.graphiks.kanvas.glyph.GlyphMaskKey
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PreparedTextFrameInventoryTest {
    @Test
    fun `every finalized subrun shares the exact immutable prepared draw`() {
        val draw = draw(
            operationIndex = 4,
            glyphs = listOf(glyph(7), glyph(8)),
        )

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                draws = listOf(draw),
                limits = limits(pageWidth = 4, pageHeight = 4, maxPages = 2),
            ),
        ).inventory

        val subRuns = ready.subRunsByOperationIndex.getValue(4)
        assertEquals(2, subRuns.size)
        subRuns.forEach { subRun -> assertSame(draw, subRun.draw) }
        assertSame(subRuns[0].draw, subRuns[1].draw)
    }

    @Test
    fun `inventory deduplicates exact masks and preserves first operation order`() {
        val first = draw(
            operationIndex = 0,
            glyphs = listOf(glyph(7), glyph(7)),
        )
        val second = draw(
            operationIndex = 2,
            glyphs = listOf(glyph(8)),
        )

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                draws = listOf(first, second),
                limits = limits(pageWidth = 8, pageHeight = 8, maxPages = 2),
            ),
        ).inventory

        assertEquals(2, ready.pages.sumOf { page -> page.uniqueMaskCount() })
        assertEquals(listOf(0, 2), ready.subRunsByOperationIndex.keys.toList())
        assertEquals(3, ready.metrics.instanceCount)
        assertTrue(ready.pages.size in 1..2)
    }

    @Test
    fun `exact mask identity separates face variation subpixel and palette`() {
        val base = glyph(9)
        val glyphs = listOf(
            base,
            base.copyForTest(
                strikeKey = base.strikeKey.copy(
                    variationCoordinates = mapOf("wght" to 700f),
                ),
            ),
            base.copyForTest(
                strikeKey = base.strikeKey.copy(subpixelX = 0.5f),
            ),
            base.copyForTest(
                strikeKey = base.strikeKey.copy(paletteIdentity = "cpal:1"),
            ),
        )
        val secondFace = draw(
            operationIndex = 1,
            glyphs = listOf(base),
            faceIndex = 1,
        )

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                draws = listOf(draw(0, glyphs), secondFace),
                limits = limits(pageWidth = 16, pageHeight = 16, maxPages = 4),
            ),
        ).inventory

        assertEquals(5, ready.pages.sumOf { it.uniqueMaskCount() })
        assertEquals(5, ready.metrics.uniqueMaskCount)
    }

    @Test
    fun `atlas pages are non-overlapping guarded row-stride exact and have exact UVs`() {
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                draws = listOf(draw(0, listOf(glyph(1), glyph(2), glyph(3)))),
                limits = limits(pageWidth = 8, pageHeight = 8, maxPages = 3),
            ),
        ).inventory

        ready.pages.forEach { page ->
            assertEquals(page.width, page.rowBytes)
            assertEquals(page.rowBytes * page.height, page.bytes.size)
            page.placements.forEach { placement ->
                assertTrue(placement.contentRect.left - placement.allocationRect.left >= 1)
                assertTrue(placement.contentRect.top - placement.allocationRect.top >= 1)
                assertTrue(placement.allocationRect.right - placement.contentRect.right >= 1)
                assertTrue(placement.allocationRect.bottom - placement.contentRect.bottom >= 1)
            }
            page.placements.indices.forEach { leftIndex ->
                ((leftIndex + 1) until page.placements.size).forEach { rightIndex ->
                    assertTrue(
                        !page.placements[leftIndex].allocationRect
                            .overlaps(page.placements[rightIndex].allocationRect),
                    )
                }
            }
        }
        val instance = ready.subRunsByOperationIndex.getValue(0).first().instances.first()
        val placement = ready.pages[instance.pageIndex].placements
            .single { it.itemKey == ready.maskIdentityByGlyphUse.first().maskKeySha256 }
        assertEquals(
            placement.contentRect.left.toFloat() / ready.pages[instance.pageIndex].width,
            instance.uvRect.left,
        )
        assertEquals(
            placement.contentRect.bottom.toFloat() / ready.pages[instance.pageIndex].height,
            instance.uvRect.bottom,
        )
    }

    @Test
    fun `published inventory pages maps subruns instances and identities are deeply immutable`() {
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(listOf(draw(0, listOf(glyph(4))))),
        ).inventory
        val pageBytes = ready.pages.single().bytes.toList()
        val firstQuad = ready.subRunsByOperationIndex.getValue(0).single()
            .instances.single().deviceQuad.toList()

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (ready.pages as MutableList).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (ready.subRunsByOperationIndex as MutableMap).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (ready.acceptedTextOperationIndices as MutableSet).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (ready.pages.single().bytes as MutableList<Int>)[0] = 255
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (
                ready.subRunsByOperationIndex.getValue(0).single()
                    .instances.single().deviceQuad as MutableList<Float>
                )[0] = 999f
        }
        assertEquals(pageBytes, ready.pages.single().bytes)
        assertEquals(setOf(0), ready.acceptedTextOperationIndices)
        assertEquals(
            firstQuad,
            ready.subRunsByOperationIndex.getValue(0).single()
                .instances.single().deviceQuad,
        )
    }

    @Test
    fun `same finalized inventory has deterministic hashes and one changed mask does not`() {
        val draw = draw(0, listOf(glyph(5)))
        val first = assertIs<PreparedTextFrameInventoryResult.Ready>(build(listOf(draw))).inventory
        val second = assertIs<PreparedTextFrameInventoryResult.Ready>(build(listOf(draw))).inventory
        val changed = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                listOf(draw),
                resolver = resolver(maskPixel = 254),
            ),
        ).inventory

        assertEquals(first.contentSha256, second.contentSha256)
        assertEquals(first.pages.map { it.contentSha256 }, second.pages.map { it.contentSha256 })
        assertNotEquals(first.contentSha256, changed.contentSha256)
    }

    @Test
    fun `ordinary empty glyph publishes no page instance or subrun`() {
        val typeface = liberationTypeface()
        val spaceGlyph = typeface.glyphIdForCodepoint(' '.code)
        val operation = operation(
            glyphIds = listOf(spaceGlyph),
            positions = listOf(Point2F32(0f, 0f)),
            transform = Matrix3x3F32.Identity,
            typeface = typeface,
        )
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(operation, 3, target(), capabilities()),
        ).draw

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(lowered),
                generation = GPUTextArtifactGeneration(1),
                limits = limits().copy(
                    maxPages = 0,
                    maxPageBytes = 0,
                    maxTotalPageBytes = 0,
                    maxInstances = 0,
                    maxSubRuns = 0,
                    maxInstanceBytes = 0,
                ),
            ),
        ).inventory

        assertEquals(emptyList(), ready.pages)
        assertEquals(emptyMap(), ready.subRunsByOperationIndex)
        assertEquals(1, ready.metrics.glyphCount)
        assertEquals(0, ready.metrics.instanceCount)
    }

    @Test
    fun `finalized blur footprint adds one mandatory unfiltered guard only`() {
        val blurred = draw(
            operationIndex = 0,
            glyphs = listOf(glyph(6)),
            paint = Paint.fill(Color.RED).copy(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
            ),
        )
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                listOf(blurred),
                limits = limits(pageWidth = 16, pageHeight = 16),
            ),
        ).inventory
        val placement = ready.pages.single().placements.single()

        assertEquals(1, placement.contentRect.left - placement.allocationRect.left)
        assertEquals(1, placement.contentRect.top - placement.allocationRect.top)
        assertEquals(10, placement.allocationRect.right - placement.allocationRect.left)
        assertEquals(10, placement.allocationRect.bottom - placement.allocationRect.top)
    }

    @Test
    fun `scale already present in mask density is not applied twice`() {
        val transform = Matrix3x3F32.of(
            2f, 0f, 10f,
            0f, 3f, 20f,
        )
        val ready = inventoryForAffine(transform, position = Point2F32(1f, 2f))

        assertQuadEquals(
            listOf(
                14f, 29f,
                18f, 29f,
                18f, 35f,
                14f, 35f,
            ),
            ready.singleInstance().deviceQuad,
        )
    }

    @Test
    fun `rotation maps all four mask corners through the linear residual`() {
        val ready = inventoryForAffine(
            Matrix3x3F32.of(
                0f, -1f, 10f,
                1f, 0f, 20f,
            ),
            position = Point2F32(2f, 3f),
        )

        assertQuadEquals(
            listOf(
                4f, 24f,
                4f, 28f,
                -2f, 28f,
                -2f, 24f,
            ),
            ready.singleInstance().deviceQuad,
        )
    }

    @Test
    fun `skew maps all four corners through L times inverse D`() {
        val transform = Matrix3x3F32.of(
            1f, 0.5f, 0f,
            0.25f, 1f, 0f,
        )
        val ready = inventoryForAffine(transform, position = Point2F32(0f, 0f))
        val sx = sqrt(1f + 0.25f * 0.25f)
        val sy = sqrt(0.5f * 0.5f + 1f)
        fun mapped(qx: Float, qy: Float): Pair<Float, Float> =
            (qx / sx + 0.5f * qy / sy) to
                (0.25f * qx / sx + qy / sy)

        val corners = listOf(
            mapped(2f, 3f),
            mapped(6f, 3f),
            mapped(6f, 9f),
            mapped(2f, 9f),
        ).flatMap { (x, y) -> listOf(x, y) }
        assertQuadEquals(corners, ready.singleInstance().deviceQuad)
    }

    @Test
    fun `fractional mask phase is removed before residual placement`() {
        val ready = inventoryForAffine(
            Matrix3x3F32.translation(10.25f, 20.5f),
            position = Point2F32(0f, 0f),
        )

        assertQuadEquals(
            listOf(
                12f, 23f,
                16f, 23f,
                16f, 29f,
                12f, 29f,
            ),
            ready.singleInstance().deviceQuad,
        )
    }

    @Test
    fun `translation is applied exactly once`() {
        val ready = inventoryForAffine(
            Matrix3x3F32.translation(10f, 20f),
            position = Point2F32(0f, 0f),
        )

        assertQuadEquals(
            listOf(
                12f, 23f,
                16f, 23f,
                16f, 29f,
                12f, 29f,
            ),
            ready.singleInstance().deviceQuad,
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("budgetCases")
    fun `every budget refusal is exact and publishes no partial inventory`(
        case: BudgetCase,
    ) {
        val draws = if (case.requiresTwoPages) {
            listOf(draw(0, listOf(glyph(1), glyph(2))))
        } else {
            listOf(draw(0, listOf(glyph(1))))
        }
        val result = build(
            draws = draws,
            limits = case.configure(limits(pageWidth = 4, pageHeight = 4, maxPages = 2)),
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(case.expectedCode, refused.code)
        assertEquals("0", refused.facts["publishedPageCount"])
        assertEquals("0", refused.facts["publishedInstanceCount"])
        assertEquals("0", refused.facts["publishedSubRunCount"])
    }

    @Test
    fun `subruns split on representation page material blend clip and transform class`() {
        val first = draw(0, listOf(glyph(1), glyph(2)))
        val materialChanged = draw(
            operationIndex = 1,
            glyphs = listOf(glyph(3)),
            paint = Paint.fill(Color.BLUE),
        )
        val transformChanged = draw(
            2,
            listOf(glyph(4)),
            transform = Matrix3x3F32.skewing(0.25f, 0f),
        )

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                listOf(first, materialChanged, transformChanged),
                limits = limits(pageWidth = 4, pageHeight = 4, maxPages = 8),
            ),
        ).inventory

        assertEquals(listOf(0, 1, 2), ready.subRunsByOperationIndex.keys.toList())
        assertTrue(ready.subRunsByOperationIndex.getValue(0).size >= 2)
        assertEquals(
            materialChanged.material.materialKey,
            ready.subRunsByOperationIndex.getValue(1).single().materialKey,
        )
        assertEquals(
            materialChanged.clipContentKey,
            ready.subRunsByOperationIndex.getValue(1).single().clipIdentity,
        )
        assertEquals("affine", ready.subRunsByOperationIndex.getValue(2).single().transformClass)
    }

    @Test
    fun `default artifact resolver keeps exact face bytes index variations and typeface identity`() {
        val base = draw(
            operationIndex = 0,
            glyphs = listOf(
                glyph(5).copyForTest(
                    strikeKey = glyph(5).strikeKey.copy(
                        variationCoordinates = mapOf("wght" to 650f),
                    ),
                ),
            ),
        )

        val artifact = assertIs<PreparedTextGlyphArtifact.A8>(
            ExactPreparedTextGlyphArtifactResolver.resolve(
                draw = base,
                glyphIndex = 0,
                representation = GPUPreparedTextRepresentation.A8_MASK,
            ),
        )

        assertEquals(base.face.faceIndex, artifact.maskKey.faceIndex)
        assertEquals(base.face.typefaceId, artifact.maskKey.strikeKey.typefaceId)
        assertEquals(mapOf("wght" to 650f), artifact.maskKey.strikeKey.variationCoordinates)
        assertEquals(base.glyphs.single().strikeKey, artifact.maskKey.strikeKey)
        assertTrue(artifact.mask.sourceOutlineSha256 != null)
    }

    @Test
    fun `default resolver builds ordered COLRv0 layer masks with the canonical color plan`() {
        val typeface = org.graphiks.kanvas.text.FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { stream -> stream.readBytes() },
            fontName = "Skia COLRv0 test font",
        )
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation = operation(
                    glyphIds = listOf(2),
                    positions = listOf(Point2F32(8f, 32f)),
                    transform = Matrix3x3F32.Identity,
                    typeface = typeface,
                ),
                operationIndex = 4,
                target = target(),
                capabilities = capabilities(),
            ),
        ).draw

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(lowered),
                generation = GPUTextArtifactGeneration(7),
                limits = limits(pageWidth = 128, pageHeight = 128, maxPages = 8).copy(
                    maxPageBytes = 16_384,
                    maxTotalPageBytes = 131_072,
                ),
            ),
        ).inventory
        val subRuns = ready.subRunsByOperationIndex.getValue(4)

        assertTrue(subRuns.isNotEmpty())
        assertTrue(subRuns.all { subRun ->
            subRun.representation == GPUPreparedTextRepresentation.COLRV0
        })
        assertTrue(subRuns.all { subRun -> subRun.colorGlyphLayerPlan != null })
        assertEquals(
            GPUTextArtifactGeneration(7),
            subRuns.first().colorGlyphLayerPlan!!.artifactKey.generation,
        )
        assertEquals(
            subRuns.first().colorGlyphLayerPlan!!.layerCount,
            ready.metrics.instanceCount,
        )
        assertEquals(
            (0 until ready.metrics.instanceCount).toList(),
            subRuns.flatMap { subRun -> subRun.instances }.map { instance ->
                instance.colorLayerIndex
            },
        )
    }

    @Test
    fun `COLR zero with CPAL one reaches the exact Task five artifact resolver`() {
        val bytes = checkNotNull(
            javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
        ).use { stream -> stream.readBytes() }
        val cpalOffset = sfntTableOffset(bytes, "CPAL")
        bytes[cpalOffset] = 0
        bytes[cpalOffset + 1] = 1
        val typeface = org.graphiks.kanvas.text.FontTypeface(bytes, "COLR0 CPAL1")
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation(
                    glyphIds = listOf(2),
                    positions = listOf(Point2F32(0f, 0f)),
                    transform = Matrix3x3F32.Identity,
                    typeface = typeface,
                ),
                0,
                target(),
                capabilities(),
            ),
        ).draw

        assertIs<PreparedTextGlyphArtifact.COLRV0>(
            ExactPreparedTextGlyphArtifactResolver.resolve(
                lowered,
                glyphIndex = 0,
                representation = GPUPreparedTextRepresentation.COLRV0,
            ),
        )
    }

    @Test
    fun `Task four admitted empty COLRv0 auxiliary layer reaches a ready inventory`() {
        val bytes = checkNotNull(
            javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
        ).use { stream -> stream.readBytes() }
        val colrOffset = sfntTableOffset(bytes, "COLR")
        val baseOffset = readSfntU32(bytes, colrOffset + 4)
        val layerOffset = readSfntU32(bytes, colrOffset + 8)
        val baseGlyphCount = readSfntU16(bytes, colrOffset + 2)
        val selectedBaseOffset = (0 until baseGlyphCount)
            .map { index -> colrOffset + baseOffset + index * 6 }
            .first { recordOffset ->
                readSfntU16(bytes, recordOffset) != 0 &&
                    readSfntU16(bytes, recordOffset + 4) >= 2
            }
        val baseGlyphId = readSfntU16(bytes, selectedBaseOffset)
        val firstLayerIndex = readSfntU16(bytes, selectedBaseOffset + 2)
        writeSfntU16(bytes, colrOffset + layerOffset + firstLayerIndex * 4, 0)
        writeLocaOffset(
            bytes = bytes,
            glyphId = 1,
            value = readLocaOffset(bytes, glyphId = 0),
        )
        val typeface = org.graphiks.kanvas.text.FontTypeface(
            bytes,
            "empty-glyph-zero-colrv0-layer",
        )
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation(
                    glyphIds = listOf(baseGlyphId),
                    positions = listOf(Point2F32(0f, 0f)),
                    transform = Matrix3x3F32.Identity,
                    typeface = typeface,
                ),
                0,
                target(),
                capabilities(),
            ),
        ).draw

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(lowered),
                generation = GPUTextArtifactGeneration(1),
                limits = colorLimits(),
            ),
        ).inventory
        val subRuns = ready.subRunsByOperationIndex.getValue(0)
        val plan = checkNotNull(subRuns.first().colorGlyphLayerPlan)

        assertTrue(plan.layerCount > ready.metrics.instanceCount)
        assertTrue(
            subRuns.flatMap { subRun -> subRun.instances }.none { instance ->
                instance.colorLayerIndex == 0
            },
        )
    }

    @Test
    fun `huge blur is refused before convolution and never throws`() {
        val blurCount = AtomicInteger()
        val result = PreparedTextFrameInventoryBuilder.build(
            draws = listOf(
                draw(
                    operationIndex = 0,
                    glyphs = listOf(glyph(7)),
                    paint = Paint.fill(Color.RED).copy(
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, Float.MAX_VALUE),
                    ),
                ),
            ),
            generation = GPUTextArtifactGeneration(1),
            limits = limits(pageWidth = 16, pageHeight = 16),
            artifactResolver = resolver(),
            observer = object : PreparedTextFrameInventoryObserver {
                override fun onBlurConvolution() {
                    blurCount.incrementAndGet()
                }
            },
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED, refused.code)
        assertEquals(0, blurCount.get())
        assertEquals("0", refused.facts["publishedPageCount"])
    }

    @Test
    fun `zero sigma is normalized to no mask filter for every style`() {
        BlurStyle.entries.forEach { style ->
            val unfiltered = assertIs<PreparedTextFrameInventoryResult.Ready>(
                build(listOf(draw(0, listOf(glyph(7))))),
            ).inventory
            val zeroBlur = assertIs<PreparedTextFrameInventoryResult.Ready>(
                build(
                    listOf(
                        draw(
                            operationIndex = 0,
                            glyphs = listOf(glyph(7)),
                            paint = Paint.fill(Color.RED).copy(
                                maskFilter = MaskFilter.Blur(style, 0f),
                            ),
                        ),
                    ),
                ),
            ).inventory

            assertEquals(unfiltered.pages.single().bytes, zeroBlur.pages.single().bytes, style.name)
            assertEquals(
                unfiltered.maskIdentityByGlyphUse.single().maskKeySha256,
                zeroBlur.maskIdentityByGlyphUse.single().maskKeySha256,
                style.name,
            )
        }
    }

    @Test
    fun `blur bearing overflow is refused before convolution`() {
        val blurCount = AtomicInteger()
        val extremeMask = A8GlyphMask(
            glyphId = 7,
            width = 1,
            height = 1,
            left = Int.MIN_VALUE,
            top = Int.MIN_VALUE,
            pixels = listOf(255),
            sourceOutlineSha256 = "7".repeat(64),
        )
        val result = PreparedTextFrameInventoryBuilder.build(
            draws = listOf(
                draw(
                    operationIndex = 0,
                    glyphs = listOf(glyph(7)),
                    paint = Paint.fill(Color.RED).copy(
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    ),
                ),
            ),
            generation = GPUTextArtifactGeneration(1),
            limits = limits(pageWidth = 32, pageHeight = 32),
            artifactResolver = resolver(mask = extremeMask),
            observer = object : PreparedTextFrameInventoryObserver {
                override fun onBlurConvolution() {
                    blurCount.incrementAndGet()
                }
            },
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(GPUTextRefusalCodes.MASK_GENERATION_FAILED, refused.code)
        assertEquals("blur-bearing-overflow", refused.facts["reason"])
        assertEquals(0, blurCount.get())
    }

    @Test
    fun `blur right and bottom overflow are refused before convolution`() {
        val keyHash = "7".repeat(64)
        val masks = listOf(
            A8GlyphMask(
                glyphId = 7,
                width = 1,
                height = 1,
                left = Int.MAX_VALUE - 1,
                top = 0,
                pixels = listOf(255),
                sourceOutlineSha256 = keyHash,
            ),
            A8GlyphMask(
                glyphId = 7,
                width = 1,
                height = 1,
                left = 0,
                top = Int.MAX_VALUE - 1,
                pixels = listOf(255),
                sourceOutlineSha256 = keyHash,
            ),
        )

        masks.forEach { extremeMask ->
            val blurCount = AtomicInteger()
            val result = PreparedTextFrameInventoryBuilder.build(
                draws = listOf(
                    draw(
                        operationIndex = 0,
                        glyphs = listOf(glyph(7)),
                        paint = Paint.fill(Color.RED).copy(
                            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                        ),
                    ),
                ),
                generation = GPUTextArtifactGeneration(1),
                limits = limits(pageWidth = 32, pageHeight = 32),
                artifactResolver = resolver(mask = extremeMask),
                observer = object : PreparedTextFrameInventoryObserver {
                    override fun onBlurConvolution() {
                        blurCount.incrementAndGet()
                    }
                },
            )

            val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
            assertEquals(GPUTextRefusalCodes.MASK_GENERATION_FAILED, refused.code)
            assertEquals("blur-bounds-overflow", refused.facts["reason"])
            assertEquals(0, blurCount.get())
        }
    }

    @Test
    fun `impossible first-use budget stops after the first nonempty artifact`() {
        val pageBytes = 16 * 16
        listOf(
            GPUTextRefusalCodes.INSTANCE_BUFFER_BUDGET_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxInstances = 0,
                ),
            GPUTextRefusalCodes.INSTANCE_BYTES_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxInstanceBytes = GPUTextA8Instance.ENCODED_BYTE_SIZE - 1,
                ),
            GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxPages = 0,
                ),
            GPUTextRefusalCodes.ATLAS_PAGE_BYTES_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxPageBytes = pageBytes - 1,
                ),
            GPUTextRefusalCodes.ATLAS_TOTAL_BYTES_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxTotalPageBytes = pageBytes - 1,
                ),
            GPUTextRefusalCodes.SUBRUN_BUDGET_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(
                    maxSubRuns = 0,
                ),
        ).forEach { (expectedCode, constrainedLimits) ->
            val resolutionCount = AtomicInteger()
            val fingerprintCount = AtomicInteger()
            val sampleValidationCount = AtomicInteger()
            val blurCount = AtomicInteger()
            val delegate = resolver()
            val result = PreparedTextFrameInventoryBuilder.build(
                draws = listOf(
                    draw(
                        operationIndex = 0,
                        glyphs = listOf(glyph(7), glyph(8), glyph(9)),
                        paint = Paint.fill(Color.RED).copy(
                            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                        ),
                    ),
                ),
                generation = GPUTextArtifactGeneration(1),
                limits = constrainedLimits,
                artifactResolver = PreparedTextGlyphArtifactResolver {
                        preparedDraw,
                        glyphIndex,
                        representation,
                    ->
                    resolutionCount.incrementAndGet()
                    delegate.resolve(preparedDraw, glyphIndex, representation)
                },
                observer = object : PreparedTextFrameInventoryObserver {
                    override fun onMaskSamplesValidated() {
                        sampleValidationCount.incrementAndGet()
                    }

                    override fun onMaskFingerprintComputed() {
                        fingerprintCount.incrementAndGet()
                    }

                    override fun onBlurConvolution() {
                        blurCount.incrementAndGet()
                    }
                },
            )

            val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
            assertEquals(expectedCode, refused.code)
            assertEquals(1, resolutionCount.get(), expectedCode)
            assertEquals(0, sampleValidationCount.get(), expectedCode)
            assertEquals(0, fingerprintCount.get(), expectedCode)
            assertEquals(0, blurCount.get(), expectedCode)
        }
    }

    @Test
    fun `impossible page budget outranks sample scan after structural nonempty proof`() {
        val sampleValidationCount = AtomicInteger()
        val invalidSampleMask = A8GlyphMask(
            glyphId = 7,
            width = 1,
            height = 1,
            pixels = listOf(256),
            sourceOutlineSha256 = "7".repeat(64),
        )

        listOf(
            GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(maxPages = 0),
            GPUTextRefusalCodes.ATLAS_PAGE_BYTES_EXCEEDED to
                limits(pageWidth = 16, pageHeight = 16).copy(maxPageBytes = 255),
        ).forEach { (expectedCode, constrainedLimits) ->
            sampleValidationCount.set(0)
            val result = PreparedTextFrameInventoryBuilder.build(
                draws = listOf(draw(0, listOf(glyph(7)))),
                generation = GPUTextArtifactGeneration(1),
                limits = constrainedLimits,
                artifactResolver = resolver(mask = invalidSampleMask),
                observer = object : PreparedTextFrameInventoryObserver {
                    override fun onMaskSamplesValidated() {
                        sampleValidationCount.incrementAndGet()
                    }
                },
            )

            val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
            assertEquals(expectedCode, refused.code)
            assertEquals(0, sampleValidationCount.get())
        }
    }

    @Test
    fun `COLRv0 zero page budget refuses before layer sample scans`() {
        val sampleValidationCount = AtomicInteger()
        val artifact = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        val result = PreparedTextFrameInventoryBuilder.build(
            draws = listOf(exactColorDraw()),
            generation = GPUTextArtifactGeneration(1),
            limits = colorLimits().copy(maxPages = 0),
            artifactResolver = PreparedTextGlyphArtifactResolver { _, _, _ -> artifact },
            observer = object : PreparedTextFrameInventoryObserver {
                override fun onMaskSamplesValidated() {
                    sampleValidationCount.incrementAndGet()
                }
            },
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED, refused.code)
        assertEquals(0, sampleValidationCount.get())
    }

    @Test
    fun `mask samples are validated when page budget permits work`() {
        val sampleValidationCount = AtomicInteger()
        val invalidSampleMask = A8GlyphMask(
            glyphId = 7,
            width = 1,
            height = 1,
            pixels = listOf(256),
            sourceOutlineSha256 = "7".repeat(64),
        )

        val result = PreparedTextFrameInventoryBuilder.build(
            draws = listOf(draw(0, listOf(glyph(7)))),
            generation = GPUTextArtifactGeneration(1),
            limits = limits(),
            artifactResolver = resolver(mask = invalidSampleMask),
            observer = object : PreparedTextFrameInventoryObserver {
                override fun onMaskSamplesValidated() {
                    sampleValidationCount.incrementAndGet()
                }
            },
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(GPUTextRefusalCodes.MASK_GENERATION_FAILED, refused.code)
        assertEquals("mask-sample-invalid", refused.facts["reason"])
        assertEquals(1, sampleValidationCount.get())
    }

    @Test
    fun `incoherent injected mask is refused transactionally`() {
        val invalidMask = A8GlyphMask(
            glyphId = 7,
            width = 2,
            height = 2,
            rowBytes = 1,
            pixels = listOf(255),
            sourceOutlineSha256 = "7".repeat(64),
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(
            build(
                listOf(draw(0, listOf(glyph(7)))),
                resolver = resolver(mask = invalidMask),
            ),
        )
        assertEquals(GPUTextRefusalCodes.MASK_GENERATION_FAILED, refused.code)
        assertEquals("0", refused.facts["publishedPageCount"])
    }

    @Test
    fun `subrun budget is refused before any page materialization`() {
        val pageCount = AtomicInteger()
        val result = PreparedTextFrameInventoryBuilder.build(
            draws = listOf(draw(0, listOf(glyph(7)))),
            generation = GPUTextArtifactGeneration(1),
            limits = limits().copy(maxSubRuns = 0),
            artifactResolver = resolver(),
            observer = object : PreparedTextFrameInventoryObserver {
                override fun onPageMaterialized() {
                    pageCount.incrementAndGet()
                }
            },
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(result)
        assertEquals(GPUTextRefusalCodes.SUBRUN_BUDGET_EXCEEDED, refused.code)
        assertEquals(0, pageCount.get())
    }

    @Test
    fun `same exact mask key with divergent bytes is refused`() {
        val calls = AtomicInteger()
        val draw = draw(0, listOf(glyph(7), glyph(7)))
        val resolver = PreparedTextGlyphArtifactResolver { preparedDraw, glyphIndex, _ ->
            val glyph = preparedDraw.glyphs[glyphIndex]
            val mask = A8GlyphMask(
                glyphId = glyph.glyphId,
                width = 1,
                height = 1,
                pixels = listOf(if (calls.getAndIncrement() == 0) 10 else 11),
                sourceOutlineSha256 = "7".repeat(64),
            )
            PreparedTextGlyphArtifact.A8(
                mask,
                GlyphMaskKey(
                    glyph.strikeKey,
                    preparedDraw.face.faceIndex,
                    checkNotNull(mask.sourceOutlineSha256),
                ),
            )
        }

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(
            build(listOf(draw), resolver = resolver),
        )
        assertEquals(GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC, refused.code)
        assertEquals("mask-key-content-mismatch", refused.facts["reason"])
    }

    @Test
    fun `draw facts and repeated blur are computed once per frame identity`() {
        val drawFactsCount = AtomicInteger()
        val blurCount = AtomicInteger()
        val fingerprintCount = AtomicInteger()
        val repeated = draw(
            operationIndex = 0,
            glyphs = listOf(glyph(8), glyph(8), glyph(8)),
            paint = Paint.fill(Color.RED).copy(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.5f),
            ),
        )
        val sharedMask = A8GlyphMask(
            glyphId = 8,
            width = 2,
            height = 2,
            pixels = listOf(255, 0, 0, 255),
            sourceOutlineSha256 = "8".repeat(64),
        )

        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(repeated),
                generation = GPUTextArtifactGeneration(1),
                limits = limits(),
                artifactResolver = resolver(mask = sharedMask),
                observer = object : PreparedTextFrameInventoryObserver {
                    override fun onDrawFactsComputed() {
                        drawFactsCount.incrementAndGet()
                    }

                    override fun onBlurConvolution() {
                        blurCount.incrementAndGet()
                    }

                    override fun onMaskFingerprintComputed() {
                        fingerprintCount.incrementAndGet()
                    }
                },
            ),
        ).inventory

        assertEquals(3, ready.metrics.instanceCount)
        assertEquals(1, ready.metrics.uniqueMaskCount)
        assertEquals(1, drawFactsCount.get())
        assertEquals(1, blurCount.get())
        assertEquals(1, fingerprintCount.get())
    }

    @Test
    fun `exact resolver parses one face and one color context per frame`() {
        val faceParseCount = AtomicInteger()
        val colorParseCount = AtomicInteger()
        val observer = object : PreparedTextFrameInventoryObserver {
            override fun onFaceParsed() {
                faceParseCount.incrementAndGet()
            }

            override fun onColorTablesParsed() {
                colorParseCount.incrementAndGet()
            }
        }
        val source = exactColorDraw()
        val repeated = GPUPreparedTextDraw.create(
            operationIndex = source.operationIndex,
            face = source.face,
            glyphs = listOf(source.glyphs.single(), source.glyphs.single()),
            originX = source.originX,
            originY = source.originY,
            transform = source.transform,
            clipContentKey = source.clipContentKey,
            clip = source.clip,
            paint = source.paint,
            material = source.material,
            blendPlan = source.blendPlan,
            targetColorFormat = source.targetColorFormat,
            capabilitySnapshotHash = source.capabilitySnapshotHash,
            representationPolicy = GPUPreparedTextRepresentationPolicy.create(
                listOf(
                    GPUPreparedTextRepresentation.COLRV0,
                    GPUPreparedTextRepresentation.COLRV0,
                ),
            ),
        )

        val result = PreparedTextFrameInventoryBuilder.build(
                draws = listOf(repeated),
                generation = GPUTextArtifactGeneration(1),
                limits = colorLimits(),
                artifactResolver = PerFrameExactPreparedTextGlyphArtifactResolver(observer),
                observer = observer,
            )
        if (result is PreparedTextFrameInventoryResult.Refused) {
            error("${result.code}: ${result.facts}")
        }
        assertIs<PreparedTextFrameInventoryResult.Ready>(result)
        assertEquals(1, faceParseCount.get())
        assertEquals(1, colorParseCount.get())
    }

    @Test
    fun `transient artifact collections are snapshotted before publication`() {
        val exact = assertIs<PreparedTextGlyphArtifact.COLRV0>(
            exactColorArtifact(),
        )
        val mutableLayers = exact.layers.toMutableList()
        val color = PreparedTextGlyphArtifact.COLRV0(mutableLayers, exact.colorPlan)
        mutableLayers.clear()
        assertTrue(color.layers.isNotEmpty())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (color.layers as MutableList<PreparedTextColorLayerArtifact>).clear()
        }
        val mutablePlanLayers = exact.colorPlan.layers.toMutableList()
        val mutableDiagnostics = mutableListOf(
            org.graphiks.kanvas.glyph.color.ColorGlyphDiagnostic(
                glyphId = exact.colorPlan.glyphId,
                route = "colrv0",
                message = "snapshot",
            ),
        )
        val colorWithMutablePlan = PreparedTextGlyphArtifact.COLRV0(
            exact.layers,
            exact.colorPlan.copy(
                layers = mutablePlanLayers,
                diagnostics = mutableDiagnostics,
            ),
        )
        mutablePlanLayers.clear()
        mutableDiagnostics.clear()
        assertEquals(exact.colorPlan.layers.size, colorWithMutablePlan.colorPlan.layers.size)
        assertEquals(1, colorWithMutablePlan.colorPlan.diagnostics.size)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (
                colorWithMutablePlan.colorPlan.layers as
                    MutableList<org.graphiks.kanvas.glyph.color.COLRV0LayerPlan>
                ).clear()
        }

        val mutableFacts = linkedMapOf("reason" to "test")
        val refusal = PreparedTextGlyphArtifact.Refused(
            GPUTextRefusalCodes.MASK_GENERATION_FAILED,
            mutableFacts,
        )
        mutableFacts["reason"] = "mutated"
        assertEquals("test", refusal.facts["reason"])
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (refusal.facts as MutableMap<String, String>)["reason"] = "forged"
        }
    }

    @Test
    fun `duplicate COLRv0 layer glyph keeps exact layer index across instances`() {
        val exact = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        val sourceLayer = assertIs<PreparedTextColorLayerArtifact.A8>(exact.layers.first())
        val sourcePlan = exact.colorPlan.layers.first()
        val artifact = PreparedTextGlyphArtifact.COLRV0(
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
        val draw = exactColorDraw()
        val result = build(
                listOf(draw),
                limits = colorLimits(),
                resolver = PreparedTextGlyphArtifactResolver { _, _, _ -> artifact },
            )
        if (result is PreparedTextFrameInventoryResult.Refused) {
            error("${result.code}: ${result.facts}")
        }
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(result).inventory

        assertEquals(
            listOf(0, 1),
            ready.subRunsByOperationIndex.getValue(draw.operationIndex)
                .flatMap { it.instances }
                .map { it.colorLayerIndex },
        )
    }

    @Test
    fun `COLRv0 subruns split at sixteen layers without splitting a glyph occurrence`() {
        val artifact = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        assertEquals(2, artifact.layers.size)

        fun assertSplit(
            glyphCount: Int,
            preparedArtifact: PreparedTextGlyphArtifact.COLRV0,
            expectedSubRunSizes: List<Int>,
        ) {
            val draw = exactColorDraw(glyphCount)
            val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
                build(
                    listOf(draw),
                    limits = colorLimits().copy(maxGlyphs = 32, maxInstances = 64),
                    resolver = PreparedTextGlyphArtifactResolver { _, _, _ -> preparedArtifact },
                ),
            ).inventory
            val subRuns = ready.subRunsByOperationIndex.getValue(draw.operationIndex)

            assertEquals(expectedSubRunSizes, subRuns.map { it.instances.size })
            assertEquals(expectedSubRunSizes.size, ready.metrics.subRunCount)
            val subRunByOccurrence = subRuns.flatMapIndexed { subRunIndex, subRun ->
                subRun.instances.map { instance -> instance.sourceGlyphIndex.value to subRunIndex }
            }.groupBy({ it.first }, { it.second })
            assertTrue(subRunByOccurrence.values.all { owners -> owners.distinct().size == 1 })
        }

        val oneLayerArtifact = PreparedTextGlyphArtifact.COLRV0(
            layers = artifact.layers.take(1),
            colorPlan = artifact.colorPlan.copy(layers = artifact.colorPlan.layers.take(1)),
        )
        assertSplit(
            glyphCount = 16,
            preparedArtifact = oneLayerArtifact,
            expectedSubRunSizes = listOf(16),
        )
        assertSplit(
            glyphCount = 17,
            preparedArtifact = oneLayerArtifact,
            expectedSubRunSizes = listOf(16, 1),
        )
        assertSplit(
            glyphCount = 8,
            preparedArtifact = artifact,
            expectedSubRunSizes = listOf(16),
        )
        assertSplit(
            glyphCount = 9,
            preparedArtifact = artifact,
            expectedSubRunSizes = listOf(16, 2),
        )
    }

    @Test
    fun `one COLRv0 occurrence beyond the payload layer limit refuses canonically`() {
        val exact = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        val sourceLayer = assertIs<PreparedTextColorLayerArtifact.A8>(exact.layers.first())
        val sourcePlan = exact.colorPlan.layers.first()
        val artifact = PreparedTextGlyphArtifact.COLRV0(
            layers = List(17) { layerIndex ->
                if (layerIndex == 16) {
                    PreparedTextColorLayerArtifact.Empty(
                        layerIndex = layerIndex,
                        glyphId = sourceLayer.glyphId,
                    )
                } else {
                    sourceLayer.copy(layerIndex = layerIndex)
                }
            },
            colorPlan = exact.colorPlan.copy(
                layers = List(17) { layerIndex ->
                    sourcePlan.copy(layerIndex = layerIndex)
                },
            ),
        )

        val refused = assertIs<PreparedTextFrameInventoryResult.Refused>(
            build(
                listOf(exactColorDraw()),
                limits = colorLimits(),
                resolver = PreparedTextGlyphArtifactResolver { _, _, _ -> artifact },
            ),
        )

        assertEquals(GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED, refused.code)
        assertEquals("colrv0-layer-count-exceeds-payload-limit", refused.facts["reason"])
    }

    @Test
    fun `COLRv0 layer index survives a multi-page split`() {
        val exact = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        val artifactLayers = exact.layers.take(2).mapIndexed { index, layer ->
            val a8Layer = assertIs<PreparedTextColorLayerArtifact.A8>(layer)
            val sourceHash = (index + 1).toString().repeat(64)
            val mask = A8GlyphMask(
                glyphId = a8Layer.mask.glyphId,
                width = 1,
                height = 1,
                pixels = listOf(255),
                sourceOutlineSha256 = sourceHash,
            )
            PreparedTextColorLayerArtifact.A8(
                layerIndex = index,
                mask = mask,
                maskKey = a8Layer.maskKey.copy(sourceOutlineSha256 = sourceHash),
            )
        }
        val artifact = PreparedTextGlyphArtifact.COLRV0(
            layers = artifactLayers,
            colorPlan = exact.colorPlan.copy(layers = exact.colorPlan.layers.take(2)),
        )
        val draw = exactColorDraw()
        val result = build(
            listOf(draw),
            limits = limits(pageWidth = 3, pageHeight = 3, maxPages = 2).copy(
                maxPageBytes = 9,
                maxTotalPageBytes = 18,
            ),
            resolver = PreparedTextGlyphArtifactResolver { _, _, _ -> artifact },
        )
        if (result is PreparedTextFrameInventoryResult.Refused) {
            error("${result.code}: ${result.facts}")
        }
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(result).inventory

        assertEquals(2, ready.pages.size)
        assertEquals(
            listOf(0 to 0, 1 to 1),
            ready.subRunsByOperationIndex.getValue(draw.operationIndex)
                .flatMap { it.instances }
                .map { it.colorLayerIndex to it.pageIndex },
        )
    }

    @Test
    fun `subrun identity treats every boundary component independently`() {
        val base = PreparedTextSubRunIdentity(
            operationIndex = 0,
            representation = GPUPreparedTextRepresentation.A8_MASK,
            pageIndex = 0,
            materialKey = "material:a",
            blendPlanIdentity = "blend:a",
            clipIdentity = "clip:a",
            transformClass = "identity",
            colorPlanIdentity = null,
        )
        val variants = listOf(
            base.copy(operationIndex = 1),
            base.copy(representation = GPUPreparedTextRepresentation.COLRV0),
            base.copy(pageIndex = 1),
            base.copy(materialKey = "material:b"),
            base.copy(blendPlanIdentity = "blend:b"),
            base.copy(clipIdentity = "clip:b"),
            base.copy(transformClass = "affine"),
            base.copy(colorPlanIdentity = "color:a"),
        )

        variants.forEach { variant ->
            assertEquals(2, countPreparedTextSubRuns(listOf(base, variant)), variant.toString())
        }
        assertEquals(1, countPreparedTextSubRuns(listOf(base, base)))
    }

    @Test
    fun `builder really separates mixed A8 and COLRv0 representations`() {
        val source = exactColorDraw()
        val exactColor = assertIs<PreparedTextGlyphArtifact.COLRV0>(exactColorArtifact())
        val mixed = GPUPreparedTextDraw.create(
            operationIndex = source.operationIndex,
            face = source.face,
            glyphs = listOf(source.glyphs.single(), source.glyphs.single()),
            originX = source.originX,
            originY = source.originY,
            transform = source.transform,
            clipContentKey = source.clipContentKey,
            clip = source.clip,
            paint = source.paint,
            material = source.material,
            blendPlan = source.blendPlan,
            targetColorFormat = source.targetColorFormat,
            capabilitySnapshotHash = source.capabilitySnapshotHash,
            representationPolicy = GPUPreparedTextRepresentationPolicy.create(
                listOf(
                    GPUPreparedTextRepresentation.A8_MASK,
                    GPUPreparedTextRepresentation.COLRV0,
                ),
            ),
        )
        val a8 = resolver().resolve(
            mixed,
            glyphIndex = 0,
            representation = GPUPreparedTextRepresentation.A8_MASK,
        )
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                listOf(mixed),
                limits = colorLimits(),
                resolver = PreparedTextGlyphArtifactResolver { _, _, representation ->
                    when (representation) {
                        GPUPreparedTextRepresentation.A8_MASK -> a8
                        GPUPreparedTextRepresentation.COLRV0 -> exactColor
                    }
                },
            ),
        ).inventory

        assertEquals(
            listOf(
                GPUPreparedTextRepresentation.A8_MASK,
                GPUPreparedTextRepresentation.COLRV0,
            ),
            ready.subRunsByOperationIndex.getValue(mixed.operationIndex)
                .map { subRun -> subRun.representation }
                .distinct(),
        )
    }

    @Test
    fun `builder retains distinct canonical blend and clip identities`() {
        val wideOpen = draw(0, listOf(glyph(7)))
        val plus = draw(
            operationIndex = 1,
            glyphs = listOf(glyph(8)),
            paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.PLUS),
        )
        val clipped = draw(
            operationIndex = 2,
            glyphs = listOf(glyph(9)),
            clip = ClipStack.DeviceRect(
                RectF32.ofLTRB(1f, 2f, 12f, 13f),
                antiAlias = false,
            ),
        )
        val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(listOf(wideOpen, plus, clipped)),
        ).inventory
        val openSubRun = ready.subRunsByOperationIndex.getValue(0).single()
        val plusSubRun = ready.subRunsByOperationIndex.getValue(1).single()
        val clippedSubRun = ready.subRunsByOperationIndex.getValue(2).single()

        assertNotEquals(openSubRun.blendPlanIdentity, plusSubRun.blendPlanIdentity)
        assertNotEquals(openSubRun.clipIdentity, clippedSubRun.clipIdentity)
        assertEquals(wideOpen.clipContentKey, openSubRun.clipIdentity)
        assertEquals(clipped.clipContentKey, clippedSubRun.clipIdentity)
    }

    @Test
    fun `reflection uses signed L with positive strike density`() {
        val transform = Matrix3x3F32.of(
            -1f, 0.5f, 10f,
            0.25f, 1f, 20f,
        )
        val ready = inventoryForAffine(transform, position = Point2F32(0f, 0f))
        val sx = sqrt(1f + 0.25f * 0.25f)
        val sy = sqrt(0.5f * 0.5f + 1f)
        fun mapped(qx: Float, qy: Float): Pair<Float, Float> =
            (10f - qx / sx + 0.5f * qy / sy) to
                (20f + 0.25f * qx / sx + qy / sy)
        val expected = listOf(
            mapped(2f, 3f),
            mapped(6f, 3f),
            mapped(6f, 9f),
            mapped(2f, 9f),
        ).flatMap { (x, y) -> listOf(x, y) }

        assertQuadEquals(expected, ready.singleInstance().deviceQuad)
    }

    @Test
    fun `all new task five production files remain free of native WebGPU types`() {
        val files = listOf(
            "../font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphMaskBlur.kt",
            "../font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUPreparedTextAtlas.kt",
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventory.kt",
        )
        val source = files.joinToString("\n") { java.io.File(it).readText() }

        listOf(
            "io.ygdrasil.webgpu",
            "GPUDevice",
            "GPUQueue",
            "GPUTexture",
            "GPUBuffer",
            "GPUCommandEncoder",
        ).forEach { forbidden ->
            assertTrue(forbidden !in source, "Task 5 pure artifacts must not depend on $forbidden")
        }
    }

    private fun inventoryForAffine(
        transform: Matrix3x3F32,
        position: Point2F32,
    ): PreparedTextFrameInventory {
        val operation = operation(
            glyphIds = listOf(5),
            positions = listOf(position),
            transform = transform,
        )
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(operation, 0, target(), capabilities()),
        ).draw
        return assertIs<PreparedTextFrameInventoryResult.Ready>(
            build(
                listOf(lowered),
                resolver = resolver(
                    mask = A8GlyphMask(
                        glyphId = 5,
                        width = 4,
                        height = 6,
                        left = 2,
                        top = 3,
                        pixels = List(24) { 255 },
                        sourceOutlineSha256 = "5".repeat(64),
                    ),
                ),
            ),
        ).inventory
    }

    private fun exactColorDraw(glyphCount: Int = 1): GPUPreparedTextDraw {
        val typeface = org.graphiks.kanvas.text.FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { stream -> stream.readBytes() },
            fontName = "Skia COLRv0 test font",
        )
        return assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation(
                    glyphIds = List(glyphCount) { 2 },
                    positions = List(glyphCount) { index -> Point2F32(index * 12f, 0f) },
                    transform = Matrix3x3F32.Identity,
                    typeface = typeface,
                ),
                9,
                target(),
                capabilities(),
            ),
        ).draw
    }

    private fun exactColorArtifact(): PreparedTextGlyphArtifact {
        val draw = exactColorDraw()
        return ExactPreparedTextGlyphArtifactResolver.resolve(
            draw,
            glyphIndex = 0,
            representation = GPUPreparedTextRepresentation.COLRV0,
        )
    }

    private fun sfntTableOffset(bytes: ByteArray, wantedTag: String): Int {
        val tableCount = ((bytes[4].toInt() and 0xff) shl 8) or
            (bytes[5].toInt() and 0xff)
        repeat(tableCount) { index ->
            val record = 12 + index * 16
            val tag = String(bytes, record, 4, Charsets.ISO_8859_1)
            if (tag == wantedTag) {
                return ((bytes[record + 8].toInt() and 0xff) shl 24) or
                    ((bytes[record + 9].toInt() and 0xff) shl 16) or
                    ((bytes[record + 10].toInt() and 0xff) shl 8) or
                    (bytes[record + 11].toInt() and 0xff)
            }
        }
        error("Missing table $wantedTag")
    }

    private fun readSfntU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun readSfntU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeSfntU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun readLocaOffset(bytes: ByteArray, glyphId: Int): Int {
        val locaOffset = sfntTableOffset(bytes, "loca")
        val headOffset = sfntTableOffset(bytes, "head")
        return when (readSfntU16(bytes, headOffset + 50)) {
            0 -> readSfntU16(bytes, locaOffset + glyphId * 2) * 2
            1 -> readSfntU32(bytes, locaOffset + glyphId * 4)
            else -> error("Unsupported fixture loca format")
        }
    }

    private fun writeLocaOffset(bytes: ByteArray, glyphId: Int, value: Int) {
        val locaOffset = sfntTableOffset(bytes, "loca")
        val headOffset = sfntTableOffset(bytes, "head")
        when (readSfntU16(bytes, headOffset + 50)) {
            0 -> {
                require(value % 2 == 0)
                writeSfntU16(bytes, locaOffset + glyphId * 2, value / 2)
            }
            1 -> {
                val offset = locaOffset + glyphId * 4
                bytes[offset] = (value ushr 24).toByte()
                bytes[offset + 1] = (value ushr 16).toByte()
                bytes[offset + 2] = (value ushr 8).toByte()
                bytes[offset + 3] = value.toByte()
            }
            else -> error("Unsupported fixture loca format")
        }
    }

    private fun PreparedTextFrameInventory.singleInstance() =
        subRunsByOperationIndex.values.single().single().instances.single()

    private fun build(
        draws: List<GPUPreparedTextDraw>,
        limits: PreparedTextFrameInventoryLimits = limits(),
        resolver: PreparedTextGlyphArtifactResolver = resolver(),
    ): PreparedTextFrameInventoryResult = PreparedTextFrameInventoryBuilder.build(
        draws = draws,
        generation = GPUTextArtifactGeneration(1),
        limits = limits,
        artifactResolver = resolver,
    )

    private fun resolver(
        maskPixel: Int = 255,
        mask: A8GlyphMask? = null,
    ): PreparedTextGlyphArtifactResolver = PreparedTextGlyphArtifactResolver {
            draw,
            glyphIndex,
            _,
        ->
        val glyph = draw.glyphs[glyphIndex]
        val resolvedMask = mask ?: A8GlyphMask(
            glyphId = glyph.glyphId,
            width = 2,
            height = 2,
            pixels = listOf(maskPixel, 0, 0, maskPixel),
            sourceOutlineSha256 = glyph.glyphId.toString().last().toString().repeat(64),
        )
        PreparedTextGlyphArtifact.A8(
            mask = resolvedMask,
            maskKey = GlyphMaskKey(
                strikeKey = glyph.strikeKey,
                faceIndex = draw.face.faceIndex,
                sourceOutlineSha256 = checkNotNull(resolvedMask.sourceOutlineSha256),
            ),
        )
    }

    private fun draw(
        operationIndex: Int,
        glyphs: List<GPUPreparedGlyphInput>,
        faceIndex: Int = 0,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        paint: Paint = Paint.fill(Color.RED),
        clip: ClipStack = ClipStack.WideOpen,
    ): GPUPreparedTextDraw {
        val operation = operation(
            glyphIds = listOf(5),
            positions = listOf(Point2F32(0f, 0f)),
            transform = transform,
            paint = paint,
            clip = clip,
        )
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(operation, operationIndex, target(), capabilities()),
        ).draw
        return GPUPreparedTextDraw.create(
            operationIndex = operationIndex,
            face = GPUPreparedFontFaceSnapshot.create(
                sourceId = lowered.face.sourceId,
                typefaceId = lowered.face.typefaceId,
                faceIndex = faceIndex,
                bytes = lowered.face.bytes,
                provenance = lowered.face.provenance,
            ),
            glyphs = glyphs,
            originX = lowered.originX,
            originY = lowered.originY,
            transform = transform,
            clipContentKey = lowered.clipContentKey,
            clip = lowered.clip,
            paint = paint,
            material = lowered.material,
            blendPlan = lowered.blendPlan,
            targetColorFormat = lowered.targetColorFormat,
            capabilitySnapshotHash = lowered.capabilitySnapshotHash,
            representationPolicy = GPUPreparedTextRepresentationPolicy.create(
                List(glyphs.size) { GPUPreparedTextRepresentation.A8_MASK },
            ),
        )
    }

    private fun draw(
        operationIndex: Int,
        glyphs: List<GPUPreparedGlyphInput>,
    ): GPUPreparedTextDraw = draw(
        operationIndex = operationIndex,
        glyphs = glyphs,
        faceIndex = 0,
        transform = Matrix3x3F32.Identity,
        paint = Paint.fill(Color.RED),
    )

    private fun glyph(glyphId: Int): GPUPreparedGlyphInput {
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation(
                    glyphIds = listOf(5),
                    positions = listOf(Point2F32(0f, 0f)),
                    transform = Matrix3x3F32.Identity,
                ),
                0,
                target(),
                capabilities(),
            ),
        ).draw.glyphs.single()
        return GPUPreparedGlyphInput.create(
            glyphId = glyphId,
            positionX = lowered.positionX,
            positionY = lowered.positionY,
            fontSize = lowered.fontSize,
            strikeKey = lowered.strikeKey.copy(glyphId = glyphId),
        )
    }

    private fun operation(
        glyphIds: List<Int>,
        positions: List<Point2F32>,
        transform: Matrix3x3F32,
        typeface: org.graphiks.kanvas.text.FontTypeface = liberationTypeface(),
        paint: Paint = Paint.fill(Color.RED),
        clip: ClipStack = ClipStack.WideOpen,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = glyphIds.map(Int::toUShort),
                    positions = positions,
                    fontSize = 16f,
                ),
            ),
            typeface = typeface,
            fontSize = 16f,
        ),
        x = 0f,
        y = 0f,
        paint = paint,
        transform = transform,
        clip = clip,
    )

    private fun limits(
        pageWidth: Int = 16,
        pageHeight: Int = 16,
        maxPages: Int = 8,
    ): PreparedTextFrameInventoryLimits = PreparedTextFrameInventoryLimits(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        maxPages = maxPages,
        maxPageBytes = 1_024,
        maxTotalPageBytes = 8_192,
        maxGlyphs = 64,
        maxInstances = 64,
        maxSubRuns = 64,
        maxInstanceBytes = 4_096,
        maxTextureDimension2D = 8_192,
    )

    private fun colorLimits(): PreparedTextFrameInventoryLimits =
        limits(pageWidth = 128, pageHeight = 128, maxPages = 8).copy(
            maxPageBytes = 16_384,
            maxTotalPageBytes = 131_072,
        )

    private fun GPUPreparedGlyphInput.copyForTest(
        strikeKey: GlyphStrikeKey = this.strikeKey,
    ): GPUPreparedGlyphInput = GPUPreparedGlyphInput.create(
        glyphId = glyphId,
        positionX = positionX,
        positionY = positionY,
        fontSize = fontSize,
        strikeKey = strikeKey,
    )

    private fun assertQuadEquals(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index], 0.0001f, "quad[$index]")
        }
    }

    data class BudgetCase(
        val expectedCode: String,
        val requiresTwoPages: Boolean = false,
        val configure: (PreparedTextFrameInventoryLimits) -> PreparedTextFrameInventoryLimits,
    )

    companion object {
        @JvmStatic
        fun budgetCases(): List<Arguments> = listOf(
            Named.of(
                "maxPages",
                BudgetCase(
                    expectedCode = GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
                    requiresTwoPages = true,
                    configure = { it.copy(maxPages = 1) },
                ),
            ),
            Named.of(
                "maxPageBytes",
                BudgetCase(
                    GPUTextRefusalCodes.ATLAS_PAGE_BYTES_EXCEEDED,
                    configure = { it.copy(maxPageBytes = 15) },
                ),
            ),
            Named.of(
                "maxTotalPageBytes",
                BudgetCase(
                    GPUTextRefusalCodes.ATLAS_TOTAL_BYTES_EXCEEDED,
                    configure = { it.copy(maxTotalPageBytes = 15) },
                ),
            ),
            Named.of(
                "maxGlyphs",
                BudgetCase(
                    GPUTextRefusalCodes.GLYPH_BUDGET_EXCEEDED,
                    configure = { it.copy(maxGlyphs = 0) },
                ),
            ),
            Named.of(
                "maxInstances",
                BudgetCase(
                    GPUTextRefusalCodes.INSTANCE_BUFFER_BUDGET_EXCEEDED,
                    configure = { it.copy(maxInstances = 0) },
                ),
            ),
            Named.of(
                "maxSubRuns",
                BudgetCase(
                    GPUTextRefusalCodes.SUBRUN_BUDGET_EXCEEDED,
                    configure = { it.copy(maxSubRuns = 0) },
                ),
            ),
            Named.of(
                "maxInstanceBytes",
                BudgetCase(
                    GPUTextRefusalCodes.INSTANCE_BYTES_EXCEEDED,
                    configure = { it.copy(maxInstanceBytes = 63) },
                ),
            ),
            Named.of(
                "WebGPU texture dimension",
                BudgetCase(
                    GPUTextRefusalCodes.ATLAS_PAGE_BUDGET_EXCEEDED,
                    configure = { it.copy(maxTextureDimension2D = 3) },
                ),
            ),
        ).map { named -> Arguments.of(named) }
    }
}

private fun org.graphiks.kanvas.glyph.gpu.GPUTextIntRect.overlaps(
    other: org.graphiks.kanvas.glyph.gpu.GPUTextIntRect,
): Boolean =
    left < other.right && other.left < right &&
        top < other.bottom && other.top < bottom
