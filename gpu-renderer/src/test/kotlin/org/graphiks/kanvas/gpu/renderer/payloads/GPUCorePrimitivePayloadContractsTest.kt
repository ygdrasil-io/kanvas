package org.graphiks.kanvas.gpu.renderer.payloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.recording.stableCoreDump
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitivePayloadContractsTest {
    @Test
    fun `semantic retains exact blend and provenance authorities`() {
        val semantic = gather(
            blendPlan = blend(GPUBlendMode.SRC_OVER),
            provenance = GPUFrameProvenance.GmContent,
        )

        assertEquals(blend(GPUBlendMode.SRC_OVER).canonicalIdentity(), semantic.blendPlanIdentity)
        assertEquals(GPUFrameProvenance.GmContent, semantic.frameProvenance)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `uniform fingerprint includes target size`() {
        val small = gather(target = GPUPixelBounds(0, 0, 16, 16))
        val large = gather(target = GPUPixelBounds(0, 0, 32, 32))

        assertNotEquals(
            small.payloadRef.uniformBlock?.fingerprint,
            large.payloadRef.uniformBlock?.fingerprint,
        )
    }

    @Test
    fun `canonical hash includes exact mask budgets samples and vertex count`() {
        val baseElement = GPUClipCoverageElement(
            operation = GPUClipCoverageOperation.Intersect,
            kind = GPUClipCoverageElementKind.Path,
            values = listOf(1f, 0f, 1f, 1f, 8f, 1f, 8f, 8f),
            vertexCount = 3,
            antiAlias = true,
            fillRule = GPUClipFillRule.Winding,
            inverseFill = false,
        )
        val base = gather(
            clip = GPUClipCoveragePlan.Mask("same-key", 16, 16, 1, 256, 256, listOf(baseElement)),
        )
        val changed = gather(
            clip = GPUClipCoveragePlan.Mask("same-key", 16, 16, 4, 256, 1024, listOf(baseElement)),
        )

        assertNotEquals(base.canonicalHash, changed.canonicalHash)
        val dump = changed.clipCoveragePlan.stableCoreDump()
        assertTrue("samples=4" in dump)
        assertTrue("resolvedBytes=256" in dump)
        assertTrue("requiredBytes=1024" in dump)
        assertTrue("vertices=3" in dump)
    }

    @Test
    fun `canonical integrity rejects substituted blend or provenance`() {
        val semantic = gather()
        val substituted = GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = semantic.payloadRef,
            sourceFamily = semantic.sourceFamily,
            geometry = semantic.geometry,
            premultipliedRgba = semantic.premultipliedRgba,
            targetBounds = semantic.targetBounds,
            scissorBounds = semantic.scissorBounds,
            clipCoveragePlan = semantic.clipCoveragePlan,
            blendPlanIdentity = blend(GPUBlendMode.SRC).canonicalIdentity(),
            frameProvenance = GPUFrameProvenance.HarnessBackground,
            canonicalHash = semantic.canonicalHash,
        )

        assertFalse(substituted.hasCanonicalHashIntegrity())
    }

    @Test
    fun `semantic retains exact path fill coverage and stroke facts`() {
        val stroke = GPUCorePrimitiveStrokeStyle(
            width = 4f,
            cap = "square",
            join = "bevel",
            miterLimit = 3f,
            dashIntervals = emptyList(),
            dashPhase = 0f,
            loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1,
        )
        val semantic = gather(
            geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
                indices = listOf(0, 1, 2),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 2,
                coverBounds = GPUPixelBounds(0, 0, 8, 8),
                geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
                strokeStyle = stroke,
            ),
            coverageMode = GPUCorePrimitiveCoverageMode.StencilAA,
        )
        val geometry = semantic.geometry as GPUCorePrimitiveGeometry.TriangulatedPath

        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveFillRule.Winding, geometry.fillRule)
        assertFalse(geometry.inverseFill)
        assertEquals(stroke, geometry.strokeStyle)
        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, semantic.coverageMode)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `stroke lowering proofs reject cap dash segment fill and inverse contradictions`() {
        val validSquare = GPUCorePrimitiveStrokeStyle(
            width = 4f,
            cap = "square",
            join = "bevel",
            miterLimit = 3f,
            dashIntervals = emptyList(),
            dashPhase = 0f,
            loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1,
        )
        val contradictions = listOf(
            validSquare.copy(cap = "butt"),
            validSquare.copy(cap = "round"),
            validSquare.copy(dashIntervals = listOf(2f, 1f)),
            validSquare.copy(
                cap = "square",
                loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1,
            ),
        )
        contradictions.forEach { stroke ->
            assertFailsWith<IllegalArgumentException> {
                gather(geometry = strokeFan(strokeStyle = stroke))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = strokeFan(strokeStyle = validSquare, sourceVertexCount = 3))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = strokeFan(
                    strokeStyle = validSquare,
                    fillRule = GPUCorePrimitiveFillRule.EvenOdd,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = strokeFan(strokeStyle = validSquare, inverseFill = true))
        }
    }

    @Test
    fun `rect and rrect may cross target bounds when their scissor remains bounded`() {
        val target = GPUPixelBounds(0, 0, 16, 16)

        val rect = gather(
            target = target,
            scissor = GPUPixelBounds(0, 0, 8, 8),
            geometry = GPUCorePrimitiveGeometryInput.Rect(-4f, -3f, 8f, 9f),
        )
        val rrect = gather(
            target = target,
            scissor = GPUPixelBounds(0, 0, 8, 8),
            geometry = GPUCorePrimitiveGeometryInput.RRect(
                -4f,
                -3f,
                8f,
                9f,
                List(8) { 2f },
            ),
        )

        assertTrue(rect.hasCanonicalHashIntegrity())
        assertTrue(rrect.hasCanonicalHashIntegrity())
    }

    @Test
    fun `stencil edge fan accepts only the canonical source topology`() {
        val anchor = listOf(-1f, -1f)
        val p0 = listOf(1f, 1f)
        val p1 = listOf(7f, 1f)
        val p2 = listOf(4f, 7f)
        val vertices = anchor + p0 + p1 + anchor + p1 + p2 + anchor + p2 + p0
        val valid = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = vertices,
            indices = (0..8).toList(),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(0, 0, 8, 8),
            geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        )

        assertTrue(gather(geometry = valid).hasCanonicalHashIntegrity())
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(indices = listOf(0, 1, 2, 3, 5, 4, 6, 7, 8)))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(sourceContourStarts = listOf(0, 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(vertices = vertices.dropLast(6), indices = listOf(0, 1, 2, 3, 4, 5)))
        }
    }

    @Test
    fun `stencil edge fan rejects source metadata over its stable budget`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = listOf(0f, 0f, 1f, 0f, 0f, 1f),
                    indices = listOf(0, 1, 2),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = 257,
                    coverBounds = GPUPixelBounds(0, 0, 8, 8),
                    geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                ),
            )
        }

        assertEquals("unsupported.core_primitive.stencil_edge_fan_budget", failure.message)
    }

    private fun gather(
        target: GPUPixelBounds = GPUPixelBounds(0, 0, 16, 16),
        scissor: GPUPixelBounds = target,
        geometry: GPUCorePrimitiveGeometryInput = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
        clip: GPUClipCoveragePlan = GPUClipCoveragePlan.Scissor(GPUBounds(0f, 0f, 16f, 16f)),
        blendPlan: GPUBlendPlan = blend(GPUBlendMode.SRC_OVER),
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = 7,
            sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
            geometry = geometry,
            premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetBounds = target,
            scissorBounds = scissor,
            clipCoveragePlan = clip,
            blendPlanIdentity = blendPlan.canonicalIdentity(),
            frameProvenance = provenance,
            coverageMode = coverageMode,
        ),
    )

    private fun strokeFan(
        strokeStyle: GPUCorePrimitiveStrokeStyle,
        sourceVertexCount: Int = 2,
        fillRule: GPUCorePrimitiveFillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill: Boolean = false,
    ) = GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = sourceVertexCount,
        coverBounds = GPUPixelBounds(0, 0, 8, 8),
        geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        fillRule = fillRule,
        inverseFill = inverseFill,
        strokeStyle = strokeStyle,
    )

    private fun blend(mode: GPUBlendMode): GPUBlendPlan = GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = "state.${mode.name.lowercase()}",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )
}
