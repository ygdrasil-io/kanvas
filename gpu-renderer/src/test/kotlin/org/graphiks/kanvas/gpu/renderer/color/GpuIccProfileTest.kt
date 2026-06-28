package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuIccProfileTest {

    @Test
    fun `ICC v2 matrix TRC profile parses correctly`() {
        val plan = GpuIccProfileParsePlan.parse(
            version = GpuIccVersion.v2,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        )
        assertNotNull(plan.matrixTrc)
        assertEquals(GpuIccVersion.v2, plan.version)
    }

    @Test
    fun `ICC v4 matrix TRC profile parses correctly`() {
        val plan = GpuIccProfileParsePlan.parse(
            version = GpuIccVersion.v4,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        )
        assertNotNull(plan.matrixTrc)
        assertEquals(GpuIccVersion.v4, plan.version)
    }

    @Test
    fun `ICC v5 profile is refused`() {
        val route = GpuIccProfileParsePlan.parse(
            version = GpuIccVersion.v5,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        ).analyze()
        assertIs<GpuIccProfileRoute.Refused>(route)
        assertEquals("unsupported.color.icc_v5", route.diagnostic.code)
    }

    @Test
    fun `ICC LUT profile is refused`() {
        val route = GpuIccProfileParsePlan.parse(
            version = GpuIccVersion.v2,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = false,
        ).analyze()
        assertIs<GpuIccProfileRoute.Refused>(route)
        assertEquals("unsupported.color.icc_lut", route.diagnostic.code)
    }

    @Test
    fun `ICC cache plan produces deterministic key`() {
        val keyA = GpuIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2, 3))
        val keyB = GpuIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2, 3))
        assertEquals(keyA, keyB)
    }

    @Test
    fun `ICC cache plan produces different keys for different input`() {
        val keyA = GpuIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2))
        val keyB = GpuIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 3))
        assertTrue { keyA != keyB }
    }

    @Test
    fun `ICC tag table has expected required tags`() {
        val tags = sampleTags()
        assertTrue { tags.any { it.signature == "rTRC" } }
        assertTrue { tags.any { it.signature == "gTRC" } }
        assertTrue { tags.any { it.signature == "bTRC" } }
    }

    private fun sampleHeader() = GpuIccHeader(
        profileSize = 1024u,
        preferredCmm = "lcms",
        profileVersion = "4.3.0",
        deviceClass = "mntr",
        colorSpace = "RGB ",
        pcs = "XYZ ",
    )

    private fun sampleTags() = listOf(
        GpuIccTag("rTRC", 256u, 128u),
        GpuIccTag("gTRC", 384u, 128u),
        GpuIccTag("bTRC", 512u, 128u),
        GpuIccTag("rXYZ", 640u, 20u),
        GpuIccTag("gXYZ", 660u, 20u),
        GpuIccTag("bXYZ", 680u, 20u),
    )
}
