package org.graphiks.kanvas.render.ir

/** The arithmetic domain used to composite a Surface's color values. */
public enum class CompositionDomain {
    /** Linear-light premultiplied composition, preserving the historical contract. */
    LINEAR,

    /** sRGB-encoded premultiplied composition for the admitted Surface subset. */
    SRGB_ENCODED,
}
