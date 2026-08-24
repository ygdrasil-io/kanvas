package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPathClipRegressionTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `device rect clip path frame refuses with the path stencil code`() {
        requireWebGpu()

        // The AA background (uniform80) and the analytic-clipped path pair now
        // split into separate layout runs, but the path-stencil cover under an analytic clip
        // still cannot exact the exactly-one-path-pass authority, so the frame re-points to the
        // path-stencil preflight refusal.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    // The AA background paint (Paint.fill default) splits into its own uniform80
                    // layout run; the clipped path pair is the path-analytic-clip run.
                    drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(RectF32(8f, 8f, 24f, 24f))
                    drawPath(
                        Path {
                            moveTo(8f, 8f)
                            lineTo(24f, 8f)
                            lineTo(16f, 24f)
                            close()
                        },
                        Paint.fill(Color.RED).copy(antiAlias = false),
                    )
                    restore()
                }
                render()
            }
        }
        assertEquals(
            "invalid.preflight.core_primitive_path_stencil",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `dst in path frame refuses on the path stencil machinery boundary`() {
        requireWebGpu()

        // The DST_IN path frame splits into the analytic-shape background
        // pass and the path pair pass; the path-stencil machinery's direct authority is
        // uniform32-only, so the shape pass refuses on the path-stencil code.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    drawPath(
                        Path {
                            moveTo(8f, 8f)
                            lineTo(24f, 8f)
                            lineTo(16f, 24f)
                            close()
                        },
                        Paint.fill(Color.BLACK).copy(
                            antiAlias = false,
                            blendMode = BlendMode.DST_IN,
                        ),
                    )
                }
                render()
            }
        }
        assertEquals(
            "invalid.preflight.core_primitive_path_stencil",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `darken rect over destination renders prepared via the multi render dst copy lane`() {
        requireWebGpu()

        // A destination-read rect over an existing destination render is the
        // designed multi-render dst-copy shape (producer render, ordered snapshot copy,
        // consuming render). The prepared direct lane admits it and executes the Graphite
        // copy-then-formula recipe. The paints are hard (antiAlias = false) so the frame stays
        // on the full-coverage direct lane: default-AA rects lower to the analytic-shape
        // dst-read family whose formula program is a designed closed refusal.
        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                drawRect(
                    RectF32(8f, 8f, 24f, 24f),
                    Paint.fill(Color.BLACK).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                )
            }
            render()
        }
        val pixels = result.pixels
        // CPU reference: DARKEN over an opaque white destination = per-channel min; the black
        // source yields opaque black inside the rect and retained white outside.
        assertEquals(255, pixels[(12 * 32 + 12) * 4 + 3].toInt(), "in-rect pixel is opaque")
        assertEquals(0, pixels[(12 * 32 + 12) * 4 + 0].toInt(), "in-rect pixel is DARKEN(black, white) = black")
        assertEquals(255, pixels[(2 * 32 + 2) * 4 + 0].toInt(), "outside the rect the white destination is retained")
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.code.startsWith("route:destination-read:DrawRect:") && entry.reason == "gpu-copy-then-formula"
            },
            "the dst-read multi-render frame must emit the copy-then-formula route evidence",
        )
    }

    @Test
    fun `advanced path blend frame renders prepared via the continued path dst read lane`() {
        requireWebGpu()

        // An unclipped rect plus a destination-reading path now admits the
        // continued path dst-read shape (background render, producer fan Store, ordered snapshot
        // copy, cover fan read-only + dst-read formula).
        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                drawPath(
                    Path {
                        moveTo(8f, 8f)
                        lineTo(24f, 8f)
                        lineTo(16f, 24f)
                        close()
                    },
                    Paint.fill(Color.RED).copy(antiAlias = false, blendMode = BlendMode.DIFFERENCE),
                )
            }
            render()
        }
        val pixels = result.pixels
        // CPU reference: DIFFERENCE(red, white) = |red - white| = cyan inside the path; the
        // destination white is retained outside the path.
        assertEquals(255, pixels[(16 * 32 + 12) * 4 + 3].toInt(), "in-path pixel is opaque")
        assertEquals(0, pixels[(16 * 32 + 12) * 4 + 0].toInt(), "in-path pixel red channel is DIFFERENCE(255, 255) = 0")
        assertEquals(255, pixels[(16 * 32 + 12) * 4 + 1].toInt(), "in-path pixel green channel is DIFFERENCE(0, 255) = 255")
        assertEquals(255, pixels[(4 * 32 + 4) * 4 + 0].toInt(), "outside the path the white destination is retained")
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.code.startsWith("route:destination-read:DrawPath:") && entry.reason == "gpu-copy-then-formula"
            },
            "the continued path dst-read frame must emit the copy-then-formula route evidence",
        )
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }
}
