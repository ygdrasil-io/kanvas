package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.TestBuffer
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.text.TextBlob
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
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
        assertEquals(37_748_736L, mask.requiredBytes)
        assertTrue(mask.requiredBytes > mask.resolvedBytes)
    }

    @Test
    fun `AA mask accounts for color resolve and depth stencil allocations before allocation`() {
        val request = request(width = 16, height = 16, elements = listOf(element(antiAlias = true)))
        val pixels = 16L * 16L
        val requiredBytes = pixels * (4L * 4L + 4L + 4L * 4L)

        val plan = GPUClipCoveragePlanner.plan(
            request = request,
            config = RenderConfig(maxClipIntermediateBytes = requiredBytes.toUInt()),
            maxTextureDimension2D = 4096,
        )

        assertEquals(requiredBytes, assertIs<GPUClipCoveragePlan.Mask>(plan).requiredBytes)
        val refusal = GPUClipCoveragePlanner.plan(
            request = request,
            config = RenderConfig(maxClipIntermediateBytes = (requiredBytes - 1L).toUInt()),
            maxTextureDimension2D = 4096,
        )
        assertEquals(
            "unsupported.clip.intermediate_budget",
            assertIs<GPUClipCoveragePlan.Refused>(refusal).code,
        )
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
                scissorEligible = true,
                elements = listOf(
                    element(
                        kind = GPUClipCoverageElementKind.Rect,
                        values = listOf(2f, 3f, 10f, 11f),
                        vertexCount = 0,
                        antiAlias = false,
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
    fun `clip planner refuses intermediate byte arithmetic overflow`() {
        val plan = GPUClipCoveragePlanner.plan(
            request = request(
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                elements = listOf(element()),
            ),
            config = RenderConfig(maxClipIntermediateBytes = UInt.MAX_VALUE),
            maxTextureDimension2D = Int.MAX_VALUE,
        )

        assertEquals(
            "unsupported.clip.intermediate_budget",
            assertIs<GPUClipCoveragePlan.Refused>(plan).code,
        )
    }

    @Test
    fun `clip intermediate byte limit defaults and accepts a valid environment override`() {
        assertEquals(67_108_864u, RenderConfig.fromEnvironment().maxClipIntermediateBytes)

        System.setProperty(MAX_CLIP_INTERMEDIATE_BYTES_PROPERTY, "1024")

        assertEquals(1024u, RenderConfig.fromEnvironment().maxClipIntermediateBytes)
    }

    @Test
    fun `complex clip mapper preserves order difference and inverse fill`() {
        val path = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(Rect.fromLTRB(8f, 8f, 32f, 32f))
        }
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect.fromLTRB(2f, 3f, 60f, 61f), ClipOp.INTERSECT, antiAlias = false),
                ClipStackOp.PathOp(path, ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )

        val facts = clip.toGPUClipFacts(target())
        val request = requireNotNull(facts.coverageRequest)

        assertEquals(
            listOf(GPUClipCoverageOperation.Intersect, GPUClipCoverageOperation.Difference),
            request.elements.map(GPUClipCoverageElement::operation),
        )
        val pathElement = request.elements.last()
        assertEquals(GPUClipCoverageElementKind.Path, pathElement.kind)
        assertEquals(GPUClipFillRule.EvenOdd, pathElement.fillRule)
        assertTrue(pathElement.inverseFill)
        assertTrue(pathElement.antiAlias)
        assertEquals(1f, pathElement.values.first())
        assertEquals(0f, pathElement.values[1])
    }

    @Test
    fun `path clip mapper retains even odd holes and inverse difference coverage semantics`() {
        val evenOddHole = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(Rect.fromLTRB(3.5f, 3.5f, 28.5f, 28.5f))
            addRect(Rect.fromLTRB(11.5f, 11.5f, 20.5f, 20.5f))
        }
        val inverseRect = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(Rect.fromLTRB(8.5f, 8.5f, 23.5f, 23.5f))
        }
        val request = requireNotNull(
            ClipStack.Complex(
                listOf(
                    ClipStackOp.PathOp(evenOddHole, ClipOp.INTERSECT, antiAlias = true),
                    ClipStackOp.PathOp(inverseRect, ClipOp.DIFFERENCE, antiAlias = true),
                ),
            ).toGPUClipFacts(target()).coverageRequest,
        )

        assertEquals(
            listOf(GPUClipCoverageOperation.Intersect, GPUClipCoverageOperation.Difference),
            request.elements.map(GPUClipCoverageElement::operation),
        )
        assertEquals(GPUClipFillRule.EvenOdd, request.elements[0].fillRule)
        assertTrue(!request.elements[0].inverseFill)
        assertTrue(request.elements[0].antiAlias)
        assertEquals(GPUClipFillRule.EvenOdd, request.elements[1].fillRule)
        assertTrue(request.elements[1].inverseFill)
        assertTrue(request.elements[1].antiAlias)
    }

    @Test
    fun `clip mapper keeps coverage requests only for non wide open clips`() {
        val hardRect = ClipStack.DeviceRect(Rect.fromLTRB(2f, 3f, 10f, 11f), antiAlias = false)
        val fractionalRect = ClipStack.DeviceRect(Rect.fromLTRB(2.5f, 3f, 10f, 11f), antiAlias = false)
        val aaRect = ClipStack.DeviceRect(Rect.fromLTRB(2f, 3f, 10f, 11f), antiAlias = true)
        val complex = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect.fromLTRB(2f, 3f, 10f, 11f), ClipOp.INTERSECT, antiAlias = false)),
        )
        val config = RenderConfig()

        assertNull(ClipStack.WideOpen.toGPUClipFacts(target()).coverageRequest)
        assertIs<GPUClipCoveragePlan.Scissor>(
            GPUClipCoveragePlanner.plan(requireNotNull(hardRect.toGPUClipFacts(target()).coverageRequest), config, 4096),
        )
        assertIs<GPUClipCoveragePlan.Mask>(
            GPUClipCoveragePlanner.plan(requireNotNull(fractionalRect.toGPUClipFacts(target()).coverageRequest), config, 4096),
        )
        assertIs<GPUClipCoveragePlan.Mask>(
            GPUClipCoveragePlanner.plan(requireNotNull(aaRect.toGPUClipFacts(target()).coverageRequest), config, 4096),
        )
        assertIs<GPUClipCoveragePlan.Mask>(
            GPUClipCoveragePlanner.plan(requireNotNull(complex.toGPUClipFacts(target()).coverageRequest), config, 4096),
        )
    }

    @Test
    fun `perspective is refused before clip coverage planning`() {
        val command = DisplayOp.DrawRect(
            rect = Rect.fromLTRB(2f, 3f, 10f, 11f),
            paint = Paint.fill(Color.RED),
            transform = Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f),
            clip = ClipStack.DeviceRect(Rect.fromLTRB(2f, 3f, 10f, 11f), antiAlias = false),
        ).toNormalizedCommand(cmdId = org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(0), target = target())

        assertEquals(GPUTransformType.Perspective, command.transform.type)
        assertEquals("unsupported_transform:Perspective", command.fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `perspective clip remains refused after reset matrix`() {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        canvas.setMatrix(Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f))
        canvas.clipRect(Rect.fromLTRB(2f, 3f, 10f, 11f), antiAlias = false)
        canvas.resetMatrix()
        canvas.clipRect(Rect.fromLTRB(3f, 4f, 9f, 10f), antiAlias = false)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 16f, 16f), Paint.fill(Color.RED))

        val draw = buffer.ops().filterIsInstance<DisplayOp.DrawRect>().single()
        val command = draw.toNormalizedCommand(
            cmdId = org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(0),
            target = target(),
        )

        assertEquals(GPUTransformType.Identity, command.transform.type)
        assertTrue(command.clip.perspectiveCaptureRefusal)
        assertEquals("unsupported_transform:Perspective", command.fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `common perspective guard refuses rect image and text after reset matrix`() {
        val clip = perspectiveClipAfterReset()
        val transform = Matrix33.identity()
        val operations = listOf<DisplayOp>(
            DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 16f, 16f), Paint.fill(Color.RED), transform, clip),
            DisplayOp.DrawImage(
                image = Image.placeholder(1, 1),
                src = Rect.fromLTRB(0f, 0f, 1f, 1f),
                dst = Rect.fromLTRB(0f, 0f, 1f, 1f),
                paint = null,
                transform = transform,
                clip = clip,
            ),
            DisplayOp.DrawText(TextBlob(emptyList()), 0f, 0f, Paint.fill(Color.RED), transform, clip),
        )

        operations.forEach { operation ->
            assertEquals("unsupported_transform:Perspective", operation.perspectiveCaptureRefusalReasonOrNull())
        }
    }

    @Test
    fun `clip path defers over budget vertices to planner`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            repeat(300) { index -> lineTo(index + 1f, if (index % 2 == 0) 1f else -1f) }
            close()
        }
        val request = requireNotNull(
            ClipStack.Complex(listOf(ClipStackOp.PathOp(path, ClipOp.INTERSECT, antiAlias = false)))
                .toGPUClipFacts(target())
                .coverageRequest,
        )

        assertTrue(request.elements.single().vertexCount > 256)
        assertEquals(
            "unsupported.clip.vertex_budget",
            assertIs<GPUClipCoveragePlan.Refused>(
                GPUClipCoveragePlanner.plan(request, RenderConfig(maxPathVertices = 256u), maxTextureDimension2D = 4096),
            ).code,
        )
    }

    @Test
    fun `path payload requires contours to match vertices and start at zero`() {
        assertFailsWith<IllegalArgumentException> {
            element(vertexCount = 1, values = listOf(0f, 0f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            element(vertexCount = 2, values = listOf(1f, 1f, 0f, 0f, 1f, 1f))
        }
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
        elements = listOf(
            element(
                kind = GPUClipCoverageElementKind.Path,
                values = listOf(1f, 0f, 0f, 0f, 1024f, 0f, 1024f, 1024f, 0f, 1024f),
                vertexCount = 4,
                antiAlias = antiAlias,
            ),
            element(
                operation = GPUClipCoverageOperation.Difference,
                kind = GPUClipCoverageElementKind.RRect,
                values = listOf(32f, 32f, 992f, 992f, 24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f),
                vertexCount = 0,
                antiAlias = antiAlias,
            ),
        ),
    )

    private fun nonFiniteRequest(): GPUClipCoverageRequest = request(
        elements = listOf(
            element(
                kind = GPUClipCoverageElementKind.Path,
                values = listOf(1f, 0f, 0f, Float.NaN, 1f, 1f),
                vertexCount = 2,
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
        elements: List<GPUClipCoverageElement>,
        scissorEligible: Boolean = false,
    ): GPUClipCoverageRequest = GPUClipCoverageRequest(
        targetWidth = width,
        targetHeight = height,
        elements = elements,
        scissorEligible = scissorEligible,
    )

    private fun element(
        operation: GPUClipCoverageOperation = GPUClipCoverageOperation.Intersect,
        kind: GPUClipCoverageElementKind = GPUClipCoverageElementKind.Path,
        vertexCount: Int = 2,
        values: List<Float> = pathValues(vertexCount),
        antiAlias: Boolean = true,
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        inverseFill: Boolean = false,
    ): GPUClipCoverageElement = GPUClipCoverageElement(
        operation = operation,
        kind = kind,
        values = values,
        vertexCount = vertexCount,
        antiAlias = antiAlias,
        fillRule = fillRule,
        inverseFill = inverseFill,
    )

    private fun pathValues(vertexCount: Int): List<Float> =
        listOf(1f, 0f) + List(vertexCount * 2) { it.toFloat() }

    private fun target(): GPUTargetFacts = GPUTargetFacts(64, 64, "rgba8unorm")

    private fun perspectiveClipAfterReset(): ClipStack {
        val buffer = TestBuffer()
        val canvas = Canvas(buffer)
        canvas.setMatrix(Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f))
        canvas.clipRect(Rect.fromLTRB(2f, 3f, 10f, 11f), antiAlias = false)
        canvas.resetMatrix()
        return buffer.ops().filterIsInstance<DisplayOp.SetClip>().last().clip
    }

    private companion object {
        const val MAX_CLIP_INTERMEDIATE_BYTES_PROPERTY = "kanvas.render.maxClipIntermediateBytes"
    }
}
