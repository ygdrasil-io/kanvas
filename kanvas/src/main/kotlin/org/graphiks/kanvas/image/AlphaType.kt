package org.graphiks.kanvas.image

enum class AlphaType {
    UNKNOWN,
    OPAQUE,
    PREMUL,
    UNPREMUL,
    ;

    fun isOpaque(): Boolean = this == OPAQUE

    fun isValid(): Boolean = this != UNKNOWN
}
