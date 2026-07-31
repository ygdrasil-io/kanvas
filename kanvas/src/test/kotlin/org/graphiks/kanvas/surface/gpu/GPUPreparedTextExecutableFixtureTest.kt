package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.glyph.A8GlyphMask
import org.graphiks.kanvas.glyph.GlyphMaskKey
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialSampledBinding
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialUniformBinding
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectMaterialEvaluationInput
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectMaterialEvaluationResult
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.SimpleRTCPUOracle
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class GPUPreparedTextExecutableFixtureTest {
    @Test
    fun `typed affine scissor complex clip and every common material cross the lowerer`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "task13-material-matrix",
        )
        val affine = Matrix33.makeAll(1f, 0.25f, 3f, -0.125f, 1f, 5f)
        val scissor = ClipStack.DeviceRect(
            Rect.fromLTRB(2f, 3f, 29f, 31f),
            antiAlias = false,
        )
        val complexClip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(
                    Rect.fromLTRB(2f, 2f, 30f, 30f),
                    ClipOp.INTERSECT,
                    antiAlias = false,
                ),
                ClipStackOp.PathOp(
                    Path {
                        moveTo(8f, 8f)
                        lineTo(24f, 8f)
                        lineTo(24f, 24f)
                        lineTo(8f, 24f)
                        close()
                    },
                    ClipOp.INTERSECT,
                    antiAlias = true,
                ),
            ),
        )
        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f, Color.BLUE),
        )
        val image = Image(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "task13-material-image",
            pixels = byteArrayOf(42, 170.toByte(), 85, 255.toByte()),
            alphaType = AlphaType.UNPREMUL,
        )
        val runtime = RuntimeEffect(
            id = "runtime.simple_rt",
            module = ShaderModule.fromSource("registered-only"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
        )
        val materials = linkedMapOf(
            "solid" to Paint.fill(Color.RED),
            "linear" to Paint(shader = Shader.LinearGradient(Point(0f, 0f), Point(16f, 0f), stops)),
            "radial" to Paint(shader = Shader.RadialGradient(Point(8f, 8f), 8f, stops)),
            "sweep" to Paint(shader = Shader.SweepGradient(Point(8f, 8f), stops = stops)),
            "conical" to Paint(
                shader = Shader.ConicalGradient(
                    Point(0f, 0f),
                    1f,
                    Point(16f, 16f),
                    8f,
                    stops,
                ),
            ),
            "runtime" to Paint(
                shader = Shader.RuntimeEffect(
                    runtime,
                    UniformBlock { float4("gColor", 0.25f, 0.5f, 0.75f, 1f) },
                ),
            ),
            "image" to Paint(
                shader = Shader.Image(image, sampling = SamplingOptions.NEAREST),
            ),
        )

        val scissored = assertIs<GPUPreparedTextLowering.Ready>(
            lower(text(typeface, listOf(7), Paint.fill(Color.WHITE), affine, scissor)),
        )
        assertEquals(affine, scissored.draw.transform)
        assertEquals(scissor, scissored.draw.clip)

        val programs = materials.mapValues { (label, paint) ->
            val lowered = lower(text(typeface, listOf(7), paint, affine, complexClip))
            val ready = assertIs<GPUPreparedTextLowering.Ready>(
                lowered,
                "$label: $lowered",
            )
            assertEquals(affine, ready.draw.transform, label)
            assertTrue(
                ready.draw.clip.toGPUClipFacts(target()).coverageRequest != null,
                label,
            )
            assertTrue(ready.draw.material.materialKey.isNotBlank(), label)
            ready.draw.material
        }

        val resolved = assertIs<GPUPreparedRuntimeEffectResolution.Ready>(
            preparedTextMaterialContext(target(), capabilities())
                .runtimeEffectResolver.resolve("runtime.simple_rt", 1),
        )
        assertEquals(SimpleRTWgsl, resolved.program.wgslSource)
        val parsed = parseWgslResult(resolved.program.wgslSource)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { error -> error.message })
        assertTrue(Lowerer().lower(parsed.translationUnit).toString().isNotBlank())
        val uniformBytes = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.25f)
            .putFloat(0.5f)
            .putFloat(0.75f)
            .putFloat(1f)
            .array()
        val cpu = assertIs<GPURuntimeEffectMaterialEvaluationResult.Color>(
            SimpleRTCPUOracle.evaluateMaterial(
                GPURuntimeEffectMaterialEvaluationInput(uniformBytes, 3f, 5f),
            ),
        )
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), listOf(cpu.r, cpu.g, cpu.b, cpu.a))

        val expectations = linkedMapOf(
            "solid" to MaterialExpectation(
                GPUMaterialSourceKind.SolidColor,
                "material:prepared:solidcolor:",
                16,
                0,
            ),
            "linear" to MaterialExpectation(
                GPUMaterialSourceKind.Gradient,
                "material:prepared:gradient:",
                544,
                0,
            ),
            "radial" to MaterialExpectation(
                GPUMaterialSourceKind.Gradient,
                "material:prepared:gradient:",
                528,
                0,
            ),
            "sweep" to MaterialExpectation(
                GPUMaterialSourceKind.Gradient,
                "material:prepared:gradient:",
                544,
                0,
            ),
            "conical" to MaterialExpectation(
                GPUMaterialSourceKind.Gradient,
                "material:prepared:gradient:",
                560,
                0,
            ),
            "runtime" to MaterialExpectation(
                GPUMaterialSourceKind.RuntimeEffect,
                "material:prepared:runtimeeffect:",
                16,
                0,
            ),
            "image" to MaterialExpectation(
                GPUMaterialSourceKind.ImageShader,
                "material:prepared:imageshader:",
                32,
                1,
            ),
        )
        programs.forEach { (label, program) ->
            assertMaterialProgram(label, program, expectations.getValue(label))
        }
        assertDistinctGradientPrograms(
            listOf("linear", "radial", "sweep", "conical")
                .associateWith(programs::getValue),
        )

        val runtimeProgram = programs.getValue("runtime")
        assertTrue(runtimeProgram.wgslSource.startsWith(resolved.program.wgslSource))
        assertTrue(
            runtimeProgram.composableFragment.declarationsWgsl
                .contains(resolved.program.wgslSource.trim()),
        )
        assertEquals("fs_main", runtimeProgram.entryPoint)
        assertEquals("runtime.simple_rt", resolved.program.effectId)
        assertEquals(1, resolved.program.descriptorVersion)
        assertEquals(SimpleRTEntryPoint, resolved.program.sourceFunction)
        assertEquals(16, resolved.program.uniformBlockSizeBytes)
        val registeredField = resolved.program.uniformFields.single()
        assertEquals("gColor", registeredField.name)
        assertEquals(0, registeredField.offsetBytes)
        assertEquals(16, registeredField.sizeBytes)
        assertEquals(16, registeredField.alignmentBytes)
        val registeredBinding = resolved.program.bindings.single()
        assertEquals(1, registeredBinding.group)
        assertEquals(0, registeredBinding.binding)
        assertEquals("uniformBuffer", registeredBinding.resourceKind)
        assertEquals(16, registeredBinding.minBindingSizeBytes)
        val parsedRuntimeProgram = parseWgslResult(runtimeProgram.wgslSource)
        assertTrue(
            parsedRuntimeProgram.isSuccess,
            parsedRuntimeProgram.errors.joinToString { error -> error.message },
        )
        assertEquals(
            setOf("vs_main", "fs_main"),
            Lowerer().lower(parsedRuntimeProgram.translationUnit).entryPoints
                .map { entry -> entry.name }
                .toSet(),
        )
        assertEquals(
            listOf(
                0x00, 0x00, 0x80, 0x3e,
                0x00, 0x00, 0x00, 0x3f,
                0x00, 0x00, 0x40, 0x3f,
                0x00, 0x00, 0x80, 0x3f,
            ),
            runtimeProgram.uniformBytes,
        )
        assertEquals(
            GPUPreparedMaterialUniformBinding(group = 1, binding = 0, minBindingSizeBytes = 16),
            runtimeProgram.composableFragment.uniformBinding,
        )
        assertTrue(runtimeProgram.composableFragment.sampledBindings.isEmpty())

        val imageProgram = programs.getValue("image")
        assertEquals(
            listOf(
                GPUPreparedMaterialSampledBinding(
                    resourceIndex = 0,
                    textureGroup = 1,
                    textureBinding = 1,
                    samplerGroup = 1,
                    samplerBinding = 2,
                ),
            ),
            imageProgram.composableFragment.sampledBindings,
        )
        val sampledImage = imageProgram.sampledResources.single()
        assertEquals(1, sampledImage.width)
        assertEquals(1, sampledImage.height)
        assertEquals("nearest", sampledImage.samplingFilterMode)
        assertEquals(false, sampledImage.alphaOnly)
        assertContentEquals(
            byteArrayOf(42, 170.toByte(), 85, 255.toByte()),
            sampledImage.rgba8Bytes(),
        )
        assertTrue(sampledImage.resourceKey.startsWith("sampled-material:"))

        // Mutation check: replacing any admitted non-solid family by Solid must fail.
        expectations.keys.filterNot { it == "solid" }.forEach { label ->
            assertFailsWith<AssertionError>(label) {
                assertMaterialProgram(
                    label,
                    programs.getValue("solid"),
                    expectations.getValue(label),
                )
            }
        }
        assertFailsWith<AssertionError>("collapsing every gradient to linear must fail") {
            assertDistinctGradientPrograms(
                listOf("linear", "radial", "sweep", "conical")
                    .associateWith { programs.getValue("linear") },
            )
        }
    }

    @Test
    fun `typed stroke and every blur style cross common preparation authorities`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "task13-stroke-blur-matrix",
        )
        val strokes = listOf(
            Paint.stroke(Color.RED, 3f).copy(
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                pathEffect = PathEffect.Dash(floatArrayOf(4f, 2f)),
            ),
            Paint.stroke(Color.GREEN, 3f).copy(
                strokeCap = StrokeCap.ROUND,
                strokeJoin = StrokeJoin.ROUND,
                pathEffect = PathEffect.Dash(floatArrayOf(3f, 1f)),
            ),
            Paint.stroke(Color.BLUE, 3f).copy(
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.BEVEL,
            ),
        )
        strokes.forEach { paint ->
            val prepared = prepare(text(typeface, listOf(7), paint))
            assertEquals(1, prepared.inventory.metrics.pathStrokeDrawCount)
            val path = assertIs<NormalizedDrawCommand.FillPath>(
                prepared.mapping.visualCommands.single().normalized,
            )
            assertEquals("drawText.stroke-path", path.source.operation)
            assertEquals(paint.strokeCap.name.lowercase(), path.strokeCap)
            assertEquals(paint.strokeJoin.name.lowercase(), path.strokeJoin)
            assertContentEquals(
                (paint.pathEffect as? PathEffect.Dash)?.intervals,
                path.dashIntervals,
            )
        }

        val hashes = BlurStyle.entries.map { style ->
            val prepared = prepare(
                text(
                    typeface,
                    listOf(7),
                    Paint.fill(Color.WHITE).copy(
                        maskFilter = MaskFilter.Blur(style, sigma = 0.75f),
                    ),
                ),
            )
            assertEquals(1, prepared.inventory.metrics.a8InstanceCount)
            prepared.inventory.contentSha256
        }
        assertEquals(4, hashes.distinct().size)
    }

    @Test
    fun `typed TTC faces emoji routes and notdef presence use the exact font resolver`() {
        val faces = GPUPreparedTextTestFixtures.fontFaces()
        assertEquals(listOf(0, 1), faces.map(FontTypeface::faceIndex))
        faces.forEachIndexed { index, face ->
            val ready = assertIs<GPUPreparedTextLowering.Ready>(
                lower(text(face, listOf(7), Paint.fill(Color.WHITE)), operationIndex = index),
            )
            assertEquals(index, ready.draw.face.faceIndex)
        }

        val colorFace = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "task13-emoji-routes",
        )
        val colorResolution = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(colorFace),
        )
        assertEquals(
            GPUPreparedTextSourceRepresentation.OUTLINE,
            colorResolution.representationResolver.resolve(7, 48f, emptyMap()),
        )
        assertEquals(
            GPUPreparedTextSourceRepresentation.COLRV0,
            colorResolution.representationResolver.resolve(2, 48f, emptyMap()),
        )

        val notdefFace = liberationTypeface()
        val withNotdef = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(notdefFace),
        )
        val withoutNotdef = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(
                GPUPreparedTextTestFixtures.fontWithoutNotdef(notdefFace),
            ),
        )
        assertNotEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            withNotdef.representationResolver.resolve(0, 48f, emptyMap()),
        )
        assertEquals(
            GPUPreparedTextSourceRepresentation.MISSING,
            withoutNotdef.representationResolver.resolve(0, 48f, emptyMap()),
        )
    }

    @Test
    fun `diagonal AA bytes and repeated glyphs produce two shared masks on one real page`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "task13-diagonal-page-sharing",
        )
        val glyphIds = GPUPreparedTextTestFixtures.repeatedGlyphPageSharing()
        val lowered = assertIs<GPUPreparedTextLowering.Ready>(
            lower(text(typeface, glyphIds, Paint.fill(Color.WHITE))),
        )
        val diagonal = GPUPreparedTextTestFixtures.diagonalAntialiasedGlyph()
        val result = assertIs<PreparedTextFrameInventoryResult.Ready>(
            PreparedTextFrameInventoryBuilder.build(
                draws = listOf(lowered.draw),
                generation = GPUTextArtifactGeneration(1),
                limits = limits(),
                artifactResolver = PreparedTextGlyphArtifactResolver { draw, glyphIndex, _ ->
                    val glyph = draw.glyphs[glyphIndex]
                    val pixels = if (glyph.glyphId == 7) {
                        diagonal.map { byte -> byte.toInt() and 0xff }
                    } else {
                        diagonal.reversed().map { byte -> byte.toInt() and 0xff }
                    }
                    val hashDigit = if (glyph.glyphId == 7) "7" else "8"
                    val mask = A8GlyphMask(
                        glyphId = glyph.glyphId,
                        width = 4,
                        height = 4,
                        pixels = pixels,
                        sourceOutlineSha256 = hashDigit.repeat(64),
                    )
                    PreparedTextGlyphArtifact.A8(
                        mask,
                        GlyphMaskKey(
                            glyph.strikeKey,
                            draw.face.faceIndex,
                            checkNotNull(mask.sourceOutlineSha256),
                        ),
                    )
                },
            ),
        )
        val inventory = result.inventory
        assertEquals(4, inventory.metrics.instanceCount)
        assertEquals(2, inventory.metrics.uniqueMaskCount)
        assertEquals(1, inventory.metrics.pageCount)
        val page = inventory.pages.single()
        assertEquals(2, page.placements.size)
        val diagonalPlacement = page.placements.first()
        val rect = diagonalPlacement.contentRect
        val packedDiagonal = buildList {
            for (y in rect.top until rect.bottom) {
                for (x in rect.left until rect.right) add(page.bytes[y * page.rowBytes + x])
            }
        }
        assertEquals(diagonal.map { byte -> byte.toInt() and 0xff }, packedDiagonal)
        assertEquals(
            listOf(7, 7, 8, 7),
            inventory.subRunsByOperationIndex.getValue(0).single().instances
                .map { instance -> instance.glyphId },
        )
    }

    private fun lower(
        operation: DisplayOp.DrawText,
        operationIndex: Int = 0,
    ): GPUPreparedTextLowering = GPUPreparedTextLowerer.lower(
        operation,
        operationIndex,
        target(),
        capabilities(),
    )

    private fun prepare(operation: DisplayOp.DrawText): GPUPreparedTextFramePreparation.Ready =
        assertIs(
            GPUPreparedTextFramePreparer.prepare(
                listOf(operation),
                target(),
                RenderConfig.DEFAULT,
                capabilities(),
                GPUTextArtifactGeneration(1),
            ),
        )

    private fun text(
        typeface: FontTypeface,
        glyphIds: List<Int>,
        paint: Paint,
        transform: Matrix33 = Matrix33.identity(),
        clip: ClipStack = ClipStack.WideOpen,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        TextBlob(
            listOf(
                KanvasGlyphRun(
                    glyphIds.map(Int::toUShort),
                    glyphIds.indices.map { index -> Point(index * 12f, 0f) },
                    fontSize = 48f,
                ),
            ),
            typeface,
            48f,
        ),
        4f,
        58f,
        paint,
        transform,
        clip,
    )

    private fun limits() = PreparedTextFrameInventoryLimits(
        pageWidth = 32,
        pageHeight = 32,
        maxPages = 1,
        maxPageBytes = 1_024,
        maxTotalPageBytes = 1_024,
        maxGlyphs = 16,
        maxInstances = 16,
        maxSubRuns = 16,
        maxInstanceBytes = 4_096,
        maxTextureDimension2D = 32,
    )

    private fun assertMaterialProgram(
        label: String,
        program: GPUPreparedMaterialProgram,
        expected: MaterialExpectation,
    ) {
        assertEquals(expected.sourceKind, program.sourceKind, label)
        assertTrue(program.materialKey.startsWith(expected.materialKeyPrefix), label)
        assertTrue(program.abiHash.matches(Regex("sha256:[0-9a-f]{64}")), label)
        assertEquals("fs_main", program.entryPoint, label)
        assertEquals(expected.uniformByteCount, program.uniformBytes.size, label)
        assertEquals(expected.sampledResourceCount, program.sampledResources.size, label)
        assertEquals(
            expected.sampledResourceCount,
            program.composableFragment.sampledBindings.size,
            label,
        )
        assertEquals(
            GPUPreparedMaterialUniformBinding(
                group = 1,
                binding = 0,
                minBindingSizeBytes = expected.uniformByteCount,
            ),
            program.composableFragment.uniformBinding,
            label,
        )
    }

    private fun assertDistinctGradientPrograms(
        programs: Map<String, GPUPreparedMaterialProgram>,
    ) {
        assertEquals(4, programs.values.map { it.materialKey }.distinct().size)
        assertEquals(4, programs.values.map { it.wgslSource }.distinct().size)
        assertEquals(
            4,
            programs.values.map { it.composableFragment.declarationsWgsl }.distinct().size,
        )
        assertEquals(4, programs.values.map { it.abiHash }.distinct().size)
    }

    private data class MaterialExpectation(
        val sourceKind: GPUMaterialSourceKind,
        val materialKeyPrefix: String,
        val uniformByteCount: Int,
        val sampledResourceCount: Int,
    )
}
