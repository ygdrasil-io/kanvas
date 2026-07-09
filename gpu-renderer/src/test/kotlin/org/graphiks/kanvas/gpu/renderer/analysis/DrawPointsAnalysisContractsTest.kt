package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
                "draw_points.point_mode",
                "draw_points.stroke_cap",
                "draw_points.local_matrix",
                "draw_points.consumer",
            ),
            touchpoint.diagnostics.map { it.code },
        )
        assertEquals(
            mapOf("pointMode" to "Lines"),
            touchpoint.diagnostics[0].facts,
        )
        assertEquals(
            mapOf("strokeCap" to "Butt"),
            touchpoint.diagnostics[1].facts,
        )
        assertEquals(
            mapOf("localMatrix" to "lm.rotate20.scale"),
            touchpoint.diagnostics[2].facts,
        )
        assertEquals(
            mapOf("consumerKind" to "draw-points-line-strip.render-step"),
            touchpoint.diagnostics[3].facts,
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
                "draw_points.point_mode",
                "draw_points.stroke_cap",
                "draw_points.local_matrix",
            ),
            touchpoint.diagnostics.map { it.code },
        )
        assertNull(touchpoint.diagnostics[0].message)
        assertEquals(mapOf("pointMode" to "Lines"), touchpoint.diagnostics[1].facts)
        assertEquals(mapOf("strokeCap" to "Butt"), touchpoint.diagnostics[2].facts)
        assertEquals(mapOf("localMatrix" to "invalid"), touchpoint.diagnostics[3].facts)
        assertEquals("drawPoints local matrix=invalid", touchpoint.diagnostics[3].message)
        assertTrue(
            touchpoint.diagnostics.none { diagnostic ->
                diagnostic.code.contains("handle:0xdeadbeef") ||
                    diagnostic.message?.contains("handle:0xdeadbeef") == true ||
                    diagnostic.facts.values.any { it.contains("handle:0xdeadbeef") }
            },
        )
    }
}

private val drawPointsShape = GPUShapeDescriptor(
    shapeKind = "draw-points",
    boundsLabel = "local[0,0,64,64]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)
