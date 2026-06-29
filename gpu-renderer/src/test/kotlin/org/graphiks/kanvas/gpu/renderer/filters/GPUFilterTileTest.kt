package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUFilterTileTest {

    @Test
    fun `tile grid for 4096x4096 source with 1024x1024 tiles and overlap 60 produces 25 tiles`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 4096,
            sourceHeight = 4096,
            tileWidth = 1024,
            tileHeight = 1024,
            overlap = 60,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted
        val plan = accepted.plan
        assertEquals(5, plan.columns)
        assertEquals(5, plan.rows)
        assertEquals(25, accepted.renderPlans.size)
        assertEquals(4096, plan.sourceWidth)
        assertEquals(4096, plan.sourceHeight)
        assertEquals(1024, plan.tileWidth)
        assertEquals(1024, plan.tileHeight)
        assertEquals(60, plan.overlap)
    }

    @Test
    fun `refusal when tile interior is zero or negative`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 1024,
            sourceHeight = 1024,
            tileWidth = 100,
            tileHeight = 100,
            overlap = 60,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Refused)
        val refused = result as GPUFilterTileEvaluationResult.Refused
        assertEquals("unsupported.filter.tile_smaller_than_kernel", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `tile regions for 100x100 source with 50x50 tiles and overlap 10`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 100,
            sourceHeight = 100,
            tileWidth = 50,
            tileHeight = 50,
            overlap = 10,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted

        val interior = 50 - 2 * 10
        assertEquals(30, interior)
        assertEquals(4, accepted.plan.columns)
        assertEquals(4, accepted.plan.rows)
        assertEquals(16, accepted.renderPlans.size)

        // Tile (0, 0): top-left corner
        val firstTile = accepted.renderPlans.first()
        assertEquals(0, firstTile.tileIndex.column)
        assertEquals(0, firstTile.tileIndex.row)

        // Source region: interior (0,0,30,30) expanded by 10 → (-10,-10,50,50) clamped to (0,0,50,50)
        val srcReg = firstTile.sourceRegion
        assertEquals(0, srcReg.x)
        assertEquals(0, srcReg.y)
        assertEquals(50, srcReg.width)
        assertEquals(50, srcReg.height)

        // Target region: interior (0,0,30,30)
        val tgtReg = firstTile.targetRegion
        assertEquals(0, tgtReg.x)
        assertEquals(0, tgtReg.y)
        assertEquals(30, tgtReg.width)
        assertEquals(30, tgtReg.height)

        // Tile (3, 3): bottom-right corner
        val lastTile = accepted.renderPlans.last()
        assertEquals(3, lastTile.tileIndex.column)
        assertEquals(3, lastTile.tileIndex.row)

        // interior starts at (3*30, 3*30) = (90, 90)
        // source region: (90-10, 90-10, 30+20, 30+20) = (80, 80, 50, 50), but clamped: (80, 80, 20, 20)
        val lastSrc = lastTile.sourceRegion
        assertEquals(80, lastSrc.x)
        assertEquals(80, lastSrc.y)
        assertEquals(20, lastSrc.width)
        assertEquals(20, lastSrc.height)

        // target region: interior (90, 90, min(30, 100-90)=10, min(30, 100-90)=10)
        val lastTgt = lastTile.targetRegion
        assertEquals(90, lastTgt.x)
        assertEquals(90, lastTgt.y)
        assertEquals(10, lastTgt.width)
        assertEquals(10, lastTgt.height)
    }

    @Test
    fun `single tile covers full source produces degenerate case with one tile`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 500,
            sourceHeight = 400,
            tileWidth = 600,
            tileHeight = 500,
            overlap = 10,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted
        assertEquals(1, accepted.plan.columns)
        assertEquals(1, accepted.plan.rows)
        assertEquals(1, accepted.renderPlans.size)

        // Source region covers full source (clamped)
        val srcReg = accepted.renderPlans.first().sourceRegion
        assertEquals(0, srcReg.x)
        assertEquals(0, srcReg.y)
        assertEquals(500, srcReg.width)
        assertEquals(400, srcReg.height)

        // Target region covers full source (interior clamped)
        val tgtReg = accepted.renderPlans.first().targetRegion
        assertEquals(0, tgtReg.x)
        assertEquals(0, tgtReg.y)
        assertEquals(500, tgtReg.width)
        assertEquals(400, tgtReg.height)
    }

    @Test
    fun `per-tile intermediate byte estimate does not exceed budget`() {
        val budget = GPUFilterTileBudgetPolicy(
            maxIntermediateBytes = 1024L * 1024L * 4L, // 4 MB
            maxActiveTileCount = 64,
        )
        val planner = GPUFilterTilePlanner(
            budgetPolicy = budget,
            bytesPerPixel = 4L,
        )
        val result = planner.evaluate(
            sourceWidth = 4096,
            sourceHeight = 4096,
            tileWidth = 1024,
            tileHeight = 1024,
            overlap = 60,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted

        val expectedTileBytes = 1024L * 1024L * 4L
        val maxAllowed = budget.maxIntermediateBytes
        assertTrue(expectedTileBytes <= maxAllowed, "Tile bytes $expectedTileBytes exceeds budget $maxAllowed")

        // Check each render plan's intermediate byte estimate
        for (plan in accepted.renderPlans) {
            assertTrue(plan.intermediateByteEstimate <= maxAllowed)
            assertEquals(expectedTileBytes, plan.intermediateByteEstimate)
        }
    }

    @Test
    fun `refusal when per-tile intermediate exceeds budget`() {
        val budget = GPUFilterTileBudgetPolicy(
            maxIntermediateBytes = 512L * 1024L, // 512 KB
            maxActiveTileCount = 64,
        )
        val planner = GPUFilterTilePlanner(
            budgetPolicy = budget,
            bytesPerPixel = 4L,
        )
        val result = planner.evaluate(
            sourceWidth = 4096,
            sourceHeight = 4096,
            tileWidth = 1024,
            tileHeight = 1024,
            overlap = 60,
        )
        // 1024 * 1024 * 4 = 4 MB > 512 KB → refusal
        assertTrue(result is GPUFilterTileEvaluationResult.Refused)
        val refused = result as GPUFilterTileEvaluationResult.Refused
        assertEquals("unsupported.filter.tile_intermediate_memory_budget", refused.diagnostic.code)
    }

    @Test
    fun `refusal when tile count exceeds max active tile budget`() {
        val budget = GPUFilterTileBudgetPolicy(
            maxIntermediateBytes = 16L * 1024L * 1024L,
            maxActiveTileCount = 10,
        )
        val planner = GPUFilterTilePlanner(budgetPolicy = budget)
        val result = planner.evaluate(
            sourceWidth = 4096,
            sourceHeight = 4096,
            tileWidth = 256,
            tileHeight = 256,
            overlap = 16,
        )
        // interior = 256 - 32 = 224, ceil(4096/224) = 19 columns, 19 rows = 361 tiles > 10
        assertTrue(result is GPUFilterTileEvaluationResult.Refused)
        val refused = result as GPUFilterTileEvaluationResult.Refused
        assertEquals("unsupported.filter.tile_count_exceeds_budget", refused.diagnostic.code)
    }

    @Test
    fun `overlap zero produces valid plan with no overlap`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 100,
            sourceHeight = 100,
            tileWidth = 50,
            tileHeight = 50,
            overlap = 0,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted
        assertEquals(2, accepted.plan.columns)
        assertEquals(2, accepted.plan.rows)
        assertEquals(4, accepted.renderPlans.size)

        // With zero overlap, source == target for each tile
        for (plan in accepted.renderPlans) {
            assertEquals(plan.sourceRegion, plan.targetRegion)
        }
    }

    @Test
    fun `tile indices are sequential and cover full grid`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 200,
            sourceHeight = 200,
            tileWidth = 100,
            tileHeight = 100,
            overlap = 20,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Accepted)
        val accepted = result as GPUFilterTileEvaluationResult.Accepted
        val interior = 100 - 2 * 20 // 60
        val expectedCols = (200 + interior - 1) / interior // ceil(200/60) = 4
        val expectedRows = (200 + interior - 1) / interior

        val indices = accepted.renderPlans.map { it.tileIndex }.toSet()
        assertEquals(expectedCols * expectedRows, indices.size)

        for (col in 0 until expectedCols) {
            for (row in 0 until expectedRows) {
                assertTrue(
                    GPUFilterTileIndex(col, row) in indices,
                    "Missing tile index ($col, $row)",
                )
            }
        }
    }

    @Test
    fun `zero or negative source dimensions produce refusal`() {
        val planner = GPUFilterTilePlanner()
        val result = planner.evaluate(
            sourceWidth = 0,
            sourceHeight = 100,
            tileWidth = 100,
            tileHeight = 100,
            overlap = 10,
        )
        assertTrue(result is GPUFilterTileEvaluationResult.Refused)
        val refused = result as GPUFilterTileEvaluationResult.Refused
        assertTrue(refused.diagnostic.code in setOf(
            "unsupported.filter.tile_source_invalid",
            "unsupported.filter.tile_smaller_than_kernel",
        ))
    }
}
