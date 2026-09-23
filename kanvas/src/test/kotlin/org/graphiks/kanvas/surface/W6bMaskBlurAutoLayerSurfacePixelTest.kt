@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.junit.jupiter.api.Test

/** Public Surface contract for frozen W6b mask coverage and auto-layer materialization. */
class W6bMaskBlurAutoLayerSurfacePixelTest {
    @Test
    fun `mask blur styles transform raw coverage before W5 source shading`() {
        BlurStyle.entries.forEach { style ->
            val actual = renderTranslatedMaskedRect(style)

            W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderStyle(style), actual)
        }
    }

    @Test
    fun `fractional anti aliased coverage uses Porter Duff mask blur styles`() {
        listOf(BlurStyle.SOLID, BlurStyle.OUTER, BlurStyle.INNER).forEach { style ->
            val expected = W6bMaskBlurCpuOracle.renderFractionalStyle(style)
            val actual = renderFractionalMaskedRect(style)

            W6bMaskBlurCpuOracle.assertNear(expected, actual, toleranceI32 = 18)
        }
    }

    @Test
    fun `translated masked draw applies its selected blend once over colored destination`() {
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawRect(fullBounds(), Paint(ColorARGB.Green, antiAlias = false))
                translate(1f, 0f)
                drawRect(localMaskedBounds(), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    blendMode = BlendMode.DST_OUT,
                    antiAlias = false,
                ))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderDstOutOverGreen(), actual)
    }

    @Test
    fun `masked source is materialized before its frozen image blur`() {
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                translate(1f, 0f)
                drawRect(localMaskedBounds(), Paint(
                    ColorARGB.White,
                    imageFilter = ImageFilter.Blur(1f, 1f, org.graphiks.kanvas.paint.TileMode.DECAL),
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderMaskedThenImageBlur(), actual)
    }

    @Test
    fun `explicit W6a layer keeps its masked auto-layer in parent child parent restore order`() {
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawRect(fullBounds(), Paint(ColorARGB.Blue, antiAlias = false))
                saveLayer(SaveLayerRec())
                translate(1f, 0f)
                drawRect(localMaskedBounds(), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
                restore()
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderLayerOverBlue(), actual)
    }

    @Test
    fun `parent and descendant Picture mask blurs preserve sealed alpha overlap and transparent hole`() {
        val bounds = fullBounds()
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(RectF32.ofLTRB(2f, 2f, 6f, 4f), Paint(ColorARGB.of(128, 255, 255, 255), antiAlias = false))
                drawRect(RectF32.ofLTRB(2f, 2f, 4f, 4f), Paint(ColorARGB.of(128, 255, 255, 255), antiAlias = false))
                drawRect(RectF32.ofLTRB(5f, 2f, 6f, 3f), Paint(blendMode = BlendMode.CLEAR, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawPicture(child, Paint(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPicture(parent, Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderNestedPictureMaskBlur(), actual, toleranceI32 = 18)
    }

    @Test
    fun `clipped rounded rect mask blur preserves its frozen analytic coverage`() {
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                clipRect(RectF32.ofLTRB(3f, 2f, 8f, 7f), antiAlias = false)
                val radius = CornerRadiiF32.of(2f, 2f)
                drawRRect(
                    RRectF32.of(RectF32.ofLTRB(2f, 2f, 8f, 7f), radius, radius, radius, radius),
                    Paint(
                        ColorARGB.Red,
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                        antiAlias = true,
                    ),
                )
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderClippedRoundedRectMask(), actual)
    }

    @Test
    fun `convex direct path mask blurs its frozen coverage with red material`() {
        val path = Path()
            .moveTo(2f, 2f)
            .lineTo(8f, 2f)
            .lineTo(2f, 7f)
            .close()
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPath(path, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderDirectTriangleMaskSourceOver(), actual)
    }

    @Test
    fun `stencil path mask applies DST_OUT only at the parent composite`() {
        val path = Path()
            .moveTo(2f, 2f)
            .lineTo(8f, 2f)
            .lineTo(8f, 6f)
            .lineTo(5f, 4f)
            .lineTo(2f, 6f)
            .close()
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawRect(fullBounds(), Paint(ColorARGB.Green, antiAlias = false))
                drawPath(path, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    blendMode = BlendMode.DST_OUT,
                    antiAlias = false,
                ))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderStencilPathDstOutOverGreen(), actual)
    }

    @Test
    fun `stencil path mask shades red material across its blurred coverage`() {
        val path = Path()
            .moveTo(2f, 2f)
            .lineTo(8f, 2f)
            .lineTo(8f, 6f)
            .lineTo(5f, 4f)
            .lineTo(2f, 6f)
            .close()
        val actual = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPath(path, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        W6bMaskBlurCpuOracle.assertNear(W6bMaskBlurCpuOracle.renderStencilPathMaskSourceOver(), actual)
    }

    private fun renderTranslatedMaskedRect(style: BlurStyle): UByteArray =
        Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                translate(1f, 0f)
                drawRect(localMaskedBounds(), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(style, 1f),
                    antiAlias = false,
                ))
            }
        }.render().pixels

    private fun renderFractionalMaskedRect(style: BlurStyle): UByteArray =
        Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(3.25f, 2.25f, 6.75f, 5.75f), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(style, 1f),
                    antiAlias = true,
                ))
            }
        }.render().pixels

    private fun fullBounds(): RectF32 = RectF32.ofLTRB(0f, 0f,
        W6bMaskBlurCpuOracle.widthI32.toFloat(), W6bMaskBlurCpuOracle.heightI32.toFloat())

    private fun localMaskedBounds(): RectF32 = RectF32.ofLTRB(3f, 3f, 6f, 6f)
}
