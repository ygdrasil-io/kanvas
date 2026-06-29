package org.graphiks.kanvas.gpu.renderer.transform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PerspectiveTransformTest {

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    private val unitRect = Rect(0f, 0f, 1f, 1f)

    @Test
    fun `perspective transform accepted for rect geometry with solid color`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
            matrix = identity(),
            sourceBounds = unitRect,
        ).analyze()
        assertIs<PerspectiveTransformRoute.Accepted>(route)
        assertEquals("perspective-rect", route.transformKind)
        assertEquals(GPUPerspectiveRouteAcceptance.Accepted, route.acceptance)
    }

    @Test
    fun `perspective transform accepted for rrect geometry with solid color`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.RRect,
            material = MaterialKind.SolidColor,
            matrix = identity(),
            sourceBounds = unitRect,
        ).analyze()
        assertIs<PerspectiveTransformRoute.Accepted>(route)
    }

    @Test
    fun `perspective transform refused for path geometry`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Path,
            material = MaterialKind.SolidColor,
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals(
            "unsupported.transform.perspective_route_rejected.path",
            route.diagnostic.code,
        )
        assertEquals(GPUPerspectiveRouteAcceptance.RefusedAffineOnly, route.acceptance)
    }

    @Test
    fun `perspective transform refused for text geometry`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Text,
            material = MaterialKind.SolidColor,
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals(
            "unsupported.transform.perspective_route_rejected.text",
            route.diagnostic.code,
        )
    }

    @Test
    fun `perspective transform refused for non-solid material on rect`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.Gradient,
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals(
            "unsupported.transform.perspective_route_rejected.gradient",
            route.diagnostic.code,
        )
    }

    @Test
    fun `transform plan reports finite determinant and positive w-divide for identity`() {
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
            matrix = identity(),
            sourceBounds = unitRect,
        ).transformPlan()
        assertTrue(plan.finiteDeterminant)
        assertEquals(1f, plan.wDivideSign)
    }

    @Test
    fun `perspective transform refused for near-zero determinant`() {
        val singular = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
            matrix = singular,
            sourceBounds = unitRect,
        )
        assertFalse(plan.transformPlan().finiteDeterminant)
        val route = plan.analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals("unsupported.transform.perspective_degenerate", route.diagnostic.code)
        assertEquals(GPUPerspectiveRouteAcceptance.RefusedDegenerate, route.acceptance)
    }

    @Test
    fun `perspective transform refused for behind-camera geometry`() {
        val behindCamera = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, -1f,
        )
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
            matrix = behindCamera,
            sourceBounds = unitRect,
        )
        val proof = plan.boundsProof()
        assertTrue(proof.behindCamera)
        val route = plan.analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals("unsupported.transform.perspective_degenerate", route.diagnostic.code)
        assertEquals(GPUPerspectiveRouteAcceptance.RefusedDegenerate, route.acceptance)
    }

    @Test
    fun `bounds proof projects four corners to device bounds`() {
        val perspective = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 1f,
        )
        val plan = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect,
            material = MaterialKind.SolidColor,
            matrix = perspective,
            sourceBounds = unitRect,
        )
        val proof = plan.boundsProof()
        assertTrue(proof.allCornersFinite)
        assertFalse(proof.behindCamera)
        val bounds = assertNotNull(proof.projectedBounds)
        assertEquals(0f, bounds.left, 1e-4f)
        assertEquals(0f, bounds.top, 1e-4f)
        assertEquals(1f, bounds.right, 1e-4f)
        assertEquals(0.6666667f, bounds.bottom, 1e-4f)

        val route = plan.analyze()
        assertIs<PerspectiveTransformRoute.Accepted>(route)
        assertNotNull(route.boundsProof.projectedBounds)
    }
}
