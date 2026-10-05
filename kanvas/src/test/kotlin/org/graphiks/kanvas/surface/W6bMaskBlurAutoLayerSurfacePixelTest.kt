@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

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
            // These style paths retain the original coverage beside its blur; establish the
            // public oracle before Surface construction for the CoverageRetain fullscreen site.
            val expected = W6bMaskBlurCpuOracle.renderFractionalStyle(style)
            val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
                surface.canvas {
                    drawRect(RectF32.ofLTRB(3.25f, 2.25f, 6.75f, 5.75f), Paint(
                        ColorARGB.Red, maskFilter = MaskFilter.Blur(style, 1f), antiAlias = true,
                    ))
                }
            }.render()
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                result.nativeEvidenceScopeKinds.toString())

            W6bMaskBlurCpuOracle.assertNear(expected, result.pixels, toleranceI32 = 18)
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
        // MaterializedSource owns the filtered source and its separate coverage input.
        // Establish this causal oracle before creating the Surface native work.
        val expected = W6bMaskBlurCpuOracle.renderMaskedThenImageBlur()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                translate(1f, 0f)
                drawRect(localMaskedBounds(), Paint(
                    ColorARGB.White,
                    imageFilter = ImageFilter.Blur(1f, 1f, org.graphiks.kanvas.paint.TileMode.DECAL),
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())

        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `Picture owned masked source multiplies its retained alpha by frozen coverage`() {
        // A child Picture routes its masked draw through appendPlannedDraw: it has coverage but
        // no RenderPass W6b raster binding, so MaterializedSource must retain and multiply alpha.
        val expected = W6bMaskBlurCpuOracle.renderPictureOwnedMultiplyMaskBlur()
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(fullBounds()).drawRect(RectF32.ofLTRB(3f, 2f, 7f, 6f), Paint(
                ColorARGB.of(128, 255, 255, 255),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas { drawPicture(picture) }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())

        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `explicit W6a layer keeps its masked auto-layer in parent child parent restore order`() {
        // This non-AA rect takes the planner-owned CoverageSolidRect fullscreen branch.
        val expected = W6bMaskBlurCpuOracle.renderLayerOverBlue()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
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
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())

        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `parent and descendant Picture mask blurs preserve sealed alpha overlap and transparent hole`() {
        // Fix the public pixel oracle before either Picture or Surface construction: this witness
        // crosses the CoverageAlpha source path, including target-local sampling and its Load.
        val expected = W6bMaskBlurCpuOracle.renderNestedPictureMaskBlur()
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
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPicture(parent, Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
            }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())

        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels, toleranceI32 = 18)
    }

    @Test
    fun `clipped rounded rect mask blur preserves its frozen analytic coverage`() {
        val expected = W6bMaskBlurCpuOracle.renderClippedRoundedRectMask()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
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
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        val actual = result.pixels
        val outsideClipOffsetI32 = (4 * W6bMaskBlurCpuOracle.widthI32 + 2) * 4
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u),
            expected.copyOfRange(outsideClipOffsetI32, outsideClipOffsetI32 + 4))
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u),
            actual.copyOfRange(outsideClipOffsetI32, outsideClipOffsetI32 + 4))
        assertTrue(expected[(4 * W6bMaskBlurCpuOracle.widthI32 + 4) * 4 + 3] > 0u,
            "Expected non-zero RRect coverage inside the clip")

        W6bMaskBlurCpuOracle.assertNear(expected, actual)
    }

    @Test
    fun `convex direct path mask blurs its frozen coverage with red material`() {
        val expected = W6bMaskBlurCpuOracle.renderDirectTriangleMaskSourceOver()
        val path = Path()
            .moveTo(2f, 2f)
            .lineTo(8f, 2f)
            .lineTo(2f, 7f)
            .close()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPath(path, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())

        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `point mask blur uses its frozen triangulated coverage`() {
        val expected = W6bMaskBlurCpuOracle.renderPointMaskSourceOver()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                drawPoint(5f, 4f, Paint(
                    ColorARGB.Red,
                    strokeWidth = 2f,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
                    antiAlias = false,
                ))
            }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
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

    @Test
    fun `translated layer stencil path mask blur retains frozen coverage`() {
        val expected = W6bMaskBlurCpuOracle.renderTranslatedStencilPathMaskSourceOver()
        val path = Path()
            .moveTo(1f, 1f).lineTo(7f, 1f).lineTo(7f, 5f)
            .lineTo(4f, 3f).lineTo(1f, 5f).close()
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas {
                saveLayer(SaveLayerRec())
                translate(1f, 1f)
                drawPath(path, Paint(ColorARGB.Red,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
                restore()
            }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        val causalOffsetI32 = (4 * W6bMaskBlurCpuOracle.widthI32 + 4) * 4
        assertTrue(expected[causalOffsetI32 + 3] > 0u, "Expected stencil coverage at translated interior pixel")
        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `even odd stencil donut mask blur retains its frozen hole`() {
        val expected = W6bMaskBlurCpuOracle.renderEvenOddDonutMaskSourceOver()
        val path = Path().apply {
            addRect(RectF32.ofLTRB(2f, 2f, 8f, 6f))
            addRect(RectF32.ofLTRB(4f, 3f, 6f, 5f))
            fillType = FillType.EVEN_ODD
        }
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas { drawPath(path, Paint(ColorARGB.Red,
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false)) }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        val holeOffsetI32 = (4 * W6bMaskBlurCpuOracle.widthI32 + 5) * 4
        assertTrue(expected[holeOffsetI32 + 3] < 255u, "Expected blurred EVEN_ODD hole")
        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
    }

    @Test
    fun `bevelled elbow path stroke mask blur retains frozen stencil coverage`() {
        // The non-axis-aligned elbow keeps the CPU pixel centres off the bevel's shared
        // triangle edge, so the independent hard-edge oracle does not assume a GPU edge rule.
        val path = Path().apply { moveTo(2.25f, 2.25f); lineTo(7.25f, 2.25f); lineTo(8.25f, 6.25f); lineTo(3.25f, 6.25f) }
        val paint = Paint(ColorARGB.Red, style = PaintStyle.STROKE, strokeWidth = 2f,
            strokeJoin = StrokeJoin.BEVEL, strokeCap = StrokeCap.BUTT, antiAlias = false,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f))
        val coverage = W4dPathStrokeCpuOracle.render(W6bMaskBlurCpuOracle.widthI32,
            W6bMaskBlurCpuOracle.heightI32, listOf(W4dPathStrokeCpuOracle.Draw(path.toPathF32(),
                paint.copy(maskFilter = null), scissorI32 = RectI32(0, 0, W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32))))
        val expected = W6bMaskBlurCpuOracle.renderStrokeMaskSourceOver(coverage)
        val result = Surface(W6bMaskBlurCpuOracle.widthI32, W6bMaskBlurCpuOracle.heightI32).also { surface ->
            surface.canvas { drawPath(path, paint) }
        }.render()
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
        W6bMaskBlurCpuOracle.assertNear(expected, result.pixels)
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
