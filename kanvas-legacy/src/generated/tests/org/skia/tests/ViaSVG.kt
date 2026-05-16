package org.skia.tests

import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class ViaSVG : public Via {
 * public:
 *     explicit ViaSVG(Sink* sink) : Via(sink) {}
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 * }
 * ```
 */
public open class ViaSVG public constructor(
  sink: Sink?,
) : Via(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override
   * ```
   */
  public override fun draw(
    param0: Src,
    param1: SkBitmap?,
    param2: SkWStream?,
    param3: String?,
  ): Result {
    TODO("Implement draw")
  }
}
