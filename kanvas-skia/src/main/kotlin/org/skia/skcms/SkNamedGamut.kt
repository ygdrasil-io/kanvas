package org.skia.skcms

/**
 * Bit-compatible port of `SkNamedGamut` — RGB→XYZ matrices Bradford-adapted
 * to D50 (which is what ICC PCS expects). Numerically identical to upstream
 * `SkNamedGamut::*` constants; for the Rec.2020 entry we also matched the
 * exact s15Fixed16 values from the reference PNGs (see
 * colorspace-fingerprint.md).
 */
public object SkNamedGamut {

    public val kSRGB: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.436065674f, 0.385147095f, 0.143066406f,
        0.222488403f, 0.716873169f, 0.060607910f,
        0.013916016f, 0.097076416f, 0.714096069f,
    )

    public val kAdobeRGB: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.609741211f, 0.205276489f, 0.149185181f,
        0.311111450f, 0.625671387f, 0.063217163f,
        0.019470215f, 0.060867310f, 0.744567871f,
    )

    public val kDisplayP3: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.515102f,    0.291965f,   0.157153f,
        0.241182f,    0.692236f,   0.0665819f,
       -0.00104941f,  0.0418818f,  0.784378f,
    )

    public val kRec2020: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.673459f,   0.165665f,   0.125107f,
        0.279037f,   0.675339f,   0.045624f,
       -0.001938f,   0.029984f,   0.797165f,
    )

    public val kXYZ: SkcmsMatrix3x3 = SkcmsMatrix3x3.IDENTITY
}
