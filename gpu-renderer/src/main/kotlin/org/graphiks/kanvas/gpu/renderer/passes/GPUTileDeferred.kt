package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

data class GPUTileGridPlan(
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

data class GPUTileGridPolicy(
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

data class GPUTileBounds(
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

    fun intersects(other: GPUTileBounds): Boolean {
        return left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top
    }
}

data class GPUTileBin(
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

data class GPUTileBinningPass(
    val sourcePass: String,
    val bins: List<GPUTileBin>,
) {
    init {
        require(sourcePass.isNotBlank()) { "sourcePass must not be blank" }
        require(bins.isNotEmpty()) { "bins must not be empty" }
    }

    val nonEmptyBinCount: Int get() = bins.count { bin -> !bin.isEmpty }
}

data class GPUTilePass(
    val tile: GPUTileBin,
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

sealed class GPUTileStrategy {
    object DirectTargetSlice : GPUTileStrategy()
    data class TileIntermediateTexture(
        val intermediate: String,
        val compositePlan: GPUTileCompositePass,
    ) : GPUTileStrategy()
}

data class GPUTileCompositePass(
    val sourceTiles: List<GPUTilePass>,
    val mergeMode: String,
) {
    init {
        require(mergeMode.isNotBlank()) { "mergeMode must not be blank" }
    }
}

data class GPUTileMemoryBudget(
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

sealed interface GPUTileDeferredResult {
    data class Accepted(
        val tilePasses: List<GPUTilePass>,
        val strategy: GPUTileStrategy,
    ) : GPUTileDeferredResult {
        fun dumpLines(): List<String> = listOf(
            "tile-deferred.accepted tiles=${tilePasses.size} " +
                "strategy=${strategy.dumpLabel()}",
        ) + tilePasses.flatMap { pass -> pass.dumpLines() }
    }

    data class Refused(val diagnostic: RefuseDiagnostic) : GPUTileDeferredResult {
        fun dumpLines(): List<String> = listOf(
            "tile-deferred.refused code=${diagnostic.code} " +
                "stage=${diagnostic.stage} " +
                "terminal=${diagnostic.terminal} " +
                "message=${diagnostic.message}",
        )
    }
}

/** Stable refusal/diagnostic codes for tile-deferred rendering (KGPU-M40-001). */
object GPUTileDeferredReason {
    const val BUDGET_EXCEEDED = "unsupported.tile.budget_exceeded"
    const val CROSS_TILE_DESTINATION_READ = "unsupported.tile.cross_tile_destination_read"
    const val CROSS_TILE_CLIP_ATOMIC_GROUP = "unsupported.tile.cross_tile_clip_atomic_group"
}

fun computeTileGrid(
    targetWidth: Int,
    targetHeight: Int,
    tileSize: Int = 256,
): GPUTileGridPlan {
    require(targetWidth > 0) { "targetWidth must be positive" }
    require(targetHeight > 0) { "targetHeight must be positive" }
    require(tileSize > 0) { "tileSize must be positive" }

    val tileCountX = (targetWidth + tileSize - 1) / tileSize
    val tileCountY = (targetHeight + tileSize - 1) / tileSize
    val paddedWidth = tileCountX * tileSize
    val paddedHeight = tileCountY * tileSize

    return GPUTileGridPlan(
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
    grid: GPUTileGridPlan,
    tileIndexX: Int,
    tileIndexY: Int,
): GPUTileBounds {
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

    return GPUTileBounds(x = x, y = y, width = tileWidth, height = tileHeight)
}

fun binDrawsToTiles(
    grid: GPUTileGridPlan,
    draws: List<GPUDrawInvocation>,
    drawBounds: Map<Int, GPUTileBounds>,
): List<GPUTileBin> {
    val bins = mutableListOf<GPUTileBin>()

    for (ty in 0 until grid.tileCountY) {
        for (tx in 0 until grid.tileCountX) {
            val tileRect = tileRectForIndex(grid, tx, ty)
            val intersectingDraws = draws.filter { invocation ->
                val bounds = drawBounds[invocation.commandIdValue]
                bounds != null && tileRect.intersects(bounds)
            }
            bins.add(
                GPUTileBin(
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
    bins: List<GPUTileBin>,
    packetsByCommandId: Map<Int, List<GPUDrawPacket>>,
): List<GPUTilePass> {
    return bins.filter { bin -> !bin.isEmpty }.map { bin ->
        val packets = bin.drawInvocations.flatMap { invocation ->
            packetsByCommandId[invocation.commandIdValue] ?: emptyList()
        }
        require(packets.isNotEmpty()) {
            "Tile [${bin.tileIndexX},${bin.tileIndexY}] has draws but no resolved packets"
        }
        val scissor = "tile-scissor:${bin.tileIndexX}x${bin.tileIndexY}"
        GPUTilePass(
            tile = bin,
            scissor = scissor,
            sortedPackets = packets,
        )
    }
}

fun buildCompositePass(
    tilePasses: List<GPUTilePass>,
    mergeMode: String,
): GPUTileCompositePass {
    return GPUTileCompositePass(
        sourceTiles = tilePasses,
        mergeMode = mergeMode,
    )
}

/**
 * Refuses a destination-read draw that bins into more than one tile.
 *
 * Cross-tile destination reads cannot be satisfied per-tile because each tile only owns its own
 * sub-rect of the target; the read must be deferred to the composite pass. Single-tile destination
 * reads are accepted (the read stays within one tile-private intermediate).
 */
fun checkCrossTileDestinationRead(
    bins: List<GPUTileBin>,
    destinationReadingCommandIds: Set<Int>,
): GPUTileDeferredResult {
    for (commandId in destinationReadingCommandIds) {
        val coveringTiles = bins.filter { bin ->
            bin.drawInvocations.any { invocation -> invocation.commandIdValue == commandId }
        }
        if (coveringTiles.size > 1) {
            val tileLabels = coveringTiles
                .sortedWith(compareBy({ it.tileIndexY }, { it.tileIndexX }))
                .joinToString(separator = "") { tile -> "[${tile.tileIndexX},${tile.tileIndexY}]" }
            return GPUTileDeferredResult.Refused(
                RefuseDiagnostic(
                    code = GPUTileDeferredReason.CROSS_TILE_DESTINATION_READ,
                    message = "Cross-tile destination read refused for draw $commandId; " +
                        "spans tiles $tileLabels; deferred to composite pass",
                    stage = "tile.binning",
                    terminal = true,
                ),
            )
        }
    }
    return GPUTileDeferredResult.Accepted(
        tilePasses = emptyList(),
        strategy = GPUTileStrategy.DirectTargetSlice,
    )
}

/**
 * Refuses a clip atomic group whose member draws bin into more than one tile.
 *
 * Clip atomic groups must be evaluated together within a single tile pass; splitting them across
 * tiles would break the all-or-nothing clip semantics, so the split is refused at binning.
 */
fun checkCrossTileClipAtomicGroup(
    bins: List<GPUTileBin>,
    clipAtomicGroupByCommandId: Map<Int, String>,
): GPUTileDeferredResult {
    val tilesByGroup = mutableMapOf<String, MutableSet<Pair<Int, Int>>>()
    for (bin in bins) {
        for (invocation in bin.drawInvocations) {
            val group = clipAtomicGroupByCommandId[invocation.commandIdValue] ?: continue
            tilesByGroup.getOrPut(group) { mutableSetOf() }.add(bin.tileIndexX to bin.tileIndexY)
        }
    }
    for ((group, tiles) in tilesByGroup) {
        if (tiles.size > 1) {
            val tileLabels = tiles
                .sortedWith(compareBy({ it.second }, { it.first }))
                .joinToString(separator = "") { tile -> "[${tile.first},${tile.second}]" }
            return GPUTileDeferredResult.Refused(
                RefuseDiagnostic(
                    code = GPUTileDeferredReason.CROSS_TILE_CLIP_ATOMIC_GROUP,
                    message = "Cross-tile clip atomic group '$group' refused at binning; " +
                        "spans tiles $tileLabels",
                    stage = "tile.binning",
                    terminal = true,
                ),
            )
        }
    }
    return GPUTileDeferredResult.Accepted(
        tilePasses = emptyList(),
        strategy = GPUTileStrategy.DirectTargetSlice,
    )
}

fun checkTileMemoryBudget(
    grid: GPUTileGridPlan,
    budget: GPUTileMemoryBudget,
    bytesPerPixel: Int = 4,
    totalAdapterTextureMemoryBytes: Long = Long.MAX_VALUE,
): GPUTileDeferredResult {
    val tilePixelCount = grid.tileSize.toLong() * grid.tileSize.toLong()
    val actualPerTileBytes = tilePixelCount * bytesPerPixel

    val maxAllowedPerTile = (totalAdapterTextureMemoryBytes * budget.adapterTextureMemoryFraction).toLong()

    if (actualPerTileBytes > maxAllowedPerTile) {
        return GPUTileDeferredResult.Refused(
            RefuseDiagnostic(
                code = GPUTileDeferredReason.BUDGET_EXCEEDED,
                message = "Per-tile budget exceeded: $actualPerTileBytes bytes needed, " +
                    "$maxAllowedPerTile bytes available (${budget.adapterTextureMemoryFraction * 100}% of adapter texture memory)",
                stage = "tile.budget",
                terminal = true,
            )
        )
    }

    if (actualPerTileBytes > budget.perTileBytes) {
        return GPUTileDeferredResult.Refused(
            RefuseDiagnostic(
                code = GPUTileDeferredReason.BUDGET_EXCEEDED,
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

    return GPUTileDeferredResult.Accepted(
        tilePasses = emptyList(),
        strategy = GPUTileStrategy.DirectTargetSlice,
    )
}

private fun GPUTileStrategy.dumpLabel(): String = when (this) {
    is GPUTileStrategy.DirectTargetSlice -> "DirectTargetSlice"
    is GPUTileStrategy.TileIntermediateTexture -> "TileIntermediateTexture:${intermediate}"
}

private const val NONE_DUMP_VALUE = "none"
