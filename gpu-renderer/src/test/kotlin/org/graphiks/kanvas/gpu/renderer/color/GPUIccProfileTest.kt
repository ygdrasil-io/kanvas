package org.graphiks.kanvas.gpu.renderer.color

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUIccProfileTest {

    @Test
    fun `ICC v2 matrix TRC profile parses correctly`() {
        val plan = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v2,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        )
        assertNotNull(plan.matrixTrc)
        assertEquals(GPUIccVersion.v2, plan.version)
    }

    @Test
    fun `ICC v4 matrix TRC profile parses correctly`() {
        val plan = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v4,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        )
        assertNotNull(plan.matrixTrc)
        assertEquals(GPUIccVersion.v4, plan.version)
    }

    @Test
    fun `ICC v5 profile is refused`() {
        val route = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v5,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        ).analyze()
        assertIs<GPUIccProfileRoute.Refused>(route)
        assertEquals("unsupported.color.icc_profile_version", route.diagnostic.code)
    }

    @Test
    fun `ICC LUT profile is refused`() {
        val route = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v2,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = false,
        ).analyze()
        assertIs<GPUIccProfileRoute.Refused>(route)
        assertEquals("unsupported.color.icc_lut_profile", route.diagnostic.code)
    }

    @Test
    fun `ICC malformed profile parse failure is refused`() {
        val plan = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v2,
            header = sampleHeader(),
            tagTable = emptyList(),
            hasMatrixTrc = true,
        )
        val route = plan.analyze()
        assertIs<GPUIccProfileRoute.Refused>(route)
        assertEquals("unsupported.color.icc_parse_failure", route.diagnostic.code)
    }

    @Test
    fun `ICC accepted route carries full cache plan`() {
        val route = GPUIccProfileParsePlan.parse(
            version = GPUIccVersion.v2,
            header = sampleHeader(),
            tagTable = sampleTags(),
            hasMatrixTrc = true,
        ).analyze()
        assertIs<GPUIccProfileRoute.Accepted>(route)
        assertNotNull(route.cache.parsedPlan)
        assertNotNull(route.cache.transformPlan)
    }

    @Test
    fun `ICC cache plan produces deterministic key`() {
        val keyA = GPUIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2, 3))
        val keyB = GPUIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2, 3))
        assertEquals(keyA, keyB)
    }

    @Test
    fun `ICC cache plan produces different keys for different input`() {
        val keyA = GPUIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 2))
        val keyB = GPUIccProfileCachePlan.computeCacheKey(byteArrayOf(0, 1, 3))
        assertTrue { keyA != keyB }
    }

    @Test
    fun `ICC tag table has expected required tags`() {
        val tags = sampleTags()
        assertTrue { tags.any { it.signature == "rTRC" } }
        assertTrue { tags.any { it.signature == "gTRC" } }
        assertTrue { tags.any { it.signature == "bTRC" } }
    }

    private fun sampleHeader() = GPUIccHeader(
        profileSize = 1024u,
        preferredCmm = "lcms",
        profileVersion = "4.3.0",
        deviceClass = "mntr",
        colorSpace = "RGB ",
        pcs = "XYZ ",
    )

    private fun sampleTags() = listOf(
        GPUIccTag("rTRC", 256u, 128u),
        GPUIccTag("gTRC", 384u, 128u),
        GPUIccTag("bTRC", 512u, 128u),
        GPUIccTag("rXYZ", 640u, 20u),
        GPUIccTag("gXYZ", 660u, 20u),
        GPUIccTag("bXYZ", 680u, 20u),
    )
}
