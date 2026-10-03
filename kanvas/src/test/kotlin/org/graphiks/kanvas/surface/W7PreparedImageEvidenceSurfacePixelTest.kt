@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public native evidence and visual-count witnesses for prepared Vertices and DrawMesh. */
class W7PreparedImageEvidenceSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // Release the shared native runtime once, after all same-Surface replay witnesses.
            GPUBackendRuntimeFactory.dispose()
        }

        private val BLUE_IMAGE = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte()),
            alphaType = AlphaType.PREMUL,
            sourceId = "w7-prepared-image-evidence-blue",
        )
        private val UNIT = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        private val BLUE = ubyteArrayOf(0u, 0u, 255u, 255u)
        private val RED = ubyteArrayOf(255u, 0u, 0u, 255u)
        private val CLEAR = ubyteArrayOf(0u, 0u, 0u, 0u)
    }

    /**
     * Mutation rationale: losing either mapped image owner from visual accounting leaves the
     * blue pixel intact but changes opsDispatched; synthesizing scopes or completion without a
     * native submission leaves lifecycle evidence inconsistent with this public Surface result.
     */
    @Test
    fun verticesAndMeshPublishCompletedNativeEvidence() {
        val verticesSurface = Surface(1, 1, format = PixelFormat.RGBA8).also { surface ->
            surface.canvas { drawVertices(visibleTriangle(), imagePaint()) }
        }
        val meshSurface = Surface(1, 1, format = PixelFormat.RGBA8).also { surface ->
            // This public overload records DisplayOp.DrawMesh; Mesh facade forwarding is not used.
            surface.canvas { drawVertices(visibleTriangle(), BlendMode.SRC_ATOP, imagePaint()) }
        }

        assertReplay("vertices", verticesSurface, BLUE, expectedDispatched = 1, visible = true)
        assertReplay("mesh", meshSurface, BLUE, expectedDispatched = 1, visible = true)
    }

    /**
     * Mutation rationale: counting a culled triangle or a DST no-op as an owner changes the
     * expected count; dropping Vertices undercounts the two surviving visual owners. The final
     * red pixel independently proves that the later ordinary Rect still reaches the same frame.
     */
    @Test
    fun mixedVerticesExcludeCulledAndNoOpOwners() {
        val surface = Surface(1, 1, format = PixelFormat.RGBA8).also { frame ->
            frame.canvas {
                clipRect(UNIT, antiAlias = false)
                drawVertices(visibleTriangle(), imagePaint())
                drawVertices(shiftedTriangle(10f, 10f), imagePaint())
                drawVertices(visibleTriangle(), imagePaint().copy(blendMode = BlendMode.DST))
                drawRect(UNIT, Paint(color = ColorARGB.Red, blendMode = BlendMode.SRC, antiAlias = false))
            }
        }

        assertReplay("mixed-vertices", surface, RED, expectedDispatched = 2, visible = true)
    }

    /**
     * Mutation rationale: a real GPU initialization with no surviving Vertices owner must retain
     * QueueSubmitted then CompletionSucceeded evidence while dispatching zero visual operations
     * and returning clear. A native synthetic-clear draw may still occur; it must not become a
     * visual owner. Lifecycle assembled without native completion fails the trace and deltas.
     */
    @Test
    fun zeroSurvivorVerticesRetainRealGpuInitializationEvidence() {
        val surface = Surface(1, 1, format = PixelFormat.RGBA8).also { frame ->
            frame.canvas {
                drawVertices(visibleTriangle(), imagePaint().copy(blendMode = BlendMode.DST))
            }
        }

        assertReplay("zero-survivor-vertices", surface, CLEAR, expectedDispatched = 0, visible = false)
    }

    private fun assertReplay(
        name: String,
        surface: Surface,
        expectedPixels: UByteArray,
        expectedDispatched: Int,
        visible: Boolean,
    ) {
        var firstResult: RenderResult? = null
        repeat(2) { replayIndex ->
            val result = surface.render()
            // Preserve full public evidence before any assertion can stop the fixture.
            println(
                "$name replay=${replayIndex + 1} completeRGBA=${result.pixels.toList()} " +
                    "stats=${result.stats} scopes=${result.nativeEvidenceScopeKinds} " +
                    "counters=${result.nativeEvidenceCounters} structuralSteps=${result.structuralSteps}",
            )

            assertEquals(1, result.width, "$name width")
            assertEquals(1, result.height, "$name height")
            assertEquals(PixelFormat.RGBA8, result.format, "$name format")
            assertEquals(4, result.pixels.size, "$name complete 1x1 RGBA8 buffer")
            result.assertClean()
            assertContentEquals(expectedPixels, result.pixels, "$name full RGBA pixel")
            assertEquals(expectedDispatched, result.stats.opsDispatched, "$name visual owners")
            assertEquals(0, result.stats.opsRefused, "$name refusals")
            if (visible) {
                assertTrue(result.stats.drawCallCount > 0, "$name native draw calls: ${result.stats}")
                assertTrue(result.stats.pipelineCount > 0, "$name native pipelines: ${result.stats}")
            }
            assertTrue(
                result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "$name scopes=${result.nativeEvidenceScopeKinds}",
            )
            listOf(
                "frameCoordinatorCreations",
                "encoders",
                "commandBuffers",
                "submits",
                "readbackCopies",
            ).forEach { key ->
                assertEquals(1L, result.nativeEvidenceCounters[key], "$name $key delta")
            }
            val queueSubmitted = result.structuralSteps.indexOf("QueueSubmitted")
            val completionSucceeded = result.structuralSteps.indexOf("CompletionSucceeded")
            assertTrue(
                queueSubmitted >= 0 && completionSucceeded > queueSubmitted,
                "$name requires QueueSubmitted before CompletionSucceeded: ${result.structuralSteps}",
            )
            val draws = requireNotNull(result.nativeEvidenceCounters["draws"]) { "$name draws counter" }
            val drawIndexed = requireNotNull(result.nativeEvidenceCounters["drawIndexed"]) { "$name drawIndexed counter" }
            val pipelineBinds = requireNotNull(result.nativeEvidenceCounters["pipelineBinds"]) { "$name pipelineBinds counter" }
            assertEquals(result.stats.drawCallCount.toLong(), Math.addExact(draws, drawIndexed), "$name draw delta")
            assertEquals(result.stats.pipelineCount.toLong(), pipelineBinds, "$name pipeline delta")

            val previous = firstResult
            if (previous == null) firstResult = result
            else assertContentEquals(previous.pixels, result.pixels, "$name complete same-Surface RGBA replay")
        }
    }

    private fun imagePaint() = Paint(
        color = ColorARGB.White,
        shader = Shader.Image(BLUE_IMAGE, sampling = SamplingOptions.NEAREST),
        blendMode = BlendMode.SRC,
        antiAlias = false,
    )

    private fun visibleTriangle() = triangle(0f, 0f)

    private fun shiftedTriangle(dx: Float, dy: Float) = triangle(dx, dy)

    private fun triangle(dx: Float, dy: Float) = Vertices(
        mode = VertexMode.TRIANGLES,
        positions = listOf(
            Point2F32(-1f + dx, -1f + dy),
            Point2F32(5f + dx, -1f + dy),
            Point2F32(-1f + dx, 5f + dy),
        ),
        texCoords = listOf(
            Point2F32(-1f + dx, -1f + dy),
            Point2F32(5f + dx, -1f + dy),
            Point2F32(-1f + dx, 5f + dy),
        ),
        indices = listOf(0, 1, 2),
    )
}
