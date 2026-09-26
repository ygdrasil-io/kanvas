@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Public cross-lane W6e cover: each dynamic case is independently reported by JUnit.
 *
 * Each oracle and public filter is constructed before its case allocates a [Surface].  The
 * rendering route deliberately puts the filter in an inner layer of a nested layer pair, so an
 * omitted filter input must bind to that layer's contextual implicit source rather than to a
 * root or a sibling target.  The source draws are W4 geometry carried by W5 [Paint] materials.
 */
class W6eCrossLaneEffectsSurfacePixelTest {
    @TestFactory
    fun `contextual implicit source`(): Stream<DynamicTest> = allFamilies().stream().map { family ->
        DynamicTest.dynamicTest(family.name) {
            assertCaseThroughW4W5NestedLayer(family)
        }
    }

    @Test
    fun backdropPreviousAndDestinationReadKeepTheirSpecifiedOrder() {
        // These bytes are calculated before the Surface is created.  Backdrop wins over
        // previous for the first child: red DIFFERENCE green.  The next child initializes from
        // that restored parent.  Blue DIFFERENCE first changes x=1 against that retained green,
        // then the W6d 3x1 convolution samples the completed parent-plus-child row.  Reading
        // root content, sampling before the child, or replacing the convolution with identity
        // changes the exact result.
        val afterBackdropChild = rgbaRow(listOf(
            opaqueDifference(ColorARGB.Red, ColorARGB.Green),
            rgba(ColorARGB.Green),
            rgba(ColorARGB.Green),
        ))
        val parentPlusChild = afterBackdropChild.copyOf().also { pixels ->
            opaqueDifference(ColorARGB.Blue, pixels.copyOfRange(4, 8)).copyInto(pixels, 4)
        }
        val expected = W6eAdvancedSamplingCpuOracle.convolution3x1Clamp(
            parentPlusChild,
            floatArrayOf(1f, 0f, 0f),
            kernelOffsetX = 1,
        )
        val previousSampling = ImageFilter.MatrixConvolution(
            SizeF32.of(3f, 1f),
            floatArrayOf(1f, 0f, 0f),
            1f,
            0f,
            Vector2F32(1f, 0f),
            TileMode.CLAMP,
            true,
        )

        val result = Surface(3, 1).also { surface ->
            surface.canvas {
                drawOpaque(0f, 3f, ColorARGB.Blue)
                saveLayer(SaveLayerRec(
                    backdrop = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Green, BlendMode.SRC)),
                    initWithPrevious = true,
                    paint = Paint(blendMode = BlendMode.SRC, antiAlias = false),
                ))
                drawOpaque(0f, 1f, ColorARGB.Red, BlendMode.DIFFERENCE)
                restore()
                saveLayer(SaveLayerRec(
                    initWithPrevious = true,
                    paint = Paint(imageFilter = previousSampling, blendMode = BlendMode.SRC, antiAlias = false),
                ))
                drawOpaque(1f, 2f, ColorARGB.Blue, BlendMode.DIFFERENCE)
                restore()
            }
        }.render()

        assertContentEquals(expected, result.pixels)
        assertRenderAndReadback(result)
    }

    @Test
    fun composeBlurAndOffsetBindTheOuterNodeToTheInnerIntermediate() {
        // Both expected images are calculated through the family-local Blur oracle before a
        // Surface exists.  The inner Offset moves the impulse before the outer Blur samples it;
        // binding the outer node to the raw layer source would instead leave each impulse one
        // pixel to the left.  The second source mutation makes that wrong binding independently
        // observable rather than depending on one fixed impulse location.
        val firstExpected = W6eBlurShadowCpuOracle.blurOpaqueWhiteImpulse(7, 1, 1, 0)
        val secondExpected = W6eBlurShadowCpuOracle.blurOpaqueWhiteImpulse(7, 1, 2, 0)
        val filter = ImageFilter.Compose(
            ImageFilter.Blur(1f, 1f, TileMode.DECAL),
            ImageFilter.Offset(1f, 0f),
        )

        val first = renderNestedFilter(7, 1, filter) {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false))
        }
        assertNear(firstExpected, first.pixels, 12, "Compose Blur Offset first source")
        assertRenderAndReadback(first)

        val second = renderNestedFilter(7, 1, filter) {
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.White, antiAlias = false))
        }
        assertNear(secondExpected, second.pixels, 12, "Compose Blur Offset source mutation")
        assertRenderAndReadback(second)
    }

    @Test
    fun offsetOutputSurvivesThreeUnboundedParentLayers() {
        // The Offset output is [1,2), whereas its raw child content is [0,1).  Two unbounded
        // parents force the reservation to propagate at both restore boundaries; the expected
        // bytes are complete before Surface construction and no saveLayer hint acts as a clip.
        val expected = rgbaRow(listOf(transparent, blue, transparent, transparent))

        val result = Surface(4, 1).also { surface ->
            surface.canvas {
                saveLayer()
                saveLayer()
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false)))
                drawOpaque(0f, 1f, ColorARGB.Blue)
                restore()
                restore()
                restore()
            }
        }.render()

        assertContentEquals(expected, result.pixels)
        assertRenderAndReadback(result)
    }

    private fun allFamilies(): List<FamilyCase> {
        // This declaration intentionally remains one named public case per W6 family.  The
        // four earlier shards own their family equations; this shard combines those witnesses
        // with nesting and reports their failures without a first-failure loop.
        val cropExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val blurExpected = W6eBlurShadowCpuOracle.blurOpaqueWhiteImpulse(7, 7, 0, 3)
        val shadowExpected = W6eBlurShadowCpuOracle.dropShadow(
            width = 7, height = 7, sourceAlpha = impulseAlpha(7, 7, 2, 3),
            sourceColor = ColorARGB.White, dx = 2f, dy = 0f, sigma = 1f,
            shadowColor = ColorARGB.Blue, mode = DropShadowMode.COMPOSITE,
        )
        val colorFilterExpected = ubyteArrayOf(0u, 0u, 0u, 54u)
        val composeExpected = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 54u)
        // The source is opaque white.  Two half-alpha black SRC_OVER passes attenuate each
        // linear-sRGB channel to the independently derived encoded value 136.  With an empty
        // contextual source their alpha is 128 + 128 * (1 - 128 / 255) = 191.749, or 192.
        val mergeExpected = ubyteArrayOf(136u, 136u, 136u, 255u)
        val blendExpected = rgba(ColorARGB.Blue)
        // SRC_IN scales opaque blue by the contextual background alpha.  A half-opaque white
        // source therefore yields linear-premul blue .5, whose sRGB OETF is 188 at alpha 128.
        val blendHalfSourceExpected = ubyteArrayOf(0u, 0u, 188u, 128u)
        val halfOpaqueWhite = ColorARGB.of(128, 255, 255, 255)
        val dilateExpected = opaqueRed(0, 1, 2, width = 3)
        val erodeExpected = opaqueRed(2, width = 5)
        val samplingSource = samplingSourcePixels()
        val convolutionExpected = W6eAdvancedSamplingCpuOracle.convolution3x1Clamp(
            samplingSource, floatArrayOf(1f, 0f, 0f), kernelOffsetX = 1,
        )
        val displacementExpected = W6eAdvancedSamplingCpuOracle.displacementRedNearestClamp(samplingSource, 1f)
        val magnifierExpected = W6eAdvancedSamplingCpuOracle.magnifierNearestClamp(
            samplingSource, lensLeft = -1f, lensRight = 3f, zoom = 2f, inset = .5f,
        )
        val runtimeExpected = ubyteArrayOf(60u, 30u, 15u, 128u)
        val distantDiffuseExpected = W6eLightingCpuOracle.distantDiffuse()
        val pointDiffuseExpected = W6eLightingCpuOracle.pointDiffuse()
        val spotDiffuseExpected = W6eLightingCpuOracle.spotDiffuse()
        val distantSpecularExpected = W6eLightingCpuOracle.distantSpecular()
        val pointSpecularExpected = W6eLightingCpuOracle.pointSpecular()
        val spotSpecularExpected = W6eLightingCpuOracle.spotSpecular()
        val offsetExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val tileExpected = rgbaRow(listOf(blue, transparent, blue, transparent))
        val pictureExpected = rgba(ColorARGB.Red)
        val sourcePicture = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
        }.finishRecordingAsPicture()
        val lightingSource: Canvas.() -> Unit = {
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f),
                    Paint(ColorARGB.White, antiAlias = false))
            }
        }
        // This omitted input must bind to the immediate nested layer.  A root/blank binding
        // makes Blend's half-alpha variation transparent and makes Merge black/translucent,
        // instead of the independently declared blue/gray results below.
        val contextualSource = ImageFilter.ColorFilter(ColorFilter.Matrix(ColorMatrixF32.ofIdentity()))
        val blueFilter = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC))
        val sharedBlack = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.of(128, 0, 0, 0), BlendMode.SRC))

        return listOf(
            FamilyCase("Crop", 4, 1, cropExpected, null,
                ImageFilter.Crop(RectF32.ofLTRB(1f, 0f, 2f, 1f), TileMode.DECAL)) {
                drawOpaque(0f, 1f, ColorARGB.Red); drawOpaque(1f, 2f, ColorARGB.Blue)
            },
            FamilyCase("Blur", 7, 7, blurExpected, 12, ImageFilter.Blur(1f, 1f, TileMode.DECAL)) {
                drawRect(RectF32.ofLTRB(0f, 3f, 1f, 4f), Paint(ColorARGB.White, antiAlias = false))
            },
            FamilyCase("DropShadow", 7, 7, shadowExpected, 12,
                ImageFilter.DropShadow(2f, 0f, 1f, 1f, ColorARGB.Blue)) {
                drawRect(RectF32.ofLTRB(2f, 3f, 3f, 4f), Paint(ColorARGB.White, antiAlias = false))
            },
            FamilyCase("ColorFilter", 1, 1, colorFilterExpected, null,
                ImageFilter.ColorFilter(ColorFilter.Luma)) { drawOpaque(0f, 1f, ColorARGB.Red) },
            FamilyCase("Compose", 2, 1, composeExpected, null,
                ImageFilter.Compose(ImageFilter.ColorFilter(ColorFilter.Luma), ImageFilter.Offset(1f, 0f))) {
                drawOpaque(0f, 1f, ColorARGB.Red)
            },
            FamilyCase("Blend", 1, 1, blendExpected, null,
                SourceVariation(blendHalfSourceExpected) { drawOpaque(0f, 1f, halfOpaqueWhite) },
                ImageFilter.Blend(BlendMode.SRC_IN, contextualSource, blueFilter),
                { drawOpaque(0f, 1f, ColorARGB.White) }),
            FamilyCase("Dilate", 3, 1, dilateExpected, null, ImageFilter.Dilate(1f, 0f)) {
                drawOpaque(1f, 2f, ColorARGB.Red)
            },
            FamilyCase("Erode", 5, 1, erodeExpected, null, ImageFilter.Erode(1f, 0f)) {
                drawOpaque(1f, 4f, ColorARGB.Red)
            },
            FamilyCase("DistantDiffuse", 3, 3, distantDiffuseExpected, 2,
                ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f), lightingSource),
            FamilyCase("PointDiffuse", 3, 3, pointDiffuseExpected, 2,
                ImageFilter.PointLitDiffuse(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f), lightingSource),
            FamilyCase("SpotDiffuse", 3, 3, spotDiffuseExpected, 2,
                ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f,
                    ColorARGB.White, 1f, 1f), lightingSource),
            FamilyCase("DistantSpecular", 3, 3, distantSpecularExpected, 2,
                ImageFilter.DistantLitSpecular(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f), lightingSource),
            FamilyCase("PointSpecular", 3, 3, pointSpecularExpected, 2,
                ImageFilter.PointLitSpecular(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f), lightingSource),
            FamilyCase("SpotSpecular", 3, 3, spotSpecularExpected, 2,
                ImageFilter.SpotLitSpecular(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f,
                    ColorARGB.White, 1f, 1f, 2f), lightingSource),
            FamilyCase("Offset", 4, 1, offsetExpected, null,
                ImageFilter.Offset(1f, 0f)) { drawOpaque(0f, 1f, ColorARGB.Blue) },
            FamilyCase("Tile", 4, 1, tileExpected, null,
                ImageFilter.Tile(RectF32.ofLTRB(0f, 0f, 2f, 1f), RectF32.ofLTRB(0f, 0f, 4f, 1f))) {
                drawOpaque(0f, 1f, ColorARGB.Blue)
            },
            FamilyCase("Merge", 1, 1, mergeExpected, null,
                null, ImageFilter.Merge(listOf(contextualSource, sharedBlack, sharedBlack)),
                { drawOpaque(0f, 1f, ColorARGB.White) }),
            FamilyCase("DisplacementMap", 3, 1, displacementExpected, 1,
                ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 1f, ImageFilter.Offset(0f, 0f))) {
                drawSamplingSource()
            },
            FamilyCase("Picture", 1, 1, pictureExpected, null, ImageFilter.Picture(sourcePicture)) {
                drawOpaque(0f, 1f, ColorARGB.Blue)
            },
            FamilyCase("Magnifier", 3, 1, magnifierExpected, 1,
                ImageFilter.Magnifier(RectF32.ofLTRB(-1f, 0f, 3f, 1f), 2f, .5f)) { drawSamplingSource() },
            FamilyCase("MatrixConvolution", 3, 1, convolutionExpected, 1,
                ImageFilter.MatrixConvolution(SizeF32.of(3f, 1f), floatArrayOf(1f, 0f, 0f), 1f, 0f,
                    Vector2F32(1f, 0f), TileMode.CLAMP, true)) { drawSamplingSource() },
            FamilyCase("RuntimeImageFilter", 1, 1, runtimeExpected, null, imageOpacity(.5f)) {
                drawRect(unit, Paint(shader = Shader.Image(Image.fromPixels(1, 1,
                    ubyteArrayOf(85u, 45u, 24u, 255u).toByteArray(), alphaType = AlphaType.PREMUL)), antiAlias = false))
            },
        )
    }

    private fun assertCaseThroughW4W5NestedLayer(family: FamilyCase) {
        fun renderNested(draw: Canvas.() -> Unit): RenderResult = renderNestedFilter(
            family.width, family.height, family.filter, draw,
        )

        val result = renderNested(family.draw)
        family.maxChannelDelta?.let { delta -> assertNear(family.expected, result.pixels, delta, family.name) }
            ?: assertContentEquals(family.expected, result.pixels, family.name)
        assertRenderAndReadback(result)
        family.sourceVariation?.let { variation ->
            val variationResult = renderNested(variation.draw)
            assertContentEquals(variation.expected, variationResult.pixels, "${family.name} contextual source variation")
            assertRenderAndReadback(variationResult)
        }
    }

    private fun renderNestedFilter(
        width: Int,
        height: Int,
        filter: ImageFilter,
        draw: Canvas.() -> Unit,
    ): RenderResult = Surface(width, height).also { surface ->
        surface.canvas {
            saveLayer()
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            draw(this)
            restore()
            restore()
        }
    }.render()

    private fun assertNear(expected: UByteArray, actual: UByteArray, maxDelta: Int, name: String) {
        assertTrue(expected.size == actual.size, "$name expected=${expected.size} actual=${actual.size}")
        expected.indices.forEach { index -> assertTrue(
            abs(expected[index].toInt() - actual[index].toInt()) <= maxDelta,
            "$name channel $index expected=${expected[index]} actual=${actual[index]}",
        ) }
    }

    private fun assertRenderAndReadback(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun Canvas.drawOpaque(left: Float, right: Float, color: ColorARGB, blendMode: BlendMode = BlendMode.SRC_OVER) {
        drawRect(RectF32.ofLTRB(left, 0f, right, 1f), Paint(color, blendMode = blendMode, antiAlias = false))
    }

    private fun Canvas.drawSamplingSource() {
        drawOpaque(0f, 1f, ColorARGB.Red); drawOpaque(1f, 2f, ColorARGB.Green); drawOpaque(2f, 3f, ColorARGB.Blue)
    }

    private fun imageOpacity(alpha: Float): ImageFilter.RuntimeEffect = ImageFilter.RuntimeEffect(
        requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
        UniformBlock { float1("alpha", alpha) },
    )

    private fun opaqueDifference(source: ColorARGB, destination: ColorARGB): UByteArray = rgba(
        difference(source.red, destination.red), difference(source.green, destination.green), difference(source.blue, destination.blue),
    )

    private fun opaqueDifference(source: ColorARGB, destination: UByteArray): UByteArray {
        require(destination.size == 4)
        return rgba(
            difference(source.red, destination[0].toInt()),
            difference(source.green, destination[1].toInt()),
            difference(source.blue, destination[2].toInt()),
        )
    }

    private fun difference(source: Int, destination: Int): Int {
        fun decode(value: Int): Double = (value / 255.0).let { encoded ->
            if (encoded <= .04045) encoded / 12.92 else ((encoded + .055) / 1.055).pow(2.4)
        }
        val linear = abs(decode(source) - decode(destination))
        val encoded = if (linear <= .0031308) linear * 12.92 else 1.055 * linear.pow(1.0 / 2.4) - .055
        return (encoded * 255.0).roundToInt()
    }

    private fun samplingSourcePixels(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u, 0u, 255u, 0u, 255u, 0u, 0u, 255u, 255u,
    )

    private fun impulseAlpha(width: Int, height: Int, x: Int, y: Int): UByteArray =
        UByteArray(width * height).also { it[x + y * width] = 255u }

    private fun opaqueRed(vararg indices: Int, width: Int): UByteArray = UByteArray(width * 4).also { pixels ->
        indices.forEach { index -> pixels[index * 4] = 255u; pixels[index * 4 + 3] = 255u }
    }

    private fun rgba(color: ColorARGB): UByteArray = rgba(color.red, color.green, color.blue, color.alpha)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    private fun rgbaRow(pixels: List<UByteArray>): UByteArray = pixels.flatMap { it.asList() }.toUByteArray()

    private data class FamilyCase(
        val name: String,
        val width: Int,
        val height: Int,
        val expected: UByteArray,
        val maxChannelDelta: Int?,
        val sourceVariation: SourceVariation?,
        val filter: ImageFilter,
        val draw: Canvas.() -> Unit,
    ) {
        constructor(
            name: String,
            width: Int,
            height: Int,
            expected: UByteArray,
            maxChannelDelta: Int?,
            filter: ImageFilter,
            draw: Canvas.() -> Unit,
        ) : this(name, width, height, expected, maxChannelDelta, null, filter, draw)
    }

    private data class SourceVariation(
        val expected: UByteArray,
        val draw: Canvas.() -> Unit,
    )

    private companion object {
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
    }
}
