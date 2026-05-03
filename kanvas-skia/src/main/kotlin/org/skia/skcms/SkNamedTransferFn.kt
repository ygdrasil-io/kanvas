package org.skia.skcms

/**
 * Bit-compatible port of `SkNamedTransferFn` (a few of the named transfer
 * functions Skia uses everywhere). Phase 1: only the ones we need for sRGB
 * and Rec.2020 round-trips.
 */
public object SkNamedTransferFn {

    /**
     * Standard sRGB OETF — these are the same constants as
     * `SkNamedTransferFn::kSRGB` upstream.
     */
    public val kSRGB: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.4f,
        a = 1.0f / 1.055f,
        b = 0.055f / 1.055f,
        c = 1.0f / 12.92f,
        d = 0.04045f,
        e = 0.0f,
        f = 0.0f,
    )

    /**
     * Identity transfer function. `kLinear.eval(x) == x` for x in [0, 1].
     * Matches upstream `SkNamedTransferFn::kLinear`.
     */
    public val kLinear: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /**
     * Pure 2.2-power. Matches upstream `SkNamedTransferFn::k2Dot2`.
     */
    public val k2Dot2: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.2f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f,
    )

    /**
     * BT.2020 OETF. Same exact constants as the ICC profile shipped with
     * the `original-888` reference PNGs (see colorspace-fingerprint.md),
     * except stored at full float precision instead of s15Fixed16. Matches
     * upstream `SkNamedTransferFn::kRec2020`.
     */
    public val kRec2020: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.22221961f,
        a = 0.909672439f,
        b = 0.0903276134f,
        c = 0.222222447f,
        d = 0.0812428713f,
        e = 0f, f = 0f,
    )
}
