package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

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
        assertEquals(
            listOf(
                "geometry:draw-points.refused reason=unsupported.draw_points.local_matrix_key",
                "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-draw-points-parity no-local-matrix-runtime-compilation",
            ),
            plan.dumpLines(),
        )
    }
}

private val drawPointsShape = GPUShapeDescriptor(
    shapeKind = "draw-points",
    boundsLabel = "local[0,0,64,64]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)
