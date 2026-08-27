package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import java.io.File
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Top-level mask-blur frame pins.
 *
 * Top-level (non-saveLayer) mask blur on core primitives:
 * rect/path/rrect draws with `paint.maskFilter = MaskFilter.Blur` now build and
 * execute prepared with A8 blur coverage materialization (mask → blur-h →
 * blur-v → style → composite), shaded color × blurred coverage. These tests
 * assert `Ready`-class pixels against the CPU oracle
 * [TopLevelMaskBlurPixelOracle] (legacy dispatcher blur math: MaskBlurPlanner +
 * blurKernelUniform kernel, decal sampling, style formulas, composite-route
 * blend oracle). The genuinely non-blur families (mixed uniform layouts,
 * multi-render dst copy) stay terminal elsewhere.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class GPUMaskBlurSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `normal rect blur renders prepared with blurred coverage pixels`() {
        val pixels = renderRectPixels(BlurStyle.NORMAL, 2f)
        val expected = TopLevelMaskBlurPixelOracle.render(
            targetWidth = 32,
            targetHeight = 32,
            shape = rectShape(8f, 8f, 17f, 17f),
            clipBounds = fullTarget(),
            style = BlurStyle.NORMAL,
            sigma = 2f,
            source = ColorARGB.Black,
            blendMode = BlendMode.SRC_OVER,
            destinationEncoded = transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
        assertCenterCoverage(pixels, 12, 12)
    }

    @Test
    fun `bounded rect blur exposes one native draw with no refusal`() {
        val result = renderRectResult(BlurStyle.NORMAL, 2f)

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test
    fun `translated rect blur renders at its transformed device bounds`() {
        requireWebGpu()
        val pixels = Surface(width = 32, height = 32).run {
            canvas {
                translate(4f, 5f)
                drawRect(RectF32(8f, 8f, 17f, 17f), blurPaint(BlurStyle.NORMAL, 2f))
            }
            render().pixels.toUByteArray()
        }
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(12f, 13f, 21f, 22f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )

        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `scaled rect blur remains an explicit bounded-route refusal`() {
        requireWebGpu()
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    scale(1.5f, 1.5f)
                    drawRect(RectF32(8f, 8f, 17f, 17f), blurPaint(BlurStyle.NORMAL, 2f))
                }
                render()
            }
        }

        assertEquals(
            "unsupported.core_primitive.mask_blur.unsupported.mask-filter.blur.transform",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `source-composited translucent blur renders prepared over an opaque destination`() {
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, ColorARGB.Blue)

        // Opaque source.
        val opaque = renderBlurredOverOpaqueBlue(ColorARGB.Red, sigma = 3f)
        val expectedOpaque = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(10f, 10f, 22f, 22f), fullTarget(), BlurStyle.NORMAL, 3f,
            ColorARGB.Red, BlendMode.SRC_OVER, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedOpaque, opaque)

        // Translucent source: premultiplied alpha participates in the coverage shade.
        val translucent = renderBlurredOverOpaqueBlue(ColorARGB.of(128, 255, 0, 0), sigma = 3f)
        val expectedTranslucent = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(10f, 10f, 22f, 22f), fullTarget(), BlurStyle.NORMAL, 3f,
            ColorARGB.of(128, 255, 0, 0), BlendMode.SRC_OVER, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedTranslucent, translucent)
    }

    @Test
    fun `blur touching the mask edge renders prepared with decal falloff`() {
        val pixels = Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 8f, 9f, 17f), blurPaint(BlurStyle.NORMAL, 2f))
            }
            render().pixels.toUByteArray()
        }
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(0f, 8f, 9f, 17f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
        // The halo must fade out beyond the target edge (decal), never wrap.
        assertTrue(pixels[(31 * 32 + 31) * 4 + 3].toInt() == 0)
    }

    @Test
    fun `outer blur renders prepared with outside-only coverage`() {
        val pixels = renderRectPixels(BlurStyle.OUTER, 2f)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(8f, 8f, 17f, 17f), fullTarget(), BlurStyle.OUTER, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
        // OUTER coverage is zero inside the original shape bounds.
        assertEquals(0, pixels[(12 * 32 + 12) * 4 + 3].toInt())
    }

    @Test
    fun `solid and inner blur render prepared with their coverage formulas`() {
        val solid = renderRectPixels(BlurStyle.SOLID, 2f)
        val expectedSolid = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(8f, 8f, 17f, 17f), fullTarget(), BlurStyle.SOLID, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedSolid, solid)
        // SOLID keeps full coverage inside the shape (max(original, blurred)).
        assertEquals(255, solid[(12 * 32 + 12) * 4 + 3].toInt())

        val inner = renderRectPixels(BlurStyle.INNER, 2f)
        val expectedInner = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(8f, 8f, 17f, 17f), fullTarget(), BlurStyle.INNER, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedInner, inner)
        // INNER coverage is zero outside the original shape bounds.
        assertEquals(0, inner[(2 * 32 + 2) * 4 + 3].toInt())
    }

    @Test
    fun `path and rrect blur render prepared with shape coverage masks`() {
        requireWebGpu()

        val triangle = renderTriangle(2f)
        val expectedTriangle = TopLevelMaskBlurPixelOracle.render(
            32, 32,
            TopLevelMaskBlurPixelOracle.Shape.Path(
                vertices = listOf(8f, 8f, 17f, 8f, 12.5f, 17f),
                contourStarts = listOf(0),
                inverseFill = false,
            ),
            fullTarget(), BlurStyle.NORMAL, 2f, ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedTriangle, triangle)

        val rrect = renderRRect(2f)
        val expectedRRect = TopLevelMaskBlurPixelOracle.render(
            32, 32,
            TopLevelMaskBlurPixelOracle.Shape.RRectShape(
                RRectF32.of(
                    rect = RectF32(8f, 8f, 17f, 17f),
                    topLeft = CornerRadiiF32.of(2f, 2f),
                    topRight = CornerRadiiF32.of(2f, 2f),
                    bottomRight = CornerRadiiF32.of(2f, 2f),
                    bottomLeft = CornerRadiiF32.of(2f, 2f),
                ),
            ),
            fullTarget(), BlurStyle.NORMAL, 2f, ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedRRect, rrect)
    }

    @Test
    fun `sigma forty eight rect blur renders prepared at reduced resolution`() {
        val pixels = renderRectPixels(BlurStyle.NORMAL, 48f)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(8f, 8f, 17f, 17f), fullTarget(), BlurStyle.NORMAL, 48f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
        // The wide halo must reach well beyond the shape bounds.
        assertTrue(pixels[(2 * 32 + 2) * 4 + 3].toInt() > 0)
    }

    @Test
    fun `mask blur budget refusal is reachable after admission`() {
        // The legacy budget gate (unsupported.mask-filter.blur.intermediate-budget)
        // is reachable now that the prepared route admits top-level blur: the
        // 8-byte intermediate budget refuses at the MaskBlurPlanner gate.
        requireWebGpu()
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderRectResult(
                style = BlurStyle.NORMAL,
                sigma = 12f,
                config = RenderConfig(maxMaskBlurIntermediateBytes = 8u),
            )
        }
        assertEquals(
            "unsupported.mask-filter.blur.intermediate-budget",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `ordinary paint does not force mask blur composition`() {
        val result = renderOrdinaryRect(Paint.fill(ColorARGB.Red))

        assertTrue(result.diagnostics.entries.none { it.code.startsWith("route:clip:") })
    }

    @Test
    fun `source-composited mask blur frame renders prepared under a wide-open clip`() {
        requireWebGpu()

        // Wide-open clip: full-screen blur.
        val wideOpen = renderSourceCompositedBlur(RenderConfig.DEFAULT) {}
        val expectedWideOpen = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(0f, 0f, 32f, 32f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expectedWideOpen, wideOpen)
    }

    @Test
    fun `mask blur composites under coverage clips are terminal`() {
        requireWebGpu()

        // The blur composite applies NoClip, integer
        // ScissorOnly, or analytic device-rect clips. A stacked non-AA device-rect clip
        // plans a coverage-mask clip and refuses with the documented lane-scope code
        // instead of rendering unclipped (the analytic device-rect case renders
        // prepared under `mask blur composite under an analytic rect clip renders prepared`).
        val stacked = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderSourceCompositedBlur(RenderConfig.DEFAULT) {
                clipRect(RectF32(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = false)
                clipRect(RectF32(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = false)
            }
        }
        assertEquals("unsupported.native-mask-blur.clip", stacked.diagnostic.code.value)
    }

    @Test
    fun `mask blur composite under an analytic rect clip renders prepared`() {
        requireWebGpu()
        val pixels = renderSourceCompositedBlur(RenderConfig.DEFAULT) {
            clipRect(RectF32(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = true)
        }
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(0f, 0f, 32f, 32f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
            clip = TopLevelMaskBlurPixelOracle.RectClip(14f, 14f, 18f, 18f, antiAlias = true),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `mask blur composite clip ramp renders prepared at half integer bounds`() {
        requireWebGpu()
        // Integer bounds (14..18) leave every pixel center at least half a pixel from the
        // clip edge, so both the WGSL `0.5 - distance` ramp and the oracle evaluate to hard
        // 0/1 and the clip term is numerically redundant. Half-integer bounds place pixel
        // centers EXACTLY on the clip edge (coverage 0.5): this pins the AA ramp and the
        // uniform64 packing of fractional bounds (compositeClipUniformBytes).
        val pixels = renderSourceCompositedBlur(RenderConfig.DEFAULT) {
            clipRect(RectF32(14.5f, 14.5f, 18.5f, 18.5f), ClipOp.INTERSECT, antiAlias = true)
        }
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(0f, 0f, 32f, 32f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
            clip = TopLevelMaskBlurPixelOracle.RectClip(14.5f, 14.5f, 18.5f, 18.5f, antiAlias = true),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `mask blur composite under a multi rect analytic clip renders prepared`() {
        requireWebGpu()
        // A rect-decomposable complex clip (AA rect INTERSECT + axis-aligned
        // orthogonal polygon DIFFERENCE) lowers to bounded analytic multi-rect coverage for the
        // blur composite; the L-shape DIFFERENCE polygon decomposes into the band rects
        // [10,8,24,16] and [10,16,18,24].
        val pixels = renderSourceCompositedBlur(RenderConfig.DEFAULT) {
            clipRect(RectF32(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)
            clipPath(
                Path {
                    moveTo(10f, 8f)
                    lineTo(24f, 8f)
                    lineTo(24f, 16f)
                    lineTo(18f, 16f)
                    lineTo(18f, 24f)
                    lineTo(10f, 24f)
                    close()
                },
                ClipOp.DIFFERENCE,
                antiAlias = true,
            )
        }
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(0f, 0f, 32f, 32f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC_OVER, transparent(),
            clip = TopLevelMaskBlurPixelOracle.ComplexClip(
                listOf(
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        1f, 1f, 31f, 31f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Intersect,
                        antiAlias = true,
                    ),
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        10f, 8f, 24f, 16f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Difference,
                        antiAlias = true,
                    ),
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        10f, 16f, 18f, 24f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Difference,
                        antiAlias = true,
                    ),
                ),
            ),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `destination-read blur with a device clip renders prepared via the copy-then-formula lane`() {
        requireWebGpu()
        val pixels = Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                save()
                clipRect(RectF32(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
                drawRect(
                    RectF32(4f, 4f, 28f, 28f),
                    blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.DARKEN),
                )
                restore()
            }
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, ColorARGB.White)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(4f, 4f, 28f, 28f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.DARKEN, destination,
            clip = TopLevelMaskBlurPixelOracle.RectClip(8f, 8f, 24f, 24f, antiAlias = false),
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `darken blur over a non uniform destination samples the full scene snapshot`() {
        requireWebGpu()
        // CRITICAL-1 regression: the destination snapshot must be TARGET-sized so the
        // composite samples the true scene pixel under the blur. A two-tone destination
        // (gray left half, white right half, boundary at x=16) sits under the blur
        // region [12,24)²; any local-mask-sized snapshot would clamp the right half to
        // the gray edge pixel.
        val pixels = Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                drawRect(RectF32(0f, 0f, 16f, 32f), Paint.fill(ColorARGB.of(255, 128, 128, 128)))
                drawRect(
                    RectF32(12f, 8f, 24f, 24f),
                    blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.DARKEN),
                )
            }
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.overlayRect(
            TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, ColorARGB.White),
            32, 32, 0f, 0f, 16f, 32f, ColorARGB.of(255, 128, 128, 128),
        )
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(12f, 8f, 24f, 24f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.DARKEN, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `translucent colored darken blur matches the w3c formula over a mid-tone destination`() {
        requireWebGpu()
        // IMPORTANT-2 regression: the DARKEN composite formula blends the UNPREMULTIPLIED
        // per-channel minimum (W3C compositing, mirroring kanvasBlendAdvancedPremul);
        // a premultiplied-min oracle diverges by tens of levels for colored translucent
        // sources. Source = translucent (128,128,255,0) over mid-gray (0.5).
        val source = ColorARGB.of(128, 128, 255, 0)
        val destinationColor = ColorARGB.of(255, 128, 128, 128)
        val pixels = Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(destinationColor))
                drawRect(
                    RectF32(10f, 10f, 22f, 22f),
                    Paint.fill(source).copy(
                        antiAlias = false,
                        blendMode = BlendMode.DARKEN,
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 2f),
                    ),
                )
            }
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, destinationColor)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(10f, 10f, 22f, 22f), fullTarget(), BlurStyle.NORMAL, 2f,
            source, BlendMode.DARKEN, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun `leading blur composite on a mixed retained frame clears instead of sampling the previous frame`() {
        requireWebGpu()
        // Frame 1 fills the retained session target with blue.
        Surface(width = 32, height = 32).run {
            canvas { drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Blue)) }
            render()
        }
        // Frame 2 is the leading-blur-mixed shape: the FIRST paint op is a mask blur, a later
        // scene render draws only a small red rect. The blur composite sorts before the red
        // rect and must clear the scene target itself (no clear scene render is ordered before
        // it). Outside the blur region and the red rect the target must be transparent, never
        // the retained blue. Probe pixel (0,0): the sigma-2 kernel (5 taps, half 2) reaches the
        // shape [4,12) only at x>=2 or x<=13, so (0,0) lies outside the blur halo (coverage 0).
        val pixels = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(4f, 4f, 12f, 12f), blurPaint(BlurStyle.NORMAL, 2f))
                drawRect(RectF32(20f, 20f, 30f, 30f), Paint.fill(ColorARGB.Red))
            }
            render().pixels.toUByteArray()
        }
        assertEquals(0, pixels[(0 * 32 + 0) * 4 + 3].toInt(), "cleared region outside the blur must be transparent")
        assertEquals(0, pixels[(0 * 32 + 0) * 4 + 2].toInt(), "cleared region must carry no retained blue")
        assertEquals(255, pixels[(25 * 32 + 25) * 4 + 0].toInt(), "the later scene render must draw its red rect")
    }

    @Test
    fun `second blur composite on a two blur frame loads the composited scene instead of clearing it`() {
        requireWebGpu()
        // Frame 1 fills the retained session target with blue.
        Surface(width = 32, height = 32).run {
            canvas { drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Blue)) }
            render()
        }
        // Frame 2 draws TWO blur rects and nothing else. Chain 0's composite is the frame's
        // first scene-target writer and clears; chain 1's composite must LOAD the already
        // composited scene — a "clear" loadOp wipes the entire attachment and only redraws
        // within chain 1's scissor, erasing chain 0's output outside it. Both sigma-2 halos
        // (radius 6) extend the local masks to the same 20x20 size (bounds [6,14) and [14,22)
        // halo-extend to [0,20) and [8,28)), so the lane's one-local-mask-size-per-frame
        // serialization admits the frame. Pixel (10,10) sits in chain 0's shape interior
        // (4px from every [6,14) edge, full kernel coverage) while chain 1's blurred coverage
        // there is zero (its shape edge is 14, out of the 5-tap reach), so the second
        // composite must leave it as chain 0's output.
        val pixels = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(6f, 6f, 14f, 14f), blurPaint(BlurStyle.NORMAL, 2f))
                drawRect(RectF32(14f, 14f, 22f, 22f), blurPaint(BlurStyle.NORMAL, 2f))
            }
            render().pixels.toUByteArray()
        }
        val firstBlurAlpha = pixels[(10 * 32 + 10) * 4 + 3].toInt()
        assertTrue(
            firstBlurAlpha >= 200,
            "the second composite must not clear away the first blur: alpha=$firstBlurAlpha",
        )
    }

    @Test
    fun `source blur renders prepared with replace semantics`() {
        requireWebGpu()
        val pixels = Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                drawRect(
                    RectF32(10f, 10f, 22f, 22f),
                    blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.SRC),
                )
            }
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, ColorARGB.White)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32, rectShape(10f, 10f, 22f, 22f), fullTarget(), BlurStyle.NORMAL, 2f,
            ColorARGB.Black, BlendMode.SRC, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    private fun rectShape(left: Float, top: Float, right: Float, bottom: Float) =
        TopLevelMaskBlurPixelOracle.Shape.Rect(left, top, right, bottom)

    private fun fullTarget() = GPUBounds(0f, 0f, 32f, 32f)

    private fun transparent() = UByteArray(32 * 32 * 4)

    private fun renderBlurredOverOpaqueBlue(source: ColorARGB, sigma: Float): UByteArray =
        Surface(width = 32, height = 32).run {
            requireWebGpu()
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Blue))
                drawRect(
                    RectF32(10f, 10f, 22f, 22f),
                    Paint.fill(source).copy(antiAlias = false, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma)),
                )
            }
            render().pixels.toUByteArray()
        }

    private fun renderOrdinaryRect(paint: Paint) = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRect(RectF32(8f, 8f, 17f, 17f), paint)
        }
        render()
    }

    private fun renderRectPixels(style: BlurStyle, sigma: Float): UByteArray =
        renderRectResult(style, sigma).pixels.toUByteArray()

    private fun renderRectResult(
        style: BlurStyle,
        sigma: Float,
        config: RenderConfig = RenderConfig.DEFAULT,
    ) = Surface(width = 32, height = 32, config = config).run {
        requireWebGpu()
        canvas {
            drawRect(RectF32(8f, 8f, 17f, 17f), blurPaint(style, sigma))
        }
        render()
    }

    private fun renderSourceCompositedBlur(
        config: RenderConfig,
        clip: Canvas.() -> Unit,
    ): UByteArray = Surface(width = 32, height = 32, config = config).run {
        requireWebGpu()
        canvas {
            save()
            clip()
            drawRect(
                RectF32(0f, 0f, 32f, 32f),
                blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.SRC_OVER),
            )
            restore()
        }
        render().pixels.toUByteArray()
    }

    private fun renderTriangle(sigma: Float): UByteArray = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawPath(
                Path {
                    moveTo(8f, 8f)
                    lineTo(17f, 8f)
                    lineTo(12.5f, 17f)
                    close()
                },
                blurPaint(BlurStyle.NORMAL, sigma),
            )
        }
        render().pixels.toUByteArray()
    }

    private fun renderRRect(sigma: Float): UByteArray = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRRect(
                RRectF32.of(
                    rect = RectF32(8f, 8f, 17f, 17f),
                    topLeft = CornerRadiiF32.of(2f, 2f),
                    topRight = CornerRadiiF32.of(2f, 2f),
                    bottomRight = CornerRadiiF32.of(2f, 2f),
                    bottomLeft = CornerRadiiF32.of(2f, 2f),
                ),
                blurPaint(BlurStyle.NORMAL, sigma),
            )
        }
        render().pixels.toUByteArray()
    }

    private fun blurPaint(style: BlurStyle, sigma: Float): Paint = Paint(
        color = ColorARGB.Black,
        maskFilter = MaskFilter.Blur(style, sigma),
        antiAlias = false,
    )

    private fun assertCenterCoverage(pixels: UByteArray, x: Int, y: Int) {
        val alpha = pixels[(y * 32 + x) * 4 + 3].toInt()
        assertTrue(alpha >= 200, "expected blurred coverage at ($x, $y), alpha=$alpha")
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

}
