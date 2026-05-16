package org.skia.foundation.skcms

/**
 * Locator for one entry in the ICC tag table.
 *
 * Mirrors `skcms_ICCTag` in upstream
 * [modules/skcms/src/skcms_public.h](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h):
 *  - [signature] — 4-byte tag signature, e.g. `'rTRC'` = `0x72545243`.
 *  - [type] — 4-byte type signature read from the first 4 bytes of the
 *    tag's data, e.g. `'para'` for parametric curves, `'curv'` for LUT.
 *  - [offset] — byte offset of the tag's data in the source ICC buffer.
 *  - [size] — byte length of the tag's data.
 */
public data class SkcmsICCTag(
    public val signature: Int,
    public val type: Int,
    public val offset: Int,
    public val size: Int,
)
