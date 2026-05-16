package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkRefCnt
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class PrecompInterceptor : public SkRefCnt {
 * public:
 *     /**
 *      * Invoked at animation build time, for each precomp layer.
 *      *
 *      * @param id    The target composition ID (usually assigned automatically by BM: comp_0, ...)
 *      * @param name  The name of the precomp layer (by default it matches the target comp name,
 *      *              but can be changed in AE)
 *      * @param size  Lottie-specified precomp layer size
 *      * @return      An ExternalLayer implementation (to be used instead of the actual Lottie file
 *      *              content), or nullptr (to use the Lottie file content).
 *      */
 *     virtual sk_sp<ExternalLayer> onLoadPrecomp(const char id[],
 *                                                const char name[],
 *                                                const SkSize& size) = 0;
 * }
 * ```
 */
public abstract class PrecompInterceptor : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ExternalLayer> onLoadPrecomp(const char id[],
   *                                                const char name[],
   *                                                const SkSize& size) = 0
   * ```
   */
  public abstract fun onLoadPrecomp(
    id: CharArray,
    name: CharArray,
    size: SkSize,
  ): Int
}
