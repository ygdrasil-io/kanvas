package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUCorePrimitiveNativeShaderTest {
    @Test
    fun `shader is parser validated and converts device coordinates with y inversion`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveNativeShader(),
        )

        assertContains(ready.plan.wgslSource, "device_position.x / core.target_size.x * 2.0 - 1.0")
        assertContains(ready.plan.wgslSource, "1.0 - device_position.y / core.target_size.y * 2.0")
        assertContains(ready.plan.wgslSource, "return core.premul_rgba;")
    }

    @Test
    fun `one reflected module exposes shared direct cover and stencil entry points`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT to "fragment",
                CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        assertEquals(32, reflection.layouts.single { it.structName == "CorePrimitiveBlock" }.size)
        assertEquals("core-primitive-device-geometry-wgsl-v2", CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY)
    }

    @Test
    fun `analytic clip shader is parser reflected as exact uniform64 coverage before premul output`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticClipNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        assertEquals(64, reflection.layouts.single {
            it.structName == "CorePrimitiveAnalyticClipBlock"
        }.size)
        assertContains(ready.plan.wgslSource, "let coverage = select(rect_coverage(position), rrect_coverage(position)")
        assertContains(ready.plan.wgslSource, "return analytic.premul_rgba * coverage;")
        assertContains(ready.plan.wgslSource, "sd_coverage")
        assertEquals(
            "core-primitive-analytic-clip-device-geometry-wgsl-v1",
            CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_SHADER_IDENTITY,
        )
        assertEquals(
            "vertex-fragment-dynamic-uniform64-analytic-clip-v1",
            CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_BINDING_LAYOUT_IDENTITY,
        )
    }

    @Test
    fun `elliptic rrect aa distance remains in device pixels on strips and corners`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticClipNativeShader(),
        )
        val source = ready.plan.wgslSource

        assertContains(source, "let outer_q = abs(position - center) - half_extent;")
        assertContains(source, "let corner_q = outer_q + radius;")
        assertContains(source, "let implicit_value = normalized_length - 1.0;")
        assertContains(source, "let implicit_gradient = corner_q / (radius * radius * safe_normalized_length);")
        assertContains(source, "let corner_distance = implicit_value / safe_gradient_length;")
        assertContains(source, "let strip_distance = max(outer_q.x, outer_q.y);")
        assertContains(source, "corner_q.x > 0.0 && corner_q.y > 0.0")

        val halfX = 20f
        val halfY = 12f
        val radiusX = 8f
        val radiusY = 3f
        fun distance(x: Float, y: Float): Float {
            val outerX = abs(x) - halfX
            val outerY = abs(y) - halfY
            val cornerX = outerX + radiusX
            val cornerY = outerY + radiusY
            if (cornerX <= 0f || cornerY <= 0f) return maxOf(outerX, outerY)
            val normalizedLength = sqrt(
                cornerX * cornerX / (radiusX * radiusX) +
                    cornerY * cornerY / (radiusY * radiusY),
            ).coerceAtLeast(1e-6f)
            val implicitValue = normalizedLength - 1f
            val gradientX = cornerX / (radiusX * radiusX * normalizedLength)
            val gradientY = cornerY / (radiusY * radiusY * normalizedLength)
            val gradientLength = sqrt(gradientX * gradientX + gradientY * gradientY).coerceAtLeast(1e-6f)
            return implicitValue / gradientLength
        }

        assertTrue(abs(distance(halfX + 1f, 0f) - 1f) < 1e-6f)
        assertTrue(abs(distance(halfX - 1f, 0f) + 1f) < 1e-6f)
        assertTrue(abs(distance(0f, halfY + 1f) - 1f) < 1e-6f)
        assertTrue(abs(distance(0f, halfY - 1f) + 1f) < 1e-6f)

        val invSqrt2 = 1f / sqrt(2f)
        val boundaryCornerX = radiusX * invSqrt2
        val boundaryCornerY = radiusY * invSqrt2
        val gradientX = boundaryCornerX / (radiusX * radiusX)
        val gradientY = boundaryCornerY / (radiusY * radiusY)
        val gradientLength = sqrt(gradientX * gradientX + gradientY * gradientY)
        val normalX = gradientX / gradientLength
        val normalY = gradientY / gradientLength
        val boundaryX = halfX - radiusX + boundaryCornerX
        val boundaryY = halfY - radiusY + boundaryCornerY

        assertTrue(abs(distance(boundaryX, boundaryY)) < 1e-5f)
        assertTrue(abs(distance(boundaryX + normalX, boundaryY + normalY) - 1f) < 0.15f)
        assertTrue(abs(distance(boundaryX - normalX, boundaryY - normalY) + 1f) < 0.15f)
        assertTrue(requireNotNull(ready.plan.wgslReflection).report.validation.success)
    }
}
