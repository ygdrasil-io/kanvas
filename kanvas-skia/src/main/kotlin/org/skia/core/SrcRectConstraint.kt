package org.skia.core

/**
 * Mirrors Skia's `SkCanvas::SrcRectConstraint`. Tells `drawImageRect` whether
 * texel sampling is allowed to leak outside the supplied source rect (e.g.
 * via bilinear filter taps). The kanvas-skia raster device honours strict
 * by clamping sample coordinates to the source-rect interior; under fast
 * the sampler is allowed to read clamped texels at the image edges.
 */
public enum class SrcRectConstraint {
    /** Sampling must stay inside the source rect (no edge bleed). */
    kStrict,

    /** Sampling may extend slightly outside the source rect (faster). */
    kFast,
}
