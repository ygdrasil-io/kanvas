package org.graphiks.kanvas.glyph.color

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.glyph.OutlineGlyphRepresentation
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunID
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Verifies that the pure Kotlin color glyph package exposes the planned public surface.
 */
class ColorGlyphSurfaceTest {
    /**
     * References every color glyph planning type so the module fails to compile until the
     * surface exists.
     */
    @Test
    fun exposesColorGlyphPipelineSurface() {
        val names = listOf(
            ColorGlyphPlanner::class.simpleName,
            COLRGlyphPlanner::class.simpleName,
            COLRPaintGraph::class.simpleName,
            COLRPaintNode::class.simpleName,
            CPALPalette::class.simpleName,
            CPALPaletteSelection::class.simpleName,
            CPALPaletteOverride::class.simpleName,
            BitmapStrikeSelector::class.simpleName,
            PNGGlyphDecoder::class.simpleName,
            SVGGlyphRenderer::class.simpleName,
            SVGGlyphParser::class.simpleName,
            EmojiGlyphDispatcher::class.simpleName,
            ColorGlyphDiagnostic::class.simpleName,
        )

        assertEquals(
            listOf(
                "ColorGlyphPlanner",
                "COLRGlyphPlanner",
                "COLRPaintGraph",
                "COLRPaintNode",
                "CPALPalette",
                "CPALPaletteSelection",
                "CPALPaletteOverride",
                "BitmapStrikeSelector",
                "PNGGlyphDecoder",
                "SVGGlyphRenderer",
                "SVGGlyphParser",
                "EmojiGlyphDispatcher",
                "ColorGlyphDiagnostic",
            ),
            names,
        )
    }

    @Test
    fun parsesCPALV0PalettesFromRawTableBytes() {
        val table = assertNotNull(CPALV0Parser.parse(syntheticCpalV0()))

        assertEquals(2, table.numPaletteEntries)
        assertEquals(4, table.numColorRecords)
        assertEquals(
            listOf(
                CPALPalette(
                    index = 0,
                    colors = listOf(
                        argb(alpha = 0x44, red = 0x11, green = 0x22, blue = 0x33),
                        argb(alpha = 0x88, red = 0x55, green = 0x66, blue = 0x77),
                    ),
                ),
                CPALPalette(
                    index = 1,
                    colors = listOf(
                        argb(alpha = 0xCC, red = 0x99, green = 0xAA, blue = 0xBB),
                        argb(alpha = 0xFF, red = 0xDD, green = 0xEE, blue = 0x10),
                    ),
                ),
            ),
            table.palettes,
        )
    }

    @Test
    fun rejectsCPALV0PalettesThatExceedCaps() {
        val bytes = ByteArray(12)
        writeU16(bytes, 0, 0)
        writeU16(bytes, 2, 4097)
        writeU16(bytes, 4, 1)
        writeU16(bytes, 6, 4097)
        writeU32(bytes, 8, 12)

        assertNull(CPALV0Parser.parse(bytes))
    }

    @Test
    fun selectsCPALPaletteByIndexAndAppliesOverridesByPaletteEntry() {
        val table = assertNotNull(CPALV0Parser.parse(syntheticCpalV0()))
        val selection = CPALPaletteSelection(
            index = 1,
            overrides = listOf(
                CPALPaletteOverride(index = 0, color = argb(alpha = 0xFF, red = 0x00, green = 0x00, blue = 0xFF)),
                CPALPaletteOverride(index = 99, color = argb(alpha = 0xFF, red = 0x00, green = 0x00, blue = 0x00)),
                CPALPaletteOverride(index = 1, color = argb(alpha = 0xFF, red = 0x00, green = 0xFF, blue = 0x00)),
                CPALPaletteOverride(index = 1, color = argb(alpha = 0xFF, red = 0xFF, green = 0x00, blue = 0x00)),
            ),
        )

        val palette = assertNotNull(selection.select(table))

        assertEquals(1, palette.index)
        assertEquals(
            listOf(
                argb(alpha = 0xFF, red = 0x00, green = 0x00, blue = 0xFF),
                argb(alpha = 0xFF, red = 0xFF, green = 0x00, blue = 0x00),
            ),
            palette.colors,
        )
    }

    @Test
    fun rejectsMissingCPALPaletteSelectionWithoutApplyingOverrides() {
        val table = assertNotNull(CPALV0Parser.parse(syntheticCpalV0()))

        assertNull(CPALPaletteSelection(index = -1).select(table))
        assertNull(CPALPaletteSelection(index = 2).select(table))
    }

    @Test
    fun parsesCOLRV0BaseGlyphsFromRawTableBytes() {
        val table = assertNotNull(COLRV0Parser.parse(syntheticColrV0()))

        assertEquals(
            listOf(
                COLRBaseGlyphRecord(
                    glyphId = 100,
                    firstLayerIndex = 0,
                    numLayers = 2,
                ),
            ),
            table.baseGlyphRecords,
        )
        assertEquals(
            listOf(
                COLRLayerRecord(glyphId = 11, paletteIndex = 0),
                COLRLayerRecord(glyphId = 12, paletteIndex = 1),
            ),
            table.layersForGlyph(100),
        )
    }

    @Test
    fun rejectsCOLRV0TablesWithOutOfRangeLayerSlices() {
        val bytes = syntheticColrV0()
        writeU16(bytes, 16, 1)
        writeU16(bytes, 18, 2)

        assertNull(COLRV0Parser.parse(bytes))
    }

    @Test
    fun buildsCOLRV0PaintGraphWithPaletteIndexes() {
        val table = assertNotNull(COLRV0Parser.parse(syntheticColrV0()))
        val palette = assertNotNull(CPALV0Parser.parse(syntheticCpalV0())).palettes[0]
        val graph = SimpleCOLRGlyphPlanner(table).plan(glyphId = 100, palette = palette)

        assertEquals("colr-v0-glyph", graph.root.kind)
        assertEquals(100, graph.root.glyphId)
        assertEquals(listOf(1, 2), graph.root.children)
        assertEquals(
            listOf(
                COLRPaintNode(id = 0, kind = "colr-v0-glyph", children = listOf(1, 2), glyphId = 100),
                COLRPaintNode(id = 1, kind = "colr-v0-layer", paletteIndex = 0, glyphId = 11),
                COLRPaintNode(id = 2, kind = "colr-v0-layer", paletteIndex = 1, glyphId = 12),
            ),
            graph.nodes,
        )
    }

    @Test
    fun parsesCOLRV1BaseGlyphPaintGlyphSolidGraph() {
        val table = assertNotNull(
            COLRV1Parser.parse(
                syntheticColrV1GlyphSolid(
                    baseGlyph = 120,
                    glyph = 121,
                    paletteIndex = 2,
                    alphaF2Dot14 = 0x2000,
                ),
            ),
        )

        assertEquals(listOf(120), table.baseGlyphPaintRecords.map { record -> record.glyphId })
        val paint = assertNotNull(table.paintForGlyph(120))
        assertEquals(
            COLRV1Paint.Glyph(
                glyphId = 121,
                paint = COLRV1Paint.Solid(
                    paletteIndex = 2,
                    alpha = 0.5f,
                ),
            ),
            paint,
        )

        val graph = assertNotNull(table.paintGraphForGlyph(120))
        assertEquals("colr-v1-glyph", graph.root.kind)
        assertEquals(120, graph.root.glyphId)
        assertEquals(listOf(1), graph.root.children)
        assertEquals(
            listOf(
                COLRPaintNode(id = 0, kind = "colr-v1-glyph", children = listOf(1), glyphId = 120),
                COLRPaintNode(id = 1, kind = "colr-v1-paint-glyph", children = listOf(2), glyphId = 121),
                COLRPaintNode(id = 2, kind = "colr-v1-paint-solid", paletteIndex = 2),
            ),
            graph.nodes,
        )
    }

    @Test
    fun parsesCOLRV1LayerListPaintColrLayersAndClipBox() {
        val table = assertNotNull(
            COLRV1Parser.parse(
                syntheticColrV1LayersAndClip(
                    baseGlyph = 130,
                    firstLayerGlyph = 131,
                    secondLayerGlyph = 132,
                ),
            ),
        )

        assertEquals(2, table.layerPaints.size)
        assertEquals(
            COLRV1Paint.Layers(
                paints = listOf(
                    COLRV1Paint.Glyph(
                        glyphId = 131,
                        paint = COLRV1Paint.Solid(paletteIndex = 0, alpha = 1f),
                    ),
                    COLRV1Paint.Glyph(
                        glyphId = 132,
                        paint = COLRV1Paint.Solid(paletteIndex = 1, alpha = 0.5f),
                    ),
                ),
            ),
            table.paintForGlyph(130),
        )
        assertEquals(
            COLRV1ClipBox(xMin = -10, yMin = -20, xMax = 30, yMax = 40),
            table.clipBoxForGlyph(130),
        )

        val graph = assertNotNull(table.paintGraphForGlyph(130))
        assertEquals(
            listOf(
                COLRPaintNode(id = 0, kind = "colr-v1-glyph", children = listOf(1), glyphId = 130),
                COLRPaintNode(id = 1, kind = "colr-v1-paint-layers", children = listOf(2, 4)),
                COLRPaintNode(id = 2, kind = "colr-v1-paint-glyph", children = listOf(3), glyphId = 131),
                COLRPaintNode(id = 3, kind = "colr-v1-paint-solid", paletteIndex = 0),
                COLRPaintNode(id = 4, kind = "colr-v1-paint-glyph", children = listOf(5), glyphId = 132),
                COLRPaintNode(id = 5, kind = "colr-v1-paint-solid", paletteIndex = 1),
            ),
            graph.nodes,
        )
    }

    @Test
    fun parsesCOLRV1GradientPaintsAsRendererNeutralData() {
        val table = assertNotNull(COLRV1Parser.parse(syntheticColrV1Gradients()))

        assertEquals(
            COLRV1Paint.LinearGradient(
                colorLine = syntheticColorLine(),
                x0 = 1,
                y0 = 2,
                x1 = 3,
                y1 = 4,
                x2 = 5,
                y2 = 6,
            ),
            table.paintForGlyph(200),
        )
        assertEquals(
            COLRV1Paint.RadialGradient(
                colorLine = syntheticColorLine(),
                x0 = -1,
                y0 = -2,
                radius0 = 7,
                x1 = 8,
                y1 = 9,
                radius1 = 10,
            ),
            table.paintForGlyph(201),
        )
        assertEquals(
            COLRV1Paint.SweepGradient(
                colorLine = syntheticColorLine(),
                centerX = 20,
                centerY = 30,
                startAngle = 0.25f,
                endAngle = 0.75f,
            ),
            table.paintForGlyph(202),
        )

        val graph = assertNotNull(table.paintGraphForGlyph(200))
        assertEquals(
            listOf(
                COLRPaintNode(id = 0, kind = "colr-v1-glyph", children = listOf(1), glyphId = 200),
                COLRPaintNode(id = 1, kind = "colr-v1-paint-linear-gradient"),
            ),
            graph.nodes,
        )
    }

    @Test
    fun parsesCOLRV1CompositeAndPaintColrGlyphAsRendererNeutralData() {
        val table = assertNotNull(COLRV1Parser.parse(syntheticColrV1CompositeAndColrGlyph()))

        assertEquals(
            COLRV1Paint.Composite(
                source = COLRV1Paint.Solid(paletteIndex = 4, alpha = 1f),
                mode = COLRV1CompositeMode.SRC_OVER,
                backdrop = COLRV1Paint.ColrGlyph(glyphId = 211),
            ),
            table.paintForGlyph(210),
        )

        val graph = assertNotNull(table.paintGraphForGlyph(210))
        assertEquals(
            listOf(
                COLRPaintNode(id = 0, kind = "colr-v1-glyph", children = listOf(1), glyphId = 210),
                COLRPaintNode(id = 1, kind = "colr-v1-paint-composite-src-over", children = listOf(2, 3)),
                COLRPaintNode(id = 2, kind = "colr-v1-paint-solid", paletteIndex = 4),
                COLRPaintNode(id = 3, kind = "colr-v1-paint-colr-glyph", glyphId = 211),
            ),
            graph.nodes,
        )
    }

    @Test
    fun rejectsCOLRV1GradientPaintWithOutOfBoundsColorLineOffset() {
        val bytes = syntheticColrV1Gradients()
        writeU24(bytes, 65, bytes.size)

        assertNull(COLRV1Parser.parse(bytes))
    }

    @Test
    fun rejectsUnsupportedOrMalformedCOLRV1PaintsWithoutThrowing() {
        assertNull(
            COLRV1Parser.parse(
                syntheticColrV1UnsupportedPaint(
                    baseGlyph = 140,
                    paintFormat = 4,
                ),
            ),
        )
        assertNull(
            COLRV1Parser.parse(
                syntheticColrV1GlyphSolid(
                    baseGlyph = 141,
                    glyph = 142,
                    paletteIndex = 0,
                    alphaF2Dot14 = 0x4000,
                ).copyOf(52),
            ),
        )
    }

    @Test
    fun dispatchesEmojiGlyphsByRoutePreference() {
        val dispatcher = SimpleEmojiGlyphDispatcher(
            EmojiGlyphRouteAvailability(
                colrGlyphs = setOf(10),
                bitmapGlyphs = setOf(10, 20),
                pngGlyphs = setOf(20, 30),
                svgGlyphs = setOf(30, 40),
                outlineGlyphs = setOf(40, 50),
            ),
        )

        assertEquals("colr", dispatcher.dispatch(glyphId = 10, strikeKey = strikeKey()).route)
        assertEquals("bitmap", dispatcher.dispatch(glyphId = 20, strikeKey = strikeKey()).route)
        assertEquals("png", dispatcher.dispatch(glyphId = 30, strikeKey = strikeKey()).route)
        assertEquals("svg", dispatcher.dispatch(glyphId = 40, strikeKey = strikeKey()).route)
        assertEquals("outline", dispatcher.dispatch(glyphId = 50, strikeKey = strikeKey()).route)
    }

    @Test
    fun recordsEmojiGlyphDispatchDiagnostics() {
        val dispatcher = SimpleEmojiGlyphDispatcher(
            EmojiGlyphRouteAvailability(
                bitmapGlyphs = setOf(60),
                pngGlyphs = setOf(60),
                outlineGlyphs = setOf(60),
            ),
        )

        val dispatch = dispatcher.dispatch(glyphId = 60, strikeKey = strikeKey())

        assertEquals("bitmap", dispatch.route)
        assertEquals(listOf("colr", "bitmap", "png", "outline"), dispatch.diagnostics.map { it.route })
        assertTrue(dispatch.diagnostics.first().message.contains("unavailable"))
        assertTrue(dispatch.diagnostics[1].message.contains("Selected"))
        assertTrue(dispatch.diagnostics.drop(2).all { diagnostic -> diagnostic.message.contains("lower preference") })
    }

    @Test
    fun recordsEmojiGlyphDispatchDiagnosticsWithStableCodesAndDetails() {
        val dispatcher = SimpleEmojiGlyphDispatcher(
            EmojiGlyphRouteAvailability(
                bitmapGlyphs = setOf(60),
                pngGlyphs = setOf(60),
                outlineGlyphs = setOf(60),
            ),
        )

        val dispatch = dispatcher.dispatch(glyphId = 60, strikeKey = strikeKey())
        val dump = dispatch.toCanonicalJson()

        assertEquals("bitmap", dispatch.route)
        assertEquals(
            listOf(
                ColorGlyphDiagnosticCodes.ColorGlyphUnavailable,
                ColorGlyphDiagnosticCodes.EmojiRouteSelected,
                ColorGlyphDiagnosticCodes.EmojiRouteLowerPreferenceSkipped,
                ColorGlyphDiagnosticCodes.EmojiRouteLowerPreferenceSkipped,
            ),
            dispatch.diagnostics.map { diagnostic -> diagnostic.code },
        )
        assertEquals(
            listOf(
                "candidate=colr;glyphId=60;sizePx=16.0",
                "selected=bitmap;glyphId=60;sizePx=16.0",
                "candidate=png;selected=bitmap;glyphId=60;sizePx=16.0",
                "candidate=outline;selected=bitmap;glyphId=60;sizePx=16.0",
            ),
            dispatch.diagnostics.map { diagnostic -> diagnostic.detail },
        )
        assertEquals(listOf("info", "info", "info", "info"), dispatch.diagnostics.map { it.severity })
        assertEquals(64, dispatch.dumpSha256.length)
        assertTrue(dump.contains("\"code\": \"text.emoji.color-glyph-unavailable\""))
        assertTrue(dump.contains("\"detail\": \"candidate=colr;glyphId=60;sizePx=16.0\""))
        assertTrue(dump.contains("\"severity\": \"info\""))
        assertEvidenceDumpClean(dump)
    }

    @Test
    fun recordsFullMissingEmojiRouteRefusalWithStableSpecCode() {
        val dispatch = SimpleEmojiGlyphDispatcher(EmojiGlyphRouteAvailability())
            .dispatch(glyphId = 61, strikeKey = strikeKey())

        assertEquals("missing", dispatch.route)
        assertTrue(
            dispatch.diagnostics.any { diagnostic ->
                diagnostic.route == "missing" &&
                    diagnostic.code == ColorGlyphDiagnosticCodes.EmojiFallbackUnavailable &&
                    diagnostic.detail == "glyphId=61;sizePx=16.0;availableRoutes=none" &&
                    diagnostic.severity == "warning"
            },
        )
        assertTrue(dispatch.toCanonicalJson().contains("\"code\": \"text.emoji.fallback-unavailable\""))
        assertEvidenceDumpClean(dispatch.toCanonicalJson())
    }

    @Test
    fun plansColorGlyphRunByRoutePriorityWithStableFallbackDiagnostics() {
        val planner = SimpleColorGlyphPlanner(
            availability = EmojiGlyphRouteAvailability(
                colrGlyphs = setOf(10),
                bitmapGlyphs = setOf(10, 20),
                pngGlyphs = setOf(10, 20, 30),
                svgGlyphs = setOf(10, 20, 30, 40),
                outlineGlyphs = setOf(10, 20, 30, 40, 50),
            ),
            outlineRepresentations = mapOf(
                50 to OutlineGlyphRepresentation(glyphId = 50, pathCommands = listOf("M 0 0")),
            ),
        )
        val run = glyphRun(glyphIds = listOf(10, 20, 30, 40, 50, 60))

        val first = planner.plan(run = run, strikeKey = strikeKey())
        val second = planner.plan(run = run, strikeKey = strikeKey())

        assertEquals(
            listOf("colr", "bitmap", "png", "svg", "outline"),
            first.routes.map { route -> route.route },
        )
        assertEquals(first, second)
        assertTrue(
            first.diagnostics.any { diagnostic ->
                diagnostic.glyphId == 20 &&
                    diagnostic.route == "colr" &&
                    diagnostic.message.contains("unavailable")
            },
        )
        assertTrue(
            first.diagnostics.any { diagnostic ->
                diagnostic.glyphId == 10 &&
                    diagnostic.route == "bitmap" &&
                    diagnostic.message.contains("lower preference")
            },
        )
        assertTrue(
            first.diagnostics.any { diagnostic ->
                diagnostic.glyphId == 60 &&
                    diagnostic.route == "missing" &&
                    diagnostic.severity == "warning"
            },
        )
    }

    @Test
    fun dumpsColorGlyphPlanningResultWithStableRouteOrderAndHashes() {
        val planner = SimpleColorGlyphPlanner(
            availability = EmojiGlyphRouteAvailability(
                colrGlyphs = setOf(10),
                bitmapGlyphs = setOf(10, 20),
                pngGlyphs = setOf(10, 20, 30),
                svgGlyphs = setOf(10, 20, 30, 40),
                outlineGlyphs = setOf(10, 20, 30, 40, 50),
            ),
            outlineRepresentations = mapOf(
                50 to OutlineGlyphRepresentation(glyphId = 50, pathCommands = listOf("M 0 0")),
            ),
        )
        val run = glyphRun(glyphIds = listOf(10, 20, 30, 40, 50, 60))

        val first = planner.plan(run = run, strikeKey = strikeKey())
        val second = planner.plan(run = run, strikeKey = strikeKey())
        val dump = first.toCanonicalJson()

        assertEquals(first.toCanonicalJson(), second.toCanonicalJson())
        assertEquals(first.dumpSha256, second.dumpSha256)
        assertEquals(64, first.dumpSha256.length)
        assertTrue(dump.contains("\"routeOrder\": [\"colr\", \"bitmap\", \"png\", \"svg\", \"outline\"]"))
        assertTrue(dump.indexOf("\"glyphId\": 10") < dump.indexOf("\"glyphId\": 20"))
        assertTrue(dump.indexOf("\"glyphId\": 20") < dump.indexOf("\"glyphId\": 30"))
        assertTrue(dump.indexOf("\"glyphId\": 30") < dump.indexOf("\"glyphId\": 40"))
        assertTrue(dump.indexOf("\"glyphId\": 40") < dump.indexOf("\"glyphId\": 50"))
        assertTrue(dump.contains("\"outlineFallback\": true"))
        assertTrue(dump.contains("\"pathCommandCount\": 1"))
        assertTrue(dump.contains("\"code\": \"text.emoji.fallback-unavailable\""))
        assertEvidenceDumpClean(dump)
    }

    @Test
    fun dumpHashesTrackMutableListContentsAfterFirstRead() {
        val dispatchDiagnostics = mutableListOf(
            ColorGlyphDiagnostic(
                glyphId = 70,
                route = "colr",
                message = "Selected colr route for glyph 70 at 16.0px.",
                code = ColorGlyphDiagnosticCodes.EmojiRouteSelected,
                detail = "selected=colr;glyphId=70;sizePx=16.0",
            ),
        )
        val dispatch = EmojiGlyphDispatch(
            glyphId = 70,
            route = "colr",
            diagnostics = dispatchDiagnostics,
        )
        val firstDispatchHash = dispatch.dumpSha256

        dispatchDiagnostics += ColorGlyphDiagnostic(
            glyphId = 71,
            route = "png",
            message = "Route png is unavailable for glyph 71 at 16.0px.",
            code = ColorGlyphDiagnosticCodes.ColorGlyphUnavailable,
            detail = "candidate=png;glyphId=71;sizePx=16.0",
        )
        val mutatedDispatchDump = dispatch.toCanonicalJson()

        assertTrue(mutatedDispatchDump.contains("\"glyphId\": 71"))
        assertEquals(canonicalDumpBodySha256(mutatedDispatchDump), dispatch.dumpSha256)
        assertTrue(dispatch.dumpSha256 != firstDispatchHash)

        val routes = mutableListOf(ColorGlyphRoute(glyphId = 72, route = "colr"))
        val planningResult = ColorGlyphPlanningResult(routes = routes)
        val firstPlanningHash = planningResult.dumpSha256

        routes += ColorGlyphRoute(glyphId = 73, route = "png")
        val mutatedPlanningDump = planningResult.toCanonicalJson()

        assertTrue(mutatedPlanningDump.contains("\"glyphId\": 73"))
        assertEquals(canonicalDumpBodySha256(mutatedPlanningDump), planningResult.dumpSha256)
        assertTrue(planningResult.dumpSha256 != firstPlanningHash)
    }

    @Test
    fun colorGlyphDiagnosticKeepsFourArgumentJvmConstructorCompatibility() {
        val constructor = ColorGlyphDiagnostic::class.java.getConstructor(
            Int::class.javaObjectType,
            String::class.java,
            String::class.java,
            String::class.java,
        )

        val diagnostic = constructor.newInstance(
            74,
            "colr",
            "Route colr is unavailable for glyph 74 at 16.0px.",
            "warning",
        ) as ColorGlyphDiagnostic

        assertEquals(74, diagnostic.glyphId)
        assertEquals("colr", diagnostic.route)
        assertEquals("Route colr is unavailable for glyph 74 at 16.0px.", diagnostic.message)
        assertEquals("warning", diagnostic.severity)
        assertEquals(ColorGlyphDiagnosticCodes.ColorGlyphUnavailable, diagnostic.code)
        assertEquals(diagnostic.message, diagnostic.detail)
    }

    @Test
    fun parsesBasicSVGGlyphViewBoxAndElementSummaries() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 64">
              <rect x="1" y="2" width="10" height="20" fill="#ff0000"/>
              <path d="M0 0 L10 10" stroke="#000000" fill="none"/>
              <metadata>ignored</metadata>
            </svg>
        """.trimIndent()

        val document = BasicSVGGlyphParser.parse(glyphId = 77, text = svg)

        assertEquals(77, document.glyphId)
        assertEquals(listOf(0f, 0f, 128f, 64f), document.viewBox)
        assertEquals(
            listOf(
                "rect{fill=#ff0000,height=20,width=10,x=1,y=2}",
                "path{d=M0 0 L10 10,fill=none,stroke=#000000}",
            ),
            document.elements,
        )
    }

    @Test
    fun ignoresBasicSVGGlyphTagsInsideCommentsAndCdata() {
        val svg = """
            <!-- <svg viewBox="0 0 1 1"><path d="M0 0"/></svg> -->
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
              <![CDATA[<circle cx="4" cy="4" r="2"/>]]>
              <rect width="8" height="8" fill="#00ff00"/>
              <!-- <path d="M0 0 L16 16"/> -->
            </svg>
        """.trimIndent()

        val document = BasicSVGGlyphParser.parse(glyphId = 79, text = svg)

        assertEquals(listOf(0f, 0f, 16f, 16f), document.viewBox)
        assertEquals(
            listOf("rect{fill=#00ff00,height=8,width=8}"),
            document.elements,
        )
    }

    @Test
    fun rejectsOversizedBasicSVGGlyphPayloadsBeforeParsing() {
        val oversizedSvg = "<svg viewBox=\"0 0 1 1\">" + " ".repeat(70_000) + "</svg>"

        val failure = assertFailsWith<IllegalArgumentException> {
            BasicSVGGlyphParser.parse(glyphId = 78, text = oversizedSvg)
        }

        assertTrue(failure.message.orEmpty().contains("exceeds"))
    }

    @Test
    fun rejectsBasicPNGGlyphHeaderOnlyPayloadsWithoutRasterPixels() {
        val failure = assertFailsWith<IllegalArgumentException> {
            BasicPNGGlyphDecoder.decode(
                glyphId = 81,
                bytes = syntheticPngHeader(width = 19, height = 23),
            )
        }

        assertTrue(failure.message.orEmpty().contains("decode PNG glyph payload"))
    }

    @Test
    fun decodesBasicPNGGlyphRgbaPixels() {
        val image = BasicPNGGlyphDecoder.decode(
            glyphId = 83,
            bytes = syntheticRgbaPng(
                width = 2,
                height = 1,
                pixels = intArrayOf(
                    argb(alpha = 0x44, red = 0x11, green = 0x22, blue = 0x33),
                    argb(alpha = 0x88, red = 0x55, green = 0x66, blue = 0x77),
                ),
            ),
        )

        assertEquals(83, image.glyphId)
        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertEquals(
            listOf(
                argb(alpha = 0x44, red = 0x11, green = 0x22, blue = 0x33),
                argb(alpha = 0x88, red = 0x55, green = 0x66, blue = 0x77),
            ),
            image.pixels,
        )
    }

    @Test
    fun rejectsBasicPNGGlyphPayloadsWithInvalidCrc() {
        val png = syntheticRgbaPng(
            width = 1,
            height = 1,
            pixels = intArrayOf(argb(alpha = 0xFF, red = 0x10, green = 0x20, blue = 0x30)),
        )
        png[png.lastIndex - 4] = (png[png.lastIndex - 4].toInt() xor 0x01).toByte()

        val failure = assertFailsWith<IllegalArgumentException> {
            BasicPNGGlyphDecoder.decode(glyphId = 84, bytes = png)
        }

        assertTrue(failure.message.orEmpty().contains("decode PNG glyph payload"))
    }

    @Test
    fun rejectsBasicPNGGlyphPayloadsWithoutPngSignature() {
        val failure = assertFailsWith<IllegalArgumentException> {
            BasicPNGGlyphDecoder.decode(glyphId = 82, bytes = ByteArray(33))
        }

        assertTrue(failure.message.orEmpty().contains("PNG signature"))
    }

    @Test
    fun selectsNearestStaticBitmapStrikeFromImmutableSnapshot() {
        val sixteenPpem = BitmapStrikeSelection(
            glyphId = 90,
            width = 16,
            height = 16,
            format = "png",
            ppem = 16,
        )
        val twentyPpem = BitmapStrikeSelection(
            glyphId = 90,
            width = 20,
            height = 20,
            format = "png",
            ppem = 20,
        )
        val twentyFourPpem = BitmapStrikeSelection(
            glyphId = 90,
            width = 24,
            height = 24,
            format = "png",
            ppem = 24,
        )
        val entries = mutableListOf(
            twentyFourPpem,
            sixteenPpem,
            BitmapStrikeSelection(glyphId = 91, width = 18, height = 18, format = "png", ppem = 18),
            twentyPpem,
        )
        val selector = StaticBitmapStrikeSelector(entries)

        entries.clear()

        assertEquals(twentyPpem, selector.select(glyphId = 90, requestedSizePx = 21f))
        assertEquals(sixteenPpem, selector.select(glyphId = 90, requestedSizePx = 18f))
        assertNull(selector.select(glyphId = 92, requestedSizePx = 18f))
    }

    @Test
    fun colorSvgEmojiGoldenRecordsFixtureFamiliesRefusalsAndNonClaims() {
        val dump = readProjectFile("reports/font/fixtures/expected/color/color-svg-emoji-goldens.json")

        assertEquals("color-svg-emoji-goldens", jsonStringField(dump, "dumpId"))
        assertEquals(listOf("color-colrv1-test-glyphs"), jsonStringArrayField(dump, "fixtureIds"))
        assertEquals(
            listOf(
                "color-glyphs",
                "png-bitmap-glyphs",
                "svg-glyphs",
                "emoji",
            ),
            jsonStringArrayField(dump, "colorFamilies"),
        )
        assertEquals(
            listOf(
                "text.color.COLRv1-cycle-detected",
                "text.color.COLRv1-budget-exceeded",
                "text.bitmap.PNG-decode-failed",
                "text.bitmap.strike-unavailable",
                "text.bitmap.payload-format-unsupported",
                "text.SVG.external-resource-refused",
                "text.SVG.feature-unsupported",
                "text.SVG.budget-exceeded",
                "text.emoji.sequence-unsupported",
                "text.emoji.fallback-unavailable",
                "text.emoji.color-glyph-unavailable",
            ),
            jsonStringArrayField(dump, "requiredRefusals"),
        )
        assertEquals(
            listOf(
                "no-complete-target-support-claim",
                "no-complete-colrv1-rendering-claim",
                "no-complete-png-bitmap-glyph-routing-claim",
                "no-complete-svg-in-opentype-rendering-claim",
                "no-emoji-zwj-shaping-claim",
                "no-gpu-color-glyph-support-claim",
                "no-platform-color-font-fallback-claim",
            ),
            jsonStringArrayField(dump, "nonClaims"),
        )
    }

    private fun syntheticCpalV0(): ByteArray {
        val bytes = ByteArray(32)
        writeU16(bytes, 0, 0)
        writeU16(bytes, 2, 2)
        writeU16(bytes, 4, 2)
        writeU16(bytes, 6, 4)
        writeU32(bytes, 8, 16)
        writeU16(bytes, 12, 0)
        writeU16(bytes, 14, 2)
        writeBgra(bytes, 16, blue = 0x33, green = 0x22, red = 0x11, alpha = 0x44)
        writeBgra(bytes, 20, blue = 0x77, green = 0x66, red = 0x55, alpha = 0x88)
        writeBgra(bytes, 24, blue = 0xBB, green = 0xAA, red = 0x99, alpha = 0xCC)
        writeBgra(bytes, 28, blue = 0x10, green = 0xEE, red = 0xDD, alpha = 0xFF)
        return bytes
    }

    private fun syntheticColrV0(): ByteArray {
        val bytes = ByteArray(28)
        writeU16(bytes, 0, 0)
        writeU16(bytes, 2, 1)
        writeU32(bytes, 4, 14)
        writeU32(bytes, 8, 20)
        writeU16(bytes, 12, 2)
        writeU16(bytes, 14, 100)
        writeU16(bytes, 16, 0)
        writeU16(bytes, 18, 2)
        writeU16(bytes, 20, 11)
        writeU16(bytes, 22, 0)
        writeU16(bytes, 24, 12)
        writeU16(bytes, 26, 1)
        return bytes
    }

    private fun syntheticColrV1GlyphSolid(
        baseGlyph: Int,
        glyph: Int,
        paletteIndex: Int,
        alphaF2Dot14: Int,
    ): ByteArray {
        val baseGlyphListOffset = 34
        val glyphPaintOffset = baseGlyphListOffset + 10
        val solidPaintOffset = glyphPaintOffset + 6
        val bytes = ByteArray(solidPaintOffset + 5)
        writeU16(bytes, 0, 1)
        writeU32(bytes, 14, baseGlyphListOffset)

        writeU32(bytes, baseGlyphListOffset, 1)
        writeU16(bytes, baseGlyphListOffset + 4, baseGlyph)
        writeU32(bytes, baseGlyphListOffset + 6, glyphPaintOffset - baseGlyphListOffset)

        bytes[glyphPaintOffset] = 10
        writeU24(bytes, glyphPaintOffset + 1, solidPaintOffset - glyphPaintOffset)
        writeU16(bytes, glyphPaintOffset + 4, glyph)

        bytes[solidPaintOffset] = 2
        writeU16(bytes, solidPaintOffset + 1, paletteIndex)
        writeI16(bytes, solidPaintOffset + 3, alphaF2Dot14)
        return bytes
    }

    private fun syntheticColrV1UnsupportedPaint(baseGlyph: Int, paintFormat: Int): ByteArray {
        val baseGlyphListOffset = 34
        val paintOffset = baseGlyphListOffset + 10
        val bytes = ByteArray(paintOffset + 1)
        writeU16(bytes, 0, 1)
        writeU32(bytes, 14, baseGlyphListOffset)

        writeU32(bytes, baseGlyphListOffset, 1)
        writeU16(bytes, baseGlyphListOffset + 4, baseGlyph)
        writeU32(bytes, baseGlyphListOffset + 6, paintOffset - baseGlyphListOffset)
        bytes[paintOffset] = paintFormat.toByte()
        return bytes
    }

    private fun syntheticColrV1LayersAndClip(
        baseGlyph: Int,
        firstLayerGlyph: Int,
        secondLayerGlyph: Int,
    ): ByteArray {
        val baseGlyphListOffset = 34
        val layerListOffset = 44
        val rootLayersPaintOffset = 56
        val firstGlyphPaintOffset = 62
        val firstSolidPaintOffset = 68
        val secondGlyphPaintOffset = 73
        val secondSolidPaintOffset = 79
        val clipListOffset = 84
        val clipBoxOffset = 96
        val bytes = ByteArray(clipBoxOffset + 9)
        writeU16(bytes, 0, 1)
        writeU32(bytes, 14, baseGlyphListOffset)
        writeU32(bytes, 18, layerListOffset)
        writeU32(bytes, 22, clipListOffset)

        writeU32(bytes, baseGlyphListOffset, 1)
        writeU16(bytes, baseGlyphListOffset + 4, baseGlyph)
        writeU32(bytes, baseGlyphListOffset + 6, rootLayersPaintOffset - baseGlyphListOffset)

        writeU32(bytes, layerListOffset, 2)
        writeU32(bytes, layerListOffset + 4, firstGlyphPaintOffset - layerListOffset)
        writeU32(bytes, layerListOffset + 8, secondGlyphPaintOffset - layerListOffset)

        bytes[rootLayersPaintOffset] = 1
        bytes[rootLayersPaintOffset + 1] = 2
        writeU32(bytes, rootLayersPaintOffset + 2, 0)

        bytes[firstGlyphPaintOffset] = 10
        writeU24(bytes, firstGlyphPaintOffset + 1, firstSolidPaintOffset - firstGlyphPaintOffset)
        writeU16(bytes, firstGlyphPaintOffset + 4, firstLayerGlyph)
        bytes[firstSolidPaintOffset] = 2
        writeU16(bytes, firstSolidPaintOffset + 1, 0)
        writeI16(bytes, firstSolidPaintOffset + 3, 0x4000)

        bytes[secondGlyphPaintOffset] = 10
        writeU24(bytes, secondGlyphPaintOffset + 1, secondSolidPaintOffset - secondGlyphPaintOffset)
        writeU16(bytes, secondGlyphPaintOffset + 4, secondLayerGlyph)
        bytes[secondSolidPaintOffset] = 2
        writeU16(bytes, secondSolidPaintOffset + 1, 1)
        writeI16(bytes, secondSolidPaintOffset + 3, 0x2000)

        bytes[clipListOffset] = 1
        writeU32(bytes, clipListOffset + 1, 1)
        writeU16(bytes, clipListOffset + 5, baseGlyph)
        writeU16(bytes, clipListOffset + 7, baseGlyph)
        writeU24(bytes, clipListOffset + 9, clipBoxOffset - clipListOffset)

        bytes[clipBoxOffset] = 1
        writeI16(bytes, clipBoxOffset + 1, -10)
        writeI16(bytes, clipBoxOffset + 3, -20)
        writeI16(bytes, clipBoxOffset + 5, 30)
        writeI16(bytes, clipBoxOffset + 7, 40)
        return bytes
    }

    private fun syntheticColrV1Gradients(): ByteArray {
        val baseGlyphListOffset = 34
        val linearPaintOffset = 64
        val radialPaintOffset = 80
        val sweepPaintOffset = 96
        val linearColorLineOffset = 112
        val radialColorLineOffset = 127
        val sweepColorLineOffset = 142
        val bytes = ByteArray(157)
        writeU16(bytes, 0, 1)
        writeU32(bytes, 14, baseGlyphListOffset)

        writeU32(bytes, baseGlyphListOffset, 3)
        writeU16(bytes, baseGlyphListOffset + 4, 200)
        writeU32(bytes, baseGlyphListOffset + 6, linearPaintOffset - baseGlyphListOffset)
        writeU16(bytes, baseGlyphListOffset + 10, 201)
        writeU32(bytes, baseGlyphListOffset + 12, radialPaintOffset - baseGlyphListOffset)
        writeU16(bytes, baseGlyphListOffset + 16, 202)
        writeU32(bytes, baseGlyphListOffset + 18, sweepPaintOffset - baseGlyphListOffset)

        bytes[linearPaintOffset] = 4
        writeU24(bytes, linearPaintOffset + 1, linearColorLineOffset - linearPaintOffset)
        writeI16(bytes, linearPaintOffset + 4, 1)
        writeI16(bytes, linearPaintOffset + 6, 2)
        writeI16(bytes, linearPaintOffset + 8, 3)
        writeI16(bytes, linearPaintOffset + 10, 4)
        writeI16(bytes, linearPaintOffset + 12, 5)
        writeI16(bytes, linearPaintOffset + 14, 6)

        bytes[radialPaintOffset] = 6
        writeU24(bytes, radialPaintOffset + 1, radialColorLineOffset - radialPaintOffset)
        writeI16(bytes, radialPaintOffset + 4, -1)
        writeI16(bytes, radialPaintOffset + 6, -2)
        writeU16(bytes, radialPaintOffset + 8, 7)
        writeI16(bytes, radialPaintOffset + 10, 8)
        writeI16(bytes, radialPaintOffset + 12, 9)
        writeU16(bytes, radialPaintOffset + 14, 10)

        bytes[sweepPaintOffset] = 8
        writeU24(bytes, sweepPaintOffset + 1, sweepColorLineOffset - sweepPaintOffset)
        writeI16(bytes, sweepPaintOffset + 4, 20)
        writeI16(bytes, sweepPaintOffset + 6, 30)
        writeI16(bytes, sweepPaintOffset + 8, 0x1000)
        writeI16(bytes, sweepPaintOffset + 10, 0x3000)

        writeColorLine(bytes, linearColorLineOffset)
        writeColorLine(bytes, radialColorLineOffset)
        writeColorLine(bytes, sweepColorLineOffset)
        return bytes
    }

    private fun syntheticColrV1CompositeAndColrGlyph(): ByteArray {
        val baseGlyphListOffset = 34
        val compositePaintOffset = 44
        val sourcePaintOffset = 52
        val backdropPaintOffset = 57
        val bytes = ByteArray(60)
        writeU16(bytes, 0, 1)
        writeU32(bytes, 14, baseGlyphListOffset)

        writeU32(bytes, baseGlyphListOffset, 1)
        writeU16(bytes, baseGlyphListOffset + 4, 210)
        writeU32(bytes, baseGlyphListOffset + 6, compositePaintOffset - baseGlyphListOffset)

        bytes[compositePaintOffset] = 32
        writeU24(bytes, compositePaintOffset + 1, sourcePaintOffset - compositePaintOffset)
        bytes[compositePaintOffset + 4] = 3
        writeU24(bytes, compositePaintOffset + 5, backdropPaintOffset - compositePaintOffset)

        bytes[sourcePaintOffset] = 2
        writeU16(bytes, sourcePaintOffset + 1, 4)
        writeI16(bytes, sourcePaintOffset + 3, 0x4000)

        bytes[backdropPaintOffset] = 11
        writeU16(bytes, backdropPaintOffset + 1, 211)
        return bytes
    }

    private fun syntheticColorLine(): COLRV1ColorLine =
        COLRV1ColorLine(
            extend = COLRV1ColorLineExtend.REPEAT,
            stops = listOf(
                COLRV1ColorStop(offset = 0f, paletteIndex = 5, alpha = 1f),
                COLRV1ColorStop(offset = 1f, paletteIndex = 6, alpha = 0.5f),
            ),
        )

    private fun writeColorLine(bytes: ByteArray, offset: Int) {
        bytes[offset] = 1
        writeU16(bytes, offset + 1, 2)
        writeI16(bytes, offset + 3, 0)
        writeU16(bytes, offset + 5, 5)
        writeI16(bytes, offset + 7, 0x4000)
        writeI16(bytes, offset + 9, 0x4000)
        writeU16(bytes, offset + 11, 6)
        writeI16(bytes, offset + 13, 0x2000)
    }

    private fun syntheticPngHeader(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(33)
        val signature = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        signature.forEachIndexed { index, value -> bytes[index] = value.toByte() }
        writeU32(bytes, 8, 13)
        bytes[12] = 'I'.code.toByte()
        bytes[13] = 'H'.code.toByte()
        bytes[14] = 'D'.code.toByte()
        bytes[15] = 'R'.code.toByte()
        writeU32(bytes, 16, width)
        writeU32(bytes, 20, height)
        bytes[24] = 8
        bytes[25] = 6
        return bytes
    }

    private fun syntheticRgbaPng(width: Int, height: Int, pixels: IntArray): ByteArray {
        require(pixels.size == width * height)

        val raw = ByteArrayOutputStream()
        repeat(height) { y ->
            raw.write(0)
            repeat(width) { x ->
                val color = pixels[y * width + x]
                raw.write((color ushr 16) and 0xFF)
                raw.write((color ushr 8) and 0xFF)
                raw.write(color and 0xFF)
                raw.write((color ushr 24) and 0xFF)
            }
        }

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writePngChunk("IHDR", ByteArrayOutputStream().apply {
                writeU32(width)
                writeU32(height)
                write(8)
                write(6)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            writePngChunk("IDAT", deflate(raw.toByteArray()))
            writePngChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writePngChunk(type: String, data: ByteArray) {
        writeU32(data.size)
        val crc = CRC32()
        val typeBytes = type.encodeToByteArray()
        write(typeBytes)
        write(data)
        crc.update(typeBytes)
        crc.update(data)
        writeU32(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(data)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(128)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun writeBgra(bytes: ByteArray, offset: Int, blue: Int, green: Int, red: Int, alpha: Int) {
        bytes[offset] = blue.toByte()
        bytes[offset + 1] = green.toByte()
        bytes[offset + 2] = red.toByte()
        bytes[offset + 3] = alpha.toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeI16(bytes: ByteArray, offset: Int, value: Int) {
        writeU16(bytes, offset, value and 0xFFFF)
    }

    private fun writeU24(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 16).toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = value.toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)

    private fun glyphRun(glyphIds: List<Int>): GPUGlyphRunDescriptor =
        GPUGlyphRunDescriptor(
            runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441202")),
            glyphIDs = glyphIds,
        )

    private fun strikeKey(): GlyphStrikeKey =
        GlyphStrikeKey(
            typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655441201")),
            sizePx = 16f,
        )

    private fun canonicalDumpBodySha256(dump: String): String {
        val body = dump.replace(
            Regex(",\n  \"dumpSha256\": \"[0-9a-f]{64}\"\n}\\n$"),
            "\n}\n",
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun assertEvidenceDumpClean(dump: String) {
        val forbiddenTokens = listOf(
            "Sk",
            "HarfBuzz",
            "FreeType",
            "Fontations",
            "AWT",
            "JNI",
            "CoreText",
            "DirectWrite",
            "fontconfig",
            "gpuHandle",
            "nativeHandle",
            "/Users/",
            "tmp/",
        )
        forbiddenTokens.forEach { token ->
            assertTrue(!dump.contains(token), "Dump must not contain forbidden token $token: $dump")
        }
        assertTrue(!Regex("@[0-9a-fA-F]{4,}").containsMatchIn(dump), "Dump must not contain object identity: $dump")
    }

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
