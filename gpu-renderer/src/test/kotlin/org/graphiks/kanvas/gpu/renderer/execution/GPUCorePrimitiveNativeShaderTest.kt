package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslValidationSummary

class GPUCorePrimitiveNativeShaderTest {
    @Test
    fun `analytic shape executable shader fails closed on absent or incomplete parser reflection`() {
        val validReflection = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticShapeNativeShader(),
        ).plan.wgslReflection
        requireNotNull(validReflection)
        val report = validReflection.report
        val invalidReflections = listOf(
            null,
            validReflection.copy(validated = false),
            GPUColorWgslReflection(
                report.copy(validation = WgslValidationSummary(success = false)),
                validated = true,
            ),
            GPUColorWgslReflection(report.copy(entryPoints = report.entryPoints.dropLast(1))),
            GPUColorWgslReflection(report.copy(bindings = emptyList())),
            GPUColorWgslReflection(
                report.copy(
                    layouts = report.layouts.map { layout ->
                        if (layout.structName == "CorePrimitiveAnalyticShapeBlock") {
                            layout.copy(size = 64)
                        } else {
                            layout
                        }
                    },
                ),
            ),
            GPUColorWgslReflection(
                report.copy(
                    layouts = report.layouts.map { layout ->
                        if (layout.structName == "CorePrimitiveAnalyticShapeBlock") {
                            layout.copy(
                                members = layout.members.map { member ->
                                    if (member.name == "radii1") member.copy(offset = 60) else member
                                },
                            )
                        } else {
                            layout
                        }
                    },
                ),
            ),
        )

        invalidReflections.forEach { reflection ->
            val rejected = assertIs<GPUCorePrimitiveNativeShaderResult.Rejected>(
                buildCorePrimitiveAnalyticShapeNativeShader { _, _ ->
                    GPUColorWgslValidation.Validated(reflection)
                },
            )
            assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_REFLECTION_INVALID_REASON, rejected.reason)
            assertTrue(rejected.message.isNotBlank())
        }
    }

    @Test
    fun `analytic shape shader reflects uniform80 and applies Graphite like four corner coverage`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticShapeNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        val block = reflection.layouts.single { it.structName == "CorePrimitiveAnalyticShapeBlock" }
        assertEquals(80, block.size)
        assertEquals(
            listOf(
                "target_size" to (0 to 8),
                "anti_alias" to (8 to 4),
                "padding0" to (12 to 4),
                "premul_rgba" to (16 to 16),
                "device_bounds" to (32 to 16),
                "radii0" to (48 to 16),
                "radii1" to (64 to 16),
            ),
            block.members.map { it.name to (it.offset to it.size) },
        )

        val source = ready.plan.wgslSource
        assertContains(source, "corner_distance(distance, edge_distances.xy, analytic.radii0.xy)")
        assertContains(source, "corner_distance(distance, edge_distances.zy, analytic.radii0.zw)")
        assertContains(source, "corner_distance(distance, edge_distances.zw, analytic.radii1.xy)")
        assertContains(source, "corner_distance(distance, edge_distances.xw, analytic.radii1.zw)")
        assertContains(source, "uv.x > 0.0 && uv.y > 0.0")
        assertContains(source, "radii.x > 0.0 && radii.y > 0.0")
        assertContains(source, "0.5 * (1.0 - dot(uv, normalized_uv)) / normalized_length")
        assertContains(source, "let scale = clamp(min(shape_size.x, shape_size.y), 0.0, 1.0);")
        assertContains(source, "let bias = 1.0 - 0.5 * scale;")
        assertContains(source, "return analytic.premul_rgba * coverage;")
        assertEquals(1, source.split("premul_rgba * coverage").size - 1)
        listOf("fwidth", "texture", "sampler", "discard").forEach { forbidden ->
            assertFalse(forbidden in source, "Analytic shape shader must not contain $forbidden")
        }
        listOf("0.0001", "0.000001", "center", "quadrant").forEach { forbidden ->
            assertFalse(forbidden in source, "Analytic shape shader must not contain $forbidden")
        }
    }

    @Test
    fun `analytic shape coverage math handles subpixel rect ellipse and square corners`() {
        val rectBounds = listOf(1.25f, 2.5f, 3.75f, 4.75f)
        val squareRadii = List(8) { 0f }
        assertEquals(1f, analyticCoverage(2f, 3f, rectBounds, squareRadii, antiAlias = true))
        assertEquals(0.25f, analyticCoverage(1f, 3f, rectBounds, squareRadii, antiAlias = true), 1e-6f)
        assertEquals(0f, analyticCoverage(0.5f, 3f, rectBounds, squareRadii, antiAlias = true))
        assertEquals(0f, analyticCoverage(1f, 3f, rectBounds, squareRadii, antiAlias = false))

        val rrectBounds = listOf(0f, 0f, 10f, 10f)
        val topLeftEllipse = listOf(4f, 4f, 0f, 0f, 0f, 0f, 0f, 0f)
        val diagonalBoundary = 4f - 4f / sqrt(2f)
        assertEquals(
            0.5f,
            analyticCoverage(
                diagonalBoundary,
                diagonalBoundary,
                rrectBounds,
                topLeftEllipse,
                antiAlias = true,
            ),
            1e-5f,
        )
        assertEquals(0f, analyticCoverage(0.5f, 0.5f, rrectBounds, topLeftEllipse, antiAlias = true))
        assertEquals(0.75f, analyticCoverage(9.75f, 9.75f, rrectBounds, topLeftEllipse, true), 1e-6f)

        val oneZeroComponent = listOf(0f, 4f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(0.75f, analyticCoverage(0.25f, 0.25f, rrectBounds, oneZeroComponent, true), 1e-6f)
    }

    @Test
    fun `asymmetric corner extending past center is not selected by center quadrant`() {
        val bounds = listOf(0f, 0f, 100f, 100f)
        val radii = listOf(80f, 80f, 10f, 10f, 0f, 0f, 0f, 0f)
        val pointX = 60f
        val pointY = 1f

        val fourCornerDistance = graphiteLikeRRectDistance(pointX, pointY, bounds, radii)
        val wrongCenterQuadrantDistance = minOf(
            pointX - bounds[0],
            pointY - bounds[1],
            bounds[2] - pointX,
            bounds[3] - pointY,
        )

        assertTrue(fourCornerDistance < 0f, "The point lies outside the large top-left ellipse")
        assertTrue(wrongCenterQuadrantDistance > 0f, "A center-quadrant shortcut would incorrectly keep it")
        val source = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticShapeNativeShader(),
        ).plan.wgslSource
        assertFalse("position.x > center.x" in source)
        assertFalse("position.y > center.y" in source)
    }

    @Test
    fun `coverage mask producer shader reflects uniform64 fullscreen hard rect and rrect programs`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveCoverageMaskProducerNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        assertEquals(
            setOf(
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RECT_FRAGMENT_ENTRY_POINT to "fragment",
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RRECT_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        val block = reflection.layouts.single {
            it.structName == "CorePrimitiveCoverageMaskProducerBlock"
        }
        assertEquals(64, block.size)
        assertEquals(
            listOf(
                "mask_origin" to (0 to 8),
                "mask_size" to (8 to 8),
                "shape_bounds" to (16 to 16),
                "shape_radii0" to (32 to 16),
                "shape_radii1" to (48 to 16),
            ),
            block.members.map { it.name to (it.offset to it.size) },
        )
        assertContains(ready.plan.wgslSource, "@builtin(vertex_index) vertex_index: u32")
        assertContains(ready.plan.wgslSource, "let device_position = fragment_position.xy + producer.mask_origin;")
        assertContains(ready.plan.wgslSource, "select(0.0, 1.0, rect_signed_distance(device_position) <= 0.0)")
        assertContains(ready.plan.wgslSource, "select(0.0, 1.0, rrect_signed_distance(device_position) <= 0.0)")
        assertTrue("fwidth" !in ready.plan.wgslSource)
        assertTrue("sampler" !in ready.plan.wgslSource)
        assertEquals(
            "core-primitive-coverage-mask-producer-wgsl-v1",
            CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_SHADER_IDENTITY,
        )
        assertEquals(
            "vertex-fragment-dynamic-uniform64-coverage-mask-producer-v1",
            CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_BINDING_LAYOUT_IDENTITY,
        )
    }

    @Test
    fun `coverage mask rrect keeps four asymmetric radii in tl tr br bl packing order`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveCoverageMaskProducerNativeShader(),
        )
        val source = ready.plan.wgslSource

        assertContains(
            source,
            "let top_radius = select(producer.shape_radii0.xy, producer.shape_radii0.zw, local_position.x > 0.0);",
        )
        assertContains(
            source,
            "let bottom_radius = select(producer.shape_radii1.zw, producer.shape_radii1.xy, local_position.x > 0.0);",
        )
        assertContains(
            source,
            "select(top_radius, bottom_radius, local_position.y > 0.0)",
        )

        val topLeft = 2f to 3f
        val topRight = 4f to 5f
        val bottomRight = 6f to 7f
        val bottomLeft = 8f to 9f
        val shapeRadii0 = listOf(topLeft, topRight)
        val shapeRadii1 = listOf(bottomRight, bottomLeft)

        assertEquals(
            listOf(topLeft, topRight, bottomLeft, bottomRight),
            listOf(shapeRadii0[0], shapeRadii0[1], shapeRadii1[1], shapeRadii1[0]),
        )
        assertTrue(
            listOf(topLeft, topRight, bottomRight, bottomLeft).all { (rx, ry) ->
                rx <= 10f && ry <= 10f
            },
            "The bounded oracle must stay inside the accepted width/2 and height/2 subset.",
        )
    }

    @Test
    fun `coverage mask consumer reflects uniform64 textureLoad nearest with payload only invert`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveCoverageMaskConsumerNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(listOf(0 to 0, 0 to 1), reflection.bindings.map { it.group to it.binding })
        assertEquals(
            setOf(
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        val block = reflection.layouts.single {
            it.structName == "CorePrimitiveCoverageMaskConsumerBlock"
        }
        assertEquals(64, block.size)
        assertEquals(
            listOf(
                "target_size" to (0 to 8),
                "mask_origin" to (8 to 8),
                "mask_size" to (16 to 8),
                "padding0" to (24 to 8),
                "premul_rgba" to (32 to 16),
                "invert" to (48 to 4),
                "padding1" to (52 to 4),
                "padding2" to (56 to 4),
                "padding3" to (60 to 4),
            ),
            block.members.map { it.name to (it.offset to it.size) },
        )
        assertContains(ready.plan.wgslSource, "@group(0) @binding(1) var coverage_mask: texture_2d<f32>;")
        assertContains(ready.plan.wgslSource, "let stored_sample = textureLoad(coverage_mask, mask_pixel, 0);")
        assertContains(ready.plan.wgslSource, "stored_coverage = stored_sample[0];")
        assertContains(ready.plan.wgslSource, "consumer.invert != 0u")
        assertContains(ready.plan.wgslSource, "device_position.x / consumer.target_size.x * 2.0 - 1.0")
        assertTrue("sampler" !in ready.plan.wgslSource)
        assertTrue("textureSample" !in ready.plan.wgslSource)
        assertEquals(
            "core-primitive-coverage-mask-consumer-nearest-wgsl-v1",
            CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_SHADER_IDENTITY,
        )
        assertEquals(
            "vertex-fragment-dynamic-uniform64-texture2d-coverage-mask-consumer-v1",
            CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_BINDING_LAYOUT_IDENTITY,
        )
    }

    @Test
    fun `clip stencil producer shader is reflected without bindings and consumes sealed NDC vertices`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveClipStencilProducerNativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(emptyList(), reflection.bindings)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertContains(ready.plan.wgslSource, "fn vs_main(@location(0) ndc_position: vec2<f32>)")
        assertContains(ready.plan.wgslSource, "return vec4<f32>(ndc_position, 0.0, 1.0);")
        assertTrue("target_size" !in ready.plan.wgslSource)
        assertTrue("@group" !in ready.plan.wgslSource)
        assertEquals(
            "core-primitive-clip-stencil-producer-ndc-wgsl-v1",
            CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_SHADER_IDENTITY,
        )
        assertEquals(
            "no-bindings-v1",
            CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_BINDING_LAYOUT_IDENTITY,
        )
    }

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
    fun `analytic intersection4 shader reflects exact uniform160 and multiplies four runtime clips`() {
        val ready = assertIs<GPUCorePrimitiveNativeShaderResult.Ready>(
            buildCorePrimitiveAnalyticIntersection4NativeShader(),
        )
        val reflection = requireNotNull(ready.plan.wgslReflection).report

        assertTrue(reflection.validation.success)
        assertEquals(
            setOf(
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_VERTEX_ENTRY_POINT to "vertex",
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
            ),
            reflection.entryPoints.map { it.name to it.stage }.toSet(),
        )
        assertEquals(listOf(0 to 0), reflection.bindings.map { it.group to it.binding })
        val block = reflection.layouts.single {
            it.structName == "CorePrimitiveAnalyticIntersection4Block"
        }
        assertEquals(160, block.size)
        assertEquals(
            listOf(
                "target_size" to (0 to 8),
                "clip_count" to (8 to 4),
                "padding" to (12 to 4),
                "premul_rgba" to (16 to 16),
                "clip0_bounds" to (32 to 16),
                "clip0_radii" to (48 to 8),
                "clip0_kind" to (56 to 4),
                "clip0_anti_alias" to (60 to 4),
                "clip1_bounds" to (64 to 16),
                "clip1_radii" to (80 to 8),
                "clip1_kind" to (88 to 4),
                "clip1_anti_alias" to (92 to 4),
                "clip2_bounds" to (96 to 16),
                "clip2_radii" to (112 to 8),
                "clip2_kind" to (120 to 4),
                "clip2_anti_alias" to (124 to 4),
                "clip3_bounds" to (128 to 16),
                "clip3_radii" to (144 to 8),
                "clip3_kind" to (152 to 4),
                "clip3_anti_alias" to (156 to 4),
            ),
            block.members.map { it.name to (it.offset to it.size) },
        )
        assertContains(ready.plan.wgslSource, "select(1.0, clip_coverage(position, analytic.clip0_bounds")
        assertContains(ready.plan.wgslSource, "coverage0 * coverage1 * coverage2 * coverage3")
        assertContains(ready.plan.wgslSource, "return analytic.premul_rgba * coverage;")
        assertEquals(
            "core-primitive-analytic-clip-intersection4-device-geometry-wgsl-v1",
            CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_SHADER_IDENTITY,
        )
        assertEquals(
            "vertex-fragment-dynamic-uniform160-analytic-clip-intersection4-v1",
            CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_BINDING_LAYOUT_IDENTITY,
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

    private fun graphiteLikeRRectDistance(
        x: Float,
        y: Float,
        bounds: List<Float>,
        radii: List<Float>,
    ): Float {
        val left = x - bounds[0]
        val top = y - bounds[1]
        val right = bounds[2] - x
        val bottom = bounds[3] - y
        var distance = minOf(left, top, right, bottom)
        fun applyCorner(edgeX: Float, edgeY: Float, radiusX: Float, radiusY: Float) {
            val uvX = radiusX - edgeX
            val uvY = radiusY - edgeY
            if (uvX > 0f && uvY > 0f && radiusX > 0f && radiusY > 0f) {
                val nx = uvX / (radiusX * radiusX)
                val ny = uvY / (radiusY * radiusY)
                val length = sqrt(nx * nx + ny * ny)
                if (length > 0f) {
                    distance = minOf(
                        distance,
                        0.5f * (1f - (uvX * nx + uvY * ny)) / length,
                    )
                }
            }
        }
        applyCorner(left, top, radii[0], radii[1])
        applyCorner(right, top, radii[2], radii[3])
        applyCorner(right, bottom, radii[4], radii[5])
        applyCorner(left, bottom, radii[6], radii[7])
        return distance
    }

    private fun analyticCoverage(
        x: Float,
        y: Float,
        bounds: List<Float>,
        radii: List<Float>,
        antiAlias: Boolean,
    ): Float {
        val distance = graphiteLikeRRectDistance(x, y, bounds, radii)
        if (!antiAlias) return if (distance >= 0f) 1f else 0f
        val scale = minOf(bounds[2] - bounds[0], bounds[3] - bounds[1]).coerceIn(0f, 1f)
        val bias = 1f - 0.5f * scale
        return (scale * (distance + bias)).coerceIn(0f, 1f)
    }
}
