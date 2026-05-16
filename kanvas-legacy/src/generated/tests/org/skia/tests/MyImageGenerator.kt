package org.skia.tests

import org.skia.foundation.SkImageGenerator

/**
 * C++ original:
 * ```cpp
 * class MyImageGenerator : public SkImageGenerator {
 * public:
 *     MyImageGenerator() : SkImageGenerator(SkImageInfo::MakeN32Premul(0, 0)) {}
 * }
 * ```
 */
public open class MyImageGenerator public constructor() : SkImageGenerator(TODO())
