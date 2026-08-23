package org.graphiks.kanvas.image

data class ColorTypeCapabilities(
    val allocatable: Boolean,
    val cpuReadableWritable: Boolean,
)

fun ColorType.capabilities(): ColorTypeCapabilities = when (this) {
    ColorType.ALPHA_8,
    ColorType.RGB_565,
    ColorType.ARGB_4444,
    ColorType.RGBA_8888,
    ColorType.BGRA_8888,
    ColorType.GRAY_8,
    ColorType.RGBA_F16_NORM,
    ColorType.RGBA_F16,
        -> ColorTypeCapabilities(allocatable = true, cpuReadableWritable = true)
    else -> ColorTypeCapabilities(allocatable = false, cpuReadableWritable = false)
}
