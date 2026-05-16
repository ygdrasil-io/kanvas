package org.skia.tests

import org.skia.foundation.SkImageGenerator

/**
 * C++ original:
 * ```cpp
 * class EmptyGenerator : public SkImageGenerator {
 * public:
 *     EmptyGenerator() : SkImageGenerator(SkImageInfo::MakeN32Premul(0, 0)) {}
 * }
 * ```
 */
public open class EmptyGenerator public constructor() : SkImageGenerator(TODO())
