package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.geometry.GPUDrawPointsDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.GPUDrawPointsPreparedPlanner
import org.graphiks.kanvas.gpu.renderer.geometry.GPUShapeDescriptor

class DrawPointsAnalysisContractsTest {
    @Test
    fun `analysis touchpoint reports prepared draw points route facts`() {
        val geometryPlan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = drawPointsShape.copy(provenance = "drawlines_with_local_matrix"),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 4,
                strokeWidth = 2f,
                strokeCap = "Butt",
                localMatrixHash = "lm.rotate20.scale",
            ),
        )

        val touchpoint = geometryPlan.toDrawPointsAnalysisTouchpoint(recordId = "analysis.draw_points.24")

        assertEquals("prepared.draw_points.lines", touchpoint.routeDecisionLabel)
        assertEquals(listOf("draw-points-line-strip.render-step"), touchpoint.renderStepCandidates)
        assertEquals(
            listOf("prepared_draw_points:prepared.draw-points.lines.count4.width2.butt.identity.lm_lm_rotate20_scale"),
            touchpoint.resourceDeclarations,
        )
        assertEquals(
            listOf(
                "draw_points:point_mode=Lines",
                "draw_points:stroke_cap=Butt",
                "draw_points:local_matrix=lm.rotate20.scale",
                "draw_points:consumer=draw-points-line-strip.render-step",
            ),
            touchpoint.diagnostics.map { it.code },
        )
    }

    @Test
    fun `analysis touchpoint reports draw points refusal facts`() {
        val geometryPlan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = drawPointsShape.copy(provenance = "points"),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 2,
                strokeWidth = 1f,
                strokeCap = "Butt",
                localMatrixHash = "handle:0xdeadbeef",
            ),
        )

        val touchpoint = geometryPlan.toDrawPointsAnalysisTouchpoint(recordId = "analysis.draw_points.25")

        assertEquals("refused.draw_points", touchpoint.routeDecisionLabel)
        assertEquals(emptyList(), touchpoint.renderStepCandidates)
        assertEquals(emptyList(), touchpoint.resourceDeclarations)
        assertEquals(
            listOf(
                "unsupported.draw_points.local_matrix_key",
                "draw_points:point_mode=Lines",
                "draw_points:stroke_cap=Butt",
                "draw_points:local_matrix=handle:0xdeadbeef",
            ),
            touchpoint.diagnostics.map { it.code },
        )
    }
}

private val drawPointsShape = GPUShapeDescriptor(
    shapeKind = "draw-points",
    boundsLabel = "local[0,0,64,64]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)
