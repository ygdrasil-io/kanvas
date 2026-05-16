package org.skia.testing

/**
 * Per-channel absolute diff. Used both as the worst-case across a comparison
 * (`maxChannelDiff`) and as the average across mismatching pixels
 * (`meanMismatchDiff`).
 */
public data class ChannelDiff(val a: Int, val r: Int, val g: Int, val b: Int) {
    public fun max(): Int = maxOf(a, maxOf(r, maxOf(g, b)))
    override fun toString(): String = "(A=$a, R=$r, G=$g, B=$b)"
}

/**
 * Detailed result of comparing two bitmaps. The single `similarity`
 * percentage matches the legacy `TestUtils.compareBitmaps` return value;
 * the rest exposes what's behind the number for diagnosis (which channel is
 * leaking, how big is the worst miss, how is the diff distributed on
 * average across mismatching pixels).
 *
 * Produced by [TestUtils.compareBitmapsDetailed] and consumed by
 * [TestUtils.saveComparisonImage] (triptych renderer) and [TestReport]
 * (markdown summary).
 */
public data class BitmapComparison(
    val similarity: Double,
    val totalPixels: Int,
    val matchingPixels: Int,
    val tolerance: Int,
    val maxChannelDiff: ChannelDiff,
    val meanMismatchDiff: ChannelDiff,
) {
    public val mismatchingPixels: Int get() = totalPixels - matchingPixels
    public val sizeMatches: Boolean get() = totalPixels > 0
}
