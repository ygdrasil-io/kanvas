package org.skia.foundation

import java.io.InputStream

/**
 * Mirrors Skia's `SkFontMgr` (`include/core/SkFontMgr.h:37`).
 *
 * Top-level family/style discovery interface. Each backend subclass
 * (FreeType + fontconfig on Linux, CoreText on macOS, DirectWrite on
 * Windows in upstream) enumerates the system fonts and exposes them
 * through this API surface — the cross-platform consumer never sees
 * platform-specific types.
 *
 * **Kotlin port :**
 *  - `:kanvas-skia` exposes this abstract surface plus portable managers
 *    such as `LiberationOpenTypeFontMgr`, backed by bundled OpenType data.
 *  - [EmptyFontMgr] is the parity with upstream's `SkFontMgr::RefEmpty()`
 *    — useful for unit tests that need an `SkFontMgr` handle without
 *    accidentally pulling in real fonts.
 *  - Platform default managers are supplied by integration modules so this
 *    portable foundation layer does not import desktop font APIs.
 *
 * **No reference counting** — Skia ships `SkFontMgr` as `sk_sp<SkFontMgr>`
 * because C++ has no GC. The Kotlin port relies on JVM tracing GC.
 *
 * **`InputStream` instead of `SkStream`** — upstream's `makeFromStream`
 * takes `std::unique_ptr<SkStreamAsset>`. The kanvas-skia port hasn't
 * lifted `SkStream` to Kotlin yet (deferred to R3.4 in the API plan),
 * so we expose [makeFromStream] taking a stdlib `InputStream` — same
 * convention as [org.graphiks.kanvas.codec.SkCodec.MakeFromStream]. Callers
 * retain ownership ; the stream is **not** closed by [makeFromStream]
 * (idiomatic Kotlin), unlike upstream which consumes the `unique_ptr`.
 */
public abstract class SkFontMgr protected constructor() {

    /** Mirrors `int SkFontMgr::countFamilies() const`. */
    public abstract fun countFamilies(): Int

    /** Mirrors `void SkFontMgr::getFamilyName(int, SkString*) const`. */
    public abstract fun getFamilyName(index: Int): String

    /** Mirrors `sk_sp<SkFontStyleSet> SkFontMgr::createStyleSet(int) const`. */
    public abstract fun createStyleSet(index: Int): SkFontStyleSet

    /**
     * Mirrors `sk_sp<SkFontStyleSet> SkFontMgr::matchFamily(const char[]) const`.
     *
     * Never returns null: when the family is not found, returns
     * [SkFontStyleSet.CreateEmpty]. When [familyName] is `null`, returns
     * the empty set on backends that have no default family
     * (e.g. [EmptyFontMgr]); bundled portable managers normally resolve to
     * their default sans-serif family.
     */
    public abstract fun matchFamily(familyName: String?): SkFontStyleSet

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::matchFamilyStyle(const char[],
     * const SkFontStyle&) const`. Returns `null` when no good match exists.
     */
    public abstract fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::matchFamilyStyleCharacter(...)
     * const`. Returns a typeface from the system fallback chain that can
     * render the supplied [character].
     *
     * Portable managers may return `null` when they cannot find a bundled
     * face that carries the requested codepoint. Callers needing full system
     * fallback should use a platform integration module.
     */
    public abstract fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromData(sk_sp<SkData>,
     * int ttcIndex) const`. Returns `null` when the data is not a
     * recognised font format.
     *
     * Backends that cannot select a requested collection face should return
     * `null` or their documented best-effort result.
     */
    public abstract fun makeFromData(data: SkData, ttcIndex: Int = 0): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromStream(
     * std::unique_ptr<SkStreamAsset>, int ttcIndex) const`. Returns
     * `null` when the stream is not recognised. The stream is **not**
     * closed by this call (idiomatic Kotlin).
     */
    public abstract fun makeFromStream(stream: InputStream, ttcIndex: Int = 0): SkTypeface?

    /**
     * R-suivi.45 — SkStream overload of [makeFromStream]. Drains the
     * stream to a `ByteArray` and routes through [makeFromData]. Subclasses
     * with a memory-backed [org.skia.foundation.stream.SkStreamAsset] can
     * override to avoid the copy.
     *
     * Mirrors upstream's `sk_sp<SkTypeface> SkFontMgr::makeFromStream(
     * std::unique_ptr<SkStreamAsset>, int)` once the kanvas-skia [SkStream]
     * hierarchy is the canonical byte source.
     */
    public open fun makeFromStream(
        stream: org.skia.foundation.stream.SkStream,
        ttcIndex: Int = 0,
    ): SkTypeface? {
        val bytes = drainSkStream(stream)
        if (bytes.isEmpty()) return null
        return makeFromData(SkData.MakeWithCopy(bytes), ttcIndex)
    }

    private fun drainSkStream(stream: org.skia.foundation.stream.SkStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!stream.isAtEnd()) {
            val n = stream.read(buf, buf.size)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromFile(const char[],
     * int ttcIndex) const`. Returns `null` when the file is missing or
     * the contents are not recognised.
     */
    public abstract fun makeFromFile(path: String, ttcIndex: Int = 0): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::legacyMakeTypeface(const char[],
     * SkFontStyle) const`. Equivalent to [matchFamilyStyle] on most
     * backends — Skia preserves it as a separate hook because some
     * legacy code paths bypass family enumeration.
     */
    public abstract fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface?

    public companion object {
        /**
         * Platform default font managers are provided by extension modules
         * so that `:kanvas-skia/foundation` remains portable and free of
         * desktop font imports.
         */

        /** Mirrors `sk_sp<SkFontMgr> SkFontMgr::RefEmpty()`. */
        public fun RefEmpty(): SkFontMgr = EmptyFontMgr
    }
}

/**
 * Singleton zero-family font manager. Mirrors `SkFontMgr::RefEmpty()`
 * upstream — every accessor returns null/empty. Useful for unit tests
 * that need an [SkFontMgr] handle without picking up the JVM's fonts.
 */
internal object EmptyFontMgr : SkFontMgr() {
    override fun countFamilies(): Int = 0
    override fun getFamilyName(index: Int): String =
        throw IndexOutOfBoundsException("EmptyFontMgr has 0 families ; index=$index")
    override fun createStyleSet(index: Int): SkFontStyleSet =
        throw IndexOutOfBoundsException("EmptyFontMgr has 0 families ; index=$index")
    override fun matchFamily(familyName: String?): SkFontStyleSet = SkFontStyleSet.CreateEmpty()
    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? = null
    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface? = null

    override fun makeFromData(data: SkData, ttcIndex: Int): SkTypeface? = null
    override fun makeFromStream(stream: InputStream, ttcIndex: Int): SkTypeface? = null
    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? = null
    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface? = null
}
