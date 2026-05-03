package org.skia.skcms

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
