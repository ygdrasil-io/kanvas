package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Path1DStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ChildSlot
import org.graphiks.kanvas.pipeline.ChildType
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.pipeline.UniformSlot
import org.graphiks.kanvas.pipeline.UniformType
import org.graphiks.kanvas.pipeline.VertexAttribute
import org.graphiks.kanvas.pipeline.VertexFormat
import org.graphiks.kanvas.pipeline.VertexLayout
import org.graphiks.kanvas.pipeline.VertexStepMode
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.KanvasTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.Base64

class PictureTest {
    @Test
    fun `decodes the fixed version 7 path fixture through the public API`() {
        val bytes = Base64.getDecoder().decode(
            requireNotNull(javaClass.getResource("/picture/format-7-path.base64"))
                .readText()
                .trim(),
        )

        val picture = requireNotNull(Picture.fromByteArray(bytes))
        val draw = assertIs<DisplayOp.DrawPath>(picture.ops.single())
        val effect = assertIs<PathEffect.Path1D>(draw.paint.pathEffect)

        assertEquals(RectF32.ofLTRB(0f, 0f, 8f, 8f), picture.cullRect)
        assertEquals(FillType.INVERSE_EVEN_ODD, draw.path.fillType)
        assertEquals(
            listOf(PathVerb.MOVE, PathVerb.LINE, PathVerb.QUAD, PathVerb.CUBIC, PathVerb.ARC_TO, PathVerb.CLOSE),
            draw.path.commands().map { it.verb },
        )
        assertEquals(Path1DStyle.ROTATE, effect.style)
        assertEquals(2.5f, effect.advance)
        assertEquals(0.5f, effect.phase)
        assertEquals(listOf(PathVerb.MOVE, PathVerb.LINE), effect.path.commands().map { it.verb })
    }

    @Test
    fun `writer emits version 8 pictures`() {
        val picture = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(1f, 1f, 7f, 7f),
                    Paint.fill(ColorARGB.Red),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
        )

        assertEquals(8, picture.toByteArray().readBigEndianInt(offset = 4))
    }

    @Test
    fun `version 8 round trips every public serialized enum value`() {
        val identity = Matrix3x3F32.Identity
        val bounds = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val source = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val effectPath = Path().apply { moveTo(0f, 0f); lineTo(1f, 1f) }
        val ops = buildList<DisplayOp> {
            FillType.entries.forEachIndexed { index, fillType ->
                add(
                    DisplayOp.DrawPath(
                        Path().apply {
                            this.fillType = fillType
                            moveTo(1f, 1f)
                            lineTo(7f, 1f)
                            quadTo(7f, 3f, 5f, 4f)
                            cubicTo(4f, 5f, 3f, 6f, 2f, 5f)
                            arcTo(2f, 3f, 45f, largeArc = true, sweep = false, x = 1f, y = 1f)
                            close()
                        },
                        Paint(
                            pathEffect = PathEffect.Path1D(
                                effectPath,
                                advance = 2f,
                                phase = 0f,
                                style = Path1DStyle.entries[index % Path1DStyle.entries.size],
                            ),
                            style = PaintStyle.entries[index % PaintStyle.entries.size],
                            strokeCap = StrokeCap.entries[index % StrokeCap.entries.size],
                            strokeJoin = StrokeJoin.entries[index % StrokeJoin.entries.size],
                        ),
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
            BlendMode.entries.forEach { mode -> add(DisplayOp.DrawColor(ColorARGB.Red, mode, identity, ClipStack.WideOpen)) }
            TileMode.entries.forEachIndexed { index, tileMode ->
                add(
                    DisplayOp.DrawRect(
                        bounds,
                        Paint(
                            shader = Shader.LinearGradient(
                                Point2F32(0f, 0f),
                                Point2F32(8f, 8f),
                                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                                tileMode,
                                ColorSpaceInterpolation.entries[index % ColorSpaceInterpolation.entries.size],
                            ),
                        ),
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
            ColorSpaceInterpolation.entries.drop(TileMode.entries.size).forEach { interpolation ->
                add(
                    DisplayOp.DrawRect(
                        bounds,
                        Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(8f, 8f), listOf(GradientStop(0f, ColorARGB.Red)), interpolation = interpolation)),
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
            BlurStyle.entries.forEach { style -> add(DisplayOp.DrawRect(bounds, Paint(maskFilter = MaskFilter.Blur(style, 1f)), identity, ClipStack.WideOpen)) }
            ColorChannel.entries.forEach { channel ->
                add(
                    DisplayOp.DrawRect(
                        bounds,
                        Paint(imageFilter = ImageFilter.DisplacementMap(channel, channel, 1f, ImageFilter.Blur(1f, 1f))),
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
            PointMode.entries.forEach { mode -> add(DisplayOp.DrawPoints(mode, listOf(Point2F32(1f, 1f)), Paint.fill(ColorARGB.Red), identity, ClipStack.WideOpen)) }
            VertexMode.entries.forEach { mode -> add(DisplayOp.DrawVertices(Vertices(mode, listOf(Point2F32(0f, 0f))), Paint.fill(ColorARGB.Red), identity, ClipStack.WideOpen)) }
            LatticeFlags.entries.forEach { flag ->
                add(
                    DisplayOp.DrawImageLattice(
                        Image(1, 1, sourceId = "enum-lattice-$flag"),
                        Lattice(listOf(1), listOf(1), flags = listOf(flag)),
                        bounds,
                        null,
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
            ClipOp.entries.forEach { op ->
                add(
                    DisplayOp.DrawRect(
                        bounds,
                        Paint.fill(ColorARGB.Red),
                        identity,
                        ClipStack.Complex(listOf(ClipStackOp.RectOp(bounds, op))),
                    ),
                )
            }
            ColorType.entries.forEachIndexed { index, colorType ->
                add(
                    DisplayOp.DrawImage(
                        Image(
                            1,
                            1,
                            colorType,
                            "enum-image-$index",
                            colorSpace = ColorSpace(
                                "enum-space-$index",
                                org.graphiks.kanvas.color.TransferFunction.entries[index % org.graphiks.kanvas.color.TransferFunction.entries.size],
                                org.graphiks.kanvas.color.Gamut.entries[index % org.graphiks.kanvas.color.Gamut.entries.size],
                            ),
                            alphaType = AlphaType.entries[index % AlphaType.entries.size],
                        ),
                        source,
                        bounds,
                        null,
                        identity,
                        ClipStack.WideOpen,
                    ),
                )
            }
        }

        val restored = requireNotNull(Picture(bounds, ops).let { Picture.fromByteArray(it.toByteArray()) })
        val paths = restored.ops.filterIsInstance<DisplayOp.DrawPath>()
        val gradients = restored.ops.filterIsInstance<DisplayOp.DrawRect>().mapNotNull { it.paint.shader as? Shader.LinearGradient }
        val collectedImages = mutableListOf<Image>()
        restored.walkImages(collectedImages::add)
        val images = collectedImages.filter { it.sourceId.startsWith("enum-image-") }

        assertEquals(FillType.entries.toList(), paths.map { it.path.fillType })
        assertTrue(
            paths.all { path ->
                path.path.commands().map { command -> command.verb } ==
                    listOf(PathVerb.MOVE, PathVerb.LINE, PathVerb.QUAD, PathVerb.CUBIC, PathVerb.ARC_TO, PathVerb.CLOSE)
            },
        )
        assertEquals(Path1DStyle.entries.toList(), paths.map { assertIs<PathEffect.Path1D>(it.paint.pathEffect).style }.distinct())
        assertEquals(PaintStyle.entries.toList(), paths.map { it.paint.style }.distinct())
        assertEquals(StrokeCap.entries.toList(), paths.map { it.paint.strokeCap }.distinct())
        assertEquals(StrokeJoin.entries.toList(), paths.map { it.paint.strokeJoin }.distinct())
        assertEquals(BlendMode.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawColor>().map { it.mode })
        assertEquals(TileMode.entries.toList(), gradients.map { it.tileMode }.take(TileMode.entries.size))
        assertEquals(ColorSpaceInterpolation.entries.toList(), gradients.map { it.interpolation }.distinct())
        assertEquals(BlurStyle.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawRect>().mapNotNull { (it.paint.maskFilter as? MaskFilter.Blur)?.style })
        assertEquals(ColorChannel.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawRect>().mapNotNull { (it.paint.imageFilter as? ImageFilter.DisplacementMap)?.xChannelSelector })
        assertEquals(PointMode.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawPoints>().map { it.mode })
        assertEquals(VertexMode.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawVertices>().map { it.vertices.mode })
        assertEquals(LatticeFlags.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawImageLattice>().map { it.lattice.flags!!.single() })
        assertEquals(ClipOp.entries.toList(), restored.ops.filterIsInstance<DisplayOp.DrawRect>().mapNotNull { (it.clip as? ClipStack.Complex)?.ops?.single()?.let { op -> (op as? ClipStackOp.RectOp)?.op } })
        assertEquals(ColorType.entries.toList(), images.map { it.colorType })
        assertEquals(AlphaType.entries.toList().toSet(), images.map { it.alphaType }.toSet())
        assertEquals(org.graphiks.kanvas.color.TransferFunction.entries.toList().toSet(), images.map { it.colorSpace.transferFunction }.toSet())
        assertEquals(org.graphiks.kanvas.color.Gamut.entries.toList().toSet(), images.map { it.colorSpace.gamut }.toSet())

        val runtimeEffect = RuntimeEffect(
            "enum-runtime",
            ShaderModule.fromResource("enum-runtime"),
            UniformLayout(UniformType.entries.mapIndexed { index, type -> UniformSlot("uniform-$index", index, type, 1) }),
            ChildType.entries.mapIndexed { index, type -> ChildSlot("child-$index", type) },
        )
        val runtimeRestored = requireNotNull(
            Picture(
                bounds,
                listOf(DisplayOp.DrawRect(bounds, Paint(shader = runtimeEffect.makeShader(UniformBlock.EMPTY)), identity, ClipStack.WideOpen)),
            ).let { Picture.fromByteArray(it.toByteArray()) },
        )
        val restoredRuntime = assertIs<Shader.RuntimeEffect>(
            assertIs<DisplayOp.DrawRect>(runtimeRestored.ops.single()).paint.shader,
        ).effect
        assertEquals(UniformType.entries.toList(), restoredRuntime.uniformLayout.slots.map { it.type })
        assertEquals(ChildType.entries.toList(), restoredRuntime.children.map { it.type })
    }

    @Test
    fun `version 8 round trips runtime vertex layouts through the public Picture API`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val expectedAttributes = VertexFormat.entries.mapIndexed { index, format ->
            VertexAttribute(format, offset = index * 16, shaderLocation = index + 3)
        }
        val expectedStride = expectedAttributes.size * 16

        val restoredLayouts = VertexStepMode.entries.map { stepMode ->
            val effect = RuntimeEffect(
                "runtime-layout-$stepMode",
                shaderModuleWithVertexLayout(VertexLayout(expectedAttributes, expectedStride, stepMode)),
                UniformLayout(emptyList()),
                emptyList(),
            )
            val restored = requireNotNull(
                Picture(
                    bounds,
                    listOf(
                        DisplayOp.DrawRect(
                            bounds,
                            Paint(shader = effect.makeShader(UniformBlock.EMPTY)),
                            Matrix3x3F32.Identity,
                            ClipStack.WideOpen,
                        ),
                    ),
                ).let { picture -> Picture.fromByteArray(picture.toByteArray()) },
            )
            assertIs<Shader.RuntimeEffect>(
                assertIs<DisplayOp.DrawRect>(restored.ops.single()).paint.shader,
            ).effect.module.vertexLayout
        }

        assertEquals(VertexStepMode.entries.toList(), restoredLayouts.map(VertexLayout::stepMode))
        restoredLayouts.forEach { layout ->
            assertEquals(expectedAttributes, layout.attributes)
            assertEquals(expectedStride, layout.stride)
        }
    }

    @Test
    fun `format 8 preserves expanded text and clip provenance through round trip and playback`() {
        val path = DisplayOp.DrawPath.withSourceOperation(
            path = Path().addRect(RectF32.ofLTRB(1f, 2f, 3f, 4f)),
            paint = Paint.fill(ColorARGB.Red),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
            sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
        )
        val original = Picture(RectF32.ofLTRB(0f, 0f, 8f, 8f), listOf(path))

        val encoded = original.toByteArray()
        assertEquals(8, encoded.readBigEndianInt(offset = 4))
        val restored = requireNotNull(Picture.fromByteArray(encoded))
        assertEquals("text-expanded", assertIs<DisplayOp.DrawPath>(restored.ops.single()).sourceOperation)

        val playback = TestBuffer()
        restored.playback(Canvas(playback))
        assertEquals(
            "text-expanded",
            assertIs<DisplayOp.DrawPath>(playback.ops().single()).sourceOperation,
        )
    }

    @Test
    fun `format 5 DrawPath remains decodable with the truthful legacy source`() {
        val source = "drawPath"
        val current = Base64.getDecoder().decode(
            requireNotNull(javaClass.getResource("/picture/format-7-path.base64"))
                .readText()
                .trim(),
        )
        val legacy = current.copyOf(current.size - 2 - source.encodeToByteArray().size).also { bytes ->
            bytes.writeBigEndianInt(offset = 4, value = 5)
        }

        val restored = requireNotNull(Picture.fromByteArray(legacy))

        assertEquals("drawPath", assertIs<DisplayOp.DrawPath>(restored.ops.single()).sourceOperation)
    }

    @Test
    fun `format 5 layer fixture retains the version 2 non path layout`() {
        val legacyHeader = ByteArray(V2_LAYER_PICTURE_FIXTURE.size + 1).also { bytes ->
            V2_LAYER_PICTURE_FIXTURE.copyInto(bytes, endIndex = V2_LAYER_PICTURE_FIXTURE.size - 1)
            bytes[V2_LAYER_PICTURE_FIXTURE.size - 1] = 0 // no composite clip in format 5
            V2_LAYER_PICTURE_FIXTURE.copyInto(
                bytes,
                destinationOffset = V2_LAYER_PICTURE_FIXTURE.size,
                startIndex = V2_LAYER_PICTURE_FIXTURE.size - 1,
            )
            bytes.writeBigEndianInt(offset = 4, value = 5)
        }

        val restored = requireNotNull(Picture.fromByteArray(legacyHeader))

        assertEquals(
            listOf(DisplayOp.BeginLayer(SaveLayerRec()), DisplayOp.EndLayer),
            restored.ops,
        )
    }

    @Test
    fun `format 8 refuses an arbitrary serialized DrawPath source`() {
        val encoded = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawPath.withSourceOperation(
                    path = Path().addRect(RectF32.ofLTRB(1f, 2f, 3f, 4f)),
                    paint = Paint.fill(ColorARGB.Red),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                    sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
                ),
            ),
        ).toByteArray()
        "forged-source".encodeToByteArray().copyInto(
            destination = encoded,
            destinationOffset = encoded.size - "forged-source".length,
        )

        assertNull(Picture.fromByteArray(encoded))
    }

    @Test
    fun `format 8 round trip preserves each explicit image alpha authority`() {
        for (alpha in listOf(AlphaType.PREMUL, AlphaType.OPAQUE, AlphaType.UNPREMUL)) {
            val picture = pictureWithImageAlpha(alpha)
            val restored = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
            val images = mutableListOf<Image>()
            restored.walkImages(images::add)
            val restoredImage = images.single()

            assertEquals(alpha, restoredImage.alphaType)
        }
    }

    @Test
    fun `PictureRecorder records and produces Picture`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(RectF32.ofLTRB(0f, 0f, 100f, 100f), picture.cullRect)
        assertEquals(2, picture.approximateOpCount()) // clipRect + drawRect
        assertTrue(picture.uniqueID > 0)
    }

    @Test
    fun `Picture playback replays ops on target canvas`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        val targetBuffer = TestBuffer()
        val targetCanvas = Canvas(targetBuffer)
        picture.playback(targetCanvas)

        val ops = targetBuffer.ops()
        assertTrue(ops.isNotEmpty())
        assertTrue(ops.any { it is DisplayOp.DrawRect })
    }

    @Test
    fun `approximateOpCount with nested pictures`() {
        val r1 = PictureRecorder()
        val c1 = r1.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        c1.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(ColorARGB.Red))
        val inner = r1.finishRecordingAsPicture()

        val r2 = PictureRecorder()
        val c2 = r2.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        c2.drawPicture(inner)
        val outer = r2.finishRecordingAsPicture()

        assertTrue(outer.approximateOpCount(true) > outer.approximateOpCount(false))
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(ColorARGB.Red))
        canvas.drawRect(RectF32.ofLTRB(60f, 60f, 80f, 80f), Paint.fill(ColorARGB.Blue))
        val original = recorder.finishRecordingAsPicture()

        val bytes = original.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val restored = Picture.fromByteArray(bytes)
        assertNotNull(restored)
        assertEquals(original.cullRect, restored.cullRect)
        assertEquals(original.approximateOpCount(), restored.approximateOpCount())
    }

    @Test
    fun `mutating a source path after recording cannot change a Picture or its round trip`() {
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 7f, 8f))
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).drawPath(source, Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        fun recordedPathBounds(value: Picture): RectF32 {
            val bounds = mutableListOf<RectF32>()
            value.forEachOp { op -> if (op is DisplayOp.DrawPath) bounds += requireNotNull(op.path.computeBounds()) }
            return bounds.single()
        }

        assertEquals(RectF32.ofLTRB(1f, 2f, 7f, 8f), recordedPathBounds(picture))
        assertEquals(
            RectF32.ofLTRB(1f, 2f, 7f, 8f),
            recordedPathBounds(requireNotNull(Picture.fromByteArray(picture.toByteArray()))),
        )
    }

    @Test
    fun `roundtrip preserves transformed path clip provenance`() {
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.PathOp(
                    path = Path().addRect(RectF32.ofLTRB(1f, 1f, 7f, 7f)),
                    op = ClipOp.INTERSECT,
                    antiAlias = false,
                    transformClass = "affine",
                ),
            ),
        )
        val original = Picture(
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
            listOf(
                DisplayOp.DrawRect(
                    rect = RectF32.ofLTRB(0f, 0f, 8f, 8f),
                    paint = Paint.fill(ColorARGB.Red),
                    transform = Matrix3x3F32.Identity,
                    clip = clip,
                ),
            ),
        )

        val restored = requireNotNull(Picture.fromByteArray(original.toByteArray()))
        val restoredClip = assertIs<DisplayOp.DrawRect>(restored.ops.single()).clip
        assertEquals(
            "affine",
            assertIs<ClipStackOp.PathOp>(assertIs<ClipStack.Complex>(restoredClip).ops.single())
                .transformClass,
        )
    }

    @Test
    fun `roundtrip preserves a backdrop save layer record`() {
        val crop = RectF32.ofLTRB(0f, 10f, 100f, 90f)
        val rec = SaveLayerRec(
            backdrop = ImageFilter.Crop(crop, TileMode.DECAL, ImageFilter.Blur(3f, 3f)),
        )
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.saveLayer(rec)
        canvas.restore()
        val original = recorder.finishRecordingAsPicture()

        val restored = requireNotNull(Picture.fromByteArray(original.toByteArray()))

        assertEquals(original.ops, restored.ops)
    }

    @Test
    fun `roundtrip preserves deferred outer clip on a save layer`() {
        val outerClip = RectF32.ofLTRB(10f, 10f, 90f, 90f)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.clipRect(outerClip, antiAlias = false)
        canvas.saveLayer()
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 100f, 100f), Paint.fill(ColorARGB.Red))
        canvas.restore()
        val original = recorder.finishRecordingAsPicture()

        val restored = requireNotNull(Picture.fromByteArray(original.toByteArray()))

        assertEquals(original.ops, restored.ops)
    }

    @Test
    fun `decodes fixed version 1 picture layer fixture`() {
        val picture = requireNotNull(Picture.fromByteArray(V1_LAYER_PICTURE_FIXTURE))

        assertEquals(RectF32.ofLTRB(0f, 0f, 10f, 10f), picture.cullRect)
        assertEquals(listOf(DisplayOp.BeginLayer(SaveLayerRec()), DisplayOp.EndLayer), picture.ops)
    }

    @Test
    fun `decodes fixed version 2 picture layer fixture`() {
        val picture = requireNotNull(Picture.fromByteArray(V2_LAYER_PICTURE_FIXTURE))

        assertEquals(RectF32.ofLTRB(0f, 0f, 10f, 10f), picture.cullRect)
        assertEquals(listOf(DisplayOp.BeginLayer(SaveLayerRec()), DisplayOp.EndLayer), picture.ops)
    }

    @Test
    fun `playback intersects serialized layer clip with the host clip and defers it from children`() {
        val pictureClip = RectF32.ofLTRB(10f, 10f, 50f, 50f)
        val hostClip = RectF32.ofLTRB(30f, 30f, 70f, 70f)
        val recorder = PictureRecorder()
        val recordingCanvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        recordingCanvas.clipRect(pictureClip, antiAlias = true)
        recordingCanvas.saveLayer()
        recordingCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 100f, 100f), Paint.fill(ColorARGB.Red))
        recordingCanvas.restore()
        val picture = recorder.finishRecordingAsPicture()

        val targetBuffer = TestBuffer()
        val targetCanvas = Canvas(targetBuffer)
        targetCanvas.clipRect(hostClip, antiAlias = true)
        picture.playback(targetCanvas)

        val begin = targetBuffer.ops().filterIsInstance<DisplayOp.BeginLayer>().single()
        val compositeClip = assertIs<ClipStack.Complex>(begin.rec.compositeClip)
        val rectOps = compositeClip.ops.filterIsInstance<ClipStackOp.RectOp>()
        assertEquals(
            listOf(
                RectF32.ofLTRB(0f, 0f, 100f, 100f),
                pictureClip,
                hostClip,
            ),
            rectOps.map(ClipStackOp.RectOp::rect),
        )
        assertTrue(rectOps.all(ClipStackOp.RectOp::antiAlias))
        assertEquals(
            ClipStack.WideOpen,
            targetBuffer.ops().filterIsInstance<DisplayOp.DrawRect>().single().clip,
        )
    }

    @Test
    fun `fromByteArray returns null for invalid data`() {
        assertNull(Picture.fromByteArray(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun `fromByteArray returns null for an unknown format version`() {
        val encoded = Picture(
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            listOf(DisplayOp.Clear(ColorARGB.Transparent)),
        ).toByteArray().also { it.writeBigEndianInt(offset = 4, value = 9) }

        assertNull(Picture.fromByteArray(encoded))
    }

    @Test
    fun `fromByteArray returns null for empty data`() {
        assertNull(Picture.fromByteArray(ByteArray(0)))
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private fun ByteArray.writeBigEndianInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    @Test
    fun `opCount returns top-level operation count`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 10f, 10f), Paint.fill(ColorARGB.Red))
        canvas.drawRect(RectF32.ofLTRB(20f, 20f, 30f, 30f), Paint.fill(ColorARGB.Blue))
        val picture = recorder.finishRecordingAsPicture()

        assertEquals(3, picture.opCount) // clipRect + 2x drawRect
    }

    @Test
    fun `totalOpCount includes nested pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(ColorARGB.Red))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        val outer = outerRec.finishRecordingAsPicture()

        assertTrue(outer.totalOpCount > outer.opCount)
    }

    @Test
    fun `walkImages invokes action for each embedded image`() {
        val img = Image(4, 4, ColorType.RGBA_8888, "test", ByteArray(64) { 0 })
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawImage(img, RectF32.ofLTRB(10f, 10f, 50f, 50f))
        canvas.drawImage(img, RectF32.ofLTRB(60f, 60f, 80f, 80f))
        val picture = recorder.finishRecordingAsPicture()

        val collected = mutableListOf<Image>()
        picture.walkImages { collected.add(it) }
        assertEquals(2, collected.size)
        assertEquals(img, collected[0])
        assertEquals(img, collected[1])
    }

    @Test
    fun `walkImages does not invoke action when no images present`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkImages { called = true }
        assertFalse(called)
    }

    @Test
    fun `walkNestedPictures invokes action for each nested picture`() {
        val inner = PictureRecorder().apply {
            beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f)).drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(ColorARGB.Red))
        }.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        outerCanvas.drawPicture(inner)
        val outer = outerRec.finishRecordingAsPicture()

        val collected = mutableListOf<Picture>()
        outer.walkNestedPictures { collected.add(it) }
        assertEquals(2, collected.size)
        assertEquals(inner, collected[0])
        assertEquals(inner, collected[1])
    }

    @Test
    fun `walkTextBlobs deduplicates by reference and invokes action once per distinct blob`() {
        val glyphRuns = listOf(KanvasGlyphRun(listOf(65u, 66u), listOf(Point2F32(10f, 10f), Point2F32(30f, 10f))))
        val tf = KanvasTypeface("test-font")
        val blob1 = TextBlob(glyphRuns, tf, 16f)
        val blob2 = TextBlob(glyphRuns, tf, 16f) // structurally equal but different reference

        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 200f, 200f))
        canvas.drawText(blob1, 0f, 50f, Paint.fill(ColorARGB.Black))
        canvas.drawText(blob1, 0f, 100f, Paint.fill(ColorARGB.Black)) // same reference -> dedup
        canvas.drawText(blob2, 0f, 150f, Paint.fill(ColorARGB.Black)) // different reference
        val picture = recorder.finishRecordingAsPicture()

        val collected = mutableListOf<TextBlob>()
        picture.walkTextBlobs { collected.add(it) }
        assertEquals(2, collected.size) // blob1 deduped to 1, blob2 = 1 more
        assertEquals(blob1, collected[0])
        assertEquals(blob2, collected[1])
    }

    @Test
    fun `walkTextBlobs does not invoke action when no text present`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(10f, 10f, 50f, 50f), Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        var called = false
        picture.walkTextBlobs { called = true }
        assertFalse(called)
    }

    @Test
    fun `forEachOp visits all top-level ops in order`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 10f, 10f), Paint.fill(ColorARGB.Red))
        canvas.drawRect(RectF32.ofLTRB(20f, 20f, 30f, 30f), Paint.fill(ColorARGB.Blue))
        val picture = recorder.finishRecordingAsPicture()

        val ops = mutableListOf<DisplayOp>()
        picture.forEachOp { ops.add(it) }
        assertEquals(picture.opCount, ops.size)
        assertTrue(ops.count { it is DisplayOp.DrawRect } == 2)
    }

    @Test
    fun `forEachOp nested visits ops from child pictures`() {
        val innerRec = PictureRecorder()
        val innerCanvas = innerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        innerCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 5f, 5f), Paint.fill(ColorARGB.Red))
        val inner = innerRec.finishRecordingAsPicture()

        val outerRec = PictureRecorder()
        val outerCanvas = outerRec.beginRecording(RectF32.ofLTRB(0f, 0f, 100f, 100f))
        outerCanvas.drawPicture(inner)
        outerCanvas.drawRect(RectF32.ofLTRB(50f, 50f, 80f, 80f), Paint.fill(ColorARGB.Blue))
        val outer = outerRec.finishRecordingAsPicture()

        val collected = mutableListOf<DisplayOp>()
        outer.forEachOp(nested = true) { collected.add(it) }

        // outer: clipRect + DrawPicture + drawRect = 3
        // inner: clipRect + drawRect = 2
        assertTrue(collected.size >= 4)
        assertTrue(collected.any { it is DisplayOp.DrawPicture })
    }
}

private fun shaderModuleWithVertexLayout(vertexLayout: VertexLayout): ShaderModule {
    val constructor = ShaderModule::class.java.declaredConstructors.single { it.parameterCount == 5 }
        .also { it.isAccessible = true }
    return constructor.newInstance(
        "runtime-layout-module",
        "main",
        emptyList<UniformSlot>(),
        emptyList<org.graphiks.kanvas.pipeline.TextureSlot>(),
        vertexLayout,
    ) as ShaderModule
}

private fun pictureWithImageAlpha(alphaType: AlphaType): Picture {
    val recorder = PictureRecorder()
    recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 2f)).drawImage(
        Image(1, 1, ColorType.RGBA_8888, "legacy-alpha", byteArrayOf(1, 2, 3, 4), alphaType = alphaType),
        RectF32.ofLTRB(0f, 0f, 1f, 1f),
    )
    return recorder.finishRecordingAsPicture()
}

private val V1_LAYER_PICTURE_FIXTURE = byteArrayOf(
    0x4B, 0x50, 0x49, 0x43, // KPIC
    0x00, 0x00, 0x00, 0x01, // format version 1
    0x00, 0x00, 0x00, 0x00, // cull left
    0x00, 0x00, 0x00, 0x00, // cull top
    0x41, 0x20, 0x00, 0x00, // cull right = 10f
    0x41, 0x20, 0x00, 0x00, // cull bottom = 10f
    0x00, 0x00, 0x00, 0x02, // op count
    0x11, // BeginLayer
    0x00, // bounds absent
    0x00, // paint absent
    0x12, // EndLayer
)

private val V2_LAYER_PICTURE_FIXTURE = byteArrayOf(
    0x4B, 0x50, 0x49, 0x43, // KPIC
    0x00, 0x00, 0x00, 0x02, // format version 2
    0x00, 0x00, 0x00, 0x00, // cull left
    0x00, 0x00, 0x00, 0x00, // cull top
    0x41, 0x20, 0x00, 0x00, // cull right = 10f
    0x41, 0x20, 0x00, 0x00, // cull bottom = 10f
    0x00, 0x00, 0x00, 0x02, // op count
    0x11, // BeginLayer
    0x00, // bounds absent
    0x00, // paint absent
    0xFF.toByte(), // null v2 backdrop filter
    0x12, // EndLayer
)

private class TestBuffer : DisplayListBuffer {
    private val ops = mutableListOf<DisplayOp>()
    override fun append(op: DisplayOp) { ops.add(op) }
    override fun ops(): List<DisplayOp> = ops.toList()
}
