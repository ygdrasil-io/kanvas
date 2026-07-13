package org.graphiks.kanvas.codec.jpeg2000

import kotlin.ConsistentCopyVisibility

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

internal data class J2kGeometryModel(
    val frame: Jpeg2000FrameInfo,
    val components: List<J2kComponentSpec>,
    val tileGrid: J2kTileGrid,
)

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

@ConsistentCopyVisibility
internal data class J2kQuantizationStyle internal constructor(
    val guardBits: Int,
    val style: Int,
    val reversible: Boolean,
    private val arrays: J2kQuantizationArrays,
) {
    constructor(
        guardBits: Int,
        style: Int,
        reversible: Boolean,
        exponents: IntArray,
        mantissas: IntArray?,
    ) : this(guardBits, style, reversible, J2kQuantizationArrays(exponents, mantissas))

    val exponents: IntArray get() = arrays.copyExponents()
    val mantissas: IntArray? get() = arrays.copyMantissas()
}

internal data class J2kMainHeader(
    val geometry: J2kGeometryModel,
    val coding: J2kCodingStyle,
    val quantization: J2kQuantizationStyle,
    val nextMarkerOffset: Int,
)

internal class J2kQuantizationArrays(
    exponents: IntArray,
    mantissas: IntArray?,
) {
    private val storedExponents = exponents.copyOf()
    private val storedMantissas = mantissas?.copyOf()

    fun copyExponents(): IntArray = storedExponents.copyOf()
    fun copyMantissas(): IntArray? = storedMantissas?.copyOf()

    override fun equals(other: Any?): Boolean =
        other is J2kQuantizationArrays &&
            storedExponents.contentEquals(other.storedExponents) &&
            when {
                storedMantissas == null -> other.storedMantissas == null
                other.storedMantissas == null -> false
                else -> storedMantissas.contentEquals(other.storedMantissas)
            }

    override fun hashCode(): Int =
        31 * storedExponents.contentHashCode() + (storedMantissas?.contentHashCode() ?: 0)
}
