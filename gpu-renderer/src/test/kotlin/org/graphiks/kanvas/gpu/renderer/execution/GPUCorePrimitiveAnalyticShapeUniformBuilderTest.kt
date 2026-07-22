package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitiveAnalyticShapeUniformBuilderTest {
    @Test
    fun `prepared rect builds exact uniform80 with zero radii and coverage anti alias`() {
        val semantic = rectSemantic(coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA)

        val accepted = assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted>(
            buildCorePrimitiveAnalyticShapeUniform(
                semantic,
                GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
            ),
        )
        val packed = accepted.block.packedBytes()
        val bytes = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES, packed.size)
        assertContentEquals(listOf(32f, 24f), bytes.floatsAt(0, 2))
        assertEquals(1, bytes.getInt(8))
        assertEquals(0, bytes.getInt(12))
        assertContentEquals(COLOR, bytes.floatsAt(16, 4))
        assertContentEquals(listOf(2.5f, 3.25f, 18.75f, 20.5f), bytes.floatsAt(32, 4))
        assertContentEquals(List(8) { 0f }, bytes.floatsAt(48, 8))

        val hardSemantic = rectSemantic(coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor)
        val hardPacked = assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted>(
            buildCorePrimitiveAnalyticShapeUniform(
                hardSemantic,
                GPUCorePrimitivePreparedSemanticAuthority.capture(hardSemantic),
            ),
        ).block.packedBytes()
        assertEquals(0, ByteBuffer.wrap(hardPacked).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
    }

    @Test
    fun `prepared rrect uses only signed normalized radii in TL TR BR BL order`() {
        val sourceRadii = mutableListOf(12f, 4f, 8f, 4f, 4f, 12f, 4f, 12f)
        val color = COLOR.toMutableList()
        val semantic = rrectSemantic(sourceRadii, color)

        sourceRadii.fill(99f)
        color.fill(99f)
        val accepted = assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted>(
            buildCorePrimitiveAnalyticShapeUniform(
                semantic,
                GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
            ),
        )
        val first = accepted.block.packedBytes()
        val bytes = ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES, first.size)
        assertContentEquals(COLOR, bytes.floatsAt(16, 4))
        assertContentEquals(listOf(1f, 2f, 17f, 14f), bytes.floatsAt(32, 4))
        assertContentEquals(listOf(9f, 3f, 6f, 3f, 3f, 9f, 3f, 9f), bytes.floatsAt(48, 8))

        first.fill(0)
        assertContentEquals(
            listOf(9f, 3f, 6f, 3f, 3f, 9f, 3f, 9f),
            ByteBuffer.wrap(accepted.block.packedBytes()).order(ByteOrder.LITTLE_ENDIAN).floatsAt(48, 8),
        )
    }

    @Test
    fun `builder refuses incoherent semantic source and signed geometry authority with stable codes`() {
        val rect = rectSemantic()
        val identicalButDistinctRect = rectSemantic()
        val semanticMismatch = buildCorePrimitiveAnalyticShapeUniform(
            identicalButDistinctRect,
            GPUCorePrimitivePreparedSemanticAuthority.capture(rect),
        )
        assertEquals(
            "invalid.native-core-primitive.analytic-shape.semantic-authority",
            assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused>(semanticMismatch).code,
        )

        val forgedSource = rect.forged(sourceFamily = GPUCorePrimitiveSourceFamily.Path)
        assertEquals(
            "invalid.native-core-primitive.analytic-shape.source",
            assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused>(
                buildCorePrimitiveAnalyticShapeUniform(
                    forgedSource,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(forgedSource),
                ),
            ).code,
        )

        val missingRectAuthority = rect.forged(rectGeometryAuthority = null)
        assertEquals(
            "invalid.native-core-primitive.analytic-shape.geometry-authority",
            assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused>(
                buildCorePrimitiveAnalyticShapeUniform(
                    missingRectAuthority,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(missingRectAuthority),
                ),
            ).code,
        )

        val rrect = rrectSemantic(mutableListOf(2f, 2f, 3f, 3f, 4f, 4f, 5f, 5f))
        val otherAuthority = requireNotNull(
            rrectSemantic(mutableListOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f))
                .rrectGeometryAuthority,
        )
        val mismatchedRRectAuthority = rrect.forged(rrectGeometryAuthority = otherAuthority)
        assertEquals(
            "invalid.native-core-primitive.analytic-shape.geometry-authority",
            assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused>(
                buildCorePrimitiveAnalyticShapeUniform(
                    mismatchedRRectAuthority,
                    GPUCorePrimitivePreparedSemanticAuthority.capture(mismatchedRRectAuthority),
                ),
            ).code,
        )
    }

    @Test
    fun `builder refuses stencil coverage before constructing uniform80`() {
        val semantic = rectSemantic().forged(coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x)

        val refusal = assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused>(
            buildCorePrimitiveAnalyticShapeUniform(
                semantic,
                GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
            ),
        )

        assertEquals("unsupported.native-core-primitive.analytic-shape.coverage", refusal.code)
    }

    private fun rectSemantic(
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
    ): GPUDrawSemanticPayload.CorePrimitive {
        val geometry = GPUCorePrimitiveGeometryInput.Rect(2.5f, 3.25f, 18.75f, 20.5f)
        return gatheredSemantic(
            geometry = geometry,
            sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
            coverageMode = coverageMode,
            rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            rectGeometryAuthority = corePrimitiveRectGeometryAuthority(
                GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom),
                GPUTransformFacts.identity(),
            ),
        )
    }

    private fun rrectSemantic(
        sourceRadii: MutableList<Float>,
        color: MutableList<Float> = COLOR.toMutableList(),
    ): GPUDrawSemanticPayload.CorePrimitive {
        val source = GPURRect(
            rect = GPURect(1f, 2f, 17f, 14f),
            topLeft = GPURRectCornerRadii(sourceRadii[0], sourceRadii[1]),
            topRight = GPURRectCornerRadii(sourceRadii[2], sourceRadii[3]),
            bottomRight = GPURRectCornerRadii(sourceRadii[4], sourceRadii[5]),
            bottomLeft = GPURRectCornerRadii(sourceRadii[6], sourceRadii[7]),
        )
        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(GPURRectNormalizer.normalize(source))
        val authority = assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
            corePrimitiveRRectGeometryAuthority(source, normalized, GPUTransformFacts.identity()),
        ).authority
        return gatheredSemantic(
            geometry = authority.sealedDeviceGeometryInput(),
            sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
            coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
            color = color,
            rrectGeometryAuthority = authority,
        )
    }

    private fun gatheredSemantic(
        geometry: GPUCorePrimitiveGeometryInput,
        sourceFamily: GPUCorePrimitiveSourceFamily,
        coverageMode: GPUCorePrimitiveCoverageMode,
        color: List<Float> = COLOR,
        rectRouteAuthority: GPUCorePrimitiveRectRouteAuthority? = null,
        rectGeometryAuthority: GPUCorePrimitiveRectGeometryAuthority? = null,
        rrectGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority? = null,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = COMMAND_ID,
            sourceFamily = sourceFamily,
            geometry = geometry,
            premultipliedRgba = color,
            targetBounds = TARGET,
            scissorBounds = TARGET,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            blendPlanIdentity = "src-over.premul",
            frameProvenance = GPUFrameProvenance.GmContent,
            coverageMode = coverageMode,
            analysisRecordId = when (sourceFamily) {
                GPUCorePrimitiveSourceFamily.Rect -> "analysis.fill_rect.$COMMAND_ID"
                GPUCorePrimitiveSourceFamily.RRect -> "analysis.fill_rrect.$COMMAND_ID"
                else -> null
            },
            analysisCommandFamily = when (sourceFamily) {
                GPUCorePrimitiveSourceFamily.Rect -> "FillRect"
                GPUCorePrimitiveSourceFamily.RRect -> "FillRRect"
                else -> null
            },
            rectRouteAuthority = rectRouteAuthority,
            rectGeometryAuthority = rectGeometryAuthority,
            rrectGeometryAuthority = rrectGeometryAuthority,
        ),
    )

    private fun GPUDrawSemanticPayload.CorePrimitive.forged(
        sourceFamily: GPUCorePrimitiveSourceFamily = this.sourceFamily,
        coverageMode: GPUCorePrimitiveCoverageMode = this.coverageMode,
        rectGeometryAuthority: GPUCorePrimitiveRectGeometryAuthority? = this.rectGeometryAuthority,
        rrectGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority? = this.rrectGeometryAuthority,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUDrawSemanticPayload.CorePrimitive(
        payloadRef = payloadRef,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = premultipliedRgba,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlanIdentity = clipExecutionPlanIdentity,
        blendPlanIdentity = blendPlanIdentity,
        frameProvenance = frameProvenance,
        coverageMode = coverageMode,
        analysisRecordId = analysisRecordId,
        analysisCommandFamily = analysisCommandFamily,
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
    )

    private fun ByteBuffer.floatsAt(offset: Int, count: Int): List<Float> =
        List(count) { index -> getFloat(offset + index * Float.SIZE_BYTES) }

    private companion object {
        const val COMMAND_ID = 41
        val TARGET = GPUPixelBounds(0, 0, 32, 24)
        val COLOR = listOf(0.1f, 0.2f, 0.3f, 0.4f)
    }
}
