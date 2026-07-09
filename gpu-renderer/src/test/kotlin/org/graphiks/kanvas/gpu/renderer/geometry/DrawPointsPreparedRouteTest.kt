package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DrawPointsPreparedRouteTest {
    @Test
    fun `point mode lines preserve shader local matrix evidence`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = drawPointsShape.copy(provenance = "drawlines_with_local_matrix"),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 4,
                strokeWidth = 2f,
                strokeCap = "Butt",
                localMatrixHash = "lm.rotate20.scale",
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertEquals("draw-points-line-strip.render-step", route.plan.consumerKind)
        assertEquals(1, route.plan.artifact.generation)
        assertContains(route.plan.artifact.artifactKey, "lm_rotate20_scale")
        assertContains(plan.dumpLines().joinToString("\n"), "mode=Lines")
        assertContains(plan.dumpLines().joinToString("\n"), "localMatrix=lm.rotate20.scale")
    }

    @Test
    fun `point mode points use stroke cap in key`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = drawPointsShape.copy(boundsLabel = "local[0,0,16,16]", provenance = "points"),
            points = GPUDrawPointsDescriptor(
                pointMode = "Points",
                pointCount = 3,
                strokeWidth = 5f,
                strokeCap = "Round",
                localMatrixHash = null,
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertContains(route.plan.artifact.artifactKey, "round")
        assertFalse(route.plan.artifact.artifactKey.contains("handle"))
        assertContains(plan.dumpLines().joinToString("\n"), "mode=Points")
    }

    @Test
    fun `draw points refuses invalid local matrix proof`() {
        val plan = GPUDrawPointsPreparedPlanner().plan(
            descriptor = drawPointsShape.copy(boundsLabel = "local[0,0,16,16]", provenance = "points"),
            points = GPUDrawPointsDescriptor(
                pointMode = "Lines",
                pointCount = 2,
                strokeWidth = 1f,
                strokeCap = "Butt",
                localMatrixHash = "handle:0xdeadbeef",
                transformClass = "identity",
                finiteProof = "finite",
            ),
        )

        val route = assertIs<GPUGeometryRoute.Refused>(plan.route)
        assertEquals("unsupported.draw_points.local_matrix_key", route.diagnostic.code)
        val dump = plan.dumpLines()
        assertEquals("geometry:draw-points.refused reason=unsupported.draw_points.local_matrix_key", dump.first())
        assertContains(
            dump,
            "draw-points:descriptor mode=Lines count=2 width=1 cap=Butt transform=identity finite=finite localMatrix=invalid",
        )
        assertTrue(dump.none { it.contains("handle:0xdeadbeef") })
    }

    @Test
    fun `draw points refuses non canonical local matrix evidence keys`() {
        val invalidKeys = listOf(
            "handle:0xdeadbeef",
            "lm rotate",
            "lm/rotate",
            "lm:rotate",
            "lm#rotate",
            "lm.rotate20.scale?",
            "lm.rotate20.scale~proof",
            "lm.rotate20.scale\u00e9",
            "l".repeat(65),
        )

        invalidKeys.forEach { localMatrixKey ->
            val plan = GPUDrawPointsPreparedPlanner().plan(
                descriptor = drawPointsShape.copy(boundsLabel = "local[0,0,16,16]", provenance = "points"),
                points = GPUDrawPointsDescriptor(
                    pointMode = "Lines",
                    pointCount = 2,
                    strokeWidth = 1f,
                    strokeCap = "Butt",
                    localMatrixHash = localMatrixKey,
                    transformClass = "identity",
                    finiteProof = "finite",
                ),
            )

            val route = assertIs<GPUGeometryRoute.Refused>(plan.route)
            assertEquals(
                "unsupported.draw_points.local_matrix_key",
                route.diagnostic.code,
                "localMatrixHash=$localMatrixKey",
            )
        }
    }
}

private val drawPointsShape = GPUShapeDescriptor(
    shapeKind = "draw-points",
    boundsLabel = "local[0,0,64,64]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)
