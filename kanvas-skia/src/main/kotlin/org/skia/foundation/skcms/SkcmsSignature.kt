package org.skia.foundation.skcms

/**
 * Bit-compatible port of `skcms_Signature`
 * ([modules/skcms/src/skcms_public.h:326-357](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h)).
 *
 * Common ICC profile signatures, used as `data_color_space` and `pcs`
 * fields. Values are the big-endian interpretation of the 4-byte ASCII
 * signature, e.g. `'RGB '` = `0x52474220`.
 */
public enum class SkcmsSignature(public val value: Int) {
    CMYK(0x434D594B),
    Gray(0x47524159),
    RGB(0x52474220),
    Lab(0x4C616220),
    XYZ(0x58595A20),

    CIELUV(0x4C757620),
    YCbCr(0x59436272),
    CIEYxy(0x59787920),
    HSV(0x48535620),
    HLS(0x484C5320),
    CMY(0x434D5920),

    `2CLR`(0x32434C52),
    `3CLR`(0x33434C52),
    `4CLR`(0x34434C52),
    `5CLR`(0x35434C52),
    `6CLR`(0x36434C52),
    `7CLR`(0x37434C52),
    `8CLR`(0x38434C52),
    `9CLR`(0x39434C52),
    `10CLR`(0x41434C52),
    `11CLR`(0x42434C52),
    `12CLR`(0x43434C52),
    `13CLR`(0x44434C52),
    `14CLR`(0x45434C52),
    `15CLR`(0x46434C52);

    public companion object {
        /** Look up a [SkcmsSignature] by its 4-byte big-endian value, or
         *  `null` if not a known signature. */
        public fun fromValue(value: Int): SkcmsSignature? =
            entries.firstOrNull { it.value == value }
    }
}

/**
 * ICC tag and type signatures, file-scope constants. Mirrors `Signature_*`
 * in upstream
 * [modules/skcms/skcms.cc:359-377](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/skcms.cc).
 *
 * Values are big-endian ASCII: e.g. `'rTRC'` = `0x72545243`.
 */
@Suppress("MagicNumber")
public object SkcmsTagSignature {
    /** ICC profile header signature ('acsp'). */
    public const val acsp: Int = 0x61637370

    // Type signatures (first 4 bytes of a tag's data)
    public const val curv: Int = 0x63757276
    public const val para: Int = 0x70617261
    public const val sf32: Int = 0x73663332
    public const val mft1: Int = 0x6D667431
    public const val mft2: Int = 0x6D667432
    public const val mAB: Int = 0x6D414220
    public const val mBA: Int = 0x6D424120

    // Tag signatures
    public const val rTRC: Int = 0x72545243
    public const val gTRC: Int = 0x67545243
    public const val bTRC: Int = 0x62545243
    public const val kTRC: Int = 0x6B545243
    public const val rXYZ: Int = 0x7258595A
    public const val gXYZ: Int = 0x6758595A
    public const val bXYZ: Int = 0x6258595A
    public const val WTPT: Int = 0x77747074
    public const val CHAD: Int = 0x63686164
    public const val CICP: Int = 0x63696370
    public const val A2B0: Int = 0x41324230
    public const val A2B1: Int = 0x41324231
    public const val A2B2: Int = 0x41324232
    public const val B2A0: Int = 0x42324130
    public const val B2A1: Int = 0x42324131
    public const val B2A2: Int = 0x42324132
}
