package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SkSpanPriv {
 * public:
 *     template <typename T> static bool EQ(SkSpan<T> a, SkSpan<T> b) {
 *         if (a.size() != b.size()) {
 *             return false;
 *         }
 *         if (a.empty()) {
 *             return true;
 *         }
 *         return (a.data() == b.data()) || std::equal(a.begin(), a.end(), b.begin());
 *     }
 *
 *     template <typename T> static void Copy(SkSpan<T> dst, SkSpan<const T> src) {
 *         SkASSERT(dst.size() == src.size());
 *         sk_careful_memcpy(dst.data(), src.data(), src.size_bytes());
 *     }
 * }
 * ```
 */
public open class SkSpanPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool EQ(SkSpan<T> a, SkSpan<T> b) {
     *         if (a.size() != b.size()) {
     *             return false;
     *         }
     *         if (a.empty()) {
     *             return true;
     *         }
     *         return (a.data() == b.data()) || std::equal(a.begin(), a.end(), b.begin());
     *     }
     * ```
     */
    public fun <T> eq(a: SkSpan<T>, b: SkSpan<T>): Boolean {
      TODO("Implement eq")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Copy(SkSpan<T> dst, SkSpan<const T> src) {
     *         SkASSERT(dst.size() == src.size());
     *         sk_careful_memcpy(dst.data(), src.data(), src.size_bytes());
     *     }
     * ```
     */
    public fun <T> copy(dst: SkSpan<T>, src: SkSpan<T>) {
      TODO("Implement copy")
    }
  }
}
