package org.graphiks.kanvas.gpu.renderer.transform

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class PerspectiveTransformTest {

    @Test
    fun `perspective transform accepted for rect geometry with solid color`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect, material = MaterialKind.SolidColor
        ).analyze()
        assertIs<PerspectiveTransformRoute.Accepted>(route)
        assertEquals("perspective-rect", route.transformKind)
    }

    @Test
    fun `perspective transform accepted for rrect geometry with solid color`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.RRect, material = MaterialKind.SolidColor
        ).analyze()
        assertIs<PerspectiveTransformRoute.Accepted>(route)
    }

    @Test
    fun `perspective transform refused for path geometry`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Path, material = MaterialKind.SolidColor
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals("unsupported.perspective_transform.path", route.diagnostic.code)
    }

    @Test
    fun `perspective transform refused for text geometry`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Text, material = MaterialKind.SolidColor
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals("unsupported.perspective_transform.text", route.diagnostic.code)
    }

    @Test
    fun `perspective transform refused for non-solid material on rect`() {
        val route = PerspectiveTransformPlan.forGeometry(
            geometry = GeometryKind.Rect, material = MaterialKind.Gradient
        ).analyze()
        assertIs<PerspectiveTransformRoute.Refused>(route)
        assertEquals("unsupported.perspective_transform.gradient", route.diagnostic.code)
    }
}
