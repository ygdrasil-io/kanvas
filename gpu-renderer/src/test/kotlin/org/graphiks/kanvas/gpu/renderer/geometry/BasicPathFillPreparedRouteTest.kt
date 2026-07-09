package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class BasicPathFillPreparedRouteTest {
    @Test
    fun `inverse fill remains visible in stroke and fill planning`() {
        val plan = GPUStrokeAndFillPreparedPlanner().plan(
            descriptor = triangleShape.copy(shapeKind = "path-stroke-and-fill"),
            path = trianglePath.copy(fillRule = "InverseEvenOdd", inverseFill = true),
            stroke = GPUStrokeDescriptor(
                width = 3f,
                cap = "Square",
                join = "Miter",
                miter = 4f,
                edgeCount = 6,
            ),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertEquals("stroke-and-fill.coverage-composite", route.plan.consumerKind)
        assertContains(plan.dumpLines().joinToString("\n"), "inverse=true")
    }

    @Test
    fun `bounded path fill builds CPU prepared GPU artifact evidence`() {
        val plan = GPUBasicPathFillPreparedPlanner().plan(
            descriptor = triangleShape,
            path = trianglePath,
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        val artifact = route.plan.artifact

        assertEquals("prepared.path-fill.path_triangle_v1.nonzero.identity.edges3", artifact.artifactKey)
        assertEquals("coverage-mask.sample.path-fill", route.plan.consumerKind)
        assertEquals(listOf("path-content-hash", "fill-rule", "transform-class", "bounds-proof"), route.plan.invalidationFacts)
        assertFalse(artifact.artifactKey.contains("handle"))
        assertFalse(artifact.artifactKey.contains("0x"))
        assertEquals(
            listOf(
                "geometry:path-fill.prepared routeKind=CPUPreparedGPU consumer=coverage-mask.sample.path-fill",
                "path:descriptor key=path:triangle:v1 verbs=4 points=3 fillRule=NonZero inverse=false transform=identity edges=3 finite=finite volatility=immutable",
                "artifact:key=prepared.path-fill.path_triangle_v1.nonzero.identity.edges3 lifetime=recording-local budget=path-fill-small bounds=local[0,0,16,16]",
                "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-path-aa",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `inverse fill path is accepted`() {
        val plan = GPUBasicPathFillPreparedPlanner().plan(
            descriptor = triangleShape,
            path = trianglePath.copy(inverseFill = true),
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        assertEquals("coverage-mask.sample.path-fill", route.plan.consumerKind)
        assertContains(plan.dumpLines().joinToString("\n"), "inverse=true")
    }

    @Test
    fun `all Kanvas fill rule names are accepted by prepared fill evidence`() {
        val fillRules = listOf("NonZero", "EvenOdd", "InverseWinding", "InverseEvenOdd")

        for (fillRule in fillRules) {
            val plan = GPUBasicPathFillPreparedPlanner().plan(
                descriptor = triangleShape,
                path = trianglePath.copy(
                    fillRule = fillRule,
                    inverseFill = fillRule.startsWith("Inverse"),
                ),
            )
            val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
            assertEquals("coverage-mask.sample.path-fill", route.plan.consumerKind)
            assertContains(plan.dumpLines().joinToString("\n"), "fillRule=$fillRule")
        }
    }

    @Test
    fun `perspective path fill refuses with split-ready diagnostic`() {
        val plan = GPUBasicPathFillPreparedPlanner().plan(
            descriptor = triangleShape,
            path = trianglePath.copy(transformClass = "perspective"),
        )

        val route = assertIs<GPUGeometryRoute.Refused>(plan.route)
        assertEquals("unsupported.transform.path_perspective", route.diagnostic.code)
        assertContains(route.diagnostic.message, "perspective")
    }

    @Test
    fun `unsupported path fill variants refuse with stable diagnostics`() {
        val cases = listOf(
            RefusalCase("unsupported.path.noncanonical_key", path = trianglePath.copy(pathKey = "handle:0xdeadbeef")),
            RefusalCase("unsupported.path.fill_rule", path = trianglePath.copy(fillRule = "WindingMaybe")),
            RefusalCase("unsupported.transform.path_perspective", path = trianglePath.copy(transformClass = "perspective")),
            RefusalCase("unsupported.path.edge_budget", path = trianglePath.copy(edgeCount = 257)),
            RefusalCase("unsupported.bounds.path", path = trianglePath.copy(finiteProof = "non-finite")),
            RefusalCase("unsupported.bounds.path", descriptor = triangleShape.copy(boundsLabel = "")),
            RefusalCase("unsupported.path.volatile", path = trianglePath.copy(volatility = "volatile")),
        )

        for (case in cases) {
            val plan = GPUBasicPathFillPreparedPlanner().plan(
                descriptor = case.descriptor,
                path = case.path,
            )
            val route = assertIs<GPUGeometryRoute.Refused>(plan.route)

            assertEquals(case.expectedCode, route.diagnostic.code)
            assertContains(plan.diagnostics.map { it.code }, case.expectedCode)
            assertEquals(
                listOf(
                    "geometry:path-fill.refused reason=${case.expectedCode}",
                    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-path-aa",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val descriptor: GPUShapeDescriptor = triangleShape,
    val path: GPUPathDescriptor = trianglePath,
)

private val triangleShape = GPUShapeDescriptor(
    shapeKind = "path-fill",
    boundsLabel = "local[0,0,16,16]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)

private val trianglePath = GPUPathDescriptor(
    pathKey = "path:triangle:v1",
    verbCount = 4,
    pointCount = 3,
    fillRule = "NonZero",
    inverseFill = false,
    finiteProof = "finite",
    volatility = "immutable",
    transformClass = "identity",
    edgeCount = 3,
)
