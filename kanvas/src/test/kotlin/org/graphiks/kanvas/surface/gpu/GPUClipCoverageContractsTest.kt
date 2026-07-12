package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class GPUClipCoverageContractsTest {
    @AfterEach
    fun clearClipIntermediateByteLimit() {
        System.clearProperty(MAX_CLIP_INTERMEDIATE_BYTES_PROPERTY)
    }

    @Test
    fun `mask plan is keyed by value and budgeted before allocation`() {
        val request = complexRequest(antiAlias = true)
        val config = RenderConfig(maxClipIntermediateBytes = 67_108_864u)

        val plan = GPUClipCoveragePlanner.plan(request, config, maxTextureDimension2D = 4096)

        val mask = assertIs<GPUClipCoveragePlan.Mask>(plan)
        assertEquals(request.contentKey, mask.contentKey)
        assertEquals(4, mask.sampleCount)
        assertEquals(4_194_304L, mask.resolvedBytes)
        assertEquals(20_971_520L, mask.requiredBytes)
        assertTrue(mask.requiredBytes > mask.resolvedBytes)
    }

    @Test
    fun `content key is deterministic for equivalent value requests`() {
        val first = complexRequest(antiAlias = true)
        val equivalent = complexRequest(antiAlias = true)
        val different = complexRequest(antiAlias = false)

        assertEquals(first.contentKey, equivalent.contentKey)
        assertNotEquals(first.contentKey, different.contentKey)
    }

    @Test
    fun `no elements plan as no clip and a hard rect plans as scissor`() {
        val noClip = GPUClipCoveragePlanner.plan(
            request = request(elements = emptyList()),
            config = RenderConfig(),
            maxTextureDimension2D = 4096,
        )
        val scissor = GPUClipCoveragePlanner.plan(
            request = request(
                antiAlias = false,
                elements = listOf(
                    element(
                        kind = GPUClipCoverageElementKind.Rect,
                        values = listOf(2f, 3f, 10f, 11f),
                        vertexCount = 4,
                    ),
                ),
            ),
            config = RenderConfig(),
            maxTextureDimension2D = 4096,
        )

        assertEquals(GPUClipCoveragePlan.NoClip, noClip)
        assertIs<GPUClipCoveragePlan.Scissor>(scissor)
    }

    @Test
    fun `cache reuses the first immutable mask for a content key`() {
        val plan = GPUClipCoveragePlanner.plan(
            request = complexRequest(antiAlias = true),
            config = RenderConfig(),
            maxTextureDimension2D = 4096,
        )
        val mask = assertIs<GPUClipCoveragePlan.Mask>(plan)
        val cache = GPUClipCoverageCache()

        assertSame(mask, cache.acquire(mask))
        assertSame(mask, cache.acquire(mask.copy(elements = mask.elements.toList())))
    }

    @Test
    fun `diagnostic facts are sorted and retained`() {
        val diagnostics = Diagnostics()
        diagnostics.fatal(
            code = "refuse:clip",
            operation = "drawPath",
            reason = "unsupported.clip.intermediate_budget",
            facts = listOf(DiagnosticFact("z", "2"), DiagnosticFact("a", "1")),
        )

        assertEquals(listOf("a", "z"), diagnostics.entries.single().facts.map(DiagnosticFact::key))
    }

    @Test
    fun `diagnostics reject duplicate fact keys`() {
        val diagnostics = Diagnostics()

        assertFailsWith<IllegalArgumentException> {
            diagnostics.warn(
                code = "warn:clip",
                operation = "drawPath",
                reason = "clip.warning",
                facts = listOf(DiagnosticFact("shape", "path"), DiagnosticFact("shape", "rect")),
            )
        }
    }

    @Test
    fun `clip planner refuses every exceeded limit with its stable code`() {
        assertEquals("unsupported.clip.nonfinite_input", refusalFor(nonFiniteRequest()))
        assertEquals("unsupported.clip.texture_limit", refusalFor(requestLargerThanDevice()))
        assertEquals("unsupported.clip.intermediate_budget", refusalFor(requestOverByteBudget()))
        assertEquals("unsupported.clip.vertex_budget", refusalFor(requestOverVertexBudget()))
    }

    @Test
    fun `clip intermediate byte limit defaults and accepts a valid environment override`() {
        assertEquals(67_108_864u, RenderConfig.fromEnvironment().maxClipIntermediateBytes)

        System.setProperty(MAX_CLIP_INTERMEDIATE_BYTES_PROPERTY, "1024")

        assertEquals(1024u, RenderConfig.fromEnvironment().maxClipIntermediateBytes)
    }

    private fun refusalFor(request: GPUClipCoverageRequest): String {
        val plan = GPUClipCoveragePlanner.plan(
            request = request,
            config = RenderConfig(maxClipIntermediateBytes = 1024u, maxPathVertices = 8u),
            maxTextureDimension2D = 64,
        )
        return assertIs<GPUClipCoveragePlan.Refused>(plan).code
    }

    private fun complexRequest(antiAlias: Boolean): GPUClipCoverageRequest = request(
        antiAlias = antiAlias,
        elements = listOf(
            element(
                kind = GPUClipCoverageElementKind.Path,
                values = listOf(0f, 0f, 1024f, 0f, 1024f, 1024f, 0f, 1024f),
                vertexCount = 4,
            ),
            element(
                operation = GPUClipCoverageOperation.Difference,
                kind = GPUClipCoverageElementKind.RRect,
                values = listOf(32f, 32f, 992f, 992f, 24f, 24f),
                vertexCount = 8,
            ),
        ),
    )

    private fun nonFiniteRequest(): GPUClipCoverageRequest = request(
        elements = listOf(
            element(
                kind = GPUClipCoverageElementKind.Path,
                values = listOf(0f, Float.NaN, 1f, 1f),
                vertexCount = 4,
            ),
        ),
    )

    private fun requestLargerThanDevice(): GPUClipCoverageRequest = request(
        width = 65,
        height = 1,
        elements = listOf(element()),
    )

    private fun requestOverByteBudget(): GPUClipCoverageRequest = request(
        width = 64,
        height = 64,
        elements = listOf(element()),
    )

    private fun requestOverVertexBudget(): GPUClipCoverageRequest = request(
        width = 1,
        height = 1,
        elements = listOf(element(vertexCount = 9)),
    )

    private fun request(
        width: Int = 1024,
        height: Int = 1024,
        antiAlias: Boolean = true,
        elements: List<GPUClipCoverageElement>,
    ): GPUClipCoverageRequest = GPUClipCoverageRequest(
        targetWidth = width,
        targetHeight = height,
        elements = elements,
        fillRule = GPUClipFillRule.Winding,
        inverseFill = false,
        antiAlias = antiAlias,
    )

    private fun element(
        operation: GPUClipCoverageOperation = GPUClipCoverageOperation.Intersect,
        kind: GPUClipCoverageElementKind = GPUClipCoverageElementKind.Path,
        values: List<Float> = listOf(0f, 0f, 1f, 1f),
        vertexCount: Int = 4,
    ): GPUClipCoverageElement = GPUClipCoverageElement(
        operation = operation,
        kind = kind,
        values = values,
        vertexCount = vertexCount,
    )

    private companion object {
        const val MAX_CLIP_INTERMEDIATE_BYTES_PROPERTY = "kanvas.render.maxClipIntermediateBytes"
    }
}
