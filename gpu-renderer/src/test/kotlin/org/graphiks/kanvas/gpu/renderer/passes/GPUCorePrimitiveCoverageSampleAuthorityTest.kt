package org.graphiks.kanvas.gpu.renderer.passes

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode

class GPUCorePrimitiveCoverageSampleAuthorityTest {
    @Test
    fun `single sample authority accepts only the already promoted hard edge routes`() {
        assertNull(code(rect, GPUCorePrimitiveCoverageMode.FullOrScissor, GPUSamplePlan.SingleSampleFrame))
        assertNull(code(directTriangles, GPUCorePrimitiveCoverageMode.FullOrScissor, GPUSamplePlan.SingleSampleFrame))
        assertNull(code(stencilEdgeFan, GPUCorePrimitiveCoverageMode.Stencil1x, GPUSamplePlan.SingleSampleFrame))

        assertEquals(
            "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
            code(rect, GPUCorePrimitiveCoverageMode.ScalarAA, GPUSamplePlan.SingleSampleFrame),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
            code(rrect, GPUCorePrimitiveCoverageMode.FullOrScissor, GPUSamplePlan.SingleSampleFrame),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
            code(rrect, GPUCorePrimitiveCoverageMode.ScalarAA, GPUSamplePlan.SingleSampleFrame),
        )
    }

    @Test
    fun `coverage modes accept only their exact promoted geometry lane`() {
        listOf(
            rect to GPUCorePrimitiveCoverageMode.Stencil1x,
            directTriangles to GPUCorePrimitiveCoverageMode.Stencil1x,
            stencilEdgeFan to GPUCorePrimitiveCoverageMode.FullOrScissor,
            strokeStencilEdgeFan to GPUCorePrimitiveCoverageMode.Stencil1x,
        ).forEach { (geometry, coverageMode) ->
            assertEquals(
                "invalid.core_primitive.coverage_sample.geometry_coverage",
                code(geometry, coverageMode, GPUSamplePlan.SingleSampleFrame),
            )
        }
    }

    @Test
    fun `stencil coverage and sample contradictions have stable priority`() {
        assertEquals(
            "invalid.core_primitive.coverage_sample.stencil_aa_requires_multisample",
            code(stencilEdgeFan, GPUCorePrimitiveCoverageMode.StencilAA, GPUSamplePlan.SingleSampleFrame),
        )
        assertEquals(
            "invalid.core_primitive.coverage_sample.stencil_1x_requires_single_sample",
            code(stencilEdgeFan, GPUCorePrimitiveCoverageMode.Stencil1x, GPUSamplePlan.MultisampleFrame(4)),
        )
    }

    @Test
    fun `local resolve and samples outside the bounded WebGPU lane refuse explicitly`() {
        assertEquals(
            "unsupported.core_primitive.coverage_sample.local_resolve",
            code(rect, GPUCorePrimitiveCoverageMode.FullOrScissor, GPUSamplePlan.LocalResolveApproximation(4)),
        )
        listOf(2, 8, 16).forEach { sampleCount ->
            assertEquals(
                "unsupported.core_primitive.coverage_sample.sample_count",
                code(
                    rect,
                    GPUCorePrimitiveCoverageMode.FullOrScissor,
                    GPUSamplePlan.MultisampleFrame(sampleCount),
                    completeMsaaCapabilities(),
                ),
            )
        }
    }

    @Test
    fun `color multisample requires exact render and resolve capability before promotion`() {
        assertEquals(
            "unsupported.core_primitive.coverage_sample.color_capability",
            code(
                rect,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                GPUSamplePlan.MultisampleFrame(4),
                capabilities(),
            ),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.color_capability",
            code(
                rect,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                GPUSamplePlan.MultisampleFrame(4),
                capabilities(colorRender = setOf(1, 4), colorResolve = emptySet()),
            ),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.multisample_not_promoted",
            code(
                rect,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                GPUSamplePlan.MultisampleFrame(4),
                completeMsaaCapabilities(),
            ),
        )
    }

    @Test
    fun `stencil AA requires independent color resolve and depth stencil render proofs`() {
        assertEquals(
            "unsupported.core_primitive.coverage_sample.depth_stencil_capability",
            code(
                stencilEdgeFan,
                GPUCorePrimitiveCoverageMode.StencilAA,
                GPUSamplePlan.MultisampleFrame(4),
                capabilities(colorRender = setOf(1, 4), colorResolve = setOf(4)),
            ),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.stencil_aa_not_promoted",
            code(
                stencilEdgeFan,
                GPUCorePrimitiveCoverageMode.StencilAA,
                GPUSamplePlan.MultisampleFrame(4),
                completeMsaaCapabilities(),
            ),
        )
    }

    @Test
    fun `geometry and scalar refusals precede irrelevant multisample capability checks`() {
        assertEquals(
            "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
            code(rrect, GPUCorePrimitiveCoverageMode.FullOrScissor, GPUSamplePlan.MultisampleFrame(4)),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
            code(rect, GPUCorePrimitiveCoverageMode.ScalarAA, GPUSamplePlan.MultisampleFrame(4)),
        )
    }

    @Test
    fun `empty forged target refuses instead of throwing during capability validation`() {
        assertEquals(
            "invalid.core_primitive.coverage_sample.target_bounds",
            code(
                rect,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                GPUSamplePlan.MultisampleFrame(4),
                completeMsaaCapabilities(),
                GPUPixelBounds(0, 0, 0, 16),
            ),
        )
    }

    private fun code(
        geometry: GPUCorePrimitiveGeometry,
        coverageMode: GPUCorePrimitiveCoverageMode,
        samplePlan: GPUSamplePlan,
        capabilities: GPUCapabilities = capabilities(),
        bounds: GPUPixelBounds = targetBounds,
    ): String? = when (
        val authority = validateCorePrimitiveCoverageSampleAuthority(
            geometry = geometry,
            coverageMode = coverageMode,
            targetBounds = bounds,
            samplePlan = samplePlan,
            capabilities = capabilities,
        )
    ) {
        GPUCorePrimitiveCoverageSampleAuthority.Accepted -> null
        is GPUCorePrimitiveCoverageSampleAuthority.Refused -> {
            assertIs<GPUCorePrimitiveCoverageSampleAuthority.Refused>(authority)
            authority.code
        }
    }

    private fun completeMsaaCapabilities(): GPUCapabilities = capabilities(
        colorRender = setOf(1, 4),
        colorResolve = setOf(4),
        depthStencilRender = setOf(1, 4),
    )

    private fun capabilities(
        colorRender: Set<Int>? = null,
        colorResolve: Set<Int> = emptySet(),
        depthStencilRender: Set<Int>? = null,
    ): GPUCapabilities {
        val sampleSupport = buildMap {
            colorRender?.let {
                put(GPUTextureFormat.RGBA8Unorm, GPUTextureSampleCountSupport(it, colorResolve))
            }
            depthStencilRender?.let {
                put(GPUTextureFormat.Depth24PlusStencil8, GPUTextureSampleCountSupport(it))
            }
        }
        return GPUCapabilities(
            implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
            facts = listOf(GPUCapabilityFact("samples", "unit", "observed", true, "coverage-sample")),
            snapshotId = "coverage-sample-${sampleSupport.hashCode()}",
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(sampleSupport),
        )
    }

    private companion object {
        val targetBounds = GPUPixelBounds(0, 0, 16, 16)
        val rect = GPUCorePrimitiveGeometry.Rect(1f, 1f, 8f, 8f)
        val rrect = GPUCorePrimitiveGeometry.RRect(1f, 1f, 8f, 8f, List(8) { 1f })
        val directTriangles = path(GPUCorePrimitiveGeometryMode.DirectTriangles)
        val stencilEdgeFan = path(GPUCorePrimitiveGeometryMode.StencilEdgeFan)
        val strokeStencilEdgeFan = path(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan)

        private fun path(mode: GPUCorePrimitiveGeometryMode) =
            GPUCorePrimitiveGeometry.TriangulatedPath(
                vertices = listOf(1f, 1f, 8f, 1f, 4f, 8f),
                indices = listOf(0, 1, 2),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 3,
                coverBounds = targetBounds,
                geometryMode = mode,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
                strokeStyle = null,
            )
    }
}
