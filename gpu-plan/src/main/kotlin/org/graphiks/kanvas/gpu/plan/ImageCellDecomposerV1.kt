package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32

/** Semantic image cells; all raw rectangles remain math-owned, defensively copied values. */
public sealed interface ImageCellPlanV1 {
    public class Sampled internal constructor(sourceF32: RectF32, destinationF32: RectF32,
        outerEdges: List<Boolean>) : ImageCellPlanV1 {
        private val source = sourceF32.copy()
        private val destination = destinationF32.copy()
        // Source-oriented start X/Y, end X/Y: outer coverage belongs to original geometry.
        public val outerEdges: List<Boolean> = immutableList(outerEdges)
        public fun copySourceF32(): RectF32 = source.copy()
        public fun copyDestinationF32(): RectF32 = destination.copy()
        public val canonicalIdentity: String = (listOf(source.left, source.top, source.right, source.bottom,
            destination.left, destination.top, destination.right, destination.bottom).joinToString(",") { it.toRawBits().toString() }) +
            ":outer=" + outerEdges.joinToString(",")
    }
}

/** Host decomposition only: no GPU resources, sampling or color conversion. */
public object ImageCellDecomposerV1 {
    public fun nine(widthI32: Int, heightI32: Int, centerF32: RectF32, destinationF32: RectF32): List<ImageCellPlanV1.Sampled> {
        require(widthI32 > 0 && heightI32 > 0) { W5eImagePlanDiagnostics.Dimensions }
        require(listOf(centerF32.left, centerF32.top, centerF32.right, centerF32.bottom).all(Float::isFinite) &&
            centerF32.isSorted()) { "invalid.material.image.nine-center" }
        require(listOf(destinationF32.left, destinationF32.top, destinationF32.right, destinationF32.bottom).all(Float::isFinite)) {
            W5eImagePlanDiagnostics.NumericDomainUnbounded
        }
        val widthF32 = widthI32.toFloat()
        val heightF32 = heightI32.toFloat()
        val xSource = listOf(0f, centerF32.left.coerceIn(0f, widthF32), centerF32.right.coerceIn(0f, widthF32), widthF32)
        val ySource = listOf(0f, centerF32.top.coerceIn(0f, heightF32), centerF32.bottom.coerceIn(0f, heightF32), heightF32)
        fun destinationAxis(source: List<Float>, startF32: Float, endF32: Float): List<Float> {
            // F64 construction prevents intermediate F32 overflow; copied F32 edges are
            // the actual later graph inputs, whose entire evaluation is separately sealed.
            val extentF64 = kotlin.math.abs(endF32.toDouble() - startF32.toDouble())
            val leadingF64 = source[1].toDouble()
            val trailingF64 = (source[3] - source[2]).toDouble()
            val fixedF64 = leadingF64 + trailingF64
            val directionF64 = if (endF32 >= startF32) 1.0 else -1.0
            val scaleF64 = if (fixedF64 > extentF64 && fixedF64 > 0.0) extentF64 / fixedF64 else 1.0
            val firstF32 = (startF32.toDouble() + directionF64 * leadingF64 * scaleF64).toFloat()
            val secondF32 = if (fixedF64 >= extentF64) firstF32
                else (endF32.toDouble() - directionF64 * trailingF64).toFloat()
            return listOf(startF32, firstF32, secondF32, endF32)
        }
        val xDestination = destinationAxis(xSource, destinationF32.left, destinationF32.right)
        val yDestination = destinationAxis(ySource, destinationF32.top, destinationF32.bottom)
        return immutableList(buildList {
            for (rowI32 in 0 until 3) for (columnI32 in 0 until 3) {
                val source = RectF32.ofLTRB(xSource[columnI32], ySource[rowI32], xSource[columnI32 + 1], ySource[rowI32 + 1])
                val destination = RectF32.ofLTRB(xDestination[columnI32], yDestination[rowI32], xDestination[columnI32 + 1], yDestination[rowI32 + 1])
                if (source.left == source.right || source.top == source.bottom ||
                    destination.left == destination.right || destination.top == destination.bottom) continue
                add(ImageCellPlanV1.Sampled(source, destination, listOf(destination.left == destinationF32.left,
                    destination.top == destinationF32.top, destination.right == destinationF32.right, destination.bottom == destinationF32.bottom)))
            }
        })
    }
}

/** Executable selector contract: source-oriented half-open cells, first hit, discard on miss.
 * Outer edges are unbounded because the original geometry owns fractional outer coverage.
 * Every unit coordinate is the same scalar graph used in its cell's sampling certificate. */
public class ImageCellSelectionPlanV1 internal constructor(samples: List<Sample>) {
    public class Sample internal constructor(public val cell: ImageCellPlanV1.Sampled,
        public val coordinates: ImageCoordinatePlanV1, public val numericAuthority: ImageNumericAuthorityV1) {
        init {
            require(coordinates.copySourceF32() == cell.copySourceF32() &&
                coordinates.copyDestinationF32() == cell.copyDestinationF32()) { W5eImagePlanDiagnostics.InvalidContract }
        }
    }
    public val samples: List<Sample> = immutableList(samples)
    public val capacityI32: Int = 9
    public val lowerBoundF32: Float = 0f
    public val upperBoundF32: Float = 1f
    public val lowerInclusive: Boolean = true
    public val upperInclusive: Boolean = false
    public val firstHit: Boolean = true
    public val discardOnMiss: Boolean = true
    public val topologyIdentity: String = "nine-first-hit-half-open-outer-coverage-discard-v1"
    public val canonicalIdentity: String = "$topologyIdentity:capacity=$capacityI32:count=${samples.size}:" +
        "bounds=${lowerBoundF32.toRawBits()}:${upperBoundF32.toRawBits()}:inclusive=$lowerInclusive:$upperInclusive:first=$firstHit:discard=$discardOnMiss:" +
        samples.joinToString(";") { "${it.cell.canonicalIdentity}:${it.coordinates.canonicalIdentity}:${it.numericAuthority.canonicalIdentity}" }
    init { require(samples.size <= capacityI32 && samples.all { it.cell.outerEdges.size == 4 }) }
}
