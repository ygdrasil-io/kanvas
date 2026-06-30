package org.graphiks.kanvas.svg

import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File

typealias ComparisonResult = ComparisonUtils.ComparisonResult

fun compareRgba(
    actual: ByteArray,
    reference: ByteArray,
    width: Int,
    height: Int,
    tolerance: Int = 0,
    minSimilarity: Double = 100.0,
): ComparisonResult = ComparisonUtils.compareRgba(actual, reference, width, height, tolerance, minSimilarity)

fun saveRgbaAsPng(rgba: ByteArray, width: Int, height: Int, outputFile: File) =
    ComparisonUtils.saveRgbaAsPng(rgba, width, height, outputFile)
