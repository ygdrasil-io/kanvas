package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
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
import org.graphiks.kanvas.gpu.renderer.state.GPUSourceAlphaClassification
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
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
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
        val affine = Matrix3x3F32.of(1f, 0.25f, 3f, -0.125f, 1f, 5f)
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
            "linear" to Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(16f, 0f), stops)),
            "radial" to Paint(shader = Shader.RadialGradient(Point2F32(8f, 8f), 8f, stops)),
            "sweep" to Paint(shader = Shader.SweepGradient(Point2F32(8f, 8f), stops = stops)),
            "conical" to Paint(
                shader = Shader.ConicalGradient(
                    Point2F32(0f, 0f),
                    1f,
                    Point2F32(16f, 16f),
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

        // Recorded once from this stable typed fixture after pre-coverage source alpha became
        // an admitted material-key/ABI fact, then hand-checked against all seven WGSL families.
        // No expected identity below is derived by the compiler under test.
        val expectations = linkedMapOf(
            "solid" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.SolidColor,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.ProvenOpaque,
                materialKey =
                        "material:prepared:solidcolor:" +
                        "f64dd51786077c9d73124e924f58df11ae996dc8101d0ea007c0436f4ca72ec4",
                abiHash =
                    "sha256:66f83e111e3c9b0c2a50fdbddd67ffe4c84d5679380b9a9b438559d667b7be28",
                wgslSha256 =
                    "4b1404cd4bf7aef896ae03b4f1ee152d8cf58938d0d1c30112147a72167df8cc",
                fragmentHash =
                    "a53fc090f5294e03d7e6f78af14a7d6298b70daf5199ab075103374110c256f3",
                fragmentAbiHash =
                    "sha256:cebaa20716f6f1f9629b499bb2d2246e734ef50fac556676491d75ef6059b563",
                finalSourceMarker = "fn solid_source(uv: vec2<f32>) -> vec4<f32> {",
                composableSourceMarker = "return solidMaterial.color;",
                uniformByteCount = 16,
                sampledResourceCount = 0,
            ),
            "linear" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.Gradient,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:gradient:" +
                        "7d48819c0799accb64a68c6e57d5fe994649a7f968add2d7e35e65628495debf",
                abiHash =
                    "sha256:fbe672e92ca68cfc96c0b7c609f4ff7534dbf1113715093fb02e6302131b28be",
                wgslSha256 =
                    "3135797b82879597ea6bea1c3a583180ef37efc82d29fd16aaed5b5a9269cc77",
                fragmentHash =
                    "cad682f065b1247bfe8f7cfd234ab424ac54a28779ca0be6a691aebccf94bcfe",
                fragmentAbiHash =
                    "sha256:082ec8a902a93d4e99874345f38a3c2b8a066179fddbdb91e0bd7112f0057b7c",
                finalSourceMarker = "let lenSq = dot(dir, dir);",
                composableSourceMarker = "let lenSq = dot(dir, dir);",
                uniformByteCount = 544,
                sampledResourceCount = 0,
            ),
            "radial" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.Gradient,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:gradient:" +
                        "9f74f89a5745eecc74cb4475d149fb34702e9ab02e07d32227426123dcacd460",
                abiHash =
                    "sha256:d298ac7e63b5249d292b4ba6afe0ec0db0af81a6dc37cb5531db685188d8a9ca",
                wgslSha256 =
                    "c6d95da0614bad81bee5a9c51f93bb95f4f34a5b4584a26ae4380e1567f9624a",
                fragmentHash =
                    "db6ed7c4edb048b3a5b492ffaff942473fed9744f6405f86385c7d2c0966514d",
                fragmentAbiHash =
                    "sha256:9d69f9696464ce0fb4192d210b800d2dd8b35bc2f15d2cc4ad58f169975c4d02",
                finalSourceMarker = "t_raw = length(d) / gradient.radius;",
                composableSourceMarker = "t_raw = length(d) / gradient.radius;",
                uniformByteCount = 528,
                sampledResourceCount = 0,
            ),
            "sweep" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.Gradient,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:gradient:" +
                        "f6a0d3cc43837653e525e9eb45fe2ca64ac0ec6130cf6cb1791278b60282e592",
                abiHash =
                    "sha256:64e55cf562330cd6ddc527823c1dc57f98ed539b5844c00719efbb92f067349b",
                wgslSha256 =
                    "4a85b68d2661aaf863faecb5263d46255007cd5f06ad2ff7cc81bfe4182d8596",
                fragmentHash =
                    "1d7a8717541cc0942aecc7cd77c083a2b47f81c66c1e322e640c0f5d6d76a96e",
                fragmentAbiHash =
                    "sha256:19fe58b296b486e7fec11c509dfd216c16c681e4b9cbdea83178237debfe2fe4",
                finalSourceMarker = "const TWO_PI: f32 = 6.2831853071795864;",
                composableSourceMarker = "const TWO_PI: f32 = 6.2831853071795864;",
                uniformByteCount = 544,
                sampledResourceCount = 0,
            ),
            "conical" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.Gradient,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:gradient:" +
                        "a86984997d7211b50460602972f4c49ba5a7b907631241d20375e392bc86881f",
                abiHash =
                    "sha256:f58efe65ece9b35aa72db002ac56372f17b613027eba8088564ee25fdfafd85b",
                wgslSha256 =
                    "3fcf60a17773dccfb1c8931875d13ec5a16f730b7f2ae8e897981c046e5f9038",
                fragmentHash =
                    "88a0a418c7d217b7c1f569f05db80b870d0c6dc2d0097bbaed26548bab8cfccd",
                fragmentAbiHash =
                    "sha256:745951cb48eeb6d7b53ad164534408826518e2febb28a7106deae4213891e159",
                finalSourceMarker = "let A = dx*dx + dy*dy - dr*dr;",
                composableSourceMarker = "let A = dx*dx + dy*dy - dr*dr;",
                uniformByteCount = 560,
                sampledResourceCount = 0,
            ),
            "runtime" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.RuntimeEffect,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:runtimeeffect:" +
                        "a6b5762789a92257a0a9ae3ea9ff9b8f11533e5118aea1ff1671c818f6c0aebb",
                abiHash =
                    "sha256:b8e2d81dd2d56319e34b0adeb702ccba41a46bf72a5f2f2434058358a604d86d",
                wgslSha256 =
                    "875c3a6bae0022c75098b84981a699d1cbe5632d2d2ef0ce8045dc706978988c",
                fragmentHash =
                    "6da63b8c0c7ae4facd267248db6f6a1b91771b860e1fa24a02be2ca5463bc774",
                fragmentAbiHash =
                    "sha256:f4f9dae638b5b3503cd71ce47fc9a192d2785955a0e398e2158723d76c79e0ac",
                finalSourceMarker =
                    "fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {",
                composableSourceMarker =
                    "fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {",
                uniformByteCount = 16,
                sampledResourceCount = 0,
            ),
            "image" to MaterialExpectation(
                sourceKind = GPUMaterialSourceKind.ImageShader,
                preCoverageSourceAlpha = GPUSourceAlphaClassification.Translucent,
                materialKey =
                        "material:prepared:imageshader:" +
                        "d787b7887941a35099f2e38c11a4422803cbe7c982d7a2d7bee1614a56234037",
                abiHash =
                    "sha256:c64c2245a0331543f8715af27aee7f7e1b2e98487a03a985893a064abacbd85e",
                wgslSha256 =
                    "7337b56fac8b54a56df024cce5bedb65d9a871abaadf3c209c823375fc006683",
                fragmentHash =
                    "6adabce4713b8ade386e0b44991f3ec97b6d8b14901e96d94f293291a8a4e9eb",
                fragmentAbiHash =
                    "sha256:f7a304a6cd238e398d6bfe45e437dd9fe4a0466c9d86e18558310ca11825d507",
                finalSourceMarker = "let sampled = bitmap_shader_clamp(input.uv);",
                composableSourceMarker =
                    "let sampled = bitmap_shader_clamp(localPosition);",
                uniformByteCount = 32,
                sampledResourceCount = 1,
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
        assertTrue(runtimeProgram.wgslSource.startsWith(resolved.program.wgslSource.trim()))
        assertTrue(
            runtimeProgram.composableFragment.declarationsWgsl
                .contains(resolved.program.wgslSource.trim()),
        )
        assertEquals("fs_main", runtimeProgram.entryPoint)
        assertEquals("runtime.simple_rt", resolved.program.effectId)
        assertEquals(1, resolved.program.descriptorVersion)
        assertEquals("simple_rt_source", SimpleRTEntryPoint)
        assertEquals("simple_rt_source", resolved.program.sourceFunction)
        assertEquals(16, resolved.program.uniformBlockSizeBytes)
        val runtimeFinalInvocation = """
            @fragment
            fn fs_main(input: PreparedMaterialVertexOutput) -> @location(0) vec4<f32> {
                return simple_rt_source(input.uv);
            }
        """.trimIndent()
        val runtimeComposableInvocation = """
            fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                return simple_rt_source(localPosition);
            }
        """.trimIndent()
        assertRuntimeInvocation(runtimeProgram.wgslSource, runtimeFinalInvocation)
        assertRuntimeInvocation(
            runtimeProgram.composableFragment.declarationsWgsl,
            runtimeComposableInvocation,
        )
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
        val runtimeWithoutInvocation = runtimeProgram.wgslSource.replace(
            "return simple_rt_source(input.uv);",
            "return vec4<f32>(0.0);",
        )
        assertNotEquals(runtimeProgram.wgslSource, runtimeWithoutInvocation)
        assertTrue(runtimeWithoutInvocation.startsWith(resolved.program.wgslSource.trim()))
        val parsedRuntimeWithoutInvocation = parseWgslResult(runtimeWithoutInvocation)
        assertTrue(
            parsedRuntimeWithoutInvocation.isSuccess,
            parsedRuntimeWithoutInvocation.errors.joinToString { error -> error.message },
        )
        assertFailsWith<AssertionError>(
            "registered SimpleRT source without its final invocation must fail",
        ) {
            assertRuntimeInvocation(runtimeWithoutInvocation, runtimeFinalInvocation)
        }

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
        assertFailsWith<AssertionError>("exchanging linear and sweep must fail") {
            assertMaterialProgram(
                "linear",
                programs.getValue("sweep"),
                expectations.getValue("linear"),
            )
        }
        assertFailsWith<AssertionError>("four distinct but rotated gradients must fail") {
            val rotated = linkedMapOf(
                "linear" to programs.getValue("sweep"),
                "radial" to programs.getValue("conical"),
                "sweep" to programs.getValue("linear"),
                "conical" to programs.getValue("radial"),
            )
            assertDistinctGradientPrograms(rotated)
            rotated.forEach { (label, program) ->
                assertMaterialProgram(label, program, expectations.getValue(label))
            }
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
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStack = ClipStack.WideOpen,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        TextBlob(
            listOf(
                KanvasGlyphRun(
                    glyphIds.map(Int::toUShort),
                    glyphIds.indices.map { index -> Point2F32(index * 12f, 0f) },
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
        assertEquals(
            expected.preCoverageSourceAlpha,
            program.preCoverageSourceAlpha,
            label,
        )
        assertEquals(expected.materialKey, program.materialKey, label)
        assertEquals(expected.abiHash, program.abiHash, label)
        assertEquals(expected.wgslSha256, sha256(program.wgslSource), label)
        assertEquals(
            expected.fragmentHash,
            program.composableFragment.fragmentHash,
            label,
        )
        assertEquals(
            expected.fragmentAbiHash,
            program.composableFragment.abiHash,
            label,
        )
        assertTrue(program.wgslSource.contains(expected.finalSourceMarker), label)
        assertTrue(
            program.composableFragment.declarationsWgsl
                .contains(expected.composableSourceMarker),
            label,
        )
        assertTrue(
            program.composableFragment.declarationsWgsl.contains(
                "fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {",
            ),
            label,
        )
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

    private fun assertRuntimeInvocation(source: String, exactCallSite: String) {
        val normalizedSource = source.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
        val normalizedCallSite = exactCallSite.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
        assertTrue(normalizedSource.contains(normalizedCallSite))
    }

    private fun sha256(source: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(source.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
        val preCoverageSourceAlpha: GPUSourceAlphaClassification,
        val materialKey: String,
        val abiHash: String,
        val wgslSha256: String,
        val fragmentHash: String,
        val fragmentAbiHash: String,
        val finalSourceMarker: String,
        val composableSourceMarker: String,
        val uniformByteCount: Int,
        val sampledResourceCount: Int,
    )
}
