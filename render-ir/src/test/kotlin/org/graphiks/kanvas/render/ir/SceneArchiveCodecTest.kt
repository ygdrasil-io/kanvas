@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.render.ir

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

class SceneArchiveCodecTest {
    @Test
    fun `picture archive round trips an ordered scene and cull bounds`() {
        val scene = SceneSnapshot.of(
            extent = SceneExtent(32, 16),
            colorSpace = ColorSpace.SRGB,
            commands = listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Rect.of(RectF32(1f, 2f, 10f, 12f)),
                        material = MaterialNode.Solid(ColorARGB.Red),
                        coverage = CoverageRequest.ANTIALIASED,
                        clip = ClipStackNode.Empty,
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = org.graphiks.math.matrix.Matrix3x3F32.Identity,
                    ),
                ),
                SceneCommand.DrawColor(ColorARGB.Blue, BlendMode.SRC_OVER),
            ),
        )

        val bytes = SceneArchiveCodec.encodePicture(scene, RectF32(1f, 2f, 33f, 18f))
        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(SceneArchiveCodec.decodePicture(bytes))

        assertEquals("KPIC", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(8, java.nio.ByteBuffer.wrap(bytes, 4, 4).int)
        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
        assertEquals(RectF32(1f, 2f, 33f, 18f), decoded.copyCullRect())
    }

    @Test
    fun `picture archive rejects an unknown version without allocating a scene`() {
        val bytes = byteArrayOf(0x4b, 0x50, 0x49, 0x43, 0, 0, 0, 9)

        val result = SceneArchiveCodec.decodePicture(bytes)

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(result)
        assertEquals("unknown-version", invalid.code)
        assertTrue(invalid.message.isNotBlank())
    }

    @Test
    fun `picture archive round trips every command node graph resource and runtime contract`() {
        val scene = exhaustiveScene()
        val encoded = SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 64f, 64f))

        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(
            SceneArchiveCodec.decodePicture(encoded),
        )

        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
        assertEquals(scene.commandCount, decoded.scene.commandCount)
        assertTrue(encoded.contentEquals(SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 64f, 64f))))
    }

    @Test
    fun `picture archive rejects corrupted marker tag and collection length`() {
        val bytes = SceneArchiveCodec.encodePicture(exhaustiveScene(), RectF32(0f, 0f, 64f, 64f))
        val truncated = bytes.copyOf(bytes.size - 1)
        val invalidMarker = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(24, -2)
        }
        val invalidTag = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(firstCommandTagOffset(encoded), 999)
        }
        val invalidLength = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(40, Int.MAX_VALUE)
        }

        assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(truncated))
        val marker = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidMarker))
        assertEquals("invalid-marker", marker.code)
        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidTag))
        assertEquals("unknown-command", invalid.code)
        val invalidLengthResult = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidLength))
        assertEquals("invalid-length", invalidLengthResult.code)
    }

    @Test
    fun `historical v8 payload is explicitly distinguished from the IR payload`() {
        val historical = ByteBuffer.allocate(28)
            .put("KPIC".encodeToByteArray())
            .putInt(8)
            .putFloat(0f).putFloat(0f).putFloat(1f).putFloat(1f)
            .putInt(0)
            .array()

        assertEquals(SceneArchiveDecodeResult.LegacyV8, SceneArchiveCodec.decodePicture(historical))
    }

    private fun firstCommandTagOffset(bytes: ByteArray): Int {
        var offset = 4 + 4 + 16 + 4 + 4 + 4 + 4
        repeat(3) {
            val length = ByteBuffer.wrap(bytes, offset, 4).int
            offset += 4 + length
        }
        return offset + 4
    }

    private fun exhaustiveScene(): SceneSnapshot {
        val bounds = RectF32(1f, 2f, 30f, 40f)
        val image = ImageResourceSnapshot.fromPixels(
            sourceId = "pixels",
            width = 2,
            height = 2,
            pixelFormat = ImagePixelFormat.RGBA_8888,
            alphaType = ImageAlphaType.PREMUL,
            colorSpace = ColorSpace.SRGB,
            rowBytes = 8,
            pixels = ByteArray(16) { it.toByte() },
        )
        val external = ExternalImageReference.of("external", 0, 0, ImagePixelFormat.UNKNOWN, ImageAlphaType.UNKNOWN, ColorSpace.DISPLAY_P3)
        val path = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).quadTo(5f, 6f, 7f, 8f)
            .cubicTo(9f, 10f, 11f, 12f, 13f, 14f).arcTo(2f, 3f, 45f, true, false, 15f, 16f).close().build()
        val nested = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, listOf(SceneCommand.Clear(org.graphiks.math.color.ColorF32.Black)))
        val shaderDescriptor = descriptor("shader", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER))
        val colorDescriptor = descriptor("color", RuntimeEffectAbi.COLOR_FILTER, RuntimeChildSlot("child", RuntimeChildType.COLOR_FILTER))
        val imageDescriptor = descriptor("image", RuntimeEffectAbi.IMAGE_FILTER, RuntimeChildSlot("child", RuntimeChildType.IMAGE_FILTER))
        val uniforms = linkedMapOf("u" to RuntimeUniformValue.F1(1f))
        val everyUniform = linkedMapOf(
            "f1" to RuntimeUniformValue.F1(1f),
            "f2" to RuntimeUniformValue.F2(1f, 2f),
            "f3" to RuntimeUniformValue.F3(1f, 2f, 3f),
            "f4" to RuntimeUniformValue.F4(1f, 2f, 3f, 4f),
            "i1" to RuntimeUniformValue.I1(5),
            "m3" to RuntimeUniformValue.M3(Matrix3x3F32.Identity),
            "m4" to RuntimeUniformValue.M4(FloatArray(16) { it.toFloat() }),
        )
        val materialChild = MaterialNode.Solid(ColorARGB.Green)
        val colorChild = ColorFilterNode.Blend(ColorARGB.Blue, BlendMode.SRC_OVER)
        val imageChild = ImageFilterNode.Blur(1f, 2f)
        val runtimeMaterial = MaterialNode.RuntimeEffect.of(shaderDescriptor, uniforms, listOf(RuntimeMaterialChild("child", materialChild)))
        val allUniformMaterial = MaterialNode.RuntimeEffect.of(
            descriptor("all-uniforms", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER), everyUniform),
            everyUniform,
            listOf(RuntimeMaterialChild("child", materialChild)),
        )
        val runtimeColor = ColorFilterNode.RuntimeEffect.of(colorDescriptor, uniforms, listOf(RuntimeColorFilterChild("child", colorChild)))
        val runtimeImage = ImageFilterNode.RuntimeEffect.of(imageDescriptor, uniforms, null, listOf(RuntimeImageFilterChild("child", imageChild)))
        val meshProgram = MeshProgramNode.of(shaderDescriptor, uniforms, listOf(MeshProgramChild.Shader("child", materialChild)))
        val filters = listOf<ImageFilterNode>(
            ImageFilterNode.Crop.of(bounds), ImageFilterNode.Blur(1f, 2f), ImageFilterNode.DropShadow(1f, 2f, 3f, 4f, ColorARGB.Red),
            ImageFilterNode.ColorFilter(colorChild), ImageFilterNode.Compose(imageChild, ImageFilterNode.Offset(1f, 2f)),
            ImageFilterNode.Blend(BlendMode.SCREEN, imageChild, ImageFilterNode.Offset(3f, 4f)), ImageFilterNode.Dilate(1f, 2f), ImageFilterNode.Erode(1f, 2f),
            ImageFilterNode.DistantLitDiffuse(1f, 2f, ColorARGB.Red, 3f, 4f), ImageFilterNode.PointLitDiffuse(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f),
            ImageFilterNode.SpotLitDiffuse(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f),
            ImageFilterNode.DistantLitSpecular(1f, 2f, ColorARGB.Red, 3f, 4f, 5f), ImageFilterNode.PointLitSpecular(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f, 5f),
            ImageFilterNode.SpotLitSpecular(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f, 9f),
            ImageFilterNode.Offset(1f, 2f), ImageFilterNode.Tile.of(bounds, RectF32(2f, 3f, 20f, 30f)), ImageFilterNode.Merge.of(listOf(imageChild)),
            ImageFilterNode.DisplacementMap(ColorChannel.RED, ColorChannel.GREEN, 2f, imageChild), ImageFilterNode.Picture.of(nested, bounds, RectF32(2f, 3f, 4f, 5f)),
            ImageFilterNode.Magnifier.of(bounds, 2f, 1f), ImageFilterNode.MatrixConvolution.of(SizeF32(1f, 1f), ImmutableFloats.copyOf(floatArrayOf(1f)), 1f, 0f, Vector2F32(0f, 0f), TileMode.CLAMP, false),
            runtimeImage,
        )
        val allEffects = EffectStack.of(
            listOf(
                ColorFilterNode.Matrix(ImmutableFloats.copyOf(FloatArray(20) { it.toFloat() })),
                ColorFilterNode.Compose(colorChild, ColorFilterNode.Table(ImmutableUBytes.copyOf(ubyteArrayOf(1u)))),
                ColorFilterNode.Lighting(ColorARGB.Red, ColorARGB.Blue), ColorFilterNode.SRGBToLinear, ColorFilterNode.LinearToSRGB,
                ColorFilterNode.HSLAMatrix(ImmutableFloats.copyOf(floatArrayOf(1f))), ColorFilterNode.Lerp(0.5f, colorChild, ColorFilterNode.Luma),
                ColorFilterNode.HighContrast, ColorFilterNode.Overdraw, runtimeColor,
                MaskFilterNode.Blur(MaskBlurStyle.OUTER, 2f), MaskFilterNode.Shader(materialChild), MaskFilterNode.Table(ImmutableUBytes.copyOf(ubyteArrayOf(2u))),
                PathEffectNode.Dash(ImmutableFloats.copyOf(floatArrayOf(1f, 2f)), 0.5f), PathEffectNode.Corner(2f), PathEffectNode.Discrete(3f, 4f),
                PathEffectNode.Path1D(path, 5f, 6f, Path1DStyle.MORPH), PathEffectNode.Path2D(Matrix3x3F32.Identity, path), PathEffectNode.Trim(0.1f, 0.9f),
            ) + filters,
        )
        val paint = PaintNode(
            color = ColorARGB.Magenta,
            shader = runtimeMaterial,
            blendMode = BlendMode.OVERLAY,
            blender = BlenderNode.Arithmetic(1f, 2f, 3f, 4f),
            colorFilter = runtimeColor,
            maskFilter = MaskFilterNode.Blur(MaskBlurStyle.NORMAL, 1f),
            pathEffect = PathEffectNode.Path2D(Matrix3x3F32.Identity, path),
            imageFilter = runtimeImage,
            style = PaintStyleNode.STROKE_AND_FILL,
            strokeWidth = 2f,
            strokeCap = StrokeCapNode.ROUND,
            strokeJoin = StrokeJoinNode.BEVEL,
            strokeMiter = 3f,
            antiAlias = false,
        )
        val geometries = listOf<GeometryNode>(
            GeometryNode.Rect.of(bounds),
            GeometryNode.RRect.of(org.graphiks.math.geometry.RRectF32.of(bounds, 2f)),
            GeometryNode.DoubleRRect.of(org.graphiks.math.geometry.RRectF32.of(bounds, 2f), org.graphiks.math.geometry.RRectF32.of(RectF32(3f, 4f, 20f, 25f), 1f)),
            GeometryNode.Path(path), GeometryNode.Points.of(PointMode.LINES, listOf(Point2F32(1f, 2f), Point2F32(3f, 4f))),
            GeometryNode.IndexedMesh.of(MeshPrimitiveMode.TRIANGLES, listOf(Point2F32(0f, 0f)), listOf(Point2F32(1f, 1f)), listOf(ColorARGB.Red), intArrayOf(), bounds, ResourceReference(ResourceId("mesh")), meshProgram),
            GeometryNode.ImagePatch.of(ResourceReference(ResourceId("pixels")), bounds, RectF32(2f, 3f, 20f, 30f)),
            GeometryNode.ImageLattice.of(ResourceReference(ResourceId("pixels")), intArrayOf(1), intArrayOf(2), listOf(bounds), listOf(ColorARGB.Red), listOf(LatticeCellFlag.FIXED_COLOR), bounds, ImageSampling.Cubic(0.2f, 0.3f)),
            GeometryNode.Atlas.of(ResourceReference(ResourceId("pixels")), listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32.Identity, bounds, ColorARGB.Blue))),
            GeometryNode.GlyphRun.of(intArrayOf(1), listOf(Point2F32(1f, 2f)), 12f, mapOf("wght" to 400f), TypefaceReference(TypefaceId("font"))),
            GeometryNode.TextBlob.of(listOf(GeometryNode.GlyphRun.of(intArrayOf(2), listOf(Point2F32(3f, 4f)))), 1f, 2f, TypefaceReference(TypefaceId("font")), 13f, mapOf("wdth" to 90f)),
            GeometryNode.Picture.of(nested, bounds),
        )
        val materials = listOf<MaterialNode>(
            MaterialNode.Transparent, MaterialNode.Solid(ColorARGB.Red), MaterialNode.LinearGradient.of(Point2F32(0f, 0f), Point2F32(1f, 1f), listOf(GradientStop(0f, ColorARGB.Red))),
            MaterialNode.RadialGradient.of(Point2F32(0f, 0f), 1f, listOf(GradientStop(0f, ColorARGB.Red))), MaterialNode.SweepGradient.of(Point2F32(0f, 0f), stops = listOf(GradientStop(0f, ColorARGB.Red))),
            MaterialNode.ConicalGradient.of(Point2F32(0f, 0f), 1f, Point2F32(1f, 1f), 2f, listOf(GradientStop(0f, ColorARGB.Red))), MaterialNode.ImageSample(image),
            MaterialNode.Blend(BlendMode.SRC_OVER, materialChild, MaterialNode.Solid(ColorARGB.Blue)), runtimeMaterial, MaterialNode.WithLocalMatrix(materialChild, Matrix3x3F32.Identity),
            MaterialNode.WithColorFilter(materialChild, colorChild), MaterialNode.Opacity(materialChild, 0.5f), MaterialNode.PerlinNoise(1f, 2f, 3, 4, SizeF32(1f, 2f)),
            MaterialNode.FractalNoise(1f, 2f, 3, 4, null), MaterialNode.WithWorkingColorSpace(materialChild, ColorInterpolation.OKLAB), MaterialNode.CoordClamp(materialChild, bounds), allUniformMaterial,
        )
        return SceneSnapshot.of(
            SceneExtent(64, 64), ColorSpace.DISPLAY_P3,
            buildList {
                add(SceneCommand.Clear(org.graphiks.math.color.ColorF32.of(0.1f, 0.2f, 0.3f, 0.4f)))
                add(SceneCommand.DrawColor(ColorARGB.Cyan, BlendMode.MULTIPLY, Matrix3x3F32.Identity, ClipStackNode.DeviceRect.of(bounds, false)))
                add(SceneCommand.SetTransform(Matrix3x3F32.Identity))
                add(SceneCommand.SetClip(ClipStackNode.Operations.of(listOf(ClipEntry(GeometryNode.Path(path), ClipOperation.DIFFERENCE, false, true, "perspective")))))
                add(SceneCommand.BeginLayer(LayerDescriptor.of("layer", bounds, runtimeMaterial, paint, BlendNode.Paint(BlendMode.SCREEN, BlenderNode.Mode(BlendMode.XOR)), ClipStackNode.Empty, ClipStackNode.DeviceRect.of(bounds), allEffects, allEffects, Matrix3x3F32.Identity)))
                geometries.forEachIndexed { index, geometry ->
                    add(SceneCommand.Draw(DrawNode(geometry, materials[index % materials.size], CoverageRequest.HARD_EDGE, ClipStackNode.Empty, BlendNode.Custom(BlenderNode.Mode(BlendMode.PLUS)), allEffects, Matrix3x3F32.Identity, DrawOrigin.entries[index % DrawOrigin.entries.size], paint, if (index % 2 == 0) image else external, BlendMode.DIFFERENCE)))
                }
                materials.forEach { material ->
                    add(SceneCommand.Draw(DrawNode(GeometryNode.Rect.of(bounds), material, CoverageRequest.DEFAULT, ClipStackNode.Empty, BlendNode.Mode(BlendMode.DST_OVER), EffectStack.Empty, Matrix3x3F32.Identity)))
                }
                DrawOrigin.entries.forEach { origin ->
                    add(SceneCommand.Draw(DrawNode(GeometryNode.Rect.of(bounds), MaterialNode.Solid(ColorARGB.Black), CoverageRequest.ANTIALIASED, ClipStackNode.Empty, BlendNode.SrcOver, EffectStack.Empty, Matrix3x3F32.Identity, origin)))
                }
                add(SceneCommand.EndLayer)
                add(SceneCommand.State.of("state", linkedMapOf("a" to "b")))
                add(SceneCommand.Annotation.of(bounds, "key", "value"))
                add(SceneCommand.Readback(ReadbackRequest.of("readback", bounds)))
            },
        )
    }

    private fun descriptor(
        id: String,
        abi: RuntimeEffectAbi,
        child: RuntimeChildSlot,
        values: Map<String, RuntimeUniformValue> = mapOf("u" to RuntimeUniformValue.F1(1f)),
    ): RuntimeEffectDescriptor =
        RuntimeEffectDescriptor.of(
            id = RuntimeEffectId(id),
            abi = abi,
            uniformLayout = RuntimeUniformLayout.of(values.entries.mapIndexed { index, (name, value) -> RuntimeUniformSlot(name, index, value.runtimeUniformType(), 0) }),
            childSlots = listOf(child),
            vertexLayout = RuntimeVertexLayout.of(4, listOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32, 0, 0))),
            module = ShaderModuleDescriptor.of("void main() {}", "main", listOf(RuntimeUniformSlot("m", 1, RuntimeUniformType.FLOAT, 0))),
        )

    private fun RuntimeUniformValue.runtimeUniformType(): RuntimeUniformType = when (this) {
        is RuntimeUniformValue.F1 -> RuntimeUniformType.FLOAT
        is RuntimeUniformValue.F2 -> RuntimeUniformType.FLOAT2
        is RuntimeUniformValue.F3 -> RuntimeUniformType.FLOAT3
        is RuntimeUniformValue.F4 -> RuntimeUniformType.FLOAT4
        is RuntimeUniformValue.I1 -> RuntimeUniformType.INT1
        is RuntimeUniformValue.M3 -> RuntimeUniformType.MAT3X3
        is RuntimeUniformValue.M4 -> RuntimeUniformType.MAT4X4
    }
}
