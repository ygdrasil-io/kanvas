package org.graphiks.kanvas.image

import org.graphiks.kanvas.types.Matrix33

enum class EncodedOrigin(val exifValue: Int) {
    TOP_LEFT(1),
    TOP_RIGHT(2),
    BOTTOM_RIGHT(3),
    BOTTOM_LEFT(4),
    LEFT_TOP(5),
    RIGHT_TOP(6),
    RIGHT_BOTTOM(7),
    LEFT_BOTTOM(8),
    ;

    fun swapsWidthHeight(): Boolean = ordinal >= LEFT_TOP.ordinal

    fun toMatrix(width: Int, height: Int): Matrix33 {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (this) {
            TOP_LEFT -> Matrix33.identity()
            TOP_RIGHT -> Matrix33.makeAll(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f)
            BOTTOM_RIGHT -> Matrix33.makeAll(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f)
            BOTTOM_LEFT -> Matrix33.makeAll(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f)
            LEFT_TOP -> Matrix33.makeAll(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            RIGHT_TOP -> Matrix33.makeAll(0f, -1f, w, 1f, 0f, 0f, 0f, 0f, 1f)
            RIGHT_BOTTOM -> Matrix33.makeAll(0f, -1f, w, -1f, 0f, h, 0f, 0f, 1f)
            LEFT_BOTTOM -> Matrix33.makeAll(0f, 1f, 0f, -1f, 0f, h, 0f, 0f, 1f)
        }
    }

    companion object {
        val DEFAULT: EncodedOrigin = TOP_LEFT
        val LAST: EncodedOrigin = LEFT_BOTTOM

        fun fromExifValue(value: Int): EncodedOrigin =
            entries.firstOrNull { it.exifValue == value } ?: DEFAULT
    }
}
