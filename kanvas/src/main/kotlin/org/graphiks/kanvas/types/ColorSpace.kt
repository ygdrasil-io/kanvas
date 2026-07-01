package org.graphiks.kanvas.types

data class ColorSpace(
    val name: String,
    val transferFunction: TransferFunction,
    val gamut: Gamut,
) {
    companion object {
        val SRGB = ColorSpace("sRGB", TransferFunction.SRGB, Gamut.SRGB)
        val DISPLAY_P3 = ColorSpace("Display P3", TransferFunction.SRGB, Gamut.DISPLAY_P3)
        val LINEAR_SRGB = ColorSpace("Linear sRGB", TransferFunction.LINEAR, Gamut.SRGB)
    }
}

enum class TransferFunction { SRGB, LINEAR, PQ, HLG }

enum class Gamut { SRGB, DISPLAY_P3, REC2020 }
