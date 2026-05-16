package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkPMColor
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * class SK_API SkUnPreMultiply {
 * public:
 *     typedef uint32_t Scale;
 *
 *     // index this table with alpha [0..255]
 *     static const Scale* GetScaleTable() {
 *         return gTable;
 *     }
 *
 *     static Scale GetScale(U8CPU alpha) {
 *         SkASSERT(alpha <= 255);
 *         return gTable[alpha];
 *     }
 *
 *     /** Usage:
 *
 *         const Scale* table = SkUnPreMultiply::GetScaleTable();
 *
 *         for (...) {
 *             unsigned a = ...
 *             SkUnPreMultiply::Scale scale = table[a];
 *
 *             red = SkUnPreMultiply::ApplyScale(scale, red);
 *             ...
 *             // now red is unpremultiplied
 *         }
 *     */
 *     static U8CPU ApplyScale(Scale scale, U8CPU component) {
 *         SkASSERT(component <= 255);
 *         return (scale * component + (1 << 23)) >> 24;
 *     }
 *
 *     static SkColor PMColorToColor(SkPMColor c);
 *
 * private:
 *     static const uint32_t gTable[256];
 * }
 * ```
 */
public open class SkUnPreMultiply {
  public companion object {
    private val gTable: Array<UInt> = TODO("Initialize gTable")

    /**
     * C++ original:
     * ```cpp
     * static const Scale* GetScaleTable() {
     *         return gTable;
     *     }
     * ```
     */
    public fun getScaleTable(): SkUnPreMultiplyScale {
      TODO("Implement getScaleTable")
    }

    /**
     * C++ original:
     * ```cpp
     * static Scale GetScale(U8CPU alpha) {
     *         SkASSERT(alpha <= 255);
     *         return gTable[alpha];
     *     }
     * ```
     */
    public fun getScale(alpha: U8CPU): SkUnPreMultiplyScale {
      TODO("Implement getScale")
    }

    /**
     * C++ original:
     * ```cpp
     * static U8CPU ApplyScale(Scale scale, U8CPU component) {
     *         SkASSERT(component <= 255);
     *         return (scale * component + (1 << 23)) >> 24;
     *     }
     * ```
     */
    public fun applyScale(scale: SkUnPreMultiplyScale, component: U8CPU): Int {
      TODO("Implement applyScale")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColor SkUnPreMultiply::PMColorToColor(SkPMColor c) {
     *     const unsigned a = SkGetPackedA32(c);
     *     const Scale scale = GetScale(a);
     *     return SkColorSetARGB(a,
     *                           ApplyScale(scale, SkGetPackedR32(c)),
     *                           ApplyScale(scale, SkGetPackedG32(c)),
     *                           ApplyScale(scale, SkGetPackedB32(c)));
     * }
     * ```
     */
    public fun pMColorToColor(c: SkPMColor): Int {
      TODO("Implement pMColorToColor")
    }
  }
}
