package org.graphiks.kanvas.image

enum class ColorType(val bytesPerPixel: Int) {
    UNKNOWN(0),
    ALPHA_8(1),
    RGB_565(2),
    ARGB_4444(2),
    RGBA_8888(4),
    RGB_888X(4),
    BGRA_8888(4),
    RGBA_1010102(4),
    BGRA_1010102(4),
    RGB_101010X(4),
    BGR_101010X(4),
    BGR_101010X_XR(4),
    BGRA_10101010_XR(8),
    RGBA_10X6(8),
    GRAY_8(1),
    RGBA_F16_NORM(8),
    RGBA_F16(8),
    RGB_F16F16F16X(8),
    RGBA_F32(16),
    R8G8_UNORM(2),
    A16_FLOAT(2),
    R16G16_FLOAT(4),
    A16_UNORM(2),
    R16_UNORM(2),
    R16G16_UNORM(4),
    R16G16B16A16_UNORM(8),
    SRGBA_8888(4),
    R8_UNORM(1),
    ;

    fun defaultAlphaType(): AlphaType = when (this) {
        UNKNOWN -> AlphaType.UNKNOWN
        ALPHA_8,
        ARGB_4444,
        RGBA_F16_NORM,
        RGBA_F16,
        A16_FLOAT,
        A16_UNORM,
            -> AlphaType.PREMUL
        RGB_565,
        RGB_888X,
        RGB_101010X,
        BGR_101010X,
        BGR_101010X_XR,
        GRAY_8,
        RGB_F16F16F16X,
        R8G8_UNORM,
        R16G16_FLOAT,
        R16_UNORM,
        R16G16_UNORM,
        R8_UNORM,
            -> AlphaType.OPAQUE
        RGBA_8888,
        BGRA_8888,
        RGBA_1010102,
        BGRA_1010102,
        BGRA_10101010_XR,
        RGBA_10X6,
        RGBA_F32,
        R16G16B16A16_UNORM,
        SRGBA_8888,
            -> AlphaType.UNPREMUL
    }

    fun isConcrete(): Boolean = this != UNKNOWN
}
