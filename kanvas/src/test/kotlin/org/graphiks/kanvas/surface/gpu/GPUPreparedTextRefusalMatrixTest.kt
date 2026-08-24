package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@OptIn(ExperimentalUnsignedTypes::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GPUPreparedTextRefusalMatrixTest {
    data class RefusalCase(
        val name: String,
        val operation: DisplayOp.DrawText,
        val expectedCode: String,
        val resolver: GPUPreparedTextFontResolver = GPUPreparedFontTypefaceResolver,
    ) {
        override fun toString(): String = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusalCases")
    fun `text refusals are stable and terminal`(case: RefusalCase) {
        val result = assertIs<GPUPreparedTextLowering.Refused>(
            GPUPreparedTextLowerer.lower(
                operation = case.operation,
                operationIndex = 17,
                target = target(),
                capabilities = capabilities(),
                fontResolver = case.resolver,
            ),
        )

        assertEquals(case.expectedCode, result.code)
        assertEquals(17, result.operationIndex)
        assertNotNull(result.facts["message"])
        assertNotNull(result.facts["stage"])
        assertNotNull(result.facts["reason"])
        assertNotNull(result.facts["authority"])
    }

    fun refusalCases(): List<RefusalCase> {
        val valid = validOperation()
        return listOf(
            RefusalCase(
                "null typeface",
                valid.copy(blob = valid.blob.copy(typeface = null)),
                GPUTextRefusalCodes.TYPEFACE_MISSING,
            ),
            RefusalCase(
                "unsupported typeface",
                valid.copy(blob = valid.blob.copy(typeface = UnsupportedTypeface)),
                GPUTextRefusalCodes.TYPEFACE_UNSUPPORTED,
            ),
            RefusalCase(
                "unstable identity",
                valid,
                GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
                refusingResolver(GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE),
            ),
            RefusalCase(
                "malformed font bytes",
                valid,
                GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                refusingResolver(GPUTextRefusalCodes.FONT_BYTES_MALFORMED),
            ),
            RefusalCase(
                "mismatched positions",
                valid.withRun(glyphs = listOf(5u, 9u), positions = listOf(Point2F32(1f, 2f))),
                GPUTextRefusalCodes.POSITION_COUNT_MISMATCH,
            ),
            RefusalCase(
                "non finite run size",
                valid.withRun(fontSize = Float.NaN),
                GPUTextRefusalCodes.FONT_SIZE_INVALID,
            ),
            RefusalCase(
                "non finite position",
                valid.withRun(positions = listOf(Point2F32(Float.POSITIVE_INFINITY, 2f))),
                GPUTextRefusalCodes.POSITION_NONFINITE,
            ),
            RefusalCase(
                "UShort maximum glyph outside parsed face range",
                valid.withRun(glyphs = listOf(UShort.MAX_VALUE)),
                GPUTextRefusalCodes.GLYPH_ID_INVALID,
            ),
            RefusalCase(
                "missing notdef representation",
                valid.withRun(glyphs = listOf(0u)),
                GPUTextRefusalCodes.NOTDEF_UNAVAILABLE,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.MISSING),
            ),
            RefusalCase(
                "CBDT CBLC bitmap glyph",
                valid,
                GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.CBDT_CBLC),
            ),
            RefusalCase(
                "sbix bitmap glyph",
                valid,
                GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.SBIX),
            ),
            RefusalCase(
                "SVG glyph",
                valid,
                GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.SVG),
            ),
            RefusalCase(
                "unproved COLRv1 glyph",
                valid,
                GPUTextRefusalCodes.COLRV1_UNPROVED,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.COLRV1),
            ),
            RefusalCase(
                "missing internal representation",
                valid,
                GPUTextRefusalCodes.REPRESENTATION_MISSING,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.MISSING),
            ),
            RefusalCase(
                "non finite origin",
                valid.copy(x = Float.NaN),
                GPUTextRefusalCodes.ORIGIN_NONFINITE,
            ),
            RefusalCase(
                "non finite transform",
                valid.copy(
                    transform = Matrix3x3F32.of(
                        1f, 0f, 0f,
                        0f, Float.NaN, 0f,
                        0f, 0f, 1f,
                    ),
                ),
                GPUTextRefusalCodes.TRANSFORM_NONFINITE,
            ),
            RefusalCase(
                "singular transform",
                valid.copy(transform = Matrix3x3F32.scaling(0f, 1f)),
                GPUTextRefusalCodes.TRANSFORM_SINGULAR,
            ),
            RefusalCase(
                "perspective transform",
                valid.copy(
                    transform = Matrix3x3F32.of(
                        1f, 0f, 0f,
                        0f, 1f, 0f,
                        0.1f, 0f, 1f,
                    ),
                ),
                GPUTextRefusalCodes.TRANSFORM_PERSPECTIVE,
            ),
            RefusalCase(
                "clip refused by common authority",
                valid.copy(
                    clip = ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                Path { moveTo(Float.NaN, 0f); lineTo(1f, 1f) },
                                ClipOp.INTERSECT,
                            ),
                        ),
                    ),
                ),
                GPUTextRefusalCodes.CLIP_ROUTE_UNACCEPTED,
            ),
            RefusalCase(
                "stroke and fill paint style unsupported",
                valid.copy(paint = valid.paint.copy(style = PaintStyle.STROKE_AND_FILL)),
                GPUTextRefusalCodes.PAINT_STYLE_UNSUPPORTED,
            ),
            RefusalCase(
                "arithmetic blender has no canonical blend mode",
                valid.copy(paint = valid.paint.copy(blender = Blender.Arithmetic(0f, 1f, 1f, 0f))),
                GPUTextRefusalCodes.BLEND_UNSUPPORTED,
            ),
            RefusalCase(
                "image filter requires composite",
                valid.copy(paint = valid.paint.copy(imageFilter = ImageFilter.Blur(1f, 1f))),
                GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE,
            ),
            RefusalCase(
                "unsupported shader mask filter",
                valid.copy(
                    paint = valid.paint.copy(
                        maskFilter = MaskFilter.Shader(Shader.SolidColor(Color.RED)),
                    ),
                ),
                GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED,
            ),
            RefusalCase(
                "unsupported table mask filter",
                valid.copy(paint = valid.paint.copy(maskFilter = MaskFilter.Table(UByteArray(256)))),
                GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED,
            ),
            RefusalCase(
                "unsupported material",
                valid.copy(
                    paint = valid.paint.copy(shader = Shader.PerlinNoise(1f, 1f, 2, 3, null)),
                ),
                "unsupported.material.mapping.noise_shader",
            ),
            RefusalCase(
                "priority typeface before malformed run and transform",
                valid.copy(
                    blob = valid.blob.copy(
                        typeface = null,
                        glyphRuns = listOf(
                            KanvasGlyphRun(
                                glyphs = listOf(5u, 9u),
                                positions = emptyList(),
                                fontSize = Float.NaN,
                            ),
                        ),
                    ),
                    transform = Matrix3x3F32.scaling(0f, 0f),
                ),
                GPUTextRefusalCodes.TYPEFACE_MISSING,
            ),
            RefusalCase(
                "priority run structure before glyph and origin",
                valid.withRun(
                    glyphs = listOf(UShort.MAX_VALUE, 5u),
                    positions = listOf(Point2F32(Float.NaN, 0f)),
                ).copy(x = Float.NaN),
                GPUTextRefusalCodes.POSITION_COUNT_MISMATCH,
            ),
            RefusalCase(
                "priority representation before clip and paint",
                valid.copy(
                    clip = ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                Path { moveTo(Float.NaN, 0f) },
                                ClipOp.INTERSECT,
                            ),
                        ),
                    ),
                    paint = valid.paint.copy(imageFilter = ImageFilter.Blur(1f, 1f)),
                ),
                GPUTextRefusalCodes.REPRESENTATION_MISSING,
                overridingRepresentation(GPUPreparedTextSourceRepresentation.MISSING),
            ),
            RefusalCase(
                "non-finite determinant produced by finite coefficients",
                valid.copy(
                    transform = Matrix3x3F32.of(
                        Float.MAX_VALUE, Float.MAX_VALUE, 0f,
                        Float.MAX_VALUE, Float.MAX_VALUE, 0f,
                        0f, 0f, 1f,
                    ),
                ),
                GPUTextRefusalCodes.TRANSFORM_NONFINITE,
            ),
        )
    }

    @Test
    fun `global run priority is independent of run order`() {
        val valid = validOperation()
        val malformedStructure = KanvasGlyphRun(
            glyphs = listOf(5u, 9u),
            positions = listOf(Point2F32(1f, 2f)),
            fontSize = 16f,
        )
        val nonFinitePosition = KanvasGlyphRun(
            glyphs = listOf(5u),
            positions = listOf(Point2F32(Float.NaN, 2f)),
            fontSize = 16f,
        )

        listOf(
            listOf(nonFinitePosition, malformedStructure),
            listOf(malformedStructure, nonFinitePosition),
        ).forEach { runs ->
            val result = assertIs<GPUPreparedTextLowering.Refused>(
                lower(valid.copy(blob = valid.blob.copy(glyphRuns = runs))),
            )
            assertEquals(GPUTextRefusalCodes.POSITION_COUNT_MISMATCH, result.code)
        }
    }

    @Test
    fun `global glyph priority is independent of glyph order`() {
        val valid = validOperation()

        listOf(
            listOf(5u, UShort.MAX_VALUE),
            listOf(UShort.MAX_VALUE, 5u),
        ).forEach { glyphs ->
            val result = assertIs<GPUPreparedTextLowering.Refused>(
                lower(
                    valid.withRun(
                        glyphs = glyphs,
                        positions = glyphs.indices.map { index -> Point2F32(index.toFloat(), 2f) },
                    ),
                    resolver = overridingRepresentation(
                        GPUPreparedTextSourceRepresentation.MISSING,
                    ),
                ),
            )
            assertEquals(GPUTextRefusalCodes.GLYPH_ID_INVALID, result.code)
        }
    }

    @Test
    fun `representation refusal priority is independent of every glyph permutation`() {
        data class RankedRepresentation(
            val glyphId: UShort,
            val representation: GPUPreparedTextSourceRepresentation,
            val expectedCode: String,
        )

        val ranked = listOf(
            RankedRepresentation(
                glyphId = 0u,
                representation = GPUPreparedTextSourceRepresentation.MISSING,
                expectedCode = GPUTextRefusalCodes.NOTDEF_UNAVAILABLE,
            ),
            RankedRepresentation(
                glyphId = 5u,
                representation = GPUPreparedTextSourceRepresentation.CBDT_CBLC,
                expectedCode = GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED,
            ),
            RankedRepresentation(
                glyphId = 6u,
                representation = GPUPreparedTextSourceRepresentation.SBIX,
                expectedCode = GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED,
            ),
            RankedRepresentation(
                glyphId = 7u,
                representation = GPUPreparedTextSourceRepresentation.SVG,
                expectedCode = GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
            ),
            RankedRepresentation(
                glyphId = 8u,
                representation = GPUPreparedTextSourceRepresentation.COLRV1,
                expectedCode = GPUTextRefusalCodes.COLRV1_UNPROVED,
            ),
            RankedRepresentation(
                glyphId = 9u,
                representation = GPUPreparedTextSourceRepresentation.MISSING,
                expectedCode = GPUTextRefusalCodes.REPRESENTATION_MISSING,
            ),
        )
        val representations = ranked.associate { it.glyphId.toInt() to it.representation }
        val default = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(validOperation().blob.typeface),
        )
        val resolver = GPUPreparedTextFontResolver {
            GPUPreparedTextFontResolution.ready(
                face = default.face,
                glyphCount = default.glyphCount,
                representationResolver = GPUPreparedTextGlyphRepresentationResolver {
                        glyphId,
                        _,
                        _,
                    ->
                    checkNotNull(representations[glyphId])
                },
            )
        }

        ranked.indices.forEach { higherIndex ->
            ((higherIndex + 1) until ranked.size).forEach { lowerIndex ->
                val higher = ranked[higherIndex]
                val lower = ranked[lowerIndex]
                listOf(
                    listOf(higher.glyphId, lower.glyphId),
                    listOf(lower.glyphId, higher.glyphId),
                ).forEach { glyphs ->
                    val result = assertIs<GPUPreparedTextLowering.Refused>(
                        lower(
                            operation = validOperation().withRun(
                                glyphs = glyphs,
                                positions = glyphs.indices.map { Point2F32(it.toFloat(), 2f) },
                            ),
                            resolver = resolver,
                        ),
                    )
                    assertEquals(higher.expectedCode, result.code)
                }
            }
        }
    }

    @Test
    fun `representation exception participates in canonical refusal priority`() {
        val valid = validOperation()
        val default = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(valid.blob.typeface),
        )
        val resolver = GPUPreparedTextFontResolver {
            GPUPreparedTextFontResolution.ready(
                face = default.face,
                glyphCount = default.glyphCount,
                representationResolver = GPUPreparedTextGlyphRepresentationResolver {
                        glyphId,
                        _,
                        _,
                    ->
                    when (glyphId) {
                        5 -> GPUPreparedTextSourceRepresentation.CBDT_CBLC
                        9 -> error("fixture representation failure")
                        else -> GPUPreparedTextSourceRepresentation.OUTLINE
                    }
                },
            )
        }

        listOf(
            listOf(5.toUShort(), 9.toUShort()),
            listOf(9.toUShort(), 5.toUShort()),
        ).forEach { glyphs ->
            val result = assertIs<GPUPreparedTextLowering.Refused>(
                lower(
                    operation = valid.withRun(
                        glyphs = glyphs,
                        positions = glyphs.indices.map { Point2F32(it.toFloat(), 2f) },
                    ),
                    resolver = resolver,
                ),
            )
            assertEquals(GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED, result.code)
        }
    }

    @Test
    fun `effective position overflow is refused before strike publication`() {
        val result = assertIs<GPUPreparedTextLowering.Refused>(
            lower(
                validOperation().withRun(
                    positions = listOf(Point2F32(Float.MAX_VALUE, Float.MAX_VALUE)),
                ).copy(
                    x = Float.MAX_VALUE,
                    y = Float.MAX_VALUE,
                ),
            ),
        )

        assertEquals(GPUTextRefusalCodes.POSITION_NONFINITE, result.code)
    }

    @Test
    fun `refusal facts are deeply immutable`() {
        val refused = assertIs<GPUPreparedTextLowering.Refused>(
            lower(validOperation().copy(x = Float.NaN)),
        )

        assertEquals("Text origin must be finite", refused.facts["message"])
        @Suppress("UNCHECKED_CAST")
        val mutableFacts = refused.facts as MutableMap<String, String>
        assertFailsWith<UnsupportedOperationException> {
            mutableFacts["message"] = "mutated"
        }
        assertEquals("Text origin must be finite", refused.facts["message"])
    }

    @Test
    fun `canonical authority owns every production text refusal literal`() {
        val projectRoot = java.io.File("..").canonicalFile
        val authorities = listOf(
            projectRoot.resolve(
                "font/core/src/main/kotlin/org/graphiks/kanvas/font/" +
                    "FontTextRefusalCodes.kt",
            ).canonicalFile,
            projectRoot.resolve(
                "font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/" +
                    "GPUTextRouteRefusals.kt",
            ).canonicalFile,
        )
        val diagnosticLiteral = Regex(
            "\"(?:unsupported|dependency)\\.text\\.[^\"]+\"",
        )
        assertTrue(authorities.all(java.io.File::isFile))
        val authorityOwners = LinkedHashMap<String, String>()
        val duplicateOwners = authorities.flatMap { authority ->
            diagnosticLiteral.findAll(authority.readText()).mapNotNull { match ->
                val literal = match.value
                authorityOwners.putIfAbsent(
                    literal,
                    authority.relativeTo(projectRoot).invariantSeparatorsPath,
                )?.let { previousOwner ->
                    "$literal:$previousOwner:${authority.relativeTo(projectRoot).invariantSeparatorsPath}"
                }
            }.toList()
        }
        assertEquals(emptyList(), duplicateOwners)

        val duplicates = listOf("font", "gpu-renderer", "gpu-renderer-scenes", "kanvas")
            .flatMap { module ->
                projectRoot.resolve(module).walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.extension == "kt" &&
                            "/src/main/" in file.invariantSeparatorsPath &&
                            file.canonicalFile !in authorities
                    }
                    .flatMap { file ->
                        diagnosticLiteral.findAll(file.readText()).map { match ->
                            "${file.relativeTo(projectRoot).invariantSeparatorsPath}:${match.value}"
                        }
                    }
                    .toList()
            }

        assertEquals(emptyList(), duplicates)
    }

    private fun validOperation(): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(5u),
                    positions = listOf(Point2F32(2f, 3f)),
                    fontSize = 16f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 16f,
        ),
        x = 1f,
        y = 2f,
        paint = Paint.fill(Color.RED).copy(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.5f),
        ),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun DisplayOp.DrawText.withRun(
        glyphs: List<UShort> = listOf(5u),
        positions: List<Point2F32> = listOf(Point2F32(2f, 3f)),
        fontSize: Float = 16f,
    ): DisplayOp.DrawText = copy(
        blob = blob.copy(
            glyphRuns = listOf(KanvasGlyphRun(glyphs, positions, fontSize)),
        ),
    )

    private fun refusingResolver(code: String): GPUPreparedTextFontResolver =
        GPUPreparedTextFontResolver {
            GPUPreparedTextFontResolution.refused(code, "fixture refusal")
        }

    private fun overridingRepresentation(
        representation: GPUPreparedTextSourceRepresentation,
    ): GPUPreparedTextFontResolver = GPUPreparedTextFontResolver { typeface ->
        when (val resolved = GPUPreparedFontTypefaceResolver.resolve(typeface)) {
            is GPUPreparedTextFontResolution.Refused -> resolved
            is GPUPreparedTextFontResolution.Ready -> GPUPreparedTextFontResolution.ready(
                face = resolved.face,
                glyphCount = resolved.glyphCount,
                representationResolver = GPUPreparedTextGlyphRepresentationResolver { _, _, _ ->
                    representation
                },
            )
        }
    }

    private data object UnsupportedTypeface : Typeface {
        override val fontName: String = "unsupported"
        override fun glyphIdForCodepoint(codepoint: Int): Int = 0
        override fun getAdvance(glyphId: Int, fontSize: Float): Float = 0f
        override fun getGlyphPath(glyphId: Int, fontSize: Float) = null
    }

    private fun lower(
        operation: DisplayOp.DrawText,
        resolver: GPUPreparedTextFontResolver = GPUPreparedFontTypefaceResolver,
    ): GPUPreparedTextLowering = GPUPreparedTextLowerer.lower(
        operation = operation,
        operationIndex = 17,
        target = target(),
        capabilities = capabilities(),
        fontResolver = resolver,
    )
}
