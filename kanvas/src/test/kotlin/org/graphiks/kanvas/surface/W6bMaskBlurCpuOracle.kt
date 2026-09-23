@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import org.graphiks.kanvas.pipeline.BlurStyle

/**
 * Public-pixel reference for the small W6b mask-blur fixtures.
 *
 * This deliberately owns its raster coverage, Gaussian kernel, style equations, and
 * premultiplied sRGB encoding.  It has no renderer, graph, shader, or Surface dependency.
 */
internal object W6bMaskBlurCpuOracle {
    const val widthI32 = 11
    const val heightI32 = 9

    fun assertNear(expected: UByteArray, actual: UByteArray, toleranceI32: Int = 14) {
        require(expected.size == actual.size)
        val largestDifferenceI32 = expected.indices.maxOf { indexI32 ->
            kotlin.math.abs(expected[indexI32].toInt() - actual[indexI32].toInt())
        }
        val mismatchI32 = expected.indices.firstOrNull { indexI32 ->
            kotlin.math.abs(expected[indexI32].toInt() - actual[indexI32].toInt()) == largestDifferenceI32
        }
        val pixelI32 = requireNotNull(mismatchI32).div(4)
        val pixelOffsetI32 = pixelI32 * 4
        assertTrue(largestDifferenceI32 <= toleranceI32, "W6b mask blur differs by $largestDifferenceI32 at " +
            "pixel=$pixelI32, channel=${mismatchI32.rem(4)}, expected=" +
            "${expected[mismatchI32]}, actual=${actual[mismatchI32]}, expectedRgba=" +
            "${expected.copyOfRange(pixelOffsetI32, pixelOffsetI32 + 4).joinToString()}, actualRgba=" +
            "${actual.copyOfRange(pixelOffsetI32, pixelOffsetI32 + 4).joinToString()}")
    }

    fun renderStyle(style: BlurStyle): UByteArray = opaqueSource(styled(style, translatedRectCoverage()))

    /** Independent area coverage for the public fractional AA rectangle fixture. */
    fun renderFractionalStyle(style: BlurStyle): UByteArray = opaqueSource(styled(style, fractionalRectCoverage()))

    fun renderDstOutOverGreen(): UByteArray = dstOutGreen(styled(BlurStyle.NORMAL, translatedRectCoverage()))

    fun renderClippedRoundedRectMask(): UByteArray = opaqueSource(styled(BlurStyle.NORMAL, clippedRoundedRectCoverage()))

    fun renderDirectTriangleMaskSourceOver(): UByteArray = opaqueSource(styled(BlurStyle.NORMAL, directTriangleCoverage()))

    fun renderStencilPathDstOutOverGreen(): UByteArray = dstOutGreen(styled(BlurStyle.NORMAL, stencilPathCoverage()))

    fun renderStencilPathMaskSourceOver(): UByteArray = opaqueSource(styled(BlurStyle.NORMAL, stencilPathCoverage()))

    fun renderLayerOverBlue(): UByteArray = sourceOverBlue(opaqueSourceAlpha(styled(BlurStyle.NORMAL, translatedRectCoverage())))

    fun renderMaskedThenImageBlur(): UByteArray = opaqueWhiteSource(blur(styled(BlurStyle.NORMAL, translatedRectCoverage())))

    fun renderNestedPictureMaskBlur(): UByteArray {
        val childCoverage = FloatArray(widthI32 * heightI32)
        fillRect(childCoverage, 2, 2, 6, 4, 0.5f)
        // Two half-alpha white draws overlap by source-over, then Clear opens a true transparent hole.
        for (yI32 in 2 until 4) for (xI32 in 2 until 4) {
            val indexI32 = yI32 * widthI32 + xI32
            childCoverage[indexI32] = 0.75f
        }
        for (yI32 in 2 until 3) for (xI32 in 5 until 6) childCoverage[yI32 * widthI32 + xI32] = 0f
        val innerMask = blur(childCoverage)
        // Picture material is sampled before its own mask.  Only texels with a sealed child
        // source retain straight white; the parent blur may still carry their alpha farther as
        // transparent black.  This mirrors the public premultiplied Picture contract without
        // consulting the renderer or graph.
        return pictureMaskSource(childCoverage, blur(innerMask))
    }

    private fun translatedRectCoverage(): FloatArray = FloatArray(widthI32 * heightI32).also { coverage ->
        // The public draw is local [3,6)×[3,6) after translate(1, 0).
        fillRect(coverage, 4, 3, 7, 6, 1f)
    }

    private fun fractionalRectCoverage(): FloatArray = FloatArray(widthI32 * heightI32).also { coverage ->
        val leftF32 = 3.25f
        val topF32 = 2.25f
        val rightF32 = 6.75f
        val bottomF32 = 5.75f
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val coverageX = (minOf(xI32 + 1f, rightF32) - maxOf(xI32.toFloat(), leftF32)).coerceIn(0f, 1f)
            val coverageY = (minOf(yI32 + 1f, bottomF32) - maxOf(yI32.toFloat(), topF32)).coerceIn(0f, 1f)
            coverage[yI32 * widthI32 + xI32] = coverageX * coverageY
        }
    }

    private fun clippedRoundedRectCoverage(): FloatArray = FloatArray(widthI32 * heightI32).also { coverage ->
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val xF32 = xI32 + .5f
            val yF32 = yI32 + .5f
            val clip = xF32 >= 3f && xF32 < 8f && yF32 >= 2f && yF32 < 7f
            val nearestX = xF32.coerceIn(4f, 6f)
            val nearestY = yF32.coerceIn(4f, 5f)
            val dxF32 = xF32 - nearestX
            val dyF32 = yF32 - nearestY
            if (clip && dxF32 * dxF32 + dyF32 * dyF32 <= 4f) coverage[yI32 * widthI32 + xI32] = 1f
        }
    }

    private fun stencilPathCoverage(): FloatArray = FloatArray(widthI32 * heightI32).also { coverage ->
        val vertices = arrayOf(
            floatArrayOf(2f, 2f),
            floatArrayOf(8f, 2f),
            floatArrayOf(8f, 6f),
            floatArrayOf(5f, 4f),
            floatArrayOf(2f, 6f),
        )
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val xF32 = xI32 + .5f
            val yF32 = yI32 + .5f
            var inside = false
            for (indexI32 in vertices.indices) {
                val start = vertices[indexI32]
                val end = vertices[(indexI32 + 1).rem(vertices.size)]
                val crosses = (start[1] > yF32) != (end[1] > yF32)
                if (crosses && xF32 < (end[0] - start[0]) * (yF32 - start[1]) / (end[1] - start[1]) + start[0]) inside = !inside
            }
            if (inside) coverage[yI32 * widthI32 + xI32] = 1f
        }
    }

    private fun directTriangleCoverage(): FloatArray = FloatArray(widthI32 * heightI32).also { coverage ->
        val vertices = arrayOf(
            floatArrayOf(2f, 2f),
            floatArrayOf(8f, 2f),
            floatArrayOf(2f, 7f),
        )
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val xF32 = xI32 + .5f
            val yF32 = yI32 + .5f
            val cross0 = (vertices[1][0] - vertices[0][0]) * (yF32 - vertices[0][1]) -
                (vertices[1][1] - vertices[0][1]) * (xF32 - vertices[0][0])
            val cross1 = (vertices[2][0] - vertices[1][0]) * (yF32 - vertices[1][1]) -
                (vertices[2][1] - vertices[1][1]) * (xF32 - vertices[1][0])
            val cross2 = (vertices[0][0] - vertices[2][0]) * (yF32 - vertices[2][1]) -
                (vertices[0][1] - vertices[2][1]) * (xF32 - vertices[2][0])
            if ((cross0 >= 0f && cross1 >= 0f && cross2 >= 0f) ||
                (cross0 <= 0f && cross1 <= 0f && cross2 <= 0f)) coverage[yI32 * widthI32 + xI32] = 1f
        }
    }

    private fun styled(style: BlurStyle, original: FloatArray): FloatArray {
        val blurred = blur(original)
        return FloatArray(original.size) { indexI32 -> when (style) {
            BlurStyle.NORMAL -> blurred[indexI32]
            BlurStyle.SOLID -> original[indexI32] + blurred[indexI32] * (1f - original[indexI32])
            BlurStyle.OUTER -> blurred[indexI32] * (1f - original[indexI32])
            BlurStyle.INNER -> blurred[indexI32] * original[indexI32]
        }.coerceIn(0f, 1f) }
    }

    private fun blur(source: FloatArray): FloatArray {
        val kernel = gaussianKernel(1f)
        val radiusI32 = kernel.size / 2
        val horizontal = FloatArray(source.size)
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            var sumF32 = 0f
            kernel.forEachIndexed { tapI32, weightF32 ->
                val sampleXI32 = xI32 + tapI32 - radiusI32
                if (sampleXI32 in 0 until widthI32) sumF32 += source[yI32 * widthI32 + sampleXI32] * weightF32
            }
            horizontal[yI32 * widthI32 + xI32] = sumF32
        }
        return FloatArray(source.size).also { output ->
            for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
                var sumF32 = 0f
                kernel.forEachIndexed { tapI32, weightF32 ->
                    val sampleYI32 = yI32 + tapI32 - radiusI32
                    if (sampleYI32 in 0 until heightI32) sumF32 += horizontal[sampleYI32 * widthI32 + xI32] * weightF32
                }
                output[yI32 * widthI32 + xI32] = sumF32
            }
        }
    }

    private fun gaussianKernel(sigmaF32: Float): FloatArray {
        val radiusI32 = ceil(3f * sigmaF32).toInt()
        val weights = FloatArray(radiusI32 * 2 + 1) { indexI32 ->
            val distanceF32 = (indexI32 - radiusI32).toFloat()
            exp(-(distanceF32 * distanceF32) / (2f * sigmaF32 * sigmaF32))
        }
        val totalF32 = weights.sum()
        return weights.also { normalized -> normalized.indices.forEach { indexI32 -> normalized[indexI32] /= totalF32 } }
    }

    private fun opaqueSource(alpha: FloatArray): UByteArray = opaqueSourceAlpha(alpha)

    private fun opaqueWhiteSource(alpha: FloatArray): UByteArray = UByteArray(alpha.size * 4).also { pixels ->
        alpha.forEachIndexed { pixelI32, alphaF32 ->
            val offsetI32 = pixelI32 * 4
            val encoded = encodePremul(alphaF32)
            pixels[offsetI32] = encoded
            pixels[offsetI32 + 1] = encoded
            pixels[offsetI32 + 2] = encoded
            pixels[offsetI32 + 3] = encodeAlpha(alphaF32)
        }
    }

    private fun opaqueSourceAlpha(alpha: FloatArray): UByteArray = UByteArray(alpha.size * 4).also { pixels ->
        alpha.forEachIndexed { pixelI32, alphaF32 ->
            val offsetI32 = pixelI32 * 4
            pixels[offsetI32] = encodePremul(alphaF32)
            pixels[offsetI32 + 3] = encodeAlpha(alphaF32)
        }
    }

    private fun pictureMaskSource(sourceAlpha: FloatArray, mask: FloatArray): UByteArray = UByteArray(mask.size * 4).also { pixels ->
        mask.forEachIndexed { pixelI32, alphaF32 ->
            val offsetI32 = pixelI32 * 4
            val encoded = if (sourceAlpha[pixelI32] > 0f) encodePremul(alphaF32) else 0u
            pixels[offsetI32] = encoded
            pixels[offsetI32 + 1] = encoded
            pixels[offsetI32 + 2] = encoded
            pixels[offsetI32 + 3] = encodeAlpha(alphaF32)
        }
    }

    private fun dstOutGreen(mask: FloatArray): UByteArray = UByteArray(mask.size * 4).also { pixels ->
        mask.forEachIndexed { pixelI32, alphaF32 ->
            val remainingF32 = (1f - alphaF32).coerceIn(0f, 1f)
            val offsetI32 = pixelI32 * 4
            pixels[offsetI32 + 1] = encodePremul(remainingF32)
            pixels[offsetI32 + 3] = encodeAlpha(remainingF32)
        }
    }

    private fun sourceOverBlue(redSource: UByteArray): UByteArray = UByteArray(redSource.size).also { pixels ->
        repeat(widthI32 * heightI32) { pixelI32 ->
            val offsetI32 = pixelI32 * 4
            val alphaF32 = redSource[offsetI32 + 3].toInt() / 255f
            pixels[offsetI32] = redSource[offsetI32]
            pixels[offsetI32 + 2] = encodePremul(1f - alphaF32)
            pixels[offsetI32 + 3] = 255u
        }
    }

    private fun fillRect(values: FloatArray, leftI32: Int, topI32: Int, rightI32: Int, bottomI32: Int, valueF32: Float) {
        for (yI32 in topI32 until bottomI32) for (xI32 in leftI32 until rightI32) values[yI32 * widthI32 + xI32] = valueF32
    }

    private fun encodePremul(valueF32: Float): UByte = (valueF32.coerceIn(0f, 1f).pow(1f / 2.2f) * 255f).roundToInt().toUByte()
    private fun encodeAlpha(valueF32: Float): UByte = (valueF32.coerceIn(0f, 1f) * 255f).roundToInt().toUByte()
}
