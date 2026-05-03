package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class AutoAAClipValidate {
 * public:
 *     AutoAAClipValidate(const SkAAClip& clip) : fClip(clip) {
 *         fClip.validate();
 *     }
 *     ~AutoAAClipValidate() {
 *         fClip.validate();
 *     }
 * private:
 *     const SkAAClip& fClip;
 * }
 * ```
 */
public data class AutoAAClipValidate public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkAAClip& fClip
   * ```
   */
  private val fClip: SkAAClip,
)
