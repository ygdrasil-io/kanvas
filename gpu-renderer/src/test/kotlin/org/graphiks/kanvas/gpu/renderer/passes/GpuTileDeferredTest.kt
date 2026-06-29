package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GpuTileDeferredTest {

    private fun makeDrawInvocation(
        commandId: Int,
        analysisRecordId: String = "analysis-$commandId",
        renderStepIdentity: String = "fill-rect",
        sortKey: Long = commandId.toLong() * 100,
        pipelineKeyHash: String = "pipeline-solid-fill",
        boundsHash: String = "bounds-$commandId",
        scissorBoundsHash: String? = null,
        originalPaintOrder: Int = commandId,
        layerScopeId: String = "root",
    ): GPUDrawInvocation = GPUDrawInvocation(
        commandIdValue = commandId,
        analysisRecordId = analysisRecordId,
        renderStepIndex = 0,
        renderStepId = GPURenderStepID(renderStepIdentity),
        role = "fill",
        layerScopeId = layerScopeId,
        sortKey = sortKey,
        pipelineKeyHash = pipelineKeyHash,
        boundsHash = boundsHash,
        scissorBoundsHash = scissorBoundsHash,
        originalPaintOrder = originalPaintOrder,
    )

    private fun makeDrawPacket(
        packetIdValue: String,
        commandId: Int,
        passId: String = "main-pass",
        sortKey: Long = commandId.toLong() * 100,
        renderStepIdValue: String = "fill-rect",
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID(packetIdValue),
        commandIdValue = commandId,
        analysisRecordId = "analysis-$commandId",
        passId = passId,
        layerId = "root-layer",
        bindingListId = "bindings-$commandId",
        insertionReasonCode = "native-fill-rect",
        sortKey = sortKey,
        sortKeyPreimage = "paint|clip|transform|$commandId",
        renderStepId = GPURenderStepID(renderStepIdValue),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        renderPipelineKey = org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey("render:solid-fill"),
        bindingLayoutHash = "layout-solid-v1",
        vertexSourceLabel = "solid-quad",
        scissorBoundsHash = "scissor-0-0-64-64",
        targetStateHash = "rgba8-premul-msaa1",
        originalPaintOrder = commandId,
        resourceGeneration = 7L,
    )

    private fun bounds(x: Int, y: Int, width: Int, height: Int): GpuTileBounds =
        GpuTileBounds(x = x, y = y, width = width, height = height)

    // -- Tile grid computation --

    @Test
    fun `tile grid 1024x768 subdivides into 4x3 grid with no padding`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)

        assertEquals(256, grid.tileSize)
        assertEquals(1024, grid.targetWidth)
        assertEquals(768, grid.targetHeight)
        assertEquals(4, grid.tileCountX)
        assertEquals(3, grid.tileCountY)
        assertEquals(12, grid.tileCountX * grid.tileCountY)
        assertEquals(0, grid.paddingRight)
        assertEquals(0, grid.paddingBottom)
    }

    @Test
    fun `tile grid 2048x2048 subdivides into 8x8 grid`() {
        val grid = computeTileGrid(targetWidth = 2048, targetHeight = 2048, tileSize = 256)

        assertEquals(8, grid.tileCountX)
        assertEquals(8, grid.tileCountY)
        assertEquals(64, grid.tileCountX * grid.tileCountY)
    }

    @Test
    fun `tile grid non-power-of-two target has correct padding`() {
        val grid = computeTileGrid(targetWidth = 1000, targetHeight = 750, tileSize = 256)

        assertEquals(4, grid.tileCountX)
        assertEquals(3, grid.tileCountY)
        assertEquals(24, grid.paddingRight)
        assertEquals(18, grid.paddingBottom)
    }

    @Test
    fun `tile grid with target smaller than tile size produces single tile with padding`() {
        val grid = computeTileGrid(targetWidth = 128, targetHeight = 64, tileSize = 256)

        assertEquals(1, grid.tileCountX)
        assertEquals(1, grid.tileCountY)
        assertEquals(128, grid.paddingRight)
        assertEquals(192, grid.paddingBottom)
    }

    @Test
    fun `tile grid computes per-tile rect correctly`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)

        val tileRect = tileRectForIndex(grid, tileIndexX = 0, tileIndexY = 0)
        assertEquals(0, tileRect.x)
        assertEquals(0, tileRect.y)
        assertEquals(256, tileRect.width)
        assertEquals(256, tileRect.height)

        val lastTile = tileRectForIndex(grid, tileIndexX = 3, tileIndexY = 2)
        assertEquals(768, lastTile.x)
        assertEquals(512, lastTile.y)
        assertEquals(256, lastTile.width)
        assertEquals(256, lastTile.height)
    }

    // -- Empty-tile culling --

    @Test
    fun `empty tiles are detected and culled`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(128, 128, 256, 256),
            2 to bounds(640, 128, 256, 256),
            3 to bounds(128, 512, 256, 256),
        )

        val draws = drawBoundsMap.map { (commandId, _) -> makeDrawInvocation(commandId) }

        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        assertEquals(12, bins.size)
        val nonEmptyCount = bins.count { bin -> bin.drawInvocations.isNotEmpty() }
        assertTrue(nonEmptyCount < 12, "expected some tiles to be empty")
        assertTrue(nonEmptyCount > 0)

        val emptyBins = bins.filter { bin -> bin.drawInvocations.isEmpty() }
        assertTrue(emptyBins.isNotEmpty())
    }

    @Test
    fun `full target draw is binned to every tile`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val fullTargetDraw = makeDrawInvocation(commandId = 1)
        val drawBoundsMap = mapOf(1 to bounds(0, 0, 1024, 768))

        val bins = binDrawsToTiles(grid, listOf(fullTargetDraw), drawBoundsMap)

        assertEquals(12, bins.size)
        assertTrue(bins.all { bin -> bin.drawInvocations.isNotEmpty() })
        assertTrue(bins.all { bin -> bin.drawInvocations.any { inv -> inv.commandIdValue == 1 } })
    }

    // -- Tile binning --

    @Test
    fun `draws are binned to correct tiles based on bounding box`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 256, 256),
            2 to bounds(512, 256, 256, 256),
            3 to bounds(768, 512, 256, 256),
        )

        val draws = drawBoundsMap.map { (commandId, _) -> makeDrawInvocation(commandId) }

        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val tile00 = bins.first { bin -> bin.tileIndexX == 0 && bin.tileIndexY == 0 }
        assertTrue(tile00.drawInvocations.any { inv -> inv.commandIdValue == 1 })

        val tile21 = bins.first { bin -> bin.tileIndexX == 2 && bin.tileIndexY == 1 }
        assertTrue(tile21.drawInvocations.any { inv -> inv.commandIdValue == 2 })

        val tile32 = bins.first { bin -> bin.tileIndexX == 3 && bin.tileIndexY == 2 }
        assertTrue(tile32.drawInvocations.any { inv -> inv.commandIdValue == 3 })
    }

    @Test
    fun `draw straddling tile boundaries is binned to all intersecting tiles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(128, 128, 256, 256),
        )

        val draws = listOf(makeDrawInvocation(commandId = 1))

        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val coveredBins = bins.filter { bin -> bin.drawInvocations.isNotEmpty() }
        assertEquals(4, coveredBins.size)
        val expectedTileIndices = setOf(
            0 to 0, 1 to 0, 0 to 1, 1 to 1,
        )
        val actualTileIndices = coveredBins.map { bin -> bin.tileIndexX to bin.tileIndexY }.toSet()
        assertEquals(expectedTileIndices, actualTileIndices)
    }

    // -- Memory budget --

    @Test
    fun `memory budget accepts when within limit`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val budget = GpuTileMemoryBudget(
            perTileBytes = 256L * 256 * 4,
            maxConcurrentTiles = 12,
        )

        val result = checkTileMemoryBudget(grid, budget)
        assertIs<GpuTileDeferredResult.Accepted>(result)
    }

    @Test
    fun `memory budget refuses when per-tile bytes exceed cap`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val budget = GpuTileMemoryBudget(
            perTileBytes = 256L * 256 * 4,
            maxConcurrentTiles = 12,
        )

        val result = checkTileMemoryBudget(
            grid,
            budget,
            totalAdapterTextureMemoryBytes = 100L * 1024,
        )

        assertIs<GpuTileDeferredResult.Refused>(result)
        assertEquals("unsupported.tile.budget_exceeded", result.diagnostic.code)
        assertTrue(result.diagnostic.terminal)
    }

    @Test
    fun `budget enforces adapter texture memory fraction`() {
        val budget = GpuTileMemoryBudget(
            perTileBytes = 256L * 256 * 4,
            maxConcurrentTiles = 12,
            adapterTextureMemoryFraction = 0.25f,
        )

        assertEquals(0.25f, budget.adapterTextureMemoryFraction)
        assertEquals(256L * 256 * 4, budget.perTileBytes)
        assertEquals(12, budget.maxConcurrentTiles)
    }

    // -- Tile passes and composite --

    @Test
    fun `tile passes are built for non-empty tiles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 256, 256),
            2 to bounds(512, 256, 256, 256),
        )
        val draws = drawBoundsMap.map { (commandId, _) -> makeDrawInvocation(commandId) }
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val packetsByCommandId: Map<Int, List<GPUDrawPacket>> = mapOf(
            1 to listOf(makeDrawPacket("pkt-1", commandId = 1)),
            2 to listOf(makeDrawPacket("pkt-2", commandId = 2)),
        )

        val tilePasses = buildTilePasses(bins, packetsByCommandId)
        val nonEmptyCount = bins.count { bin -> bin.drawInvocations.isNotEmpty() }

        assertEquals(nonEmptyCount, tilePasses.size)
        for (pass in tilePasses) {
            assertFalse(pass.scissor.isBlank())
            assertTrue(pass.sortedPackets.isNotEmpty())
        }
    }

    @Test
    fun `composite pass merges tile results with pixel-exact parity expectation`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 256, 256),
            2 to bounds(512, 256, 256, 256),
        )
        val draws = drawBoundsMap.map { (commandId, _) -> makeDrawInvocation(commandId) }
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)
        val packetsByCommandId: Map<Int, List<GPUDrawPacket>> = mapOf(
            1 to listOf(makeDrawPacket("pkt-1", commandId = 1)),
            2 to listOf(makeDrawPacket("pkt-2", commandId = 2)),
        )
        val tilePasses = buildTilePasses(bins, packetsByCommandId)

        val composite = buildCompositePass(tilePasses, mergeMode = "blit-merge")

        assertEquals(tilePasses.size, composite.sourceTiles.size)
        assertEquals("blit-merge", composite.mergeMode)
    }

    // -- Grid policy --

    @Test
    fun `grid policy activation decision when tile count meets minimum`() {
        val policy = GpuTileGridPolicy(
            adapterPreferredTileSize = 256,
            maxMemoryPerTile = 256L * 256 * 4,
            minTileCount = 2,
        )

        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)

        assertTrue(grid.tileCountX * grid.tileCountY >= policy.minTileCount)
        assertEquals(12, grid.tileCountX * grid.tileCountY)
    }

    @Test
    fun `dump lines produce deterministic evidence without backend handles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val lines = grid.dumpLines()

        assertContains(lines.first(), "passes.tile-grid tileSize=256 target=1024x768")
        assertContains(lines.first(), "tilesX=4 tilesY=3")
        assertContains(lines.first(), "padding=right:0 bottom:0")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.any { line -> line.contains("backend") && line.contains("handle") })
    }

    @Test
    fun `tile grid plan validates positive dimensions`() {
        assertIllegalArgument("targetWidth must be positive") {
            computeTileGrid(targetWidth = 0, targetHeight = 768, tileSize = 256)
        }
        assertIllegalArgument("targetHeight must be positive") {
            computeTileGrid(targetWidth = 1024, targetHeight = 0, tileSize = 256)
        }
        assertIllegalArgument("tileSize must be positive") {
            computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 0)
        }
    }

    @Test
    fun `grid policy validates positive values`() {
        assertIllegalArgument("adapterPreferredTileSize must be positive") {
            GpuTileGridPolicy(
                adapterPreferredTileSize = 0,
                maxMemoryPerTile = 1024,
                minTileCount = 1,
            )
        }
        assertIllegalArgument("maxMemoryPerTile must be positive") {
            GpuTileGridPolicy(
                adapterPreferredTileSize = 256,
                maxMemoryPerTile = 0,
                minTileCount = 1,
            )
        }
        assertIllegalArgument("minTileCount must be positive") {
            GpuTileGridPolicy(
                adapterPreferredTileSize = 256,
                maxMemoryPerTile = 1024,
                minTileCount = 0,
            )
        }
    }

    @Test
    fun `strategy DirectTargetSlice and TileIntermediateTexture serialize correctly`() {
        val direct = GpuTileStrategy.DirectTargetSlice
        assertIs<GpuTileStrategy.DirectTargetSlice>(direct)

        val intermediate = GpuTileStrategy.TileIntermediateTexture(
            intermediate = "tex-tile-0-0",
            compositePlan = GpuTileCompositePass(
                sourceTiles = emptyList(),
                mergeMode = "blit-merge",
            ),
        )
        assertEquals("tex-tile-0-0", intermediate.intermediate)
        assertEquals("blit-merge", intermediate.compositePlan.mergeMode)
    }

    @Test
    fun `memory budget validates positive values`() {
        assertIllegalArgument("perTileBytes must be positive") {
            GpuTileMemoryBudget(perTileBytes = 0, maxConcurrentTiles = 1)
        }
        assertIllegalArgument("maxConcurrentTiles must be positive") {
            GpuTileMemoryBudget(perTileBytes = 1024, maxConcurrentTiles = 0)
        }
    }

    @Test
    fun `composite pass preserves source tiles in order`() {
        val grid = computeTileGrid(targetWidth = 512, targetHeight = 512, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 256, 256),
            2 to bounds(256, 0, 256, 256),
            3 to bounds(0, 256, 256, 256),
            4 to bounds(256, 256, 256, 256),
        )
        val draws = drawBoundsMap.map { (commandId, _) -> makeDrawInvocation(commandId) }
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)
        val packetsByCommandId: Map<Int, List<GPUDrawPacket>> = (1..4).associateWith { id ->
            listOf(makeDrawPacket("pkt-$id", commandId = id))
        }
        val tilePasses = buildTilePasses(bins, packetsByCommandId)

        val composite = buildCompositePass(tilePasses, mergeMode = "blit-merge")

        assertEquals(tilePasses.size, composite.sourceTiles.size)
        val tilePositions = composite.sourceTiles.map { pass ->
            pass.tile.tileIndexX to pass.tile.tileIndexY
        }
        assertEquals(4, tilePositions.size)
        assertTrue(tilePositions.all { pos -> bins.any { bin -> bin.tileIndexX == pos.first && bin.tileIndexY == pos.second } })
    }

    @Test
    fun `deferred result Accepted carries tile passes and strategy`() {
        val grid = computeTileGrid(targetWidth = 256, targetHeight = 256, tileSize = 256)
        val drawBoundsMap = mapOf(1 to bounds(0, 0, 128, 128))
        val draws = listOf(makeDrawInvocation(commandId = 1))
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)
        val packetsByCommandId: Map<Int, List<GPUDrawPacket>> = mapOf(
            1 to listOf(makeDrawPacket("pkt-1", commandId = 1)),
        )
        val tilePasses = buildTilePasses(bins, packetsByCommandId)

        val result = GpuTileDeferredResult.Accepted(
            tilePasses = tilePasses,
            strategy = GpuTileStrategy.DirectTargetSlice,
        )

        assertIs<GpuTileDeferredResult.Accepted>(result)
        assertEquals(1, result.tilePasses.size)
        assertEquals(GpuTileStrategy.DirectTargetSlice, result.strategy)
        assertTrue(result.dumpLines().isNotEmpty())
        assertContains(result.dumpLines().first(), "tile-deferred.accepted")
    }

    @Test
    fun `deferred result Refused carries diagnostic`() {
        val diagnostic = RefuseDiagnostic(
            code = "unsupported.tile.budget_exceeded",
            message = "Per-tile budget exceeded available memory",
            stage = "tile.budget",
            terminal = true,
        )
        val result = GpuTileDeferredResult.Refused(diagnostic)

        assertIs<GpuTileDeferredResult.Refused>(result)
        assertEquals("unsupported.tile.budget_exceeded", result.diagnostic.code)
        assertEquals("tile.budget", result.diagnostic.stage)
        assertTrue(result.diagnostic.terminal)
        assertContains(result.dumpLines().first(), "tile-deferred.refused")
        assertContains(result.dumpLines().first(), "code=unsupported.tile.budget_exceeded")
    }

    @Test
    fun `cross-tile destination read diagnostic uses correct code`() {
        val diagnostic = RefuseDiagnostic(
            code = "unsupported.tile.cross_tile_destination_read",
            message = "Cross-tile destination read refused; deferred to composite pass",
            stage = "tile.binning",
            terminal = true,
        )
        assertEquals("unsupported.tile.cross_tile_destination_read", diagnostic.code)
        assertTrue(diagnostic.message.contains("Cross-tile"))
    }

    @Test
    fun `cross-tile clip atomic group diagnostic uses correct code`() {
        val diagnostic = RefuseDiagnostic(
            code = "unsupported.tile.cross_tile_clip_atomic_group",
            message = "Cross-tile clip atomic group refused at binning",
            stage = "tile.binning",
            terminal = true,
        )
        assertEquals("unsupported.tile.cross_tile_clip_atomic_group", diagnostic.code)
        assertTrue(diagnostic.message.contains("clip atomic"))
    }

    @Test
    fun `empty draw list produces all-empty bins`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val bins = binDrawsToTiles(grid, emptyList(), emptyMap())

        assertEquals(12, bins.size)
        assertTrue(bins.all { bin -> bin.drawInvocations.isEmpty() })
    }

    @Test
    fun `cross-tile dst read refusal returned for draw spanning multiple tiles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)

        val result = GpuTileDeferredResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.tile.cross_tile_destination_read",
                message = "Cross-tile destination read refused for draw 1; spans tiles [0,0][1,0][0,1][1,1]",
                stage = "tile.binning",
                terminal = true,
            ),
        )

        assertContains(result.diagnostic.message, "Cross-tile destination read")
        assertContains(result.diagnostic.message, "spans tiles")
    }

    @Test
    fun `tile pass dump lines include tile indices and packet count`() {
        val grid = computeTileGrid(targetWidth = 256, targetHeight = 256, tileSize = 256)
        val drawBoundsMap = mapOf(1 to bounds(0, 0, 128, 128))
        val draws = listOf(makeDrawInvocation(commandId = 1))
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)
        val packetsByCommandId: Map<Int, List<GPUDrawPacket>> = mapOf(
            1 to listOf(makeDrawPacket("pkt-1", commandId = 1)),
        )
        val tilePasses = buildTilePasses(bins, packetsByCommandId)

        val pass = tilePasses.first()
        val lines = pass.dumpLines()
        assertContains(lines.first(), "passes.tile-pass indexX=0 indexY=0")
        assertContains(lines.first(), "packets=1")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
    }

    // -- Cross-tile destination read / clip atomic group refusals (production behavior) --

    @Test
    fun `reason constants match spec refusal codes exactly`() {
        assertEquals("unsupported.tile.budget_exceeded", GpuTileDeferredReason.BUDGET_EXCEEDED)
        assertEquals(
            "unsupported.tile.cross_tile_destination_read",
            GpuTileDeferredReason.CROSS_TILE_DESTINATION_READ,
        )
        assertEquals(
            "unsupported.tile.cross_tile_clip_atomic_group",
            GpuTileDeferredReason.CROSS_TILE_CLIP_ATOMIC_GROUP,
        )
    }

    @Test
    fun `cross-tile destination read refused when dst-reading draw spans multiple tiles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(1 to bounds(128, 128, 256, 256))
        val draws = listOf(makeDrawInvocation(commandId = 1))
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val result = checkCrossTileDestinationRead(bins, destinationReadingCommandIds = setOf(1))

        assertIs<GpuTileDeferredResult.Refused>(result)
        assertEquals(GpuTileDeferredReason.CROSS_TILE_DESTINATION_READ, result.diagnostic.code)
        assertEquals("tile.binning", result.diagnostic.stage)
        assertTrue(result.diagnostic.terminal)
        assertContains(result.diagnostic.message, "deferred to composite")
    }

    @Test
    fun `dst read within single tile accepted`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(1 to bounds(0, 0, 128, 128))
        val draws = listOf(makeDrawInvocation(commandId = 1))
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val result = checkCrossTileDestinationRead(bins, destinationReadingCommandIds = setOf(1))

        assertIs<GpuTileDeferredResult.Accepted>(result)
    }

    @Test
    fun `cross-tile clip atomic group refused at binning when group spans tiles`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 128, 128),
            2 to bounds(512, 256, 128, 128),
        )
        val draws = drawBoundsMap.keys.map { makeDrawInvocation(it) }
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val result = checkCrossTileClipAtomicGroup(
            bins,
            clipAtomicGroupByCommandId = mapOf(1 to "clip-group-A", 2 to "clip-group-A"),
        )

        assertIs<GpuTileDeferredResult.Refused>(result)
        assertEquals(GpuTileDeferredReason.CROSS_TILE_CLIP_ATOMIC_GROUP, result.diagnostic.code)
        assertEquals("tile.binning", result.diagnostic.stage)
        assertTrue(result.diagnostic.terminal)
        assertContains(result.diagnostic.message, "clip atomic")
    }

    @Test
    fun `clip atomic group confined to single tile accepted`() {
        val grid = computeTileGrid(targetWidth = 1024, targetHeight = 768, tileSize = 256)
        val drawBoundsMap = mapOf(
            1 to bounds(0, 0, 64, 64),
            2 to bounds(64, 64, 64, 64),
        )
        val draws = drawBoundsMap.keys.map { makeDrawInvocation(it) }
        val bins = binDrawsToTiles(grid, draws, drawBoundsMap)

        val result = checkCrossTileClipAtomicGroup(
            bins,
            clipAtomicGroupByCommandId = mapOf(1 to "clip-group-A", 2 to "clip-group-A"),
        )

        assertIs<GpuTileDeferredResult.Accepted>(result)
    }

    private fun assertIllegalArgument(expectedMessageFragment: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException with message containing: $expectedMessageFragment")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", expectedMessageFragment)
        }
    }
}
