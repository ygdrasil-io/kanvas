package org.graphiks.kanvas.glyph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
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

        assertEquals(A8GlyphMask(glyphId = 51, width = 0, height = 0, pixels = emptyList()), mask)
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
}
