package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
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
    fun `device rect clip path frame refuses with the mixed uniform layouts code`() {
        requireWebGpu()

        // FP-09 terminal refusal: the frame mixes an unclipped uniform32 rect with a
        // scissored path (path layout) — the designed mixed-uniform-layouts family.
        // Pre-FP-09 the legacy renderer rendered this frame (green at the FP-08 tip
        // accaea616); the route collapse converted it to this stable code (Task 6
        // evidence family 4).
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(8f, 8f, 24f, 24f))
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
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `dst in path frame refuses with the mixed uniform layouts code`() {
        requireWebGpu()

        // FP-09 terminal refusal: an unclipped rect plus a dst-read path mixes the
        // uniform32 lane with the path dst-read lane — the designed mixed-uniform-layouts
        // family. Pre-FP-09 the legacy renderer rendered it (green at accaea616).
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
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
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `darken rect over destination renders prepared via the multi render dst copy lane`() {
        requireWebGpu()

        // FP-11 Task 4: a destination-read rect over an existing destination render is the
        // designed multi-render dst-copy shape (producer render, ordered snapshot copy,
        // consuming render). The prepared direct lane admits it and executes the Graphite
        // copy-then-formula recipe. The paints are hard (antiAlias = false) so the frame stays
        // on the full-coverage direct lane: default-AA rects lower to the analytic-shape
        // dst-read family whose formula program is a designed closed refusal. Pre-FP-09 the
        // legacy renderer rendered it (green at the FP-08 tip accaea616); the FP-09 route
        // collapse refused it by name.
        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                drawRect(
                    Rect(8f, 8f, 24f, 24f),
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
    fun `advanced path blend frame refuses with the mixed uniform layouts code`() {
        requireWebGpu()

        // FP-09 terminal refusal: an unclipped rect plus a dst-read path mixes the
        // uniform32 lane with the path dst-read lane — the designed mixed-uniform-layouts
        // family. Pre-FP-09 the legacy renderer rendered this frame with the
        // copy-then-formula route (green at the FP-08 tip accaea616).
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
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
        }
        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            failure.diagnostic.code.value,
        )
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }
}
