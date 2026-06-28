package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

data class GpuTileGridPlan(
    val tileSize: Int = 256,
    val targetWidth: Int,
    val targetHeight: Int,
    val tileCountX: Int,
    val tileCountY: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
) {
    init {
        require(targetWidth > 0) { "targetWidth must be positive" }
        require(targetHeight > 0) { "targetHeight must be positive" }
        require(tileSize > 0) { "tileSize must be positive" }
        require(tileCountX > 0) { "tileCountX must be positive" }
        require(tileCountY > 0) { "tileCountY must be positive" }
        require(paddingRight >= 0) { "paddingRight must be non-negative" }
        require(paddingBottom >= 0) { "paddingBottom must be non-negative" }
    }

    val totalTiles: Int get() = tileCountX * tileCountY

    fun dumpLines(): List<String> = listOf(
        "passes.tile-grid tileSize=$tileSize target=${targetWidth}x${targetHeight} " +
            "tilesX=$tileCountX tilesY=$tileCountY " +
            "padding=right:$paddingRight bottom:$paddingBottom",
    )
}

data class GpuTileGridPolicy(
    val adapterPreferredTileSize: Int,
    val maxMemoryPerTile: Long,
    val minTileCount: Int,
) {
    init {
        require(adapterPreferredTileSize > 0) { "adapterPreferredTileSize must be positive" }
        require(maxMemoryPerTile > 0) { "maxMemoryPerTile must be positive" }
        require(minTileCount > 0) { "minTileCount must be positive" }
    }
}

data class GpuTileBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0) { "x must be non-negative" }
        require(y >= 0) { "y must be non-negative" }
        require(width >= 0) { "width must be non-negative" }
        require(height >= 0) { "height must be non-negative" }
    }

    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun intersects(other: GpuTileBounds): Boolean {
        return left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top
    }
}

data class GpuTileBin(
    val tileIndexX: Int,
    val tileIndexY: Int,
    val drawInvocations: List<GPUDrawInvocation>,
) {
    init {
        require(tileIndexX >= 0) { "tileIndexX must be non-negative" }
        require(tileIndexY >= 0) { "tileIndexY must be non-negative" }
    }

    val isEmpty: Boolean get() = drawInvocations.isEmpty()
}

data class GpuTileBinningPass(
    val sourcePass: String,
    val bins: List<GpuTileBin>,
) {
    init {
        require(sourcePass.isNotBlank()) { "sourcePass must not be blank" }
        require(bins.isNotEmpty()) { "bins must not be empty" }
    }

    val nonEmptyBinCount: Int get() = bins.count { bin -> !bin.isEmpty }
}

data class GpuTilePass(
    val tile: GpuTileBin,
    val scissor: String,
    val sortedPackets: List<GPUDrawPacket>,
    val intermediates: List<String> = emptyList(),
) {
    init {
        require(scissor.isNotBlank()) { "scissor must not be blank" }
        require(sortedPackets.isNotEmpty()) { "sortedPackets must not be empty" }
    }

    fun dumpLines(): List<String> = listOf(
        "passes.tile-pass indexX=${tile.tileIndexX} indexY=${tile.tileIndexY} " +
            "scissor=$scissor " +
            "packets=${sortedPackets.size} " +
            "intermediates=${if (intermediates.isEmpty()) NONE_DUMP_VALUE else intermediates.joinToString(",")}",
    )
}

sealed class GpuTileStrategy {
    object DirectTargetSlice : GpuTileStrategy()
    data class TileIntermediateTexture(
        val intermediate: String,
        val compositePlan: GpuTileCompositePass,
    ) : GpuTileStrategy()
}

data class GpuTileCompositePass(
    val sourceTiles: List<GpuTilePass>,
    val mergeMode: String,
) {
    init {
        require(mergeMode.isNotBlank()) { "mergeMode must not be blank" }
    }
}

data class GpuTileMemoryBudget(
    val perTileBytes: Long,
    val maxConcurrentTiles: Int,
    val adapterTextureMemoryFraction: Float = 0.25f,
) {
    init {
        require(perTileBytes > 0) { "perTileBytes must be positive" }
        require(maxConcurrentTiles > 0) { "maxConcurrentTiles must be positive" }
        require(adapterTextureMemoryFraction in 0.0f..1.0f) {
            "adapterTextureMemoryFraction must be in [0.0, 1.0]"
        }
    }
}

sealed interface GpuTileDeferredResult {
    data class Accepted(
        val tilePasses: List<GpuTilePass>,
        val strategy: GpuTileStrategy,
    ) : GpuTileDeferredResult {
        fun dumpLines(): List<String> = listOf(
            "tile-deferred.accepted tiles=${tilePasses.size} " +
                "strategy=${strategy.dumpLabel()}",
        ) + tilePasses.flatMap { pass -> pass.dumpLines() }
    }

    data class Refused(val diagnostic: RefuseDiagnostic) : GpuTileDeferredResult {
        fun dumpLines(): List<String> = listOf(
            "tile-deferred.refused code=${diagnostic.code} " +
                "stage=${diagnostic.stage} " +
                "terminal=${diagnostic.terminal} " +
                "message=${diagnostic.message}",
        )
    }
}

fun computeTileGrid(
    targetWidth: Int,
    targetHeight: Int,
    tileSize: Int = 256,
): GpuTileGridPlan {
    require(targetWidth > 0) { "targetWidth must be positive" }
    require(targetHeight > 0) { "targetHeight must be positive" }
    require(tileSize > 0) { "tileSize must be positive" }

    val tileCountX = (targetWidth + tileSize - 1) / tileSize
    val tileCountY = (targetHeight + tileSize - 1) / tileSize
    val paddedWidth = tileCountX * tileSize
    val paddedHeight = tileCountY * tileSize

    return GpuTileGridPlan(
        tileSize = tileSize,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        tileCountX = tileCountX,
        tileCountY = tileCountY,
        paddingRight = paddedWidth - targetWidth,
        paddingBottom = paddedHeight - targetHeight,
    )
}

fun tileRectForIndex(
    grid: GpuTileGridPlan,
    tileIndexX: Int,
    tileIndexY: Int,
): GpuTileBounds {
    require(tileIndexX in 0 until grid.tileCountX) {
        "tileIndexX $tileIndexX out of range [0, ${grid.tileCountX})"
    }
    require(tileIndexY in 0 until grid.tileCountY) {
        "tileIndexY $tileIndexY out of range [0, ${grid.tileCountY})"
    }

    val x = tileIndexX * grid.tileSize
    val y = tileIndexY * grid.tileSize

    val tileWidth: Int
    if (tileIndexX == grid.tileCountX - 1 && grid.paddingRight > 0) {
        tileWidth = grid.tileSize - grid.paddingRight
    } else {
        tileWidth = grid.tileSize
    }

    val tileHeight: Int
    if (tileIndexY == grid.tileCountY - 1 && grid.paddingBottom > 0) {
        tileHeight = grid.tileSize - grid.paddingBottom
    } else {
        tileHeight = grid.tileSize
    }

    return GpuTileBounds(x = x, y = y, width = tileWidth, height = tileHeight)
}

fun binDrawsToTiles(
    grid: GpuTileGridPlan,
    draws: List<GPUDrawInvocation>,
    drawBounds: Map<Int, GpuTileBounds>,
): List<GpuTileBin> {
    val bins = mutableListOf<GpuTileBin>()

    for (ty in 0 until grid.tileCountY) {
        for (tx in 0 until grid.tileCountX) {
            val tileRect = tileRectForIndex(grid, tx, ty)
            val intersectingDraws = draws.filter { invocation ->
                val bounds = drawBounds[invocation.commandIdValue]
                bounds != null && tileRect.intersects(bounds)
            }
            bins.add(
                GpuTileBin(
                    tileIndexX = tx,
                    tileIndexY = ty,
                    drawInvocations = intersectingDraws,
                )
            )
        }
    }

    return bins
}

fun buildTilePasses(
    bins: List<GpuTileBin>,
    packetsByCommandId: Map<Int, List<GPUDrawPacket>>,
): List<GpuTilePass> {
    return bins.filter { bin -> !bin.isEmpty }.map { bin ->
        val packets = bin.drawInvocations.flatMap { invocation ->
            packetsByCommandId[invocation.commandIdValue] ?: emptyList()
        }
        require(packets.isNotEmpty()) {
            "Tile [${bin.tileIndexX},${bin.tileIndexY}] has draws but no resolved packets"
        }
        val scissor = "tile-scissor:${bin.tileIndexX}x${bin.tileIndexY}"
        GpuTilePass(
            tile = bin,
            scissor = scissor,
            sortedPackets = packets,
        )
    }
}

fun buildCompositePass(
    tilePasses: List<GpuTilePass>,
    mergeMode: String,
): GpuTileCompositePass {
    return GpuTileCompositePass(
        sourceTiles = tilePasses,
        mergeMode = mergeMode,
    )
}

fun checkTileMemoryBudget(
    grid: GpuTileGridPlan,
    budget: GpuTileMemoryBudget,
    bytesPerPixel: Int = 4,
    totalAdapterTextureMemoryBytes: Long = Long.MAX_VALUE,
): GpuTileDeferredResult {
    val tilePixelCount = grid.tileSize.toLong() * grid.tileSize.toLong()
    val actualPerTileBytes = tilePixelCount * bytesPerPixel

    val maxAllowedPerTile = (totalAdapterTextureMemoryBytes * budget.adapterTextureMemoryFraction).toLong()

    if (actualPerTileBytes > maxAllowedPerTile) {
        return GpuTileDeferredResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.tile.budget_exceeded",
                message = "Per-tile budget exceeded: $actualPerTileBytes bytes needed, " +
                    "$maxAllowedPerTile bytes available (${budget.adapterTextureMemoryFraction * 100}% of adapter texture memory)",
                stage = "tile.budget",
                terminal = true,
            )
        )
    }

    if (actualPerTileBytes > budget.perTileBytes) {
        return GpuTileDeferredResult.Refused(
            RefuseDiagnostic(
                code = "unsupported.tile.budget_exceeded",
                message = "Per-tile budget exceeded: $actualPerTileBytes bytes needed, " +
                    "${budget.perTileBytes} limit",
                stage = "tile.budget",
                terminal = true,
            )
        )
    }

    val maxConcurrent = minOf(
        budget.maxConcurrentTiles,
        grid.totalTiles,
        if (totalAdapterTextureMemoryBytes != Long.MAX_VALUE) {
            (totalAdapterTextureMemoryBytes * budget.adapterTextureMemoryFraction / actualPerTileBytes).toInt().coerceAtLeast(1)
        } else {
            Int.MAX_VALUE
        },
    )

    return GpuTileDeferredResult.Accepted(
        tilePasses = emptyList(),
        strategy = GpuTileStrategy.DirectTargetSlice,
    )
}

private fun GpuTileStrategy.dumpLabel(): String = when (this) {
    is GpuTileStrategy.DirectTargetSlice -> "DirectTargetSlice"
    is GpuTileStrategy.TileIntermediateTexture -> "TileIntermediateTexture:${intermediate}"
}

private const val NONE_DUMP_VALUE = "none"
