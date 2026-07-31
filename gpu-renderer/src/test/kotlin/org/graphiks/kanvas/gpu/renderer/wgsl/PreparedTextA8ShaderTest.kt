package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextShaderComposer
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class PreparedTextA8ShaderTest {

    @Test
    fun `text uses the exact 64 byte instance vertex ABI`() {
        assertEquals(64L, PreparedTextA8Shader.VertexLayout.arrayStrideBytes)
        assertEquals("Instance", PreparedTextA8Shader.VertexLayout.stepMode)
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            PreparedTextA8Shader.VertexLayout.attributes.map { it.location },
        )
        assertEquals(
            listOf(0L, 8L, 16L, 24L, 32L),
            PreparedTextA8Shader.VertexLayout.attributes.map { it.offsetBytes },
        )
        assertEquals(
            listOf("Float32x2", "Float32x2", "Float32x2", "Float32x2", "Float32x4"),
            PreparedTextA8Shader.VertexLayout.attributes.map { it.format },
        )
        assertFalse(PreparedTextA8Shader.vertexWgsl.contains("var<storage"))
    }

    @Test
    fun `vertex layout attributes are an immutable snapshot`() {
        val mutableAttributes = mutableListOf(
            GPUPreparedTextVertexAttribute(0, 0L, "Float32x2"),
        )
        val layout = GPUPreparedTextVertexLayout(64L, "Instance", mutableAttributes)

        mutableAttributes.clear()
        assertEquals(listOf(0), layout.attributes.map { it.location })
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (layout.attributes as MutableList<GPUPreparedTextVertexAttribute>).clear()
            }.isFailure,
        )
    }

    @Test
    fun `skewed quad preserves TL TR BR TL BR BL and UV LTRB`() {
        val vertices = PreparedTextA8Shader.vertexOracle(
            deviceQuad = listOf(10f, 10f, 30f, 12f, 28f, 32f, 8f, 30f),
            uvLTRB = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetWidth = 100f,
            targetHeight = 50f,
            deviceToLocal = identityAffine(),
        )

        assertEquals(
            listOf(
                10f to 10f,
                30f to 12f,
                28f to 32f,
                10f to 10f,
                28f to 32f,
                8f to 30f,
            ),
            vertices.map { it.deviceX to it.deviceY },
        )
        assertEquals(
            listOf(
                0.25f to 0.5f,
                0.75f to 0.5f,
                0.75f to 1f,
                0.25f to 0.5f,
                0.75f to 1f,
                0.25f to 1f,
            ),
            vertices.map { it.uvX to it.uvY },
        )
        assertEquals(-1f to 1f, PreparedTextA8Shader.deviceToNdc(0f, 0f, 100f, 50f))
        assertEquals(1f to -1f, PreparedTextA8Shader.deviceToNdc(100f, 50f, 100f, 50f))
    }

    @Test
    fun `affine local coordinates remain continuous across glyph quads`() {
        val affine = listOf(
            2f, 0.5f, 3f,
            -0.25f, 1.5f, -4f,
        )
        val firstGlyph = PreparedTextA8Shader.vertexOracle(
            deviceQuad = listOf(10f, 20f, 14f, 20f, 14f, 28f, 10f, 28f),
            uvLTRB = listOf(0f, 0f, 0.5f, 1f),
            targetWidth = 100f,
            targetHeight = 50f,
            deviceToLocal = affine,
        )
        val secondGlyph = PreparedTextA8Shader.vertexOracle(
            deviceQuad = listOf(18f, 20f, 22f, 20f, 22f, 28f, 18f, 28f),
            uvLTRB = listOf(0.5f, 0f, 1f, 1f),
            targetWidth = 100f,
            targetHeight = 50f,
            deviceToLocal = affine,
        )

        assertEquals(33f to 23.5f, firstGlyph.first().let { it.localX to it.localY })
        assertEquals(49f to 21.5f, secondGlyph.first().let { it.localX to it.localY })
        assertEquals(
            16f to -2f,
            (secondGlyph.first().localX - firstGlyph.first().localX) to
                (secondGlyph.first().localY - firstGlyph.first().localY),
        )
    }

    @Test
    fun `invalid vertex oracle inputs are rejected instead of producing undefined coordinates`() {
        val validQuad = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val validUv = listOf(0f, 0f, 1f, 1f)

        assertTrue(
            runCatching {
                PreparedTextA8Shader.vertexOracle(
                    validQuad.dropLast(1),
                    validUv,
                    10f,
                    10f,
                    identityAffine(),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                PreparedTextA8Shader.vertexOracle(
                    validQuad,
                    validUv,
                    0f,
                    10f,
                    identityAffine(),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                PreparedTextA8Shader.vertexOracle(
                    validQuad,
                    validUv,
                    10f,
                    10f,
                    identityAffine().dropLast(1),
                )
            }.isFailure,
        )
    }

    @Test
    fun `final composed module parses lowers and reflects both entry points`() {
        val source = composedProgram().wgslSource
        val parsed = parseWgslResult(source)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })

        val lowered = Lowerer().lower(parsed.translationUnit)
        val report = lowered.reflectWgslModule(sourceId = "prepared-text-test")
        assertEquals(
            listOf("vs_main" to "vertex", "fs_main" to "fragment"),
            report.entryPoints.map { it.name to it.stage },
        )
        assertTrue(report.validation.success)
        assertTrue(report.unsupportedFeatures.isEmpty())
    }

    @Test
    fun `all four partial alpha encodings parse lower and retain reflected uniform80 ABI`() {
        listOf(
            GPUSourceCoverageEncoding.Coverage,
            GPUSourceCoverageEncoding.ModulateRGBA,
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceAlpha,
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceRGBA,
        ).forEach { encoding ->
            val program = composedProgram(encoding)
            val parsed = parseWgslResult(program.wgslSource)
            assertTrue(parsed.isSuccess, "$encoding: ${parsed.errors.joinToString { it.message }}")
            val report = Lowerer().lower(parsed.translationUnit)
                .reflectWgslModule(sourceId = "prepared-text-partial-alpha-${encoding.name}")

            assertEquals(encoding, program.sourceCoverageEncoding)
            assertEquals(
                listOf("vs_main" to "vertex", "fs_main" to "fragment"),
                report.entryPoints.map { it.name to it.stage },
                encoding.name,
            )
            val drawUniform = report.layouts.single {
                it.structName == "PreparedTextDrawUniforms"
            }
            assertEquals(80, drawUniform.size, encoding.name)
            assertEquals(5, drawUniform.members.size, encoding.name)
            assertTrue(report.validation.success, encoding.name)
            assertTrue(report.unsupportedFeatures.isEmpty(), encoding.name)
        }
    }

    @Test
    fun `vertex stage converts device pixels to NDC and never double applies firstInstance`() {
        val source = composedProgram().wgslSource
        val vertex = source.substringBefore("@fragment")

        assertTrue("devicePosition.x / drawUniforms.targetSizeAndPaintAlpha.x" in vertex)
        assertTrue("drawUniforms.targetSizeAndPaintAlpha.y" in vertex)
        assertTrue("1.0 - devicePosition.y" in vertex)
        assertFalse("instance_index" in vertex)
        assertFalse("instanceIndex" in vertex)
        assertFalse("firstInstance" in vertex)
    }

    @Test
    fun `fragment constructs coverage alpha and prepared source independently exactly once`() {
        val source = composedProgram().wgslSource
        val fragment = source.substringAfter("@fragment")

        assertEquals(
            1,
            Regex("""textureSample\s*\(\s*textAtlas\s*,\s*textSampler\s*,\s*input\.uv\s*\)\.r""")
                .findAll(fragment)
                .count(),
        )
        assertEquals(1, Regex("""\bglyphCoverage\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bclipCoverage\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bcoverageFactor\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bpaintAlpha\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bpreparedSource\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bmaterialPremul\b""").findAll(fragment).count() - 1)
        assertEquals(
            1,
            Regex(
                """clamp\s*\(\s*drawUniforms\.targetSizeAndPaintAlpha\.z\s*,\s*""" +
                    """0\.0\s*,\s*1\.0\s*\)""",
            ).findAll(fragment).count(),
        )
        assertEquals(
            1,
            Regex("""drawUniforms\.targetSizeAndPaintAlpha\.z""").findAll(fragment).count(),
        )
        assertTrue("let coverageFactor = glyphCoverage * clipCoverage;" in fragment)
        assertTrue(
            "let paintAlpha = clamp(drawUniforms.targetSizeAndPaintAlpha.z, 0.0, 1.0);" in fragment,
        )
        assertTrue("let preparedSource = materialPremul * paintAlpha;" in fragment)
        assertFalse(
            fragment.lineSequence().single { "let coverageFactor =" in it }.contains("paintAlpha"),
        )
        assertTrue("return coverageFactor * preparedSource;" in fragment)
    }

    @Test
    fun `fixed blend source coverage encodings consume independent F and S formulas`() {
        val fragments = mapOf(
            GPUSourceCoverageEncoding.Coverage to
                "return vec4<f32>(coverageFactor);",
            GPUSourceCoverageEncoding.ModulateRGBA to
                "return coverageFactor * preparedSource;",
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceAlpha to
                "return vec4<f32>(coverageFactor * (1.0 - preparedSource.a));",
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceRGBA to
                "return coverageFactor * (vec4<f32>(1.0) - preparedSource);",
        )

        fragments.forEach { (encoding, exactReturn) ->
            val fragment = PreparedTextA8Shader.fragmentWgsl(encoding)
            assertTrue("let coverageFactor = glyphCoverage * clipCoverage;" in fragment)
            assertTrue(
                "let paintAlpha = clamp(drawUniforms.targetSizeAndPaintAlpha.z, 0.0, 1.0);" in fragment,
            )
            assertTrue("let preparedSource = materialPremul * paintAlpha;" in fragment)
            assertTrue(exactReturn in fragment, encoding.name)
            assertEquals(
                1,
                fragments.values.count { candidate -> candidate in fragment },
                encoding.name,
            )
        }
        assertEquals(4, fragments.keys.map(PreparedTextA8Shader::fragmentWgsl).distinct().size)
    }

    @Test
    fun `analytic clip multiplies glyph coverage exactly once before source coverage encoding`() {
        val fragment = PreparedTextA8Shader.fragmentWgsl(
            GPUSourceCoverageEncoding.ModulateRGBA,
            GPUPreparedTextClipVariant.AnalyticRectAA,
        )

        assertTrue("let clipCoverage = prepared_text_rect_coverage(input.position.xy);" in fragment)
        assertTrue("let coverageFactor = glyphCoverage * clipCoverage;" in fragment)
        assertTrue(
            fragment.indexOf("let coverageFactor = glyphCoverage * clipCoverage;") <
                fragment.indexOf("return coverageFactor * preparedSource;"),
        )
        assertEquals(1, Regex("""\bglyphCoverage\b""").findAll(fragment).count() - 1)
        assertEquals(1, Regex("""\bclipCoverage\b""").findAll(fragment).count() - 1)
        assertFalse("clipVariant" in fragment)
    }

    private fun composedProgram(
        sourceCoverageEncoding: GPUSourceCoverageEncoding =
            GPUSourceCoverageEncoding.ModulateRGBA,
    ): GPUPreparedTextCompositeProgram {
        val material = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(
                descriptor = GPUMaterialDescriptor.LinearGradient(
                    startX = 0f,
                    startY = 0f,
                    endX = 32f,
                    endY = 8f,
                    startR = 1f,
                    startG = 0f,
                    startB = 0f,
                    startA = 0.25f,
                    endR = 0f,
                    endG = 0f,
                    endB = 1f,
                    endA = 0.75f,
                    allStopPositions = floatArrayOf(0f, 1f),
                    allStopColors =
                        floatArrayOf(1f, 0f, 0f, 0.25f, 0f, 0f, 1f, 0.75f),
                ),
                paintAlpha = 0.6f,
                context = GPUMaterialLoweringContext(
                    capabilityClass = "prepared-text-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:prepared-text-test:v1",
                ),
            ),
        ).program
        return assertIs<GPUPreparedTextCompositeProgramResult.Ready>(
            GPUPreparedTextShaderComposer.compose(
                material = material,
                targetFormatClass = "rgba8unorm",
                blendPlanIdentity = "fixed-function:src-over:premul",
                sourceCoverageEncoding = sourceCoverageEncoding,
            ),
        ).program
    }

    private fun identityAffine(): List<Float> =
        listOf(1f, 0f, 0f, 0f, 1f, 0f)
}
