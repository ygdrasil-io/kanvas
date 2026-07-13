package org.graphiks.kanvas.codec.jpeg2000

import org.graphiks.kanvas.codec.Codec

private const val MAX_PART_ONE_TILE_PART_INDEX = 254

internal data class J2kDecodePlan(
    val syntax: J2kSyntaxModel,
    val tilePartsByTile: List<List<J2kTilePart>>,
    val codeblockUpperBound: Long,
) {
    internal companion object {
        fun create(syntax: J2kSyntaxModel, limits: Jpeg2000Limits): J2kDecodePlan {
            val tileCount = syntax.mainHeader.geometry.tileGrid.tileCount
            if (tileCount > limits.maxTiles || tileCount > Int.MAX_VALUE) {
                j2kFailure("jpeg2000.limit.tiles", syntax.mainHeader.nextMarkerOffset, Codec.Result.kOutOfMemory)
            }
            val orderedByTile = syntax.tileParts
                .groupBy(J2kTilePart::tileIndex)
                .mapValues { (_, parts) -> parts.sortedBy(J2kTilePart::partIndex) }

            orderedByTile.forEach { (tileIndex, partsForTile) ->
                val first = partsForTile.first()
                val allCountsUnknown = partsForTile.all { it.partCount == 0 }
                val allCountsDeclared = partsForTile.all { it.partCount > 0 }
                val hasOutOfDomainPartIndex = partsForTile.any {
                    it.partIndex !in 0..MAX_PART_ONE_TILE_PART_INDEX
                }
                val orderedIndexes = partsForTile.map(J2kTilePart::partIndex)
                val invalidSequence = when {
                    allCountsUnknown -> orderedIndexes != (0 until partsForTile.size).toList()
                    allCountsDeclared -> {
                        val expected = first.partCount
                        partsForTile.any { it.partCount != expected || it.partIndex < 0 || it.partIndex >= expected } ||
                            partsForTile.size != expected || orderedIndexes != (0 until expected).toList()
                    }
                    else -> true
                }
                if (
                    tileIndex < 0 || tileIndex.toLong() >= tileCount ||
                    hasOutOfDomainPartIndex || invalidSequence
                ) {
                    j2kFailure("jpeg2000.sot.sequence.invalid", first.headerOffset)
                }
            }

            val codeblockUpperBound = codeblockUpperBound(syntax.mainHeader, limits)
            val tilePartsByTile = MutableList(tileCount.toInt()) { tileIndex ->
                orderedByTile[tileIndex]
                    ?: j2kFailure("jpeg2000.sot.sequence.invalid", syntax.mainHeader.nextMarkerOffset)
            }
            return J2kDecodePlan(syntax, tilePartsByTile, codeblockUpperBound)
        }
    }
}

internal fun narrowEntropyInputOrNull(
    mainHeader: J2kMainHeader,
    tileParts: List<J2kTilePart>,
    data: ByteArray,
): J2kEntropyInput? {
    val geometry = mainHeader.geometry
    val grid = geometry.tileGrid
    val coding = mainHeader.coding
    val quantization = mainHeader.quantization
    val frame = geometry.frame
    val expectedExponents = when (coding.decompositions) {
        0 -> intArrayOf(8)
        1 -> intArrayOf(8, 9, 9, 10)
        2 -> intArrayOf(8, 9, 9, 10, 9, 9, 10)
        else -> return null
    }
    val part = tileParts.singleOrNull() ?: return null
    val dataEnd = part.dataOffset.toLong() + part.dataLength.toLong()
    if (
        geometry.rsiz != 0 || frame.components != 1 || frame.precision != 8 ||
        geometry.components.singleOrNull() != J2kComponentSpec(8, false, 1, 1) ||
        grid.imageX0 != 0 || grid.imageY0 != 0 ||
        grid.tileX0 != 0 || grid.tileY0 != 0 ||
        grid.imageX1 != frame.width || grid.imageY1 != frame.height ||
        grid.tileWidth != frame.width || grid.tileHeight != frame.height ||
        grid.columns != 1 || grid.rows != 1 ||
        coding.progression != J2kProgressionOrder.LRCP || coding.layers != 1 ||
        coding.multiComponentTransform != 0 || coding.usesSopMarkers || coding.usesEphMarkers ||
        coding.usesPrecinctPartitions || coding.codeBlockWidth != 4 || coding.codeBlockHeight != 4 ||
        coding.style != 0 || coding.transform != 1 ||
        quantization.guardBits != 2 || quantization.style != 0 || !quantization.reversible ||
        !quantization.exponents.contentEquals(expectedExponents) || quantization.mantissas != null ||
        part.tileIndex != 0 || part.partIndex != 0 || part.partCount != 1 || part.isOpenEndedLength ||
        part.dataOffset < 0 || part.dataLength < 0 || dataEnd > data.size.toLong()
    ) {
        return null
    }
    return J2kEntropyInput(part.dataOffset, part.dataLength, coding.decompositions)
}

private fun codeblockUpperBound(mainHeader: J2kMainHeader, limits: Jpeg2000Limits): Long {
    val grid = mainHeader.geometry.tileGrid
    val coding = mainHeader.coding
    val codeblockWidth = 1L shl (coding.codeBlockWidth + 2)
    val codeblockHeight = 1L shl (coding.codeBlockHeight + 2)
    var total = 0L

    for (tileRow in 0 until grid.rows) {
        for (tileColumn in 0 until grid.columns) {
            val gridTileX0 = checkedPlanAdd(
                grid.tileX0.toLong(),
                checkedPlanProduct(tileColumn.toLong(), grid.tileWidth.toLong(), mainHeader.nextMarkerOffset),
                mainHeader.nextMarkerOffset,
            )
            val gridTileY0 = checkedPlanAdd(
                grid.tileY0.toLong(),
                checkedPlanProduct(tileRow.toLong(), grid.tileHeight.toLong(), mainHeader.nextMarkerOffset),
                mainHeader.nextMarkerOffset,
            )
            val tileX0 = maxOf(grid.imageX0.toLong(), gridTileX0)
            val tileY0 = maxOf(grid.imageY0.toLong(), gridTileY0)
            val tileX1 = minOf(
                grid.imageX1.toLong(),
                checkedPlanAdd(gridTileX0, grid.tileWidth.toLong(), mainHeader.nextMarkerOffset),
            )
            val tileY1 = minOf(
                grid.imageY1.toLong(),
                checkedPlanAdd(gridTileY0, grid.tileHeight.toLong(), mainHeader.nextMarkerOffset),
            )
            mainHeader.geometry.components.forEach { component ->
                val componentWidth = ceilPlanDivide(tileX1, component.xSampling.toLong()) -
                    ceilPlanDivide(tileX0, component.xSampling.toLong())
                val componentHeight = ceilPlanDivide(tileY1, component.ySampling.toLong()) -
                    ceilPlanDivide(tileY0, component.ySampling.toLong())
                total = boundedPlanAdd(
                    total,
                    codeblocksForSubband(
                        componentWidth,
                        componentHeight,
                        coding.precinctExponents[0],
                        codeblockWidth,
                        codeblockHeight,
                        mainHeader.nextMarkerOffset,
                    ),
                    limits,
                    mainHeader.nextMarkerOffset,
                )
                for (resolution in 1..coding.decompositions) {
                    val scale = 1L shl (coding.decompositions - resolution)
                    val resolutionWidth = ceilPlanDivide(componentWidth, scale)
                    val resolutionHeight = ceilPlanDivide(componentHeight, scale)
                    val lowWidth = ceilPlanDivide(resolutionWidth, 2)
                    val lowHeight = ceilPlanDivide(resolutionHeight, 2)
                    val highWidth = resolutionWidth - lowWidth
                    val highHeight = resolutionHeight - lowHeight
                    val precinct = coding.precinctExponents[resolution]
                    total = boundedPlanAdd(
                        total,
                        codeblocksForSubband(
                            highWidth,
                            lowHeight,
                            precinct,
                            codeblockWidth,
                            codeblockHeight,
                            mainHeader.nextMarkerOffset,
                        ),
                        limits,
                        mainHeader.nextMarkerOffset,
                    )
                    total = boundedPlanAdd(
                        total,
                        codeblocksForSubband(
                            lowWidth,
                            highHeight,
                            precinct,
                            codeblockWidth,
                            codeblockHeight,
                            mainHeader.nextMarkerOffset,
                        ),
                        limits,
                        mainHeader.nextMarkerOffset,
                    )
                    total = boundedPlanAdd(
                        total,
                        codeblocksForSubband(
                            highWidth,
                            highHeight,
                            precinct,
                            codeblockWidth,
                            codeblockHeight,
                            mainHeader.nextMarkerOffset,
                        ),
                        limits,
                        mainHeader.nextMarkerOffset,
                    )
                }
            }
        }
    }
    return total
}

private fun codeblocksForSubband(
    width: Long,
    height: Long,
    precinct: Pair<Int, Int>,
    codeblockWidth: Long,
    codeblockHeight: Long,
    offset: Int,
): Long {
    if (width == 0L || height == 0L) return 0L
    val precinctWidth = 1L shl precinct.first
    val precinctHeight = 1L shl precinct.second
    val precinctCount = checkedPlanProduct(
        ceilPlanDivide(width, precinctWidth),
        ceilPlanDivide(height, precinctHeight),
        offset,
    )
    val codeblocksPerPrecinct = checkedPlanProduct(
        ceilPlanDivide(minOf(width, precinctWidth), codeblockWidth),
        ceilPlanDivide(minOf(height, precinctHeight), codeblockHeight),
        offset,
    )
    return checkedPlanProduct(precinctCount, codeblocksPerPrecinct, offset)
}

private fun boundedPlanAdd(current: Long, addition: Long, limits: Jpeg2000Limits, offset: Int): Long {
    val result = checkedPlanAdd(current, addition, offset)
    if (result > limits.maxCodeblocks) {
        j2kFailure("jpeg2000.limit.codeblocks", offset, Codec.Result.kOutOfMemory)
    }
    return result
}

private fun checkedPlanProduct(left: Long, right: Long, offset: Int): Long = try {
    Math.multiplyExact(left, right)
} catch (_: ArithmeticException) {
    j2kFailure("jpeg2000.limit.codeblocks", offset, Codec.Result.kOutOfMemory)
}

private fun checkedPlanAdd(left: Long, right: Long, offset: Int): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    j2kFailure("jpeg2000.limit.codeblocks", offset, Codec.Result.kOutOfMemory)
}

private fun ceilPlanDivide(value: Long, divisor: Long): Long =
    if (value == 0L) 0L else ((value - 1) / divisor) + 1
