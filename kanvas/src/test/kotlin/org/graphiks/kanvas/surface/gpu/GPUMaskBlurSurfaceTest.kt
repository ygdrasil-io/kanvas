package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Top-level mask-blur frame pins after FP-09.
 *
 * Pre-FP-09, plain (non-saveLayer) mask-blur rect/rrect/path frames rendered through the
 * legacy immediate renderer (verified green at the FP-08 tip accaea616). FP-09 deleted the
 * legacy mask-blur machinery (Tasks 7-8) and the prepared top-level blur route is not
 * wired, so these frames now refuse with the designed stable codes (Task 6 evidence §4:
 * `unsupported.core_primitive.rect.analysis_authority_missing` for mask-blur rect frames).
 * These tests pin the fixtures and their exact terminal codes; mask blur inside a
 * saveLayer scope still renders through the composite capture's prepared
 * `GPUPreparedMaskFilterLowerer` (FP-07 composite route, unchanged).
 */
@OptIn(ExperimentalUnsignedTypes::class)
class GPUMaskBlurSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `normal rect blur is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing { renderRectResult(BlurStyle.NORMAL, 2f) }
    }

    @Test
    fun `source-composited translucent blur is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing {
            renderBlurredOverOpaqueBlue(Color.RED, sigma = 3f)
            renderBlurredOverOpaqueBlue(Color.fromArgb(128, 255, 0, 0), sigma = 3f)
        }
    }

    @Test
    fun `blur touching the mask edge is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing {
            Surface(width = 32, height = 32).run {
                requireWebGpu()
                canvas {
                    drawRect(Rect(0f, 8f, 9f, 17f), blurPaint(BlurStyle.NORMAL, 2f))
                }
                render()
            }
        }
    }

    @Test
    fun `outer blur is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing { renderRectResult(BlurStyle.OUTER, 2f) }
    }

    @Test
    fun `solid and inner blur are terminal analysis authority missing refusals`() {
        // Each style needs its own assert block: the terminal exception is thrown inside
        // the wrapped lambda, so a single block would only ever exercise the first style.
        assertFatalAnalysisAuthorityMissing { renderRectResult(BlurStyle.SOLID, 2f) }
        assertFatalAnalysisAuthorityMissing { renderRectResult(BlurStyle.INNER, 2f) }
    }

    @Test
    fun `path and rrect blur are terminal capability refusals`() {
        requireWebGpu()

        // The triangle blur frame refuses at the first-route planner's FillPath
        // capability gate (unsupported.pipeline.capability_missing) before any native
        // work. The rrect blur frame passes the first-route planner but refuses at the
        // recording authority (invalid.recording.core_primitive_semantic_authority): its
        // gathered core rrect semantic cannot match the analyzed blur lane. Pre-FP-09 the
        // legacy renderer materialized both blur masks (green at the FP-08 tip accaea616).
        val triangle = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderTriangle(2f)
        }
        assertEquals(
            "unsupported.pipeline.capability_missing",
            triangle.diagnostic.code.value,
        )
        val rrect = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderRRect(2f)
        }
        assertEquals(
            "invalid.recording.core_primitive_semantic_authority",
            rrect.diagnostic.code.value,
        )
    }

    @Test
    fun `sigma forty eight rect blur is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing { renderRectResult(BlurStyle.NORMAL, 48f) }
    }

    @Test
    fun `budget refusal is preempted by the analysis authority refusal`() {
        // The legacy budget gate (unsupported.mask-filter.blur.intermediate-budget) is
        // unreachable: the frame refuses at the core-semantic builder first.
        assertFatalAnalysisAuthorityMissing {
            renderRectResult(
                style = BlurStyle.NORMAL,
                sigma = 12f,
                config = RenderConfig(maxMaskBlurIntermediateBytes = 8u),
            )
        }
    }

    @Test
    fun `ordinary paint does not force mask blur composition`() {
        val result = renderOrdinaryRect(Paint.fill(Color.RED))

        assertTrue(result.diagnostics.entries.none { it.code.startsWith("route:clip:") })
    }

    @Test
    fun `source-composited mask blur frames are terminal analysis authority missing refusals`() {
        // Pre-FP-09, a device-clipped source-composited blur dispatched once and a
        // wide-open/exhausted one refused at the budget gate; both behaviors were legacy
        // renderer behavior. After the collapse every top-level blur frame refuses at the
        // core-semantic builder with the designed analysis_authority_missing code.
        assertFatalAnalysisAuthorityMissing {
            renderSourceCompositedBlur(RenderConfig(maxMaskBlurIntermediateBytes = 1_024u)) {
                clipRect(Rect(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = true)
            }
            renderSourceCompositedBlur(RenderConfig(maxMaskBlurIntermediateBytes = 1_024u)) {}
            renderSourceCompositedBlur(RenderConfig(maxMaskBlurIntermediateBytes = 1_024u)) {
                clipRect(Rect(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = false)
                clipRect(Rect(14f, 14f, 18f, 18f), ClipOp.INTERSECT, antiAlias = false)
            }
        }
    }

    @Test
    fun `destination-read blur with a device clip is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing {
            Surface(width = 32, height = 32).run {
                requireWebGpu()
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
                    drawRect(
                        Rect(4f, 4f, 28f, 28f),
                        blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.DARKEN),
                    )
                    restore()
                }
                render()
            }
        }
    }

    @Test
    fun `source blur is a terminal analysis authority missing refusal`() {
        assertFatalAnalysisAuthorityMissing {
            Surface(width = 32, height = 32).run {
                requireWebGpu()
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    drawRect(
                        Rect(10f, 10f, 22f, 22f),
                        blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.SRC),
                    )
                }
                render()
            }
        }
    }

    private fun assertFatalAnalysisAuthorityMissing(render: () -> Unit) {
        requireWebGpu()
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> { render() }
        assertEquals(
            "unsupported.core_primitive.rect.analysis_authority_missing",
            failure.diagnostic.code.value,
        )
    }

    private fun renderBlurredOverOpaqueBlue(source: Color, sigma: Float) = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.BLUE))
            drawRect(
                Rect(10f, 10f, 22f, 22f),
                Paint.fill(source).copy(antiAlias = false, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma)),
            )
        }
        render()
    }

    private fun renderOrdinaryRect(paint: Paint) = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRect(Rect(8f, 8f, 17f, 17f), paint)
        }
        render()
    }

    private fun renderRectResult(
        style: BlurStyle,
        sigma: Float,
        config: RenderConfig = RenderConfig.DEFAULT,
    ) = Surface(width = 32, height = 32, config = config).run {
        requireWebGpu()
        canvas {
            drawRect(Rect(8f, 8f, 17f, 17f), blurPaint(style, sigma))
        }
        render()
    }

    private fun renderSourceCompositedBlur(
        config: RenderConfig,
        clip: Canvas.() -> Unit,
    ) = Surface(width = 32, height = 32, config = config).run {
        requireWebGpu()
        canvas {
            save()
            clip()
            drawRect(
                Rect(0f, 0f, 32f, 32f),
                blurPaint(BlurStyle.NORMAL, 2f).copy(blendMode = BlendMode.SRC_OVER),
            )
            restore()
        }
        render()
    }

    private fun renderTriangle(sigma: Float): ByteArray = Surface(width = 32, height = 32).run {
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
        render().pixels.toByteArray()
    }

    private fun renderRRect(sigma: Float): ByteArray = Surface(width = 32, height = 32).run {
        requireWebGpu()
        canvas {
            drawRRect(
                RRect(
                    rect = Rect(8f, 8f, 17f, 17f),
                    topLeft = CornerRadii(2f, 2f),
                    topRight = CornerRadii(2f, 2f),
                    bottomRight = CornerRadii(2f, 2f),
                    bottomLeft = CornerRadii(2f, 2f),
                ),
                blurPaint(BlurStyle.NORMAL, sigma),
            )
        }
        render().pixels.toByteArray()
    }

    private fun blurPaint(style: BlurStyle, sigma: Float): Paint = Paint(
        color = Color.BLACK,
        maskFilter = MaskFilter.Blur(style, sigma),
        antiAlias = false,
    )

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }
}
