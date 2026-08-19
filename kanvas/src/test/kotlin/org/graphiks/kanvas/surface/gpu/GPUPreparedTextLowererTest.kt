package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import kotlin.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ChildSlot
import org.graphiks.kanvas.pipeline.ChildType
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.TextureSlot
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.pipeline.UniformSlot
import org.graphiks.kanvas.pipeline.UniformType
import org.graphiks.kanvas.pipeline.VertexAttribute
import org.graphiks.kanvas.pipeline.VertexFormat
import org.graphiks.kanvas.pipeline.VertexLayout
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPreparedTextLowererTest {
    @Test
    fun `canonical public entry lowers without an injectable resolver`() {
        val result = GPUPreparedTextLowerer.lower(
            operation = validOperation(),
            operationIndex = 0,
            target = target(),
            capabilities = capabilities(),
        )

        assertIs<GPUPreparedTextLowering.Ready>(result)
    }

    @Test
    fun `prepared draw retains exact blend target and capability authorities`() {
        val operation = validOperation()
        val target = target()
        val capabilities = capabilities()
        val ready = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation = operation,
                operationIndex = 0,
                target = target,
                capabilities = capabilities,
            ),
        )
        val expectedPlan = operation.paint.blendMode.toGpuBlendFacts().canonicalBlendPlan(
            coverage = org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption.ScalarCoverage,
            targetFormatClass = target.colorFormat,
        )

        assertEquals(expectedPlan.canonicalIdentity(), ready.draw.blendPlan.canonicalIdentity())
        assertEquals(target.colorFormat, ready.draw.targetColorFormat)
        assertEquals(capabilities.canonicalSnapshotHash(), ready.draw.capabilitySnapshotHash)
    }

    @Test
    fun `prepared solid material authenticates pre coverage opacity before blend planning`() {
        val opaqueSource = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation = validOperation().copy(
                    paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.SRC),
                ),
                operationIndex = 0,
                target = target(),
                capabilities = capabilities(),
            ),
        ).draw
        val translucentSource = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation = validOperation().copy(
                    paint = Paint.fill(Color.fromRGBA(1f, 0f, 0f, 0.5f))
                        .copy(blendMode = BlendMode.SRC),
                ),
                operationIndex = 1,
                target = target(),
                capabilities = capabilities(),
            ),
        ).draw

        assertEquals(
            GPUSourceAlphaClassification.ProvenOpaque,
            opaqueSource.material.preCoverageSourceAlpha,
        )
        assertEquals(
            GPUSourceCoverageEncoding.ModulateRGBA,
            assertIs<GPUBlendPlan.FixedFunctionBlend>(opaqueSource.blendPlan)
                .sourceCoverageEncoding,
        )
        assertEquals(
            GPUSourceAlphaClassification.Translucent,
            translucentSource.material.preCoverageSourceAlpha,
        )
        assertIs<GPUBlendPlan.ShaderBlendWithDstRead>(translucentSource.blendPlan)
    }

    @Test
    fun `lowerer snapshots exact positioned glyphs and paint state`() {
        val glyphs = mutableListOf(5.toUShort(), 9.toUShort())
        val positions = mutableListOf(Point(1.25f, 2f), Point(7.5f, 2f))
        val variations = linkedMapOf("wght" to 500f)
        val stops = mutableListOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f, Color.BLUE),
        )
        val clipOps = mutableListOf<ClipStackOp>(
            ClipStackOp.RectOp(
                rect = Rect.fromLTRB(0f, 0f, 48f, 48f),
                op = ClipOp.INTERSECT,
                antiAlias = false,
            ),
            ClipStackOp.PathOp(
                path = Path {
                    moveTo(4f, 4f)
                    lineTo(44f, 4f)
                    lineTo(24f, 44f)
                    close()
                },
                op = ClipOp.INTERSECT,
                antiAlias = true,
            ),
        )
        val typeface = liberationTypeface()
        val operation = DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(KanvasGlyphRun(glyphs, positions, fontSize = 16f)),
                typeface = typeface,
                fontSize = 16f,
                variationCoordinates = variations,
            ),
            x = 3f,
            y = 4f,
            paint = Paint.fill(Color.WHITE).copy(
                color = Color.fromRGBA(1f, 1f, 1f, 0.5f),
                shader = Shader.LinearGradient(
                    start = Point(0f, 0f),
                    end = Point(32f, 0f),
                    stops = stops,
                    tileMode = TileMode.CLAMP,
                ),
            ),
            transform = Matrix33.skew(0.2f, 0f),
            clip = ClipStack.Complex(clipOps),
        )

        val ready = assertIs<GPUPreparedTextLowering.Ready>(
            GPUPreparedTextLowerer.lower(
                operation = operation,
                operationIndex = 4,
                target = target(),
                capabilities = capabilities(),
                fontResolver = GPUPreparedFontTypefaceResolver,
            ),
        )

        assertEquals(listOf(5, 9), ready.draw.glyphs.map { it.glyphId })
        assertEquals(listOf(1.25f, 7.5f), ready.draw.glyphs.map { it.positionX })
        assertEquals(listOf(2f, 2f), ready.draw.glyphs.map { it.positionY })
        assertEquals(listOf(16f, 16f), ready.draw.glyphs.map { it.fontSize })
        assertEquals(operation.transform, ready.draw.transform)
        assertEquals(
            operation.clip.toGPUClipFacts(target()).coverageRequest?.contentKey,
            ready.draw.clip.toGPUClipFacts(target()).coverageRequest?.contentKey,
        )
        assertEquals(operation.paint.color.a, ready.draw.material.paintAlpha)
        assertEquals(4, ready.draw.operationIndex)
        assertEquals(typeface.sourceId, ready.draw.face.sourceId)
        assertEquals(typeface.typefaceId, ready.draw.face.typefaceId)
        assertEquals(typeface.typefaceId, ready.draw.glyphs.first().strikeKey.typefaceId)
        assertEquals(5, ready.draw.glyphs.first().strikeKey.glyphId)
        assertEquals("a8", ready.draw.glyphs.first().strikeKey.representationRoute)
        assertEquals("a8", ready.draw.glyphs.first().strikeKey.maskFormat)
        assertEquals(0.45f, ready.draw.glyphs.first().strikeKey.subpixelX, 0.00001f)
        assertEquals(0f, ready.draw.glyphs.first().strikeKey.subpixelY)
        assertEquals(mapOf("wght" to 500f), ready.draw.glyphs.first().strikeKey.variationCoordinates)
        assertEquals(typeface.fontBytes.map { it.toInt() and 0xff }, ready.draw.face.bytes)
        assertEquals(3f, ready.draw.originX)
        assertEquals(4f, ready.draw.originY)
        assertTrue(ready.draw.representationPolicy.representations.isNotEmpty())

        glyphs[0] = 10u
        positions[0] = Point(99f, 99f)
        stops[0] = GradientStop(0f, Color.GREEN)
        clipOps.clear()
        variations["wght"] = 700f
        typeface.fontBytes.fill(0)

        assertEquals(listOf(5, 9), ready.draw.glyphs.map { it.glyphId })
        assertEquals(listOf(1.25f, 7.5f), ready.draw.glyphs.map { it.positionX })
        assertEquals(2, (ready.draw.clip as ClipStack.Complex).ops.size)
        assertEquals(Color.RED, (ready.draw.paint.shader as Shader.LinearGradient).stops.first().color)
        assertEquals(mapOf("wght" to 500f), ready.draw.glyphs.first().strikeKey.variationCoordinates)
        assertTrue(ready.draw.face.bytes.any { it != 0 })
        assertNotSame(operation.paint, ready.draw.paint)
    }

    @Test
    fun `prepared draw clip getter never exposes its mutable path snapshot`() {
        val sourcePath = Path {
            moveTo(2f, 2f)
            lineTo(38f, 2f)
            lineTo(20f, 38f)
            close()
        }
        val operation = validOperation().copy(
            clip = ClipStack.Complex(
                listOf(
                    ClipStackOp.PathOp(
                        path = sourcePath,
                        op = ClipOp.INTERSECT,
                        antiAlias = true,
                    ),
                ),
            ),
        )
        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

        sourcePath.lineTo(7f, 7f)
        sourcePath.fillType = FillType.EVEN_ODD
        val firstRead = assertIs<ClipStack.Complex>(ready.draw.clip)
        val firstPath = assertIs<ClipStackOp.PathOp>(firstRead.ops.single()).path
        assertEquals(4, firstPath.verbs().size)
        assertEquals(FillType.WINDING, firstPath.fillType)

        firstPath.lineTo(9f, 9f)
        firstPath.fillType = FillType.INVERSE_WINDING
        val secondRead = assertIs<ClipStack.Complex>(ready.draw.clip)
        val secondPath = assertIs<ClipStackOp.PathOp>(secondRead.ops.single()).path
        assertEquals(4, secondPath.verbs().size)
        assertEquals(FillType.WINDING, secondPath.fillType)
        assertNotSame(firstRead, secondRead)
        assertNotSame(firstPath, secondPath)
    }

    @Test
    fun `prepared draw paint getter never exposes image pixel snapshots`() {
        val sourcePixels = byteArrayOf(10, 20, 30, 40)
        val operation = validOperation().copy(
            paint = Paint(
                shader = Shader.Image(
                    image = Image.fromPixels(
                        width = 1,
                        height = 1,
                        pixels = sourcePixels,
                        sourceId = "task4-defensive-image",
                    ),
                ),
            ),
        )
        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

        sourcePixels.fill(99)
        val firstPaint = ready.draw.paint
        val firstPixels = checkNotNull(
            assertIs<Shader.Image>(firstPaint.shader).image.pixels,
        )
        assertContentEquals(byteArrayOf(10, 20, 30, 40), firstPixels)

        firstPixels.fill(77)
        val secondPaint = ready.draw.paint
        val secondPixels = checkNotNull(
            assertIs<Shader.Image>(secondPaint.shader).image.pixels,
        )
        assertContentEquals(byteArrayOf(10, 20, 30, 40), secondPixels)
        assertNotSame(firstPaint, secondPaint)
        assertNotSame(firstPixels, secondPixels)
    }

    @Test
    fun `prepared draw paint getter never exposes color filter arrays`() {
        val sourceMatrix = FloatArray(20).also { values ->
            values[0] = 1f
            values[6] = 1f
            values[12] = 1f
            values[18] = 1f
        }
        val operation = validOperation().copy(
            paint = Paint.fill(Color.RED).copy(
                colorFilter = ColorFilter.Matrix(sourceMatrix),
            ),
        )
        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

        sourceMatrix[0] = 99f
        val firstValues = assertIs<ColorFilter.Matrix>(ready.draw.paint.colorFilter).values
        assertEquals(1f, firstValues[0])

        firstValues[0] = 77f
        val secondValues = assertIs<ColorFilter.Matrix>(ready.draw.paint.colorFilter).values
        assertEquals(1f, secondValues[0])
        assertNotSame(firstValues, secondValues)
    }

    @Test
    fun `prepared draw paint getter isolates nested HSL and table color filter arrays`() {
        val sourceHsl = FloatArray(20).also { values ->
            values[0] = 1f
            values[6] = 1f
            values[12] = 1f
            values[18] = 1f
        }
        val sourceTable = UByteArray(256) { index -> index.toUByte() }
        val draw = preparedDrawWithPaint(
            Paint.fill(Color.RED).copy(
                colorFilter = ColorFilter.Compose(
                    outer = ColorFilter.HSLAMatrix(sourceHsl),
                    inner = ColorFilter.Table(sourceTable),
                ),
            ),
        )

        sourceHsl[0] = 99f
        sourceTable[10] = 99u
        val first = assertIs<ColorFilter.Compose>(draw.paint.colorFilter)
        val firstHsl = assertIs<ColorFilter.HSLAMatrix>(first.outer).values
        val firstTable = assertIs<ColorFilter.Table>(first.inner).table
        assertEquals(1f, firstHsl[0])
        assertEquals(10.toUByte(), firstTable[10])

        firstHsl[0] = 77f
        firstTable[10] = 77u
        val second = assertIs<ColorFilter.Compose>(draw.paint.colorFilter)
        val secondHsl = assertIs<ColorFilter.HSLAMatrix>(second.outer).values
        val secondTable = assertIs<ColorFilter.Table>(second.inner).table
        assertEquals(1f, secondHsl[0])
        assertEquals(10.toUByte(), secondTable[10])
        assertNotSame(firstHsl, secondHsl)
        assertNotSame(firstTable, secondTable)
    }

    @Test
    fun `prepared draw paint getter isolates mask filter table arrays`() {
        val sourceTable = UByteArray(256) { index -> index.toUByte() }
        val draw = preparedDrawWithPaint(
            Paint.fill(Color.RED).copy(maskFilter = MaskFilter.Table(sourceTable)),
        )

        sourceTable[12] = 99u
        val firstTable = assertIs<MaskFilter.Table>(draw.paint.maskFilter).table
        assertEquals(12.toUByte(), firstTable[12])

        firstTable[12] = 77u
        val secondTable = assertIs<MaskFilter.Table>(draw.paint.maskFilter).table
        assertEquals(12.toUByte(), secondTable[12])
        assertNotSame(firstTable, secondTable)
    }

    @Test
    fun `prepared draw paint getter isolates runtime uniforms and preserves child sharing per read`() {
        val sourceMatrix = FloatArray(16) { index -> index.toFloat() }
        val sourcePixels = byteArrayOf(10, 20, 30, 40)
        val sharedChild = Shader.Image(
            image = Image.fromPixels(
                width = 1,
                height = 1,
                pixels = sourcePixels,
                sourceId = "task4-runtime-child",
            ),
        )
        val draw = preparedDrawWithPaint(
            Paint(
                shader = Shader.RuntimeEffect(
                    effect = RuntimeEffect(
                        id = "runtime.snapshot-only",
                        module = ShaderModule.fromSource("snapshot-only"),
                        uniformLayout = UniformLayout(emptyList()),
                        children = emptyList(),
                    ),
                    uniforms = UniformBlock {
                        mat4x4("transform", sourceMatrix)
                    },
                    children = linkedMapOf(
                        "first" to sharedChild,
                        "second" to sharedChild,
                    ),
                ),
            ),
        )

        sourceMatrix[0] = 99f
        sourcePixels[0] = 99
        val first = assertIs<Shader.RuntimeEffect>(draw.paint.shader)
        val firstMatrix =
            assertIs<org.graphiks.kanvas.pipeline.UniformValue.M4>(
                first.uniforms.entries.getValue("transform"),
            ).values
        val firstChild = assertIs<Shader.Image>(first.children.getValue("first"))
        val firstChildPixels = checkNotNull(firstChild.image.pixels)
        assertEquals(0f, firstMatrix[0])
        assertEquals(10.toByte(), firstChildPixels[0])
        assertSame(firstChild, first.children.getValue("second"))

        firstMatrix[0] = 77f
        firstChildPixels[0] = 77
        val second = assertIs<Shader.RuntimeEffect>(draw.paint.shader)
        val secondMatrix =
            assertIs<org.graphiks.kanvas.pipeline.UniformValue.M4>(
                second.uniforms.entries.getValue("transform"),
            ).values
        val secondChild = assertIs<Shader.Image>(second.children.getValue("first"))
        val secondChildPixels = checkNotNull(secondChild.image.pixels)
        assertEquals(0f, secondMatrix[0])
        assertEquals(10.toByte(), secondChildPixels[0])
        assertSame(secondChild, second.children.getValue("second"))
        assertNotSame(first, second)
        assertNotSame(firstMatrix, secondMatrix)
        assertNotSame(firstChild, secondChild)
        assertNotSame(firstChildPixels, secondChildPixels)
    }

    @Test
    fun `prepared draw deeply snapshots shared runtime effect metadata per getter read`() {
        val moduleUniforms = mutableListOf(
            UniformSlot("color", binding = 0, type = UniformType.FLOAT4, size = 16),
        )
        val moduleTextures = mutableListOf(TextureSlot("image", binding = 1))
        val vertexAttributes = mutableListOf(
            VertexAttribute(VertexFormat.FLOAT32x2, offset = 0, shaderLocation = 0),
        )
        val layoutSlots = mutableListOf(moduleUniforms.single())
        val childSlots = mutableListOf(ChildSlot("child", ChildType.SHADER))
        val effect = RuntimeEffect(
            id = "runtime.deep-snapshot",
            module = shaderModuleFixture(
                uniforms = moduleUniforms,
                textures = moduleTextures,
                attributes = vertexAttributes,
            ),
            uniformLayout = UniformLayout(layoutSlots),
            children = childSlots,
        )
        val left = Shader.RuntimeEffect(effect, UniformBlock.EMPTY, emptyMap())
        val right = Shader.RuntimeEffect(effect, UniformBlock.EMPTY, emptyMap())
        val draw = preparedDrawWithPaint(
            Paint(shader = Shader.Blend(BlendMode.SRC_OVER, left, right)),
        )

        moduleUniforms.clear()
        moduleTextures.clear()
        vertexAttributes.clear()
        layoutSlots.clear()
        childSlots.clear()

        val firstBlend = assertIs<Shader.Blend>(draw.paint.shader)
        val firstLeft = assertIs<Shader.RuntimeEffect>(firstBlend.dst)
        val firstRight = assertIs<Shader.RuntimeEffect>(firstBlend.src)
        assertSame(firstLeft.effect, firstRight.effect)
        assertEquals(listOf("color"), firstLeft.effect.module.uniforms.map { it.name })
        assertEquals(listOf("image"), firstLeft.effect.module.textures.map { it.name })
        assertEquals(1, firstLeft.effect.module.vertexLayout.attributes.size)
        assertEquals(listOf("color"), firstLeft.effect.uniformLayout.slots.map { it.name })
        assertEquals(listOf("child"), firstLeft.effect.children.map { it.name })
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (firstLeft.effect.children as MutableList<ChildSlot>).clear()
        }

        val secondBlend = assertIs<Shader.Blend>(draw.paint.shader)
        val secondLeft = assertIs<Shader.RuntimeEffect>(secondBlend.dst)
        val secondRight = assertIs<Shader.RuntimeEffect>(secondBlend.src)
        assertSame(secondLeft.effect, secondRight.effect)
        assertNotSame(firstLeft.effect, secondLeft.effect)
        assertEquals(listOf("color"), secondLeft.effect.module.uniforms.map { it.name })
        assertEquals(listOf("child"), secondLeft.effect.children.map { it.name })
    }

    @Test
    fun `material context factory is deterministic and keeps registered runtime effect authority`() {
        val target = target()
        val facts = listOf(
            GPUCapabilityFact("feature-b", "test", "supported", true, "b"),
            GPUCapabilityFact("feature-a", "test", "supported", true, "a"),
        )
        val capabilities = capabilities(facts)
        val firstContext = preparedTextMaterialContext(target, capabilities)
        val secondContext = preparedTextMaterialContext(
            target(),
            capabilities(facts.reversed()),
        )
        val mapping = Paint.fill(Color.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
            .toPreparedMaterialMapping()

        val first = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(mapping.descriptor, mapping.paintAlpha, firstContext),
        )
        val second = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(mapping.descriptor, mapping.paintAlpha, secondContext),
        )

        assertEquals(first.program.materialKey, second.program.materialKey)
        assertEquals(
            GPUPreparedRuntimeEffectResolution.Ready::class,
            firstContext.runtimeEffectResolver.resolve("runtime.simple_rt", 1)::class,
        )
        assertEquals(capabilities.canonicalSnapshotHash(), firstContext.capabilityClass)
    }

    @Test
    fun `prepared material context shares the complete canonical capability identity`() {
        val base = capabilities()
        val changed = capabilities(
            facts = listOf(
                GPUCapabilityFact(
                    name = "texture.sample",
                    source = "test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "sample",
                ),
            ),
        )

        listOf(base, changed).forEach { capabilities ->
            assertEquals(
                capabilities.canonicalSnapshotHash(),
                preparedTextMaterialContext(target(), capabilities).capabilityClass,
            )
        }
        assertNotEquals(base.canonicalSnapshotHash(), changed.canonicalSnapshotHash())
    }

    @Test
    fun `module internal prepared font factory snapshots caller-owned state`() {
        val typeface = liberationTypeface()
        val bytes = mutableListOf(1, 2, 3, 4)
        val face = GPUPreparedFontFaceSnapshot.create(
            sourceId = typeface.sourceId,
            typefaceId = checkNotNull(typeface.typefaceId),
            faceIndex = 0,
            bytes = bytes,
            provenance = "test:external-factory",
        )
        val ready = GPUPreparedTextFontResolution.ready(
            face = face,
            glyphCount = 8,
            representationResolver = GPUPreparedTextGlyphRepresentationResolver { _, _, _ ->
                GPUPreparedTextSourceRepresentation.OUTLINE
            },
        )
        bytes[0] = 99

        assertEquals(listOf(1, 2, 3, 4), face.bytes)
        assertEquals(face, ready.face)
        @Suppress("UNCHECKED_CAST")
        val mutableBytes = face.bytes as MutableList<Int>
        assertFailsWith<UnsupportedOperationException> {
            mutableBytes[0] = 99
        }
    }

    @Test
    fun `lowerer preserves every clip accepted by the common clip authority`() {
        val clips = listOf(
            ClipStack.DeviceRect(
                Rect.fromLTRB(1f, 1f, 39f, 39f),
                antiAlias = false,
            ),
            ClipStack.Complex(
                Collections.unmodifiableList(
                    listOf(
                        ClipStackOp.RectOp(
                            Rect.fromLTRB(0f, 0f, 40f, 40f),
                            ClipOp.INTERSECT,
                            antiAlias = false,
                        ),
                        ClipStackOp.PathOp(
                            Path {
                                moveTo(2f, 2f)
                                lineTo(38f, 2f)
                                lineTo(20f, 38f)
                                close()
                            },
                            ClipOp.DIFFERENCE,
                            antiAlias = true,
                        ),
                    ),
                ),
            ),
        )

        clips.forEach { clip ->
            val operation = validOperation().copy(clip = clip)
            val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

            assertEquals(
                operation.clip.toGPUClipFacts(target()).coverageRequest?.contentKey,
                ready.draw.clip.toGPUClipFacts(target()).coverageRequest?.contentKey,
            )
            assertNotSame(operation.clip, ready.draw.clip)
        }
    }

    @Test
    fun `paint snapshot preserves shared shader graph without aliasing its source`() {
        val sharedLeaf = Shader.SolidColor(Color.GREEN)
        val operation = validOperation().copy(
            paint = Paint(
                shader = Shader.Blend(
                    mode = BlendMode.SRC_OVER,
                    dst = sharedLeaf,
                    src = sharedLeaf,
                ),
            ),
        )

        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))
        val snapshot = assertIs<Shader.Blend>(ready.draw.paint.shader)
        val secondSnapshot = assertIs<Shader.Blend>(ready.draw.paint.shader)

        assertNotSame(sharedLeaf, snapshot.dst)
        assertSame(snapshot.dst, snapshot.src)
        assertNotSame(snapshot, secondSnapshot)
        assertNotSame(snapshot.dst, secondSnapshot.dst)
        assertSame(secondSnapshot.dst, secondSnapshot.src)
    }

    @Test
    fun `mode blender is normalized through the common scalar coverage planner`() {
        val operation = validOperation().copy(
            paint = Paint.fill(Color.RED).copy(
                blendMode = BlendMode.CLEAR,
                blender = Blender.Mode(BlendMode.XOR),
            ),
        )

        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

        assertEquals(BlendMode.XOR, ready.draw.paint.blendMode)
        assertEquals(null, ready.draw.paint.blender)
    }

    @Test
    fun `common blend target refusal remains terminal for text`() {
        val operation = validOperation().copy(
            paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.XOR),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(
            GPUPreparedTextLowerer.lower(
                operation = operation,
                operationIndex = 11,
                target = GPUTargetFacts(64, 64, "r32float"),
                capabilities = capabilities(),
                fontResolver = GPUPreparedFontTypefaceResolver,
            ),
        )

        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.BLEND_UNSUPPORTED,
            refused.code,
        )
        assertEquals(
            "unsupported.target.format_blend_incompatible",
            refused.facts["commonDiagnosticCode"],
        )
        assertTrue(refused.facts.getValue("commonDiagnosticMessage").isNotBlank())
        assertEquals("XOR", refused.facts["blendMode"])
        assertEquals("r32float", refused.facts["targetFormatClass"])
        assertEquals("ScalarCoverage", refused.facts["coverage"])
    }

    @Test
    fun `prepared material refusal code is propagated exactly`() {
        val effect = RuntimeEffect(
            id = "runtime.unregistered.task4",
            module = ShaderModule.fromSource("unregistered"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
        )
        val operation = validOperation().copy(
            paint = Paint(
                shader = Shader.RuntimeEffect(
                    effect = effect,
                    uniforms = UniformBlock.EMPTY,
                    children = emptyMap(),
                ),
            ),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(lower(operation))

        assertEquals("unsupported.material.runtime_effect.descriptor", refused.code)
        assertEquals("RuntimeEffect", refused.facts["sourceKind"])
        assertTrue(refused.facts.getValue("message").isNotBlank())
    }

    @Test
    fun `lowerer admits real COLRv0 representation without changing shaped glyph ids`() {
        val typeface = FontTypeface(
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf"),
            ).use { it.readBytes() },
            fontName = "Skia COLRv0 test font",
        )
        val operation = validOperation().copy(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(2u),
                        positions = listOf(Point(8f, 32f)),
                        fontSize = 48f,
                    ),
                ),
                typeface = typeface,
                fontSize = 48f,
            ),
        )

        val ready = assertIs<GPUPreparedTextLowering.Ready>(lower(operation))

        assertEquals(listOf(2), ready.draw.glyphs.map { glyph -> glyph.glyphId })
        assertEquals(
            listOf(GPUPreparedTextRepresentation.COLRV0),
            ready.draw.representationPolicy.representations,
        )
        assertEquals("colr", ready.draw.glyphs.single().strikeKey.representationRoute)
        assertEquals("none", ready.draw.glyphs.single().strikeKey.maskFormat)
    }

    @Test
    fun `injected ready resolution must match the exact requested typeface`() {
        val sourceOperation = validOperation()
        val sourceReady = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(sourceOperation.blob.typeface),
        )
        val sourceTypeface = assertIs<FontTypeface>(sourceOperation.blob.typeface)
        val otherTypeface = FontTypeface(
            sourceTypeface.fontBytes,
            "${sourceTypeface.fontName}-different-identity",
        )
        val operation = sourceOperation.copy(
            blob = sourceOperation.blob.copy(typeface = otherTypeface),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(
            GPUPreparedTextLowerer.lower(
                operation = operation,
                operationIndex = 11,
                target = target(),
                capabilities = capabilities(),
                fontResolver = GPUPreparedTextFontResolver { sourceReady },
            ),
        )

        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_IDENTITY_UNSTABLE,
            refused.code,
        )
        assertEquals("font-resolution", refused.facts["stage"])
        assertEquals("identity-mismatch", refused.facts["reason"])
    }

    @Test
    fun `injected ready resolution cannot supply a missing requested typeface`() {
        val sourceReady = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(validOperation().blob.typeface),
        )
        val operation = validOperation().copy(
            blob = validOperation().blob.copy(typeface = null),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(
            GPUPreparedTextLowerer.lower(
                operation = operation,
                operationIndex = 12,
                target = target(),
                capabilities = capabilities(),
                fontResolver = GPUPreparedTextFontResolver { sourceReady },
            ),
        )

        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_MISSING,
            refused.code,
        )
    }

    @Test
    fun `variation tags must be four printable ASCII characters`() {
        val operation = validOperation().copy(
            blob = validOperation().blob.copy(
                variationCoordinates = mapOf("w\u0000t!" to 1f),
            ),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(lower(operation))

        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_SIZE_INVALID,
            refused.code,
        )
    }

    @Test
    fun `strike key uses the fully transformed device anchor and affine basis`() {
        val translation = Matrix33.translate(0.375f, 0.625f)
        val translated = assertIs<GPUPreparedTextLowering.Ready>(
            lower(validOperation().copy(transform = translation)),
        ).draw.glyphs.single().strikeKey
        assertEquals(0.375f, translated.subpixelX, 0.00001f)
        assertEquals(0.625f, translated.subpixelY, 0.00001f)
        assertEquals(1f, translated.scaleX)
        assertEquals(1f, translated.scaleY)

        val scaledTransform = Matrix33.makeAll(
            2f, 0f, 0.125f,
            0f, 3f, 0.375f,
        )
        val scaled = assertIs<GPUPreparedTextLowering.Ready>(
            lower(validOperation().copy(transform = scaledTransform)),
        ).draw.glyphs.single().strikeKey
        assertEquals(0.125f, scaled.subpixelX, 0.00001f)
        assertEquals(0.375f, scaled.subpixelY, 0.00001f)
        assertEquals(2f, scaled.scaleX)
        assertEquals(3f, scaled.scaleY)
        assertNotEquals(translated.transformBucket, scaled.transformBucket)
    }

    @Test
    fun `rotation and skew produce stable exact and distinct strike identities`() {
        fun strike(transform: Matrix33) = assertIs<GPUPreparedTextLowering.Ready>(
            lower(validOperation().copy(transform = transform)),
        ).draw.glyphs.single().strikeKey

        val rotation = Matrix33.rotate(15f)
        val skew = Matrix33.skew(0.2f, 0f)
        val closeSkew = Matrix33.skew(Float.fromBits(0.2f.toRawBits() + 1), 0f)
        val firstRotation = strike(rotation)
        val secondRotation = strike(rotation)
        val skewed = strike(skew)
        val closeSkewed = strike(closeSkew)

        assertEquals(firstRotation, secondRotation)
        assertNotEquals(firstRotation.transformBucket, skewed.transformBucket)
        assertNotEquals(skewed.transformBucket, closeSkewed.transformBucket)
        assertTrue(firstRotation.scaleX > 0f)
        assertTrue(firstRotation.scaleY > 0f)
    }

    @Test
    fun `resolver exceptions become one terminal refusal`() {
        val result = GPUPreparedTextLowerer.lower(
            operation = validOperation(),
            operationIndex = 9,
            target = target(),
            capabilities = capabilities(),
            fontResolver = GPUPreparedTextFontResolver { error("fixture resolver failure") },
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(result)
        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
            refused.code,
        )
        assertEquals(9, refused.operationIndex)
        assertEquals("font-resolution", refused.facts["stage"])
        assertEquals("resolver-exception", refused.facts["reason"])
        assertEquals("GPUPreparedTextFontResolver", refused.facts["authority"])
        assertTrue("IllegalStateException" !in refused.message)
    }

    @Test
    fun `resolver refusals retain the generic injected resolver authority`() {
        val result = GPUPreparedTextLowerer.lower(
            operation = validOperation(),
            operationIndex = 11,
            target = target(),
            capabilities = capabilities(),
            fontResolver = GPUPreparedTextFontResolver {
                GPUPreparedTextFontResolution.refused(
                    org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.FONT_BYTES_MALFORMED,
                    "fixture refusal",
                )
            },
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(result)
        assertEquals("font-resolution", refused.facts["stage"])
        assertEquals("GPUPreparedTextFontResolver", refused.facts["authority"])
    }

    @Test
    fun `affine handoff contract separates translation and defines mask coordinates`() {
        val source = java.io.File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextContracts.kt",
        ).readText()

        assertTrue("L = linear2x2(draw.transform)" in source)
        assertTrue("R = L * inverse(D)" in source)
        assertTrue("q = D * p + phase" in source)
        assertTrue("phase = (strikeKey.subpixelX, strikeKey.subpixelY)" in source)
        assertTrue("R = A * inverse(D)" !in source)
    }

    @Test
    fun `representation exceptions become one terminal refusal`() {
        val default = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(validOperation().blob.typeface),
        )
        val resolver = GPUPreparedTextFontResolver {
            GPUPreparedTextFontResolution.ready(
                face = default.face,
                glyphCount = default.glyphCount,
                representationResolver = GPUPreparedTextGlyphRepresentationResolver { _, _, _ ->
                    error("fixture representation failure")
                },
            )
        }

        val result = GPUPreparedTextLowerer.lower(
            operation = validOperation(),
            operationIndex = 10,
            target = target(),
            capabilities = capabilities(),
            fontResolver = resolver,
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(result)
        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.REPRESENTATION_MISSING,
            refused.code,
        )
        assertEquals(10, refused.operationIndex)
        assertEquals("representation", refused.facts["stage"])
        assertEquals("resolver-exception", refused.facts["reason"])
        assertEquals("5", refused.facts["glyphId"])
    }

    @Test
    fun `new prepared text files have no native WebGPU dependency`() {
        val root = java.io.File("src/main/kotlin/org/graphiks/kanvas/surface/gpu")
        val sources = listOf(
            "GPUPreparedTextContracts.kt",
            "GPUPreparedTextFontResolver.kt",
            "GPUPreparedTextLowerer.kt",
        ).joinToString("\n") { fileName -> root.resolve(fileName).readText() }

        listOf(
            "io.ygdrasil.webgpu",
            "GPUDevice",
            "GPUQueue",
            "GPUTexture",
            "GPUBuffer",
            "GPUCommandEncoder",
        ).forEach { forbidden ->
            assertTrue(forbidden !in sources, "Prepared text pure lowering must not depend on $forbidden")
        }
    }

    @Test
    fun `expanded text path source remains visible on the normalized GPU command`() {
        val operation = DisplayOp.DrawPath.withSourceOperation(
            path = Path().addRect(Rect.fromLTRB(1f, 2f, 5f, 8f)),
            paint = Paint.fill(Color.BLACK),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
            sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
        )

        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(operation),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        assertEquals(
            "text-expanded",
            inventory.visualCommands.single().normalized.source.operation,
        )
    }

    private fun lower(operation: DisplayOp.DrawText): GPUPreparedTextLowering =
        GPUPreparedTextLowerer.lower(
            operation = operation,
            operationIndex = 0,
            target = target(),
            capabilities = capabilities(),
            fontResolver = GPUPreparedFontTypefaceResolver,
        )

    private fun validOperation(): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(5.toUShort()),
                    positions = listOf(Point(2f, 3f)),
                    fontSize = 16f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 16f,
        ),
        x = 1f,
        y = 2f,
        paint = Paint.fill(Color.RED),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun preparedDrawWithPaint(paint: Paint): GPUPreparedTextDraw {
        val base = assertIs<GPUPreparedTextLowering.Ready>(lower(validOperation())).draw
        return GPUPreparedTextDraw.create(
            operationIndex = base.operationIndex,
            face = base.face,
            glyphs = base.glyphs,
            originX = base.originX,
            originY = base.originY,
            transform = base.transform,
            clipContentKey = base.clipContentKey,
            clip = base.clip,
            paint = paint,
            material = base.material,
            blendPlan = base.blendPlan,
            targetColorFormat = base.targetColorFormat,
            capabilitySnapshotHash = base.capabilitySnapshotHash,
            representationPolicy = base.representationPolicy,
        )
    }

    private fun shaderModuleFixture(
        uniforms: List<UniformSlot>,
        textures: List<TextureSlot>,
        attributes: List<VertexAttribute>,
    ): ShaderModule {
        val constructor = ShaderModule::class.java.declaredConstructors.single {
            it.parameterCount == 5
        }.also { it.isAccessible = true }
        return constructor.newInstance(
            "fixture",
            "main",
            uniforms,
            textures,
            VertexLayout(attributes, stride = 8),
        ) as ShaderModule
    }
}

internal fun liberationTypeface(): FontTypeface = FontTypeface(
    checkNotNull(
        GPUPreparedTextLowererTest::class.java.classLoader
            .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf"),
    ).use { it.readBytes() },
    fontName = "LiberationSans-Regular",
)

internal fun target(): GPUTargetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb")

internal fun capabilities(
    facts: List<GPUCapabilityFact> = emptyList(),
): GPUCapabilities = GPUCapabilities(
    implementation = GPUImplementationIdentity(
        facadeName = "wgpu4k",
        implementationName = "test",
        adapterName = "deterministic-adapter",
        deviceName = "deterministic-device",
    ),
    facts = facts,
    knownUnsupportedFacts = emptyList(),
    snapshotId = "fp05-text-lowerer",
    limits = GPULimits(
        maxTextureDimension2D = 8192,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        maxBufferSize = 1L shl 30,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
    ),
)
