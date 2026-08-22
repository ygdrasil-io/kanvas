package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.parametricCurveValidationError
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.toEncoded

/** A compiled matrix/TRC transform with all profile-dependent state cached. */
internal class MatrixColorTransform(
    private val sourceToXyzD50: ColorMatrix3x3F32,
    private val destinationFromXyzD50: ColorMatrix3x3F32,
    private val sourceTransferFunction: ColorTransferFunction.Parametric,
    private val destinationTransferFunction: ColorTransferFunction.Parametric,
    private val alphaType: AlphaType,
) : CompiledRgbPlan {
    override fun apply(pixels: FloatArray, offset: Int) {
        val alpha = pixels[offset + ALPHA_OFFSET]
        if (alphaType == AlphaType.PREMULTIPLIED && (!alpha.isFinite() || alpha == 0f)) {
            pixels[offset] = 0f
            pixels[offset + 1] = 0f
            pixels[offset + 2] = 0f
            return
        }

        val sourceRed = sourceTransferFunction.toLinear(
            if (alphaType == AlphaType.PREMULTIPLIED) pixels[offset] / alpha else pixels[offset],
        )
        val sourceGreen = sourceTransferFunction.toLinear(
            if (alphaType == AlphaType.PREMULTIPLIED) pixels[offset + 1] / alpha else pixels[offset + 1],
        )
        val sourceBlue = sourceTransferFunction.toLinear(
            if (alphaType == AlphaType.PREMULTIPLIED) pixels[offset + 2] / alpha else pixels[offset + 2],
        )

        val sourceLinear = floatArrayOf(sourceRed, sourceGreen, sourceBlue)
        val xyz = FloatArray(RGB_CHANNELS)
        val destinationLinear = FloatArray(RGB_CHANNELS)
        sourceToXyzD50.map(sourceLinear, 0, xyz, 0)
        destinationFromXyzD50.map(xyz, 0, destinationLinear, 0)

        val premultiply = if (alphaType == AlphaType.PREMULTIPLIED) alpha else 1f
        pixels[offset] = clampEncoded(destinationTransferFunction.toEncoded(destinationLinear[0])) * premultiply
        pixels[offset + 1] = clampEncoded(destinationTransferFunction.toEncoded(destinationLinear[1])) * premultiply
        pixels[offset + 2] = clampEncoded(destinationTransferFunction.toEncoded(destinationLinear[2])) * premultiply
    }

    private companion object {
        const val ALPHA_OFFSET = 3
        const val RGB_CHANNELS = 3
    }
}

private fun clampEncoded(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun validTransferFunction(transferFunction: ColorTransferFunction.Parametric): Boolean {
    val parameters = floatArrayOf(
        transferFunction.g,
        transferFunction.a,
        transferFunction.b,
        transferFunction.c,
        transferFunction.d,
        transferFunction.e,
        transferFunction.f,
    )
    return parametricCurveValidationError(4, parameters) == null
}
