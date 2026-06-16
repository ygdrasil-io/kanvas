package org.graphiks.kanvas.glyph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Verifies that the pure Kotlin glyph package exposes the planned public surface.
 */
class GlyphSurfaceTest {
    /**
     * References every top-level glyph pipeline type so the module fails to compile until the
     * surface exists.
     */
    @Test
    fun exposesGlyphPipelineSurface() {
        val names = listOf(
            GPUGlyphRunDescriptor::class.simpleName,
            GlyphStrikeKey::class.simpleName,
            GlyphRepresentation::class.simpleName,
            GlyphArtifactPlanner::class.simpleName,
            GlyphArtifactPlan::class.simpleName,
            OutlineGlyphRepresentation::class.simpleName,
            GlyphMaskGenerator::class.simpleName,
            A8GlyphMask::class.simpleName,
            SDFGlyphGenerator::class.simpleName,
            SDFGlyphMask::class.simpleName,
            GlyphAtlasPacker::class.simpleName,
            GlyphAtlasArtifactBuilder::class.simpleName,
            SDFGlyphAtlasArtifactBuilder::class.simpleName,
            GlyphCache::class.simpleName,
            GlyphCacheBudget::class.simpleName,
            GlyphRunCacheBuildGlyph::class.simpleName,
            GlyphRunCacheInventory::class.simpleName,
            GlyphRunCacheInventoryItem::class.simpleName,
            GlyphCacheRecord::class.simpleName,
            GlyphMaskSummary::class.simpleName,
            GlyphRouteDiagnostic::class.simpleName,
            GlyphArtifactPlanDecision::class.simpleName,
            GlyphArtifactRouteRejection::class.simpleName,
            GlyphAtlasPackingResult::class.simpleName,
            A8GlyphMaskArtifactEvidence::class.simpleName,
            A8GlyphMaskEvidenceDump::class.simpleName,
        )

        assertEquals(
            listOf(
                "GPUGlyphRunDescriptor",
                "GlyphStrikeKey",
                "GlyphRepresentation",
                "GlyphArtifactPlanner",
                "GlyphArtifactPlan",
                "OutlineGlyphRepresentation",
                "GlyphMaskGenerator",
                "A8GlyphMask",
                "SDFGlyphGenerator",
                "SDFGlyphMask",
                "GlyphAtlasPacker",
                "GlyphAtlasArtifactBuilder",
                "SDFGlyphAtlasArtifactBuilder",
                "GlyphCache",
                "GlyphCacheBudget",
                "GlyphRunCacheBuildGlyph",
                "GlyphRunCacheInventory",
                "GlyphRunCacheInventoryItem",
                "GlyphCacheRecord",
                "GlyphMaskSummary",
                "GlyphRouteDiagnostic",
                "GlyphArtifactPlanDecision",
                "GlyphArtifactRouteRejection",
                "GlyphAtlasPackingResult",
                "A8GlyphMaskArtifactEvidence",
                "A8GlyphMaskEvidenceDump",
            ),
            names,
        )
    }

    @Test
    fun glyphMaskGeneratorRasterizesClosedRectangleToDeterministicA8Mask() {
        val generator = object : GlyphMaskGenerator {}
        val outline = OutlineGlyphRepresentation(
            glyphId = 50,
            pathCommands = listOf(
                "M 1 2",
                "L 4 2",
                "L 4 5",
                "L 1 5",
                "Z",
            ),
        )

        val mask = generator.generate(
            outline = outline,
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441041"),
        )

        assertEquals(50, mask.glyphId)
        assertEquals(3, mask.width)
        assertEquals(3, mask.height)
        assertEquals(1, mask.left)
        assertEquals(2, mask.top)
        assertEquals(3, mask.rowBytes)
        assertEquals(
            listOf(
                255, 255, 255,
                255, 255, 255,
                255, 255, 255,
            ),
            mask.pixels,
        )
    }

    @Test
    fun glyphMaskGeneratorReturnsEmptyA8MaskForEmptyOutline() {
        val generator = object : GlyphMaskGenerator {}

        val mask = generator.generate(
            outline = OutlineGlyphRepresentation(glyphId = 51),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441042"),
        )

        assertEquals(
            A8GlyphMask(glyphId = 51, width = 0, height = 0, pixels = emptyList()),
            mask.copy(diagnostics = emptyList(), sourceOutlineSha256 = null),
        )
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(1, mask.diagnostics.size)
        assertEquals("text.glyph.A8-generation-failed", mask.diagnostics.single().route)
        assertEquals("info", mask.diagnostics.single().severity)
        assertTrue(mask.diagnostics.single().message.contains("reason=empty-outline"))
    }

    @Test
    fun glyphMaskGeneratorRasterizesClosedQuadraticContourToDeterministicA8Mask() {
        val generator = object : GlyphMaskGenerator {}
        val outline = OutlineGlyphRepresentation(
            glyphId = 54,
            pathCommands = listOf(
                "M 1 2",
                "Q 2.5 2 4 2",
                "Q 4 3.5 4 5",
                "Q 2.5 5 1 5",
                "Q 1 3.5 1 2",
                "Z",
            ),
        )

        val mask = generator.generate(
            outline = outline,
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441080"),
        )

        assertEquals(54, mask.glyphId)
        assertEquals(3, mask.width)
        assertEquals(3, mask.height)
        assertEquals(1, mask.left)
        assertEquals(2, mask.top)
        assertEquals(3, mask.rowBytes)
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(emptyList(), mask.diagnostics)
        assertEquals(
            listOf(
                255, 255, 255,
                255, 255, 255,
                255, 255, 255,
            ),
            mask.pixels,
        )
    }

    @Test
    fun glyphMaskGeneratorRasterizesClosedCubicContourToDeterministicA8Mask() {
        val generator = object : GlyphMaskGenerator {}
        val outline = OutlineGlyphRepresentation(
            glyphId = 55,
            pathCommands = listOf(
                "M 1 2",
                "C 2 2 3 2 4 2",
                "C 4 3 4 4 4 5",
                "C 3 5 2 5 1 5",
                "C 1 4 1 3 1 2",
                "Z",
            ),
        )

        val mask = generator.generate(
            outline = outline,
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441081"),
        )

        assertEquals(55, mask.glyphId)
        assertEquals(3, mask.width)
        assertEquals(3, mask.height)
        assertEquals(1, mask.left)
        assertEquals(2, mask.top)
        assertEquals(3, mask.rowBytes)
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(emptyList(), mask.diagnostics)
        assertEquals(
            listOf(
                255, 255, 255,
                255, 255, 255,
                255, 255, 255,
            ),
            mask.pixels,
        )
    }

    @Test
    fun glyphMaskGeneratorReturnsStableDiagnosticForUnsupportedWindingRule() {
        val generator = object : GlyphMaskGenerator {}
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441082")
        val outline = OutlineGlyphRepresentation(
            glyphId = 56,
            pathCommands = listOf(
                "M 1 2",
                "L 4 2",
                "L 4 5",
                "L 1 5",
                "Z",
            ),
            windingRule = "evenOdd",
        )

        val mask = generator.generate(outline = outline, strikeKey = strikeKey)

        assertEquals(A8GlyphMask(glyphId = 56, width = 0, height = 0, pixels = emptyList()), mask.copy(
            diagnostics = emptyList(),
            sourceOutlineSha256 = null,
        ))
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(1, mask.diagnostics.size)
        assertEquals(56, mask.diagnostics.single().glyphId)
        assertEquals("text.glyph.A8-generation-failed", mask.diagnostics.single().route)
        assertEquals("warning", mask.diagnostics.single().severity)
        assertTrue(mask.diagnostics.single().message.contains("reason=unsupported-winding-rule"))
        assertTrue(mask.diagnostics.single().message.contains(
            strikeKey.copy(
                representationRoute = "text.glyph.mask.A8",
                maskFormat = "A8",
            ).preimageSha256(glyphId = 56),
        ))
    }

    @Test
    fun glyphMaskGeneratorReturnsStableDiagnosticForMalformedContour() {
        val generator = object : GlyphMaskGenerator {}
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441083")
        val outline = OutlineGlyphRepresentation(
            glyphId = 57,
            pathCommands = listOf("L 1 2"),
        )

        val mask = generator.generate(outline = outline, strikeKey = strikeKey)

        assertEquals(A8GlyphMask(glyphId = 57, width = 0, height = 0, pixels = emptyList()), mask.copy(
            diagnostics = emptyList(),
            sourceOutlineSha256 = null,
        ))
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(1, mask.diagnostics.size)
        assertEquals(57, mask.diagnostics.single().glyphId)
        assertEquals("text.glyph.A8-generation-failed", mask.diagnostics.single().route)
        assertEquals("warning", mask.diagnostics.single().severity)
        assertTrue(mask.diagnostics.single().message.contains("reason=malformed-outline"))
        assertTrue(mask.diagnostics.single().message.contains(
            strikeKey.copy(
                representationRoute = "text.glyph.mask.A8",
                maskFormat = "A8",
            ).preimageSha256(glyphId = 57),
        ))
    }

    @Test
    fun glyphMaskGeneratorReturnsStableDiagnosticForCoverageOverflow() {
        val generator = object : GlyphMaskGenerator {}
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441090")
        val outline = OutlineGlyphRepresentation(
            glyphId = 58,
            pathCommands = listOf(
                "M 0 0",
                "L 5000 0",
                "L 5000 5000",
                "L 0 5000",
                "Z",
            ),
        )

        val mask = generator.generate(outline = outline, strikeKey = strikeKey)

        assertEquals(
            A8GlyphMask(glyphId = 58, width = 0, height = 0, pixels = emptyList()),
            mask.copy(diagnostics = emptyList(), sourceOutlineSha256 = null),
        )
        assertEquals(64, mask.sourceOutlineSha256?.length)
        assertEquals(1, mask.diagnostics.size)
        assertEquals(58, mask.diagnostics.single().glyphId)
        assertEquals("text.glyph.A8-generation-failed", mask.diagnostics.single().route)
        assertEquals("warning", mask.diagnostics.single().severity)
        assertTrue(mask.diagnostics.single().message.contains("reason=coverage-overflow"))
        assertTrue(mask.diagnostics.single().message.contains(
            strikeKey.copy(
                representationRoute = "text.glyph.mask.A8",
                maskFormat = "A8",
            ).preimageSha256(glyphId = 58),
        ))
    }

    @Test
    fun a8GlyphMaskArtifactEvidenceRecordsBoundsAndCoverageHash() {
        val mask = A8GlyphMask(
            glyphId = 70,
            width = 2,
            height = 2,
            left = -1,
            top = 3,
            rowBytes = 3,
            sourceOutlineSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            pixels = listOf(
                0, 7, 99,
                255, 0, 99,
            ),
            diagnostics = listOf(
                GlyphRouteDiagnostic(
                    glyphId = 70,
                    route = "text.glyph.A8-generation-failed",
                    message = "Synthetic diagnostic snapshot.",
                    severity = "warning",
                ),
            ),
        )

        val evidence = A8GlyphMaskArtifactEvidence.from(
            mask = mask,
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441072"),
        )

        assertEquals(70, evidence.glyphId)
        assertEquals(4, evidence.addressablePixelCount)
        assertEquals(2, evidence.nonZeroPixels)
        assertEquals("79ef38e8384dc02cd1de6202a09cab298e2bac8148fff30a3c437f87e63956eb", evidence.coverageSha256)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", evidence.sourceOutlineSha256)
        assertEquals(mask.diagnostics, evidence.diagnostics)
        assertEquals(64, evidence.strikeKeySha256.length)
        assertEquals(64, evidence.dumpSha256.length)
        assertEquals(
            """
            {
              "schema": "org.graphiks.kanvas.glyph.A8GlyphMaskArtifactEvidence.v1",
              "glyphId": 70,
              "strikeKeySha256": "${evidence.strikeKeySha256}",
              "bounds": {"left": -1, "top": 3, "width": 2, "height": 2},
              "rowBytes": 3,
              "addressablePixelCount": 4,
              "nonZeroPixels": 2,
              "coverageSha256": "79ef38e8384dc02cd1de6202a09cab298e2bac8148fff30a3c437f87e63956eb",
              "sourceOutlineSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              "diagnostics": [
                {
                  "glyphId": 70,
                  "route": "text.glyph.A8-generation-failed",
                  "severity": "warning",
                  "message": "Synthetic diagnostic snapshot."
                }
              ],
              "dumpSha256": "${evidence.dumpSha256}"
            }
            """.trimIndent() + "\n",
            evidence.toCanonicalJson(),
        )
    }

    @Test
    fun sdfGlyphGeneratorBuildsDeterministicSignedDistanceMaskForClosedRectangle() {
        val generator = object : SDFGlyphGenerator {}
        val outline = OutlineGlyphRepresentation(
            glyphId = 52,
            pathCommands = listOf(
                "M 1 1",
                "L 5 1",
                "L 5 5",
                "L 1 5",
                "Z",
            ),
        )

        val mask = generator.generate(
            outline = outline,
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441043"),
        )

        assertEquals(52, mask.glyphId)
        assertEquals(6, mask.width)
        assertEquals(6, mask.height)
        assertEquals(4f, mask.distanceRange)
        assertEquals(36, mask.pixels.size)
        assertTrue(mask.pixels.any { sample -> sample != 0 })

        val outside = mask.pixels[0]
        val edge = mask.pixels[1 * mask.width + 1]
        val center = mask.pixels[3 * mask.width + 3]

        assertTrue(outside < edge, "Outside samples should encode a lower signed distance than edge samples.")
        assertTrue(edge in 120..136, "Edge samples should stay near the 128 SDF midpoint.")
        assertTrue(center > edge, "Interior samples should encode a higher signed distance than edge samples.")
    }

    @Test
    fun sdfGlyphGeneratorReturnsEmptyMaskForEmptyOutline() {
        val generator = object : SDFGlyphGenerator {}

        val mask = generator.generate(
            outline = OutlineGlyphRepresentation(glyphId = 53),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441044"),
        )

        assertEquals(SDFGlyphMask(glyphId = 53, width = 0, height = 0, distanceRange = 4f, pixels = emptyList()), mask)
    }

    @Test
    fun rowPackerPlacesMasksDeterministicallyWithPaddingAndRowWraps() {
        val packer = RowGlyphAtlasPacker(atlasWidth = 8, padding = 1)

        val placements = packer.pack(
            listOf(
                a8Mask(glyphId = 10, width = 3, height = 2),
                a8Mask(glyphId = 11, width = 2, height = 4),
                a8Mask(glyphId = 12, width = 1, height = 1),
            ),
        )

        assertEquals(
            listOf(
                GlyphAtlasPlacement(glyphId = 10, x = 1, y = 1, width = 3, height = 2),
                GlyphAtlasPlacement(glyphId = 11, x = 5, y = 1, width = 2, height = 4),
                GlyphAtlasPlacement(glyphId = 12, x = 1, y = 6, width = 1, height = 1),
            ),
            placements,
        )
    }

    @Test
    fun rowPackerReportsCapacityDiagnosticWithoutPartialPlacements() {
        val packer = RowGlyphAtlasPacker(atlasWidth = 4, padding = 1)

        val result = packer.packWithDiagnostics(
            listOf(
                a8Mask(glyphId = 60, width = 2, height = 1),
                a8Mask(glyphId = 61, width = 3, height = 1),
            ),
        )

        assertEquals(emptyList(), result.placements)
        assertEquals(1, result.diagnostics.size)
        assertEquals(61, result.diagnostics.single().glyphId)
        assertEquals("text.glyph.atlas-capacity-exceeded", result.diagnostics.single().route)
        assertTrue(result.diagnostics.single().message.contains("atlas width 4"))
        assertEquals(64, result.dumpSha256.length)
        assertEquals(
            """
            {
              "schema": "org.graphiks.kanvas.glyph.GlyphAtlasPackingResult.v1",
              "placementCount": 0,
              "diagnosticCount": 1,
              "placements": [],
              "diagnostics": [
                {
                  "glyphId": 61,
                  "route": "text.glyph.atlas-capacity-exceeded",
                  "severity": "warning",
                  "message": "Glyph 61 width plus padding (5) exceeds atlas width 4; refusing atlas pack without partial placements."
                }
              ],
              "dumpSha256": "${result.dumpSha256}"
            }
            """.trimIndent() + "\n",
            result.toCanonicalGlyphAtlasPackingJson(),
        )
    }

    @Test
    fun a8AtlasBuilderComposesMaskPixelsUsingRowBytes() {
        val builder = KotlinGlyphAtlasArtifactBuilder()
        val masks = listOf(
            A8GlyphMask(
                glyphId = 20,
                width = 2,
                height = 2,
                rowBytes = 3,
                pixels = listOf(
                    1, 2, 99,
                    3, 4, 99,
                ),
            ),
            A8GlyphMask(
                glyphId = 21,
                width = 1,
                height = 3,
                pixels = listOf(5, 6, 7),
            ),
        )
        val placements = listOf(
            GlyphAtlasPlacement(glyphId = 20, x = 1, y = 0, width = 2, height = 2),
            GlyphAtlasPlacement(glyphId = 21, x = 0, y = 1, width = 1, height = 3),
        )

        val result = builder.build(masks, placements)

        assertEquals(3, result.width)
        assertEquals(4, result.height)
        assertEquals(placements, result.placements)
        assertEquals(
            listOf(
                0, 1, 2,
                5, 3, 4,
                6, 0, 0,
                7, 0, 0,
            ),
            result.pixels,
        )
    }

    @Test
    fun a8AtlasBuilderRejectsNegativeAddressableSample() {
        val builder = KotlinGlyphAtlasArtifactBuilder()

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                masks = listOf(
                    A8GlyphMask(
                        glyphId = 22,
                        width = 2,
                        height = 1,
                        rowBytes = 3,
                        pixels = listOf(0, -1, 255),
                    ),
                ),
                placements = listOf(
                    GlyphAtlasPlacement(glyphId = 22, x = 0, y = 0, width = 2, height = 1),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("glyph 22"))
        assertTrue(failure.message.orEmpty().contains("-1"))
    }

    @Test
    fun a8AtlasBuilderRejectsPixelCountWhenRowBytesTimesHeightOverflowsInt() {
        val builder = KotlinGlyphAtlasArtifactBuilder()

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                masks = listOf(
                    A8GlyphMask(
                        glyphId = 23,
                        width = 1,
                        height = 65_536,
                        rowBytes = 65_536,
                        pixels = listOf(0),
                    ),
                ),
                placements = listOf(
                    GlyphAtlasPlacement(glyphId = 23, x = 0, y = 0, width = 1, height = 65_536),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("pixel count"))
    }

    @Test
    fun a8AtlasBuilderRejectsSparsePlacementAtlasAreaOverflowBeforeCopying() {
        val builder = KotlinGlyphAtlasArtifactBuilder()

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                masks = listOf(A8GlyphMask(glyphId = 24, width = 1, height = 1, pixels = listOf(255))),
                placements = listOf(
                    GlyphAtlasPlacement(
                        glyphId = 24,
                        x = 65_535,
                        y = 65_535,
                        width = 1,
                        height = 1,
                    ),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("atlas pixel count"))
    }

    @Test
    fun sdfAtlasBuilderComposesMasksAndPreservesDistanceRange() {
        val builder = KotlinSDFGlyphAtlasArtifactBuilder(atlasWidth = 5, padding = 1)

        val result = builder.build(
            listOf(
                SDFGlyphMask(
                    glyphId = 30,
                    width = 2,
                    height = 1,
                    distanceRange = 4f,
                    pixels = listOf(8, 9),
                ),
                SDFGlyphMask(
                    glyphId = 31,
                    width = 1,
                    height = 2,
                    distanceRange = 4f,
                    pixels = listOf(6, 7),
                ),
            ),
        )

        assertEquals(3, result.width)
        assertEquals(5, result.height)
        assertEquals(4f, result.distanceRange)
        assertEquals(
            listOf(
                GlyphAtlasPlacement(glyphId = 30, x = 1, y = 1, width = 2, height = 1),
                GlyphAtlasPlacement(glyphId = 31, x = 1, y = 3, width = 1, height = 2),
            ),
            result.placements,
        )
        assertEquals(
            listOf(
                0, 0, 0,
                0, 8, 9,
                0, 0, 0,
                0, 6, 0,
                0, 7, 0,
            ),
            result.pixels,
        )
    }

    @Test
    fun sdfAtlasBuilderRejectsSampleAboveByteRange() {
        val builder = KotlinSDFGlyphAtlasArtifactBuilder()

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                listOf(
                    SDFGlyphMask(
                        glyphId = 32,
                        width = 1,
                        height = 1,
                        distanceRange = 4f,
                        pixels = listOf(256),
                    ),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("glyph 32"))
        assertTrue(failure.message.orEmpty().contains("256"))
    }

    @Test
    fun sdfAtlasBuilderRejectsPixelCountWhenWidthTimesHeightOverflowsInt() {
        val builder = KotlinSDFGlyphAtlasArtifactBuilder(atlasWidth = Int.MAX_VALUE)

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                listOf(
                    SDFGlyphMask(
                        glyphId = 33,
                        width = 65_536,
                        height = 65_536,
                        distanceRange = 4f,
                        pixels = listOf(0),
                    ),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("pixel count"))
    }

    @Test
    fun sdfAtlasBuilderRejectsAtlasAreaOverflowBeforeAllocating() {
        val builder = KotlinSDFGlyphAtlasArtifactBuilder(atlasWidth = Int.MAX_VALUE, padding = 65_535)

        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(
                listOf(
                    SDFGlyphMask(
                        glyphId = 34,
                        width = 1,
                        height = 1,
                        distanceRange = 4f,
                        pixels = listOf(128),
                    ),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("atlas pixel count"))
    }

    @Test
    fun sdfAtlasBuilderRejectsMixedDistanceRanges() {
        val builder = KotlinSDFGlyphAtlasArtifactBuilder()

        assertFailsWith<IllegalArgumentException> {
            builder.build(
                listOf(
                    SDFGlyphMask(glyphId = 40, width = 1, height = 1, distanceRange = 4f),
                    SDFGlyphMask(glyphId = 41, width = 1, height = 1, distanceRange = 8f),
                ),
            )
        }
    }

    @Test
    fun inMemoryGlyphCacheUsesStableTypefaceGlyphSizeAndVariationKeys() {
        val cache = InMemoryGlyphCache(GlyphCacheBudget(maxBytes = 16_384, maxEntries = 8))
        val typeface = typefaceId("550e8400-e29b-41d4-a716-446655441001")
        val alternateTypeface = typefaceId("550e8400-e29b-41d4-a716-446655441002")
        val strikeKey = GlyphStrikeKey(
            typefaceId = typeface,
            sizePx = 18f,
            variationCoordinates = mapOf("wght" to 0.6f, "wdth" to 0.25f),
        )
        val sameVariationDifferentOrder = strikeKey.copy(
            variationCoordinates = mapOf("wdth" to 0.25f, "wght" to 0.6f),
        )
        val representation = OutlineGlyphRepresentation(
            glyphId = 42,
            pathCommands = listOf("M 0 0", "L 4 0", "Z"),
        )

        cache.put(strikeKey, representation)

        assertEquals(representation, cache.get(sameVariationDifferentOrder, glyphId = 42))
        assertEquals(null, cache.get(strikeKey, glyphId = 43))
        assertEquals(null, cache.get(strikeKey.copy(sizePx = 19f), glyphId = 42))
        assertEquals(null, cache.get(strikeKey.copy(typefaceId = alternateTypeface), glyphId = 42))
        assertEquals(
            null,
            cache.get(
                strikeKey.copy(
                    variationCoordinates = mapOf("wght" to 0.7f, "wdth" to 0.25f),
                ),
                glyphId = 42,
            ),
        )
    }

    @Test
    fun glyphStrikeKeyCanonicalPreimageIncludesRenderingFactsInStableOrder() {
        val strikeKey = GlyphStrikeKey(
            typefaceId = typefaceId("550e8400-e29b-41d4-a716-446655441061"),
            sizePx = 18f,
            scaleX = 1.25f,
            scaleY = 0.75f,
            subpixelX = 0.5f,
            subpixelY = 0.25f,
            variationCoordinates = mapOf("wght" to 0.6f, "wdth" to 0.25f),
            representationRoute = "text.glyph.mask.SDF",
            maskFormat = "R8Unorm",
            transformBucket = "affine.scale-translate",
            edging = "antialias",
            sdfSpreadPx = 8f,
            sdfSourceResolutionPx = 18f,
            paletteIdentity = "cpal.palette.3",
            unicodeDataVersion = "Unicode-15.1.0",
            rendererDescriptorVersion = "sdf-cpu.v1",
        )

        assertEquals(
            """
            {
              "schema": "org.graphiks.kanvas.glyph.GlyphStrikeKey.v1",
              "typefaceId": "550e8400-e29b-41d4-a716-446655441061",
              "glyphId": 77,
              "clusterFingerprint": null,
              "sizePx": 18,
              "scaleX": 1.25,
              "scaleY": 0.75,
              "transformBucket": "affine.scale-translate",
              "subpixelBucket": {"x": 0.5, "y": 0.25},
              "route": "text.glyph.mask.SDF",
              "maskFormat": "R8Unorm",
              "edging": "antialias",
              "variationCoordinates": [
                {"axis": "wdth", "value": 0.25},
                {"axis": "wght", "value": 0.6}
              ],
              "paletteIdentity": "cpal.palette.3",
              "sdf": {"spreadPx": 8, "sourceResolutionPx": 18},
              "unicodeDataVersion": "Unicode-15.1.0",
              "rendererDescriptorVersion": "sdf-cpu.v1"
            }
            """.trimIndent() + "\n",
            strikeKey.canonicalPreimage(glyphId = 77),
        )
        assertEquals(64, strikeKey.preimageSha256(glyphId = 77).length)
    }

    @Test
    fun glyphStrikeKeyPreimageHashNormalizesVariationOrderAndTracksRouteFacts() {
        val strikeKey = GlyphStrikeKey(
            typefaceId = typefaceId("550e8400-e29b-41d4-a716-446655441062"),
            sizePx = 16f,
            variationCoordinates = mapOf("wght" to 0.55f, "opsz" to 14f),
            representationRoute = "text.glyph.mask.A8",
            maskFormat = "A8",
            paletteIdentity = "cpal.palette.1",
            unicodeDataVersion = "Unicode-15.1.0",
        )
        val sameFactsDifferentVariationOrder = strikeKey.copy(
            variationCoordinates = mapOf("opsz" to 14f, "wght" to 0.55f),
        )

        assertEquals(strikeKey.canonicalPreimage(glyphId = 12), sameFactsDifferentVariationOrder.canonicalPreimage(glyphId = 12))
        assertEquals(strikeKey.preimageSha256(glyphId = 12), sameFactsDifferentVariationOrder.preimageSha256(glyphId = 12))
        assertTrue(
            strikeKey.preimageSha256(glyphId = 12) != strikeKey.copy(paletteIdentity = "cpal.palette.2").preimageSha256(glyphId = 12),
        )
        assertTrue(
            strikeKey.preimageSha256(glyphId = 12) != strikeKey.copy(unicodeDataVersion = "Unicode-16.0.0").preimageSha256(glyphId = 12),
        )
        assertTrue(
            strikeKey.preimageSha256(glyphId = 12) != strikeKey.copy(representationRoute = "text.glyph.mask.SDF").preimageSha256(glyphId = 12),
        )
    }

    @Test
    fun glyphStrikeKeyPreimagePreservesDistinctFloatFacts() {
        val strikeKey = GlyphStrikeKey(
            typefaceId = typefaceId("550e8400-e29b-41d4-a716-446655441066"),
            sizePx = 16f,
            subpixelX = 0f,
            variationCoordinates = mapOf("wght" to 0f),
        )
        val distinctSubpixel = strikeKey.copy(subpixelX = 0.0000001f)
        val distinctVariation = strikeKey.copy(variationCoordinates = mapOf("wght" to 0.0000001f))

        assertTrue(strikeKey.canonicalPreimage(glyphId = 9) != distinctSubpixel.canonicalPreimage(glyphId = 9))
        assertTrue(strikeKey.preimageSha256(glyphId = 9) != distinctSubpixel.preimageSha256(glyphId = 9))
        assertTrue(strikeKey.canonicalPreimage(glyphId = 9) != distinctVariation.canonicalPreimage(glyphId = 9))
        assertTrue(strikeKey.preimageSha256(glyphId = 9) != distinctVariation.preimageSha256(glyphId = 9))
    }

    @Test
    fun inMemoryGlyphCacheSeparatesRenderingRoutePaletteAndUnicodeFacts() {
        val cache = InMemoryGlyphCache(GlyphCacheBudget(maxBytes = 16_384, maxEntries = 8))
        val strikeKey = GlyphStrikeKey(
            typefaceId = typefaceId("550e8400-e29b-41d4-a716-446655441063"),
            sizePx = 16f,
            representationRoute = "text.glyph.mask.A8",
            maskFormat = "A8",
            paletteIdentity = "cpal.palette.1",
            unicodeDataVersion = "Unicode-15.1.0",
        )
        val representation = A8GlyphMask(glyphId = 88, width = 1, height = 1, pixels = listOf(255))

        cache.put(strikeKey, representation)

        assertEquals(representation, cache.get(strikeKey, glyphId = 88))
        assertEquals(null, cache.get(strikeKey.copy(representationRoute = "text.glyph.mask.SDF"), glyphId = 88))
        assertEquals(null, cache.get(strikeKey.copy(maskFormat = "R8Unorm"), glyphId = 88))
        assertEquals(null, cache.get(strikeKey.copy(paletteIdentity = "cpal.palette.2"), glyphId = 88))
        assertEquals(null, cache.get(strikeKey.copy(unicodeDataVersion = "Unicode-16.0.0"), glyphId = 88))
    }

    @Test
    fun inMemoryGlyphCacheEvictsOldestEntriesWhenEntryBudgetIsExceeded() {
        val cache = InMemoryGlyphCache(GlyphCacheBudget(maxBytes = 16_384, maxEntries = 2))
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441011")
        val first = OutlineGlyphRepresentation(glyphId = 1, pathCommands = listOf("M 0 0"))
        val second = A8GlyphMask(glyphId = 2, width = 1, height = 1, pixels = listOf(2))
        val third = SDFGlyphMask(glyphId = 3, width = 1, height = 1, distanceRange = 4f, pixels = listOf(3))

        cache.put(strikeKey, first)
        cache.put(strikeKey, second)
        cache.put(strikeKey, third)

        assertEquals(null, cache.get(strikeKey, glyphId = 1))
        assertEquals(second, cache.get(strikeKey, glyphId = 2))
        assertEquals(third, cache.get(strikeKey, glyphId = 3))
    }

    @Test
    fun routeDiagnosticRecordsStaleAtlasGenerationRefusal() {
        val diagnostic = GlyphRouteDiagnostic.atlasGenerationStale(
            glyphId = 91,
            artifactGeneration = 3,
            currentGeneration = 4,
            invalidationToken = "font-source-v2",
        )

        assertEquals(91, diagnostic.glyphId)
        assertEquals("text.glyph.atlas-generation-stale", diagnostic.route)
        assertEquals("warning", diagnostic.severity)
        assertTrue(diagnostic.message.contains("artifactGeneration=3"))
        assertTrue(diagnostic.message.contains("currentGeneration=4"))
        assertTrue(diagnostic.message.contains("invalidationToken=font-source-v2"))
        assertEquals(64, diagnostic.dumpSha256.length)
        assertEquals(
            """
            {
              "glyphId": 91,
              "route": "text.glyph.atlas-generation-stale",
              "severity": "warning",
              "message": "Glyph atlas generation is stale for glyph 91: artifactGeneration=3, currentGeneration=4, invalidationToken=font-source-v2."
            }
            """.trimIndent(),
            diagnostic.toCanonicalJson(),
        )
    }

    @Test
    fun routeDiagnosticRecordsSDFTransformUnsupportedRefusal() {
        val diagnostic = GlyphRouteDiagnostic.sdfTransformUnsupported(
            glyphId = 92,
            transformBucket = "perspective",
            fallbackRoute = "text.glyph.mask.A8",
        )

        assertEquals(92, diagnostic.glyphId)
        assertEquals("text.glyph.SDF-transform-unsupported", diagnostic.route)
        assertEquals("warning", diagnostic.severity)
        assertTrue(diagnostic.message.contains("transformBucket=perspective"))
        assertTrue(diagnostic.message.contains("fallbackRoute=text.glyph.mask.A8"))
        assertEquals(
            """
            {
              "glyphId": 92,
              "route": "text.glyph.SDF-transform-unsupported",
              "severity": "warning",
              "message": "SDF transform is unsupported for glyph 92: transformBucket=perspective, fallbackRoute=text.glyph.mask.A8."
            }
            """.trimIndent(),
            diagnostic.toCanonicalJson(),
        )
    }

    @Test
    fun routePlannerSelectsPreferredAvailableRepresentationForEachGlyph() {
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441021")
        val outline = OutlineGlyphRepresentation(glyphId = 10, pathCommands = listOf("M 0 0"))
        val fallbackOutline = OutlineGlyphRepresentation(glyphId = 11, pathCommands = listOf("M 1 1"))
        val a8 = A8GlyphMask(glyphId = 11, width = 1, height = 1, pixels = listOf(64))
        val fallbackA8 = A8GlyphMask(glyphId = 12, width = 1, height = 1, pixels = listOf(128))
        val sdf = SDFGlyphMask(glyphId = 12, width = 1, height = 1, distanceRange = 8f, pixels = listOf(192))
        val cache = InMemoryGlyphCache(GlyphCacheBudget(maxBytes = 16_384, maxEntries = 8))
        val planner = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.SDF, GlyphArtifactRoute.A8, GlyphArtifactRoute.OUTLINE),
                availableRepresentations = mapOf(
                    10 to listOf(outline),
                    11 to listOf(fallbackOutline, a8),
                    12 to listOf(fallbackA8, sdf),
                ),
            ),
            cache = cache,
        )

        val plan = planner.plan(
            run = glyphRun(glyphIds = listOf(10, 11, 12)),
            strikeKey = strikeKey,
        )

        assertEquals(listOf(outline, a8, sdf), plan.representations)
        assertEquals(emptyList(), plan.diagnostics)
        assertEquals(outline, cache.get(strikeKey, glyphId = 10))
        assertEquals(a8, cache.get(strikeKey, glyphId = 11))
        assertEquals(sdf, cache.get(strikeKey, glyphId = 12))
    }

    @Test
    fun routePlannerPrefersCachedRepresentationWhenItMatchesEarlierRoute() {
        val strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441022")
        val outline = OutlineGlyphRepresentation(glyphId = 13, pathCommands = listOf("M 0 0"))
        val sdf = SDFGlyphMask(glyphId = 13, width = 1, height = 1, distanceRange = 8f, pixels = listOf(192))
        val cache = InMemoryGlyphCache(GlyphCacheBudget(maxBytes = 16_384, maxEntries = 8))
        cache.put(strikeKey, sdf)
        val planner = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.SDF, GlyphArtifactRoute.OUTLINE),
                availableRepresentations = mapOf(13 to listOf(outline)),
            ),
            cache = cache,
        )

        val plan = planner.plan(
            run = glyphRun(glyphIds = listOf(13)),
            strikeKey = strikeKey,
        )

        assertEquals(listOf(sdf), plan.representations)
        assertEquals(emptyList(), plan.diagnostics)
    }

    @Test
    fun routePlannerRecordsDiagnosticsWhenRequestedRouteIsImpossible() {
        val outline = OutlineGlyphRepresentation(glyphId = 20, pathCommands = listOf("M 0 0"))
        val planner = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.SDF),
                availableRepresentations = mapOf(20 to listOf(outline)),
            ),
        )

        val plan = planner.plan(
            run = glyphRun(glyphIds = listOf(20, 21)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441031"),
        )

        assertEquals(emptyList(), plan.representations)
        assertEquals(listOf(20, 21), plan.diagnostics.map { it.glyphId })
        assertEquals(listOf("sdf", "sdf"), plan.diagnostics.map { it.route })
        assertTrue(plan.diagnostics.all { diagnostic -> diagnostic.severity == "warning" })
        assertTrue(plan.diagnostics.all { diagnostic -> diagnostic.message.contains("requested") })
    }

    @Test
    fun glyphArtifactPlanRecordsDecisionTraceAndCanonicalDump() {
        val outline = OutlineGlyphRepresentation(glyphId = 30, pathCommands = listOf("M 0 0"))
        val a8 = A8GlyphMask(glyphId = 31, width = 1, height = 1, pixels = listOf(64))
        val sdf = SDFGlyphMask(glyphId = 32, width = 1, height = 1, distanceRange = 8f, pixels = listOf(192))
        val planner = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.SDF, GlyphArtifactRoute.A8, GlyphArtifactRoute.OUTLINE),
                availableRepresentations = mapOf(
                    30 to listOf(outline),
                    31 to listOf(outline.copy(glyphId = 31), a8),
                    32 to listOf(a8.copy(glyphId = 32), sdf),
                ),
            ),
        )

        val plan = planner.plan(
            run = glyphRun(glyphIds = listOf(30, 31, 32, 33)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441071"),
        )

        assertEquals(listOf(outline, a8, sdf), plan.representations)
        assertEquals(listOf(0, 1, 2, 3), plan.decisions.map { decision -> decision.index })
        assertEquals(listOf(30, 31, 32, 33), plan.decisions.map { decision -> decision.glyphId })
        assertEquals(
            listOf(
                "text.glyph.outline",
                "text.glyph.mask.A8",
                "text.glyph.mask.SDF",
                "text.glyph.unsupported",
            ),
            plan.decisions.map { decision -> decision.selectedRoute },
        )
        assertEquals(
            listOf(
                "fallback-selected-after-rejections",
                "fallback-selected-after-rejections",
                "selected-first-requested-route",
                "refuse-no-requested-representation",
            ),
            plan.decisions.map { decision -> decision.fallbackPolicy },
        )
        assertEquals(1, plan.diagnostics.size)
        assertTrue(plan.decisions.all { decision -> decision.keySha256.length == 64 })
        assertEquals(64, plan.decisions[0].sourceRepresentationSha256?.length)
        assertEquals(64, plan.decisions[1].sourceRepresentationSha256?.length)
        assertEquals(64, plan.decisions[2].sourceRepresentationSha256?.length)
        assertEquals(null, plan.decisions[3].sourceRepresentationSha256)
        assertEquals(plan.diagnostics.single(), plan.decisions.last().diagnostic)
        assertEquals(
            listOf("text.glyph.mask.SDF", "text.glyph.mask.A8"),
            plan.decisions.first().rejectedAlternatives.map { rejection -> rejection.route },
        )
        assertEquals(64, plan.dumpSha256.length)
        assertEquals(
            """
            {
              "schema": "org.graphiks.kanvas.glyph.GlyphArtifactPlan.v1",
              "runId": "550e8400-e29b-41d4-a716-446655441101",
              "glyphCount": 4,
              "representationCount": 3,
              "diagnosticCount": 1,
              "decisions": [
                {
                  "index": 0,
                  "glyphId": 30,
                  "selectedRoute": "text.glyph.outline",
                  "representation": "outline",
                  "source": "request",
                  "keySha256": "${plan.decisions[0].keySha256}",
                  "sourceRepresentationSha256": "${plan.decisions[0].sourceRepresentationSha256}",
                  "fallbackPolicy": "fallback-selected-after-rejections",
                  "rejectedAlternatives": [
                    {"route": "text.glyph.mask.SDF", "reason": "route-unavailable"},
                    {"route": "text.glyph.mask.A8", "reason": "route-unavailable"}
                  ],
                  "diagnostic": null
                },
                {
                  "index": 1,
                  "glyphId": 31,
                  "selectedRoute": "text.glyph.mask.A8",
                  "representation": "a8",
                  "source": "request",
                  "keySha256": "${plan.decisions[1].keySha256}",
                  "sourceRepresentationSha256": "${plan.decisions[1].sourceRepresentationSha256}",
                  "fallbackPolicy": "fallback-selected-after-rejections",
                  "rejectedAlternatives": [
                    {"route": "text.glyph.mask.SDF", "reason": "route-unavailable"}
                  ],
                  "diagnostic": null
                },
                {
                  "index": 2,
                  "glyphId": 32,
                  "selectedRoute": "text.glyph.mask.SDF",
                  "representation": "sdf",
                  "source": "request",
                  "keySha256": "${plan.decisions[2].keySha256}",
                  "sourceRepresentationSha256": "${plan.decisions[2].sourceRepresentationSha256}",
                  "fallbackPolicy": "selected-first-requested-route",
                  "rejectedAlternatives": [],
                  "diagnostic": null
                },
                {
                  "index": 3,
                  "glyphId": 33,
                  "selectedRoute": "text.glyph.unsupported",
                  "representation": null,
                  "source": null,
                  "keySha256": "${plan.decisions[3].keySha256}",
                  "sourceRepresentationSha256": null,
                  "fallbackPolicy": "refuse-no-requested-representation",
                  "rejectedAlternatives": [
                    {"route": "text.glyph.mask.SDF", "reason": "route-unavailable"},
                    {"route": "text.glyph.mask.A8", "reason": "route-unavailable"},
                    {"route": "text.glyph.outline", "reason": "route-unavailable"}
                  ],
                  "diagnostic": {
                    "glyphId": 33,
                    "route": "sdf|a8|outline",
                    "severity": "warning",
                    "message": "No requested glyph representation is available for glyph 33; requested sdf|a8|outline, available none."
                  }
                }
              ],
              "diagnostics": [
                {
                  "glyphId": 33,
                  "route": "sdf|a8|outline",
                  "severity": "warning",
                  "message": "No requested glyph representation is available for glyph 33; requested sdf|a8|outline, available none."
                }
              ],
              "dumpSha256": "${plan.dumpSha256}"
            }
            """.trimIndent() + "\n",
            plan.toCanonicalGlyphArtifactPlanJson(),
        )
    }

    @Test
    fun glyphArtifactPlanSupportsColorBitmapAndSvgPlaceholderRoutes() {
        val outlineFallback = OutlineGlyphRepresentation(glyphId = 40, pathCommands = listOf("M 0 0"))
        val colorRef = ColorGlyphPlanRef(glyphId = 41, planId = "color-plan-41")
        val bitmapRef = BitmapGlyphPlanRef(glyphId = 42, planId = "bitmap-plan-42")
        val svgRef = SVGGlyphPlanRef(glyphId = 43, planId = "svg-plan-43")

        val planner = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(
                    GlyphArtifactRoute.COLOR,
                    GlyphArtifactRoute.BITMAP,
                    GlyphArtifactRoute.SVG,
                    GlyphArtifactRoute.OUTLINE,
                ),
                availableRepresentations = mapOf(
                    40 to listOf(outlineFallback),
                    41 to listOf(colorRef),
                    42 to listOf(bitmapRef),
                    43 to listOf(svgRef),
                ),
            ),
        )

        val plan = planner.plan(
            run = glyphRun(glyphIds = listOf(40, 41, 42, 43, 44)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441072"),
        )

        assertEquals(
            listOf(
                "text.glyph.outline",
                "text.glyph.color.COLR",
                "text.glyph.bitmap.PNG",
                "text.glyph.SVG",
                "text.glyph.unsupported",
            ),
            plan.decisions.map { decision -> decision.selectedRoute },
        )
        assertEquals(
            listOf("ColorGlyphPlan", "BitmapGlyphPlan", "SVGGlyphPlan"),
            plan.decisions.mapNotNull { decision -> decision.planRef?.artifactName },
        )
        assertEquals(
            listOf("color-plan-41", "bitmap-plan-42", "svg-plan-43"),
            plan.decisions.mapNotNull { decision -> decision.planRef?.planId },
        )
        assertEquals(
            listOf(
                "fallback-selected-after-rejections",
                "selected-first-requested-route",
                "fallback-selected-after-rejections",
                "fallback-selected-after-rejections",
                "refuse-no-requested-representation",
            ),
            plan.decisions.map { decision -> decision.fallbackPolicy },
        )

        val dump = plan.toCanonicalGlyphArtifactPlanJson()
        assertTrue(dump.contains(""""artifactName": "ColorGlyphPlan""""))
        assertTrue(dump.contains(""""artifactName": "BitmapGlyphPlan""""))
        assertTrue(dump.contains(""""artifactName": "SVGGlyphPlan""""))
        assertTrue(dump.contains(""""selectedRoute": "text.glyph.color.COLR""""))
        assertTrue(dump.contains(""""selectedRoute": "text.glyph.bitmap.PNG""""))
        assertTrue(dump.contains(""""selectedRoute": "text.glyph.SVG""""))
        assertEquals(plan.diagnostics.single(), plan.decisions.last().diagnostic)
    }

    @Test
    fun glyphArtifactPlanRecordsPolicyInputsIntentAndExplicitRouteDiagnostics() {
        val outlineFallback = OutlineGlyphRepresentation(glyphId = 60, pathCommands = listOf("M 0 0"))
        val a8 = a8Mask(glyphId = 61, width = 1, height = 1)
        val sdf = SDFGlyphMask(glyphId = 63, width = 1, height = 1, distanceRange = 8f, pixels = listOf(192))

        val request = GlyphArtifactRouteRequest(
            preferredRoutes = listOf(
                GlyphArtifactRoute.SDF,
                GlyphArtifactRoute.A8,
                GlyphArtifactRoute.OUTLINE,
            ),
            policyInputs = GlyphArtifactRoutePolicyInputs(
                textStylePreference = "body",
                transformClass = "perspective",
                atlasBudgetClass = "tight",
                sdfEligibility = "mixed",
                colorGlyphAvailability = "placeholders-only",
                emojiSequenceFacts = "none",
                rendererCapabilitySummary = "cpu-only",
            ),
            routeDiagnostics = mapOf(
                60 to listOf(
                    GlyphRouteDiagnostic.sdfTransformUnsupported(
                        glyphId = 60,
                        transformBucket = "perspective",
                        fallbackRoute = "text.glyph.outline",
                    ),
                ),
                62 to listOf(
                    GlyphRouteDiagnostic(
                        glyphId = 62,
                        route = "text.glyph.atlas-capacity-exceeded",
                        message = "Atlas budget exceeded for glyph 62.",
                        severity = "warning",
                    ),
                ),
            ),
            availableRepresentations = mapOf(
                60 to listOf(outlineFallback),
                61 to listOf(a8),
                62 to listOf(outlineFallback.copy(glyphId = 62)),
                63 to listOf(sdf),
            ),
        )

        val plan = GlyphArtifactRoutePlanner(request).plan(
            run = glyphRun(glyphIds = listOf(60, 61, 62, 63)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441073"),
        )

        assertEquals(request.policyInputs, plan.policyInputs)
        assertEquals("text.glyph.SDF-transform-unsupported", plan.decisions[0].rejectedAlternatives.first().reason)
        assertEquals("CPUPreparedGPU", plan.decisions[1].artifactIntent)
        assertEquals(64, plan.decisions[1].sourceRepresentationSha256?.length)
        assertEquals("text.glyph.atlas-capacity-exceeded", plan.decisions[2].rejectedAlternatives[1].reason)
        assertEquals("CPUPreparedGPU", plan.decisions[3].artifactIntent)
        assertEquals(64, plan.decisions[3].sourceRepresentationSha256?.length)

        val dump = plan.toCanonicalGlyphArtifactPlanJson()
        assertTrue(dump.contains(""""artifactIntent": "CPUPreparedGPU""""))
        assertTrue(dump.contains(""""sourceRepresentationSha256": """"))
        assertTrue(dump.contains(""""transformClass": "perspective""""))
        assertTrue(dump.contains(""""rendererCapabilitySummary": "cpu-only""""))
    }

    @Test
    fun glyphArtifactPlanSupportsExplicitLcdAndOutlineRefusalDiagnostics() {
        val lcdPlan = GlyphArtifactRoutePlanner(
            GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.LCD),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(70)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441074"),
        )
        assertEquals("text.glyph.LCD-future-research", lcdPlan.diagnostics.single().route)
        assertEquals(lcdPlan.diagnostics.single(), lcdPlan.decisions.single().diagnostic)

        val outlinePlan = GlyphArtifactRoutePlanner(
            GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.OUTLINE),
                routeDiagnostics = mapOf(
                    71 to listOf(GlyphRouteDiagnostic.outlineUnavailable(glyphId = 71)),
                ),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(71)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441075"),
        )
        assertEquals("text.glyph.outline-unavailable", outlinePlan.diagnostics.single().route)
        assertEquals(outlinePlan.diagnostics.single(), outlinePlan.decisions.single().diagnostic)
    }

    @Test
    fun glyphArtifactPlanEvidenceDumpMatchesRepoFixture() {
        val placeholderPlan = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(
                    GlyphArtifactRoute.COLOR,
                    GlyphArtifactRoute.BITMAP,
                    GlyphArtifactRoute.SVG,
                    GlyphArtifactRoute.OUTLINE,
                ),
                availableRepresentations = mapOf(
                    40 to listOf(OutlineGlyphRepresentation(glyphId = 40, pathCommands = listOf("M 0 0"))),
                    41 to listOf(ColorGlyphPlanRef(glyphId = 41, planId = "color-plan-41")),
                    42 to listOf(BitmapGlyphPlanRef(glyphId = 42, planId = "bitmap-plan-42")),
                    43 to listOf(SVGGlyphPlanRef(glyphId = 43, planId = "svg-plan-43")),
                ),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(40, 41, 42, 43, 44)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441076"),
        )

        val policyPlan = GlyphArtifactRoutePlanner(
            request = GlyphArtifactRouteRequest(
                preferredRoutes = listOf(
                    GlyphArtifactRoute.SDF,
                    GlyphArtifactRoute.A8,
                    GlyphArtifactRoute.OUTLINE,
                ),
                policyInputs = GlyphArtifactRoutePolicyInputs(
                    textStylePreference = "body",
                    transformClass = "perspective",
                    atlasBudgetClass = "tight",
                    sdfEligibility = "mixed",
                    colorGlyphAvailability = "placeholders-only",
                    emojiSequenceFacts = "none",
                    rendererCapabilitySummary = "cpu-only",
                ),
                routeDiagnostics = mapOf(
                    60 to listOf(GlyphRouteDiagnostic.sdfTransformUnsupported(60, "perspective", "text.glyph.outline")),
                    62 to listOf(
                        GlyphRouteDiagnostic(
                            glyphId = 62,
                            route = "text.glyph.atlas-capacity-exceeded",
                            message = "Atlas budget exceeded for glyph 62.",
                            severity = "warning",
                        ),
                    ),
                ),
                availableRepresentations = mapOf(
                    60 to listOf(OutlineGlyphRepresentation(glyphId = 60, pathCommands = listOf("M 0 0"))),
                    61 to listOf(a8Mask(glyphId = 61, width = 1, height = 1)),
                    62 to listOf(OutlineGlyphRepresentation(glyphId = 62, pathCommands = listOf("M 0 0"))),
                    63 to listOf(SDFGlyphMask(glyphId = 63, width = 1, height = 1, distanceRange = 8f, pixels = listOf(192))),
                ),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(60, 61, 62, 63)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441077"),
        )

        val lcdPlan = GlyphArtifactRoutePlanner(
            GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.LCD),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(70)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441078"),
        )

        val outlinePlan = GlyphArtifactRoutePlanner(
            GlyphArtifactRouteRequest(
                preferredRoutes = listOf(GlyphArtifactRoute.OUTLINE),
                routeDiagnostics = mapOf(
                    71 to listOf(GlyphRouteDiagnostic.outlineUnavailable(glyphId = 71)),
                ),
            ),
        ).plan(
            run = glyphRun(glyphIds = listOf(71)),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441079"),
        )

        val dump = GlyphArtifactPlanEvidenceDump(
            dumpId = "glyph-artifact-plan",
            ownerTickets = listOf("KFONT-M9-002"),
            fixtureIds = listOf(
                "glyph-artifact-plan-placeholders",
                "glyph-artifact-plan-policy-refusals",
                "glyph-artifact-plan-lcd-refusal",
                "glyph-artifact-plan-outline-refusal",
            ),
            plans = listOf(placeholderPlan, policyPlan, lcdPlan, outlinePlan),
            requiredDiagnostics = listOf(
                "text.glyph.LCD-future-research",
                "text.glyph.SDF-transform-unsupported",
                "text.glyph.atlas-capacity-exceeded",
                "text.glyph.outline-unavailable",
            ),
            nonClaims = listOf(
                "producer-only",
                "no-complete-color-bitmap-svg-plan-claim",
                "no-gpu-text-route-claim",
            ),
        )

        assertEquals(
            readProjectFile("reports/font/fixtures/expected/glyph/glyph-artifact-plan.json"),
            dump.toCanonicalJson(),
        )
    }

    @Test
    fun a8GlyphMaskEvidenceDumpMatchesRepoFixture() {
        val generator = object : GlyphMaskGenerator {}

        val quadratic = generator.generate(
            outline = OutlineGlyphRepresentation(
                glyphId = 54,
                pathCommands = listOf(
                    "M 1 2",
                    "Q 2.5 2 4 2",
                    "Q 4 3.5 4 5",
                    "Q 2.5 5 1 5",
                    "Q 1 3.5 1 2",
                    "Z",
                ),
            ),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441084"),
        )
        val cubic = generator.generate(
            outline = OutlineGlyphRepresentation(
                glyphId = 55,
                pathCommands = listOf(
                    "M 1 2",
                    "C 2 2 3 2 4 2",
                    "C 4 3 4 4 4 5",
                    "C 3 5 2 5 1 5",
                    "C 1 4 1 3 1 2",
                    "Z",
                ),
            ),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441085"),
        )
        val composite = generator.generate(
            outline = OutlineGlyphRepresentation(
                glyphId = 0,
                pathCommands = listOf(
                    "M 50 50",
                    "L 150 50",
                    "L 150 150",
                    "L 50 150",
                    "Z",
                    "M 150 50",
                    "L 250 50",
                    "L 250 150",
                    "L 150 150",
                    "Z",
                ),
            ),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441086"),
        )
        val notdefEmpty = generator.generate(
            outline = OutlineGlyphRepresentation(glyphId = 0),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441087"),
        )
        val malformed = generator.generate(
            outline = OutlineGlyphRepresentation(glyphId = 57, pathCommands = listOf("L 1 2")),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441088"),
        )
        val unsupportedFill = generator.generate(
            outline = OutlineGlyphRepresentation(
                glyphId = 56,
                pathCommands = listOf(
                    "M 1 2",
                    "L 4 2",
                    "L 4 5",
                    "L 1 5",
                    "Z",
                ),
                windingRule = "evenOdd",
            ),
            strikeKey = strikeKey(typefaceUuid = "550e8400-e29b-41d4-a716-446655441089"),
        )

        val dump = A8GlyphMaskEvidenceDump(
            dumpId = "a8-glyph-mask",
            ownerTickets = listOf("KFONT-M9-003"),
            fixtureIds = listOf(
                "a8-quadratic-simple",
                "a8-cubic-simple",
                "a8-composite-outline",
                "a8-empty-notdef",
                "a8-malformed-contour",
                "a8-unsupported-fill-rule",
            ),
            masks = listOf(
                A8GlyphMaskArtifactEvidence.from(quadratic, strikeKey("550e8400-e29b-41d4-a716-446655441084")),
                A8GlyphMaskArtifactEvidence.from(cubic, strikeKey("550e8400-e29b-41d4-a716-446655441085")),
                A8GlyphMaskArtifactEvidence.from(composite, strikeKey("550e8400-e29b-41d4-a716-446655441086")),
                A8GlyphMaskArtifactEvidence.from(notdefEmpty, strikeKey("550e8400-e29b-41d4-a716-446655441087")),
                A8GlyphMaskArtifactEvidence.from(malformed, strikeKey("550e8400-e29b-41d4-a716-446655441088")),
                A8GlyphMaskArtifactEvidence.from(unsupportedFill, strikeKey("550e8400-e29b-41d4-a716-446655441089")),
            ),
            requiredDiagnostics = listOf("text.glyph.A8-generation-failed"),
            nonClaims = listOf(
                "producer-only",
                "no-gpu-text-route-claim",
                "no-lcd-support-claim",
                "no-sdf-support-claim",
                "no-external-rasterizer-parity-claim",
            ),
        )

        assertEquals(
            readProjectFile("reports/font/fixtures/expected/glyph/a8-glyph-mask.json"),
            dump.toCanonicalJson(),
        )
    }
    
    @Test
    fun routeDiagnosticsHaveCanonicalDumpAndStableHash() {
        val diagnostics = listOf(
            GlyphRouteDiagnostic(
                glyphId = 20,
                route = "text.glyph.mask.SDF",
                severity = "warning",
                message = "No \"SDF\" route\navailable.",
            ),
            GlyphRouteDiagnostic(
                glyphId = null,
                route = "text.glyph.unsupported",
                severity = "info",
                message = "LCD is future research.",
            ),
        )

        assertEquals(
            """
            [
              {
                "glyphId": 20,
                "route": "text.glyph.mask.SDF",
                "severity": "warning",
                "message": "No \"SDF\" route\navailable."
              },
              {
                "glyphId": null,
                "route": "text.glyph.unsupported",
                "severity": "info",
                "message": "LCD is future research."
              }
            ]
            """.trimIndent() + "\n",
            diagnostics.toCanonicalGlyphRouteDiagnosticsJson(),
        )
        assertEquals(64, diagnostics.glyphRouteDiagnosticsSha256().length)
        assertEquals(
            diagnostics.glyphRouteDiagnosticsSha256(),
            diagnostics.toList().glyphRouteDiagnosticsSha256(),
        )
    }

    @Test
    fun glyphRunCacheInventoryBuildsStableRecordsJsonAndHash() {
        val missingDiagnostic = "font.missing-glyph.notdef-used"
        val alphaMask = A8GlyphMask(
            glyphId = 42,
            width = 2,
            height = 2,
            left = -1,
            top = 3,
            rowBytes = 3,
            pixels = listOf(
                0, 7, 99,
                255, 0, 99,
            ),
        )
        val emptyMask = GlyphMaskSummary.Empty

        val inventory = GlyphRunCacheInventory.build(
            runId = "run.simple-latin.v1",
            representation = GlyphRunCacheInventory.AlphaMaskRepresentation,
            glyphs = listOf(
                GlyphRunCacheBuildGlyph(
                    index = 0,
                    codePoint = 'A'.code,
                    glyphId = 42,
                    key = "scope|font|size=16|glyph=42",
                    advance = 9.5f,
                    x = 0f,
                    maskSummary = GlyphMaskSummary.fromA8Mask(alphaMask),
                ),
                GlyphRunCacheBuildGlyph(
                    index = 1,
                    codePoint = 'A'.code,
                    glyphId = 42,
                    key = "scope|font|size=16|glyph=42",
                    advance = 9.5f,
                    x = 9.5f,
                    maskSummary = GlyphMaskSummary.fromA8Mask(alphaMask),
                ),
                GlyphRunCacheBuildGlyph(
                    index = 2,
                    codePoint = 0xE000,
                    glyphId = 0,
                    key = "scope|font|size=16|glyph=0",
                    advance = 8f,
                    x = 19f,
                    maskSummary = emptyMask,
                    diagnostic = missingDiagnostic,
                ),
            ),
        )

        assertEquals(3, inventory.items.size)
        assertEquals(2, inventory.records.size)
        assertEquals(listOf(missingDiagnostic), inventory.diagnostics)
        assertEquals(listOf('A'.code), inventory.records[0].codePoints)
        assertEquals(listOf(0xE000), inventory.records[1].codePoints)
        assertEquals(2, inventory.records[0].maskSummary.nonZeroPixels)
        assertEquals("79ef38e8384dc02cd1de6202a09cab298e2bac8148fff30a3c437f87e63956eb", inventory.records[0].maskSummary.sha256)
        assertEquals(64, inventory.dumpSha256.length)
        assertEquals(inventory.dumpSha256, GlyphRunCacheInventory.build(
            runId = "run.simple-latin.v1",
            representation = GlyphRunCacheInventory.AlphaMaskRepresentation,
            glyphs = listOf(
                GlyphRunCacheBuildGlyph(
                    index = 0,
                    codePoint = 'A'.code,
                    glyphId = 42,
                    key = "scope|font|size=16|glyph=42",
                    advance = 9.5f,
                    x = 0f,
                    maskSummary = GlyphMaskSummary.fromA8Mask(alphaMask),
                ),
                GlyphRunCacheBuildGlyph(
                    index = 1,
                    codePoint = 'A'.code,
                    glyphId = 42,
                    key = "scope|font|size=16|glyph=42",
                    advance = 9.5f,
                    x = 9.5f,
                    maskSummary = GlyphMaskSummary.fromA8Mask(alphaMask),
                ),
                GlyphRunCacheBuildGlyph(
                    index = 2,
                    codePoint = 0xE000,
                    glyphId = 0,
                    key = "scope|font|size=16|glyph=0",
                    advance = 8f,
                    x = 19f,
                    maskSummary = emptyMask,
                    diagnostic = missingDiagnostic,
                ),
            ),
        ).dumpSha256)
        assertEquals(
            """
            {
              "runId": "run.simple-latin.v1",
              "representation": "font.glyph.alpha-mask",
              "itemCount": 3,
              "recordCount": 2,
              "diagnostics": ["font.missing-glyph.notdef-used"],
              "items": [
                {
                  "index": 0,
                  "codePoint": "U+0041",
                  "codePointValue": 65,
                  "glyphId": 42,
                  "key": "scope|font|size=16|glyph=42",
                  "advance": 9.5,
                  "x": 0,
                  "diagnostic": null
                },
                {
                  "index": 1,
                  "codePoint": "U+0041",
                  "codePointValue": 65,
                  "glyphId": 42,
                  "key": "scope|font|size=16|glyph=42",
                  "advance": 9.5,
                  "x": 9.5,
                  "diagnostic": null
                },
                {
                  "index": 2,
                  "codePoint": "U+E000",
                  "codePointValue": 57344,
                  "glyphId": 0,
                  "key": "scope|font|size=16|glyph=0",
                  "advance": 8,
                  "x": 19,
                  "diagnostic": "font.missing-glyph.notdef-used"
                }
              ],
              "records": [
                {
                  "key": "scope|font|size=16|glyph=42",
                  "glyphId": 42,
                  "codePoints": ["U+0041"],
                  "advance": 9.5,
                  "maskSummary": {"left": -1, "top": 3, "width": 2, "height": 2, "rowBytes": 3, "nonZeroPixels": 2, "sha256": "79ef38e8384dc02cd1de6202a09cab298e2bac8148fff30a3c437f87e63956eb"},
                  "diagnostic": null
                },
                {
                  "key": "scope|font|size=16|glyph=0",
                  "glyphId": 0,
                  "codePoints": ["U+E000"],
                  "advance": 8,
                  "maskSummary": {"left": 0, "top": 0, "width": 0, "height": 0, "rowBytes": 0, "nonZeroPixels": 0, "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"},
                  "diagnostic": "font.missing-glyph.notdef-used"
                }
              ],
              "dumpSha256": "${inventory.dumpSha256}"
            }
            """.trimIndent() + "\n",
            inventory.toCanonicalJson(),
        )
    }

    @Test
    fun a8SdfAtlasLifecycleGoldenRecordsFixtureDiagnosticsAndNonClaims() {
        val dump = readProjectFile("reports/font/fixtures/expected/glyph/a8-sdf-atlas-lifecycle.json")

        assertEquals("a8-sdf-atlas-lifecycle", jsonStringField(dump, "dumpId"))
        assertEquals(listOf("PKT-10D"), jsonStringArrayField(dump, "ownerTickets"))
        assertEquals(listOf("font-source-liberation-core"), jsonStringArrayField(dump, "fixtureIds"))
        assertEquals(
            listOf(
                "text.glyph.SDF-transform-unsupported",
                "text.glyph.SDF-generation-failed",
                "text.glyph.atlas-capacity-exceeded",
                "text.glyph.atlas-generation-stale",
                "text.glyph.cache-key-nondeterministic",
                "text.glyph.artifact-budget-exceeded",
            ),
            jsonStringArrayField(dump, "requiredDiagnostics"),
        )
        assertEquals(
            listOf(
                "no-complete-target-support-claim",
                "no-complete-a8-atlas-claim",
                "no-complete-sdf-production-claim",
                "no-complete-atlas-lifecycle-claim",
                "no-gpu-text-route-claim",
                "no-gpu-upload-execution-claim",
                "no-renderer-resource-ownership-claim",
            ),
            jsonStringArrayField(dump, "nonClaims"),
        )
    }

    private fun a8Mask(glyphId: Int, width: Int, height: Int): A8GlyphMask =
        A8GlyphMask(
            glyphId = glyphId,
            width = width,
            height = height,
            pixels = List(width * height) { glyphId },
        )

    private fun strikeKey(typefaceUuid: String): GlyphStrikeKey =
        GlyphStrikeKey(
            typefaceId = typefaceId(typefaceUuid),
            sizePx = 16f,
        )

    private fun glyphRun(glyphIds: List<Int>): GPUGlyphRunDescriptor =
        GPUGlyphRunDescriptor(
            runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
            glyphIDs = glyphIds,
        )

    private fun typefaceId(uuid: String): TypefaceID =
        TypefaceID(Uuid.parse(uuid))

    private fun readProjectFile(relativePath: String): String =
        Files.readString(kanvasProjectRoot().resolve(relativePath))

    private fun kanvasProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null) {
            if (Files.isDirectory(current.resolve("reports/font/fixtures"))) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate Kanvas project root from ${Path.of("").toAbsolutePath()}")
    }

    private fun jsonStringField(json: String, field: String): String {
        val pattern = Regex("\"" + Regex.escape(field) + "\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
            ?: error("Missing JSON string field $field")
    }

    private fun jsonStringArrayField(json: String, field: String): List<String> {
        val pattern = Regex(
            "\"" + Regex.escape(field) + "\"\\s*:\\s*\\[(.*?)\\]",
            RegexOption.DOT_MATCHES_ALL,
        )
        val body = pattern.find(json)?.groupValues?.get(1)
            ?: error("Missing JSON array field $field")
        return Regex("\"([^\"]+)\"").findAll(body).map { match -> match.groupValues[1] }.toList()
    }
}
