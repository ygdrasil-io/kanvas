package org.skia.foundation

import kotlin.math.abs

/**
 * Mirrors Skia's `SkFontStyleSet` (`include/core/SkFontMgr.h:24`).
 *
 * A collection of typefaces all sharing the same family name. Backends
 * subclass this to expose the styles available under a given family —
 * e.g. the bundled Liberation OpenType manager represents each family
 * with regular / bold / italic / bold-italic faces. The
 * upstream signature exposes four virtuals (`count`, `getStyle`,
 * `createTypeface`, `matchStyle`) plus a static empty constructor — we
 * mirror exactly that surface.
 *
 * **No reference counting** — Skia ships `SkFontStyleSet` as
 * `sk_sp<SkFontStyleSet>` because C++ has no GC. The Kotlin port relies
 * on JVM tracing GC.
 *
 * @see SkFontMgr for the discovery entry point.
 */
public abstract class SkFontStyleSet protected constructor() {

    /** Number of typefaces in the set. Mirrors `SkFontStyleSet::count()`. */
    public abstract fun count(): Int

    /**
     * Fills [style] (when non-null) with the [index]-th typeface's
     * [SkFontStyle], appends the style name (e.g. `"Bold"`, `"Italic"`)
     * to [name] (when non-null) and returns the style as a convenience.
     *
     * Mirrors `void SkFontStyleSet::getStyle(int, SkFontStyle*, SkString*)`.
     * Kotlin requires a return value because we cannot mutate the
     * `SkFontStyle` in place (it's immutable, like upstream's
     * `SkFontStyle` — the `getStyle` C++ overwrite is a copy assignment).
     * Callers can ignore the return value and read from [style] when
     * mimicking the upstream C++ pattern.
     */
    public abstract fun getStyle(index: Int, style: SkFontStyle?, name: StringBuilder?): SkFontStyle

    /** Mirrors `sk_sp<SkTypeface> SkFontStyleSet::createTypeface(int)`. */
    public abstract fun createTypeface(index: Int): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontStyleSet::matchStyle(const SkFontStyle&)`.
     *
     * Default implementation in upstream is `matchStyleCSS3(pattern)` —
     * here we expose [matchStyleCSS3] as a `protected` helper so subclasses
     * can call it but aren't forced to use it.
     */
    public abstract fun matchStyle(pattern: SkFontStyle): SkTypeface?

    /**
     * Mirrors `sk_sp<SkTypeface> SkFontStyleSet::matchStyleCSS3(const SkFontStyle&)`.
     *
     * Implements the CSS 3 font-style matching algorithm (see
     * `src/core/SkFontMgr.cpp::matchStyleCSS3` upstream): for each
     * typeface in the set, compute a distance to [pattern] across the
     * three style axes (slant, then width, then weight per CSS 3 §5.2);
     * return the typeface with the smallest total distance. Ties are
     * broken in favour of the earlier-indexed typeface (mirroring
     * upstream's left-to-right scan).
     */
    protected fun matchStyleCSS3(pattern: SkFontStyle): SkTypeface? {
        val n = count()
        if (n == 0) return null
        var bestIdx = -1
        var bestScore = Int.MAX_VALUE
        for (i in 0 until n) {
            val s = getStyle(i, null, null)
            val score = css3Distance(s, pattern)
            if (score < bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        if (bestIdx < 0) return null
        return createTypeface(bestIdx)
    }

    public companion object {
        /** Mirrors `sk_sp<SkFontStyleSet> SkFontStyleSet::CreateEmpty()`. */
        public fun CreateEmpty(): SkFontStyleSet = EmptyFontStyleSet

        /**
         * Distance heuristic between two [SkFontStyle]s — mirrors the
         * upstream CSS 3 algorithm with simplified scalar weighting:
         * slant mismatch dominates (×1000), width next (×100), weight last
         * (×1). Lower is closer.
         */
        internal fun css3Distance(a: SkFontStyle, b: SkFontStyle): Int {
            val slantDelta = if (a.slant == b.slant) 0 else 1
            val widthDelta = abs(a.width - b.width)
            val weightDelta = abs(a.weight - b.weight)
            return slantDelta * 1000 + widthDelta * 100 + weightDelta
        }
    }
}

/**
 * Singleton zero-typeface set. Mirrors the static returned by
 * `SkFontStyleSet::CreateEmpty()` upstream — used by font managers
 * when a query matches no family (and they need to return a non-null
 * set, per upstream's `matchFamily` contract).
 */
internal object EmptyFontStyleSet : SkFontStyleSet() {
    override fun count(): Int = 0
    override fun getStyle(index: Int, style: SkFontStyle?, name: StringBuilder?): SkFontStyle =
        throw IndexOutOfBoundsException("EmptyFontStyleSet has 0 typefaces ; index=$index")
    override fun createTypeface(index: Int): SkTypeface? = null
    override fun matchStyle(pattern: SkFontStyle): SkTypeface? = null
}
