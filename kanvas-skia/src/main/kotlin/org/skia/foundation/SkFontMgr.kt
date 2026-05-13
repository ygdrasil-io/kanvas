package org.skia.foundation

import org.skia.foundation.stream.SkStream
import java.io.InputStream

/**
 * Mirrors Skia's
 * [`SkFontMgr`](https://github.com/google/skia/blob/main/include/core/SkFontMgr.h)
 * — a polymorphic factory + resolver for [SkTypeface] instances. In
 * Skia, concrete subclasses bind to a platform's font subsystem
 * (FreeType + fontconfig, CoreText, DirectWrite). The Kotlin port
 * ships a single concrete subclass in `:kanvas-skia` :
 * `org.skia.foundation.awt.JvmAwtFontMgr`, which wraps
 * `java.awt.Font` for both file/stream-backed typefaces and
 * system-resolved family lookups.
 *
 * **API surface** — this base class mirrors the headline upstream
 * methods (`matchFamilyStyle`, `matchFamilyStyleCharacter`,
 * `makeFromData`, `makeFromStream`, `makeFromFile`,
 * `legacyMakeTypeface`). The `count*` / `createStyleSet` family
 * iteration methods, the `SkFontArguments` variation overload, and
 * the `protected on*` virtuals from `SkFontMgr.h` are not yet ported
 * — they are tracked in `API_REMEDIATION_PLAN.md` §1.6 and will
 * land when a consumer needs them.
 */
public abstract class SkFontMgr {

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::matchFamilyStyle(const char
     * familyName[], const SkFontStyle&)`. Returns the closest matching
     * typeface to the requested `(familyName, style)`, or `null` if no
     * good match is found. Passing `null` as `familyName` returns the
     * default system family.
     */
    public abstract fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::matchFamilyStyleCharacter(
     *     const char familyName[], const SkFontStyle&,
     *     const char* bcp47[], int bcp47Count, SkUnichar character)`.
     *
     * Resolves a typeface that contains a glyph for [character] via the
     * platform's font fallback chain. `bcp47` is a list of BCP-47
     * language tags in order of increasing significance (Skia's
     * convention — `bcp47[bcp47Count-1]` is the most significant).
     * Returns `null` if no family can be found.
     */
    public abstract fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: List<String>,
        character: Int,
    ): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromData(sk_sp<SkData>, int ttcIndex = 0)`.
     * Returns a typeface decoded from [data], or `null` if the bytes
     * are not a recognised font. If [data] is a TrueType Collection
     * (TTC) the [ttcIndex]-th face is extracted; passing `ttcIndex`
     * out of range returns `null`.
     */
    public abstract fun makeFromData(data: ByteArray, ttcIndex: Int = 0): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromStream(
     *     std::unique_ptr<SkStreamAsset>, int ttcIndex = 0)`.
     * Reads [stream] to end-of-stream into a byte buffer and routes to
     * [makeFromData]. Returns `null` if the stream contents are not a
     * recognised font, or if [ttcIndex] is out of range for the
     * collection.
     */
    public abstract fun makeFromStream(stream: SkStream, ttcIndex: Int = 0): SkTypeface?

    /**
     * Backwards-compatible overload accepting a `java.io.InputStream`.
     * The default implementation reads [stream] into memory and
     * delegates to [makeFromData].
     */
    @Deprecated(
        "Use SkStream overload",
        ReplaceWith("makeFromStream(stream, ttcIndex)"),
    )
    public open fun makeFromStream(stream: InputStream, ttcIndex: Int = 0): SkTypeface? {
        val bytes = stream.readBytes()
        return makeFromData(bytes, ttcIndex)
    }

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::makeFromFile(const char path[], int ttcIndex = 0)`.
     * Reads [path] from disk and routes through [makeFromData]. Returns
     * `null` if the file is not found, unreadable, or its contents are
     * not a recognised font.
     */
    public abstract fun makeFromFile(path: String, ttcIndex: Int = 0): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontMgr::legacyMakeTypeface(const
     * char familyName[], SkFontStyle style)`. Skia keeps this around
     * for callers that historically expected a never-null return — the
     * implementation should always resolve to *some* typeface,
     * falling back to the platform default if [familyName] is unknown.
     */
    public abstract fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface

    public companion object {
        /**
         * Mirrors `sk_sp<SkFontMgr> SkFontMgr::RefEmpty()` — returns a
         * font manager that produces no typefaces. Useful for tests
         * that exercise the API surface without bringing in a full
         * font subsystem.
         */
        public fun RefEmpty(): SkFontMgr = EmptyFontMgr

        /**
         * Returns the default platform font manager. On the JVM this is
         * the AWT-backed `JvmAwtFontMgr`. Loaded lazily via
         * `Class.forName` to keep this base class free of an `:awt`
         * compile-time dependency — useful if the module is ever
         * spliced into a non-AWT runtime (Android, native).
         */
        public fun RefDefault(): SkFontMgr {
            val cached = defaultInstance
            if (cached != null) return cached
            val loaded = try {
                val cls = Class.forName("org.skia.foundation.awt.JvmAwtFontMgr")
                cls.getDeclaredConstructor().newInstance() as SkFontMgr
            } catch (_: Throwable) {
                EmptyFontMgr
            }
            defaultInstance = loaded
            return loaded
        }

        @Volatile
        private var defaultInstance: SkFontMgr? = null

        private object EmptyFontMgr : SkFontMgr() {
            override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? = null
            override fun matchFamilyStyleCharacter(
                familyName: String?,
                style: SkFontStyle,
                bcp47: List<String>,
                character: Int,
            ): SkTypeface? = null

            override fun makeFromData(data: ByteArray, ttcIndex: Int): SkTypeface? = null
            override fun makeFromStream(stream: SkStream, ttcIndex: Int): SkTypeface? = null
            override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? = null
            override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface =
                SkTypeface.MakeEmpty()
        }
    }
}
