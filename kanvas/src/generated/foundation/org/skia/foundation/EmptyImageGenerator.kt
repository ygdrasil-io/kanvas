package org.skia.foundation

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
