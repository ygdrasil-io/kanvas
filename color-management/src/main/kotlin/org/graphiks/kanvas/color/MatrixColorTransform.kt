package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.parametricCurveValidationError
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.abs
import kotlin.math.pow

/** A compiled matrix/TRC transform with all profile-dependent state cached. */
internal class MatrixColorTransform(
    sourceToXyzD50: FloatArray,
    destinationFromXyzD50: FloatArray,
    private val sourceTransferFunction: SkcmsTransferFunction,
    private val destinationTransferFunction: SkcmsTransferFunction,
    private val alphaType: AlphaType,
) : CompiledRgbPlan {
    private val sourceToXyzD50 = sourceToXyzD50.copyOf()
    private val destinationFromXyzD50 = destinationFromXyzD50.copyOf()

    init {
        require(this.sourceToXyzD50.size == MATRIX_COMPONENTS) { "source matrix must be 3x3" }
        require(this.destinationFromXyzD50.size == MATRIX_COMPONENTS) { "destination matrix must be 3x3" }
    }

    override fun apply(pixels: FloatArray, offset: Int) {
        val alpha = pixels[offset + ALPHA_OFFSET]
        if (alphaType == AlphaType.PREMULTIPLIED && alpha == 0f) {
            pixels[offset] = 0f
            pixels[offset + 1] = 0f
            pixels[offset + 2] = 0f
            return
        }

        val unpremultiply = if (alphaType == AlphaType.PREMULTIPLIED) 1f / alpha else 1f
        val sourceRed = decode(sourceTransferFunction, pixels[offset] * unpremultiply)
        val sourceGreen = decode(sourceTransferFunction, pixels[offset + 1] * unpremultiply)
        val sourceBlue = decode(sourceTransferFunction, pixels[offset + 2] * unpremultiply)

        val xyzX = sourceToXyzD50[0] * sourceRed + sourceToXyzD50[1] * sourceGreen + sourceToXyzD50[2] * sourceBlue
        val xyzY = sourceToXyzD50[3] * sourceRed + sourceToXyzD50[4] * sourceGreen + sourceToXyzD50[5] * sourceBlue
        val xyzZ = sourceToXyzD50[6] * sourceRed + sourceToXyzD50[7] * sourceGreen + sourceToXyzD50[8] * sourceBlue

        val destinationRed = destinationFromXyzD50[0] * xyzX + destinationFromXyzD50[1] * xyzY + destinationFromXyzD50[2] * xyzZ
        val destinationGreen = destinationFromXyzD50[3] * xyzX + destinationFromXyzD50[4] * xyzY + destinationFromXyzD50[5] * xyzZ
        val destinationBlue = destinationFromXyzD50[6] * xyzX + destinationFromXyzD50[7] * xyzY + destinationFromXyzD50[8] * xyzZ

        val premultiply = if (alphaType == AlphaType.PREMULTIPLIED) alpha else 1f
        pixels[offset] = clampEncoded(encode(destinationTransferFunction, destinationRed)) * premultiply
        pixels[offset + 1] = clampEncoded(encode(destinationTransferFunction, destinationGreen)) * premultiply
        pixels[offset + 2] = clampEncoded(encode(destinationTransferFunction, destinationBlue)) * premultiply
    }

    private companion object {
        const val ALPHA_OFFSET = 3
        const val MATRIX_COMPONENTS = 9
    }
}

private fun decode(transferFunction: SkcmsTransferFunction, encoded: Float): Float = if (encoded >= transferFunction.d) {
    (transferFunction.a * encoded + transferFunction.b).pow(transferFunction.g) + transferFunction.e
} else {
    transferFunction.c * encoded + transferFunction.f
}

private fun encode(transferFunction: SkcmsTransferFunction, linear: Float): Float {
    val lowerLimit = transferFunction.c * transferFunction.d + transferFunction.f
    val upperLimit = decode(transferFunction, transferFunction.d)
    return when {
        linear < lowerLimit && transferFunction.c > 0f -> (linear - transferFunction.f) / transferFunction.c
        linear < upperLimit -> transferFunction.d
        else -> ((linear - transferFunction.e).pow(1f / transferFunction.g) -
            transferFunction.b) / transferFunction.a
    }
}

private fun clampEncoded(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun validTransferFunction(transferFunction: SkcmsTransferFunction): Boolean {
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

internal fun matrixValues(matrix: SkcmsMatrix3x3): FloatArray = FloatArray(9) { index ->
    matrix[index / 3, index % 3]
}

internal fun invert3x3(matrix: FloatArray): FloatArray? {
    val a = matrix[0].toDouble()
    val b = matrix[1].toDouble()
    val c = matrix[2].toDouble()
    val d = matrix[3].toDouble()
    val e = matrix[4].toDouble()
    val f = matrix[5].toDouble()
    val g = matrix[6].toDouble()
    val h = matrix[7].toDouble()
    val i = matrix[8].toDouble()
    val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if (!determinant.isFinite() || determinant == 0.0) return null
    val inverseDeterminant = 1.0 / determinant
    val values = doubleArrayOf(
        (e * i - f * h) * inverseDeterminant,
        (c * h - b * i) * inverseDeterminant,
        (b * f - c * e) * inverseDeterminant,
        (f * g - d * i) * inverseDeterminant,
        (a * i - c * g) * inverseDeterminant,
        (c * d - a * f) * inverseDeterminant,
        (d * h - e * g) * inverseDeterminant,
        (b * g - a * h) * inverseDeterminant,
        (a * e - b * d) * inverseDeterminant,
    )
    if (values.any { !it.isFinite() || abs(it) > Float.MAX_VALUE.toDouble() }) return null
    return FloatArray(9) { values[it].toFloat() }
}
