package org.skia.foundation.skcms
import org.graphiks.math.SkcmsMatrix3x3

/**
 * Bit-compatible port of `SkNamedGamut` — RGB→XYZ matrices Bradford-adapted
 * to D50 (which is what ICC PCS expects). All values match upstream
 * [SkColorSpace.h:225-261](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)
 * exactly; the kSRGB/kAdobeRGB matrices are the `SkFixedToFloat`-decoded
 * s15Fixed16 representations Skia ships, written out as float literals.
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

    /** Bradford-adapted Rec.2020 primaries to D50, aligned with upstream
     *  Skia (6 decimal places). Phase B snap (`xyz_almost_equal` tolerance
     *  0.01) absorbs the ~1e-5 divergence with the s15Fixed16-decoded
     *  values from the DM reference PNG ICC profile. */
    public val kRec2020: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
         0.673459f,   0.165661f,   0.125100f,
         0.279033f,   0.675338f,   0.0456288f,
        -0.00193139f, 0.0299794f,  0.797162f,
    )

    public val kXYZ: SkcmsMatrix3x3 = SkcmsMatrix3x3.IDENTITY
}
