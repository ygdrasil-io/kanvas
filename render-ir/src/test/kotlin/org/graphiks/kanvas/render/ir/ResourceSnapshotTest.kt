@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class ResourceSnapshotTest {
    @Test
    fun `image resource copies caller owned pixels and external images stay explicit`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val resource = ImageResourceSnapshot.rgba8(1, 1, pixels, ColorSpace.SRGB)
        val identity = resource.canonicalId
        val external = ExternalImageReference.of(
            sourceId = "remote-image",
            width = 5,
            height = 6,
            pixelFormat = ImagePixelFormat.BGRA_8888,
            alphaType = ImageAlphaType.PREMUL,
            colorSpace = ColorSpace.DISPLAY_P3,
        )

        pixels.fill(0)
        resource.copyPixels()[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3, 4), resource.copyPixels())
        assertEquals(identity, resource.canonicalId)
        assertEquals("remote-image", external.sourceId)
        assertEquals(ImagePixelFormat.BGRA_8888, external.pixelFormat)
        assertNotEquals(resource.canonicalId, external.canonicalId)
    }

    @Test
    fun `owned pixels require concrete metadata safe byte arithmetic and a color-space identity`() {
        assertFailsWith<IllegalArgumentException> {
            ImageResourceSnapshot.fromPixels(
                "unknown", 1, 1, ImagePixelFormat.UNKNOWN, ImageAlphaType.UNKNOWN,
                ColorSpace.SRGB, 0, byteArrayOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ImageResourceSnapshot.fromPixels(
                "blank-space", 1, 1, ImagePixelFormat.RGBA_8888, ImageAlphaType.UNPREMUL,
                ColorSpace("", TransferFunction.SRGB, Gamut.SRGB), 4, byteArrayOf(1, 2, 3, 4),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ImageResourceSnapshot.rgba8(Int.MAX_VALUE, 1, byteArrayOf(), ColorSpace.SRGB)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageResourceSnapshot.fromPixels(
                "short", 2, 2, ImagePixelFormat.RGBA_8888, ImageAlphaType.UNPREMUL,
                ColorSpace.SRGB, 8, byteArrayOf(1, 2, 3, 4),
            )
        }
        assertEquals(
            ImagePixelFormat.UNKNOWN,
            ExternalImageReference.of("external", 0, 0, ImagePixelFormat.UNKNOWN, ImageAlphaType.UNKNOWN, ColorSpace.SRGB).pixelFormat,
        )
    }

    @Test
    fun `equivalent image resources have structural equality and hash codes`() {
        val first = ImageResourceSnapshot.rgba8(1, 1, byteArrayOf(1, 2, 3, 4), ColorSpace.SRGB)
        val second = ImageResourceSnapshot.rgba8(1, 1, byteArrayOf(1, 2, 3, 4), ColorSpace.SRGB)
        val externalFirst = ExternalImageReference.of("remote", 1, 1, ImagePixelFormat.RGBA_8888, ImageAlphaType.PREMUL, ColorSpace.SRGB)
        val externalSecond = ExternalImageReference.of("remote", 1, 1, ImagePixelFormat.RGBA_8888, ImageAlphaType.PREMUL, ColorSpace.SRGB)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(externalFirst, externalSecond)
        assertEquals(externalFirst.hashCode(), externalSecond.hashCode())
    }

    @Test
    fun `immutable primitive wrappers use structural equality and defensive output`() {
        val bytes = ImmutableBytes.copyOf(byteArrayOf(1, 2))
        val floats = ImmutableFloats.copyOf(floatArrayOf(1f, 2f))
        val ubytes = ImmutableUBytes.copyOf(ubyteArrayOf(1u, 2u))
        val ints = ImmutableInts.copyOf(intArrayOf(1, 2))

        bytes.copyToByteArray()[0] = 9
        floats.copyToFloatArray()[0] = 9f
        ubytes.copyToUByteArray()[0] = 9u
        ints.copyToIntArray()[0] = 9

        assertEquals(ImmutableBytes.copyOf(byteArrayOf(1, 2)), bytes)
        assertEquals(ImmutableFloats.copyOf(floatArrayOf(1f, 2f)), floats)
        assertEquals(ImmutableUBytes.copyOf(ubyteArrayOf(1u, 2u)), ubytes)
        assertEquals(ImmutableInts.copyOf(intArrayOf(1, 2)), ints)
        assertContentEquals(byteArrayOf(1, 2), bytes.copyToByteArray())
        assertContentEquals(floatArrayOf(1f, 2f), floats.copyToFloatArray())
        assertContentEquals(ubyteArrayOf(1u, 2u), ubytes.copyToUByteArray())
        assertContentEquals(intArrayOf(1, 2), ints.copyToIntArray())
    }

    @Test
    fun `every color mask path image and blender effect variant retains semantic fields`() {
        val floats = floatArrayOf(1f, 2f, 3f)
        val colorMatrix = FloatArray(20) { (it + 1).toFloat() }
        val table = ubyteArrayOf(1u, 2u)
        val kernel = floatArrayOf(3f, 4f)
        val path = PathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).build()
        val filters: List<EffectNode> = listOf(
            ColorFilterNode.Matrix(ImmutableFloats.copyOf(colorMatrix)),
            ColorFilterNode.Blend(ColorARGB.Red, BlendMode.SCREEN),
            ColorFilterNode.Compose(ColorFilterNode.Luma, ColorFilterNode.Overdraw),
            ColorFilterNode.Table(ImmutableUBytes.copyOf(table)),
            ColorFilterNode.Lighting(ColorARGB.Red, ColorARGB.Blue),
            ColorFilterNode.SRGBToLinear,
            ColorFilterNode.LinearToSRGB,
            ColorFilterNode.HSLAMatrix(ImmutableFloats.copyOf(floats)),
            ColorFilterNode.Lerp(0.25f, ColorFilterNode.Luma, ColorFilterNode.Overdraw),
            ColorFilterNode.HighContrast,
            ColorFilterNode.Luma,
            ColorFilterNode.Overdraw,
            ColorFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.COLOR_FILTER), emptyMap(), emptyList()),
            MaskFilterNode.Blur(MaskBlurStyle.OUTER, 3f),
            MaskFilterNode.Shader(MaterialNode.Solid(ColorARGB.Red)),
            MaskFilterNode.Table(ImmutableUBytes.copyOf(table)),
            PathEffectNode.Dash(ImmutableFloats.copyOf(floats), 1f),
            PathEffectNode.Corner(2f),
            PathEffectNode.Discrete(3f, 4f),
            PathEffectNode.Path1D(path, 5f, 6f, Path1DStyle.MORPH),
            PathEffectNode.Path2D(Matrix3x3F32(tx = 2f), path),
            PathEffectNode.Trim(0.1f, 0.9f),
            ImageFilterNode.Crop.of(RectF32(1f, 2f, 3f, 4f), TileMode.MIRROR),
            ImageFilterNode.Blur(1f, 2f, TileMode.REPEAT),
            ImageFilterNode.DropShadow(1f, 2f, 3f, 4f, ColorARGB.Blue),
            ImageFilterNode.ColorFilter(ColorFilterNode.Luma),
            ImageFilterNode.Compose(ImageFilterNode.Offset(1f, 2f), ImageFilterNode.Erode(2f, 3f)),
            ImageFilterNode.Blend(BlendMode.OVERLAY, ImageFilterNode.Offset(1f, 2f), ImageFilterNode.Dilate(3f, 4f)),
            ImageFilterNode.Dilate(1f, 2f),
            ImageFilterNode.Erode(1f, 2f),
            ImageFilterNode.DistantLitDiffuse(1f, 2f, ColorARGB.Red, 3f, 4f),
            ImageFilterNode.PointLitDiffuse(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f),
            ImageFilterNode.SpotLitDiffuse(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f),
            ImageFilterNode.DistantLitSpecular(1f, 2f, ColorARGB.Red, 3f, 4f, 5f),
            ImageFilterNode.PointLitSpecular(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f, 5f),
            ImageFilterNode.SpotLitSpecular(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f, 9f),
            ImageFilterNode.Offset(1f, 2f),
            ImageFilterNode.Tile.of(RectF32(1f, 2f, 3f, 4f), RectF32(5f, 6f, 7f, 8f)),
            ImageFilterNode.Merge.of(listOf(ImageFilterNode.Offset(1f, 2f))),
            ImageFilterNode.DisplacementMap(ColorChannel.RED, ColorChannel.BLUE, 2f, ImageFilterNode.Offset(1f, 2f)),
            ImageFilterNode.Picture.of(
                SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList()),
                RectF32(0f, 0f, 1f, 1f),
            ),
            ImageFilterNode.Magnifier.of(RectF32(1f, 2f, 3f, 4f), 2f, 3f),
            ImageFilterNode.MatrixConvolution.of(SizeF32(1f, 2f), ImmutableFloats.copyOf(kernel), 3f, 4f, Vector2F32(5f, 6f), TileMode.DECAL, true),
            ImageFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.IMAGE_FILTER), emptyMap(), null, emptyList()),
        )
        val blenders = listOf(
            BlenderNode.Mode(BlendMode.COLOR_BURN),
            BlenderNode.Arithmetic(1f, 2f, 3f, 4f),
        )
        val stack = EffectStack.of(filters)
        val identity = stack.canonicalId

        floats.fill(0f)
        colorMatrix.fill(0f)
        table.fill(0u)
        kernel.fill(0f)
        assertContentEquals(FloatArray(20) { (it + 1).toFloat() }, (filters[0] as ColorFilterNode.Matrix).values.copyToFloatArray())
        assertContentEquals(ubyteArrayOf(1u, 2u), (filters[3] as ColorFilterNode.Table).table.copyToUByteArray())
        assertContentEquals(floatArrayOf(3f, 4f), (filters[42] as ImageFilterNode.MatrixConvolution).kernel.copyToFloatArray())
        assertEquals(44, (stack as EffectStack.Entries).effectCount)
        assertEquals(identity, stack.canonicalId)
        assertEquals(BlendMode.COLOR_BURN, (blenders[0] as BlenderNode.Mode).mode)
        assertEquals(4f, (blenders[1] as BlenderNode.Arithmetic).k4)
    }

    @Test
    fun `color matrix filters retain exactly the public 4 by 5 matrix payload`() {
        assertFailsWith<IllegalArgumentException> {
            ColorFilterNode.Matrix(ImmutableFloats.copyOf(floatArrayOf(1f)))
        }
    }

    @Test
    fun `effect graph limits are exact for nested public effects`() {
        val leaf = ColorFilterNode.Luma
        val oneLevel = ColorFilterNode.Compose(leaf, leaf)
        val twoLevels = ColorFilterNode.Compose(oneLevel, leaf)

        assertIs<GraphValidationResult.Valid>(EffectGraph.validate(leaf, GraphLimits(1, 1)))
        assertIs<GraphValidationResult.Valid>(EffectGraph.validate(oneLevel, GraphLimits(2, 3)))
        val depthFailure = assertIs<GraphValidationResult.DepthLimitExceeded>(EffectGraph.validate(twoLevels, GraphLimits(2, 5)))
        assertEquals(3, depthFailure.observedDepth)
        val nodeFailure = assertIs<GraphValidationResult.NodeLimitExceeded>(EffectGraph.validate(oneLevel, GraphLimits(2, 2)))
        assertEquals(3, nodeFailure.observedNodes)
    }

    @Test
    fun `effect path identity uses Kotlin canonical float semantics`() {
        val firstPath = PathBuilder().moveTo(Float.fromBits(0x7fc00000), 1f).build()
        val secondPath = PathBuilder().moveTo(Float.fromBits(0x7fc00001), 1f).build()
        assertEquals(
            PathEffectNode.Path1D(firstPath, 1f, 0f, Path1DStyle.TRANSLATE).canonicalId,
            PathEffectNode.Path1D(secondPath, 1f, 0f, Path1DStyle.TRANSLATE).canonicalId,
        )
    }

    @Test
    fun `picture filter graph counts nested scene content against the explicit budget`() {
        val picture = ImageFilterNode.Picture.of(
            SceneSnapshot.of(
                SceneExtent(1, 1),
                ColorSpace.SRGB,
                listOf(
                    SceneCommand.Draw(
                        DrawNode(
                            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)),
                            material = MaterialNode.Solid(ColorARGB.Red),
                            coverage = CoverageRequest.DEFAULT,
                            clip = ClipStackNode.Empty,
                            blend = BlendNode.SrcOver,
                            effects = EffectStack.Empty,
                            transform = Matrix3x3F32.Identity,
                        ),
                    ),
                ),
            ),
            RectF32(-0.5f, 0.25f, 1.5f, 2.75f),
        )

        val depthFailure = assertIs<GraphValidationResult.DepthLimitExceeded>(EffectGraph.validate(picture, GraphLimits(2, 3)))
        assertEquals(3, depthFailure.observedDepth)
    }

    @Test
    fun `picture cull rect is immutable explicit and semantic`() {
        val scene = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList())
        val cull = RectF32(0.25f, 0.5f, 1.5f, 2.75f)
        val source = RectF32(1f, 2f, 3f, 4f)
        val first = ImageFilterNode.Picture.of(scene, cull, source)
        val equivalent = ImageFilterNode.Picture.of(scene, cull, source)
        val different = ImageFilterNode.Picture.of(scene, RectF32(0f, 0f, 1.5f, 2.75f), source)

        assertEquals(cull, first.copyCullRect())
        assertEquals(source, first.copySource())
        assertEquals(first, equivalent)
        assertEquals(first.hashCode(), equivalent.hashCode())
        assertEquals(first.canonicalId, equivalent.canonicalId)
        assertNotEquals(first, different)
        assertNotEquals(first.canonicalId, different.canonicalId)
    }

    @Test
    fun `non data material effect and stack wrappers have structural equality`() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val linearA = MaterialNode.LinearGradient.of(Point2F32(0f, 0f), Point2F32(1f, 1f), stops)
        val linearB = MaterialNode.LinearGradient.of(Point2F32(0f, 0f), Point2F32(1f, 1f), stops)
        val radialA = MaterialNode.RadialGradient.of(Point2F32(0f, 0f), 1f, stops)
        val radialB = MaterialNode.RadialGradient.of(Point2F32(0f, 0f), 1f, stops)
        val sweepA = MaterialNode.SweepGradient.of(Point2F32(0f, 0f), stops = stops)
        val sweepB = MaterialNode.SweepGradient.of(Point2F32(0f, 0f), stops = stops)
        val conicalA = MaterialNode.ConicalGradient.of(Point2F32(0f, 0f), 0f, Point2F32(1f, 1f), 1f, stops)
        val conicalB = MaterialNode.ConicalGradient.of(Point2F32(0f, 0f), 0f, Point2F32(1f, 1f), 1f, stops)
        val cropA = ImageFilterNode.Crop.of(RectF32(0f, 0f, 1f, 1f))
        val cropB = ImageFilterNode.Crop.of(RectF32(0f, 0f, 1f, 1f))
        val tileA = ImageFilterNode.Tile.of(RectF32(0f, 0f, 1f, 1f), RectF32(1f, 1f, 2f, 2f))
        val tileB = ImageFilterNode.Tile.of(RectF32(0f, 0f, 1f, 1f), RectF32(1f, 1f, 2f, 2f))
        val mergeA = ImageFilterNode.Merge.of(listOf(cropA))
        val mergeB = ImageFilterNode.Merge.of(listOf(cropB))
        val magnifierA = ImageFilterNode.Magnifier.of(RectF32(0f, 0f, 1f, 1f), 2f, 3f)
        val magnifierB = ImageFilterNode.Magnifier.of(RectF32(0f, 0f, 1f, 1f), 2f, 3f)
        val convolutionA = ImageFilterNode.MatrixConvolution.of(
            SizeF32(1f, 2f), ImmutableFloats.copyOf(floatArrayOf(1f, 2f)), 1f, 0f,
            Vector2F32(0f, 0f), TileMode.CLAMP, false,
        )
        val convolutionB = ImageFilterNode.MatrixConvolution.of(
            SizeF32(1f, 2f), ImmutableFloats.copyOf(floatArrayOf(1f, 2f)), 1f, 0f,
            Vector2F32(0f, 0f), TileMode.CLAMP, false,
        )
        val clampA = MaterialNode.CoordClamp(linearA, RectF32(0f, 0f, 1f, 1f))
        val clampB = MaterialNode.CoordClamp(linearB, RectF32(0f, 0f, 1f, 1f))
        val stackA = EffectStack.of(listOf(cropA, tileA))
        val stackB = EffectStack.of(listOf(cropB, tileB))
        val materialRuntimeA = MaterialNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.SHADER), emptyMap(), emptyList())
        val materialRuntimeB = MaterialNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.SHADER), emptyMap(), emptyList())
        val colorRuntimeA = ColorFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.COLOR_FILTER), emptyMap(), emptyList())
        val colorRuntimeB = ColorFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.COLOR_FILTER), emptyMap(), emptyList())
        val imageRuntimeA = ImageFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.IMAGE_FILTER), emptyMap(), null, emptyList())
        val imageRuntimeB = ImageFilterNode.RuntimeEffect.of(descriptor(RuntimeEffectAbi.IMAGE_FILTER), emptyMap(), null, emptyList())

        listOf(
            linearA to linearB,
            radialA to radialB,
            sweepA to sweepB,
            conicalA to conicalB,
            cropA to cropB,
            tileA to tileB,
            mergeA to mergeB,
            magnifierA to magnifierB,
            convolutionA to convolutionB,
            clampA to clampB,
            stackA to stackB,
            materialRuntimeA to materialRuntimeB,
            colorRuntimeA to colorRuntimeB,
            imageRuntimeA to imageRuntimeB,
        ).forEach { (first, second) -> assertEquivalent(first, second) }
        assertNotEquals(cropA, ImageFilterNode.Crop.of(RectF32(0f, 0f, 2f, 1f)))
        assertNotEquals(stackA, EffectStack.of(listOf(tileA, cropA)))
    }

    private fun assertEquivalent(first: CanonicalValue, second: CanonicalValue) {
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.canonicalId, second.canonicalId)
    }

    private fun descriptor(abi: RuntimeEffectAbi): RuntimeEffectDescriptor = RuntimeEffectDescriptor.of(
        RuntimeEffectId("effect-$abi"),
        abi,
        RuntimeUniformLayout.of(emptyList()),
        emptyList(),
    )
}
