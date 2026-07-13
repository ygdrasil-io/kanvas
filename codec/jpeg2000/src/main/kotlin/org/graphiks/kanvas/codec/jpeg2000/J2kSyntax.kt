package org.graphiks.kanvas.codec.jpeg2000

internal data class J2kComponentSpec(
    val precision: Int,
    val signed: Boolean,
    val xSampling: Int,
    val ySampling: Int,
)

internal data class J2kTileGrid(
    val imageX0: Int,
    val imageY0: Int,
    val imageX1: Int,
    val imageY1: Int,
    val tileX0: Int,
    val tileY0: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val columns: Int,
    val rows: Int,
) {
    val tileCount: Long get() = columns.toLong() * rows.toLong()
}

internal enum class J2kProgressionOrder {
    LRCP,
    RLCP,
    RPCL,
    PCRL,
    CPRL,
}

internal data class J2kCodingStyle(
    val progression: J2kProgressionOrder,
    val layers: Int,
    val multiComponentTransform: Int,
    val decompositions: Int,
    val codeBlockWidth: Int,
    val codeBlockHeight: Int,
    val style: Int,
    val transform: Int,
    val precinctExponents: List<Pair<Int, Int>>,
)

internal class J2kQuantizationStyle(
    val guardBits: Int,
    val style: Int,
    val reversible: Boolean,
    exponents: IntArray,
    mantissas: IntArray?,
) {
    private val storedExponents: IntArray = exponents.copyOf()
    private val storedMantissas: IntArray? = mantissas?.copyOf()

    val exponents: IntArray get() = storedExponents.copyOf()
    val mantissas: IntArray? get() = storedMantissas?.copyOf()
}
