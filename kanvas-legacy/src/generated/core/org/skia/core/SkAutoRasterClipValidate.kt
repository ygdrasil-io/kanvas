package org.skia.core

import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoRasterClipValidate : SkNoncopyable {
 * public:
 *     SkAutoRasterClipValidate(const SkRasterClip& rc) : fRC(rc) {
 *         fRC.validate();
 *     }
 *     ~SkAutoRasterClipValidate() {
 *         fRC.validate();
 *     }
 * private:
 *     const SkRasterClip& fRC;
 * }
 * ```
 */
public open class SkAutoRasterClipValidate public constructor(
  rc: SkRasterClip,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const SkRasterClip& fRC
   * ```
   */
  private val fRC: SkRasterClip = TODO("Initialize fRC")
}
