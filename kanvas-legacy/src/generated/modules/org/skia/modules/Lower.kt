package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct Lower {
 *     // All Lowers are equal.
 *     friend bool operator< (const Lower&, const Lower&) {
 *         return false;
 *     }
 * }
 * ```
 */
public open class Lower
