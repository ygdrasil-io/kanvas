package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class SimpleStrokePreparedRouteTest {
    @Test
    fun `simple bounded stroke builds CPU prepared GPU artifact evidence`() {
        val plan = GPUSimpleStrokePreparedPlanner().plan(
            descriptor = strokeShape,
            path = strokePath,
            stroke = simpleStroke,
        )

        val route = assertIs<GPUGeometryRoute.Prepared>(plan.route)
        val artifact = route.plan.artifact

        assertEquals("prepared.stroke.path_segment_v1.width2.butt.miter4.identity.edges4", artifact.artifactKey)
        assertEquals("stroke-strip.render-step", route.plan.consumerKind)
        assertEquals(
            listOf("path-content-hash", "stroke-width", "cap", "join", "miter", "transform-class", "bounds-proof"),
            route.plan.invalidationFacts,
        )
        assertFalse(artifact.artifactKey.contains("handle"))
        assertFalse(artifact.artifactKey.contains("0x"))
        assertEquals(
            listOf(
                "geometry:stroke.prepared routeKind=CPUPreparedGPU consumer=stroke-strip.render-step",
                "stroke:descriptor path=path:segment:v1 width=2.0 cap=Butt join=Miter miter=4.0 transform=identity edges=4 finite=true hairline=false dash=none",
                "stroke:expansion mode=cpu-prepared-stroke-strip descriptorHash=stroke.path_segment_v1.width2.butt.miter4.identity.edges4 outputBounds=local[0,0,16,4] joinsFallback=false",
                "artifact:key=prepared.stroke.path_segment_v1.width2.butt.miter4.identity.edges4 lifetime=recording-local budget=stroke-simple bounds=local[0,0,16,4]",
                "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-stroke-parity no-hairline no-dash no-round-cap-join",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `accepted miter values derive distinct prepared stroke keys`() {
        val miterTwo = GPUSimpleStrokePreparedPlanner().plan(
            descriptor = strokeShape,
            path = strokePath,
            stroke = simpleStroke.copy(miter = 2f),
        )
        val miterFour = GPUSimpleStrokePreparedPlanner().plan(
            descriptor = strokeShape,
            path = strokePath,
            stroke = simpleStroke.copy(miter = 4f),
        )

        val miterTwoArtifact = assertIs<GPUGeometryRoute.Prepared>(miterTwo.route).plan.artifact
        val miterFourArtifact = assertIs<GPUGeometryRoute.Prepared>(miterFour.route).plan.artifact

        assertEquals("prepared.stroke.path_segment_v1.width2.butt.miter2.identity.edges4", miterTwoArtifact.artifactKey)
        assertEquals("prepared.stroke.path_segment_v1.width2.butt.miter4.identity.edges4", miterFourArtifact.artifactKey)
        assertNotEquals(miterTwoArtifact.artifactKey, miterFourArtifact.artifactKey)
    }

    @Test
    fun `unsupported stroke variants refuse with stable diagnostics`() {
        val cases = listOf(
            StrokeRefusalCase("unsupported.stroke.width_invalid", stroke = simpleStroke.copy(width = 0f)),
            StrokeRefusalCase("unsupported.stroke.width_invalid", stroke = simpleStroke.copy(finiteWidth = false)),
            StrokeRefusalCase("unsupported.stroke.hairline_policy", stroke = simpleStroke.copy(hairline = true)),
            StrokeRefusalCase("unsupported.stroke.cap", stroke = simpleStroke.copy(cap = "Round")),
            StrokeRefusalCase("unsupported.stroke.join", stroke = simpleStroke.copy(join = "Round")),
            StrokeRefusalCase("unsupported.stroke.miter_limit", stroke = simpleStroke.copy(miter = 0.5f)),
            StrokeRefusalCase("unsupported.stroke.dash_complex", stroke = simpleStroke.copy(dashOrPathEffectRef = "dash:v1")),
            StrokeRefusalCase(
                "unsupported.stroke.path_effect_unregistered",
                stroke = simpleStroke.copy(dashOrPathEffectRef = "path-effect:corner"),
            ),
            StrokeRefusalCase("unsupported.stroke.nonuniform_transform", stroke = simpleStroke.copy(transformClass = "nonuniform")),
            StrokeRefusalCase("unsupported.stroke.expansion_budget_exceeded", stroke = simpleStroke.copy(edgeCount = 129)),
            StrokeRefusalCase("unsupported.geometry.path_key_nondeterministic", path = strokePath.copy(pathKey = "handle:0xdeadbeef")),
            StrokeRefusalCase("unsupported.geometry.path_nonfinite", path = strokePath.copy(finiteProof = "non-finite")),
        )

        for (case in cases) {
            val plan = GPUSimpleStrokePreparedPlanner().plan(
                descriptor = case.descriptor,
                path = case.path,
                stroke = case.stroke,
            )
            val route = assertIs<GPUGeometryRoute.Refused>(plan.route)

            assertEquals(case.expectedCode, route.diagnostic.code)
            assertContains(plan.diagnostics.map { it.code }, case.expectedCode)
            assertEquals(
                listOf(
                    "geometry:stroke.refused reason=${case.expectedCode}",
                    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-stroke-parity no-hairline no-dash no-round-cap-join",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class StrokeRefusalCase(
    val expectedCode: String,
    val descriptor: GPUShapeDescriptor = strokeShape,
    val path: GPUPathDescriptor = strokePath,
    val stroke: GPUStrokeDescriptor = simpleStroke,
)

private val strokeShape = GPUShapeDescriptor(
    shapeKind = "path-stroke",
    boundsLabel = "local[0,0,16,4]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)

private val strokePath = GPUPathDescriptor(
    pathKey = "path:segment:v1",
    verbCount = 2,
    pointCount = 2,
    fillRule = "NonZero",
    inverseFill = false,
    finiteProof = "finite",
    volatility = "immutable",
    transformClass = "identity",
    edgeCount = 1,
)

private val simpleStroke = GPUStrokeDescriptor(
    width = 2f,
    cap = "Butt",
    join = "Miter",
    miter = 4f,
    dashOrPathEffectRef = null,
    transformClass = "identity",
    finiteWidth = true,
    hairline = false,
    edgeCount = 4,
)
