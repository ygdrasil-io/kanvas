package org.skia.foundation

import org.skia.core.SkImageGenerator
import org.skia.core.SkImageInfo

/**
 * C++ original:
 * ```cpp
 * class EmptyImageGenerator final : public SkImageGenerator {
 *     public:
 *         EmptyImageGenerator(const SkImageInfo& info) : SkImageGenerator(info) { }
 *
 *     }
 * ```
 */
public class EmptyImageGenerator public constructor(
  info: SkImageInfo,
) : SkImageGenerator(TODO())
