package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveDirectClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitiveNativeRouteTest {
    @Test
    fun `rect route expands to exact device vertices and uint32 indices`() {
        val accepted = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(GPUCorePrimitiveGeometryInput.Rect(2f, 3f, 11f, 13f)),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )

        assertContentEquals(
            floatArrayOf(2f, 3f, 11f, 3f, 11f, 13f, 2f, 13f),
            accepted.vertexSnapshot(),
        )
        assertContentEquals(intArrayOf(0, 2, 1, 0, 3, 2), accepted.indexSnapshot())
        assertEquals(GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry, accepted.lane)
        assertEquals(TARGET, accepted.renderScissor)
    }

    @Test
    fun `scalar rect uses one pixel analytic quad and exact clipped scissor`() {
        val clip = GPUPixelBounds(2, 4, 12, 12)
        val accepted = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(
                    GPUCorePrimitiveGeometryInput.Rect(2.25f, 3.5f, 11.75f, 13.25f),
                    coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
                    scissor = clip,
                ),
                GPUClipExecutionPlan.ScissorOnly(clip),
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )

        assertEquals(GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape, accepted.lane)
        assertContentEquals(
            floatArrayOf(1.25f, 2.5f, 12.75f, 2.5f, 12.75f, 14.25f, 1.25f, 14.25f),
            accepted.vertexSnapshot(),
        )
        assertEquals(clip, accepted.renderScissor)
    }

    @Test
    fun `hard rrect uses exact bounds while scalar rrect uses one pixel outset`() {
        val geometry = GPUCorePrimitiveGeometryInput.RRect(
            2.25f,
            3.5f,
            11.75f,
            13.25f,
            listOf(1f, 2f, 2f, 1f, 3f, 2f, 1f, 1f),
        )
        val hard = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(geometry, GPUCorePrimitiveSourceFamily.RRect),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )
        val scalar = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(
                    geometry,
                    GPUCorePrimitiveSourceFamily.RRect,
                    coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
                ),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )

        assertEquals(GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape, hard.lane)
        assertEquals(GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape, scalar.lane)
        assertContentEquals(
            floatArrayOf(2.25f, 3.5f, 11.75f, 3.5f, 11.75f, 13.25f, 2.25f, 13.25f),
            hard.vertexSnapshot(),
        )
        assertContentEquals(
            floatArrayOf(1.25f, 2.5f, 12.75f, 2.5f, 12.75f, 14.25f, 1.25f, 14.25f),
            scalar.vertexSnapshot(),
        )
        assertEquals(GPUPixelBounds(2, 3, 12, 14), hard.renderScissor)
        assertEquals(GPUPixelBounds(1, 2, 13, 15), scalar.renderScissor)
    }

    @Test
    fun `analytic shape refuses an empty geometry clip intersection`() {
        val clip = GPUPixelBounds(14, 14, 16, 16)
        val result = validateCorePrimitiveDirectNativeRoute(
            semantic(
                GPUCorePrimitiveGeometryInput.Rect(2f, 2f, 8f, 8f),
                coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
                scissor = clip,
            ),
            GPUClipExecutionPlan.ScissorOnly(clip),
            srcOver(),
            GPUSamplePlan.SingleSampleFrame,
            "rgba8unorm",
        )

        assertEquals(
            "unsupported.native-core-primitive.analytic-shape.empty-scissor",
            assertIs<GPUCorePrimitiveDirectNativeRoute.Refused>(result).code,
        )
    }

    @Test
    fun `rect AA and both rrect coverages share the sole analytic structural lane`() {
        val rectAa = semantic(
            GPUCorePrimitiveGeometryInput.Rect(2f, 3f, 11f, 13f),
            coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
        )
        val rrectGeometry = GPUCorePrimitiveGeometryInput.RRect(2f, 3f, 11f, 13f, List(8) { 1f })
        val rrectHard = semantic(rrectGeometry, GPUCorePrimitiveSourceFamily.RRect)
        val rrectAa = semantic(
            rrectGeometry,
            GPUCorePrimitiveSourceFamily.RRect,
            coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
        )
        val keys = listOf(rectAa, rrectHard, rrectAa).map { semantic ->
            corePrimitiveRenderPipelineStructuralKey(semantic, GPUClipExecutionPlan.NoClip, srcOver())
        }

        keys.forEach { key ->
            assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape, key.shader)
            assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList, key.topology)
            assertEquals(
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                key.uniformLayout,
            )
        }
        assertEquals(1, keys.distinct().size)
    }

    @Test
    fun `direct triangles preserve exact device vertices and indices`() {
        val vertices = listOf(1f, 2f, 15f, 4f, 7f, 14f)
        val indices = listOf(2, 0, 1)
        val accepted = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(
                    GPUCorePrimitiveGeometryInput.TriangulatedPath(
                        vertices = vertices,
                        indices = indices,
                        sourceContourStarts = listOf(0),
                        sourceVertexCount = 3,
                        coverBounds = TARGET,
                        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                        fillRule = GPUCorePrimitiveFillRule.Winding,
                    ),
                    GPUCorePrimitiveSourceFamily.Path,
                ),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )

        assertContentEquals(vertices.toFloatArray(), accepted.vertexSnapshot())
        assertContentEquals(indices.toIntArray(), accepted.indexSnapshot())
    }

    @Test
    fun `clip plan and semantic scissor must be identical`() {
        val partial = GPUPixelBounds(2, 3, 12, 14)
        val noClipMismatch = validateCorePrimitiveDirectNativeRoute(
            semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f), scissor = partial),
            GPUClipExecutionPlan.NoClip,
            srcOver(),
            GPUSamplePlan.SingleSampleFrame,
            "rgba8unorm",
        )
        val scissorMismatch = validateCorePrimitiveDirectNativeRoute(
            semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f), scissor = partial),
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(1, 1, 10, 10)),
            srcOver(),
            GPUSamplePlan.SingleSampleFrame,
            "rgba8unorm",
        )

        assertEquals(
            "invalid.native-core-primitive.scissor-authority",
            assertIs<GPUCorePrimitiveDirectNativeRoute.Refused>(noClipMismatch).code,
        )
        assertEquals(
            "invalid.native-core-primitive.scissor-authority",
            assertIs<GPUCorePrimitiveDirectNativeRoute.Refused>(scissorMismatch).code,
        )
    }

    @Test
    fun `unsupported geometry coverage clip blend sample and format refuse before native work`() {
        val cases = listOf(
            validateCorePrimitiveDirectNativeRoute(
                semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f), blend = src()),
                GPUClipExecutionPlan.NoClip,
                src(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ) to "unsupported.native-core-primitive.blend",
            validateCorePrimitiveDirectNativeRoute(
                semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f)),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.MultisampleFrame(4),
                "rgba8unorm",
            ) to "unsupported.native-core-primitive.sample-plan",
            validateCorePrimitiveDirectNativeRoute(
                semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f)),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba16float",
            ) to "unsupported.native-core-primitive.target-format",
            validateCorePrimitiveDirectNativeRoute(
                semantic(
                    GPUCorePrimitiveGeometryInput.TriangulatedPath(
                        vertices = listOf(1f, 1f, 8f, 1f, 4f, 8f),
                        indices = listOf(0, 1, 2),
                        sourceContourStarts = listOf(0),
                        sourceVertexCount = 3,
                        coverBounds = TARGET,
                        inverseFill = true,
                    ),
                    GPUCorePrimitiveSourceFamily.Path,
                ),
                GPUClipExecutionPlan.NoClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ) to "unsupported.native-core-primitive.inverse-fill",
        )

        cases.forEach { (result, code) ->
            assertEquals(code, assertIs<GPUCorePrimitiveDirectNativeRoute.Refused>(result).code)
        }
    }

    @Test
    fun `analytic rect clip retains direct geometry with its conservative scissor authority`() {
        val analyticClip = GPUClipExecutionPlan.AnalyticCoverage(
            GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 16f, 16f)),
            TARGET,
            antiAlias = false,
        )

        assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
            validateCorePrimitiveDirectNativeRoute(
                semantic(GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f)),
                analyticClip,
                srcOver(),
                GPUSamplePlan.SingleSampleFrame,
                "rgba8unorm",
            ),
        )
    }

    private fun semantic(
        geometry: GPUCorePrimitiveGeometryInput,
        sourceFamily: GPUCorePrimitiveSourceFamily = GPUCorePrimitiveSourceFamily.Rect,
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        blend: GPUBlendPlan = srcOver(),
        scissor: GPUPixelBounds = TARGET,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = 7,
            sourceFamily = sourceFamily,
            geometry = geometry,
            premultipliedRgba = listOf(0.2f, 0.1f, 0.05f, 0.5f),
            targetBounds = TARGET,
            scissorBounds = scissor,
            clipCoveragePlan = org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip,
            clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
            blendPlanIdentity = blend.canonicalIdentity(),
            frameProvenance = GPUFrameProvenance.GmContent,
            coverageMode = coverageMode,
            analysisRecordId = when (sourceFamily) {
                GPUCorePrimitiveSourceFamily.Rect -> "analysis.fill_rect.7"
                GPUCorePrimitiveSourceFamily.RRect -> "analysis.fill_rrect.7"
                else -> null
            },
            analysisCommandFamily = when (sourceFamily) {
                GPUCorePrimitiveSourceFamily.Rect -> "FillRect"
                GPUCorePrimitiveSourceFamily.RRect -> "FillRRect"
                else -> null
            },
            rectRouteAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned
            } else {
                null
            },
            rectGeometryAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                rectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.Rect)
            } else {
                null
            },
            rrectGeometryAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.RRect) {
                rrectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.RRect)
            } else {
                null
            },
        ),
    )

    private fun GPUCorePrimitiveDirectNativeRoute.Accepted.vertexSnapshot(): FloatArray =
        FloatArray(vertexCount * 2).also { copyVerticesInto(it) }

    private fun GPUCorePrimitiveDirectNativeRoute.Accepted.indexSnapshot(): IntArray =
        IntArray(indexCount).also { copyIndicesInto(it) }

    private fun rectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.Rect,
    ) = corePrimitiveRectGeometryAuthority(
        GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom),
        GPUTransformFacts.identity(),
    )

    private fun rrectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.RRect,
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority {
        val source = geometry.toSourceRRect()
        val accepted = assertIs<GPURRectNormalizationResult.Accepted>(GPURRectNormalizer.normalize(source))
        return assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
            corePrimitiveRRectGeometryAuthority(source, accepted, GPUTransformFacts.identity()),
        ).authority
    }

    private fun GPUCorePrimitiveGeometryInput.RRect.toSourceRRect(): GPURRect = GPURRect(
        rect = GPURect(left, top, right, bottom),
        topLeft = GPURRectCornerRadii(radii[0], radii[1]),
        topRight = GPURRectCornerRadii(radii[2], radii[3]),
        bottomRight = GPURRectCornerRadii(radii[4], radii[5]),
        bottomLeft = GPURRectCornerRadii(radii[6], radii[7]),
    )

    private fun validateCorePrimitiveDirectNativeRoute(
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        clipExecutionPlan: GPUClipExecutionPlan,
        blendPlan: GPUBlendPlan?,
        samplePlan: GPUSamplePlan,
        targetFormat: String,
    ): GPUCorePrimitiveDirectNativeRoute {
        val clipAuthority = org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority(
            clipExecutionPlan,
            semantic.targetBounds,
        )
        return org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveDirectNativeRoute(
            semantic = semantic,
            exactClipScissor = (clipAuthority as? GPUCorePrimitiveDirectClipAuthority.Accepted)?.scissor,
            canonicalPremultipliedSrcOver = blendPlan.isCanonicalSolidRectSrcOver(),
            samplePlan = samplePlan,
            targetFormat = targetFormat,
        )
    }

    private fun srcOver(): GPUBlendPlan.FixedFunctionBlend = fixed(GPUBlendMode.SRC_OVER)

    private fun src(): GPUBlendPlan.FixedFunctionBlend = fixed(GPUBlendMode.SRC)

    private fun fixed(mode: GPUBlendMode) = GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = if (mode == GPUBlendMode.SRC_OVER) "one_isa" else mode.name.lowercase(),
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private companion object {
        val TARGET = GPUPixelBounds(0, 0, 16, 16)
    }
}
